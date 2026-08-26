-- P5-EOR-01 G02A employee payroll onboarding, readiness, scoped holds and snapshot completion.
-- Forward-only from V051. V001-V051 remain immutable.
--
-- This migration is jurisdiction-neutral. STATUTORY and TAX readiness never infer legal
-- obligations; absent provider/policy evidence fails closed as NOT_EVALUATED. Generic
-- payroll holds remain separate from the V051 payment-only restriction model.

CREATE TABLE employee_payroll.payroll_onboarding_case (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  current_status varchar(24) NOT NULL DEFAULT 'DATA_COLLECTION',
  created_at timestamptz NOT NULL,
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL,
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, payroll_relationship_id),
  CHECK (current_status IN (
    'DATA_COLLECTION','VALIDATION','APPROVAL','READINESS',
    'COMPLETED','ON_HOLD','CANCELLED')),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.payroll_onboarding_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  onboarding_case_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  event_sequence integer NOT NULL,
  from_status varchar(24),
  to_status varchar(24) NOT NULL,
  reason varchar(500) NOT NULL,
  evidence_ref varchar(240) NOT NULL,
  occurred_at timestamptz NOT NULL,
  actor varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, onboarding_case_id, event_sequence),
  CHECK (event_sequence > 0),
  CHECK (from_status IS NULL OR from_status IN (
    'DATA_COLLECTION','VALIDATION','APPROVAL','READINESS',
    'COMPLETED','ON_HOLD','CANCELLED')),
  CHECK (to_status IN (
    'DATA_COLLECTION','VALIDATION','APPROVAL','READINESS',
    'COMPLETED','ON_HOLD','CANCELLED')),
  CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
  CHECK (btrim(evidence_ref) <> '' AND btrim(actor) <> ''),
  FOREIGN KEY (tenant_id, onboarding_case_id)
    REFERENCES employee_payroll.payroll_onboarding_case(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE INDEX payroll_onboarding_status_ix
  ON employee_payroll.payroll_onboarding_case(
    tenant_id, current_status, payroll_relationship_id);

CREATE TABLE employee_payroll.payroll_readiness_policy_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  dimension varchar(24) NOT NULL,
  version_sequence integer NOT NULL,
  applicability varchar(32) NOT NULL,
  severity varchar(20) NOT NULL,
  evidence_ref varchar(240) NOT NULL,
  reason varchar(500) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  supersedes_version_id uuid,
  approved_at timestamptz NOT NULL,
  approved_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL,
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, dimension, version_sequence),
  UNIQUE (tenant_id, supersedes_version_id),
  CHECK (dimension IN (
    'IDENTITY','ASSIGNMENT','COMPENSATION','CALENDAR','PAYMENT',
    'STATUTORY','TAX','DOCUMENTATION','APPROVAL','INTEGRATION')),
  CHECK (version_sequence > 0),
  CHECK (applicability IN ('REQUIRED','EXPLICIT_NOT_APPLICABLE')),
  CHECK (severity IN ('BLOCKING','WARNING','INFORMATIONAL')),
  CHECK (btrim(evidence_ref) <> ''),
  CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (btrim(approved_by) <> '' AND btrim(created_by) <> ''),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, supersedes_version_id)
    REFERENCES employee_payroll.payroll_readiness_policy_version(tenant_id, id)
);

CREATE INDEX payroll_readiness_policy_effective_ix
  ON employee_payroll.payroll_readiness_policy_version(
    tenant_id, dimension, effective_from, effective_to, version_sequence DESC);

CREATE TABLE employee_payroll.payroll_hold (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL,
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL,
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  CHECK (status IN ('PENDING_APPROVAL','ACTIVE','RELEASED','RETIRED')),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.payroll_hold_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_hold_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  reason_code varchar(80) NOT NULL,
  reason varchar(500) NOT NULL,
  source_reference varchar(240) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'DRAFT',
  approval_evidence_ref varchar(240),
  approved_at timestamptz,
  approved_by varchar(160),
  release_evidence_ref varchar(240),
  released_at timestamptz,
  released_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL,
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL,
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_hold_id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, payroll_hold_id, version_sequence),
  UNIQUE (tenant_id, supersedes_version_id),
  CHECK (version_sequence > 0),
  CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
  CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
  CHECK (btrim(source_reference) <> ''),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN ('DRAFT','ACTIVE','RELEASED','SUPERSEDED','REJECTED')),
  CHECK (
    lifecycle_status NOT IN ('ACTIVE','RELEASED','SUPERSEDED')
    OR (approval_evidence_ref IS NOT NULL AND btrim(approval_evidence_ref) <> ''
        AND approved_at IS NOT NULL AND approved_by IS NOT NULL
        AND btrim(approved_by) <> '')),
  CHECK (
    lifecycle_status <> 'RELEASED'
    OR (release_evidence_ref IS NOT NULL AND btrim(release_evidence_ref) <> ''
        AND released_at IS NOT NULL AND released_by IS NOT NULL
        AND btrim(released_by) <> '')),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  CONSTRAINT payroll_hold_version_identity_fk
    FOREIGN KEY (tenant_id, payroll_hold_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_hold(
      tenant_id, id, payroll_relationship_id),
  CONSTRAINT payroll_hold_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, payroll_hold_id)
    REFERENCES employee_payroll.payroll_hold_version(
      tenant_id, id, payroll_hold_id)
);

