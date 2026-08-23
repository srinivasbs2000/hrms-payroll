# P5-EIP-01 — Employee Identity, Bank & Payment Readiness

**Status:** CLOSED — G02A/G02B merged and G02C post-merge reconciliation complete
**R3 selection date:** 19 August 2026
**Backend/program authority baseline:** activation PR #86 merge/main `7818f874d01e3391b922a3f90c65f916d6bf70f4`
**UI authority baseline:** `9dbf0d2f700764e2fe577f89142cd6784028f70c`
**Canonical epic:** E05 — Employee Payroll Profile
**Closure migration state:** V001–V051 immutable; V052 unreserved
**Closure product write state:** NONE

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
Activation definition-of-done is satisfied by PR #86 / merge
`7818f874d01e3391b922a3f90c65f916d6bf70f4`. The sections below are the
subsequent G01 verdict and G02 implementation authority.

## 12. G01 architecture/schema/API/UI verdict

G01 is **CLOSED** against backend/program main
`7818f874d01e3391b922a3f90c65f916d6bf70f4` and UI main
`9dbf0d2f700764e2fe577f89142cd6784028f70c`.

The existing employee-payroll module is valid foundation and must be evolved,
not replaced. V021/V022 stable relationship/assignment identity and version
history, V028 jurisdiction-neutral statutory-profile bindings, V035 employer
banking security precedent and V050 employee relationship/assignment/
compensation binding remain authoritative.

The migration verdict is **ADDITIVE V051 REQUIRED**. V001-V050 remain
immutable. V051 is reserved exclusively to P5-EIP-01 G02 after this verdict
authority merges.

### 12.1 Reusable authority and bounded reuse

V035 already proves the required application-side protection pattern for bank
secrets: AES-256-GCM ciphertext, IV, key-version metadata, deterministic
HMAC-SHA256 fingerprinting, safe last-four display, restricted audited reveal,
versioned lifecycle, verification, approval, duplicate/default controls,
tenant-safe foreign keys and RLS.

P5-EIP-01 must reuse that security **contract and behavior**, but it must not
cross-import `organisation.internal.*` classes. The employee-payroll module owns
its employee-sensitive implementation boundary and uses domain-separated
additional authenticated data and employee-sensitive key configuration. No
plaintext employee identifier, bank-account number or mismatch comparison value
may be persisted, logged, placed in an outbox/event, idempotency payload or
ordinary error response.

The existing `employee_payroll_profile.payroll_status` remains compatibility
state. P5-EIP-01 must not reinterpret it as the authoritative payment-readiness
boolean. Payment readiness is an explainable as-of evaluation over exact
approved facts.

### 12.2 Secure employee payroll identifiers

Add a stable employee payroll-identifier identity under the exact payroll
relationship and immutable/effective-dated versions for secret values and
verification lifecycle.

The generic identifier contract must support `PAN`, `UAN`, `ESI`, `PRAN` and
extensible future scheme codes without embedding country-specific contribution,
eligibility, tax-rate or membership truth.

Each version must retain at least:

- identifier type/scheme and source/authority metadata;
- encrypted value, IV, key version, deterministic fingerprint and safe mask;
- effective range and supersession lineage;
- verification state, verification source/reference and evidence timestamps;
- approval/lifecycle state and actor evidence; and
- immutable audit/outbox/idempotency evidence that contains no plaintext.

Duplicate detection is by scheme plus deterministic fingerprint over overlapping
effective approved/usable ranges. The same secret must not silently become an
active identifier for two different payroll relationships in the same tenant.

Routine reads return masked values only. Reveal requires an explicit restricted
permission plus reason, is audited, and never writes the revealed value to audit
detail, logs or events.

### 12.3 Identity mismatch and source-authority workflow

Add a mismatch case plus append-only resolution evidence for comparisons across
authoritative HR/source identity, bank observations and statutory/verification
sources.

