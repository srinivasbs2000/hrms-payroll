-- S3-04A controlled recalculation and supersession database foundation.
--
-- Preserve every completed calculation request and its immutable result,
-- component and trace evidence. A recalculation creates a new completed request
-- linked to the prior active request, then atomically advances the payroll-cycle
-- active-calculation pointer. V024 sealed inputs remain the only calculation
-- inputs and the STARTER_FIXED_V1 engine remains deterministic.

ALTER TABLE payroll_ops.payroll_cycle NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_request NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.payroll_result NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.component_result NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_trace NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request request
    WHERE request.request_schema_version = 1
    GROUP BY request.tenant_id, request.payroll_cycle_id
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION
      'V026 requires at most one schema-version-1 calculation request per cycle before recalculation support';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    WHERE cycle.status = 'CALCULATED'
      AND (
        cycle.active_calculation_request_id IS NULL
        OR NOT EXISTS (
          SELECT 1
          FROM payroll_calc.calculation_request request
          WHERE request.tenant_id = cycle.tenant_id
            AND request.id = cycle.active_calculation_request_id
            AND request.payroll_cycle_id = cycle.id
            AND request.status = 'COMPLETED'
        )
      )
  ) THEN
    RAISE EXCEPTION
      'calculated cycles require one active completed request before V026';
  END IF;
END $$;

ALTER TABLE payroll_calc.calculation_request
  ADD COLUMN calculation_kind varchar(20) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN attempt_no integer NOT NULL DEFAULT 0,
  ADD COLUMN supersedes_request_id uuid,
  ADD COLUMN recalculation_reason varchar(500),
  ADD COLUMN engine_version varchar(40) NOT NULL DEFAULT 'LEGACY';

UPDATE payroll_calc.calculation_request
SET calculation_kind = 'INITIAL',
    attempt_no = 1,
    engine_version = 'STARTER_FIXED_V1'
WHERE request_schema_version = 1;

ALTER TABLE payroll_calc.calculation_request
  ADD CONSTRAINT calculation_request_kind_ck
    CHECK (calculation_kind IN ('LEGACY', 'INITIAL', 'RECALCULATION')),
  ADD CONSTRAINT calculation_request_attempt_ck
    CHECK (attempt_no >= 0),
  ADD CONSTRAINT calculation_request_engine_version_ck
    CHECK (engine_version ~ '^[A-Z][A-Z0-9_]{2,39}$'),
  ADD CONSTRAINT calculation_request_supersedes_self_ck
    CHECK (
      supersedes_request_id IS NULL
      OR supersedes_request_id <> id
    ),
  ADD CONSTRAINT calculation_request_attempt_shape_ck
    CHECK (
      (
        request_schema_version = 0
        AND calculation_kind = 'LEGACY'
        AND attempt_no = 0
        AND supersedes_request_id IS NULL
        AND recalculation_reason IS NULL
        AND engine_version = 'LEGACY'
      )
      OR (
        request_schema_version = 1
        AND engine_version = 'STARTER_FIXED_V1'
        AND (
          (
            calculation_kind = 'INITIAL'
            AND attempt_no = 1
            AND supersedes_request_id IS NULL
            AND recalculation_reason IS NULL
          )
          OR (
            calculation_kind = 'RECALCULATION'
            AND attempt_no > 1
            AND supersedes_request_id IS NOT NULL
            AND recalculation_reason IS NOT NULL
            AND length(btrim(recalculation_reason)) BETWEEN 8 AND 500
          )
        )
      )
    ),
  ADD CONSTRAINT calculation_request_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_request_id,
      payroll_cycle_id
    )
    REFERENCES payroll_calc.calculation_request(
      tenant_id,
      id,
      payroll_cycle_id
    );

DROP INDEX payroll_calc.calculation_request_one_completed_cycle_uk;

-- Recalculations preserve historical results under separate requests.
-- Per-attempt uniqueness remains enforced by the V025 request/snapshot key.
ALTER TABLE payroll_calc.payroll_result
  DROP CONSTRAINT payroll_result_tenant_id_payroll_cycle_id_payroll_assignmen_key;

