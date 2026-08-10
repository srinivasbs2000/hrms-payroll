# P5-FBA-01 — Foundation Banking & Authority

**Status:** MERGED / CLOSED — POST-MERGE AUTHORITY RELEASED
**Execution capability:** `P5-FBA-01`
**Original program mapping:** `P5-A3 — Foundation Bank, Authority, Snapshots and Readiness`
**Primary canonical stories:** `PLN-E01-008`, `PLN-E01-009`
**Cross-cutting partial stories:** `PLN-E01-011`, `PLN-E01-012`
**Activation base:** `0cae307b0f5e7bcd05b47836e6e4df24c8701add`
**Implementation branch:** `feature/p5-fba-01-foundation-banking-authority`
**Migration reservation:** V035 committed and immutable; V036 unreserved
**UI repository baseline:** `dc8f17cfbabe0a3322f24a3dc0457509fe1e7d01`
**Publication evidence:** backend PR #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`; UI PR #12 / `5c45ab41ee3cb4466fac822c04c771f5de0ba119`; closure PR #__P5_FBA_CLOSURE_PR__
**Reasoning transition:** R3 publication and post-merge reconciliation complete; ownership released

## 1. Objective

Deliver the remaining employer-banking and authorised-signatory foundation needed
by Original P5-A3 without entering payment execution, immutable configuration
snapshotting or complete foundation-readiness closure.

The capability must provide tenant-safe, effective-dated, independently
controlled employer bank-account and legal-authority configuration that can be
consumed later by snapshot/readiness and payment capabilities without reading
mutable or unsafe secrets.

## 2. Canonical story reconciliation

### PLN-E01-008 — Configure employer bank accounts for payroll ownership

Required source-backed outcomes:

- employer bank accounts are versioned and verified;
- each account is scoped to the correct legal entity or payroll statutory unit;
- currency is explicit and effective dates are enforced;
- full account numbers never appear in normal UI or logs;
- only restricted permission may reveal unmasked account details; and
- a default bank account cannot be deleted.

P5-FBA-01 owns full implementation of this story.

### PLN-E01-009 — Manage authorised signatories and delegated authority

Required source-backed outcomes:

- signatory authority is effective-dated;
- authority is purpose-scoped;
- legal authority is independently approved;
- expired authority is rejected;
- delegated amount/currency limits are enforceable where configured; and
- legal/signatory identity does not imply payroll-system access.

P5-FBA-01 owns full implementation of this story.

### PLN-E01-011 — Apply maker-checker approval to foundation configuration

P5-FBA-01 owns only the bank/signatory portion:

- maker cannot verify or finally approve the same high-risk version;
- verifier cannot finally approve the same version;
- approval/rejection/suspension records preserve actor, reason/evidence and time;
- effective dates remain immutable historical evidence.

The broader cross-foundation approver-scope/delegation model remains partially
implemented unless a reusable entity-scoped authorization facility already
exists in repository authority. P5-FBA-01 must not invent an application-access
identity link for a legal signatory to claim closure of that broader control.

### PLN-E01-012 — Expose foundation readiness and validation status

P5-FBA-01 owns bounded banking/authority readiness findings only:

- missing or non-active employer bank account;
- missing default bank account for required owner/currency;
- unapproved, suspended or expired banking configuration;
- missing, unapproved, suspended or expired signatory authority;
- configured delegated-limit mismatch.

The complete cross-foundation readiness dashboard remains deferred to
`P5-FSR-01 — Foundation Snapshot & Readiness Closure`.

## 3. Explicit exclusions

P5-FBA-01 does not implement:

- `PLN-E01-010` immutable configuration snapshots;
- complete `PLN-E01-011` entity-scoped application authorization;
- complete `PLN-E01-012` readiness across all foundation domains;
- employee personal bank accounts;
- payment-file generation, bank integration or payment execution;
- payroll calculation changes;
- country-specific statutory rates, formulas, filing or remittance;
- production migration/cutover;
- unrelated dependency upgrades;
- a third contract repository;
- any rewrite of V001-V034.

