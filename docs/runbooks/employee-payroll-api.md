# Employee payroll lifecycle API

S2-05B exposes the V021/V022 employee-payroll foundation through tenant-scoped,
permission-protected REST endpoints. The application does not update immutable
history tables directly. Approval, end-date and profile-status changes call the
controlled database lifecycle functions introduced by V021 and hardened by
V022.

## Endpoint families

| Family | Base path | Purpose |
| --- | --- | --- |
| Payroll relationship | `/api/v1/payroll-relationships` | Stable employee identity and effective-dated legal-employer relationship |
| Payroll assignment | `/api/v1/payroll-assignments` | Stable assignment identity and effective-dated establishment placement |
| Payroll profile | `/api/v1/employee-payroll-profiles` | INR payroll readiness and operational status |
| Pay-group assignment | `/api/v1/pay-group-assignments` | Exact approved pay-group version assigned to an assignment version |
| Salary assignment | `/api/v1/salary-assignments` | Exact approved salary-structure version and monthly amount |

Profile lookup by relationship uses `/api/v1/payroll-relationships/{relationshipId}/profile`; this avoids ambiguous routing beside profile status and audit endpoints.

Every write requires `Idempotency-Key`. End-date and profile-status operations
also require `If-Match` with the numeric `versionNo` returned as the response
ETag. Reusing an idempotency key with a different canonical request returns
HTTP 409. A stale ETag also returns HTTP 409.

## Lifecycle order

1. Create and approve a payroll relationship version.
2. Create and approve a payroll assignment version that belongs to that
   relationship and an approved establishment version.
3. Create the employee payroll profile. It always starts as `INCOMPLETE`.
4. Create and approve a pay-group assignment for the exact assignment version.
5. Create and approve a salary assignment for the same assignment version.
6. Transition the profile to `READY` using its current ETag.

The `READY` transition is rejected unless V021/V022 can prove an active
relationship and assignment with overlapping approved pay-group and salary
configuration. The API deliberately delegates this invariant to the database
rather than duplicating it in controller code.

## Corrections and history

Relationship and assignment corrections append a successor version and retain
the superseded draft. Pay-group and salary assignment corrections append a new
record linked by `supersedesAssignmentId`. Only a non-superseded future draft is
eligible for correction. Historical rows are never rewritten or deleted.

## Security and tenancy

The controllers use the `employee-payroll.*` permission constants defined in
`EmployeePayrollPermissions`. Audit endpoints require `audit.read`. All service
operations execute through `TenantTransactionExecutor`, which applies the JWT
`tenant_id` using transaction-local database context before RLS-protected SQL is
executed.

## Verification

`EmployeePayrollApiIT` proves:

- full relationship-to-READY lifecycle;
- idempotent replay and different-payload conflict;
- approval, audit and ETag behavior;
- stale optimistic-concurrency rejection;
- tenant isolation; and
- permission denial.

The canonical API contract remains
`contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`. Employee-payroll
path items and schemas are maintained in the externally referenced
`contracts/openapi/employee-payroll-openapi-v1.yaml` fragment.