CREATE OR REPLACE FUNCTION
  payroll_calc.apply_calculation_attempt_defaults()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.request_schema_version = 1
     AND NEW.calculation_kind = 'LEGACY'
     AND NEW.attempt_no = 0
     AND NEW.supersedes_request_id IS NULL THEN
    NEW.calculation_kind := 'INITIAL';
    NEW.attempt_no := 1;
    NEW.engine_version := 'STARTER_FIXED_V1';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER calculation_request_attempt_defaults
  BEFORE INSERT
  ON payroll_calc.calculation_request
  FOR EACH ROW
  EXECUTE FUNCTION payroll_calc.apply_calculation_attempt_defaults();

CREATE UNIQUE INDEX calculation_request_cycle_attempt_uk
  ON payroll_calc.calculation_request(
    tenant_id,
    payroll_cycle_id,
    attempt_no
  )
  WHERE request_schema_version = 1;

CREATE UNIQUE INDEX calculation_request_one_successor_uk
  ON payroll_calc.calculation_request(
    tenant_id,
    supersedes_request_id
  )
  WHERE supersedes_request_id IS NOT NULL;

CREATE INDEX calculation_request_cycle_attempt_desc_ix
  ON payroll_calc.calculation_request(
    tenant_id,
    payroll_cycle_id,
    attempt_no DESC,
    requested_at DESC
  );