A mismatch case must identify:

- payroll relationship and affected field (`NAME`, `DATE_OF_BIRTH`,
  `IDENTIFIER`, `BANK_BENEFICIARY_NAME` or an extensible equivalent);
- source kind, source authority/reference and comparison timestamp;
- encrypted/tokenised comparison values or non-reversible fingerprints where
  full values need not be retained;
- mismatch classification and payment impact (`BLOCKING`, `WARNING`,
  `INFORMATIONAL`);
- correction owner/source-of-truth route; and
- resolution status, reason, evidence and actor/time history.

Payroll must never overwrite an upstream legal name/date merely because a bank
or statutory source differs. Resolution routes correction to the authoritative
source or records an approved variance; the mismatch workflow itself does not
become a new HR legal-identity master.

### 12.4 Employee bank account lifecycle

Employee bank accounts are separate from employer bank accounts and from
payment instructions.

Add stable employee-bank-account identity plus effective-dated versions under a
payroll relationship. Reuse the V035 security/lifecycle behavior:

- encrypted account number with deterministic fingerprint and safe mask;
- bank/branch/routing metadata needed for payment;
- account-holder/beneficiary comparison evidence without plaintext leakage;
- currency and effective dates;
- draft -> verification -> approval lifecycle;
- independent maker/verifier/final-approver actors;
- rejection/suspension/end-date/successor history;
- duplicate active-account prevention; and
- restricted, reasoned, audited reveal.

A version created after an approved payment-relevant account change must retain
explicit payment-impact-review state/evidence. Approval of the successor must
fail closed until required impact review is completed.

### 12.5 Payment instruction set and allocation rules

Model payment instructions separately from employee bank accounts.

Use a stable payment-instruction-set identity/version under payroll relationship
plus payment currency, with effective dating, approval and supersession history.
Instruction lines reference exact approved/effective employee-bank-account
versions.

The allocation contract must be deterministic and reject ambiguous mixes:

1. `PERCENTAGE` mode: percentage lines only, no remaining-balance line, and the
   exact total is 100%; or
2. `FIXED_THEN_REMAINDER` mode: one or more fixed-amount lines plus exactly one
   `REMAINING_BALANCE` line, with no percentage lines.

Across every effective instruction set, no bank version may be closed,
suspended, unverified, unapproved, out of range or currency-incompatible.
Only one `REMAINING_BALANCE` line is permitted.

A successor to an approved instruction set must carry payment-impact-review
evidence before approval. P5-EIP-01 does not create bank files or execute a
payment.

### 12.6 Payment restrictions and readiness

The canonical payment-readiness story explicitly requires no fraud, security or
beneficiary hold. P5-EIP-01 therefore owns a **payment-only restriction**
contract, not the generic payroll-hold capability in PLN-E05-017.

A payment restriction is effective-dated/append-only evidence for:
`FRAUD`, `SECURITY` or `BENEFICIARY`, with source/reference, severity,
reason, lifecycle and clear/release evidence. It may block payment readiness but
must not be reused as a generic calculation or payroll-cycle hold.

Payment readiness is computed as-of for payroll relationship + payment currency
from exact approved facts and returns dimensions/findings. It is ready only when:

- an approved effective payment-instruction set exists;
- every referenced employee bank account is verified, approved and effective;
- allocation rules are complete and valid;
- instruction/bank/payment currency is compatible with current approved
  employee compensation/payroll authority;
- no unresolved mismatch classified as payment-blocking exists;
- no active fraud/security/beneficiary payment restriction exists; and
- no required verification or post-approval impact review is missing/expired.

The API returns explicit `BLOCKER`/`WARNING` findings and source codes. It does
not persist or expose an operator-editable readiness boolean.

## 13. V051 migration contract

The only migration owned by P5-EIP-01 is:

`database/flyway/sql/V051__employee_identity_bank_payment_readiness.sql`

