# HRMS Payroll Thread and Capability Registry

**Last verified:** 10 August 2026 P5-FBA-01 activation
**Product reconciliation baseline:** P5-JRF-01 product merge on `main` at `6ee101bd398b745a0078bd0517b4e3797c571c2b`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-JRF-01 through PR #36
**P5-JRF-01 product-status closure:** PR #39
**Latest merged quality increment:** PR #33
**Active product write owner:** `P5-FBA-01`
**Migration authority:** V001–V034 immutable; V035 reserved exclusively to P5-FBA-01

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
| P5-FBA-01 | ACTIVE | Foundation employer banking, authorised signatories, delegated authority and bounded readiness | Activation authority | V035 reserved | Frozen scope authority |

## Active capability workstream

**P5-FBA-01 — Foundation Banking & Authority: ACTIVE**

- activation base: `0cae307b0f5e7bcd05b47836e6e4df24c8701add`;
- implementation branch: `feature/p5-fba-01-foundation-banking-authority`;
- primary stories: `PLN-E01-008`, `PLN-E01-009`;
- bounded cross-cutting scope: banking/signatory portions of `PLN-E01-011` and `PLN-E01-012`;
- scope authority: `docs/planning/pln-01/p5-fba-01-foundation-banking-authority-scope.md`;
- V035 is reserved exclusively to P5-FBA-01;
- V001-V034 remain immutable;
- P5-JRF-01 and HK-UI-SPLIT-01 remain historical/closed;
- assistant/agent GitHub access remains read-only.

PLN-E01-010 snapshots, complete readiness, employee bank accounts and payment
execution are not owned by this workstream.

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
