# Pay-group configuration runbook

## Scope

This runbook covers the Sprint 2 pay-group foundation only:

- stable tenant-scoped pay-group identities;
- immutable effective-dated versions;
- one same-tenant monthly calendar;
- INR currency;
- calendar-day proration;
- approval, future correction and optimistic end-dating;
- audit and transactional outbox evidence.

Period generation and richer calendar selection are delivered separately.

## Lifecycle

1. Create a pay-group identity and first draft version.
2. Approve the non-superseded draft.
3. Read the current version using an `asOf` date.
4. Add a future version for a planned change.
5. Correct only a non-superseded future draft.
6. End-date a version with its numeric `ETag` in `If-Match`.

Approved ranges are half-open: `[effectiveFrom, effectiveTo)`.
Overlapping approved versions are rejected by PostgreSQL.

## Required permissions

- `pay-group.read`
- `pay-group.create`
- `pay-group.version.create`
- `pay-group.version.correct`
- `pay-group.version.end-date`
- `pay-group.approve`
- `audit.read`

## Approved request shape

```json
{
  "code": "MONTHLY_IN",
  "name": "Monthly India",
  "payrollStatutoryUnitVersionId": "00000000-0000-0000-0000-000000000000",
  "calendarId": "00000000-0000-0000-0000-000000000000",
  "currency": "INR",
  "prorationMethod": "CALENDAR_DAYS",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2027-01-01"
}
```

Synthetic UUIDs above are placeholders.

## Verification

```powershell
.\scripts\verify-backend.ps1

Push-Location frontend/payroll-web
npm test
npm run build
Pop-Location

npx --yes --package=@redocly/cli@2.39.0 `
  redocly lint `
  contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
```
