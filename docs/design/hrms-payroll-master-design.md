# HRMS Payroll Master Design

**Status:** Living approved-design and architecture authority
**Repository:** `srinivasbs2000/hrms-payroll`
**Publication source baseline:** `main` at PR #21 merge `18d5ca3554ff217140b7e3c443d086d63bd02070`
**Current product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Last verified:** 1 August 2026
**Maintainers:** Project owner and the currently authorised project thread
**Full product scope authority:** `docs/product/payroll-product-scope-and-epic-catalog.md`
**Companion state document:** `docs/runbooks/project-continuation-handoff.md`

## 1. Purpose and authority

This document owns approved architecture, cross-cutting design rules and the
relationship between the complete Payroll product scope and the implemented
repository baseline.

The complete product capability and epic authority is:

`docs/product/payroll-product-scope-and-epic-catalog.md`

The machine-readable source backlog and delivery lineage are:

- `backlog/payroll-master-implementation-backlog.csv`
- `docs/governance/payroll-feature-delivery-lineage.md`

The current repository is a bounded vertical slice and must not be read as the
complete Payroll product.

## 2. Source-of-truth hierarchy

1. current local working tree and complete uncommitted diff;
2. live remote branch, commit, pull request and CI evidence;
3. committed migrations, code, tests, OpenAPI, security policy and `AGENTS.md`;
4. full product scope/epic catalog and source register;
5. this master design and decision register;
6. continuation handoff and thread registry;
7. quality, closure and reconciliation records;
8. conversation history only as a locator.

Record disagreements as `DOCUMENTATION CONFLICT`. Never silently reduce the
original approved product scope.

## 3. Complete product objective

Build an India-first, enterprise-grade, multi-tenant Payroll operating
platform that can operate independently and integrate with the wider HRMS.

End-to-end product journey:

`configuration -> onboarding/readiness -> inputs -> calculation -> validation -> approval -> payments -> statutory processing -> accounting -> documents/reporting -> reconciliation -> corrections -> audit -> migration/cutover -> BAU`

The complete product contains 18 epics across original releases R1-R4.

## 4. Implemented scope through Sprint 4

### 4.1 Organisation and tenancy

- tenant context and PostgreSQL RLS;
- legal entities, payroll statutory units and establishments;
- stable identities and immutable effective-dated versions;
- hierarchy, lifecycle, audit and transactional outbox evidence.

### 4.2 Payroll configuration and employee payroll

- pay groups and deterministic monthly calendars/periods;
- starter BASIC, HRA and SPECIAL_ALLOWANCE catalog;
- starter salary structures and immutable component lines;
- payroll relationships, assignments and employee payroll profiles;
- pay-group and salary assignments;
- permission-aware APIs and React workspaces.

### 4.3 Regular payroll execution

- fixed monthly starter calculation;
- calendar-day proration;
- controlled cycle and population resolution;
- immutable input snapshots;
- deterministic result, component and trace evidence;
- controlled recalculation;
- non-legal persisted draft-payslip view.

### 4.4 Jurisdiction-neutral statutory evidence

- effective-dated generic statutory rules;
- employee statutory profiles and exact assignments;
- deterministic evaluation;
- append-only ledger, signed corrections and balances;
- reconciliation and remittance-preparation evidence;
- secured APIs and operator workspace.

This does not establish an India legal rule pack.

## 5. Full-product implementation qualification

- 11 epics are `PARTIALLY IMPLEMENTED`;
- 6 epics are `NOT STARTED`;
- E09 is `REQUIRES LEGAL OR DOMAIN REVALIDATION`;
- no original epic is fully implemented against all source acceptance.

This prevents bounded Sprint slices from being misrepresented as complete
product epics.

## 6. Material remaining capabilities

- complete jurisdiction and registration foundation;
- general components, named bases, CTC and salary structures;
- complete bank/statutory/tax readiness;
- general formula and balance engines;
- complete input, cut-off, trial, approval, lock and release operations;
- current India statutory and salary-tax rule packs;
- retro, arrears, off-cycle, recoveries and final settlement;
- payments and banking;
- accounting, costing and ERP integration;
- legal documents, ESS, reporting, analytics and communications;
- complete SoD, access governance, retention, BCP, performance and DR;
- data migration, parallel Payroll, cutover and hypercare.

