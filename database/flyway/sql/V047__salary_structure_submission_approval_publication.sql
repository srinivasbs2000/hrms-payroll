-- P5-SSC-01 G02H / E04-010 salary-structure maker-checker lifecycle.
--
-- Schema-1 salary structures now move through an explicit governed lifecycle:
-- DRAFT -> SUBMITTED -> APPROVED -> PUBLISHED. Rejection returns an unapproved
-- submission to DRAFT while preserving immutable decision history. Existing
-- approval_status remains the compatibility contract for downstream tables;
-- publication is the additional product-governance condition for discovery.
-- Legacy schema-0 approval remains backward compatible and is treated as
-- published when approved.

ALTER TABLE compensation.salary_structure_version
  ADD COLUMN workflow_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN submitted_at timestamptz,
  ADD COLUMN submitted_by varchar(160),
  ADD COLUMN published_at timestamptz,
  ADD COLUMN published_by varchar(160);

ALTER TABLE compensation.salary_structure_version
  ADD CONSTRAINT salary_structure_version_workflow_status_ck
    CHECK (workflow_status IN (
      'DRAFT','SUBMITTED','APPROVED','PUBLISHED','REJECTED'
    )),
  ADD CONSTRAINT salary_structure_version_submission_metadata_ck
    CHECK (
      (workflow_status='SUBMITTED'
       AND submitted_at IS NOT NULL
       AND submitted_by IS NOT NULL
       AND btrim(submitted_by)<>'')
      OR workflow_status<>'SUBMITTED'
    ),
  ADD CONSTRAINT salary_structure_version_publication_metadata_ck
    CHECK (
      (workflow_status='PUBLISHED'
       AND approval_status='APPROVED'
       AND published_at IS NOT NULL
       AND published_by IS NOT NULL
       AND btrim(published_by)<>'')
      OR workflow_status<>'PUBLISHED'
    );

UPDATE compensation.salary_structure_version
   SET workflow_status = CASE
         WHEN approval_status='APPROVED' THEN 'PUBLISHED'
         WHEN approval_status='REJECTED' THEN 'REJECTED'
         ELSE 'DRAFT'
       END,
       submitted_at = CASE
         WHEN approval_status='APPROVED' THEN coalesce(approved_at,created_at)
         ELSE NULL
       END,
       submitted_by = CASE
         WHEN approval_status='APPROVED' THEN coalesce(approved_by,created_by)
         ELSE NULL
       END,
       published_at = CASE
         WHEN approval_status='APPROVED' THEN coalesce(approved_at,created_at)
         ELSE NULL
       END,
       published_by = CASE
         WHEN approval_status='APPROVED' THEN coalesce(approved_by,created_by)
         ELSE NULL
       END;

CREATE TABLE compensation.salary_structure_workflow_action (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  salary_structure_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  action_sequence integer NOT NULL,
  action_type varchar(20) NOT NULL,
  actor varchar(160) NOT NULL,
  occurred_at timestamptz NOT NULL,
  comment varchar(1000),
  configuration_hash varchar(64) NOT NULL,
  validation_fingerprint varchar(64),
  statutory_binding_revision bigint NOT NULL DEFAULT 0,
  statutory_evidence_hash varchar(64),
  structure_version_no bigint NOT NULL,
  action_hash varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,salary_structure_version_id,action_sequence),
  CHECK (action_sequence>0),
  CHECK (action_type IN ('SUBMITTED','APPROVED','REJECTED','PUBLISHED')),
  CHECK (btrim(actor)<>''),
  CHECK (comment IS NULL OR length(btrim(comment)) BETWEEN 1 AND 1000),
  CHECK (configuration_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    validation_fingerprint IS NULL
    OR validation_fingerprint ~ '^[0-9a-f]{64}$'
  ),
  CHECK (statutory_binding_revision>=0),
  CHECK (
    statutory_evidence_hash IS NULL
    OR statutory_evidence_hash ~ '^[0-9a-f]{64}$'
  ),
  CHECK (structure_version_no>=0),
  CHECK (action_hash ~ '^[0-9a-f]{64}$'),
  FOREIGN KEY (tenant_id,salary_structure_id)
    REFERENCES compensation.salary_structure(tenant_id,id),
  FOREIGN KEY (
    tenant_id,
    salary_structure_version_id,
    salary_structure_id
  ) REFERENCES compensation.salary_structure_version(
    tenant_id,
    id,
    salary_structure_id
  )
);

