-- Development-only Sprint 4 deployed-path fixture.
-- This file is repository-owned, idempotent and deliberately excluded from Flyway.
-- It supplies the V030 statutory balance-year prerequisite for the deterministic
-- tenant 00000000-0000-0000-0000-000000000001 July 2026 smoke cycle.

\set ON_ERROR_STOP on

BEGIN;
SET LOCAL app.tenant_id = '00000000-0000-0000-0000-000000000001';

DO $fixture$
DECLARE
  v_tenant_id constant uuid :=
    '00000000-0000-0000-0000-000000000001';
  v_balance_year_id constant uuid :=
    'd1000000-0000-0000-0000-000000000001';
  v_actor constant varchar(160) := 'service:local-payroll-smoke';
  v_cycle_id uuid;
  v_payment_date date;
  v_coverage_count integer;
  v_row record;
BEGIN
  IF current_user <> 'payroll_app' THEN
    RAISE EXCEPTION
      'Sprint 4 local fixture must run as payroll_app, not %',
      current_user
      USING ERRCODE = '42501';
  END IF;

  SELECT cycle.id, period.payment_date
  INTO STRICT v_cycle_id, v_payment_date
  FROM payroll_ops.payroll_cycle cycle
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
  WHERE cycle.tenant_id = v_tenant_id
    AND cycle.status = 'CALCULATED'
    AND period.payment_date >= DATE '2026-07-01'
    AND period.payment_date < DATE '2026-08-01';

  IF NOT EXISTS (
    SELECT 1
    FROM statutory.statutory_evaluation_request evaluation
    WHERE evaluation.tenant_id = v_tenant_id
      AND evaluation.payroll_cycle_id = v_cycle_id
      AND evaluation.status = 'COMPLETED'
  ) THEN
    RAISE EXCEPTION
      'completed statutory evaluation is required before balance-year preparation'
      USING ERRCODE = '55000';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_result result
    JOIN statutory.statutory_input_snapshot snapshot
      ON snapshot.tenant_id = result.tenant_id
     AND snapshot.id = result.statutory_input_snapshot_id
    JOIN statutory.statutory_rule rule
      ON rule.tenant_id = snapshot.tenant_id
     AND rule.id = snapshot.statutory_rule_id
    JOIN statutory.statutory_evaluation_request evaluation
      ON evaluation.tenant_id = result.tenant_id
     AND evaluation.id = result.evaluation_request_id
    WHERE result.tenant_id = v_tenant_id
      AND evaluation.payroll_cycle_id = v_cycle_id
      AND evaluation.status = 'COMPLETED'
      AND (
        rule.jurisdiction_code <> 'IN'
        OR rule.authority_code <> 'CENTRAL'
      )
  ) THEN
    RAISE EXCEPTION
      'deterministic Sprint 4 fixture expects only IN/CENTRAL statutory results'
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*)
  INTO v_coverage_count
  FROM statutory.statutory_balance_year balance_year
  WHERE balance_year.tenant_id = v_tenant_id
    AND balance_year.jurisdiction_code = 'IN'
    AND balance_year.authority_code = 'CENTRAL'
    AND balance_year.approval_status = 'APPROVED'
    AND balance_year.period_start <= v_payment_date
    AND balance_year.period_end > v_payment_date
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_balance_year successor
      WHERE successor.tenant_id = balance_year.tenant_id
        AND successor.supersedes_balance_year_id = balance_year.id
    );

  IF v_coverage_count = 0 THEN
    SELECT
      balance_year.jurisdiction_code,
      balance_year.authority_code,
      balance_year.balance_year_code,
      balance_year.version_sequence,
      balance_year.period_start,
      balance_year.period_end,
      balance_year.approval_status
    INTO v_row
    FROM statutory.statutory_balance_year balance_year
    WHERE balance_year.tenant_id = v_tenant_id
      AND balance_year.id = v_balance_year_id;

    IF FOUND THEN
      IF v_row.jurisdiction_code <> 'IN'
         OR v_row.authority_code <> 'CENTRAL'
         OR v_row.balance_year_code <> 'IN_CENTRAL_2026'
         OR v_row.version_sequence <> 1
         OR v_row.period_start <> DATE '2026-01-01'
         OR v_row.period_end <> DATE '2027-01-01'
         OR v_row.approval_status NOT IN ('DRAFT', 'APPROVED') THEN
        RAISE EXCEPTION
          'existing deterministic balance-year identity has unexpected content'
          USING ERRCODE = '23514';
      END IF;
    ELSE
      INSERT INTO statutory.statutory_balance_year(
        id,
        tenant_id,
        jurisdiction_code,
        authority_code,
        balance_year_code,
        version_sequence,
        period_start,
        period_end,
        approval_status,
        created_by,
        updated_by
      ) VALUES (
        v_balance_year_id,
        v_tenant_id,
        'IN',
        'CENTRAL',
        'IN_CENTRAL_2026',
        1,
        DATE '2026-01-01',
        DATE '2027-01-01',
        'DRAFT',
        v_actor,
        v_actor
      );
    END IF;

    IF (
      SELECT balance_year.approval_status
      FROM statutory.statutory_balance_year balance_year
      WHERE balance_year.tenant_id = v_tenant_id
        AND balance_year.id = v_balance_year_id
    ) = 'DRAFT' THEN
      PERFORM statutory.approve_statutory_balance_year(
        v_tenant_id,
        v_balance_year_id,
        v_actor,
        clock_timestamp()
      );
    END IF;
  ELSIF v_coverage_count <> 1 THEN
    RAISE EXCEPTION
      'expected zero or one current approved IN/CENTRAL balance year; found %',
      v_coverage_count
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*)
  INTO v_coverage_count
  FROM statutory.statutory_balance_year balance_year
  WHERE balance_year.tenant_id = v_tenant_id
    AND balance_year.jurisdiction_code = 'IN'
    AND balance_year.authority_code = 'CENTRAL'
    AND balance_year.approval_status = 'APPROVED'
    AND balance_year.period_start <= v_payment_date
    AND balance_year.period_end > v_payment_date
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_balance_year successor
      WHERE successor.tenant_id = balance_year.tenant_id
        AND successor.supersedes_balance_year_id = balance_year.id
    );

  IF v_coverage_count <> 1 THEN
    RAISE EXCEPTION
      'exactly one approved current IN/CENTRAL balance year must cover %; found %',
      v_payment_date,
      v_coverage_count
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_result result
    JOIN statutory.statutory_input_snapshot snapshot
      ON snapshot.tenant_id = result.tenant_id
     AND snapshot.id = result.statutory_input_snapshot_id
    JOIN statutory.statutory_rule rule
      ON rule.tenant_id = snapshot.tenant_id
     AND rule.id = snapshot.statutory_rule_id
    JOIN statutory.statutory_evaluation_request evaluation
      ON evaluation.tenant_id = result.tenant_id
     AND evaluation.id = result.evaluation_request_id
    WHERE result.tenant_id = v_tenant_id
      AND evaluation.payroll_cycle_id = v_cycle_id
      AND evaluation.status = 'COMPLETED'
      AND (
        SELECT count(*)
        FROM statutory.statutory_balance_year balance_year
        WHERE balance_year.tenant_id = result.tenant_id
          AND balance_year.jurisdiction_code = rule.jurisdiction_code
          AND balance_year.authority_code = rule.authority_code
          AND balance_year.approval_status = 'APPROVED'
          AND balance_year.period_start <= v_payment_date
          AND balance_year.period_end > v_payment_date
          AND NOT EXISTS (
            SELECT 1
            FROM statutory.statutory_balance_year successor
            WHERE successor.tenant_id = balance_year.tenant_id
              AND successor.supersedes_balance_year_id = balance_year.id
          )
      ) <> 1
  ) THEN
    RAISE EXCEPTION
      'one or more statutory results do not resolve to exactly one balance year'
      USING ERRCODE = '23514';
  END IF;
END
$fixture$;

COMMIT;
