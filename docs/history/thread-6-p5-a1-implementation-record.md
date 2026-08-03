# Thread 6 — P5-A1 implementation record

**Activated:** 2 August 2026
**Closed:** 3 August 2026
**Final status:** Merged and closed
**Original base:** `d2df2e7a9cc597ea6e4a15de4ed9d1d040de8462`
**Source commit:** `2e28a96939f8c86c7de26047b4666f77a0278cf9`
**PR:** #25
**Merge commit/current main:** `5b40904764e138a7019f5d5a2b905f7019df8465`
**Branch:** `feature/p5-a1-foundation-hierarchy-closure` retained, inactive
**Migration:** `V031__organisation_hierarchy_closure.sql` implemented

## Authorization boundary

Thread 6 originally owned only the approved P5-A1 allow-list and V031.
Publication and merge were authorized through separately evidenced G08-G10
local packages. The branch was not deleted. P5-A2, S4-06A and S4-06B were not
included.

Thread 6 is now closed. Its path and migration ownership are released. V032
remains unreserved.

## Implemented intent

- preserve V015 stable identity and immutable exact versions;
- preserve V022 approved-parent/range/dependent end-date safeguards;
- add identity lifecycle and retirement evidence;
- enforce maker-checker approval in PostgreSQL;
- serialize version-sequence allocation by locking identity rows;
- add PSU responsibility scope and establishment type;
- expose retirement through identity-level optimistic concurrency;
- produce complete audit state and kind-specific events;
- extend RFC 9457, OpenAPI, Keycloak and the existing React workspace;
- add populated-V030 upgrade, API and frontend coverage.

## Final validation and publication

- G05 full verification completed successfully.
- Migration regression: 94/94 passed.
- Targeted organisation backend and frontend tests passed.
- Full Maven and frontend regressions passed.
- OpenAPI, lint, production build, RLS and security checks passed.
- GitHub Actions `payroll-baseline` run 94 completed with 9/9 successful jobs.
- PR #25 contained one source commit and exactly 27 changed files.
- Merge completed as merge commit
  `5b40904764e138a7019f5d5a2b905f7019df8465`.
- Remote `main` was verified identical to the merge commit.
- The local feature checkout remained clean with an empty index.

## Recovery rule

A focused failure does not reduce scope. Preserve the approved checklist,
correct only the bounded defect, rerun the failed targeted gate and disclose any
remaining limitation or proposed deferral for explicit approval.

## Packaging incident and correction

The v1.0 apply script failed PowerShell parsing at an interpolated `$Path:`
sequence. Parsing stopped before preflight and no repository change occurred.
Package v1.1 supersedes v1.0, fixes the interpolation and requires
validator-first execution for both apply and rollback scripts.

## Local package correction history

- v1.0: parser failure before preflight; no repository change.
- v1.1: native-output nested-array failure during clean-worktree preflight;
  evidence showed clean `main`, approved HEAD and no repository change.
- v1.2: flat-output helper contract plus mandatory semantic cardinality gate.

- v1.3: external GitHub HTTPS failure classified separately from script defects;
  dedicated remote-main/branch preflight and bounded retries added before mutation.

## G05 local execution incident: stream-mixing false positive

On 3 August 2026, package v1.2 passed parser, output-cardinality and remote-SHA
preflights, created the authorized local feature branch and applied the complete
26-path payload. The allow-list gate then failed because `git diff --name-only`
stdout was merged with LF-to-CRLF warnings written to stderr. All reported
repository paths were authorized; no tests, staging, commit, push or pull request
had occurred.

The approved recovery is a resume-only package. It validates the exact partial
state and payload hashes, captures Git stdout and stderr separately, records
warnings as diagnostics, and continues the original verification sequence
without reapplying or rolling back the authorized changes.

## G05 recovery incident: stale PowerShell native exit status

The v1.4 recovery package received the exact approved SHA from `git ls-remote`, but `$LASTEXITCODE` remained `-1` and the valid result was misclassified as a network failure. No resume mutation or test execution began.

Recovery v1.5 replaces every controlled native execution gate with `System.Diagnostics.Process`, using the process object's `ExitCode` together with separately redirected stdout and stderr. Its semantic gate deliberately exits with code 7 and proves the exact exit code and both streams before the resume begins.

## Final delivered capability

P5-A1 closes the organisation hierarchy lifecycle foundation for legal
entities, payroll statutory units and establishments. It implements stable
identity, immutable effective versions, maker-checker approval, approved-parent
and range controls, controlled classification vocabularies, retirement evidence
and blockers, identity-level concurrency, RLS/least privilege, RFC 9457
problems, audit/outbox evidence and aligned API, Keycloak and React behavior.

## Closure state

- Thread 6: closed.
- Write ownership: released.
- V001-V031: committed and immutable.
- V032: unreserved.
- P5-A2: not activated.
- S4-06A: paused, not cancelled.
- GitHub mutations: project-owner local execution only; assistant access is
  strictly read-only.