CREATE INDEX salary_structure_workflow_action_lookup_ix
  ON compensation.salary_structure_workflow_action(
    tenant_id,
    salary_structure_version_id,
    action_sequence
  );

ALTER TABLE compensation.salary_structure_workflow_action
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_workflow_action
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON compensation.salary_structure_workflow_action
  USING (tenant_id=platform.current_tenant_id())
  WITH CHECK (tenant_id=platform.current_tenant_id());

CREATE OR REPLACE FUNCTION
  compensation.reject_salary_structure_workflow_action_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'salary-structure workflow actions are immutable'
    USING ERRCODE='42501';
END $$;

CREATE TRIGGER salary_structure_workflow_action_immutable
  BEFORE UPDATE OR DELETE
  ON compensation.salary_structure_workflow_action
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.reject_salary_structure_workflow_action_mutation();

CREATE OR REPLACE FUNCTION
  compensation.append_salary_structure_workflow_action(
    p_tenant_id uuid,
    p_salary_structure_id uuid,
    p_salary_structure_version_id uuid,
    p_action_type varchar,
    p_actor varchar,
    p_occurred_at timestamptz,
    p_comment varchar,
    p_configuration_hash varchar,
    p_validation_fingerprint varchar,
    p_statutory_binding_revision bigint,
    p_statutory_evidence_hash varchar,
    p_structure_version_no bigint
  ) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform, public AS $$
