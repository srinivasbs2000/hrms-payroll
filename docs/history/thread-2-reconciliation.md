# HRMS Payroll Thread Checkpoint

## Identity

- **Thread:** Thread 2
- **Exact title:** `Payroll System Design - Thread 2`
- **Historical purpose:** Complete inherited Sprint 2 payroll-configuration and employee-payroll work, close PR #3, then start Sprint 3 regular-payroll execution and implement through the controlled recalculation database foundation V026 before handing the remaining Sprint 3 scope to Thread 3.
- **Record type:** Existing-thread reconciliation record
- **Reconciliation mode:** Read-only
- **Evidence cut-off:** 1 August 2026, 11:58 Asia/Kolkata
- **Role at historical entry:** `IMPLEMENTATION OWNER`
- **Role at historical exit:** `RECOVERY/HANDOFF`
- **Recommended final role:** `CLOSED`
- **Repository:** `srinivasbs2000/hrms-payroll`
- **Current repository branch:** `main`
- **Current repository HEAD:** `4b5da975eb851434957667bdecf138ea9b43f929`
- **Current implementation baseline:** Sprint 4 implementation merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`, followed by documentation/governance merge `4b5da975eb851434957667bdecf138ea9b43f929`
- **Historical pull requests owned or materially advanced by this thread:** PR #3 and PR #18
- **Historical branches:** `feature/sprint-2-payroll-configuration`; `feature/sprint-3-payroll-execution`
- **Current active Thread 2 branch/PR:** None
- **Working tree:** `NOT VERIFIED`
- **Git index:** `NOT VERIFIED`
- **Current local branch and HEAD:** `NOT VERIFIED`
- **Template used:** `docs/templates/thread-checkpoint-template.md`
- **Repository modification during this pass:** None
- **Branch creation or switching during this pass:** None
- **Migration reservation during this pass:** None
- **Stage, commit, push, PR update or merge during this pass:** None

## Evidence classification

| Label | Meaning |
|---|---|
| `VERIFIED - REMOTE` | Confirmed from current GitHub repository, branch, PR, commit, workflow or committed file. |
| `VERIFIED - THREAD RECORD` | Confirmed by the committed historical Thread 2 reconciliation or a durable Thread 2 checkpoint cited by that record. |
| `DERIVED` | Direct conclusion from identified verified evidence. |
| `DESIGN BASELINE` | Approved repository design or decision-register rule, not necessarily introduced by Thread 2. |
| `DOCUMENTATION CONFLICT` | Current repository authorities disagree, or historical naming conflicts with current canonical naming. |
| `SUPERSEDED` | Historically accurate work or state replaced by a later merged implementation or authority. |
| `NOT VERIFIED` | Exact evidence is unavailable; no inference is permitted. |

---

## Approved scope

### Capability and story scope historically owned

Thread 2 had two sequential implementation scopes:

1. **Sprint 2 closure**
   - recover and validate the inherited Sprint 2 feature branch;
   - implement V021 employee-payroll identity and assignment foundation;
   - implement V022 foundation negative-path hardening;
   - complete employee-payroll services, APIs, OpenAPI, permissions and UI;
   - verify and close Sprint 2 through PR #3.

2. **Sprint 3 execution foundation through V026**
   - create regular payroll cycle and population-resolution foundation;
   - expose cycle and population APIs;
   - seal immutable payroll input snapshots;
   - expose snapshot APIs;
   - execute deterministic BASIC/HRA/SPECIAL_ALLOWANCE calculation with calendar-day proration;
   - persist immutable result, component and trace evidence;
   - expose calculation and historical result APIs;
   - implement controlled recalculation and supersession database foundation;
   - stop at a durable, pushed, green transition boundary before S3-04B.

### Exact active file allow-list

`NONE`.

Thread 2 is historical and closed. It has no active write ownership over any repository file.

### Historical bounded file ownership

- Sprint 2: compensation/configuration, employee-payroll, corresponding migrations V017–V022, OpenAPI, Keycloak, frontend workspaces, tests, runbooks and CI/dependency-policy changes on PR #3.
- Sprint 3 at Thread 2 exit: the exact 51-file delta from Sprint 2 merge `84530e1...` to Thread 2 exit `db644298...`, listed under **Repository surfaces affected**.

### Migration reservation

- **Historical:** V021, V022, V023, V024, V025 and V026 were directly implemented or closed by Thread 2.
- **Current:** `NONE`.
- **Current repository migration baseline:** V001–V030 are committed and immutable.
- **Next migration available to a separately authorised implementation owner:** V031.
- **Any current local-only reservation:** `NOT VERIFIED`.

### Explicit exclusions at Thread 2 exit

- jurisdiction-specific statutory deductions and tax;
- retroactive and off-cycle payroll;
- final settlement;
- banking and payment execution;
- accounting and general-ledger integration;
- legal/final payslip publication;
- uncontrolled dependency upgrades;
- rewriting committed migrations;
- merge without a separate explicit authorization.

---

## Authority files read

- [x] `AGENTS.md`
- [x] `docs/design/hrms-payroll-master-design.md`
- [x] `docs/design/decision-register.md`
- [x] `docs/runbooks/project-continuation-handoff.md`
- [x] `docs/governance/thread-registry.md`
- [x] `docs/governance/thread-maintenance-protocol.md`
- [x] `docs/templates/thread-checkpoint-template.md`
- [x] `README.md`
- [x] `backlog/organisation-to-draft-payslip-sprint-backlog.csv`
- [x] PR #3 metadata
- [x] PR #18 metadata
- [x] PR #20 metadata and changed-file list
- [x] current branch list
- [x] current `main` comparison
- [x] current and historical CI evidence available through GitHub
- [x] committed historical record `docs/history/thread-2-reconciliation.md`
- [x] exact Thread 2 Sprint 3 commit comparison `84530e1...db644298`
- [ ] current local branch, HEAD, index, working tree and complete diff — `NOT VERIFIED`
- [ ] local-only Thread 2 checkpoint archives — retrieval unavailable in this pass

---

## Current repository checkpoint

| Item | Current verified fact | Evidence classification |
|---|---|---|
| Current `main` SHA | `4b5da975eb851434957667bdecf138ea9b43f929` | VERIFIED - REMOTE |
| Proof | Comparison of `4b5da975...` to `main` is identical | VERIFIED - REMOTE |
| Latest merge | PR #20, `docs(project): establish living design and thread governance` | VERIFIED - REMOTE |
| PR #20 base | `def3dd2e212f85c440eee5497e292be2f1f2bf64` | VERIFIED - REMOTE |
| PR #20 head | `20935aa4f73dc7e6262cf4bf5f82a3d0b81c2395` | VERIFIED - REMOTE |
| PR #20 merge | `4b5da975eb851434957667bdecf138ea9b43f929` | VERIFIED - REMOTE |
| PR #20 CI | `payroll-baseline` run 83, success, on branch head `20935aa4...` | VERIFIED - REMOTE |
| Direct workflow on merge commit | No PR-triggered run returned | NOT VERIFIED |
| Current implemented product baseline | Sprint 1–4 vertical slice | VERIFIED - REMOTE |
| Current migrations | V001–V030, immutable | VERIFIED - REMOTE |
| Next migration | V031, only after registration and authorization | DESIGN BASELINE |
| Thread 2 registry status on current `main` | `NOT VERIFIED`, no write ownership | VERIFIED - REMOTE |
| Current active Thread 2 implementation | None | DERIVED |
| Current local state | Unavailable | NOT VERIFIED |

### Current authority-baseline conflict

PR #20 established the living authority system, but several authority files still state the prior Sprint 4 merge SHA `def3dd2e...` as the current repository baseline.

Current GitHub proves that `main` is now `4b5da975...`.

Affected authority fields include:

- master-design “Current verified baseline”;
- thread-registry “Repository baseline”;
- running-handoff superseding checkpoint;
- the committed historical Thread 2 reconciliation.

This is a `DOCUMENTATION CONFLICT`, not an implementation conflict. The product implementation baseline remains the Sprint 4 merge `def3dd2e...`, while the exact current repository HEAD is the later documentation merge `4b5da975...`.

### Current encoding defect

The governance merge contains visible mojibake in existing and newly touched authority text, including strings such as:

- `V001â€“V030`;
- `Sprint 1â€“4`;
- `DRAFT Â· NOT FOR PAYMENT`;
- corrupted smart quotes and em-dashes in the running handoff.

This is current documentation debt introduced or exposed by PR #20. It is not Thread 2 implementation debt, but it affects authority-file readability and should be corrected by the governance consolidation owner.

---

## Historical thread purpose and ownership

Thread 2 was an implementation-and-recovery thread, not a design-only thread.

### Entry condition

At Thread 2 entry:

- Sprint 1 was already merged.
- Sprint 2 branch `feature/sprint-2-payroll-configuration` and PR #3 already existed.
- V017–V020 and their configuration/application work were already present on the feature branch.
- The inherited remote head was `24f2ed4893a90627eb6be69aa3747eba4343e195`.
- V021 had been started locally but had not been completed and verified.
- The immediate instruction was to inspect existing work and continue without rewriting committed migrations.

### Exit condition

At Thread 2 exit:

- Sprint 2 was merged through PR #3.
- Sprint 3 branch `feature/sprint-3-payroll-execution` and PR #18 existed.
- Thread 2 had published seven Sprint 3 commits through V026.
- Thread 2 exit head was `db644298ab3197a6931cd9c6b8d9875ef30d28c5`.
- CI run 55 was green.
- S3-04A was complete.
- S3-04B and later Sprint 3 work were handed to Thread 3.

---

## Historical branches, pull requests and commits

### Sprint 2

| Field | Verified value |
|---|---|
| Branch | `feature/sprint-2-payroll-configuration` |
| Current branch state | Not present in current remote branch list; treated as deleted/superseded after merge |
| PR | #3 — `Sprint 2: payroll configuration foundation` |
| Base branch/SHA | `main` / `42d4b50a8fae64c12ddfc1fcb5553476d86fb252` |
| Inherited Thread 2 entry head | `24f2ed4893a90627eb6be69aa3747eba4343e195` |
| First Thread 2 implementation commit | `adf3769b945d56828aa984e634e6e1bbb62582d7` |
| Last Sprint 2 branch commit | `e98f70b0346a13e463f8e768ab4014be0e30ca0f` |
| Merge commit | `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9` |
| PR state | Closed and merged |
| PR size | 16 commits; 111 changed files |
| Final branch-head CI | `payroll-baseline` run 35, success |

#### Direct Thread 2 Sprint 2 commits

| Commit | Purpose | Status |
|---|---|---|
| `adf3769b945d56828aa984e634e6e1bbb62582d7` | Employee-payroll identity and assignment foundation / V021 | MERGED |
| `1575cbc373bf4dc22ff116b1ea4bbfb7e5a19288` | Foundation negative-path hardening / V022 | MERGED |
| `63c9b1a719765fce3868eb7fc69fac37bc196dc9` | Employee-payroll services and contracts | MERGED |
| `12536c3f629cf567022f3fd50998397d1d0b5911` | Employee-payroll lifecycle APIs | MERGED |
| `e98f70b0346a13e463f8e768ab4014be0e30ca0f` | Employee-payroll setup workspace | MERGED |

The earlier PR #3 commits through `24f2ed...` were inherited and closed by Thread 2, but were not first authored in Thread 2.

### Sprint 3

| Field | Verified value |
|---|---|
| Branch | `feature/sprint-3-payroll-execution` |
| Current branch state | Still present remotely but historical/superseded |
| PR | #18 — `Sprint 3: payroll execution foundation` |
| Base branch/SHA | `main` / Sprint 2 merge `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9` |
| First Thread 2 Sprint 3 commit | `5bc08e440c21bbeeddc3c1bb4e28ad04943ac9cd` |
| Thread 2 exit head | `db644298ab3197a6931cd9c6b8d9875ef30d28c5` |
| Thread 2 delta | 7 commits; 51 changed files; 0 commits behind base |
| Thread 2 exit CI | `payroll-baseline` run 55, success |
| Final PR head after Thread 3 | `ebd2603d91551c6f9e60dc57e2d3500948015703` |
| PR merge commit | `73c356662b1888194a72c7006a66bd91443550ca` |
| PR state | Closed and merged |
| Final PR size | 15 commits; 107 changed files |
| Final branch-head CI | `payroll-baseline` run 63, success |

#### Direct Thread 2 Sprint 3 commits

| Commit | Increment | Status |
|---|---|---|
| `5bc08e440c21bbeeddc3c1bb4e28ad04943ac9cd` | S3-01A cycle/population database foundation / V023 | MERGED |
| `64b4ca7b2a7a53c373b56d5f6767192a000dd60f` | S3-01B cycle and population APIs | MERGED |
| `625e38dc1fed649eb37ec6c1d1171f142430403a` | S3-02A immutable input sealing / V024 | MERGED |
| `134fe3e63e6b04f2da08df957f4d415a1fd97606` | S3-02B input-snapshot APIs | MERGED |
| `c9ada6bad94071d70a6d10fbcfec085d476a6279` | S3-03A deterministic starter calculation / V025 | MERGED |
| `f7eb7fa1fc152b8da4088b881f03bff18558d140` | S3-03B calculation and historical-read APIs | MERGED |
| `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | S3-04A controlled recalculation and supersession / V026 | MERGED |

