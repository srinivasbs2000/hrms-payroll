# P5-JRF-01 — Jurisdiction & Registration Foundations Quality Closure

**Status:** LOCAL IMPLEMENTATION VERIFIED — PUBLICATION PENDING
**Capability:** `P5-JRF-01`
**Activation base:** `ff581cafce3be5495d93932abfae3931b139358f`
**Branch:** `feature/p5-jrf-01-jurisdiction-registration-foundations`
**Migration:** `V034__jurisdiction_registration_foundations.sql`
**Final local boundary after G03-C documentation finalization:** 82 changed/untracked paths within the 88-path product maximum plus a separately owner-approved 3-path dependency-security closure exception

## Delivered capability

P5-JRF-01 locally delivers:

- dedicated work-location identity and immutable effective-dated versions;
- extensible payroll-jurisdiction hierarchy and exact parent-version lineage;
- deterministic jurisdiction resolution with preview and immutable evidence;
- explicit override, work-location and establishment-fallback precedence;
- conflict/unresolved findings without residential-address guessing;
- generic registration-type metadata and allowed owner kinds;
- statutory-registration identity/version lifecycle and temporal uniqueness;
- independent maker, verifier and final approver evidence;
- rejection, suspension, expiry and renewal/successor controls;
- exact parent registration lineage with same/ancestor jurisdiction enforcement;
- bounded foundation-readiness findings;
- `JAVA_REGEX_V1` application-level identifier-pattern semantics;
- masked routine identifiers and permission-controlled audited exact reveal;
- tenant-safe controlled successor row locking without granting runtime table UPDATE;
- business-selector operator UI for owners, jurisdictions, establishments and parents.

## Verification lineage

Major locally GREEN gates include:

- G02-A through G02-H;
- G03-A v1.1;
- architecture consistency checkpoint: PASS WITH TARGETED CLOSURE CORRECTIONS;
- AC-G03-B1 v1.3;
- AC-G03-B2 v1.2;
- G03-C full pre-publication closure.

AC-G03-B2 v1.2 independently proved:

- PostgreSQL 17.10 and Flyway V001 -> V034 plus V033 -> V034;
- all selected JRF successor/readiness/security integration tests;
- controlled row-lock boundary;
- future draft does not hide current approved/active versions;
- parent ACTIVE -> child ready and parent suspension -> blocker;
- 20 frontend test files / 92 tests;
- frontend lint with 0 warnings/errors;
- production frontend build;
- OpenAPI/Keycloak alignment;
- exact 69-path pre-documentation state.

G03-C writes this document only after its complete technical gate has passed.

## Dependency-security closure

The first G03-C technical run on 8 August 2026 detected three newly surfaced
high-severity npm audit entries in inherited frontend dependencies:

- `nanoid` advisory `GHSA-2v37-7h3g-55p8`, affecting `<3.3.17`;
- `react-router` advisory `GHSA-qwww-vcr4-c8h2`;
- the direct `react-router-dom` wrapper entry inheriting the router advisory.

The 69-path P5-JRF implementation had not modified either dependency manifest.
The closure therefore uses a separately owner-approved three-path security
exception limited to:

- `frontend/payroll-web/package.json`;
- `frontend/payroll-web/package-lock.json`;
- `frontend/payroll-web/scripts/verify-npm-audit.mjs`.

The remediation upgrades `react-router-dom`/`react-router` from 7.18.1 to
7.18.2 and resolves transitive Nano ID to a non-vulnerable 3.x version
`>=3.3.17`. The prior temporary React Router audit exception is removed; the
policy returns to zero permitted high/critical advisories.

No P5-JRF product source, migration, API or domain behavior is changed by this
dependency-security closure.

## Security and architecture closure

- PostgreSQL 17 remains intentional; Oracle/multi-RDBMS portability is not a
  P5-JRF-01 requirement.
- FORCE RLS, tenant-safe FKs and runtime `NOBYPASSRLS` remain mandatory.
- Direct runtime table UPDATE is not granted merely to obtain row locks.
- Registration identifiers are minimized at API/event boundaries.
- No exact identifier is allowed in logs, errors, audit or outbox payloads.
- No India-specific legal rate/formula conclusion is introduced.

## R3 critical review

**Result:** PASS — NO BLOCKING ARCHITECTURE DEFECT IDENTIFIED.

Review focus:

1. tenant/RLS integrity;
2. effective dating and successor semantics;
3. maker-checker/SoD;
4. registration identifier handling;
5. parent registration lineage;
6. deterministic jurisdiction resolution;
7. runtime privilege boundary;
8. API/OpenAPI/Keycloak alignment;
9. operator usability;
10. approved scope/exclusion reconciliation.

The earlier AC-G03-B1/B2 defects were corrected without weakening the approved
architecture. Final local verification is the authority for publication
readiness, not intermediate failed package versions.

## Explicit exclusions / residual work

Still outside P5-JRF-01:

- employer bank accounts;
- authorised signatories/delegated authority;
- full configuration snapshots;
- complete foundation-readiness dashboard;
- India PF/EPS/EDLI/ESI/PT/LWF/NPS/TDS rates/formulas/legal truth;
- statutory filing/returns and remittance/payment;
- employee statutory profiles;
- payroll calculation and payroll-assignment changes;
- minimum-wage calculation;
- retro payroll;
- production migration/deployment.

The detailed story ledger is intentionally reconciled after product merge, not
before publication. Remote PR CI remains responsible for repository-hosted
gitleaks/dependency-review and other normal publication gates.

## Publication status

G03-C completion means **READY FOR OWNER-EXECUTED COMMIT/PUSH/DRAFT-PR**.

It does not mean:

- V034 is committed;
- the product PR is merged;
- detailed-story status is reconciled;
- capability ownership is released;
- V035 may be allocated.

Those occur only through the separately controlled publication and post-merge
authority-closure steps.
