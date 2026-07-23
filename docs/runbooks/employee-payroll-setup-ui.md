# Employee Payroll Setup UI

## Scope

The Sprint 2 employee-payroll workspace exposes the complete setup chain needed
before payroll-cycle population can include an employee:

1. payroll relationship identity and effective-dated versions;
2. payroll assignment identity and effective-dated versions;
3. employee payroll profile and controlled status transitions;
4. pay-group assignment; and
5. salary assignment.

The screen is available at `/employee-payroll` and is linked from the primary
navigation.

## Operating sequence

1. Select the effective date.
2. Create or select a payroll relationship.
3. Approve the relationship version.
4. Create or select a payroll assignment linked to the exact relationship and
   establishment versions.
5. Approve the assignment version.
6. Create the employee payroll profile.
7. Create and approve a pay-group assignment.
8. Create and approve a salary assignment.
9. Move the payroll profile to `READY`.

The database remains the source of truth for tenant isolation, approval state,
effective-range compatibility, overlap prevention, immutable history and READY
eligibility. The UI displays database/API conflicts rather than attempting to
weaken or duplicate those rules.

## Lifecycle behavior

The workspace supports:

- stable relationship and assignment identity creation;
- future effective-dated versions;
- future-draft corrections without history rewriting;
- approval;
- controlled end dating with numeric `If-Match` values;
- profile transitions to `READY`, `ON_HOLD` and `INACTIVE`;
- pay-group and salary assignment creation, future-draft correction, approval
  and controlled end dating; and
- permission-driven hiding of unavailable controls.

All writes use a fresh idempotency key and all optimistic updates send the
current numeric version through `If-Match`.

## Permissions

Read access:

- `employee-payroll.relationship.read`
- `employee-payroll.assignment.read`
- `employee-payroll.profile.read`
- `employee-payroll.pay-group-assignment.read`
- `employee-payroll.salary-assignment.read`

Write controls use the matching create, version-create, correct, approve,
end-date and status-update permissions defined by the backend.

## Verification

From `frontend/payroll-web`:

```powershell
npm ci
npm run lint
npm test
npm run build
npm audit --audit-level=high
```

Acceptance:

- lint passes;
- all frontend tests pass with zero skips;
- the production build passes;
- no high or critical audit findings; and
- `EmployeePayrollPage.test.tsx` passes all five tests.

Then run the repository-wide verification:

```powershell
Set-Location C:\dev\hrms-payroll

$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
$env:MAVEN_OPTS = "-Duser.timezone=UTC"

.\mvnw.cmd clean verify "-DskipTests=false" "-DskipITs=false"

npx --yes --package=@redocly/cli@2.39.0 `
  redocly lint `
  contracts/openapi/payroll-vertical-slice-openapi-v1.yaml

git diff --check
```

Required outcome:

- backend `BUILD SUCCESS`;
- failures, errors and skips are all zero;
- OpenAPI has zero errors and zero warnings; and
- `git diff --check` produces no output.
