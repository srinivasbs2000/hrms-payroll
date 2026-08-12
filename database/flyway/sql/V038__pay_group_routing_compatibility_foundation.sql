-- P5-A4 G01: deterministic pay-group routing and compatibility foundation.
--
-- Preserve the existing V017 pay-group identity/version model, V018 monthly
-- calendar/period API, and V021 approved explicit pay-group assignment model.
-- An approved explicit assignment is the governed override. Routing rules are
-- defaults used only when no approved explicit assignment is effective.

CREATE TABLE organisation.pay_group_routing_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  pay_group_version_id uuid NOT NULL,
  payroll_statutory_unit_version_id uuid NOT NULL,
  establishment_version_id uuid,
  priority integer NOT NULL DEFAULT 100,
  effective_from date NOT NULL,
  effective_to date,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (priority > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, pay_group_version_id)
    REFERENCES organisation.pay_group_version(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_statutory_unit_version_id)
    REFERENCES organisation.payroll_statutory_unit_version(tenant_id, id),
  FOREIGN KEY (tenant_id, establishment_version_id)
    REFERENCES organisation.establishment_version(tenant_id, id)
);

ALTER TABLE organisation.pay_group_routing_rule
  ADD CONSTRAINT pay_group_routing_rule_psu_priority_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_statutory_unit_version_id WITH =,
    priority WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  )
  WHERE (status = 'ACTIVE' AND establishment_version_id IS NULL);

ALTER TABLE organisation.pay_group_routing_rule
  ADD CONSTRAINT pay_group_routing_rule_est_priority_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_statutory_unit_version_id WITH =,
    establishment_version_id WITH =,
    priority WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  )
  WHERE (status = 'ACTIVE' AND establishment_version_id IS NOT NULL);

CREATE INDEX pay_group_routing_rule_resolution_ix
  ON organisation.pay_group_routing_rule(
    tenant_id,
    payroll_statutory_unit_version_id,
    establishment_version_id,
    priority,
    effective_from DESC
  )
  WHERE status = 'ACTIVE';

ALTER TABLE organisation.pay_group_routing_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group_routing_rule FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation
  ON organisation.pay_group_routing_rule
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE FUNCTION organisation.assert_pay_group_routing_rule_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_group_status varchar;
  v_group_psu uuid;
  v_group_from date;
  v_group_to date;
  v_psu_status varchar;
  v_psu_from date;
  v_psu_to date;
  v_establishment_status varchar;
  v_establishment_psu uuid;
  v_establishment_from date;
  v_establishment_to date;
BEGIN
  SELECT
    group_version.approval_status,
    group_version.payroll_statutory_unit_version_id,
    group_version.effective_from,
    group_version.effective_to
  INTO
    v_group_status,
    v_group_psu,
    v_group_from,
    v_group_to
  FROM organisation.pay_group_version group_version
  WHERE group_version.tenant_id = NEW.tenant_id
    AND group_version.id = NEW.pay_group_version_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'pay-group version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_group_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'routing rules require an approved pay-group version'
      USING ERRCODE = '23514';
  END IF;

  IF v_group_psu <> NEW.payroll_statutory_unit_version_id THEN
    RAISE EXCEPTION 'routing rule PSU must match the pay-group PSU'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < v_group_from
     OR (
       v_group_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > v_group_to
       )
     ) THEN
    RAISE EXCEPTION 'routing rule range must be contained by the pay-group version'
      USING ERRCODE = '23514';
  END IF;

  SELECT psu.approval_status, psu.effective_from, psu.effective_to
  INTO v_psu_status, v_psu_from, v_psu_to
  FROM organisation.payroll_statutory_unit_version psu
  WHERE psu.tenant_id = NEW.tenant_id
    AND psu.id = NEW.payroll_statutory_unit_version_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'routing rule PSU does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_psu_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'routing rules require an approved payroll statutory unit'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < v_psu_from
     OR (
       v_psu_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > v_psu_to
       )
     ) THEN
    RAISE EXCEPTION 'routing rule range must be contained by the payroll statutory unit'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.establishment_version_id IS NOT NULL THEN
    SELECT
      establishment.approval_status,
      establishment.payroll_statutory_unit_version_id,
      establishment.effective_from,
      establishment.effective_to
    INTO
      v_establishment_status,
      v_establishment_psu,
      v_establishment_from,
      v_establishment_to
    FROM organisation.establishment_version establishment
    WHERE establishment.tenant_id = NEW.tenant_id
      AND establishment.id = NEW.establishment_version_id;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'routing rule establishment does not exist in the current tenant'
        USING ERRCODE = '23503';
    END IF;

    IF v_establishment_status <> 'APPROVED' THEN
      RAISE EXCEPTION 'routing rules require an approved establishment'
        USING ERRCODE = '23514';
    END IF;

    IF v_establishment_psu <> NEW.payroll_statutory_unit_version_id THEN
      RAISE EXCEPTION 'routing rule establishment must belong to the routing-rule PSU'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.effective_from < v_establishment_from
       OR (
         v_establishment_to IS NOT NULL
         AND (
           NEW.effective_to IS NULL
           OR NEW.effective_to > v_establishment_to
         )
       ) THEN
      RAISE EXCEPTION 'routing rule range must be contained by the establishment version'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_routing_rule_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    pay_group_version_id,
    payroll_statutory_unit_version_id,
    establishment_version_id,
    effective_from,
    effective_to,
    status
  ON organisation.pay_group_routing_rule
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_pay_group_routing_rule_dependencies();

