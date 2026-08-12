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