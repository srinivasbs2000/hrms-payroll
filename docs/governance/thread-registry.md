# HRMS Payroll Thread and Capability Registry

**Last verified:** 19 August 2026 P5-EIP-01 fresh-R3 activation
**Product reconciliation baseline:** P5-EPA-01 backend PR #83 / `847adab127dcbca3431f9f0af4f35ce46ab55285`; UI PR #19 / `9dbf0d2f700764e2fe577f89142cd6784028f70c`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-EPA-01 backend PR #83 and UI PR #19
**P5-JRF-01 product-status closure:** PR #39
**P5-FBA-01 product-status closure:** PR #45
**Latest merged quality increment:** P5-EPA-01 backend 7/7 GREEN; runtime hardening PR #84 merged; UI selector PR #20 GREEN/merged; final UI PR #19 5/5 GREEN including cross-repository browser E2E
**Active product write owner:** NONE — P5-EIP-01 G01 is read-only
**Migration authority:** V001–V050 immutable; V051 unreserved

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
| P5-A4 | CLOSED after this status closure | Pay groups, period generation, milestone rules and calendar lifecycle | Product PR #58 / `6ce57213c8d77e76d8addee55a92f0349229a314`; closure PR is the PR containing this update | V038 immutable | None |

| P5-SSC-01 | CLOSED after this status closure | Salary Structure Composition, Target & Control Completion | Backend PR #78 / `969be73f971207e09541f1a6cfef7319ac2d8621`; UI PR #18 / `f2d7d1ac1e96cf154b624cf583681c6b751b5219`; closure PR is the PR containing this update | V042-V049 immutable | None |

| P5-EPA-01 | CLOSED after this status closure | Employee payroll relationship, assignment, pay-group binding, salary/target assignment, employee override, compensation-change impact and transfer/rehire/concurrent-assignment completion | Backend PR #83 / `847adab127dcbca3431f9f0af4f35ce46ab55285`; runtime PR #84 / `3c8528b8cd809bdb89aeb50fda2d43f96e8550a7`; UI selector PR #20 / `c81d351dda6607030a22c3d8afa1514ba17f75f0`; UI product PR #19 / `9dbf0d2f700764e2fe577f89142cd6784028f70c`; closure PR is the PR containing this update | V050 immutable | None |

| P5-EIP-01 | ACTIVE — G01 READ-ONLY | Secure employee payroll identifiers, identity mismatch workflow, employee bank/payment instructions and payment readiness | Activation authority; product implementation not yet authorized | V051 unreserved pending G01 verdict | Scope/governance read-only only |

## Active capability workstream

P5-EIP-01 is active at **G01 READ-ONLY** after this activation authority merges.

- selected stories: `PLN-E05-005`, `006`, `011`, `012`;
- all four remain NOT EVIDENCED and are REQUIRED_PRODUCT_UI;
- product write owner remains NONE during G01;
- backend/UI product paths are not yet write-owned;
- V001-V050 are immutable; V051 remains unreserved;
- canonical story statuses and totals remain unchanged; and
- statutory memberships, tax/declarations, onboarding/broad readiness, holds, complete employee snapshots, E06 calculation and payment execution remain excluded.

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

<!-- P5-A4-THREAD-REGISTRY-CLOSURE -->
P5-A4 product evidence is PR #58 / `6ce57213c8d77e76d8addee55a92f0349229a314` with hosted run
`31634393939` green. The post-merge ledger closes `PLN-E02-001` through
`PLN-E02-010`. V038 is immutable and no active write owner remains.

<!-- P5-SSC-01-THREAD-REGISTRY-CLOSURE -->
P5-SSC-01 product evidence is backend PR #78 / `969be73f971207e09541f1a6cfef7319ac2d8621` and UI PR #18 / `f2d7d1ac1e96cf154b624cf583681c6b751b5219`, with hosted backend 7/7 and UI 5/5 checks GREEN. Canonical E04 business stories are all IMPLEMENTED, V001-V049 are immutable, V050 is unreserved, and no active write owner remains.

<!-- P5-EPA-01-THREAD-REGISTRY-CLOSURE -->
P5-EPA-01 product evidence is backend PR #83 / `847adab127dcbca3431f9f0af4f35ce46ab55285` and UI PR #19 / `9dbf0d2f700764e2fe577f89142cd6784028f70c`. Runtime hardening PR #84 merged at `3c8528b8cd809bdb89aeb50fda2d43f96e8550a7`; UI selector hardening PR #20 merged at `c81d351dda6607030a22c3d8afa1514ba17f75f0`. Backend hosted CI was 7/7 GREEN and final UI hosted CI was 5/5 GREEN including cross-repository browser E2E. The seven selected E05 stories are IMPLEMENTED; E05 remains PARTIALLY IMPLEMENTED overall because excluded stories remain open. V001-V050 are immutable, V051 is unreserved, no active product write owner remains, and no next capability is activated.


<!-- P5-EIP-01-ACTIVATION -->
P5-EIP-01 fresh-R3 activation selects PLN-E05-005, 006, 011 and 012 for a read-only G01 architecture/schema/API/UI verdict. Activation changes no canonical story status, assigns no product write owner and leaves V051 unreserved. Backend baseline `549ca266c736aafc58bfbfe2e57c5af554c4448b`; UI baseline `9dbf0d2f700764e2fe577f89142cd6784028f70c`.
