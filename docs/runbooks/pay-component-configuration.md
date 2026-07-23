# Pay-component configuration

## Purpose

Pay components define tenant-scoped earning, deduction and informational
identities. Calculation changes are represented by immutable, effective-dated
versions so salary structures and payroll results can retain exact lineage.

## Supported component types

- `EARNING`
- `DEDUCTION`
- `INFORMATION`

## Supported formula types

- `FIXED`: requires a non-negative `fixedAmount` and no expression.
- `PERCENTAGE_OF_COMPONENT`: requires `formulaExpression` and no fixed amount.
- `RESIDUAL`: requires `formulaExpression` and no fixed amount.

`roundingScale` defaults to 2 and must be between 0 and 4.

## Lifecycle

1. Create a stable identity and first draft version.
2. Approve a non-superseded draft before it becomes effective.
3. Append future versions for prospective calculation changes.
4. Correct only a future, non-superseded draft by creating a replacement.
5. End-date through the controlled command with the current ETag.
6. Read immutable audit evidence through the identity audit endpoint.

Approved effective ranges for the same component cannot overlap. Direct update
or delete access to component versions is denied to the application role.

## Permissions

- `compensation.component.read`
- `compensation.component.create`
- `compensation.component.version.create`
- `compensation.component.version.correct`
- `compensation.component.version.end-date`
- `compensation.component.approve`
- `audit.read`

## Idempotency and concurrency

Every write requires `Idempotency-Key`. Reusing the same key with a different
request is rejected. End-dating also requires `If-Match` containing the current
numeric version.

## Tenant boundary

The application transaction sets `app.tenant_id`. Forced row-level security
applies to component identities and versions, and cross-tenant reads return no
records.