# Payroll Product Scope and Epic Catalog

**Repository:** `srinivasbs2000/hrms-payroll`
**Publication source baseline:** `18d5ca3554ff217140b7e3c443d086d63bd02070`
**Latest product implementation baseline:** `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Source package:** `Payroll-Full-Product-Design-Source-v1.0.zip`
**Source package SHA-256:** `ffbd8d8bdb1053e610171a2b08b7a039132ccb408bba20be694bacced428d41b`
**Prepared:** 1 August 2026
**Status:** Approved full-product scope authority; implementation status remains evidence-based

## 1. Authority statement

The complete intended Payroll application is defined by the uploaded full-product
design set. The canonical consolidated scope authority is:

`Payroll_System_Consolidated_Product_Blueprint_v1_0.docx`

The Product Charter supplies original intent, the twelve iteration documents supply
detailed design, and the backlog/DDL/API-event catalogue are canonical companion
artifacts. The current Git repository is implementation evidence for a bounded
vertical slice and must not be treated as the full product scope.

## 2. Product vision and boundary

Build an India-first, enterprise-grade, multi-tenant payroll operating platform
that can operate independently and integrate with the wider HRMS.

End-to-end operating journey:

`configuration -> onboarding/readiness -> input collection -> calculation -> validation -> approval -> payment -> statutory processing -> accounting -> documents/reporting -> reconciliation -> correction -> audit -> production transition`

The product is not merely a monthly salary calculator. It includes configuration,
operations, legal localisation, downstream settlement, controls, migration and
production acceptance.

## 3. Functional-stage coverage

| Stage | Scope | Detailed Source |
|---|---|---|
| 1 | Organisation, statutory units, establishments, registrations, pay groups and calendars | Iteration 1 |
| 2 | Pay components, salary structures, eligibility, CTC and statutory classifications | Iteration 2 |
| 3 | Employee payroll profile, onboarding, assignments, statutory memberships, tax profile and compensation changes | Iteration 3 |
| 4 | Payroll calculation and formula engine | Iteration 4 |
| 5 | Payroll input collection and regular payroll operations | Iteration 5 |
| 6 | India statutory and salary-tax rule pack | Iteration 6 |
| 7 | Retroactive payroll, off-cycle processing, corrections, reversals, recoveries and final settlement | Iteration 7 |
| 8 | Salary payments, banking and treasury | Iteration 8 |
| 9 | Payroll accounting, costing, GL and ERP reconciliation | Iteration 9 |
| 10 | Payslips, ESS, documents and certificates | Iteration 10 |
| 11 | Reporting, analytics and employee communications | Iteration 10 |
| 12 | Audit, controls, compliance, security, governance, retention, BCP and administration | Iteration 11 |
| 13 | Implementation, data migration, parallel payroll and cutover | Iteration 12 |
| 14 | Go-live, hypercare, handover and product acceptance | Iteration 12 |

## 4. Original release model

| Release | Purpose | Epic Coverage |
|---|---|---|
| R1 — Foundation | Configuration and payroll readiness | E01-E05 plus E15 |
| R2 — Payroll Core | Calculation, balances, operations and India rules | E06-E09 plus E18 |
| R3 — Downstream Completion | Settlement, payments, accounting, documents and reporting | E10-E14 plus E15/E18 |
| R4 — Production Transition | Migration, parallel, cutover and hypercare | E16-E17 plus E15/E18 |

## 5. Epic catalog and current repository qualification

| Epic | Capability | Release | Source | Current Classification | Current Repository Coverage |
|---|---|---|---|---|---|
| E01 | Foundation | R1 | Iteration 1 | PARTIALLY IMPLEMENTED | Tenant context and RLS; legal entities, payroll statutory units and establishments; effective-dated identity/version hierarchy; lifecycle APIs/UI; audit, idempotency and outbox foundations. |
| E02 | Calendar & Pay Groups | R1 | Iteration 1 | PARTIALLY IMPLEMENTED | Stable pay-group identities and versions, monthly calendars, contiguous period generation, INR assignment, starter proration policy and later population resolution. |
| E03 | Component Catalogue | R1 | Iteration 2 | PARTIALLY IMPLEMENTED | Versioned BASIC, HRA and SPECIAL_ALLOWANCE components with approval lifecycle and fixed-component calculation use. |
| E04 | Salary Structures | R1 | Iteration 2 | PARTIALLY IMPLEMENTED | Stable salary-structure identity/version model, immutable component lines and starter BASIC-HRA-SPECIAL dependency validation. |
| E05 | Employee Payroll Profile | R1 | Iteration 3 | PARTIALLY IMPLEMENTED | Payroll relationships, assignments, employee payroll profiles, pay-group and salary assignments, readiness lifecycle and tenant-safe lineage. |
| E06 | Calculation Engine | R2 | Iteration 4 | PARTIALLY IMPLEMENTED | Deterministic fixed-component starter calculation, immutable input and configuration lineage, calendar-day proration, persisted results, component trace and controlled recalculation. |
| E07 | Balances & Retro | R2 | Iterations 4 and 7 | PARTIALLY IMPLEMENTED | Controlled recalculation/supersession patterns, signed statutory corrections and statutory cycle/PTD/YTD balance evidence. |
| E08 | Payroll Operations | R2 | Iteration 5 | PARTIALLY IMPLEMENTED | Controlled regular payroll cycle, population resolution, immutable input snapshots, calculation/recalculation operations, API and operator workspace. |
| E09 | India Statutory Rules | R2 | Iteration 6 | REQUIRES LEGAL OR DOMAIN REVALIDATION | Jurisdiction-neutral effective-dated rule, profile, assignment, evaluation, ledger, correction, balance and reconciliation foundation. |
| E10 | Off-cycle & Final Settlement | R3 | Iteration 7 | NOT STARTED | No off-cycle or final-settlement business capability is evidenced. |
| E11 | Payments | R3 | Iteration 8 | NOT STARTED | No payment execution or banking capability is evidenced. |
| E12 | Accounting | R3 | Iteration 9 | NOT STARTED | No payroll accounting or GL integration capability is evidenced. |
| E13 | Payslips & ESS | R3 | Iteration 10 | PARTIALLY IMPLEMENTED | Persisted-result draft-payslip view clearly marked non-legal and not for payment. |
| E14 | Reporting & Communications | R3 | Iteration 10 | NOT STARTED | Operational evidence reads do not constitute the original reporting and communications epic. |
| E15 | Audit & Security | R1-R4 | Iteration 11 | PARTIALLY IMPLEMENTED | OIDC permissions, tenant RLS, least privilege, immutable audit, idempotency, outbox/inbox reliability, correlation IDs, secret/dependency scans and architecture gates. |
| E16 | Migration & Parallel | R4 | Iteration 12 | NOT STARTED | No source-data migration or parallel-payroll program is evidenced. |
| E17 | Cutover & Hypercare | R4 | Iteration 12 | NOT STARTED | No production cutover or hypercare capability is evidenced. |
| E18 | Performance & Resilience | R2-R4 | Cross-cutting Iterations 4, 5, 11 and 12 | PARTIALLY IMPLEMENTED | Automated CI, Testcontainers, idempotency/outbox/inbox foundations, architecture tests, deterministic execution rules and local Compose environment. |

## 6. Interpretation controls

- A similarly named table, API or module does not prove the complete epic.
- Technical Flyway migration is not evidence that business-data migration/parallel payroll exists.
- A generic statutory framework is not an implemented India legal rule pack.
- A persisted draft-payslip view is not a legally published payslip/ESS document lifecycle.
- Current Sprint 0-4 stories are implementation slices, not replacements for the 18 original epics.
- Each original backlog row remains open until its complete acceptance summary is evidenced.

## 7. Current status

- Original epics: 18.
- Original backlog rows: 72.
- Current repository stories through S4-06B: 36.
- Sprints 0-4 functionally merged as a bounded vertical slice.
- S4-06A selected but not started; S4-06B planned but not authorised.
- V001-V030 immutable; V031 unreserved.
- Thread 6 inactive.

## 8. Required epic-to-story decomposition

`PLN-01 - Decompose E01-E18 into detailed source-linked stories`

The 18 epics are the approved product-capability structure. They are not yet a
complete executable delivery backlog.

After this publication is merged, Thread 1 must perform a separate controlled
planning activity that:

1. reads the detailed Iteration 1-12 documents;
2. decomposes each epic into business features and detailed user stories;
3. preserves the source iteration, rule, workflow, control, API/event, data,
   security, reporting and acceptance references;
4. maps already implemented Sprint 0-4 stories to the detailed source stories;
5. identifies missing, partial, superseded and legally revalidated stories;
6. assigns future sprint story IDs only after project-owner review.

This activity is required before selecting the next new product-feature sprint.
It does not reserve V031 or authorise implementation.
