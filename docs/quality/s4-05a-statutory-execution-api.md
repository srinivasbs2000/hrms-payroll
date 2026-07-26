# S4-05A Statutory Execution API

## Baseline

- Branch: `feature/sprint-4-statutory-deductions`
- Required head: `34a3af93433eb61b801db36c8ff84fe1ccfad874`
- Database foundation: V001-V030 committed and CI green
- Pull request: #19 remains open and unmerged

## Scope

This increment exposes the controlled V029/V030 execution contracts without
granting application users direct writes to statutory evidence tables.

Commands:

- evaluate the active completed payroll calculation;
- post a completed statutory evaluation to the append-only ledger;
- post a signed statutory correction.

Evidence reads:

- evaluation-request history;
- immutable statutory results;
- ledger batches and entries;
- PTD/cycle/YTD balance snapshots;
- zero-variance reconciliation;
- remittance-ready preparation summaries.

## Endpoint boundary

All routes are cycle-scoped under:

`/api/v1/payroll-cycles/{cycleId}/statutory`

The three commands require both `Idempotency-Key` and numeric `If-Match`.
The database functions remain the only write path. HTTP handlers do not issue
direct INSERT or UPDATE statements against statutory evidence tables.

## Permissions

- `statutory-evaluation.execute`
- `statutory-evaluation.read`
- `statutory-ledger.post`
- `statutory-ledger.correct`
- `statutory-ledger.read`
- `statutory-balance.read`
- `statutory-reconciliation.read`
- `statutory-remittance.read`

The synthetic administrator receives all permissions. The synthetic smoke user
receives only the five read permissions.

## Idempotent audit and events

After a controlled command returns, the application checks for an existing
outbox event with the same aggregate and event type. Audit and outbox evidence
is appended only when that event is absent. This preserves command replay
semantics without duplicating application-side side effects.

Events:

- `StatutoryEvaluated`
- `StatutoryLedgerPosted`
- `StatutoryLedgerCorrected`

## Explicit exclusions

- statutory rule/profile/classification configuration APIs;
- balance-year configuration APIs;
- jurisdiction-specific eligibility or exemption resolution;
- filing, payment, acknowledgement and settlement;
- statutory UI;
- final/legal payslip publication.

## Verification

The supplied verifier runs:

1. statutory module tests;
2. complete Maven `clean verify`;
3. OpenAPI lint for the statutory and aggregate contracts;
4. Keycloak JSON parsing and permission checks;
5. exact working-tree allow-list and `git diff --check`.

No staging, commit, push or merge is performed.