### Work-state classification

| Historical work | Current classification |
|---|---|
| Sprint 2 PR #3 | MERGED |
| Thread 2 Sprint 2 commits | MERGED |
| Thread 2 Sprint 3 commits through V026 | MERGED |
| Remaining Sprint 3 work | SUPERSEDED by later Thread 3 implementation on PR #18 |
| Sprint 2 feature branch | SUPERSEDED / absent remotely |
| Sprint 3 feature branch | SUPERSEDED / historical branch still present |
| Thread 2 durable handoff ZIPs outside Git | LOCAL-ONLY or artifact-only; exact current availability NOT VERIFIED |
| Thread 2 active ownership | None |

---

## Migrations and backlog stories

### Sprint 2

| Migration | Responsibility | Canonical backlog story | Thread relationship | Status |
|---|---|---|---|---|
| V017 `pay_group_identity_versions` | Pay-group stable identity and effective-dated versions | S2-02 Pay group | Inherited and closed | MERGED / IMMUTABLE |
| V018 `payroll_calendar_period_foundation` | Monthly calendars and deterministic periods | S2-01 Calendar | Inherited and closed | MERGED / IMMUTABLE |
| V019 `pay_component_identity_versions` | Pay-component identity/version lifecycle | S2-03 Compensation | Inherited and closed | MERGED / IMMUTABLE |
| V020 `salary_structure_identity_versions` | Salary-structure identity/version separation and exact component-line lineage | S2-04 Compensation | Inherited entry head | MERGED / IMMUTABLE |
| V021 `employee_payroll_identity_assignments` | Payroll relationships, assignments, profiles and exact assignment lineage | S2-05 and S2-06 | Direct Thread 2 implementation | MERGED / IMMUTABLE |
| V022 `foundation_negative_path_hardening` | Parent-range, dependent end-date, tenant and lifecycle hardening | Cross-cutting closure; no independent current backlog row | Direct Thread 2 implementation | MERGED / IMMUTABLE |

