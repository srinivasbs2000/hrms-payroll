# Payroll End-to-End Story Reconciliation

**Date:** 13 August 2026
**Backend baseline:** `5e9126d5331b89b468a25edb3159a723e8b52b4b`
**UI baseline:** `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Scope:** all 450 detailed stories
**Previously IMPLEMENTED stories audited:** 29
**Remaining stories UI-classified:** 421

## Result

| Result | Count |
|---|---:|
| End-to-end complete among previous 29 | 18 |
| UI partial among previous 29 | 5 |
| UI missing among previous 29 | 6 |
| Downgraded IMPLEMENTED -> PARTIALLY IMPLEMENTED | 11 |
| Remaining stories classified for UI applicability | 421 |
| Total stories classified | 450 |

## Previous 29 — end-to-end complete

PLN-E01-001, PLN-E01-002, PLN-E01-003, PLN-E01-004, PLN-E01-005,
PLN-E01-006, PLN-E01-007, PLN-E01-008, PLN-E01-009, PLN-E01-010,
PLN-E01-012, PLN-E03-001, PLN-E03-002, PLN-E03-003, PLN-E04-001,
PLN-E04-004, PLN-E04-007, PLN-E04-008.

## Previous 29 — UI partial

PLN-E01-011, PLN-E02-001, PLN-E02-003, PLN-E02-004, PLN-E02-010.

## Previous 29 — UI missing

PLN-E02-002, PLN-E02-005, PLN-E02-006, PLN-E02-007, PLN-E02-008,
PLN-E02-009.

## Post-reconciliation story totals

- 18 IMPLEMENTED;
- 158 PARTIALLY IMPLEMENTED;
- 84 NOT EVIDENCED;
- 159 NOT STARTED;
- 31 legal/domain revalidation;
- total 450.

## Mandatory next product action

Do not activate P5-A5/E03 yet. First close the 11 end-to-end UI gaps using the
existing backend contracts wherever possible. Backend redesign is not authorized
by this reconciliation unless a UI implementation proves a real contract defect.

## Classification authority

All 450 classifications are recorded in:

`backlog/payroll-story-ui-applicability.csv`

UI-applicability counts at reconciliation:

- UI required/admin/operational/audit: 396
- no direct UI gate: 54

<!-- P5-E2E-UI-01-G06-OVERLAY -->
## Post-P5-E2E-UI-01 G05 overlay — 14 August 2026

The original 13 August 2026 reconciliation above remains the historical baseline.
G05 UI PR #15 merged at `2a42f3909a2ee249ca26be8fb0e14e945f8903a9` and added real-browser closure evidence.

Restored to IMPLEMENTED:

- `PLN-E01-011`;
- `PLN-E02-001`;
- `PLN-E02-005`.

Eight selected stories remain PARTIALLY IMPLEMENTED: `PLN-E02-002`, `003`,
`004`, `006`, `007`, `008`, `009`, `010`.

Post-G06 totals: 21 IMPLEMENTED / 155 PARTIALLY IMPLEMENTED / 84 NOT EVIDENCED /
159 NOT STARTED / 31 legal-domain revalidation = 450.

The next action is not P5-A5/E03. It is a separately bounded backend amendment
activation for the demonstrated contract gaps before any further product write.

<!-- P5-EIP-01-G02C-E2E-OVERLAY -->
## P5-EIP-01 post-merge end-to-end overlay — 23 August 2026

P5-EIP-01 backend PR #88 / `7ade2c199c0eca1351e8907a6e43fbfe8b567b7a`
and UI PR #21 / `00368e714665785000002fe4cbd330bc1e5cc180`
provide the required merged backend, product UI and real-backend browser boundary
for `PLN-E05-005`, `PLN-E05-006`, `PLN-E05-011` and `PLN-E05-012`.

All four are REQUIRED_PRODUCT_UI and are now end-to-end COMPLETE. Exact local
G02B v3.1 proved maker/verifier/approver actor claims, identifier verification/
approval, mismatch resolution, bank verification/approval, payment-instruction
approval and SECURITY-restriction BLOCKER -> clear readiness behavior. Independent
exact-eight-path review passed; backend hosted CI was 7/7 GREEN and UI hosted CI
was 5/5 GREEN.

Canonical totals after this overlay are 62 IMPLEMENTED / 127 PARTIALLY
IMPLEMENTED / 71 NOT EVIDENCED / 159 NOT STARTED / 31 LEGAL/DOMAIN
REVALIDATION = 450. No next product capability is activated by this overlay.
