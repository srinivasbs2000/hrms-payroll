-- S3-03A deterministic starter-calculation database foundation.
--
-- V008 introduced request/result/component/trace persistence. V021 repointed
-- result lineage to exact assignment versions and V024 sealed canonical inputs.
-- This migration upgrades the legacy calculation tables in place, preserves
-- historical rows as schema version 0, and exposes a single controlled,
-- idempotent, tenant-safe calculation command for schema-version-1 snapshots.

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
    FROM payroll_calc.payroll_result result
    GROUP BY result.tenant_id,
             result.calculation_request_id,
             result.input_snapshot_id
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION
      'V025 requires at most one payroll result per request and input snapshot';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request request
    WHERE request.status NOT IN (
      'ACCEPTED',
      'CALCULATING',
      'COMPLETED',
      'FAILED'
    )
  ) THEN
    RAISE EXCEPTION
      'legacy calculation requests contain unsupported statuses';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request request
    WHERE request.status = 'COMPLETED'
      AND NOT EXISTS (
        SELECT 1
        FROM payroll_calc.payroll_result result
        WHERE result.tenant_id = request.tenant_id
          AND result.calculation_request_id = request.id
      )
  ) THEN
    RAISE EXCEPTION
      'completed legacy calculation requests require at least one result';
  END IF;
END $$;

ALTER TABLE payroll_calc.calculation_request
  ADD COLUMN request_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN expected_cycle_version bigint NOT NULL DEFAULT 0,
  ADD COLUMN input_snapshot_set_hash char(64) NOT NULL
    DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',
  ADD COLUMN started_at timestamptz,
  ADD COLUMN completed_at timestamptz,
  ADD COLUMN completed_by varchar(160),
  ADD COLUMN completed_cycle_version bigint,
  ADD COLUMN result_count integer,
  ADD COLUMN gross_total numeric(19,4),
  ADD COLUMN deduction_total numeric(19,4),
  ADD COLUMN net_total numeric(19,4),
  ADD COLUMN result_set_hash char(64);

ALTER TABLE payroll_calc.payroll_result
  ADD COLUMN result_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN input_snapshot_hash char(64),
  ADD COLUMN salary_structure_version_id uuid,
  ADD COLUMN component_count integer,
  ADD COLUMN result_payload jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE payroll_calc.component_result
  ADD COLUMN component_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN component_version_id uuid,
  ADD COLUMN salary_structure_line_id uuid,
  ADD COLUMN salary_structure_version_id uuid,
  ADD COLUMN component_type varchar(20),
  ADD COLUMN formula_type varchar(30),
  ADD COLUMN rounding_scale smallint,
  ADD COLUMN component_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN component_hash char(64);

ALTER TABLE payroll_calc.calculation_trace
  ADD COLUMN trace_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN input_snapshot_id uuid,
  ADD COLUMN component_version_id uuid,
  ADD COLUMN trace_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN trace_hash char(64);

ALTER TABLE payroll_ops.payroll_cycle
  ADD COLUMN active_calculation_request_id uuid,
  ADD COLUMN calculated_at timestamptz,
  ADD COLUMN calculated_by varchar(160),
  ADD COLUMN calculation_result_count integer,
  ADD COLUMN calculation_result_set_hash char(64),
  ADD COLUMN gross_total numeric(19,4),
  ADD COLUMN deduction_total numeric(19,4),
  ADD COLUMN net_total numeric(19,4);

UPDATE payroll_calc.calculation_request request
SET expected_cycle_version = cycle.version_no,
    input_snapshot_set_hash = coalesce(
      cycle.input_snapshot_set_hash,
      '0000000000000000000000000000000000000000000000000000000000000000'
    ),
    started_at = coalesce(request.requested_at, request.created_at)
FROM payroll_ops.payroll_cycle cycle
WHERE cycle.tenant_id = request.tenant_id
  AND cycle.id = request.payroll_cycle_id;