### Sprint 3

| Migration | Responsibility | Canonical backlog relationship | Status |
|---|---|---|---|
| V023 `payroll_cycle_population_resolution` | Regular cycle and immutable population-resolution evidence | S3-01 | MERGED / IMMUTABLE |
| V024 `payroll_input_snapshot_sealing` | Canonical immutable employee calculation input | S3-02 | MERGED / IMMUTABLE |
| V025 `deterministic_starter_calculation` | BASIC/HRA/SPECIAL plan, calendar-day proration, result/component/trace persistence | Spans S3-03, S3-04 and S3-05 | MERGED / IMMUTABLE |
| V026 `controlled_recalculation_supersession` | Controlled recalculation, attempts, supersession and history preservation | No distinct row in the current Sprint 3 backlog | MERGED / IMMUTABLE |

### Story-label conflict

The current backlog defines:

- S3-03 — compile BASIC/HRA/SPECIAL plan;
- S3-04 — calendar-day proration;
- S3-05 — persist immutable results and component trace;
- S3-06 — draft payslip;
- S3-07 — golden end-to-end scenario.

Thread 2 used implementation-slice labels such as `S3-03A`, `S3-03B` and `S3-04A`, where `S3-04A` referred to controlled recalculation rather than the backlog’s proration story.

**Classification:** DOCUMENTATION CONFLICT.

**Permanent rule:** identify historical work by migration number, capability and commit SHA; never rely on a story label alone when the historical slice notation diverges from the current backlog.

---

## Durable decisions

| Decision ID | Decision | Status | Evidence |
|---|---|---|---|
| T2-D01 | Repository evidence outranks conversation memory. | IMPLEMENTED | Current AGENTS, master design and MDR-001 |
| T2-D02 | Existing committed migrations are immutable; upgrades are forward-only. | IMPLEMENTED | V006→V021 strategy; current MDR-015 |
| T2-D03 | Preserve existing UUIDs as historical version IDs wherever practical. | IMPLEMENTED | V017, V020, V021; MDR-004 |
| T2-D04 | Payroll lineage references exact approved effective-dated versions, not only stable identities. | IMPLEMENTED | V017–V026 and master design |
| T2-D05 | Effective ranges are half-open and approved ranges do not overlap. | IMPLEMENTED | MDR-005 |
| T2-D06 | Tenant-owned relationships use tenant-safe composite FKs and ENABLE/FORCE RLS. | IMPLEMENTED | MDR-006 |
| T2-D07 | Runtime roles remain non-owner, NOBYPASSRLS and use transaction-scoped `SET LOCAL`. | IMPLEMENTED | MDR-007 |
| T2-D08 | Direct runtime mutation of immutable history/evidence is prohibited. | IMPLEMENTED | Master design immutable-evidence rules |
| T2-D09 | Idempotency, audit and outbox evidence commit atomically with the business write. | IMPLEMENTED pattern | MDR-009 |
| T2-D10 | `mvn verify`, not `mvn test`, is the backend integration gate. | IMPLEMENTED | MDR-011 |
| T2-D11 | Frontend lint, tests and production build are separate gates. | IMPLEMENTED | MDR-012 |
| T2-D12 | OpenAPI, backend, Keycloak and UI permissions must align exactly. | IMPLEMENTED | Master design security/API rules |
| T2-D13 | Calculation is deterministic and snapshot-based. | IMPLEMENTED | V024–V026 and master design |
| T2-D14 | Recalculation appends a new attempt and preserves prior requests/results/components/trace. | IMPLEMENTED | V026 and later Sprint 3 completion |
| T2-D15 | A sprint feature PR remains draft/unmerged until bounded scope and closure gates are complete. | IMPLEMENTED historically | PR #3 and PR #18 workflow |
| T2-D16 | Stage, commit, push, PR update and merge are separate user-authorised actions. | IMPLEMENTED process | AGENTS and Thread 2 handoffs |
| T2-D17 | Feed-dependent OWASP Dependency-Check bootstrap is separated from deterministic PR dependency review. | TEMPORARY/DEBT | MDR-019 |
| T2-D18 | Modified-file generation must use exact current repository blobs, not synthetic reduced bases. | APPROVED historical prevention | Thread 3 handoff generated by Thread 2; not yet indexed in decision register |
| T2-D19 | Versioning/supersession work must audit inherited unique constraints and indexes. | APPROVED historical prevention | V026 uniqueness failure and repair; not yet indexed in decision register |
| T2-D20 | Durable checkpoints are created at pushed, green phase boundaries and before transition. | APPROVED process | Current maintenance protocol; Thread 2 checkpoints |

### Decisions already represented in the current decision register

T2-D01 through T2-D17 substantially map to MDR-001, MDR-004–MDR-012, MDR-014, MDR-015 and MDR-019.

### Decisions not yet clearly represented

The current decision register does not explicitly index:

