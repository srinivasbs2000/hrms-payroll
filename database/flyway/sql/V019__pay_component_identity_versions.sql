-- S2-03 pay-component identity/version foundation.
--
-- Preserve the Sprint 0 pay-component and pay-component-version identifiers so
-- salary-structure lines continue to reference the exact historical component
-- version. Add controlled lifecycle metadata, effective-date protection,
-- formula invariants and runtime immutability.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM compensation.pay_component
    WHERE btrim(name) = ''
  ) THEN
    RAISE EXCEPTION 'existing pay-component names must not be blank';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM compensation.pay_component_version
    WHERE version_no_business < 1
       OR status NOT IN ('DRAFT', 'APPROVED', 'REJECTED')
       OR formula_type IS NULL
       OR formula_type !~ '^[A-Z][A-Z0-9_]{1,29}$'
       OR rounding_scale < 0
       OR rounding_scale > 4
       OR (
         formula_type = 'FIXED'
         AND (
           fixed_amount IS NULL
           OR fixed_amount < 0
           OR formula_expression IS NOT NULL
         )
       )
       OR (
         formula_type <> 'FIXED'
         AND (
           fixed_amount IS NOT NULL
           OR formula_expression IS NULL
           OR btrim(formula_expression) = ''
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing pay-component versions do not satisfy S2-03 invariants';
  END IF;
END $$;

ALTER TABLE compensation.pay_component_version
  RENAME COLUMN version_no_business TO version_sequence;

ALTER TABLE compensation.pay_component_version
  RENAME COLUMN status TO approval_status;

ALTER TABLE compensation.pay_component_version
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

-- V011 already forced RLS on this table. Temporarily exempt the migration
-- owner so legacy approved versions can be backfilled across all tenants.
-- The tenant policy remains present and FORCE RLS is restored immediately.
ALTER TABLE compensation.pay_component_version
  NO FORCE ROW LEVEL SECURITY;

UPDATE compensation.pay_component_version
SET approved_at = created_at,
    approved_by = created_by
WHERE approval_status = 'APPROVED';

ALTER TABLE compensation.pay_component_version
  FORCE ROW LEVEL SECURITY;

ALTER TABLE compensation.pay_component
  ADD CONSTRAINT pay_component_name_not_blank_ck
    CHECK (btrim(name) <> '');

ALTER TABLE compensation.pay_component_version
  ADD CONSTRAINT pay_component_version_sequence_ck
    CHECK (version_sequence > 0),
  ADD CONSTRAINT pay_component_version_approval_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT pay_component_version_formula_type_ck
    CHECK (formula_type ~ '^[A-Z][A-Z0-9_]{1,29}$'),
  ADD CONSTRAINT pay_component_version_rounding_scale_ck
    CHECK (rounding_scale BETWEEN 0 AND 4),
  ADD CONSTRAINT pay_component_version_formula_shape_ck
    CHECK (
      (
        formula_type = 'FIXED'
        AND fixed_amount IS NOT NULL
        AND fixed_amount >= 0
        AND formula_expression IS NULL
      )
      OR
      (
        formula_type <> 'FIXED'
        AND fixed_amount IS NULL
        AND formula_expression IS NOT NULL
        AND btrim(formula_expression) <> ''
      )
    ),
  ADD CONSTRAINT pay_component_version_approval_metadata_ck
    CHECK (
      approval_status <> 'APPROVED'
      OR (
        approved_at IS NOT NULL
        AND approved_by IS NOT NULL
        AND btrim(approved_by) <> ''
      )
    ),
  ADD CONSTRAINT pay_component_version_supersedes_self_ck
    CHECK (
      supersedes_version_id IS NULL
      OR supersedes_version_id <> id
    ),
  ADD CONSTRAINT pay_component_version_identity_id_uk
    UNIQUE (tenant_id, id, component_id),
  ADD CONSTRAINT pay_component_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      component_id
    )
    REFERENCES compensation.pay_component_version(
      tenant_id,
      id,
      component_id
    );

ALTER TABLE compensation.pay_component_version
  ADD CONSTRAINT pay_component_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    component_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE INDEX pay_component_version_current_ix
  ON compensation.pay_component_version(
    tenant_id,
    component_id,
    effective_from DESC
  );

CREATE INDEX pay_component_version_formula_type_ix
  ON compensation.pay_component_version(
    tenant_id,
    formula_type,
    effective_from
  );

CREATE INDEX pay_component_version_supersedes_ix
  ON compensation.pay_component_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

ALTER TABLE compensation.pay_component
  FORCE ROW LEVEL SECURITY;

ALTER TABLE compensation.pay_component_version
  FORCE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION
  compensation.reject_uncontrolled_pay_component_version_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION
      'immutable pay-component version: %.%',
      TG_TABLE_SCHEMA,
      TG_TABLE_NAME;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'pay-component versions cannot be deleted';
  END IF;

  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS pay_component_version_immutable
  ON compensation.pay_component_version;

CREATE TRIGGER pay_component_version_immutable
  BEFORE UPDATE OR DELETE
  ON compensation.pay_component_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.reject_uncontrolled_pay_component_version_mutation();

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
    AND NOT EXISTS (
      SELECT 1
      FROM compensation.pay_component_version successor
      WHERE successor.tenant_id = v.tenant_id
        AND successor.supersedes_version_id = v.id
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.end_date_pay_component_version(
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

  UPDATE compensation.pay_component_version
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE tenant_id = p_tenant_id
    AND id = p_version_id
    AND version_no = p_expected_version
    AND effective_from < p_effective_to
    AND (
      effective_to IS NULL
      OR effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.approve_pay_component_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION compensation.end_date_pay_component_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.approve_pay_component_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION compensation.end_date_pay_component_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

GRANT SELECT, INSERT
  ON compensation.pay_component,
     compensation.pay_component_version
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON compensation.pay_component,
     compensation.pay_component_version
  FROM payroll_app;

REVOKE CREATE ON SCHEMA compensation FROM payroll_app;

COMMENT ON TABLE compensation.pay_component IS
  'Stable tenant-scoped pay-component identity and classification.';

COMMENT ON TABLE compensation.pay_component_version IS
  'Immutable effective-dated pay-component calculation configuration.';

COMMENT ON COLUMN
  compensation.pay_component_version.supersedes_version_id IS
  'Prior future draft replaced by this correction version.';