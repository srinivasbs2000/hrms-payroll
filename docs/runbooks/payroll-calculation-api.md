# Payroll calculation API

## Purpose

S3-04B exposes the V025 deterministic starter calculation and the V026
controlled recalculation function through the dedicated calculation-engine
module. The application never inserts or updates calculation requests, results,
component results or trace evidence directly.

## Endpoints

```text
POST /api/v1/payroll-cycles/{cycleId}/calculation
POST /api/v1/payroll-cycles/{cycleId}/recalculation
GET  /api/v1/payroll-cycles/{cycleId}/calculation-requests
GET  /api/v1/payroll-cycles/{cycleId}/results
GET  /api/v1/payroll-cycles/{cycleId}/results/{resultId}
GET  /api/v1/payroll-cycles/{cycleId}/results/{resultId}/trace
```

The initial calculation command requires:

- `Idempotency-Key`;
- numeric cycle `If-Match`;
- `payroll-calculation.execute`; and
- a V025-compatible input-sealed regular payroll cycle.

It calls only:

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

The recalculation command additionally requires a JSON body with a meaningful
reason between 8 and 500 characters:

```json
{
  "reason": "Approved payroll review rerun"
}
```

It requires `payroll-calculation.recalculate`, a calculated regular payroll
cycle and the current numeric cycle `If-Match`. It calls only:

```text
payroll_calc.recalculate_sealed_payroll(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  varchar,
  timestamptz
)
```

## Permissions

```text
payroll-calculation.execute
payroll-calculation.recalculate
payroll-result.read
payroll-result.trace.read
```

Development mapping:

- `payroll.admin`: execute, recalculate, result read and trace read;
- `payroll.smoke`: result read and trace read only.

## Idempotency and concurrency

The database request is authoritative for both commands. The canonical initial
request hash contains the cycle ID and expected version. The canonical
recalculation request hash contains the cycle ID, expected version and trimmed
reason.

Replaying the same key with the same request returns the persisted calculation
summary. Reusing the key with a different cycle, expected version, reason or
calculation kind returns a conflict. A stale `If-Match` also returns a conflict.

Audit and outbox evidence is written only when the cycle gains a new active
calculation request:

- initial calculation: `CALCULATED` and `PayrollCalculated`;
- controlled recalculation: `RECALCULATED` and `PayrollRecalculated`.

An exact replay of a completed request does not duplicate audit or outbox
evidence.

## Recalculation lineage

Each completed recalculation:

- creates a new immutable calculation request;
- records `calculationKind=RECALCULATION`;
- increments `attemptNo`;
- links `supersededRequestId` to the previously active request;
- stores the approved recalculation reason;
- records `engineVersion=STARTER_FIXED_V1`;
- preserves all prior request, result, component and trace rows; and
- atomically advances the payroll cycle active-calculation pointer.

The calculation-request history endpoint exposes this lineage. Results are
returned newest attempt first while remaining fully addressable by result ID.
Trace reads remain tied to the exact historical result.

## Response

The recalculation response includes:

- payroll cycle ID;
- new calculation request ID;
- superseded request ID;
- attempt number;
- result count and gross, deduction and net totals;
- result-set hash;
- new payroll-cycle version; and
- completion timestamp and actor.

The response `ETag` is the new payroll-cycle version.

## Scope limitations

This API supports only the V025/V026 fixed monthly, INR, non-statutory starter
engine. It does not introduce statutory tax, retro, off-cycle processing, final
settlement, banking, GL integration or legal/final payslips. Recalculation
reuses the sealed V024 inputs and does not reopen or mutate them.
