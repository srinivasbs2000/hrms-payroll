-- Development only. Apply with app.tenant_id set to the demo tenant.
INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES ('00000000-0000-0000-0000-000000000001','DEMO','Demo Payroll','seed','seed');
SELECT set_config('app.tenant_id','00000000-0000-0000-0000-000000000001',false);
INSERT INTO security.role(id,tenant_id,code,name,created_by,updated_by) VALUES
('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','PAYROLL_ADMIN','Payroll Administrator','seed','seed'),
('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','PAYROLL_OPERATOR','Payroll Operator','seed','seed'),
('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','PAYROLL_REVIEWER','Payroll Reviewer','seed','seed');
INSERT INTO compensation.pay_component(id,tenant_id,code,name,component_type,created_by,updated_by) VALUES
('50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','BASIC','Basic Pay','EARNING','seed','seed'),
('50000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','HRA','House Rent Allowance','EARNING','seed','seed'),
('50000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','SPECIAL_ALLOWANCE','Special Allowance','EARNING','seed','seed');
