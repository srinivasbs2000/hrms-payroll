-- P5-E2E-UI-01-B02-R01: bounded routing-rule effective-end contract.
--
-- Direct routing-table mutation remains unavailable to payroll_app. Corrections
-- shorten an ACTIVE rule through optimistic concurrency; replacement routing
-- data continues to use organisation.create_pay_group_routing_rule.

CREATE FUNCTION organisation.end_date_pay_group_routing_rule(
  p_tenant_id uuid,
  p_rule_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_effective_from date;
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

  SELECT rule.effective_from
    INTO v_effective_from
    FROM organisation.pay_group_routing_rule rule
   WHERE rule.tenant_id = p_tenant_id
     AND rule.id = p_rule_id
     AND rule.status = 'ACTIVE'
     AND rule.version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_effective_to <= v_effective_from THEN
    RAISE EXCEPTION 'routing rule effective-to must be after effective-from'
      USING ERRCODE = '23514';
  END IF;

  UPDATE organisation.pay_group_routing_rule rule
     SET effective_to = p_effective_to,
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = rule.version_no + 1
   WHERE rule.tenant_id = p_tenant_id
     AND rule.id = p_rule_id
     AND rule.status = 'ACTIVE'
     AND rule.version_no = p_expected_version
     AND (rule.effective_to IS NULL OR rule.effective_to > p_effective_to);

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION organisation.end_date_pay_group_routing_rule(
  uuid, uuid, date, bigint, varchar, timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.end_date_pay_group_routing_rule(
  uuid, uuid, date, bigint, varchar, timestamptz
) TO payroll_app;

COMMENT ON FUNCTION organisation.end_date_pay_group_routing_rule(
  uuid, uuid, date, bigint, varchar, timestamptz
) IS
  'Optimistically shortens an ACTIVE pay-group routing rule while preserving direct payroll_app table-DML revocation.';
