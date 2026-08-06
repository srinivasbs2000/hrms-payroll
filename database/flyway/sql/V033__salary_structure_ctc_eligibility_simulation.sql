-- P5-A3 G02-B salary-structure, CTC, eligibility and validation schema hardening.
--
-- Forward-only from V032. V001-V032 remain immutable. Existing V020 salary-
-- structure identities, version UUIDs, lines and V021 salary-assignment lineage
-- remain schema 0. This gate adds durable configuration contracts only; it does
-- not implement official payroll calculation or legal/statutory truth. G02-B adds
-- controlled lifecycle commands and populated-upgrade safeguards.

ALTER TABLE compensation.salary_structure_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line
  NO FORCE ROW LEVEL SECURITY;

ALTER TABLE compensation.salary_structure_version
  ADD COLUMN structure_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN structure_type varchar(30),
  ADD COLUMN pay_frequency varchar(20),
  ADD COLUMN confidentiality_level varchar(20),
  ADD COLUMN ctc_policy_version_id uuid,
  ADD COLUMN eligibility_rule_version_id uuid,
  ADD COLUMN target_type varchar(24),
  ADD COLUMN target_annual_amount numeric(19,4),
  ADD COLUMN tolerance_amount numeric(19,4),
  ADD COLUMN residual_component_version_id uuid,
  ADD COLUMN configuration_hash varchar(64),
  ADD COLUMN validation_fingerprint varchar(64);

UPDATE compensation.salary_structure_version
   SET structure_schema_version = 0;

ALTER TABLE compensation.salary_structure_line
  ADD COLUMN line_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN line_type varchar(30),
  ADD COLUMN minimum_amount numeric(19,4),
  ADD COLUMN maximum_amount numeric(19,4),
  ADD COLUMN mandatory_flag boolean,
  ADD COLUMN override_policy varchar(24),
  ADD COLUMN ctc_display_order integer,
  ADD COLUMN payslip_display_order integer;

UPDATE compensation.salary_structure_line
   SET line_schema_version = 0;

CREATE TABLE compensation.ctc_policy (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  retirement_effective_date date,
  retirement_reason varchar(500),
  retired_at timestamptz,
  retired_by varchar(160),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (
    (
      lifecycle_status <> 'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL
    )
    OR (
      lifecycle_status = 'RETIRED'
      AND retirement_effective_date IS NOT NULL
      AND retirement_reason IS NOT NULL
      AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL
      AND length(btrim(retired_by)) BETWEEN 1 AND 160
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE compensation.ctc_policy_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ctc_policy_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'INR',
  annualisation_method varchar(24) NOT NULL,
  tolerance_amount numeric(19,4) NOT NULL DEFAULT 0,
  residual_component_id uuid NOT NULL,
  residual_component_version_id uuid NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, ctc_policy_id),
  UNIQUE (tenant_id, ctc_policy_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (currency = 'INR'),
  CHECK (annualisation_method IN ('MONTHLY_X_12', 'PAY_PERIOD_FACTOR', 'EXACT_ANNUAL')),
  CHECK (tolerance_amount >= 0),
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
    (residual_component_id IS NULL AND residual_component_version_id IS NULL)
    OR
    (residual_component_id IS NOT NULL AND residual_component_version_id IS NOT NULL)
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  FOREIGN KEY (tenant_id, ctc_policy_id)
    REFERENCES compensation.ctc_policy(tenant_id, id),
  CONSTRAINT ctc_policy_version_residual_component_fk
    FOREIGN KEY (tenant_id, residual_component_version_id, residual_component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id),
  CONSTRAINT ctc_policy_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, ctc_policy_id)
    REFERENCES compensation.ctc_policy_version(tenant_id, id, ctc_policy_id)
);

ALTER TABLE compensation.ctc_policy_version
  ADD CONSTRAINT ctc_policy_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    ctc_policy_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX ctc_policy_version_one_successor_uk
  ON compensation.ctc_policy_version(tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX ctc_policy_version_current_ix
  ON compensation.ctc_policy_version(
    tenant_id, ctc_policy_id, effective_from DESC
  );

CREATE TABLE compensation.ctc_policy_treatment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ctc_policy_id uuid NOT NULL,
  ctc_policy_version_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  treatment_sequence integer NOT NULL,
  cost_view varchar(30) NOT NULL,
  treatment_type varchar(30) NOT NULL,
  fixed_value numeric(19,4),
  target_percentage numeric(12,8),
  payroll_base_id uuid,
  payroll_base_version_id uuid,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, ctc_policy_version_id, cost_view, component_version_id),
  UNIQUE (tenant_id, ctc_policy_version_id, treatment_sequence),
  CHECK (treatment_sequence > 0),
  CHECK (cost_view IN ('OFFERED', 'TARGET', 'ACCRUED', 'ACTUAL_EMPLOYER_COST')),
  CHECK (
    treatment_type IN (
      'FIXED_VALUE', 'TARGET_VALUE', 'ACTUAL_VALUE', 'PROVISION',
      'EMPLOYER_CONTRIBUTION', 'BENEFIT_PREMIUM', 'EXCLUDE', 'INFORMATIONAL'
    )
  ),
  CHECK (fixed_value IS NULL OR fixed_value >= 0),
  CHECK (
    target_percentage IS NULL
    OR (target_percentage > 0 AND target_percentage <= 100)
  ),
  CHECK (
    (payroll_base_id IS NULL AND payroll_base_version_id IS NULL)
    OR
    (payroll_base_id IS NOT NULL AND payroll_base_version_id IS NOT NULL)
  ),
  CHECK (
    (
      treatment_type = 'FIXED_VALUE'
      AND fixed_value IS NOT NULL
      AND target_percentage IS NULL
    )
    OR (
      treatment_type = 'TARGET_VALUE'
      AND fixed_value IS NULL
      AND target_percentage IS NOT NULL
    )
    OR (
      treatment_type NOT IN ('FIXED_VALUE', 'TARGET_VALUE')
      AND fixed_value IS NULL
      AND target_percentage IS NULL
    )
  ),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CONSTRAINT ctc_policy_treatment_policy_version_fk
    FOREIGN KEY (tenant_id, ctc_policy_version_id, ctc_policy_id)
    REFERENCES compensation.ctc_policy_version(tenant_id, id, ctc_policy_id),
  CONSTRAINT ctc_policy_treatment_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id),
  CONSTRAINT ctc_policy_treatment_payroll_base_version_fk
    FOREIGN KEY (tenant_id, payroll_base_version_id, payroll_base_id)
    REFERENCES compensation.payroll_base_version(tenant_id, id, payroll_base_id)
);

CREATE INDEX ctc_policy_treatment_lookup_ix
  ON compensation.ctc_policy_treatment(
    tenant_id, ctc_policy_version_id, cost_view, treatment_sequence
  );

