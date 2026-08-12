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
-- P5-A4 G02: deterministic period, frequency and milestone engine.
--
-- The standard payroll frequency set is MONTHLY, FORTNIGHTLY, WEEKLY and
-- DAILY. CUSTOM is available only when explicitly authorised with a fixed
-- period-day policy. Existing V018 monthly commands remain supported.
--
-- Publication/amend/retire lifecycle and operational API/read-model closure
-- remain G03 responsibilities.

ALTER TABLE organisation.payroll_calendar
  DROP CONSTRAINT IF EXISTS payroll_calendar_frequency_check;

ALTER TABLE organisation.payroll_calendar
  ADD COLUMN custom_period_days integer,
  ADD COLUMN custom_frequency_authorised boolean NOT NULL DEFAULT false,
  ADD COLUMN weekend_iso_days smallint[] NOT NULL
    DEFAULT ARRAY[6, 7]::smallint[];

ALTER TABLE organisation.payroll_calendar
  ADD CONSTRAINT payroll_calendar_frequency_ck
  CHECK (
    frequency IN (
      'MONTHLY',
      'FORTNIGHTLY',
      'WEEKLY',
      'DAILY',
      'CUSTOM'
    )
  ),
  ADD CONSTRAINT payroll_calendar_custom_frequency_ck
  CHECK (
    (
      frequency = 'CUSTOM'
      AND custom_frequency_authorised
      AND custom_period_days BETWEEN 1 AND 366
    )
    OR
    (
      frequency <> 'CUSTOM'
      AND NOT custom_frequency_authorised
      AND custom_period_days IS NULL
    )
  ),
  ADD CONSTRAINT payroll_calendar_weekend_iso_days_ck
  CHECK (
    cardinality(weekend_iso_days) <= 7
    AND array_position(weekend_iso_days, NULL) IS NULL
    AND weekend_iso_days
      <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[]
  );

ALTER TABLE organisation.pay_period
  DROP CONSTRAINT IF EXISTS pay_period_month_boundaries_ck;

ALTER TABLE organisation.pay_period
  DROP CONSTRAINT IF EXISTS pay_period_payment_within_period_ck;

ALTER TABLE organisation.pay_period
  ADD CONSTRAINT pay_period_calendar_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    calendar_id WITH =,
    daterange(period_start, period_end, '[]') WITH &&
  );

CREATE TABLE organisation.payroll_calendar_milestone_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  calendar_id uuid NOT NULL,
  milestone_type varchar(24) NOT NULL,
  anchor_type varchar(24) NOT NULL,
  offset_days integer NOT NULL,
  adjustment_policy varchar(32) NOT NULL DEFAULT 'NONE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, calendar_id, milestone_type),
  CHECK (
    milestone_type IN (
      'INPUT_CUTOFF',
      'CALCULATION',
      'APPROVAL',
      'RELEASE',
      'PAYMENT'
    )
  ),
  CHECK (anchor_type IN ('PERIOD_START', 'PERIOD_END')),
  CHECK (offset_days BETWEEN -366 AND 366),
  CHECK (
    adjustment_policy IN (
      'NONE',
      'PREVIOUS_WORKING_DAY',
      'NEXT_WORKING_DAY'
    )
  ),
  FOREIGN KEY (tenant_id)
    REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, calendar_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id)
    ON DELETE CASCADE
);

CREATE TABLE organisation.payroll_calendar_holiday (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  calendar_id uuid NOT NULL,
  holiday_date date NOT NULL,
  holiday_name varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, calendar_id, holiday_date),
  CHECK (btrim(holiday_name) <> ''),
  FOREIGN KEY (tenant_id)
    REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, calendar_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id)
    ON DELETE CASCADE
);

CREATE TABLE organisation.pay_period_milestone (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  milestone_rule_id uuid NOT NULL,
  milestone_type varchar(24) NOT NULL,
  original_date date NOT NULL,
  adjusted_date date NOT NULL,
  adjustment_policy varchar(32) NOT NULL,
  adjustment_days integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, pay_period_id, milestone_type),
  CHECK (
    milestone_type IN (
      'INPUT_CUTOFF',
      'CALCULATION',
      'APPROVAL',
      'RELEASE',
      'PAYMENT'
    )
  ),
  CHECK (
    adjustment_policy IN (
      'NONE',
      'PREVIOUS_WORKING_DAY',
      'NEXT_WORKING_DAY'
    )
  ),
  CHECK (adjustment_days = adjusted_date - original_date),
  FOREIGN KEY (tenant_id)
    REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, pay_period_id)
    REFERENCES organisation.pay_period(tenant_id, id)
    ON DELETE CASCADE,
  FOREIGN KEY (tenant_id, milestone_rule_id)
    REFERENCES organisation.payroll_calendar_milestone_rule(tenant_id, id)
);

CREATE INDEX payroll_calendar_milestone_rule_lookup_ix
  ON organisation.payroll_calendar_milestone_rule(
    tenant_id,
    calendar_id,
    milestone_type
  );

CREATE INDEX payroll_calendar_holiday_lookup_ix
  ON organisation.payroll_calendar_holiday(
    tenant_id,
    calendar_id,
    holiday_date
  );

CREATE INDEX pay_period_milestone_lookup_ix
  ON organisation.pay_period_milestone(
    tenant_id,
    pay_period_id,
    milestone_type
  );

ALTER TABLE organisation.payroll_calendar_milestone_rule
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_calendar_milestone_rule
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON organisation.payroll_calendar_milestone_rule
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE organisation.payroll_calendar_holiday
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_calendar_holiday
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON organisation.payroll_calendar_holiday
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE organisation.pay_period_milestone
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_period_milestone
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON organisation.pay_period_milestone
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE FUNCTION organisation.create_payroll_calendar(
  p_tenant_id uuid,
  p_code varchar,
  p_name varchar,
  p_frequency varchar,
  p_timezone varchar,
  p_custom_period_days integer,
  p_custom_frequency_authorised boolean,
  p_weekend_iso_days smallint[],
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_calendar_id uuid := gen_random_uuid();
  v_frequency varchar := upper(btrim(p_frequency));
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_code IS NULL
     OR p_code !~ '^[A-Z][A-Z0-9_]{1,39}$' THEN
    RAISE EXCEPTION
      'calendar code must match ^[A-Z][A-Z0-9_]{1,39}$'
      USING ERRCODE = '23514';
  END IF;

  IF p_name IS NULL OR btrim(p_name) = '' THEN
    RAISE EXCEPTION 'calendar name is required'
      USING ERRCODE = '23514';
  END IF;

  IF v_frequency IS NULL
     OR v_frequency NOT IN (
       'MONTHLY',
       'FORTNIGHTLY',
       'WEEKLY',
       'DAILY',
       'CUSTOM'
     ) THEN
    RAISE EXCEPTION 'unsupported payroll frequency: %', p_frequency
      USING ERRCODE = '23514';
  END IF;

  IF v_frequency = 'CUSTOM' THEN
    IF NOT coalesce(p_custom_frequency_authorised, false)
       OR p_custom_period_days IS NULL
       OR p_custom_period_days < 1
       OR p_custom_period_days > 366 THEN
      RAISE EXCEPTION
        'custom frequency requires explicit authorisation and 1..366 period days'
        USING ERRCODE = '23514';
    END IF;
  ELSIF coalesce(p_custom_frequency_authorised, false)
        OR p_custom_period_days IS NOT NULL THEN
    RAISE EXCEPTION
      'custom frequency policy is valid only for CUSTOM calendars'
      USING ERRCODE = '23514';
  END IF;

  IF p_timezone IS NULL
     OR NOT EXISTS (
       SELECT 1
       FROM pg_timezone_names timezone_name
       WHERE timezone_name.name = p_timezone
     ) THEN
    RAISE EXCEPTION 'unknown IANA timezone: %', p_timezone
      USING ERRCODE = '23514';
  END IF;

  IF p_weekend_iso_days IS NULL
     OR cardinality(p_weekend_iso_days) > 7
     OR array_position(p_weekend_iso_days, NULL) IS NOT NULL
     OR NOT (
       p_weekend_iso_days
         <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[]
     ) THEN
    RAISE EXCEPTION
      'weekend ISO days must be a subset of 1..7'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO organisation.payroll_calendar(
    id,
    tenant_id,
    code,
    name,
    frequency,
    timezone,
    custom_period_days,
    custom_frequency_authorised,
    weekend_iso_days,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_calendar_id,
    p_tenant_id,
    p_code,
    btrim(p_name),
    v_frequency,
    p_timezone,
    p_custom_period_days,
    coalesce(p_custom_frequency_authorised, false),
    p_weekend_iso_days,
    p_created_at,
    p_actor,
    p_created_at,
    p_actor
  );

  RETURN v_calendar_id;
END $$;

CREATE FUNCTION organisation.configure_payroll_calendar_milestone_rule(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_milestone_type varchar,
  p_anchor_type varchar,
  p_offset_days integer,
  p_adjustment_policy varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_rule_id uuid;
  v_milestone_type varchar := upper(btrim(p_milestone_type));
  v_anchor_type varchar := upper(btrim(p_anchor_type));
  v_adjustment_policy varchar := upper(btrim(p_adjustment_policy));
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.payroll_calendar calendar
    WHERE calendar.tenant_id = p_tenant_id
      AND calendar.id = p_calendar_id
  ) THEN
    RAISE EXCEPTION
      'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_milestone_type NOT IN (
       'INPUT_CUTOFF',
       'CALCULATION',
       'APPROVAL',
       'RELEASE',
       'PAYMENT'
     ) THEN
    RAISE EXCEPTION 'unsupported milestone type: %', p_milestone_type
      USING ERRCODE = '23514';
  END IF;

  IF v_anchor_type NOT IN ('PERIOD_START', 'PERIOD_END') THEN
    RAISE EXCEPTION 'unsupported milestone anchor: %', p_anchor_type
      USING ERRCODE = '23514';
  END IF;

  IF p_offset_days IS NULL
     OR p_offset_days < -366
     OR p_offset_days > 366 THEN
    RAISE EXCEPTION 'milestone offset must be between -366 and 366'
      USING ERRCODE = '23514';
  END IF;

  IF v_adjustment_policy NOT IN (
       'NONE',
       'PREVIOUS_WORKING_DAY',
       'NEXT_WORKING_DAY'
     ) THEN
    RAISE EXCEPTION
      'unsupported milestone adjustment policy: %',
      p_adjustment_policy
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO organisation.payroll_calendar_milestone_rule(
    id,
    tenant_id,
    calendar_id,
    milestone_type,
    anchor_type,
    offset_days,
    adjustment_policy,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    gen_random_uuid(),
    p_tenant_id,
    p_calendar_id,
    v_milestone_type,
    v_anchor_type,
    p_offset_days,
    v_adjustment_policy,
    p_changed_at,
    p_actor,
    p_changed_at,
    p_actor
  )
  ON CONFLICT (tenant_id, calendar_id, milestone_type)
  DO UPDATE
  SET anchor_type = EXCLUDED.anchor_type,
      offset_days = EXCLUDED.offset_days,
      adjustment_policy = EXCLUDED.adjustment_policy,
      updated_at = EXCLUDED.updated_at,
      updated_by = EXCLUDED.updated_by,
      version_no =
        organisation.payroll_calendar_milestone_rule.version_no + 1
  RETURNING id INTO v_rule_id;

  RETURN v_rule_id;
