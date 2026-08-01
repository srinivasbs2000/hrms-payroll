# HRMS Payroll Master Design

**Status:** Living approved-design authority
**Repository:** `srinivasbs2000/hrms-payroll`
**Current verified repository base:** `main` at PR #20 merge `4b5da975eb851434957667bdecf138ea9b43f929`
**Current product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Last verified:** 1 August 2026
**Maintainers:** Project owner and the currently authorised implementation thread
**Companion state document:** `docs/runbooks/project-continuation-handoff.md`

## 1. Purpose and authority

This document is the repository-owned authority for the approved product scope,
architecture, cross-cutting design rules and long-lived decisions of the HRMS
Payroll system.

It is intentionally different from the running continuation handoff:

- this master design answers **what the system is and why it is designed this way**;
- the continuation handoff answers **where implementation currently stands and
  what may happen next**;
- ADRs provide the detailed rationale for individual architecture decisions;
- migrations, code, tests and OpenAPI provide executable implementation truth;
- immutable checkpoints record durable transitions but do not replace this
  document.

Conversation history is never the authoritative project record. A decision that
must survive a thread boundary must be recorded in the repository.

## 2. Source-of-truth hierarchy

Use this order whenever sources disagree:

1. Current local working tree and complete uncommitted diff.
2. Current remote branch, commit, pull request and CI evidence.
3. Committed migrations, code, tests, OpenAPI, ADRs, `AGENTS.md`, backlog and
   security policies.
4. This master design for approved product and architecture intent.
5. The running continuation handoff for current sequence, blockers and next
   authorised action.
6. Committed verification reports and immutable transition checkpoints.
7. Thread extracts and conversation summaries only as historical locators.

Do not silently reconcile a conflict. Record it as `DOCUMENTATION CONFLICT`,
identify the sources, and resolve it through an approved repository change.

A documentation or recovery failure must not silently narrow the original
approved deliverable. Before a phase is declared complete, reconcile the
original file/scope checklist against the actual committed result. Any omission
or deferral requires explicit project-owner approval.

## 3. Product objective

Build an India-first, enterprise-grade, multi-tenant payroll operating platform
that can function as an independently deployable payroll subsystem while
integrating with the wider HRMS.

The target product journey is:

`configuration -> approved inputs -> deterministic gross-to-net calculation ->
validation -> approval -> payment preparation -> statutory evidence ->
reconciliation -> correction -> audit`

The current repository is a bounded vertical slice, not the complete target
product.

## 4. Implemented scope through Sprint 4

### 4.1 Organisation and tenancy

- tenant context and PostgreSQL RLS;
- legal entities, payroll statutory units and establishments;
- stable identities plus immutable effective-dated versions;
- exact-version hierarchy and parent-range containment;
- approval, correction and controlled end-date lifecycle;
- audit and transactional outbox evidence.

### 4.2 Payroll configuration and employee payroll

- pay groups and deterministic monthly calendars/periods;
- pay-component catalogue;
- salary structures and immutable component lines;
- payroll relationships and assignments;
- employee payroll profiles;
- pay-group and salary assignments;
- permission-aware backend APIs and React workspaces.

### 4.3 Regular payroll execution

- fixed monthly BASIC, HRA and SPECIAL_ALLOWANCE;
- calendar-day proration;
- controlled payroll-cycle and population resolution;
- immutable sealed input snapshots;
- deterministic calculation requests, results, component results and trace;
- controlled recalculation preserving history;
- persisted draft payslip explicitly marked non-legal and not for payment.

### 4.4 Jurisdiction-neutral statutory evidence

- effective-dated statutory rule identities and versions;
- employee statutory profiles and exact assignments;
- deterministic statutory evaluation;
- append-only ledger posting and signed correction deltas;
- PTD/YTD balances, reconciliation and remittance-preparation evidence;
- secured APIs and permission-aware operator UI.

This foundation does not establish a legal India rule pack or authoritative
country-specific calculation.

## 5. Explicitly excluded until separately approved

The following remain outside the implemented baseline:

- jurisdiction-specific PF, EPS, EDLI, ESI, professional tax, labour welfare
  fund, NPS and salary-TDS rules;
- legal interpretation of statutory rates, thresholds, forms or filing duties;
- statutory return generation, submission, acknowledgements and remittance
  settlement;
- retroactive payroll and arrears processing;
- off-cycle and supplementary payroll;
- recoveries, salary advances and employee receivables;
- final settlement;
- banking, payment files/APIs and payment settlement;
- payroll accounting and general-ledger integration;
- legal/final payslip publication.

Each excluded area requires an approved design, authoritative source review,
effective-date model, complete test matrix and independent critical review.

## 6. Architecture

### 6.1 Composition