CREATE TABLE compensation.eligibility_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  retirement_effective_date date,
  retirement_reason varchar(500),
  retired_at timestamptz,
  retired_by varchar(160),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (
    (
      lifecycle_status <> 'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL
    )
    OR (
      lifecycle_status = 'RETIRED'
      AND retirement_effective_date IS NOT NULL
      AND retirement_reason IS NOT NULL
      AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL
      AND length(btrim(retired_by)) BETWEEN 1 AND 160
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE compensation.eligibility_rule_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  eligibility_rule_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  result_when_matched varchar(30) NOT NULL,
  result_when_not_matched varchar(30) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, eligibility_rule_id),
  UNIQUE (tenant_id, eligibility_rule_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (result_when_matched IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'REQUIRES_APPROVAL')),
  CHECK (result_when_not_matched IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'REQUIRES_APPROVAL')),
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
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  FOREIGN KEY (tenant_id, eligibility_rule_id)
    REFERENCES compensation.eligibility_rule(tenant_id, id),
  CONSTRAINT eligibility_rule_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, eligibility_rule_id)
    REFERENCES compensation.eligibility_rule_version(
      tenant_id, id, eligibility_rule_id
    )
);

ALTER TABLE compensation.eligibility_rule_version
  ADD CONSTRAINT eligibility_rule_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    eligibility_rule_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX eligibility_rule_version_one_successor_uk
  ON compensation.eligibility_rule_version(tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX eligibility_rule_version_current_ix
  ON compensation.eligibility_rule_version(
    tenant_id, eligibility_rule_id, effective_from DESC
  );

CREATE OR REPLACE FUNCTION compensation.is_typed_eligibility_value(
  p_value jsonb,
  p_fact_type varchar,
  p_comparison_operator varchar
) RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
STRICT AS $$
DECLARE
  item jsonb;
  item_text text;
BEGIN
  IF p_comparison_operator IN ('IN', 'NOT_IN') THEN
    IF jsonb_typeof(p_value) <> 'array' OR jsonb_array_length(p_value) = 0 THEN
      RETURN false;
    END IF;
  ELSE
    IF jsonb_typeof(p_value) = 'array' THEN
      RETURN false;
    END IF;
  END IF;

  FOR item IN
    SELECT value
      FROM jsonb_array_elements(
        CASE
          WHEN jsonb_typeof(p_value) = 'array' THEN p_value
          ELSE jsonb_build_array(p_value)
        END
      )
  LOOP
    IF p_fact_type = 'NUMBER' THEN
      IF jsonb_typeof(item) <> 'number' THEN
        RETURN false;
      END IF;
    ELSE
      IF jsonb_typeof(item) <> 'string' THEN
        RETURN false;
      END IF;
      item_text := item #>> '{}';
      IF item_text IS NULL OR btrim(item_text) = '' THEN
        RETURN false;
      END IF;
      IF p_fact_type = 'DATE' THEN
        IF item_text !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN
          RETURN false;
        END IF;
        BEGIN
          PERFORM make_date(
            substring(item_text FROM 1 FOR 4)::integer,
            substring(item_text FROM 6 FOR 2)::integer,
            substring(item_text FROM 9 FOR 2)::integer
          );
        EXCEPTION WHEN OTHERS THEN
          RETURN false;
        END;
      ELSIF p_fact_type = 'UUID' THEN
        IF item_text !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
          RETURN false;
        END IF;
      END IF;
    END IF;
  END LOOP;

  RETURN true;
END $$;

CREATE TABLE compensation.eligibility_rule_criterion (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  eligibility_rule_id uuid NOT NULL,
  eligibility_rule_version_id uuid NOT NULL,
  criterion_sequence integer NOT NULL,
  fact_key varchar(50) NOT NULL,
  fact_type varchar(16) NOT NULL,
  comparison_operator varchar(16) NOT NULL,
  value_json jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, eligibility_rule_version_id, criterion_sequence),
  CHECK (criterion_sequence > 0),
  CHECK (
    fact_key IN (
      'COUNTRY_CODE', 'STATE_CODE', 'LOCATION_CODE', 'LEGAL_ENTITY_VERSION_ID',
      'PAYROLL_STATUTORY_UNIT_VERSION_ID', 'ESTABLISHMENT_VERSION_ID',
      'PAY_GROUP_VERSION_ID', 'EMPLOYMENT_TYPE', 'EMPLOYEE_CATEGORY',
      'GRADE_CODE', 'JOB_CODE', 'SERVICE_MONTHS',
      'ANNUAL_COMPENSATION_AMOUNT', 'EFFECTIVE_DATE'
    )
  ),
  CONSTRAINT eligibility_rule_criterion_fact_type_ck
    CHECK (fact_type IN ('TEXT', 'NUMBER', 'DATE', 'UUID')),
  CONSTRAINT eligibility_rule_criterion_operator_ck
    CHECK (comparison_operator IN ('EQ', 'NE', 'IN', 'NOT_IN', 'GTE', 'LTE')),
  CONSTRAINT eligibility_rule_criterion_key_type_ck
    CHECK (
    (fact_key IN (
      'LEGAL_ENTITY_VERSION_ID', 'PAYROLL_STATUTORY_UNIT_VERSION_ID',
      'ESTABLISHMENT_VERSION_ID', 'PAY_GROUP_VERSION_ID'
    ) AND fact_type = 'UUID')
    OR
    (fact_key IN ('SERVICE_MONTHS', 'ANNUAL_COMPENSATION_AMOUNT')
      AND fact_type = 'NUMBER')
    OR
    (fact_key = 'EFFECTIVE_DATE' AND fact_type = 'DATE')
    OR
    (fact_key IN (
      'COUNTRY_CODE', 'STATE_CODE', 'LOCATION_CODE', 'EMPLOYMENT_TYPE',
      'EMPLOYEE_CATEGORY', 'GRADE_CODE', 'JOB_CODE'
    ) AND fact_type = 'TEXT')
  ),
  CONSTRAINT eligibility_rule_criterion_operator_type_ck
    CHECK (
    comparison_operator IN ('EQ', 'NE', 'IN', 'NOT_IN')
    OR (
      comparison_operator IN ('GTE', 'LTE')
      AND fact_type IN ('NUMBER', 'DATE')
    )
  ),
  CONSTRAINT eligibility_rule_criterion_typed_value_ck
    CHECK (
    compensation.is_typed_eligibility_value(
      value_json, fact_type, comparison_operator
    )
  ),
  CONSTRAINT eligibility_rule_criterion_version_fk
    FOREIGN KEY (tenant_id, eligibility_rule_version_id, eligibility_rule_id)
    REFERENCES compensation.eligibility_rule_version(
      tenant_id, id, eligibility_rule_id
    )
);

CREATE INDEX eligibility_rule_criterion_lookup_ix
  ON compensation.eligibility_rule_criterion(
    tenant_id, eligibility_rule_version_id, criterion_sequence
  );

