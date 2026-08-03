# HRMS Payroll Project Continuation Handoff

**Updated:** 3 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Repository baseline:** `main` at `5b40904764e138a7019f5d5a2b905f7019df8465`
**Latest merged product increment:** P5-A1 through PR #25
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Mandatory status:** Validate this file against local Git and live read-only
GitHub evidence before writing.

## Current checkpoint

| Item | Current fact |
|---|---|
| Remote `main` | `5b40904764e138a7019f5d5a2b905f7019df8465` |
| Latest repository publication | PR #25 — merged |
| P5-A1 source commit | `2e28a96939f8c86c7de26047b4666f77a0278cf9` |
| CI evidence | `payroll-baseline` run 94; 9/9 jobs successful |
| Active implementation package | None |
| Active write owner | None |
| Thread 6 | Closed; ownership released |
| Migrations | V001-V031 committed and immutable |
| Next migration | V032 unreserved |
| P5-A2 | Not activated |
| S4-06A | Paused, not cancelled |
| S4-06B | Planned, not authorized |
| GitHub access for assistant/agents | Strictly read-only |

## P5-A1 delivered state

P5-A1 closes the bounded organisation hierarchy lifecycle foundation from
V015/V016/V022 through V031:

- stable identities and immutable effective-dated exact versions;
- `PENDING_APPROVAL -> ACTIVE -> RETIRED` identity lifecycle;
- `DRAFT -> APPROVED` version lifecycle;
- database-enforced maker-checker approval;
- serialized version-sequence allocation;
- approved-parent and effective-range enforcement;
- PSU `responsibility_scope` and establishment `establishment_type`;
- controlled retirement with evidence, dependency blockers and identity ETag;
- tenant RLS, least privilege, audit and outbox evidence;
- RFC 9457 API problems without SQL leakage;
- aligned OpenAPI, Keycloak and React workflows;
- populated-V030 upgrade, API, concurrency, migration and frontend regression
  coverage.

PR #25 merged this state into `main` as
`5b40904764e138a7019f5d5a2b905f7019df8465`.

## Current ownership and migration boundary

No thread currently owns repository writes. The retained P5-A1 feature branch is
historical and must not be reused. V001-V031 are immutable. V032 may be reserved
only after one next increment is selected and one active owner is registered
with an exact allow-list.

P5-A2 and S4-06A are separate choices. Neither is implicitly authorized by
P5-A1 closure, and they must not be combined for convenience.

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

## Next controlled decision

After this post-merge authority reconciliation is merged, choose exactly one:

1. activate P5-A2 through source-linked planning and a new thread/allow-list; or
2. resume S4-06A as the previously paused statutory API integration closure.

Do not reserve V032 or start implementation until that choice is recorded.

## Prohibited assumptions

Do not infer that P5-A2, S4-06A, S4-06B, country-specific legal rules, branch
deletion or V032 reservation is authorized. Do not treat the historical
P5-A1 branch or Thread 6 allow-list as reusable ownership.