CREATE TABLE employee_payroll.payroll_hold_scope (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_hold_version_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  scope varchar(32) NOT NULL,
  created_at timestamptz NOT NULL,
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, payroll_hold_version_id, scope),
  CHECK (scope IN (
    'CALCULATION','PAYMENT','DOCUMENT_PUBLICATION','STATUTORY_SUBMISSION')),
  CHECK (btrim(created_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_hold_version_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_hold_version(
      tenant_id, id, payroll_relationship_id)
);

CREATE INDEX payroll_hold_relationship_ix
  ON employee_payroll.payroll_hold(
    tenant_id, payroll_relationship_id, status);
CREATE INDEX payroll_hold_effective_ix
  ON employee_payroll.payroll_hold_version(
    tenant_id, payroll_relationship_id, lifecycle_status,
    effective_from, effective_to);

CREATE TRIGGER payroll_onboarding_event_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.payroll_onboarding_event
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER payroll_hold_scope_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.payroll_hold_scope
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

ALTER TABLE payroll_ops.input_snapshot
  DROP CONSTRAINT input_snapshot_payload_schema_ck,
  ADD COLUMN employee_readiness_set_hash char(64),
  ADD COLUMN active_payroll_hold_set_hash char(64),
  ADD CONSTRAINT input_snapshot_payload_schema_ck
    CHECK (payload_schema_version IN (0, 1, 2)),
  ADD CONSTRAINT input_snapshot_employee_readiness_hash_ck
    CHECK (employee_readiness_set_hash IS NULL
           OR employee_readiness_set_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT input_snapshot_payroll_hold_hash_ck
    CHECK (active_payroll_hold_set_hash IS NULL
           OR active_payroll_hold_set_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT input_snapshot_v052_hash_presence_ck
    CHECK (payload_schema_version <> 2
           OR (employee_readiness_set_hash IS NOT NULL
               AND active_payroll_hold_set_hash IS NOT NULL));

CREATE OR REPLACE FUNCTION employee_payroll.create_payroll_onboarding_case(
  p_tenant_id uuid,
  p_case_id uuid,
  p_payroll_relationship_id uuid,
  p_reason varchar,
  p_evidence_ref varchar,
  p_actor varchar,
  p_occurred_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_case_id IS NULL OR p_payroll_relationship_id IS NULL
     OR p_reason IS NULL OR btrim(p_reason)=''
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref)=''
     OR p_actor IS NULL OR btrim(p_actor)=''
     OR p_occurred_at IS NULL THEN
    RAISE EXCEPTION 'complete onboarding creation evidence is required'
      USING ERRCODE='23514';
  END IF;

  INSERT INTO employee_payroll.payroll_onboarding_case(
    id, tenant_id, payroll_relationship_id, current_status,
    created_at, created_by, updated_at, updated_by
  ) VALUES (
    p_case_id, p_tenant_id, p_payroll_relationship_id, 'DATA_COLLECTION',
    p_occurred_at, p_actor, p_occurred_at, p_actor
  );
  INSERT INTO employee_payroll.payroll_onboarding_event(
    tenant_id, onboarding_case_id, payroll_relationship_id,
    event_sequence, from_status, to_status, reason, evidence_ref,
    occurred_at, actor
  ) VALUES (
    p_tenant_id, p_case_id, p_payroll_relationship_id,
    1, NULL, 'DATA_COLLECTION', p_reason, p_evidence_ref,
    p_occurred_at, p_actor
  );
  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.create_payroll_readiness_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_dimension varchar,
  p_applicability varchar,
  p_severity varchar,
  p_evidence_ref varchar,
  p_reason varchar,
  p_effective_from date,
  p_effective_to date,
  p_actor varchar,
  p_occurred_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  v_sequence integer;
  v_predecessor uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_version_id IS NULL
     OR p_dimension NOT IN (
       'IDENTITY','ASSIGNMENT','COMPENSATION','CALENDAR','PAYMENT',
       'STATUTORY','TAX','DOCUMENTATION','APPROVAL','INTEGRATION')
     OR p_applicability NOT IN ('REQUIRED','EXPLICIT_NOT_APPLICABLE')
     OR p_severity NOT IN ('BLOCKING','WARNING','INFORMATIONAL')
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref)=''
     OR p_reason IS NULL OR btrim(p_reason)=''
     OR p_effective_from IS NULL
     OR (p_effective_to IS NOT NULL AND p_effective_to <= p_effective_from)
     OR p_actor IS NULL OR btrim(p_actor)=''
     OR p_occurred_at IS NULL THEN
    RAISE EXCEPTION 'valid approved readiness policy evidence is required'
      USING ERRCODE='23514';
  END IF;

  SELECT id, version_sequence
    INTO v_predecessor, v_sequence
    FROM employee_payroll.payroll_readiness_policy_version
   WHERE tenant_id=p_tenant_id AND dimension=p_dimension
   ORDER BY version_sequence DESC
   LIMIT 1
   FOR UPDATE;
  IF NOT FOUND THEN
    v_sequence := 1;
    v_predecessor := NULL;
  ELSE
    v_sequence := v_sequence + 1;
  END IF;

  INSERT INTO employee_payroll.payroll_readiness_policy_version(
    id,tenant_id,dimension,version_sequence,applicability,severity,
    evidence_ref,reason,effective_from,effective_to,supersedes_version_id,
    approved_at,approved_by,created_at,created_by
  ) VALUES (
    p_version_id,p_tenant_id,p_dimension,v_sequence,p_applicability,p_severity,
    p_evidence_ref,p_reason,p_effective_from,p_effective_to,v_predecessor,
    p_occurred_at,p_actor,p_occurred_at,p_actor
  );
  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.create_payroll_hold_version(
  p_tenant_id uuid,
  p_hold_id uuid,
  p_version_id uuid,
  p_payroll_relationship_id uuid,
  p_scopes varchar,
  p_reason_code varchar,
  p_reason varchar,
  p_source_reference varchar,
  p_effective_from date,
  p_effective_to date,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  v_sequence integer;
  v_predecessor uuid;
  v_scope varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_hold_id IS NULL OR p_version_id IS NULL OR p_payroll_relationship_id IS NULL
     OR p_scopes IS NULL OR btrim(p_scopes)=''
     OR p_reason_code IS NULL OR p_reason_code !~ '^[A-Z][A-Z0-9_]{1,79}$'
     OR p_reason IS NULL OR btrim(p_reason)=''
     OR p_source_reference IS NULL OR btrim(p_source_reference)=''
     OR p_effective_from IS NULL
     OR (p_effective_to IS NOT NULL AND p_effective_to <= p_effective_from)
     OR p_actor IS NULL OR btrim(p_actor)=''
     OR p_created_at IS NULL THEN
    RAISE EXCEPTION 'complete payroll hold draft evidence is required'
      USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM unnest(string_to_array(p_scopes, ',')) scope_value
     WHERE scope_value NOT IN (
       'CALCULATION','PAYMENT','DOCUMENT_PUBLICATION','STATUTORY_SUBMISSION')) THEN
    RAISE EXCEPTION 'unsupported payroll hold scope' USING ERRCODE='23514';
  END IF;
  IF cardinality(string_to_array(p_scopes, ',')) <> (
    SELECT count(DISTINCT scope_value) FROM unnest(string_to_array(p_scopes, ',')) scope_value) THEN
    RAISE EXCEPTION 'payroll hold scopes must be unique' USING ERRCODE='23514';
  END IF;

  SELECT version_sequence, id
    INTO v_sequence, v_predecessor
    FROM employee_payroll.payroll_hold_version
   WHERE tenant_id=p_tenant_id AND payroll_hold_id=p_hold_id
   ORDER BY version_sequence DESC LIMIT 1 FOR UPDATE;

  IF NOT FOUND THEN
    v_sequence := 1;
    v_predecessor := NULL;
    INSERT INTO employee_payroll.payroll_hold(
      id,tenant_id,payroll_relationship_id,status,
      created_at,created_by,updated_at,updated_by
    ) VALUES (
      p_hold_id,p_tenant_id,p_payroll_relationship_id,'PENDING_APPROVAL',
      p_created_at,p_actor,p_created_at,p_actor
    );
  ELSE
    v_sequence := v_sequence + 1;
    IF NOT EXISTS (
      SELECT 1 FROM employee_payroll.payroll_hold
       WHERE tenant_id=p_tenant_id AND id=p_hold_id
         AND payroll_relationship_id=p_payroll_relationship_id
         AND status <> 'RETIRED') THEN
      RAISE EXCEPTION 'payroll hold identity is unavailable for this relationship'
        USING ERRCODE='23503';
    END IF;
  END IF;

  INSERT INTO employee_payroll.payroll_hold_version(
    id,tenant_id,payroll_hold_id,payroll_relationship_id,version_sequence,
    reason_code,reason,source_reference,effective_from,effective_to,
    supersedes_version_id,created_at,created_by,updated_at,updated_by
  ) VALUES (
    p_version_id,p_tenant_id,p_hold_id,p_payroll_relationship_id,v_sequence,
    p_reason_code,p_reason,p_source_reference,p_effective_from,p_effective_to,
    v_predecessor,p_created_at,p_actor,p_created_at,p_actor
  );

  FOREACH v_scope IN ARRAY string_to_array(p_scopes, ',') LOOP
    INSERT INTO employee_payroll.payroll_hold_scope(
      tenant_id,payroll_hold_version_id,payroll_relationship_id,
      scope,created_at,created_by
    ) VALUES (
      p_tenant_id,p_version_id,p_payroll_relationship_id,
      v_scope,p_created_at,p_actor
    );
  END LOOP;
  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.approve_payroll_hold_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  v_maker varchar(160);
  v_hold_id uuid;
  v_affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  SELECT created_by,payroll_hold_id INTO v_maker,v_hold_id
    FROM employee_payroll.payroll_hold_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND lifecycle_status='DRAFT' AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_actor=v_maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref)=''
     OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'independent payroll hold approval evidence is required'
      USING ERRCODE='42501';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM employee_payroll.payroll_hold_scope
     WHERE tenant_id=p_tenant_id AND payroll_hold_version_id=p_version_id) THEN
    RAISE EXCEPTION 'payroll hold requires at least one impact scope'
      USING ERRCODE='23514';
  END IF;

  UPDATE employee_payroll.payroll_hold_version
     SET lifecycle_status='SUPERSEDED',updated_at=p_approved_at,
         updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND payroll_hold_id=v_hold_id
     AND id<>p_version_id AND lifecycle_status='ACTIVE';

  UPDATE employee_payroll.payroll_hold_version
     SET lifecycle_status='ACTIVE',approval_evidence_ref=p_evidence_ref,
         approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND lifecycle_status='DRAFT' AND version_no=p_expected_version;
  GET DIAGNOSTICS v_affected=ROW_COUNT;
  IF v_affected=1 THEN
    UPDATE employee_payroll.payroll_hold
       SET status='ACTIVE',updated_at=p_approved_at,updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=v_hold_id;
  END IF;
  RETURN v_affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.release_payroll_hold_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_released_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  v_hold_id uuid;
  v_maker varchar(160);
  v_affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  SELECT payroll_hold_id,created_by INTO v_hold_id,v_maker
    FROM employee_payroll.payroll_hold_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND lifecycle_status='ACTIVE' AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_actor=v_maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref)=''
     OR p_released_at IS NULL THEN
    RAISE EXCEPTION 'independent payroll hold release evidence is required'
      USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.payroll_hold_version
     SET lifecycle_status='RELEASED',release_evidence_ref=p_evidence_ref,
         released_at=p_released_at,released_by=p_actor,
         updated_at=p_released_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND lifecycle_status='ACTIVE' AND version_no=p_expected_version;
  GET DIAGNOSTICS v_affected=ROW_COUNT;
  IF v_affected=1 THEN
    UPDATE employee_payroll.payroll_hold
       SET status='RELEASED',updated_at=p_released_at,updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=v_hold_id;
  END IF;
  RETURN v_affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.payroll_readiness_findings(
  p_tenant_id uuid,
  p_payroll_relationship_id uuid,
  p_currency_code varchar,
  p_as_of date
) RETURNS TABLE (
  dimension varchar,
  severity varchar,
  status varchar,
  finding_code varchar,
  detail varchar,
  source_kind varchar,
  source_reference varchar
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, organisation, platform AS $$
DECLARE
  v_dimension varchar;
  v_applicability varchar;
  v_severity varchar;
  v_policy_ref varchar;
  v_currency varchar(3);
  v_payment record;
  v_emitted boolean;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_payroll_relationship_id IS NULL OR p_as_of IS NULL THEN
    RAISE EXCEPTION 'relationship and as-of date are required' USING ERRCODE='23514';
  END IF;

  SELECT coalesce(p_currency_code, profile.currency::text)
    INTO v_currency
    FROM employee_payroll.employee_payroll_profile profile
   WHERE profile.tenant_id=p_tenant_id
     AND profile.payroll_relationship_id=p_payroll_relationship_id;

  FOREACH v_dimension IN ARRAY ARRAY[
    'IDENTITY','ASSIGNMENT','COMPENSATION','CALENDAR','PAYMENT',
    'STATUTORY','TAX','DOCUMENTATION','APPROVAL','INTEGRATION'
  ] LOOP
    SELECT policy.applicability,policy.severity,
           ('READINESS_POLICY:'||policy.id::text||':'||policy.evidence_ref)
      INTO v_applicability,v_severity,v_policy_ref
      FROM employee_payroll.payroll_readiness_policy_version policy
     WHERE policy.tenant_id=p_tenant_id
       AND policy.dimension=v_dimension
       AND policy.effective_from<=p_as_of
       AND (policy.effective_to IS NULL OR p_as_of<policy.effective_to)
     ORDER BY policy.version_sequence DESC
     LIMIT 1;

    IF NOT FOUND THEN
      v_applicability := 'REQUIRED';
      v_severity := 'BLOCKING';
      v_policy_ref := 'DEFAULT_REQUIRED_BLOCKING';
    END IF;

    IF v_applicability='EXPLICIT_NOT_APPLICABLE' THEN
      RETURN QUERY SELECT v_dimension,v_severity,
        'EXPLICIT_NOT_APPLICABLE'::varchar,'POLICY_NOT_APPLICABLE'::varchar,
        'Approved organisational policy marks this dimension not applicable.'::varchar,
        'READINESS_POLICY'::varchar,v_policy_ref;
      CONTINUE;
    END IF;

    IF v_dimension='IDENTITY' THEN
      IF EXISTS (
        SELECT 1
          FROM employee_payroll.payroll_identifier_version version
         WHERE version.tenant_id=p_tenant_id
           AND version.payroll_relationship_id=p_payroll_relationship_id
           AND version.lifecycle_status='ACTIVE'
           AND version.effective_from<=p_as_of
           AND (version.effective_to IS NULL OR p_as_of<version.effective_to)
      ) AND NOT EXISTS (
        SELECT 1 FROM employee_payroll.identity_mismatch_case mismatch
         WHERE mismatch.tenant_id=p_tenant_id
           AND mismatch.payroll_relationship_id=p_payroll_relationship_id
           AND mismatch.status='OPEN' AND mismatch.payment_impact='BLOCKING'
      ) THEN
        RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
          'IDENTITY_READY'::varchar,'Approved identity evidence is effective.'::varchar,
          'PAYROLL_IDENTIFIER'::varchar,v_policy_ref;
      ELSE
        RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
          'IDENTITY_EVIDENCE_INCOMPLETE'::varchar,
          'Active approved identifier evidence is missing or a blocking mismatch is open.'::varchar,
          'PAYROLL_IDENTIFIER_OR_MISMATCH'::varchar,v_policy_ref;
      END IF;

    ELSIF v_dimension='ASSIGNMENT' THEN
      IF EXISTS (
        SELECT 1
          FROM employee_payroll.payroll_relationship_version relationship_version
          JOIN employee_payroll.payroll_assignment assignment
            ON assignment.tenant_id=relationship_version.tenant_id
           AND assignment.payroll_relationship_id=relationship_version.payroll_relationship_id
          JOIN employee_payroll.payroll_assignment_version assignment_version
            ON assignment_version.tenant_id=assignment.tenant_id
           AND assignment_version.payroll_assignment_id=assignment.id
         WHERE relationship_version.tenant_id=p_tenant_id
           AND relationship_version.payroll_relationship_id=p_payroll_relationship_id
           AND relationship_version.approval_status='APPROVED'
           AND relationship_version.relationship_start<=p_as_of
           AND (relationship_version.relationship_end IS NULL
                OR p_as_of<relationship_version.relationship_end)
           AND assignment_version.approval_status='APPROVED'
           AND assignment_version.assignment_start<=p_as_of
           AND (assignment_version.assignment_end IS NULL
                OR p_as_of<assignment_version.assignment_end)
           AND NOT EXISTS (
             SELECT 1 FROM employee_payroll.payroll_assignment_version successor
              WHERE successor.tenant_id=assignment_version.tenant_id
                AND successor.supersedes_version_id=assignment_version.id)
      ) THEN
        RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
          'ASSIGNMENT_READY'::varchar,'Approved assignment evidence is effective.'::varchar,
          'PAYROLL_ASSIGNMENT'::varchar,v_policy_ref;
      ELSE
        RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
          'ASSIGNMENT_EVIDENCE_INCOMPLETE'::varchar,
          'No current approved payroll assignment exists.'::varchar,
          'PAYROLL_ASSIGNMENT'::varchar,v_policy_ref;
      END IF;

    ELSIF v_dimension='COMPENSATION' THEN
      IF EXISTS (
        SELECT 1 FROM employee_payroll.salary_assignment salary
        JOIN employee_payroll.payroll_assignment_version av
          ON av.tenant_id=salary.tenant_id AND av.id=salary.payroll_assignment_version_id
        JOIN employee_payroll.payroll_assignment assignment
          ON assignment.tenant_id=av.tenant_id AND assignment.id=av.payroll_assignment_id
        WHERE salary.tenant_id=p_tenant_id
          AND assignment.payroll_relationship_id=p_payroll_relationship_id
          AND salary.approval_status='APPROVED'
          AND salary.effective_from<=p_as_of
          AND (salary.effective_to IS NULL OR p_as_of<salary.effective_to)
          AND NOT EXISTS (
            SELECT 1 FROM employee_payroll.salary_assignment successor
             WHERE successor.tenant_id=salary.tenant_id
               AND successor.supersedes_assignment_id=salary.id)
      ) THEN
        RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
          'COMPENSATION_READY'::varchar,'Approved compensation binding is effective.'::varchar,
          'SALARY_ASSIGNMENT'::varchar,v_policy_ref;
      ELSE
        RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
          'COMPENSATION_BINDING_MISSING'::varchar,
          'No current approved compensation binding exists.'::varchar,
          'SALARY_ASSIGNMENT'::varchar,v_policy_ref;
      END IF;

    ELSIF v_dimension='CALENDAR' THEN
      IF EXISTS (
        SELECT 1 FROM employee_payroll.pay_group_assignment group_assignment
        JOIN employee_payroll.payroll_assignment_version av
          ON av.tenant_id=group_assignment.tenant_id
         AND av.id=group_assignment.payroll_assignment_version_id
        JOIN employee_payroll.payroll_assignment assignment
          ON assignment.tenant_id=av.tenant_id AND assignment.id=av.payroll_assignment_id
        JOIN organisation.pay_group_version group_version
          ON group_version.tenant_id=group_assignment.tenant_id
         AND group_version.id=group_assignment.pay_group_version_id
        WHERE group_assignment.tenant_id=p_tenant_id
          AND assignment.payroll_relationship_id=p_payroll_relationship_id
          AND group_assignment.approval_status='APPROVED'
          AND group_assignment.effective_from<=p_as_of
          AND (group_assignment.effective_to IS NULL OR p_as_of<group_assignment.effective_to)
          AND group_version.approval_status='APPROVED'
          AND NOT EXISTS (
            SELECT 1 FROM employee_payroll.pay_group_assignment successor
             WHERE successor.tenant_id=group_assignment.tenant_id
               AND successor.supersedes_assignment_id=group_assignment.id)
      ) THEN
        RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
          'CALENDAR_READY'::varchar,'Approved pay-group/calendar binding is effective.'::varchar,
          'PAY_GROUP_ASSIGNMENT'::varchar,v_policy_ref;
      ELSE
        RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
          'CALENDAR_BINDING_MISSING'::varchar,
          'No current approved pay-group/calendar binding exists.'::varchar,
          'PAY_GROUP_ASSIGNMENT'::varchar,v_policy_ref;
      END IF;

    ELSIF v_dimension='PAYMENT' THEN
      IF v_currency IS NULL OR v_currency !~ '^[A-Z]{3}$' THEN
        RETURN QUERY SELECT v_dimension,v_severity,'NOT_EVALUATED'::varchar,
          'PAYMENT_CURRENCY_NOT_EVALUATED'::varchar,
          'Payment readiness cannot be evaluated without profile currency evidence.'::varchar,
          'EMPLOYEE_PAYROLL_PROFILE'::varchar,v_policy_ref;
      ELSE
        v_emitted := false;
        FOR v_payment IN
          SELECT * FROM employee_payroll.payment_readiness_findings(
            p_tenant_id,p_payroll_relationship_id,v_currency,p_as_of)
        LOOP
          v_emitted := true;
          RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
            v_payment.finding_code::varchar,v_payment.detail::varchar,
            'PAYMENT_READINESS'::varchar,v_policy_ref;
        END LOOP;
        IF NOT v_emitted THEN
          RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
            'PAYMENT_READY'::varchar,'Payment readiness has no effective findings.'::varchar,
            'PAYMENT_READINESS'::varchar,v_policy_ref;
        END IF;
      END IF;

    ELSIF v_dimension='APPROVAL' THEN
      IF EXISTS (
        SELECT 1 FROM employee_payroll.payroll_onboarding_case onboarding
         WHERE onboarding.tenant_id=p_tenant_id
           AND onboarding.payroll_relationship_id=p_payroll_relationship_id
           AND onboarding.current_status IN ('READINESS','COMPLETED')
      ) THEN
        RETURN QUERY SELECT v_dimension,v_severity,'READY'::varchar,
          'APPROVAL_READY'::varchar,
          'Onboarding approval has advanced to readiness.'::varchar,
          'PAYROLL_ONBOARDING'::varchar,v_policy_ref;
      ELSE
        RETURN QUERY SELECT v_dimension,v_severity,'BLOCKED'::varchar,
          'ONBOARDING_APPROVAL_INCOMPLETE'::varchar,
          'Onboarding has not reached approved readiness state.'::varchar,
          'PAYROLL_ONBOARDING'::varchar,v_policy_ref;
      END IF;

    ELSE
      RETURN QUERY SELECT v_dimension,v_severity,'NOT_EVALUATED'::varchar,
        (v_dimension||'_PROVIDER_NOT_EVALUATED')::varchar,
        ('No approved provider evidence is available for '||v_dimension||'.')::varchar,
        'PROVIDER_OR_POLICY'::varchar,v_policy_ref;
    END IF;
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.transition_payroll_onboarding(
  p_tenant_id uuid,
  p_case_id uuid,
  p_expected_version bigint,
  p_target_status varchar,
  p_reason varchar,
  p_evidence_ref varchar,
  p_actor varchar,
  p_occurred_at timestamptz,
  p_as_of date,
  p_require_independent boolean
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  v_from varchar(24);
  v_maker varchar(160);
  v_relationship uuid;
  v_sequence integer;
  v_affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  SELECT current_status,created_by,payroll_relationship_id
    INTO v_from,v_maker,v_relationship
    FROM employee_payroll.payroll_onboarding_case
   WHERE tenant_id=p_tenant_id AND id=p_case_id
     AND version_no=p_expected_version FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor)=''
     OR p_reason IS NULL OR btrim(p_reason)=''
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref)=''
     OR p_occurred_at IS NULL THEN
    RAISE EXCEPTION 'complete onboarding transition evidence is required'
      USING ERRCODE='23514';
  END IF;
  IF p_require_independent AND p_actor=v_maker THEN
    RAISE EXCEPTION 'independent onboarding approver is required'
      USING ERRCODE='42501';
  END IF;
  IF v_from IN ('COMPLETED','CANCELLED') THEN
    RAISE EXCEPTION 'terminal onboarding case cannot transition'
      USING ERRCODE='23514';
  END IF;
  IF NOT (
    (v_from='DATA_COLLECTION' AND p_target_status IN ('VALIDATION','ON_HOLD','CANCELLED')) OR
    (v_from='VALIDATION' AND p_target_status IN ('DATA_COLLECTION','APPROVAL','ON_HOLD','CANCELLED')) OR
    (v_from='APPROVAL' AND p_target_status IN ('VALIDATION','READINESS','ON_HOLD','CANCELLED')) OR
    (v_from='READINESS' AND p_target_status IN ('VALIDATION','COMPLETED','ON_HOLD','CANCELLED')) OR
    (v_from='ON_HOLD' AND p_target_status IN ('DATA_COLLECTION','VALIDATION','APPROVAL','READINESS','CANCELLED'))
  ) THEN
    RAISE EXCEPTION 'unsupported onboarding status transition'
      USING ERRCODE='23514';
  END IF;
  IF p_target_status='READINESS' AND NOT p_require_independent THEN
    RAISE EXCEPTION 'approval-to-readiness transition requires approver authority'
      USING ERRCODE='42501';
  END IF;
  IF p_target_status='COMPLETED' THEN
    IF NOT p_require_independent OR p_as_of IS NULL THEN
      RAISE EXCEPTION 'completion requires approver authority and as-of date'
        USING ERRCODE='42501';
    END IF;
    IF EXISTS (
      SELECT 1 FROM employee_payroll.payroll_readiness_findings(
        p_tenant_id,v_relationship,NULL,p_as_of) finding
       WHERE finding.severity='BLOCKING'
         AND finding.status NOT IN ('READY','EXPLICIT_NOT_APPLICABLE')) THEN
      RAISE EXCEPTION 'blocking readiness finding prevents onboarding completion'
        USING ERRCODE='23514';
    END IF;
    IF EXISTS (
      SELECT 1
        FROM employee_payroll.payroll_hold_version hold_version
        JOIN employee_payroll.payroll_hold_scope hold_scope
          ON hold_scope.tenant_id=hold_version.tenant_id
         AND hold_scope.payroll_hold_version_id=hold_version.id
       WHERE hold_version.tenant_id=p_tenant_id
         AND hold_version.payroll_relationship_id=v_relationship
         AND hold_version.lifecycle_status='ACTIVE'
         AND hold_version.effective_from<=p_as_of
         AND (hold_version.effective_to IS NULL OR p_as_of<hold_version.effective_to)
         AND hold_scope.scope='CALCULATION') THEN
      RAISE EXCEPTION 'active calculation hold prevents onboarding completion'
        USING ERRCODE='23514';
    END IF;
  END IF;

  SELECT coalesce(max(event_sequence),0)+1 INTO v_sequence
    FROM employee_payroll.payroll_onboarding_event
   WHERE tenant_id=p_tenant_id AND onboarding_case_id=p_case_id;

  INSERT INTO employee_payroll.payroll_onboarding_event(
    tenant_id,onboarding_case_id,payroll_relationship_id,event_sequence,
    from_status,to_status,reason,evidence_ref,occurred_at,actor
  ) VALUES (
    p_tenant_id,p_case_id,v_relationship,v_sequence,
    v_from,p_target_status,p_reason,p_evidence_ref,p_occurred_at,p_actor
  );

  UPDATE employee_payroll.payroll_onboarding_case
     SET current_status=p_target_status,updated_at=p_occurred_at,
         updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_case_id
     AND version_no=p_expected_version;
  GET DIAGNOSTICS v_affected=ROW_COUNT;
  RETURN v_affected;