ALTER TABLE compensation.salary_structure_version
  ADD CONSTRAINT salary_structure_version_ctc_policy_version_fk
    FOREIGN KEY (tenant_id, ctc_policy_version_id)
    REFERENCES compensation.ctc_policy_version(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_eligibility_rule_version_fk
    FOREIGN KEY (tenant_id, eligibility_rule_version_id)
    REFERENCES compensation.eligibility_rule_version(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_residual_component_version_fk
    FOREIGN KEY (tenant_id, residual_component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_p5a3_shape_ck
    CHECK (
      (
        structure_schema_version = 0
        AND structure_type IS NULL
        AND pay_frequency IS NULL
        AND confidentiality_level IS NULL
        AND ctc_policy_version_id IS NULL
        AND eligibility_rule_version_id IS NULL
        AND target_type IS NULL
        AND target_annual_amount IS NULL
        AND tolerance_amount IS NULL
        AND residual_component_version_id IS NULL
        AND configuration_hash IS NULL
        AND validation_fingerprint IS NULL
      )
      OR
      (
        structure_schema_version = 1
        AND structure_type IS NOT NULL
        AND structure_type IN ('STANDARD', 'EXECUTIVE', 'SALES', 'HOURLY', 'CONTRACT')
        AND pay_frequency IS NOT NULL
        AND pay_frequency IN ('MONTHLY', 'WEEKLY', 'BIWEEKLY', 'SEMIMONTHLY')
        AND confidentiality_level IS NOT NULL
        AND confidentiality_level IN ('STANDARD', 'RESTRICTED', 'EXECUTIVE')
        AND ctc_policy_version_id IS NOT NULL
        AND target_type IS NOT NULL
        AND target_type IN ('ANNUAL_CTC', 'ANNUAL_GROSS', 'MONTHLY_GROSS')
        AND target_annual_amount IS NOT NULL
        AND target_annual_amount > 0
        AND tolerance_amount IS NOT NULL
        AND tolerance_amount >= 0
        AND residual_component_version_id IS NOT NULL
        AND configuration_hash IS NOT NULL
        AND configuration_hash ~ '^[0-9a-f]{64}$'
        AND (
          validation_fingerprint IS NULL
          OR validation_fingerprint ~ '^[0-9a-f]{64}$'
        )
      )
    );

ALTER TABLE compensation.salary_structure_line
  ADD CONSTRAINT salary_structure_line_p5a3_shape_ck
    CHECK (
      (
        line_schema_version = 0
        AND line_type IS NULL
        AND minimum_amount IS NULL
        AND maximum_amount IS NULL
        AND mandatory_flag IS NULL
        AND override_policy IS NULL
        AND ctc_display_order IS NULL
        AND payslip_display_order IS NULL
      )
      OR
      (
        line_schema_version = 1
        AND line_type IS NOT NULL
        AND line_type IN ('FIXED', 'PERCENTAGE', 'RESIDUAL')
        AND (minimum_amount IS NULL OR minimum_amount >= 0)
        AND (maximum_amount IS NULL OR maximum_amount >= 0)
        AND (
          minimum_amount IS NULL
          OR maximum_amount IS NULL
          OR maximum_amount >= minimum_amount
        )
        AND mandatory_flag IS NOT NULL
        AND override_policy IS NOT NULL
        AND override_policy IN ('PROHIBITED', 'CONTROLLED', 'ALLOWED')
        AND ctc_display_order IS NOT NULL
        AND ctc_display_order > 0
        AND payslip_display_order IS NOT NULL
        AND payslip_display_order > 0
      )
    );

CREATE UNIQUE INDEX salary_structure_line_schema1_component_uk
  ON compensation.salary_structure_line(
    tenant_id, salary_structure_version_id, component_version_id
  ) WHERE (line_schema_version = 1);

CREATE UNIQUE INDEX salary_structure_line_schema1_ctc_order_uk
  ON compensation.salary_structure_line(
    tenant_id, salary_structure_version_id, ctc_display_order
  ) WHERE (line_schema_version = 1);

CREATE UNIQUE INDEX salary_structure_line_schema1_payslip_order_uk
  ON compensation.salary_structure_line(
    tenant_id, salary_structure_version_id, payslip_display_order
  ) WHERE (line_schema_version = 1);

CREATE UNIQUE INDEX salary_structure_line_schema1_one_residual_uk
  ON compensation.salary_structure_line(tenant_id, salary_structure_version_id)
  WHERE (line_schema_version = 1 AND line_type = 'RESIDUAL');

CREATE TABLE compensation.salary_structure_validation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  salary_structure_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  ctc_policy_version_id uuid NOT NULL,
  eligibility_rule_version_id uuid,
  effective_date date NOT NULL,
  target_amount numeric(19,4) NOT NULL,
  validation_status varchar(16) NOT NULL,
  request_hash varchar(64) NOT NULL,
  configuration_hash varchar(64) NOT NULL,
  result_hash varchar(64) NOT NULL,
  blocking_error_count integer NOT NULL DEFAULT 0,
  warning_count integer NOT NULL DEFAULT 0,
  summary_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, salary_structure_version_id, result_hash),
  CHECK (target_amount > 0),
  CHECK (validation_status IN ('PASS', 'FAIL')),
  CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  CHECK (configuration_hash ~ '^[0-9a-f]{64}$'),
  CHECK (result_hash ~ '^[0-9a-f]{64}$'),
  CHECK (blocking_error_count >= 0),
  CHECK (warning_count >= 0),
  CHECK (
    (validation_status = 'PASS' AND blocking_error_count = 0)
    OR validation_status = 'FAIL'
  ),
  CONSTRAINT salary_structure_validation_structure_version_fk
    FOREIGN KEY (tenant_id, salary_structure_version_id, salary_structure_id)
    REFERENCES compensation.salary_structure_version(
      tenant_id, id, salary_structure_id
    ),
  CONSTRAINT salary_structure_validation_ctc_policy_fk
    FOREIGN KEY (tenant_id, ctc_policy_version_id)
    REFERENCES compensation.ctc_policy_version(tenant_id, id),
  CONSTRAINT salary_structure_validation_eligibility_rule_fk
    FOREIGN KEY (tenant_id, eligibility_rule_version_id)
    REFERENCES compensation.eligibility_rule_version(tenant_id, id)
);

CREATE TABLE compensation.salary_structure_validation_line (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  validation_id uuid NOT NULL,
  line_sequence integer NOT NULL,
  component_id uuid,
  component_version_id uuid,
  annual_amount numeric(19,4),
  monthly_amount numeric(19,4),
  classification varchar(30),
  evidence_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, validation_id, line_sequence),
  CHECK (line_sequence > 0),
  CHECK ((component_id IS NULL) = (component_version_id IS NULL)),
  CHECK (annual_amount IS NULL OR annual_amount >= 0),
  CHECK (monthly_amount IS NULL OR monthly_amount >= 0),
  CHECK (
    classification IS NULL
    OR classification IN (
      'FIXED', 'VARIABLE', 'EMPLOYER_CONTRIBUTION', 'PROVISION',
      'BENEFIT', 'INFORMATIONAL', 'RESIDUAL'
    )
  ),
  CONSTRAINT salary_structure_validation_line_validation_fk
    FOREIGN KEY (tenant_id, validation_id)
    REFERENCES compensation.salary_structure_validation(tenant_id, id),
  CONSTRAINT salary_structure_validation_line_component_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id)
);

