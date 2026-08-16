-- P5-CCF-01 runtime row-lock authority hardening.
-- V040 intentionally keeps UPDATE revoked from payroll_app. PostgreSQL locking reads
-- (SELECT ... FOR UPDATE) also require UPDATE privilege, so application code must
-- obtain identity-row locks through narrow tenant-checked SECURITY DEFINER functions.

CREATE FUNCTION compensation.lock_component_rate_table(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  status varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;

  SELECT lifecycle_status
    INTO status
    FROM compensation.component_rate_table
   WHERE tenant_id=p_tenant_id AND id=p_identity_id
   FOR UPDATE;

  RETURN status;
END $$;

CREATE FUNCTION compensation.lock_component_rounding_policy(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  status varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;

  SELECT lifecycle_status
    INTO status
    FROM compensation.component_rounding_policy
   WHERE tenant_id=p_tenant_id AND id=p_identity_id
   FOR UPDATE;

  RETURN status;
END $$;

CREATE FUNCTION compensation.lock_component_proration_policy(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  status varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;

  SELECT lifecycle_status
    INTO status
    FROM compensation.component_proration_policy
   WHERE tenant_id=p_tenant_id AND id=p_identity_id
   FOR UPDATE;

  RETURN status;
END $$;

REVOKE ALL ON FUNCTION compensation.lock_component_rate_table(uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.lock_component_rounding_policy(uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.lock_component_proration_policy(uuid,uuid) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.lock_component_rate_table(uuid,uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.lock_component_rounding_policy(uuid,uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.lock_component_proration_policy(uuid,uuid) TO payroll_app;

COMMENT ON FUNCTION compensation.lock_component_rate_table(uuid,uuid) IS
  'Tenant-checked row-lock authority for serializing component rate-table version creation without granting payroll_app UPDATE.';
COMMENT ON FUNCTION compensation.lock_component_rounding_policy(uuid,uuid) IS
  'Tenant-checked row-lock authority for serializing component rounding-policy version creation without granting payroll_app UPDATE.';
COMMENT ON FUNCTION compensation.lock_component_proration_policy(uuid,uuid) IS
  'Tenant-checked row-lock authority for serializing component proration-policy version creation without granting payroll_app UPDATE.';
