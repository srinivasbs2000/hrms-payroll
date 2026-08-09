# HRMS Payroll Thread and Capability Registry

**Last verified:** 9 August 2026 HK-UI-SPLIT-01 repository separation closure
**Product reconciliation baseline:** P5-JRF-01 product merge on `main` at `6ee101bd398b745a0078bd0517b4e3797c571c2b`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-JRF-01 through PR #36
**P5-JRF-01 product-status closure:** PR #39
**Latest merged quality increment:** PR #33
**Active product write owner:** None
**Migration authority:** V001–V034 immutable; V035 unreserved

Thread numbers are historical conversation labels, not implementation
authority. Only an explicitly active capability entry may own files or a
migration number.

## Historical threads

| Thread | Status | Scope | Ownership |
|---|---|---|---|
| Thread 1 | INACTIVE | Full-product authority and planning history | None |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | None |
| Thread 3 | CLOSED | Sprint 3 completion | None |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | None |
| Thread 5 | CLOSED | Recovery and process audit | None |
| Thread 6 | CLOSED | P5 planning and P5-A1 history | None |
| Thread 7 | CLOSED | S4-06A quality closure | None |

## Capability history

| Execution label | Status | Primary scope | PR/merge | Migration | Ownership |
|---|---|---|---|---|---|
| P5-A1 | CLOSED | Organisation hierarchy lifecycle closure | PR #25 merged | V031 immutable | None |
| P5-A2 | CLOSED | Component catalogue and named payroll bases | PR #30 merged; PR #31 closure | V032 immutable | None |
| P5-A3 | CLOSED | Salary structure, CTC, eligibility and design-time simulation | PR #32 merged; PR #33 quality follow-up | V033 immutable | None |
| S4-06A | CLOSED | Secured statutory API integration quality | PR #28 merged; PR #29 closure | None | None |
| P5-JRF-01 | CLOSED | Jurisdiction and registration foundations | PR #36 / `6ee101bd398b745a0078bd0517b4e3797c571c2b` | V034 immutable | None |
| HK-UI-SPLIT-01 | CLOSED | History-preserving React UI repository split, independent UI CI and backend source cleanup | Backend PR #41 seam; web PR #1 independent CI; 01D closure | None | None |

## Active capability workstream

**NONE.**

P5-JRF-01 is historical/closed:

- activation base `ff581cafce3be5495d93932abfae3931b139358f`;
- publication commit `c8ab727787a23b0b211caf27c2158300a38a8eab`;
- PR #36 merge `6ee101bd398b745a0078bd0517b4e3797c571c2b`;
- historical branch `feature/p5-jrf-01-jurisdiction-registration-foundations` is retained;
- exact product ownership is released by this post-merge closure;
- the temporary three-path dependency-security exception authority is released;
- V034 is committed and immutable;
- V035 is unreserved.

No next product capability, path ownership or migration reservation is implied
by this closure. A future capability must be separately selected and activated.

## Program-status closure process

After each future product increment:

1. verify product merge;
2. reconcile the detailed-story ledger by semantic mapping;
3. update `docs/governance/payroll-program-status.md`;
4. merge a small status-closure PR;
5. release ownership and migration reservation;
6. separately authorize the next capability.

## Mandatory GitHub boundary

Assistant and agent GitHub access remains strictly read-only. Repository
mutations are performed by the project owner through deterministic local
packages and returned evidence.
