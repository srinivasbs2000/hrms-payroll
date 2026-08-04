# HRMS Payroll Thread Registry

**Last verified:** 4 August 2026
**Repository baseline:** `main` at `961465cb551f3757a6f51f1322e6b46c32317b16`
**Latest repository publication:** PR #26 merged
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
| Thread 7 | IMPLEMENTATION OWNER — active | S4-06A statutory API integration closure only | Local `quality/s4-06a-statutory-api-integration`; no PR | Exact six-path allow-list below | None; V032 unreserved | Run focused/full gates, preserve evidence, then critical review |

## Current ownership state

- Thread 7 is the only active write owner.
- S4-06A is a test-only quality closure.
- V001-V031 are committed and immutable.
- V032 is unreserved and is not owned by Thread 7.
- P5-A2 is not activated.
- S4-06B remains planned and unauthorized.
- Historical P5-A1 and authority-closure branches have no active ownership.
- Unrelated Dependabot pull requests are informational and do not grant
  implementation ownership.

## Thread 7 exact allow-list

### New files

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`

### Modified files

3. `docs/design/hrms-payroll-master-design.md`
4. `docs/design/decision-register.md`
5. `docs/governance/thread-registry.md`
6. `docs/runbooks/project-continuation-handoff.md`

No production Java, migration, OpenAPI, Keycloak, POM/dependency, frontend,
deployment or CI/workflow file is owned by Thread 7.

## S4-06A activation evidence

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
- stop-and-split required for every path outside the exact allow-list.

## Thread 7 completion boundary

Thread 7 may proceed through local implementation and verification. It must stop
before staging, commit, push or PR creation until a separate publication gate is
approved through returned evidence.

If testing reveals a production, contract, security, dependency, migration,
frontend or CI defect, Thread 7 must preserve the failing evidence and prepare a
separate bounded defect/design proposal. It must not broaden S4-06A silently.

## Mandatory GitHub boundary

Assistant and agent GitHub access is strictly read-only. GitHub mutations are
performed only by the project owner using a deterministic local package, then
verified through read-only GitHub inspection and returned evidence.
