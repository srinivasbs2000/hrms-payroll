# Payroll Calendar Configuration Runbook

## Scope

This runbook covers tenant-scoped monthly payroll calendars and deterministic annual pay-period generation introduced by S2-02.

## Supported model

- Frequency: `MONTHLY`
- Default timezone: `Asia/Kolkata`
- Generation window: calendar years 2020 through 2100
- Period boundaries: first through last calendar day of each month
- Default payment day: 31
- Short months: payment date is clipped to the final day of the month
- Generated status: `OPEN`

## Security and transaction model

Application code never inserts, updates or deletes calendar or period rows directly. It calls controlled PostgreSQL functions inside a tenant transaction.

Every write requires:

- authenticated actor;
- tenant claim;
- permission;
- correlation ID;
- idempotency key.

Calendar creation and period generation write audit and transactional outbox records in the same transaction.

## Permissions

- `calendar.read`
- `calendar.create`
- `calendar.period.generate`
- `audit.read`

## Idempotency

Reusing the same idempotency key with the same request returns the stored response. Reusing it with different content returns a conflict.

Generating the same calendar year and payment day is database-idempotent and returns the existing twelve periods. A different requested payment schedule for an already-generated year is rejected.

## Validation

Before using a generated period in a payroll cycle, the database confirms that the period belongs to the calendar referenced by the exact pay-group version.

## Recovery

There is no direct runtime correction of generated periods. A conflicting or incorrect generated schedule must be investigated and handled through an approved future correction design rather than rewriting payroll history.
