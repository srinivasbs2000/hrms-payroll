CREATE FUNCTION organisation.assert_version_parent_range()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_from date; parent_to date;
BEGIN
  IF TG_TABLE_NAME = 'payroll_statutory_unit_version' THEN
    SELECT effective_from, effective_to INTO parent_from, parent_to
    FROM organisation.legal_entity_version
    WHERE tenant_id = NEW.tenant_id AND id = NEW.legal_entity_version_id;
  ELSE
    SELECT effective_from, effective_to INTO parent_from, parent_to
    FROM organisation.payroll_statutory_unit_version
    WHERE tenant_id = NEW.tenant_id AND id = NEW.payroll_statutory_unit_version_id;
  END IF;
  IF parent_from IS NULL THEN
    RAISE EXCEPTION 'parent version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;
  IF NEW.effective_from < parent_from
     OR (parent_to IS NOT NULL AND (NEW.effective_to IS NULL OR NEW.effective_to > parent_to)) THEN
    RAISE EXCEPTION 'child effective range must be contained by its parent version'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER psu_version_parent_range
  BEFORE INSERT OR UPDATE ON organisation.payroll_statutory_unit_version
  FOR EACH ROW EXECUTE FUNCTION organisation.assert_version_parent_range();
CREATE TRIGGER establishment_version_parent_range
  BEFORE INSERT OR UPDATE ON organisation.establishment_version
  FOR EACH ROW EXECUTE FUNCTION organisation.assert_version_parent_range();

CREATE FUNCTION organisation.approve_version(
  p_kind varchar,
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, organisation, platform AS $$
DECLARE affected bigint; target_table text;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  target_table := CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN 'legal_entity_version'
    WHEN 'PAYROLL_STATUTORY_UNIT' THEN 'payroll_statutory_unit_version'
    WHEN 'ESTABLISHMENT' THEN 'establishment_version'
    ELSE NULL END;
  IF target_table IS NULL THEN RAISE EXCEPTION 'unsupported organisation kind'; END IF;
  EXECUTE format(
    'UPDATE organisation.%I v SET approval_status=''APPROVED'', approved_at=$1, approved_by=$2, updated_at=$1, updated_by=$2, version_no=version_no+1 '
    || 'WHERE tenant_id=$3 AND id=$4 AND approval_status=''DRAFT'' '
    || 'AND NOT EXISTS (SELECT 1 FROM organisation.%I successor WHERE successor.tenant_id=v.tenant_id AND successor.supersedes_version_id=v.id)',
    target_table, target_table)
    USING p_approved_at, p_actor, p_tenant_id, p_version_id;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION organisation.end_date_version(
  p_kind varchar,
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, organisation, platform AS $$
DECLARE affected bigint; target_table text;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  target_table := CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN 'legal_entity_version'
    WHEN 'PAYROLL_STATUTORY_UNIT' THEN 'payroll_statutory_unit_version'
    WHEN 'ESTABLISHMENT' THEN 'establishment_version'
    ELSE NULL END;
  IF target_table IS NULL THEN RAISE EXCEPTION 'unsupported organisation kind'; END IF;
  EXECUTE format(
    'UPDATE organisation.%I SET effective_to=$1,updated_at=$2,updated_by=$3,version_no=version_no+1 '
    || 'WHERE tenant_id=$4 AND id=$5 AND version_no=$6 AND effective_from < $1 AND (effective_to IS NULL OR effective_to > $1)',
    target_table)
    USING p_effective_to, p_changed_at, p_actor, p_tenant_id, p_version_id, p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION organisation.approve_version(varchar,uuid,uuid,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.end_date_version(varchar,uuid,uuid,date,bigint,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION organisation.approve_version(varchar,uuid,uuid,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.end_date_version(varchar,uuid,uuid,date,bigint,varchar,timestamptz) TO payroll_app;
