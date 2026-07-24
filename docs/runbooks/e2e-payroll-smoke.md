# Isolated Payroll E2E Fixture

## Purpose

Create a repeatable synthetic environment without touching the normal local
PostgreSQL volume.

## Preconditions

- PowerShell 7;
- Docker Desktop;
- Java 21;
- a populated ignored `deploy/local/.env`;
- ports 8081 and 25432 available;
- no frontend or backend process required for S3-09A reset.

## Reset

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\e2e\reset-payroll-smoke.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

The command:

1. deletes only the `hrms-payroll-e2e` Compose project and its volume;
2. starts isolated PostgreSQL and Keycloak;
3. applies and validates all Flyway migrations;
4. applies ordered E2E fixtures;
5. verifies the exact organisation, compensation and employee-payroll data;
6. proves one included and one `PROFILE_NOT_READY` population decision; and
7. proves sealing, initial calculation and recalculation in a rollback-only
   transaction.

The executable tenant is `E2E001` and uses tenant ID
`00000000-0000-0000-0000-000000000001`, matching the development Keycloak
realm.

## Verify without reset

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\e2e\verify-payroll-smoke.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

## Backend against the E2E database

```powershell
Set-Location C:\dev\hrms-payroll

$local = ConvertFrom-StringData (
  Get-Content -Raw deploy/local/.env
)

$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=Asia/Kolkata'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:25432/payroll'
$env:DB_USER = 'payroll_app'
$env:DB_PASSWORD = $local.PAYROLL_APP_PASSWORD
$env:OIDC_ISSUER = 'http://localhost:8081/realms/payroll'

.\mvnw.cmd `
  -f backend/payroll-boot/pom.xml `
  '-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Kolkata' `
  spring-boot:run
```

S3-09B will replace the manual backend/frontend launch with a guarded Playwright
runner and CI job.

## Stop

Remove the isolated database:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\e2e\stop-payroll-smoke.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll"
```

Preserve it temporarily for investigation:

```powershell
pwsh.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
  -File "C:\dev\hrms-payroll\scripts\e2e\stop-payroll-smoke.ps1" `
  -RepositoryPath "C:\dev\hrms-payroll" `
  -KeepDatabase
```

## Safety

The reset script never issues `TRUNCATE` or `DROP` against the normal local
database. It removes only the named `hrms-payroll-e2e` Compose project and its
dedicated volume.

## Run Playwright after reset

Stop any separately running backend or frontend first. The Playwright runner
starts both against the isolated E2E services.

Local development passwords default to the synthetic realm value `change-me`.
Override them without committing values when required:

```powershell
$env:E2E_PAYROLL_ADMIN_PASSWORD = '<local-only-password>'
$env:E2E_PAYROLL_SMOKE_PASSWORD = '<local-only-password>'
```

Install the pinned browser once:

```powershell
Set-Location C:\dev\hrms-payroll\frontend\payroll-web
npm ci
npx playwright install chromium
```

Run the complete browser suite:

```powershell
npm run e2e
```

Visible debugging:

```powershell
npm run e2e:headed
```

Interactive Playwright UI:

```powershell
npm run e2e:ui
```

The suite starts the backend against PostgreSQL port `25432` and starts Vite on
`localhost:5173`. It must begin from a freshly reset fixture.

Expected coverage:

- real administrator and read-only Keycloak login;
- refresh-restored authentication;
- no browser storage token persistence;
- controlled cycle creation and full payroll execution;
- one included and one `PROFILE_NOT_READY` population outcome;
- persisted draft-payslip evidence;
- controlled recalculation and historical attempts;
- stale-version conflict in a second browser page;
- read-only UI and backend `403`; and
- no unexpected API `401`, `403` or `5xx`.

Reports are written to ignored local directories. Authentication state must not
be uploaded or committed.