DECLARE
  v_id uuid:=gen_random_uuid();
  v_sequence integer;
  v_hash varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;

  IF p_action_type NOT IN ('SUBMITTED','APPROVED','REJECTED','PUBLISHED')
     OR p_actor IS NULL OR btrim(p_actor)=''
     OR p_occurred_at IS NULL
     OR p_configuration_hash IS NULL THEN
    RAISE EXCEPTION 'complete workflow action metadata is required'
      USING ERRCODE='23514';
  END IF;

  IF p_comment IS NOT NULL
     AND length(btrim(p_comment)) NOT BETWEEN 1 AND 1000 THEN
    RAISE EXCEPTION 'workflow comment must contain between 1 and 1000 characters'
      USING ERRCODE='23514';
  END IF;

  SELECT coalesce(max(action.action_sequence),0)+1
    INTO v_sequence
    FROM compensation.salary_structure_workflow_action action
   WHERE action.tenant_id=p_tenant_id
     AND action.salary_structure_version_id=p_salary_structure_version_id;

  v_hash:=encode(
    public.digest(
      concat_ws(
        ':',
        p_tenant_id::text,
        p_salary_structure_id::text,
        p_salary_structure_version_id::text,
        v_sequence::text,
        p_action_type,
        p_actor,
        p_occurred_at::text,
        coalesce(btrim(p_comment),''),
        p_configuration_hash,
        coalesce(p_validation_fingerprint,''),
        p_statutory_binding_revision::text,
        coalesce(p_statutory_evidence_hash,''),
        p_structure_version_no::text
      ),
      'sha256'
    ),
    'hex'
  );

  INSERT INTO compensation.salary_structure_workflow_action(
    id,
    tenant_id,
    salary_structure_id,
    salary_structure_version_id,
    action_sequence,
    action_type,
    actor,
    occurred_at,
    comment,
    configuration_hash,
    validation_fingerprint,
    statutory_binding_revision,
    statutory_evidence_hash,
    structure_version_no,
    action_hash
  ) VALUES (
    v_id,
    p_tenant_id,
    p_salary_structure_id,
    p_salary_structure_version_id,
    v_sequence,
    p_action_type,
    p_actor,
    p_occurred_at,
    CASE WHEN p_comment IS NULL OR btrim(p_comment)='' THEN NULL ELSE btrim(p_comment) END,
    p_configuration_hash,
    p_validation_fingerprint,
    p_statutory_binding_revision,
    p_statutory_evidence_hash,
    p_structure_version_no,
    v_hash
  );

  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION compensation.submit_salary_structure_version(
  p_tenant_id uuid,
  p_salary_structure_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_comment varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  v_schema smallint;
  v_approval varchar;
  v_workflow varchar;
  v_version_no bigint;
  v_configuration_hash varchar;
  v_validation_fingerprint varchar;
  v_validation_id uuid;
  v_statutory_revision bigint:=0;
  v_statutory_evidence_hash varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)=''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'actor and submission timestamp are required'
      USING ERRCODE='23514';
  END IF;
  IF p_comment IS NOT NULL
     AND length(btrim(p_comment))>1000 THEN
    RAISE EXCEPTION 'submission comment must not exceed 1000 characters'
      USING ERRCODE='23514';
  END IF;

  SELECT version.structure_schema_version,
         version.approval_status,
         version.workflow_status,
         version.version_no,
         version.configuration_hash,
         version.validation_fingerprint
    INTO v_schema,
         v_approval,
         v_workflow,
         v_version_no,
         v_configuration_hash,
         v_validation_fingerprint
    FROM compensation.salary_structure_version version
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id
     AND version.salary_structure_id=p_salary_structure_id
   FOR UPDATE OF version;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;
  IF v_version_no<>p_expected_version THEN
    RETURN 0;
  END IF;
  IF v_schema<>1 OR v_approval<>'DRAFT' OR v_workflow<>'DRAFT' THEN
    RAISE EXCEPTION 'only a schema-1 draft can be submitted for approval'
      USING ERRCODE='23514';
  END IF;
  IF v_validation_fingerprint IS NULL THEN
    RAISE EXCEPTION 'submission requires a bound passing validation fingerprint'
      USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version successor
     WHERE successor.tenant_id=p_tenant_id
       AND successor.supersedes_version_id=p_version_id
  ) THEN
    RAISE EXCEPTION 'superseded salary-structure versions cannot be submitted'
      USING ERRCODE='23514';
  END IF;

  SELECT validation.id
    INTO v_validation_id
    FROM compensation.salary_structure_validation validation
   WHERE validation.tenant_id=p_tenant_id
     AND validation.salary_structure_version_id=p_version_id
     AND validation.validation_status='PASS'
     AND validation.blocking_error_count=0
     AND validation.configuration_hash=v_configuration_hash
     AND validation.result_hash=v_validation_fingerprint
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_validation newer
        WHERE newer.tenant_id=validation.tenant_id
          AND newer.salary_structure_version_id=validation.salary_structure_version_id
          AND (newer.created_at,newer.id)>(validation.created_at,validation.id)
     );

  IF v_validation_id IS NULL THEN
    RAISE EXCEPTION 'submission requires the latest passing bound structural validation'
      USING ERRCODE='23514';
  END IF;

  SELECT coalesce((
    SELECT state.binding_revision
      FROM compensation.salary_structure_statutory_state state
     WHERE state.tenant_id=p_tenant_id
       AND state.salary_structure_version_id=p_version_id
  ),0)
    INTO v_statutory_revision;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_statutory_binding binding
     WHERE binding.tenant_id=p_tenant_id
       AND binding.salary_structure_version_id=p_version_id
       AND binding.status='ACTIVE'
  ) THEN
    SELECT evaluation.evidence_hash
      INTO v_statutory_evidence_hash
      FROM compensation.salary_structure_statutory_evaluation evaluation
     WHERE evaluation.tenant_id=p_tenant_id
       AND evaluation.validation_id=v_validation_id
       AND evaluation.salary_structure_version_id=p_version_id
       AND evaluation.statutory_binding_revision=v_statutory_revision
       AND evaluation.validation_status='PASS'
       AND evaluation.blocking_issue_count=0
     ORDER BY evaluation.created_at DESC,evaluation.id DESC
     LIMIT 1;

    IF v_statutory_evidence_hash IS NULL THEN
      RAISE EXCEPTION 'submission requires current passing statutory compatibility evidence'
        USING ERRCODE='23514';
    END IF;
  END IF;

  UPDATE compensation.salary_structure_version version
     SET workflow_status='SUBMITTED',
         submitted_at=p_changed_at,
         submitted_by=p_actor,
         updated_at=p_changed_at,
         updated_by=p_actor,
         version_no=version_no+1
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id;

  PERFORM compensation.append_salary_structure_workflow_action(
    p_tenant_id,
    p_salary_structure_id,
    p_version_id,
    'SUBMITTED',
    p_actor,
    p_changed_at,
    p_comment,
    v_configuration_hash,
    v_validation_fingerprint,
    v_statutory_revision,
    v_statutory_evidence_hash,
    v_version_no+1
  );

  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION compensation.reject_salary_structure_submission(
  p_tenant_id uuid,
  p_salary_structure_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_reason varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  v_version_no bigint;
  v_submitted_by varchar;
  v_configuration_hash varchar;
  v_validation_fingerprint varchar;
  v_statutory_revision bigint;
  v_statutory_evidence_hash varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)=''
     OR p_changed_at IS NULL
     OR p_reason IS NULL
     OR length(btrim(p_reason)) NOT BETWEEN 1 AND 1000 THEN
    RAISE EXCEPTION 'checker, timestamp and rejection reason are required'
      USING ERRCODE='23514';
  END IF;

  SELECT version.version_no,
         version.submitted_by,
         version.configuration_hash,
         version.validation_fingerprint
    INTO v_version_no,
         v_submitted_by,
         v_configuration_hash,
         v_validation_fingerprint
    FROM compensation.salary_structure_version version
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id
     AND version.salary_structure_id=p_salary_structure_id
     AND version.structure_schema_version=1
     AND version.approval_status='DRAFT'
     AND version.workflow_status='SUBMITTED'
   FOR UPDATE OF version;

  IF NOT FOUND OR v_version_no<>p_expected_version THEN
    RETURN 0;
  END IF;
  IF v_submitted_by=p_actor THEN
    RAISE EXCEPTION 'maker cannot reject their own salary-structure submission'
      USING ERRCODE='23514';
  END IF;

  SELECT action.statutory_binding_revision,
         action.statutory_evidence_hash
    INTO v_statutory_revision,
         v_statutory_evidence_hash
    FROM compensation.salary_structure_workflow_action action
   WHERE action.tenant_id=p_tenant_id
     AND action.salary_structure_version_id=p_version_id
     AND action.action_type='SUBMITTED'
   ORDER BY action.action_sequence DESC
   LIMIT 1;

  UPDATE compensation.salary_structure_version version
     SET workflow_status='DRAFT',
         submitted_at=NULL,
         submitted_by=NULL,
         updated_at=p_changed_at,
         updated_by=p_actor,
         version_no=version_no+1
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id;

  PERFORM compensation.append_salary_structure_workflow_action(
    p_tenant_id,
    p_salary_structure_id,
    p_version_id,
    'REJECTED',
    p_actor,
    p_changed_at,
    p_reason,
    v_configuration_hash,
    v_validation_fingerprint,
    coalesce(v_statutory_revision,0),
    v_statutory_evidence_hash,
    v_version_no+1
  );

  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_workflow_approval()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  v_submission compensation.salary_structure_workflow_action%ROWTYPE;
  v_validation_id uuid;
  v_statutory_revision bigint:=0;
  v_statutory_evidence_hash varchar;
