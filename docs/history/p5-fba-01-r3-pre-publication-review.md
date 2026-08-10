# P5-FBA-01 G06 R3 Pre-Publication Review

**Date:** 10 August 2026
**Capability:** `P5-FBA-01 — Foundation Banking & Authority`
**Decision:** RELEASE READY — product publication may proceed
**Backend G05 head:** `0c3be43bc8268d60b97973e9faa4f98e716ec26f`
**Web G05 head:** `062e3a1e43e311a79687ae5645ae2934b8e5cb35`
**G05 evidence SHA-256:** `0f06ffbf06c886740d309007cba20fd1f988f728d35d0bebfc75d29e2a003e4d`
**Migration:** V035 reserved to P5-FBA-01; V001-V034 immutable; V036 unreserved

## Critical-review conclusions

No product-code or migration blocker remains after G01-G05.

The review confirms:
- employer bank accounts use stable identities plus effective-dated versions;
- bank-account plaintext has no database column and application cryptography is AES-256-GCM with HMAC-SHA-256 equality fingerprinting;
- normal responses expose masking only and reveal is separately permissioned, audited and no-store;
- bank and signatory lifecycle transitions enforce maker/verifier/final-approver segregation;
- final bank/signatory activation is effective-date and active-owner constrained;
- authorised-signatory scopes are purpose scoped with optional currency/amount limits and remain separate from application access;
- bounded readiness is explicitly `BANKING_AND_SIGNATORY_ONLY`, not global payroll readiness;
- all five P5-FBA tenant tables use ENABLE/FORCE RLS and direct payroll-app UPDATE/DELETE is revoked;
- OpenAPI, Keycloak and runtime permission contracts are guarded by the G04 contract verifier;
- standalone React UI and cross-repository browser E2E are green.

## Frozen acceptance reconciliation

| # | Acceptance outcome | Result |
|---:|---|---|
| 1 | tenant-safe bank identity/version | PASS |
| 2 | legal-entity/PSU owner validation | PASS |
| 3 | currency/effective dates | PASS |
| 4 | AES-GCM ciphertext / no plaintext DB | PASS |
| 5 | HMAC fingerprint equality/duplicate control | PASS |
| 6 | masked list/current/history | PASS |
| 7 | restricted audited no-store reveal | PASS |
| 8 | maker/verifier/final segregation | PASS |
| 9 | stale If-Match / conflicting idempotency | PASS |
| 10 | default uniqueness owner/currency | PASS |
| 11 | no hard delete / immutable approved history | PASS |
| 12 | signatory legal authority separate from system access | PASS |
| 13 | purpose/effective delegated authority | PASS |
| 14 | optional amount/currency limits | PASS |
| 15 | expired/suspended/out-of-limit rejection | PASS |
| 16 | bounded bank/signatory readiness | PASS |
| 17 | RLS cross-tenant denial on all five tables | PASS |
| 18 | direct controlled-transition mutation bypass denied | PASS |
| 19 | audit/event payloads exclude plaintext bank secrets | PASS |
| 20 | OpenAPI/Keycloak/runtime contract alignment | PASS |
| 21 | React masking and permission gating | PASS |
| 22 | cross-repository browser E2E | PASS |
| 23 | backend + UI regression | PASS |

## Governance reconciliation found during R3

The frozen scope's product-path list did not enumerate five direct verification/support artifacts that were introduced during the explicitly authorized G04/G05 contract and browser-integration stages:

- `.github/workflows/ci.yml`;
- `deploy/local/smoke/auth-smoke.ps1`;
- `scripts/verify-foundation-banking-contracts.mjs`;
- `database/flyway/e2e/fixtures/S03_001__sprint_3_executable_payroll.sql`;
- `database/flyway/e2e/verify_smoke_fixture.sql`.

They do not expand Payroll business scope, add a backend module, allocate another migration or change payment/calculation behavior. They are retained as bounded G04/G05 verification-support ownership and are recorded in the scope authority before publication.

The continuation handoff also retained stale checkpoint-table values from the earlier repository-split closure even though its header, canonical program status and thread registry recorded P5-FBA-01 as active. G06 corrects that supporting-document drift before publication.

## Story decision before merge

No canonical story status changes before product merge.

After both product repositories merge:
- `PLN-E01-008` is eligible for IMPLEMENTED;
- `PLN-E01-009` is eligible for IMPLEMENTED;
- `PLN-E01-011` remains PARTIALLY IMPLEMENTED because complete entity-scoped application approver authorization/delegation is outside this capability;
- `PLN-E01-012` remains PARTIALLY IMPLEMENTED because only banking/signatory readiness is delivered.

`PLN-E01-010` remains unchanged and is not part of P5-FBA-01.
