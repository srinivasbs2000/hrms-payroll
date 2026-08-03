-- P5-A1 organisation hierarchy closure.
--
-- Forward-only from V030. V001-V030 remain immutable.
-- This migration closes identity lifecycle, maker-checker, classification,
-- retirement, code validation and version-allocation safety gaps without
-- replacing the stable identity/effective-dated version model introduced in
-- V015/V016 and hardened in V022.
--
-- The identity tables use FORCE RLS. The migration owner is intentionally
-- NOBYPASSRLS, so temporarily remove FORCE (while keeping RLS enabled for every
-- non-owner) to validate and backfill all tenants. The transaction restores
-- FORCE before completion; any failure rolls the temporary state back.

ALTER TABLE organisation.legal_entity NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.establishment NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM organisation.legal_entity
    WHERE status NOT IN ('ACTIVE', 'INACTIVE')
  ) OR EXISTS (
    SELECT 1
    FROM organisation.payroll_statutory_unit
    WHERE status NOT IN ('ACTIVE', 'INACTIVE')
  ) OR EXISTS (
    SELECT 1
    FROM organisation.establishment
    WHERE status NOT IN ('ACTIVE', 'INACTIVE')
  ) THEN
    RAISE EXCEPTION
      'V031 requires pre-existing organisation identity statuses to use the V030 vocabulary';
  END IF;

  IF EXISTS (
    SELECT 1 FROM organisation.legal_entity
    WHERE code !~ '^[A-Z][A-Z0-9_]{1,39}$'
  ) OR EXISTS (
    SELECT 1 FROM organisation.payroll_statutory_unit
    WHERE code !~ '^[A-Z][A-Z0-9_]{1,39}$'
  ) OR EXISTS (
    SELECT 1 FROM organisation.establishment
    WHERE code !~ '^[A-Z][A-Z0-9_]{1,39}$'
  ) THEN
    RAISE EXCEPTION
      'V031 cannot add organisation code constraints while invalid identity codes exist';
  END IF;
END $$;

ALTER TABLE organisation.legal_entity
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160);

ALTER TABLE organisation.payroll_statutory_unit
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160);

ALTER TABLE organisation.establishment
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160);

UPDATE organisation.legal_entity SET status = 'ACTIVE';
UPDATE organisation.payroll_statutory_unit SET status = 'ACTIVE';
UPDATE organisation.establishment SET status = 'ACTIVE';

DO $$
DECLARE
  target regclass;
  status_attribute smallint;
  status_constraint name;
  status_constraint_count integer;
BEGIN
  FOREACH target IN ARRAY ARRAY[
    'organisation.legal_entity'::regclass,
    'organisation.payroll_statutory_unit'::regclass,
    'organisation.establishment'::regclass
  ]
  LOOP
    SELECT attnum
      INTO status_attribute
      FROM pg_attribute
     WHERE attrelid = target
       AND attname = 'status'
       AND NOT attisdropped;

    SELECT count(*), min(conname)
      INTO status_constraint_count, status_constraint
      FROM pg_constraint
     WHERE conrelid = target
       AND contype = 'c'
       AND status_attribute = ANY (conkey);

    IF status_constraint_count <> 1 THEN
      RAISE EXCEPTION
        'expected exactly one status check on %, found %',
        target,
        status_constraint_count;
    END IF;

    EXECUTE format(
      'ALTER TABLE %s DROP CONSTRAINT %I',
      target,
      status_constraint
    );
  END LOOP;
END $$;

ALTER TABLE organisation.legal_entity
  ALTER COLUMN status SET DEFAULT 'PENDING_APPROVAL',
  ADD CONSTRAINT legal_entity_status_ck
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  ADD CONSTRAINT legal_entity_code_format_ck
    CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  ADD CONSTRAINT legal_entity_retirement_evidence_ck
    CHECK (
      (
        status <> 'RETIRED'
        AND retirement_effective_date IS NULL
        AND retirement_reason IS NULL
        AND retired_at IS NULL
        AND retired_by IS NULL
      )
      OR
      (
        status = 'RETIRED'
        AND retirement_effective_date IS NOT NULL
        AND retirement_reason IS NOT NULL
        AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
        AND retired_at IS NOT NULL
        AND retired_by IS NOT NULL
        AND length(btrim(retired_by)) BETWEEN 1 AND 160
      )
    );