- Java 21 and Spring Boot modular monolith.
- Maven multi-module repository under `backend/`.
- `backend/payroll-boot` is the composition root.
- React 18, TypeScript and Vite frontend.
- PostgreSQL 17 with Flyway.
- Keycloak/OIDC for development identity.
- OpenAPI 3.1 contracts.

### 6.2 Module boundaries

A module may consume another module only through its public API.

Prohibited across modules:

- direct repository access;
- cross-module JPA relationships;
- imports from another module's `internal` packages;
- shared mutable database ownership without an approved boundary;
- bypassing application services to mutate another bounded context.

Spring Modulith and ArchUnit verification are mandatory boundary gates.

### 6.3 Core capability ownership

| Capability | Primary owner |
|---|---|
| Tenant transaction and shared platform contracts | `platform-core` |
| Authentication and authorization | `security` |
| Idempotency, outbox and integration reliability | `integrations` |
| Legal entity, PSU and establishment | `organisation` |
| Pay group, calendar, components and structures | configuration/compensation modules |
| Payroll relationship, assignment and profile | `employee-payroll` |
| Payroll-cycle orchestration | `payroll-operations` |
| Deterministic calculation and trace | `calculation-engine` |
| Statutory rules, evaluation and ledger | statutory bounded context |
| Draft-payslip/document views | `documents-reporting` |
| Runtime composition | `payroll-boot` |

The exact committed package structure remains implementation truth.

## 7. Data and migration model

### 7.1 Migration authority

`database/flyway/sql` is the canonical ordered migration directory.

- V001-V030 are committed and immutable.
- V031 is currently unreserved.
- Future schema work begins at V031 only after explicit reservation.
- Versioned migrations are forward-only and fail loudly.
- Do not add permissive `IF NOT EXISTS` clauses to hide drift.
- Bootstrap, development seed and verification SQL remain separate.
- Application roles do not own schemas or tables.

### 7.2 Stable identity and exact version

Long-lived business concepts use:

1. a stable identity row for enduring navigation and aggregate identity;
2. immutable effective-dated version rows for historical state;
3. exact version foreign keys wherever payroll lineage must prove the state used.

Legacy UUIDs are preserved as historical version identifiers where practical.
A new migration must document the old-to-new identity/version mapping.

### 7.3 Effective dating

- Ranges are half-open: `[effective_from, effective_to)`.
- Approved ranges for the same identity never overlap.
- Child versions must fit within the applicable exact parent-version range.
- Future-effective versions are the normal change mechanism.
- Backdated changes require impact assessment and controlled processing.
- Approved or consumed historical evidence is never rewritten.

### 7.4 Immutable evidence

The following are append-only after sealing or completion:

- input snapshots;
- payroll results and component results;
- calculation trace;
- draft-payslip versions;
- statutory inputs/evaluations;
- statutory ledger entries and correction deltas;
- balance snapshots;
- reconciliation and remittance summaries;
- audit and outbox evidence.

Corrections create new evidence linked to the prior evidence.

## 8. Security and tenancy

- Every tenant-owned table contains tenant ownership.
- Tenant-owned foreign keys include `tenant_id`.
- PostgreSQL RLS is enabled and forced.
- Runtime roles are non-owner and `NOBYPASSRLS`.
- Application transactions set `app.tenant_id` with `SET LOCAL`.
- Cross-tenant reads, writes and references must fail.
- OIDC principals are identified by issuer plus subject, never email.
- Missing tenant or audience claims fail closed.
- Tokens, employee personal data, salary data and payroll payloads must not be
  logged or persisted in browser storage.
- Authorization must align across Keycloak, backend, OpenAPI and UI controls.

## 9. Reliability and transaction rules

A business write that produces evidence commits atomically with:

- the aggregate change;
- idempotency result;
- audit record;
- outbox event.

Outbox/inbox rules:

- stable event identity;
- tenant and consumer scoped inbox deduplication;
- retry and poison-message handling;
- explicit dead-letter/replay policy;
- no producer may bypass the public integrations contract.

## 10. API and error rules

- OpenAPI contracts are wire-contract authorities.
- Writes use an `Idempotency-Key` where retry duplication is possible.
- Calls propagate `X-Correlation-ID`.
- Optimistic lifecycle changes use ETag/`If-Match`.
- Errors use RFC 9457 problem details.
- Money uses decimal strings plus ISO currency codes at API boundaries.
- Java money uses `BigDecimal`; binary floating point is prohibited.
- Error details must not leak cross-tenant existence or sensitive values.

## 11. Deterministic calculation rules

The same immutable employee, configuration and input snapshots plus the same
engine version must reproduce the same result and result hash.

- Inject `Clock`; do not consult uncontrolled current time.
- Do not depend on database retrieval order or worker execution order.
- Retain formula/rule version, input source, intermediate values, rounding and
  result trace.
