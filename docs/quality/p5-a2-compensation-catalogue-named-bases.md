# P5-A2 compensation catalogue and named payroll bases

**Status:** MERGED AND VERIFIED
**Activation commit:** `e9e297de5e59762f3701ce39ca2295e1839d7d16`
**Implementation commit:** `c30cb1f2f0c16cd78387bb9551b93825bc7ef688`
**Pull request:** PR #30
**Merge commit:** `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`
**Post-merge workflow:** `payroll-baseline` run `30957450623` — successful

## Delivered behaviour

- Preserves `pay_component.component_type` as the existing calculation-direction
  contract: `EARNING`, `DEDUCTION`, or `INFORMATION`.
- Adds complete schema-version-1 catalogue behaviour to component versions while
  preserving existing approved rows as schema 0.
- Adds stable named payroll-base identities, effective-dated versions, and exact
  component-version/base-version memberships.
- Enforces tenant isolation, append-only history, maker-checker approval,
  optimistic end-dating, one-successor correction lineage, approved range
  non-overlap, and deterministic retirement blockers.
- Serialises membership inclusion percentages as decimal strings with database
  precision `numeric(12,8)`.
- Leaves the V025/V026 deterministic starter calculator unchanged. Named bases
  remain configuration metadata for later calculation-plan consumption.

## Verification evidence

- Flyway V001-V032 installation and validation passed.
- Database migration integration suite: 100 tests passed.
- Compensation contract tests passed.
- P5-A2 API and compatibility integration tests passed.
- Frontend: 16 test files and 67 tests passed.
- Frontend production build passed.
- OpenAPI validation passed.
- Exact reviewed implementation boundary: 46 paths.
- PR checks: 9/9 successful.
- Post-merge main workflow run `30957450623` completed successfully; dependency
  review was skipped on the main push event as expected.

The compatibility evidence preserves the V031 definition of
`payroll_calc.calculate_sealed_payroll` and deterministic BASIC, HRA, and
SPECIAL_ALLOWANCE results, trace evidence, and hashes after V032.

## Explicit exclusions

P5-A2 does not implement formula execution through named bases, salary-package
or CTC construction, component eligibility, statutory rates or legal truth,
employee compensation readiness, retro processing, banking, accounting,
deployment, or dependency upgrades.

## Authority state

P5-A2 product and migration authority is closed. The feature branch is retained
as historical evidence and has no active write ownership. V032 is committed and
immutable. V033, P5-A3, and S4-06B remain unreserved or unauthorised.
