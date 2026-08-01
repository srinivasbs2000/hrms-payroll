# Payroll Original Design to Current Implementation Gap Assessment

**Original scope:** 18 epics, 72 backlog rows, 12 iterations / 14 functional stages
**Current repository baseline:** `18d5ca3554ff217140b7e3c443d086d63bd02070`
**Latest product implementation merge:** `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Assessment rule:** No capability is marked complete unless the original acceptance scope is evidenced.

## 1. Executive finding

The repository is a strong, secure vertical slice, not a complete Payroll product.
Against the original full-product baseline:

| Classification | Epics | Backlog Rows |
|---|---|---|
| PARTIALLY IMPLEMENTED | 11 | 44 |
| NOT STARTED | 6 | 24 |
| REQUIRES LEGAL OR DOMAIN REVALIDATION | 1 | 4 |
| IMPLEMENTED | 0 | 0 |

No original epic is classified `IMPLEMENTED` in full. This does not negate the
completion of current Sprint stories; it means those stories intentionally cover
only part of the original epic acceptance.

## 2. Epic-by-epic gap assessment

| Epic | Classification | Implemented Evidence | Remaining Scope | Recommended Action |
|---|---|---|---|---|
| E01 Foundation | PARTIALLY IMPLEMENTED | Tenant context and RLS; legal entities, payroll statutory units and establishments; effective-dated identity/version hierarchy; lifecycle APIs/UI; audit, idempotency and outbox foundations. | Enterprise group, work locations and jurisdiction resolution, generic statutory-registration framework and metadata, and the complete original foundation control scope are not evidenced. | Complete remaining Iteration 1 foundation before treating E01 as closed. |
| E02 Calendar & Pay Groups | PARTIALLY IMPLEMENTED | Stable pay-group identities and versions, monthly calendars, contiguous period generation, INR assignment, starter proration policy and later population resolution. | The original design's complete calendar frequencies, deadlines, cut-off controls and broader population-routing rules are not evidenced. | Reconcile the full Iteration 1 calendar/pay-group rule set before expansion. |
| E03 Component Catalogue | PARTIALLY IMPLEMENTED | Versioned BASIC, HRA and SPECIAL_ALLOWANCE components with approval lifecycle and fixed-component calculation use. | Named bases and the broader catalogue of earnings, deductions, employer contributions, provisions, reimbursements, benefits, perquisites, notional and accrual components are not implemented. | Design a general component/base model without breaking the starter slice. |
| E04 Salary Structures | PARTIALLY IMPLEMENTED | Stable salary-structure identity/version model, immutable component lines and starter BASIC-HRA-SPECIAL dependency validation. | CTC policies, eligibility, simulations, minimum-wage/statutory validation, benefits/perquisites and the general structure-design model are not evidenced. | Complete the original compensation-design scope before broad payroll adoption. |
| E05 Employee Payroll Profile | PARTIALLY IMPLEMENTED | Payroll relationships, assignments, employee payroll profiles, pay-group and salary assignments, readiness lifecycle and tenant-safe lineage. | Bank profile, employee identifiers, India-specific statutory memberships, tax profile/declarations, full multidimensional readiness, multi-assignment and compensation-change workflows are not complete. | Complete payroll onboarding data and readiness dimensions. |
| E06 Calculation Engine | PARTIALLY IMPLEMENTED | Deterministic fixed-component starter calculation, immutable input and configuration lineage, calendar-day proration, persisted results, component trace and controlled recalculation. | The general formula engine, named bases, full dependency compiler, rounding/cap/gross-up policies, additional run types, simulations, parallel calculation and full failure-isolation model are not complete. | Evolve the starter calculator into the approved general calculation engine. |
| E07 Balances & Retro | PARTIALLY IMPLEMENTED | Controlled recalculation/supersession patterns, signed statutory corrections and statutory cycle/PTD/YTD balance evidence. | General payroll balances, append-only balance movements, chronological retroactive difference calculation, arrears, carry-forwards and retro settlement across payroll periods are not implemented. | Design the complete payroll balance and retro engine as a separate epic increment. |
| E08 Payroll Operations | PARTIALLY IMPLEMENTED | Controlled regular payroll cycle, population resolution, immutable input snapshots, calculation/recalculation operations, API and operator workspace. | Input-source catalogue, delivery windows, cut-offs, source-owner certification, trial payroll replacement, full approval/lock/release workflow, checklists, resumable partitions and operational reconciliations are incomplete. | Complete the original controlled operating model before production use. |
| E09 India Statutory Rules | REQUIRES LEGAL OR DOMAIN REVALIDATION | Jurisdiction-neutral effective-dated rule, profile, assignment, evaluation, ledger, correction, balance and reconciliation foundation. | PF, EPS, EDLI, ESI, PT, LWF, NPS, gratuity, bonus, salary TDS, minimum wage, overtime, deduction limits, legal forms, filing and settlement are explicitly not implemented. | Revalidate Iteration 6 against current authoritative Indian law and notifications before approving detailed stories or calculations. |
| E10 Off-cycle & Final Settlement | NOT STARTED | No off-cycle or final-settlement business capability is evidenced. | Retro corrections, off-cycle runs, reversals, recoveries, advances, negative-net handling, final settlement, legal deadlines and settlement documents. | Create approved design slices after E06-E09 dependencies are stable. |
| E11 Payments | NOT STARTED | No payment execution or banking capability is evidenced. | Payment entitlement/instructions, employer bank and treasury profiles, routing, batches, maker-checker, files/APIs, submission, acknowledgements, returns, retries and bank reconciliation. | Implement only after approved payroll results and settlement boundaries exist. |
| E12 Accounting | NOT STARTED | No payroll accounting or GL integration capability is evidenced. | Accounting events, costing, chart-of-account mapping, journals, accruals, reversals, ERP transfer, drill-back and reconciliation. | Design after calculation, payment and correction event contracts are stable. |
| E13 Payslips & ESS | PARTIALLY IMPLEMENTED | Persisted-result draft-payslip view clearly marked non-legal and not for payment. | Immutable published document snapshots, template lifecycle, authenticity, ESS, former-employee access, correction/replacement/revocation and certificates are not implemented. | Keep the draft view separate from the future legal-document lifecycle. |
| E14 Reporting & Communications | NOT STARTED | Operational evidence reads do not constitute the original reporting and communications epic. | Reconciled operational/statutory/management reports, analytics, secure distribution, employee communications, delivery evidence and report governance. | Define report catalog and reconciliation authorities after core domains stabilize. |
| E15 Audit & Security | PARTIALLY IMPLEMENTED | OIDC permissions, tenant RLS, least privilege, immutable audit, idempotency, outbox/inbox reliability, correlation IDs, secret/dependency scans and architecture gates. | Formal SoD catalog and conflict engine, JML/access reviews, privileged/break-glass access, encryption/key lifecycle, retention/legal hold, incident controls, control testing, governance administration and full BCP operations are incomplete. | Treat E15 as a continuing cross-release program, not a closed foundation task. |
| E16 Migration & Parallel | NOT STARTED | No source-data migration or parallel-payroll program is evidenced. | Data inventory, profiling, source-to-target mapping, cleansing, migration zones, crosswalks, opening balances, mock conversions, parallel payroll and sign-off. | Start only after target functional scope and production model are stable. |
| E17 Cutover & Hypercare | NOT STARTED | No production cutover or hypercare capability is evidenced. | Cutover rehearsals, go/no-go, rollback, first production payroll, command centre, hypercare exit, BAU handover and legacy retirement. | Plan after migration/parallel acceptance is complete. |
| E18 Performance & Resilience | PARTIALLY IMPLEMENTED | Automated CI, Testcontainers, idempotency/outbox/inbox foundations, architecture tests, deterministic execution rules and local Compose environment. | 100k+ employee performance evidence, partitioned/resumable workers, capacity model, production observability/SLOs, DR RTO/RPO, failover and resilience exercises. | Create measurable NFR and resilience acceptance program before production transition. |

## 3. Quality debt within the implemented slice

- S4-05A is functionally merged, but its real secured HTTP/PostgreSQL statutory integration layer is missing (S4-06A).
- S4-05B is functionally merged, but a dedicated statutory Playwright scenario is missing (S4-06B).
- The committed Sprint 4 manual-smoke document is an unsigned template and is not completion evidence.

## 4. High-risk misinterpretations to prevent

- Do not claim E09 India statutory rules are implemented from the generic V027-V030 foundation.
- Do not claim E16 migration/parallel is implemented because Flyway migrations exist.
- Do not claim E13 legal payslips/ESS are implemented from the draft-payslip view.
- Do not claim E07 retro is implemented from recalculation or statutory corrections.
- Do not claim E18 production resilience from CI/Testcontainers alone.

## 5. Required governance correction before implementation

The six draft authorities should be reviewed and then, through a separately
authorised documentation-only change, published to the repository. The current
master-design/handoff/thread-registry metadata should be refreshed to the PR #21
merge baseline at the same controlled boundary.
