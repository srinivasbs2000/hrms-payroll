# P5-CCF-01 — Component Catalogue Formula, Rate and Control Completion

**Status:** G01 COMPLETE — R01 APPROVED FOR G02 BACKEND/API IMPLEMENTATION
**Execution identity:** P5-CCF-01
**Original program mapping:** P5-A5 / E03 Component Catalogue
**Activation backend baseline:** 6e8355e80a7cf719fa3a7fc6766f4d486879d1d4
**Activation UI baseline:** 42487de1e99240a99df1ba99742a728671c1636e
**Backend product write owner:** P5-CCF-01-G02
**UI product write owner:** NONE
**Migration owner:** P5-CCF-01-G02; V040 reserved
**R3 verdict:** SCHEMA_AMENDMENT_REQUIRED
**Recommended reasoning:** R2 for bounded G02 implementation; R3 for independent review and G03/G04 gates

## 1. Purpose

Complete the existing Component Catalogue foundation without redoing stable
component identity/versioning, category taxonomy or behavioural dimensions.
The capability owns the bounded reconciliation and completion of formulas,
dependencies, rate parameters, rounding, proration metadata, lifecycle,
operator inspection, API/events, least privilege, audit lineage and tests.

## 2. Preserved implemented boundary

The following stories remain IMPLEMENTED and are not reopened:

- PLN-E03-001 — stable component identities and immutable versions;
- PLN-E03-002 — complete component category taxonomy;
- PLN-E03-003 — component behavioural dimensions.

## 3. Selected completion boundary

- PLN-E03-004 — effective-dated named payroll bases;
- PLN-E03-005 — labour-code/statutory wage classification model;
- PLN-E03-006 — restricted safe component formulas;
- PLN-E03-007 — explicit dependency compilation and cycle/phase validation;
- PLN-E03-008 — multidimensional effective-dated rate tables and parameters;
- PLN-E03-009 — component-specific rounding policy and evidence;
- PLN-E03-010 — event-specific proration metadata;
- PLN-E03-011 — lifecycle, validation, approval, supersession and retirement;
- PLN-E03-012 — catalogue search, dependency and impact views;
- PLN-E03-013 — versioned API contract;
- PLN-E03-014 — reliable domain-event contract;
- PLN-E03-015 — least privilege and segregation of duties;
- PLN-E03-016 — audit and lineage inspection;
- PLN-E03-017 — source-linked functional and edge-case tests;
- PLN-E03-018 — persistence, tenant, concurrency and integration tests.

No selected story is promoted by activation. Canonical totals remain
29 IMPLEMENTED / 147 PARTIALLY IMPLEMENTED / 84 NOT EVIDENCED /
159 NOT STARTED / 31 LEGAL-DOMAIN REVALIDATION = 450.

## 4. Selected-story UI applicability

SELECTED_STORY_UI_APPLICABILITY_REVALIDATED: YES

- PLN-E03-004 through PLN-E03-012: REQUIRED_PRODUCT_UI. Payroll Rule,
  Configuration, Approver and Analyst actors must configure or inspect these
  contracts through the React Component Catalogue workbench.
- PLN-E03-013: NOT_REQUIRED_DIRECTLY. API evidence is consumed by the selected
  human-facing catalogue journeys.
- PLN-E03-014: NOT_REQUIRED_DIRECTLY. Event reliability is technical evidence
  for catalogue state changes.
- PLN-E03-015: REQUIRED_ADMIN_OR_SECURITY_UI. Role, population, field scope,
  maker-checker conflicts and denied-action evidence must be inspectable.
- PLN-E03-016: REQUIRED_AUDIT_UI. Actor, version, transition, source,
  correlation and result lineage must have a usable read-only inspection path.
- PLN-E03-017: NOT_REQUIRED_DIRECTLY. Automated tests prove linked business
  journeys.
- PLN-E03-018: NOT_REQUIRED_DIRECTLY. Integration/security tests must exercise
  the required product, admin/security and audit journeys but create no separate
  user-facing surface.

UI authority is `srinivasbs2000/hrms-payroll-web`. Required journeys extend the
existing pay-component and payroll-base surfaces; exact routes, API bindings,
permissions, frontend tests and real-backend browser-E2E path ownership must be
proven by G01 before UI product write is authorized.

## 5. Architecture and safety invariants

- Formula evaluation uses a restricted parsed domain expression model. It must
  never execute arbitrary JavaScript, Java, SQL, SpEL or operating-system code.