END $$;

CREATE FUNCTION organisation.add_payroll_calendar_holiday(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_holiday_date date,
  p_holiday_name varchar,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_holiday_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.payroll_calendar calendar
    WHERE calendar.tenant_id = p_tenant_id
      AND calendar.id = p_calendar_id
  ) THEN
    RAISE EXCEPTION
      'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF p_holiday_date IS NULL THEN
    RAISE EXCEPTION 'holiday date is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_holiday_name IS NULL OR btrim(p_holiday_name) = '' THEN
    RAISE EXCEPTION 'holiday name is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO organisation.payroll_calendar_holiday(
    id,
    tenant_id,
    calendar_id,
    holiday_date,
    holiday_name,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    gen_random_uuid(),
    p_tenant_id,
    p_calendar_id,
    p_holiday_date,
    btrim(p_holiday_name),
    p_changed_at,
    p_actor,
    p_changed_at,
    p_actor
  )
  ON CONFLICT (tenant_id, calendar_id, holiday_date)
  DO UPDATE
  SET holiday_name = EXCLUDED.holiday_name,
      updated_at = EXCLUDED.updated_at,
      updated_by = EXCLUDED.updated_by,
      version_no = organisation.payroll_calendar_holiday.version_no + 1
  RETURNING id INTO v_holiday_id;

  RETURN v_holiday_id;
END $$;

CREATE FUNCTION organisation.is_payroll_calendar_working_day(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_date date
) RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_weekend_iso_days smallint[];
  v_iso_day smallint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_date IS NULL THEN
    RAISE EXCEPTION 'date is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT calendar.weekend_iso_days
  INTO v_weekend_iso_days
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = p_calendar_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  v_iso_day := extract(isodow FROM p_date)::smallint;

  IF v_iso_day = ANY(v_weekend_iso_days) THEN
    RETURN false;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.payroll_calendar_holiday holiday
    WHERE holiday.tenant_id = p_tenant_id
      AND holiday.calendar_id = p_calendar_id
      AND holiday.holiday_date = p_date
  ) THEN
    RETURN false;
  END IF;

  RETURN true;
END $$;

CREATE FUNCTION organisation.adjust_payroll_calendar_date(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_date date,
  p_adjustment_policy varchar
) RETURNS date
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_policy varchar := upper(btrim(p_adjustment_policy));
  v_candidate date := p_date;
  v_step integer;
  v_attempt integer := 0;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_date IS NULL THEN
    RAISE EXCEPTION 'date is required'
      USING ERRCODE = '23514';
  END IF;

  IF v_policy = 'NONE' THEN
    RETURN p_date;
  ELSIF v_policy = 'PREVIOUS_WORKING_DAY' THEN
    v_step := -1;
  ELSIF v_policy = 'NEXT_WORKING_DAY' THEN
    v_step := 1;
  ELSE
    RAISE EXCEPTION
      'unsupported milestone adjustment policy: %',
      p_adjustment_policy
      USING ERRCODE = '23514';
  END IF;

  WHILE NOT organisation.is_payroll_calendar_working_day(
    p_tenant_id,
    p_calendar_id,
    v_candidate
  ) LOOP
    v_candidate := v_candidate + v_step;
    v_attempt := v_attempt + 1;

    IF v_attempt > 370 THEN
      RAISE EXCEPTION
        'unable to resolve a working day within 370 days'
        USING ERRCODE = '23514';
    END IF;
  END LOOP;

  RETURN v_candidate;
END $$;