ALTER TABLE organisation.payroll_statutory_unit
  ALTER COLUMN status SET DEFAULT 'PENDING_APPROVAL',
  ADD CONSTRAINT payroll_statutory_unit_status_ck
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  ADD CONSTRAINT payroll_statutory_unit_code_format_ck
    CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  ADD CONSTRAINT payroll_statutory_unit_retirement_evidence_ck
    CHECK (
      (
        status <> 'RETIRED'
        AND retirement_effective_date IS NULL
        AND retirement_reason IS NULL
        AND retired_at IS NULL
        AND retired_by IS NULL
      )
      OR
      (
        status = 'RETIRED'
        AND retirement_effective_date IS NOT NULL
        AND retirement_reason IS NOT NULL
        AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
        AND retired_at IS NOT NULL
        AND retired_by IS NOT NULL
        AND length(btrim(retired_by)) BETWEEN 1 AND 160
      )
    );

ALTER TABLE organisation.establishment
  ALTER COLUMN status SET DEFAULT 'PENDING_APPROVAL',
  ADD CONSTRAINT establishment_status_ck
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  ADD CONSTRAINT establishment_code_format_ck
    CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  ADD CONSTRAINT establishment_retirement_evidence_ck
    CHECK (
      (
        status <> 'RETIRED'
        AND retirement_effective_date IS NULL
        AND retirement_reason IS NULL
        AND retired_at IS NULL
        AND retired_by IS NULL
      )
      OR
      (
        status = 'RETIRED'
        AND retirement_effective_date IS NOT NULL
        AND retirement_reason IS NOT NULL
        AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
        AND retired_at IS NOT NULL
        AND retired_by IS NOT NULL
        AND length(btrim(retired_by)) BETWEEN 1 AND 160
      )
    );

ALTER TABLE organisation.payroll_statutory_unit_version
  ADD COLUMN responsibility_scope varchar(30)
    NOT NULL DEFAULT 'TAX_AND_STATUTORY',
  ADD CONSTRAINT psu_version_responsibility_scope_ck
    CHECK (
      responsibility_scope IN (
        'TAX_AND_STATUTORY',
        'TAX_ONLY',
        'STATUTORY_ONLY',
        'PAYROLL_OPERATIONS'
      )
    );

ALTER TABLE organisation.establishment_version
  ADD COLUMN establishment_type varchar(30)
    NOT NULL DEFAULT 'OTHER',
  ADD CONSTRAINT establishment_version_type_ck
    CHECK (
      establishment_type IN (
        'OFFICE',
        'BRANCH',
        'FACTORY',
        'SHOP',
        'CONSTRUCTION',
        'OTHER'
      )
    );

CREATE FUNCTION organisation.assert_identity_accepts_version()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(20);
BEGIN
  CASE TG_TABLE_NAME
    WHEN 'legal_entity_version' THEN
      SELECT status
        INTO identity_status
        FROM organisation.legal_entity
       WHERE tenant_id = NEW.tenant_id
         AND id = NEW.legal_entity_id;
    WHEN 'payroll_statutory_unit_version' THEN
      SELECT status
        INTO identity_status
        FROM organisation.payroll_statutory_unit
       WHERE tenant_id = NEW.tenant_id
         AND id = NEW.payroll_statutory_unit_id;
    WHEN 'establishment_version' THEN
      SELECT status
        INTO identity_status
        FROM organisation.establishment
       WHERE tenant_id = NEW.tenant_id
         AND id = NEW.establishment_id;
    ELSE
      RAISE EXCEPTION 'unsupported organisation version table'
        USING ERRCODE = 'P5A03';
  END CASE;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'organisation identity does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired organisation identities cannot accept new versions'
      USING ERRCODE = 'P5A02';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER legal_entity_version_identity_lifecycle
  BEFORE INSERT ON organisation.legal_entity_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_identity_accepts_version();

CREATE TRIGGER psu_version_identity_lifecycle
  BEFORE INSERT ON organisation.payroll_statutory_unit_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_identity_accepts_version();

CREATE TRIGGER establishment_version_identity_lifecycle
  BEFORE INSERT ON organisation.establishment_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_identity_accepts_version();

REVOKE ALL ON FUNCTION organisation.assert_identity_accepts_version()
  FROM PUBLIC;

