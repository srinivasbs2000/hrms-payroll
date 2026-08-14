# P5-E2E-UI-01-B02 — Remaining E02 Contract Boundary Amendment

**Status:** ACTIVE — B02-G01/G02 backend contract published; B02-G03 UI closure is next
**Parent capability:** P5-E2E-UI-01 — Existing Story UI Gap Closure
**Authority baseline:** backend `981417aaa6fc7f9b141dfcf7433ff0fe2cd515da`; UI `2a42f3909a2ee249ca26be8fb0e14e945f8903a9`
**Migration position:** V001-V039 immutable; V040 remains unreserved
**P5-A5/E03:** NOT ACTIVATED

## 1. Purpose

G06 conservatively left eight E02 stories PARTIALLY IMPLEMENTED because browser
coverage cannot close a story when required backend administration/readiness
contracts are absent.

B02 is the separately bounded amendment authority required by the parent scope.
It authorizes only the smallest backend contract exposure and subsequent UI work
needed to close the demonstrated E02 boundaries.

Selected rows remain unchanged at activation:

- `PLN-E02-002`
- `PLN-E02-003`
- `PLN-E02-004`
- `PLN-E02-006`
- `PLN-E02-007`
- `PLN-E02-008`
- `PLN-E02-009`
- `PLN-E02-010`

No story is promoted by this activation document.

## 2. Read-only contract audit conclusion

The current repository already contains the underlying persistence/function
primitives for the demonstrated gaps.

Calendar foundation already provides:

- `payroll.payroll_calendar_milestone_rule`;
- `payroll.payroll_calendar_holiday`;
- `payroll.configure_payroll_calendar_milestone_rule`;
- `payroll.add_payroll_calendar_holiday`;
- `payroll.resolve_working_day`;
- `payroll.generate_pay_periods`;
- tenant/RLS coverage for calendar configuration;
- generated original/adjusted milestone evidence.

Pay-group foundation already provides:

- `organisation.pay_group_routing_rule`;
- `organisation.create_pay_group_routing_rule`;
- `organisation.resolve_employee_pay_group`;
- `organisation.pay_group_assignment_compatibility_issues`;
- effective/ranked routing semantics and tenant/RLS coverage.

The current Java/HTTP/UI layers do not expose the complete administration and
readiness contracts required by the remaining stories.

**Activation decision:** no schema migration is authorized or expected for B02.
V039 remains unreserved.

If implementation proves that safe correction/history semantics cannot be
provided with the existing V038 schema/functions, the affected B02 gate must
STOP and return for a separately reviewed amendment. It must not reserve or
create V039 implicitly.

## 3. B02-G01 — backend contract exposure

B02-G01 is the first product-write gate after this activation merges.

### 3.1 Calendar configuration contract

Expose a bounded, tenant-safe HTTP/application contract for:

- reading the five milestone rules for a calendar version;
- configuring the complete required five-rule set while the governed calendar
  is in the valid configuration state;
- reading configured holidays / working-day exceptions;
- bounded holiday configuration/correction using existing V038 semantics;
- returning readiness evidence sufficient for generation/publication decisions.

The exact route names are finalized during the B02-G01 artifact-contract
preflight. The resource boundary must remain under payroll-calendar
configuration; no unrelated API expansion is authorized.

Existing lifecycle, generated-period and operational-evidence contracts remain
authoritative and must not be duplicated.

### 3.2 Pay-group routing administration contract

Expose bounded administration/inspection for the existing
`organisation.pay_group_routing_rule` foundation:

- inspect current/effective routing rules;
- author effective-dated/ranked routing rules using the existing function
  semantics;
- preserve deterministic employee/payroll population resolution;
- do not duplicate the existing population resolver.

If safe correction/end-dating cannot be expressed through the current database
contract, stop that sub-boundary and seek amendment authority.

### 3.3 Compatibility/readiness contract

Expose proactive read evidence based on existing compatibility/readiness
primitives, including the information needed to explain blockers such as:

- pay-group/calendar coverage;
- calendar generation/publication readiness;
- routing/assignment compatibility;
- bounded PSU/frequency/timezone/coverage context already represented by the
  current model.

This is a read/readiness exposure, not a new policy engine.

### 3.4 Security and audit

B02-G01 must preserve:

- tenant isolation and RLS;
- existing actor/audit/correlation conventions;
- least privilege;
- current calendar/pay-group authorization boundaries.

Existing permissions should be reused where semantically correct. Any proposal
for a new permission requires explicit B02-G01 security review and must not
silently broaden access.

## 4. B02-G02 — backend evidence and publication

Before UI product work:

- OpenAPI impact must be explicit;
- REST integration tests must cover positive, negative, lifecycle and tenant
  isolation behavior;
- existing V038 database-contract tests remain green;
- full affected Maven/reactor validation must pass;
- hosted `payroll-baseline` must be green;
- backend contract changes must merge to `main`.

No UI product write begins against an unpublished backend contract.

## 5. B02-G03 — UI closure

Only after B02-G02 backend merge, revalidate exact UI ownership and implement
the smallest operator/admin surfaces for:

