# HRMS Payroll Thread Registry

**Last verified:** 3 August 2026
**Repository baseline:** `main` at `5b40904764e138a7019f5d5a2b905f7019df8465`
**Latest merged product increment:** P5-A1 through PR #25
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

Only one thread or write-capable process may own overlapping files or the next
migration number. A thread not explicitly registered as active has no write
ownership.

## Thread ledger

| Thread | Role/status | Scope | Branch/PR | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | DESIGN/PLANNING — inactive | Full-product authority and PLN-01 source | Historical PR #22 | None | None | Reference only |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | Historical PR #3/#18 | None | Historical | Reference only |
| Thread 3 | CLOSED | Sprint 3 completion/E2E | PR #18 merged | None | Historical | Reference only |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | PR #19 merged | None | V027-V030 implemented | Reference only |
| Thread 5 | CLOSED | Recovery/handoff/process audit | No active PR | None | None | Reference only |
| Thread 6 | CLOSED | P5-A1 organisation hierarchy closure | PR #25 merged | None; released | V031 implemented | Reference and incident history only |

## Current ownership state

- No implementation thread is active.
- No repository path has active write ownership.
- V001-V031 are committed and immutable.
- V032 is unreserved.
- The retained remote branch
  `feature/p5-a1-foundation-hierarchy-closure` has no active ownership and must
  not be reused for later work.
- P5-A2 is not activated.
- S4-06A remains paused, not cancelled.
- S4-06B remains planned and not authorized.

## P5-A1 closure evidence

- source commit:
  `2e28a96939f8c86c7de26047b4666f77a0278cf9`;
- PR #25;
- CI workflow `payroll-baseline` run 94 with 9/9 successful jobs;
- merge commit/current `main`:
  `5b40904764e138a7019f5d5a2b905f7019df8465`;
- V031:
  `V031__organisation_hierarchy_closure.sql`;
- 27 changed files, one source commit and one merge commit;
- Thread 6 write ownership released after merge.

## Activation requirements

Before any new repository write:

1. choose exactly one next increment;
2. identify its source story and acceptance boundary;
3. register one active implementation owner;
4. reserve V032 only when a schema change is actually approved;
5. define an exact path allow-list;
6. validate the current `main` SHA and clean local state;
7. retain P5-A1, S4-06A and P5-A2 as separate scopes.

## Mandatory GitHub boundary

Assistant and agent GitHub access is strictly read-only. GitHub mutations are
performed only by the project owner using a deterministic local package, then
verified through read-only GitHub inspection and returned evidence.
