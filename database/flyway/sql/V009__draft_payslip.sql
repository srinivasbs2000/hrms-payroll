CREATE TABLE documents.draft_payslip (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, payroll_result_id uuid NOT NULL, document_version integer NOT NULL DEFAULT 1,
 snapshot_payload jsonb NOT NULL, rendered_html text NOT NULL, status varchar(20) NOT NULL DEFAULT 'DRAFT', generated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,payroll_result_id,document_version),
 FOREIGN KEY(tenant_id,payroll_result_id) REFERENCES payroll_calc.payroll_result(tenant_id,id));
