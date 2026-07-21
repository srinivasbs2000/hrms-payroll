-- Split the Sprint 0 combined pay-group record into a stable identity and
-- immutable, effective-dated versions. PostgreSQL preserves existing foreign
-- keys across the rename, so payroll assignments and payroll cycles continue
-- to reference the exact historical pay-group version ID.
ALTER TABLE organisation.pay_group RENAME TO pay_group_version;
ALTER TABLE organisation.pay_group_version
  RENAME COLUMN statutory_unit_id TO payroll_statutory_unit_version_id;

-- V011 already forced RLS on the legacy table. During this owner-controlled,
-- transactional upgrade, temporarily allow the migration owner to backfill
-- all tenants. Runtime access remains unavailable and FORCE RLS is restored
-- below before any grants are finalized.
ALTER TABLE organisation.pay_group_version NO FORCE ROW LEVEL SECURITY;

CREATE TABLE organisation.pay_group (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

ALTER TABLE organisation.pay_group_version
  ADD COLUMN pay_group_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO organisation.pay_group(
  id, tenant_id, code, created_at, created_by, updated_at, updated_by, version_no
)
SELECT gen_random_uuid(), tenant_id, code, created_at, created_by, updated_at, updated_by, 0
FROM organisation.pay_group_version;

UPDATE organisation.pay_group_version v
SET pay_group_id = i.id,
    approved_at = v.created_at,
    approved_by = v.created_by
FROM organisation.pay_group i
WHERE i.tenant_id = v.tenant_id
  AND i.code = v.code;

ALTER TABLE organisation.pay_group_version
  ALTER COLUMN pay_group_id SET NOT NULL,
  DROP CONSTRAINT pay_group_tenant_id_code_key,
  DROP COLUMN code,
  ADD CONSTRAINT pay_group_version_identity_fk
    FOREIGN KEY (tenant_id, pay_group_id)
    REFERENCES organisation.pay_group(tenant_id, id),
  ADD CONSTRAINT pay_group_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id)
    REFERENCES organisation.pay_group_version(tenant_id, id),
  ADD CONSTRAINT pay_group_version_identity_sequence_uk
    UNIQUE (tenant_id, pay_group_id, version_sequence),
  ADD CONSTRAINT pay_group_version_identity_id_uk
    UNIQUE (tenant_id, id, pay_group_id),
  ADD CONSTRAINT pay_group_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT pay_group_version_currency_ck
    CHECK (currency = 'INR'),
  ADD CONSTRAINT pay_group_version_proration_ck
    CHECK (proration_method = 'CALENDAR_DAYS');

ALTER TABLE organisation.pay_group_version
  ADD CONSTRAINT pay_group_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    pay_group_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE INDEX pay_group_version_current_ix
  ON organisation.pay_group_version(
    tenant_id, pay_group_id, effective_from DESC
  );
CREATE INDEX pay_group_version_parent_ix
  ON organisation.pay_group_version(
    tenant_id, payroll_statutory_unit_version_id, effective_from
  );
CREATE INDEX pay_group_version_calendar_ix
  ON organisation.pay_group_version(
    tenant_id, calendar_id, effective_from
  );

-- Restore the renamed version table's forced tenant boundary immediately after
-- the owner-only backfill. Its tenant_isolation policy survives the rename.
ALTER TABLE organisation.pay_group_version FORCE ROW LEVEL SECURITY;

ALTER TABLE organisation.pay_group ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organisation.pay_group
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE FUNCTION organisation.assert_pay_group_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_from date;
  parent_to date;
  calendar_frequency varchar(20);
BEGIN
  SELECT effective_from, effective_to
  INTO parent_from, parent_to
  FROM organisation.payroll_statutory_unit_version
  WHERE tenant_id = NEW.tenant_id
    AND id = NEW.payroll_statutory_unit_version_id;

  IF parent_from IS NULL THEN
    RAISE EXCEPTION 'payroll statutory unit version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF NEW.effective_from < parent_from
     OR (parent_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > parent_to)) THEN
    RAISE EXCEPTION 'pay-group effective range must be contained by its payroll statutory unit version'
      USING ERRCODE = '23514';
  END IF;

  SELECT frequency
  INTO calendar_frequency
  FROM organisation.payroll_calendar
  WHERE tenant_id = NEW.tenant_id
    AND id = NEW.calendar_id;

  IF calendar_frequency IS NULL THEN
    RAISE EXCEPTION 'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF calendar_frequency <> 'MONTHLY' THEN
    RAISE EXCEPTION 'pay group requires a monthly payroll calendar'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_version_dependencies
  BEFORE INSERT OR UPDATE ON organisation.pay_group_version
  FOR EACH ROW EXECUTE FUNCTION organisation.assert_pay_group_version_dependencies();

CREATE FUNCTION organisation.reject_uncontrolled_pay_group_version_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable pay-group version: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
  END IF;
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'pay-group versions cannot be deleted';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_version_immutable
  BEFORE UPDATE OR DELETE ON organisation.pay_group_version
  FOR EACH ROW EXECUTE FUNCTION organisation.reject_uncontrolled_pay_group_version_mutation();

CREATE FUNCTION organisation.approve_pay_group_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.pay_group_version v
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
      FROM organisation.pay_group_version successor
      WHERE successor.tenant_id = v.tenant_id
        AND successor.supersedes_version_id = v.id
    )
    AND EXISTS (
      SELECT 1
      FROM organisation.payroll_statutory_unit_version parent
      WHERE parent.tenant_id = v.tenant_id
        AND parent.id = v.payroll_statutory_unit_version_id
        AND parent.approval_status = 'APPROVED'
    )
    AND EXISTS (
      SELECT 1
      FROM organisation.payroll_calendar calendar
      WHERE calendar.tenant_id = v.tenant_id
        AND calendar.id = v.calendar_id
        AND calendar.frequency = 'MONTHLY'
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION organisation.end_date_pay_group_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.pay_group_version
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

REVOKE ALL ON FUNCTION organisation.approve_pay_group_version(
  uuid, uuid, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.end_date_pay_group_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION organisation.approve_pay_group_version(
  uuid, uuid, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.end_date_pay_group_version(
  uuid, uuid, date, bigint, varchar, timestamptz
) TO payroll_app;

GRANT SELECT, INSERT
  ON organisation.pay_group, organisation.pay_group_version
  TO payroll_app;
REVOKE UPDATE, DELETE
  ON organisation.pay_group, organisation.pay_group_version
  FROM payroll_app;
REVOKE CREATE ON SCHEMA organisation FROM payroll_app;

COMMENT ON TABLE organisation.pay_group IS
  'Stable tenant-scoped pay-group identity. Business changes create versions.';
COMMENT ON TABLE organisation.pay_group_version IS
  'Immutable approved or draft effective-dated pay-group configuration.';
COMMENT ON COLUMN organisation.pay_group_version.payroll_statutory_unit_version_id IS
  'Exact approved organisation version used by payroll lineage.';