## 4. Architecture decision set

### 4.1 Module ownership

Employer bank-account, signatory and bounded banking-readiness domain behavior
belongs in `backend/organisation`.

Rationale:

- the source stories attach bank ownership and authority to legal entities and
  payroll statutory units;
- no payments/treasury module currently exists;
- this capability is foundation configuration, not payment execution; and
- creating a payments module here would pull later P9 behavior into P5.

`backend/payroll-boot` owns end-to-end API integration tests only.

### 4.2 Stable identity and immutable effective-dated versions

Use stable identity plus successor-linked version records for both:

- employer bank accounts; and
- authorised signatories.

Approved historical versions are never overwritten or hard-deleted. Changes
create successor versions with exact lineage. Database exclusion/uniqueness
constraints enforce effective-date rules and default-account uniqueness.

No DELETE API is provided for employer bank accounts or signatory history.

### 4.3 Employer bank-account model

The database foundation will distinguish:

- stable employer-bank-account identity;
- effective-dated bank-account version;
- exact owner kind: `LEGAL_ENTITY` or `PAYROLL_STATUTORY_UNIT`;
- exact owner identity;
- ISO currency;
- bank/routing metadata needed for configuration;
- masked display suffix;
- encrypted account-number payload;
- deterministic keyed fingerprint for duplicate detection;
- effective dates;
- lifecycle/verification/approval evidence;
- default-account flag;
- exact superseded-version lineage.

At most one active approved default account may exist for the same tenant,
owner and currency for payroll funding.

### 4.4 Sensitive bank-data protection

The approved security design is application-level encryption:

- AES-256-GCM;
- random 12-byte IV per encryption;
- key-version stored with ciphertext;
- multiple configured decrypt keys to support rotation;
- separate HMAC-SHA-256 fingerprint key for exact duplicate/equality checks;
- no PostgreSQL `pgcrypto`;
- no plaintext account number persisted;
- no plaintext account number in audit state, domain events, logs, errors,
  idempotent responses or normal API views;
- normal views expose only masked account information;
- reveal requires a dedicated restricted permission;
- reveal is audited and returned with `Cache-Control: no-store`.

The prior JPA `AttributeConverter` mechanism is adapted to an explicit
application crypto component because this repository's organisation
persistence uses `JdbcTemplate`, not JPA entity persistence. The cryptographic
decision itself is unchanged.

Key material is injected at runtime and is not stored in Payroll database
tables. V035 stores ciphertext, IV, key version, keyed fingerprint and safe
masking metadata only.

### 4.5 Bank-account lifecycle

Use a controlled lifecycle aligned with the proven registration pattern:

`DRAFT -> PENDING_VERIFICATION -> VERIFIED -> APPROVAL_PENDING -> ACTIVE`

Terminal/controlled states include `REJECTED`, `SUSPENDED`, `EXPIRED` and
`SUPERSEDED` as applicable.

The maker submits the draft. Verification must be performed by another actor.
Final activation must be performed by an actor who is neither maker nor
verifier. Suspension of an active version requires an independent authorized
actor and reason.

### 4.6 Authorised-signatory and delegated-authority model

A signatory is a legal-authority identity, not an application user.

The model must not contain an automatic link that grants payroll-system access
because somebody is an authorised signatory.

Each exact signatory version carries owner, effective dates, lifecycle and
approval evidence. One or more immutable authority-scope rows bind the exact
version to:

- purpose code;
- optional currency;
- optional maximum amount/authority limit; and
- the version effective period.

A pure authority-evaluation operation must reject expired/suspended/unapproved
versions and requested amounts outside configured limits. Payment execution is
not part of this capability; later payment functionality must consume this
foundation rather than duplicate it.

### 4.7 Tenant/RLS and mutation controls

All V035 tenant-owned tables:

- carry `tenant_id`;
- use tenant-safe composite foreign keys;
- ENABLE and FORCE RLS;
- use the established `platform.current_tenant_id()` policy pattern;
- deny direct UPDATE/DELETE to `payroll_app` where controlled functions own
  state transitions;