END $$;

CREATE OR REPLACE FUNCTION payroll_ops.enrich_employee_operational_snapshot_v052()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, payroll_ops, employee_payroll, organisation, platform, public AS $$
DECLARE
  v_relationship_id uuid;
  v_as_of date;
  v_currency varchar(3);
  v_onboarding jsonb;
  v_readiness jsonb := '[]'::jsonb;
  v_holds jsonb := '[]'::jsonb;
  v_readiness_hash char(64);
  v_hold_hash char(64);
  v_has_onboarding boolean := false;
  v_has_readiness_policy boolean := false;
  v_has_active_hold boolean := false;
BEGIN
  SELECT version.payroll_relationship_id
    INTO v_relationship_id
    FROM employee_payroll.payroll_relationship_version version
   WHERE version.tenant_id=NEW.tenant_id
     AND version.id=NEW.payroll_relationship_version_id;
  IF v_relationship_id IS NULL THEN
    RAISE EXCEPTION 'V052 snapshot requires exact payroll relationship identity'
      USING ERRCODE='23503';
  END IF;

  SELECT period.period_end
    INTO v_as_of
    FROM payroll_ops.payroll_cycle cycle
    JOIN organisation.pay_period period
      ON period.tenant_id=cycle.tenant_id AND period.id=cycle.pay_period_id
   WHERE cycle.tenant_id=NEW.tenant_id AND cycle.id=NEW.payroll_cycle_id;

  SELECT profile.currency::text INTO v_currency
    FROM employee_payroll.employee_payroll_profile profile
   WHERE profile.tenant_id=NEW.tenant_id
     AND profile.payroll_relationship_id=v_relationship_id;

  SELECT jsonb_build_object(
      'caseId', onboarding.id::text,
      'status', onboarding.current_status,
      'versionNo', onboarding.version_no,
      'updatedAt', onboarding.updated_at,
      'updatedBy', onboarding.updated_by)
    INTO v_onboarding
    FROM employee_payroll.payroll_onboarding_case onboarding
   WHERE onboarding.tenant_id=NEW.tenant_id
     AND onboarding.payroll_relationship_id=v_relationship_id;

  v_has_onboarding := v_onboarding IS NOT NULL;

  SELECT EXISTS (
    SELECT 1
      FROM employee_payroll.payroll_readiness_policy_version policy
     WHERE policy.tenant_id=NEW.tenant_id
       AND policy.effective_from<=v_as_of
       AND (policy.effective_to IS NULL OR v_as_of<policy.effective_to)
  ) INTO v_has_readiness_policy;

  SELECT EXISTS (
    SELECT 1
      FROM employee_payroll.payroll_hold_version hold_version
     WHERE hold_version.tenant_id=NEW.tenant_id
       AND hold_version.payroll_relationship_id=v_relationship_id
       AND hold_version.lifecycle_status='ACTIVE'
       AND hold_version.effective_from<=v_as_of
       AND (hold_version.effective_to IS NULL OR v_as_of<hold_version.effective_to)
  ) INTO v_has_active_hold;

  -- Preserve the pre-V052 snapshot contract for relationships that have no
  -- P5-EOR operational evidence. This keeps historical/legacy payroll flows
  -- deterministic while making V052 fail-closed once P5-EOR is applicable.
  IF NOT (v_has_onboarding OR v_has_readiness_policy OR v_has_active_hold) THEN
    RETURN NEW;
  END IF;

  IF v_has_onboarding AND v_onboarding->>'status' <> 'COMPLETED' THEN
    RAISE EXCEPTION 'completed onboarding evidence is required before input sealing'
      USING ERRCODE='23514';
  END IF;

  IF v_has_onboarding OR v_has_readiness_policy THEN
    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'dimension',finding.dimension,
        'severity',finding.severity,
        'status',finding.status,
        'code',finding.finding_code,
        'detail',finding.detail,
        'sourceKind',finding.source_kind,
        'sourceReference',finding.source_reference)
        ORDER BY finding.dimension,finding.finding_code), '[]'::jsonb)
      INTO v_readiness
      FROM employee_payroll.payroll_readiness_findings(
        NEW.tenant_id,v_relationship_id,v_currency,v_as_of) finding;

    IF EXISTS (
      SELECT 1 FROM employee_payroll.payroll_readiness_findings(
        NEW.tenant_id,v_relationship_id,v_currency,v_as_of) finding
       WHERE finding.severity='BLOCKING'
         AND finding.status NOT IN ('READY','EXPLICIT_NOT_APPLICABLE')) THEN
      RAISE EXCEPTION 'blocking employee readiness prevents input sealing'
        USING ERRCODE='23514';
    END IF;
  END IF;

  SELECT coalesce(jsonb_agg(item ORDER BY item->>'holdId',item->>'versionId'), '[]'::jsonb)
    INTO v_holds
    FROM (
      SELECT jsonb_build_object(
        'holdId',hold_version.payroll_hold_id::text,
        'versionId',hold_version.id::text,
        'versionSequence',hold_version.version_sequence,
        'versionNo',hold_version.version_no,
        'reasonCode',hold_version.reason_code,
        'sourceReference',hold_version.source_reference,
        'effectiveFrom',hold_version.effective_from,
        'effectiveTo',hold_version.effective_to,
        'scopes',(
          SELECT jsonb_agg(scope.scope ORDER BY scope.scope)
            FROM employee_payroll.payroll_hold_scope scope
           WHERE scope.tenant_id=hold_version.tenant_id
             AND scope.payroll_hold_version_id=hold_version.id)
      ) item
      FROM employee_payroll.payroll_hold_version hold_version
      WHERE hold_version.tenant_id=NEW.tenant_id
        AND hold_version.payroll_relationship_id=v_relationship_id
        AND hold_version.lifecycle_status='ACTIVE'
        AND hold_version.effective_from<=v_as_of
        AND (hold_version.effective_to IS NULL OR v_as_of<hold_version.effective_to)
    ) active_hold;

  IF EXISTS (
    SELECT 1
      FROM employee_payroll.payroll_hold_version hold_version
      JOIN employee_payroll.payroll_hold_scope scope
        ON scope.tenant_id=hold_version.tenant_id
       AND scope.payroll_hold_version_id=hold_version.id
     WHERE hold_version.tenant_id=NEW.tenant_id
       AND hold_version.payroll_relationship_id=v_relationship_id
       AND hold_version.lifecycle_status='ACTIVE'
       AND hold_version.effective_from<=v_as_of
       AND (hold_version.effective_to IS NULL OR v_as_of<hold_version.effective_to)
       AND scope.scope='CALCULATION') THEN
    RAISE EXCEPTION 'active calculation hold prevents input sealing'
      USING ERRCODE='23514';
  END IF;

  v_readiness_hash := encode(public.digest(v_readiness::text,'sha256'),'hex');
  v_hold_hash := encode(public.digest(v_holds::text,'sha256'),'hex');

  NEW.snapshot_payload := jsonb_set(
      NEW.snapshot_payload,'{schemaVersion}','2'::jsonb,true)
    || jsonb_build_object(
      'employeeOperationalState',jsonb_build_object(
        'payrollRelationshipId',v_relationship_id::text,
        'asOf',v_as_of,
        'onboarding',v_onboarding,
        'readiness',v_readiness,
        'readinessSetHash',v_readiness_hash,
        'activeHolds',v_holds,
        'activeHoldSetHash',v_hold_hash));
  NEW.payload_schema_version := 2;
  NEW.employee_readiness_set_hash := v_readiness_hash;
  NEW.active_payroll_hold_set_hash := v_hold_hash;
  NEW.snapshot_hash := encode(public.digest(NEW.snapshot_payload::text,'sha256'),'hex');
  RETURN NEW;