- exact-blob/full-file replacement after repeated synthetic patch failures;
- inherited-constraint audit before supersession/versioning;
- atomic one-block delivery for complex PowerShell repair commands;
- story-label reconciliation by migration/capability/commit.

These should be evaluated by Thread 1 during consolidation. They should not automatically be added without confirming they meet the decision-register trigger.

---

## Implementation state

### Completed directly by Thread 2

#### Sprint 2

- V021 forward-only employee-payroll upgrade.
- Stable payroll-relationship and payroll-assignment identities.
- Employee payroll profile lifecycle.
- Exact pay-group and salary-structure version assignments.
- V022 negative-path hardening.
- Employee-payroll services and repositories.
- Employee-payroll REST APIs.
- OpenAPI integration.
- Keycloak permission mappings.
- Employee-payroll setup UI.
- Migration, API, RLS, permission, frontend and OpenAPI verification.
- PR #3 closure and merge transition.

#### Sprint 3 through Thread 2 exit

- regular payroll cycle creation and lifecycle;
- deterministic population resolution;
- immutable inclusion/exclusion decisions;
- cycle and population APIs;
- immutable input-snapshot sealing;
- canonical payload and configuration lineage;
- snapshot APIs;
- deterministic BASIC/HRA/SPECIAL_ALLOWANCE calculation;
- calendar-day proration;
- immutable request/result/component/trace persistence;
- result and trace reads;
- controlled recalculation database foundation;
- preserved calculation history;
- V026 uniqueness repair;
- CI run 55 success;
- durable handoff to Thread 3.

### Partially completed at Thread 2 exit

| Capability | Thread 2 exit state | Current repository state |
|---|---|---|
| Controlled recalculation | Database foundation complete; application/API layer deferred | Completed later by Thread 3 and merged |
| Recalculation audit/outbox replay control | Database support present; application evidence deferred | Completed later by Thread 3 |
| Payroll execution UI | Not started | Completed later by Thread 3 |
| Persisted draft-payslip UI | Not started | Completed later by Thread 3 |
| Sprint 3 negative-path closure | Focused coverage present; sprint-wide closure incomplete | Completed later by Thread 3 |
| Sprint 3 E2E/browser regression | Not started | Completed later by Thread 3 |
| Sprint 3 closure report/manual smoke | Not started | Completed later by Thread 3 |
| Durable OWASP Dependency-Check cache/data service | Deferred | Current completion NOT VERIFIED |

### Not started by Thread 2

- legal India statutory rule packs;
- statutory filing/returns and acknowledgements;
- statutory remittance settlement;
- retro and arrears payroll;
- off-cycle/supplementary payroll;
- recoveries and salary advances;
- final settlement;
- banking/payment files or APIs;
- accounting/GL integration;
- legal/final payslip publication.

### Local-only

- Thread 2 checkpoint ZIPs and transitional packages were created outside the repository.
- Exact current local availability and hashes are `NOT VERIFIED`.
- The first reconciliation was later committed as `docs/history/thread-2-reconciliation.md` through PR #20.

### Published

- PR #3, merged.
- PR #18, merged after later Thread 3 completion.
- Historical reconciliation committed through PR #20.
- This regenerated record is an external downloadable artifact only; it is not committed.

---

## Repository surfaces affected

### Sprint 2 modules and areas

- `backend/compensation`
- `backend/employee-payroll`
- `backend/database-migrations`
- `backend/payroll-boot`
- `contracts/openapi`
- `database/flyway/sql/V017...V022`
- `database/flyway/verification`
- `deploy/local/keycloak/payroll-realm.json`
- `frontend/payroll-web` configuration and employee-payroll features
- `.github/workflows/ci.yml`
- `.github/dependabot.yml`
- dependency-review and OWASP cache follow-up documentation
- configuration and employee-payroll runbooks
- negative-path quality evidence
- PowerShell regression scripts

### Exact Thread 2 Sprint 3 file delta at exit

The comparison `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9...db644298ab3197a6931cd9c6b8d9875ef30d28c5` contains 51 files.

#### Calculation engine

- `backend/calculation-engine/pom.xml`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollCalculationController.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollCalculationPermissions.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollCalculationRequestView.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollCalculationResult.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollCalculationTraceView.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollComponentResultView.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollResultDetailView.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/PayrollResultSummaryView.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/internal/application/PayrollCalculationService.java`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/internal/infrastructure/PayrollCalculationRepository.java`

#### Payroll operations

- `backend/payroll-operations/pom.xml`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollCycleController.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollCycleCreateRequest.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollCycleView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollInputSealResult.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollInputSnapshotController.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollInputSnapshotDetailView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollInputSnapshotView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollOperationsHttpSupport.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PayrollOperationsPermissions.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PopulationDecisionView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PopulationMemberView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PopulationResolutionResult.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/PopulationResolutionView.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/application/PayrollCycleService.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/application/PayrollInputSnapshotService.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/application/PayrollOperationsCommandExecutor.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/application/PayrollOperationsEventRecorder.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/infrastructure/PayrollInputSnapshotRepository.java`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/infrastructure/PayrollOperationsRepository.java`

#### Tests

- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/PayrollPopulationResolutionMigrationIT.java`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java`
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/PayrollOperationsApiIT.java`

#### Contracts and identity

- `contracts/openapi/payroll-calculation-openapi-v1.yaml`
- `contracts/openapi/payroll-operations-openapi-v1.yaml`
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
- `deploy/local/keycloak/payroll-realm.json`

#### Database

- `database/flyway/README.md`
- `database/flyway/sql/V023__payroll_cycle_population_resolution.sql`
- `database/flyway/sql/V024__payroll_input_snapshot_sealing.sql`
- `database/flyway/sql/V025__deterministic_starter_calculation.sql`
- `database/flyway/sql/V026__controlled_recalculation_supersession.sql`
- `database/flyway/verification/verify_vertical_slice.sql`

#### Quality evidence and runbooks

- `docs/quality/s3-01a-payroll-population-schema-audit.md`
- `docs/quality/s3-02a-input-snapshot-schema-audit.md`
- `docs/quality/s3-03a-deterministic-starter-calculation-schema-audit.md`
- `docs/quality/s3-04a-controlled-recalculation-schema-audit.md`
- `docs/runbooks/payroll-calculation-api.md`
- `docs/runbooks/payroll-cycle-population-api.md`
- `docs/runbooks/payroll-input-snapshot-api.md`

---

## APIs, OpenAPI and permissions

### Sprint 2 API families

Thread 2 completed or closed:

- payroll-calendar create/list and deterministic period generation;
- pay-group identity/version lifecycle;
- pay-component identity/version lifecycle;
- salary-structure identity/version and component-line lifecycle;
- payroll relationship identity/version lifecycle;
- payroll assignment identity/version lifecycle;
- employee payroll profile creation and status transitions;
- pay-group assignment lifecycle;
- salary assignment lifecycle;
- audit-history reads.

Authoritative contracts:

- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
- `contracts/openapi/employee-payroll-openapi-v1.yaml`

### Employee-payroll permissions introduced or closed

- `employee-payroll.relationship.read`
- `employee-payroll.relationship.create`
- `employee-payroll.relationship.version.create`
- `employee-payroll.relationship.version.correct`
- `employee-payroll.relationship.version.end-date`
- `employee-payroll.relationship.approve`
- `employee-payroll.assignment.read`
- `employee-payroll.assignment.create`
- `employee-payroll.assignment.version.create`
- `employee-payroll.assignment.version.correct`
- `employee-payroll.assignment.version.end-date`
- `employee-payroll.assignment.approve`
- `employee-payroll.profile.read`
- `employee-payroll.profile.create`
- `employee-payroll.profile.status.update`
- `employee-payroll.pay-group-assignment.read`
- `employee-payroll.pay-group-assignment.create`
- `employee-payroll.pay-group-assignment.correct`
- `employee-payroll.pay-group-assignment.end-date`
- `employee-payroll.pay-group-assignment.approve`
- `employee-payroll.salary-assignment.read`
- `employee-payroll.salary-assignment.create`
- `employee-payroll.salary-assignment.correct`
- `employee-payroll.salary-assignment.end-date`
- `employee-payroll.salary-assignment.approve`

### Thread 2 Sprint 3 API surface at exit

All paths use the aggregate `/api/v1` server prefix.

#### Payroll operations

- `GET /payroll-cycles`
- `POST /payroll-cycles`
- `GET /payroll-cycles/{cycleId}`
- `POST /payroll-cycles/{cycleId}/population-resolution`
- `GET /payroll-cycles/{cycleId}/population`
- `GET /payroll-cycles/{cycleId}/population-resolutions`
- `GET /payroll-cycles/{cycleId}/population-decisions`
- `POST /payroll-cycles/{cycleId}/seal-inputs`
- `GET /payroll-cycles/{cycleId}/input-snapshots`
- `GET /payroll-cycles/{cycleId}/input-snapshots/{snapshotId}`
- cycle audit read path.

#### Calculation

- `POST /payroll-cycles/{cycleId}/calculation`
- `GET /payroll-cycles/{cycleId}/calculation-requests`
- `GET /payroll-cycles/{cycleId}/results`
- `GET /payroll-cycles/{cycleId}/results/{resultId}`
- `GET /payroll-cycles/{cycleId}/results/{resultId}/trace`

The recalculation POST endpoint was not completed by Thread 2. It was delivered later by Thread 3.

### Thread 2 Sprint 3 permissions at exit

- `payroll-cycle.read`
- `payroll-cycle.create`
- `payroll-cycle.population.resolve`
- `payroll-cycle.inputs.read`
- `payroll-cycle.inputs.seal`
- `payroll-calculation.execute`
- `payroll-result.read`
- `payroll-result.trace.read`

Current repository also contains `payroll-calculation.recalculate`; that permission belongs to the later Thread 3 completion.

---

## Verification evidence

| Gate | Evidence | Result |
|---|---|---|
| Sprint 2 final PR head | `e98f70b...` | Published |
| Sprint 2 CI | `payroll-baseline` run 35 | SUCCESS |
| Sprint 2 migration range | V017–V022 | MERGED |
| Sprint 2 backend verification | PR #3 closure evidence | GREEN |
| Sprint 2 fresh/upgrade/RLS verification | PR #3 closure evidence | GREEN |
| Sprint 2 frontend lint/tests/build | PR #3 closure evidence | GREEN |
| Sprint 2 OpenAPI | Zero errors and zero warnings | GREEN |
| Sprint 2 secret/dependency/SBOM/auth checks | PR #3 closure evidence | GREEN |
| Thread 2 Sprint 3 exit head | `db644298...` | Published |
| Thread 2 Sprint 3 CI | `payroll-baseline` run 55 | SUCCESS |
| Thread 2 Sprint 3 Maven gate | Full verify reported green | VERIFIED - THREAD RECORD |
| V026 persistent migration and Flyway validation | Reported green before commit | VERIFIED - THREAD RECORD |
| V026 SQL vertical-slice verification | Reported green | VERIFIED - THREAD RECORD |
| Final PR #18 CI after Thread 3 | run 63 | SUCCESS |
| PR #20 governance CI | run 83 on `20935aa4...` | SUCCESS |
| CI on current merge SHA `4b5da975...` | No PR-triggered run returned | NOT VERIFIED |
| Current local verification | Not executed in this read-only pass | NOT APPLICABLE |

### Verification rules established or reinforced

- `mvn test` is insufficient when Failsafe integration tests matter.
- `mvn verify` must visibly execute Failsafe integration-test and verify phases.
- A focused green test cannot substitute for the full reactor.
- Migration work requires fresh install, legacy upgrade, UUID/data preservation and Flyway validation.
- Tenant-owned schema requires RLS, FORCE RLS, tenant-safe FKs and runtime-role verification.
- API work requires permission, cross-tenant, idempotency, optimistic-concurrency and problem-response coverage.
- OpenAPI closure requires zero errors and zero warnings.
- Frontend lint, tests and production build are independent gates.
- High-risk payroll/effective-date/RLS/migration changes require independent critical review under current policy.
- Stage, commit, push, PR update and merge remain separate decisions.

---

## Required negative-path coverage introduced or enforced

- cross-tenant reads, writes and references;
- tenant-unsafe foreign-key attempts;
- overlapping approved versions;
- child effective range outside exact parent range;
- parent end-date while active dependants exist;
- direct runtime DML against immutable history;
- stale `If-Match`;
- idempotency key reused with a different request hash;
- invalid profile READY transition;
- missing pay-group or salary assignment;
- invalid cycle state;
- unsealed or drifting input data;
- stale cycle version;
- blank or oversized recalculation reason;
- conflicting recalculation replay;
- invalid predecessor/successor lineage;
- duplicate recalculation successor;
- mutation of sealed snapshots, results, components or trace;
- missing permission;
- OpenAPI path ambiguity or unused contract components.

---

## Failure-learning delta

| ID | Failure | Root cause | Permanent prevention | Carry forward and repository destination |
|---|---|---|---|---|
| T2-F01 | V017 upgrade failed under new NOT NULL constraints | Strict constraint applied before complete legacy backfill/order validation | Backfill and validate legacy rows before adding strict constraints; test upgrade path | Carry forward; migration review policy |
| T2-F02 | RLS/catalogue test referenced an old column | Rename not propagated across catalogue tests and verifier | Search and update code, tests and verification SQL together | Carry forward; runbook/checklist |
| T2-F03 | V019 approval metadata constraint failed | Metadata backfill occurred after shape constraints activated | Backfill lifecycle metadata before enabling approval constraints | Carry forward; migration checklist |
| T2-F04 | V021 fixture referenced nonexistent `professional_tax_state` | Test assumed out-of-scope schema | Correct the fixture; never expand production schema to satisfy an invalid test | Carry forward; test-fixture guidance |
| T2-F05 | Maven looked green while integration tests were skipped | Surefire ran but Failsafe did not | Use `mvn verify` and inspect Failsafe phases | Reached AGENTS, master design and MDR-011 |
| T2-F06 | Frontend tests/build passed but lint failed | Gates were treated as interchangeable | Keep lint independent from tests and build | Reached MDR-012 and verification protocol |
| T2-F07 | NVD update/cache failures destabilised builds | Feed bootstrap and external availability were coupled to normal commits | Deterministic dependency review for PRs; cached/scheduled feed work separately | Reached MDR-019; debt remains |
| T2-F08 | CI used an invalid expression/path context | Workflow context was not valid at that placement | Validate workflow syntax and use context-valid paths | Carry forward; exact authority destination NOT VERIFIED |
| T2-F09 | Maven wrapper executable bit was lost | Git mode changed | Verify wrapper mode before publication | Carry forward; exact authority destination NOT VERIFIED |
| T2-F10 | Isolated dependency scan could not resolve reactor snapshots | Reactor artifacts were not installed | Build/install reactor before standalone module scan | Carry forward; exact authority destination NOT VERIFIED |
| T2-F11 | Sprint 3 PR was initially confused with PR #4 | PR number inferred from sequence; #4 was Dependabot | Resolve PR by head branch and metadata | Carry forward; thread protocol |
| T2-F12 | Patch failed because existing file context was stale | Patch generated against non-current text | Fetch exact committed blob before generating modifications | Carry forward; not explicit in current decision register |
| T2-F13 | Synthetic/reduced file replacement damaged OpenAPI/test context | Package was generated from an incomplete artificial base | Generate complete final files from exact current blobs and verify blob SHA | Carry forward; candidate decision-register addition |
| T2-F14 | Downloaded PowerShell repair script was blocked | Local execution policy rejected downloaded unsigned script | Prefer data files plus pasted guarded command; avoid unnecessary downloaded executables | Carry forward; exact authority destination NOT VERIFIED |
| T2-F15 | Strict-mode `.Count` failed | Pipeline collapsed to scalar or null | Capture collections with `@(...)` before cardinality tests | Carry forward; PowerShell runbook |
| T2-F16 | Guard checked the wrong Java test name | Validation string diverged from generated source | Validate the final prepared file instead of duplicate magic strings | Carry forward; packaging checklist |
| T2-F17 | Guard required verifier content that did not exist by design | Validation tested an unrelated marker | Validate intended invariants only | Carry forward; packaging checklist |
| T2-F18 | Partial paste produced standalone `else` | Long control-flow repair was not delivered atomically | Use one complete scriptblock or full-file replacement | Carry forward; PowerShell runbook |
| T2-F19 | Assistant guessed PowerShell working directory | Diagnosis made without evidence | Never assert cwd without checking; use verified or absolute paths | Carry forward; evidence-discipline rule |
| T2-F20 | V026 recalculation violated inherited result uniqueness | New multi-attempt history model did not audit old uniqueness constraints | Audit inherited unique constraints/indexes before versioning or supersession | Carry forward; candidate decision-register addition |
| T2-F21 | Repeated patch/guard failures consumed time | Packaging was synthetically validated rather than tested against exact blobs and local behavior | Exact-base full-file preparation, static validation before delivery, local verification authoritative | Carry forward; handoff/checklist |
| GOV-F01 | Governance merge introduced visible mojibake | Text encoding changed or was mis-decoded during documentation generation/publication | Preserve UTF-8, validate rendered diff and scan for mojibake before publication | New current repository debt; Thread 1 governance consolidation |

### Prevention-rule adoption status

| Rule group | Current authority status |
|---|---|
| Repository evidence over chat memory | In AGENTS, master design, handoff, protocol and MDR-001 |
| Maven verify/Failsafe | In AGENTS, master design and MDR-011 |
| Independent frontend gates | In AGENTS/master design and MDR-012 |
| Migration immutability and V031 baseline | In AGENTS/master design and MDR-015 |
| One implementation owner | In AGENTS/protocol and MDR-014 |
| Exact-blob/full-file package generation | Present in historical handoff evidence; not explicitly indexed in current decision register |
| Inherited uniqueness audit | Present in historical reconciliation; not explicitly indexed in current decision register |
| Atomic PowerShell delivery | Not clearly present in current authority files |
| Mojibake/UTF-8 validation | Not yet present; newly observed current debt |

---

## Documentation updates

### Performed in this pass

- Master design: **No change**
- Decision register: **No change**
- Running handoff: **No change**
- Thread registry: **No change**
- ADR/backlog/runbook: **No change**
- Repository historical record: **No change**
- GitHub PR metadata: **No change**

### Existing repository state

PR #20 added or updated:

- `AGENTS.md`
- `README.md`
- `docs/design/decision-register.md`
- `docs/design/hrms-payroll-master-design.md`
- `docs/governance/thread-maintenance-protocol.md`
- `docs/governance/thread-registry.md`
- `docs/governance/thread-start-prompt.md`
- `docs/history/thread-1-decision-extract.md`
- `docs/history/thread-2-reconciliation.md`
- `docs/runbooks/project-continuation-handoff.md`
- `docs/templates/thread-checkpoint-template.md`

### Corrections required during future Thread 1 consolidation

1. Update exact repository baseline from `def3dd2e...` to current `4b5da975...` where a file claims to show current HEAD.
2. Preserve the distinction between:
   - implementation baseline `def3dd2e...`;
   - current repository documentation HEAD `4b5da975...`.
3. Replace Thread 2’s `NOT VERIFIED` registry row with the proposed closed row below.
4. Record this regenerated reconciliation as the current reconciliation artifact or supersede the stale committed version explicitly.
5. Correct mojibake in AGENTS, README, handoff and any other PR #20 file.
6. Evaluate T2-D18 and T2-D19 for decision-register inclusion.
7. Keep Thread 2 closed with no file ownership or migration reservation.
8. Do not rewrite historical migration or implementation evidence.

---

## Blockers and conflicts

### Verified blockers

No implementation blocker belongs to Thread 2 because the thread’s work is merged and closed.

### Documentation conflicts

1. Current `main` is `4b5da975...`, while the master design, thread registry and handoff baseline fields still name `def3dd2e...`.
2. The thread registry marks Thread 2 `NOT VERIFIED`, while repository and historical evidence now support a complete closed record.
3. The committed historical Thread 2 reconciliation says the template and governance files were missing; PR #20 added them.
4. Historical Sprint 3 slice name `S3-04A` conflicts with the current backlog’s S3-04 proration label.
5. The running handoff retains an old pre-merge continuation card and later superseding sections; readers must not treat the old card as current.
6. Current authority text contains mojibake introduced or exposed by PR #20.
7. The repository historical record is intentionally historical but is currently the only committed Thread 2 record; it should be explicitly superseded by this regenerated version during consolidation.

### Potential conflict with another thread

- Thread 1 currently owns governance bootstrap/consolidation according to the registry.
- This Thread 2 pass is read-only and does not claim that ownership.
- Thread 2 must not update authority files independently.
- Thread 3 historically superseded remaining Sprint 3 implementation.
- Current Thread 3 reconciliation status remains `NOT VERIFIED` in the registry until separately performed.

### Current migration conflict

None for Thread 2.

- V001–V030 are immutable.
- Thread 2 reserves no migration.
- Future V031 ownership must be registered before writes.

---

## NOT VERIFIED items

1. Current local branch.
2. Current local HEAD.
3. Current working-tree cleanliness.
4. Current Git index state.
5. Any local uncommitted or untracked files.
6. Whether local `main` has been fast-forwarded to `4b5da975...`.
7. Whether any local Thread 2 checkpoint ZIPs remain available.
8. Exact historical literal commands used to create the Sprint 2 branch.
9. Exact historical literal commands used to create the Sprint 3 branch.
10. Exact local file hashes for Thread 2 checkpoint packages.
11. Direct CI status on merge commit `4b5da975...`.
12. Completion of the durable centralized OWASP Dependency-Check data service.
13. Any local-only migration reservation outside the committed registry.
14. Whether all package-generation prevention rules have been added to an operational runbook.
15. Whether the current mojibake exists identically in the user’s local checkout or only in connected decoded output; current remote fetched content displays it, so the remote documentation issue is verified, but local state is not.
16. Any current work in Threads 3–5 beyond what their merged PRs prove.

---

## Work superseded by later merged implementation

| Thread 2 exit item | Superseding work |
|---|---|
| Recalculation application/API not yet implemented | Thread 3 commit `0331d3ce14fa33a347db356c50e1e947767d7e3f` |
| Recalculation permission missing | Thread 3 added `payroll-calculation.recalculate` |
| Execution workspace absent | Thread 3 commit `7fee492bd8899269fe588c9d3ab8202029a8b0b5` |
| Persisted draft-payslip UI absent | Thread 3 execution/draft-payslip delivery |
| Sprint-wide negative-path closure incomplete | Thread 3 commit `d54085b87b6fd7a92b0d3b20a35618ff2f169663` |
| Closure report/manual smoke incomplete | Thread 3 commit `558b2de2f12e846c3f8c2cc4cd684cf30af3a349` |
| Browser authentication/E2E absent | Later Thread 3 commits `0a9fe57c...`, `af72a413...`, `dda03c14...`, `ebd2603d...` |
| Sprint 3 PR draft/unmerged | PR #18 later merged as `73c356662...` |
| Repository stopped at V026 | Sprint 4 later added V027–V030 |
| No living governance authority files | PR #20 later established them |
| First reconciliation said template/registry/master design absent | PR #20 added those files, making that statement historical only |

---

## Unresolved debt in the current repository

1. Cached/scheduled OWASP Dependency-Check data service remains temporary/deferred under MDR-019.
2. Production event-broker operations, replay and alerting remain controlled debt in the master design.
3. Next feature increment after Sprint 4 is not selected.
4. Country-specific legal statutory rule packs remain excluded.
5. Retro, off-cycle, recoveries, final settlement, banking, accounting and legal payslip remain excluded.
6. Current authority files need exact baseline alignment to `4b5da975...`.
7. Thread 2 registry row remains `NOT VERIFIED`.
8. Threads 3–5 still require reconciliation.
9. Historical and current Sprint 3 story labels need capability/migration reconciliation.
10. Exact-blob packaging and inherited-constraint audit rules are not clearly indexed in the decision register.
11. Authority files contain mojibake and require UTF-8 cleanup.
12. Direct CI on governance merge commit `4b5da975...` is not verified; only PR-head run 83 is verified.
13. Current local working-tree and index state are not represented in this remote-only pass.

---

## Assumptions future threads must not make

- Do not assume the current local checkout matches remote `main`.
- Do not assume `def3dd2e...` is still the exact current repository HEAD.
- Do not assume a historical continuation card remains current.
- Do not assume a thread registry row is accurate without live reconciliation.
- Do not assume Thread 2 has any active ownership.
- Do not assume a historical feature branch is safe to reuse.
- Do not infer PR number from creation sequence.
- Do not infer branch base from branch name.
- Do not rewrite committed migrations.
- Do not assume stable identity is enough for payroll lineage.
- Do not assume `mvn test` executed Failsafe integration tests.
- Do not treat Testcontainers success as proof of persistent local database state.
- Do not accept OpenAPI warnings as closure.
- Do not treat lint, tests and build as interchangeable.
- Do not assume a downloaded PowerShell script will execute.
- Do not generate patches from synthetic or reduced file bases.
- Do not introduce supersession/history without auditing inherited unique constraints and indexes.
- Do not assume story labels are consistent across historical checkpoints and current backlog.
- Do not stage, commit, push, update a PR or merge because verification is green.
- Do not allow two threads to reserve V031 or overlapping files.
- Do not silently repair mojibake while performing unrelated implementation.
- Do not use the committed first Thread 2 reconciliation as current without reading this regenerated record and current authorities.

---

## Recommended final thread role

### `CLOSED`

Rationale:

- All directly owned Thread 2 implementation is merged.
- Sprint 2 is complete.
- Thread 2’s Sprint 3 work is merged.
- Later Thread 3 work superseded all incomplete Sprint 3 items.
- The repository has advanced through Sprint 4 and a governance bootstrap.
- Thread 2 has no legitimate active branch, PR, file allow-list or migration reservation.
- Remaining work is governance consolidation owned by Thread 1, not implementation by Thread 2.

Thread 2 should remain available only as historical evidence and for clarification of this record.

---

## Proposed thread-registry row

| Thread | Role/status | Recovered scope | Branch/PR | Write ownership | Latest durable record | Next action |
|---|---|---|---|---|---|---|
| Thread 2 | CLOSED — historical implementation and handoff | Completed Sprint 2 employee-payroll/configuration closure; created Sprint 3 execution branch and delivered cycle/population, immutable snapshots, deterministic calculation and controlled recalculation database foundation through V026; remaining Sprint 3 completed by Thread 3 | Historical: `feature/sprint-2-payroll-configuration` / PR #3 merged; `feature/sprint-3-payroll-execution` / PR #18 merged; Thread 2 exit `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | None | `docs/history/thread-2-reconciliation.md` to be superseded or updated from regenerated `thread-2-reconciliation.md` | Thread 1 consolidates this record, corrects baseline/encoding conflicts and closes the registry row; no Thread 2 implementation |