- Formula inputs, result type, dependencies and calculation phase are explicit,
  versioned and validated before approval.
- Dependencies are acyclic outside separately approved bounded iterative groups;
  self-reference and later-phase reference fail closed.
- Rate-table and rule metadata are effective-dated, versioned, tenant-isolated,
  overlap-safe and calculation-evidence addressable.
- Money remains BigDecimal internally and decimal-string plus ISO currency at
  API boundaries. Units are explicit.
- Effective ranges remain half-open [from,to).
- Rounding method, scale/precision, stage and negative-value treatment are
  versioned; required intermediate and final evidence remains distinguishable.
- Joining, exit, unpaid leave, transfer and salary-revision proration metadata
  remain independently modelled. This capability does not execute payroll
  proration outside the existing calculation boundary.
- Legal wage treatment remains delegated to separately approved effective rule
  versions. No current jurisdiction rate or legal conclusion is encoded here.
- Tenant-aware foreign keys, forced RLS, NOBYPASSRLS runtime access,
  maker-checker controls, append-only audit/outbox evidence and immutable
  approved history remain mandatory.
- V001–V039 are immutable. R01 reserves V040 exclusively for P5-CCF-01-G02.
- Existing E03 behavior and existing API compatibility are preserved unless a
  later approved amendment explicitly versions the change.

## 6. Explicit exclusions

- country-specific statutory rates, thresholds, interpretations or filings;
- employee salary configuration, payroll-run expansion or calculation-engine
  execution beyond catalogue metadata contracts;
- retro, off-cycle, recoveries, final settlement, banking, payments or
  accounting;
- product, API, permission, Keycloak, database or migration mutation during G01;
- V040 reservation without a separately reviewed schema amendment authority;
- story promotion before backend, UI and real-backend browser evidence merge.

## 7. G01 — read-only artifact and contract preflight

G01 must inspect the exact backend and UI activation baselines and produce one
consolidated evidence report containing:

1. exact repository, branch/main, working-tree, PR and hosted-CI state;
2. exact current database tables, constraints, RLS, migrations and persistence
   tests relevant to PLN-E03-004 through PLN-E03-018;
3. exact domain/application/public API/OpenAPI/event/security/audit artifacts;
4. exact existing React routes, components, API clients, permissions, tests and
   browser journeys;
5. a story-by-story implemented/reusable/gap verdict preserving E03-001..003;
6. the restricted-expression grammar/type/function/error contract proposal;
7. dependency graph, phase, rate-dimension, rounding and proration metadata
   contracts, including compatibility and calculation-evidence implications;
8. lifecycle, concurrency, tenant/RLS, maker-checker, audit and outbox gaps;
9. exact proposed backend and UI path allow-lists and verification impact;
10. a binary schema verdict: NO_SCHEMA_CHANGE_REQUIRED or
    SCHEMA_AMENDMENT_REQUIRED, with repository evidence;
11. exact permission and Keycloak verdict without granting any new authority;
12. exact API/event versioning and compatibility verdict;
13. exact backend/API/UI/real-backend browser-E2E delivery ordering; and
14. blocking unknowns, source conflicts and separately governed decisions.

G01 is read-only. It creates no product branch, changes no repository file,
reserves no migration and promotes no story.

## 8. Controlled delivery sequence

1. Activation authority — this governance-only increment.
2. G01 — read-only backend/database/API/UI artifact and contract preflight.
3. R01 only if G01 proves schema, migration, permission or material contract
   amendment authority is required.
4. G02 — separately authorized backend/API implementation and verification.
5. G03 — separately authorized UI workflow implementation and cross-repository
   browser evidence.
6. G04 — independent critical review, ordered publication, hosted CI, exact-head
   merges, story reconciliation and final capability closure.

## 9. G01/R01 exit state

- G01 evidence: PASS; product mutation NONE.
- R3 verdict: SCHEMA_AMENDMENT_REQUIRED.
- Capability: P5-CCF-01 ACTIVATED FOR G02 BACKEND/API IMPLEMENTATION.
- Backend product write owner: P5-CCF-01-G02.
- UI product write owner: NONE.
- Migration owner: P5-CCF-01-G02; V040 reserved.
- Authorized next action: bounded G02 backend/API implementation within the R3 allow-list.
- Prohibited next action: UI mutation, story promotion, or changes outside the
  exact G02 allow-list without separate authority.

`P5_CCF_01_R01: APPROVED`