CREATE FUNCTION organisation.allocate_version_sequence(
  p_kind varchar,
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(20);
  next_sequence integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      SELECT status
        INTO identity_status
        FROM organisation.legal_entity
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'organisation identity was not found'
          USING ERRCODE = 'P5A05';
      END IF;

      IF identity_status = 'RETIRED' THEN
        RAISE EXCEPTION 'retired organisation identities cannot accept new versions'
          USING ERRCODE = 'P5A02';
      END IF;

      SELECT coalesce(max(version_sequence), 0) + 1
        INTO next_sequence
        FROM organisation.legal_entity_version
       WHERE tenant_id = p_tenant_id
         AND legal_entity_id = p_identity_id;

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      SELECT status
        INTO identity_status
        FROM organisation.payroll_statutory_unit
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'organisation identity was not found'
          USING ERRCODE = 'P5A05';
      END IF;

      IF identity_status = 'RETIRED' THEN
        RAISE EXCEPTION 'retired organisation identities cannot accept new versions'
          USING ERRCODE = 'P5A02';
      END IF;

      SELECT coalesce(max(version_sequence), 0) + 1
        INTO next_sequence
        FROM organisation.payroll_statutory_unit_version
       WHERE tenant_id = p_tenant_id
         AND payroll_statutory_unit_id = p_identity_id;

    WHEN 'ESTABLISHMENT' THEN
      SELECT status
        INTO identity_status
        FROM organisation.establishment
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'organisation identity was not found'
          USING ERRCODE = 'P5A05';
      END IF;

      IF identity_status = 'RETIRED' THEN
        RAISE EXCEPTION 'retired organisation identities cannot accept new versions'
          USING ERRCODE = 'P5A02';
      END IF;

      SELECT coalesce(max(version_sequence), 0) + 1
        INTO next_sequence
        FROM organisation.establishment_version
       WHERE tenant_id = p_tenant_id
         AND establishment_id = p_identity_id;

    ELSE
      RAISE EXCEPTION 'unsupported organisation kind'
        USING ERRCODE = '23514';
  END CASE;

  RETURN next_sequence;
END $$;

REVOKE ALL ON FUNCTION organisation.allocate_version_sequence(
  varchar, uuid, uuid
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.allocate_version_sequence(
  varchar, uuid, uuid
) TO payroll_app;

CREATE OR REPLACE FUNCTION organisation.approve_version(
  p_kind varchar,
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
  identity_affected bigint;
  version_created_by varchar(160);
  version_status varchar(20);
  version_superseded boolean;
  version_effective_from date;
  version_effective_to date;
  parent_version_id uuid;
  identity_id uuid;
  identity_status varchar(20);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL
     OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160 THEN
    RAISE EXCEPTION 'actor is required and must not exceed 160 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_approved_at IS NULL THEN
    RAISE EXCEPTION 'approval timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      SELECT version.created_by,
             version.approval_status,
             EXISTS (
               SELECT 1
                 FROM organisation.legal_entity_version successor
                WHERE successor.tenant_id = version.tenant_id
                  AND successor.supersedes_version_id = version.id
             ),
             version.effective_from,
             version.effective_to,
             NULL::uuid,
             version.legal_entity_id,
             identity.status
        INTO version_created_by,
             version_status,
             version_superseded,
             version_effective_from,
             version_effective_to,
             parent_version_id,
             identity_id,
             identity_status
        FROM organisation.legal_entity_version version
        JOIN organisation.legal_entity identity
          ON identity.tenant_id = version.tenant_id
         AND identity.id = version.legal_entity_id
       WHERE version.tenant_id = p_tenant_id
         AND version.id = p_version_id
       FOR UPDATE OF version, identity;

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      SELECT version.created_by,
             version.approval_status,
             EXISTS (
               SELECT 1
                 FROM organisation.payroll_statutory_unit_version successor
                WHERE successor.tenant_id = version.tenant_id
                  AND successor.supersedes_version_id = version.id
             ),
             version.effective_from,
             version.effective_to,
             version.legal_entity_version_id,
             version.payroll_statutory_unit_id,
             identity.status
        INTO version_created_by,
             version_status,
             version_superseded,
             version_effective_from,
             version_effective_to,
             parent_version_id,
             identity_id,
             identity_status
        FROM organisation.payroll_statutory_unit_version version
        JOIN organisation.payroll_statutory_unit identity
          ON identity.tenant_id = version.tenant_id
         AND identity.id = version.payroll_statutory_unit_id
       WHERE version.tenant_id = p_tenant_id
         AND version.id = p_version_id
       FOR UPDATE OF version, identity;

    WHEN 'ESTABLISHMENT' THEN
      SELECT version.created_by,
             version.approval_status,
             EXISTS (
               SELECT 1
                 FROM organisation.establishment_version successor
                WHERE successor.tenant_id = version.tenant_id
                  AND successor.supersedes_version_id = version.id
             ),
             version.effective_from,
             version.effective_to,
             version.payroll_statutory_unit_version_id,
             version.establishment_id,
             identity.status
        INTO version_created_by,
             version_status,
             version_superseded,
             version_effective_from,
             version_effective_to,
             parent_version_id,
             identity_id,
             identity_status
        FROM organisation.establishment_version version
        JOIN organisation.establishment identity
          ON identity.tenant_id = version.tenant_id
         AND identity.id = version.establishment_id
       WHERE version.tenant_id = p_tenant_id
         AND version.id = p_version_id
       FOR UPDATE OF version, identity;

    ELSE
      RAISE EXCEPTION 'unsupported organisation kind'
        USING ERRCODE = '23514';
  END CASE;

  IF identity_id IS NULL THEN
    RAISE EXCEPTION 'organisation version was not found'
      USING ERRCODE = 'P5A05';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired organisation identities cannot approve versions'
      USING ERRCODE = 'P5A02';
  END IF;

  IF version_created_by = p_actor THEN
    RAISE EXCEPTION 'the creator of a version cannot approve the same version'
      USING ERRCODE = 'P5A01';
  END IF;

  IF version_status <> 'DRAFT' OR version_superseded THEN
    RAISE EXCEPTION 'version is not an approvable draft'
      USING ERRCODE = 'P5A03';
  END IF;

  IF p_kind = 'PAYROLL_STATUTORY_UNIT'
     AND NOT EXISTS (
       SELECT 1
         FROM organisation.legal_entity_version parent
        WHERE parent.tenant_id = p_tenant_id
          AND parent.id = parent_version_id
          AND parent.approval_status = 'APPROVED'
          AND version_effective_from >= parent.effective_from
          AND (
            parent.effective_to IS NULL
            OR (
              version_effective_to IS NOT NULL
              AND version_effective_to <= parent.effective_to
            )
          )
     ) THEN
    RAISE EXCEPTION
      'payroll statutory unit requires an approved containing legal-entity version'
      USING ERRCODE = 'P5A03';
  END IF;

  IF p_kind = 'ESTABLISHMENT'
     AND NOT EXISTS (
       SELECT 1
         FROM organisation.payroll_statutory_unit_version parent
        WHERE parent.tenant_id = p_tenant_id
          AND parent.id = parent_version_id
          AND parent.approval_status = 'APPROVED'
          AND version_effective_from >= parent.effective_from
          AND (
            parent.effective_to IS NULL
            OR (
              version_effective_to IS NOT NULL
              AND version_effective_to <= parent.effective_to
            )
          )
     ) THEN
    RAISE EXCEPTION
      'establishment requires an approved containing payroll-statutory-unit version'
      USING ERRCODE = 'P5A03';
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      UPDATE organisation.legal_entity_version
         SET approval_status = 'APPROVED',
             approved_at = p_approved_at,
             approved_by = p_actor,
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_version_id
         AND approval_status = 'DRAFT';

      GET DIAGNOSTICS affected = ROW_COUNT;
      IF affected <> 1 THEN
        RAISE EXCEPTION 'version changed during approval'
          USING ERRCODE = 'P5A04';
      END IF;

      UPDATE organisation.legal_entity
         SET status = 'ACTIVE',
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = identity_id
         AND status = 'PENDING_APPROVAL';

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      UPDATE organisation.payroll_statutory_unit_version
         SET approval_status = 'APPROVED',
             approved_at = p_approved_at,
             approved_by = p_actor,
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_version_id
         AND approval_status = 'DRAFT';

      GET DIAGNOSTICS affected = ROW_COUNT;
      IF affected <> 1 THEN
        RAISE EXCEPTION 'version changed during approval'
          USING ERRCODE = 'P5A04';
      END IF;

      UPDATE organisation.payroll_statutory_unit
         SET status = 'ACTIVE',
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = identity_id
         AND status = 'PENDING_APPROVAL';

    WHEN 'ESTABLISHMENT' THEN
      UPDATE organisation.establishment_version
         SET approval_status = 'APPROVED',
             approved_at = p_approved_at,
             approved_by = p_actor,
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_version_id
         AND approval_status = 'DRAFT';

      GET DIAGNOSTICS affected = ROW_COUNT;
      IF affected <> 1 THEN
        RAISE EXCEPTION 'version changed during approval'
          USING ERRCODE = 'P5A04';
      END IF;

      UPDATE organisation.establishment
         SET status = 'ACTIVE',
             updated_at = p_approved_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = identity_id
         AND status = 'PENDING_APPROVAL';
  END CASE;

  GET DIAGNOSTICS identity_affected = ROW_COUNT;
  IF identity_affected NOT IN (0, 1) THEN
    RAISE EXCEPTION 'unexpected identity activation result'
      USING ERRCODE = 'P5A03';
  END IF;

  RETURN affected;
END $$;

CREATE FUNCTION organisation.retire_identity(
  p_kind varchar,
  p_tenant_id uuid,
  p_identity_id uuid,
  p_effective_date date,
  p_expected_identity_version bigint,
  p_reason varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  organisation,
  employee_payroll,
  platform AS $$
DECLARE
  current_status varchar(20);
  current_identity_version bigint;
  final_version_id uuid;
  final_version_no bigint;
  final_effective_from date;
  final_effective_to date;
  end_date_result bigint;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_effective_date IS NULL THEN
    RAISE EXCEPTION 'retirement effective date is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_reason IS NULL
     OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500 THEN
    RAISE EXCEPTION 'retirement reason is required and must not exceed 500 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL
     OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160 THEN
    RAISE EXCEPTION 'actor is required and must not exceed 160 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      SELECT status, version_no
        INTO current_status, current_identity_version
        FROM organisation.legal_entity
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      SELECT status, version_no
        INTO current_status, current_identity_version
        FROM organisation.payroll_statutory_unit
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

    WHEN 'ESTABLISHMENT' THEN
      SELECT status, version_no
        INTO current_status, current_identity_version
        FROM organisation.establishment
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
       FOR UPDATE;

    ELSE
      RAISE EXCEPTION 'unsupported organisation kind'
        USING ERRCODE = '23514';
  END CASE;

  IF current_status IS NULL THEN
    RAISE EXCEPTION 'organisation identity was not found'
      USING ERRCODE = 'P5A05';
  END IF;

  IF current_identity_version <> p_expected_identity_version THEN
    RAISE EXCEPTION 'organisation identity changed'
      USING ERRCODE = 'P5A04';
  END IF;

  IF current_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'only an active organisation identity can be retired'
      USING ERRCODE = 'P5A02';
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      IF EXISTS (
        SELECT 1
          FROM organisation.legal_entity_version
         WHERE tenant_id = p_tenant_id
           AND legal_entity_id = p_identity_id
           AND approval_status = 'APPROVED'
           AND effective_from >= p_effective_date
      ) THEN
        RAISE EXCEPTION 'future approved versions must be resolved before retirement'
          USING ERRCODE = 'P5A03';
      END IF;

      SELECT id, version_no, effective_from, effective_to
        INTO final_version_id,
             final_version_no,
             final_effective_from,
             final_effective_to
        FROM organisation.legal_entity_version
       WHERE tenant_id = p_tenant_id
         AND legal_entity_id = p_identity_id
         AND approval_status = 'APPROVED'
         AND effective_from < p_effective_date
       ORDER BY effective_from DESC, version_sequence DESC
       LIMIT 1
       FOR UPDATE;

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      IF EXISTS (
        SELECT 1
          FROM organisation.payroll_statutory_unit_version
         WHERE tenant_id = p_tenant_id
           AND payroll_statutory_unit_id = p_identity_id
           AND approval_status = 'APPROVED'
           AND effective_from >= p_effective_date
      ) THEN
        RAISE EXCEPTION 'future approved versions must be resolved before retirement'
          USING ERRCODE = 'P5A03';
      END IF;

      SELECT id, version_no, effective_from, effective_to
        INTO final_version_id,
             final_version_no,
             final_effective_from,
             final_effective_to
        FROM organisation.payroll_statutory_unit_version
       WHERE tenant_id = p_tenant_id
         AND payroll_statutory_unit_id = p_identity_id
         AND approval_status = 'APPROVED'
         AND effective_from < p_effective_date
       ORDER BY effective_from DESC, version_sequence DESC
       LIMIT 1
       FOR UPDATE;

    WHEN 'ESTABLISHMENT' THEN
      IF EXISTS (
        SELECT 1
          FROM organisation.establishment_version
         WHERE tenant_id = p_tenant_id
           AND establishment_id = p_identity_id
           AND approval_status = 'APPROVED'
           AND effective_from >= p_effective_date
      ) THEN
        RAISE EXCEPTION 'future approved versions must be resolved before retirement'
          USING ERRCODE = 'P5A03';
      END IF;

      SELECT id, version_no, effective_from, effective_to
        INTO final_version_id,
             final_version_no,
             final_effective_from,
             final_effective_to
        FROM organisation.establishment_version
       WHERE tenant_id = p_tenant_id
         AND establishment_id = p_identity_id
         AND approval_status = 'APPROVED'
         AND effective_from < p_effective_date
       ORDER BY effective_from DESC, version_sequence DESC
       LIMIT 1
       FOR UPDATE;
  END CASE;

  IF final_version_id IS NULL THEN
    RAISE EXCEPTION 'retirement requires an approved version before the effective date'
      USING ERRCODE = 'P5A03';
  END IF;

  IF final_effective_to IS NOT NULL
     AND final_effective_to < p_effective_date THEN
    RAISE EXCEPTION
      'the final approved version ends before the requested retirement date'
      USING ERRCODE = 'P5A03';
  END IF;

  IF final_effective_to IS NULL OR final_effective_to > p_effective_date THEN
    SELECT organisation.end_date_version(
      p_kind,
      p_tenant_id,
      final_version_id,
      p_effective_date,
      final_version_no,
      p_actor,
      p_changed_at
    )
      INTO end_date_result;

    IF end_date_result <> 1 THEN
      RAISE EXCEPTION
        'dependent configuration or concurrent change blocks retirement'
        USING ERRCODE = 'P5A03';
    END IF;
  END IF;

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      UPDATE organisation.legal_entity
         SET status = 'RETIRED',
             retirement_effective_date = p_effective_date,
             retirement_reason = btrim(p_reason),
             retired_at = p_changed_at,
             retired_by = p_actor,
             updated_at = p_changed_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
         AND status = 'ACTIVE'
         AND version_no = p_expected_identity_version;

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      UPDATE organisation.payroll_statutory_unit
         SET status = 'RETIRED',
             retirement_effective_date = p_effective_date,
             retirement_reason = btrim(p_reason),
             retired_at = p_changed_at,
             retired_by = p_actor,
             updated_at = p_changed_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
         AND status = 'ACTIVE'
         AND version_no = p_expected_identity_version;

    WHEN 'ESTABLISHMENT' THEN
      UPDATE organisation.establishment
         SET status = 'RETIRED',
             retirement_effective_date = p_effective_date,
             retirement_reason = btrim(p_reason),
             retired_at = p_changed_at,
             retired_by = p_actor,
             updated_at = p_changed_at,
             updated_by = p_actor,
             version_no = version_no + 1
       WHERE tenant_id = p_tenant_id
         AND id = p_identity_id
         AND status = 'ACTIVE'
         AND version_no = p_expected_identity_version;
  END CASE;

  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN
    RAISE EXCEPTION 'organisation identity changed during retirement'
      USING ERRCODE = 'P5A04';
  END IF;

  RETURN final_version_id;
END $$;

REVOKE ALL ON FUNCTION organisation.retire_identity(
  varchar, uuid, uuid, date, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.retire_identity(
  varchar, uuid, uuid, date, bigint, varchar, varchar, timestamptz
) TO payroll_app;

REVOKE ALL ON FUNCTION organisation.approve_version(
  varchar, uuid, uuid, varchar, timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.approve_version(
  varchar, uuid, uuid, varchar, timestamptz
) TO payroll_app;

ALTER TABLE organisation.legal_entity FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.establishment FORCE ROW LEVEL SECURITY;
