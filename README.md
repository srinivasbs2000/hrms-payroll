# HRMS Payroll vertical slice

Runnable Sprint 0A hardened baseline for the approved organisation-to-draft-payslip slice. The repository contains a Java 21/Spring Boot modular-monolith starter, React 18/Vite starter, OpenAPI 3.1 contract, PostgreSQL 17/Flyway schema, Keycloak development realm, Docker Compose stack and Sprint 0-3 backlog.

The implemented starter scope is limited to fixed monthly BASIC, HRA and SPECIAL_ALLOWANCE gross-to-net calculation and a synthetic draft-payslip preview. Statutory deductions, tax, retro, off-cycle, final settlement, banking and accounting are intentionally excluded.

## Repository layout

- `backend/` - Maven modules and Spring Boot composition root
- `frontend/payroll-web/` - React 18, TypeScript and Vite application
- `contracts/openapi/` - approved OpenAPI 3.1 contract
- `database/flyway/` - canonical bootstrap, migrations, development seed and verification SQL
- `deploy/local/` - PostgreSQL and Keycloak Docker Compose stack
- `docs/baseline/` - implementation pack and artifact manifest
- `docs/adr/` - accepted architecture decisions
- `backlog/` - Sprint 0-3 delivery backlog

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java 21
- Node.js 24.14.0 and npm 11.9.x
- PowerShell 7 or Windows PowerShell 5.1

Maven does not need to be installed globally; the checked-in wrapper downloads Maven 3.9.11 on first use.

## Exact local start (PowerShell)

Run every command from the repository root.

```powershell
Copy-Item deploy/local/.env.example deploy/local/.env
# Edit deploy/local/.env and replace every development placeholder before first start.
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
```

On a new PostgreSQL volume, Compose applies `database/flyway/bootstrap/001_admin_bootstrap.sql` automatically. The bootstrap is idempotent and can also be applied explicitly:

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

Verify the database, backend, frontend and OpenAPI contract:

```powershell
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql | docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
.\mvnw.cmd verify
Push-Location frontend/payroll-web
npm ci
npm test
npm run build
Pop-Location
npx --yes --package=@redocly/cli@2.39.0 redocly lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
npm audit --audit-level=high
```

The verification SQL fails immediately on any missing RLS/force/policy control, tenant-unsafe FK, prohibited runtime grant, ownership issue or immutable-table mutation grant. It then prints the complete RLS, FK and role evidence. Local credentials and the synthetic `DEMO001` record are development-only placeholders. The `prod` Spring profile has no credential, issuer, audience or service-identity defaults and cannot start without externally supplied values.

Start the backend and frontend after the checks pass:

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:DB_USER = 'payroll_app'
$env:DB_PASSWORD = $local.PAYROLL_APP_PASSWORD
.\mvnw.cmd -pl backend/payroll-boot -am spring-boot:run
```

To run the local real-token authentication smoke test, enable its technical endpoint before starting the backend:

```powershell
$env:BASELINE_AUTH_SMOKE_ENABLED = 'true'
.\mvnw.cmd -pl backend/payroll-boot -am spring-boot:run
```

Then, in a second terminal from the repository root:

```powershell
.\deploy\local\smoke\auth-smoke.ps1
```

The script obtains a real token from the development Keycloak realm, checks issuer, `payroll-api` audience, tenant, `PAYROLL_OPERATOR` role and `payroll.read` permission, and calls `GET /internal/baseline/auth-smoke`. The raw token remains in memory and is neither printed nor persisted. The endpoint is disabled by default and is forced off in the `prod` profile.

In a second terminal:

```powershell
Set-Location frontend/payroll-web
npm run dev
```

The API runs on `http://localhost:8080`, PostgreSQL is exposed only on `127.0.0.1:15432`, Keycloak only on `http://127.0.0.1:8081`, and the web application on `http://localhost:5173`. Set `POSTGRES_PORT` in `deploy/local/.env` if 15432 is unavailable.

`mvn verify` includes PostgreSQL 17 Testcontainers migration, RLS, cross-tenant FK, immutability and least-privilege tests. When Maven itself is run inside a Docker container on Docker Desktop, add `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` and `TESTCONTAINERS_RYUK_DISABLED=true`; direct host and CI Maven runs do not need this nested-container workaround.

Stop local infrastructure without deleting the database volume:

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
```
