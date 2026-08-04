-- P5-A2 general pay-component catalogue and named payroll bases.
--
-- Forward-only from V031. V001-V031 remain immutable.
-- Existing pay-component UUIDs, approval history and component_type calculation
-- direction contract remain unchanged. Existing
-- component versions are explicitly schema 0 and readable; new or corrected
-- versions are schema 1 and require complete behavioural classification.

ALTER TABLE compensation.pay_component NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version NO FORCE ROW LEVEL SECURITY;

ALTER TABLE compensation.pay_component
  ADD COLUMN lifecycle_status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN ownership_scope varchar(24) NOT NULL DEFAULT 'TENANT',
  ADD COLUMN country_code varchar(2),
  ADD COLUMN protected_flag boolean NOT NULL DEFAULT false,
  ADD COLUMN confidentiality_level varchar(20) NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160);

UPDATE compensation.pay_component
   SET lifecycle_status = 'ACTIVE';

ALTER TABLE compensation.pay_component
  ALTER COLUMN lifecycle_status SET DEFAULT 'PENDING_APPROVAL',
  ADD CONSTRAINT pay_component_lifecycle_status_ck
    CHECK (lifecycle_status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  ADD CONSTRAINT pay_component_ownership_scope_ck
    CHECK (ownership_scope IN ('SYSTEM', 'COUNTRY_PACK', 'TENANT')),
  ADD CONSTRAINT pay_component_country_code_ck
    CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
  ADD CONSTRAINT pay_component_confidentiality_ck
    CHECK (confidentiality_level IN ('STANDARD', 'RESTRICTED', 'EXECUTIVE')),
  ADD CONSTRAINT pay_component_retirement_evidence_ck
    CHECK (
      (
        lifecycle_status <> 'RETIRED'
        AND retirement_effective_date IS NULL
        AND retirement_reason IS NULL
        AND retired_at IS NULL
        AND retired_by IS NULL
      )
      OR
      (
        lifecycle_status = 'RETIRED'
        AND retirement_effective_date IS NOT NULL
        AND retirement_reason IS NOT NULL
        AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
        AND retired_at IS NOT NULL
        AND retired_by IS NOT NULL
        AND length(btrim(retired_by)) BETWEEN 1 AND 160
      )
    );

ALTER TABLE compensation.pay_component_version
  ADD COLUMN catalogue_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN component_category varchar(40),
  ADD COLUMN component_subcategory varchar(60),
  ADD COLUMN cash_impact varchar(20),
  ADD COLUMN payee_type varchar(30),
  ADD COLUMN payment_channel varchar(30),
  ADD COLUMN settlement_timing varchar(30),
  ADD COLUMN payslip_visibility varchar(20),
  ADD COLUMN zero_value_visibility varchar(20),
  ADD COLUMN negative_value_policy varchar(20),
  ADD COLUMN frequency varchar(30),
  ADD COLUMN value_nature varchar(30),
  ADD COLUMN amount_representation varchar(30),
  ADD COLUMN tax_treatment varchar(30),
  ADD COLUMN payroll_timing varchar(30);

UPDATE compensation.pay_component_version
   SET catalogue_schema_version = 0;

ALTER TABLE compensation.pay_component_version
  -- Privileged legacy fixtures may omit this column and remain schema 0.
  -- Runtime application writers explicitly insert schema 1 and the child
  -- lifecycle trigger below rejects schema-0 inserts by payroll_app.
  ALTER COLUMN catalogue_schema_version SET DEFAULT 0,
  ADD CONSTRAINT pay_component_version_catalogue_schema_ck
    CHECK (catalogue_schema_version IN (0, 1)),
  ADD CONSTRAINT pay_component_version_catalogue_shape_ck
    CHECK (
      (
        catalogue_schema_version = 0
        AND component_category IS NULL
        AND component_subcategory IS NULL
        AND cash_impact IS NULL
        AND payee_type IS NULL
        AND payment_channel IS NULL
        AND settlement_timing IS NULL
        AND payslip_visibility IS NULL
        AND zero_value_visibility IS NULL
        AND negative_value_policy IS NULL
        AND frequency IS NULL
        AND value_nature IS NULL
        AND amount_representation IS NULL
        AND tax_treatment IS NULL
        AND payroll_timing IS NULL
      )
      OR
      (
        catalogue_schema_version = 1
        AND component_category IS NOT NULL
        AND component_category IN (
          'CASH_EARNING',
          'EMPLOYEE_DEDUCTION',
          'EMPLOYER_CONTRIBUTION',
          'EMPLOYER_PROVISION',
          'REIMBURSEMENT',
          'BENEFIT',
          'TAXABLE_PERQUISITE',
          'NOTIONAL',
          'ACCRUAL'
        )
        AND (
          component_subcategory IS NULL
          OR component_subcategory ~ '^[A-Z][A-Z0-9_]{1,59}$'
        )
        AND cash_impact IS NOT NULL
        AND cash_impact IN ('INCREASE', 'DECREASE', 'NONE')
        AND payee_type IS NOT NULL
        AND payee_type IN (
          'EMPLOYEE', 'AUTHORITY', 'LENDER', 'BENEFIT_PROVIDER', 'INTERNAL', 'NONE'
        )
        AND payment_channel IS NOT NULL
        AND payment_channel IN (
          'PAYROLL_BANK', 'SEPARATE_BANK', 'VENDOR', 'STATUTORY_REMITTANCE', 'NONE'
        )
        AND settlement_timing IS NOT NULL
        AND settlement_timing IN (
          'CURRENT_PERIOD', 'DEFERRED', 'ACCRUAL', 'EXIT', 'ANNUAL', 'NONE'
        )
        AND payslip_visibility IS NOT NULL
        AND payslip_visibility IN ('SHOW', 'SUMMARISE', 'HIDE', 'CONDITIONAL')
        AND zero_value_visibility IS NOT NULL
        AND zero_value_visibility IN ('SHOW', 'SUPPRESS')
        AND negative_value_policy IS NOT NULL
        AND negative_value_policy IN ('ALLOW', 'PROHIBIT', 'REVERSAL_ONLY')
        AND frequency IS NOT NULL
        AND frequency IN (
          'PER_PAYROLL_PERIOD', 'MONTHLY', 'WEEKLY', 'DAILY', 'ANNUAL',
          'ONE_TIME', 'EVENT_DRIVEN', 'AD_HOC', 'ON_EXIT', 'ON_JOINING',
          'ON_CONFIRMATION', 'ON_ANNIVERSARY'
        )
        AND value_nature IS NOT NULL
        AND value_nature IN (
          'FIXED', 'VARIABLE', 'DERIVED', 'EXTERNAL_INPUT', 'EMPLOYEE_ELECTION',
          'EMPLOYER_DISCRETION', 'STATUTORY', 'BALANCE_RECOVERY', 'PROVISION',
          'NOTIONAL'
        )
        AND amount_representation IS NOT NULL
        AND amount_representation IN (
          'ANNUAL_AMOUNT', 'MONTHLY_AMOUNT', 'DAILY_RATE', 'HOURLY_RATE',
          'PER_UNIT_RATE', 'PERCENTAGE', 'SLAB', 'QUANTITY_RATE',
          'FORMULA_RESULT', 'EXTERNAL_VALUE'
        )
        AND tax_treatment IS NOT NULL
        AND tax_treatment IN (
          'DELEGATED', 'TAXABLE', 'EXEMPT', 'PARTIALLY_EXEMPT',
          'PROOF_DEPENDENT', 'REGIME_DEPENDENT', 'PERQUISITE',
          'REIMBURSEMENT', 'TAX_ONLY_NOTIONAL'
        )
        AND payroll_timing IS NOT NULL
        AND payroll_timing IN (
          'REGULAR', 'OFF_CYCLE_ONLY', 'REGULAR_AND_OFF_CYCLE',
          'FINAL_SETTLEMENT_ONLY', 'ANNUAL', 'CORRECTION',
          'NON_PAYROLL_REPORTING'
        )
      )
    );

CREATE INDEX pay_component_lifecycle_ix
  ON compensation.pay_component(tenant_id, lifecycle_status, code);

CREATE INDEX pay_component_version_catalogue_ix
  ON compensation.pay_component_version(
    tenant_id,
    catalogue_schema_version,
    component_category,
    effective_from
  );

CREATE TABLE compensation.payroll_base (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(160) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  ownership_scope varchar(24) NOT NULL DEFAULT 'TENANT',
  country_code varchar(2),
  protected_flag boolean NOT NULL DEFAULT false,
  confidentiality_level varchar(20) NOT NULL DEFAULT 'STANDARD',
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
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (btrim(name) <> ''),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (ownership_scope IN ('SYSTEM', 'COUNTRY_PACK', 'TENANT')),
  CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
  CHECK (confidentiality_level IN ('STANDARD', 'RESTRICTED', 'EXECUTIVE')),
  CHECK (
    (
      lifecycle_status <> 'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL
    )
    OR
    (
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

CREATE TABLE compensation.payroll_base_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_base_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  catalogue_schema_version smallint NOT NULL DEFAULT 1,
  base_category varchar(24) NOT NULL,
  aggregation_method varchar(20) NOT NULL,
  description varchar(1000),
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
  UNIQUE (tenant_id, id, payroll_base_id),
  UNIQUE (tenant_id, payroll_base_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (catalogue_schema_version = 1),
  CHECK (base_category IN ('CALCULATION', 'STATUTORY', 'TAX', 'CTC', 'REPORTING')),
  CHECK (aggregation_method IN ('SUM', 'AVERAGE', 'MAXIMUM', 'MINIMUM', 'CUSTOM')),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  FOREIGN KEY (tenant_id, payroll_base_id)
    REFERENCES compensation.payroll_base(tenant_id, id),
  CONSTRAINT payroll_base_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, payroll_base_id)
    REFERENCES compensation.payroll_base_version(tenant_id, id, payroll_base_id)
);

ALTER TABLE compensation.payroll_base_version
  ADD CONSTRAINT payroll_base_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_base_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX payroll_base_version_one_successor_uk
  ON compensation.payroll_base_version(tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX payroll_base_version_current_ix
  ON compensation.payroll_base_version(
    tenant_id,
    payroll_base_id,
    effective_from DESC
  );

CREATE TABLE compensation.component_base_membership (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_base_id uuid NOT NULL,
  payroll_base_version_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  membership_sequence integer NOT NULL,
  membership_type varchar(30) NOT NULL,
  inclusion_percent numeric(12,8) NOT NULL DEFAULT 100,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_membership_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_base_id, component_id),
  UNIQUE (
    tenant_id,
    payroll_base_id,
    component_id,
    membership_sequence
  ),
  CHECK (membership_sequence > 0),
  CHECK (
    membership_type IN (
      'INCLUDE',
      'EXCLUDE',
      'ADD_BACK',
      'ELIGIBILITY_ONLY',
      'CONTRIBUTION_ONLY',
      'NOTIONAL'
    )
  ),
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
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  CHECK (
    supersedes_membership_id IS NULL
    OR supersedes_membership_id <> id
  ),
  CONSTRAINT component_base_membership_base_version_fk
    FOREIGN KEY (tenant_id, payroll_base_version_id, payroll_base_id)
    REFERENCES compensation.payroll_base_version(
      tenant_id,
      id,
      payroll_base_id
    ),
  CONSTRAINT component_base_membership_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(
      tenant_id,
      id,
      component_id
    ),
  CONSTRAINT component_base_membership_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_membership_id,
      payroll_base_id,
      component_id
    ) REFERENCES compensation.component_base_membership(
      tenant_id,
      id,
      payroll_base_id,
      component_id
    )
);

ALTER TABLE compensation.component_base_membership
  ADD CONSTRAINT component_base_membership_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_base_id WITH =,
    component_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX component_base_membership_one_successor_uk
  ON compensation.component_base_membership(
    tenant_id,
    supersedes_membership_id
  )
  WHERE supersedes_membership_id IS NOT NULL;

CREATE INDEX component_base_membership_lookup_ix
  ON compensation.component_base_membership(
    tenant_id,
    payroll_base_version_id,
    component_version_id,
    effective_from,
    effective_to
  );

ALTER TABLE compensation.payroll_base ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.payroll_base_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_base_membership ENABLE ROW LEVEL SECURITY;

CREATE POLICY payroll_base_tenant_policy
  ON compensation.payroll_base
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE POLICY payroll_base_version_tenant_policy
  ON compensation.payroll_base_version
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE POLICY component_base_membership_tenant_policy
  ON compensation.component_base_membership
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE compensation.payroll_base FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.payroll_base_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_base_membership FORCE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION compensation.assert_catalogue_identity_accepts_child()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  identity_status varchar(24);
BEGIN
  IF TG_TABLE_NAME = 'pay_component_version'
     AND NEW.catalogue_schema_version = 0
     AND current_user = 'payroll_app' THEN
    RAISE EXCEPTION
      'runtime pay-component versions must use catalogue schema 1'
      USING ERRCODE = '23514';
  END IF;
  IF TG_TABLE_NAME = 'pay_component_version' THEN
    SELECT lifecycle_status
      INTO identity_status
      FROM compensation.pay_component
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.component_id;
  ELSIF TG_TABLE_NAME = 'payroll_base_version' THEN
    SELECT lifecycle_status
      INTO identity_status
      FROM compensation.payroll_base
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.payroll_base_id;
  ELSE
    RETURN NEW;
  END IF;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'catalogue identity does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired catalogue identities cannot accept new versions'
      USING ERRCODE = 'P5A22';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_component_version_identity_lifecycle
  BEFORE INSERT ON compensation.pay_component_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_catalogue_identity_accepts_child();

CREATE TRIGGER payroll_base_version_identity_lifecycle
  BEFORE INSERT ON compensation.payroll_base_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_catalogue_identity_accepts_child();

CREATE OR REPLACE FUNCTION compensation.reject_catalogue_child_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable compensation catalogue row: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'compensation catalogue versions and memberships cannot be deleted';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_base_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.payroll_base_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_catalogue_child_mutation();

CREATE TRIGGER component_base_membership_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_base_membership
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_catalogue_child_mutation();

CREATE OR REPLACE FUNCTION compensation.approve_pay_component_version(
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
  target_component_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.pay_component_version v
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE v.tenant_id = p_tenant_id
     AND v.id = p_version_id
     AND v.approval_status = 'DRAFT'
     AND (
       (
         v.catalogue_schema_version = 1
         AND v.created_by <> p_actor
       )
       OR (
         v.catalogue_schema_version = 0
         AND session_user <> 'payroll_app'
       )
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.pay_component_version successor
        WHERE successor.tenant_id = v.tenant_id
          AND successor.supersedes_version_id = v.id
     )
  RETURNING v.component_id INTO target_component_id;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE compensation.pay_component
       SET lifecycle_status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = target_component_id
       AND lifecycle_status = 'PENDING_APPROVAL';
  END IF;

  RETURN affected;
END $$;

CREATE FUNCTION compensation.approve_payroll_base_version(
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
  target_base_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.payroll_base_version v
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE v.tenant_id = p_tenant_id
     AND v.id = p_version_id
     AND v.approval_status = 'DRAFT'
     AND v.created_by <> p_actor
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.payroll_base_version successor
        WHERE successor.tenant_id = v.tenant_id
          AND successor.supersedes_version_id = v.id
     )
  RETURNING v.payroll_base_id INTO target_base_id;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE compensation.payroll_base
       SET lifecycle_status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = target_base_id
       AND lifecycle_status = 'PENDING_APPROVAL';
  END IF;

  RETURN affected;
END $$;

CREATE FUNCTION compensation.approve_component_base_membership(
  p_tenant_id uuid,
  p_membership_id uuid,
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
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.component_base_membership membership
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE membership.tenant_id = p_tenant_id
     AND membership.id = p_membership_id
     AND membership.approval_status = 'DRAFT'
     AND membership.created_by <> p_actor
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.component_base_membership successor
        WHERE successor.tenant_id = membership.tenant_id
          AND successor.supersedes_membership_id = membership.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.payroll_base_version base_version
         JOIN compensation.payroll_base base_identity
           ON base_identity.tenant_id = base_version.tenant_id
          AND base_identity.id = base_version.payroll_base_id
        WHERE base_version.tenant_id = membership.tenant_id
          AND base_version.id = membership.payroll_base_version_id
          AND base_version.payroll_base_id = membership.payroll_base_id
          AND base_version.approval_status = 'APPROVED'
          AND base_identity.lifecycle_status = 'ACTIVE'
          AND base_version.effective_from <= membership.effective_from
          AND (
            base_version.effective_to IS NULL
            OR (
              membership.effective_to IS NOT NULL
              AND membership.effective_to <= base_version.effective_to
            )
          )
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.pay_component_version component_version
         JOIN compensation.pay_component component_identity
           ON component_identity.tenant_id = component_version.tenant_id
          AND component_identity.id = component_version.component_id
        WHERE component_version.tenant_id = membership.tenant_id
          AND component_version.id = membership.component_version_id
          AND component_version.component_id = membership.component_id
          AND component_version.approval_status = 'APPROVED'
          AND component_identity.lifecycle_status = 'ACTIVE'
          AND component_version.effective_from <= membership.effective_from
          AND (
            component_version.effective_to IS NULL
            OR (
              membership.effective_to IS NOT NULL
              AND membership.effective_to <= component_version.effective_to
            )
          )
     );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.end_date_payroll_base_version(
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
  UPDATE compensation.payroll_base_version
     SET effective_to = p_effective_to,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND version_no = p_expected_version
     AND effective_from < p_effective_to
     AND (effective_to IS NULL OR effective_to > p_effective_to);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.end_date_component_base_membership(
  p_tenant_id uuid,
  p_membership_id uuid,
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
  UPDATE compensation.component_base_membership
     SET effective_to = p_effective_to,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_membership_id
     AND version_no = p_expected_version
     AND effective_from < p_effective_to
     AND (effective_to IS NULL OR effective_to > p_effective_to);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.retire_pay_component(
  p_tenant_id uuid,
  p_component_id uuid,
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
      FROM compensation.pay_component_version version
     WHERE version.tenant_id = p_tenant_id
       AND version.component_id = p_component_id
       AND version.approval_status = 'APPROVED'
       AND (version.effective_to IS NULL OR version.effective_to > p_effective_date)
  ) OR EXISTS (
    SELECT 1
      FROM compensation.salary_structure_line line
      JOIN compensation.pay_component_version version
        ON version.tenant_id = line.tenant_id
       AND version.id = line.component_version_id
     WHERE line.tenant_id = p_tenant_id
       AND version.component_id = p_component_id
       AND (line.effective_to IS NULL OR line.effective_to > p_effective_date)
  ) OR EXISTS (
    SELECT 1
      FROM compensation.component_base_membership membership
     WHERE membership.tenant_id = p_tenant_id
       AND membership.component_id = p_component_id
       AND membership.approval_status = 'APPROVED'
       AND (membership.effective_to IS NULL OR membership.effective_to > p_effective_date)
  ) THEN
    RAISE EXCEPTION 'pay component has active or future approved dependencies'
      USING ERRCODE = 'P5A23';
  END IF;

  UPDATE compensation.pay_component
     SET lifecycle_status = 'RETIRED',
         retirement_effective_date = p_effective_date,
         retirement_reason = btrim(p_reason),
         retired_at = p_changed_at,
         retired_by = p_actor,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_component_id
     AND lifecycle_status <> 'RETIRED'
     AND version_no = p_expected_version
     AND p_effective_date IS NOT NULL
     AND p_reason IS NOT NULL
     AND length(btrim(p_reason)) BETWEEN 1 AND 500;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.retire_payroll_base(
  p_tenant_id uuid,
  p_base_id uuid,
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
      FROM compensation.payroll_base_version version
     WHERE version.tenant_id = p_tenant_id
       AND version.payroll_base_id = p_base_id
       AND version.approval_status = 'APPROVED'
       AND (version.effective_to IS NULL OR version.effective_to > p_effective_date)
  ) OR EXISTS (
    SELECT 1
      FROM compensation.component_base_membership membership
     WHERE membership.tenant_id = p_tenant_id
       AND membership.payroll_base_id = p_base_id
       AND membership.approval_status = 'APPROVED'
       AND (membership.effective_to IS NULL OR membership.effective_to > p_effective_date)
  ) THEN
    RAISE EXCEPTION 'payroll base has active or future approved dependencies'
      USING ERRCODE = 'P5A24';
  END IF;

  UPDATE compensation.payroll_base
     SET lifecycle_status = 'RETIRED',
         retirement_effective_date = p_effective_date,
         retirement_reason = btrim(p_reason),
         retired_at = p_changed_at,
         retired_by = p_actor,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_base_id
     AND lifecycle_status <> 'RETIRED'
     AND version_no = p_expected_version
     AND p_effective_date IS NOT NULL
     AND p_reason IS NOT NULL
     AND length(btrim(p_reason)) BETWEEN 1 AND 500;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.assert_catalogue_identity_accepts_child() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_catalogue_child_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_pay_component_version(uuid, uuid, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_payroll_base_version(uuid, uuid, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_component_base_membership(uuid, uuid, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_payroll_base_version(uuid, uuid, date, bigint, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_component_base_membership(uuid, uuid, date, bigint, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_pay_component(uuid, uuid, date, bigint, varchar, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_payroll_base(uuid, uuid, date, bigint, varchar, varchar, timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.approve_pay_component_version(uuid, uuid, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_payroll_base_version(uuid, uuid, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_component_base_membership(uuid, uuid, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_payroll_base_version(uuid, uuid, date, bigint, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_component_base_membership(uuid, uuid, date, bigint, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_pay_component(uuid, uuid, date, bigint, varchar, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_payroll_base(uuid, uuid, date, bigint, varchar, varchar, timestamptz) TO payroll_app;

GRANT SELECT, INSERT
  ON compensation.payroll_base,
     compensation.payroll_base_version,
     compensation.component_base_membership
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON compensation.pay_component,
     compensation.pay_component_version,
     compensation.payroll_base,
     compensation.payroll_base_version,
     compensation.component_base_membership
  FROM payroll_app;

ALTER TABLE compensation.pay_component FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version FORCE ROW LEVEL SECURITY;

COMMENT ON COLUMN compensation.pay_component_version.catalogue_schema_version IS
  '0 preserves legacy approved history; 1 requires complete P5-A2 behavioural classification.';
COMMENT ON TABLE compensation.payroll_base IS
  'Stable tenant-scoped named payroll-base identity.';
COMMENT ON TABLE compensation.payroll_base_version IS
  'Immutable effective-dated named payroll-base definition.';
COMMENT ON TABLE compensation.component_base_membership IS
  'Append-only exact component-version to payroll-base-version membership.';
