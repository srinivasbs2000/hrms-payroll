# P5-A3 Salary Structure, CTC, Eligibility and Simulation Quality Record

**Status:** MERGED, VERIFIED AND CLOSED
**Verified:** 6 August 2026
**Branch:** `feature/p5-a3-salary-structure-ctc-eligibility-simulation` retained
**Activation HEAD:** `0e5471f31b7c9d65cd735e2055148d802fb1b960`
**Migration:** V033 committed and immutable

## Delivered capability

P5-A3 provides versioned salary-structure design, CTC policies, typed
eligibility-rule configuration, deterministic design-time simulation and
immutable validation evidence. It does not execute official payroll, mutate
employee assignments, or encode legal/statutory rates.

## Verified evidence before G07

- G06 exact changed-path boundary: 49 paths.
- Redocly aggregate OpenAPI lint: valid.
- Maven reactor: build success.
- Backend: 220 tests, 0 failures, 0 errors, 0 skipped.
- Frontend focused G06 tests: 25 passed.
- Frontend complete tests: 85 passed.
- Frontend production build: passed.
- PostgreSQL 17.10 Testcontainers.
- Flyway: 33 migrations through V033.
- Persistent database mutation outside ephemeral test containers: false.
- Git/GitHub writes: false.

## Acceptance reconciliation

- V020/V021 salary-structure and assignment UUID lineage is preserved.
- V001–V032 remain immutable.
- New tenant-owned tables use tenant-safe foreign keys and ENABLE/FORCE RLS.
- Maker-checker, overlap, lifecycle, retirement and optimistic-concurrency
  negative paths are covered by migration, contract and API tests.
- Eligibility facts are limited to `TEXT`, `NUMBER`, `DATE` and `UUID`.
- Simulation outputs are deterministic and explicitly non-payroll.
- Approval requires the current passing validation fingerprint.
- V025/V026 calculation behaviour remains covered by the existing
  `GoldenPayrollTest` and `CompensationCatalogueCalculationCompatibilityIT`
  suites, both executed by the full Maven reactor after V033.
- OpenAPI, Keycloak permissions and frontend routes are aligned.

## Planned-path consolidation

The following initially anticipated classes were not required:

- separate salary-structure create/version request classes;
- separate simulation controller/service/view/comparison classes;
- separate validator class;
- separate simulation-only contract/API test classes;
- separate P5-A3 calculation compatibility class.

Equivalent behaviour and evidence are consolidated into the existing
salary-structure controller/service/view and contract/API suites. The existing
calculation compatibility suites execute against the complete V033 migration
set.

## Publication and closure evidence

- implementation PR #32 merged as `b4f3013e1d7404d09eac64a305ad3736e5a28a5c`;
- PR #33 merged React test-hygiene changes as
  `23df1f7a11f4090cef8715eba7104f5b1138b760`;
- PR #33 changed two test files and no production code;
- V033 is immutable;
- no active P5-A3 owner remains;
- no next capability, migration or branch deletion is authorised.
