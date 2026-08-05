# HRMS Payroll Project Continuation Handoff

**Updated:** 5 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Repository baseline:** `main` at `887347fb23b35ca72c479f377c0f6e3a1bf89722`
**Latest repository publication:** PR #31 — merged
**Latest merged product increment:** P5-A2 — General Pay Component Catalogue and Named Payroll Bases
**Active capability:** P5-A3 — preparation only
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

Validate this handoff against local Git and live read-only GitHub evidence before
starting any write-capable package.

## Current checkpoint

| Item | Current fact |
|---|---|
| Remote `main` activation baseline | `887347fb23b35ca72c479f377c0f6e3a1bf89722` |
| Latest product PR | PR #30 — merged |
| Latest authority-closure PR | PR #31 — merged |
| P5-A2 product merge | `aeb4b1560e7c7d6147bb288ef989b15ad1be4946` |
| P5-A2 authority-closure merge | `887347fb23b35ca72c479f377c0f6e3a1bf89722` |
| P5-A2 post-product CI | `30957450623` — successful |
| P5-A2 post-closure CI | `30981832364` — successful |
| Active write owner | P5-A3 |
| Active implementation branch | `feature/p5-a3-salary-structure-ctc-eligibility-simulation` |
| Active maximum boundary | Exactly 69 paths |
| Migrations | V001-V032 committed and immutable |
| Next migration | V033 exclusively reserved for P5-A3 |
| V033 SQL file | Not authorised and must not exist after activation |
| P5-A3 product implementation | Not authorised |
| S4-06B | Planned; not authorised |
| Product deployment | Greenfield; no evidenced production deployment or live customer payroll migration |
| GitHub access for assistant/agents | Strictly read-only |

## P5-A3 activated preparation boundary

P5-A3 covers only:

- schema-versioned salary-structure design;
- versioned CTC policies and four distinguishable cost views;
- typed effective-dated eligibility-rule configuration;
- deterministic design-time simulation, comparison and validation;
- exact passing validation fingerprints required before structure approval.

The active authority is:

`docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md`

The boundary contains exactly 69 paths. Any additional path requires a
stop-and-split decision and separate authorisation.

## P5-A3 prohibited assumptions

Activation does not authorise:

- creation of `V033__salary_structure_ctc_eligibility_simulation.sql`;
- product implementation, product commit, push or PR;
- formula DSL, rate tables or arbitrary executable expressions;
- official payroll, tax, statutory, net-pay, target-net or gross-up calculation;
- legal minimum-wage or statutory truth;
- employee salary assignment, revision, override, readiness or live eligibility;
- flexible-benefit elections or supplemental-plan assignment;
- multi-currency execution;
- dependency, CI/workflow or deployment changes;
- branch deletion.

## Migration state

- V001-V032 are immutable.
- V032 is `V032__compensation_catalogue_named_bases.sql`.
- V033 is exclusively reserved by P5-A3.
- Reservation prevents competing use; it does not authorise the SQL file.
- Existing structure, component, base, membership, employee-assignment and
  payroll-result lineage must be preserved.

## Mandatory GitHub read-only boundary

Assistant and agent GitHub access is strictly read-only, even when a connector
advertises write operations. GitHub mutations are executed only by the project
owner through deterministic local `git`/`gh` packages, followed by returned
evidence and read-only verification.

## Standing execution norm

Long or multi-line commands are delivered as downloadable scripts. Every
execution step states what to run, what complete log to upload on success or
failure, and what follows after success. Native stdout, stderr and process exit
codes remain separate controlled evidence.

## Exact next controlled action

Return and independently verify the P5-A3 activation commit and remote branch.

After successful verification, obtain a separate explicit authorisation for
P5-A3 product implementation. That later authorisation may permit creation of
V033 and work inside the 69-path boundary, subject to the recorded critical
review and stop conditions.

No implementation begins implicitly from activation.
