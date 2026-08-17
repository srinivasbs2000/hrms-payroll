# P5-EPA-01 — Employee Payroll Assignment & Compensation Binding Completion

**Status:** ACTIVATED FOR G01 READ-ONLY ARCHITECTURE/SCHEMA VERDICT after activation authority merges
**R3 selection date:** 17 August 2026
**Backend/program authority baseline:** `f24064af8e1649d675ed9de527e36e4103c2e4b3`
**UI authority baseline:** `f2d7d1ac1e96cf154b624cf583681c6b751b5219`
**Canonical epic:** E05 — Employee Payroll Profile
**Migration state at activation:** V001–V049 immutable; V050 unreserved
**Product write state at activation:** NONE — G01 is read-only

## 1. R3 selection verdict

Fresh post-P5-SSC-01 R3 selects a bounded E05 completion increment:

**P5-EPA-01 — Employee Payroll Assignment & Compensation Binding Completion**

This is not a greenfield rewrite. Sprint 2 already delivered the initial
relationship, payroll-assignment, pay-group-assignment and salary-assignment
foundation through S2-05/S2-06 and V021-V022. The current implementation and UI
must be evolved from that merged authority.

The selection is sequenced now because:

1. E04 Salary Structures is fully reconciled and closed;
2. E05 is the direct employee-level binding layer consumed by E06 Calculation
   Engine stories;
3. the repository already contains substantial but incomplete E05 foundations;
4. completing employee relationship/assignment/compensation binding creates
   useful business functionality without pulling country-specific tax or
   statutory legal truth into the same increment.

## 2. Canonical story boundary

P5-EPA-01 owns exactly these canonical stories:

| Story | Current canonical status | Capability objective |
|---|---|---|
| PLN-E05-001 | PARTIALLY IMPLEMENTED | Create payroll relationships distinct from employment and assignment |
| PLN-E05-002 | PARTIALLY IMPLEMENTED | Create payroll assignments and regular pay-group links |
| PLN-E05-007 | PARTIALLY IMPLEMENTED | Assign pay groups with effective-dated override control |
| PLN-E05-008 | PARTIALLY IMPLEMENTED | Assign salary structures and compensation targets |
| PLN-E05-009 | PARTIALLY IMPLEMENTED | Manage employee-specific component values and overrides |
| PLN-E05-010 | NOT EVIDENCED | Process compensation changes with impact assessment |
| PLN-E05-018 | NOT EVIDENCED | Handle transfers, rehires and concurrent assignments |

Activation does not change any canonical story status.

Every selected story is `REQUIRED_PRODUCT_UI`; backend-only implementation
cannot close any selected story.

## 3. Required business outcome

### 3.1 Payroll relationship

Complete the employee payroll relationship so it is explicitly distinct from
employment and work assignment and preserves the legally relevant payroll
boundary, including legal employer/PSU, country context and aggregation
authority required by the approved design.

Multiple employments or payroll relationships must be explicit; overlapping or
successor relationships must never be inferred by accidental data overlap.

### 3.2 Payroll assignment

Complete payroll assignment as the effective-dated bridge between a payroll
relationship and the work assignment used for payroll.

The assignment must support controlled concurrent-assignment lineage and must
bind only compatible approved payroll configuration.

### 3.3 Pay-group assignment

Complete one-active-regular-pay-group semantics per payroll assignment,
effective-dated change control, PSU/relationship compatibility, approved
override handling and backdated impact assessment.

### 3.4 Salary-structure and compensation-target assignment

Replace the legacy monthly-INR-only salary binding with a governed assignment to
an approved salary-structure version and an explicit compensation target
contract compatible with the completed E04 target model.

The assignment must preserve target type/value/currency, source compensation
event, effective dates, approval status and supersession lineage. Currency must
derive from approved configuration; P5-EPA-01 must not introduce another
country/currency hard-code.

### 3.5 Employee component values and controlled overrides

Support effective-dated, approved employee-specific values/overrides only where
the component/structure contract permits them.

Overrides must not change protected statutory classification, component
identity, calculation meaning, payee, tax semantics or other protected
configuration dimensions.

### 3.6 Compensation-change event and impact assessment

Model prospective, current-period, retrospective, correction and reversal
compensation events distinctly.

P5-EPA-01 may identify affected periods and show deterministic design/impact
evidence. It does not execute retro payroll, off-cycle payroll, recalculation,
balance posting, payment or accounting.

### 3.7 Transfer, rehire and concurrent-assignment lineage

Legal-entity/PSU transfer, rehire and concurrent assignment must preserve
relationship/assignment lineage and historical boundaries. A transfer must
explicitly decide whether the current payroll relationship continues or a
successor relationship is required.

## 4. UI authority

The separate `srinivasbs2000/hrms-payroll-web` repository is in scope.

The existing Employee Payroll page is the starting surface. P5-EPA-01 must
deliver the human-operable journeys required by the selected stories, including:

- relationship and assignment lifecycle/history;
- pay-group compatibility and effective-dated change impact;
- salary-structure/target assignment and history;
- controlled employee component overrides;
- compensation-change classification and impact view;
- transfer/rehire/concurrent-assignment lineage;
- permission-aware read-only behavior and audit/history access.