V051 is forward-only and additive. It must preserve all V001-V050 identifiers
and history and may add only the minimum employee-payroll schema/functions/
indexes required for:

- stable/versioned secure payroll identifiers;
- identity mismatch cases and append-only resolution evidence;
- stable/versioned employee bank accounts;
- versioned payment-instruction sets and allocation lines;
- payment-only fraud/security/beneficiary restrictions;
- payment-impact-review evidence; and
- deterministic payment-readiness query/function support where database
  enforcement is appropriate.

V051 must use tenant-safe composite foreign keys, FORCE RLS, half-open effective
ranges, supersession/history controls, non-owner runtime restrictions and
database constraints/functions for invariants that cannot safely rely on UI
validation.

V051 must not add statutory membership/rate/rule truth, tax profiles,
declarations, generic payroll holds, payment execution, bank-file/settlement,
balances, accounting, remittance, payslip or calculation-result schema.

## 14. G02 API, security and UI contract

G02 evolves the existing employee-payroll HTTP/OpenAPI surface rather than
creating a parallel service.

Required permission families are bounded to:

- `employee-payroll.identifier.read|write|verify|approve|reveal`;
- `employee-payroll.identity-mismatch.read|write|resolve`;
- `employee-payroll.bank-account.read|write|verify|approve|reveal`;
- `employee-payroll.payment-instruction.read|write|approve`;
- `employee-payroll.payment-restriction.read|write|clear`; and
- `employee-payroll.payment-readiness.read`.

The seeded read-only/smoke operator may receive masked read/readiness
permissions only. It must not receive reveal, verify, approve, resolve, clear or
write authority.

Employee sensitive-value encryption uses an employee-payroll-owned provider with
the proven AES-256-GCM/HMAC-SHA256 contract and domain-separated AAD. G02 may add
only these configuration placeholders:

- `PAYROLL_EMPLOYEE_SENSITIVE_ACTIVE_KEY_VERSION`;
- `PAYROLL_EMPLOYEE_SENSITIVE_ENCRYPTION_KEYS`;
- `PAYROLL_EMPLOYEE_SENSITIVE_FINGERPRINT_KEY`.

No real secret value may be committed.

The existing Employee Payroll React workspace is the starting UI. G02 must add
human-operable journeys for masked identifiers/history/verification/reveal,
mismatch review and source-authority routing, bank lifecycle/verification,
payment allocations and impact review, payment restrictions, and payment
readiness findings.

All four selected stories are `REQUIRED_PRODUCT_UI`; real-backend browser E2E is
mandatory.

## 15. G02 exact product path ownership

After this verdict authority merges, P5-EIP-01 G02 owns only these backend/
program paths:

- `database/flyway/sql/V051__employee_identity_bank_payment_readiness.sql`;
- `database/flyway/README.md`;
- `backend/employee-payroll/**`;
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeePayrollMigrationIT.java`;
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeeIdentityBankPaymentReadinessMigrationIT.java`;
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/EmployeePayrollApiIT.java`;
- `contracts/openapi/employee-payroll-openapi-v1.yaml`;
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`;
- `deploy/local/keycloak/payroll-realm.json`;
- `deploy/local/.env.example`;
- `docs/runbooks/employee-payroll-api.md`;
- `docs/runbooks/employee-payroll-application-layer.md`; and
- `docs/runbooks/employee-payroll-setup-ui.md`.

The UI repository owns only:

- `src/features/employee-payroll/**`; and
- `e2e/p5-eip-01*.ts`.

No `backend/organisation/**`, `backend/statutory-deductions/**`,
`backend/compensation/**`, generic payroll-hold, calculation or payment-
execution write is authorized. V035 employer banking remains immutable. If
implementation proves a cross-module change unavoidable, stop and obtain a
bounded amendment rather than widening ownership silently.

## 16. Mandatory negative-test and real-backend evidence matrix

G02 must prove at minimum:

- no plaintext identifier/account/mismatch secret exists in tables, ordinary API
  reads, logs, audit details, outbox/event payloads, idempotency payloads or
  errors;
- masked routine reads and restricted reasoned reveal;
- reveal is audited without storing revealed plaintext;
- wrong-tenant reads/writes/reveals and cross-tenant FKs are denied under FORCE
  RLS/non-owner runtime;
- duplicate active identifier fingerprint for the same scheme across different
  employee relationships is rejected;
- identifier/account effective versions cannot overlap illegally;
- unverified/unapproved/out-of-range identifiers/accounts are not considered
  usable;
- maker cannot provide required independent verification/final approval for the
  same sensitive bank version;
- mismatch never mutates authoritative legal identity and unresolved
  payment-blocking mismatch blocks readiness;
- invalid/closed/suspended/unverified employee bank accounts cannot be used by
  active instructions;
- `PERCENTAGE` allocation must total exactly 100%;
- more than one remaining-balance line is rejected;
- ambiguous percentage/fixed/remainder combinations are rejected;
- fixed allocation without exactly one remaining-balance line is rejected;
- instruction/account/payment currency mismatch blocks approval/readiness;
- post-approval bank/instruction successor changes require impact-review
  evidence;
- active FRAUD/SECURITY/BENEFICIARY payment restriction blocks readiness and
  clearing it preserves history;
- permission-aware read-only users cannot write/verify/approve/reveal/resolve/
  clear;
- idempotency, optimistic concurrency, audit and outbox evidence remain atomic;
- no E05-013/014/015/016/017 legal/statutory/tax/generic-hold behavior is
  inferred or silently implemented; and
- real-backend browser E2E covers administrator/operator/read-only journeys,
  including masked display, denied reveal, successful restricted reveal,
  mismatch resolution routing, bank verification, allocation validation,
  payment restrictions and readiness blockers.

## 17. G02 sequencing and closure

G02 is authorized only after this verdict authority merges:

1. **G02A backend/database/contracts** — implement V051 and the backend,
   migration, OpenAPI, Keycloak, configuration-placeholder and backend tests;
   perform local full Maven/Flyway/API validation and independent review before
   publication; merge only with the exact seven hosted backend checks green.
2. **G02B UI + real-backend E2E** — implement the existing Employee Payroll
   workspace against the exact merged G02A backend; run complete frontend
   regression plus real-backend browser E2E; publish/merge only with required UI
   hosted checks green.
3. **G02C reconciliation/closure** — independently review complete backend + UI
   evidence and reconcile only PLN-E05-005, 006, 011 and 012 where the evidence
   proves the acceptance criteria.

Canonical story status and program totals are unchanged by this G01/G02
authority. All explicitly excluded E05 and E06 stories remain outside scope.

<!-- P5-EIP-01-G02C-SCOPE-CLOSURE -->
## 15. G02C final closure outcome

P5-EIP-01 is CLOSED from merged evidence:

- backend PR #88, exact reviewed head
  `74c649fa4b9d7df34da4c7b7b4836e3787215305`, merge
  `7ade2c199c0eca1351e8907a6e43fbfe8b567b7a`, hosted 7/7 GREEN;
- bounded G02B auth-runtime amendment PR #90 merged at
  `8ca6edfb06f20f90c9ef7f3624196d73fc2260a5`;
- UI PR #21, exact reviewed head
  `668a1a7d0a09b41afbb5f43432c2764f33edf6c1`, merge
  `00368e714665785000002fe4cbd330bc1e5cc180`, hosted 5/5 GREEN;
- exact local G02B real-backend E2E and independent source/diff review: PASS.

PLN-E05-005, 006, 011 and 012 are IMPLEMENTED / HIGH with COMPLETE required
UI/browser evidence. V051 is immutable. V052 is unreserved. Product ownership is
NONE. The exclusions in this scope remain excluded; no next capability is
activated by this closure.
