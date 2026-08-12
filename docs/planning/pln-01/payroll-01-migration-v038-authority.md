# Payroll Migration V038 Authority — P5-A4

## Status

**RESERVED — P5-A4 ONLY**

## Canonical base

`850e934b5ab839b349d0130e021f03276f9c90c6`

## Authority

- `V001` through `V037` are committed and immutable.
- `V038` is the next free migration number on the activation base.
- `V038` is reserved exclusively for **P5-A4 — Pay Groups, Period Generation & Milestone Rules**.
- The activation commit itself must not create `database/flyway/sql/V038__*.sql`.
- P5-A4 may create V038 only when the reconciled implementation requires additive schema changes to close `PLN-E02-001` through `PLN-E02-010`.
- No other workstream may claim V038 while P5-A4 is ACTIVE.
- Existing migrations must not be edited, renumbered or rewritten.

## Allowed V038 subject matter

Only additive data structures/constraints/indexes/policies needed for the approved P5-A4 boundary, including as required:

- payroll-calendar identity/version completion;
- pay-group version/routing completion;
- deterministic period-generation support;
- frequency configuration;
- milestone/cut-off/payment-date rules and generated evidence;
- weekend/holiday adjustment evidence;
- publication/versioning/compatibility support; and
- tenant/RLS/audit support directly required by those additions.

## Excluded

V038 must not contain unrelated E03+ capability, calculation/results/payment/accounting/statutory-return behavior, or changes belonging to already-closed Foundation packages.

## Closure rule

If P5-A4 product merge uses V038, V038 becomes committed and immutable at that merge. If P5-A4 closes without requiring a migration, closure reconciliation must explicitly release V038 as unused rather than creating an empty migration.
