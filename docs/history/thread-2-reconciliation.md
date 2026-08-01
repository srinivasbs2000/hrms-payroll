# Thread Reconciliation Record

## Record identity

| Field | Value |
|---|---|
| Record | Thread 2 Reconciliation |
| Required output filename | `thread-2-reconciliation.md` |
| Thread number | `2` |
| Exact thread name | `Payroll System Design - Thread 2` |
| Reconciliation mode | Read-only; repository and GitHub metadata were not modified |
| Evidence cut-off | 1 August 2026, 10:54 Asia/Kolkata |
| Repository | `srinivasbs2000/hrms-payroll` |
| Recommended final thread role | **CLOSED** |
| Template status | **DOCUMENTATION CONFLICT** — `docs/templates/thread-checkpoint-template.md` is not present on current `main`; this record follows the fields required by the reconciliation request and the minimum checkpoint fields stated in the running handoff |
| Local working-tree status | **NOT VERIFIED** — no local repository connector or local shell access to `C:\dev\hrms-payroll` was available in this reconciliation pass |

## 0. Evidence labels

| Label | Meaning |
|---|---|
| VERIFIED — REMOTE | Confirmed from the connected GitHub repository, PR, branch, commit, workflow or committed file. |
| VERIFIED — THREAD ARTIFACT | Confirmed by a previously produced Thread 2 checkpoint or handoff artifact. |
| DERIVED | A conclusion directly implied by identified verified evidence. |
| DOCUMENTATION CONFLICT | Two authority sources disagree, or a mandated authority file is absent. |
| NOT VERIFIED | Required evidence is unavailable and no assumption is made. |

## 1. Authority-file validation

The uploaded project-thread start prompt requires every thread to read the repository authority files before work, validate them against the local working tree and live GitHub, mark unknown facts `NOT VERIFIED`, and register role, scope, branch/PR, file allow-list and migration reservation before writes.

| Required authority file | Current `main` result | Evidence/status | Reconciliation consequence |
|---|---|---|---|
| `AGENTS.md` | Present; blob `5ca89442462d29c76c30bcc95baf18f1f3008553` | VERIFIED — REMOTE | Governs evidence discipline, scope, architecture, migration immutability, verification and Git-write authorization. |
| `docs/design/hrms-payroll-master-design.md` | 404 / not present | DOCUMENTATION CONFLICT | No master-design comparison can be completed. No design conflict may be silently resolved. |
| `docs/design/decision-register.md` | 404 / not present | DOCUMENTATION CONFLICT | No repository-resident decision-register comparison can be completed. |
| `docs/runbooks/project-continuation-handoff.md` | Present; blob `50ee5507fd50f55a8c084629873c3d39382a2c02` | VERIFIED — REMOTE, but stale | It records an earlier Sprint 4 pre-merge state and conflicts with current GitHub. |
| `docs/governance/thread-registry.md` | 404 / not present | DOCUMENTATION CONFLICT | Thread 2 is not registered in the required repository authority. |
| `docs/governance/thread-maintenance-protocol.md` | 404 / not present | DOCUMENTATION CONFLICT | The maintenance procedure mandated by the start prompt is unavailable. |
| `docs/templates/thread-checkpoint-template.md` | 404 / not present | DOCUMENTATION CONFLICT | Exact template conformance cannot be verified. |
| `README.md` | Present; blob `f74c00e4f65186079c3ad186c3f53bd117dcc3f1` | VERIFIED — REMOTE | Current product scope is Sprint 1–4 and V001–V030. |
| `backlog/organisation-to-draft-payslip-sprint-backlog.csv` | Present; blob `7f40794bda6ea467831b5f935c32385425ad40` | VERIFIED — REMOTE | Provides current canonical Sprint 2 and Sprint 3 story labels, with noted mapping conflicts. |

### Authority conflict: running handoff versus current repository

The running handoff states:

- main baseline `73c356662b1888194a72c7006a66bd91443550ca`;
- active branch `feature/sprint-4-statutory-deductions`;
- PR #19 open and unmerged;
- remote head `6cf39fc1734a50a514cfee22db2fd78bd41b80cc`;
- CI run 77;
- V001–V030 committed.

Current GitHub shows:

- `main` is exactly `def3dd2e212f85c440eee5497e292be2f1f2bf64`;
- PR #19 is merged and closed;
- PR #19 head was `b2a220461cf5ba581b5f67e7619ec146bf7982ed`;
- merge commit is `def3dd2e212f85c440eee5497e292be2f1f2bf64`;
- branch-head CI run 81 succeeded;
- V001–V030 remain committed and immutable.

**Classification:** DOCUMENTATION CONFLICT. Current repository evidence prevails.

## 2. Current repository checkpoint

