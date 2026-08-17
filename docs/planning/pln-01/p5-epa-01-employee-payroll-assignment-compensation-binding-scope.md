# P5-EPA-01 — Employee Payroll Assignment & Compensation Binding Completion

**Status:** G01 CLOSED — G02 IMPLEMENTATION AUTHORIZED after this verdict authority merges
**R3 selection date:** 17 August 2026
**Backend/program authority baseline:** activation PR #81 merge/main `6e29c564f20694c1855972b426fb18accb0631ab`
**UI authority baseline:** `f2d7d1ac1e96cf154b624cf583681c6b751b5219`
**Canonical epic:** E05 — Employee Payroll Profile
**Migration state after G01 authority:** V001–V049 immutable; V050 reserved exclusively to P5-EPA-01 G02
**Product write state after G01 authority:** G02 exact backend/UI allow-list authorized

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
4. G01 publishes the architecture/schema verdict through the separately merged
   authority containing Sections 11-16 below;
5. after that authority merges, G02 acquires only the exact product paths in
   Section 14 and V050 is reserved exclusively to P5-EPA-01;
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

Activation definition-of-done is satisfied by PR #81 / merge
`6e29c564f20694c1855972b426fb18accb0631ab`. The sections below are the
subsequent G01 verdict and G02 implementation authority.

## 11. G01 architecture/schema/API/UI verdict

G01 is **CLOSED** against backend/program main
`6e29c564f20694c1855972b426fb18accb0631ab` and UI main
`f2d7d1ac1e96cf154b624cf583681c6b751b5219`.

The inherited S2-05/S2-06 implementation is valid foundation and must be
evolved, not replaced. V021/V022 stable identity/version history, V038 explicit
pay-group assignment/routing compatibility, and the completed E04 salary-
structure target/override contracts remain authoritative.

The migration verdict is **ADDITIVE V050 REQUIRED**. V001-V049 remain immutable.
V050 is reserved exclusively to P5-EPA-01 after this G01/G02 authority merges.

### 11.1 Relationship boundary

`payroll_relationship_version` must gain an explicit exact
`payroll_statutory_unit_version_id` and an opaque `aggregation_boundary_key`.
The PSU version must belong to the relationship's exact legal-entity version
and contain the relationship effective range.

Country and employer currency remain derived from the exact approved
organisation/legal-entity version. P5-EPA-01 must not create independent mutable
employee country or employer-currency truth.

V050 must preserve legacy V021 rows without guessing missing responsibility
facts. Legacy rows may remain boundary-incomplete after migration when their PSU
or aggregation boundary cannot be proven deterministically; new/successor
relationship approval must require the complete boundary.

### 11.2 Payroll assignment and concurrency

The stable payroll-assignment identity must carry an external/source work-
assignment reference. Assignment versions must expose payroll eligibility dates
and an explicit PRIMARY/SECONDARY payroll role while retaining the exact
relationship-version and establishment-version lineage.

Concurrent assignments are allowed only as explicit distinct assignment
identities/source references. No historical assignments may be merged merely
because effective ranges overlap.

### 11.3 Pay-group assignment

Do not introduce another routing or pay-group assignment model. The existing
approved explicit `pay_group_assignment` remains the governed employee override,
and V038 routing remains the fallback authority when no explicit approved
assignment applies.

P5-EPA-01 must extend compatibility/impact evidence so an approved explicit
assignment is valid only when relationship PSU, assignment establishment PSU,
pay-group PSU, effective range and salary-structure frequency are compatible.
Backdated changes must expose affected-period evidence before approval; they do
not execute retro payroll.

### 11.4 Salary structure and employee compensation target

Keep the existing salary-assignment identity/history and add an explicit
employee target contract: target type, target value, target frequency, currency
and source compensation-event identity. The referenced salary-structure version
must be approved and effective, and target/currency/frequency must be compatible
with the completed E04 contract and active regular pay group.

The legacy `monthly_amount`/INR representation is compatibility-only historical
shape after V050. New authoritative writes must not infer employee compensation
from a hard-coded INR monthly amount.

### 11.5 Employee component overrides

Employee overrides must reference the exact approved salary-structure line and
component version. The existing E04 line `overridePolicy` values
`PROHIBITED`, `CONTROLLED` and `ALLOWED` are authoritative; P5-EPA-01 must not
create a second override-policy taxonomy.

An employee override may change only an approved employee value/percentage
within the line's allowed bounds. It must never mutate component identity,
component type, tax treatment, payee, payment channel, protected statutory
classification, calculation meaning or other configuration-owned semantics.
Every override is effective-dated, approved, supersession-safe and audited.

### 11.6 Compensation-change and impact evidence

Add a compensation-change event with explicit classifications
`PROSPECTIVE`, `CURRENT_PERIOD`, `RETROSPECTIVE`, `CORRECTION` and `REVERSAL`.
Correction and reversal lineage must point to the event being corrected or
reversed. Salary assignments created by compensation change must retain the
source event.

Impact assessment must deterministically identify affected payroll periods and
why they are affected. It may retain design-time/estimated values only when they
can be produced without invoking official payroll calculation. P5-EPA-01 must
not recalculate payroll, post retro differences, update balances, create
payments or post accounting.

### 11.7 Transfer, rehire and concurrent-assignment lineage

