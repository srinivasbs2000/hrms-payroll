# Sprint 3 Manual Payroll-Execution Smoke

## Purpose

Run one live, browser-driven payroll workflow before merging PR #18. Automated
CI already validates migrations/RLS, APIs, permissions, deterministic
calculation, frontend components and build output. This checklist validates the
integrated operator experience against a running local stack.

## Preconditions

- checkout the exact reviewed PR head;
- working tree is clean;
- full `scripts/verify-sprint-3.ps1` regression is green;
- Docker Desktop is running;
- `deploy/local/.env` contains non-placeholder development credentials;
- migrations through V026 are applied;
- a development user has the required payroll-cycle, calculation, result and
  trace permissions; and
- suitable approved pay-group, period, employee-payroll and salary-structure
  configuration exists.

Record:

| Field | Value |
|---|---|
| Tester | |
| Date/time | |
| Commit SHA | |
| Browser/version | |
| Tenant | |
| Development user | |

## Start the stack

From the repository root:

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
```

Apply migrations when required:

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:FLYWAY_USER = 'payroll_migrator'
$env:FLYWAY_PASSWORD = $local.PAYROLL_MIGRATOR_PASSWORD
.\mvnw.cmd -pl backend/database-migrations flyway:migrate
```

Start the backend:

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:DB_USER = 'payroll_app'
$env:DB_PASSWORD = $local.PAYROLL_APP_PASSWORD
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f backend/payroll-boot/pom.xml spring-boot:run
```

Start the frontend in another PowerShell 7 terminal:

```powershell
Set-Location C:\dev\hrms-payroll\frontend\payroll-web
npm run dev
```

Open `http://localhost:5173`.

## Workflow checks

### 1. Permission boundary

- Sign in with the intended development operator.
- Open **Payroll execution**.
- Confirm the cycle list loads.
- Confirm only actions granted to the operator are visible.
- Confirm a user lacking `payroll-cycle.read` cannot view the workspace.

Result: `PASS / FAIL`

Notes:

### 2. Cycle and population

- Select an existing regular cycle or create one using valid approved
  pay-group-version and open pay-period IDs.
- Record the cycle ID and displayed version.
- Resolve population.
- Confirm the version increments.
- Confirm included employees appear.
- Confirm inclusion evidence is visible.
- Confirm no employee from another tenant appears.

| Evidence | Value |
|---|---|
| Cycle ID | |
| Initial version | |
| Population resolution ID | |
| Included count | |
| Excluded count | |

Result: `PASS / FAIL`

### 3. Immutable input sealing

- Seal inputs.
- Confirm the lifecycle becomes `INPUTS_SEALED`.
- Confirm snapshot count is non-zero.
- Confirm snapshot hashes and combined input-set hash are visible.
- Refresh the page and confirm the same evidence remains.

| Evidence | Value |
|---|---|
| Sealed version | |
| Snapshot count | |
| Combined hash | |

Result: `PASS / FAIL`

### 4. Initial calculation

- Calculate payroll.
- Confirm the lifecycle becomes `CALCULATED`.
- Confirm gross, deduction and net totals are shown.
- Confirm an initial calculation attempt appears.
- Record the calculation request ID and one result ID.

| Evidence | Value |
|---|---|
| Calculated version | |
| Initial request ID | |
| Result ID | |
| Gross total | |
| Deduction total | |
| Net total | |

Result: `PASS / FAIL`

### 5. Real draft payslip

- Open **View** for a persisted result.
- Confirm the employee and assignment match the selected result.
- Confirm earning and deduction rows come from persisted components.
- Confirm gross, deductions and net reconcile with the result.
- Confirm result, input-snapshot and salary-structure evidence is present.
- Confirm trace evidence appears when the operator has trace permission.
- Confirm the page displays:

`DRAFT · NOT FOR PAYMENT · NOT A LEGAL PAYSLIP`

Result: `PASS / FAIL`

### 6. Controlled recalculation

- Return to Payroll execution.
- Enter a meaningful reason of at least eight characters.
- Recalculate.
- Confirm the cycle version increments.
- Confirm a second attempt appears before the first.
- Confirm the reason is displayed.
- Confirm both historical result sets remain readable.
- Confirm refreshing does not create another attempt.

| Evidence | Value |
|---|---|
| Recalculated version | |
| Recalculation request ID | |
| Superseded request ID | |
| Reason | |

Result: `PASS / FAIL`

### 7. Negative operator behavior

- Attempt an action not valid for the current lifecycle and confirm it is
  disabled.
- Using a stale browser tab, attempt a version-sensitive action and confirm a
  visible conflict rather than false success.
- Remove trace permission and confirm the draft payslip does not request or
  display trace evidence.
- Confirm no secrets or raw access tokens appear in browser output or logs.

Result: `PASS / FAIL`

## Completion

Overall result: `PASS / FAIL`

Blocking observations:

Non-blocking observations:

Tester sign-off:

## Stop local infrastructure

```powershell
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
```
