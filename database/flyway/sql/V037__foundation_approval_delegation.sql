-- P5-FAD-01 G01 shared application approval-authority foundation.
-- Forward-only from V036. V001-V036 remain immutable.
-- Legal authorised-signatory authority remains separate from application authority.

CREATE TABLE security.approval_authority_assignment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  owner_kind varchar(30) NOT NULL,
  owner_id uuid NOT NULL,
  approval_role varchar(30) NOT NULL,
  domain_code varchar(80) NOT NULL,
  action_code varchar(80) NOT NULL,
  actor_id varchar(160) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  suspended_at timestamptz,
  suspended_by varchar(160),
  suspension_reason varchar(500),
  retired_at timestamptz,
  retired_by varchar(160),
  retirement_reason varchar(500),
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (owner_kind IN ('LEGAL_ENTITY', 'PAYROLL_STATUTORY_UNIT')),
  CHECK (approval_role IN ('VERIFIER', 'FINAL_APPROVER')),
  CHECK (domain_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
  CHECK (action_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
  CHECK (btrim(actor_id) <> ''),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  CHECK (
    status <> 'SUSPENDED'
    OR (
      suspended_at IS NOT NULL AND suspended_by IS NOT NULL
      AND btrim(suspended_by) <> ''
      AND suspension_reason IS NOT NULL
      AND length(btrim(suspension_reason)) BETWEEN 1 AND 500
    )
  ),
  CHECK (
    status <> 'RETIRED'
    OR (
      retired_at IS NOT NULL AND retired_by IS NOT NULL
      AND btrim(retired_by) <> ''
      AND retirement_reason IS NOT NULL
      AND length(btrim(retirement_reason)) BETWEEN 1 AND 500
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

ALTER TABLE security.approval_authority_assignment
  ADD CONSTRAINT approval_authority_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =, owner_kind WITH =, owner_id WITH =,
    approval_role WITH =, domain_code WITH =, action_code WITH =,
    actor_id WITH =, daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (status = 'ACTIVE');

CREATE INDEX approval_authority_resolution_ix
  ON security.approval_authority_assignment(
    tenant_id, owner_kind, owner_id, approval_role,
    domain_code, action_code, actor_id, effective_from
  ) WHERE status = 'ACTIVE';

CREATE TABLE security.approval_delegation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  source_authority_id uuid NOT NULL,
  delegator_actor_id varchar(160) NOT NULL,
  delegate_actor_id varchar(160) NOT NULL,
  effective_from date NOT NULL,
  effective_to date NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  revoked_at timestamptz,
  revoked_by varchar(160),
  revocation_reason varchar(500),
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (btrim(delegator_actor_id) <> ''),
  CHECK (btrim(delegate_actor_id) <> ''),
  CHECK (delegator_actor_id <> delegate_actor_id),
  CHECK (effective_to > effective_from),
  CHECK (status IN ('ACTIVE', 'REVOKED')),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  CHECK (
    status <> 'REVOKED'
    OR (
      revoked_at IS NOT NULL AND revoked_by IS NOT NULL
      AND btrim(revoked_by) <> ''
      AND revocation_reason IS NOT NULL
      AND length(btrim(revocation_reason)) BETWEEN 1 AND 500
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, source_authority_id)
    REFERENCES security.approval_authority_assignment(tenant_id, id)
);

ALTER TABLE security.approval_delegation
  ADD CONSTRAINT approval_delegation_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =, source_authority_id WITH =, delegate_actor_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (status = 'ACTIVE');

CREATE INDEX approval_delegation_resolution_ix
  ON security.approval_delegation(
    tenant_id, source_authority_id, delegate_actor_id, effective_from
  ) WHERE status = 'ACTIVE';

ALTER TABLE security.approval_authority_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE security.approval_authority_assignment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON security.approval_authority_assignment
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE security.approval_delegation ENABLE ROW LEVEL SECURITY;
ALTER TABLE security.approval_delegation FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON security.approval_delegation
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE OR REPLACE FUNCTION security.assert_approval_authority_owner()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, security, organisation, platform AS $$
DECLARE owner_status varchar(24);
BEGIN
  IF NEW.tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF NEW.owner_kind = 'LEGAL_ENTITY' THEN
    SELECT status INTO owner_status FROM organisation.legal_entity
     WHERE tenant_id = NEW.tenant_id AND id = NEW.owner_id;
  ELSIF NEW.owner_kind = 'PAYROLL_STATUTORY_UNIT' THEN
    SELECT status INTO owner_status FROM organisation.payroll_statutory_unit
     WHERE tenant_id = NEW.tenant_id AND id = NEW.owner_id;
  ELSE
    RAISE EXCEPTION 'unsupported approval-authority owner kind' USING ERRCODE = '23514';
  END IF;
  IF owner_status IS NULL THEN
    RAISE EXCEPTION 'approval-authority owner does not exist' USING ERRCODE = '23503';
  END IF;
  IF owner_status NOT IN ('ACTIVE', 'PENDING_APPROVAL') THEN
    RAISE EXCEPTION 'approval-authority owner must be active or pending initial approval'
      USING ERRCODE = '23514';
  END IF;
  IF owner_status = 'PENDING_APPROVAL'
     AND NOT (
       NEW.approval_role = 'FINAL_APPROVER'
       AND NEW.domain_code = 'ORGANISATION_CONFIG'
       AND NEW.action_code = 'APPROVE'
     ) THEN
    RAISE EXCEPTION
      'pending organisation owner may receive only initial organisation final-approval authority'
      USING ERRCODE = '23514';
  END IF;
  IF NEW.approval_role = 'FINAL_APPROVER' AND NEW.actor_id LIKE 'service:%' THEN
    RAISE EXCEPTION 'service identity cannot receive interactive final-approval authority'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER approval_authority_owner_guard
  BEFORE INSERT ON security.approval_authority_assignment
  FOR EACH ROW EXECUTE FUNCTION security.assert_approval_authority_owner();

CREATE OR REPLACE FUNCTION security.assert_approval_delegation()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, security, platform AS $$
DECLARE
  source_actor varchar(160);
  source_role varchar(30);
  source_from date;
  source_to date;
  source_status varchar(20);
BEGIN
  IF NEW.tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  SELECT actor_id, approval_role, effective_from, effective_to, status
    INTO source_actor, source_role, source_from, source_to, source_status
    FROM security.approval_authority_assignment
   WHERE tenant_id = NEW.tenant_id AND id = NEW.source_authority_id;
  IF source_actor IS NULL THEN
    RAISE EXCEPTION 'source approval authority does not exist' USING ERRCODE = '23503';
  END IF;
  IF source_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'source approval authority must be active' USING ERRCODE = '23514';
  END IF;
  IF NEW.delegator_actor_id <> source_actor THEN
    RAISE EXCEPTION 'delegator must own the source approval authority' USING ERRCODE = '42501';
  END IF;
  IF NEW.delegate_actor_id = source_actor THEN
    RAISE EXCEPTION 'self-delegation is not permitted' USING ERRCODE = '23514';
  END IF;
  IF NEW.effective_from < source_from
     OR (source_to IS NOT NULL AND NEW.effective_to > source_to) THEN
    RAISE EXCEPTION 'delegation period cannot exceed source authority period'
      USING ERRCODE = '23514';
  END IF;
  IF source_role = 'FINAL_APPROVER' AND NEW.delegate_actor_id LIKE 'service:%' THEN
    RAISE EXCEPTION 'service identity cannot receive delegated final-approval authority'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER approval_delegation_scope_guard
  BEFORE INSERT ON security.approval_delegation
  FOR EACH ROW EXECUTE FUNCTION security.assert_approval_delegation();

CREATE OR REPLACE FUNCTION security.suspend_approval_authority(
  p_tenant_id uuid, p_authority_id uuid, p_expected_version bigint,
  p_actor varchar, p_reason varchar, p_changed_at timestamptz
) RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, security, platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  UPDATE security.approval_authority_assignment
     SET status='SUSPENDED', suspended_at=p_changed_at, suspended_by=p_actor,
         suspension_reason=p_reason, updated_at=p_changed_at, updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_authority_id AND status='ACTIVE'
     AND version_no=p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION security.retire_approval_authority(
  p_tenant_id uuid, p_authority_id uuid, p_expected_version bigint,
  p_actor varchar, p_reason varchar, p_changed_at timestamptz
) RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, security, platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  UPDATE security.approval_authority_assignment
     SET status='RETIRED', retired_at=p_changed_at, retired_by=p_actor,
         retirement_reason=p_reason, updated_at=p_changed_at, updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_authority_id
     AND status IN ('ACTIVE','SUSPENDED') AND version_no=p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION security.revoke_approval_delegation(
  p_tenant_id uuid, p_delegation_id uuid, p_expected_version bigint,
  p_actor varchar, p_reason varchar, p_changed_at timestamptz
) RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, security, platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  UPDATE security.approval_delegation
     SET status='REVOKED', revoked_at=p_changed_at, revoked_by=p_actor,
         revocation_reason=p_reason, updated_at=p_changed_at, updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_delegation_id AND status='ACTIVE'
     AND delegator_actor_id=p_actor AND version_no=p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION security.resolve_approval_authority(
  p_tenant_id uuid, p_actor varchar, p_owner_kind varchar, p_owner_id uuid,
  p_approval_role varchar, p_domain_code varchar, p_action_code varchar, p_as_of date
) RETURNS TABLE (
  authority_id uuid, delegation_id uuid,
  source_actor_id varchar, effective_actor_id varchar
) LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, security, organisation, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_approval_role = 'FINAL_APPROVER' AND p_actor LIKE 'service:%' THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT candidate.authority_id, candidate.delegation_id,
         candidate.source_actor_id, candidate.effective_actor_id
  FROM (
    SELECT 0 AS priority, a.id AS authority_id, NULL::uuid AS delegation_id,
           a.actor_id AS source_actor_id, p_actor::varchar AS effective_actor_id
      FROM security.approval_authority_assignment a
     WHERE a.tenant_id=p_tenant_id AND a.status='ACTIVE'
       AND a.owner_kind=p_owner_kind AND a.owner_id=p_owner_id
       AND a.approval_role=p_approval_role AND a.domain_code=p_domain_code
       AND a.action_code=p_action_code AND a.actor_id=p_actor
       AND a.effective_from<=p_as_of
       AND (a.effective_to IS NULL OR a.effective_to>p_as_of)
    UNION ALL
    SELECT 1 AS priority, a.id AS authority_id, d.id AS delegation_id,
           a.actor_id AS source_actor_id, p_actor::varchar AS effective_actor_id
      FROM security.approval_authority_assignment a
      JOIN security.approval_delegation d
        ON d.tenant_id=a.tenant_id AND d.source_authority_id=a.id
     WHERE a.tenant_id=p_tenant_id AND a.status='ACTIVE' AND d.status='ACTIVE'
       AND a.owner_kind=p_owner_kind AND a.owner_id=p_owner_id
       AND a.approval_role=p_approval_role AND a.domain_code=p_domain_code
       AND a.action_code=p_action_code
       AND d.delegator_actor_id=a.actor_id AND d.delegate_actor_id=p_actor
       AND a.effective_from<=p_as_of
       AND (a.effective_to IS NULL OR a.effective_to>p_as_of)
       AND d.effective_from<=p_as_of AND d.effective_to>p_as_of
  ) candidate
  WHERE (
    p_owner_kind='LEGAL_ENTITY'
    AND EXISTS (
      SELECT 1 FROM organisation.legal_entity owner
       WHERE owner.tenant_id=p_tenant_id AND owner.id=p_owner_id
         AND (
           owner.status='ACTIVE'
           OR (
             owner.status='PENDING_APPROVAL'
             AND p_approval_role='FINAL_APPROVER'
             AND p_domain_code='ORGANISATION_CONFIG'
             AND p_action_code='APPROVE'
           )
         )
    )
  ) OR (
    p_owner_kind='PAYROLL_STATUTORY_UNIT'
    AND EXISTS (
      SELECT 1 FROM organisation.payroll_statutory_unit owner
       WHERE owner.tenant_id=p_tenant_id AND owner.id=p_owner_id
         AND (
           owner.status='ACTIVE'
           OR (
             owner.status='PENDING_APPROVAL'
             AND p_approval_role='FINAL_APPROVER'
             AND p_domain_code='ORGANISATION_CONFIG'
             AND p_action_code='APPROVE'
           )
         )
    )
  )
  ORDER BY candidate.priority, candidate.authority_id
  LIMIT 1;
END $$;

REVOKE ALL ON security.approval_authority_assignment FROM PUBLIC;
REVOKE ALL ON security.approval_delegation FROM PUBLIC;
GRANT USAGE ON SCHEMA security TO payroll_app;
GRANT SELECT, INSERT ON security.approval_authority_assignment TO payroll_app;
GRANT SELECT, INSERT ON security.approval_delegation TO payroll_app;

REVOKE ALL ON FUNCTION security.suspend_approval_authority(
  uuid, uuid, bigint, varchar, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION security.retire_approval_authority(
  uuid, uuid, bigint, varchar, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION security.revoke_approval_delegation(
  uuid, uuid, bigint, varchar, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION security.resolve_approval_authority(
  uuid, varchar, varchar, uuid, varchar, varchar, varchar, date) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION security.suspend_approval_authority(
  uuid, uuid, bigint, varchar, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION security.retire_approval_authority(
  uuid, uuid, bigint, varchar, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION security.revoke_approval_delegation(
  uuid, uuid, bigint, varchar, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION security.resolve_approval_authority(
  uuid, varchar, varchar, uuid, varchar, varchar, varchar, date) TO payroll_app;
