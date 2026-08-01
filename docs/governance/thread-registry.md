# HRMS Payroll Thread Registry

**Last verified:** 1 August 2026
**Publication source baseline:** `main` at `18d5ca3554ff217140b7e3c443d086d63bd02070`
**Product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

Only one thread may own overlapping write scope. A thread not explicitly
registered as active has no write ownership.

## Thread ledger

| Thread | Role/status | Scope | Branch/PR | Write ownership | Durable record | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | DESIGN-RESEARCH / DOCUMENTATION OWNER - active | Full-product reconciliation/publication; later PLN-01 | Proposed `docs/full-product-scope-authority`; PR none at preparation | Exact 12-file publication allow-list only | `docs/history/full-product-scope-reconciliation-record.md` after publication | Create branch/apply payload only after approval |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | Historical PR #3/#18 | None | `docs/history/thread-2-reconciliation.md` | Reference only |
| Thread 3 | CLOSED | Sprint 3 completion/E2E | PR #18 merged | None | `docs/history/thread-3-reconciliation.md` | Reference only |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | PR #19 merged | None | `docs/history/thread-4-reconciliation.md` | Reference only |
| Thread 5 | CLOSED | Recovery/handoff/process audit | No active branch/PR | None | `docs/history/thread-5-reconciliation.md` | Reference only |
| Thread 6 | PLANNED / INACTIVE | S4-06A | Proposed quality branch; no PR | None; migration `NONE` if activated | Not started | Wait for separate activation |

## Exact publication allow-list

### New files

1. `docs/product/payroll-product-scope-and-epic-catalog.md`
2. `docs/product/payroll-design-source-register.md`
3. `docs/product/payroll-design-source-register.csv`
4. `backlog/payroll-master-implementation-backlog.csv`
5. `docs/governance/payroll-feature-delivery-lineage.md`
6. `docs/quality/payroll-original-design-to-current-implementation-gap-assessment.md`
7. `docs/roadmap/payroll-release-and-sprint-roadmap.md`
8. `docs/history/full-product-scope-reconciliation-record.md`

### Modified files

9. `docs/design/hrms-payroll-master-design.md`
10. `docs/design/decision-register.md`
11. `docs/runbooks/project-continuation-handoff.md`
12. `docs/governance/thread-registry.md`

No other file is authorised.

## Acceptance criteria

- 18-epic scope is repository-owned;
- all 72 original rows are retained;
- source checksums/hierarchy are recorded;
- Sprint 0-4 lineage is preserved;
- partial/missing/revalidation classifications are explicit;
- PLN-01 is durably recorded;
- pre-PR-21 status is corrected;
- V001-V030 remain unchanged and V031 unreserved;
- Thread 6 remains inactive;
- actual changed files exactly match this allow-list;
- `git diff --check` passes.

## PLN-01 boundary

PLN-01 requires a separate planning-only file plan and approval after this
publication is merged. It is not part of the current allow-list.

## Prohibited actions

This registration does not authorise staging, commit, push, PR creation, merge,
branch deletion, V031 reservation, S4-06A/S4-06B or PLN-01 execution.