CREATE UNIQUE INDEX salary_structure_validation_line_component_uk
  ON compensation.salary_structure_validation_line(
    tenant_id, validation_id, component_version_id
  ) WHERE (component_version_id IS NOT NULL);

CREATE INDEX salary_structure_validation_lookup_ix
  ON compensation.salary_structure_validation(
    tenant_id, salary_structure_version_id, created_at DESC
  );

CREATE OR REPLACE FUNCTION compensation.assert_p5a3_identity_accepts_version()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  identity_status varchar(24);
BEGIN
  IF TG_TABLE_NAME = 'ctc_policy_version' THEN
    SELECT lifecycle_status
      INTO identity_status
      FROM compensation.ctc_policy
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.ctc_policy_id;
  ELSIF TG_TABLE_NAME = 'eligibility_rule_version' THEN
    SELECT lifecycle_status
      INTO identity_status
      FROM compensation.eligibility_rule
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.eligibility_rule_id;
  ELSE
    RETURN NEW;
  END IF;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'P5-A3 identity does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired P5-A3 identities cannot accept new versions'
      USING ERRCODE = 'P5A31';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER ctc_policy_version_identity_lifecycle
  BEFORE INSERT ON compensation.ctc_policy_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_p5a3_identity_accepts_version();
CREATE TRIGGER eligibility_rule_version_identity_lifecycle
  BEFORE INSERT ON compensation.eligibility_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_p5a3_identity_accepts_version();

CREATE OR REPLACE FUNCTION compensation.assert_ctc_policy_treatment_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  policy_from date;
  policy_to date;
  policy_status varchar;
  component_from date;
  component_to date;
  component_status varchar;
  base_from date;
  base_to date;
  base_status varchar;
  policy_identity_status varchar;
BEGIN
  SELECT version.effective_from, version.effective_to, version.approval_status,
         identity.lifecycle_status
    INTO policy_from, policy_to, policy_status, policy_identity_status
    FROM compensation.ctc_policy_version version
    JOIN compensation.ctc_policy identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.ctc_policy_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.ctc_policy_version_id
     AND version.ctc_policy_id = NEW.ctc_policy_id;

  IF policy_from IS NULL THEN
    RAISE EXCEPTION 'CTC-policy version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF policy_identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired CTC policies cannot accept treatments'
      USING ERRCODE = 'P5A31';
  END IF;
  IF TG_OP = 'INSERT' AND policy_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'CTC-policy treatments require a draft parent version'
      USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'UPDATE' AND current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'CTC-policy treatments require a controlled lifecycle command'
      USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'INSERT' AND EXISTS (
    SELECT 1
      FROM compensation.ctc_policy_version successor
     WHERE successor.tenant_id = NEW.tenant_id
       AND successor.supersedes_version_id = NEW.ctc_policy_version_id
  ) THEN
    RAISE EXCEPTION 'CTC-policy treatments cannot be added to a superseded draft'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_from < policy_from
     OR (
       policy_to IS NOT NULL
       AND (NEW.effective_to IS NULL OR NEW.effective_to > policy_to)
     ) THEN
    RAISE EXCEPTION 'CTC-policy treatment range must be contained by its version'
      USING ERRCODE = '23514';
  END IF;

  SELECT effective_from, effective_to, approval_status
    INTO component_from, component_to, component_status
    FROM compensation.pay_component_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.component_version_id
     AND component_id = NEW.component_id;

  IF component_from IS NULL OR component_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'CTC-policy treatments require an approved component version'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_from < component_from
     OR (
       component_to IS NOT NULL
       AND (NEW.effective_to IS NULL OR NEW.effective_to > component_to)
     ) THEN
    RAISE EXCEPTION 'CTC-policy treatment range must be contained by its component version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.payroll_base_version_id IS NOT NULL THEN
    SELECT effective_from, effective_to, approval_status
      INTO base_from, base_to, base_status
      FROM compensation.payroll_base_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.payroll_base_version_id
       AND payroll_base_id = NEW.payroll_base_id;
    IF base_from IS NULL OR base_status <> 'APPROVED' THEN
      RAISE EXCEPTION 'CTC-policy treatments require an approved payroll-base version'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.effective_from < base_from
       OR (
         base_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > base_to)
       ) THEN
      RAISE EXCEPTION 'CTC-policy treatment range must be contained by its payroll-base version'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER ctc_policy_treatment_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id, ctc_policy_id, ctc_policy_version_id, component_id,
    component_version_id, payroll_base_id, payroll_base_version_id,
    effective_from, effective_to
  ON compensation.ctc_policy_treatment
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_ctc_policy_treatment_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_eligibility_criterion_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_status varchar;
  identity_status varchar;
BEGIN
  SELECT version.approval_status, identity.lifecycle_status
    INTO parent_status, identity_status
    FROM compensation.eligibility_rule_version version
    JOIN compensation.eligibility_rule identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.eligibility_rule_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.eligibility_rule_version_id
     AND version.eligibility_rule_id = NEW.eligibility_rule_id;

  IF parent_status IS NULL THEN
    RAISE EXCEPTION 'eligibility-rule version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired eligibility rules cannot accept criteria'
      USING ERRCODE = 'P5A31';
  END IF;
  IF parent_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'eligibility criteria require a draft parent version'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.eligibility_rule_version successor
     WHERE successor.tenant_id = NEW.tenant_id
       AND successor.supersedes_version_id = NEW.eligibility_rule_version_id
  ) THEN
    RAISE EXCEPTION 'eligibility criteria cannot be added to a superseded draft'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER eligibility_rule_criterion_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id, eligibility_rule_id, eligibility_rule_version_id
  ON compensation.eligibility_rule_criterion
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_eligibility_criterion_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_salary_structure_p5a3_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  policy_from date;
  policy_to date;
  policy_status varchar;
  policy_identity_status varchar;
  policy_residual_component_version_id uuid;
  rule_from date;
  rule_to date;
  rule_status varchar;
  rule_identity_status varchar;
  component_from date;
  component_to date;
  component_status varchar;
  component_identity_status varchar;
