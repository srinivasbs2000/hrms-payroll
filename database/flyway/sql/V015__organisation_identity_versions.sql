-- Convert the V003 organisation records into exact, effective-dated versions.
-- PostgreSQL preserves dependent foreign keys across table/column renames, so
-- Sprint 2/3 lineage continues to point at the exact version IDs.
ALTER TABLE organisation.establishment RENAME TO establishment_version;
ALTER TABLE organisation.payroll_statutory_unit RENAME TO payroll_statutory_unit_version;
ALTER TABLE organisation.legal_entity RENAME TO legal_entity_version;

ALTER TABLE organisation.payroll_statutory_unit_version
  RENAME COLUMN legal_entity_id TO legal_entity_version_id;
ALTER TABLE organisation.establishment_version
  RENAME COLUMN statutory_unit_id TO payroll_statutory_unit_version_id;

CREATE TABLE organisation.legal_entity (
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

CREATE TABLE organisation.payroll_statutory_unit (
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

CREATE TABLE organisation.establishment (
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

ALTER TABLE organisation.legal_entity_version
  ADD COLUMN legal_entity_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO organisation.legal_entity(id, tenant_id, code, created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), tenant_id, code, created_at, created_by, updated_at, updated_by
FROM organisation.legal_entity_version;

UPDATE organisation.legal_entity_version v
SET legal_entity_id = i.id,
    approved_at = v.created_at,
    approved_by = v.created_by
FROM organisation.legal_entity i
WHERE i.tenant_id = v.tenant_id AND i.code = v.code;

ALTER TABLE organisation.legal_entity_version
  ALTER COLUMN legal_entity_id SET NOT NULL,
  DROP CONSTRAINT legal_entity_tenant_id_code_key,
  DROP COLUMN code,
  ADD CONSTRAINT legal_entity_version_identity_fk
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES organisation.legal_entity(tenant_id, id),
  ADD CONSTRAINT legal_entity_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id) REFERENCES organisation.legal_entity_version(tenant_id, id),
  ADD CONSTRAINT legal_entity_version_identity_sequence_uk
    UNIQUE (tenant_id, legal_entity_id, version_sequence),
  ADD CONSTRAINT legal_entity_version_identity_id_uk
    UNIQUE (tenant_id, id, legal_entity_id),
  ADD CONSTRAINT legal_entity_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED'));

ALTER TABLE organisation.payroll_statutory_unit_version
  ADD COLUMN payroll_statutory_unit_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO organisation.payroll_statutory_unit(id, tenant_id, code, created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), tenant_id, code, created_at, created_by, updated_at, updated_by
FROM organisation.payroll_statutory_unit_version;

UPDATE organisation.payroll_statutory_unit_version v
SET payroll_statutory_unit_id = i.id,
    approved_at = v.created_at,
    approved_by = v.created_by
FROM organisation.payroll_statutory_unit i
WHERE i.tenant_id = v.tenant_id AND i.code = v.code;

ALTER TABLE organisation.payroll_statutory_unit_version
  ALTER COLUMN payroll_statutory_unit_id SET NOT NULL,
  DROP CONSTRAINT payroll_statutory_unit_tenant_id_code_key,
  DROP COLUMN code,
  ADD CONSTRAINT psu_version_identity_fk
    FOREIGN KEY (tenant_id, payroll_statutory_unit_id) REFERENCES organisation.payroll_statutory_unit(tenant_id, id),
  ADD CONSTRAINT psu_version_legal_version_fk
    FOREIGN KEY (tenant_id, legal_entity_version_id) REFERENCES organisation.legal_entity_version(tenant_id, id),
  ADD CONSTRAINT psu_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id) REFERENCES organisation.payroll_statutory_unit_version(tenant_id, id),
  ADD CONSTRAINT psu_version_identity_sequence_uk
    UNIQUE (tenant_id, payroll_statutory_unit_id, version_sequence),
  ADD CONSTRAINT psu_version_identity_id_uk
    UNIQUE (tenant_id, id, payroll_statutory_unit_id),
  ADD CONSTRAINT psu_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED'));

ALTER TABLE organisation.establishment_version
  ADD COLUMN establishment_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO organisation.establishment(id, tenant_id, code, created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), tenant_id, code, created_at, created_by, updated_at, updated_by
FROM organisation.establishment_version;

UPDATE organisation.establishment_version v
SET establishment_id = i.id,
    approved_at = v.created_at,
    approved_by = v.created_by
FROM organisation.establishment i
WHERE i.tenant_id = v.tenant_id AND i.code = v.code;

ALTER TABLE organisation.establishment_version
  ALTER COLUMN establishment_id SET NOT NULL,
  DROP CONSTRAINT establishment_tenant_id_code_key,
  DROP COLUMN code,
  ADD CONSTRAINT establishment_version_identity_fk
    FOREIGN KEY (tenant_id, establishment_id) REFERENCES organisation.establishment(tenant_id, id),
  ADD CONSTRAINT establishment_version_psu_version_fk
    FOREIGN KEY (tenant_id, payroll_statutory_unit_version_id) REFERENCES organisation.payroll_statutory_unit_version(tenant_id, id),
  ADD CONSTRAINT establishment_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id) REFERENCES organisation.establishment_version(tenant_id, id),
  ADD CONSTRAINT establishment_version_identity_sequence_uk
    UNIQUE (tenant_id, establishment_id, version_sequence),
  ADD CONSTRAINT establishment_version_identity_id_uk
    UNIQUE (tenant_id, id, establishment_id),
  ADD CONSTRAINT establishment_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED'));

