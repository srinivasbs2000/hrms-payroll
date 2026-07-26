# S4-04 / V030 Statutory Ledger, Balance and Reconciliation Foundation

## Scope delivered

- Approved jurisdiction/authority statutory balance years with explicit YTD boundaries.
- Controlled, idempotent statutory ledger posting for a completed V029 evaluation.
- Append-only signed ledger entries for:
  - initial evaluation postings;
  - recalculation replacement reversals;
  - replacement evaluation postings; and
  - approved corrections.
- Exact lineage to the payroll cycle, pay period, calculation request, evaluation request, statutory result, employee statutory profile, rule assignment, rule version and balance year.
- Immutable PTD, cycle and YTD balance snapshots generated after each completed batch.
- Zero-variance reconciliation between:
  - immutable V029 source totals;
  - cumulative signed corrections; and
  - append-only cycle ledger totals.
- Authority/rule remittance-ready summaries with PAYABLE, CREDIT or ZERO positions.
- Active statutory-ledger pointer and current statutory totals on the payroll cycle.
- Forced RLS, tenant-safe composite foreign keys, controlled lifecycle functions and least-privilege runtime grants.
- Focused PostgreSQL 17/Testcontainers migration coverage and vertical-slice verification.

## Accounting model

The ledger is the source of truth. Balance snapshots and remittance summaries are immutable derived evidence.

A recalculated payroll does not rewrite prior statutory postings. V030 reverses only the active posting epoch—the latest evaluation posting plus its signed corrections—then posts the replacement V029 evaluation. Earlier reversal history remains immutable and is never reversed again. A correction is a signed delta against an exact statutory result in the active posted evaluation.

## Jurisdiction neutrality

V030 does not assume a calendar year or a specific national tax year. An approved `statutory_balance_year` supplies the jurisdiction/authority boundary that contains the pay period payment date.

## Deliberately excluded

- Authority filing schemas, statutory returns or acknowledgements.
- Payment initiation, banking integration or remittance settlement.
- Country-specific correction approvals or refund rules.
- Retro/off-cycle reopening policy beyond signed correction evidence.
- REST/OpenAPI/Keycloak/UI work.
- Final/legal payslip publication.

## Verification commands

```powershell
.\mvnw.cmd --batch-mode -pl backend/database-migrations -am `
  -Dit.test=StatutoryLedgerMigrationIT clean verify

.\mvnw.cmd --batch-mode clean verify

git diff --check
```

## Expected evidence

- V001-V030 fresh migration and Flyway validation pass on PostgreSQL 17.
- `StatutoryLedgerMigrationIT` passes with no skips.
- Initial posting, idempotent replay, correction, first replacement and consecutive replacement paths pass.
- Immutable evidence cannot be rewritten by `payroll_app`.
- Tenant-B cannot read tenant-A balance-year or ledger evidence.
- Full Maven reactor passes.
- `database/flyway/verification/verify_vertical_slice.sql` validates V030.
- The package performs no staging, commit, push, PR update or merge.
