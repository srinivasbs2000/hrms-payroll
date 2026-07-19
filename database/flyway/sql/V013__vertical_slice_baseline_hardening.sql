ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_tenant_assignment_fk
  FOREIGN KEY (tenant_id, payroll_assignment_id)
  REFERENCES employee_payroll.payroll_assignment (tenant_id, id);

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_tenant_population_fk
  FOREIGN KEY (tenant_id, payroll_cycle_id, payroll_assignment_id)
  REFERENCES payroll_ops.population_member (tenant_id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_calc.calculation_request
  ADD CONSTRAINT calculation_request_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_tenant_request_cycle_fk
  FOREIGN KEY (tenant_id, calculation_request_id, payroll_cycle_id)
  REFERENCES payroll_calc.calculation_request (tenant_id, id, payroll_cycle_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_tenant_snapshot_cycle_assignment_fk
  FOREIGN KEY (tenant_id, input_snapshot_id, payroll_cycle_id, payroll_assignment_id)
  REFERENCES payroll_ops.input_snapshot (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

CREATE TRIGGER input_snapshot_immutable
  BEFORE UPDATE OR DELETE ON payroll_ops.input_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER draft_payslip_immutable
  BEFORE UPDATE OR DELETE ON documents.draft_payslip
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

REVOKE UPDATE, DELETE ON payroll_ops.input_snapshot, documents.draft_payslip FROM payroll_app;

REVOKE CREATE ON SCHEMA platform, security, audit, organisation, compensation,
  employee_payroll, payroll_ops, payroll_calc, documents, integration FROM payroll_app;
