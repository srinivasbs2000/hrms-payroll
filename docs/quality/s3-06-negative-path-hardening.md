# S3-06 Negative-Path Hardening and Full Regression

## Purpose

S3-06 closes the planned Sprint 3 negative-path and regression slice without
changing V001–V026 or expanding the payroll functional boundary.

## Hardened boundaries

### Numeric optimistic-concurrency headers

Both payroll-operations and calculation commands now have direct unit coverage
for the shared accepted syntax:

- plain numeric values;
- surrounding whitespace;
- quoted ETag values;
- weak quoted ETag values.

The tests reject:

- missing or blank values;
- negative and decimal values;
- malformed quotes;
- lowercase malformed weak markers;
- non-numeric values; and
- values outside the Java `long` range.

The calculation controller's existing parsing logic was extracted into
`PayrollCalculationHttpSupport` solely to make that boundary independently
testable. Runtime behavior is unchanged.

### Frontend HTTP client

The payroll-execution client is verified to:

- generate a correlation ID;
- generate an idempotency key for every write;
- omit idempotency keys for reads;
- send the selected numeric cycle version through `If-Match`;
- send the recalculation reason as JSON;
- attach the bearer token when available; and
- surface server problem details, including stale-version conflicts.

### Payroll execution workspace

Negative UI coverage verifies:

- cycle-list failures are visible;
- actions are gated by both permission and lifecycle state;
- stale calculation conflicts do not produce false success feedback; and
- controlled recalculation trims the reason and uses the current version.

### Draft payslip

Negative UI coverage verifies:

- a persisted result must be selected;
- result-loading failures are visible; and
- trace evidence is not requested without `payroll-result.trace.read`.

## Existing integration coverage retained

The existing Sprint 3 integration and migration suites continue to cover:

- tenant isolation;
- missing permissions;
- stale versions;
- exact idempotent replay;
- conflicting idempotency-key reuse;
- invalid recalculation reasons;
- immutable population attempts;
- immutable input snapshots;
- deterministic result/component/trace persistence;
- controlled recalculation and supersession;
- audit and outbox cardinality; and
- runtime-role/RLS restrictions.

## Full regression command

Run the repository script from any PowerShell host:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\verify-sprint-3.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

Use `powershell.exe` instead of `pwsh.exe` until PowerShell 7 is installed.

## Acceptance

- payroll-operations and calculation header tests pass;
- all backend tests pass with zero failures/errors/skips;
- all frontend tests pass with zero failures/skips;
- frontend lint and production build pass;
- npm audit has no high or critical findings;
- fresh migration/RLS CI remains green;
- OpenAPI has zero errors and warnings;
- secret scan, dependency review and SBOM jobs pass;
- `git diff --check` produces no output; and
- PR #18 remains draft and unmerged.