| Item | Current fact | Status |
|---|---|---|
| Default branch | `main` | VERIFIED — REMOTE |
| Current `main` SHA | `def3dd2e212f85c440eee5497e292be2f1f2bf64` | VERIFIED — REMOTE; comparison `def3dd2…` to `main` is identical |
| Current merged baseline | Sprint 4 statutory deductions lifecycle | VERIFIED — REMOTE |
| Current committed migrations | V001–V030 | VERIFIED — REMOTE from `AGENTS.md`, README and Sprint 4 merge evidence |
| Next permitted migration | V031, subject to an authorised reservation | VERIFIED — REMOTE from `AGENTS.md` |
| Direct CI attached to merge commit `def3dd2…` | No PR-triggered workflow or combined status returned | NOT VERIFIED |
| Last verified pre-merge branch-head CI | `payroll-baseline` run 81 on `b2a220461cf5ba581b5f67e7619ec146bf7982ed`, success | VERIFIED — REMOTE |
| Current local branch, HEAD, index and uncommitted diff | Unavailable | NOT VERIFIED |
| Current open feature work owned by Thread 2 | None | DERIVED from merged historical PRs and current main |
| Current migration reservation owned by Thread 2 | None | DERIVED; no registry exists, therefore repository-wide reservation state remains NOT VERIFIED |

## 3. Exact thread name and historical purpose

### Exact thread name

`Payroll System Design - Thread 2`

### Historical purpose

Thread 2 was an implementation-and-recovery continuation thread with two sequential purposes:

1. **Complete Sprint 2 configuration and employee-payroll work** from an inherited branch/PR state where V017–V020 were already on the feature branch and V021 had been started locally but was not yet completed or verified.
2. **Start Sprint 3 payroll execution** after Sprint 2 merged, create the Sprint 3 branch/PR, and implement through the controlled recalculation database foundation S3-04A/V026 before handing remaining Sprint 3 work to Thread 3.

Thread 2 was therefore not merely a design thread. It directly owned implementation, verification, checkpointing and authorised publication for a bounded portion of Sprint 2 and Sprint 3.

## 4. Branch, pull-request and commit ownership

### 4.1 Sprint 2 branch and PR

| Field | Value |
|---|---|
| Branch | `feature/sprint-2-payroll-configuration` |
| Branch current existence | Not returned in the current branch list; treated as deleted after merge |
| PR | #3 — `Sprint 2: payroll configuration foundation` |
| PR base | `main` at `42d4b50a8fae64c12ddfc1fcb5553476d86fb252` |
| Inherited branch head at Thread 2 entry | `24f2ed4893a90627eb6be69aa3747eba4343e195` — `feat(salary-structure): implement salary structure foundation` |
| First Thread 2 implementation commit | `adf3769b945d56828aa984e634e6e1bbb62582d7` — `feat(employee-payroll): add identity and assignment foundation` |
| Last Sprint 2 branch commit | `e98f70b0346a13e463f8e768ab4014be0e30ca0f` — `feat(employee-payroll): add setup workspace` |
| Merge commit | `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9` |
| Current PR state | Closed and merged |
| Final PR size | 16 commits; 111 changed files |
| Final branch-head CI | `payroll-baseline` run 35, success |

#### Thread 2-owned Sprint 2 commits

| Commit | Purpose | Current status |
|---|---|---|
| `adf3769b945d56828aa984e634e6e1bbb62582d7` | V021 employee-payroll identity and assignment foundation | Merged |
| `1575cbc373bf4dc22ff116b1ea4bbfb7e5a19288` | V022 foundation negative-path hardening | Merged |
| `63c9b1a719765fce3868eb7fc69fac37bc196dc9` | Employee-payroll application services and contracts | Merged |
| `12536c3f629cf567022f3fd50998397d1d0b5911` | Employee-payroll lifecycle APIs | Merged |
| `e98f70b0346a13e463f8e768ab4014be0e30ca0f` | Employee-payroll setup workspace | Merged |

The earlier PR #3 commits through `24f2ed…` were inherited by Thread 2 and reconciled/closed by it, but were not the first implementation commits created by this thread.

### 4.2 Sprint 3 branch and PR

| Field | Value |
|---|---|
| Branch | `feature/sprint-3-payroll-execution` |
| Branch current existence | Present in current remote branch list |
| PR | #18 — `Sprint 3: payroll execution foundation` |
| PR base | `main` at Sprint 2 merge `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9` |
| First Thread 2 Sprint 3 commit | `5bc08e440c21bbeeddc3c1bb4e28ad04943ac9cd` |
| Thread 2 exit/head | `db644298ab3197a6931cd9c6b8d9875ef30d28c5` |
| Thread 2 branch delta | 7 commits ahead of base; 0 behind; 51 files |
| CI at Thread 2 exit | `payroll-baseline` run 55, success |
| PR final head after later Thread 3 work | `ebd2603d91551c6f9e60dc57e2d3500948015703` |
| PR merge commit | `73c356662b1888194a72c7006a66bd91443550ca` |
| Current PR state | Closed and merged |
| Final PR size | 15 commits; 107 changed files |
| Final head CI | `payroll-baseline` run 63, success |
| Historical ownership conclusion | Thread 2 owned the first 7 Sprint 3 commits through S3-04A; Thread 3 completed the later commits and closure on the same PR |

