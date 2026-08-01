# HRMS Payroll Sprint 1-4 vertical slice

Runnable payroll vertical slice covering tenant-safe organisation, payroll
configuration, employee payroll identity, controlled regular payroll execution
and jurisdiction-neutral statutory evidence. The repository contains a Java
21/Spring Boot modular monolith, React 18/Vite web application, OpenAPI 3.1
contracts, PostgreSQL 17/Flyway schema, Keycloak development realm, Docker
Compose stack and a Sprint 0-4 delivery backlog.

The implemented regular-payroll path supports approved fixed monthly BASIC,
HRA and SPECIAL_ALLOWANCE components, immutable sealed inputs, deterministic
calculation evidence, controlled recalculation and a persisted draft-payslip
view.

Sprint 4 adds a country-neutral statutory bounded context with effective-dated
rule versions, employee statutory profiles and exact assignments,
deterministic statutory evaluation, append-only ledger posting, signed
corrections, PTD/YTD balances, reconciliation, remittance preparation evidence,
secured APIs and a permission-aware statutory operator workspace.

The repository does not provide jurisdiction-specific statutory rates or legal
tax interpretation. Statutory filing, returns, acknowledgements, payment or
settlement, retro and off-cycle payroll, final settlement, banking/payment
files, accounting/GL integration and legal/final payslip publication remain
explicitly excluded.

## Current controlled restart state

- Current verified repository base: PR #20 merge
  `4b5da975eb851434957667bdecf138ea9b43f929`.
- Latest product implementation baseline: Sprint 4 merge
  `def3dd2e212f85c440eee5497e292be2f1f2bf64`.
- V001-V030 are committed and immutable.
- V031 remains unreserved.
- S4-06A Statutory API Integration Closure is the next selected implementation
  increment after the documentation restart is merged and separately authorised.
- S4-06B statutory-specific Playwright E2E is planned but not authorised.
- The Sprint 4 manual-smoke file is an unsigned historical checklist and is not
  proof that a live manual smoke was completed.

## Repository layout

- `backend/` - Maven modules and Spring Boot composition root
- `frontend/payroll-web/` - React 18, TypeScript and Vite application
- `contracts/openapi/` - aggregate and bounded-context OpenAPI 3.1 contracts
- `database/flyway/` - canonical bootstrap, migrations, development seed and verification SQL
- `deploy/local/` - PostgreSQL and Keycloak Docker Compose stack
- `docs/adr/` - accepted architecture decisions
- `docs/baseline/` - implementation packs and historical integration reports
- `docs/quality/` - schema audits, negative-path evidence and Sprint closure reports
- `docs/runbooks/` - API, UI, operational and project-continuation guidance
- `backlog/` - Sprint 0-4 delivery backlog

## Regular payroll execution flow

1. Create or select a regular payroll cycle.
2. Resolve an immutable population attempt with inclusion/exclusion evidence.
3. Seal immutable employee input snapshots.
4. Execute the deterministic fixed-component calculation.
5. Persist calculation request, result, component and trace evidence atomically.
6. Inspect the persisted draft payslip.
7. Perform a reasoned, version-checked recalculation while preserving history.

The draft payslip remains explicitly marked:

`DRAFT - NOT FOR PAYMENT - NOT A LEGAL PAYSLIP`

Use `docs/runbooks/payroll-execution-ui.md` for the regular-payroll workspace.

## Statutory execution flow

1. Select a calculated payroll cycle and its exact completed calculation request.
2. Execute deterministic statutory evaluation against exact approved rule,
   profile, assignment and payroll-result lineage.
3. Post the completed evaluation to the append-only statutory ledger.
4. Review ledger entries, balances, reconciliation and remittance preparation
   evidence.
5. Append a signed correction with an auditable reason when authorised.

Use `docs/runbooks/statutory-execution-ui.md` for normal operation.
`docs/runbooks/sprint-4-manual-smoke.md` is retained as a historical unsigned
checklist, not as completed closure evidence.

## Project continuation

Before continuing design or implementation in a new thread or session, read:

1. `AGENTS.md`
2. `docs/design/hrms-payroll-master-design.md`
3. `docs/design/decision-register.md`
4. `docs/runbooks/project-continuation-handoff.md`
5. `docs/governance/thread-registry.md`
6. `docs/governance/thread-maintenance-protocol.md`

Then validate the handoff against the current local working tree and live GitHub
branch, pull request and CI evidence. Unknown or conflicting facts must be
reported rather than guessed.

