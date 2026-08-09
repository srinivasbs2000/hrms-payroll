# HRMS Payroll Repository Instructions

## Project continuation and evidence discipline

Before continuing any design or implementation session, read
`docs/governance/payroll-program-status.md` first, then
`docs/runbooks/project-continuation-handoff.md`. Validate both against the
current local working tree and live GitHub branch, pull request and CI state.

Do not reconstruct repository state from conversation memory. Do not infer
filenames, migrations, routes, permissions, statuses, test results, dependency
policy or architecture decisions. Classify material statements as verified,
derived or not verified. Record source conflicts explicitly and ask before
choosing among materially different valid options.

Current repository evidence overrides an older handoff entry. Update the
running handoff after every committed increment and before every thread or
session transition.

## Mandatory assistant GitHub read-only boundary

For this project, assistant and agent GitHub access is strictly read-only even
when a connector advertises write-capable operations. Never attempt to create or
update branches, refs, blobs, trees, commits, repository files, pull requests,
reviews, comments, labels, workflow runs, auto-merge, or merges through a
connected GitHub tool.

When GitHub state must change, prepare a deterministic local package for the
project owner to execute with the owner's authenticated `git`/`gh` environment,
then verify the resulting remote state through read-only GitHub inspection and
returned evidence. Do not spend time attempting connector mutations first.

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
- Read native success or failure from the launched process object's
  `ExitCode`; `$LASTEXITCODE` is not authoritative for controlled gates.
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

## Standing non-Codex execution and response norm

HRMS Payroll work defaults to deterministic downloadable payloads and local
PowerShell execution. Do not invoke, require, recommend or install Codex CLI,
Codex desktop, Codex IDE extensions, Codex cloud tasks or API-key-backed Codex
execution unless the project owner gives explicit, task-specific override
authorization. Generic approvals such as `Approved`, `Proceed` or `Start
implementation` do not authorize Codex.

Downloaded packages are assumed to be under `$HOME\Downloads`. Generated
scripts default to `C:\dev\hrms-payroll`, accept `-RepoRoot` as an alias for
`-RepositoryPath`, resolve companion files through `$PSScriptRoot`, quote all
Windows paths and support profile names containing spaces.

Every HRMS Payroll response ends with `What you need to do now` and `What I
need from you`. Every downloadable artifact must state whether it is executable,
reference-only, evidence, a checkpoint or superseded; whether to download,
extract, execute, retain, archive, ignore or delete it; the exact command;
expected output; and the evidence to return. See
`docs/governance/hrms-payroll-execution-norm.md`.

Every generated PowerShell script, including rollback scripts, must pass
`[System.Management.Automation.Language.Parser]::ParseFile(...)` before
execution. Package commands must run `scripts/Test-PowerShellScript.ps1` first
and fail closed if validation is skipped or fails. Delimiter counting is not
parser validation. Avoid ambiguous interpolation such as `$Path:`; use
`${Path}:` or `$($Path):`.

Native-command wrapper functions must preserve zero/one/many output cardinality.
Do not return captured native output with a unary comma (`return ,$output`). Emit
flat strings, capture variable output as `[string[]] @(…)`, and execute semantic
empty/single/multiple cardinality checks before repository writes.
### Native process stream separation

When Git or another native command produces data that will be parsed, capture
stdout, stderr and exit code separately. Never merge `2>&1` into changed-path,
branch, SHA, migration or allow-list comparisons. Semantic package tests must
prove that stderr warnings cannot enter stdout data collections.

When an authorized payload is already applied and only a post-application
validation false positive occurs, preserve it and use a bounded resume package
after verifying the exact branch, base SHA, empty index, changed paths and
payload hashes.
## Model and Agent Routing Policy

This routing policy is dormant unless the project owner has explicitly authorized Codex for the specific task. The standing default remains non-Codex local payload execution.

When explicitly activated, use the lowest-cost agent capable of producing a complete and verified result.

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