BEGIN
  -- Schema 0 remains writable through the pre-P5-A3 contract until the G03
  -- application/API cutover. Schema-1 rows opt into the stronger P5-A3
  -- dependency and validation contract without breaking existing callers.
  IF NEW.structure_schema_version = 0 THEN
    RETURN NEW;
  END IF;

  SELECT version.effective_from, version.effective_to, version.approval_status,
         identity.lifecycle_status, version.residual_component_version_id
    INTO policy_from, policy_to, policy_status, policy_identity_status,
         policy_residual_component_version_id
    FROM compensation.ctc_policy_version version
    JOIN compensation.ctc_policy identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.ctc_policy_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.ctc_policy_version_id;
  IF policy_from IS NULL OR policy_status <> 'APPROVED'
     OR policy_identity_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'schema-1 salary structures require an active approved CTC-policy version'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.residual_component_version_id IS DISTINCT FROM
     policy_residual_component_version_id THEN
    RAISE EXCEPTION 'salary-structure residual component must match its CTC policy'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_from < policy_from
     OR (
       policy_to IS NOT NULL
       AND (NEW.effective_to IS NULL OR NEW.effective_to > policy_to)
     ) THEN
    RAISE EXCEPTION 'salary-structure range must be contained by its CTC-policy version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.eligibility_rule_version_id IS NOT NULL THEN
    SELECT version.effective_from, version.effective_to,
           version.approval_status, identity.lifecycle_status
      INTO rule_from, rule_to, rule_status, rule_identity_status
      FROM compensation.eligibility_rule_version version
      JOIN compensation.eligibility_rule identity
        ON identity.tenant_id = version.tenant_id
       AND identity.id = version.eligibility_rule_id
     WHERE version.tenant_id = NEW.tenant_id
       AND version.id = NEW.eligibility_rule_version_id;
    IF rule_from IS NULL OR rule_status <> 'APPROVED'
       OR rule_identity_status <> 'ACTIVE' THEN
      RAISE EXCEPTION
        'schema-1 salary structures require an active approved eligibility-rule version'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.effective_from < rule_from
       OR (
         rule_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > rule_to)
       ) THEN
      RAISE EXCEPTION 'salary-structure range must be contained by its eligibility-rule version'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  SELECT version.effective_from, version.effective_to,
         version.approval_status, identity.lifecycle_status
    INTO component_from, component_to, component_status,
         component_identity_status
    FROM compensation.pay_component_version version
    JOIN compensation.pay_component identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.component_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.residual_component_version_id;
  IF component_from IS NULL OR component_status <> 'APPROVED'
     OR component_identity_status <> 'ACTIVE' THEN
    RAISE EXCEPTION
      'schema-1 salary structures require an active approved residual component version'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_from < component_from
     OR (
       component_to IS NOT NULL
       AND (NEW.effective_to IS NULL OR NEW.effective_to > component_to)
     ) THEN
    RAISE EXCEPTION 'salary-structure range must be contained by its residual component version'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_p5a3_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id, structure_schema_version, ctc_policy_version_id,
    eligibility_rule_version_id, residual_component_version_id,
    effective_from, effective_to
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_structure_p5a3_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_salary_structure_line_p5a3_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_schema smallint;
  parent_fingerprint varchar(64);
BEGIN
  -- Preserve the schema-0 line contract for existing callers. Schema-1 lines
  -- remain subject to P5-A3 shape, ordering and validation-binding controls.
  SELECT structure_schema_version, validation_fingerprint
    INTO parent_schema, parent_fingerprint
    FROM compensation.salary_structure_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.salary_structure_version_id;

  IF parent_schema IS NULL THEN
    RAISE EXCEPTION 'salary-structure version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF parent_schema <> NEW.line_schema_version THEN
    RAISE EXCEPTION 'salary-structure line schema must match its version schema'
      USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'INSERT' AND parent_fingerprint IS NOT NULL THEN
    RAISE EXCEPTION 'salary-structure lines cannot be appended after validation is bound'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.line_schema_version = 1 THEN
    IF NEW.line_type = 'FIXED' AND NOT (
      NEW.target_amount IS NOT NULL
      AND NEW.target_percentage IS NULL
      AND NEW.percentage_base_code IS NULL
    ) THEN
      RAISE EXCEPTION 'FIXED line metadata must match the fixed target shape'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.line_type = 'PERCENTAGE' AND NOT (
      NEW.target_amount IS NULL
      AND NEW.target_percentage IS NOT NULL
      AND NEW.percentage_base_code IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'PERCENTAGE line metadata must match the percentage target shape'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.line_type = 'RESIDUAL' AND NOT (
      NEW.target_amount IS NULL
      AND NEW.target_percentage IS NULL
      AND NEW.percentage_base_code IS NULL
    ) THEN
      RAISE EXCEPTION 'RESIDUAL line metadata must match the residual target shape'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_line_p5a3_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id, salary_structure_version_id, line_schema_version, line_type,
    target_amount, target_percentage, percentage_base_code
  ON compensation.salary_structure_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_structure_line_p5a3_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_salary_structure_validation_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  structure_schema smallint;
  structure_status varchar;
  structure_from date;
  structure_to date;
  structure_policy uuid;
  structure_rule uuid;
  structure_hash varchar;
BEGIN
  SELECT structure_schema_version, approval_status, effective_from, effective_to,
         ctc_policy_version_id, eligibility_rule_version_id, configuration_hash
    INTO structure_schema, structure_status, structure_from, structure_to,
         structure_policy, structure_rule, structure_hash
    FROM compensation.salary_structure_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.salary_structure_version_id
     AND salary_structure_id = NEW.salary_structure_id;

  IF structure_schema IS NULL THEN
    RAISE EXCEPTION 'salary-structure version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF structure_schema <> 1 OR structure_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'design-time validation requires a schema-1 draft salary structure'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.ctc_policy_version_id IS DISTINCT FROM structure_policy
     OR NEW.eligibility_rule_version_id IS DISTINCT FROM structure_rule THEN
    RAISE EXCEPTION 'validation references must match the exact structure policy and rule versions'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.configuration_hash IS DISTINCT FROM structure_hash THEN
    RAISE EXCEPTION 'validation configuration hash must match the salary structure'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_date < structure_from
     OR (structure_to IS NOT NULL AND NEW.effective_date >= structure_to) THEN
    RAISE EXCEPTION 'validation effective date must be inside the structure range'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_validation_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id, salary_structure_id, salary_structure_version_id,
    ctc_policy_version_id, eligibility_rule_version_id, effective_date,
    configuration_hash
  ON compensation.salary_structure_validation
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_structure_validation_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_validation_line_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  structure_status varchar;
  structure_fingerprint varchar;
  validation_result_hash varchar;
  validation_structure_version_id uuid;
BEGIN
  SELECT structure.approval_status, structure.validation_fingerprint,
         validation.result_hash, validation.salary_structure_version_id
    INTO structure_status, structure_fingerprint, validation_result_hash,
         validation_structure_version_id
    FROM compensation.salary_structure_validation validation
    JOIN compensation.salary_structure_version structure
      ON structure.tenant_id = validation.tenant_id
     AND structure.id = validation.salary_structure_version_id
   WHERE validation.tenant_id = NEW.tenant_id
     AND validation.id = NEW.validation_id;

  IF structure_status IS NULL THEN
    RAISE EXCEPTION 'validation does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF structure_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'validation lines can be added only while the structure is draft'
      USING ERRCODE = '23514';
  END IF;
  IF structure_fingerprint = validation_result_hash THEN
    RAISE EXCEPTION 'validation lines cannot be appended after evidence is bound'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_validation current_validation
      JOIN compensation.salary_structure_validation newer_validation
        ON newer_validation.tenant_id = current_validation.tenant_id
       AND newer_validation.salary_structure_version_id =
         current_validation.salary_structure_version_id
       AND (newer_validation.created_at, newer_validation.id) >
         (current_validation.created_at, current_validation.id)
     WHERE current_validation.tenant_id = NEW.tenant_id
       AND current_validation.id = NEW.validation_id
  ) THEN
    RAISE EXCEPTION 'validation lines can be added only to the latest evidence'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.component_version_id IS NOT NULL AND NOT EXISTS (
    SELECT 1
      FROM compensation.salary_structure_line line
     WHERE line.tenant_id = NEW.tenant_id
       AND line.salary_structure_version_id = validation_structure_version_id
       AND line.component_version_id = NEW.component_version_id
  ) THEN
    RAISE EXCEPTION 'validation component must belong to the exact salary structure'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_validation_line_dependencies
  BEFORE INSERT
  ON compensation.salary_structure_validation_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_validation_line_dependencies();

