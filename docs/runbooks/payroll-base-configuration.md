# Named payroll-base configuration

## Purpose

Define reusable calculation, statutory, tax, CTC, or reporting bases and map
exact pay-component versions into exact base versions. A base is configuration
metadata; P5-A2 does not change the current calculation engine.

## Base lifecycle

1. Create the stable base identity and first complete draft version.
2. Approve with a checker different from the maker.
3. Add or correct future versions append-only.
4. End-date with optimistic concurrency.
5. Retire only when no active/future approved version or membership remains.

## Membership lifecycle

A membership contains exact `payrollBaseVersionId`, `componentId`, and
`componentVersionId` lineage plus:

- `membershipType`: `INCLUDE`, `EXCLUDE`, `ADD_BACK`, `ELIGIBILITY_ONLY`,
  `CONTRIBUTION_ONLY`, or `NOTIONAL`;
- `inclusionPercent`: decimal string, greater than zero and at most 100, stored
  as `numeric(12,8)`;
- half-open effective dates;
- maker-checker approval and append-only correction lineage.

Approval verifies both referenced versions are approved, schema 1 where
required, belong to active identities, and fully cover the membership range.

## API permissions

- `compensation.base.read`
- `compensation.base.create`
- `compensation.base.version.create`
- `compensation.base.version.correct`
- `compensation.base.version.end-date`
- `compensation.base.approve`
- `compensation.base.retire`
- `compensation.base.membership.create`
- `compensation.base.membership.correct`
- `compensation.base.membership.end-date`
- `compensation.base.membership.approve`
- `audit.read`
