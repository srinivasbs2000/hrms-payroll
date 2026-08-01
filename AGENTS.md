# HRMS Payroll Repository Instructions

## Project continuation and evidence discipline

Before continuing a prior design or implementation session, read
`docs/runbooks/project-continuation-handoff.md`, then validate it against the
current local working tree and live GitHub branch, pull request and CI state.

Do not reconstruct repository state from conversation memory. Do not infer
filenames, migrations, routes, permissions, statuses, test results, dependency
policy or architecture decisions. Classify material statements as verified,
derived or not verified. Record source conflicts explicitly and ask before
choosing among materially different valid options.

Current repository evidence overrides an older handoff entry. Update the
running handoff after every committed increment and before every thread or
session transition.

## Scope-completion and recovery discipline

Keep an explicit checklist of the original approved deliverables, files,
acceptance criteria and prohibited actions for every multi-step increment.

When an error, recovery action or tooling workaround occurs:

1. fix only the bounded failure;
2. retain the original approved checklist;
3. record any changed approach without silently changing the intended outcome;
4. compare planned scope with actual changed/staged/committed scope before
   declaring completion;
5. list every omitted or deferred item; and
6. obtain explicit project-owner approval for each deferral.

A phase, sprint, pull request or thread transition must not be called complete
while planned items are silently omitted. Recovery convenience does not
override the approved business objective.

After two failures from custom repository-update automation, stop extending the
custom updater. Prefer standard tools, deterministic complete-file payloads and
small verifiable commands.

## PowerShell and repository-update safety

Generated PowerShell must not assume command output shape.

- Capture output with `@(...)`.
- Validate zero, one or multiple results explicitly.
- Cast the selected value to `[string]` before calling string methods.
- Do not index `[0]` on an expression that may collapse to a scalar string.
- Check `$LASTEXITCODE` for native commands.
- Do not interpret harmless warning text on stderr as command failure when the
  process exit code is zero.
- Parser checks do not replace runtime checks.

Repository update automation must not depend on exact full-line or
full-paragraph literal preimages in living Markdown files. Prefer, in order:

1. standard Git operations;
2. deterministic complete-file payloads with SHA-256 manifests;
3. marker-bounded or uniquely headed sections with uniqueness validation.

Normalize line endings for comparison, preserve the repository's intended text
encoding and run `git diff --check`. Repository text files must be valid UTF-8
and must not contain mojibake.

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

* the original approved scope checklist is reconciled with the actual result;
* every omission or deferral is explicitly approved;
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

This repository is the organisation-to-statutory-evidence vertical-slice
baseline through Sprint 4. It is a Java 21 Spring Boot modular monolith with a
React 18 SPA, PostgreSQL, Flyway, and Keycloak/OIDC. Keep code grouped by
payroll capability under `backend/`; the composition root is
`backend/payroll-boot`. Only a module's public API may be consumed by another
module. Do not add cross-module JPA relationships, repository access, or
internal-package imports.

The approved implemented regular-payroll scope is fixed monthly gross-to-net
payroll with BASIC, HRA, SPECIAL_ALLOWANCE, calendar-day proration, immutable
calculation evidence and a draft payslip.

The approved implemented statutory scope is jurisdiction-neutral rule,
profile, assignment, evaluation, ledger, balance, reconciliation, remittance
preparation, API and operator-workspace infrastructure. It does not establish
country-specific rates, legal tax interpretation, statutory filing/returns,
acknowledgements, remittance payment/settlement or legal payslip obligations.

Do not add jurisdiction-specific PF, EPS, EDLI, ESI, professional tax, labour
welfare fund, NPS, salary TDS or tax-declaration logic without a separately
approved design, authoritative legal source review, effective-date model,
test matrix and critical review. Retro payroll, off-cycle payroll, recoveries,
final settlement, banking, payments, accounting and legal/final payslip
publication also remain excluded unless separately approved.

## Contract and domain constraints

`contracts/openapi/payroll-vertical-slice-openapi-v1.yaml` is the aggregate
wire contract. `contracts/openapi/statutory-deductions-openapi-v1.yaml` is the
statutory bounded-context contract. Do not weaken or change either merely to
make code or tests pass.

Money uses `BigDecimal` in Java and decimal strings plus ISO currency codes at
API boundaries; never use binary floating point. Effective ranges are
half-open: `[effective_from, effective_to)`. Inject `Clock` for time-dependent
behavior and keep calculation and statutory inputs deterministic and
snapshot-based.

## Security and tenancy

