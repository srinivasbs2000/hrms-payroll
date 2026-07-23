# Employee payroll application layer

## Scope

S2-05B-01 through S2-05B-03 expose the V021/V022 employee-payroll foundation
through Java contracts, tenant-scoped repositories and transactional application
services. This slice intentionally does not add REST controllers, OpenAPI paths or
frontend screens; those are delivered in S2-05B-04 through S2-05B-06.

## Aggregates

- Payroll relationship identity and immutable effective-dated versions
- Payroll assignment identity and immutable effective-dated versions
- Employee payroll profile lifecycle
- Pay-group assignment succession chain
- Salary assignment succession chain

All writes use the `payroll_app` role and rely on the controlled lifecycle
functions introduced in V021 and hardened in V022. Existing migrations must not be
edited.

## Command guarantees

Every application command:

1. requires an `Idempotency-Key`;
2. executes inside `TenantTransactionExecutor.write`;
3. verifies replay payload hashes;
4. writes the aggregate change, audit event and transactional outbox event in the
   same transaction;
5. returns conflicts for stale optimistic versions or invalid lifecycle changes.

Future-dated draft corrections create a successor record instead of updating
history in place.

## Read guarantees

Reads execute inside `TenantTransactionExecutor.read` and therefore set the
transaction-local tenant context before querying forced-RLS tables. Current views
return only approved, effective and non-superseded versions.

## Profile readiness

Profiles are created as `INCOMPLETE`. The service delegates status transitions to
`employee_payroll.update_employee_payroll_profile_status`, so `READY` remains
blocked until an active relationship has overlapping approved assignment,
pay-group and salary configuration.

## Permissions

The local Keycloak realm includes explicit permissions for relationship,
assignment, profile, pay-group-assignment and salary-assignment operations.
`payroll.admin` receives lifecycle permissions; `payroll.smoke` receives read-only
permissions.

## Verification for this slice

Run the full backend reactor, not a module-only stale build:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
$env:MAVEN_OPTS = "-Duser.timezone=UTC"
.\mvnw.cmd clean verify "-DskipTests=false" "-DskipITs=false"
```

Then verify formatting and scope:

```powershell
git diff --check
git status --short --untracked-files=all
```
