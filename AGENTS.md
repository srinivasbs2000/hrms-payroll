# HRMS Payroll Repository Instructions

## Model and Agent Routing Policy

Use the lowest-cost agent capable of producing a complete and verified result.

### Available agents

Use `payroll_explorer` for bounded, read-only work such as:

* locating files, classes, methods, database objects, and tests
* tracing code paths and dependencies
* analysing repository structure
* reviewing documentation and OpenAPI definitions
* summarising logs and test output
* identifying likely files affected by a change

The explorer must not modify files or run an unnecessarily broad test suite.

Use `payroll_implementer` for:

* normal backend and frontend implementation
* Flyway migrations
* OpenAPI changes
* unit and integration tests
* documentation accompanying implementation
* straightforward build and CI corrections

Only one write-capable implementation agent may work on a given set of files at a time.

Use `payroll_critical_reviewer` after implementation when changes involve:

* salary, earnings, deductions, taxes, benefits, arrears, or retroactive processing
* currency, decimal precision, rounding, proration, or effective dating
* payroll-run state transitions, concurrency, retries, or idempotency
* PostgreSQL RLS or tenant isolation
* authentication, authorization, or privilege boundaries
* destructive, irreversible, or data-transforming migrations
* audit trails, statutory evidence, or financial data lineage
* payslip generation, approval, finalisation, reversal, or publication
* a material failure that remains unresolved after two focused implementation attempts

The critical reviewer is read-only and must review the completed diff rather than independently reimplementing the sprint.

### Escalation rules

1. Do not begin routine work with Sol merely because it is available.
2. Use `payroll_explorer` only when repository discovery is substantial enough to justify a separate context.
3. Use `payroll_implementer` for normal implementation.
4. Escalate to `payroll_critical_reviewer` only when a documented high-risk condition applies.
5. State the reason before escalating.
6. Do not run multiple write-capable agents against overlapping files concurrently.
7. Do not use Fast, Max, Ultra, or additional parallel agents unless explicitly requested.
8. Keep delegated work bounded and return concise findings rather than raw logs.
9. Passing tests is necessary but not sufficient for high-risk Payroll changes.
10. High-risk work requires an independent critical review before completion.

### Verification sequence

Run verification in the following order:

1. Compile or statically validate the affected module.
2. Run targeted unit tests.
3. Run targeted integration tests.
4. Run migration and tenant-isolation tests when applicable.
5. Run the required backend Maven verification.
6. Run frontend tests and the production build when affected.
7. Validate OpenAPI when contracts change.
8. Run required dependency and security checks.
9. Review the final diff.
10. Run an independent critical review for high-risk changes.

Do not repeatedly run the complete verification suite while a known targeted failure remains unresolved.

### Definition of done

A task is complete only when:

* the approved acceptance criteria are satisfied;
* the implementation is complete rather than illustrative;
* relevant targeted tests pass;
* required full verification passes;
* the final diff has been reviewed;
* tenant isolation, RLS, authorization, security, and audit controls remain intact;
* high-risk changes have completed independent critical review;
* assumptions and residual risks are documented;
* no unrelated changes are included; and
* no commit or merge has been performed unless explicitly requested.


## Scope and architecture

This repository is the organisation-to-draft-payslip vertical-slice baseline. It is a Java 21 Spring Boot modular monolith with a React 18 SPA, PostgreSQL, Flyway, and Keycloak/OIDC. Keep code grouped by payroll capability under `backend/`; the composition root is `backend/payroll-boot`. Only a module's public API may be consumed by another module. Do not add cross-module JPA relationships, repository access, or internal-package imports.

The approved starter scope is fixed monthly gross-to-net payroll with BASIC, HRA, SPECIAL_ALLOWANCE, calendar-day proration, immutable calculation results and a draft payslip. Do not add PF, EPS, EDLI, ESI, professional tax, labour welfare fund, NPS, salary TDS, tax declarations, retro, off-cycle payroll, recoveries, final settlement, banking, payments or accounting.

## Contract and domain constraints

`contracts/openapi/payroll-vertical-slice-openapi-v1.yaml` is the approved wire contract. Do not weaken or change it merely to make code or tests pass. Money uses `BigDecimal` in Java and decimal strings plus ISO currency codes at API boundaries; never use binary floating point. Effective ranges are half-open: `[effective_from, effective_to)`. Inject `Clock` for time-dependent behavior and keep calculation inputs deterministic and snapshot-based.

## Security and tenancy

Every tenant-owned table and relationship must be tenant-safe. Tenant-owned foreign keys include `tenant_id`; PostgreSQL row-level security is enabled and forced; the runtime role is a non-owner `NOBYPASSRLS` principal. Application transactions must set `app.tenant_id` with `SET LOCAL` before accessing tenant data. OIDC principals are identified by issuer plus subject, never email. Fail closed when tenant or audience claims are absent. Never log employee personal data, tokens, salaries or payroll response bodies, and never persist tokens or payroll payloads in browser storage.

## Database migrations

`database/flyway/sql` is the single source of ordered versioned migrations. Versioned migrations are immutable after review and must fail loudly; do not add permissive `IF NOT EXISTS` clauses to them. V001-V013 are frozen; Sprint 1 is forward-only from V014. The `backend/database-migrations` Maven module packages this canonical directory as `db/migration`. Administrator bootstrap, development seed and verification SQL remain separate. Application roles never own schemas or tables. Sealed input snapshots, payroll results, component results, calculation trace, draft payslips and audit rows are append-only.

Legal entities, payroll statutory units and establishments use stable identity rows plus exact effective-dated version rows. PSU versions reference exact legal-entity versions; establishment versions reference exact PSU versions. Approved ranges for one identity never overlap. Business attributes and superseded drafts are not rewritten; approval and end-dating use the narrow database lifecycle commands and always produce audit/outbox evidence in the same transaction.

Mutable payroll source inputs belong in domain-owned source or staging structures. Insert an immutable `input_snapshot` only after tenant, assignment, cycle, effective-date, required-component and canonical-payload validation succeeds in the sealing transaction. Corrections create a new sealed snapshot. Draft-payslip regeneration creates a new append-only version that supersedes the prior draft; never update a draft payslip in place.

## Testing and delivery

Use the Maven Wrapper and make `mvnw verify` the backend quality command. Every change must keep normal behavior, validation, boundary, rounding, date, tenancy and immutability checks deterministic. Run React tests and the production build, validate OpenAPI, and exercise clean database migration plus verification for integration changes. Never use real credentials, employee records or payroll data in source or tests; only clearly synthetic fixtures are permitted.

Before handoff, run `git status --short`, list verification performed, and disclose configuration, schema, security or unresolved impacts. Do not commit unless the user explicitly asks.

Before the first real domain event is published in Sprint 1, the outbox/inbox reliability entry gate must prove transactional outbox persistence, stable event identity, duplicate-dispatch safety and tenant-and-consumer-scoped inbox deduplication. No business feature may publish events before that gate passes. Once enabled, producers use the integrations module public `OutboxWriter`; consumers commit their inbox record and effect atomically. Retry, poison-message and replay policy is recorded in `docs/runbooks/event-reliability.md`.