CREATE FUNCTION organisation.resolve_payroll_milestone(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_milestone_type varchar,
  p_period_start date,
  p_period_end date
) RETURNS TABLE (
  milestone_rule_id uuid,
  original_date date,
  adjusted_date date,
  adjustment_policy varchar,
  adjustment_days integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_rule_id uuid;
  v_anchor_type varchar;
  v_offset_days integer;
  v_policy varchar;
  v_original date;
  v_adjusted date;
  v_milestone_type varchar := upper(btrim(p_milestone_type));
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_period_start IS NULL
     OR p_period_end IS NULL
     OR p_period_end < p_period_start THEN
    RAISE EXCEPTION 'invalid pay-period range'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    rule.id,
    rule.anchor_type,
    rule.offset_days,
    rule.adjustment_policy
  INTO
    v_rule_id,
    v_anchor_type,
    v_offset_days,
    v_policy
  FROM organisation.payroll_calendar_milestone_rule rule
  WHERE rule.tenant_id = p_tenant_id
    AND rule.calendar_id = p_calendar_id
    AND rule.milestone_type = v_milestone_type;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'milestone rule % is not configured for payroll calendar',
      v_milestone_type
      USING ERRCODE = '23514';
  END IF;

  IF v_anchor_type = 'PERIOD_START' THEN
    v_original := p_period_start + v_offset_days;
  ELSE
    v_original := p_period_end + v_offset_days;
  END IF;

  v_adjusted := organisation.adjust_payroll_calendar_date(
    p_tenant_id,
    p_calendar_id,
    v_original,
    v_policy
  );

  RETURN QUERY
  SELECT
    v_rule_id,
    v_original,
    v_adjusted,
    v_policy,
    v_adjusted - v_original;
END $$;

CREATE FUNCTION organisation.generate_pay_periods(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_start_date date,
  p_period_count integer,
  p_actor varchar,
  p_generated_at timestamptz
) RETURNS TABLE (
  id uuid,
  period_code varchar,
  period_start date,
  period_end date,
  payment_date date,
  status varchar
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_frequency varchar;
  v_timezone varchar;
  v_custom_period_days integer;
  v_custom_authorised boolean;
  v_rule_count integer;
  v_start date := p_start_date;
  v_end date;
  v_code varchar(20);
  v_period_id uuid;
  v_existing_end date;
  v_existing_payment date;
  v_existing_code varchar(20);

  v_input_rule uuid;
  v_input_original date;
  v_input_adjusted date;
  v_input_policy varchar;
  v_input_days integer;

  v_calculation_rule uuid;
  v_calculation_original date;
  v_calculation_adjusted date;
  v_calculation_policy varchar;
  v_calculation_days integer;

  v_approval_rule uuid;
  v_approval_original date;
  v_approval_adjusted date;
  v_approval_policy varchar;
  v_approval_days integer;

  v_release_rule uuid;
  v_release_original date;
  v_release_adjusted date;
  v_release_policy varchar;
  v_release_days integer;

  v_payment_rule uuid;
  v_payment_original date;
  v_payment_adjusted date;
  v_payment_policy varchar;
  v_payment_days integer;

  v_index integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_start_date IS NULL THEN
    RAISE EXCEPTION 'period start date is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_period_count IS NULL
     OR p_period_count < 1
     OR p_period_count > 1000 THEN
    RAISE EXCEPTION 'period count must be between 1 and 1000'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    calendar.frequency,
    calendar.timezone,
    calendar.custom_period_days,
    calendar.custom_frequency_authorised
  INTO
    v_frequency,
    v_timezone,
    v_custom_period_days,
    v_custom_authorised
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = p_calendar_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_frequency NOT IN (
       'MONTHLY',
       'FORTNIGHTLY',
       'WEEKLY',
       'DAILY',
       'CUSTOM'
     ) THEN
    RAISE EXCEPTION 'unsupported payroll frequency: %', v_frequency
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_timezone_names timezone_name
    WHERE timezone_name.name = v_timezone
  ) THEN
    RAISE EXCEPTION 'calendar timezone is not a valid IANA timezone'
      USING ERRCODE = '23514';
  END IF;

  IF v_frequency = 'CUSTOM'
     AND (
       NOT v_custom_authorised
       OR v_custom_period_days IS NULL
       OR v_custom_period_days < 1
       OR v_custom_period_days > 366
     ) THEN
    RAISE EXCEPTION 'custom frequency policy is not authorised'
      USING ERRCODE = '23514';
  END IF;

  IF v_frequency = 'MONTHLY'
     AND p_start_date <> date_trunc('month', p_start_date)::date THEN
    RAISE EXCEPTION
      'monthly generation must start on the first day of a month'
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*)
  INTO v_rule_count
  FROM organisation.payroll_calendar_milestone_rule rule
  WHERE rule.tenant_id = p_tenant_id
    AND rule.calendar_id = p_calendar_id;

  IF v_rule_count <> 5 THEN
    RAISE EXCEPTION
      'period generation requires exactly five milestone rules'
      USING ERRCODE = '23514';
  END IF;

  PERFORM pg_advisory_xact_lock(
    hashtextextended(
      p_tenant_id::text
        || ':'
        || p_calendar_id::text
        || ':'
        || p_start_date::text,
      0
    )
  );

  FOR v_index IN 1..p_period_count LOOP
    IF v_frequency = 'MONTHLY' THEN
      v_end := (
        date_trunc('month', v_start)
        + interval '1 month'
        - interval '1 day'
      )::date;
      v_code := to_char(v_start, 'YYYY-MM');
    ELSIF v_frequency = 'FORTNIGHTLY' THEN
      v_end := v_start + 13;
      v_code := to_char(v_start, 'YYYYMMDD');
    ELSIF v_frequency = 'WEEKLY' THEN
      v_end := v_start + 6;
      v_code := to_char(v_start, 'YYYYMMDD');
    ELSIF v_frequency = 'DAILY' THEN
      v_end := v_start;
      v_code := to_char(v_start, 'YYYYMMDD');
    ELSE
      v_end := v_start + v_custom_period_days - 1;
      v_code := to_char(v_start, 'YYYYMMDD');
    END IF;

    SELECT
      milestone.milestone_rule_id,
      milestone.original_date,
      milestone.adjusted_date,
      milestone.adjustment_policy,
      milestone.adjustment_days
    INTO
      v_input_rule,
      v_input_original,
      v_input_adjusted,
      v_input_policy,
      v_input_days
    FROM organisation.resolve_payroll_milestone(
      p_tenant_id,
      p_calendar_id,
      'INPUT_CUTOFF',
      v_start,
      v_end
    ) milestone;

    SELECT
      milestone.milestone_rule_id,
      milestone.original_date,
      milestone.adjusted_date,
      milestone.adjustment_policy,
      milestone.adjustment_days
    INTO
      v_calculation_rule,
      v_calculation_original,
      v_calculation_adjusted,
      v_calculation_policy,
      v_calculation_days
    FROM organisation.resolve_payroll_milestone(
      p_tenant_id,
      p_calendar_id,
      'CALCULATION',
      v_start,
      v_end
    ) milestone;

    SELECT
      milestone.milestone_rule_id,
      milestone.original_date,
      milestone.adjusted_date,
      milestone.adjustment_policy,
      milestone.adjustment_days
    INTO
      v_approval_rule,
      v_approval_original,
      v_approval_adjusted,
      v_approval_policy,
      v_approval_days
    FROM organisation.resolve_payroll_milestone(
      p_tenant_id,
      p_calendar_id,
      'APPROVAL',
      v_start,
      v_end
    ) milestone;

    SELECT
      milestone.milestone_rule_id,
      milestone.original_date,
      milestone.adjusted_date,
      milestone.adjustment_policy,
      milestone.adjustment_days
    INTO
      v_release_rule,
      v_release_original,
      v_release_adjusted,
      v_release_policy,
      v_release_days
    FROM organisation.resolve_payroll_milestone(
      p_tenant_id,
      p_calendar_id,
      'RELEASE',
      v_start,
      v_end
    ) milestone;

    SELECT
      milestone.milestone_rule_id,
      milestone.original_date,
      milestone.adjusted_date,
      milestone.adjustment_policy,
      milestone.adjustment_days
    INTO
      v_payment_rule,
      v_payment_original,
      v_payment_adjusted,
      v_payment_policy,
      v_payment_days
    FROM organisation.resolve_payroll_milestone(
      p_tenant_id,
      p_calendar_id,
      'PAYMENT',
      v_start,
      v_end
    ) milestone;

    IF v_input_adjusted > v_calculation_adjusted
       OR v_calculation_adjusted > v_approval_adjusted
       OR v_approval_adjusted > v_release_adjusted
       OR v_release_adjusted > v_payment_adjusted THEN
      RAISE EXCEPTION
        'adjusted milestone order must be input <= calculation <= approval <= release <= payment'
        USING ERRCODE = '23514';
    END IF;

    v_period_id := NULL;
    v_existing_end := NULL;
    v_existing_payment := NULL;
    v_existing_code := NULL;

    SELECT
      period.id,
      period.period_end,
      period.payment_date,
      period.period_code
    INTO
      v_period_id,
      v_existing_end,
      v_existing_payment,
      v_existing_code
    FROM organisation.pay_period period
    WHERE period.tenant_id = p_tenant_id
      AND period.calendar_id = p_calendar_id
      AND period.period_start = v_start;

    IF FOUND THEN
      IF v_existing_end <> v_end
         OR v_existing_payment <> v_payment_adjusted
         OR v_existing_code <> v_code THEN
        RAISE EXCEPTION
          'existing pay period does not match the requested deterministic schedule'
          USING ERRCODE = '23514';
      END IF;
    ELSE
      v_period_id := gen_random_uuid();

      INSERT INTO organisation.pay_period(
        id,
        tenant_id,
        calendar_id,
        period_code,
        period_start,
        period_end,
        payment_date,
        status,
        created_at,
        created_by,
        updated_at,
        updated_by
      ) VALUES (
        v_period_id,
        p_tenant_id,
        p_calendar_id,
        v_code,
        v_start,
        v_end,
        v_payment_adjusted,
        'OPEN',
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      );
    END IF;

    INSERT INTO organisation.pay_period_milestone(
      id,
      tenant_id,
      pay_period_id,
      milestone_rule_id,
      milestone_type,
      original_date,
      adjusted_date,
      adjustment_policy,
      adjustment_days,
      created_at,
      created_by,
      updated_at,
      updated_by
    ) VALUES
      (
        gen_random_uuid(),
        p_tenant_id,
        v_period_id,
        v_input_rule,
        'INPUT_CUTOFF',
        v_input_original,
        v_input_adjusted,
        v_input_policy,
        v_input_days,
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      ),
      (
        gen_random_uuid(),
        p_tenant_id,
        v_period_id,
        v_calculation_rule,
        'CALCULATION',
        v_calculation_original,
        v_calculation_adjusted,
        v_calculation_policy,
        v_calculation_days,
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      ),
      (
        gen_random_uuid(),
        p_tenant_id,
        v_period_id,
        v_approval_rule,
        'APPROVAL',
        v_approval_original,
        v_approval_adjusted,
        v_approval_policy,
        v_approval_days,
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      ),
      (
        gen_random_uuid(),
        p_tenant_id,
        v_period_id,
        v_release_rule,
        'RELEASE',
        v_release_original,
        v_release_adjusted,
        v_release_policy,
        v_release_days,
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      ),
      (
        gen_random_uuid(),
        p_tenant_id,
        v_period_id,
        v_payment_rule,
        'PAYMENT',
        v_payment_original,
        v_payment_adjusted,
        v_payment_policy,
        v_payment_days,
        p_generated_at,
        p_actor,
        p_generated_at,
        p_actor
      )
    ON CONFLICT (tenant_id, pay_period_id, milestone_type)
    DO NOTHING;

    IF EXISTS (
      SELECT 1
      FROM (
        VALUES
          (
            'INPUT_CUTOFF'::varchar,
            v_input_rule,
            v_input_original,
            v_input_adjusted,
            v_input_policy,
            v_input_days
          ),
          (
            'CALCULATION'::varchar,
            v_calculation_rule,
            v_calculation_original,
            v_calculation_adjusted,
            v_calculation_policy,
            v_calculation_days
          ),
          (
            'APPROVAL'::varchar,
            v_approval_rule,
            v_approval_original,
            v_approval_adjusted,
            v_approval_policy,
            v_approval_days
          ),
          (
            'RELEASE'::varchar,
            v_release_rule,
            v_release_original,
            v_release_adjusted,
            v_release_policy,
            v_release_days
          ),
          (
            'PAYMENT'::varchar,
            v_payment_rule,
            v_payment_original,
            v_payment_adjusted,
            v_payment_policy,
            v_payment_days
          )
      ) expected(
        milestone_type,
        milestone_rule_id,
        original_date,
        adjusted_date,
        adjustment_policy,
        adjustment_days
      )
      LEFT JOIN organisation.pay_period_milestone actual
        ON actual.tenant_id = p_tenant_id
       AND actual.pay_period_id = v_period_id
       AND actual.milestone_type = expected.milestone_type
      WHERE actual.id IS NULL
         OR actual.milestone_rule_id <> expected.milestone_rule_id
         OR actual.original_date <> expected.original_date
         OR actual.adjusted_date <> expected.adjusted_date
         OR actual.adjustment_policy <> expected.adjustment_policy
         OR actual.adjustment_days <> expected.adjustment_days
    ) THEN
      RAISE EXCEPTION
        'existing milestone evidence does not match the requested deterministic schedule'
        USING ERRCODE = '23514';
    END IF;

    RETURN QUERY
    SELECT
      period.id,
      period.period_code,
      period.period_start,
      period.period_end,
      period.payment_date,
      period.status
    FROM organisation.pay_period period
    WHERE period.tenant_id = p_tenant_id
      AND period.id = v_period_id;

    v_start := v_end + 1;
  END LOOP;
