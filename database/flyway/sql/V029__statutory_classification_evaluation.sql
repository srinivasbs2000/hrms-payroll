-- S4-03 deterministic statutory classification, snapshot and evaluation evidence.
--
-- V029 maps exact pay-component versions into jurisdiction/authority assessment
-- bases and evaluates the exact V025/V026 active payroll calculation request.
-- Existing payroll results remain immutable. Statutory employee deductions,
-- employer liabilities and post-statutory net values are persisted separately as
-- immutable evidence. Conditional eligibility and non-NONE exemptions fail
-- closed until a later jurisdiction-specific resolver supplies their semantics.

CREATE TABLE statutory.statutory_component_classification (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  assessment_base_code varchar(60) NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  classification_sequence integer NOT NULL,
  inclusion_percent numeric(12,8) NOT NULL DEFAULT 100,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_classification_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    jurisdiction_code,
    authority_code,
    assessment_base_code,
    component_id
  ),
  UNIQUE (
    tenant_id,
    jurisdiction_code,
    authority_code,
    assessment_base_code,
    component_id,
    classification_sequence
  ),
  CHECK (jurisdiction_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (assessment_base_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (classification_sequence > 0),
  CHECK (inclusion_percent > 0 AND inclusion_percent <= 100),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  CHECK (
    supersedes_classification_id IS NULL
    OR supersedes_classification_id <> id
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CONSTRAINT statutory_component_classification_component_fk
    FOREIGN KEY (tenant_id, component_id)
    REFERENCES compensation.pay_component(tenant_id, id),
  CONSTRAINT statutory_component_classification_version_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(
      tenant_id,
      id,
      component_id
    ),
  CONSTRAINT statutory_component_classification_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_classification_id,
      jurisdiction_code,
      authority_code,
      assessment_base_code,
      component_id
    ) REFERENCES statutory.statutory_component_classification(
      tenant_id,
      id,
      jurisdiction_code,
      authority_code,
      assessment_base_code,
      component_id
    )
);

ALTER TABLE statutory.statutory_component_classification
  ADD CONSTRAINT statutory_component_classification_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    jurisdiction_code WITH =,
    authority_code WITH =,
    assessment_base_code WITH =,
    component_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX statutory_component_classification_one_successor_uk
  ON statutory.statutory_component_classification(
    tenant_id,
    supersedes_classification_id
  )
  WHERE supersedes_classification_id IS NOT NULL;

CREATE INDEX statutory_component_classification_lookup_ix
  ON statutory.statutory_component_classification(
    tenant_id,
    jurisdiction_code,
    authority_code,
    assessment_base_code,
    component_version_id,
    effective_from,
    effective_to
  );

CREATE TABLE statutory.statutory_evaluation_request (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  calculation_request_id uuid NOT NULL,
  idempotency_key varchar(120) NOT NULL,
  request_hash char(64) NOT NULL,
  request_schema_version smallint NOT NULL DEFAULT 1,
  engine_version varchar(40) NOT NULL DEFAULT 'STATUTORY_NEUTRAL_V1',
  expected_cycle_version bigint NOT NULL,
  calculation_result_set_hash char(64) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'EVALUATING',
  started_at timestamptz NOT NULL,
  completed_at timestamptz,
  completed_by varchar(160),
  payroll_result_count integer,
  statutory_result_count integer,
  employee_total numeric(19,4),
  employer_total numeric(19,4),
  post_statutory_net_total numeric(19,4),
  evidence_set_hash char(64),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    calculation_request_id,
    payroll_cycle_id
  ),
  UNIQUE (tenant_id, idempotency_key),
  UNIQUE (tenant_id, calculation_request_id),
  CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  CHECK (request_schema_version = 1),
  CHECK (engine_version = 'STATUTORY_NEUTRAL_V1'),
  CHECK (expected_cycle_version >= 0),
  CHECK (calculation_result_set_hash ~ '^[0-9a-f]{64}$'),
  CHECK (status IN ('EVALUATING', 'COMPLETED', 'FAILED')),
  CHECK (
    (
      status = 'COMPLETED'
      AND completed_at IS NOT NULL
      AND completed_by IS NOT NULL
      AND btrim(completed_by) <> ''
      AND payroll_result_count IS NOT NULL
      AND payroll_result_count > 0
      AND statutory_result_count IS NOT NULL
      AND statutory_result_count >= 0
      AND employee_total IS NOT NULL
      AND employee_total >= 0
      AND employer_total IS NOT NULL
      AND employer_total >= 0
      AND post_statutory_net_total IS NOT NULL
      AND post_statutory_net_total >= 0
      AND evidence_set_hash IS NOT NULL
      AND evidence_set_hash ~ '^[0-9a-f]{64}$'
    )
    OR (
      status <> 'COMPLETED'
      AND completed_at IS NULL
      AND completed_by IS NULL
      AND payroll_result_count IS NULL
      AND statutory_result_count IS NULL
      AND employee_total IS NULL
      AND employer_total IS NULL
      AND post_statutory_net_total IS NULL
      AND evidence_set_hash IS NULL
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CONSTRAINT statutory_evaluation_request_cycle_fk
    FOREIGN KEY (tenant_id, payroll_cycle_id)
    REFERENCES payroll_ops.payroll_cycle(tenant_id, id),
  CONSTRAINT statutory_evaluation_request_calculation_fk
    FOREIGN KEY (
      tenant_id,
      calculation_request_id,
      payroll_cycle_id
    ) REFERENCES payroll_calc.calculation_request(
      tenant_id,
      id,
      payroll_cycle_id
    )
);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_statutory_summary_uk
  UNIQUE (
    tenant_id,
    id,
    calculation_request_id,
    payroll_cycle_id
  ),
  ADD CONSTRAINT payroll_result_statutory_lineage_uk
  UNIQUE (
    tenant_id,
    id,
    calculation_request_id,
    payroll_cycle_id,
    payroll_assignment_version_id,
    input_snapshot_id,
    result_hash
  );

CREATE TABLE statutory.statutory_input_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  calculation_request_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  payroll_result_id uuid NOT NULL,
  payroll_result_hash char(64) NOT NULL,
  input_snapshot_id uuid NOT NULL,
  input_snapshot_hash char(64) NOT NULL,
  payroll_assignment_id uuid NOT NULL,
  payroll_assignment_version_id uuid NOT NULL,
  employee_statutory_profile_id uuid NOT NULL,
  employee_statutory_profile_version_id uuid NOT NULL,
  employee_statutory_rule_assignment_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  snapshot_schema_version smallint NOT NULL DEFAULT 1,
  snapshot_payload jsonb NOT NULL,
  snapshot_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    evaluation_request_id,
    payroll_result_id,
    employee_statutory_rule_assignment_id,
    statutory_rule_version_id
  ),
  UNIQUE (
    tenant_id,
    evaluation_request_id,
    payroll_result_id,
    employee_statutory_rule_assignment_id
  ),
  CHECK (payroll_result_hash ~ '^[0-9a-f]{64}$'),
  CHECK (input_snapshot_hash ~ '^[0-9a-f]{64}$'),
  CHECK (snapshot_schema_version = 1),
  CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    snapshot_hash = encode(
      public.digest(snapshot_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_input_snapshot_request_fk
    FOREIGN KEY (
      tenant_id,
      evaluation_request_id,
      calculation_request_id,
      payroll_cycle_id
    ) REFERENCES statutory.statutory_evaluation_request(
      tenant_id,
      id,
      calculation_request_id,
      payroll_cycle_id
    ),
  CONSTRAINT statutory_input_snapshot_payroll_result_fk
    FOREIGN KEY (
      tenant_id,
      payroll_result_id,
      calculation_request_id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      input_snapshot_id,
      payroll_result_hash
    ) REFERENCES payroll_calc.payroll_result(
      tenant_id,
      id,
      calculation_request_id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      input_snapshot_id,
      result_hash
    ),
  CONSTRAINT statutory_input_snapshot_assignment_version_fk
    FOREIGN KEY (
      tenant_id,
      payroll_assignment_version_id,
      payroll_assignment_id
    ) REFERENCES employee_payroll.payroll_assignment_version(
      tenant_id,
      id,
      payroll_assignment_id
    ),
  CONSTRAINT statutory_input_snapshot_profile_version_fk
    FOREIGN KEY (
      tenant_id,
      employee_statutory_profile_version_id,
      employee_statutory_profile_id
    ) REFERENCES statutory.employee_statutory_profile_version(
      tenant_id,
      id,
      employee_statutory_profile_id
    ),
  CONSTRAINT statutory_input_snapshot_rule_assignment_fk
    FOREIGN KEY (
      tenant_id,
      employee_statutory_rule_assignment_id,
      employee_statutory_profile_id,
      payroll_assignment_id,
      statutory_rule_id
    ) REFERENCES statutory.employee_statutory_rule_assignment(
      tenant_id,
      id,
      employee_statutory_profile_id,
      payroll_assignment_id,
      statutory_rule_id
    ),
  CONSTRAINT statutory_input_snapshot_rule_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    ) REFERENCES statutory.statutory_rule_version(
      tenant_id,
      id,
      statutory_rule_id
    )
);

CREATE TABLE statutory.statutory_result (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  statutory_input_snapshot_id uuid NOT NULL,
  payroll_result_id uuid NOT NULL,
  employee_statutory_rule_assignment_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  currency platform.currency_code NOT NULL,
  employee_amount numeric(19,4) NOT NULL,
  employer_amount numeric(19,4) NOT NULL,
  result_schema_version smallint NOT NULL DEFAULT 1,
  result_payload jsonb NOT NULL,
  result_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, statutory_rule_version_id),
  UNIQUE (
    tenant_id,
    id,
    statutory_input_snapshot_id,
    statutory_rule_version_id
  ),
  UNIQUE (tenant_id, statutory_input_snapshot_id),
  CHECK (employee_amount >= 0),
  CHECK (employer_amount >= 0),
  CHECK (result_schema_version = 1),
  CHECK (result_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    result_hash = encode(
      public.digest(result_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_result_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      statutory_input_snapshot_id,
      evaluation_request_id,
      payroll_result_id,
      employee_statutory_rule_assignment_id,
      statutory_rule_version_id
    ) REFERENCES statutory.statutory_input_snapshot(
      tenant_id,
      id,
      evaluation_request_id,
      payroll_result_id,
      employee_statutory_rule_assignment_id,
      statutory_rule_version_id
    )
);

CREATE TABLE statutory.statutory_portion_result (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  statutory_result_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  statutory_rule_portion_id uuid NOT NULL,
  sequence_no integer NOT NULL,
  liable_party varchar(20) NOT NULL,
  calculation_method varchar(20) NOT NULL,
  assessment_base_code varchar(60),
  assessment_base_amount numeric(19,4) NOT NULL,
  calculated_amount numeric(19,4) NOT NULL,
  result_schema_version smallint NOT NULL DEFAULT 1,
  result_payload jsonb NOT NULL,
  result_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, statutory_result_id, sequence_no),
  UNIQUE (
    tenant_id,
    statutory_result_id,
    statutory_rule_portion_id
  ),
  CHECK (sequence_no > 0),
  CHECK (liable_party IN ('EMPLOYEE', 'EMPLOYER')),
  CHECK (calculation_method IN ('FIXED', 'PERCENTAGE', 'SLAB')),
  CHECK (
    assessment_base_code IS NULL
    OR assessment_base_code ~ '^[A-Z][A-Z0-9_]{1,59}$'
  ),
  CHECK (assessment_base_amount >= 0),
  CHECK (calculated_amount >= 0),
  CHECK (result_schema_version = 1),
  CHECK (result_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    result_hash = encode(
      public.digest(result_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_portion_result_result_fk
    FOREIGN KEY (
      tenant_id,
      statutory_result_id,
      statutory_rule_version_id
    ) REFERENCES statutory.statutory_result(
      tenant_id,
      id,
      statutory_rule_version_id
    ),
  CONSTRAINT statutory_portion_result_portion_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_portion_id,
      statutory_rule_version_id
    ) REFERENCES statutory.statutory_rule_portion(
      tenant_id,
      id,
      statutory_rule_version_id
    )
);

CREATE TABLE statutory.payroll_statutory_summary (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  calculation_request_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  payroll_result_id uuid NOT NULL,
  currency platform.currency_code NOT NULL,
  nonstatutory_net_amount numeric(19,4) NOT NULL,
  employee_statutory_amount numeric(19,4) NOT NULL,
  employer_statutory_amount numeric(19,4) NOT NULL,
  post_statutory_net_amount numeric(19,4) NOT NULL,
  result_count integer NOT NULL,
  summary_schema_version smallint NOT NULL DEFAULT 1,
  summary_payload jsonb NOT NULL,
  summary_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, evaluation_request_id, payroll_result_id),
  CHECK (nonstatutory_net_amount >= 0),
  CHECK (employee_statutory_amount >= 0),
  CHECK (employer_statutory_amount >= 0),
  CHECK (post_statutory_net_amount >= 0),
  CHECK (
    post_statutory_net_amount =
      nonstatutory_net_amount - employee_statutory_amount
  ),
  CHECK (result_count >= 0),
  CHECK (summary_schema_version = 1),
  CHECK (summary_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    summary_hash = encode(
      public.digest(summary_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT payroll_statutory_summary_request_fk
    FOREIGN KEY (
      tenant_id,
      evaluation_request_id,
      calculation_request_id,
      payroll_cycle_id
    ) REFERENCES statutory.statutory_evaluation_request(
      tenant_id,
      id,
      calculation_request_id,
      payroll_cycle_id
    ),
  CONSTRAINT payroll_statutory_summary_result_fk
    FOREIGN KEY (
      tenant_id,
      payroll_result_id,
      calculation_request_id,
      payroll_cycle_id
    ) REFERENCES payroll_calc.payroll_result(
      tenant_id,
      id,
      calculation_request_id,
      payroll_cycle_id
    )
);

CREATE INDEX statutory_evaluation_request_cycle_ix
  ON statutory.statutory_evaluation_request(
    tenant_id,
    payroll_cycle_id,
    calculation_request_id,
    status
  );

CREATE INDEX statutory_input_snapshot_result_ix
  ON statutory.statutory_input_snapshot(
    tenant_id,
    payroll_result_id,
    statutory_rule_version_id
  );

CREATE INDEX statutory_result_request_ix
  ON statutory.statutory_result(
    tenant_id,
    evaluation_request_id,
    payroll_result_id
  );

CREATE INDEX statutory_portion_result_rule_ix
  ON statutory.statutory_portion_result(
    tenant_id,
    statutory_rule_version_id,
    statutory_rule_portion_id
  );

CREATE INDEX payroll_statutory_summary_request_ix
  ON statutory.payroll_statutory_summary(
    tenant_id,
    evaluation_request_id,
    payroll_result_id
  );

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_component_classification_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, compensation, platform AS $$
DECLARE
  component_version_status varchar(20);
  component_from date;
  component_to date;
  parent_sequence integer;
BEGIN
  SELECT
    version.approval_status,
    version.effective_from,
    version.effective_to
  INTO
    component_version_status,
    component_from,
    component_to
  FROM compensation.pay_component component
  JOIN compensation.pay_component_version version
    ON version.tenant_id = component.tenant_id
   AND version.component_id = component.id
  WHERE component.tenant_id = NEW.tenant_id
    AND component.id = NEW.component_id
    AND version.id = NEW.component_version_id
  FOR UPDATE OF version;

  IF component_version_status IS NULL THEN
    RAISE EXCEPTION
      'component classification requires an exact pay-component version'
      USING ERRCODE = '23503';
  END IF;

  IF component_version_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'component classification requires an approved component version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < component_from
     OR (
       component_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > component_to
       )
     ) THEN
    RAISE EXCEPTION
      'component classification must remain within its exact component-version range'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.approval_status <> 'DRAFT'
       OR NEW.approved_at IS NOT NULL
       OR NEW.approved_by IS NOT NULL
       OR NEW.version_no <> 0 THEN
      RAISE EXCEPTION
        'component classifications must be inserted as new drafts'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.classification_sequence = 1 THEN
      IF NEW.supersedes_classification_id IS NOT NULL
         OR EXISTS (
           SELECT 1
           FROM statutory.statutory_component_classification existing
           WHERE existing.tenant_id = NEW.tenant_id
             AND existing.jurisdiction_code = NEW.jurisdiction_code
             AND existing.authority_code = NEW.authority_code
             AND existing.assessment_base_code = NEW.assessment_base_code
             AND existing.component_id = NEW.component_id
         ) THEN
        RAISE EXCEPTION
          'first component classification must start a new chain'
          USING ERRCODE = '23514';
      END IF;
    ELSE
      IF NEW.supersedes_classification_id IS NULL THEN
        RAISE EXCEPTION
          'later component classifications must supersede the prior classification'
          USING ERRCODE = '23514';
      END IF;

      SELECT parent.classification_sequence
      INTO parent_sequence
      FROM statutory.statutory_component_classification parent
      WHERE parent.tenant_id = NEW.tenant_id
        AND parent.id = NEW.supersedes_classification_id
        AND parent.jurisdiction_code = NEW.jurisdiction_code
        AND parent.authority_code = NEW.authority_code
        AND parent.assessment_base_code = NEW.assessment_base_code
        AND parent.component_id = NEW.component_id
      FOR UPDATE OF parent;

      IF parent_sequence IS NULL THEN
        RAISE EXCEPTION
          'superseded component classification does not exist in the current chain'
          USING ERRCODE = '23503';
      END IF;

      IF NEW.classification_sequence <> parent_sequence + 1 THEN
        RAISE EXCEPTION
          'component classification sequence must follow its parent'
          USING ERRCODE = '23514';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_component_classification_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    jurisdiction_code,
    authority_code,
    assessment_base_code,
    component_id,
    component_version_id,
    classification_sequence,
    effective_from,
    effective_to,
    supersedes_classification_id
  ON statutory.statutory_component_classification
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.assert_statutory_component_classification_dependencies();

REVOKE ALL ON FUNCTION
  statutory.assert_statutory_component_classification_dependencies()
  FROM PUBLIC;

CREATE TRIGGER statutory_component_classification_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_component_classification
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE OR REPLACE FUNCTION
  statutory.reject_uncontrolled_statutory_evaluation_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting(
       'statutory.evaluation_mutation',
       true
     ) IS DISTINCT FROM 'allowed' THEN
    RAISE EXCEPTION
      'statutory evaluation requests may change only through controlled commands'
      USING ERRCODE = '42501';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER statutory_evaluation_request_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_evaluation_request
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_evaluation_mutation();

REVOKE ALL ON FUNCTION
  statutory.reject_uncontrolled_statutory_evaluation_mutation()
  FROM PUBLIC;

CREATE TRIGGER statutory_input_snapshot_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_input_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER statutory_result_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_result
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER statutory_portion_result_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_portion_result
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER payroll_statutory_summary_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.payroll_statutory_summary
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE OR REPLACE FUNCTION
  statutory.guard_component_version_end_date_for_classifications()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, compensation, platform AS $$
BEGIN
  IF NEW.effective_to IS NOT DISTINCT FROM OLD.effective_to
     OR NEW.effective_to IS NULL THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_component_classification classification
    WHERE classification.tenant_id = OLD.tenant_id
      AND classification.component_version_id = OLD.id
      AND classification.approval_status = 'APPROVED'
      AND (
        classification.effective_to IS NULL
        OR classification.effective_to > NEW.effective_to
      )
  ) THEN
    RAISE EXCEPTION
      'component version cannot end before an approved statutory classification'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_component_version_statutory_classification_guard
  BEFORE UPDATE OF effective_to
  ON compensation.pay_component_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.guard_component_version_end_date_for_classifications();

REVOKE ALL ON FUNCTION
  statutory.guard_component_version_end_date_for_classifications()
  FROM PUBLIC;

CREATE OR REPLACE FUNCTION
  statutory.approve_statutory_component_classification(
    p_tenant_id uuid,
    p_classification_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_approved_at IS NULL THEN
    RAISE EXCEPTION 'approval timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  PERFORM 1
  FROM statutory.statutory_component_classification classification
  JOIN compensation.pay_component_version component_version
    ON component_version.tenant_id = classification.tenant_id
   AND component_version.id = classification.component_version_id
   AND component_version.component_id = classification.component_id
  WHERE classification.tenant_id = p_tenant_id
    AND classification.id = p_classification_id
    AND classification.approval_status = 'DRAFT'
    AND component_version.approval_status = 'APPROVED'
    AND classification.effective_from >= component_version.effective_from
    AND (
      component_version.effective_to IS NULL
      OR classification.effective_to IS NOT NULL
         AND classification.effective_to <= component_version.effective_to
    )
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_component_classification successor
      WHERE successor.tenant_id = classification.tenant_id
        AND successor.supersedes_classification_id = classification.id
    )
  FOR UPDATE OF classification;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_component_classification classification
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = classification.version_no + 1
  WHERE classification.tenant_id = p_tenant_id
    AND classification.id = p_classification_id
    AND classification.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_component_classification successor
      WHERE successor.tenant_id = classification.tenant_id
        AND successor.supersedes_classification_id = classification.id
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  statutory.end_date_statutory_component_classification(
    p_tenant_id uuid,
    p_classification_id uuid,
    p_effective_to date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_effective_to IS NULL THEN
    RAISE EXCEPTION 'effective-to date is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_changed_at IS NULL THEN
    RAISE EXCEPTION 'change timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_component_classification classification
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = classification.version_no + 1
  WHERE classification.tenant_id = p_tenant_id
    AND classification.id = p_classification_id
    AND classification.approval_status = 'APPROVED'
    AND classification.version_no = p_expected_version
    AND classification.effective_from < p_effective_to
    AND (
      classification.effective_to IS NULL
      OR classification.effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.round_statutory_amount(
  p_amount numeric,
  p_scale smallint,
  p_mode varchar
) RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
STRICT AS $$
DECLARE
  factor numeric;
  scaled numeric;
  lower_value numeric;
  fraction numeric;
  rounded_scaled numeric;
BEGIN
  IF p_amount < 0 THEN
    RAISE EXCEPTION 'statutory rounding supports non-negative amounts only'
      USING ERRCODE = '23514';
  END IF;

  IF p_scale < 0 OR p_scale > 4 THEN
    RAISE EXCEPTION 'statutory rounding scale must be between zero and four'
      USING ERRCODE = '23514';
  END IF;

  factor := power(10::numeric, p_scale);
  scaled := p_amount * factor;
  lower_value := floor(scaled);
  fraction := scaled - lower_value;

  IF p_mode = 'DOWN' THEN
    rounded_scaled := lower_value;
  ELSIF p_mode = 'UP' THEN
    rounded_scaled := ceil(scaled);
  ELSIF p_mode = 'HALF_UP' THEN
    rounded_scaled := floor(scaled + 0.5);
  ELSIF p_mode = 'HALF_EVEN' THEN
    IF fraction < 0.5 THEN
      rounded_scaled := lower_value;
    ELSIF fraction > 0.5 THEN
      rounded_scaled := lower_value + 1;
    ELSIF mod(lower_value, 2) = 0 THEN
      rounded_scaled := lower_value;
    ELSE
      rounded_scaled := lower_value + 1;
    END IF;
  ELSE
    RAISE EXCEPTION 'unsupported statutory rounding mode: %', p_mode
      USING ERRCODE = '23514';
  END IF;

  RETURN rounded_scaled / factor;
END $$;

REVOKE ALL ON FUNCTION statutory.round_statutory_amount(
  numeric,
  smallint,
  varchar
) FROM PUBLIC;

CREATE OR REPLACE FUNCTION statutory.evaluate_calculated_payroll(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_calculation_request_id uuid,
  p_expected_cycle_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_actor varchar,
  p_evaluated_at timestamptz
) RETURNS TABLE (
  evaluation_request_id uuid,
  payroll_result_count integer,
  statutory_result_count integer,
  employee_total numeric(19,4),
  employer_total numeric(19,4),
  post_statutory_net_total numeric(19,4),
  evidence_set_hash char(64)
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  statutory,
  payroll_calc,
  payroll_ops,
  compensation,
  employee_payroll,
  organisation,
  platform AS $$
DECLARE
  existing_request statutory.statutory_evaluation_request%ROWTYPE;
  cycle_status payroll_ops.cycle_status;
  cycle_version bigint;
  active_calculation_request_id uuid;
  calculation_status varchar(20);
  calculation_schema_version smallint;
  calculation_result_count integer;
  calculation_result_set_hash char(64);
  calculation_net_total numeric(19,4);
  request_id uuid;
  actual_payroll_result_count integer := 0;
  actual_statutory_result_count integer := 0;
  total_employee numeric(19,4) := 0;
  total_employer numeric(19,4) := 0;
  total_post_net numeric(19,4) := 0;
  final_evidence_set_hash char(64);
  payroll_result_row record;
  assignment_row record;
  portion_row record;
  slab_row record;
  period_start date;
  period_end date;
  statutory_snapshot_id uuid;
  statutory_result_id uuid;
  snapshot_payload jsonb;
  snapshot_hash char(64);
  portion_results jsonb;
  portion_payload jsonb;
  portion_hash char(64);
  result_payload jsonb;
  result_hash char(64);
  summary_payload jsonb;
  summary_hash char(64);
  base_amount numeric(19,4);
  capped_base numeric(19,4);
  assessable_base numeric(19,4);
  band_width numeric(19,4);
  raw_amount numeric(19,8);
  rounded_amount numeric(19,4);
  rule_employee numeric(19,4);
  rule_employer numeric(19,4);
  payroll_employee numeric(19,4);
  payroll_employer numeric(19,4);
  payroll_statutory_count integer;
  post_net numeric(19,4);
  portion_json jsonb;
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

  IF p_evaluated_at IS NULL THEN
    RAISE EXCEPTION 'evaluation timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT request.*
  INTO existing_request
  FROM statutory.statutory_evaluation_request request
  WHERE request.tenant_id = p_tenant_id
    AND request.idempotency_key = btrim(p_idempotency_key)
  FOR UPDATE;

  IF FOUND THEN
    IF existing_request.payroll_cycle_id <> p_payroll_cycle_id
       OR existing_request.calculation_request_id <>
          p_calculation_request_id
       OR existing_request.request_hash <> p_request_hash::char(64) THEN
      RAISE EXCEPTION
        'idempotency key was already used with a different statutory evaluation'
        USING ERRCODE = '23505';
    END IF;

    IF existing_request.status = 'COMPLETED' THEN
      RETURN QUERY
      SELECT
        existing_request.id,
        existing_request.payroll_result_count,
        existing_request.statutory_result_count,
        existing_request.employee_total,
        existing_request.employer_total,
        existing_request.post_statutory_net_total,
        existing_request.evidence_set_hash;
      RETURN;
    END IF;

    RAISE EXCEPTION 'statutory evaluation is already in progress'
      USING ERRCODE = '40001';
  END IF;

  SELECT
    cycle.status,
    cycle.version_no,
    cycle.active_calculation_request_id
  INTO
    cycle_status,
    cycle_version,
    active_calculation_request_id
  FROM payroll_ops.payroll_cycle cycle
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
  FOR UPDATE OF cycle;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll cycle does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF cycle_version <> p_expected_cycle_version THEN
    RAISE EXCEPTION 'payroll cycle changed since it was read'
      USING ERRCODE = '40001';
  END IF;

  IF cycle_status <> 'CALCULATED'
     OR active_calculation_request_id IS DISTINCT FROM
        p_calculation_request_id THEN
    RAISE EXCEPTION
      'statutory evaluation requires the active calculated payroll request'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    request.status,
    request.request_schema_version,
    request.result_count,
    request.result_set_hash,
    request.net_total
  INTO
    calculation_status,
    calculation_schema_version,
    calculation_result_count,
    calculation_result_set_hash,
    calculation_net_total
  FROM payroll_calc.calculation_request request
  WHERE request.tenant_id = p_tenant_id
    AND request.id = p_calculation_request_id
    AND request.payroll_cycle_id = p_payroll_cycle_id
  FOR UPDATE OF request;

  IF NOT FOUND
     OR calculation_status <> 'COMPLETED'
     OR calculation_schema_version <> 1
     OR calculation_result_count IS NULL
     OR calculation_result_count < 1
     OR calculation_result_set_hash IS NULL
     OR calculation_net_total IS NULL THEN
    RAISE EXCEPTION
      'statutory evaluation requires a completed schema-version-1 calculation request'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO statutory.statutory_evaluation_request(
    tenant_id,
    payroll_cycle_id,
    calculation_request_id,
    idempotency_key,
    request_hash,
    request_schema_version,
    engine_version,
    expected_cycle_version,
    calculation_result_set_hash,
    status,
    started_at,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    p_tenant_id,
    p_payroll_cycle_id,
    p_calculation_request_id,
    btrim(p_idempotency_key),
    p_request_hash::char(64),
    1,
    'STATUTORY_NEUTRAL_V1',
    p_expected_cycle_version,
    calculation_result_set_hash,
    'EVALUATING',
    p_evaluated_at,
    p_evaluated_at,
    p_actor,
    p_evaluated_at,
    p_actor
  ) RETURNING id INTO request_id;

  FOR payroll_result_row IN
    SELECT
      result.id,
      result.result_hash,
      result.input_snapshot_id,
      result.input_snapshot_hash,
      result.payroll_assignment_version_id,
      result.net_amount,
      result.currency,
      snapshot.snapshot_payload,
      assignment_version.payroll_assignment_id AS result_payroll_assignment_id
    FROM payroll_calc.payroll_result result
    JOIN payroll_ops.input_snapshot snapshot
      ON snapshot.tenant_id = result.tenant_id
     AND snapshot.id = result.input_snapshot_id
     AND snapshot.snapshot_hash = result.input_snapshot_hash
    JOIN employee_payroll.payroll_assignment_version assignment_version
      ON assignment_version.tenant_id = result.tenant_id
     AND assignment_version.id = result.payroll_assignment_version_id
    WHERE result.tenant_id = p_tenant_id
      AND result.calculation_request_id = p_calculation_request_id
      AND result.payroll_cycle_id = p_payroll_cycle_id
      AND result.result_schema_version = 1
      AND result.result_status = 'CALCULATED'
    ORDER BY result.payroll_assignment_version_id
  LOOP
    actual_payroll_result_count := actual_payroll_result_count + 1;
    payroll_employee := 0;
    payroll_employer := 0;
    payroll_statutory_count := 0;
    period_start := (
      payroll_result_row.snapshot_payload
        #>> '{payPeriod,periodStart}'
    )::date;
    period_end := (
      payroll_result_row.snapshot_payload
        #>> '{payPeriod,periodEnd}'
    )::date;

    IF period_start IS NULL
       OR period_end IS NULL
       OR period_end < period_start THEN
      RAISE EXCEPTION
        'payroll input snapshot lacks a valid pay-period range'
        USING ERRCODE = '23514';
    END IF;

    FOR assignment_row IN
      SELECT
        assignment.id,
        assignment.employee_statutory_profile_id,
        assignment.employee_statutory_profile_version_id,
        assignment.statutory_rule_id,
        assignment.statutory_rule_version_id,
        assignment.eligibility_status,
        assignment.exemption_status,
        assignment.exemption_reason_code,
        profile.jurisdiction_code,
        profile.authority_code,
        profile_version.registration_status,
        profile_version.classification_code,
        rule.code AS rule_code,
        rule.rule_category,
        rule_version.currency,
        rule_version.rounding_scale,
        rule_version.rounding_mode
      FROM statutory.employee_statutory_rule_assignment assignment
      JOIN statutory.employee_statutory_profile profile
        ON profile.tenant_id = assignment.tenant_id
       AND profile.id = assignment.employee_statutory_profile_id
      JOIN statutory.employee_statutory_profile_version profile_version
        ON profile_version.tenant_id = assignment.tenant_id
       AND profile_version.id =
           assignment.employee_statutory_profile_version_id
       AND profile_version.employee_statutory_profile_id = profile.id
      JOIN statutory.statutory_rule rule
        ON rule.tenant_id = assignment.tenant_id
       AND rule.id = assignment.statutory_rule_id
      JOIN statutory.statutory_rule_version rule_version
        ON rule_version.tenant_id = assignment.tenant_id
       AND rule_version.id = assignment.statutory_rule_version_id
       AND rule_version.statutory_rule_id = rule.id
      WHERE assignment.tenant_id = p_tenant_id
        AND assignment.payroll_assignment_id =
            payroll_result_row.result_payroll_assignment_id
        AND assignment.payroll_assignment_version_id =
            payroll_result_row.payroll_assignment_version_id
        AND assignment.approval_status = 'APPROVED'
        AND assignment.effective_from <= period_start
        AND (
          assignment.effective_to IS NULL
          OR assignment.effective_to > period_end
        )
        AND profile.status = 'ACTIVE'
        AND profile_version.approval_status = 'APPROVED'
        AND profile_version.effective_from <= period_start
        AND (
          profile_version.effective_to IS NULL
          OR profile_version.effective_to > period_end
        )
        AND rule.status = 'ACTIVE'
        AND rule.jurisdiction_code = profile.jurisdiction_code
        AND rule.authority_code = profile.authority_code
        AND rule_version.approval_status = 'APPROVED'
        AND rule_version.effective_from <= period_start
        AND (
          rule_version.effective_to IS NULL
          OR rule_version.effective_to > period_end
        )
      ORDER BY rule.code, assignment.id
    LOOP
      IF assignment_row.eligibility_status = 'INELIGIBLE' THEN
        CONTINUE;
      END IF;

      IF assignment_row.eligibility_status <> 'ELIGIBLE' THEN
        RAISE EXCEPTION
          'conditional statutory eligibility requires a jurisdiction-specific resolver'
          USING ERRCODE = '23514';
      END IF;

      IF assignment_row.exemption_status <> 'NONE' THEN
        RAISE EXCEPTION
          'statutory exemptions require a jurisdiction-specific liability resolver'
          USING ERRCODE = '23514';
      END IF;

      IF assignment_row.currency <> payroll_result_row.currency THEN
        RAISE EXCEPTION
          'statutory-rule currency does not match the payroll result'
          USING ERRCODE = '23514';
      END IF;

      snapshot_payload := jsonb_build_object(
        'schemaVersion', 1,
        'engineVersion', 'STATUTORY_NEUTRAL_V1',
        'evaluationRequestId', request_id::text,
        'payrollCycleId', p_payroll_cycle_id::text,
        'calculationRequestId', p_calculation_request_id::text,
        'payrollResult', jsonb_build_object(
          'id', payroll_result_row.id::text,
          'hash', payroll_result_row.result_hash,
          'netAmount', payroll_result_row.net_amount,
          'currency', payroll_result_row.currency
        ),
        'inputSnapshot', jsonb_build_object(
          'id', payroll_result_row.input_snapshot_id::text,
          'hash', payroll_result_row.input_snapshot_hash,
          'periodStart', period_start,
          'periodEnd', period_end
        ),
        'assignment', jsonb_build_object(
          'id', assignment_row.id::text,
          'payrollAssignmentId',
            payroll_result_row.result_payroll_assignment_id::text,
          'payrollAssignmentVersionId',
            payroll_result_row.payroll_assignment_version_id::text,
          'profileId', assignment_row.employee_statutory_profile_id::text,
          'profileVersionId',
            assignment_row.employee_statutory_profile_version_id::text,
          'eligibilityStatus', assignment_row.eligibility_status,
          'exemptionStatus', assignment_row.exemption_status
        ),
        'profile', jsonb_build_object(
          'jurisdictionCode', assignment_row.jurisdiction_code,
          'authorityCode', assignment_row.authority_code,
          'registrationStatus', assignment_row.registration_status,
          'classificationCode', assignment_row.classification_code
        ),
        'rule', jsonb_build_object(
          'identityId', assignment_row.statutory_rule_id::text,
          'versionId', assignment_row.statutory_rule_version_id::text,
          'code', assignment_row.rule_code,
          'category', assignment_row.rule_category,
          'currency', assignment_row.currency,
          'roundingScale', assignment_row.rounding_scale,
          'roundingMode', assignment_row.rounding_mode,
          'portions', (
            SELECT jsonb_agg(
              jsonb_build_object(
                'id', portion.id::text,
                'sequenceNo', portion.sequence_no,
                'liableParty', portion.liable_party,
                'calculationMethod', portion.calculation_method,
                'assessmentBaseCode', portion.assessment_base_code,
                'fixedAmount', portion.fixed_amount,
                'ratePercent', portion.rate_percent,
                'thresholdAmount', portion.threshold_amount,
                'baseCapAmount', portion.base_cap_amount,
                'resultMinimumAmount', portion.result_minimum_amount,
                'resultCapAmount', portion.result_cap_amount,
                'slabs', coalesce((
                  SELECT jsonb_agg(
                    jsonb_build_object(
                      'id', slab.id::text,
                      'sequenceNo', slab.sequence_no,
                      'lowerBound', slab.lower_bound,
                      'upperBound', slab.upper_bound,
                      'fixedAmount', slab.fixed_amount,
                      'ratePercent', slab.rate_percent
                    ) ORDER BY slab.sequence_no
                  )
                  FROM statutory.statutory_rule_slab slab
                  WHERE slab.tenant_id = portion.tenant_id
                    AND slab.statutory_rule_portion_id = portion.id
                ), '[]'::jsonb)
              ) ORDER BY portion.sequence_no
            )
            FROM statutory.statutory_rule_portion portion
            WHERE portion.tenant_id = p_tenant_id
              AND portion.statutory_rule_version_id =
                  assignment_row.statutory_rule_version_id
          )
        ),
        'classifications', coalesce((
          SELECT jsonb_agg(
            jsonb_build_object(
              'classificationId', classification.id::text,
              'assessmentBaseCode', classification.assessment_base_code,
              'componentId', classification.component_id::text,
              'componentVersionId',
                classification.component_version_id::text,
              'inclusionPercent', classification.inclusion_percent,
              'componentResultId', component_result.id::text,
              'componentCode', component_result.component_code,
              'calculatedAmount', component_result.calculated_amount
            ) ORDER BY
              classification.assessment_base_code,
              component_result.sequence_no,
              component_result.id
          )
          FROM payroll_calc.component_result component_result
          JOIN compensation.pay_component_version component_version
            ON component_version.tenant_id = component_result.tenant_id
           AND component_version.id = component_result.component_version_id
          JOIN statutory.statutory_component_classification classification
            ON classification.tenant_id = component_result.tenant_id
           AND classification.component_version_id = component_result.component_version_id
           AND classification.component_id = component_version.component_id
          WHERE component_result.tenant_id = p_tenant_id
            AND component_result.payroll_result_id = payroll_result_row.id
            AND component_result.component_schema_version = 1
            AND classification.jurisdiction_code =
                assignment_row.jurisdiction_code
            AND classification.authority_code = assignment_row.authority_code
            AND classification.approval_status = 'APPROVED'
            AND classification.effective_from <= period_start
            AND (
              classification.effective_to IS NULL
              OR classification.effective_to > period_end
            )
        ), '[]'::jsonb)
      );

      snapshot_hash := encode(
        public.digest(snapshot_payload::text, 'sha256'::text),
        'hex'
      );

      INSERT INTO statutory.statutory_input_snapshot(
        tenant_id,
        evaluation_request_id,
        calculation_request_id,
        payroll_cycle_id,
        payroll_result_id,
        payroll_result_hash,
        input_snapshot_id,
        input_snapshot_hash,
        payroll_assignment_id,
        payroll_assignment_version_id,
        employee_statutory_profile_id,
        employee_statutory_profile_version_id,
        employee_statutory_rule_assignment_id,
        statutory_rule_id,
        statutory_rule_version_id,
        snapshot_schema_version,
        snapshot_payload,
        snapshot_hash,
        created_at,
        created_by
      ) VALUES (
        p_tenant_id,
        request_id,
        p_calculation_request_id,
        p_payroll_cycle_id,
        payroll_result_row.id,
        payroll_result_row.result_hash,
        payroll_result_row.input_snapshot_id,
        payroll_result_row.input_snapshot_hash,
        payroll_result_row.result_payroll_assignment_id,
        payroll_result_row.payroll_assignment_version_id,
        assignment_row.employee_statutory_profile_id,
        assignment_row.employee_statutory_profile_version_id,
        assignment_row.id,
        assignment_row.statutory_rule_id,
        assignment_row.statutory_rule_version_id,
        1,
        snapshot_payload,
        snapshot_hash,
        p_evaluated_at,
        p_actor
      ) RETURNING id INTO statutory_snapshot_id;

      portion_results := '[]'::jsonb;
      rule_employee := 0;
      rule_employer := 0;

      FOR portion_row IN
        SELECT portion.*
        FROM statutory.statutory_rule_portion portion
        WHERE portion.tenant_id = p_tenant_id
          AND portion.statutory_rule_version_id =
              assignment_row.statutory_rule_version_id
        ORDER BY portion.sequence_no, portion.id
      LOOP
        base_amount := 0;
        capped_base := 0;
        assessable_base := 0;
        raw_amount := 0;

        IF portion_row.calculation_method = 'FIXED' THEN
          raw_amount := portion_row.fixed_amount;
        ELSE
          IF NOT EXISTS (
            SELECT 1
            FROM statutory.statutory_component_classification classification
            WHERE classification.tenant_id = p_tenant_id
              AND classification.jurisdiction_code =
                  assignment_row.jurisdiction_code
              AND classification.authority_code = assignment_row.authority_code
              AND classification.assessment_base_code =
                  portion_row.assessment_base_code
              AND classification.approval_status = 'APPROVED'
              AND classification.effective_from <= period_start
              AND (
                classification.effective_to IS NULL
                OR classification.effective_to > period_end
              )
          ) THEN
            RAISE EXCEPTION
              'no approved component classification exists for assessment base %',
              portion_row.assessment_base_code
              USING ERRCODE = '23514';
          END IF;

          SELECT coalesce(
              sum(
                component_result.calculated_amount
                  * classification.inclusion_percent / 100
              ),
              0
            )::numeric(19,4)
          INTO base_amount
          FROM payroll_calc.component_result component_result
          JOIN compensation.pay_component_version component_version
            ON component_version.tenant_id = component_result.tenant_id
           AND component_version.id = component_result.component_version_id
          JOIN statutory.statutory_component_classification classification
            ON classification.tenant_id = component_result.tenant_id
           AND classification.component_version_id =
               component_result.component_version_id
           AND classification.component_id = component_version.component_id
          WHERE component_result.tenant_id = p_tenant_id
            AND component_result.payroll_result_id = payroll_result_row.id
            AND component_result.component_schema_version = 1
            AND classification.jurisdiction_code =
                assignment_row.jurisdiction_code
            AND classification.authority_code = assignment_row.authority_code
            AND classification.assessment_base_code =
                portion_row.assessment_base_code
            AND classification.approval_status = 'APPROVED'
            AND classification.effective_from <= period_start
            AND (
              classification.effective_to IS NULL
              OR classification.effective_to > period_end
            );

          capped_base := CASE
            WHEN portion_row.base_cap_amount IS NULL THEN base_amount
            ELSE least(base_amount, portion_row.base_cap_amount)
          END;
          assessable_base := greatest(
            capped_base - coalesce(portion_row.threshold_amount, 0),
            0
          );

          IF portion_row.calculation_method = 'PERCENTAGE' THEN
            raw_amount :=
              assessable_base * portion_row.rate_percent / 100;
          ELSE
            FOR slab_row IN
              SELECT slab.*
              FROM statutory.statutory_rule_slab slab
              WHERE slab.tenant_id = p_tenant_id
                AND slab.statutory_rule_portion_id = portion_row.id
              ORDER BY slab.sequence_no
            LOOP
              IF assessable_base > slab_row.lower_bound THEN
                band_width := greatest(
                  least(
                    assessable_base,
                    coalesce(slab_row.upper_bound, assessable_base)
                  ) - slab_row.lower_bound,
                  0
                );
                raw_amount := raw_amount
                  + slab_row.fixed_amount
                  + band_width * slab_row.rate_percent / 100;
              END IF;
            END LOOP;
          END IF;
        END IF;

        IF portion_row.result_minimum_amount IS NOT NULL THEN
          raw_amount := greatest(
            raw_amount,
            portion_row.result_minimum_amount
          );
        END IF;

        IF portion_row.result_cap_amount IS NOT NULL THEN
          raw_amount := least(raw_amount, portion_row.result_cap_amount);
        END IF;

        rounded_amount := statutory.round_statutory_amount(
          raw_amount,
          assignment_row.rounding_scale,
          assignment_row.rounding_mode
        )::numeric(19,4);

        portion_payload := jsonb_build_object(
          'schemaVersion', 1,
          'sequenceNo', portion_row.sequence_no,
          'portionId', portion_row.id::text,
          'liableParty', portion_row.liable_party,
          'calculationMethod', portion_row.calculation_method,
          'assessmentBaseCode', portion_row.assessment_base_code,
          'assessmentBaseAmount', base_amount,
          'cappedBaseAmount', capped_base,
          'assessableBaseAmount', assessable_base,
          'rawAmount', raw_amount,
          'calculatedAmount', rounded_amount,
          'currency', assignment_row.currency,
          'roundingScale', assignment_row.rounding_scale,
          'roundingMode', assignment_row.rounding_mode
        );
        portion_hash := encode(
          public.digest(portion_payload::text, 'sha256'::text),
          'hex'
        );

        portion_results := portion_results || jsonb_build_array(
          portion_payload || jsonb_build_object(
            'portionHash', portion_hash
          )
        );

        IF portion_row.liable_party = 'EMPLOYEE' THEN
          rule_employee := rule_employee + rounded_amount;
        ELSE
          rule_employer := rule_employer + rounded_amount;
        END IF;
      END LOOP;

      result_payload := jsonb_build_object(
        'schemaVersion', 1,
        'statutoryInputSnapshotId', statutory_snapshot_id::text,
        'payrollResultId', payroll_result_row.id::text,
        'ruleAssignmentId', assignment_row.id::text,
        'statutoryRuleVersionId',
          assignment_row.statutory_rule_version_id::text,
        'currency', assignment_row.currency,
        'employeeAmount', round(rule_employee, 4),
        'employerAmount', round(rule_employer, 4),
        'portions', portion_results
      );
      result_hash := encode(
        public.digest(result_payload::text, 'sha256'::text),
        'hex'
      );

      INSERT INTO statutory.statutory_result(
        tenant_id,
        evaluation_request_id,
        statutory_input_snapshot_id,
        payroll_result_id,
        employee_statutory_rule_assignment_id,
        statutory_rule_version_id,
        currency,
        employee_amount,
        employer_amount,
        result_schema_version,
        result_payload,
        result_hash,
        created_at,
        created_by
      ) VALUES (
        p_tenant_id,
        request_id,
        statutory_snapshot_id,
        payroll_result_row.id,
        assignment_row.id,
        assignment_row.statutory_rule_version_id,
        assignment_row.currency,
        round(rule_employee, 4),
        round(rule_employer, 4),
        1,
        result_payload,
        result_hash,
        p_evaluated_at,
        p_actor
      ) RETURNING id INTO statutory_result_id;

      FOR portion_json IN
        SELECT value
        FROM jsonb_array_elements(portion_results)
      LOOP
        INSERT INTO statutory.statutory_portion_result(
          tenant_id,
          statutory_result_id,
          statutory_rule_version_id,
          statutory_rule_portion_id,
          sequence_no,
          liable_party,
          calculation_method,
          assessment_base_code,
          assessment_base_amount,
          calculated_amount,
          result_schema_version,
          result_payload,
          result_hash,
          created_at,
          created_by
        ) VALUES (
          p_tenant_id,
          statutory_result_id,
          assignment_row.statutory_rule_version_id,
          (portion_json ->> 'portionId')::uuid,
          (portion_json ->> 'sequenceNo')::integer,
          portion_json ->> 'liableParty',
          portion_json ->> 'calculationMethod',
          portion_json ->> 'assessmentBaseCode',
          (portion_json ->> 'assessmentBaseAmount')::numeric,
          (portion_json ->> 'calculatedAmount')::numeric,
          1,
          portion_json - 'portionHash',
          (portion_json ->> 'portionHash')::char(64),
          p_evaluated_at,
          p_actor
        );
      END LOOP;

      actual_statutory_result_count :=
        actual_statutory_result_count + 1;
      payroll_statutory_count := payroll_statutory_count + 1;
      payroll_employee := payroll_employee + rule_employee;
      payroll_employer := payroll_employer + rule_employer;
    END LOOP;

    payroll_employee := round(payroll_employee, 4);
    payroll_employer := round(payroll_employer, 4);
    post_net := round(
      payroll_result_row.net_amount - payroll_employee,
      4
    );

    IF post_net < 0 THEN
      RAISE EXCEPTION
        'employee statutory deductions cannot exceed non-statutory net pay'
        USING ERRCODE = '23514';
    END IF;

    summary_payload := jsonb_build_object(
      'schemaVersion', 1,
      'evaluationRequestId', request_id::text,
      'payrollResultId', payroll_result_row.id::text,
      'currency', payroll_result_row.currency,
      'nonstatutoryNetAmount', payroll_result_row.net_amount,
      'employeeStatutoryAmount', payroll_employee,
      'employerStatutoryAmount', payroll_employer,
      'postStatutoryNetAmount', post_net,
      'resultCount', payroll_statutory_count,
      'statutoryResults', coalesce((
        SELECT jsonb_agg(
          jsonb_build_object(
            'id', result.id::text,
            'ruleAssignmentId',
              result.employee_statutory_rule_assignment_id::text,
            'statutoryRuleVersionId',
              result.statutory_rule_version_id::text,
            'employeeAmount', result.employee_amount,
            'employerAmount', result.employer_amount,
            'resultHash', result.result_hash
          ) ORDER BY
            result.employee_statutory_rule_assignment_id,
            result.id
        )
        FROM statutory.statutory_result result
        WHERE result.tenant_id = p_tenant_id
          AND result.evaluation_request_id = request_id
          AND result.payroll_result_id = payroll_result_row.id
      ), '[]'::jsonb)
    );
    summary_hash := encode(
      public.digest(summary_payload::text, 'sha256'::text),
      'hex'
    );

    INSERT INTO statutory.payroll_statutory_summary(
      tenant_id,
      evaluation_request_id,
      calculation_request_id,
      payroll_cycle_id,
      payroll_result_id,
      currency,
      nonstatutory_net_amount,
      employee_statutory_amount,
      employer_statutory_amount,
      post_statutory_net_amount,
      result_count,
      summary_schema_version,
      summary_payload,
      summary_hash,
      created_at,
      created_by
    ) VALUES (
      p_tenant_id,
      request_id,
      p_calculation_request_id,
      p_payroll_cycle_id,
      payroll_result_row.id,
      payroll_result_row.currency,
      payroll_result_row.net_amount,
      payroll_employee,
      payroll_employer,
      post_net,
      payroll_statutory_count,
      1,
      summary_payload,
      summary_hash,
      p_evaluated_at,
      p_actor
    );

    total_employee := total_employee + payroll_employee;
    total_employer := total_employer + payroll_employer;
    total_post_net := total_post_net + post_net;
  END LOOP;

  IF actual_payroll_result_count <> calculation_result_count THEN
    RAISE EXCEPTION
      'statutory evaluation did not cover every payroll result'
      USING ERRCODE = '23514';
  END IF;

  SELECT encode(
      public.digest(
        coalesce(
          string_agg(
            summary.payroll_result_id::text
              || ':' || summary.summary_hash,
            '|'
            ORDER BY summary.payroll_result_id
          ),
          ''
        ),
        'sha256'::text
      ),
      'hex'
    )
  INTO final_evidence_set_hash
  FROM statutory.payroll_statutory_summary summary
  WHERE summary.tenant_id = p_tenant_id
    AND summary.evaluation_request_id = request_id;

  IF round(total_post_net, 4) < 0
     OR round(total_employee, 4) > calculation_net_total THEN
    RAISE EXCEPTION 'invalid statutory evaluation totals'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.evaluation_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_evaluation_request request
  SET status = 'COMPLETED',
      completed_at = p_evaluated_at,
      completed_by = p_actor,
      payroll_result_count = actual_payroll_result_count,
      statutory_result_count = actual_statutory_result_count,
      employee_total = round(total_employee, 4),
      employer_total = round(total_employer, 4),
      post_statutory_net_total = round(total_post_net, 4),
      evidence_set_hash = final_evidence_set_hash,
      updated_at = p_evaluated_at,
      updated_by = p_actor,
      version_no = request.version_no + 1
  WHERE request.tenant_id = p_tenant_id
    AND request.id = request_id
    AND request.status = 'EVALUATING';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory evaluation request changed while evidence was being persisted'
      USING ERRCODE = '40001';
  END IF;

  RETURN QUERY
  SELECT
    request_id,
    actual_payroll_result_count,
    actual_statutory_result_count,
    round(total_employee, 4),
    round(total_employer, 4),
    round(total_post_net, 4),
    final_evidence_set_hash;
END $$;

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'statutory_component_classification',
    'statutory_evaluation_request',
    'statutory_input_snapshot',
    'statutory_result',
    'statutory_portion_result',
    'payroll_statutory_summary'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE statutory.%I ENABLE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'ALTER TABLE statutory.%I FORCE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON statutory.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      table_name
    );
  END LOOP;
END $$;

REVOKE ALL ON FUNCTION
  statutory.approve_statutory_component_classification(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) FROM PUBLIC;

REVOKE ALL ON FUNCTION
  statutory.end_date_statutory_component_classification(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) FROM PUBLIC;

REVOKE ALL ON FUNCTION
  statutory.evaluate_calculated_payroll(
    uuid,
    uuid,
    uuid,
    bigint,
    varchar,
    varchar,
    varchar,
    timestamptz
  ) FROM PUBLIC;

GRANT SELECT, INSERT
  ON statutory.statutory_component_classification
  TO payroll_app;

GRANT SELECT
  ON statutory.statutory_evaluation_request,
     statutory.statutory_input_snapshot,
     statutory.statutory_result,
     statutory.statutory_portion_result,
     statutory.payroll_statutory_summary
  TO payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON statutory.statutory_evaluation_request,
     statutory.statutory_input_snapshot,
     statutory.statutory_result,
     statutory.statutory_portion_result,
     statutory.payroll_statutory_summary
  FROM payroll_app;

REVOKE UPDATE, DELETE
  ON statutory.statutory_component_classification
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION
  statutory.approve_statutory_component_classification(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) TO payroll_app;

GRANT EXECUTE ON FUNCTION
  statutory.end_date_statutory_component_classification(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) TO payroll_app;

GRANT EXECUTE ON FUNCTION
  statutory.evaluate_calculated_payroll(
    uuid,
    uuid,
    uuid,
    bigint,
    varchar,
    varchar,
    varchar,
    timestamptz
  ) TO payroll_app;

REVOKE CREATE ON SCHEMA statutory FROM payroll_app;

COMMENT ON TABLE statutory.statutory_component_classification IS
  'Approved effective-dated mapping from an exact pay-component version into a jurisdiction and authority assessment base.';
COMMENT ON TABLE statutory.statutory_evaluation_request IS
  'Idempotent evaluation command and totals for one exact active completed payroll calculation request.';
COMMENT ON TABLE statutory.statutory_input_snapshot IS
  'Immutable canonical statutory configuration and payroll-result snapshot for one employee rule assignment.';
COMMENT ON TABLE statutory.statutory_result IS
  'Immutable per-rule employee deduction and employer liability evidence.';
COMMENT ON TABLE statutory.statutory_portion_result IS
  'Immutable FIXED, PERCENTAGE or SLAB portion calculation evidence.';
COMMENT ON TABLE statutory.payroll_statutory_summary IS
  'Immutable per-payroll-result statutory totals and post-statutory net evidence.';
COMMENT ON FUNCTION statutory.evaluate_calculated_payroll(
  uuid,
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Evaluates exact approved statutory assignments and component classifications against the active immutable payroll result set without rewriting payroll results.';
