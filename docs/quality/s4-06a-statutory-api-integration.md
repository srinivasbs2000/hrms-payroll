# S4-06A Statutory API Integration Closure

**Status:** ACTIVE — CRITICAL-REVIEW HARDENING, UNCOMMITTED
**Owner:** Thread 7
**Branch:** `quality/s4-06a-statutory-api-integration`
**Activation baseline:** `main` at `961465cb551f3757a6f51f1322e6b46c32317b16`
**Migration:** None
**V032:** Unreserved
**Product/contract change:** None authorized

## Purpose

S4-06A closes the statutory API integration gap with a real secured
HTTP-to-PostgreSQL integration suite in the `payroll-boot` composition module.
It verifies the already-committed V027-V030 statutory implementation against
the complete current Flyway chain through V031.

This increment does not introduce statutory product behavior. It proves the
existing controller, service, database functions, runtime roles, RLS, exact
money, idempotency, concurrency, audit and outbox behavior as one deployed
application path.

## Exact scope

New:

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`

Living authorities updated:

3. `docs/design/hrms-payroll-master-design.md`
4. `docs/design/decision-register.md`
5. `docs/governance/thread-registry.md`
6. `docs/runbooks/project-continuation-handoff.md`

No other path is authorized.

## Test architecture

`StatutoryApiIT` is deterministically derived from the repository's existing
`PayrollOperationsApiIT` fixture. It therefore retains the already-green:

- PostgreSQL 17 Testcontainer;
- `payroll_owner`, `payroll_migrator` and `payroll_app` role boundary;
- Flyway migration through the complete classpath migration set;
- Spring Boot and MockMvc composition;
- JWT tenant and authority mapping;
- real payroll cycle, population, input-sealing and calculation flow.

The new fixture adds approved neutral statutory rules, exact assignments,
component classification and a covering balance year. Commands are executed
only through the secured HTTP API. Independent JDBC assertions run as
`payroll_app`.

## Covered lifecycle

The focused suite proves:

1. calculated payroll preparation;
2. statutory evaluation and exact result reads;
3. idempotent evaluation replay;
4. changed replay conflict;
5. initial ledger posting and all evidence reads;
6. idempotent posting replay;
7. stale-version conflict;
8. signed correction using `-10.1250` and `0.1000`;
9. corrected balances and zero-variance reconciliation;
10. exactly-once audit and outbox evidence;
11. unauthenticated and missing-permission rejection;
12. tenant isolation for commands and reads;
13. numeric money-token rejection;
14. zero-delta and short-reason rejection;
15. a same-version two-request posting race with one success and one conflict;
16. runtime `payroll_app` non-superuser/non-bypass evidence;
17. ENABLE/FORCE RLS on every statutory tenant table.

## Expected deterministic values

For the synthetic monthly result of `90000.0000`:

- social employee portion: `9000.0000`;
- social employer portion: `500.0000`;
- tax employee portion: `8000.0000`;
- evaluation employee total: `17000.0000`;
- evaluation employer total: `500.0000`;
- post-statutory net: `73000.0000`;
- corrected employee total: `16989.8750`;
- corrected employer total: `500.1000`.

All public statutory money values are asserted as JSON strings.

## Activation recovery record

The v1.0 activation attempt failed safely during fixture preparation before
full Maven verification. PostgreSQL rejected the open-ended statutory rule
assignments because their exact profile, payroll-assignment and statutory-rule
versions end on `2027-01-01`. The component classification had the same
open-ended range against its exact component version.

Recovery v1.1 bounds both assignment rows and the component classification at
`2027-01-01`. This is a test-fixture correction only. No production Java,
migration, OpenAPI, Keycloak, dependency, frontend or CI change is introduced.

## Activation recovery v1.2 record

Recovery v1.1 corrected the fixture ranges successfully. The focused suite then
executed all four tests, with three passing and one failing only because the
generated test expected HTTP `400` for semantic correction validation.

The committed API behavior maps `IllegalArgumentException` validation failures
to RFC 9457 HTTP `422 Unprocessable Entity`. Therefore:

- numeric JSON money tokens remain HTTP `400` because JSON deserialization
  fails before command processing;
- zero/zero signed deltas are HTTP `422`;
- a correction reason shorter than eight characters is HTTP `422`.

Recovery v1.2 changes only those two semantic-validation expectations in
`StatutoryApiIT`. It does not change product Java, database behavior, contracts,
security, dependencies, frontend, CI or migration scope.

## Independent critical review v1.3 record

The v1.2 recovery evidence completed the focused suite at 4/4 and the complete
backend Maven reactor successfully. Independent review nevertheless identified
two proof-strength gaps against the approved S4-06A acceptance matrix:

1. audit and outbox evidence was counted as one combined set rather than
   asserting each required action/event type exactly once;
2. correction evidence did not directly assert signed ledger lineage or prove
   that the latest balance snapshots, reconciliation and remittance summaries
   reconcile to the corrected cycle totals.

Recovery v1.3 hardens only `StatutoryApiIT` and this quality record. It adds
database-level assertions for:

- exactly one `EVALUATED`, `POSTED` and `CORRECTED` audit action;
- exactly one `StatutoryEvaluated`, `StatutoryLedgerPosted` and
  `StatutoryLedgerCorrected` outbox event;
- one correction ledger entry linked to the exact source result and source
  entry, with `-10.1250` and `0.1000` signed deltas;
- exact correction reconciliation totals and zero variance;
- latest per-rule balance and remittance evidence reconciling to
  `16989.8750`, `500.1000` and `17489.9750`.

This is critical-review test hardening only. It does not change product Java,
database behavior, migrations, contracts, security, dependencies, frontend or
CI.

## Verification gates

The activation package runs:

```text
mvn -pl backend/payroll-boot -am
    -Dit.test=StatutoryApiIT
    -Dsurefire.failIfNoSpecifiedTests=false
    -Dfailsafe.failIfNoSpecifiedTests=false
    verify
```

followed by:

```text
mvn --batch-mode verify
```

It additionally validates Java syntax, Docker availability, exact changed
paths, an empty index, `git diff --check`, unchanged V001-V031, absent V032,
unchanged production/contracts/security/frontend/dependency/CI paths, and an
unchanged branch HEAD.

## Stop-and-split rule

Stop S4-06A and preserve evidence if green verification requires any:

- production Java change;
- migration or V032 reservation;
- OpenAPI or Keycloak change;
- dependency/POM change;
- frontend, Playwright, deployment or workflow change;
- RLS weakening or elevated runtime role;
- jurisdiction-specific legal behavior.

Such work requires a separately approved defect or design increment.

## Publication state

Activation creates local uncommitted work only. It does not stage, commit, push,
create a pull request, request review, enable auto-merge or merge.

S4-06B remains planned and unauthorized.
