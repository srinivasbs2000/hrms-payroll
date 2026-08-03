# Organisation hierarchy closure runbook

## Purpose

P5-A1 closes lifecycle and ownership gaps in the existing legal entity ->
payroll statutory unit -> establishment hierarchy. It does not replace the
V015 identity/version model or introduce country-specific legal interpretation.

## Lifecycle

- A newly created identity starts `PENDING_APPROVAL` with a first `DRAFT`
  version.
- The immutable authenticated actor (`issuer|subject`) that created a version
  cannot approve it.
- The first successful independent approval activates the identity.
- An `ACTIVE` identity may be retired through the identity endpoint with an
  effective date, reason and identity ETag.
- Retirement end-dates the final approved version through the V022-compatible
  controlled command. Future versions or dependants that extend beyond the
  requested date block retirement.
- `RETIRED` identities reject new versions and approvals. Nothing is deleted.

## Classifications

PSU versions use one of:

- `TAX_AND_STATUTORY`
- `TAX_ONLY`
- `STATUTORY_ONLY`
- `PAYROLL_OPERATIONS`

Establishment versions use one of:

- `OFFICE`
- `BRANCH`
- `FACTORY`
- `SHOP`
- `CONSTRUCTION`
- `OTHER`

These are jurisdiction-neutral product classifications, not statutory legal
conclusions.

## API

`POST /api/v1/{collection}/{identityId}/retirement`

Required headers:

- `Idempotency-Key`
- `If-Match` containing `identityVersionNo`

Body:

```json
{
  "effectiveDate": "2028-01-01",
  "reason": "Employer registration closed"
}
```

Permission: `organisation.retire`.

## Conflict behavior

- duplicate tenant code: 409;
- creator self-approval: 409;
- stale identity/version ETag: 409;
- retired identity lifecycle mutation: 409;
- dependent configuration blocking retirement/end-date: 409;
- malformed code or invalid classification/date: 422;
- cross-tenant absence: secure 404 or empty list.

Problem responses never expose SQL, constraint names, stack traces or another
tenant's existence.

## Operator checks

Before retirement, inspect version history and dependent PSU, establishment,
pay-group and employee-payroll effective ranges. Resolve future or extending
dependencies through their approved lifecycle commands. Never edit history
rows directly.