CREATE OR REPLACE FUNCTION payroll_calc.recalculate_sealed_payroll(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_expected_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_recalculation_reason varchar,
  p_actor varchar,
  p_recalculated_at timestamptz
) RETURNS TABLE (
  calculation_request_id uuid,
  superseded_request_id uuid,
  attempt_no integer,
  result_count integer,
  gross_total numeric(19,4),
  deduction_total numeric(19,4),
  net_total numeric(19,4),
  result_set_hash char(64),
  cycle_version_no bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_calc,
  payroll_ops,
  compensation,
  employee_payroll,
  organisation,
  platform AS $$
DECLARE
  v_existing payroll_calc.calculation_request%ROWTYPE;
  v_parent payroll_calc.calculation_request%ROWTYPE;
  v_cycle_status payroll_ops.cycle_status;
  v_cycle_version bigint;
  v_cycle_type varchar(20);
  v_active_request_id uuid;
  v_attempt_no integer;
  v_snapshot_count integer;
  v_expected_snapshot_count integer;
  v_snapshot_set_hash char(64);
  v_input_sealed_at timestamptz;
  v_actual_snapshot_set_hash char(64);
  v_request_id uuid;
  v_result_id uuid;
  v_component_result_id uuid;
  v_result_count integer := 0;
  v_gross_total numeric(19,4) := 0;
  v_deduction_total numeric(19,4) := 0;
  v_net_total numeric(19,4) := 0;
  v_result_set_hash char(64);
  v_snapshot record;
  v_line jsonb;
  v_component jsonb;
  v_components jsonb;
  v_component_payload jsonb;
  v_component_hash char(64);
  v_trace_payload jsonb;
  v_trace_hash char(64);
  v_result_payload jsonb;
  v_result_hash char(64);
  v_formula_type varchar(30);
  v_component_type varchar(20);
  v_component_code varchar(40);
  v_rounding_scale integer;
  v_sequence_no integer;
  v_component_count integer;
  v_source_amount numeric(19,4);
  v_calculated_amount numeric(19,4);
  v_result_gross numeric(19,4);
  v_result_deduction numeric(19,4);
  v_result_net numeric(19,4);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_idempotency_key IS NULL
     OR length(btrim(p_idempotency_key)) < 8
     OR length(btrim(p_idempotency_key)) > 120 THEN
    RAISE EXCEPTION
      'idempotency key must contain between 8 and 120 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_request_hash IS NULL
     OR p_request_hash !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'request hash must be a lowercase SHA-256 value'
      USING ERRCODE = '23514';
  END IF;

  IF p_recalculation_reason IS NULL
     OR length(btrim(p_recalculation_reason)) < 8
     OR length(btrim(p_recalculation_reason)) > 500 THEN
    RAISE EXCEPTION
      'recalculation reason must contain between 8 and 500 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_recalculated_at IS NULL THEN
    RAISE EXCEPTION 'recalculation timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT request.*
  INTO v_existing
  FROM payroll_calc.calculation_request request
  WHERE request.tenant_id = p_tenant_id
    AND request.idempotency_key = btrim(p_idempotency_key)
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.payroll_cycle_id <> p_payroll_cycle_id
       OR v_existing.request_hash <> p_request_hash::char(64)
       OR v_existing.calculation_kind <> 'RECALCULATION'
       OR v_existing.recalculation_reason <> btrim(p_recalculation_reason) THEN
      RAISE EXCEPTION
        'idempotency key was already used with a different recalculation request'
        USING ERRCODE = '23505';
    END IF;

    IF v_existing.request_schema_version = 1
       AND v_existing.status = 'COMPLETED' THEN
      RETURN QUERY
      SELECT
        v_existing.id,
        v_existing.supersedes_request_id,
        v_existing.attempt_no,
        v_existing.result_count,
        v_existing.gross_total,
        v_existing.deduction_total,
        v_existing.net_total,
        v_existing.result_set_hash,
        v_existing.completed_cycle_version;
      RETURN;
    END IF;

    RAISE EXCEPTION 'recalculation request is already in progress'
      USING ERRCODE = '40001';
  END IF;

  SELECT
    cycle.status,
    cycle.version_no,
    cycle.cycle_type,
    cycle.active_calculation_request_id,
    cycle.input_snapshot_count,
    cycle.input_snapshot_set_hash,
    cycle.input_sealed_at
  INTO
    v_cycle_status,
    v_cycle_version,
    v_cycle_type,
    v_active_request_id,
    v_expected_snapshot_count,
    v_snapshot_set_hash,
    v_input_sealed_at
  FROM payroll_ops.payroll_cycle cycle
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
   AND period.status = 'OPEN'
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
  FOR UPDATE OF cycle;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll cycle does not exist with an open period in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_cycle_version <> p_expected_version THEN
    RAISE EXCEPTION 'payroll cycle changed since it was read'
      USING ERRCODE = '40001';
  END IF;

  IF v_cycle_type <> 'REGULAR'
     OR v_cycle_status <> 'CALCULATED'
     OR v_active_request_id IS NULL THEN
    RAISE EXCEPTION
      'recalculation requires a calculated regular payroll cycle'
      USING ERRCODE = '23514';
  END IF;

  SELECT request.*
  INTO v_parent
  FROM payroll_calc.calculation_request request
  WHERE request.tenant_id = p_tenant_id
    AND request.id = v_active_request_id
    AND request.payroll_cycle_id = p_payroll_cycle_id
  FOR UPDATE;

  IF NOT FOUND
     OR v_parent.request_schema_version <> 1
     OR v_parent.status <> 'COMPLETED'
     OR v_parent.result_count IS NULL
     OR v_parent.result_set_hash IS NULL THEN
    RAISE EXCEPTION
      'active calculation request is not a completed schema-version-1 request'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request successor
    WHERE successor.tenant_id = p_tenant_id
      AND successor.supersedes_request_id = v_parent.id
  ) THEN
    RAISE EXCEPTION
      'active calculation request already has a recalculation successor'
      USING ERRCODE = '23505';
  END IF;

  v_attempt_no := v_parent.attempt_no + 1;

  IF v_expected_snapshot_count IS NULL
     OR v_expected_snapshot_count < 1
     OR v_snapshot_set_hash IS NULL
     OR v_input_sealed_at IS NULL THEN
    RAISE EXCEPTION 'payroll cycle lacks complete input-seal metadata'
      USING ERRCODE = '23514';
  END IF;

  IF v_parent.input_snapshot_set_hash IS DISTINCT FROM v_snapshot_set_hash THEN
    RAISE EXCEPTION
      'active calculation request does not match the sealed snapshot set'
      USING ERRCODE = '23514';
  END IF;

  IF p_recalculated_at < v_input_sealed_at
     OR p_recalculated_at < v_parent.completed_at THEN
    RAISE EXCEPTION
      'recalculation timestamp cannot precede sealing or prior completion'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    count(*)::integer,
    encode(
      public.digest(
        string_agg(
          snapshot.payroll_assignment_version_id::text
            || ':' || snapshot.snapshot_hash,
          '|'
          ORDER BY snapshot.payroll_assignment_version_id
        ),
        'sha256'::text
      ),
      'hex'
    )
  INTO
    v_snapshot_count,
    v_actual_snapshot_set_hash
  FROM payroll_ops.input_snapshot snapshot
  WHERE snapshot.tenant_id = p_tenant_id
    AND snapshot.payroll_cycle_id = p_payroll_cycle_id;

  IF v_snapshot_count <> v_expected_snapshot_count
     OR v_actual_snapshot_set_hash IS DISTINCT FROM v_snapshot_set_hash THEN
    RAISE EXCEPTION
      'sealed snapshot set no longer matches payroll-cycle metadata'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    WHERE snapshot.tenant_id = p_tenant_id
      AND snapshot.payroll_cycle_id = p_payroll_cycle_id
      AND (
        snapshot.payload_schema_version <> 1
        OR snapshot.snapshot_hash <> encode(
          public.digest(
            snapshot.snapshot_payload::text,
            'sha256'::text
          ),
          'hex'
        )
      )
  ) THEN
    RAISE EXCEPTION
      'recalculation requires canonical schema-version-1 input snapshots'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO payroll_calc.calculation_request(
    tenant_id,
    payroll_cycle_id,
    idempotency_key,
    request_hash,
    status,
    requested_at,
    created_at,
    created_by,
    updated_at,
    updated_by,
    request_schema_version,
    expected_cycle_version,
    input_snapshot_set_hash,
    started_at,
    calculation_kind,
    attempt_no,
    supersedes_request_id,
    recalculation_reason,
    engine_version
  ) VALUES (
    p_tenant_id,
    p_payroll_cycle_id,
    btrim(p_idempotency_key),
    p_request_hash::char(64),
    'CALCULATING',
    p_recalculated_at,
    p_recalculated_at,
    p_actor,
    p_recalculated_at,
    p_actor,
    1,
    p_expected_version,
    v_snapshot_set_hash,
    p_recalculated_at,
    'RECALCULATION',
    v_attempt_no,
    v_parent.id,
    btrim(p_recalculation_reason),
    'STARTER_FIXED_V1'
  )
  RETURNING id INTO v_request_id;

  FOR v_snapshot IN
    SELECT
      snapshot.id,
      snapshot.payroll_assignment_version_id,
      snapshot.salary_structure_version_id,
      snapshot.snapshot_hash,
      snapshot.snapshot_payload
    FROM payroll_ops.input_snapshot snapshot
    WHERE snapshot.tenant_id = p_tenant_id
      AND snapshot.payroll_cycle_id = p_payroll_cycle_id
    ORDER BY snapshot.payroll_assignment_version_id
  LOOP
    IF (v_snapshot.snapshot_payload #>> '{payGroup,currency}')
         IS DISTINCT FROM 'INR'
       OR (v_snapshot.snapshot_payload
            #>> '{salaryAssignment,currency}')
         IS DISTINCT FROM 'INR'
       OR (v_snapshot.snapshot_payload
            #>> '{salaryStructure,currency}')
         IS DISTINCT FROM 'INR' THEN
      RAISE EXCEPTION
        'starter calculation supports INR snapshots only'
        USING ERRCODE = '23514';
    END IF;

    IF (v_snapshot.snapshot_payload #>> '{payGroup,prorationMethod}')
         IS DISTINCT FROM 'CALENDAR_DAYS' THEN
      RAISE EXCEPTION
        'starter calculation supports CALENDAR_DAYS proration only'
        USING ERRCODE = '23514';
    END IF;

    IF jsonb_typeof(
         v_snapshot.snapshot_payload
           #> '{salaryStructure,lines}'
       ) IS DISTINCT FROM 'array'
       OR jsonb_array_length(
         v_snapshot.snapshot_payload
           #> '{salaryStructure,lines}'
       ) < 1 THEN
      RAISE EXCEPTION
        'input snapshot contains no executable salary-structure lines'
        USING ERRCODE = '23514';
    END IF;

    v_components := '[]'::jsonb;
    v_component_count := 0;
    v_result_gross := 0;
    v_result_deduction := 0;

    FOR v_line IN
      SELECT line.value
      FROM jsonb_array_elements(
        v_snapshot.snapshot_payload
          #> '{salaryStructure,lines}'
      ) AS line(value)
      ORDER BY
        (line.value ->> 'sequenceNo')::integer,
        line.value ->> 'lineId'
    LOOP
      v_sequence_no := (v_line ->> 'sequenceNo')::integer;
      v_formula_type := v_line #>> '{component,formulaType}';
      v_component_type := v_line #>> '{component,componentType}';
      v_component_code := v_line #>> '{component,code}';
      v_rounding_scale := coalesce(
        (v_line #>> '{component,roundingScale}')::integer,
        2
      );

      IF v_formula_type IS DISTINCT FROM 'FIXED' THEN
        RAISE EXCEPTION
          'unsupported starter formula type: %',
          coalesce(v_formula_type, '<null>')
          USING ERRCODE = '23514';
      END IF;

      IF v_component_type IS NULL
         OR v_component_type NOT IN ('EARNING', 'DEDUCTION') THEN
        RAISE EXCEPTION
          'unsupported starter component type: %',
          coalesce(v_component_type, '<null>')
          USING ERRCODE = '23514';
      END IF;

      IF v_component_code IS NULL
         OR v_component_code !~ '^[A-Z][A-Z0-9_]{1,39}$'
         OR v_line ->> 'lineId' IS NULL
         OR v_line #>> '{component,versionId}' IS NULL
         OR v_sequence_no IS NULL
         OR v_sequence_no < 1 THEN
        RAISE EXCEPTION
          'starter component lineage and sequence are required'
          USING ERRCODE = '23514';
      END IF;

      IF v_rounding_scale < 0 OR v_rounding_scale > 4 THEN
        RAISE EXCEPTION
          'component rounding scale must be between 0 and 4'
          USING ERRCODE = '23514';
      END IF;

      IF v_line ->> 'targetPercentage' IS NOT NULL
         OR v_line ->> 'percentageBaseCode' IS NOT NULL THEN
        RAISE EXCEPTION
          'starter calculation does not support percentage salary-structure lines'
          USING ERRCODE = '23514';
      END IF;

      v_source_amount := coalesce(
        nullif(v_line ->> 'targetAmount', '')::numeric,
        nullif(
          v_line #>> '{component,fixedAmount}',
          ''
        )::numeric
      );

      IF v_source_amount IS NULL OR v_source_amount < 0 THEN
        RAISE EXCEPTION
          'fixed component requires a non-negative source amount'
          USING ERRCODE = '23514';
      END IF;

      v_calculated_amount := round(
        v_source_amount,
        v_rounding_scale
      );

      v_component_payload := jsonb_build_object(
        'schemaVersion', 1,
        'sequenceNo', v_sequence_no,
        'salaryStructureLineId', v_line ->> 'lineId',
        'salaryStructureVersionId',
          v_snapshot.salary_structure_version_id::text,
        'componentVersionId',
          v_line #>> '{component,versionId}',
        'componentCode', v_component_code,
        'componentType', v_component_type,
        'formulaType', v_formula_type,
        'sourceAmount', v_source_amount,
        'prorationFactor', 1.0000000000,
        'calculatedAmount', v_calculated_amount,
        'currency', 'INR',
        'roundingScale', v_rounding_scale
      );

      v_component_hash := encode(
        public.digest(
          v_component_payload::text,
          'sha256'::text
        ),
        'hex'
      );

      v_components := v_components || jsonb_build_array(
        v_component_payload || jsonb_build_object(
          'componentHash',
          v_component_hash
        )
      );

      IF v_component_type = 'EARNING' THEN
        v_result_gross := v_result_gross + v_calculated_amount;
      ELSE
        v_result_deduction :=
          v_result_deduction + v_calculated_amount;
      END IF;

      v_component_count := v_component_count + 1;
    END LOOP;

    v_result_gross := round(v_result_gross, 4);
    v_result_deduction := round(v_result_deduction, 4);
    v_result_net := round(
      v_result_gross - v_result_deduction,
      4
    );

    IF v_result_net < 0 THEN
      RAISE EXCEPTION
        'starter calculation does not support negative net pay'
        USING ERRCODE = '23514';
    END IF;

    v_result_payload := jsonb_build_object(
      'schemaVersion', 1,
      'payrollCycleId', p_payroll_cycle_id::text,
      'inputSnapshotId', v_snapshot.id::text,
      'inputSnapshotHash', v_snapshot.snapshot_hash,
      'payrollAssignmentVersionId',
        v_snapshot.payroll_assignment_version_id::text,
      'salaryStructureVersionId',
        v_snapshot.salary_structure_version_id::text,
      'currency', 'INR',
      'grossAmount', v_result_gross,
      'deductionAmount', v_result_deduction,
      'netAmount', v_result_net,
      'components', v_components
    );

    v_result_hash := encode(
      public.digest(
        v_result_payload::text,
        'sha256'::text
      ),
      'hex'
    );

    INSERT INTO payroll_calc.payroll_result(
      tenant_id,
      calculation_request_id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      input_snapshot_id,
      result_hash,
      result_status,
      currency,
      gross_amount,
      deduction_amount,
      net_amount,
      calculated_at,
      created_at,
      created_by,
      updated_at,
      updated_by,
      result_schema_version,
      input_snapshot_hash,
      salary_structure_version_id,
      component_count,
      result_payload
    ) VALUES (
      p_tenant_id,
      v_request_id,
      p_payroll_cycle_id,
      v_snapshot.payroll_assignment_version_id,
      v_snapshot.id,
      v_result_hash,
      'CALCULATED',
      'INR',
      v_result_gross,
      v_result_deduction,
      v_result_net,
      p_recalculated_at,
      p_recalculated_at,
      p_actor,
      p_recalculated_at,
      p_actor,
      1,
      v_snapshot.snapshot_hash,
      v_snapshot.salary_structure_version_id,
      v_component_count,
      v_result_payload
    )
    RETURNING id INTO v_result_id;

    FOR v_component IN
      SELECT component.value
      FROM jsonb_array_elements(v_components)
        AS component(value)
      ORDER BY
        (component.value ->> 'sequenceNo')::integer,
        component.value ->> 'componentVersionId'
    LOOP
      INSERT INTO payroll_calc.component_result(
        tenant_id,
        payroll_result_id,
        component_code,
        sequence_no,
        unprorated_amount,
        proration_factor,
        calculated_amount,
        currency,
        formula_expression,
        created_at,
        created_by,
        updated_at,
        updated_by,
        component_schema_version,
        component_version_id,
        salary_structure_line_id,
        salary_structure_version_id,
        component_type,
        formula_type,
        rounding_scale,
        component_payload,
        component_hash
      ) VALUES (
        p_tenant_id,
        v_result_id,
        v_component ->> 'componentCode',
        (v_component ->> 'sequenceNo')::integer,
        (v_component ->> 'sourceAmount')::numeric,
        1.0000000000,
        (v_component ->> 'calculatedAmount')::numeric,
        'INR',
        NULL,
        p_recalculated_at,
        p_actor,
        p_recalculated_at,
        p_actor,
        1,
        (v_component ->> 'componentVersionId')::uuid,
        (v_component ->> 'salaryStructureLineId')::uuid,
        (v_component ->> 'salaryStructureVersionId')::uuid,
        v_component ->> 'componentType',
        v_component ->> 'formulaType',
        (v_component ->> 'roundingScale')::smallint,
        v_component - 'componentHash',
        (v_component ->> 'componentHash')::char(64)
      )
      RETURNING id INTO v_component_result_id;

      v_trace_payload := jsonb_build_object(
        'schemaVersion', 1,
        'stepNo', (v_component ->> 'sequenceNo')::integer,
        'stepType', 'FIXED_COMPONENT',
        'inputSnapshotId', v_snapshot.id::text,
        'inputSnapshotHash', v_snapshot.snapshot_hash,
        'componentVersionId',
          v_component ->> 'componentVersionId',
        'componentCode', v_component ->> 'componentCode',
        'formulaType', v_component ->> 'formulaType',
        'sourceAmount',
          (v_component ->> 'sourceAmount')::numeric,
        'roundingScale',
          (v_component ->> 'roundingScale')::integer,
        'output',
          (v_component ->> 'calculatedAmount')::numeric
      );

      v_trace_hash := encode(
        public.digest(
          v_trace_payload::text,
          'sha256'::text
        ),
        'hex'
      );

      INSERT INTO payroll_calc.calculation_trace(
        tenant_id,
        payroll_result_id,
        component_result_id,
        step_no,
        step_type,
        inputs,
        expression,
        output_value,
        message,
        created_at,
        created_by,
        trace_schema_version,
        input_snapshot_id,
        component_version_id,
        trace_payload,
        trace_hash
      ) VALUES (
        p_tenant_id,
        v_result_id,
        v_component_result_id,
        (v_component ->> 'sequenceNo')::integer,
        'FIXED_COMPONENT',
        jsonb_build_object(
          'sourceAmount',
            (v_component ->> 'sourceAmount')::numeric,
          'roundingScale',
            (v_component ->> 'roundingScale')::integer,
          'prorationFactor',
            1.0000000000
        ),
        NULL,
        (v_component ->> 'calculatedAmount')::numeric,
        'Fixed monthly non-statutory component',
        p_recalculated_at,
        p_actor,
        1,
        v_snapshot.id,
        (v_component ->> 'componentVersionId')::uuid,
        v_trace_payload,
        v_trace_hash
      );
    END LOOP;

    v_result_count := v_result_count + 1;
    v_gross_total := v_gross_total + v_result_gross;
    v_deduction_total :=
      v_deduction_total + v_result_deduction;
    v_net_total := v_net_total + v_result_net;
  END LOOP;

  IF v_result_count <> v_expected_snapshot_count THEN
    RAISE EXCEPTION
      'calculation result count does not match sealed snapshot count'
      USING ERRCODE = '23514';
  END IF;

  SELECT encode(
      public.digest(
        string_agg(
          result.payroll_assignment_version_id::text
            || ':' || result.result_hash,
          '|'
          ORDER BY result.payroll_assignment_version_id
        ),
        'sha256'::text
      ),
      'hex'
    )
  INTO v_result_set_hash
  FROM payroll_calc.payroll_result result
  WHERE result.tenant_id = p_tenant_id
    AND result.calculation_request_id = v_request_id;

  PERFORM set_config(
    'payroll_calc.calculation_mutation',
    'allowed',
    true
  );

  UPDATE payroll_calc.calculation_request request
  SET status = 'COMPLETED',
      completed_at = p_recalculated_at,
      completed_by = p_actor,
      completed_cycle_version = v_cycle_version + 1,
      result_count = v_result_count,
      gross_total = round(v_gross_total, 4),
      deduction_total = round(v_deduction_total, 4),
      net_total = round(v_net_total, 4),
      result_set_hash = v_result_set_hash,
      updated_at = p_recalculated_at,
      updated_by = p_actor,
      version_no = request.version_no + 1
  WHERE request.tenant_id = p_tenant_id
    AND request.id = v_request_id
    AND request.status = 'CALCULATING';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'recalculation request changed while results were being persisted'
      USING ERRCODE = '40001';
  END IF;

  PERFORM set_config(
    'payroll_ops.population_mutation',
    'allowed',
    true
  );

  UPDATE payroll_ops.payroll_cycle cycle
  SET status = 'CALCULATED',
      control_total = round(v_net_total, 4),
      active_calculation_request_id = v_request_id,
      calculated_at = p_recalculated_at,
      calculated_by = p_actor,
      calculation_result_count = v_result_count,
      calculation_result_set_hash = v_result_set_hash,
      gross_total = round(v_gross_total, 4),
      deduction_total = round(v_deduction_total, 4),
      net_total = round(v_net_total, 4),
      updated_at = p_recalculated_at,
      updated_by = p_actor,
      version_no = cycle.version_no + 1
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.version_no = p_expected_version
    AND cycle.status = 'CALCULATED'
    AND cycle.active_calculation_request_id = v_parent.id;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll cycle changed while recalculation was being completed'
      USING ERRCODE = '40001';
  END IF;

  RETURN QUERY
  SELECT
    v_request_id,
    v_parent.id,
    v_attempt_no,
    v_result_count,
    round(v_gross_total, 4),
    round(v_deduction_total, 4),
    round(v_net_total, 4),
    v_result_set_hash,
    v_cycle_version + 1;
END $$;

REVOKE ALL ON FUNCTION payroll_calc.recalculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION payroll_calc.recalculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

ALTER TABLE payroll_ops.payroll_cycle FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_request FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.payroll_result FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.component_result FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_trace FORCE ROW LEVEL SECURITY;

COMMENT ON COLUMN payroll_calc.calculation_request.calculation_kind IS
  'LEGACY for schema-version-0 history, INITIAL for attempt 1, or RECALCULATION for a linked successor.';

COMMENT ON COLUMN payroll_calc.calculation_request.attempt_no IS
  'Deterministic per-cycle calculation-attempt sequence; schema-version-1 attempts start at 1.';

COMMENT ON COLUMN payroll_calc.calculation_request.supersedes_request_id IS
  'Prior active completed calculation request replaced by this recalculation attempt.';

COMMENT ON COLUMN payroll_calc.calculation_request.recalculation_reason IS
  'Mandatory bounded operator reason for a RECALCULATION attempt.';

COMMENT ON FUNCTION payroll_calc.recalculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Creates a deterministic recalculation attempt from immutable sealed inputs, preserves prior evidence and atomically advances the cycle active request.';