Before declaring a documentation or implementation phase complete, compare the
original approved scope/file checklist with the actual staged or committed
result. An omitted item must be completed or explicitly deferred by the project
owner.

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java 21
- Node.js 24.14.0 and npm 11.9.x
- PowerShell 7

Maven does not need to be installed globally; the checked-in wrapper downloads
Maven 3.9.11 on first use.

## Exact local start (PowerShell 7)

Run every command from the repository root.

```powershell
Copy-Item deploy/local/.env.example deploy/local/.env
# Edit deploy/local/.env and replace every development placeholder before first start.
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
```

On a new PostgreSQL volume, Compose applies
`database/flyway/bootstrap/001_admin_bootstrap.sql` automatically. The bootstrap
is idempotent and can also be applied explicitly:

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres bash -lc 'psql -v ON_ERROR_STOP=1 -v payroll_app_password="$PAYROLL_APP_PASSWORD" -v payroll_migrator_password="$PAYROLL_MIGRATOR_PASSWORD" -U postgres -d payroll -f /bootstrap/001_admin_bootstrap.sql'
```

Apply the ordered migrations through the `database-migrations` Maven module:

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:FLYWAY_USER = 'payroll_migrator'
$env:FLYWAY_PASSWORD = $local.PAYROLL_MIGRATOR_PASSWORD
.\mvnw.cmd -pl backend/database-migrations flyway:migrate
```

## Full verification

Run the checked-in Sprint 4 regression script:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\verify-sprint-4.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

The script runs frontend dependency installation, scoped npm-audit policy
self-tests and live validation, lint, all frontend tests, production build,
full Maven verification, aggregate OpenAPI validation with external
statutory-fragment references resolved, `git diff --check` and final Git
status.

The database verification can also be run explicitly:

```powershell
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql |
  docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml `
    exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
```

The verification SQL fails immediately on any missing RLS/force/policy control,
tenant-unsafe FK, prohibited runtime grant, ownership issue or immutable-table
mutation grant. Local credentials and synthetic records are development-only.
The `prod` Spring profile has no credential, issuer, audience or
service-identity defaults and cannot start without externally supplied values.

## Start backend and frontend

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:DB_USER = 'payroll_app'
$env:DB_PASSWORD = $local.PAYROLL_APP_PASSWORD
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f backend/payroll-boot/pom.xml spring-boot:run
```

To run the real-token authentication smoke test, enable its technical endpoint
before starting the backend:

```powershell
$env:BASELINE_AUTH_SMOKE_ENABLED = 'true'
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f backend/payroll-boot/pom.xml spring-boot:run
```

Then, in a second terminal from the repository root:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\deploy\local\smoke\auth-smoke.ps1"
```

The script obtains a real token from the development Keycloak realm, validates
issuer, audience, tenant and mapped permissions, and calls secured backend
endpoints. The raw token remains in memory and is neither printed nor
persisted. The technical endpoint is disabled by default and forced off in the
`prod` profile.

In another terminal:

```powershell
Set-Location frontend/payroll-web
npm run dev
```

The API runs on `http://localhost:8080`, PostgreSQL is exposed only on
`127.0.0.1:15432`, Keycloak only on `http://127.0.0.1:8081`, and the web
application on `http://localhost:5173`. Set `POSTGRES_PORT` in
`deploy/local/.env` if 15432 is unavailable.

`mvn verify` includes PostgreSQL 17 Testcontainers migration, RLS,
cross-tenant FK, immutability and least-privilege tests. When Maven itself is
run inside a Docker container on Docker Desktop, add
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` and
`TESTCONTAINERS_RYUK_DISABLED=true`; direct host and CI Maven runs do not need
this nested-container workaround.

## Stop local infrastructure

Stop without deleting the database volume:

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
```

<!-- LIVING-PROJECT-DESIGN:START -->
## Living project design and multi-thread continuation

The repository-owned product and architecture authority is:

`docs/design/hrms-payroll-master-design.md`

Material decisions are indexed in:

`docs/design/decision-register.md`

Parallel ChatGPT/Codex threads coordinate through:

- `docs/governance/thread-registry.md`
- `docs/governance/thread-maintenance-protocol.md`
- `docs/governance/thread-start-prompt.md`

The running handoff remains the authority for current branch, PR, CI, blockers
and next authorised action. Separate chat threads do not automatically share
complete context; seed each thread once with the standard thread-start prompt.
<!-- LIVING-PROJECT-DESIGN:END -->