WITH summary AS (
  SELECT
    request.tenant_id,
    request.id AS calculation_request_id,
    count(result.id)::integer AS result_count,
    coalesce(sum(result.gross_amount), 0)::numeric(19,4) AS gross_total,
    coalesce(sum(result.deduction_amount), 0)::numeric(19,4)
      AS deduction_total,
    coalesce(sum(result.net_amount), 0)::numeric(19,4) AS net_total,
    max(result.calculated_at) AS completed_at,
    encode(
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
    ) AS result_set_hash
  FROM payroll_calc.calculation_request request
  JOIN payroll_calc.payroll_result result
    ON result.tenant_id = request.tenant_id
   AND result.calculation_request_id = request.id
  GROUP BY request.tenant_id, request.id
)
UPDATE payroll_calc.calculation_request request
SET status = 'COMPLETED',
    completed_at = summary.completed_at,
    completed_by = request.created_by,
    completed_cycle_version = request.expected_cycle_version,
    result_count = summary.result_count,
    gross_total = summary.gross_total,
    deduction_total = summary.deduction_total,
    net_total = summary.net_total,
    result_set_hash = summary.result_set_hash
FROM summary
WHERE summary.tenant_id = request.tenant_id
  AND summary.calculation_request_id = request.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    WHERE cycle.status = 'CALCULATED'
      AND NOT EXISTS (
        SELECT 1
        FROM payroll_calc.calculation_request request
        WHERE request.tenant_id = cycle.tenant_id
          AND request.payroll_cycle_id = cycle.id
          AND request.status = 'COMPLETED'
      )
  ) THEN
    RAISE EXCEPTION
      'calculated legacy cycles require a completed calculation request before V025';
  END IF;
END $$;

SELECT set_config(
  'payroll_ops.population_mutation',
  'allowed',
  true
);

WITH latest AS (
  SELECT DISTINCT ON (
    request.tenant_id,
    request.payroll_cycle_id
  )
    request.tenant_id,
    request.payroll_cycle_id,
    request.id AS calculation_request_id,
    request.completed_at,
    request.completed_by,
    request.result_count,
    request.result_set_hash,
    request.gross_total,
    request.deduction_total,
    request.net_total
  FROM payroll_calc.calculation_request request
  WHERE request.status = 'COMPLETED'
  ORDER BY
    request.tenant_id,
    request.payroll_cycle_id,
    request.completed_at DESC NULLS LAST,
    request.id DESC
)
UPDATE payroll_ops.payroll_cycle cycle
SET active_calculation_request_id = latest.calculation_request_id,
    calculated_at = latest.completed_at,
    calculated_by = latest.completed_by,
    calculation_result_count = latest.result_count,
    calculation_result_set_hash = latest.result_set_hash,
    gross_total = latest.gross_total,
    deduction_total = latest.deduction_total,
    net_total = latest.net_total
FROM latest
WHERE cycle.tenant_id = latest.tenant_id
  AND cycle.id = latest.payroll_cycle_id
  AND cycle.status = 'CALCULATED';

