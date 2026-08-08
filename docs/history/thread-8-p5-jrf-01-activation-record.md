# Thread 8 — P5-JRF-01 Activation and Local Closure Record

**Capability:** `P5-JRF-01`
**Title:** Jurisdiction and Registration Foundations
**Starting main SHA:** `ff581cafce3be5495d93932abfae3931b139358f`
**Branch:** `feature/p5-jrf-01-jurisdiction-registration-foundations`
**Migration reservation:** V034
**Maximum authorized path boundary:** 88 paths
**GitHub assistant boundary:** read-only
**Publication state:** not committed / not pushed / not merged

## Owner activation

The project owner authorized activation from the exact starting SHA, named the
feature branch, reserved V034 and approved the reviewed module/file boundary
and explicit exclusions.

No repeat activation authorization is required for the same local increment.

## Gate history

- G02-A: database foundation GREEN
- G02-B: organisation work-location/jurisdiction API GREEN
- G02-C: override/resolution GREEN
- G02-D v1.1: statutory registration/readiness foundation GREEN
- G02-E: Keycloak permissions GREEN
- G02-F v1.1: OpenAPI GREEN
- G02-G v1.6: frontend GREEN
- G02-H v1.1: secured PostgreSQL API integration GREEN
- G03-A v1.1: parent-jurisdiction and suspension DB hardening GREEN
- Architecture Consistency Checkpoint: PASS WITH TARGETED CLOSURE CORRECTIONS
- AC-G03-B1 v1.3: identifier contract/data minimization GREEN
- AC-G03-B2 v1.2: readiness/operator UI/successor semantics GREEN
- G03-C: pre-publication full-regression/documentation/R3 closure

Intermediate failed/corrected package versions are historical troubleshooting
evidence and are not the completion authority.

## Frozen architecture

- Java 21 / Spring Boot modular monolith
- React 18 / TypeScript / Vite
- PostgreSQL 17 / Flyway
- Keycloak/OIDC
- tenant-safe FKs / FORCE RLS
- runtime non-owner / NOBYPASSRLS / SET LOCAL
- stable identities + immutable effective-dated versions
- half-open effective ranges
- atomic audit/outbox/idempotency
- RFC9457
- PostgreSQL-native integrity retained; Oracle portability is out of scope

## Closure state

G03-C local PASS authorizes preparation of owner-executed publication commands
only. It does not itself stage, commit, push, create a PR, merge, update the
detailed story ledger or release V034/path ownership.

After merge, perform the post-merge status/story reconciliation and explicit
authority release before selecting the next capability.
