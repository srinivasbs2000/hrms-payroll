# HRMS Payroll Thread Registry

**Last verified:** 5 August 2026
**Repository baseline:** `main` at `887347fb23b35ca72c479f377c0f6e3a1bf89722`
**Latest repository publication:** PR #31 merged
**Latest merged product increment:** P5-A2 through PR #30
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

## Capability workstream history

| Capability owner | Status | Scope | Branch/PR | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| P5-A2 | CLOSED | General component catalogue, named payroll bases and exact component/base memberships | `feature/p5-a2-compensation-catalogue-named-bases`; PR #30 merged | None; released | V032 implemented and immutable | Reference and incident history only |

## Active capability workstream

| Capability owner | Status | Scope | Branch | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| P5-A3 | ACTIVE — PREPARATION | Salary-structure design, CTC policy, typed eligibility rules and deterministic design-time simulation | `feature/p5-a3-salary-structure-ctc-eligibility-simulation` | Exact 69-path maximum boundary in `docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md` | V033 exclusively reserved; SQL creation not authorised | Obtain separate product-implementation authorisation |

## Current ownership state

- P5-A3 is the only active capability workstream.
- P5-A3 owns only the exact 69-path maximum boundary recorded in its scope file.
- V001-V032 are committed and immutable.
- V033 is reserved exclusively for P5-A3.
- Creation of `V033__salary_structure_ctc_eligibility_simulation.sql` and product implementation are not authorised.
- P5-A2 and S4-06A remain merged and inactive.
- S4-06B remains planned and unauthorised.
- Historical P5-A2, P5-A1, S4-06A and authority-closure branches have no active
  ownership and must not be reused or deleted implicitly.
- Unrelated Dependabot pull requests are informational and do not grant
  implementation ownership.

## P5-A3 exact authority

The active scope, architecture controls, stop conditions and exact 69-path
maximum boundary are recorded in:

`docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md`

The planning package is:

`HRMS-Payroll-P5-A3-Planning-and-Critical-Review-v1.0.zip`

SHA-256:

`d704409e9fb4792f15ce05d5ade5cb4f04c80be04e0dc1d31d357402f12e5f77`

Activation reserves V033 but does not authorise its SQL file or product
implementation.

## Historical P5-A2 exact authority

The exact 46-path boundary and architecture controls remain recorded in:

`docs/planning/pln-01/p5-a2-compensation-configuration-scope.md`

That boundary is completion evidence only and grants no current write ownership.

## Mandatory GitHub boundary

Assistant and agent GitHub access is strictly read-only. GitHub mutations are
performed only by the project owner using deterministic local packages, then
verified through read-only GitHub inspection and returned evidence.

## P5-A2 completion evidence

- activation: `e9e297de5e59762f3701ce39ca2295e1839d7d16`;
- implementation: `c30cb1f2f0c16cd78387bb9551b93825bc7ef688`;
- product merge: `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`;
- authority-closure merge/current activation baseline:
  `887347fb23b35ca72c479f377c0f6e3a1bf89722`;
- post-product workflow: `30957450623` — successful;
- post-closure workflow: `30981832364` — successful;
- V032 committed and immutable;
- V033 was unreserved at P5-A2 closure and is now governed exclusively by
  P5-A3 activation authority.

## P5-A3 G07 local closure

| Capability owner | Status | Scope | Branch/PR | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| P5-A3 | LOCALLY VERIFIED — publication pending | Salary-structure design, CTC policies, eligibility rules and design-time simulation | `feature/p5-a3-salary-structure-ctc-eligibility-simulation`; no PR | Owner retains local branch; assistant GitHub read-only | V033 implemented locally | Obtain separate owner authorisation for Git publication |

G07 verification does not release the branch, reserve the next migration or
authorise another capability.