ALTER TABLE payroll_calc.calculation_request
  ADD CONSTRAINT calculation_request_schema_version_ck
    CHECK (request_schema_version IN (0, 1)),
  ADD CONSTRAINT calculation_request_expected_version_ck
    CHECK (expected_cycle_version >= 0),
  ADD CONSTRAINT calculation_request_request_hash_ck
    CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT calculation_request_snapshot_set_hash_ck
    CHECK (input_snapshot_set_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT calculation_request_status_ck
    CHECK (
      status IN ('ACCEPTED', 'CALCULATING', 'COMPLETED', 'FAILED')
    ),
  ADD CONSTRAINT calculation_request_schema1_shape_ck
    CHECK (
      request_schema_version = 0
      OR (
        started_at IS NOT NULL
        AND (
          (
            status = 'COMPLETED'
            AND completed_at IS NOT NULL
            AND completed_by IS NOT NULL
            AND btrim(completed_by) <> ''
            AND completed_cycle_version IS NOT NULL
            AND completed_cycle_version > expected_cycle_version
            AND result_count IS NOT NULL
            AND result_count > 0
            AND gross_total IS NOT NULL
            AND gross_total >= 0
            AND deduction_total IS NOT NULL
            AND deduction_total >= 0
            AND net_total IS NOT NULL
            AND net_total >= 0
            AND net_total = gross_total - deduction_total
            AND result_set_hash IS NOT NULL
            AND result_set_hash ~ '^[0-9a-f]{64}$'
          )
          OR (
            status <> 'COMPLETED'
            AND completed_at IS NULL
            AND completed_by IS NULL
            AND completed_cycle_version IS NULL
            AND result_count IS NULL
            AND gross_total IS NULL
            AND deduction_total IS NULL
            AND net_total IS NULL
            AND result_set_hash IS NULL
          )
        )
      )
    );

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_calculation_lineage_uk
    UNIQUE (
      tenant_id,
      id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      salary_structure_version_id,
      snapshot_hash
    );

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_schema_version_ck
    CHECK (result_schema_version IN (0, 1)),
  ADD CONSTRAINT payroll_result_schema1_shape_ck
    CHECK (
      result_schema_version = 0
      OR (
        input_snapshot_hash IS NOT NULL
        AND input_snapshot_hash ~ '^[0-9a-f]{64}$'
        AND salary_structure_version_id IS NOT NULL
        AND component_count IS NOT NULL
        AND component_count > 0
        AND result_status = 'CALCULATED'
        AND currency = 'INR'
        AND gross_amount >= 0
        AND deduction_amount >= 0
        AND net_amount >= 0
        AND net_amount = gross_amount - deduction_amount
        AND result_hash ~ '^[0-9a-f]{64}$'
        AND result_hash = encode(
          public.digest(result_payload::text, 'sha256'::text),
          'hex'
        )
      )
    ),
  ADD CONSTRAINT payroll_result_request_snapshot_uk
    UNIQUE (
      tenant_id,
      calculation_request_id,
      input_snapshot_id
    ),
  ADD CONSTRAINT payroll_result_snapshot_lineage_fk
    FOREIGN KEY (
      tenant_id,
      input_snapshot_id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      salary_structure_version_id,
      input_snapshot_hash
    )
    REFERENCES payroll_ops.input_snapshot(
      tenant_id,
      id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      salary_structure_version_id,
      snapshot_hash
    );

ALTER TABLE compensation.salary_structure_line
  ADD CONSTRAINT salary_structure_line_calculation_lineage_uk
    UNIQUE (
      tenant_id,
      id,
      salary_structure_version_id,
      component_version_id
    );

ALTER TABLE payroll_calc.component_result
  ADD CONSTRAINT component_result_schema_version_ck
    CHECK (component_schema_version IN (0, 1)),
  ADD CONSTRAINT component_result_schema1_shape_ck
    CHECK (
      component_schema_version = 0
      OR (
        component_version_id IS NOT NULL
        AND salary_structure_line_id IS NOT NULL
        AND salary_structure_version_id IS NOT NULL
        AND component_type IN ('EARNING', 'DEDUCTION')
        AND formula_type = 'FIXED'
        AND rounding_scale BETWEEN 0 AND 4
        AND unprorated_amount >= 0
        AND proration_factor = 1.0000000000
        AND calculated_amount >= 0
        AND component_hash IS NOT NULL
        AND component_hash ~ '^[0-9a-f]{64}$'
        AND component_hash = encode(
          public.digest(component_payload::text, 'sha256'::text),
          'hex'
        )
      )
    ),
  ADD CONSTRAINT component_result_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id, id),
  ADD CONSTRAINT component_result_structure_line_fk
    FOREIGN KEY (
      tenant_id,
      salary_structure_line_id,
      salary_structure_version_id,
      component_version_id
    )
    REFERENCES compensation.salary_structure_line(
      tenant_id,
      id,
      salary_structure_version_id,
      component_version_id
    );

ALTER TABLE payroll_calc.calculation_trace
  ADD CONSTRAINT calculation_trace_schema_version_ck
    CHECK (trace_schema_version IN (0, 1)),
  ADD CONSTRAINT calculation_trace_schema1_shape_ck
    CHECK (
      trace_schema_version = 0
      OR (
        input_snapshot_id IS NOT NULL
        AND component_version_id IS NOT NULL
        AND trace_hash IS NOT NULL
        AND trace_hash ~ '^[0-9a-f]{64}$'
        AND trace_hash = encode(
          public.digest(trace_payload::text, 'sha256'::text),
          'hex'
        )
      )
    ),
  ADD CONSTRAINT calculation_trace_input_snapshot_fk
    FOREIGN KEY (tenant_id, input_snapshot_id)
    REFERENCES payroll_ops.input_snapshot(tenant_id, id),
  ADD CONSTRAINT calculation_trace_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id, id);

