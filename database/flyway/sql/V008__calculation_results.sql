CREATE TABLE payroll_calc.calculation_request (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_cycle_id uuid NOT NULL, idempotency_key varchar(120) NOT NULL,
 request_hash char(64) NOT NULL, status varchar(20) NOT NULL DEFAULT 'ACCEPTED', requested_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,idempotency_key), FOREIGN KEY(tenant_id,payroll_cycle_id) REFERENCES payroll_ops.payroll_cycle(tenant_id,id));
CREATE TABLE payroll_calc.payroll_result (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, calculation_request_id uuid NOT NULL, payroll_cycle_id uuid NOT NULL,
 payroll_assignment_id uuid NOT NULL, input_snapshot_id uuid NOT NULL, result_hash char(64) NOT NULL, result_status payroll_calc.result_status NOT NULL DEFAULT 'CALCULATED',
 currency platform.currency_code NOT NULL, gross_amount numeric(19,4) NOT NULL, deduction_amount numeric(19,4) NOT NULL DEFAULT 0, net_amount numeric(19,4) NOT NULL,
 calculated_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_cycle_id,payroll_assignment_id,result_hash),
 FOREIGN KEY(tenant_id,calculation_request_id) REFERENCES payroll_calc.calculation_request(tenant_id,id),
 FOREIGN KEY(tenant_id,payroll_assignment_id) REFERENCES employee_payroll.payroll_assignment(tenant_id,id),
 FOREIGN KEY(tenant_id,input_snapshot_id) REFERENCES payroll_ops.input_snapshot(tenant_id,id));
CREATE TABLE payroll_calc.component_result (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_result_id uuid NOT NULL, component_code platform.component_code NOT NULL,
 sequence_no integer NOT NULL, unprorated_amount numeric(19,4) NOT NULL, proration_factor numeric(18,10) NOT NULL, calculated_amount numeric(19,4) NOT NULL,
 currency platform.currency_code NOT NULL, formula_expression varchar(1000), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_result_id,component_code),
 FOREIGN KEY(tenant_id,payroll_result_id) REFERENCES payroll_calc.payroll_result(tenant_id,id));
CREATE TABLE payroll_calc.calculation_trace (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_result_id uuid NOT NULL, component_result_id uuid,
 step_no integer NOT NULL, step_type varchar(50) NOT NULL, inputs jsonb NOT NULL, expression varchar(1000), output_value numeric(19,6), message varchar(1000),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_result_id,step_no), FOREIGN KEY(tenant_id,payroll_result_id) REFERENCES payroll_calc.payroll_result(tenant_id,id),
 FOREIGN KEY(tenant_id,component_result_id) REFERENCES payroll_calc.component_result(tenant_id,id));
