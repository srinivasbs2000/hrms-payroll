-- S2-02 payroll-calendar and monthly-period foundation.
--
-- Existing Sprint 0 calendar and period identifiers remain unchanged. This
-- migration adds controlled creation/generation commands and makes the
-- application role read-only on the underlying configuration tables.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM organisation.payroll_calendar
    WHERE frequency <> 'MONTHLY'
  ) THEN
    RAISE EXCEPTION
      'V018 supports monthly payroll calendars only';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.pay_period
    WHERE period_start <> date_trunc('month', period_start)::date
       OR period_end <> (
         date_trunc('month', period_start)
         + interval '1 month'
         - interval '1 day'
       )::date
       OR payment_date < period_start
       OR payment_date > period_end
  ) THEN
    RAISE EXCEPTION
      'existing pay periods do not satisfy monthly period invariants';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'organisation.pay_period'::regclass
      AND conname = 'pay_period_month_boundaries_ck'
  ) THEN
    ALTER TABLE organisation.pay_period
      ADD CONSTRAINT pay_period_month_boundaries_ck
      CHECK (
        period_start = date_trunc('month', period_start)::date
        AND period_end = (
          date_trunc('month', period_start)
          + interval '1 month'
          - interval '1 day'
        )::date
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'organisation.pay_period'::regclass
      AND conname = 'pay_period_payment_within_period_ck'
  ) THEN
    ALTER TABLE organisation.pay_period
      ADD CONSTRAINT pay_period_payment_within_period_ck
      CHECK (
        payment_date >= period_start
        AND payment_date <= period_end
      );
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS pay_period_calendar_month_uk
  ON organisation.pay_period(
    tenant_id,
    calendar_id,
    period_start
  );

CREATE INDEX IF NOT EXISTS pay_period_calendar_range_ix
  ON organisation.pay_period(
    tenant_id,
    calendar_id,
    period_start,
    period_end
  );

CREATE OR REPLACE FUNCTION organisation.create_monthly_payroll_calendar(
  p_tenant_id uuid,
  p_code varchar,
  p_name varchar,
  p_timezone varchar,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  v_calendar_id uuid := gen_random_uuid();
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

  IF p_timezone IS NULL
     OR NOT EXISTS (
       SELECT 1
       FROM pg_timezone_names
       WHERE name = p_timezone
     ) THEN
    RAISE EXCEPTION 'unknown IANA timezone: %', p_timezone
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
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_calendar_id,
    p_tenant_id,
    p_code,
    btrim(p_name),
    'MONTHLY',
    p_timezone,
    p_created_at,
    p_actor,
    p_created_at,
    p_actor
  );

  RETURN v_calendar_id;
END $$;