ALTER TABLE payroll_ops.payroll_cycle
  ADD CONSTRAINT payroll_cycle_calculation_shape_ck
    CHECK (
      (
        active_calculation_request_id IS NULL
        AND calculated_at IS NULL
        AND calculated_by IS NULL
        AND calculation_result_count IS NULL
        AND calculation_result_set_hash IS NULL
        AND gross_total IS NULL
        AND deduction_total IS NULL
        AND net_total IS NULL
      )
      OR (
        active_calculation_request_id IS NOT NULL
        AND calculated_at IS NOT NULL
        AND calculated_by IS NOT NULL
        AND btrim(calculated_by) <> ''
        AND calculation_result_count IS NOT NULL
        AND calculation_result_count > 0
        AND calculation_result_set_hash IS NOT NULL
        AND calculation_result_set_hash ~ '^[0-9a-f]{64}$'
        AND gross_total IS NOT NULL
        AND gross_total >= 0
        AND deduction_total IS NOT NULL
        AND deduction_total >= 0
        AND net_total IS NOT NULL
        AND net_total >= 0
        AND net_total = gross_total - deduction_total
      )
    ),
  ADD CONSTRAINT payroll_cycle_calculated_state_ck
    CHECK (
      status <> 'CALCULATED'
      OR active_calculation_request_id IS NOT NULL
    ),
  ADD CONSTRAINT payroll_cycle_active_calculation_fk
    FOREIGN KEY (
      tenant_id,
      active_calculation_request_id,
      id
    )
    REFERENCES payroll_calc.calculation_request(
      tenant_id,
      id,
      payroll_cycle_id
    );

CREATE OR REPLACE FUNCTION
  payroll_calc.reject_uncontrolled_calculation_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting(
       'payroll_calc.calculation_mutation',
       true
     ) IS DISTINCT FROM 'allowed' THEN
    RAISE EXCEPTION
      'calculation records may change only through controlled commands'
      USING ERRCODE = '42501';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER calculation_request_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON payroll_calc.calculation_request
  FOR EACH ROW
  EXECUTE FUNCTION
    payroll_calc.reject_uncontrolled_calculation_mutation();

CREATE INDEX calculation_request_cycle_status_ix
  ON payroll_calc.calculation_request(
    tenant_id,
    payroll_cycle_id,
    status,
    requested_at DESC
  );

CREATE UNIQUE INDEX calculation_request_one_completed_cycle_uk
  ON payroll_calc.calculation_request(
    tenant_id,
    payroll_cycle_id
  )
  WHERE request_schema_version = 1
    AND status = 'COMPLETED';

CREATE INDEX payroll_result_snapshot_hash_ix
  ON payroll_calc.payroll_result(
    tenant_id,
    input_snapshot_id,
    input_snapshot_hash
  );

CREATE INDEX calculation_trace_snapshot_ix
  ON payroll_calc.calculation_trace(
    tenant_id,
    input_snapshot_id,
    step_no
  );