Every tenant-owned table and relationship must be tenant-safe. Tenant-owned
foreign keys include `tenant_id`; PostgreSQL row-level security is enabled and
forced; the runtime role is a non-owner `NOBYPASSRLS` principal. Application
transactions must set `app.tenant_id` with `SET LOCAL` before accessing tenant
data. OIDC principals are identified by issuer plus subject, never email. Fail
closed when tenant or audience claims are absent. Never log employee personal
data, tokens, salaries or payroll response bodies, and never persist tokens or
payroll payloads in browser storage.

## Database migrations

`database/flyway/sql` is the single source of ordered versioned migrations.
V001-V030 are committed and immutable. V031 remains unreserved. Future schema
work is forward-only from V031 after explicit reservation. Versioned migrations
must fail loudly; do not add permissive `IF NOT EXISTS` clauses to them. The
`backend/database-migrations` Maven module packages the canonical directory as
`db/migration`. Administrator bootstrap, development seed and verification SQL
remain separate. Application roles never own schemas or tables. Sealed input
snapshots, payroll results, component results, calculation trace, draft
payslips, statutory inputs/results, ledger entries, balance snapshots,
reconciliation, remittance summaries and audit rows are append-only.

Legal entities, payroll statutory units and establishments use stable identity
rows plus exact effective-dated version rows. PSU versions reference exact
legal-entity versions; establishment versions reference exact PSU versions.
Approved ranges for one identity never overlap. Business attributes and
superseded drafts are not rewritten; approval and end-dating use the narrow
database lifecycle commands and always produce audit/outbox evidence in the
same transaction.

Mutable payroll source inputs belong in domain-owned source or staging
structures. Insert an immutable input snapshot only after tenant, assignment,
cycle, effective-date, required-component and canonical-payload validation
succeeds in the sealing transaction. Corrections create new immutable evidence.
Draft-payslip regeneration creates a new append-only version that supersedes
the prior draft; never update a draft payslip in place. Statutory corrections
append signed ledger deltas and never rewrite prior postings.

## Testing and delivery

Use the Maven Wrapper and make `mvnw verify` the backend quality command. Every
change must keep normal behavior, validation, boundary, rounding, date,
tenancy, idempotency and immutability checks deterministic. Run React tests and
the production build, validate relevant OpenAPI contracts, exercise clean
database migration plus verification for integration changes and enforce the
repository scoped npm-audit policy. Never use real credentials, employee
records or payroll data in source or tests; only clearly synthetic fixtures are
permitted.

For the full Sprint 4 baseline, run `scripts/verify-sprint-4.ps1`.

`docs/runbooks/sprint-4-manual-smoke.md` is an unsigned historical checklist.
Do not claim it proves a completed live smoke. S4-06A must close the real
statutory API integration-test gap before the next feature increment. S4-06B is
a planned statutory-specific Playwright follow-up and is not authorised.

Before handoff, run `git status --short`, list verification performed, compare
the original scope checklist with the actual changed files, and disclose
configuration, schema, security, deferred or unresolved impacts. Do not commit
unless the user explicitly asks.

Before the first real domain event is published in Sprint 1, the outbox/inbox
reliability entry gate must prove transactional outbox persistence, stable
event identity, duplicate-dispatch safety and tenant-and-consumer-scoped inbox
deduplication. No business feature may publish events before that gate passes.
Once enabled, producers use the integrations module public `OutboxWriter`;
consumers commit their inbox record and effect atomically. Retry,
poison-message and replay policy is recorded in
`docs/runbooks/event-reliability.md`.

<!-- LIVING-PROJECT-AUTHORITY:START -->
## Living project authority and multi-thread maintenance

For every HRMS Payroll thread or implementation session, read these files after
this repository instruction file:

1. `docs/design/hrms-payroll-master-design.md`
2. `docs/design/decision-register.md`
3. `docs/runbooks/project-continuation-handoff.md`
4. `docs/governance/thread-registry.md`
5. `docs/governance/thread-maintenance-protocol.md`

The master design owns approved product scope and long-lived architecture. The
running handoff owns current repository state and the next authorised action.
The thread registry owns write coordination across parallel threads.

Before writing, register the thread role, bounded scope, branch/PR, exact file
allow-list and migration reservation. Only one thread or write-capable agent may
own overlapping files or the next migration number at a time.

Update the running handoff and thread registry before every thread transition.
Update the master design and decision register only when their documented
triggers are met. A durable decision that exists only in chat is not project
authority.
<!-- LIVING-PROJECT-AUTHORITY:END -->