ALTER TABLE organisation.legal_entity_version
  ADD CONSTRAINT legal_entity_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    legal_entity_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

ALTER TABLE organisation.payroll_statutory_unit_version
  ADD CONSTRAINT psu_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_statutory_unit_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

ALTER TABLE organisation.establishment_version
  ADD CONSTRAINT establishment_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    establishment_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE INDEX legal_entity_version_current_ix
  ON organisation.legal_entity_version(tenant_id, legal_entity_id, effective_from DESC);
CREATE INDEX psu_version_current_ix
  ON organisation.payroll_statutory_unit_version(tenant_id, payroll_statutory_unit_id, effective_from DESC);
CREATE INDEX establishment_version_current_ix
  ON organisation.establishment_version(tenant_id, establishment_id, effective_from DESC);

DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT unnest(ARRAY[
    'legal_entity', 'legal_entity_version',
    'payroll_statutory_unit', 'payroll_statutory_unit_version',
    'establishment', 'establishment_version'
  ]) AS table_name
  LOOP
    EXECUTE format('ALTER TABLE organisation.%I ENABLE ROW LEVEL SECURITY', r.table_name);
    EXECUTE format('ALTER TABLE organisation.%I FORCE ROW LEVEL SECURITY', r.table_name);
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON organisation.%I', r.table_name);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON organisation.%I USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id())',
      r.table_name);
  END LOOP;
END $$;

CREATE FUNCTION platform.reject_uncontrolled_version_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable organisation version: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
  END IF;
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'organisation versions cannot be deleted';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER legal_entity_version_immutable
  BEFORE UPDATE OR DELETE ON organisation.legal_entity_version
  FOR EACH ROW EXECUTE FUNCTION platform.reject_uncontrolled_version_mutation();
CREATE TRIGGER psu_version_immutable
  BEFORE UPDATE OR DELETE ON organisation.payroll_statutory_unit_version
  FOR EACH ROW EXECUTE FUNCTION platform.reject_uncontrolled_version_mutation();
CREATE TRIGGER establishment_version_immutable
  BEFORE UPDATE OR DELETE ON organisation.establishment_version
  FOR EACH ROW EXECUTE FUNCTION platform.reject_uncontrolled_version_mutation();

GRANT SELECT, INSERT ON organisation.legal_entity, organisation.legal_entity_version,
  organisation.payroll_statutory_unit, organisation.payroll_statutory_unit_version,
  organisation.establishment, organisation.establishment_version TO payroll_app;
REVOKE UPDATE, DELETE ON organisation.legal_entity, organisation.legal_entity_version,
  organisation.payroll_statutory_unit, organisation.payroll_statutory_unit_version,
  organisation.establishment, organisation.establishment_version FROM payroll_app;
REVOKE CREATE ON SCHEMA organisation FROM payroll_app;
