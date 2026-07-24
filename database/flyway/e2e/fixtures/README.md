# E2E fixture overlays

This directory contains synthetic, non-production fixture overlays applied
after V001–V026 to the isolated `hrms-payroll-e2e` PostgreSQL volume.

## Contract

- Files are applied in ordinal filename order.
- Use names such as `S04_010__statutory_fixture.sql`.
- Do not use Flyway `V` prefixes; these files are not migrations.
- Assume a newly recreated E2E PostgreSQL volume.
- Use deterministic UUIDs and clearly synthetic codes.
- Never include real employee, salary, credential or personal data.
- Every future sprint that adds a new executable business path must extend the
  fixture here and add Playwright or API assertions in the same sprint.
- Do not weaken production constraints, RLS, immutability or lifecycle rules
  to make a fixture load.

## Sprint 3 baseline

`S03_001__sprint_3_executable_payroll.sql` supplies:

- tenant `E2E001`, matching the development Keycloak tenant claim;
- approved organisation hierarchy;
- monthly calendar and two open periods;
- approved pay group;
- approved fixed earning component and salary structure;
- one READY employee included in payroll; and
- one ON_HOLD employee excluded as `PROFILE_NOT_READY`.

`verify_smoke_fixture.sql` proves population, sealing, calculation and
recalculation inside a transaction that is rolled back.
