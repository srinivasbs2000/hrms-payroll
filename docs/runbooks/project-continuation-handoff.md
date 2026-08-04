# HRMS Payroll Project Continuation Handoff

**Updated:** 4 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Repository baseline:** `main` at `961465cb551f3757a6f51f1322e6b46c32317b16`
**Latest repository publication:** PR #26 — merged
**Latest merged product increment:** P5-A1 through PR #25
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Mandatory status:** Validate this file against local Git and live read-only
GitHub evidence before writing.

## Current checkpoint

| Item | Current fact |
|---|---|
| Remote `main` | `961465cb551f3757a6f51f1322e6b46c32317b16` |
| Latest repository publication | PR #26 — merged |
| Latest product increment | P5-A1 through PR #25 |
| Latest CI evidence | PR #26 run 96; 9/9 jobs successful |
| Active implementation package | S4-06A statutory API integration closure |
| Active write owner | Thread 7 |
| Local implementation branch | `quality/s4-06a-statutory-api-integration` |
| Thread 6 | Closed; ownership released |
| Migrations | V001-V031 committed and immutable |
| Next migration | V032 unreserved; not required by S4-06A |
| P5-A2 | Not activated |
| S4-06A | Active, bounded and uncommitted |
| S4-06B | Planned, not authorized |
| GitHub access for assistant/agents | Strictly read-only |

## S4-06A active boundary

S4-06A closes the existing statutory API integration gap through one real
secured HTTP/PostgreSQL integration suite. It does not add statutory product
behavior.

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
- balances, zero-variance reconciliation and remittance preparation.

## Exact Thread 7 allow-list

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`
3. `docs/design/hrms-payroll-master-design.md`
4. `docs/design/decision-register.md`
5. `docs/governance/thread-registry.md`
6. `docs/runbooks/project-continuation-handoff.md`

Any other path is outside the active package.

## Migration and product boundary

V001-V031 are immutable. V032 remains unreserved. S4-06A must not introduce a
migration, production Java change, OpenAPI/Keycloak change, dependency/POM
change, frontend/Playwright change, deployment/workflow change or
jurisdiction-specific legal rule.

A discovered need for one of those changes is a stop-and-split defect/design
boundary, not implicit authorization.

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

Run the S4-06A activation package. It may safely move the clean local checkout
to current `main`, create the Thread 7 branch, apply exactly six files and run
focused/full backend gates.

It must stop before staging, commit, push, PR creation or merge.

After green evidence, perform independent critical review before authorizing a
publication package.

## Prohibited assumptions

Do not infer authorization for P5-A2, S4-06B, V032, production fixes,
jurisdiction-specific legal rules, publication, branch deletion or merge.
