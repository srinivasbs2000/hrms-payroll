CREATE FUNCTION platform.reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable record: %.%',TG_TABLE_SCHEMA,TG_TABLE_NAME; END $$;
CREATE TRIGGER payroll_result_immutable BEFORE UPDATE OR DELETE ON payroll_calc.payroll_result FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();
CREATE TRIGGER component_result_immutable BEFORE UPDATE OR DELETE ON payroll_calc.component_result FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();
CREATE TRIGGER trace_immutable BEFORE UPDATE OR DELETE ON payroll_calc.calculation_trace FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit.audit_event FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();
GRANT USAGE ON SCHEMA platform,security,audit,organisation,compensation,employee_payroll,payroll_ops,payroll_calc,documents,integration TO payroll_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA security,organisation,compensation,employee_payroll,payroll_ops,documents,integration TO payroll_app;
GRANT SELECT,INSERT ON ALL TABLES IN SCHEMA audit,payroll_calc TO payroll_app;
REVOKE UPDATE,DELETE ON audit.audit_event,payroll_calc.payroll_result,payroll_calc.component_result,payroll_calc.calculation_trace FROM payroll_app;
