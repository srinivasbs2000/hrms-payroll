# S3-04A Controlled Recalculation and Supersession Schema Audit

## Decision

V026 evolves the V025 calculation-request model in place. It does not rewrite
V025 and does not duplicate result, component or trace tables.

A recalculation is a new immutable evidence set. The prior request and all prior
`payroll_result`, `component_result` and `calculation_trace` rows remain stored
and readable. The payroll cycle identifies the current attempt through
`active_calculation_request_id`.

## Attempt lineage

Schema-version-1 requests are classified as:

- `INITIAL`, attempt 1, with no predecessor or reason; or
- `RECALCULATION`, attempt 2 or greater, with one same-cycle predecessor and a
  mandatory operator reason.

Each request stores:

- `calculation_kind`;
- deterministic `attempt_no`;
- `supersedes_request_id`;
- `recalculation_reason`; and
- `engine_version` (`STARTER_FIXED_V1`).

A predecessor may have only one direct successor. Further recalculations form a
linear chain from the current active request.

## Legacy compatibility

Schema-version-0 rows retain:

- `calculation_kind = LEGACY`;
- `attempt_no = 0`;
- no predecessor or reason; and
- `engine_version = LEGACY`.

The column defaults remain legacy-safe for historical fixtures. A controlled
before-insert trigger classifies schema-version-1 requests created by the V025
initial-calculation function as `INITIAL`, attempt 1, without requiring V025 to
be modified.

## Controlled command

`payroll_calc.recalculate_sealed_payroll(...)` requires:

- matching tenant context;
- a calculated regular cycle in an open period;
- the expected cycle version;
- the current active completed schema-version-1 request;
- immutable canonical V024 snapshots whose combined hash still matches the
  cycle and parent request;
- a unique idempotency key and request hash;
- a reason between 8 and 500 characters; and
- a timestamp not earlier than sealing or prior completion.

It reruns the fixed monthly INR engine from the sealed snapshots, writes a new
request/result/component/trace set, and atomically advances the cycle active
request and version.

## Determinism

The engine and inputs are unchanged, so a same-engine recalculation produces
the same per-assignment result hashes and result-set hash. New request, result,
component and trace identifiers are evidence identities and are deliberately
excluded from deterministic payload hashes.

## Concurrency and idempotency

The command locks the cycle and parent request. It rejects stale versions,
non-active parents and duplicate direct successors. Replaying the same
idempotency key, request hash and reason returns the already completed attempt
without adding evidence.

## Deferred to S3-04B

V026 does not add REST endpoints, permissions, Keycloak mappings, OpenAPI or UI.
Those are application-layer concerns for S3-04B.