Required story closure evidence includes real-backend browser E2E. Mock-only UI
tests are insufficient.

## 5. G01 — mandatory read-only architecture/schema verdict

No product write is authorized by activation itself.

G01 must independently inspect the exact merged backend/UI authority and publish
one architecture/schema verdict covering:

1. exact V021/V022 employee-payroll schema and constraints already present;
2. exact employee-payroll public/internal APIs and cross-module dependencies;
3. current OpenAPI and Keycloak permission surface;
4. current Employee Payroll UI/API/test surface;
5. missing data needed for the seven selected stories;
6. stable identity/version and half-open effective-date model;
7. overlap/concurrency/transfer/rehire rules;
8. compensation-target and source-event contract aligned to completed E04;
9. employee component-override protection boundaries;
10. impact-assessment versus retro-execution boundary;
11. RLS, tenant-safe FK, audit/outbox/idempotency and least-privilege controls;
12. exact backend/UI implementation path allow-list;
13. exact negative-test matrix and real-backend browser journeys; and
14. migration verdict.

### Migration rule

V001–V049 are immutable.

V050 remains unreserved during activation and G01 discovery. If G01 proves an
additive schema change is required, the G01 verdict may reserve V050
exclusively for the subsequent P5-EPA-01 implementation gate. No V050 SQL may
be authored before that explicit verdict/reservation.

## 6. Known inherited implementation facts to verify, not assume away

R3 read-only inspection identified these concrete inherited limitations:

- relationship input currently carries legal-entity version and dates but does
  not express the complete relationship boundary required by PLN-E05-001;
- payroll assignment currently carries relationship version, establishment and
  dates but requires review against work-assignment/concurrent-assignment
  lineage;
- salary assignment currently carries salary-structure version,
  `monthlyAmount`, currency and dates and explicitly permits only INR;
- the current employee-payroll module does not expose the complete
  employee-component override / compensation-change event model required by
  PLN-E05-009/010;
- the existing Employee Payroll React page is substantial and must be extended,
  not replaced.

G01 must confirm these facts against exact repository content before deciding
the implementation shape.

## 7. Explicit exclusions

P5-EPA-01 does not activate or close:

- PLN-E05-003 — employee payroll onboarding lifecycle;
- PLN-E05-004 — multidimensional payroll readiness;
- PLN-E05-005/006 — secure payroll identifiers and identity mismatch workflow;
- PLN-E05-011/012 — employee bank/payment instructions and payment readiness;
- PLN-E05-013 — PF/ESI/NPS membership profiles;
- PLN-E05-014 — generic statutory memberships;
- PLN-E05-015/016 — tax profile/regime election, previous-employer
  income/declarations/proofs;
- PLN-E05-017 — scoped payroll holds;
- PLN-E05-019 — complete immutable employee payroll snapshot;
- PLN-E05-020 — broad onboarding/readiness/exception workbench;
- E06 calculation execution or official gross-to-net;
- country-specific statutory rates/formulas/legal conclusions;
- salary TDS or tax computation;
- retro/off-cycle/final settlement execution;
- banking/payment execution;
- balances, accounting or remittance settlement.

Country-specific PF, ESI, NPS or salary-tax behavior still requires separately
approved legal/domain authority.

## 8. Architecture controls that remain mandatory

P5-EPA-01 must preserve:

- Java 21 / Spring Boot modular monolith boundaries;
- PostgreSQL/Flyway forward-only migrations;
- stable identity plus immutable/effective-dated versions where the domain
  requires history;
- half-open effective ranges;
- tenant-safe foreign keys and FORCE RLS;
- non-owner/NOBYPASSRLS runtime;
- no cross-module JPA relationships or internal-package imports;
- atomic audit/outbox/idempotency evidence;
- Keycloak/OIDC authorization;
- RFC 9457 error contracts;
- exact decimal/currency semantics;
- no hidden server-time dependence;
- no historical overwrite or delete shortcut.

`employee-payroll` owns employee payroll relationship/assignment/binding state.
`compensation` remains the authority for component catalogue and salary
structure configuration. Cross-module use must go through public APIs.

## 9. Publication and sequencing

After this activation authority merges:

1. P5-EPA-01 becomes the active execution capability at G01 only;
2. product write owner remains NONE;
3. V050 remains unreserved;
4. G01 publishes the architecture/schema verdict;
5. only the separately authorized implementation gate may acquire product path
   ownership and, if G01 proves it necessary, V050;
6. backend publication precedes UI hosted cross-repository E2E when the UI CI
   consumes backend `main`;
7. post-product closure must use
   `docs/governance/payroll-capability-closure-standard.md`.

## 10. Definition of activation done

Activation is complete only when:

- this scope authority is merged;
- program status, thread registry and continuation handoff identify P5-EPA-01
  G01 consistently;
- canonical story statuses remain unchanged;
- V050 remains unreserved;
- no product code or migration SQL changed;
- no product write owner is assigned; and
- the next action is G01 read-only architecture/schema verdict.