- Calculation workers are stateless.
- Employee-level failure must not silently corrupt other employees.
- Missing required data produces an explicit default, warning or blocker.

## 12. Delivery and quality gates

The canonical verification order is:

1. static validation/compile of affected modules;
2. focused unit tests;
3. focused integration tests;
4. migration, RLS and tenant-isolation tests when applicable;
5. backend Maven `verify`;
6. frontend lint, focused/full tests and production build when affected;
7. OpenAPI validation when contracts change;
8. dependency, secret and security checks;
9. final diff review;
10. independent critical review for high-risk payroll work.

`mvn test` is not sufficient because Failsafe integration tests may be skipped.

A change is done only when the acceptance criteria, targeted and full
verification, security/tenancy controls, final diff review, documented residual
risks and required critical review are complete.

### 12.1 Sprint 4 closure qualification

Sprint 4 is functionally implemented and merged. It is not described as fully
automated because the repository does not yet contain:

- a real secured Spring Boot HTTP/PostgreSQL statutory integration test covering
  the controller-to-database path; or
- a statutory-specific Playwright browser scenario.

S4-06A is the next selected quality increment and must close the statutory API
integration gap without reserving a migration. S4-06B remains planned but is
not authorised.

The unsigned `docs/runbooks/sprint-4-manual-smoke.md` template is not evidence
that a live manual smoke was completed.

## 13. Sprint baseline ledger

| Sprint | Durable outcome | Migration range | Repository status |
|---|---|---|---|
| Sprint 0 | Repository, security, tenancy, migration and vertical-slice baseline | V001-V013 | Merged |
| Sprint 1 | Organisation lifecycle, event reliability, audit and architecture boundaries | V014-V016 | Merged |
| Sprint 2 | Payroll configuration and employee payroll foundation | V017-V022 | Merged through PR #3 |
| Sprint 3 | Regular payroll execution, sealed inputs, calculation evidence and draft payslip | V023-V026 | Merged through PR #18 |
| Sprint 4 | Jurisdiction-neutral statutory lifecycle, evaluation and ledger evidence | V027-V030 | Merged through PR #19; automation debt tracked as S4-06A/S4-06B |

Migration descriptions and exact implementation files remain the executable
source of truth.

## 14. Documentation model

| Document | Responsibility | Update trigger |
|---|---|---|
| This master design | Approved product scope and long-lived architecture | Approved scope/architecture decision or durable implemented capability |
| `docs/design/decision-register.md` | Decision index and status | Every approved material decision, supersession or conflict |
| `docs/runbooks/project-continuation-handoff.md` | Current branch/PR/CI/worktree state and next action | Every committed increment and every thread transition |
| `docs/governance/thread-registry.md` | Thread ownership, active scope and checkpoint links | Thread start, ownership change and thread close |
| ADRs | Detailed architecture rationale | New or materially changed architecture choice |
| Backlog | Approved implementation sequencing | Scope, estimate or acceptance-criteria change |
| Quality/closure reports | Verification evidence | Durable phase or sprint closure |
| Thread extracts | Historical evidence recovery only | Completed historical extraction |

## 15. Master-design update rules

Update this document only when one of these occurs:

- approved product scope changes;
- architecture or module ownership changes;
- a cross-cutting security, tenancy, data or reliability rule changes;
- a formerly excluded capability becomes implemented;
- an implemented capability is superseded;
- a migration baseline advances after merge;
- a material documentation conflict is resolved.

Do not update it for:

- routine bug fixes;
- local retries;
- temporary diagnostics;
- unapproved ideas;
- work not yet committed and verified.

Every change must include:

- date;
- affected section;
- decision/register reference;
- implementation or evidence link;
- whether the change is approved, implemented or only planned.

## 16. Open decisions and controlled debt

At this baseline:

- S4-06A Statutory API Integration Closure is the next selected implementation
  increment after the documentation restart is merged and separately authorised;
- S4-06B statutory-specific Playwright E2E is planned but not authorised;
- jurisdiction-specific legal rule packs require separate approval and research;
- production broker operations, replay and alerting remain operational debt;
- cached/scheduled OWASP Dependency Check data remains follow-up work;
- V031 remains unreserved;
- multiple project threads must coordinate through the thread registry and one
  active write owner.

## 17. Change history

| Date | Change | Evidence |
|---|---|---|
| 1 Aug 2026 | Initial repository-owned living master design, seeded from merged Sprint 0-4 state and recovered Thread 1 decisions | PR #20 merge `4b5da975...`; PR #3, PR #18 and PR #19 |
| 1 Aug 2026 | Cross-thread restart reconciliation: separated repository and product baselines, qualified Sprint 4 automation closure, selected S4-06A, retained S4-06B as unauthorised, and added completion-control discipline | Phase A documentation branch `docs/cross-thread-reconciliation`; MDR-021 through MDR-027 |