END $$;

-- G02 replaces the G01 monthly-only compatibility result with the complete
-- authorised frequency validation while preserving the existing PSU/range
-- compatibility behavior and function signature.
CREATE OR REPLACE FUNCTION organisation.pay_group_assignment_compatibility_issues(
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
  v_calendar_timezone varchar;
  v_custom_period_days integer;
  v_custom_frequency_authorised boolean;
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

  SELECT
    calendar.frequency,
    calendar.timezone,
    calendar.custom_period_days,
    calendar.custom_frequency_authorised
  INTO
    v_calendar_frequency,
    v_calendar_timezone,
    v_custom_period_days,
    v_custom_frequency_authorised
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = v_group_calendar;

  IF NOT FOUND THEN
    RETURN QUERY SELECT
      'CALENDAR_NOT_FOUND'::varchar,
      'pay-group calendar does not exist in the current tenant'::varchar;
    RETURN;
  END IF;

  IF v_calendar_frequency NOT IN (
       'MONTHLY',
       'FORTNIGHTLY',
       'WEEKLY',
       'DAILY',
       'CUSTOM'
     ) THEN
    RETURN QUERY SELECT
      'CALENDAR_FREQUENCY_UNSUPPORTED'::varchar,
      'pay-group calendar frequency is not authorised'::varchar;
  END IF;

  IF v_calendar_frequency = 'CUSTOM'
     AND (
       NOT v_custom_frequency_authorised
       OR v_custom_period_days IS NULL
       OR v_custom_period_days < 1
       OR v_custom_period_days > 366
     ) THEN
    RETURN QUERY SELECT
      'CUSTOM_FREQUENCY_NOT_AUTHORISED'::varchar,
      'custom calendar requires explicit authorised period-day policy'::varchar;
  END IF;

  IF v_calendar_timezone IS NULL
     OR NOT EXISTS (
       SELECT 1
       FROM pg_timezone_names timezone_name
       WHERE timezone_name.name = v_calendar_timezone
     ) THEN
    RETURN QUERY SELECT
      'CALENDAR_TIMEZONE_INVALID'::varchar,
      'pay-group calendar timezone must be a valid IANA timezone'::varchar;
  END IF;
END $$;

REVOKE ALL
  ON organisation.payroll_calendar_milestone_rule,
     organisation.payroll_calendar_holiday,
     organisation.pay_period_milestone
  FROM PUBLIC;

GRANT SELECT
  ON organisation.payroll_calendar_milestone_rule,
     organisation.payroll_calendar_holiday,
     organisation.pay_period_milestone
  TO payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON organisation.payroll_calendar_milestone_rule,
     organisation.payroll_calendar_holiday,
     organisation.pay_period_milestone
  FROM payroll_app;

REVOKE ALL ON FUNCTION organisation.create_payroll_calendar(
  uuid,
  varchar,
  varchar,
  varchar,
  varchar,
  integer,
  boolean,
  smallint[],
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.configure_payroll_calendar_milestone_rule(
  uuid,
  uuid,
  varchar,
  varchar,
  integer,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.add_payroll_calendar_holiday(
  uuid,
  uuid,
  date,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.is_payroll_calendar_working_day(
  uuid,
  uuid,
  date
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.adjust_payroll_calendar_date(
  uuid,
  uuid,
  date,
  varchar
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.resolve_payroll_milestone(
  uuid,
  uuid,
  varchar,
  date,
  date
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.generate_pay_periods(
  uuid,
  uuid,
  date,
  integer,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.create_payroll_calendar(
  uuid,
  varchar,
  varchar,
  varchar,
  varchar,
  integer,
  boolean,
  smallint[],
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.configure_payroll_calendar_milestone_rule(
  uuid,
  uuid,
  varchar,
  varchar,
  integer,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.add_payroll_calendar_holiday(
  uuid,
  uuid,
  date,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.is_payroll_calendar_working_day(
  uuid,
  uuid,
  date
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.adjust_payroll_calendar_date(
  uuid,
  uuid,
  date,
  varchar
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.resolve_payroll_milestone(
  uuid,
  uuid,
  varchar,
  date,
  date
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.generate_pay_periods(
  uuid,
  uuid,
  date,
  integer,
  varchar,
  timestamptz
) TO payroll_app;

REVOKE CREATE ON SCHEMA organisation FROM payroll_app;

COMMENT ON COLUMN organisation.payroll_calendar.custom_period_days IS
  'Explicit fixed day count for an authorised CUSTOM payroll frequency.';
COMMENT ON COLUMN organisation.payroll_calendar.custom_frequency_authorised IS
  'Must be true before CUSTOM frequency generation is permitted.';
COMMENT ON COLUMN organisation.payroll_calendar.weekend_iso_days IS
  'ISO-8601 day numbers treated as non-working days for milestone adjustment.';
COMMENT ON TABLE organisation.payroll_calendar_milestone_rule IS
  'Five business-distinct G02 milestone rules per payroll calendar; final-settlement timing is intentionally excluded.';
COMMENT ON TABLE organisation.payroll_calendar_holiday IS
  'Tenant/calendar-scoped non-working dates used by milestone adjustment.';
COMMENT ON TABLE organisation.pay_period_milestone IS
  'Immutable-at-publication G02 evidence shape retaining original and adjusted milestone dates and applied movement.';
COMMENT ON FUNCTION organisation.generate_pay_periods(
  uuid,
  uuid,
  date,
  integer,
  varchar,
  timestamptz
) IS
  'Idempotently generates contiguous periods and five milestone evidence rows for MONTHLY, FORTNIGHTLY, WEEKLY, DAILY or explicitly authorised CUSTOM calendars.';


-- P5-A4 G03: append-only publication lifecycle, compatibility blocking and
-- operational read evidence. G01/G02 definitions above remain authoritative.

ALTER TABLE organisation.payroll_calendar
  ADD COLUMN calendar_series_id uuid,
  ADD COLUMN calendar_version integer NOT NULL DEFAULT 1,
  ADD COLUMN supersedes_calendar_id uuid,
  ADD COLUMN publication_required boolean NOT NULL DEFAULT false;

-- Existing calendars can predate P5-A4. Follow the established migration-owner
-- pattern used by prior identity upgrades: keep RLS enabled, temporarily remove
-- FORCE so only the table owner can backfill all tenants, then restore FORCE.
ALTER TABLE organisation.payroll_calendar NO FORCE ROW LEVEL SECURITY;

UPDATE organisation.payroll_calendar
SET calendar_series_id = id
WHERE calendar_series_id IS NULL;

ALTER TABLE organisation.payroll_calendar
  ALTER COLUMN calendar_series_id SET NOT NULL,
  DROP CONSTRAINT payroll_calendar_tenant_id_code_key,
  ADD CONSTRAINT payroll_calendar_version_positive_ck
    CHECK (calendar_version > 0),
  ADD CONSTRAINT payroll_calendar_series_fk
    FOREIGN KEY (tenant_id, calendar_series_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id),
  ADD CONSTRAINT payroll_calendar_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_calendar_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id),
  ADD CONSTRAINT payroll_calendar_supersedes_self_ck
    CHECK (
      supersedes_calendar_id IS NULL
      OR supersedes_calendar_id <> id
    ),
  ADD CONSTRAINT payroll_calendar_code_version_uk
    UNIQUE (tenant_id, code, calendar_version),
  ADD CONSTRAINT payroll_calendar_series_version_uk
    UNIQUE (tenant_id, calendar_series_id, calendar_version);

ALTER TABLE organisation.payroll_calendar FORCE ROW LEVEL SECURITY;

CREATE UNIQUE INDEX payroll_calendar_one_successor_uk
  ON organisation.payroll_calendar(tenant_id, supersedes_calendar_id)
  WHERE supersedes_calendar_id IS NOT NULL;

CREATE INDEX payroll_calendar_series_lookup_ix
  ON organisation.payroll_calendar(
    tenant_id,
    calendar_series_id,
    calendar_version DESC
  );

CREATE FUNCTION organisation.initialise_payroll_calendar_version_identity()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
BEGIN
  IF NEW.calendar_series_id IS NULL THEN
    NEW.calendar_series_id := NEW.id;
  END IF;
  IF NEW.calendar_version IS NULL THEN
    NEW.calendar_version := 1;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_calendar_version_identity
  BEFORE INSERT ON organisation.payroll_calendar
  FOR EACH ROW
  EXECUTE FUNCTION organisation.initialise_payroll_calendar_version_identity();

-- V018/G02 controlled creation functions remain callable for compatibility.
-- Any calendar created through the canonical runtime login after V038 must
-- nevertheless enter the publication-governed lifecycle. Direct owner/migration
-- seed rows retain publication_required=false so pre-P5-A4 compatibility data
-- remains distinguishable and upgrade-safe.
CREATE FUNCTION organisation.require_runtime_calendar_publication()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
BEGIN
  IF session_user = 'payroll_app' THEN
    NEW.publication_required := true;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_calendar_runtime_publication_required
  BEFORE INSERT ON organisation.payroll_calendar
  FOR EACH ROW
  EXECUTE FUNCTION organisation.require_runtime_calendar_publication();

CREATE FUNCTION organisation.create_governed_payroll_calendar(
  p_tenant_id uuid,
  p_code varchar,
  p_name varchar,
  p_frequency varchar,
  p_timezone varchar,
  p_custom_period_days integer,
  p_custom_frequency_authorised boolean,
  p_weekend_iso_days smallint[],
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_calendar_id uuid;
BEGIN
  v_calendar_id := organisation.create_payroll_calendar(
    p_tenant_id,
    p_code,
    p_name,
    p_frequency,
    p_timezone,
    p_custom_period_days,
    p_custom_frequency_authorised,
    p_weekend_iso_days,
    p_actor,
    p_created_at
  );

  UPDATE organisation.payroll_calendar calendar
  SET publication_required = true,
      updated_at = p_created_at,
      updated_by = p_actor
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = v_calendar_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'governed payroll calendar creation did not persist the calendar'
      USING ERRCODE = '23503';
  END IF;

  RETURN v_calendar_id;
END $$;

CREATE TABLE organisation.payroll_calendar_lifecycle_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  calendar_id uuid NOT NULL,
  sequence_no integer NOT NULL,
  event_type varchar(20) NOT NULL,
  supersedes_event_id uuid,
  reason varchar(500),
  occurred_at timestamptz NOT NULL,
  occurred_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, calendar_id, sequence_no),
  CHECK (sequence_no > 0),
  CHECK (event_type IN ('PUBLISHED', 'RETIRED')),
  CHECK (btrim(occurred_by) <> ''),
  CHECK (reason IS NULL OR btrim(reason) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, calendar_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id),
  FOREIGN KEY (tenant_id, supersedes_event_id)
    REFERENCES organisation.payroll_calendar_lifecycle_event(tenant_id, id)
);

CREATE UNIQUE INDEX payroll_calendar_one_retirement_event_uk
  ON organisation.payroll_calendar_lifecycle_event(tenant_id, calendar_id)
  WHERE event_type = 'RETIRED';

CREATE INDEX payroll_calendar_lifecycle_latest_ix
  ON organisation.payroll_calendar_lifecycle_event(
    tenant_id,
    calendar_id,
    sequence_no DESC
  );

ALTER TABLE organisation.payroll_calendar_lifecycle_event
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_calendar_lifecycle_event
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON organisation.payroll_calendar_lifecycle_event
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

CREATE TRIGGER payroll_calendar_lifecycle_event_immutable
  BEFORE UPDATE OR DELETE
  ON organisation.payroll_calendar_lifecycle_event
  FOR EACH ROW
  EXECUTE FUNCTION platform.reject_mutation();

CREATE FUNCTION organisation.payroll_calendar_current_state(
  p_tenant_id uuid,
  p_calendar_id uuid
) RETURNS varchar
LANGUAGE sql
STABLE
SET search_path = pg_catalog, organisation, platform AS $$
  SELECT coalesce(
    (
      SELECT event.event_type
      FROM organisation.payroll_calendar_lifecycle_event event
      WHERE event.tenant_id = p_tenant_id
        AND event.calendar_id = p_calendar_id
      ORDER BY event.sequence_no DESC
      LIMIT 1
    ),
    'DRAFT'::varchar
  )
$$;

CREATE FUNCTION organisation.payroll_calendar_was_published(
  p_tenant_id uuid,
  p_calendar_id uuid
) RETURNS boolean
LANGUAGE sql
STABLE
SET search_path = pg_catalog, organisation AS $$
  SELECT EXISTS (
    SELECT 1
    FROM organisation.payroll_calendar_lifecycle_event event
    WHERE event.tenant_id = p_tenant_id
      AND event.calendar_id = p_calendar_id
      AND event.event_type = 'PUBLISHED'
  )
$$;

CREATE FUNCTION organisation.assert_payroll_calendar_schedule_immutable()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
BEGIN
  IF organisation.payroll_calendar_was_published(OLD.tenant_id, OLD.id) THEN
    IF TG_OP = 'DELETE' THEN
      RAISE EXCEPTION 'published payroll calendar versions are immutable'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.code IS DISTINCT FROM OLD.code
       OR NEW.name IS DISTINCT FROM OLD.name
       OR NEW.frequency IS DISTINCT FROM OLD.frequency
       OR NEW.timezone IS DISTINCT FROM OLD.timezone
       OR NEW.custom_period_days IS DISTINCT FROM OLD.custom_period_days
       OR NEW.custom_frequency_authorised
          IS DISTINCT FROM OLD.custom_frequency_authorised
       OR NEW.weekend_iso_days IS DISTINCT FROM OLD.weekend_iso_days
       OR NEW.publication_required IS DISTINCT FROM OLD.publication_required
       OR NEW.calendar_series_id IS DISTINCT FROM OLD.calendar_series_id
       OR NEW.calendar_version IS DISTINCT FROM OLD.calendar_version
       OR NEW.supersedes_calendar_id IS DISTINCT FROM OLD.supersedes_calendar_id THEN
      RAISE EXCEPTION 'published payroll calendar schedule attributes are immutable'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_calendar_schedule_immutable
  BEFORE UPDATE OR DELETE
  ON organisation.payroll_calendar
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_payroll_calendar_schedule_immutable();

CREATE FUNCTION organisation.assert_payroll_calendar_child_mutable()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
DECLARE
  v_tenant_id uuid;
  v_calendar_id uuid;
BEGIN
  v_tenant_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_calendar_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.calendar_id ELSE NEW.calendar_id END;

  IF organisation.payroll_calendar_was_published(v_tenant_id, v_calendar_id) THEN
    RAISE EXCEPTION 'published payroll calendar configuration is immutable'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_calendar_milestone_rule_publication_immutable
  BEFORE INSERT OR UPDATE OR DELETE
  ON organisation.payroll_calendar_milestone_rule
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_payroll_calendar_child_mutable();

CREATE TRIGGER payroll_calendar_holiday_publication_immutable
  BEFORE INSERT OR UPDATE OR DELETE
  ON organisation.payroll_calendar_holiday
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_payroll_calendar_child_mutable();

CREATE FUNCTION organisation.assert_pay_period_schedule_mutable()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
DECLARE
  v_tenant_id uuid;
  v_calendar_id uuid;
BEGIN
  v_tenant_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_calendar_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.calendar_id ELSE NEW.calendar_id END;

  IF organisation.payroll_calendar_was_published(v_tenant_id, v_calendar_id) THEN
    IF TG_OP IN ('INSERT', 'DELETE') THEN
      RAISE EXCEPTION 'published payroll period schedule is immutable'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.calendar_id IS DISTINCT FROM OLD.calendar_id
       OR NEW.period_code IS DISTINCT FROM OLD.period_code
       OR NEW.period_start IS DISTINCT FROM OLD.period_start
       OR NEW.period_end IS DISTINCT FROM OLD.period_end
       OR NEW.payment_date IS DISTINCT FROM OLD.payment_date THEN
      RAISE EXCEPTION 'published payroll period schedule is immutable'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER pay_period_publication_immutable
  BEFORE INSERT OR UPDATE OR DELETE
  ON organisation.pay_period
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_pay_period_schedule_mutable();

CREATE FUNCTION organisation.assert_pay_period_milestone_mutable()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, organisation AS $$
DECLARE
  v_tenant_id uuid;
  v_period_id uuid;
  v_calendar_id uuid;
BEGIN
  v_tenant_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_period_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.pay_period_id ELSE NEW.pay_period_id END;

  SELECT period.calendar_id
  INTO v_calendar_id
  FROM organisation.pay_period period
  WHERE period.tenant_id = v_tenant_id
    AND period.id = v_period_id;

  IF v_calendar_id IS NULL THEN
    RAISE EXCEPTION 'pay period does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF organisation.payroll_calendar_was_published(v_tenant_id, v_calendar_id) THEN
    RAISE EXCEPTION 'published pay-period milestone evidence is immutable'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER pay_period_milestone_publication_immutable
  BEFORE INSERT OR UPDATE OR DELETE
  ON organisation.pay_period_milestone
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_pay_period_milestone_mutable();

CREATE FUNCTION organisation.publish_payroll_calendar(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_reason varchar,
  p_actor varchar,
  p_occurred_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_event_id uuid := gen_random_uuid();
  v_period_count integer;
  v_bad_period_count integer;
  v_rule_count integer;
  v_supersedes_calendar_id uuid;
  v_source_event_id uuid;
  v_source_event_sequence integer;
  v_source_state varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_occurred_at IS NULL THEN
    RAISE EXCEPTION 'actor and occurrence timestamp are required' USING ERRCODE = '23514';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_tenant_id::text || ':' || p_calendar_id::text || ':publish', 0));

  SELECT calendar.supersedes_calendar_id
  INTO v_supersedes_calendar_id
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.id = p_calendar_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll calendar does not exist in the current tenant' USING ERRCODE = '23503';
  END IF;

  IF EXISTS (
    SELECT 1 FROM organisation.payroll_calendar_lifecycle_event event
    WHERE event.tenant_id = p_tenant_id AND event.calendar_id = p_calendar_id
  ) THEN
    RAISE EXCEPTION 'calendar version has already entered publication lifecycle' USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO v_rule_count
  FROM organisation.payroll_calendar_milestone_rule rule
  WHERE rule.tenant_id = p_tenant_id AND rule.calendar_id = p_calendar_id;
  IF v_rule_count <> 5 THEN
    RAISE EXCEPTION 'publication requires exactly five milestone rules' USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO v_period_count
  FROM organisation.pay_period period
  WHERE period.tenant_id = p_tenant_id AND period.calendar_id = p_calendar_id;
  IF v_period_count < 1 THEN
    RAISE EXCEPTION 'publication requires at least one generated pay period' USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO v_bad_period_count
  FROM organisation.pay_period period
  WHERE period.tenant_id = p_tenant_id
    AND period.calendar_id = p_calendar_id
    AND (
      SELECT count(*)
      FROM organisation.pay_period_milestone milestone
      WHERE milestone.tenant_id = period.tenant_id
        AND milestone.pay_period_id = period.id
    ) <> 5;
  IF v_bad_period_count <> 0 THEN
    RAISE EXCEPTION 'every published period requires exactly five milestone evidence rows' USING ERRCODE = '23514';
  END IF;

  IF v_supersedes_calendar_id IS NOT NULL THEN
    SELECT event.id, event.sequence_no, event.event_type
    INTO v_source_event_id, v_source_event_sequence, v_source_state
    FROM organisation.payroll_calendar_lifecycle_event event
    WHERE event.tenant_id = p_tenant_id
      AND event.calendar_id = v_supersedes_calendar_id
    ORDER BY event.sequence_no DESC
    LIMIT 1;

    IF v_source_state IS DISTINCT FROM 'PUBLISHED' THEN
      RAISE EXCEPTION 'an amendment may publish only while its source version is currently published' USING ERRCODE = '23514';
    END IF;

    INSERT INTO organisation.payroll_calendar_lifecycle_event(
      tenant_id, calendar_id, sequence_no, event_type, supersedes_event_id,
      reason, occurred_at, occurred_by, created_at, created_by
    ) VALUES (
      p_tenant_id, v_supersedes_calendar_id, v_source_event_sequence + 1,
      'RETIRED', v_source_event_id,
      format('Superseded by calendar version %s', p_calendar_id),
      p_occurred_at, p_actor, p_occurred_at, p_actor
    );
  END IF;

  INSERT INTO organisation.payroll_calendar_lifecycle_event(
    id, tenant_id, calendar_id, sequence_no, event_type, supersedes_event_id,
    reason, occurred_at, occurred_by, created_at, created_by
  ) VALUES (
    v_event_id, p_tenant_id, p_calendar_id, 1, 'PUBLISHED', NULL,
    nullif(btrim(p_reason), ''), p_occurred_at, p_actor, p_occurred_at, p_actor
  );

  RETURN v_event_id;
END $$;

CREATE FUNCTION organisation.amend_payroll_calendar(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_new_calendar_id uuid := gen_random_uuid();
  v_source organisation.payroll_calendar%ROWTYPE;
  v_new_version integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_created_at IS NULL THEN
    RAISE EXCEPTION 'actor and creation timestamp are required' USING ERRCODE = '23514';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_tenant_id::text || ':' || p_calendar_id::text || ':amend', 0));

  SELECT * INTO v_source
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id AND calendar.id = p_calendar_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll calendar does not exist in the current tenant' USING ERRCODE = '23503';
  END IF;
  IF organisation.payroll_calendar_current_state(p_tenant_id, p_calendar_id) <> 'PUBLISHED' THEN
    RAISE EXCEPTION 'only a currently published calendar can be amended' USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM organisation.payroll_calendar successor
    WHERE successor.tenant_id = p_tenant_id
      AND successor.supersedes_calendar_id = p_calendar_id
  ) THEN
    RAISE EXCEPTION 'calendar version already has a successor amendment' USING ERRCODE = '23514';
  END IF;

  SELECT max(calendar.calendar_version) + 1 INTO v_new_version
  FROM organisation.payroll_calendar calendar
  WHERE calendar.tenant_id = p_tenant_id
    AND calendar.calendar_series_id = v_source.calendar_series_id;

  INSERT INTO organisation.payroll_calendar(
    id, tenant_id, code, name, frequency, timezone,
    custom_period_days, custom_frequency_authorised, weekend_iso_days,
    publication_required, calendar_series_id, calendar_version,
    supersedes_calendar_id, created_at, created_by, updated_at, updated_by
  ) VALUES (
    v_new_calendar_id, p_tenant_id, v_source.code, v_source.name,
    v_source.frequency, v_source.timezone,
    v_source.custom_period_days, v_source.custom_frequency_authorised,
    v_source.weekend_iso_days, true, v_source.calendar_series_id, v_new_version,
    p_calendar_id, p_created_at, p_actor, p_created_at, p_actor
  );

  INSERT INTO organisation.payroll_calendar_milestone_rule(
    id, tenant_id, calendar_id, milestone_type, anchor_type,
    offset_days, adjustment_policy, created_at, created_by, updated_at, updated_by
  )
  SELECT gen_random_uuid(), p_tenant_id, v_new_calendar_id,
         rule.milestone_type, rule.anchor_type, rule.offset_days,
         rule.adjustment_policy, p_created_at, p_actor, p_created_at, p_actor
  FROM organisation.payroll_calendar_milestone_rule rule
  WHERE rule.tenant_id = p_tenant_id AND rule.calendar_id = p_calendar_id;

  INSERT INTO organisation.payroll_calendar_holiday(
    id, tenant_id, calendar_id, holiday_date, holiday_name,
    created_at, created_by, updated_at, updated_by
  )
  SELECT gen_random_uuid(), p_tenant_id, v_new_calendar_id,
         holiday.holiday_date, holiday.holiday_name,
         p_created_at, p_actor, p_created_at, p_actor
  FROM organisation.payroll_calendar_holiday holiday
  WHERE holiday.tenant_id = p_tenant_id AND holiday.calendar_id = p_calendar_id;

  RETURN v_new_calendar_id;
END $$;

CREATE FUNCTION organisation.retire_payroll_calendar(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_reason varchar,
  p_actor varchar,
  p_occurred_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_event_id uuid := gen_random_uuid();
  v_previous_event_id uuid;
  v_previous_sequence integer;
  v_previous_state varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_occurred_at IS NULL THEN
    RAISE EXCEPTION 'actor and occurrence timestamp are required' USING ERRCODE = '23514';
  END IF;
  IF p_reason IS NULL OR btrim(p_reason) = '' THEN
    RAISE EXCEPTION 'retirement reason is required' USING ERRCODE = '23514';
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended(p_tenant_id::text || ':' || p_calendar_id::text || ':retire', 0));

  SELECT event.id, event.sequence_no, event.event_type
  INTO v_previous_event_id, v_previous_sequence, v_previous_state
  FROM organisation.payroll_calendar_lifecycle_event event
  WHERE event.tenant_id = p_tenant_id AND event.calendar_id = p_calendar_id
  ORDER BY event.sequence_no DESC
  LIMIT 1;

  IF v_previous_state IS DISTINCT FROM 'PUBLISHED' THEN
    RAISE EXCEPTION 'only a currently published calendar can be retired' USING ERRCODE = '23514';
  END IF;

  INSERT INTO organisation.payroll_calendar_lifecycle_event(
    id, tenant_id, calendar_id, sequence_no, event_type, supersedes_event_id,
    reason, occurred_at, occurred_by, created_at, created_by
  ) VALUES (
    v_event_id, p_tenant_id, p_calendar_id, v_previous_sequence + 1,
    'RETIRED', v_previous_event_id, btrim(p_reason),
    p_occurred_at, p_actor, p_occurred_at, p_actor
  );

  RETURN v_event_id;
END $$;

CREATE FUNCTION employee_payroll.assert_p5_a4_pay_group_assignment_compatibility()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, employee_payroll, organisation AS $$
DECLARE
  v_tenant_context text;
  v_issues text;
BEGIN
  IF NEW.approval_status <> 'APPROVED' THEN
    RETURN NEW;
  END IF;

  v_tenant_context := current_setting('app.tenant_id', true);
  IF v_tenant_context IS NULL OR btrim(v_tenant_context) = '' THEN
    RETURN NEW;
  END IF;

  SELECT string_agg(
           issue.issue_code || ': ' || issue.issue_detail,
           '; ' ORDER BY issue.issue_code
         )
  INTO v_issues
  FROM organisation.pay_group_assignment_compatibility_issues(
    NEW.tenant_id,
    NEW.payroll_assignment_version_id,
    NEW.pay_group_version_id,
    NEW.effective_from,
    NEW.effective_to
  ) issue;

  IF v_issues IS NOT NULL THEN
    RAISE EXCEPTION 'P5-A4 pay-group assignment compatibility failed: %', v_issues
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_assignment_p5_a4_compatibility
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    payroll_assignment_version_id,
    pay_group_version_id,
    effective_from,
    effective_to,
    approval_status
  ON employee_payroll.pay_group_assignment
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_p5_a4_pay_group_assignment_compatibility();

CREATE FUNCTION payroll_ops.assert_p5_a4_payroll_cycle_compatibility()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, payroll_ops, organisation AS $$
DECLARE
  v_tenant_context text;
  v_group_status varchar;
  v_group_from date;
  v_group_to date;
  v_group_calendar uuid;
  v_period_calendar uuid;
  v_period_start date;
  v_period_end date;
  v_frequency varchar;
  v_timezone varchar;
  v_custom_days integer;
  v_custom_authorised boolean;
  v_publication_required boolean;
  v_calendar_state varchar;
BEGIN
  v_tenant_context := current_setting('app.tenant_id', true);
  IF v_tenant_context IS NULL OR btrim(v_tenant_context) = '' THEN
    RETURN NEW;
  END IF;

  SELECT
    group_version.approval_status,
    group_version.effective_from,
    group_version.effective_to,
    group_version.calendar_id,
    period.calendar_id,
    period.period_start,
    period.period_end,
    calendar.frequency,
    calendar.timezone,
    calendar.custom_period_days,
    calendar.custom_frequency_authorised,
    calendar.publication_required
  INTO
    v_group_status, v_group_from, v_group_to, v_group_calendar,
    v_period_calendar, v_period_start, v_period_end,
    v_frequency, v_timezone, v_custom_days, v_custom_authorised,
    v_publication_required
  FROM organisation.pay_group_version group_version
  JOIN organisation.pay_period period
    ON period.tenant_id = group_version.tenant_id
   AND period.id = NEW.pay_period_id
  JOIN organisation.payroll_calendar calendar
    ON calendar.tenant_id = group_version.tenant_id
   AND calendar.id = group_version.calendar_id
  WHERE group_version.tenant_id = NEW.tenant_id
    AND group_version.id = NEW.pay_group_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll cycle dependencies do not exist in the current tenant' USING ERRCODE = '23503';
  END IF;
  IF v_group_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'payroll cycle requires an approved pay-group version' USING ERRCODE = '23514';
  END IF;
  IF v_group_calendar <> v_period_calendar THEN
    RAISE EXCEPTION 'payroll cycle period must belong to the pay-group calendar' USING ERRCODE = '23514';
  END IF;
  IF v_period_start < v_group_from
     OR (v_group_to IS NOT NULL AND v_period_end >= v_group_to) THEN
    RAISE EXCEPTION 'payroll cycle period must be contained by pay-group effective range' USING ERRCODE = '23514';
  END IF;
  IF v_frequency NOT IN ('MONTHLY','FORTNIGHTLY','WEEKLY','DAILY','CUSTOM') THEN
    RAISE EXCEPTION 'payroll cycle calendar frequency is not authorised' USING ERRCODE = '23514';
  END IF;
  IF v_frequency = 'CUSTOM'
     AND (NOT v_custom_authorised OR v_custom_days IS NULL OR v_custom_days < 1 OR v_custom_days > 366) THEN
    RAISE EXCEPTION 'payroll cycle custom calendar policy is not authorised' USING ERRCODE = '23514';
  END IF;
  IF v_timezone IS NULL OR NOT EXISTS (
    SELECT 1 FROM pg_timezone_names timezone_name WHERE timezone_name.name = v_timezone
  ) THEN
    RAISE EXCEPTION 'payroll cycle calendar timezone is invalid' USING ERRCODE = '23514';
  END IF;

  IF v_publication_required
     OR EXISTS (
       SELECT 1
       FROM organisation.payroll_calendar_lifecycle_event event
       WHERE event.tenant_id = NEW.tenant_id
         AND event.calendar_id = v_group_calendar
     ) THEN
    v_calendar_state := organisation.payroll_calendar_current_state(
      NEW.tenant_id,
      v_group_calendar
    );
    IF v_calendar_state <> 'PUBLISHED' THEN
      RAISE EXCEPTION 'payroll cycle requires a currently published payroll calendar'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_cycle_p5_a4_compatibility
  BEFORE INSERT OR UPDATE OF tenant_id, pay_group_id, pay_period_id
  ON payroll_ops.payroll_cycle
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.assert_p5_a4_payroll_cycle_compatibility();

-- The V023 command remains the governed runtime entry point for regular-cycle creation.
-- P5-A4 closes the remaining lifecycle gap here rather than relying only on a
-- context-sensitive table trigger: a runtime cycle cannot be created until the
-- exact pay-group calendar version is currently PUBLISHED.
CREATE OR REPLACE FUNCTION payroll_ops.create_regular_payroll_cycle(
  p_tenant_id uuid,
  p_pay_group_version_id uuid,
  p_pay_period_id uuid,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  platform AS $$
DECLARE
  v_cycle_id uuid := gen_random_uuid();
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_created_at IS NULL THEN
    RAISE EXCEPTION 'creation timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.pay_group_version group_version
    JOIN organisation.pay_period period
      ON period.tenant_id = group_version.tenant_id
     AND period.id = p_pay_period_id
     AND period.calendar_id = group_version.calendar_id
    JOIN organisation.payroll_calendar calendar
      ON calendar.tenant_id = group_version.tenant_id
     AND calendar.id = group_version.calendar_id
    WHERE group_version.tenant_id = p_tenant_id
      AND group_version.id = p_pay_group_version_id
      AND group_version.approval_status = 'APPROVED'
      AND group_version.effective_from <= period.period_start
      AND (
        group_version.effective_to IS NULL
        OR group_version.effective_to > period.period_end
      )
      AND period.status = 'OPEN'
      AND (
        (
          NOT calendar.publication_required
          AND NOT EXISTS (
            SELECT 1
            FROM organisation.payroll_calendar_lifecycle_event event
            WHERE event.tenant_id = group_version.tenant_id
              AND event.calendar_id = group_version.calendar_id
          )
        )
        OR organisation.payroll_calendar_current_state(
             group_version.tenant_id,
             group_version.calendar_id
           ) = 'PUBLISHED'
      )
      AND NOT EXISTS (
        SELECT 1
        FROM organisation.pay_group_version successor
        WHERE successor.tenant_id = group_version.tenant_id
          AND successor.supersedes_version_id = group_version.id
      )
  ) THEN
    RAISE EXCEPTION
      'cycle requires an approved current pay-group version, open compatible period and published calendar'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO payroll_ops.payroll_cycle(
    id,
    tenant_id,
    pay_group_id,
    pay_period_id,
    cycle_type,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_cycle_id,
    p_tenant_id,
    p_pay_group_version_id,
    p_pay_period_id,
    'REGULAR',
    'DRAFT',
    p_created_at,
    p_actor,
    p_created_at,
    p_actor
  );

  RETURN v_cycle_id;
END $$;

CREATE VIEW organisation.payroll_calendar_operational_v
WITH (security_invoker = true) AS
SELECT
  calendar.tenant_id,
  calendar.id,
  calendar.calendar_series_id,
  calendar.calendar_version,
  calendar.supersedes_calendar_id,
  calendar.code,
  calendar.name,
  calendar.frequency,
  calendar.timezone,
  calendar.custom_period_days,
  calendar.custom_frequency_authorised,
  calendar.publication_required,
  organisation.payroll_calendar_current_state(calendar.tenant_id, calendar.id) AS lifecycle_status,
  latest_event.id AS latest_lifecycle_event_id,
  latest_event.occurred_at AS lifecycle_changed_at,
  latest_event.occurred_by AS lifecycle_changed_by,
  latest_event.reason AS lifecycle_reason,
  (SELECT count(*)::integer FROM organisation.payroll_calendar_milestone_rule rule
    WHERE rule.tenant_id = calendar.tenant_id AND rule.calendar_id = calendar.id) AS milestone_rule_count,
  (SELECT count(*)::integer FROM organisation.payroll_calendar_holiday holiday
    WHERE holiday.tenant_id = calendar.tenant_id AND holiday.calendar_id = calendar.id) AS holiday_count,
  (SELECT count(*)::integer FROM organisation.pay_period period
    WHERE period.tenant_id = calendar.tenant_id AND period.calendar_id = calendar.id) AS period_count,
  (SELECT min(period.period_start) FROM organisation.pay_period period
    WHERE period.tenant_id = calendar.tenant_id AND period.calendar_id = calendar.id) AS first_period_start,
  (SELECT max(period.period_end) FROM organisation.pay_period period
    WHERE period.tenant_id = calendar.tenant_id AND period.calendar_id = calendar.id) AS last_period_end
FROM organisation.payroll_calendar calendar
LEFT JOIN LATERAL (
  SELECT event.id, event.occurred_at, event.occurred_by, event.reason
  FROM organisation.payroll_calendar_lifecycle_event event
  WHERE event.tenant_id = calendar.tenant_id AND event.calendar_id = calendar.id
  ORDER BY event.sequence_no DESC
  LIMIT 1
) latest_event ON true;

CREATE VIEW organisation.pay_period_operational_v
WITH (security_invoker = true) AS
SELECT
  period.tenant_id,
  period.id,
  period.calendar_id,
  period.period_code,
  period.period_start,
  period.period_end,
  period.payment_date,
  period.status,
  max(milestone.original_date) FILTER (WHERE milestone.milestone_type = 'INPUT_CUTOFF') AS input_cutoff_original_date,
  max(milestone.adjusted_date) FILTER (WHERE milestone.milestone_type = 'INPUT_CUTOFF') AS input_cutoff_adjusted_date,
  max(milestone.original_date) FILTER (WHERE milestone.milestone_type = 'CALCULATION') AS calculation_original_date,
  max(milestone.adjusted_date) FILTER (WHERE milestone.milestone_type = 'CALCULATION') AS calculation_adjusted_date,
  max(milestone.original_date) FILTER (WHERE milestone.milestone_type = 'APPROVAL') AS approval_original_date,
  max(milestone.adjusted_date) FILTER (WHERE milestone.milestone_type = 'APPROVAL') AS approval_adjusted_date,
  max(milestone.original_date) FILTER (WHERE milestone.milestone_type = 'RELEASE') AS release_original_date,
  max(milestone.adjusted_date) FILTER (WHERE milestone.milestone_type = 'RELEASE') AS release_adjusted_date,
  max(milestone.original_date) FILTER (WHERE milestone.milestone_type = 'PAYMENT') AS payment_original_date,
  max(milestone.adjusted_date) FILTER (WHERE milestone.milestone_type = 'PAYMENT') AS payment_adjusted_date
FROM organisation.pay_period period
LEFT JOIN organisation.pay_period_milestone milestone
  ON milestone.tenant_id = period.tenant_id
 AND milestone.pay_period_id = period.id
GROUP BY period.tenant_id, period.id, period.calendar_id, period.period_code,
         period.period_start, period.period_end, period.payment_date, period.status;

REVOKE ALL ON organisation.payroll_calendar_lifecycle_event FROM PUBLIC;
GRANT SELECT ON organisation.payroll_calendar_lifecycle_event TO payroll_app;
REVOKE INSERT, UPDATE, DELETE ON organisation.payroll_calendar_lifecycle_event FROM payroll_app;

REVOKE ALL ON FUNCTION organisation.payroll_calendar_current_state(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.payroll_calendar_was_published(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.create_governed_payroll_calendar(
  uuid, varchar, varchar, varchar, varchar, integer, boolean, smallint[], varchar, timestamptz
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION organisation.create_governed_payroll_calendar(
  uuid, varchar, varchar, varchar, varchar, integer, boolean, smallint[], varchar, timestamptz
) TO payroll_app;

REVOKE ALL ON FUNCTION organisation.publish_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.amend_payroll_calendar(uuid, uuid, varchar, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.retire_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.payroll_calendar_current_state(uuid, uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.payroll_calendar_was_published(uuid, uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.publish_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.amend_payroll_calendar(uuid, uuid, varchar, timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.retire_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) TO payroll_app;

GRANT SELECT ON organisation.payroll_calendar_operational_v, organisation.pay_period_operational_v TO payroll_app;

REVOKE CREATE ON SCHEMA organisation FROM payroll_app;
REVOKE CREATE ON SCHEMA employee_payroll FROM payroll_app;
REVOKE CREATE ON SCHEMA payroll_ops FROM payroll_app;

COMMENT ON COLUMN organisation.payroll_calendar.publication_required IS
  'True for runtime-created P5-A4 governed calendar versions that must be PUBLISHED before payroll-cycle creation; owner/migration legacy compatibility rows may remain false.';
COMMENT ON FUNCTION organisation.require_runtime_calendar_publication() IS
  'Marks calendars inserted through the canonical payroll_app runtime login as publication governed while preserving owner-only legacy migration compatibility.';
COMMENT ON FUNCTION organisation.create_governed_payroll_calendar(
  uuid, varchar, varchar, varchar, varchar, integer, boolean, smallint[], varchar, timestamptz
) IS
  'Creates a P5-A4 lifecycle-governed payroll calendar that must be published before payroll-cycle creation.';

COMMENT ON TABLE organisation.payroll_calendar_lifecycle_event IS
  'Append-only P5-A4 publication/retirement evidence. Retirement never rewrites a published calendar or period.';
COMMENT ON COLUMN organisation.payroll_calendar.calendar_series_id IS
  'Stable calendar-series identity shared by amendment versions.';
COMMENT ON COLUMN organisation.payroll_calendar.calendar_version IS
  'Monotonic business version within a calendar series.';
COMMENT ON FUNCTION organisation.publish_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) IS
  'Publishes a complete draft schedule; publishing an amendment appends retirement evidence for its superseded source.';
COMMENT ON FUNCTION organisation.amend_payroll_calendar(uuid, uuid, varchar, timestamptz) IS
  'Creates a new draft successor version and copies policy rules/holidays without copying or rewriting published periods.';
COMMENT ON FUNCTION organisation.retire_payroll_calendar(uuid, uuid, varchar, varchar, timestamptz) IS
  'Appends retirement evidence for a currently published calendar without mutating its schedule history.';
COMMENT ON VIEW organisation.payroll_calendar_operational_v IS
  'Tenant-safe P5-A4 calendar lifecycle, frequency and generation-readiness summary.';
COMMENT ON VIEW organisation.pay_period_operational_v IS
  'Tenant-safe P5-A4 period read model exposing original and adjusted five-milestone evidence.';