- use security-definer functions with fixed search paths where necessary;
- enforce optimistic concurrency and exact transition preconditions.

### 4.8 API permissions

Use least-privilege permissions rather than generic write access:

- `organisation.bank-account.read`
- `organisation.bank-account.write`
- `organisation.bank-account.verify`
- `organisation.bank-account.approve`
- `organisation.bank-account.reveal`
- `organisation.signatory.read`
- `organisation.signatory.write`
- `organisation.signatory.verify`
- `organisation.signatory.approve`
- `organisation.banking-readiness.read`

Keycloak roles/claims grant application access. Signatory legal authority is
stored independently and never creates any of these permissions.

### 4.9 Bounded readiness

P5-FBA-01 exposes banking/signatory readiness as a bounded foundation result.
It must not claim that a PSU/pay group is globally payroll-ready.

`P5-FSR-01` will later compose this result with jurisdiction, registration,
calendar/pay-group, snapshot and other foundation readiness.

### 4.10 Events and audit

Only a bounded event set may be published, using masked/non-secret payloads,
for example activation, suspension and supersession of bank/signatory
configuration.

Audit evidence is append-only and must never contain plaintext bank-account
numbers or encryption keys.

## 5. Maximum path ownership

### Backend/program repository — `srinivasbs2000/hrms-payroll`

Owned for P5-FBA-01:

- `database/flyway/sql/V035__foundation_banking_authority.sql`
- `backend/database-migrations/src/test/**/FoundationBankingAuthorityMigrationIT.java`
- `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/**`
  only for P5-FBA-01 bank/signatory/readiness public and internal types
