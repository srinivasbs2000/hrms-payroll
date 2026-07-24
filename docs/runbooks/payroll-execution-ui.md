# Payroll Execution and Draft Payslip UI

## Scope

S3-05 adds a permission-driven React workspace over the existing Sprint 3
payroll-execution APIs. It does not add or change a database migration, backend
endpoint, OpenAPI contract, Keycloak role mapping or dependency.

Routes:

- `/payroll-execution`
- `/draft-payslip?cycleId={cycleId}&resultId={resultId}`

## Execution sequence

1. Create or select a regular payroll cycle.
2. Resolve the cycle population.
3. Inspect included employees and immutable configuration evidence.
4. Seal the input snapshots.
5. Inspect snapshot hashes and the combined set hash.
6. Execute the initial deterministic calculation.
7. Inspect calculation-attempt history and employee result totals.
8. Open a real draft payslip generated from persisted result/component rows.
9. Enter a bounded reason and perform a controlled recalculation when needed.
10. Confirm that prior attempts remain visible and the newest attempt is first.

All writes use a fresh `Idempotency-Key`; version-sensitive commands send the
selected cycle's numeric version through `If-Match`.

## Permissions

- `payroll-cycle.read`
- `payroll-cycle.create`
- `payroll-cycle.population.resolve`
- `payroll-cycle.inputs.read`
- `payroll-cycle.inputs.seal`
- `payroll-calculation.execute`
- `payroll-calculation.recalculate`
- `payroll-result.read`
- `payroll-result.trace.read`

Unavailable actions and evidence are hidden or replaced with a clear permission
message.

## Draft-payslip boundary

The page is explicitly marked:

`DRAFT · NOT FOR PAYMENT · NOT A LEGAL PAYSLIP`

It renders only persisted calculation evidence:

- employee and assignment identifiers;
- calculation request and input snapshot lineage;
- earnings and deduction component rows;
- gross, deduction and net totals;
- result/input hashes and salary-structure lineage; and
- trace evidence when permitted.

The UI does not present this preview as a final or legally published payslip.

## Verification

From `frontend/payroll-web`:

```powershell
npm ci
npm run lint
npm test
npm run build
npm audit --audit-level=high
```

Then from the repository root:

```powershell
.\mvnw.cmd --batch-mode verify

npx --yes --package=@redocly/cli@2.39.0 `
  redocly lint `
  contracts/openapi/payroll-vertical-slice-openapi-v1.yaml

git diff --check
```

Acceptance:

- frontend lint passes;
- all frontend tests pass with zero skips;
- production build passes;
- npm audit has no high or critical finding;
- backend Maven verification remains green;
- OpenAPI remains valid with zero errors and zero warnings; and
- the working-tree scope contains only the eight S3-05 files.