Add explicit payroll lifecycle lineage evidence for `TRANSFER`, `REHIRE` and
`CONCURRENT_ASSIGNMENT`. Transfer/rehire must record predecessor/successor
relationship and assignment identities where applicable and an explicit
relationship decision of continuation versus successor. Historical payroll
relationships and assignments are never merged or overwritten.

## 12. V050 migration contract

The only migration owned by P5-EPA-01 is:

`database/flyway/sql/V050__employee_payroll_assignment_compensation_binding.sql`

V050 must be forward-only and additive. It must preserve all V021/V022 IDs and
downstream lineage, use tenant-safe composite foreign keys, FORCE RLS for new
tenant tables, half-open effective ranges, deterministic approval guards,
append/supersession history and non-owner runtime restrictions.

V050 may add the minimum tables/columns/functions/indexes needed for:

- relationship PSU/aggregation boundary;
- work-assignment reference, payroll role and eligibility range;
- employee compensation target/source-event binding;
- employee component value/percentage override history;
- compensation-change event and affected-period impact evidence; and
- transfer/rehire/concurrent-assignment lineage.

No country-specific statutory rate, tax rule, PF/ESI/NPS rule, bank/payment
instruction, calculation-result, retro execution, balance or accounting schema
is authorized by V050.

## 13. G02 API, security and UI contract

G02 must evolve the existing employee-payroll HTTP/OpenAPI surface rather than
create a parallel API. Existing relationship, assignment, profile, pay-group
and salary-assignment lifecycle endpoints remain compatible where possible.

New permissions must be narrowly scoped for employee override,
compensation-change/impact and lifecycle-lineage operations. Existing read/write
permissions must not silently gain broader authority. Keycloak seed roles and
API authorization tests must stay aligned.

The existing Employee Payroll React workspace is the starting UI. Required
human-operable journeys are:

- relationship boundary and history;
- source work-assignment/concurrency and assignment history;
- explicit pay-group assignment with compatibility/backdated-impact evidence;
- salary structure + employee compensation target assignment/history;
- controlled component override creation/approval/history;
- compensation-change classification and affected-period impact review; and
- transfer/rehire/concurrent-assignment lineage and audit/history.

All seven selected stories remain `REQUIRED_PRODUCT_UI`; real-backend browser
E2E is mandatory before capability closure.

## 14. G02 exact product path ownership

After this authority merges, P5-EPA-01 G02 owns only the following backend/
program paths:

- `database/flyway/sql/V050__employee_payroll_assignment_compensation_binding.sql`;
- `database/flyway/README.md`;
- `backend/employee-payroll/**`;
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeePayrollMigrationIT.java`;
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeePayrollAssignmentCompensationBindingMigrationIT.java`;
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/EmployeePayrollApiIT.java`;
- `contracts/openapi/employee-payroll-openapi-v1.yaml`;
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`;
- `deploy/local/keycloak/payroll-realm.json`;
- `docs/runbooks/employee-payroll-api.md`;
- `docs/runbooks/employee-payroll-application-layer.md`; and
- `docs/runbooks/employee-payroll-setup-ui.md`.

The UI repository owns only:

- `src/features/employee-payroll/**`; and
- `e2e/p5-epa-01*.ts`.

No `backend/compensation/**` product write is authorized. G02 must consume the
already merged E04 public/schema authority. If implementation proves that a
compensation-module change is unavoidable, stop and obtain an explicit bounded
amendment rather than widening ownership silently.

## 15. Mandatory negative-test matrix

G02 must prove at minimum:

- cross-tenant relationship/assignment/target/override/lineage denial;
- relationship PSU belongs to the exact legal-entity version and effective
  range;
- legacy boundary-incomplete rows are preserved without guessed backfill;
- new/successor relationship approval blocks incomplete PSU/aggregation facts;
- source work-assignment identity is explicit and concurrent identities are not
  silently merged;
- one active regular explicit pay group per payroll assignment/effective date;
- relationship PSU, establishment PSU and pay-group PSU compatibility;
- pay-group/calendar and salary-structure frequency compatibility;
- unapproved, retired or out-of-range salary structures are rejected;
- employee target currency/target contract mismatch is rejected;
- `PROHIBITED` component override is rejected;
- controlled/allowed overrides obey bounds and cannot alter protected component
  semantics;
- maker/approval, idempotency, optimistic concurrency and audit/outbox evidence
  remain enforced;
- correction/reversal compensation events require valid source lineage;
- backdated changes identify affected periods without creating retro results,
  payments, balance movements or accounting entries;
- transfer/rehire explicitly records CONTINUE versus SUCCESSOR relationship
  decision; and
- permission-aware read-only users cannot invoke new write operations.

## 16. G02 sequencing and closure

G02 is authorized after this authority merges:

1. **G02A backend/database/contracts** — implement V050, backend lifecycle,
   OpenAPI, Keycloak and backend/migration tests; publish and merge the backend
   product PR with the exact seven required hosted checks green.
2. **G02B UI** — implement the existing Employee Payroll workspace against the
   exact merged G02A backend authority; publish and merge UI with its required
   hosted checks and cross-repository browser E2E.
3. **G02C reconciliation/closure** — independently review the complete backend
   + UI implementation, reconcile only the seven selected canonical stories
   supported by evidence, then use the standing payroll capability-closure
   engine.

Canonical story status is unchanged by this G01/G02 authority. Banking/payment,
PF/ESI/NPS, tax/declarations, broad readiness, holds, complete employee
snapshots and E06 calculation remain excluded.