### Expanded registration fields

| Field | Proposed value |
|---|---|
| Thread | `Thread 2 — Payroll System Design - Thread 2` |
| Role | `CLOSED` |
| Branch/PR | Historical PR #3 and PR #18, both merged |
| Approved scope | Historical evidence only |
| File allow-list | `NONE` |
| Migration reservation | `NONE` |
| Verification | No new verification; historical evidence recorded |
| Latest checkpoint | Regenerated external `thread-2-reconciliation.md`; future committed path decided by Thread 1 |
| Blockers | Current authority-baseline and encoding conflicts |
| Next authorised action | Thread 1 governance consolidation |
| Prohibited actions | No implementation, migration reservation, branch creation, stage, commit, push, PR update or merge from Thread 2 |

---

## Handoff

### One recommended next authorised action

Authorise **Thread 1** to perform one governance-only consolidation pass using all historical reconciliation records, including this regenerated Thread 2 record, with an exact documentation allow-list.

That pass should:

1. update Thread 2’s registry row to `CLOSED`;
2. preserve the implementation baseline `def3dd2e...` while recording current repository HEAD `4b5da975...`;
3. supersede or update the stale committed Thread 2 reconciliation;
4. correct UTF-8/mojibake defects;
5. evaluate the unregistered permanent prevention rules;
6. update the running handoff;
7. avoid application, dependency and migration changes;
8. keep stage, commit, push and PR update separately authorised.