CREATE FUNCTION organisation.create_pay_group_routing_rule(
  p_tenant_id uuid,
  p_pay_group_version_id uuid,
  p_payroll_statutory_unit_version_id uuid,
  p_establishment_version_id uuid,
  p_priority integer,
  p_effective_from date,
  p_effective_to date,
  p_actor varchar
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_rule_id uuid := gen_random_uuid();
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required' USING ERRCODE = '23514';
  END IF;

  INSERT INTO organisation.pay_group_routing_rule(
    id, tenant_id, pay_group_version_id,
    payroll_statutory_unit_version_id, establishment_version_id,
    priority, effective_from, effective_to, status, created_by, updated_by
  ) VALUES (
    v_rule_id, p_tenant_id, p_pay_group_version_id,
    p_payroll_statutory_unit_version_id, p_establishment_version_id,
    coalesce(p_priority, 100), p_effective_from, p_effective_to,
    'ACTIVE', p_actor, p_actor
  );

  RETURN v_rule_id;
END $$;

CREATE FUNCTION organisation.retire_pay_group_routing_rule(
  p_tenant_id uuid,
  p_rule_id uuid,
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

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required' USING ERRCODE = '23514';
  END IF;

  UPDATE organisation.pay_group_routing_rule
  SET status = 'INACTIVE',
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE tenant_id = p_tenant_id
    AND id = p_rule_id
    AND status = 'ACTIVE'
    AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION organisation.pay_group_assignment_compatibility_issues(
  p_tenant_id uuid,
  p_payroll_assignment_version_id uuid,
  p_pay_group_version_id uuid,
  p_effective_from date,
  p_effective_to date
) RETURNS TABLE (
  issue_code varchar,
  issue_detail varchar
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, organisation, employee_payroll, platform AS $$
DECLARE
  v_assignment_status varchar;
  v_assignment_from date;
  v_assignment_to date;
  v_establishment_id uuid;
  v_establishment_status varchar;
  v_establishment_psu uuid;
  v_group_status varchar;
  v_group_psu uuid;
  v_group_from date;
  v_group_to date;
  v_group_calendar uuid;
  v_calendar_frequency varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  IF p_effective_from IS NULL
     OR (
       p_effective_to IS NOT NULL
       AND p_effective_to <= p_effective_from
     ) THEN
    RETURN QUERY SELECT
      'INVALID_EFFECTIVE_RANGE'::varchar,
      'effectiveTo must be greater than effectiveFrom'::varchar;
    RETURN;
  END IF;

  SELECT
    assignment.approval_status,
    assignment.assignment_start,
    assignment.assignment_end,
    assignment.establishment_version_id
  INTO
    v_assignment_status,
    v_assignment_from,
    v_assignment_to,
    v_establishment_id
  FROM employee_payroll.payroll_assignment_version assignment
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_payroll_assignment_version_id;

  IF NOT FOUND THEN
    RETURN QUERY SELECT
      'ASSIGNMENT_NOT_FOUND'::varchar,
      'payroll assignment version does not exist in the current tenant'::varchar;
    RETURN;
  END IF;

  IF v_assignment_status <> 'APPROVED' THEN
    RETURN QUERY SELECT
      'ASSIGNMENT_NOT_APPROVED'::varchar,
      'payroll assignment version must be approved'::varchar;
  END IF;

  IF p_effective_from < v_assignment_from
     OR (
       v_assignment_to IS NOT NULL
       AND (
         p_effective_to IS NULL
         OR p_effective_to > v_assignment_to
       )
     ) THEN
    RETURN QUERY SELECT
      'ASSIGNMENT_RANGE_MISMATCH'::varchar,
      'pay-group assignment range must be contained by payroll assignment version'::varchar;
  END IF;

  SELECT
    establishment.approval_status,
    establishment.payroll_statutory_unit_version_id
  INTO
    v_establishment_status,
    v_establishment_psu
  FROM organisation.establishment_version establishment
  WHERE establishment.tenant_id = p_tenant_id
    AND establishment.id = v_establishment_id;

  IF NOT FOUND THEN
    RETURN QUERY SELECT
      'ESTABLISHMENT_NOT_FOUND'::varchar,
      'establishment version does not exist in the current tenant'::varchar;
    RETURN;
  END IF;

  IF v_establishment_status <> 'APPROVED' THEN
    RETURN QUERY SELECT
      'ESTABLISHMENT_NOT_APPROVED'::varchar,
      'establishment version must be approved'::varchar;
  END IF;

  SELECT
    group_version.approval_status,
    group_version.payroll_statutory_unit_version_id,
    group_version.effective_from,
    group_version.effective_to,
    group_version.calendar_id
  INTO
    v_group_status,
    v_group_psu,
    v_group_from,
    v_group_to,
    v_group_calendar
  FROM organisation.pay_group_version group_version
  WHERE group_version.tenant_id = p_tenant_id
    AND group_version.id = p_pay_group_version_id;

  IF NOT FOUND THEN
    RETURN QUERY SELECT
      'PAY_GROUP_NOT_FOUND'::varchar,
      'pay-group version does not exist in the current tenant'::varchar;
    RETURN;
  END IF;

  IF v_group_status <> 'APPROVED' THEN
    RETURN QUERY SELECT
      'PAY_GROUP_NOT_APPROVED'::varchar,
      'pay-group version must be approved'::varchar;
  END IF;

  IF p_effective_from < v_group_from
     OR (
       v_group_to IS NOT NULL
       AND (
         p_effective_to IS NULL
         OR p_effective_to > v_group_to
       )
     ) THEN
    RETURN QUERY SELECT
      'PAY_GROUP_RANGE_MISMATCH'::varchar,
      'pay-group assignment range must be contained by pay-group version'::varchar;
  END IF;

  IF v_group_psu <> v_establishment_psu THEN
    RETURN QUERY SELECT
      'PSU_MISMATCH'::varchar,
      'pay-group PSU must match the payroll assignment establishment PSU'::varchar;
  END IF;

  SELECT calendar.frequency
  INTO v_calendar_frequency
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = v_group_calendar;

  IF NOT FOUND THEN
    RETURN QUERY SELECT
      'CALENDAR_NOT_FOUND'::varchar,
      'pay-group calendar does not exist in the current tenant'::varchar;
    RETURN;
  END IF;

  -- G01 intentionally preserves the V018 monthly contract. P5-A4 G02 owns
  -- expansion to additional frequencies and milestone generation.
  IF v_calendar_frequency <> 'MONTHLY' THEN
    RETURN QUERY SELECT
      'CALENDAR_FREQUENCY_UNSUPPORTED'::varchar,
      'G01 compatibility requires the current monthly calendar contract'::varchar;
  END IF;
END $$;

-- V021 pay_group_assignment_dependencies remains the authoritative write guard.
-- G01 exposes the reusable compatibility contract above for routing and later
-- operational validation; it does not add a duplicate assignment trigger.

CREATE FUNCTION organisation.resolve_pay_group_version_for_assignment(
  p_tenant_id uuid,
  p_payroll_assignment_version_id uuid,
  p_as_of date
) RETURNS TABLE (
  pay_group_version_id uuid,
  resolution_source varchar,
  routing_rule_id uuid
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, organisation, employee_payroll, platform AS $$
DECLARE
  v_explicit_group uuid;
  v_assignment_status varchar;
  v_assignment_from date;
  v_assignment_to date;
  v_establishment_id uuid;
  v_establishment_psu uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  IF p_as_of IS NULL THEN
    RAISE EXCEPTION 'as-of date is required' USING ERRCODE = '23514';
  END IF;

  SELECT explicit_assignment.pay_group_version_id
  INTO v_explicit_group
  FROM employee_payroll.pay_group_assignment explicit_assignment
  WHERE explicit_assignment.tenant_id = p_tenant_id
    AND explicit_assignment.payroll_assignment_version_id =
        p_payroll_assignment_version_id
    AND explicit_assignment.approval_status = 'APPROVED'
    AND explicit_assignment.effective_from <= p_as_of
    AND (
      explicit_assignment.effective_to IS NULL
      OR explicit_assignment.effective_to > p_as_of
    )
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.pay_group_assignment successor
      WHERE successor.tenant_id = explicit_assignment.tenant_id
        AND successor.supersedes_assignment_id = explicit_assignment.id
    )
  ORDER BY explicit_assignment.effective_from DESC,
           explicit_assignment.id
  LIMIT 1;

  IF FOUND THEN
    RETURN QUERY SELECT
      v_explicit_group,
      'EXPLICIT_ASSIGNMENT'::varchar,
      NULL::uuid;
    RETURN;
  END IF;

  SELECT
    assignment.approval_status,
    assignment.assignment_start,
    assignment.assignment_end,
    assignment.establishment_version_id,
    establishment.payroll_statutory_unit_version_id
  INTO
    v_assignment_status,
    v_assignment_from,
    v_assignment_to,
    v_establishment_id,
    v_establishment_psu
  FROM employee_payroll.payroll_assignment_version assignment
  JOIN organisation.establishment_version establishment
    ON establishment.tenant_id = assignment.tenant_id
   AND establishment.id = assignment.establishment_version_id
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_payroll_assignment_version_id;

  IF NOT FOUND
     OR v_assignment_status <> 'APPROVED'
     OR p_as_of < v_assignment_from
     OR (
       v_assignment_to IS NOT NULL
       AND p_as_of >= v_assignment_to
     ) THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT
    rule.pay_group_version_id,
    CASE
      WHEN rule.establishment_version_id IS NOT NULL
        THEN 'ESTABLISHMENT_RULE'::varchar
      ELSE 'PSU_RULE'::varchar
    END,
    rule.id
  FROM organisation.pay_group_routing_rule rule
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = rule.tenant_id
   AND group_version.id = rule.pay_group_version_id
  WHERE rule.tenant_id = p_tenant_id
    AND rule.status = 'ACTIVE'
    AND rule.payroll_statutory_unit_version_id = v_establishment_psu
    AND (
      rule.establishment_version_id IS NULL
      OR rule.establishment_version_id = v_establishment_id
    )
    AND rule.effective_from <= p_as_of
    AND (
      rule.effective_to IS NULL
      OR rule.effective_to > p_as_of
    )
    AND group_version.approval_status = 'APPROVED'
    AND group_version.effective_from <= p_as_of
    AND (
      group_version.effective_to IS NULL
      OR group_version.effective_to > p_as_of
    )
  ORDER BY
    (rule.establishment_version_id IS NOT NULL) DESC,
    rule.priority ASC,
    rule.effective_from DESC,
    rule.id
  LIMIT 1;
END $$;

REVOKE ALL ON organisation.pay_group_routing_rule FROM PUBLIC;
GRANT SELECT ON organisation.pay_group_routing_rule TO payroll_app;
REVOKE INSERT, UPDATE, DELETE
  ON organisation.pay_group_routing_rule FROM payroll_app;

REVOKE ALL ON FUNCTION organisation.create_pay_group_routing_rule(
  uuid, uuid, uuid, uuid, integer, date, date, varchar
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.retire_pay_group_routing_rule(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.pay_group_assignment_compatibility_issues(
  uuid, uuid, uuid, date, date
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.resolve_pay_group_version_for_assignment(
  uuid, uuid, date
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.create_pay_group_routing_rule(
  uuid, uuid, uuid, uuid, integer, date, date, varchar
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.retire_pay_group_routing_rule(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.pay_group_assignment_compatibility_issues(
  uuid, uuid, uuid, date, date
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.resolve_pay_group_version_for_assignment(
  uuid, uuid, date
) TO payroll_app;

REVOKE CREATE ON SCHEMA organisation FROM payroll_app;
REVOKE CREATE ON SCHEMA employee_payroll FROM payroll_app;

COMMENT ON TABLE organisation.pay_group_routing_rule IS
  'P5-A4 deterministic default pay-group routing. Approved explicit employee pay-group assignments remain the governed override.';
COMMENT ON FUNCTION organisation.resolve_pay_group_version_for_assignment(
  uuid, uuid, date
) IS
  'Resolves approved explicit pay-group assignment first, then establishment-scoped routing, then PSU-scoped routing.';
COMMENT ON FUNCTION organisation.pay_group_assignment_compatibility_issues(
  uuid, uuid, uuid, date, date
) IS
  'Reusable compatibility contract for routing and later operational validation; V021 remains the authoritative assignment write guard.';
