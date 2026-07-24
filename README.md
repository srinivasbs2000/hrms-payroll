# HRMS Payroll vertical slice

Runnable Sprint 1–3 payroll vertical slice covering tenant-safe organisation,
payroll configuration, employee payroll identity and controlled payroll
execution. The repository contains a Java 21/Spring Boot modular monolith,
React 18/Vite web application, OpenAPI 3.1 contract, PostgreSQL 17/Flyway
schema, Keycloak development realm, Docker Compose stack and Sprint 0–3
delivery backlog.

The implemented payroll scope is limited to regular monthly, non-statutory
starter calculation using approved fixed components. Sprint 3 adds controlled
cycle and population resolution, immutable sealed input snapshots,
deterministic result/component/trace persistence, controlled recalculation and
a real draft-payslip view generated from persisted calculation evidence.

Statutory deductions and tax, retro and off-cycle payroll, final settlement,
banking/payment files, accounting/GL integration and legal/final payslip
publication are intentionally excluded.

## Repository layout

- `backend/` - Maven modules and Spring Boot composition root
- `frontend/payroll-web/` - React 18, TypeScript and Vite application
- `contracts/openapi/` - approved OpenAPI 3.1 contract
- `database/flyway/` - canonical bootstrap, migrations, development seed and verification SQL
- `deploy/local/` - PostgreSQL and Keycloak Docker Compose stack
- `docs/baseline/` - implementation pack and artifact manifest
- `docs/adr/` - accepted architecture decisions
- `docs/quality/` - schema audits, negative-path evidence and Sprint closure reports
- `docs/runbooks/` - API, UI and operational validation instructions
- `backlog/` - Sprint 0–3 delivery backlog

## Sprint 3 execution flow

The controlled regular-payroll path is:

1. create or select a payroll cycle;
2. resolve an immutable population attempt with inclusion/exclusion evidence;
3. seal immutable employee input snapshots;
4. execute the deterministic starter calculation;
5. persist calculation request, result, component and trace evidence atomically;
6. inspect the real draft payslip generated from persisted results; and
7. perform a reasoned, version-checked recalculation while preserving history.

The draft payslip is explicitly marked:

`DRAFT · NOT FOR PAYMENT · NOT A LEGAL PAYSLIP`

Use `docs/runbooks/payroll-execution-ui.md` for the execution workspace and
`docs/runbooks/sprint-3-manual-smoke.md` for the pre-merge live smoke check.

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

Run the checked-in Sprint 3 regression script:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\verify-sprint-3.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

The script runs frontend dependency installation, lint, all tests, production
build, npm audit, Maven verification, pinned Redocly validation,
`git diff --check` and final Git status.

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
