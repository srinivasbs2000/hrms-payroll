-- P5-SSC-01 G02M / E04-015 salary-structure audit-lineage read authority.
--
-- G02M exposes a read-only, audit-authorised lineage API over evidence that is
-- already persisted by earlier P5-SSC-01 gates. The application database role
-- must therefore have tenant-scoped SELECT authority for every table directly
-- queried by the lineage repository.
--
-- This migration adds read authority only. It does not weaken RLS, immutability,
-- maker-checker controls, or write restrictions.

GRANT SELECT ON
  compensation.salary_structure,
  compensation.salary_structure_version,
  compensation.salary_structure_validation,
  compensation.salary_structure_statutory_evaluation,
  compensation.salary_structure_workflow_action
TO payroll_app;

-- Preserve G02H lifecycle least privilege. Lineage needs only the current
-- binding revision from this state table; audit metadata remains inaccessible.
GRANT SELECT (
  tenant_id,
  salary_structure_version_id,
  binding_revision
)
ON compensation.salary_structure_statutory_state
TO payroll_app;

GRANT SELECT ON
  audit.audit_event
TO payroll_app;

GRANT SELECT ON
  integration.outbox_event
TO payroll_app;

COMMENT ON TABLE compensation.salary_structure_workflow_action IS
  'Immutable salary-structure lifecycle evidence; readable by the application role for audit-authorised lineage reconstruction.';