BEGIN
  IF OLD.approval_status<>'APPROVED'
     AND NEW.approval_status='APPROVED' THEN
    IF NEW.structure_schema_version=0 THEN
      NEW.workflow_status:='PUBLISHED';
      NEW.published_at:=coalesce(NEW.approved_at,clock_timestamp());
      NEW.published_by:=coalesce(NEW.approved_by,NEW.updated_by);
      RETURN NEW;
    END IF;

    IF OLD.workflow_status<>'SUBMITTED' THEN
      RAISE EXCEPTION 'schema-1 salary structures must be submitted before approval'
        USING ERRCODE='23514';
    END IF;
    IF OLD.submitted_by IS NULL OR OLD.submitted_by=NEW.approved_by THEN
      RAISE EXCEPTION 'salary-structure maker cannot be the final approver'
        USING ERRCODE='23514';
    END IF;

    SELECT action.*
      INTO v_submission
      FROM compensation.salary_structure_workflow_action action
     WHERE action.tenant_id=NEW.tenant_id
       AND action.salary_structure_version_id=NEW.id
       AND action.action_type='SUBMITTED'
     ORDER BY action.action_sequence DESC
     LIMIT 1;

    IF v_submission.id IS NULL
       OR v_submission.configuration_hash IS DISTINCT FROM NEW.configuration_hash
       OR v_submission.validation_fingerprint IS DISTINCT FROM NEW.validation_fingerprint THEN
      RAISE EXCEPTION 'approval evidence differs from the submitted salary-structure snapshot'
        USING ERRCODE='23514';
    END IF;

    SELECT validation.id
      INTO v_validation_id
      FROM compensation.salary_structure_validation validation
     WHERE validation.tenant_id=NEW.tenant_id
       AND validation.salary_structure_version_id=NEW.id
       AND validation.validation_status='PASS'
       AND validation.blocking_error_count=0
       AND validation.configuration_hash=NEW.configuration_hash
       AND validation.result_hash=NEW.validation_fingerprint
       AND NOT EXISTS (
         SELECT 1
           FROM compensation.salary_structure_validation newer
          WHERE newer.tenant_id=validation.tenant_id
            AND newer.salary_structure_version_id=validation.salary_structure_version_id
            AND (newer.created_at,newer.id)>(validation.created_at,validation.id)
       );

    SELECT coalesce((
      SELECT state.binding_revision
        FROM compensation.salary_structure_statutory_state state
       WHERE state.tenant_id=NEW.tenant_id
         AND state.salary_structure_version_id=NEW.id
    ),0)
      INTO v_statutory_revision;

    IF v_validation_id IS NULL
       OR v_submission.statutory_binding_revision IS DISTINCT FROM v_statutory_revision THEN
      RAISE EXCEPTION 'approval requires the exact structural validation submitted for review'
        USING ERRCODE='23514';
    END IF;

    IF EXISTS (
      SELECT 1
        FROM compensation.salary_structure_statutory_binding binding
       WHERE binding.tenant_id=NEW.tenant_id
         AND binding.salary_structure_version_id=NEW.id
         AND binding.status='ACTIVE'
    ) THEN
      SELECT evaluation.evidence_hash
        INTO v_statutory_evidence_hash
        FROM compensation.salary_structure_statutory_evaluation evaluation
       WHERE evaluation.tenant_id=NEW.tenant_id
         AND evaluation.validation_id=v_validation_id
         AND evaluation.salary_structure_version_id=NEW.id
         AND evaluation.statutory_binding_revision=v_statutory_revision
         AND evaluation.validation_status='PASS'
         AND evaluation.blocking_issue_count=0
       ORDER BY evaluation.created_at DESC,evaluation.id DESC
       LIMIT 1;

      IF v_statutory_evidence_hash IS NULL
         OR v_submission.statutory_evidence_hash IS DISTINCT FROM v_statutory_evidence_hash THEN
        RAISE EXCEPTION 'approval requires the exact statutory evidence submitted for review'
          USING ERRCODE='23514';
      END IF;
    ELSIF v_submission.statutory_evidence_hash IS NOT NULL THEN
      RAISE EXCEPTION 'submitted statutory evidence no longer matches active bindings'
        USING ERRCODE='23514';
    END IF;

    NEW.workflow_status:='APPROVED';

    PERFORM compensation.append_salary_structure_workflow_action(
      NEW.tenant_id,
      NEW.salary_structure_id,
      NEW.id,
      'APPROVED',
      NEW.approved_by,
      NEW.approved_at,
      NULL,
      NEW.configuration_hash,
      NEW.validation_fingerprint,
      v_statutory_revision,
      v_statutory_evidence_hash,
      NEW.version_no
    );
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_lifecycle_approval_guard
  BEFORE UPDATE OF approval_status
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_workflow_approval();

