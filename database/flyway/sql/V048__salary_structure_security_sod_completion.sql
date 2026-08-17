-- P5-SSC-01 G02L / E04-014 salary-structure security and SoD completion.
--
-- V047 already enforces:
--   * maker cannot approve their own schema-1 submission;
--   * maker cannot reject their own schema-1 submission;
--   * submit/approve/reject/publish actions are immutable workflow evidence;
--   * workflow action history is FORCE RLS protected;
--   * lifecycle writes occur through controlled SECURITY DEFINER functions.
--
-- V048 closes the remaining publication segregation-of-duties gap:
-- the publisher must be distinct from both the current submitter (maker)
-- and the final approver (checker). Existing event names, workflow states,
-- API routes and approved historical records remain unchanged.

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
  v_submitted_by varchar;
  v_approved_by varchar;
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
         version.validation_fingerprint,
         version.submitted_by,
         version.approved_by
    INTO v_version_no,
         v_configuration_hash,
         v_validation_fingerprint,
         v_submitted_by,
         v_approved_by
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

  IF v_submitted_by IS NULL
     OR btrim(v_submitted_by)=''
     OR v_approved_by IS NULL
     OR btrim(v_approved_by)='' THEN
    RAISE EXCEPTION
      'published salary structure requires a complete maker-checker approval chain'
      USING ERRCODE='23514';
  END IF;

  IF p_actor=v_submitted_by THEN
    RAISE EXCEPTION
      'salary-structure maker cannot publish their own submission'
      USING ERRCODE='23514';
  END IF;

  IF p_actor=v_approved_by THEN
    RAISE EXCEPTION
      'salary-structure approver cannot publish their own approval'
      USING ERRCODE='23514';
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

REVOKE ALL ON FUNCTION compensation.publish_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.publish_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) TO payroll_app;

COMMENT ON FUNCTION compensation.publish_salary_structure_version(
  uuid,uuid,uuid,bigint,varchar,varchar,timestamptz
) IS
  'P5-SSC-01 E04-014 governed publication: schema-1 publisher must be distinct from both submitter and final approver.';
