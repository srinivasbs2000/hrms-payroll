# S4-02 / V028 Employee Statutory Profile Foundation

## Scope delivered

- Stable employee statutory profiles by payroll relationship, jurisdiction and authority.
- Effective-dated profile versions with registration and neutral classification state.
- Exact employee statutory rule assignments linking:
  - employee statutory profile version;
  - payroll assignment identity and exact approved version;
  - statutory rule identity and exact approved version.
- Rule-specific eligibility status and exemption status/reason.
- Controlled approval and end-date commands with optimistic version checks.
- Approved-range overlap prevention and one-successor history.
- Parent range containment and end-date guards.
- Forced RLS, tenant-safe composite foreign keys and least-privilege grants.
- Focused Testcontainers migration coverage and vertical-slice verification.

## Deliberately excluded

- Country-specific registration identifiers or rates.
- Taxable-base/component mappings.
- Statutory calculation and payroll-result persistence.
- Period-to-date/year-to-date balances, remittance and reconciliation.
- REST/OpenAPI/Keycloak/UI work; those follow after the database lineage is green.

## Verification commands

```powershell
.\mvnw.cmd -pl backend/database-migrations -am `
  -Dit.test=EmployeeStatutoryProfileMigrationIT clean verify

.\mvnw.cmd --batch-mode clean verify

git diff --check
```

## Expected evidence

- V001-V028 fresh migration and Flyway validation pass on PostgreSQL 17.
- `EmployeeStatutoryProfileMigrationIT` passes.
- Full Maven reactor passes.
- `database/flyway/verification/verify_vertical_slice.sql` validates V028.
- No files are staged, committed, pushed or merged by the package.