CREATE OR REPLACE FUNCTION compensation.assert_schema1_structure_approval()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.structure_schema_version = 1
     AND OLD.approval_status <> 'APPROVED'
     AND NEW.approval_status = 'APPROVED' THEN
    IF NEW.created_by = NEW.approved_by THEN
      RAISE EXCEPTION 'schema-1 salary structures require maker-checker approval'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.validation_fingerprint IS NULL OR NOT EXISTS (
      SELECT 1
        FROM compensation.salary_structure_validation validation
       WHERE validation.tenant_id = NEW.tenant_id
         AND validation.salary_structure_version_id = NEW.id
         AND validation.validation_status = 'PASS'
         AND validation.configuration_hash = NEW.configuration_hash
         AND validation.result_hash = NEW.validation_fingerprint
         AND validation.blocking_error_count = 0
         AND NOT EXISTS (
           SELECT 1
             FROM compensation.salary_structure_validation newer
            WHERE newer.tenant_id = validation.tenant_id
              AND newer.salary_structure_version_id = validation.salary_structure_version_id
              AND (newer.created_at, newer.id) > (validation.created_at, validation.id)
         )
    ) THEN
      RAISE EXCEPTION
        'schema-1 salary-structure approval requires the latest passing validation fingerprint'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_p5a3_approval_guard
  BEFORE UPDATE OF approval_status
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_schema1_structure_approval();

CREATE OR REPLACE FUNCTION compensation.require_p5a3_draft_insert()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' AND NEW.approval_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'runtime P5-A3 versions must be created as drafts'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER ctc_policy_version_draft_insert
  BEFORE INSERT ON compensation.ctc_policy_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.require_p5a3_draft_insert();
CREATE TRIGGER eligibility_rule_version_draft_insert
  BEFORE INSERT ON compensation.eligibility_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.require_p5a3_draft_insert();

CREATE OR REPLACE FUNCTION compensation.reject_uncontrolled_p5a3_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable P5-A3 configuration: %.%',
      TG_TABLE_SCHEMA, TG_TABLE_NAME;
  END IF;
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'P5-A3 versions, children and validation evidence cannot be deleted';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER ctc_policy_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.ctc_policy_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();
CREATE TRIGGER ctc_policy_treatment_immutable
  BEFORE UPDATE OR DELETE ON compensation.ctc_policy_treatment
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();
CREATE TRIGGER eligibility_rule_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.eligibility_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();
CREATE TRIGGER eligibility_rule_criterion_immutable
  BEFORE UPDATE OR DELETE ON compensation.eligibility_rule_criterion
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();
CREATE TRIGGER salary_structure_validation_immutable
  BEFORE UPDATE OR DELETE ON compensation.salary_structure_validation
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();
CREATE TRIGGER salary_structure_validation_line_immutable
  BEFORE UPDATE OR DELETE ON compensation.salary_structure_validation_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_uncontrolled_p5a3_mutation();


