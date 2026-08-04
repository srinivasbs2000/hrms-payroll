# P5-A2 compensation catalogue and named payroll bases

Status: implementation candidate prepared from activation commit
`e9e297de5e59762f3701ce39ca2295e1839d7d16`.

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
  are configuration metadata for later calculation-plan consumption.

## Required verification

Run the repository's supported toolchain after applying the package:

```powershell
.\mvnw.cmd -pl backend/database-migrations test
.\mvnw.cmd -pl backend/compensation test
.\mvnw.cmd -pl backend/payroll-boot -am test
npm --prefix frontend/payroll-web test
npm --prefix frontend/payroll-web run build
npx --yes @redocly/cli lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
```

The compatibility test must prove that the V031 definition of
`payroll_calc.calculate_sealed_payroll` is byte-for-byte unchanged after V032
and that BASIC, HRA, and SPECIAL_ALLOWANCE results, trace evidence, and hashes
remain deterministic.

## Explicit exclusions

P5-A2 does not implement formula execution through named bases, salary-package
or CTC construction, component eligibility, statutory rates or legal truth,
employee compensation readiness, retro processing, banking, accounting,
deployment, or dependency upgrades.
