# HRMS Payroll Thread and Capability Registry

**Last verified:** 10 August 2026 P5-FBA-01 post-merge status closure
**Product reconciliation baseline:** P5-FBA-01 backend product merge on `main` at `a0234d94ef280a41a744ea6e8483f786a497d211`; UI merge `5c45ab41ee3cb4466fac822c04c771f5de0ba119`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-FBA-01 backend PR #44; UI PR #12
**P5-JRF-01 product-status closure:** PR #39
**P5-FBA-01 product-status closure:** PR #45
**Latest merged quality increment:** PR #33
**Active product write owner:** P5-FSR-01 implementation workstream after activation-authority merge
**Migration authority:** V001–V035 immutable; V036 reserved exclusively for P5-FSR-01 after activation-authority merge

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
| P5-FSR-01 | ACTIVE after activation-authority merge | Immutable foundation configuration snapshot and composed foundation readiness closure | Activation authority first; product PRs not yet created | V036 reserved | P5-FSR-01 implementation workstream |

## Active capability workstream

**P5-FSR-01 — Foundation Snapshot & Readiness Closure** is ACTIVE after the
activation-authority PR merges.

- primary canonical stories: PLN-E01-010 and PLN-E01-012;
- PLN-E01-011 remains partial unless separately evidenced;
- activation branch: `docs/p5-fsr-01-activation-authority`;
- product branch after activation merge: `feature/p5-fsr-01-foundation-snapshot-readiness-closure`;
- V035 remains immutable;
- V036 is reserved exclusively for P5-FSR-01;
- backend/program and bounded readiness-UI ownership is held by this capability;
- exact maximum implementation boundary is the committed P5-FSR-01 scope authority;
- no product code or V036 SQL is part of the activation increment itself.

P5-FBA-01 is historical/closed and retains no ownership.

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
