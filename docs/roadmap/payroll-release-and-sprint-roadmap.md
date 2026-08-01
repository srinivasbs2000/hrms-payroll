# Payroll Release and Sprint Roadmap

**Baseline:** `18d5ca3554ff217140b7e3c443d086d63bd02070`
**Status:** Approved planning authority; proposed product waves remain unauthorised

## 1. Roadmap principles

- Preserve the original R1-R4 release intent.
- Close foundation gaps before claiming a production-ready payroll core.
- Treat legal India localisation as a separately revalidated program.
- Keep quality closure distinct from new product scope.
- Do not reserve V031 until a specifically approved story requires schema change.
- Use stable source story IDs (`E01-01` through `E18-04`) and map future sprint stories back to them.

## 2. Proposed reconciled sequence

| Wave | Theme | Indicative Timing | Scope | Dependency | Exit Condition | Status |
|---|---|---|---|---|---|---|
| G0 | Documentation authority and lineage closure | Current / pre-implementation | Approve and repository-publish the full scope, source register, master backlog, lineage, gap assessment and roadmap; refresh stale main/Thread status. | No product code or migration. | Draft artifacts reviewed; exact repository allow-list separately authorised. | NOT AUTHORISED |
| Q1 | S4-06A statutory API integration closure | Quality gate before new feature | Real secured Spring Boot HTTP-to-PostgreSQL integration coverage for the existing generic statutory foundation. | NONE; no production code initially. | S4-05A controller-to-database path, RLS, idempotency, concurrency, money, audit and outbox proven. | SELECTED / PAUSED |
| Q2 | S4-06B statutory browser E2E | Optional quality gate | Dedicated Playwright scenario for the existing generic statutory workspace. | Depends on Q1 and separate authorisation. | Authenticated evaluation, posting, correction, evidence and browser-storage safety proven. | PLANNED |
| P5 | R1 foundation completion | Proposed future Sprint 5 | Close E01-E05 gaps: registrations/jurisdictions, complete calendars/pay groups, general component catalogue/named bases, CTC/simulation, bank/statutory/tax readiness. | Requires approved story decomposition; V031 decision remains separate. | R1 configuration and payroll-readiness acceptance satisfied end-to-end. | PROPOSED |
| P6 | Calculation and regular-operations completion | Proposed future Sprint 6 | General formula engine, balances framework, input windows, cut-offs, certification, trial/approval/lock/release and resumable execution. | Depends on P5. | E06/E08 full design acceptance; non-legal golden payroll extended beyond starter components. | PROPOSED |
| P7 | India statutory and salary-tax rule pack | Proposed future Sprint 7+ | Implement E09 only after current legal and domain revalidation of Iteration 6. | Legal research, source register, effective-dated rule model and independent review required. | Named Indian obligations calculate and reconcile against authoritative test cases. | PROPOSED / REVALIDATION REQUIRED |
| P8 | Balances, retro, off-cycle and final settlement | Proposed future Sprint 8+ | Complete E07 and E10: balance movements, retro, arrears, corrections, off-cycle, recoveries and final settlement. | Depends on P6 and relevant P7 rules. | History remains immutable; legal deadline clocks and settlement controls pass. | PROPOSED |
| P9 | Payments and banking | Proposed future Sprint 9+ | Implement E11 payment entitlement, instructions, routing, batches, approvals, transmission and bank reconciliation. | Depends on approved payroll/final settlement results. | Payroll control totals reconcile to accepted, credited and returned outcomes. | PROPOSED |
| P10 | Accounting and ERP | Proposed future Sprint 10+ | Implement E12 costing, account determination, journals, accruals, reversals and ERP reconciliation. | Depends on stable calculation/payment events. | Balanced, idempotent journals reconcile with drill-back to payroll evidence. | PROPOSED |
| P11 | Legal documents, ESS, reporting and communications | Proposed future Sprint 11+ | Complete E13-E14: published payslips/documents, ESS, certificates, reports, analytics and secure communications. | Depends on legal result/publication and correction states. | Immutable versioned documents and reconciled reports are securely distributed. | PROPOSED |
| P12 | Audit, security, performance and resilience hardening | Cross-release / proposed Sprint 12+ | Complete E15/E18: SoD, access governance, PAM, retention, incident/BCP, performance, partitioning, observability and DR. | Runs throughout P5-P11 with final production gate. | Control testing, 100k+ scale evidence and RTO/RPO exercises pass. | PROPOSED |
| P13 | Migration and parallel payroll | Proposed future Sprint 13+ | Implement E16 source migration, profiling, mappings, opening balances and parallel payroll. | Target product scope must be stable. | Zero-tolerance fields and approved parallel differences reconcile. | PROPOSED |
| P14 | Cutover, go-live and hypercare | Proposed future Sprint 14+ | Implement E17 rehearsed cutover, go/no-go, rollback, first production payroll, hypercare and handover. | Depends on P13 acceptance and production readiness. | Go-live acceptance and hypercare exit criteria approved. | PROPOSED |

## 3. Story-planning rule

Before any proposed product wave becomes an authorised sprint:

1. select exact original source rows;
2. revalidate detailed design and legal/domain sources where required;
3. assign new sprint story IDs while retaining original source IDs;
4. define acceptance criteria, file allow-list and migration reservation/`NONE`;
5. define API, event, security, audit and automated-test acceptance;
6. obtain independent critical review for payroll/legal/high-risk changes;
7. update the delivery lineage before implementation starts.

## 4. Immediate recommendation

Do not activate Thread 6 or begin S4-06A until the project owner reviews these
draft authorities and separately decides whether to publish them first. S4-06A
remains the selected quality increment, but it is paused by this reconciliation.

## 5. Deferred planning activity: epic-to-detailed-story breakdown

The next product-planning activity after this publication is:

`PLN-01 - Decompose E01-E18 into detailed source-linked stories`

PLN-01 must use Iterations 1-12 as the detailed requirements source and the
72-row original backlog as the capability-level control list. Its output must
include:

- business feature groups under each epic;
- detailed user stories and acceptance criteria;
- workflow and actor references;
- data/table and API/event references;
- security, audit, reporting and automated-test stories;
- dependencies and proposed sprint sequencing;
- current Sprint 0-4 mapping;
- gap classification and legal/domain revalidation flags.

PLN-01 is not implementation and does not reserve V031. Future product-feature
sprints must not be selected from the 18-epic catalog without this
decomposition and project-owner review.
