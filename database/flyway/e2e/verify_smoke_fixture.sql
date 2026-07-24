\set ON_ERROR_STOP on
\set VERBOSITY verbose
\echo 'E2E verification stage 1: persistent fixture shape'

DO $fixture$
DECLARE
  fixture_tenant_id constant uuid :=
    '00000000-0000-0000-0000-000000000001';
  actual bigint;
BEGIN
  SELECT count(*)
    INTO actual
    FROM platform.tenant
   WHERE id = fixture_tenant_id
     AND code = 'E2E001'
     AND status = 'ACTIVE';

  IF actual <> 1 THEN
    RAISE EXCEPTION 'E2E tenant E2E001 is missing or invalid';
  END IF;

  SELECT count(*)
    INTO actual
    FROM organisation.legal_entity_version
   WHERE tenant_id = fixture_tenant_id
     AND approval_status = 'APPROVED';

  IF actual <> 1 THEN
    RAISE EXCEPTION
      'Expected exactly one approved E2E legal-entity version, found %',
      actual;
  END IF;

  SELECT count(*)
    INTO actual
    FROM organisation.pay_period
   WHERE tenant_id = fixture_tenant_id
     AND period_code IN ('E2E-2026-07', 'E2E-2026-08')
     AND status = 'OPEN';

  IF actual <> 2 THEN
    RAISE EXCEPTION
      'Expected two open E2E pay periods, found %',
      actual;
  END IF;

  SELECT count(*)
    INTO actual
    FROM employee_payroll.employee_payroll_profile
   WHERE tenant_id = fixture_tenant_id
     AND payroll_status = 'READY';

  IF actual <> 1 THEN
    RAISE EXCEPTION
      'Expected one READY E2E payroll profile, found %',
      actual;
  END IF;

  SELECT count(*)
    INTO actual
    FROM employee_payroll.employee_payroll_profile
   WHERE tenant_id = fixture_tenant_id
     AND payroll_status = 'ON_HOLD';

  IF actual <> 1 THEN
    RAISE EXCEPTION
      'Expected one ON_HOLD E2E payroll profile, found %',
      actual;
  END IF;
END
$fixture$;

\echo 'E2E verification stage 2: rollback-only payroll lifecycle'
-- Prove the complete database lifecycle while leaving no runtime evidence.
BEGIN;
SET LOCAL ROLE payroll_owner;
SELECT set_config(
  'app.tenant_id',
  '00000000-0000-0000-0000-000000000001',
  true
);

DO $lifecycle$
DECLARE
  v_fixture_tenant_id constant uuid :=
    '00000000-0000-0000-0000-000000000001';
  v_cycle_id uuid;
  population record;
  seal record;
  calculation record;
  recalculation record;
  actual bigint;
BEGIN
  v_cycle_id := payroll_ops.create_regular_payroll_cycle(
    v_fixture_tenant_id,
    '45100000-0000-0000-0000-000000000001',
    '44100000-0000-0000-0000-000000000001',
    'e2e-fixture-verifier',
    clock_timestamp()
  );

  SELECT *
    INTO population
    FROM payroll_ops.resolve_payroll_population(
      v_fixture_tenant_id,
      v_cycle_id,
      0,
      'e2e-fixture-verifier',
      clock_timestamp()
    );

  IF population.included_count <> 1
     OR population.excluded_count <> 1
     OR population.cycle_version_no <> 1 THEN
    RAISE EXCEPTION
      'Unexpected population result: included %, excluded %, version %',
      population.included_count,
      population.excluded_count,
      population.cycle_version_no;
  END IF;

  SELECT count(*)
    INTO actual
    FROM payroll_ops.population_decision decision
   WHERE decision.tenant_id = v_fixture_tenant_id
     AND decision.payroll_cycle_id = v_cycle_id
     AND decision.decision = 'EXCLUDED'
     AND decision.reason_code = 'PROFILE_NOT_READY';

  IF actual <> 1 THEN
    RAISE EXCEPTION
      'Expected one PROFILE_NOT_READY exclusion, found %',
      actual;
  END IF;

  SELECT *
    INTO seal
    FROM payroll_ops.seal_payroll_inputs(
      v_fixture_tenant_id,
      v_cycle_id,
      1,
      'e2e-fixture-verifier',
      clock_timestamp()
    );

  IF seal.snapshot_count <> 1
     OR seal.cycle_version_no <> 2
     OR seal.combined_hash IS NULL THEN
    RAISE EXCEPTION
      'Unexpected seal result: snapshots %, version %, hash %',
      seal.snapshot_count,
      seal.cycle_version_no,
      seal.combined_hash;
  END IF;

  SELECT *
    INTO calculation
    FROM payroll_calc.calculate_sealed_payroll(
      v_fixture_tenant_id,
      v_cycle_id,
      2,
      'e2e-fixture-initial',
      repeat('a', 64),
      'e2e-fixture-verifier',
      clock_timestamp()
    );

  IF calculation.result_count <> 1
     OR calculation.gross_total <> 90000.0000
     OR calculation.deduction_total <> 0.0000
     OR calculation.net_total <> 90000.0000
     OR calculation.cycle_version_no <> 3 THEN
    RAISE EXCEPTION
      'Unexpected calculation result: count %, gross %, deduction %, net %, version %',
      calculation.result_count,
      calculation.gross_total,
      calculation.deduction_total,
      calculation.net_total,
      calculation.cycle_version_no;
  END IF;

  SELECT *
    INTO recalculation
    FROM payroll_calc.recalculate_sealed_payroll(
      v_fixture_tenant_id,
      v_cycle_id,
      3,
      'e2e-fixture-recalculation',
      repeat('b', 64),
      'Deterministic E2E fixture verification',
      'e2e-fixture-verifier',
      clock_timestamp()
    );

  IF recalculation.attempt_no <> 2
     OR recalculation.result_count <> 1
     OR recalculation.gross_total <> 90000.0000
     OR recalculation.deduction_total <> 0.0000
     OR recalculation.net_total <> 90000.0000
     OR recalculation.cycle_version_no <> 4 THEN
    RAISE EXCEPTION
      'Unexpected recalculation result: attempt %, count %, gross %, deduction %, net %, version %',
      recalculation.attempt_no,
      recalculation.result_count,
      recalculation.gross_total,
      recalculation.deduction_total,
      recalculation.net_total,
      recalculation.cycle_version_no;
  END IF;
END
$lifecycle$;

ROLLBACK;
\echo 'E2E verification stage 3: post-rollback fixture evidence'

SELECT
  'fixture_tenant' AS check_name,
  id::text AS check_value
FROM platform.tenant
WHERE code = 'E2E001';

SELECT
  'flyway_version' AS check_name,
  version AS check_value
FROM public.flyway_schema_history
WHERE success
ORDER BY installed_rank DESC
LIMIT 1;

SELECT
  profile.payroll_status,
  count(*) AS profile_count
FROM employee_payroll.employee_payroll_profile profile
WHERE profile.tenant_id =
  '00000000-0000-0000-0000-000000000001'
GROUP BY profile.payroll_status
ORDER BY profile.payroll_status;
