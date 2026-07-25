# S4-01B — V027 Statutory Rule Foundation

## Scope

This increment establishes the jurisdiction-neutral statutory bounded context and database foundation without introducing country-specific calculation logic.

## Delivered

- new `statutory` PostgreSQL schema owned by `payroll_owner`
- stable statutory-rule identities
- immutable effective-dated statutory-rule versions
- employee and employer liability portions
- fixed, percentage and slab method shapes
- ordered, non-overlapping slab bands
- controlled approval and end-dating
- tenant-safe composite foreign keys
- forced RLS and least-privilege runtime grants
- new `backend/statutory-deductions` Spring Modulith module skeleton
- migration integration tests and vertical-slice verification updates

## Explicit exclusions

- employee statutory profiles and registrations
- eligibility, exemptions, elections and declarations
- taxable-component mappings
- country-specific rates, thresholds or formulas
- payroll input-snapshot changes
- statutory calculation results or balances
- remittance, returns, payslip or UI changes

## Verification gates

1. `V001–V026` remain unchanged.
2. A clean `V001–V027` Flyway installation succeeds on PostgreSQL 17.
3. Statutory migration integration tests pass.
4. Cross-tenant rows and links are rejected or hidden.
5. Invalid fixed, percentage and slab shapes fail.
6. Empty and gapped rule versions cannot be approved.
7. Approved versions cannot overlap.
8. Approved configuration cannot be directly mutated.
9. Runtime schema creation and direct update/delete remain denied.
10. Full Maven verification and architecture rules pass.

## Follow-on

S4-01C will add the application/API layer for rule identity, version, portion and slab configuration only after this database foundation is green.
