# Sprint 3 Manual Payroll-Execution Smoke

## Purpose

Run one live, browser-driven payroll workflow before merging PR #18. Automated
CI validates migrations/RLS, APIs, permissions, deterministic calculation,
frontend components and build output. This checklist validates the integrated
authenticated operator experience.

## Reviewed version

- branch: `feature/sprint-3-payroll-execution`
- authentication slice base: `558b2de2f12e846c3f8c2cc4cd684cf30af3a349`
- latest migration: V026

Record the exact tested commit after S3-08 is committed.

## Preconditions

- working tree is clean;
- full `scripts/verify-sprint-3.ps1` regression is green;
- Docker Desktop is running;
- `deploy/local/.env` contains non-placeholder development credentials;
- migrations through V026 are applied;
- backend and frontend are running; and
- suitable approved pay-group, period, employee-payroll and salary-structure
  configuration exists.

Record:

| Field | Value |
|---|---|
| Tester | |
| Date/time | |
| Commit SHA | |
| Browser/version | |
| Tenant | |
| Development user | |

## Authentication gate

1. Open `http://localhost:5173`.
2. Confirm the unauthenticated page shows **Sign in with Keycloak** and no
   payroll navigation.
3. Sign in as `payroll.admin`.
4. Complete Keycloak's required password update when presented.
5. Confirm the application header shows the authenticated username and tenant.
6. Confirm **Sign out** is visible.
7. Refresh the browser and confirm the authenticated session is restored.
8. Confirm browser local storage and session storage contain no access or
   refresh token.

Follow `docs/runbooks/keycloak-browser-login.md` for local account recovery.

Result: `PASS / FAIL`

## Permission boundary

- Confirm the administrator sees setup and payroll-execution navigation.
- Later, sign in as `payroll.smoke` and confirm write actions are unavailable.
- Confirm an authenticated account without a supported read permission sees
  the no-access boundary.

Result: `PASS / FAIL`

## Cycle and population

- Select an existing regular cycle or create one using valid approved
  pay-group-version and open pay-period IDs.
- Record the cycle ID and displayed version.
- Resolve population.
- Confirm the version increments.
- Confirm included employees and inclusion evidence appear.
- Confirm no employee from another tenant appears.

| Evidence | Value |
|---|---|
| Cycle ID | |
| Initial version | |
| Population resolution ID | |
| Included count | |
| Excluded count | |

Result: `PASS / FAIL`

## Immutable input sealing

- Seal inputs.
- Confirm lifecycle `INPUTS_SEALED`.
- Confirm a non-zero snapshot count, snapshot hashes and combined input-set
  hash.
- Refresh and confirm the evidence is unchanged.

| Evidence | Value |
|---|---|
| Sealed version | |
| Snapshot count | |
| Combined hash | |

Result: `PASS / FAIL`

## Initial calculation

- Calculate payroll.
- Confirm lifecycle `CALCULATED`.
- Confirm gross, deduction and net totals.
- Confirm an initial calculation attempt and employee result.

| Evidence | Value |
|---|---|
| Calculated version | |
| Initial request ID | |
| Result ID | |
| Gross total | |
| Deduction total | |
| Net total | |

Result: `PASS / FAIL`

## Real draft payslip

- Open **View** for a persisted result.
- Confirm employee and assignment.
- Confirm persisted earning/deduction components.
- Reconcile gross, deductions and net.
- Confirm result, input-snapshot and salary-structure evidence.
- Confirm trace evidence for the administrator.
- Confirm:

`DRAFT · NOT FOR PAYMENT · NOT A LEGAL PAYSLIP`

Result: `PASS / FAIL`

## Controlled recalculation

- Return to Payroll execution.
- Enter a meaningful reason of at least eight characters.
- Recalculate.
- Confirm version increment, attempt order, displayed reason and readable
  historical results.
- Refresh and confirm no duplicate attempt is created.

| Evidence | Value |
|---|---|
| Recalculated version | |
| Recalculation request ID | |
| Superseded request ID | |
| Reason | |

Result: `PASS / FAIL`

## Negative behavior

- Confirm lifecycle-invalid actions are disabled.
- Use two browser tabs to produce a stale-version conflict and confirm visible
  conflict feedback rather than false success.
- Sign out and sign in as `payroll.smoke`; confirm trace reads remain allowed
  but calculation writes are unavailable.
- Confirm no secret or raw token appears in the UI or logs.

Result: `PASS / FAIL`

## Completion

Overall result: `PASS / FAIL`

Blocking observations:

Non-blocking observations:

Tester sign-off:

## Stop local infrastructure

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
```
