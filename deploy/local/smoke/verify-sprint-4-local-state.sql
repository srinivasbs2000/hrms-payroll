-- Read-only parity verification for the repository-owned Sprint 4 local fixture.

\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

BEGIN TRANSACTION READ ONLY;
SET LOCAL app.tenant_id = '00000000-0000-0000-0000-000000000001';

DO $verify$
DECLARE
  v_tenant_id constant uuid :=
    '00000000-0000-0000-0000-000000000001';
  v_cycle_id uuid;
  v_payment_date date;
  v_balance_years integer;
  v_violations integer;
BEGIN
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

  SELECT count(*)
  INTO v_balance_years
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

  SELECT count(*)
  INTO v_violations
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
    ) <> 1;

  IF v_balance_years <> 1 OR v_violations <> 0 THEN
    RAISE EXCEPTION
      'balance-year parity failure: current %, result violations %',
      v_balance_years,
      v_violations
      USING ERRCODE = '23514';
  END IF;
END
$verify$;

SELECT 'postgres_version=' || current_setting('server_version');
SELECT 'approved_current_balance_years=' || (
  SELECT count(*)
  FROM statutory.statutory_balance_year balance_year
  WHERE balance_year.tenant_id =
        '00000000-0000-0000-0000-000000000001'
    AND balance_year.jurisdiction_code = 'IN'
    AND balance_year.authority_code = 'CENTRAL'
    AND balance_year.balance_year_code = 'IN_CENTRAL_2026'
    AND balance_year.approval_status = 'APPROVED'
    AND balance_year.period_start = DATE '2026-01-01'
    AND balance_year.period_end = DATE '2027-01-01'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_balance_year successor
      WHERE successor.tenant_id = balance_year.tenant_id
        AND successor.supersedes_balance_year_id = balance_year.id
    )
);
SELECT 'statutory_result_balance_year_violations=' || (
  WITH target_cycle AS (
    SELECT cycle.id, period.payment_date
    FROM payroll_ops.payroll_cycle cycle
    JOIN organisation.pay_period period
      ON period.tenant_id = cycle.tenant_id
     AND period.id = cycle.pay_period_id
    WHERE cycle.tenant_id =
          '00000000-0000-0000-0000-000000000001'
      AND cycle.status = 'CALCULATED'
      AND period.payment_date >= DATE '2026-07-01'
      AND period.payment_date < DATE '2026-08-01'
  )
  SELECT count(*)
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
  JOIN target_cycle cycle
    ON cycle.id = evaluation.payroll_cycle_id
  WHERE result.tenant_id =
        '00000000-0000-0000-0000-000000000001'
    AND evaluation.status = 'COMPLETED'
    AND (
      SELECT count(*)
      FROM statutory.statutory_balance_year balance_year
      WHERE balance_year.tenant_id = result.tenant_id
        AND balance_year.jurisdiction_code = rule.jurisdiction_code
        AND balance_year.authority_code = rule.authority_code
        AND balance_year.approval_status = 'APPROVED'
        AND balance_year.period_start <= cycle.payment_date
        AND balance_year.period_end > cycle.payment_date
        AND NOT EXISTS (
          SELECT 1
          FROM statutory.statutory_balance_year successor
          WHERE successor.tenant_id = balance_year.tenant_id
            AND successor.supersedes_balance_year_id = balance_year.id
        )
    ) <> 1
);
SELECT 'calculated_cycles=' || (
  SELECT count(*)
  FROM payroll_ops.payroll_cycle
  WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
    AND status = 'CALCULATED'
);
SELECT 'completed_calculation_requests=' || (
  SELECT count(*)
  FROM payroll_calc.calculation_request
  WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
    AND status = 'COMPLETED'
);
SELECT 'completed_statutory_evaluations=' || (
  SELECT count(*)
  FROM statutory.statutory_evaluation_request
  WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
    AND status = 'COMPLETED'
);

ROLLBACK;
