# HRMS Payroll Thread Registry

**Last verified:** 4 August 2026
**Repository baseline:** `main` at `d7b7a7c193b964fb5606e0cb74f92ad6fd6db3e8`
**Latest repository publication:** PR #29 merged
**Latest merged product increment:** P5-A1 through PR #25
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

Only one registered capability workstream or write-capable process may own
overlapping files or the next migration number. Chat conversation numbering is
not repository ownership. An owner not explicitly registered as active has no
write ownership.

## Thread ledger

| Thread | Role/status | Scope | Branch/PR | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | DESIGN/PLANNING — inactive | Full-product authority and PLN-01 source | Historical PR #22 | None | None | Reference only |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | Historical PR #3/#18 | None | Historical | Reference only |
| Thread 3 | CLOSED | Sprint 3 completion/E2E | PR #18 merged | None | Historical | Reference only |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | PR #19 merged | None | V027-V030 implemented | Reference only |
| Thread 5 | CLOSED | Recovery/handoff/process audit | No active PR | None | None | Reference only |
| Thread 6 | CLOSED | P5-A1 organisation hierarchy closure | PR #25 merged | None; released | V031 implemented | Reference and incident history only |
| Thread 7 | CLOSED | S4-06A statutory API integration quality closure | PR #28 merged; PR #27 closed unmerged | None; released | None; V032 was unreserved at closure | Reference and incident history only |

## Active capability workstream

| Capability owner | Status | Scope | Branch | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| P5-A2 | ACTIVE — PREPARATION | General component catalogue, named payroll bases and exact component/base memberships | `feature/p5-a2-compensation-catalogue-named-bases` | Exact 46-path maximum boundary in `docs/planning/pln-01/p5-a2-compensation-configuration-scope.md` | V032 exclusively reserved; SQL creation not yet authorised | Obtain separate product-implementation authorisation |

## Current ownership state

- P5-A2 is the only active capability workstream.
- Its ownership is independent of ChatGPT thread numbering; this conversation remains Payroll System Design – Thread 6.
- Thread 7 is historical repository terminology only and has no current ownership.
- S4-06A is merged through PR #28 and remains inactive.
- V001-V031 are committed and immutable.
- V032 is reserved exclusively for P5-A2; its SQL file is not yet authorised.
- P5-A2 implementation is not yet authorised.
- S4-06B remains planned and unauthorized.
- Both S4-06A branches are historical and have no active ownership.
- Historical P5-A1 and authority-closure branches have no active ownership.
- Unrelated Dependabot pull requests are informational and do not grant
  implementation ownership.

## P5-A2 exact ownership authority

The exact maximum 46-path boundary, mandatory architecture corrections and
stop-and-split conditions are recorded in:

`docs/planning/pln-01/p5-a2-compensation-configuration-scope.md`

No path outside that document is owned. Activation and V032 reservation do not
authorise product implementation, PR creation, merge or branch deletion.

## Historical Thread 7 exact allow-list

### New files

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`

### Modified files

3. `docs/design/hrms-payroll-master-design.md`
4. `docs/design/decision-register.md`
5. `docs/governance/thread-registry.md`
6. `docs/runbooks/project-continuation-handoff.md`

No production Java, migration, OpenAPI, Keycloak, POM/dependency, frontend,
deployment or CI/workflow file was owned by Thread 7.

This allow-list is retained only as completion evidence and grants no current
write ownership.

## S4-06A activation and completion evidence

- selected by the project owner on 4 August 2026;
- planning checkpoint:
  `HRMS-Payroll-S4-06A-Resumption-Planning-Checkpoint.zip`;
- checkpoint SHA-256:
  `d6d2c465499fe27f80ee6ebf4a6fb8b39eca1d0aa36afc5a761d7c867e04c6a5`;
- approved baseline:
  `961465cb551f3757a6f51f1322e6b46c32317b16`;
- proposed branch verified absent before activation;
- 15 unrelated open Dependabot PRs classified as non-blocking;
- no migration reservation;
- stop-and-split required for every path outside the exact allow-list;
- original PR #27 closed unmerged after full-range secret-scan history retained a synthetic false positive;
- clean-history replacement PR #28 contained one commit and the same six-file final tree;
- PR #28 CI run 100 completed 9/9 successful jobs;
- PR #28 merged as `12f3210c91ca95f3f331911d4cdc1755f2afd701`;
- both historical S4-06A branches were preserved; no branch deletion was authorized.

## Thread 7 completion state

Thread 7 completed its exact six-file quality closure through PR #28 and is
closed. Its write ownership is released and must not be reused implicitly.

Any later production, contract, security, dependency, migration, frontend or
CI work requires a new registered owner, exact scope and separate authorization.

## Mandatory GitHub boundary

Assistant and agent GitHub access is strictly read-only. GitHub mutations are
performed only by the project owner using a deterministic local package, then
verified through read-only GitHub inspection and returned evidence.
