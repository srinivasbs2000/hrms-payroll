# HRMS Payroll Project Continuation Handoff

**Updated:** 2 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Repository baseline:** `main` at `d2df2e7a9cc597ea6e4a15de4ed9d1d040de8462`
**Product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Mandatory status:** Validate this file against local Git and live GitHub before writing.

## Current checkpoint

| Item | Current fact |
|---|---|
| Remote `main` at activation | `d2df2e7a9cc597ea6e4a15de4ed9d1d040de8462` |
| Latest repository publication | PR #22 |
| Latest product sprint baseline | Sprint 4 |
| Active implementation package | P5-A1 Foundation hierarchy closure |
| Active thread | Thread 6 — implementation owner |
| Local branch | `feature/p5-a1-foundation-hierarchy-closure` |
| Migrations | V001-V030 immutable |
| V031 | Reserved for `V031__organisation_hierarchy_closure.sql` |
| Commit/push/PR | Not authorized; none exists at activation |
| Package incident | v1.0 parser failure before repository change; v1.1 supersedes it |
| S4-06A | Paused, not cancelled, not part of P5-A1 |
| S4-06B | Planned, not authorized |

## P5-A1 approved scope

1. P5-E01-001 Organisation hierarchy identity and ownership constraints.
2. P5-E01-002 Legal entity lifecycle and tenant-safe uniqueness.
3. P5-E01-003 Payroll statutory unit responsibility boundaries.
4. P5-E01-004 Establishment lifecycle and operational ownership.

P5-A1 is a bounded closure of V015/V016/V022. It retains stable identity rows,
exact immutable effective-dated versions, half-open ranges, approved-parent
containment, tenant-safe relationships, forced RLS and controlled lifecycle
commands.

## P5-A1 intended change

- identity lifecycle `PENDING_APPROVAL -> ACTIVE -> RETIRED`;
- database code-format constraints and tenant-safe uniqueness behavior;
- database-enforced maker-checker approval;
- serialized version-sequence allocation;
- PSU `responsibility_scope` and establishment `establishment_type`;
- controlled retirement with reason, effective date and identity ETag;
- complete audit state and schema-versioned, kind-specific organisation events;
- RFC 9457 409/422 mapping without SQL leakage;
- OpenAPI, Keycloak and existing React workspace extension;
- migration, API, frontend and regression evidence.

## Standing execution norm

`docs/governance/hrms-payroll-execution-norm.md` is mandatory. Non-Codex local
payload execution is the default. Downloads are assumed under `$HOME\Downloads`;
scripts default to `C:\dev\hrms-payroll`, accept `-RepoRoot`, and resolve
companions from `$PSScriptRoot`. Every `.ps1` must pass the repository's real
PowerShell parser validator before execution; validator-first wrappers fail
closed when this gate is skipped.

## Verification state

This handoff is installed with the uncommitted implementation payload. No test
result is claimed by this file. The application launcher writes command logs and
a machine-readable summary under
`C:\dev\hrms-payroll-artifacts\P5-A1\G05`.

## Next controlled action

Run only the corrected P5-A1 non-Codex v1.1 validator-first package, review all generated evidence and
resolve any focused failure. Staging, commit, push and PR creation require a
separate approval after verification and critical review.

## Prohibited actions

Do not modify V001-V030, start P5-A2, resume S4-06A/S4-06B, add country-specific
legal rules, stage, commit, push, open a PR, merge or delete a branch under this
authorization.

## P5-A1 package v1.1 cardinality incident

The v1.1 wrapper passed parser validation, but the apply script stopped during
clean-worktree preflight because `Invoke-Git` used `return ,$output`. The unary
comma nested the zero-line result and produced `System.Object[]`. Failure evidence
confirmed `main`, approved HEAD, empty status, no commit, no push and no PR.
Package v1.2 supersedes v1.1 and adds semantic zero/one/many output validation.
Future threads enforce `MDR-038`.

## P5-A1 G05 package v1.3 network resilience

Package v1.3 supersedes v1.2 after an external HTTPS failure prevented
`git fetch`. Failure evidence proved that the repository remained clean on
`main` at the approved SHA. Future GitHub-dependent packages must run a bounded
remote connectivity and exact-state gate before mutation and must not permit an
offline bypass.

## PowerShell native stream rule

For Git or any native command whose output becomes repository data, capture
stdout, stderr and exit code separately. Never use `2>&1` for changed-path,
branch, SHA, migration or allow-list checks. Semantic package tests must prove
that stderr warnings cannot enter the stdout data collection.

When an implementation payload was already applied and the failure was only a
post-application validation false positive, preserve the authorized changes and
use a bounded resume package after verifying the exact branch, base SHA, empty
index, changed-path set and payload hashes.

## Native exit-code rule

For every controlled child process, read success or failure from the launched process object's `ExitCode`. Do not rely on `$LASTEXITCODE` for generated repository scripts. Keep stdout and stderr separate and validate a deliberate known non-zero exit before any mutation.