## 7. Architecture

- Java 21 and Spring Boot modular monolith.
- Maven multi-module repository under `backend/`.
- `backend/payroll-boot` is the composition root.
- React 18, TypeScript and Vite frontend.
- PostgreSQL 17 with Flyway.
- Keycloak/OIDC development identity.
- OpenAPI 3.1 contracts.

Direct cross-module repository access, internal-package imports and
unapproved shared ownership are prohibited. Spring Modulith and ArchUnit
remain mandatory.

## 8. Data and migration rules

- `database/flyway/sql` is the ordered migration authority.
- V001-V030 are immutable.
- V031 is unreserved.
- future migrations are forward-only and separately authorised;
- tenant-owned FKs include tenant ownership;
- stable identity plus immutable effective-dated versions preserve lineage;
- consumed evidence is never rewritten;
- the original 112-table DDL is a logical design source, not a migration to
  apply directly.

## 9. Security, reliability and API rules

- ENABLE/FORCE RLS for tenant-owned tables;
- runtime roles are non-owner and `NOBYPASSRLS`;
- transactions use `SET LOCAL`;
- identity is issuer plus subject;
- aggregate, idempotency, audit and outbox commit atomically;
- money uses `BigDecimal` and decimal strings;
- writes use idempotency keys where needed;
- optimistic changes use ETag/`If-Match`;
- errors use RFC 9457 and do not leak sensitive existence.

## 10. Deterministic calculation and evidence rules

The same immutable employee/configuration/input evidence and engine version
must reproduce the same result and hash.

Inject `Clock`, retain exact lineage and trace, avoid ordering dependence and
append corrections rather than mutating history.

## 11. Delivery and quality gates

1. static validation/compile;
2. focused unit tests;
3. focused integration tests;
4. migration/RLS/tenant tests when applicable;
5. full Maven `verify`;
6. frontend lint, tests and build when applicable;
7. OpenAPI validation when contracts change;
8. dependency, secret and security checks;
9. planned-versus-actual file review;
10. independent critical review for high-risk Payroll work.

A story is complete only when acceptance, verification, lineage and residual
risk are committed.

## 12. Sprint baseline ledger

| Sprint | Durable outcome | Migration range | Status |
|---|---|---|---|
| Sprint 0 | Repository/security/tenancy baseline | V001-V013 | Merged |
| Sprint 1 | Organisation, reliability, audit and architecture | V014-V016 | PR #2 merged |
| Sprint 2 | Configuration and employee-payroll foundation | V017-V022 | PR #3 merged |
| Sprint 3 | Regular execution and deterministic evidence | V023-V026 | PR #18 merged |
| Sprint 4 | Generic statutory lifecycle and evidence | V027-V030 | PR #19 merged; quality debt remains |
| Governance | Living design and reconciliation controls | None | PR #20 and PR #21 merged |

## 13. Current controlled debt and planning

- S4-06A is selected but not started.
- S4-06B is planned but not authorised.
- `PLN-01` epic-to-detailed-story decomposition is required after this
  publication and before the next new product-feature sprint.
- E09 requires current legal/domain revalidation.
- V031 remains unreserved.
- Thread 6 remains inactive.

## 14. Documentation model

| Artifact | Responsibility |
|---|---|
| Product scope and epic catalog | Complete intended capability boundary |
| Master backlog | Original 72-row control list and evidence classification |
| Feature delivery lineage | Story/migration/commit/PR/closure mapping |
| Gap assessment | Implemented, partial, missing and revalidation baseline |
| Roadmap | Controlled future sequence and planning gates |
| Master design | Architecture and product-to-implementation relationship |
| Decision register | Stable material decisions |
| Handoff | Current state and next authorised action |
| Thread registry | Ownership and write boundaries |

## 15. Change history

| Date | Change | Evidence |
|---|---|---|
| 1 Aug 2026 | Initial living master design | PR #20 |
| 1 Aug 2026 | Cross-thread completion controls | PR #21 `18d5ca3554ff217140b7e3c443d086d63bd02070` |
| 1 Aug 2026 | Recovered 18-epic/72-row product baseline and recorded PLN-01 | Full-product reconciliation; MDR-028-MDR-033 |