This repository is the organisation-to-statutory-evidence backend/program
authority through the current Payroll baseline. It is a Java 21 Spring Boot
modular monolith with PostgreSQL, Flyway and Keycloak/OIDC. The React 18 SPA is
owned by the separate `srinivasbs2000/hrms-payroll-web` repository. Keep code
grouped by payroll capability under `backend/`; the composition root is
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
V001-V034 are committed and immutable. V033 is
`V033__salary_structure_ctc_eligibility_simulation.sql`, merged through PR #32.
V034 is `V034__jurisdiction_registration_foundations.sql`, merged through
PR #36. V035 is unreserved. Later schema work is forward-only from V035 after
explicit reservation by one active implementation owner. Versioned migrations
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

For the full Sprint 4 baseline, run `scripts/verify-sprint-4.ps1` with
`-FrontendRepositoryPath C:\dev\hrms-payroll-web`.

`docs/runbooks/sprint-4-manual-smoke.md` is an unsigned historical checklist.
Do not claim it proves a completed live smoke. S4-06A secured
HTTP/PostgreSQL integration quality closure is merged through PR #28 and its
authority closure through PR #29. S4-06B remains planned and not authorised.

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

<!-- HK-UI-SPLIT-01-REPOSITORY-TOPOLOGY:START -->
## Repository topology and execution interaction after HK-UI-SPLIT-01

- `srinivasbs2000/hrms-payroll` owns Payroll program governance,
  backend/domain code, database/Flyway, API/OpenAPI, Keycloak deployment,
  deterministic backend fixtures and backend CI.
- `srinivasbs2000/hrms-payroll-web` owns the React UI, frontend dependency
  automation, frontend SBOM, frontend CI and browser E2E.
- Browser E2E integrates the repositories through
  `PAYROLL_BACKEND_REPOSITORY_PATH`; do not recreate an embedded UI copy or a
  third contract repository.
- Product/API/database semantics remain governed by `hrms-payroll`; the UI
  consumes those contracts rather than redefining them.
- V035 remains unreserved until a separately activated product capability
  requires it.

For non-business execution work, when the next bounded action is obvious and
already authorised by the standing project rules, proceed without redundant
confirmation. Keep execution responses concise. Expand for business
functionality, architecture, material trade-offs, safety/security boundaries
or design decisions. Encode durable cross-thread operating changes at thread or
workstream closure rather than interrupting every bounded execution step.

<!-- HK-UI-SPLIT-01-REPOSITORY-TOPOLOGY:END -->

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

<!-- PROGRAM-STATUS-CLOSURE-RULE -->
## Mandatory program-status closure after every product increment

A product increment is not fully closed when its product PR merges. Before the
next capability is selected:

1. verify the product merge and post-merge evidence;
2. reconcile the detailed-story ledger;
3. update `docs/governance/payroll-program-status.md` and supporting authorities;
4. merge the documentation/governance status-closure PR;
5. release active file and migration ownership;
6. only then plan and authorize the next capability.

Every new thread reads `docs/governance/payroll-program-status.md` first.

<!-- P5-JRF-CROSS-THREAD-AUTHORITY:START -->
## Capability scope, execution lessons and reasoning routing

For every repository-aware Payroll thread:

1. read `docs/governance/payroll-program-status.md` first;
2. when program status names a planned/active capability, read its exact scope
   authority before proposing implementation;
3. read `docs/governance/payroll-automation-lessons-and-package-checklist.md`
   before creating an executable repository package;
4. read `docs/governance/hrms-payroll-model-routing-policy.md` and state
   `RECOMMENDED_REASONING_LEVEL: R1 | R2 | R3` with a short reason.

`P5-JRF-01` is historical/closed. Its durable scope authority remains:

`docs/planning/pln-01/p5-jrf-01-jurisdiction-registration-foundations-scope.md`

P5-JRF-01 merged through PR #36 and its post-merge product-status authority
closed through PR #39. V034 is committed and immutable; V035 is unreserved.
There is no active P5-JRF-01 product owner or path ownership.

No next product capability is implied by this historical section. Resolve the
current capability from `docs/governance/payroll-program-status.md` after live
local/GitHub verification.
<!-- P5-JRF-CROSS-THREAD-AUTHORITY:END -->
