# Payroll input-snapshot API runbook

## Scope

S3-02B exposes the V024 immutable input-sealing command and read-only snapshot
evidence. It does not calculate payroll, create results or render payslips.

## Permissions

- `payroll-cycle.inputs.seal` — seal the active population inputs.
- `payroll-cycle.inputs.read` — list and inspect immutable snapshots.

The development administrator receives both permissions. The smoke operator
receives read-only access.

## Seal inputs

```http
POST /api/v1/payroll-cycles/{cycleId}/seal-inputs
Idempotency-Key: <8-120 characters>
If-Match: "<cycle-version>"
```

Successful response:

```json
{
  "cycleId": "00000000-0000-0000-0000-000000000000",
  "snapshotCount": 1,
  "combinedHash": "<64 lowercase hexadecimal characters>",
  "cycleVersionNo": 2,
  "sealedAt": "2026-07-23T12:00:00Z"
}
```

The response ETag is the returned cycle version.

The command is atomic. It revalidates the active V023 population and exact
configuration, writes one V024 canonical snapshot per included member, records
the cycle-level set hash and advances the cycle to `INPUTS_SEALED`.

Expected failures:

- `404` — cycle is not visible in the current tenant.
- `409` — stale ETag or conflicting idempotency-key reuse.
- `422` — invalid lifecycle state, empty population, existing snapshots or
  configuration drift.
- `403` — tenant mismatch or missing permission.

## List snapshots

```http
GET /api/v1/payroll-cycles/{cycleId}/input-snapshots
```

The list omits the potentially large JSON payload and returns immutable lineage,
hash and sealing metadata.

## Read one snapshot

```http
GET /api/v1/payroll-cycles/{cycleId}/input-snapshots/{snapshotId}
```

The detail response includes the complete canonical `snapshotPayload`. The
payload is evidence, not a mutable API request model.

## Operational controls

- Never insert, update or delete `payroll_ops.input_snapshot` directly.
- Never recalculate a snapshot hash in application code.
- Never resolve population after any snapshot exists.
- Treat the snapshot payload and hashes as immutable audit evidence.
- Use a new payroll cycle when configuration must change after sealing.