### Prohibited actions in this thread

- modify repository files;
- create or switch branch;
- reserve V031;
- stage;
- commit;
- push;
- update PR metadata;
- merge;
- start implementation;
- change dependencies;
- rewrite V001–V030.

### Separate state and authorization status

| Action/state | Status |
|---|---|
| Working tree | NOT VERIFIED |
| Git index | NOT VERIFIED |
| Repository files modified in this pass | NO |
| Branch created/switched in this pass | NO |
| Migration reserved in this pass | NO |
| Stage | NOT PERFORMED / NOT AUTHORISED |
| Commit | NOT PERFORMED / NOT AUTHORISED |
| Push | NOT PERFORMED / NOT AUTHORISED |
| PR update | NOT PERFORMED / NOT AUTHORISED |
| Merge | NOT PERFORMED / NOT AUTHORISED |
| Implementation | NOT STARTED / PROHIBITED FOR THIS PASS |

---

## Exact source and evidence inventory

| ID | Source | Exact evidence used |
|---|---|---|
| E-01 | Uploaded `thread-start-prompt(2).md` | Reconciliation-only rules and 22 required record elements |
| E-02 | `AGENTS.md` blob `7c7eb8407404679cadb384beea51626d08209565` | Scope, migration, security, verification, authority and write-ownership rules |
| E-03 | `docs/design/hrms-payroll-master-design.md` blob `96fa55c6f9e5b1a7071f728fb415752e086ee0c8` | Implemented scope through Sprint 4, architecture, migration ledger and controlled debt |
| E-04 | `docs/design/decision-register.md` blob `db513793c7f1513d18b91edee4aefde152163c10` | MDR-001 through MDR-020 |
| E-05 | `docs/runbooks/project-continuation-handoff.md` blob `1dfabb6d18225fbecc671f10c9b71260ad7df58c` | Historical Sprint 4 card, superseding checkpoint and checkpoint policy |
| E-06 | `docs/governance/thread-registry.md` blob `af6158895a143c9ea97da9c47b5bd1dc0e975368` | Current Thread 2 `NOT VERIFIED` row and ownership-transfer rules |
| E-07 | `docs/governance/thread-maintenance-protocol.md` blob `dcc725e1eaf0acb9751d925d778b2cc193778068` | Mandatory files, roles, registration, atomicity and exit protocol |
| E-08 | `docs/templates/thread-checkpoint-template.md` blob `adb5aaf81dec86818678ae4337029680c5202e60` | Record structure |
| E-09 | `README.md` current `main` | Sprint 1–4 current product description |
| E-10 | `backlog/organisation-to-draft-payslip-sprint-backlog.csv` blob `7f40794bda6ea467831b5c9f935c32385425ad40` | Canonical Sprint 2 and Sprint 3 story mapping |
| E-11 | Current `main` comparison | `4b5da975...` is identical to `main` |
| E-12 | PR #20 | Governance merge; base `def3dd2...`, head `20935aa4...`, merge `4b5da975...` |
| E-13 | PR #20 changed-file list | Eleven living-authority/history files added or updated |
| E-14 | Workflow on PR #20 head | `payroll-baseline` run 83, success |
| E-15 | Current remote branch list | Sprint 2 branch absent; Sprint 3 branch present; governance branch present |
| E-16 | PR #3 | Sprint 2 scope, V017–V022, base/head/merge, final state |
| E-17 | PR #18 | Sprint 3 final scope, base/head/merge, final state |
| E-18 | Compare `84530e1...db644298` | Exact seven-commit, 51-file Thread 2 Sprint 3 boundary |
| E-19 | Workflow on `e98f70b...` | Sprint 2 run 35 success |
| E-20 | Workflow on `db644298...` | Thread 2 exit run 55 success |
| E-21 | Workflow on final PR #18 head | run 63 success |
| E-22 | Commit history | Exact Thread 2 and later Thread 3 commit sequence |
| E-23 | Current permission classes | Employee-payroll, payroll-operations and calculation authorities |
| E-24 | Current OpenAPI fragments | Payroll operations and calculation routes |
| E-25 | `docs/history/thread-2-reconciliation.md` | First committed reconciliation, now historical and stale after PR #20 |
| E-26 | Previous durable Thread 2 handoff facts recorded in the committed reconciliation | Entry/exit state, failures and transition boundary |
| E-27 | Current GitHub fetch of authority text | Verified visible mojibake debt |
| E-28 | Local repository inspection | NOT AVAILABLE; all local state remains NOT VERIFIED |

---

## Reconciliation conclusion

Thread 2 completed Sprint 2 closure and directly delivered the first seven Sprint 3 commits through V026. All of its implementation is merged. Remaining Sprint 3 work was completed later by Thread 3. The repository has since advanced through Sprint 4 and merged a living-governance system through PR #20.

Thread 2 is therefore **CLOSED**, owns no files, reserves no migration and should not perform implementation. Its only remaining project function is historical evidence for Thread 1’s governance consolidation.

The current living documents require a follow-up governance correction because they still carry the prior implementation SHA as the current repository baseline, Thread 2 remains `NOT VERIFIED` in the registry, the committed first reconciliation is stale, and several authority files contain mojibake. No such correction was performed during this read-only pass.
