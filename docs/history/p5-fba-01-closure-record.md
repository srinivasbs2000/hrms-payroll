# P5-FBA-01 Post-Merge Closure Record

**Date:** 10 August 2026
**Capability:** `P5-FBA-01 — Foundation Banking & Authority`
**Backend product PR:** #44
**Backend product merge:** `a0234d94ef280a41a744ea6e8483f786a497d211`
**Backend publication head:** `088484b1855b5af6f0c67dfe1426204b9a720b13`
**Web product PR:** #12
**Web product merge:** `5c45ab41ee3cb4466fac822c04c771f5de0ba119`
**Status-closure PR:** #45
**G05 evidence SHA-256:** `0f06ffbf06c886740d309007cba20fd1f988f728d35d0bebfc75d29e2a003e4d`

## Delivered

P5-FBA-01 closes the employer-banking and authorised-signatory portion of
Original P5-A3:

- tenant-safe effective-dated employer bank-account identities/versions;
- application-level AES-256-GCM bank-account encryption with key versioning;
- HMAC-SHA-256 equality/duplicate fingerprinting and masked routine reads;
- restricted audited no-store plaintext reveal;
- legal-entity/PSU ownership and owner/currency/default controls;
- maker -> verifier -> final approver segregation;
- effective-dated signatory identities and immutable purpose/currency/amount scopes;
- deterministic authority evaluation;
- bounded banking/signatory readiness;
- OpenAPI, Keycloak, runtime and standalone React contract alignment;
- cross-repository browser E2E with distinct maker/verifier/approver identities.

## G06 publication hardening

The first G06 publication attempt stopped safely before merge because backend
PR #44's secret scan classified ten synthetic test `Idempotency-Key` literals
as `generic-api-key`. Hosted job/SARIF review confirmed no production
credential or bank cryptographic secret was involved. The existing exact
Gitleaks fingerprint-ignore mechanism was extended for only those ten findings.

The same attempt also proved that the standalone UI PR's cross-repository E2E
is intentionally bound to backend `main`. Therefore publication was sequenced:
backend PR #44 merged first, the failed UI workflow was rerun against the new
backend main, and web PR #12 was merged only after that rerun was green.

A later hosted Flyway/RLS attempt was also classified as infrastructure-only:
Maven Central returned HTTP 429 while resolving the Maven Enforcer plugin, so
the database-migrations module was skipped. The failed hosted job was rerun
without product or migration changes. The resume automation was also hardened
to poll for GitHub check registration before treating `no checks reported` as
an actionable CI state.

These controls are captured permanently in the automation/package governance
checklist.

## Canonical story reconciliation

- `PLN-E01-008`: IMPLEMENTED.
- `PLN-E01-009`: IMPLEMENTED.
- `PLN-E01-011`: remains PARTIALLY IMPLEMENTED; complete entity-scoped
  application approver authorization/delegation remains outside P5-FBA-01.
- `PLN-E01-012`: remains PARTIALLY IMPLEMENTED; P5-FBA-01 adds only
  banking/signatory readiness to the earlier jurisdiction/registration readiness.
- `PLN-E01-010`: unchanged; immutable configuration snapshot closure remains open.

Post-reconciliation 450-story totals:

- 16 IMPLEMENTED;
- 156 PARTIALLY IMPLEMENTED;
- 88 NOT EVIDENCED;
- 159 NOT STARTED;
- 31 requiring LEGAL/DOMAIN REVALIDATION.

## Authority release

After status-closure PR #45 merges:

- P5-FBA-01 is CLOSED;
- active product write owner is NONE;
- V035 is committed and immutable;
- V036 is UNRESERVED;
- no next capability is activated by this closure;
- `P5-FSR-01 — Foundation Snapshot & Readiness Closure` is only the
  recommended next reconciliation candidate and requires separate activation.

The product remains greenfield with no evidenced production deployment or live
customer payroll migration.
