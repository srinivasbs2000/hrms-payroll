# P5-SSC-01 — Salary Structure Composition, Target & Control Completion

**Status:** G02A PRODUCT IMPLEMENTATION — SUPPLEMENTAL-PLAN COMPOSITION
**Execution identity:** P5-SSC-01
**Original program mapping:** P5 / E04 Salary Structures residual completion
**Activation backend baseline:** dcf140701588345a5189637a2eb9037731e2fa32
**Activation UI baseline:** 8e77bcf5a9a773cc9726eec4e87c0859cdb24543
**Product write owner:** `feature/p5-ssc-01-g02a-supplemental-composition` — bounded G02A backend/schema slice
**Migration owner:** P5-SSC-01 G02A; V042 reserved for supplemental-plan composition
**Activation story totals:** unchanged at 44 / 136 / 80 / 159 / 31 = 450

## 1. Purpose

Complete the residual Salary Structures capability without rebuilding the stable
P5-A3 foundation and without entering Employee Payroll Profile, official payroll
calculation or country-specific legal-rule ownership.

The capability closes the missing salary-structure composition, richer ordered
line semantics, safe compensation target coverage, flexible-benefit plan/election
configuration, statutory-compatibility binding, approval/publication controls,
impact workbench, API/event/security/audit and source-linked tests.

## 2. Preserved implemented boundary

The following canonical E04 stories remain IMPLEMENTED and are not reopened:

- PLN-E04-001 — stable salary-structure identities and versions;
- PLN-E04-004 — versioned CTC policies and cost views;
- PLN-E04-007 — typed effective-dated eligibility-rule configuration/evaluation;
- PLN-E04-008 — deterministic design-time simulation.

V033 and all earlier migrations remain immutable.

## 3. Selected residual completion boundary

Business/workflow:
- PLN-E04-002 — base structure plus shallow supplemental-plan composition;
- PLN-E04-003 — complete ordered salary-structure line semantics;
- PLN-E04-005 — safe compensation target types and explicit conversion policy;
- PLN-E04-006 — flexible-benefit plan/election-policy configuration;
- PLN-E04-009 — minimum-wage/statutory compatibility binding and blocking evidence;
- PLN-E04-010 — complete maker-checker submission/approval/publication workflow;
- PLN-E04-011 — design/comparison/dependency/downstream-impact workbench.

Technical:
- PLN-E04-012 — versioned Salary Structures API contract;
- PLN-E04-013 — reliable Salary Structures domain events;
- PLN-E04-014 — least privilege and segregation of duties;
- PLN-E04-015 — audit and lineage;
- PLN-E04-016 — source-linked functional/edge-case tests;
- PLN-E04-017 — tenant/concurrency/integration/security tests.

Activation promotes no story and reserves no migration.

## 4. UI applicability

SELECTED_STORY_UI_APPLICABILITY_REVALIDATED: YES

- E04-002,003,005,006,009,010,011: REQUIRED_PRODUCT_UI.
- E04-012,013,016,017: NOT_REQUIRED_DIRECTLY and proven through linked journeys.
- E04-014: REQUIRED_ADMIN_OR_SECURITY_UI.
- E04-015: REQUIRED_AUDIT_UI.

E04-017 is a technical integration/security-test story. It has no independent
human-facing workflow and therefore must not fabricate a second security UI; it
must exercise the actual selected product/admin/audit journeys.

## 5. Architecture invariants

- Base + supplemental composition is shallow and deterministic. Deep inheritance
  is prohibited.
- Approved component/formula/base/rate/rounding/proration authority from E03 is
  reused rather than duplicated.
- Money remains BigDecimal/database numeric and decimal-string at external
  boundaries where monetary precision matters.
- Effective ranges remain half-open [from,to).
- Employee-specific compensation assignment and persisted employee elections
  remain E05 ownership. E04 owns reusable structure/plan/election-policy
  configuration and synthetic/design-time election validation.
- NET_PAY_TARGET/gross-up execution remains calculation-engine ownership. E04 may
  model an explicit target capability contract but may not claim official
  gross-up/tax execution.
- Minimum-wage/statutory compatibility consumes separately approved,
  effective-dated legal/rule versions. No country rate, threshold or legal
  conclusion is hard-coded by this capability.
- Maker/checker separation, tenant-safe FKs, forced RLS, NOBYPASSRLS runtime
  access, immutable approved history, audit and outbox evidence remain mandatory.
- V001–V041 are immutable. V042 remains unreserved during activation/G01.

## 6. Explicit exclusions

- employee salary assignment, employee-specific override persistence or actual
  employee flexible-benefit election persistence;
- official gross-to-net, tax, statutory contribution or net-pay gross-up
  calculation;
- country-specific statutory rates, thresholds, interpretations, filing or
  remittance;
- payroll cycles, retro/off-cycle/final settlement, payments or accounting;
- product/schema/API/permission/UI mutation during G01;
- V042 reservation before separately reviewed product-write authority.

## 7. G01 read-only contract/schema preflight

G01 must:
1. verify current backend/UI/main and canonical story/UI authority;
2. preserve E04-001/004/007/008 and audit all 13 selected residual stories;
3. inspect V033, salary-structure domain/API/service/repository/OpenAPI/Keycloak,
   tests and current React salary-structure workbench;
4. decide whether supplemental-plan/flexible-benefit/line/target residuals require
   schema amendment;
5. resolve the E04/E05 boundary for reusable election policy versus persisted
   employee elections;
6. define safe target-type coverage without importing calculation-engine work;
7. define statutory-compatibility binding without legal conclusions;
8. define maker-checker/publication/impact/API/event/security/audit/test gaps;
9. propose exact backend/UI path allow-lists for later product-write authority;
10. issue one binary schema verdict.

G01 writes evidence only to the external artifacts directory.

## 8. Controlled delivery sequence

1. Activation authority.
2. G01 read-only artifact/contract/schema preflight.
3. R01/G02 product-write authority only if G01 proves amendment is required.
4. Backend/schema/API implementation and local verification.
5. UI implementation and real-backend browser evidence.
6. Independent review, hosted publication, exact-head merges and final closure.

## 9. Activation exit state

- Capability: P5-SSC-01 ACTIVATED FOR READ-ONLY G01 PREFLIGHT.
- Backend/UI product write owners: NONE.
- Migration owner: NONE; V042 unreserved.
- Story totals unchanged.
- Authorized next action: G01 read-only preflight only.

## G02A implementation slice — supplemental-plan composition

G01 returned `SCHEMA_AMENDMENT_REQUIRED`.

G02A reserves V042 and implements the first complete business slice for
PLN-E04-002:

- reusable supplemental-plan identity/version catalogue;
- ALLOWANCE, BENEFIT and INCENTIVE plan types;
- exact approved component-version plan lines;
- half-open effective dating and amount/percentage defaults;
- employee-override metadata without employee-specific election persistence;
- maker-checker approval;
- salary structure as the base plus ordered approved supplemental plans;
- no deep inheritance because supplemental plans are a separate bounded model;
- duplicate active component-identity protection across base and bound plans;
- no supplemental binding after validation is bound;
- salary-structure approval remains blocked for bound supplemental composition until the next P5-SSC-01 slice integrates supplemental composition into validation/simulation;
- tenant-safe foreign keys, forced RLS and NOBYPASSRLS-compatible runtime access;
- append-only plan versions, lines and bindings;
- idempotent service writes, audit and outbox lineage;
- focused contract, migration and security tests.

This slice does not promote E04-002. UI, public OpenAPI publication and the
remaining target/flex/statutory/impact residuals continue under P5-SSC-01.
