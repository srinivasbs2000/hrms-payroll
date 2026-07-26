# Sprint 4 Manual Statutory Execution Smoke

## Purpose

Run one live, browser-driven statutory workflow before merging PR #19.
Automated CI validates migrations/RLS, APIs, permissions, frontend components,
build output and the existing generic Payroll browser suite. This checklist
validates the integrated authenticated statutory operator path introduced in
Sprint 4.

## Reviewed implementation baseline

- branch: `feature/sprint-4-statutory-deductions`
- implementation head before closure documentation: `6cf39fc1734a50a514cfee22db2fd78bd41b80cc`
- latest migration: V030

Record the exact tested closure commit below. Do not rely on the baseline SHA
when a later closure commit is under review.

## Preconditions

- working tree is clean;
- `scripts/verify-sprint-4.ps1` is green;
- the exact branch commit under review has green required CI checks;
- Docker Desktop is running;
- `deploy/local/.env` contains non-placeholder synthetic development credentials;
- migrations through V030 are applied;
- backend and frontend are running;
- an approved regular payroll cycle has a completed calculation request and persisted payroll results;
- approved statutory rule/profile/assignment data exists for the synthetic employee; and
- no real employee, credential, tax or payroll data is used.

Record:

| Field | Value |
|---|---|
| Tester | |
| Date/time | |
| Commit SHA | |
| Browser/version | |
| Tenant | |
| Development administrator | |
| Development read-only user | |

## Authentication and navigation gate

1. Open `http://localhost:5173`.
2. Confirm the unauthenticated page exposes no payroll or statutory navigation.
3. Sign in as the synthetic `payroll.admin` user.
4. Confirm the application header shows the authenticated username and tenant.
5. Confirm **Statutory** navigation is visible.
6. Refresh and confirm the authenticated session is restored.
7. Confirm browser local storage and session storage contain no access or refresh token.

Result: `PASS / FAIL`

## Permission boundary

- Confirm the administrator can see evaluation, posting and correction actions.
- Later sign in as the synthetic `payroll.smoke` user.
- Confirm read evidence is available according to mapped permissions.
- Confirm evaluation, posting and correction actions are unavailable to the read-only user.
- Confirm an account without `statutory-evaluation.read` cannot access the workspace.

Result: `PASS / FAIL`

## Cycle and calculation lineage

1. Select a regular payroll cycle in `CALCULATED` status.
2. Confirm the displayed cycle, period, payment date and version are correct.
3. Confirm a completed calculation request is available or enter its exact ID.
4. Record the identifiers and current version.

| Evidence | Value |
|---|---|
| Payroll cycle ID | |
| Cycle version before evaluation | |
| Pay period ID | |
| Calculation request ID | |
| Calculation result-set hash | |

Result: `PASS / FAIL`

## Statutory evaluation

1. Execute statutory evaluation once.
2. Confirm visible success and refreshed cycle/evaluation evidence.
3. Confirm the evaluation is `COMPLETED`.
4. Confirm employee, employer and post-statutory-net totals are displayed.
5. Confirm result evidence references the exact payroll result, profile,
   assignment and rule version.
6. Refresh and confirm the same completed evidence remains readable.
7. Do not create a duplicate evaluation for the same idempotent command.

| Evidence | Value |
|---|---|
| Evaluation request ID | |
| Evaluation version | |
| Statutory result ID | |
| Employee statutory total | |
| Employer statutory total | |
| Post-statutory net total | |
| Evidence-set hash | |

Result: `PASS / FAIL`

## Initial ledger posting

1. Select the completed, unposted evaluation.
2. Post it to the statutory ledger.
3. Confirm the completed ledger batch and append-only entry evidence.
4. Confirm cycle employee/employer totals reconcile to the posted evaluation.
5. Confirm PTD/cycle/YTD balance evidence appears.
6. Confirm reconciliation variance is zero.
7. Confirm remittance preparation summaries are visible.
8. Refresh and confirm no duplicate posting is created.

| Evidence | Value |
|---|---|
| Ledger batch ID | |
| Batch attempt number | |
| Ledger entry count | |
| Employee posted total | |
| Employer posted total | |
| Balance snapshot count | |
| Reconciliation ID/hash | |
| Remittance summary count | |

Result: `PASS / FAIL`

## Signed correction

1. Select an exact statutory result from the active posted evaluation.
2. Enter a signed employee or employer delta; at least one delta must be non-zero.
3. Enter a meaningful reason between 8 and 500 characters.
4. Submit the correction.
5. Confirm a new correction batch/entry is appended without rewriting the
   original posting.
6. Confirm balances and reconciliation refresh to include the signed delta.
7. Refresh and confirm the correction remains readable and is not duplicated.

| Evidence | Value |
|---|---|
| Corrected statutory result ID | |
| Correction batch ID | |
| Employee delta | |
| Employer delta | |
| Reason | |
| Updated employee total | |
| Updated employer total | |
| Updated reconciliation hash | |

Result: `PASS / FAIL`

## Negative and safety behaviour

- Attempt posting when no completed unposted evaluation is selected; confirm it is blocked.
- Attempt a zero/zero correction; confirm it is rejected.
- Attempt a correction reason shorter than eight characters; confirm it is rejected.
- Use a stale cycle version and confirm visible conflict feedback rather than false success.
- Confirm another tenant's statutory evidence is not visible.
- Confirm no raw token, secret, salary payload or statutory response body appears in browser storage or logs.
- Confirm the UI does not claim filing, payment, settlement or legal payslip completion.

Result: `PASS / FAIL`

## Completion

Overall result: `PASS / FAIL`

Blocking observations:

Non-blocking observations:

Tester sign-off:

Reviewer sign-off:

## Stop local infrastructure

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
```

## Exact decimal-string transport check

During the signed-correction smoke:

1. enter employee delta `-10.1250`;
2. enter employer delta `0.1000`;
3. confirm the browser request body contains quoted JSON strings, not numeric
   tokens;
4. confirm the response returns quoted monetary strings with the same decimal
   values; and
5. confirm the resulting database and UI evidence preserves the exact values.

Result: `PASS / FAIL`
