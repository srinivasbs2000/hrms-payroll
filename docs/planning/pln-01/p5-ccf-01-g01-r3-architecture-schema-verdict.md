# P5-CCF-01 G01 R3 Architecture and Schema Verdict

**Evidence baseline:** backend `dfc25a45c22c36be20df733c866cbd5ac0151c61`, UI `42487de1e99240a99df1ba99742a728671c1636e`

**G01 evidence SHA-256:** `45ef2910fdd8e8cadb2086342915c3658eb6a54595bf8e549922c86fa294a16d`

**Product mutation during G01:** NONE

**Binary verdict:** `SCHEMA_AMENDMENT_REQUIRED`

**Required amendment:** P5-CCF-01-R01, reserving V040 only after publication

## 1. Reuse and gap verdict

| Stories | Reusable foundation | Required completion |
|---|---|---|
| PLN-E03-004 | Named payroll-base identities, versions and memberships from V032 | Base semantics exposed to formula compilation and impact views |
| PLN-E03-005 | Component category and jurisdiction-neutral statutory metadata | Effective wage-classification references; legal values remain external |
| PLN-E03-006..007 | Formula text/type columns and version lifecycle | Restricted parser/evaluator, canonical form, dependencies, phase validation and cycle rejection |
| PLN-E03-008 | No reusable multidimensional rate-table persistence | New effective-dated rate-table identity/version/dimension/cell model |
| PLN-E03-009..010 | Scale and calendar-day calculation fragments | Versioned rounding method/stage and event-specific proration policy metadata |
| PLN-E03-011 | Draft/approve/supersede/retire, optimistic locking and maker-checker patterns | Apply those controls to every new metadata aggregate |
| PLN-E03-012 | Component/base list and history APIs | Dependency, impact, formula-validation and rate inspection views |
| PLN-E03-013..016 | v1 API, outbox, permission, audit and RLS foundations | Version new fields additively; add least-privilege actions, audit state and events |
| PLN-E03-017..018 | Contract, API and migration test infrastructure | Formula edge cases, graph, persistence, RLS, concurrency and browser coverage |

PLN-E03-001..003 remain implemented and are not reopened.

## 2. Restricted formula contract

- Grammar: decimal literals; stable component-code references; parentheses; unary minus;
  `+`, `-`, `*`, `/`; `ABS`, `MIN`, `MAX`, `ROUND`.
- No JavaScript, Java, SQL, SpEL, reflection, class lookup, method invocation or
  operating-system execution path exists.
- Maximums: 1,000 characters, 256 tokens, depth 32, 64 dependencies, decimal
  precision 19 and scale 10.
- Compilation produces a canonical expression and ordered unique dependency set.
- Evaluation uses `BigDecimal`/`DECIMAL128`; missing values and zero divisors fail closed.
- Stable error codes and positions are returned for validation failures.

## 3. Dependency and calculation-phase contract

Phases are ordered `INPUT`, `PRE_TAX`, `TAX`, `POST_TAX`, `NET`. A dependency
may reference the same or an earlier phase, never a later phase. Self-reference,
unknown references and cycles fail closed. A stable phase/code priority queue
produces deterministic topological execution order.

## 4. V040 schema amendment boundary

V040 must add, without modifying V001..V039:

1. compiled formula metadata and explicit component dependency edges;
2. effective-dated multidimensional rate-table identities, versions, dimensions
   and rows/cells;
3. versioned rounding policy including method, scale, stage and negative treatment;
4. event-specific proration policies for joining, exit, unpaid leave, transfer and
   salary revision;
5. tenant-aware foreign keys, exclusion/uniqueness constraints, forced RLS,
   optimistic versioning, maker-checker approval metadata and audit/outbox lineage.

No statutory rate, threshold or legal conclusion may be stored by the migration.

## 5. Compatibility, API and event verdict

- Existing `/api/v1/pay-components` and `/api/v1/payroll-bases` behavior remains compatible.
- Formula/rate/control fields are additive; material incompatible changes require a new API version.
- Money remains decimal-string plus ISO currency at API/event boundaries.
- Events carry schema version, stable identity/version, canonical formula fingerprint,
  correlation and actor lineage; event consumers must ignore additive fields.

## 6. Proposed implementation allow-list

Backend product paths:

- `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/**`
- `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/**`
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/**ComponentCatalogue**`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/**ComponentCatalogue**`
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
- `database/flyway/sql/V040__component_catalogue_formula_rate_controls.sql`

UI product paths for the later G03 owner:

- `src/features/pay-component/**`
- `src/features/payroll-base/**`
- exact route/navigation and browser-test files separately enumerated at G03 preflight.

Governance paths are limited to the P5-CCF-01 scope/verdict plus canonical program
status, handoff and selected-story rows during reconciliation.

## 7. Ordered delivery

1. Publish R01 schema/migration amendment authority and reserve V040.
2. G02-A restricted formula compilation and dependency planning.
3. G02-B V040 persistence, repositories, APIs, permissions, audit/outbox and tests.
4. G03 UI workflows and real-backend browser evidence.
5. G04 independent review, ordered merges and story reconciliation.

`P5_CCF_01_G01_R3_VERDICT: SCHEMA_AMENDMENT_REQUIRED`