#### Thread 2-owned Sprint 3 commits

| Commit | Increment | Current status |
|---|---|---|
| `5bc08e440c21bbeeddc3c1bb4e28ad04943ac9cd` | S3-01A population-resolution database foundation / V023 | Merged through PR #18 |
| `64b4ca7b2a7a53c373b56d5f6767192a000dd60f` | S3-01B cycle and population APIs | Merged through PR #18 |
| `625e38dc1fed649eb37ec6c1d1171f142430403a` | S3-02A immutable input sealing / V024 | Merged through PR #18 |
| `134fe3e63e6b04f2da08df957f4d415a1fd97606` | S3-02B input-snapshot APIs | Merged through PR #18 |
| `c9ada6bad94071d70a6d10fbcfec085d476a6279` | S3-03A deterministic starter calculation / V025 | Merged through PR #18 |
| `f7eb7fa1fc152b8da4088b881f03bff18558d140` | S3-03B deterministic calculation APIs | Merged through PR #18 |
| `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | S3-04A controlled recalculation and supersession / V026 | Merged through PR #18 |

### 4.3 Work status classification

| Work item | Classification |
|---|---|
| Sprint 2 PR #3 and all Thread 2 Sprint 2 commits | **MERGED** |
| Sprint 3 commits through `db644298…` | **MERGED**, later completed by Thread 3 on the same PR |
| `feature/sprint-2-payroll-configuration` branch | **SUPERSEDED / branch absent after merge** |
| `feature/sprint-3-payroll-execution` branch | **SUPERSEDED / historical branch still present** |
| Thread 2 handoff packages outside repository | **LOCAL/ARTIFACT ONLY unless separately committed; repository presence NOT VERIFIED** |
| Any active Thread 2 implementation ownership | **NONE** |

## 5. Migrations and story mapping

### 5.1 Sprint 2 migration ledger

| Migration | Responsibility | Backlog mapping | Thread 2 relationship | Current status |
|---|---|---|---|---|
| V017 `pay_group_identity_versions` | Stable pay-group identities and effective-dated versions | S2-02 Pay group | Inherited; verified and closed by Thread 2 | Merged; immutable |
| V018 `payroll_calendar_period_foundation` | Monthly calendar and deterministic periods | S2-01 Calendar | Inherited; verified and closed by Thread 2 | Merged; immutable |
| V019 `pay_component_identity_versions` | Pay-component identity/version lifecycle | S2-03 Compensation | Inherited; verified and closed by Thread 2 | Merged; immutable |
| V020 `salary_structure_identity_versions` | Salary-structure identity/version separation and line lineage | S2-04 Compensation | Inherited entry head | Merged; immutable |
| V021 `employee_payroll_identity_assignments` | Payroll relationship, assignment, profile and exact assignment lineage | S2-05 and S2-06 | Direct Thread 2 implementation | Merged; immutable |
| V022 `foundation_negative_path_hardening` | Parent-range, dependent end-date, tenant and lifecycle hardening | Cross-cutting closure; no standalone backlog story | Direct Thread 2 implementation | Merged; immutable |

### 5.2 Sprint 3 migration ledger at Thread 2 exit

| Migration | Responsibility | Backlog mapping | Mapping quality | Current status |
|---|---|---|---|---|
| V023 `payroll_cycle_population_resolution` | Regular cycle lifecycle and deterministic population evidence | S3-01 | Exact | Merged; immutable |
| V024 `payroll_input_snapshot_sealing` | Immutable canonical input snapshots and lineage | S3-02 | Exact | Merged; immutable |
| V025 `deterministic_starter_calculation` | BASIC/HRA/SPECIAL plan, calendar-day proration, immutable result/component/trace evidence | Spans S3-03, S3-04 and S3-05 | DERIVED; current backlog splits these concerns | Merged; immutable |
| V026 `controlled_recalculation_supersession` | Controlled recalculation attempts, supersession and immutable history | Not represented as a distinct current Sprint 3 backlog row | DOCUMENTATION CONFLICT between delivered PR scope and current backlog naming | Merged; immutable |

### 5.3 Story-number conflict

The current backlog identifies:

- S3-03 — compile BASIC-HRA-SPECIAL plan;
- S3-04 — calendar-day proration;
- S3-05 — persist immutable results and trace;
- S3-06 — draft payslip;
- S3-07 — end-to-end scenario.

Thread 2 checkpoints used implementation-slice labels such as S3-03A/B and S3-04A, where S3-04A meant controlled recalculation rather than the backlog’s calendar-day proration.

**Permanent reconciliation rule:** identify work by migration, capability and commit SHA. Story labels alone are insufficient where historical naming diverged.

## 6. Scope reconciliation

### 6.1 Completed directly by Thread 2

#### Sprint 2

- V021 forward-only upgrade from V006-era employee-payroll tables.
- Stable payroll-relationship and payroll-assignment identities.
- Employee payroll profile lifecycle.
- Exact pay-group-version and salary-structure-version assignment lineage.
- V022 negative-path hardening.
- Employee-payroll application services and repositories.
- Employee-payroll REST contracts and controllers.
- OpenAPI integration.
- Keycloak permissions.
- Employee-payroll setup UI.
- Migration, RLS, permission, API, frontend and OpenAPI verification.
- PR #3 review preparation and authorised merge transition.

#### Sprint 3 through S3-04A

- Regular payroll-cycle creation and lifecycle foundation.
- Deterministic population resolution with immutable decisions.
- Population APIs and history reads.
- Immutable input-snapshot sealing.
- Input-snapshot API and canonical payload reads.
- Deterministic fixed-component calculation using BASIC, HRA and SPECIAL_ALLOWANCE.
- Calendar-day proration.
- Immutable calculation request, payroll result, component result and trace persistence.
- Calculation execution and historical result/trace APIs.
- Controlled recalculation and request supersession database foundation.
- Preservation of prior requests, results, components and traces.
- V026 repair removing the obsolete one-result-per-cycle/assignment uniqueness constraint.
- CI success at Thread 2 exit.
- Durable transition handoff for S3-04B.

### 6.2 Partially completed at Thread 2 exit

| Scope | Thread 2 exit state | Later repository state |
|---|---|---|
| Controlled recalculation capability | Database foundation complete; application/API permission, DTO, audit/outbox and integration tests deferred | Completed later by Thread 3 in commit `0331d3ce…` and merged |
| Sprint 3 execution UI | Not started by Thread 2 | Completed later by Thread 3 |
| Draft payslip | Database/result evidence existed; UI/publication flow not completed by Thread 2 | Completed later by Thread 3 as a persisted draft-payslip view |
| Sprint 3 negative-path closure and full regression | Earlier targeted coverage existed; sprint-wide closure incomplete | Completed later by Thread 3 |
| Sprint 3 closure documentation/manual smoke/E2E | Not completed by Thread 2 | Completed later by Thread 3 |
| OWASP Dependency-Check durable data service | Deferred design/backlog item | Still documented as follow-up; current completion NOT VERIFIED |

### 6.3 Unstarted at Thread 2 exit

- Recalculation REST endpoint and `payroll-calculation.recalculate` permission.
- Recalculation application audit/outbox deduplication.
- Payroll execution React workspace.
- Real draft-payslip React workspace.
- Sprint 3 browser authentication/E2E suite.
- Sprint 3 full closure report and manual smoke.
- Statutory deductions and tax.
- Retro and off-cycle payroll.
- Final settlement.
- Banking/payment execution.
- Accounting/GL integration.
- Legal/final payslip publication.

The first six items were completed in later Thread 3 work. The remaining excluded domains continue to require separate approved designs; Sprint 4 added jurisdiction-neutral statutory infrastructure but did not add jurisdiction-specific legal calculations or filing/payment execution.

## 7. Repository areas affected

### 7.1 Sprint 2 affected repository areas

| Area | Files/capabilities |
|---|---|
| CI and dependency policy | `.github/dependabot.yml`, `.github/workflows/ci.yml`, dependency-review/NVD follow-up documentation |
| Compensation backend | Pay group, payroll calendar, pay component and salary-structure controllers, services, repositories, contracts and tests |
| Employee-payroll backend | `backend/employee-payroll/**` module; lifecycle controllers, services, repository, event recorder and permission constants |
| Boot integration tests | `PayGroupApiIT`, `PayrollCalendarApiIT`, `PayComponentApiIT`, `SalaryStructureApiIT`, `EmployeePayrollApiIT` |
| Migration tests | `PayGroupMigrationIT`, `PayrollCalendarMigrationIT`, `PayComponentMigrationIT`, `SalaryStructureMigrationIT`, `EmployeePayrollMigrationIT`, `FoundationNegativePathMigrationIT`, `RowLevelSecurityIT` |
| Database | V017–V022, Flyway README and vertical-slice verifier |
| OpenAPI | aggregate contract plus `employee-payroll-openapi-v1.yaml` |
| Identity | `deploy/local/keycloak/payroll-realm.json` |
| Frontend | pay-group, calendar, component, salary-structure and employee-payroll pages, APIs and tests |
| Runbooks/evidence | compensation and employee-payroll runbooks; S2 negative-path audit |
| Regression scripts | `Invoke-HrmsPayrollRegression.ps1`, PowerShell script checks |

### 7.2 Exact Thread 2 Sprint 3 file set at exit SHA

The comparison from Sprint 2 merge `84530e1…` to Thread 2 exit `db644298…` contains exactly 51 files:

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

#### Quality and runbooks

- `docs/quality/s3-01a-payroll-population-schema-audit.md`
- `docs/quality/s3-02a-input-snapshot-schema-audit.md`
- `docs/quality/s3-03a-deterministic-starter-calculation-schema-audit.md`
- `docs/quality/s3-04a-controlled-recalculation-schema-audit.md`
- `docs/runbooks/payroll-calculation-api.md`
- `docs/runbooks/payroll-cycle-population-api.md`
- `docs/runbooks/payroll-input-snapshot-api.md`

## 8. APIs and permissions affected

### 8.1 Sprint 2 API families

Thread 2 completed or closed the following REST families through PR #3:

- payroll calendars and deterministic period generation;
- pay groups and effective-dated versions;
- pay components and effective-dated versions;
- salary structures, versions and immutable component lines;
- payroll relationships and versions;
- payroll assignments and versions;
- employee payroll profiles and status transitions;
- pay-group assignments;
- salary assignments;
- audit-history reads and controlled lifecycle operations.

The definitive wire contracts are:

- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`;
- `contracts/openapi/employee-payroll-openapi-v1.yaml`.

### 8.2 Employee-payroll permissions introduced/affected

Verified current constants originating from Sprint 2 include:

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

### 8.3 Thread 2 Sprint 3 endpoints

The aggregate contract uses server prefix `/api/v1`.

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
- payroll-cycle audit read path defined in the aggregate contract.

#### Calculation at Thread 2 exit

- `POST /payroll-cycles/{cycleId}/calculation`
- `GET /payroll-cycles/{cycleId}/calculation-requests`
- `GET /payroll-cycles/{cycleId}/results`
- `GET /payroll-cycles/{cycleId}/results/{resultId}`
- `GET /payroll-cycles/{cycleId}/results/{resultId}/trace`

The recalculation POST endpoint was **not** completed by Thread 2; it was the first deferred S3-04B item and was added later.

### 8.4 Thread 2 Sprint 3 permissions

At Thread 2 exit:

- `payroll-cycle.read`
- `payroll-cycle.create`
- `payroll-cycle.population.resolve`
- `payroll-cycle.inputs.read`
- `payroll-cycle.inputs.seal`
- `payroll-calculation.execute`
- `payroll-result.read`
- `payroll-result.trace.read`

Current repository additionally contains:

- `payroll-calculation.recalculate`

That permission belongs to later Thread 3 completion, not Thread 2’s exit implementation.

## 9. Tests and verification affected

### 9.1 Verification rule established

- Maven Surefire `test` is not proof that Failsafe `*IT` tests executed.
- Integration acceptance requires `mvnw verify` or an equivalent full reactor gate.
- The output must show Failsafe integration-test and verify phases.
- A green targeted test cannot replace migration, RLS, API, frontend, OpenAPI and full-reactor closure.

This rule is now represented in `AGENTS.md` through the required `mvnw verify` and staged verification sequence.

### 9.2 Required verification classes and gates

#### Sprint 2

- migration ITs for V017–V022;
- legacy-upgrade and UUID/data preservation tests;
- `RowLevelSecurityIT`;
- negative-path migration tests;
- compensation contract tests;
- employee-payroll contract tests;
- boot API ITs for configuration and employee payroll;
- frontend lint, tests and production build;
- OpenAPI zero errors and zero warnings;
- Keycloak real-token authentication smoke;
- dependency review, SBOM and secret scan;
- clean diff and exact changed-file review.

#### Thread 2 Sprint 3

- `PayrollPopulationResolutionMigrationIT`;
- `RowLevelSecurityIT`;
- `PayrollOperationsApiIT`;
- clean V001→V026 migration;
- V026 legacy upgrade;
- persistent PostgreSQL migration and validation;
- SQL vertical-slice verification;
- deterministic result/hash tests;
- idempotency and optimistic-concurrency tests;
- cross-tenant and permission tests;
- immutable result, component and trace tests;
- controlled recalculation lineage and exact replay tests;
- full Maven verification;
- OpenAPI validation;
- CI `payroll-baseline` run 55.

### 9.3 Negative paths required by Thread 2 decisions

- cross-tenant reads and references;
- tenant-unsafe foreign keys;
- overlapping approved versions;
- invalid child range outside parent range;
- invalid parent end date with active dependants;
- direct runtime DML against immutable history;
- stale `If-Match`;
- reused idempotency key with different request hash;
- invalid profile READY transition;
- missing configuration lineage;
- cycle in invalid state;
- unsealed inputs;
- cross-tenant cycle access;
- stale cycle version;
- blank/oversized recalculation reason;
- conflicting recalculation replay;
- invalid recalculation predecessor or duplicate successor;
- mutation of sealed snapshots, results, components and trace.

## 10. Material decisions introduced or enforced

| Decision | Thread 2 outcome |
|---|---|
| Repository evidence outranks conversation memory | Enforced in handoffs; now formalised in current `AGENTS.md`. |
| Committed Flyway migrations are immutable | V001–V026 were never rewritten after publication; current rule extends through V030. |
| V006→V021 is a forward-only upgrade | V006 remained unchanged; V021 transformed and backfilled the employee-payroll model. |
| Historical UUID preservation | Existing UUIDs were preserved as historical version IDs wherever practical. |
| Exact version lineage | Pay-group and salary assignments reference exact approved versions, not only stable identities. |
| Half-open effective dating | `[effective_from, effective_to)` across configuration and assignments. |
| No approved-range overlap | Enforced by database controls. |
| Forced RLS and tenant-safe composite FKs | Mandatory at database boundary. |
| Runtime role remains non-owner and NOBYPASSRLS | Maintained. |
| Controlled lifecycle functions | Approval, correction and end dating use narrow commands rather than direct DML. |
| Atomic idempotency, audit and outbox | Required for accepted writes; exact replay does not duplicate evidence. |
| Deterministic calculation | Same exact snapshots and engine version produce the same result hash. |
| Immutable results and trace | Corrections/recalculations create new evidence rather than rewriting old evidence. |
| Recalculation supersession | Every new attempt links to the previous active request and preserves history. |
| Draft PR per sprint | PRs remain draft until the sprint’s implementation and closure evidence are complete. |
| Git writes require explicit authorization | Stage, commit, push, PR update and merge are separate authorisations. |
| NVD feed work is not a per-commit product gate | Deterministic dependency review is separated from feed-dependent cache/bootstrap work. |
| Handoffs are durable slice-boundary records | Checkpoints created after pushed, green increments and before thread transition. |

## 11. Failures, lessons and permanent prevention rules

| Failure or failed approach | Root cause | Permanent prevention rule | Repository adoption |
|---|---|---|---|
| V017 upgrade hit NOT NULL/backfill ordering failure | Strict constraint applied before all legacy data was transformed | Backfill and validate first; add strict constraints second; test legacy upgrade | Rule reflected in migration discipline, exact historical AGENTS entry NOT VERIFIED |
| RLS/catalog test referenced an old column | Rename propagation was incomplete | Search and update code, catalogue tests, verification SQL and upgrade tests together | NOT VERIFIED as explicit rule |
| V019 metadata constraint failed | Approval metadata was backfilled after constraints activated | Backfill lifecycle metadata before approval constraints | NOT VERIFIED as explicit rule |
| V021 test used nonexistent `professional_tax_state` | Fixture assumed out-of-scope schema | Repair fixtures; do not expand production schema to satisfy an invalid test | NOT VERIFIED as explicit rule |
| Maven appeared green while ITs were skipped | Surefire ran but Failsafe did not | Use `mvnw verify`; inspect Failsafe phases | Reached current `AGENTS.md` |
| Frontend tests/build passed while lint failed | Gates were treated as interchangeable | Lint is independent and mandatory | Reached verification sequence |
| NVD update/cache failures destabilised builds | Feed availability and cache bootstrap were coupled to normal commits | Use deterministic dependency review for PRs; scheduled/warmed NVD service separately | Implemented in CI/backlog; durable service completion NOT VERIFIED |
| CI expression used invalid context/path scope | Workflow syntax/context not validated at the placement used | Validate workflow expressions and use valid repository/cache paths | NOT VERIFIED as explicit rule |
| Maven wrapper executable bit was lost | Git mode changed to non-executable | Verify wrapper file mode | NOT VERIFIED as explicit rule |
| Standalone dependency scan could not resolve reactor snapshots | Reactor artifacts were not installed | Build/install reactor before isolated module scans | NOT VERIFIED as explicit rule |
| Sprint 3 PR initially confused with PR #4 | PR number inferred from sequence; #4 was Dependabot | Query PR by head branch and metadata | Captured in handoff; registry absent |
| Patch failed because context was stale | Patch generated from non-current file text | Fetch exact current blob before generating changes | Later handoff rule; not explicit in current AGENTS |
| Synthetic/reduced file replacement damaged OpenAPI/test context | Package was generated from an incomplete artificial base | Generate complete final files from exact committed blobs; verify blob SHA before writes | Later handoff rule; repository adoption NOT VERIFIED |
| Downloaded PowerShell script was blocked | Execution policy rejected unsigned downloaded script | Prefer data files and pasted guarded commands; avoid unnecessary downloaded executables | NOT VERIFIED |
| Strict-mode `.Count` failed | Pipeline result collapsed to scalar/null | Wrap collection output with `@(...)` before cardinality checks | NOT VERIFIED |
| Guard used wrong Java test method name | Validation string diverged from generated source | Validate the prepared final file, not duplicated magic strings | NOT VERIFIED |
| Verifier guard required content absent by design | Guard checked an unrelated marker | Validate actual intended invariants only | NOT VERIFIED |
| Partial paste produced standalone `else` | Long control-flow script was not atomic | Use complete single-block or full-file replacement delivery | NOT VERIFIED |
| Assistant guessed the PowerShell working directory | Diagnosis was made without evidence | Do not assert cwd; use verified path evidence or absolute path | NOT VERIFIED |
| V026 recalculation violated a legacy unique constraint | New history model did not audit inherited uniqueness rules | Before versioning/supersession, audit every inherited unique constraint/index | Captured in transition handoff; current AGENTS adoption NOT VERIFIED |
| Repeated patch/guard failures consumed time | Packaging was not validated against exact repository blobs and local behavior | Exact-base full-file replacement, prepare all outputs before writing, static-validate before delivery | Captured in Thread 3 handoff; repository adoption NOT VERIFIED |

## 12. Conflicts with master design, decision register, running handoff and current repository

| Conflict | Classification | Required handling |
|---|---|---|
| Master design path required by start prompt is absent | DOCUMENTATION CONFLICT | Do not claim master-design alignment. |
| Decision register path required by start prompt is absent | DOCUMENTATION CONFLICT | Do not claim decisions were registered. |
| Thread registry path required by start prompt is absent | DOCUMENTATION CONFLICT | Proposed registry row remains uncommitted. |
| Maintenance protocol path required by start prompt is absent | DOCUMENTATION CONFLICT | No repository-resident maintenance procedure can be followed. |
| Checkpoint template path is absent | DOCUMENTATION CONFLICT | This record uses requested sections and handoff minimum fields instead. |
| Running handoff says PR #19 is open/unmerged; GitHub says merged | DOCUMENTATION CONFLICT | Current GitHub prevails. |
| Running handoff says main `73c356…`; main is `def3dd2…` | DOCUMENTATION CONFLICT | Current GitHub prevails. |
| Running handoff CI run 77; last verified Sprint 4 branch-head run is 81 | DOCUMENTATION CONFLICT | Record both as historical/current evidence. |
| Backlog S3-04 means proration; Thread 2 S3-04A means recalculation | DOCUMENTATION CONFLICT | Refer to V026/capability/commit rather than story label. |
| Thread 2 handoff said PR #18 draft/unmerged | Historical snapshot, not current conflict when date-scoped | Mark superseded by later Thread 3 merge. |
| Current AGENTS says V001–V030 immutable and next V031 | No conflict | Governs any future work. |

## 13. Items that must remain NOT VERIFIED

1. Current local branch, HEAD, index, working-tree status and uncommitted diff.
2. Whether any local-only Thread 2 checkpoint files remain under `C:\dev\hrms-payroll`.
3. Whether Thread 2 handoff v3/v4/v5 files were ever committed to the repository.
4. Exact historical literal commands used to create the Sprint 2 branch.
5. Exact historical literal commands used to create the Sprint 3 branch.
6. Whether all Thread 2 prevention rules were added to `AGENTS.md`.
7. Whether a thread registry exists locally but is uncommitted.
8. Whether the missing master design, decision register, maintenance protocol and template exist locally or in an unpushed branch.
9. A direct CI result for current merge commit `def3dd2…`; only the pre-merge branch-head run is verified.
10. Completion status of the durable OWASP Dependency-Check data service.
11. Any current migration reservation by another local-only thread.
12. Any current open local implementation not visible on GitHub.

## 14. Recommended final thread role

### Recommendation: **CLOSED**

Rationale:

- All Thread 2 implementation commits are merged into `main`.
- Sprint 2 is complete.
- Thread 2’s Sprint 3 increments were later completed and merged by Thread 3.
- Current repository work has advanced through merged Sprint 4.
- No active branch, PR, file allow-list or migration reservation should remain assigned to Thread 2.
- The only remaining activity for Thread 2 is historical reconciliation and governance registration.

Thread 2 should not be reopened as an implementation owner. Any new work must be assigned through the missing/updated thread registry after current repository introspection.

## 15. Proposed thread-registry row

The repository thread registry does not exist on current `main`; the following is a proposal only.

| Thread | Name | Role | Status | Historical scope | Branches / PRs | Migrations | File ownership | Migration reservation | Exit checkpoint | Superseded by | Next authorised action |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | Payroll System Design - Thread 2 | IMPLEMENTATION OWNER → RECOVERY/HANDOFF | CLOSED | Complete Sprint 2 employee-payroll/configuration closure; start Sprint 3 and deliver cycle/population, snapshots, deterministic calculation and controlled recalculation DB foundation | `feature/sprint-2-payroll-configuration` / PR #3; `feature/sprint-3-payroll-execution` / PR #18 | Direct: V021–V026; associated/inherited: V017–V020 | None active | None | `db644298ab3197a6931cd9c6b8d9875ef30d28c5`, CI run 55 success | Thread 3 for remaining Sprint 3; Thread 4/5-era work for later repository state | Governance-only authorised pass to register this row and reconcile living authorities |

## 16. One recommended next authorised action

**Authorise a separate governance-only documentation pass that:**

- creates or restores the missing repository authority files;
- updates the stale running handoff to current `main` `def3dd2…`;
- records Thread 2 as `CLOSED` using the proposed registry row;
- incorporates the unresolved prevention rules into the appropriate authority;
- does not change application code, dependencies or migrations;
- uses no new branch or commit until an exact documentation file allow-list is reviewed and separately authorised.

No implementation or migration action is recommended from this historical thread.

## 17. Separate Git-action status

### Reconciliation-pass status

| Action | Status | Authority |
|---|---|---|
| Repository file modification | **NOT PERFORMED** | Prohibited by the reconciliation request |
| Branch creation | **NOT PERFORMED** | Prohibited |
| Migration reservation | **NOT PERFORMED** | Prohibited |
| Stage | **NOT PERFORMED / NOT AUTHORISED** | Separate explicit authorization required |
| Commit | **NOT PERFORMED / NOT AUTHORISED** | Separate explicit authorization required |
| Push | **NOT PERFORMED / NOT AUTHORISED** | Separate explicit authorization required |
| PR update | **NOT PERFORMED / NOT AUTHORISED** | Separate explicit authorization required |
| Merge | **NOT PERFORMED / NOT AUTHORISED** | Separate explicit authorization required |

### Historical work status

| Historical action | Status |
|---|---|
| Thread 2 Sprint 2 commits | Committed, pushed and merged through PR #3 |
| Thread 2 Sprint 3 commits through `db644298…` | Committed and pushed; later merged through PR #18 |
| PR #3 update/merge | Completed historically |
| PR #18 update/merge | Completed later after Thread 2 handoff |
| Current PR #19 | Merged before this reconciliation pass |
| Current `main` | `def3dd2e212f85c440eee5497e292be2f1f2bf64` |

## 18. Evidence index

| Evidence ID | Source | Key fact |
|---|---|---|
| E-01 | `AGENTS.md` on `main` | Evidence discipline, V001–V030 immutable, next V031, scope and verification rules |
| E-02 | `docs/runbooks/project-continuation-handoff.md` | Running authority and its stale Sprint 4 pre-merge card |
| E-03 | `README.md` on `main` | Current Sprint 1–4 scope |
| E-04 | `backlog/organisation-to-draft-payslip-sprint-backlog.csv` | Canonical Sprint 2 and Sprint 3 story labels |
| E-05 | PR #3 | Sprint 2 delivered scope, V017–V022, merge SHA and final head |
| E-06 | PR #18 | Sprint 3 final scope and merge; used only to identify later completion beyond Thread 2 |
| E-07 | PR #19 | Current Sprint 4 merge into `main` |
| E-08 | Compare `def3dd2…` to `main` | Identical; proves current main SHA |
| E-09 | Compare `84530e1…` to `db644298…` | Exact Thread 2 Sprint 3 boundary: 7 commits, 51 files |
| E-10 | Workflow run on `e98f70b…` | Sprint 2 branch-head run 35 success |
| E-11 | Workflow run on `db644298…` | Thread 2 exit run 55 success |
| E-12 | Workflow run on `b2a22046…` | Sprint 4 branch-head run 81 success |
| E-13 | Current permission classes | Exact employee-payroll, payroll-operations and calculation permission strings |
| E-14 | Current OpenAPI fragments | Payroll operations and calculation path-item contracts |
| E-15 | Uploaded standard project-thread start prompt | Required authority-file and thread-registration procedure |

## 19. Reconciliation conclusion

Thread 2 successfully closed Sprint 2 and implemented the first seven Sprint 3 commits through V026. All of that work is now merged. Thread 2 has no legitimate active file or migration ownership and should be registered as **CLOSED**.

The repository itself is ahead of the running handoff and lacks most of the governance files mandated by the uploaded start prompt. The next action should therefore be a separately authorised, documentation-only governance reconciliation—not application implementation.