CREATE OR REPLACE FUNCTION organisation.generate_monthly_pay_periods(
  p_tenant_id uuid,
  p_calendar_id uuid,
  p_year integer,
  p_payment_day integer,
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
  v_existing_count integer;
  v_matching_count integer := 0;
  v_month integer;
  v_start date;
  v_end date;
  v_payment date;
  v_days integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_year < 2020 OR p_year > 2100 THEN
    RAISE EXCEPTION 'year must be between 2020 and 2100'
      USING ERRCODE = '23514';
  END IF;

  IF p_payment_day < 1 OR p_payment_day > 31 THEN
    RAISE EXCEPTION 'payment day must be between 1 and 31'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT c.frequency
  INTO v_frequency
  FROM organisation.payroll_calendar c
  WHERE c.tenant_id = p_tenant_id
    AND c.id = p_calendar_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll calendar does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_frequency <> 'MONTHLY' THEN
    RAISE EXCEPTION
      'period generation requires a monthly payroll calendar'
      USING ERRCODE = '23514';
  END IF;

  PERFORM pg_advisory_xact_lock(
    hashtextextended(
      p_tenant_id::text
        || ':'
        || p_calendar_id::text
        || ':'
        || p_year::text,
      0
    )
  );

  SELECT count(*)
  INTO v_existing_count
  FROM organisation.pay_period p
  WHERE p.tenant_id = p_tenant_id
    AND p.calendar_id = p_calendar_id
    AND p.period_start >= make_date(p_year, 1, 1)
    AND p.period_start < make_date(p_year + 1, 1, 1);

  IF v_existing_count > 0 THEN
    FOR v_month IN 1..12 LOOP
      v_start := make_date(p_year, v_month, 1);
      v_end := (
        v_start
        + interval '1 month'
        - interval '1 day'
      )::date;
      v_days := extract(day FROM v_end)::integer;
      v_payment := make_date(
        p_year,
        v_month,
        least(p_payment_day, v_days)
      );

      IF EXISTS (
        SELECT 1
        FROM organisation.pay_period p
        WHERE p.tenant_id = p_tenant_id
          AND p.calendar_id = p_calendar_id
          AND p.period_code = format(
            '%s-%s',
            p_year,
            lpad(v_month::text, 2, '0')
          )
          AND p.period_start = v_start
          AND p.period_end = v_end
          AND p.payment_date = v_payment
      ) THEN
        v_matching_count := v_matching_count + 1;
      END IF;
    END LOOP;

    IF v_existing_count <> 12 OR v_matching_count <> 12 THEN
      RAISE EXCEPTION
        'periods already exist for % but do not match the requested schedule',
        p_year
        USING ERRCODE = '23514';
    END IF;

    RETURN QUERY
    SELECT
      p.id,
      p.period_code,
      p.period_start,
      p.period_end,
      p.payment_date,
      p.status
    FROM organisation.pay_period p
    WHERE p.tenant_id = p_tenant_id
      AND p.calendar_id = p_calendar_id
      AND p.period_start >= make_date(p_year, 1, 1)
      AND p.period_start < make_date(p_year + 1, 1, 1)
    ORDER BY p.period_start;

    RETURN;
  END IF;

  FOR v_month IN 1..12 LOOP
    v_start := make_date(p_year, v_month, 1);
    v_end := (
      v_start
      + interval '1 month'
      - interval '1 day'
    )::date;
    v_days := extract(day FROM v_end)::integer;
    v_payment := make_date(
      p_year,
      v_month,
      least(p_payment_day, v_days)
    );

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
      gen_random_uuid(),
      p_tenant_id,
      p_calendar_id,
      format(
        '%s-%s',
        p_year,
        lpad(v_month::text, 2, '0')
      ),
      v_start,
      v_end,
      v_payment,
      'OPEN',
      p_generated_at,
      p_actor,
      p_generated_at,
      p_actor
    );
  END LOOP;

  RETURN QUERY
  SELECT
    p.id,
    p.period_code,
    p.period_start,
    p.period_end,
    p.payment_date,
    p.status
  FROM organisation.pay_period p
  WHERE p.tenant_id = p_tenant_id
    AND p.calendar_id = p_calendar_id
    AND p.period_start >= make_date(p_year, 1, 1)
    AND p.period_start < make_date(p_year + 1, 1, 1)
  ORDER BY p.period_start;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle c
    JOIN organisation.pay_group_version g
      ON g.tenant_id = c.tenant_id
     AND g.id = c.pay_group_id
    JOIN organisation.pay_period p
      ON p.tenant_id = c.tenant_id
     AND p.id = c.pay_period_id
    WHERE g.calendar_id <> p.calendar_id
  ) THEN
    RAISE EXCEPTION
      'existing payroll-cycle calendar lineage is inconsistent';
  END IF;
END $$;

CREATE OR REPLACE FUNCTION payroll_ops.assert_payroll_cycle_calendar_match()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, payroll_ops, organisation, platform AS $$
DECLARE
  v_group_calendar_id uuid;
  v_period_calendar_id uuid;
BEGIN
  SELECT g.calendar_id
  INTO v_group_calendar_id
  FROM organisation.pay_group_version g
  WHERE g.tenant_id = NEW.tenant_id
    AND g.id = NEW.pay_group_id;

  SELECT p.calendar_id
  INTO v_period_calendar_id
  FROM organisation.pay_period p
  WHERE p.tenant_id = NEW.tenant_id
    AND p.id = NEW.pay_period_id;

  IF v_group_calendar_id IS NULL
     OR v_period_calendar_id IS NULL THEN
    RAISE EXCEPTION
      'payroll cycle dependencies do not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_group_calendar_id <> v_period_calendar_id THEN
    RAISE EXCEPTION
      'pay period must belong to the pay-group calendar'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS payroll_cycle_calendar_match
  ON payroll_ops.payroll_cycle;

CREATE TRIGGER payroll_cycle_calendar_match
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    pay_group_id,
    pay_period_id
  ON payroll_ops.payroll_cycle
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.assert_payroll_cycle_calendar_match();

REVOKE ALL ON FUNCTION organisation.create_monthly_payroll_calendar(
  uuid,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.generate_monthly_pay_periods(
  uuid,
  uuid,
  integer,
  integer,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION organisation.create_monthly_payroll_calendar(
  uuid,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.generate_monthly_pay_periods(
  uuid,
  uuid,
  integer,
  integer,
  varchar,
  timestamptz
) TO payroll_app;

GRANT SELECT
  ON organisation.payroll_calendar,
     organisation.pay_period
  TO payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON organisation.payroll_calendar,
     organisation.pay_period
  FROM payroll_app;

REVOKE CREATE ON SCHEMA organisation FROM payroll_app;
REVOKE CREATE ON SCHEMA payroll_ops FROM payroll_app;

COMMENT ON FUNCTION organisation.create_monthly_payroll_calendar(
  uuid,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Creates one tenant-scoped monthly payroll calendar through a controlled command.';

COMMENT ON FUNCTION organisation.generate_monthly_pay_periods(
  uuid,
  uuid,
  integer,
  integer,
  varchar,
  timestamptz
) IS
  'Idempotently creates exactly twelve contiguous monthly periods for one year.';

COMMENT ON FUNCTION payroll_ops.assert_payroll_cycle_calendar_match() IS
  'Prevents payroll cycles from combining a pay group and period from different calendars.';
