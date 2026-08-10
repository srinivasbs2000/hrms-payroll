\set ON_ERROR_STOP on

-- Synthetic Sprint 3 executable payroll fixture.
-- This file is not a Flyway migration and is applied only to the isolated
-- hrms-payroll-e2e PostgreSQL volume.

BEGIN;

INSERT INTO platform.tenant(
  id,
  code,
  name,
  status,
  created_by,
  updated_by
) VALUES (
  '00000000-0000-0000-0000-000000000001',
  'E2E001',
  'Synthetic Payroll E2E Tenant',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

SET LOCAL ROLE payroll_owner;

SELECT set_config(
  'app.tenant_id',
  '00000000-0000-0000-0000-000000000001',
  true
);

-- The overlay seeds an already-approved version, so mirror V031 approval
-- semantics by making the stable identity ACTIVE explicitly.
INSERT INTO organisation.legal_entity(
  id,tenant_id,code,status,created_by,updated_by
) VALUES (
  '41000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_LE_IN',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.legal_entity_version(
  id,tenant_id,legal_entity_id,version_sequence,
  name,country_code,currency,effective_from,effective_to,
  approval_status,approved_at,approved_by,created_by,updated_by
) VALUES (
  '41100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '41000000-0000-0000-0000-000000000001',
  1,
  'Synthetic India Legal Entity',
  'IN',
  'INR',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.payroll_statutory_unit(
  id,tenant_id,code,status,created_by,updated_by
) VALUES (
  '42000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_PSU_IN',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.payroll_statutory_unit_version(
  id,tenant_id,payroll_statutory_unit_id,
  legal_entity_version_id,version_sequence,name,
  effective_from,effective_to,approval_status,
  approved_at,approved_by,created_by,updated_by
) VALUES (
  '42100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '42000000-0000-0000-0000-000000000001',
  '41100000-0000-0000-0000-000000000001',
  1,
  'Synthetic India Payroll Unit',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.establishment(
  id,tenant_id,code,status,created_by,updated_by
) VALUES (
  '43000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_BLR',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.establishment_version(
  id,tenant_id,establishment_id,
  payroll_statutory_unit_version_id,version_sequence,
  name,state_code,effective_from,effective_to,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '43100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '43000000-0000-0000-0000-000000000001',
  '42100000-0000-0000-0000-000000000001',
  1,
  'Synthetic Bengaluru Establishment',
  'KA',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.payroll_calendar(
  id,tenant_id,code,name,frequency,timezone,
  created_by,updated_by
) VALUES (
  '44000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_MONTHLY_IN',
  'Synthetic Monthly India',
  'MONTHLY',
  'Asia/Kolkata',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.pay_period(
  id,tenant_id,calendar_id,period_code,
  period_start,period_end,payment_date,status,
  created_by,updated_by
) VALUES
(
  '44100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '44000000-0000-0000-0000-000000000001',
  'E2E-2026-07',
  '2026-07-01',
  '2026-07-31',
  '2026-07-31',
  'OPEN',
  'e2e-fixture',
  'e2e-fixture'
),
(
  '44100000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '44000000-0000-0000-0000-000000000001',
  'E2E-2026-08',
  '2026-08-01',
  '2026-08-31',
  '2026-08-31',
  'OPEN',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.pay_group(
  id,tenant_id,code,created_by,updated_by
) VALUES (
  '45000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_MONTHLY_IN',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO organisation.pay_group_version(
  id,tenant_id,pay_group_id,
  payroll_statutory_unit_version_id,calendar_id,
  version_sequence,name,currency,proration_method,
  effective_from,effective_to,approval_status,
  approved_at,approved_by,created_by,updated_by
) VALUES (
  '45100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '45000000-0000-0000-0000-000000000001',
  '42100000-0000-0000-0000-000000000001',
  '44000000-0000-0000-0000-000000000001',
  1,
  'Synthetic Monthly India',
  'INR',
  'CALENDAR_DAYS',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO compensation.pay_component(
  id,tenant_id,code,name,component_type,
  created_by,updated_by
) VALUES (
  '46000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_BASIC',
  'Synthetic Basic Pay',
  'EARNING',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO compensation.pay_component_version(
  id,tenant_id,component_id,version_sequence,
  formula_type,formula_expression,fixed_amount,
  rounding_scale,effective_from,effective_to,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '46100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '46000000-0000-0000-0000-000000000001',
  1,
  'FIXED',
  NULL,
  90000.0000,
  2,
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO compensation.salary_structure(
  id,tenant_id,code,created_by,updated_by
) VALUES (
  '47000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E_DEFAULT',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO compensation.salary_structure_version(
  id,tenant_id,salary_structure_id,version_sequence,
  name,currency,effective_from,effective_to,
  approval_status,created_by,updated_by
) VALUES (
  '47100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '47000000-0000-0000-0000-000000000001',
  1,
  'Synthetic Default Structure',
  'INR',
  '2026-01-01',
  '2027-01-01',
  'DRAFT',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO compensation.salary_structure_line(
  id,tenant_id,salary_structure_version_id,
  component_version_id,sequence_no,target_amount,
  effective_from,effective_to,created_by,updated_by
) VALUES (
  '47200000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '47100000-0000-0000-0000-000000000001',
  '46100000-0000-0000-0000-000000000001',
  1,
  90000.0000,
  '2026-01-01',
  '2027-01-01',
  'e2e-fixture',
  'e2e-fixture'
);

SELECT compensation.approve_salary_structure_version(
  '00000000-0000-0000-0000-000000000001',
  '47100000-0000-0000-0000-000000000001',
  'e2e-fixture',
  clock_timestamp()
);

-- Included worker.
INSERT INTO employee_payroll.payroll_relationship(
  id,tenant_id,external_employee_id,employee_number,
  status,created_by,updated_by
) VALUES (
  '48000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  'E2E-EXT-001',
  'E2E-EMP-001',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_relationship_version(
  id,tenant_id,payroll_relationship_id,
  legal_entity_version_id,version_sequence,
  relationship_start,relationship_end,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '48100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000001',
  '41100000-0000-0000-0000-000000000001',
  1,
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.employee_payroll_profile(
  id,tenant_id,payroll_relationship_id,
  currency,payroll_status,created_by,updated_by
) VALUES (
  '48200000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000001',
  'INR',
  'READY',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_assignment(
  id,tenant_id,payroll_relationship_id,
  assignment_number,status,created_by,updated_by
) VALUES (
  '49000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000001',
  'E2E-ASN-001',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_assignment_version(
  id,tenant_id,payroll_assignment_id,
  payroll_relationship_version_id,establishment_version_id,
  version_sequence,assignment_start,assignment_end,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '49100000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '49000000-0000-0000-0000-000000000001',
  '48100000-0000-0000-0000-000000000001',
  '43100000-0000-0000-0000-000000000001',
  1,
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.pay_group_assignment(
  id,tenant_id,payroll_assignment_version_id,
  pay_group_version_id,effective_from,effective_to,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '49200000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '49100000-0000-0000-0000-000000000001',
  '45100000-0000-0000-0000-000000000001',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.salary_assignment(
  id,tenant_id,payroll_assignment_version_id,
  salary_structure_version_id,monthly_amount,currency,
  effective_from,effective_to,approval_status,
  approved_at,approved_by,created_by,updated_by
) VALUES (
  '49300000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000001',
  '49100000-0000-0000-0000-000000000001',
  '47100000-0000-0000-0000-000000000001',
  90000.0000,
  'INR',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

-- Deliberately excluded worker: complete configuration, but profile ON_HOLD.
INSERT INTO employee_payroll.payroll_relationship(
  id,tenant_id,external_employee_id,employee_number,
  status,created_by,updated_by
) VALUES (
  '48000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  'E2E-EXT-002',
  'E2E-EMP-ON-HOLD',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_relationship_version(
  id,tenant_id,payroll_relationship_id,
  legal_entity_version_id,version_sequence,
  relationship_start,relationship_end,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '48100000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000002',
  '41100000-0000-0000-0000-000000000001',
  1,
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.employee_payroll_profile(
  id,tenant_id,payroll_relationship_id,
  currency,payroll_status,created_by,updated_by
) VALUES (
  '48200000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000002',
  'INR',
  'ON_HOLD',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_assignment(
  id,tenant_id,payroll_relationship_id,
  assignment_number,status,created_by,updated_by
) VALUES (
  '49000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '48000000-0000-0000-0000-000000000002',
  'E2E-ASN-ON-HOLD',
  'ACTIVE',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.payroll_assignment_version(
  id,tenant_id,payroll_assignment_id,
  payroll_relationship_version_id,establishment_version_id,
  version_sequence,assignment_start,assignment_end,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '49100000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '49000000-0000-0000-0000-000000000002',
  '48100000-0000-0000-0000-000000000002',
  '43100000-0000-0000-0000-000000000001',
  1,
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.pay_group_assignment(
  id,tenant_id,payroll_assignment_version_id,
  pay_group_version_id,effective_from,effective_to,
  approval_status,approved_at,approved_by,
  created_by,updated_by
) VALUES (
  '49200000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '49100000-0000-0000-0000-000000000002',
  '45100000-0000-0000-0000-000000000001',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

INSERT INTO employee_payroll.salary_assignment(
  id,tenant_id,payroll_assignment_version_id,
  salary_structure_version_id,monthly_amount,currency,
  effective_from,effective_to,approval_status,
  approved_at,approved_by,created_by,updated_by
) VALUES (
  '49300000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  '49100000-0000-0000-0000-000000000002',
  '47100000-0000-0000-0000-000000000001',
  90000.0000,
  'INR',
  '2026-01-01',
  '2027-01-01',
  'APPROVED',
  clock_timestamp(),
  'e2e-fixture',
  'e2e-fixture',
  'e2e-fixture'
);

COMMIT;