- milestone-rule administration;
- holiday/working-day configuration;
- routing-rule administration/inspection;
- proactive compatibility/readiness;
- consolidated operational visibility required by the remaining selected rows.

Browser E2E must prove the complete applicable workflows. The UI must not claim
completion for any backend behavior that remains unavailable.

## 6. B02-G04 — truthful closure reconciliation

After backend + UI publication:

- independently re-evaluate `PLN-E02-002`, `003`, `004`, `006`, `007`, `008`,
  `009`, `010`;
- promote only stories whose complete acceptance boundary is evidenced;
- retain any unresolved story as PARTIALLY IMPLEMENTED with exact blocker;
- P5-A5/E03 remains inactive until this reconciliation is complete.

## 7. Explicit exclusions

B02 does not authorize:

- P5-A5/E03;
- payroll calculation engine expansion;
- statutory/tax/payments functionality;
- broad redesign of existing pay-group/calendar lifecycle;
- history rewrite;
- unrelated security/permission expansion;
- a migration or V039 reservation;
- backend or UI product writes before this activation authority merges.

## 8. Activation success criteria

Activation is complete only when:

1. this scope and the three parent governance files merge through hosted CI;
2. backend and UI baselines remain clean and exact;
3. story totals remain 21 IMPLEMENTED / 155 PARTIALLY IMPLEMENTED /
   84 NOT EVIDENCED / 159 NOT STARTED / 31 LEGAL/DOMAIN REVALIDATION;
4. V039 remains unreserved;
5. P5-A5/E03 remains inactive;
6. the next controlled action is B02-G01 backend contract implementation
   preflight against the activation merge SHA.
<!-- P5-E2E-UI-01-B02-R01-ACTIVATION -->
## 10. B02-R01 routing correction/end-dating amendment

B02-G01 preflight executed the stop condition in section 3.2. V038 supports
routing-rule inspection/create and global retire, but it does not expose a safe
effective-end/correction function and `payroll_app` direct routing-table UPDATE
remains revoked.

The separately bounded `P5-E2E-UI-01-B02-R01` amendment is therefore activated
for the missing database contract only. This activation does not reserve V039.

After R01 activation merges, R01-G01 may reserve V039 only after confirming no
concurrent reservation and only for the bounded effective-end function,
minimum grant and contract tests. No schema/table shape expansion is authorized.

B02-G01 Java/HTTP implementation remains paused until the R01 database contract
is published. P5-A5/E03 remains inactive.

<!-- P5-E2E-UI-01-B02-R01-G01-CLOSURE -->
## 11. R01-G01 database-contract publication outcome

The R01 stop boundary is resolved. V039 was reserved only after the independent
migration preflight and adds the bounded
`organisation.end_date_pay_group_routing_rule` contract without table-shape,
policy or direct-DML expansion.

Implementation commit `6d528362b6d9ccb5066f5c033caa8035b0f6ab82` merged through
backend PR #66 at `246ca75983b37293b74fdb4baa44e093fa546f8f`; targeted and
full Maven verification passed, followed by all seven hosted backend checks.

R01-G01 is closed. V001–V039 are immutable and V040 is unreserved. The earlier
B02-G01 resume instruction was executed and is superseded by the B02-G02
closure checkpoint below. P5-A5/E03 remains inactive.

<!-- P5-E2E-UI-01-B02-G02-CLOSURE -->
## 12. B02-G01/G02 backend-contract publication outcome

The artifact-contract preflight authorized no migration and no new permission.
B02-G01 then exposed the existing V038/V039 calendar and routing foundations
through bounded Java/HTTP/OpenAPI contracts.

Implementation commit `52da3d39508c5a1c59d8cc59c10819368b55ab9b`
merged through backend PR #68 at
`d635200523c1685f42ae08c24bd6d7acaa7d68a3`. Local affected compilation,
targeted contract tests, PayGroupApiIT, PayrollCalendarApiIT, full Maven and
OpenAPI validation passed. All seven hosted backend checks passed before merge,
and merge-tree equivalence was proven.

B02-G01 and B02-G02 are closed. Their published business boundary includes:

- complete five-rule payroll-calendar milestone configuration and inspection;
- holiday/working-day configuration and correction with draft-state control;
- calendar readiness evidence;
- routing-rule inspection, creation and governed effective end-dating;
- deterministic pay-group resolution and compatibility findings;
- bounded interval routing-readiness checkpoints and coverage evidence.

No story is promoted at this backend-only gate. Totals remain 21 / 155 / 84 /
159 / 31. V001–V039 remain immutable, V040 is unreserved and P5-A5/E03 remains
inactive.

After this reconciliation merges, section 5 B02-G03 is active with:

- backend authority: `d635200523c1685f42ae08c24bd6d7acaa7d68a3`;
- UI baseline: `2a42f3909a2ee249ca26be8fb0e14e945f8903a9`;
- UI repository: `srinivasbs2000/hrms-payroll-web`;
- UI product branch: `feature/p5-e2e-ui-01-b02-g03-ui-closure`;
- backend/program repository: read-only except later evidence reconciliation;
- migration and permission mutation: prohibited.