CREATE OR REPLACE FUNCTION compensation.approve_ctc_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
  target_policy_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.ctc_policy_version version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.approval_status = 'DRAFT'
     AND version.created_by <> p_actor
     AND EXISTS (
       SELECT 1
         FROM compensation.ctc_policy identity
        WHERE identity.tenant_id = version.tenant_id
          AND identity.id = version.ctc_policy_id
          AND identity.lifecycle_status <> 'RETIRED'
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.ctc_policy_version successor
        WHERE successor.tenant_id = version.tenant_id
          AND successor.supersedes_version_id = version.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.pay_component_version component_version
         JOIN compensation.pay_component component_identity
           ON component_identity.tenant_id = component_version.tenant_id
          AND component_identity.id = component_version.component_id
        WHERE component_version.tenant_id = version.tenant_id
          AND component_version.id = version.residual_component_version_id
          AND component_version.component_id = version.residual_component_id
          AND component_version.approval_status = 'APPROVED'
          AND component_identity.lifecycle_status = 'ACTIVE'
          AND component_version.effective_from <= version.effective_from
          AND (
            component_version.effective_to IS NULL
            OR (
              version.effective_to IS NOT NULL
              AND version.effective_to <= component_version.effective_to
            )
          )
     )
     AND 4 = (
       SELECT count(DISTINCT treatment.cost_view)
         FROM compensation.ctc_policy_treatment treatment
        WHERE treatment.tenant_id = version.tenant_id
          AND treatment.ctc_policy_version_id = version.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.ctc_policy_treatment residual_treatment
        WHERE residual_treatment.tenant_id = version.tenant_id
          AND residual_treatment.ctc_policy_version_id = version.id
          AND residual_treatment.component_id = version.residual_component_id
          AND residual_treatment.component_version_id =
            version.residual_component_version_id
          AND residual_treatment.treatment_type <> 'EXCLUDE'
     )
  RETURNING version.ctc_policy_id INTO target_policy_id;

  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected = 1 THEN
    UPDATE compensation.ctc_policy
       SET lifecycle_status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = target_policy_id
       AND lifecycle_status = 'PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.approve_eligibility_rule_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
  target_rule_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.eligibility_rule_version version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.approval_status = 'DRAFT'
     AND version.created_by <> p_actor
     AND EXISTS (
       SELECT 1
         FROM compensation.eligibility_rule identity
        WHERE identity.tenant_id = version.tenant_id
          AND identity.id = version.eligibility_rule_id
          AND identity.lifecycle_status <> 'RETIRED'
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.eligibility_rule_version successor
        WHERE successor.tenant_id = version.tenant_id
          AND successor.supersedes_version_id = version.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.eligibility_rule_criterion criterion
        WHERE criterion.tenant_id = version.tenant_id
          AND criterion.eligibility_rule_version_id = version.id
     )
  RETURNING version.eligibility_rule_id INTO target_rule_id;

  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected = 1 THEN
    UPDATE compensation.eligibility_rule
       SET lifecycle_status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = target_rule_id
       AND lifecycle_status = 'PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.bind_salary_structure_validation(
  p_tenant_id uuid,
  p_structure_version_id uuid,
  p_validation_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'actor and change timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.salary_structure_version structure
     SET validation_fingerprint = validation.result_hash,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = structure.version_no + 1
    FROM compensation.salary_structure_validation validation
   WHERE structure.tenant_id = p_tenant_id
     AND structure.id = p_structure_version_id
     AND structure.version_no = p_expected_version
     AND structure.structure_schema_version = 1
     AND structure.approval_status = 'DRAFT'
     AND validation.tenant_id = structure.tenant_id
     AND validation.id = p_validation_id
     AND validation.salary_structure_version_id = structure.id
     AND validation.validation_status = 'PASS'
     AND validation.blocking_error_count = 0
     AND validation.configuration_hash = structure.configuration_hash
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation newer
        WHERE newer.tenant_id = validation.tenant_id
          AND newer.salary_structure_version_id = validation.salary_structure_version_id
          AND (newer.created_at, newer.id) > (validation.created_at, validation.id)
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation_line validation_line
        WHERE validation_line.tenant_id = validation.tenant_id
          AND validation_line.validation_id = validation.id
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_line structure_line
        WHERE structure_line.tenant_id = structure.tenant_id
          AND structure_line.salary_structure_version_id = structure.id
          AND NOT EXISTS (
            SELECT 1
              FROM compensation.salary_structure_validation_line validation_line
             WHERE validation_line.tenant_id = structure_line.tenant_id
               AND validation_line.validation_id = validation.id
               AND validation_line.component_version_id =
                 structure_line.component_version_id
          )
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation_line validation_line
        WHERE validation_line.tenant_id = validation.tenant_id
          AND validation_line.validation_id = validation.id
          AND validation_line.component_version_id IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
              FROM compensation.salary_structure_line structure_line
             WHERE structure_line.tenant_id = structure.tenant_id
               AND structure_line.salary_structure_version_id = structure.id
               AND structure_line.component_version_id =
                 validation_line.component_version_id
          )
     );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.approve_salary_structure_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.salary_structure_version version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.approval_status = 'DRAFT'
     AND (
       version.structure_schema_version = 0
       OR version.created_by <> p_actor
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_version successor
        WHERE successor.tenant_id = version.tenant_id
          AND successor.supersedes_version_id = version.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_structure_line line
        WHERE line.tenant_id = version.tenant_id
          AND line.salary_structure_version_id = version.id
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_line line
         LEFT JOIN compensation.pay_component_version component_version
           ON component_version.tenant_id = line.tenant_id
          AND component_version.id = line.component_version_id
         LEFT JOIN compensation.pay_component component_identity
           ON component_identity.tenant_id = component_version.tenant_id
          AND component_identity.id = component_version.component_id
        WHERE line.tenant_id = version.tenant_id
          AND line.salary_structure_version_id = version.id
          AND (
            line.line_schema_version <> version.structure_schema_version
            OR component_version.id IS NULL
            OR component_version.approval_status <> 'APPROVED'
            OR (
              version.structure_schema_version = 1
              AND component_identity.lifecycle_status <> 'ACTIVE'
            )
            OR line.effective_from < version.effective_from
            OR (
              version.effective_to IS NOT NULL
              AND (line.effective_to IS NULL OR line.effective_to > version.effective_to)
            )
            OR line.effective_from < component_version.effective_from
            OR (
              component_version.effective_to IS NOT NULL
              AND (
                line.effective_to IS NULL
                OR line.effective_to > component_version.effective_to
              )
            )
            OR (
              line.percentage_base_code IS NOT NULL
              AND NOT EXISTS (
                SELECT 1
                  FROM compensation.salary_structure_line base_line
                  JOIN compensation.pay_component_version base_version
                    ON base_version.tenant_id = base_line.tenant_id
                   AND base_version.id = base_line.component_version_id
                  JOIN compensation.pay_component base_component
                    ON base_component.tenant_id = base_version.tenant_id
                   AND base_component.id = base_version.component_id
                 WHERE base_line.tenant_id = line.tenant_id
                   AND base_line.salary_structure_version_id =
                     line.salary_structure_version_id
                   AND base_line.sequence_no < line.sequence_no
                   AND base_component.code = line.percentage_base_code
              )
            )
          )
     )
     AND (
       version.structure_schema_version = 0
       OR (
         version.validation_fingerprint IS NOT NULL
         AND 1 = (
           SELECT count(*)
             FROM compensation.salary_structure_line residual_line
            WHERE residual_line.tenant_id = version.tenant_id
              AND residual_line.salary_structure_version_id = version.id
              AND residual_line.line_schema_version = 1
              AND residual_line.line_type = 'RESIDUAL'
              AND residual_line.component_version_id =
                version.residual_component_version_id
         )
       )
     );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.end_date_ctc_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_effective_to IS NULL
     OR p_actor IS NULL OR btrim(p_actor) = ''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.ctc_policy_version version
     SET effective_to = p_effective_to,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.version_no = p_expected_version
     AND version.effective_from < p_effective_to
     AND (version.effective_to IS NULL OR version.effective_to > p_effective_to)
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.ctc_policy_treatment treatment
        WHERE treatment.tenant_id = version.tenant_id
          AND treatment.ctc_policy_version_id = version.id
          AND treatment.effective_from >= p_effective_to
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_version structure
        WHERE structure.tenant_id = version.tenant_id
          AND structure.ctc_policy_version_id = version.id
          AND (
            structure.effective_from >= p_effective_to
            OR structure.effective_to IS NULL
            OR structure.effective_to > p_effective_to
          )
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation validation
        WHERE validation.tenant_id = version.tenant_id
          AND validation.ctc_policy_version_id = version.id
          AND validation.effective_date >= p_effective_to
     );

  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected = 1 THEN
    UPDATE compensation.ctc_policy_treatment
       SET effective_to = p_effective_to,
           updated_at = p_changed_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND ctc_policy_version_id = p_version_id
       AND (effective_to IS NULL OR effective_to > p_effective_to);
  END IF;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.end_date_eligibility_rule_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_effective_to IS NULL
     OR p_actor IS NULL OR btrim(p_actor) = ''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.eligibility_rule_version version
     SET effective_to = p_effective_to,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.version_no = p_expected_version
     AND version.effective_from < p_effective_to
     AND (version.effective_to IS NULL OR version.effective_to > p_effective_to)
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_version structure
        WHERE structure.tenant_id = version.tenant_id
          AND structure.eligibility_rule_version_id = version.id
          AND (
            structure.effective_from >= p_effective_to
            OR structure.effective_to IS NULL
            OR structure.effective_to > p_effective_to
          )
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation validation
        WHERE validation.tenant_id = version.tenant_id
          AND validation.eligibility_rule_version_id = version.id
          AND validation.effective_date >= p_effective_to
     );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.retire_ctc_policy(
  p_tenant_id uuid,
  p_policy_id uuid,
  p_effective_date date,
  p_expected_version bigint,
  p_reason varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_effective_date IS NULL
     OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
     OR p_actor IS NULL OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement date, reason, actor and timestamp are required'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.ctc_policy_version version
     WHERE version.tenant_id = p_tenant_id
       AND version.ctc_policy_id = p_policy_id
       AND version.approval_status = 'APPROVED'
       AND (version.effective_to IS NULL OR version.effective_to > p_effective_date)
  ) OR EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version structure
      JOIN compensation.ctc_policy_version version
        ON version.tenant_id = structure.tenant_id
       AND version.id = structure.ctc_policy_version_id
     WHERE structure.tenant_id = p_tenant_id
       AND version.ctc_policy_id = p_policy_id
       AND (structure.effective_to IS NULL OR structure.effective_to > p_effective_date)
  ) THEN
    RAISE EXCEPTION 'CTC policy has active or future approved dependencies'
      USING ERRCODE = 'P5A32';
  END IF;

  UPDATE compensation.ctc_policy
     SET lifecycle_status = 'RETIRED',
         retirement_effective_date = p_effective_date,
         retirement_reason = btrim(p_reason),
         retired_at = p_changed_at,
         retired_by = p_actor,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_policy_id
     AND lifecycle_status <> 'RETIRED'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.retire_eligibility_rule(
  p_tenant_id uuid,
  p_rule_id uuid,
  p_effective_date date,
  p_expected_version bigint,
  p_reason varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_effective_date IS NULL
     OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
     OR p_actor IS NULL OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement date, reason, actor and timestamp are required'
      USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.eligibility_rule_version version
     WHERE version.tenant_id = p_tenant_id
       AND version.eligibility_rule_id = p_rule_id
       AND version.approval_status = 'APPROVED'
       AND (version.effective_to IS NULL OR version.effective_to > p_effective_date)
  ) OR EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version structure
      JOIN compensation.eligibility_rule_version version
        ON version.tenant_id = structure.tenant_id
       AND version.id = structure.eligibility_rule_version_id
     WHERE structure.tenant_id = p_tenant_id
       AND version.eligibility_rule_id = p_rule_id
       AND (structure.effective_to IS NULL OR structure.effective_to > p_effective_date)
  ) THEN
    RAISE EXCEPTION 'eligibility rule has active or future approved dependencies'
      USING ERRCODE = 'P5A33';
  END IF;

  UPDATE compensation.eligibility_rule
     SET lifecycle_status = 'RETIRED',
         retirement_effective_date = p_effective_date,
         retirement_reason = btrim(p_reason),
         retired_at = p_changed_at,
         retired_by = p_actor,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_rule_id
     AND lifecycle_status <> 'RETIRED'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.is_typed_eligibility_value(
  jsonb, varchar, varchar
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compensation.is_typed_eligibility_value(
  jsonb, varchar, varchar
) TO payroll_app;
REVOKE ALL ON FUNCTION compensation.assert_p5a3_identity_accepts_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_ctc_policy_treatment_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_eligibility_criterion_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_salary_structure_p5a3_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_salary_structure_line_p5a3_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_salary_structure_validation_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_validation_line_dependencies()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_schema1_structure_approval()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.require_p5a3_draft_insert()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_uncontrolled_p5a3_mutation()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_ctc_policy_version(
  uuid, uuid, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_eligibility_rule_version(
  uuid, uuid, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.bind_salary_structure_validation(
  uuid, uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_ctc_policy_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_eligibility_rule_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_ctc_policy(
  uuid, uuid, date, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_eligibility_rule(
  uuid, uuid, date, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.approve_ctc_policy_version(
  uuid, uuid, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_eligibility_rule_version(
  uuid, uuid, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.bind_salary_structure_validation(
  uuid, uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_ctc_policy_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_eligibility_rule_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_ctc_policy(
  uuid, uuid, date, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_eligibility_rule(
  uuid, uuid, date, bigint, varchar, varchar, timestamptz
) TO payroll_app;

ALTER TABLE compensation.ctc_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.ctc_policy FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.ctc_policy_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.ctc_policy_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.ctc_policy_treatment ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.ctc_policy_treatment FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule_criterion ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.eligibility_rule_criterion FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_validation ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_validation FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_validation_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_validation_line FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON compensation.ctc_policy
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.ctc_policy_version
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.ctc_policy_treatment
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.eligibility_rule
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.eligibility_rule_version
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.eligibility_rule_criterion
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.salary_structure_validation
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.salary_structure_validation_line
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE compensation.salary_structure_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line
  FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT ON
  compensation.ctc_policy,
  compensation.ctc_policy_version,
  compensation.ctc_policy_treatment,
  compensation.eligibility_rule,
  compensation.eligibility_rule_version,
  compensation.eligibility_rule_criterion,
  compensation.salary_structure_validation,
  compensation.salary_structure_validation_line
TO payroll_app;

REVOKE UPDATE, DELETE ON
  compensation.ctc_policy,
  compensation.ctc_policy_version,
  compensation.ctc_policy_treatment,
  compensation.eligibility_rule,
  compensation.eligibility_rule_version,
  compensation.eligibility_rule_criterion,
  compensation.salary_structure_validation,
  compensation.salary_structure_validation_line
FROM payroll_app;

REVOKE CREATE ON SCHEMA compensation FROM payroll_app;

COMMENT ON COLUMN compensation.salary_structure_version.structure_schema_version IS
  '0 preserves V020 history; 1 is the P5-A3 salary-structure design contract.';
COMMENT ON COLUMN compensation.salary_structure_line.line_schema_version IS
  '0 preserves V020 lines; 1 carries P5-A3 design metadata.';
COMMENT ON TABLE compensation.ctc_policy IS
  'Stable tenant-scoped identity for a policy-defined cost-to-company view.';
COMMENT ON TABLE compensation.ctc_policy_version IS
  'Immutable effective-dated CTC policy version with controlled G02-B lifecycle commands.';
COMMENT ON TABLE compensation.ctc_policy_treatment IS
  'Exact component-version treatment in one distinguishable CTC cost view.';
COMMENT ON TABLE compensation.eligibility_rule IS
  'Stable tenant-scoped identity for controlled compensation eligibility rules.';
COMMENT ON TABLE compensation.eligibility_rule_version IS
  'Immutable effective-dated conjunctive eligibility-rule version.';
COMMENT ON TABLE compensation.eligibility_rule_criterion IS
  'Ordered typed allow-listed criterion belonging to an exact rule version.';
COMMENT ON TABLE compensation.salary_structure_validation IS
  'Immutable design-time validation evidence; never an official payroll result.';
COMMENT ON TABLE compensation.salary_structure_validation_line IS
  'Immutable ordered component evidence for a design-time validation run.';