END $$;
CREATE TRIGGER input_snapshot_employee_operational_enrichment_v052
  BEFORE INSERT ON payroll_ops.input_snapshot
  FOR EACH ROW EXECUTE FUNCTION payroll_ops.enrich_employee_operational_snapshot_v052();

DO $$
DECLARE relation_name text;
BEGIN
  FOREACH relation_name IN ARRAY ARRAY[
    'payroll_onboarding_case','payroll_onboarding_event',
    'payroll_readiness_policy_version','payroll_hold',
    'payroll_hold_version','payroll_hold_scope'
  ] LOOP
    EXECUTE format('ALTER TABLE employee_payroll.%I ENABLE ROW LEVEL SECURITY', relation_name);
    EXECUTE format('ALTER TABLE employee_payroll.%I FORCE ROW LEVEL SECURITY', relation_name);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON employee_payroll.%I '
      || 'USING (tenant_id = platform.current_tenant_id()) '
      || 'WITH CHECK (tenant_id = platform.current_tenant_id())', relation_name);
  END LOOP;
END $$;

GRANT SELECT ON
  employee_payroll.payroll_onboarding_case,
  employee_payroll.payroll_onboarding_event,
  employee_payroll.payroll_readiness_policy_version,
  employee_payroll.payroll_hold,
  employee_payroll.payroll_hold_version,
  employee_payroll.payroll_hold_scope
