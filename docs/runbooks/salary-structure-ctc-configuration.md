# Salary Structure, CTC and Eligibility Configuration Runbook

**Capability:** P5-A3
**Boundary:** design-time configuration and validation only

## Workbench

The `/salary-structures` route is the single compensation-design workbench. It
contains permission-gated panels for salary structures, CTC policies, typed
eligibility rules, and immutable simulation evidence. It does not create an
employee salary assignment or official payroll result.

## Publication sequence

1. Approve exact pay-component versions and any required named-base memberships.
2. Create and maker-check a CTC-policy version containing all four cost views.
3. Optionally create and maker-check a typed conjunctive eligibility-rule version.
4. Create a schema-1 salary-structure draft with one final residual line.
5. Run deterministic design-time simulation using synthetic or supplied facts.
6. Resolve blockers and bind an exact passing validation fingerprint.
7. A different authorised checker approves the bound structure version.

Every simulation screen must show `DESIGN-TIME SIMULATION — NOT AN OFFICIAL
PAYROLL RESULT`. Minimum-wage status remains structural-only until a separately
authorised, legally revalidated rule pack exists.

## Permission model

`payroll.admin` receives the complete P5-A3 configuration lifecycle. The
`payroll.smoke` user is read/evaluate/simulate only and cannot create, approve,
retire, or bind validation evidence.
