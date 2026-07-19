# HRMS Payroll Repository Instructions

## Scope and architecture

This repository is the organisation-to-draft-payslip vertical-slice baseline. It is a Java 21 Spring Boot modular monolith with a React 18 SPA, PostgreSQL, Flyway, and Keycloak/OIDC. Keep code grouped by payroll capability under `backend/`; the composition root is `backend/payroll-boot`. Only a module's public API may be consumed by another module. Do not add cross-module JPA relationships, repository access, or internal-package imports.

The approved starter scope is fixed monthly gross-to-net payroll with BASIC, HRA, SPECIAL_ALLOWANCE, calendar-day proration, immutable calculation results and a draft payslip. Do not add PF, EPS, EDLI, ESI, professional tax, labour welfare fund, NPS, salary TDS, tax declarations, retro, off-cycle payroll, recoveries, final settlement, banking, payments or accounting.

## Contract and domain constraints

`contracts/openapi/payroll-vertical-slice-openapi-v1.yaml` is the approved wire contract. Do not weaken or change it merely to make code or tests pass. Money uses `BigDecimal` in Java and decimal strings plus ISO currency codes at API boundaries; never use binary floating point. Effective ranges are half-open: `[effective_from, effective_to)`. Inject `Clock` for time-dependent behavior and keep calculation inputs deterministic and snapshot-based.

## Security and tenancy

Every tenant-owned table and relationship must be tenant-safe. Tenant-owned foreign keys include `tenant_id`; PostgreSQL row-level security is enabled and forced; the runtime role is a non-owner `NOBYPASSRLS` principal. Application transactions must set `app.tenant_id` with `SET LOCAL` before accessing tenant data. OIDC principals are identified by issuer plus subject, never email. Fail closed when tenant or audience claims are absent. Never log employee personal data, tokens, salaries or payroll response bodies, and never persist tokens or payroll payloads in browser storage.

## Database migrations

`database/flyway/sql` is the single source of ordered versioned migrations. Versioned migrations are immutable after review and must fail loudly; do not add permissive `IF NOT EXISTS` clauses to them. V001-V012 are frozen; Sprint 0A hardening is forward-only in V013. The `backend/database-migrations` Maven module packages this canonical directory as `db/migration`. Administrator bootstrap, development seed and verification SQL remain separate. Application roles never own schemas or tables. Sealed input snapshots, payroll results, component results, calculation trace, draft payslips and audit rows are append-only.

Mutable payroll source inputs belong in domain-owned source or staging structures. Insert an immutable `input_snapshot` only after tenant, assignment, cycle, effective-date, required-component and canonical-payload validation succeeds in the sealing transaction. Corrections create a new sealed snapshot. Draft-payslip regeneration creates a new append-only version that supersedes the prior draft; never update a draft payslip in place.

## Testing and delivery

Use the Maven Wrapper and make `mvnw verify` the backend quality command. Every change must keep normal behavior, validation, boundary, rounding, date, tenancy and immutability checks deterministic. Run React tests and the production build, validate OpenAPI, and exercise clean database migration plus verification for integration changes. Never use real credentials, employee records or payroll data in source or tests; only clearly synthetic fixtures are permitted.

Before handoff, run `git status --short`, list verification performed, and disclose configuration, schema, security or unresolved impacts. Do not commit unless the user explicitly asks.

Before the first real domain event is published in Sprint 1, the outbox/inbox reliability entry gate must prove transactional outbox persistence, stable event identity, duplicate-dispatch safety and tenant-and-consumer-scoped inbox deduplication. No business feature may publish events before that gate passes.