CREATE OR REPLACE FUNCTION compensation.publish_salary_structure_version(
  p_tenant_id uuid,
  p_salary_structure_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_comment varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  v_version_no bigint;
  v_configuration_hash varchar;
  v_validation_fingerprint varchar;
  v_statutory_revision bigint:=0;
  v_statutory_evidence_hash varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)=''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'publisher and publication timestamp are required'
      USING ERRCODE='23514';
  END IF;
  IF p_comment IS NOT NULL
     AND length(btrim(p_comment))>1000 THEN
    RAISE EXCEPTION 'publication comment must not exceed 1000 characters'
      USING ERRCODE='23514';
  END IF;

  SELECT version.version_no,
         version.configuration_hash,
         version.validation_fingerprint
    INTO v_version_no,
         v_configuration_hash,
         v_validation_fingerprint
    FROM compensation.salary_structure_version version
    JOIN compensation.salary_structure identity
      ON identity.tenant_id=version.tenant_id
     AND identity.id=version.salary_structure_id
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id
     AND version.salary_structure_id=p_salary_structure_id
     AND version.structure_schema_version=1
     AND version.approval_status='APPROVED'
     AND version.workflow_status='APPROVED'
     AND identity.status='ACTIVE'
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_structure_version successor
        WHERE successor.tenant_id=version.tenant_id
          AND successor.supersedes_version_id=version.id
     )
   FOR UPDATE OF version;

  IF NOT FOUND OR v_version_no<>p_expected_version THEN
    RETURN 0;
  END IF;

  SELECT action.statutory_binding_revision,
         action.statutory_evidence_hash
    INTO v_statutory_revision,
         v_statutory_evidence_hash
    FROM compensation.salary_structure_workflow_action action
   WHERE action.tenant_id=p_tenant_id
     AND action.salary_structure_version_id=p_version_id
     AND action.action_type='APPROVED'
   ORDER BY action.action_sequence DESC
   LIMIT 1;

  UPDATE compensation.salary_structure_version version
     SET workflow_status='PUBLISHED',
         published_at=p_changed_at,
         published_by=p_actor,
         updated_at=p_changed_at,
         updated_by=p_actor,
         version_no=version_no+1
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id;

  PERFORM compensation.append_salary_structure_workflow_action(
    p_tenant_id,
    p_salary_structure_id,
    p_version_id,
    'PUBLISHED',
    p_actor,
    p_changed_at,
    p_comment,
    v_configuration_hash,
    v_validation_fingerprint,
    coalesce(v_statutory_revision,0),
    v_statutory_evidence_hash,
    v_version_no+1
  );

  RETURN 1;
