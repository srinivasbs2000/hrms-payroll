# S3-01A Payroll Population Schema Audit

## Decision

A forward-only V023 migration is required. The existing Sprint 0 payroll-
operations model is retained and extended; no replacement cycle, population,
snapshot, result or trace model is introduced.

## Existing controls retained

- `payroll_ops.payroll_cycle` remains the cycle aggregate table.
- `payroll_ops.population_member` remains the active included-member set.
- `payroll_ops.input_snapshot` remains the later sealed-input boundary.
- V021 already renamed population and result lineage to exact
  `payroll_assignment_version_id` values.
- V018 enforces pay-group/calendar compatibility.
- V022 enforces that the exact pay-group version contains the cycle period and
  prevents dependent end dating.
- Existing calculation-result and trace tables remain untouched.

## Confirmed gaps

### Missing immutable resolution evidence

The baseline population table records only the current included set. Re-running
population resolution would overwrite that operational view without preserving
which candidates were evaluated, why they were included or excluded, and which
attempt was active.

### Incomplete exact configuration lineage

An exact assignment version is necessary but not sufficient for repeatable
payroll execution. A population decision must also preserve the exact:

- relationship version;
- employee payroll profile;
- pay-group assignment;
- salary assignment; and
- salary-structure version.

Without these identifiers, a later sealed input cannot prove which approved
configuration made the employee eligible.

### Direct runtime DML remained possible

The original grants allowed the application role to insert and mutate payroll
cycles and population members directly. Sprint 3 requires controlled commands
so lifecycle, version and evidence changes remain atomic.

## V023 design

V023 adds:

1. `payroll_ops.population_resolution` — one immutable evidence header for each
   deterministic attempt;
2. `payroll_ops.population_decision` — one immutable candidate decision with a
   stable reason code and exact configuration lineage;
3. exact configuration columns on the existing active
   `population_member` table;
4. a controlled regular-cycle creation function;
5. an ETag-aware population-resolution function;
6. active-resolution lineage on `payroll_cycle`;
7. RLS, tenant-safe foreign keys, indexes, controlled mutation triggers and
   least-privilege grants; and
8. upgrade backfill for any existing population member that can be proven to
   have complete approved lineage.

## Candidate and eligibility rule

The candidate universe is the current non-superseded pay-group assignment for
the cycle's exact pay-group version. One deterministic candidate is chosen per
assignment version.

A candidate is included only when the full payroll period is covered by:

- active assignment and relationship identities;
- approved, non-superseded assignment and relationship versions;
- a READY payroll profile;
- an approved pay-group assignment;
- an approved salary assignment;
- an approved non-empty salary structure; and
- one consistent currency.

The decision ledger preserves exclusions such as `PROFILE_NOT_READY`,
`SALARY_ASSIGNMENT_MISSING`, `SALARY_STRUCTURE_NOT_APPROVED` and range or
supersession failures.

## Repeatability and lifecycle

Population may be resolved in `DRAFT` or `POPULATION_RESOLVED` only. Each call
creates a new immutable resolution/decision attempt and atomically replaces the
active included-member set. Once inputs are sealed, this function refuses to
run; S3-02 will own the later transition.

## Explicit non-goals

V023 does not:

- seal input snapshots;
- calculate payroll;
- create or supersede payroll results;
- implement statutory deductions or tax;
- publish a final payslip; or
- modify V001-V022.