TO payroll_app;

REVOKE INSERT,UPDATE,DELETE ON
  employee_payroll.payroll_onboarding_case,
  employee_payroll.payroll_onboarding_event,
  employee_payroll.payroll_readiness_policy_version,
  employee_payroll.payroll_hold,
  employee_payroll.payroll_hold_version,
  employee_payroll.payroll_hold_scope
FROM payroll_app;

REVOKE ALL ON FUNCTION employee_payroll.create_payroll_onboarding_case(
  uuid,uuid,uuid,varchar,varchar,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.create_payroll_readiness_policy_version(
  uuid,uuid,varchar,varchar,varchar,varchar,varchar,date,date,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.create_payroll_hold_version(
  uuid,uuid,uuid,uuid,varchar,varchar,varchar,varchar,date,date,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.approve_payroll_hold_version(
  uuid,uuid,bigint,varchar,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.release_payroll_hold_version(
  uuid,uuid,bigint,varchar,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.payroll_readiness_findings(
  uuid,uuid,varchar,date) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.transition_payroll_onboarding(
  uuid,uuid,bigint,varchar,varchar,varchar,varchar,timestamptz,date,boolean) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION employee_payroll.create_payroll_onboarding_case(
  uuid,uuid,uuid,varchar,varchar,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.create_payroll_readiness_policy_version(
  uuid,uuid,varchar,varchar,varchar,varchar,varchar,date,date,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.create_payroll_hold_version(
  uuid,uuid,uuid,uuid,varchar,varchar,varchar,varchar,date,date,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.approve_payroll_hold_version(
  uuid,uuid,bigint,varchar,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.release_payroll_hold_version(
  uuid,uuid,bigint,varchar,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.payroll_readiness_findings(
  uuid,uuid,varchar,date) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.transition_payroll_onboarding(
  uuid,uuid,bigint,varchar,varchar,varchar,varchar,timestamptz,date,boolean) TO payroll_app;

REVOKE ALL ON FUNCTION payroll_ops.enrich_employee_operational_snapshot_v052()
  FROM PUBLIC;

COMMENT ON TABLE employee_payroll.payroll_onboarding_event IS
  'Append-only employee payroll onboarding lifecycle evidence.';
COMMENT ON TABLE employee_payroll.payroll_readiness_policy_version IS
  'Versioned organisation readiness policy evidence; EXPLICIT_NOT_APPLICABLE requires evidence.';
COMMENT ON TABLE employee_payroll.payroll_hold_version IS
  'History-preserving generic payroll hold versions with scoped impact and maker-checker approval.';
COMMENT ON COLUMN payroll_ops.input_snapshot.employee_readiness_set_hash IS
  'SHA-256 of exact P5-EOR readiness evidence embedded in immutable input snapshot payload schema 2.';
COMMENT ON COLUMN payroll_ops.input_snapshot.active_payroll_hold_set_hash IS
  'SHA-256 of exact active generic payroll hold evidence embedded in immutable input snapshot payload schema 2.';
