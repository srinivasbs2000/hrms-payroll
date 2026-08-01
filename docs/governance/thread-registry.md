# HRMS Payroll Thread Registry

**Last verified:** 1 August 2026
**Current repository HEAD:** `main` at `4b5da975eb851434957667bdecf138ea9b43f929`
**Current product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

This register coordinates project threads. It does not replace Git branches,
pull requests, code, migrations or the running continuation handoff.

## Active ownership rule

Only one thread may own write access to overlapping files or the next Flyway
migration number. A thread not marked as the active `IMPLEMENTATION OWNER` is
read-only for that scope.

During the Phase A Lite documentation reconciliation, Thread 1 is the only
active write owner and only for the exact eight-file authority/history subset
listed below. Thread 6 remains planned and inactive until Phase A Lite is merged
and ownership is explicitly transferred.

## Thread ledger

| Thread | Role/status | Recovered scope | Branch/PR | Write ownership | Latest durable record | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | RECOVERY/HANDOFF - active Phase A governance owner | Original product design and Sprint 0-2 recovery; living-design bootstrap; cross-thread reconciliation | Historical PR #20 merged; Phase A branch `docs/cross-thread-reconciliation`; no PR yet | Approved Phase A documentation/governance allow-list only; no application files and no migration | `docs/history/thread-1-decision-extract.md`; `docs/history/cross-thread-project-restart-record.md` | Prepare and verify Phase A documentation changes; publication actions remain separate |
| Thread 2 | CLOSED - historical Sprint 2 and early Sprint 3 implementation owner | Completed Sprint 2 and delivered Sprint 3 cycle, population, snapshots, deterministic calculation and recalculation foundation through V026 | PR #3 merged; PR #18 later merged; Thread 2 exit `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | None | `docs/history/thread-2-reconciliation.md` | Historical reference only |
| Thread 3 | CLOSED - historical Sprint 3 completion owner | Completed recalculation application path, execution/draft-payslip UI, browser authentication, Playwright E2E and PR #18 closure | `feature/sprint-3-payroll-execution`; PR #18 merged; final head `ebd2603d91551c6f9e60dc57e2d3500948015703`; merge `73c356662b1888194a72c7006a66bd91443550ca` | None | `docs/history/thread-3-reconciliation.md` | Historical reference only |
| Thread 4 | CLOSED - historical Sprint 4 implementation owner | Implemented V027-V030 statutory rules, profiles, evaluation, ledger, balances, reconciliation, API/UI and exact-money correction | `feature/sprint-4-statutory-deductions`; PR #19 merged; final head `b2a220461cf5ba581b5f67e7619ec146bf7982ed`; merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` | None | `docs/history/thread-4-reconciliation.md` | Historical reference; automation debt transferred to the planned next owner |
| Thread 5 | RECOVERY/HANDOFF - no write ownership | Sprint 4 closure transition, process audit and multi-thread recovery | Historical PR #19 merged; no active Thread 5 branch or PR verified | None | `docs/history/thread-5-reconciliation.md` | Hand approved automation-closure scope to Thread 6, then close |
| Thread 6 | PLANNED IMPLEMENTATION OWNER - inactive | S4-06A statutory API integration closure | Proposed `quality/s4-06a-statutory-api-integration`; no branch or PR yet | None until Phase A is merged and Thread 6 accepts the handoff; no migration reservation | Not started | Read repository authorities, verify local state, register active ownership, then implement only S4-06A |

## Approved Phase A Lite documentation allow-list

Thread 1 may prepare only these files during Phase A Lite:

1. `docs/governance/thread-registry.md`
2. `docs/runbooks/project-continuation-handoff.md`
3. `docs/quality/sprint-4-closure-report.md`
4. `docs/history/thread-2-reconciliation.md`
5. `docs/history/thread-3-reconciliation.md`
6. `docs/history/thread-4-reconciliation.md`
7. `docs/history/thread-5-reconciliation.md`
8. `docs/history/cross-thread-project-restart-record.md`

The following previously proposed documentation changes are deliberately
deferred so they do not block business-value delivery: `AGENTS.md`, `README.md`,
the master design metadata, decision-register indexing, backlog rows and the
manual-smoke checklist. Their known conflicts remain recorded in the handoff.

Phase A Lite does not authorise application code, migrations, dependencies,
OpenAPI, Keycloak, frontend, CI, staging, commit, push, PR updates or merge.

## Ownership transfer to Thread 6

Thread 6 becomes active only after all of the following are true:

1. Phase A Lite is reviewed, committed, pushed and merged through separately
   authorised actions.
2. Local `main` is synchronized to the Phase A merge commit.
3. The local working tree and index are clean.
4. Thread 6 reads the master design, decision register, this registry and the
   running handoff.
5. Thread 6 accepts S4-06A, the exact file allow-list and `NONE` as the migration
   reservation.
6. This registry is updated from `PLANNED` to active `IMPLEMENTATION OWNER`
   before the first implementation write.

## Registration template

| Field | Required value |
|---|---|
| Thread | Exact thread name/number |
| Role | IMPLEMENTATION OWNER / DESIGN-RESEARCH / REVIEW / RECOVERY-HANDOFF / PAUSED / CLOSED |
| Branch/PR | Exact branch, PR and head SHA |
| Approved scope | Capability/story and exclusions |
| File allow-list | Exact paths or bounded modules |
| Migration reservation | Exact next migration, or `NONE` |
| Verification | Required focused/full gates |
| Latest checkpoint | Repository path |
| Blockers | Verified blockers only |
| Next authorised action | One exact action |
| Prohibited actions | Stage/commit/push/merge/migration restrictions |
