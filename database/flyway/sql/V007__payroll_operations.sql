CREATE TABLE payroll_ops.payroll_cycle (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, pay_group_id uuid NOT NULL, pay_period_id uuid NOT NULL,
 cycle_type varchar(20) NOT NULL DEFAULT 'REGULAR', status payroll_ops.cycle_status NOT NULL DEFAULT 'DRAFT', control_total numeric(19,4),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,pay_group_id,pay_period_id,cycle_type),
 FOREIGN KEY(tenant_id,pay_group_id) REFERENCES organisation.pay_group(tenant_id,id), FOREIGN KEY(tenant_id,pay_period_id) REFERENCES organisation.pay_period(tenant_id,id));
CREATE TABLE payroll_ops.population_member (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_cycle_id uuid NOT NULL, payroll_assignment_id uuid NOT NULL,
 inclusion_reason varchar(100) NOT NULL, status varchar(20) NOT NULL DEFAULT 'INCLUDED', created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_cycle_id,payroll_assignment_id),
 FOREIGN KEY(tenant_id,payroll_cycle_id) REFERENCES payroll_ops.payroll_cycle(tenant_id,id),
 FOREIGN KEY(tenant_id,payroll_assignment_id) REFERENCES employee_payroll.payroll_assignment(tenant_id,id));
CREATE TABLE payroll_ops.input_snapshot (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_cycle_id uuid NOT NULL, payroll_assignment_id uuid NOT NULL,
 snapshot_hash char(64) NOT NULL, snapshot_payload jsonb NOT NULL, sealed_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_cycle_id,payroll_assignment_id,snapshot_hash),
 FOREIGN KEY(tenant_id,payroll_cycle_id) REFERENCES payroll_ops.payroll_cycle(tenant_id,id));
