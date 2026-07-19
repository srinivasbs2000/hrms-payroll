# Event reliability runbook

Sprint 1 permits organisation domain-event persistence only after the S1-00 reliability tests pass. Business state, immutable audit evidence, the idempotency response and the outbox envelope are committed in one tenant-scoped database transaction.

## Envelope and ordering

The event UUID is assigned once and is stable across delivery attempts. Every row carries tenant, aggregate type and ID, aggregate version, event type/version, occurrence time, correlation ID, optional causation ID, canonical payload hash and a tenant/aggregate partition key. Dispatchers must preserve order within a partition; they may process different partitions concurrently.

## Dispatch and retry

Workers claim eligible `PENDING` rows with `FOR UPDATE SKIP LOCKED`, set `CLAIMED`, increment `attempts`, and identify themselves. A successful broker acknowledgement changes the row to `PUBLISHED`. A transient failure clears the claim, records a sanitized error, and schedules `next_attempt_at` with bounded exponential backoff and jitter. The event ID never changes.

A worker crash after broker publication but before the database acknowledgement deliberately results in redelivery. Consumers therefore insert `integration.inbox_message` in the same transaction as their effect. The unique `(tenant_id, message_id, consumer_name)` key makes duplicate delivery a successful no-op.

## Poison messages and dead letters

After the configured retry ceiling, the dispatcher atomically marks the outbox row `DEAD` and inserts a tenant-safe `integration.dead_letter` row. Failure detail must not contain credentials, tokens or personal payroll data. Alerts identify the tenant, event ID, event type, attempt count and correlation ID.

Replay requires an authorized operator, an incident reference and a corrected consumer or dependency. Replay changes only replay/dispatch control state; it never edits the event ID, payload or hash. A replayed event is still deduplicated by the consumer inbox.

## Verification

`OutboxInboxReliabilityIT` proves:

- aggregate and outbox rollback/commit atomically;
- envelope identity and correlation/causation metadata remain stable;
- consumer failure rolls back inbox and effects so retry succeeds;
- publish-before-ack recovery produces redelivery; and
- duplicate delivery creates exactly one consumer effect.

No producer may bypass `OutboxWriter`, and no consumer may apply an effect outside the transaction that inserts its inbox record.
