# Payroll calculation API

## Purpose

S3-03B exposes the V025 deterministic starter calculation through the dedicated
calculation-engine module. The application never inserts or updates calculation
evidence directly.

## Endpoints

```text
POST /api/v1/payroll-cycles/{cycleId}/calculation
GET  /api/v1/payroll-cycles/{cycleId}/calculation-requests
GET  /api/v1/payroll-cycles/{cycleId}/results
GET  /api/v1/payroll-cycles/{cycleId}/results/{resultId}
GET  /api/v1/payroll-cycles/{cycleId}/results/{resultId}/trace
```

The command requires:

- `Idempotency-Key`;
- numeric cycle `If-Match`;
- `payroll-calculation.execute`; and
- a V025-compatible input-sealed regular payroll cycle.

The command calls only:

```text
payroll_calc.calculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
)
```

## Permissions

```text
payroll-calculation.execute
payroll-result.read
payroll-result.trace.read
```

Development mapping:

- `payroll.admin`: execute, result read and trace read;
- `payroll.smoke`: result read and trace read only.

## Idempotency

The V025 database request is authoritative. Replaying the same key with the same
cycle and expected version returns the persisted calculation summary. Reusing
the key with a different request returns a conflict.

Audit and outbox evidence is written only when the cycle gains a new active
calculation request. A replay of a completed request does not duplicate the
`CALCULATED` audit event or `PayrollCalculated` outbox event.

## Result evidence

The result detail includes:

- exact calculation request;
- payroll cycle and assignment-version lineage;
- input snapshot and hash;
- salary-structure version;
- gross, deduction and net amounts;
- canonical result payload and hash; and
- ordered immutable component results.

The trace endpoint returns the immutable V025 calculation steps and payload
hashes.

## Scope limitations

This API supports only the V025 fixed monthly, INR, non-statutory starter
calculation. It does not introduce statutory tax, retro, off-cycle processing,
final settlement, banking, GL integration or legal/final payslips.

Recalculation and result supersession remain S3-04.
