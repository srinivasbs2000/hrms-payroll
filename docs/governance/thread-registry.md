# HRMS Payroll Thread Registry

**Last verified:** 1 August 2026  
**Repository baseline:** `main` at `def3dd2e212f85c440eee5497e292be2f1f2bf64`

This register coordinates project threads. It does not replace Git branches,
pull requests or the running continuation handoff.

## Active ownership rule

Only one thread may own write access to overlapping files or the next Flyway
migration number. A thread not marked `IMPLEMENTATION OWNER` is read-only for
that scope.

## Thread ledger

| Thread | Role/status | Recovered scope | Branch/PR | Write ownership | Latest durable record | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | RECOVERY/HANDOFF — current governance bootstrap | Original product design, Sprint 0–2 history and decision recovery; repository has since advanced through Sprint 4 | Documentation branch to be recorded after publication | Documentation-governance allow-list only | `docs/history/thread-1-decision-extract.md` | Establish living master design and multi-thread protocol |
| Thread 2 | NOT VERIFIED | User-created project thread; exact repository responsibility not recovered in this bootstrap | NOT VERIFIED | None until registered | None | Read authority files and register scope |
| Thread 3 | NOT VERIFIED | User-created project thread; exact repository responsibility not recovered in this bootstrap | NOT VERIFIED | None until registered | None | Read authority files and register scope |
| Thread 4 | NOT VERIFIED | User-created project thread; exact repository responsibility not recovered in this bootstrap | NOT VERIFIED | None until registered | None | Read authority files and register scope |
| Thread 5 | NOT VERIFIED | User-created project thread; exact repository responsibility not recovered in this bootstrap | NOT VERIFIED | None until registered | None | Read authority files and register scope |

## Registration template

Add or update one row only after validating live repository state.

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

## Ownership transfer

To transfer implementation ownership:

1. current owner updates its handoff and registry row;
2. working tree/index state is recorded;
3. branch/PR/head and CI are verified;
4. incomplete files and migration reservations are listed;
5. next thread accepts the exact bounded scope;
6. registry is updated before new writes begin.
