# P5-EIP-01 — Employee Identity, Bank & Payment Readiness

**Status:** ACTIVATED FOR G01 READ-ONLY ARCHITECTURE/SCHEMA/API/UI VERDICT after activation authority merges
**R3 selection date:** 19 August 2026
**Backend/program authority baseline:** `549ca266c736aafc58bfbfe2e57c5af554c4448b`
**UI authority baseline:** `9dbf0d2f700764e2fe577f89142cd6784028f70c`
**Canonical epic:** E05 — Employee Payroll Profile
**Migration state at activation:** V001–V050 immutable; V051 unreserved
**Product write state at activation:** NONE — G01 is read-only

## 1. Fresh R3 selection verdict

Fresh post-P5-EPA-01 reconciliation selects the bounded E05 increment:

**P5-EIP-01 — Employee Identity, Bank & Payment Readiness**

The selected boundary is intentionally narrower than the remaining E05 epic. It closes the employee identity and payment-readiness prerequisites without combining country-specific statutory membership or tax-rule truth into the same increment.

This is sequenced now because P5-EPA-01 completed employee relationship/assignment/compensation binding; secure identifiers and mismatch handling are prerequisites for dependable bank/payment readiness; payment readiness is useful before payment execution; and statutory/tax truth remains separately governed.

## 2. Exact canonical story boundary

| Story | Canonical status at activation | Capability objective |
|---|---|---|
| PLN-E05-005 | NOT EVIDENCED | Securely manage payroll identifiers |
| PLN-E05-006 | NOT EVIDENCED | Resolve name and identity mismatches |
| PLN-E05-011 | NOT EVIDENCED | Verify employee bank accounts and payment instructions |
| PLN-E05-012 | NOT EVIDENCED | Determine employee payment readiness |

Activation changes no canonical story status. All four selected stories are `REQUIRED_PRODUCT_UI`; backend-only evidence cannot close them.

## 3. Required business outcome

### 3.1 Secure payroll identifiers

Support employee payroll identifiers such as PAN, UAN, ESI and PRAN through an extensible identity model without treating every identifier as the same legal object. G01 must determine identity/version/effective-date, validation, verification, duplicate detection and source-authority semantics. Sensitive values must be encrypted or tokenised at rest, masked in routine reads and excluded from normal logs, events and error payloads.

The generic identifier framework must not encode country-specific eligibility, rates, contributions or tax conclusions.

### 3.2 Name and identity mismatch workflow

Distinguish authoritative legal/source identity from observations supplied by bank, statutory, tax or verification sources. Mismatch evidence must be classifiable and reviewable and must never silently overwrite the authoritative legal identity. G01 must define correction routing, source authority, approval, resolution state, evidence retention and audit behavior.

### 3.3 Employee bank account and payment instructions

Employee bank-account identity and payment instructions are separate concepts. G01 must determine the exact model for encrypted/tokenised bank details, masked routine reads, bank/branch routing metadata, verification state/evidence, effective-dated account lifecycle, payment allocation instructions, percentage/fixed/remaining-balance semantics, allocation/overlap validation, currency compatibility, change-impact evidence and duplicate/closed/invalid account prevention.

Employer bank-account foundation authority from E01 remains separate and is not reopened.

### 3.4 Employee payment readiness

Payment readiness must be deterministic and explainable, not a manually maintained boolean. G01 must define readiness for valid verified effective payment instructions, currency compatibility, allocation completeness, unresolved bank/name/identity mismatch, relevant fraud/security/beneficiary restrictions, missing/expired verification evidence, and blocker-versus-warning findings.

P5-EIP-01 determines readiness only. It does not create bank files, submit payments, receive bank acknowledgements, reissue payments or reconcile bank settlement.

## 4. Security and privacy authority

G01 must explicitly review field-level encryption/tokenisation and key boundaries; deterministic lookup/duplicate detection without plaintext exposure; masking/reveal controls; separate read/write/verify/approve/reveal permissions; maker-checker for sensitive bank changes; service-account versus interactive authority; sensitive-read/reveal audit without plaintext leakage; tenant-safe RLS/FK boundaries; PII-safe outbox/idempotency/error contracts; and synthetic/non-production test-data rules.

Existing proven security primitives should be reused where appropriate rather than creating a second incompatible encryption or reveal model.

## 5. UI authority

The separate `srinivasbs2000/hrms-payroll-web` repository is in scope for the future product gate. G01 must inspect the current Employee Payroll workspace and determine the smallest coherent UI boundary for masked identifier management, mismatch review/correction routing, employee bank-account lifecycle and verification, payment allocations, payment-readiness blockers/warnings, permission-aware read-only/reveal behavior and audit/history access.

All four selected stories require real-backend browser E2E before closure. Mock-only UI tests are insufficient.

## 6. G01 mandatory read-only verdict

Activation authorizes no product write. G01 must independently inspect the exact merged backend/UI authority and publish one verdict covering:

1. current reusable persistence/APIs/security primitives;
2. current OpenAPI and Keycloak permission surface;
3. current Employee Payroll UI/API/test surface;
4. exact gap map for PLN-E05-005, 006, 011 and 012;
5. stable identity/version/effective-date model;
6. encryption/tokenisation/masking/reveal and duplicate-detection model;
7. source-authority and mismatch-resolution workflow;
8. bank-account versus payment-instruction separation;
9. payment allocation and readiness rules;
10. tenant-safe FK, FORCE RLS, non-owner runtime, audit/outbox/idempotency and least privilege;
11. exact backend/UI implementation path allow-list;
12. exact negative-test matrix and real-backend browser journeys; and
13. binary migration verdict.

## 7. Migration rule

V001–V050 are immutable. V051 remains unreserved during activation and G01. If and only if G01 proves an additive schema change is required, a separately merged verdict may reserve V051 exclusively to the subsequent P5-EIP-01 implementation gate. No V051 SQL is authorized by activation.

## 8. Explicit exclusions

P5-EIP-01 does not activate or close PLN-E05-003, 004, 013, 014, 015, 016, 017, 019 or 020; E06 calculation; country-specific statutory rates/formulas/eligibility; salary-TDS calculation; retro/off-cycle/final-settlement execution; employer-bank foundation redesign; payment-file generation/bank submission/acknowledgement/reissue/settlement reconciliation; balances; accounting; remittance; payslips; migration/cutover; or production operations.

## 9. Mandatory architecture controls

Preserve Java 21/Spring Boot modular-monolith boundaries; PostgreSQL/Flyway forward-only migrations; stable/effective-dated history; half-open ranges; tenant-safe FKs and FORCE RLS; non-owner/NOBYPASSRLS runtime; no cross-module JPA/internal imports; atomic audit/outbox/idempotency; Keycloak/OIDC least privilege; RFC 9457 errors; no secrets/sensitive identifiers in logs/events/errors; no hidden server-time dependence; and no historical overwrite/delete shortcut.

## 10. Sequencing

After activation merges: P5-EIP-01 is active at G01 READ-ONLY only; product write owner remains NONE; V051 remains unreserved; G01 publishes its binary migration verdict and exact future write allow-list; and no product implementation begins until that verdict authority is separately reviewed and merged.

## 11. Definition of activation done

Activation is complete only when this scope authority is merged; program status, thread registry and continuation handoff identify P5-EIP-01 G01 consistently; canonical story statuses and totals remain unchanged; V001–V050 remain immutable; V051 remains unreserved; no backend/UI product path or migration is write-owned; and the next action is G01 read-only architecture/schema/API/UI verdict.
