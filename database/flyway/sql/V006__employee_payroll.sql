CREATE TABLE employee_payroll.payroll_relationship (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, external_employee_id varchar(100) NOT NULL, employee_number varchar(60) NOT NULL,
 legal_entity_id uuid NOT NULL, relationship_start date NOT NULL, relationship_end date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,employee_number),
 CHECK(relationship_end IS NULL OR relationship_end>=relationship_start), FOREIGN KEY(tenant_id,legal_entity_id) REFERENCES organisation.legal_entity(tenant_id,id));
CREATE TABLE employee_payroll.payroll_assignment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_relationship_id uuid NOT NULL, establishment_id uuid NOT NULL,
 assignment_number varchar(60) NOT NULL, assignment_start date NOT NULL, assignment_end date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,assignment_number),
 CHECK(assignment_end IS NULL OR assignment_end>=assignment_start),
 FOREIGN KEY(tenant_id,payroll_relationship_id) REFERENCES employee_payroll.payroll_relationship(tenant_id,id),
 FOREIGN KEY(tenant_id,establishment_id) REFERENCES organisation.establishment(tenant_id,id));
CREATE TABLE employee_payroll.employee_payroll_profile (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_relationship_id uuid NOT NULL, currency platform.currency_code NOT NULL DEFAULT 'INR',
 payroll_status varchar(20) NOT NULL DEFAULT 'READY', created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_relationship_id),
 FOREIGN KEY(tenant_id,payroll_relationship_id) REFERENCES employee_payroll.payroll_relationship(tenant_id,id));
CREATE TABLE employee_payroll.pay_group_assignment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_assignment_id uuid NOT NULL, pay_group_id uuid NOT NULL,
 effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,payroll_assignment_id) REFERENCES employee_payroll.payroll_assignment(tenant_id,id),
 FOREIGN KEY(tenant_id,pay_group_id) REFERENCES organisation.pay_group(tenant_id,id));
CREATE TABLE employee_payroll.salary_assignment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_assignment_id uuid NOT NULL, salary_structure_id uuid NOT NULL,
 monthly_amount numeric(19,4) NOT NULL CHECK(monthly_amount>=0), currency platform.currency_code NOT NULL DEFAULT 'INR', effective_from date NOT NULL, effective_to date,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,payroll_assignment_id) REFERENCES employee_payroll.payroll_assignment(tenant_id,id),
 FOREIGN KEY(tenant_id,salary_structure_id) REFERENCES compensation.salary_structure(tenant_id,id));
CREATE INDEX salary_assignment_lookup_ix ON employee_payroll.salary_assignment(tenant_id,payroll_assignment_id,effective_from,effective_to);
