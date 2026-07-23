# S3-02A input-snapshot schema audit

## Existing foundation

- V007 created `payroll_ops.input_snapshot` with a JSONB payload, SHA-256-shaped hash, seal timestamp, cycle ID and payroll-assignment ID.
- V013 added the population-member composite foreign key, snapshot/result consistency constraints and unconditional update/delete immutability.
- V021 preserved snapshot UUIDs and repointed assignment lineage to the exact payroll-assignment version.
- V023 introduced immutable population-resolution attempts and decisions, plus exact relationship, profile, pay-group assignment and salary assignment lineage on active population members.

## Confirmed gaps

1. A snapshot could not identify the exact V023 resolution attempt or decision that produced it.
2. Direct runtime INSERT remained possible even though sealing must be a controlled lifecycle transition.
3. The uniqueness key permitted multiple hashes for the same cycle member.
4. The database did not verify that `snapshot_hash` matched canonical JSONB payload text.
5. The cycle did not retain seal timestamp, actor, snapshot count or combined set hash.
6. There was no database command that revalidated configuration between population resolution and sealing.
7. Existing snapshots had no explicit payload schema version.

## V024 decision

V024 extends the existing snapshot table rather than replacing it. It preserves all historical snapshot IDs, backfills relational population evidence, normalizes legacy payload hashes, and records legacy payloads as schema version 0.

New snapshots are produced only by `payroll_ops.seal_payroll_inputs`, use schema version 1, are one-per-active-member, and include deterministic pay-period, pay-group, relationship, assignment, profile, pay-group assignment, salary assignment, salary-structure and component-line data.

The command locks the cycle, checks the numeric ETag, verifies a completed non-empty active population, rejects configuration drift, inserts the full snapshot set atomically, computes a deterministic combined hash and advances the cycle from `POPULATION_RESOLVED` to `INPUTS_SEALED`.

## Explicit non-goals

- calculation requests or payroll results;
- statutory or tax inputs;
- attendance/leave integration;
- reopening or resealing after successful sealing;
- API/controller implementation; and
- changing V001-V023.
