# HRMS Payroll Project Continuation Handoff

**Updated:** 4 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Repository baseline:** `main` at `12f3210c91ca95f3f331911d4cdc1755f2afd701`
**Latest repository publication:** PR #28 — merged
**Latest merged product increment:** P5-A1 through PR #25
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Mandatory status:** Validate this file against local Git and live read-only
GitHub evidence before writing.

## Current checkpoint

| Item | Current fact |
|---|---|
| Remote `main` | `12f3210c91ca95f3f331911d4cdc1755f2afd701` |
| Latest repository publication | PR #28 — merged |
| Latest product increment | P5-A1 through PR #25 |
| Latest CI evidence | PR #28 run 100; 9/9 jobs successful |
| Active implementation package | None; S4-06A is merged and closed |
| Active write owner | None |
| Local implementation branch | No active implementation branch; synchronize from `main` before the next gate |
| Thread 6 | Closed; ownership released |
| Migrations | V001-V031 committed and immutable |
| Next migration | V032 unreserved; not required by S4-06A |
| P5-A2 | Not activated |
| S4-06A | Merged through PR #28; Thread 7 closed and ownership released |
| S4-06B | Planned, not authorized |
| GitHub access for assistant/agents | Strictly read-only |

## S4-06A delivered state

S4-06A closed the statutory API integration gap through one real secured
HTTP/PostgreSQL integration suite. It added quality evidence without adding
statutory product behavior.

The implementation must prove:

- PostgreSQL 17 Testcontainers and Flyway through V031;
- runtime `payroll_app` with no superuser or RLS bypass;
- secured controller-to-service-to-database execution;
- evaluation, posting, correction and evidence reads;
- decimal-string money with exact four-place values;
- idempotent replay and changed-payload conflict;
- optimistic concurrency and a real two-request race;
- tenant isolation;
- exactly-once audit and outbox evidence;
- corrected balances, zero-variance reconciliation and remittance preparation evidence.

The final one-commit replacement PR #28 passed 9/9 CI jobs and merged as
`12f3210c91ca95f3f331911d4cdc1755f2afd701`. Original PR #27 was closed
unmerged after its earlier commit history retained a synthetic Gitleaks false
positive. Both quality branches remain historical and undeleted.

## Historical Thread 7 allow-list

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`
3. `docs/design/hrms-payroll-master-design.md`
4. `docs/design/decision-register.md`
5. `docs/governance/thread-registry.md`
6. `docs/runbooks/project-continuation-handoff.md`

This completed allow-list is historical evidence only and grants no active write ownership.

## Migration and product boundary

V001-V031 are immutable. V032 remains unreserved. S4-06A introduced no
migration, production Java change, OpenAPI/Keycloak change, dependency/POM
change, frontend/Playwright change, deployment/workflow change or
jurisdiction-specific legal rule.

Any later need for one of those changes remains a stop-and-split defect/design
boundary requiring a new owner and separate authorization.

## P5-A1 delivered state

P5-A1 and its authority reconciliation remain merged through PR #25 and PR #26.
Thread 6 is closed. The retained historical branches must not be reused.

## Mandatory GitHub read-only boundary

Assistant and agent GitHub access is strictly read-only, even when a connector
advertises write operations. Never attempt connector-based branch, ref, commit,
file, PR, review, comment, label, workflow, ready/draft, auto-merge or merge
mutations.

When GitHub state must change, prepare a deterministic package for the project
owner to run locally with authenticated `git`/`gh`, collect evidence, and verify
the remote result using read-only GitHub access.

## Standing execution norm

`docs/governance/hrms-payroll-execution-norm.md` is mandatory. Non-Codex local
payload execution remains the default. Downloads are assumed under
`$HOME\Downloads`; scripts default to `C:\dev\hrms-payroll`, quote paths,
support spaces and resolve companion files from the package directory.

Native processes must preserve stdout, stderr and the launched process object's
exit code separately. `$LASTEXITCODE` is not authoritative for controlled
gates. Every package fails closed on branch, SHA, index, path, hash or remote
drift.

## Next controlled action

No product implementation is currently authorized. Start the next gate from
`main` at `12f3210c91ca95f3f331911d4cdc1755f2afd701` and select one
separately planned package with an exact owner and file boundary.

S4-06B and P5-A2 remain inactive. V032 remains unreserved. Do not reuse either
S4-06A branch. Branch deletion requires separate explicit authorization.

## Prohibited assumptions

Do not infer authorization for P5-A2, S4-06B, V032, production fixes,
jurisdiction-specific legal rules, publication, branch deletion or merge.
