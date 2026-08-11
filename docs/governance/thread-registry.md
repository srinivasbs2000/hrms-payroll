# HRMS Payroll Thread and Capability Registry

**Last verified:** 12 August 2026 P5-FAD-01 product merge and post-merge closure reconciliation
**Product reconciliation baseline:** P5-FAD-01 PR #55 / `a80e7b4da121665a8b1548acada6b96fac4dfa01`; UI baseline `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-FAD-01 backend PR #55
**P5-JRF-01 product-status closure:** PR #39
**P5-FBA-01 product-status closure:** PR #45
**Latest merged quality increment:** PR #33
**Active product write owner:** None after P5-FAD-01 status closure
**Migration authority:** V001–V037 immutable; V038 unreserved

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
| P5-FBA-01 | CLOSED | Foundation employer banking, authorised signatories, delegated authority and bounded readiness | Backend PR #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`; UI PR #12 / `5c45ab41ee3cb4466fac822c04c771f5de0ba119`; closure PR #45 | V035 immutable | None |
| P5-FSR-01 | CLOSED after this status closure | Immutable foundation configuration snapshot, exact calculation binding and bounded composed foundation readiness | Backend PR #47 / `16d2488252b8a5c3aecd64c0f43fe18b6743d6e8`; PR #49 / `954ed05d11dcb367f6de6e1f3e78aafc17c8beab`; PR #51 / `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI PR #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770` | V036 immutable | None |
| P5-FAD-01 | CLOSED after this status closure | Shared entity/PSU-scoped application approval authority and effective-dated delegation | Product PR #55 / `a80e7b4da121665a8b1548acada6b96fac4dfa01`; closure PR is the PR containing this update | V037 immutable | None |

## Active capability workstream

No product capability is active after this P5-FAD-01 status closure.

- P5-FAD-01 is historical/closed and retains no write ownership;
- V001-V037 are immutable;
- V038 is unreserved;
- no next capability is selected or activated by this closure;
- a fresh R3 reconciliation and separately merged activation authority are
  required before any new product write or migration reservation.

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
