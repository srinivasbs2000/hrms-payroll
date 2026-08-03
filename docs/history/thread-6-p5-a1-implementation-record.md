# Thread 6 — P5-A1 implementation record

**Activated:** 2 August 2026
**Gate:** P5-G05
**Status after package application:** Implementation in progress, uncommitted
**Base:** `d2df2e7a9cc597ea6e4a15de4ed9d1d040de8462`
**Branch:** `feature/p5-a1-foundation-hierarchy-closure`
**Migration:** `V031__organisation_hierarchy_closure.sql`

## Authorization boundary

Authorized: create the local branch, reserve V031, modify the exact Thread 6
allow-list and run validation. Not authorized: staging, commit, push, pull
request, merge, branch deletion, P5-A2, S4-06A or S4-06B.

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

## Validation status

No validation is claimed in this static record. The application script creates
command logs and `P5-A1-run-summary.json`. Results must be reconciled before any
staging authorization.

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
