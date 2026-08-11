# HRMS Payroll Project Continuation Handoff

**Updated:** 11 August 2026 after P5-FAD-01 activation merge and local G01 core implementation
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\\dev\\hrms-payroll`
**UI repository:** `srinivasbs2000/hrms-payroll-web`
**Local UI repository:** `C:\\dev\\hrms-payroll-web`
**Product reconciliation baseline:** P5-FSR-01 backend final merge `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI merge `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code it here
**Latest merged product increment:** P5-FSR-01 backend PR #51 / `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI PR #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**P5-JRF-01 product-status closure:** PR #39
**P5-FBA-01 product-status closure:** PR #45
**Latest merged quality increment:** PR #33
**Active capability:** P5-FAD-01 — Foundation Approval & Delegation after activation-authority merge
**Current state:** P5-FAD-01 ACTIVE; G01 shared V037/security authority core locally implemented; G02 domain integration pending
**Migrations:** V001–V036 committed and immutable
**Next migration:** V037 reserved exclusively for P5-FAD-01 after activation-authority merge
**Canonical status:** `docs/governance/payroll-program-status.md`

Read the canonical program status first. Validate all facts against local Git and
live read-only GitHub evidence before starting write-capable work.

## Current checkpoint

| Item | Current fact |
|---|---|
| Current remote `main` | Resolve live with local Git / read-only GitHub; do not infer it from the product merge SHA |
| Payroll UI repository | `srinivasbs2000/hrms-payroll-web`; resolve current `main` live |
| Repository topology | Backend/program authority in `hrms-payroll`; React/UI authority in `hrms-payroll-web` |
| HK-UI-SPLIT-01 | CLOSED; history preserved, independent UI CI active, embedded source copy removed |
| P5-JRF-01 publication commit | `c8ab727787a23b0b211caf27c2158300a38a8eab` |
| P5-JRF-01 product merge | PR #36 / `6ee101bd398b745a0078bd0517b4e3797c571c2b` |
| P5-JRF-01 product-status closure | PR #39 |
| Hosted PR #36 CI | 9/9 GREEN |
| Active write owner | P5-FAD-01 implementation workstream |
| Historical P5-FBA-01 implementation branch | `feature/p5-fba-01-foundation-banking-authority` retained |
| Active path ownership | P5-FAD-01 scope authority; G01 security/V037 core then bounded G02 owner-scoped integrations |
| Migration state | V001–V036 immutable |
| Next migration | V037 reserved exclusively for P5-FAD-01; local G01 candidate not yet published |
| Product deployment | Greenfield; no evidenced production deployment |
| Assistant/agent GitHub access | Strictly read-only |

## Reconciliation checkpoint

The 450 detailed stories reconcile to:

- 18 implemented;
- 154 partially implemented;
- 88 not evidenced;
- 159 not started;
- 31 requiring legal/domain revalidation.

P5-JRF-01 changed canonical story status only where the merged evidence supports
it:

- `PLN-E01-005` -> IMPLEMENTED;
- `PLN-E01-006` -> IMPLEMENTED;
- `PLN-E01-007` -> IMPLEMENTED;
- `PLN-E01-012` -> PARTIALLY IMPLEMENTED.

P5-FBA-01 post-merge reconciliation adds:

- `PLN-E01-008` -> IMPLEMENTED;
- `PLN-E01-009` -> IMPLEMENTED;
- `PLN-E01-011` remains PARTIALLY IMPLEMENTED;
- `PLN-E01-012` remains PARTIALLY IMPLEMENTED with bounded banking/signatory readiness.

The execution-candidate IDs `P5-E01-005..010` are not one-for-one with the
canonical PLN numbering. Canonical bank-account and signatory rows are now implemented through P5-FBA-01; the snapshot row remains unchanged and outside that capability.

The complete machine-readable ledger is:

`backlog/payroll-detailed-story-status.csv`
P5-FSR-01 post-merge reconciliation adds:

- `PLN-E01-010` -> IMPLEMENTED;
- `PLN-E01-012` -> IMPLEMENTED for bounded generic `FOUNDATION_ONLY` readiness;
- `PLN-E01-011` remains PARTIALLY IMPLEMENTED.

The generic readiness API does not infer country-specific legal obligations;
registration requirements are caller-declared and an empty list is not a legal
conclusion.


## Naming control

Current execution labels P5-A2 and P5-A3 do not equal the original packages with
the same identifiers.

- Current P5-A2 maps primarily to original P5-B1.
- Current P5-A3 maps primarily to original P5-B4/P5-B5 and selected P5-B6.
- Original P5-A2 jurisdiction/registration is complete through P5-JRF-01.
- Original P5-A3 remains partial only because reusable application approver/delegation controls in PLN-E01-011 remain open; bank/signatory, immutable snapshots and bounded foundation readiness are implemented through P5-FBA-01 and P5-FSR-01.

Closed package:

- Original program package: `P5-A2`
- Execution capability: `P5-JRF-01`
- Title: Jurisdiction and Registration Foundations
- State: `MERGED / CLOSED`
- Scope authority:
  `docs/planning/pln-01/p5-jrf-01-jurisdiction-registration-foundations-scope.md`

## Authority state

P5-FSR-01 is CLOSED. P5-FAD-01 activation PR #53 merged at
`b4267168892eb602764d194eb0f303f8d8233323`.

- P5-FAD-01 is ACTIVE;
- V037 is reserved exclusively for P5-FAD-01;
- primary canonical story PLN-E01-011 remains PARTIALLY IMPLEMENTED;
- shared application-approval authority is owned by `security`;
- business-domain lifecycle transitions remain domain-owned;
- legal signatory authority remains separate from application access;
- G01 adds the local V037/security authority core without story-status change;
- G02 owner-scoped integration and independent critical review remain pending.

## Exact next controlled action

Verify the local P5-FAD-01 G01 core evidence, then integrate the shared public
security facade only into the owner-scoped approval surfaces frozen in
`docs/history/p5-fad-01-approval-endpoint-inventory.md`.

Do not publish the product branch until G02 integration, full verification,
OpenAPI validation and independent critical review are complete.