- `backend/organisation/src/test/java/com/acme/hrms/payroll/organisation/**`
  only for P5-FBA-01 tests
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/**`
  only for P5-FBA-01 API/integration tests
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
- `deploy/local/keycloak/payroll-realm.json`
- `deploy/local/.env.example` only if synthetic local crypto configuration is
  required
- `backend/payroll-boot/src/main/resources/application.yml` only if runtime
  crypto property binding requires it
- P5-FBA-01 planning, quality, runbook, history and governance documents

No other backend module is owned without a new architecture decision.

### UI repository — `srinivasbs2000/hrms-payroll-web`

Owned later in the same capability:

- `src/features/foundation-banking-authority/**`
- `src/App.tsx`
- `src/App.test.tsx`
- relevant `e2e/**` P5-FBA-01 specifications/fixtures only
- README only if operator/run instructions materially require update

No dependency upgrade or package-manifest change is authorized merely by this
scope.

### R3-reconciled G04/G05 verification-support paths

The final R3 review records five bounded support artifacts used by the explicitly
authorized G04 contract/security integration and G05 cross-repository E2E work:

- `.github/workflows/ci.yml`;
- `deploy/local/smoke/auth-smoke.ps1`;
- `scripts/verify-foundation-banking-contracts.mjs`;
- `database/flyway/e2e/fixtures/S03_001__sprint_3_executable_payroll.sql`;
- `database/flyway/e2e/verify_smoke_fixture.sql`.

These paths enforce contract alignment, real-token/auth smoke behavior and
runtime-fidelity fixture correctness. They do not add a new product module,
expand payment/calculation scope, alter V001-V034, or reserve V036.

## 6. V035 reservation decision

`V035` is REQUIRED and is reserved exclusively to `P5-FBA-01`.

Reason: PLN-E01-008 and PLN-E01-009 require new persistent, versioned,
tenant-owned bank/signatory foundation objects, database-level effective-date
and lifecycle constraints, RLS and controlled transition functions. These
cannot be implemented correctly as an application-only increment.

V001-V034 remain immutable.

## 7. Acceptance criteria

P5-FBA-01 is locally complete only when evidence proves at least:

1. tenant-safe employer-bank-account identity/version creation;
2. legal-entity and PSU ownership validation;
3. currency/effective-date validation;
4. AES-GCM ciphertext at rest with no plaintext account number in the database;
5. HMAC fingerprint equality/duplicate detection without plaintext storage;
6. masked normal list/current/history responses;
7. restricted, audited, `no-store` reveal;
8. maker/verifier/final-approver segregation;
9. stale `If-Match` and reused/different idempotency-key rejection;
10. default-account uniqueness by owner/currency;
11. no hard delete and immutable approved history;
12. signatory identity remains separate from system access;
13. purpose/effective-date delegated authority;
14. optional amount/currency limit validation;
15. authority evaluation rejects expired/suspended/out-of-limit requests;
16. bounded banking/signatory readiness findings;
17. RLS cross-tenant denial for every new tenant-owned table;
18. direct mutation bypass is denied where controlled functions own transitions;
19. audit/event payloads contain no plaintext bank secrets;
20. OpenAPI and Keycloak permissions match runtime behavior;
21. React UI masks secrets and gates reveal/action controls by permission;
22. cross-repository browser E2E proves the operator flow; and
23. full backend + UI regression remains green.

## 8. Negative-path minimum set

Mandatory negative tests include:

- cross-tenant read/write/reveal;
- duplicate or overlapping active default accounts;
- wrong owner kind/owner identity;
- invalid currency/effective dates;
- maker attempts verification or final approval;
- verifier attempts final approval;
- unauthorized reveal;
- reveal response caching;
- ciphertext/key-version corruption;
- missing configured key version;
- duplicate account number via HMAC fingerprint;
- direct SQL UPDATE/DELETE bypass;
- stale optimistic version;
- conflicting idempotency-key reuse;
- expired/suspended signatory;
- authority-purpose mismatch;
- amount above delegated limit;
- currency mismatch against delegated limit;
- readiness with missing/unapproved/expired bank/signatory configuration.

## 9. R2 implementation sequence

### G01 — V035 database and crypto foundation

- V035 schema, RLS, constraints, transition functions and grants;
- application crypto component and focused crypto tests;
- migration integration tests;
- no API/UI yet.

### G02 — Employer bank-account backend

- requests/views/controller/service/repository;
- idempotency, audit and bounded events;
- masked read and restricted reveal;
- backend contract/integration tests.

### G03 — Authorised signatory and authority evaluation

- signatory identity/version/scope operations;
- approval lifecycle;
- authority evaluation;
- bounded banking/signatory readiness;
- backend tests.

### G04 — Contract/security integration

- aggregate OpenAPI;
- Keycloak permissions;
- payroll-boot end-to-end API tests;
- full backend regression.

### G05 — Standalone React UI and cross-repository E2E

- bank-account operator workspace;
- signatory/delegated-authority workspace;
- readiness panel;
- permission-gated reveal and lifecycle actions;
- React tests and Playwright cross-repository E2E.

### G06 — Critical review, publication and status closure

- R3 critical review;
- complete regression/security evidence;
- product PR/merge;
- detailed-story reconciliation;
- status-closure PR;
- release path ownership and V035 reservation only after closure.

## 10. Publication and closure rule

The implementation branch may be published only after the agreed local gates
are green. Story statuses are not changed merely because the capability is
activated.

After product merge, reconcile `PLN-E01-008`, `009`, `011` and `012` against
actual evidence. Only then update the canonical ledger and close capability
authority.

## 11. Closure result

P5-FBA-01 is closed after backend PR #44, UI PR #12
and status-closure PR #__P5_FBA_CLOSURE_PR__.

Canonical reconciliation:
- PLN-E01-008 IMPLEMENTED;
- PLN-E01-009 IMPLEMENTED;
- PLN-E01-011 remains PARTIALLY IMPLEMENTED;
- PLN-E01-012 remains PARTIALLY IMPLEMENTED;
- PLN-E01-010 remains unchanged.

G06 publication-security support changed `.gitleaksignore` only to suppress the
ten reviewed synthetic idempotency-key fingerprints; product and test behavior
were unchanged.

V035 is immutable. V036 is unreserved. No next capability is activated by this
closure.
