# S4-03 / V029 Deterministic Statutory Evaluation

## Scope delivered

- Approved effective-dated mappings from exact pay-component versions into
  jurisdiction/authority assessment bases.
- Controlled classification approval and end-dating with parent-range guards.
- One idempotent statutory evaluation request for an exact active completed
  V025/V026 payroll calculation request.
- Immutable statutory input snapshots tying each evaluated rule to the exact:
  - payroll result and input-snapshot hashes;
  - payroll-assignment identity and version;
  - employee statutory profile and approved version;
  - employee statutory rule assignment; and
  - statutory rule identity and approved version.
- Deterministic FIXED, PERCENTAGE and SLAB portion evaluation.
- Immutable per-portion and per-rule employee/employer liability evidence.
- Immutable per-payroll-result statutory totals and post-statutory net evidence.
- Request-level employee, employer and post-statutory-net totals with an
  evidence-set hash.
- Forced RLS, tenant-safe composite foreign keys and least-privilege grants.
- Focused PostgreSQL 17 Testcontainers coverage and vertical-slice checks.

## Safety boundary

V029 does not rewrite V025/V026 payroll results, component results, traces or
cycle totals. Statutory evidence is persisted in the `statutory` schema and
references the exact active completed payroll calculation request.

The S4-01A audit originally described a schema-version-2 extension to the
V024 input snapshot. At the implemented V029 boundary, V024 inputs and V025/V026
results are already sealed and immutable. V029 therefore creates a separate
`statutory_input_snapshot` that references those exact hashes rather than
rewriting an existing sealed snapshot. This is an explicit architecture
refinement, not an implicit scope change.

`STATUTORY_NEUTRAL_V1` defines the jurisdiction-neutral evaluation order:

1. sum classified component results using `inclusion_percent`;
2. clamp the base to `base_cap_amount` when configured;
3. subtract `threshold_amount`, with a zero floor;
4. apply FIXED, PERCENTAGE or marginal SLAB calculation;
5. apply result minimum and result cap; and
6. apply the rule-version rounding mode and scale.

This evaluator version is evidence, not a claim that the neutral semantics match
any named country's law. Country-specific behavior remains excluded until
explicitly modelled and approved.

The jurisdiction-neutral foundation cannot safely interpret conditional
eligibility or partial/full exemptions. Evaluation therefore fails closed for
those states until a later jurisdiction-specific resolver is explicitly
implemented.

## Deliberately excluded

- Country-specific declarations, elections or exemption factors.
- Statutory period-to-date or year-to-date balances.
- Corrections, reconciliation, remittance, returns or payment files.
- Legal/final payslip publication.
- REST, OpenAPI, Keycloak and UI work.

## Verification commands

```powershell
.\mvnw.cmd -pl backend/database-migrations -am `
  -Dit.test=StatutoryEvaluationMigrationIT clean verify

.\mvnw.cmd --batch-mode clean verify

git diff --check
```

## Expected evidence

- V001-V029 fresh migration and Flyway validation pass on PostgreSQL 17.
- `StatutoryEvaluationMigrationIT` passes without skips.
- A fixed INR 90,000 payroll result deterministically produces:
  - employee statutory total: INR 17,000;
  - employer statutory total: INR 500; and
  - post-statutory net: INR 73,000.
- Idempotent replay returns the original evaluation request.
- Stale optimistic versions, overlapping classifications and direct evidence
  mutation are rejected.
- Tenant-B application traffic cannot read Tenant-A statutory evidence.
- Full Maven reactor and vertical-slice verification pass.
- No files are staged, committed, pushed, merged or used to update the PR by the
  package.