END $$;

-- A bound validation is part of the submitted evidence snapshot and cannot be
-- swapped after submission.
CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_validation_binding_workflow()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation AS $$
BEGIN
  IF NEW.validation_fingerprint IS DISTINCT FROM OLD.validation_fingerprint
     AND OLD.workflow_status<>'DRAFT' THEN
    RAISE EXCEPTION 'validation binding cannot change after salary-structure submission'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_validation_binding_workflow
  BEFORE UPDATE OF validation_fingerprint
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_validation_binding_workflow();

-- A correction successor cannot branch from a submitted/approved/published
-- schema-1 version. Rejection returns the source to DRAFT before correction.
CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_successor_workflow()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation AS $$
DECLARE
  v_parent_workflow varchar;
  v_parent_schema smallint;
BEGIN
  IF NEW.supersedes_version_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT parent.workflow_status,parent.structure_schema_version
    INTO v_parent_workflow,v_parent_schema
    FROM compensation.salary_structure_version parent
   WHERE parent.tenant_id=NEW.tenant_id
     AND parent.id=NEW.supersedes_version_id;

  IF v_parent_schema=1 AND v_parent_workflow<>'DRAFT' THEN
    RAISE EXCEPTION 'submitted salary-structure versions must be rejected before correction'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_successor_workflow
  BEFORE INSERT
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_successor_workflow();

-- Statutory bindings are part of submission evidence and therefore freeze at
-- submission. V046 remains the legal-authority owner and evaluator.
CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_statutory_binding_workflow()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation AS $$
DECLARE
  v_version_id uuid;
  v_workflow varchar;
BEGIN
  v_version_id:=CASE
    WHEN TG_OP='DELETE' THEN OLD.salary_structure_version_id
    ELSE NEW.salary_structure_version_id
  END;

  SELECT version.workflow_status
    INTO v_workflow
    FROM compensation.salary_structure_version version
   WHERE version.tenant_id=CASE WHEN TG_OP='DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END
     AND version.id=v_version_id;

  IF v_workflow IS DISTINCT FROM 'DRAFT' THEN
    RAISE EXCEPTION 'statutory bindings cannot change after salary-structure submission'
      USING ERRCODE='23514';
  END IF;

  IF TG_OP='DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_statutory_binding_workflow
  BEFORE INSERT OR UPDATE OR DELETE
  ON compensation.salary_structure_statutory_binding
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_statutory_binding_workflow();

REVOKE ALL ON FUNCTION compensation.append_salary_structure_workflow_action(
  uuid,uuid,uuid,varchar,varchar,timestamptz,varchar,varchar,varchar,bigint,varchar,bigint
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.submit_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_salary_structure_submission(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.publish_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) FROM PUBLIC;

GRANT SELECT (
  tenant_id,
  salary_structure_version_id,
  binding_revision
)
ON compensation.salary_structure_statutory_state
TO payroll_app;

GRANT SELECT ON compensation.salary_structure_workflow_action TO payroll_app;
REVOKE INSERT,UPDATE,DELETE
  ON compensation.salary_structure_workflow_action FROM payroll_app;

GRANT EXECUTE ON FUNCTION compensation.submit_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.reject_salary_structure_submission(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.publish_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) TO payroll_app;

COMMENT ON COLUMN compensation.salary_structure_version.workflow_status IS
  'P5-SSC-01 schema-1 governance lifecycle. approval_status remains downstream compatibility; only PUBLISHED versions are discoverable as effective salary structures.';
COMMENT ON TABLE compensation.salary_structure_workflow_action IS
  'Immutable submit/approve/reject/publish history with exact validation and statutory evidence snapshot hashes.';