CREATE OR REPLACE FUNCTION payroll_calc.calculate_sealed_payroll(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_expected_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_actor varchar,
  p_calculated_at timestamptz
) RETURNS TABLE (
  calculation_request_id uuid,
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
  v_cycle_status payroll_ops.cycle_status;
  v_cycle_version bigint;
  v_cycle_type varchar(20);
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

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_calculated_at IS NULL THEN
    RAISE EXCEPTION 'calculation timestamp is required'
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
       OR v_existing.request_hash <> p_request_hash::char(64) THEN
      RAISE EXCEPTION
        'idempotency key was already used with a different calculation request'
        USING ERRCODE = '23505';
    END IF;

    IF v_existing.request_schema_version = 1
       AND v_existing.status = 'COMPLETED' THEN
      RETURN QUERY
      SELECT
        v_existing.id,
        v_existing.result_count,
        v_existing.gross_total,
        v_existing.deduction_total,
        v_existing.net_total,
        v_existing.result_set_hash,
        v_existing.completed_cycle_version;
      RETURN;
    END IF;

    RAISE EXCEPTION 'calculation request is already in progress'
      USING ERRCODE = '40001';
  END IF;

  SELECT
    cycle.status,
    cycle.version_no,
    cycle.cycle_type,
    cycle.input_snapshot_count,
    cycle.input_snapshot_set_hash,
    cycle.input_sealed_at
  INTO
    v_cycle_status,
    v_cycle_version,
    v_cycle_type,
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
     OR v_cycle_status <> 'INPUTS_SEALED' THEN
    RAISE EXCEPTION
      'starter calculation requires an input-sealed regular payroll cycle'
      USING ERRCODE = '23514';
  END IF;

  IF v_expected_snapshot_count IS NULL
     OR v_expected_snapshot_count < 1
     OR v_snapshot_set_hash IS NULL
     OR v_input_sealed_at IS NULL THEN
    RAISE EXCEPTION 'payroll cycle lacks complete input-seal metadata'
      USING ERRCODE = '23514';
  END IF;

  IF p_calculated_at < v_input_sealed_at THEN
    RAISE EXCEPTION
      'calculation timestamp cannot precede input sealing'
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
      'starter calculation requires canonical schema-version-1 input snapshots'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request request
    WHERE request.tenant_id = p_tenant_id
      AND request.payroll_cycle_id = p_payroll_cycle_id
      AND request.request_schema_version = 1
      AND request.status IN ('CALCULATING', 'COMPLETED')
  ) THEN
    RAISE EXCEPTION
      'payroll cycle already has an active or completed calculation'
      USING ERRCODE = '23505';
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
    started_at
  ) VALUES (
    p_tenant_id,
    p_payroll_cycle_id,
    btrim(p_idempotency_key),
    p_request_hash::char(64),
    'CALCULATING',
    p_calculated_at,
    p_calculated_at,
    p_actor,
    p_calculated_at,
    p_actor,
    1,
    p_expected_version,
    v_snapshot_set_hash,
    p_calculated_at
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
      p_calculated_at,
      p_calculated_at,
      p_actor,
      p_calculated_at,
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
        p_calculated_at,
        p_actor,
        p_calculated_at,
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
        p_calculated_at,
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
      completed_at = p_calculated_at,
      completed_by = p_actor,
      completed_cycle_version = v_cycle_version + 1,
      result_count = v_result_count,
      gross_total = round(v_gross_total, 4),
      deduction_total = round(v_deduction_total, 4),
      net_total = round(v_net_total, 4),
      result_set_hash = v_result_set_hash,
      updated_at = p_calculated_at,
      updated_by = p_actor,
      version_no = request.version_no + 1
  WHERE request.tenant_id = p_tenant_id
    AND request.id = v_request_id
    AND request.status = 'CALCULATING';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'calculation request changed while results were being persisted'
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
      calculated_at = p_calculated_at,
      calculated_by = p_actor,
      calculation_result_count = v_result_count,
      calculation_result_set_hash = v_result_set_hash,
      gross_total = round(v_gross_total, 4),
      deduction_total = round(v_deduction_total, 4),
      net_total = round(v_net_total, 4),
      updated_at = p_calculated_at,
      updated_by = p_actor,
      version_no = cycle.version_no + 1
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.version_no = p_expected_version
    AND cycle.status = 'INPUTS_SEALED';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll cycle changed while calculation was being completed'
      USING ERRCODE = '40001';
  END IF;

  RETURN QUERY
  SELECT
    v_request_id,
    v_result_count,
    round(v_gross_total, 4),
    round(v_deduction_total, 4),
    round(v_net_total, 4),
    v_result_set_hash,
    v_cycle_version + 1;
END $$;

REVOKE ALL ON FUNCTION payroll_calc.calculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION payroll_calc.calculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

GRANT SELECT
  ON payroll_calc.calculation_request,
     payroll_calc.payroll_result,
     payroll_calc.component_result,
     payroll_calc.calculation_trace
  TO payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON payroll_calc.calculation_request,
     payroll_calc.payroll_result,
     payroll_calc.component_result,
     payroll_calc.calculation_trace
  FROM payroll_app;

REVOKE CREATE ON SCHEMA payroll_calc FROM payroll_app;

ALTER TABLE payroll_ops.payroll_cycle FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_request FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.payroll_result FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.component_result FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_trace FORCE ROW LEVEL SECURITY;

COMMENT ON FUNCTION payroll_calc.calculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Idempotently calculates one deterministic fixed monthly non-statutory result per schema-version-1 sealed input snapshot, writes immutable component and trace evidence, and atomically advances the cycle to CALCULATED.';
