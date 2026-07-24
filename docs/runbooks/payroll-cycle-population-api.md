# Payroll cycle and population API

## Scope

This runbook covers S3-01B only:

- regular payroll-cycle creation;
- cycle list and detail reads;
- deterministic pre-seal population resolution;
- active included population;
- immutable population-resolution history;
- immutable inclusion/exclusion decisions; and
- payroll-cycle audit history.

Input sealing and calculation remain later Sprint 3 slices.

## Permissions

| Permission | Purpose |
|---|---|
| `payroll-cycle.read` | Read cycles, population, resolutions and decisions |
| `payroll-cycle.create` | Create a regular payroll cycle |
| `payroll-cycle.population.resolve` | Resolve or re-resolve population before sealing |
| `audit.read` | Read cycle audit history |

The development `payroll.admin` user receives all three payroll-cycle
permissions. The development smoke user receives `payroll-cycle.read` only.

## Create a cycle

```http
POST /api/v1/payroll-cycles
Idempotency-Key: test-cycle-create
Content-Type: application/json

{
  "payGroupVersionId": "35100000-0000-0000-0000-000000000001",
  "payPeriodId": "34100000-0000-0000-0000-000000000001"
}
```

The referenced pay-group version must be approved, current, effective for the
complete open period and linked to the same payroll calendar.

The response includes a numeric `ETag`.

## Resolve population

```http
POST /api/v1/payroll-cycles/{cycleId}/population-resolution
Idempotency-Key: test-population-resolution
If-Match: "0"
```

Resolution is allowed only while the cycle is `DRAFT` or
`POPULATION_RESOLVED` and before any input snapshot exists. Every attempt
creates immutable resolution and decision evidence. Only the active included
member set is replaced.

A successful response includes:

- resolution identifier;
- attempt number;
- included and excluded counts; and
- the new payroll-cycle version number.

The response ETag is the new cycle version.

## Read endpoints

```text
GET /api/v1/payroll-cycles
GET /api/v1/payroll-cycles/{cycleId}
GET /api/v1/payroll-cycles/{cycleId}/population
GET /api/v1/payroll-cycles/{cycleId}/population-resolutions
GET /api/v1/payroll-cycles/{cycleId}/population-decisions
GET /api/v1/payroll-cycles/{cycleId}/population-decisions?resolutionId={id}
GET /api/v1/payroll-cycles/{cycleId}/audit
```

Without `resolutionId`, the decision endpoint returns the active resolution
attempt. Historical attempts remain queryable by identifier.

## Conflict behaviour

- Reusing an idempotency key with changed content returns `409`.
- A stale `If-Match` returns `409`.
- Duplicate regular cycles return `409`.
- Cross-tenant resources are not visible.
- Invalid or non-executable configuration returns `422`.
- Missing resources return `404`.

## Verification

```powershell
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
$env:MAVEN_OPTS = "-Duser.timezone=UTC"

.\mvnw.cmd clean verify "-DskipTests=false" "-DskipITs=false"

npx --yes --package=@redocly/cli@2.39.0 `
  redocly lint `
  contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
```

`PayrollOperationsApiIT` adds four API integration tests. The total test count
must not fall below the previous green baseline; a higher count is valid.
