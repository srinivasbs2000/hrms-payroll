# Sprint 4 Closure Report

## Final decision

**Functional implementation:** complete and merged
**Product merge:** PR #19 at `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Final feature-branch head:** `b2a220461cf5ba581b5f67e7619ec146bf7982ed`
**Final verified feature-head CI:** `payroll-baseline` run 81, success
**Migrations:** V001-V030 committed and immutable
**Automation closure:** incomplete; S4-06A and S4-06B remain controlled quality debt

Sprint 4 is functionally delivered and merged. This report must not be read as
proof that every test layer is complete.

## Delivered scope

- V027 jurisdiction-neutral statutory rule identities and approved versions;
- employee and employer liability portions, thresholds, caps and slabs;
- V028 employee statutory profiles and exact rule assignments;
- V029 deterministic evaluation against exact payroll and statutory lineage;
- immutable statutory input, evaluation and result evidence;
- V030 append-only ledger posting and signed corrections;
- PTD, cycle and YTD balances;
- reconciliation and remittance-preparation evidence;
- secured execution and evidence APIs;
- exact permissions and Keycloak development mappings;
- statutory OpenAPI contract;
- permission-aware statutory operator workspace;
- decimal-string monetary transport with Java `BigDecimal`;
- local smoke preparation and identity-boundary tooling.

## Explicit exclusions retained

Sprint 4 does not implement:

- jurisdiction-specific PF, EPS, EDLI, ESI, professional tax, labour welfare
  fund, NPS or salary TDS rules;
- legal tax interpretation;
- statutory filing, returns, acknowledgements or authority integration;
- remittance payment or settlement;
- retro or off-cycle payroll;
- recoveries, final settlement, banking, accounting or legal payslips.

## Verification evidence

The final Sprint 4 head completed `payroll-baseline` run 81 successfully. The
repository and PR evidence report green Maven, migration/RLS, frontend,
OpenAPI, identity, dependency, secret and SBOM gates.

The repository contains:

- V027-V030 migration integration tests;
- statutory controller and HTTP-support tests;
- exact money serialization tests;
- statutory frontend tests;
- local smoke and identity-boundary scripts;
- the generic Payroll Playwright E2E suite inherited from Sprint 3.

## Evidence limitations

### Missing S4-06A test layer

No dedicated `backend/payroll-boot` statutory API integration test currently
starts Spring Boot against PostgreSQL 17 migrated through V030, exercises the
actual secured statutory HTTP lifecycle and asserts the resulting immutable
database state.

Migration integration tests, unit/controller tests and manual smoke are not
substitutes for this layer.

### Missing S4-06B test layer

PR #19 did not add a statutory-specific Playwright scenario. The generic
Payroll browser E2E suite must not be described as dedicated statutory E2E.

### Manual smoke record

PR #19 metadata records successful statutory operator and identity-boundary
checks. The committed manual-smoke file is an uncompleted template: tester,
reviewer and evidence fields remain blank. Therefore a completed signed manual
record is not repository evidence and must not be fabricated retrospectively.

## Approved quality-closure sequence

1. Complete S4-06A as a test-first, no-migration, no-production-change initial
   increment.
2. Complete or separately authorise S4-06B.
3. Update this report and the running handoff with actual committed evidence.
4. Select the next product feature only after repository authorities and backlog
   approve it.

## Current status

Sprint 4 implementation is closed. Threads 2, 3 and 4 are historical and
closed. Thread 5 remains recovery/handoff only. A new Thread 6 is planned to
own S4-06A after Phase A documentation reconciliation is merged and ownership
is explicitly transferred.
