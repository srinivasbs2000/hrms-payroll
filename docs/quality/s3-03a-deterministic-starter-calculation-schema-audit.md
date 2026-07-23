# S3-03A Deterministic Starter Calculation Schema Audit

## Decision

The Sprint 0 calculation tables are retained and upgraded in place. V025 does
not create a parallel calculation model.

Historical request/result/component/trace rows remain schema version `0`.
Only rows written by the new controlled command use schema version `1`.

## Controlled command

```sql
payroll_calc.calculate_sealed_payroll(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_expected_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_actor varchar,
  p_calculated_at timestamptz
)
```

The command:

1. validates tenant context and optimistic cycle version;
2. accepts only `REGULAR` cycles in `INPUTS_SEALED`;
3. recomputes and compares the complete V024 snapshot-set hash;
4. accepts only canonical input payload schema version `1`;
5. supports INR fixed monthly non-statutory components only;
6. calculates components in salary-structure sequence order;
7. applies the component rounding scale before aggregation;
8. writes request, result, component and trace records in one transaction;
9. computes deterministic component, trace, result and result-set hashes without generated request IDs or timestamps;
10. advances the cycle atomically to `CALCULATED`; and
11. returns the existing completed response for an exact idempotent replay.

## Starter calculation rules

Supported:

- regular monthly payroll;
- INR;
- component type `EARNING`;
- component type `DEDUCTION`;
- formula type `FIXED`;
- full-period proration factor `1.0000000000`;
- line target amount, falling back to component fixed amount;
- component rounding scale from `0` to `4`.

Rejected:

- unsealed cycles;
- stale cycle versions;
- another tenant's cycle;
- schema-version-0 snapshots;
- changed or incomplete snapshot sets;
- non-INR payloads;
- unsupported formula types;
- unsupported component types;
- missing or negative source amounts;
- negative net result;
- duplicate active or completed calculation attempts.

Statutory deductions, tax, percentage/formula expressions, proration for partial
periods, retro, off-cycle, final settlement and recalculation are not included.

## Immutability and runtime grants

`payroll_app` has read access to calculation evidence but no direct
`INSERT`, `UPDATE` or `DELETE` access on:

- `payroll_calc.calculation_request`;
- `payroll_calc.payroll_result`;
- `payroll_calc.component_result`;
- `payroll_calc.calculation_trace`.

The runtime role receives only `EXECUTE` on the controlled calculation
function. Existing result/component/trace reject-mutation triggers remain in
place. V025 adds a controlled-mutation trigger to calculation requests.

## Recalculation boundary

V025 intentionally permits only one completed schema-version-1 calculation per
cycle. S3-04 will introduce explicit supersession and will alter that constraint
as part of the controlled recalculation design.

## Migration compatibility

- V001-V024 are unchanged.
- Existing rows are retained as schema version `0`.
- Existing result rows remain schema version `0` without rewriting their immutable
  payload or lineage.
- Existing completed requests are summarized without recomputing historical
  result hashes.
- New strict hash and lineage checks apply to schema-version-1 records.
