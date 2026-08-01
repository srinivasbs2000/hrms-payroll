# Thread 4 Reconciliation Record

**Project:** HRMS Payroll
**Thread:** Payroll System Design – Thread 4
**Reconciliation mode:** Existing-thread reconciliation; read-only
**Generated:** 1 August 2026, Asia/Kolkata
**Repository:** `srinivasbs2000/hrms-payroll`
**Repository modifications performed:** None
**Git, PR or migration writes performed:** None

---

## 0. Reconciliation result

### 0.1 Final classification

| Item | Reconciled result |
|---|---|
| Exact thread | `Payroll System Design – Thread 4` |
| Historical role | Sprint 4 implementation owner for jurisdiction-neutral statutory deductions and evidence |
| Recommended final role | `CLOSED` |
| Historical branch | `feature/sprint-4-statutory-deductions` |
| Historical PR | PR #19 |
| PR #19 current state | Merged |
| Current remote `main` | `4b5da975eb851434957667bdecf138ea9b43f929` |
| Sprint 4 merge commit | `def3dd2e212f85c440eee5497e292be2f1f2bf64` |
| Final PR head | `b2a220461cf5ba581b5f67e7619ec146bf7982ed` |
| Thread 4 conversational exit head | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` |
| Migration baseline | V001–V030 committed and immutable |
| Next migration | V031 |
| Current local working tree | **NOT VERIFIED** |
| Current local Git index | **NOT VERIFIED** |
| Active Thread 4 write ownership | None |
| One recommended next action | Thread 1 should consolidate this record into repository history and correct the Thread 4 registry row and identified authority-document conflicts through a separately approved documentation-only change |

### 0.2 High-level conclusion

Thread 4 began after Sprint 3 merged and implemented the complete generic,
jurisdiction-neutral statutory foundation through V027–V030, backend execution
and evidence APIs, and a permission-aware React workspace.

At the Thread 4 conversational transition recorded by Living Checkpoint 27:

- the remote implementation head was `6cf39fc...`;
- PR #19 was open;
- persistent PostgreSQL had been migrated from V026 to V030;
- deterministic V4 local seed verification passed;
- authentication smoke was reported passed;
- the deployed-path statutory business smoke remained pending;
- Option A exact-decimal-string money and closure-alignment files were recorded
  as local/uncommitted work;
- no Git or PR write was authorised.

The current repository is later than that checkpoint:

- a seventh Sprint 4 closure commit, `b2a2204...`, was pushed;
- PR #19 metadata was expanded;
- CI run 81 passed;
- PR #19 merged as `def3dd2...`;
- a later governance PR #20 advanced `main` to `4b5da97...`.

The later merged repository supersedes the open-PR state in the Thread 4
handoff. It does not erase the fact that the Thread 4 exit checkpoint still
recorded unresolved manual/full-stack automation gaps.

---

# 1. Exact thread identity and historical purpose

## 1.1 Thread identity

| Field | Reconciled value |
|---|---|
| Thread number | 4 |
| Thread title | `Payroll System Design – Thread 4` |
| Project title used in artifacts | `HRMS Payroll — Payroll System Design, Thread 4` |
| Start date in kickoff | 25 July 2026 |
| Transition date in Checkpoint 27 | 26 July 2026 |
| Historical purpose | Design, implement, verify and prepare closure for Sprint 4 jurisdiction-neutral statutory deductions and evidence |
| Initial baseline | Sprint 3 merged into `main` |
| Initial main SHA | `73c356662b1888194a72c7006a66bd91443550ca` |
| Initial migration baseline | V001–V026 |
| Reserved Sprint 4 migrations | V027–V030 |

## 1.2 Initial product boundary

Thread 4 inherited a regular-payroll vertical slice through:

- organisation hierarchy;
- payroll configuration;
- employee payroll identity and assignment;
- controlled regular payroll cycles;
- sealed input snapshots;
- deterministic payroll calculation;
- recalculation;
- immutable calculation evidence;
- draft payslip evidence.

Thread 4’s immediate purpose was to add a **jurisdiction-neutral statutory
foundation**, not an India legal rule pack.

The remaining product areas explicitly outside the Thread 4 implementation
boundary were:

- named-country statutory rates and legal interpretation;
- retro payroll;
- off-cycle payroll;
- final settlement;
- banking and payment files;
- accounting/GL integration;
- legal/final payslip publication.

---

# 2. Repository authority validation

## 2.1 Authority files read

The reconciliation validated the uploaded thread-start command against the
current committed authority files:

| Authority | Current repository status | Relevant result |
|---|---|---|
| `AGENTS.md` | Present on `main` | Requires repository-first continuation, exact evidence labels, high-risk review, decimal-string money, V001–V030 immutability and multi-thread ownership |
| `docs/design/hrms-payroll-master-design.md` | Present | Records Sprint 4 as merged and implemented; excludes named-country rules and later payroll capabilities |
| `docs/design/decision-register.md` | Present | Records key architecture, security, money, migration and thread-governance decisions |
| `docs/runbooks/project-continuation-handoff.md` | Present | Contains historical pre-merge material plus a later superseding Sprint 4 merge checkpoint |
| `docs/governance/thread-registry.md` | Present | Thread 4 remains `NOT VERIFIED`; this reconciliation supplies the missing record |
| `docs/governance/thread-maintenance-protocol.md` | Present | Requires one write owner, read-only historical recovery and explicit transition status |
| `docs/templates/thread-checkpoint-template.md` | Present | Used as the structural basis for identity, state, decisions, verification and handoff sections |

## 2.2 Current verified remote main

Current GitHub evidence identifies the most recent commit on `main` as:

`4b5da975eb851434957667bdecf138ea9b43f929`

Commit message:

`Merge pull request #20 from srinivasbs2000/docs/living-master-design`

This commit is later than the Sprint 4 merge commit.

## 2.3 Documentation conflict: repository baseline

The following committed authority documents still declare the verified
repository baseline as the Sprint 4 merge commit:

`def3dd2e212f85c440eee5497e292be2f1f2bf64`

Affected documents include:

- `docs/design/hrms-payroll-master-design.md`;
- `docs/governance/thread-registry.md`;
- the superseding checkpoint inside
  `docs/runbooks/project-continuation-handoff.md`.

Current remote evidence shows `main` at `4b5da97...`.

**Classification:** `DOCUMENTATION CONFLICT`

**Resolution required:** update the repository-baseline metadata in the living
authority documents during a separately authorised documentation maintenance
change. The product and Sprint 4 content in those documents remains generally
consistent, but the current-main SHA is stale.

---

# 3. Historical repository entry and exit

## 3.1 Entry source of truth

| Item | Entry state | Evidence class |
|---|---|---|
| Repository | `srinivasbs2000/hrms-payroll` | VERIFIED — REMOTE |
| Local path | `C:\dev\hrms-payroll` | THREAD-RECORDED — LOCAL |
| Main SHA | `73c356662b1888194a72c7006a66bd91443550ca` | VERIFIED — REMOTE |
| Prior PR | PR #18 — Sprint 3 payroll execution foundation | VERIFIED — REMOTE |
| PR #18 state | Merged | VERIFIED — REMOTE |
| PR #18 head | `ebd2603d91551c6f9e60dc57e2d3500948015703` | VERIFIED — REMOTE |
| Migration baseline | V001–V026 | VERIFIED — REMOTE |
| Local branch at earliest kickoff | Expected `main`; exact local result not independently captured | NOT VERIFIED |
| Thread 4 feature branch at earliest kickoff | Not yet created in kickoff artifact | NOT VERIFIED |
| PR #19 at earliest kickoff | Not yet created | NOT APPLICABLE |
| First authorised work | Read-only S4-01A foundation audit | VERIFIED — THREAD ARTIFACT |

## 3.2 First established Thread 4 branch state

By Living Checkpoint 1:

| Item | State |
|---|---|
| Branch | `feature/sprint-4-statutory-deductions` |
| PR | #19 |
| PR title at that stage | `Sprint 4: V027 statutory rule foundation` |
| First Sprint 4 commit | `7a98bef0e239972b8200b363138e5b35007948da` |
| Delivered scope | S4-01B / V027 |
| CI | `payroll-baseline` run 72, passed |
| Merge policy | Keep one PR open through all Sprint 4 increments and merge once after closure |

## 3.3 Thread 4 conversational exit

Living Checkpoint 27 recorded:

| Item | Exit state |
|---|---|
| Branch | `feature/sprint-4-statutory-deductions` |
| HEAD | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` |
| PR #19 | Open and unmerged |
| Migrations | V001–V030 |
| Persistent PostgreSQL | V030, zero failed migrations |
| Deterministic seed | V4 verification passed |
| Authentication smoke | Reported passed |
| Deployed-path statutory business smoke | Pending |
| Exact decimal-string Option A | Local/uncommitted, reported verified |
| Closure alignment | Local/uncommitted |
| Git write authorisation | None |

## 3.4 Later repository completion after the Thread 4 transition

Current repository evidence shows that work prepared during Thread 4 was later
published on the same Sprint 4 branch:

| Item | Later state |
|---|---|
| Closure commit | `b2a220461cf5ba581b5f67e7619ec146bf7982ed` |
| Commit message | `feat(payroll): close Sprint 4 statutory deductions` |
| PR #19 final head | `b2a220461cf5ba581b5f67e7619ec146bf7982ed` |
| PR #19 final changed files | 65 |
| PR #19 final commits | 7 |
| Final CI | `payroll-baseline` run 81, ID `30223401466`, success |
| PR title | `Sprint 4: complete statutory deductions lifecycle` |
| PR state | Merged |
| Merge commit | `def3dd2e212f85c440eee5497e292be2f1f2bf64` |
| Merged at | 26 July 2026, 22:45:22 UTC |

### Attribution limitation

The exact conversational thread in which `b2a2204...` was staged, committed,
pushed and merged is **NOT VERIFIED** from Thread 4’s own final checkpoint.

Thread 4 prepared the local Option A, closure, runtime and seed evidence. Its
Checkpoint 27 explicitly handed the remaining work to Thread 5. Therefore:

- the first six Sprint 4 commits are directly attributable to Thread 4;
- the seventh closure commit is associated with the same Sprint 4 branch and
  Thread 4-prepared scope;
- final publication and merge ownership between Thread 4 and Thread 5 is
  **NOT VERIFIED** and must not be silently assigned.

---

# 4. Branch, pull request and commit reconciliation

## 4.1 Current branch and PR state

| Branch/PR | Current state | Classification |
|---|---|---|
| `main` | Exists; current remote head `4b5da97...` | Active default branch |
| `feature/sprint-4-statutory-deductions` | Still exists remotely at `b2a2204...` | Merged/superseded historical branch |
| Branch comparison | Feature branch has no unique commits; `main` is three commits ahead | VERIFIED — REMOTE |
| PR #19 | Closed and merged | VERIFIED — REMOTE |
| PR #19 merge commit | `def3dd2...` | VERIFIED — REMOTE |
| `docs/living-master-design` | Later governance branch associated with PR #20 | Not Thread 4 implementation scope |
| Current local branch | NOT VERIFIED | No local inspection available |
| Current local-only Thread 4 branch | NOT VERIFIED | No assumption permitted |

The Sprint 4 branch must not be treated as an active implementation branch
merely because the remote ref still exists. Its content is merged and
superseded by `main`.

## 4.2 Sprint 4 commit sequence

| Sequence | Commit | Capability |
|---:|---|---|
| 1 | `7a98bef0e239972b8200b363138e5b35007948da` | V027 statutory rule foundation |
| 2 | `218c099fcbfa4218f4a949673de7268c243e37ed` | Employee statutory profiles and rule assignments |
| 3 | `49e72119a3daa567ae989af3b237da383cdbaebb` | Deterministic statutory evaluation |
| 4 | `34a3af93433eb61b801db36c8ff84fe1ccfad874` | Ledger, balances and reconciliation |
| 5 | `206881e088b8a2d4226cee5db9ca079fcb975e7a` | Execution API and evidence endpoints |
| 6 | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` | Execution and evidence workspace |
| 7 | `b2a220461cf5ba581b5f67e7619ec146bf7982ed` | Sprint 4 closure, exact money, smoke preparation and governance alignment |
| Merge | `def3dd2e212f85c440eee5497e292be2f1f2bf64` | PR #19 merge |

## 4.3 First and last relevant commit

| Meaning | Commit |
|---|---|
| First Thread 4 implementation commit | `7a98bef0e239972b8200b363138e5b35007948da` |
| Last commit definitely present at Thread 4 conversational exit | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` |
| Final Sprint 4 branch commit | `b2a220461cf5ba581b5f67e7619ec146bf7982ed` |
| Sprint 4 merge commit | `def3dd2e212f85c440eee5497e292be2f1f2bf64` |
| Current main after later governance merge | `4b5da975eb851434957667bdecf138ea9b43f929` |

---

# 5. Migrations and backlog stories

## 5.1 Migration responsibility

| Migration | Thread 4 responsibility | Current state |
|---|---|---|
| V027 | Statutory rule identities, effective-dated rule versions, portions and slabs | Merged, immutable |
| V028 | Employee statutory profiles, profile versions and exact rule assignments | Merged, immutable |
| V029 | Component classification, statutory snapshots, evaluation and immutable results | Merged, immutable |
| V030 | Append-only ledger, balances, reconciliation and remittance-preparation evidence | Merged, immutable |
| V031 | Not reserved or implemented by Thread 4 | Available next migration under current design authority |

V001–V026 were inherited and preserved. Thread 4 explicitly prohibited
rewriting them. The current repository extends that immutability boundary
through V030.

## 5.2 Backlog stories

The current committed backlog contains:

| Story | Capability | Thread 4 outcome |
|---|---|---|
| S4-01B | Jurisdiction-neutral statutory rule foundation | Completed and merged |
| S4-02 | Employee statutory profiles and exact rule assignments | Completed and merged |
| S4-03 | Deterministic statutory evaluation | Completed and merged |
| S4-04 | Append-only statutory ledger, balances and reconciliation | Completed and merged |
| S4-05A | Controlled statutory execution and evidence APIs | Completed and merged |
| S4-05B | Permission-aware statutory execution and evidence workspace | Completed and merged |

### Additional story facts

- S4-01A was a read-only architecture audit and was not entered as an
  implementation backlog row.
- No separate closure story ID was approved.
- The closure decision deliberately avoided inventing a story ID.
- Sprint 4 Story Points remain blank because no historical estimates were
  established.

---

# 6. Completed scope

## 6.1 Architecture and database

Completed and merged:

- separate `statutory` PostgreSQL schema;
- separate `backend/statutory-deductions` Maven module;
- Java package `com.acme.hrms.payroll.statutory`;
- tenant-safe statutory tables and composite foreign keys;
- ENABLE/FORCE RLS;
- runtime non-owner access;
- controlled lifecycle functions;
- exact effective-dated lineage;
- append-only statutory evidence.

## 6.2 Statutory rule foundation

Completed:

- stable rule identity;
- immutable effective-dated versions;
- jurisdiction and authority ownership;
- employee and employer liability portions;
- FIXED, PERCENTAGE and SLAB methods;
- thresholds, caps, minimums and rounding;
- ordered non-overlapping slabs;
- approval, end-date and supersession lifecycle;
- audit/outbox evidence;
- negative-path tests.

## 6.3 Employee profile and assignment

Completed:

- stable statutory profile identity;
- effective-dated profile versions;
- registration and classification state;
- exact payroll relationship lineage;
- exact payroll assignment and version lineage;
- exact statutory rule and rule-version lineage;
- eligibility and exemption state;
- parent range containment;
- independent assignment chains per rule;
- approval and supersession controls.

## 6.4 Deterministic statutory evaluation

Completed:

- exact component classification into assessment bases;
- evaluation against an exact completed calculation request;
- separate immutable statutory input snapshot;
- no mutation of V024 sealed input snapshots;
- no mutation of V025/V026 calculation evidence;
- per-rule and per-portion evidence;
- employee deduction and employer liability separation;
- request totals and evidence hashes;
- neutral evaluator version `STATUTORY_NEUTRAL_V1`;
- fail-closed conditional eligibility and exemption semantics.

## 6.5 Ledger, balances and reconciliation

Completed:

- append-only ledger batches and entries;
- initial evaluation posting;
- replacement posting and active-epoch reversal;
- signed corrections;
- exact correction lineage;
- PTD, cycle and YTD snapshots;
- approved balance-year boundaries;
- reconciliation of source, corrections and ledger;
- zero-variance MATCHED outcome;
- remittance-preparation summaries;
- PAYABLE/CREDIT/ZERO positions.

## 6.6 Backend API

Completed:

- cycle-scoped statutory API;
- evaluate command;
- post command;
- signed correction command;
- evaluation/result reads;
- ledger batch/entry reads;
- balance reads;
- reconciliation reads;
- remittance-preparation reads;
- idempotency keys;
- optimistic concurrency with `If-Match`;
- application audit/outbox events;
- dedicated permission model;
- tenant/RLS enforcement;
- aggregate and bounded-context OpenAPI contracts.

## 6.7 Frontend

Completed:

- `/statutory` workspace;
- cycle and calculation selection;
- evaluation action;
- posting action;
- correction action;
- evaluation and result display;
- ledger display;
- balance display;
- reconciliation display;
- remittance-preparation display;
- permission-aware navigation and controls;
- no direct database write path.

## 6.8 Exact money correction

Current merged repository includes:

- `BigDecimal` Java money;
- decimal-string OpenAPI money;
- strict string-only JSON handling;
- frontend string monetary values;
- no binary floating point for statutory money;
- tests for exact values including:
  - `0.1000`;
  - `-10.1250`;
  - `1234567890123.4567`;
- rejection of numeric tokens;
- rejection of excessive fractional precision.

## 6.9 Closure and local operational support

Current merged repository includes:

- updated README and AGENTS scope;
- Sprint 4 backlog entries;
- Sprint 4 closure report;
- Sprint 4 manual-smoke runbook;
- repository continuation handoff;
- Sprint 4 verification script;
- repository-owned local smoke preparation;
- repository-owned temporary identity preparation;
- identity-boundary verifier;
- local-state verification SQL.

---

# 7. Partially completed scope

## 7.1 Deployed-path manual smoke evidence

Thread 4’s own final checkpoint recorded this as pending.

The later PR #19 body asserts that the following were exercised successfully:

- statutory evaluation;
- posting;
- exact correction;
- reconciliation;
- identity boundaries;
- cross-tenant behavior;
- browser-storage safety.

However:

- the committed manual-smoke runbook remains an uncompleted template;
- no completed tester/reviewer evidence record is committed;
- the closure report still says manual smoke was a pre-merge gate;
- the PR body itself contains the stale sentence `PR remains unmerged` even
  though the PR is merged.

**Classification:** `DOCUMENTATION CONFLICT`

**Reconciled status:** manual smoke is a remote PR claim, but the durable
committed evidence record is incomplete.

## 7.2 Persistent-target automation

Thread 4 manually established:

- persistent Compose database parity;
- V026-to-V030 migration;
- role-password synchronization;
- canonical timezone;
- deterministic seed.

The current repository contains local smoke preparation scripts. The main CI
workflow does not invoke the Sprint 4 local-state or identity-boundary scripts.

**Status:** repository-owned tooling exists, but continuous CI coverage of the
persistent-target preparation path is incomplete.

## 7.3 Statutory API integration automation

Existing Thread 4 tests include:

- migration/Testcontainers integration tests;
- controller tests;
- HTTP support tests;
- money serialization tests;
- frontend component tests.

No dedicated real Spring Boot statutory API integration test was identified
that simultaneously:

- starts the application;
- uses migrated PostgreSQL;
- calls the statutory HTTP endpoints;
- passes through authentication/authorization;
- asserts resulting immutable database state.

**Status:** partially automated; required full API integration layer remains
unimplemented.

## 7.4 Statutory full-stack browser automation

The repository has an existing generic Payroll Playwright E2E job inherited
from Sprint 3.

The Sprint 4 PR did not add a dedicated statutory Playwright scenario. The
current CI workflow runs the generic payroll suite and does not invoke the
Sprint 4 statutory local-state/identity scripts.

**Status:** statutory deployed path is not fully CI automated.

## 7.5 Repository authority maintenance

The living design system was added after Sprint 4 through PR #20. Thread 4 is
still marked `NOT VERIFIED` in the thread registry.

**Status:** authority framework exists; Thread 4 historical registration is
incomplete.

---

# 8. Unstarted or explicitly deferred scope

The following were not implemented by Thread 4 and remain explicitly excluded
by the current master design unless separately approved:

- India PF;
- EPS;
- EDLI;
- ESI;
- professional tax;
- labour welfare fund;
- NPS;
- salary TDS;
- tax declarations and proof workflows;
- named-country statutory rates, ceilings and slabs;
- legal interpretation;
- authority filing;
- returns;
- acknowledgements;
- remittance payment and settlement;
- banking/payment files and APIs;
- accounting and GL integration;
- retro payroll;
- arrears;
- off-cycle payroll;
- supplementary payroll;
- recoveries and employee receivables;
- salary advances;
- final settlement;
- legal/final payslip publication;
- authoritative global multi-currency payroll execution.

### Deferred S4-01C concept

The S4-01A proposal anticipated a possible statutory configuration API/UI
slice. Thread 4’s delivered S4-05A API explicitly excluded statutory
rule/profile/classification CRUD.

**Status:** deferred; no implemented S4-01C story was established.

---

# 9. Repository areas affected

## 9.1 Modules and bounded contexts

| Area | Effect |
|---|---|
| `backend/statutory-deductions` | New statutory application module |
| `backend/database-migrations` | V027–V030 migration integration tests |
| `backend/payroll-boot` | Module composition and architecture verification |
| `database/flyway/sql` | Four new immutable migrations |
| `database/flyway/verification` | Expanded schema/RLS verification |
| `contracts/openapi` | New statutory contract and aggregate references |
| `deploy/local/keycloak` | New statutory permissions and development mappings |
| `deploy/local/smoke` | Local preparation and identity-boundary tooling |
| `frontend/payroll-web` | Statutory feature workspace |
| `docs/quality` | Increment and closure evidence |
| `docs/runbooks` | Operator, manual-smoke and continuation guidance |
| `backlog` | Sprint 4 stories |
| `scripts` | Sprint 4 local verification |
| root Maven POM | New module inclusion |

## 9.2 API and contract areas

Affected:

- aggregate payroll OpenAPI;
- statutory bounded-context OpenAPI;
- evaluate command;
- post command;
- correction command;
- evidence reads;
- balance/reconciliation/remittance reads;
- error/problem response behavior;
- idempotency header;
- optimistic concurrency header;
- decimal-string money schema.

## 9.3 Permissions

Thread 4 introduced:

- `statutory-evaluation.execute`;
- `statutory-evaluation.read`;
- `statutory-ledger.post`;
- `statutory-ledger.correct`;
- `statutory-ledger.read`;
- `statutory-balance.read`;
- `statutory-reconciliation.read`;
- `statutory-remittance.read`.

## 9.4 Test areas

Affected:

- V027 migration integration;
- V028 migration integration;
- V029 migration integration;
- V030 migration integration;
- architecture/module rules;
- controller tests;
- HTTP support tests;
- exact money JSON tests;
- frontend workspace tests;
- application navigation tests;
- full Maven reactor;
- frontend tests/build;
- OpenAPI validation;
- local identity boundaries;
- local statutory preparation.

## 9.5 Exact PR #19 changed-file inventory

PR #19 changed the following 65 files:

1. `AGENTS.md`
2. `README.md`
3. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeeStatutoryProfileMigrationIT.java`
4. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/StatutoryEvaluationMigrationIT.java`
5. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/StatutoryLedgerMigrationIT.java`
6. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/StatutoryRuleMigrationIT.java`
7. `backend/payroll-boot/pom.xml`
8. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/ArchitectureRulesTest.java`
9. `backend/statutory-deductions/pom.xml`
10. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/DecimalString.java`
11. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryBalanceSnapshotView.java`
12. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryController.java`
13. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryCorrectionCommand.java`
14. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryCorrectionExecution.java`
15. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryEvaluationCommand.java`
16. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryEvaluationExecution.java`
17. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryEvaluationRequestView.java`
18. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryHttpSupport.java`
19. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryLedgerBatchView.java`
20. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryLedgerEntryView.java`
21. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryLedgerPostingCommand.java`
22. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryLedgerPostingExecution.java`
23. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryPermissions.java`
24. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryReconciliationView.java`
25. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryRemittanceSummaryView.java`
26. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/StatutoryResultView.java`
27. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/internal/application/StatutoryService.java`
28. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/internal/infrastructure/StatutoryRepository.java`
29. `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/package-info.java`
30. `backend/statutory-deductions/src/test/java/com/acme/hrms/payroll/statutory/StatutoryControllerTest.java`
31. `backend/statutory-deductions/src/test/java/com/acme/hrms/payroll/statutory/StatutoryHttpSupportTest.java`
32. `backend/statutory-deductions/src/test/java/com/acme/hrms/payroll/statutory/StatutoryMoneyJsonTest.java`
33. `backlog/organisation-to-draft-payslip-sprint-backlog.csv`
34. `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
35. `contracts/openapi/statutory-deductions-openapi-v1.yaml`
36. `database/flyway/README.md`
37. `database/flyway/sql/V027__statutory_rule_identity_versions.sql`
38. `database/flyway/sql/V028__employee_statutory_profiles_assignments.sql`
39. `database/flyway/sql/V029__statutory_classification_evaluation.sql`
40. `database/flyway/sql/V030__statutory_ledger_balances_reconciliation.sql`
41. `database/flyway/verification/verify_vertical_slice.sql`
42. `deploy/local/keycloak/payroll-realm.json`
43. `deploy/local/smoke/prepare-sprint-4-local-state.ps1`
44. `deploy/local/smoke/prepare-sprint-4-test-identities.ps1`
45. `deploy/local/smoke/sprint-4-local-state.sql`
46. `deploy/local/smoke/verify-sprint-4-identity-boundaries.mjs`
47. `deploy/local/smoke/verify-sprint-4-local-state.sql`
48. `docs/quality/s4-01b-v027-statutory-rule-foundation.md`
49. `docs/quality/s4-02-v028-employee-statutory-profiles.md`
50. `docs/quality/s4-03-v029-statutory-evaluation.md`
51. `docs/quality/s4-04-v030-statutory-ledger.md`
52. `docs/quality/s4-05a-statutory-execution-api.md`
53. `docs/quality/s4-05b-statutory-execution-ui.md`
54. `docs/quality/sprint-4-closure-report.md`
55. `docs/runbooks/project-continuation-handoff.md`
56. `docs/runbooks/sprint-4-manual-smoke.md`
57. `docs/runbooks/statutory-execution-ui.md`
58. `frontend/payroll-web/src/App.test.tsx`
59. `frontend/payroll-web/src/App.tsx`
60. `frontend/payroll-web/src/features/statutory/StatutoryWorkspacePage.test.tsx`
61. `frontend/payroll-web/src/features/statutory/StatutoryWorkspacePage.tsx`
62. `frontend/payroll-web/src/features/statutory/statutory-api.ts`
63. `frontend/payroll-web/src/styles.css`
64. `pom.xml`
65. `scripts/verify-sprint-4.ps1`

---

# 10. Material decisions introduced or changed

## 10.1 Product decisions

- Implement generic statutory infrastructure before named-country legal rules.
- Keep contractual compensation separate from statutory law and liability.
- Support employee and employer portions separately.
- Treat remittance as preparation evidence, not payment or legal filing.
- Keep retro, off-cycle, banking, accounting and legal payslips excluded.
- Preserve INR-only operational execution despite jurisdiction-neutral rule
  metadata.

## 10.2 Architecture decisions

- Create a dedicated statutory bounded context.
- Reuse organisation, compensation and employee-payroll exact-version lineage.
- Create a separate statutory input snapshot rather than rewriting V024.
- Keep V025/V026 payroll evidence immutable.
- Use PostgreSQL functions as the sole statutory business-write path.
- Use an append-only ledger as the statutory accounting source of truth.
- Derive balances, reconciliation and remittance summaries from immutable
  evidence.
- Use a separately versioned neutral evaluator.
- Require active posting epochs for replacement logic.
- Append signed corrections; never rewrite prior entries.

## 10.3 Security and tenancy decisions

- Every statutory relation is tenant-owned.
- Tenant-owned foreign keys include `tenant_id`.
- RLS is enabled and forced.
- Runtime is non-owner and `NOBYPASSRLS`.
- Transactions set tenant context through `SET LOCAL`.
- Cross-tenant references and reads fail closed.
- Permission names are explicit and separated by command/read concern.
- OIDC identity uses issuer plus subject.
- Tokens and payroll/statutory payloads are not persisted in browser storage.
- Security-definer functions are narrow, fixed-search-path and inaccessible to
  `PUBLIC`.

## 10.4 Lifecycle decisions

- Approved versions do not overlap.
- Effective ranges are half-open.
- Later versions/assignments explicitly supersede prior members of the same
  chain.
- Independent statutory rules begin independent assignment chains.
- Approval and end-dating use controlled commands.
- Historical evidence remains immutable.
- Recalculation and replacement create new evidence.
- Corrections create signed deltas.

## 10.5 Delivery decisions

- Use one Sprint 4 branch and one PR.
- Keep PR #19 open across all increments.
- Merge once after complete closure.
- Use exact package allow-lists and preimage checks.
- Apply without implicit Git writes.
- Stage, commit, push, PR metadata update and merge are separate decisions.
- Do not use dependency changes merely to silence scanners.
- Keep ecosystem-native parsing in repository-native tools.
- Use transition-based checkpoints, not a checkpoint for every retry.

## 10.6 Verification decisions

- `mvn verify` is required; `mvn test` is insufficient.
- Run focused tests before broad regression.
- Treat Failsafe integration phases as mandatory evidence.
- Validate clean migrations and RLS.
- Run frontend lint, tests and build separately.
- Validate aggregate OpenAPI.
- Perform independent critical review for statutory, money, RLS and audit work.
- Separate evidence status into:
  - ephemeral test environment;
  - persistent target parity;
  - deployed-path smoke.
- Never claim “all green” without naming the target.

---

# 11. Critical-review and exact-money decisions

## 11.1 Blocking finding CR-S4-001

The critical review found that the committed statutory API/UI used JSON and
TypeScript numeric money while repository policy required decimal-string money.

Risk:

- browser IEEE-754 representation could alter exact correction identity before
  Java/database processing;
- statutory corrections require exact replayable signed values;
- whole-number tests did not expose the defect.

## 11.2 Approved decision

Option A was selected:

- OpenAPI money is a decimal string.
- Java money remains `BigDecimal`.
- Jackson accepts string tokens only.
- Serialization uses plain decimal representation.
- Numeric JSON tokens are rejected.
- More than four fractional digits are rejected.
- Frontend monetary values remain strings.
- No correction-path `Number(...)` conversion.
- Exact tests include `0.1000`, `-10.1250` and
  `1234567890123.4567`.

## 11.3 Rejected alternative

Option B—retain JSON numeric money and redefine repository policy—was rejected.

It would have required:

- a cross-repository architecture decision;
- precision/range analysis;
- browser decimal strategy;
- revised contracts and governance.

No evidence justified weakening the existing exact-money rule.

## 11.4 Corrective-review status

The corrective critical review reported no remaining blocking finding for
manual-smoke entry, subject to:

- exact reviewed file scope;
- empty index;
- green verification summary;
- matching manifest;
- no subsequent edits.

It still required live browser/network confirmation of quoted monetary
transport.

---

# 12. Statutory automation decisions

## 12.1 Real API integration test requirement

Thread 4 explicitly identified a missing layer:

> A real statutory API integration test must start Spring Boot against
> PostgreSQL migrated through V030, exercise the statutory HTTP endpoints
> through the actual security/service/repository/database-function path, and
> assert both HTTP responses and resulting database state.

### Minimum expected positive coverage

- evaluate;
- read evaluation/results;
- post;
- read ledger;
- read balances;
- read reconciliation;
- read remittance preparation;
- append signed correction;
- assert audit/outbox once;
- assert exact decimal-string request/response;
- assert database immutability and derived state.

### Minimum expected negative coverage

- unauthenticated access;
- read-only command denial;
- no-read denial;
- cross-tenant isolation;
- stale version;
- idempotent replay;
- zero/zero correction;
- invalid precision;
- short reason;
- numeric JSON money rejection;
- duplicate audit/outbox prevention.

### Intended class/module/location

No exact class name, source path or Maven module was formally fixed in Thread 4.

**Status:** `NOT VERIFIED`

### Implementation status

No dedicated statutory real-HTTP-to-database integration class was identified
in PR #19’s changed files or the current CI workflow.

**Status:** not implemented.

## 12.2 Full-stack browser automation commitment

Thread 4 required:

1. stabilize one deployed-path manual operator journey;
2. convert that journey into repository-owned Playwright or equivalent;
3. run it against a complete ephemeral stack;
4. verify identity boundaries and exact network money;
5. make it a CI gate.

The current workflow still runs the generic Sprint 3 payroll Playwright suite.
No statutory-specific E2E file was added by PR #19.

**Status:** commitment not completed.

## 12.3 Manual versus automated distinction

| Gate | Thread 4/current state |
|---|---|
| Migration behavior | CI automated |
| RLS/immutability/idempotency | CI automated |
| Controller behavior | CI automated |
| Money JSON contract | CI automated after closure commit |
| Frontend statutory component behavior | CI automated |
| Generic payroll browser E2E | CI automated |
| Statutory HTTP/database integration | Not automated |
| Statutory browser/operator journey | Not dedicated in CI |
| Identity-boundary local script | Repository-owned, reported executed; not invoked by main CI |
| Completed manual-smoke evidence record | Not committed |

---

# 13. Persistent deployment decisions

## 13.1 Persistent V026-to-V030 migration

Thread 4 discovered that:

- fresh Testcontainers databases reached V030;
- the persistent Compose volume remained at V026;
- Spring Boot runtime Flyway was disabled;
- green ephemeral tests did not prove local deployment readiness.

The corrected persistent-deployment gate:

- inspected the real target;
- confirmed V026 and zero failed migrations;
- applied V027–V030 as `payroll_migrator`;
- validated V030;
- confirmed required statutory objects;
- preserved the volume;
- did not create seed data.

## 13.2 Runtime roles

| Role | Decision |
|---|---|
| PostgreSQL administrator | Bootstrap and role synchronization only |
| `payroll_migrator` | Flyway migration role |
| `payroll_app` | Non-owner application runtime |
| `payroll_owner` | Object owner, not runtime principal |
| Spring Boot runtime | Does not run Flyway automatically |

## 13.3 Credential handling

- Persistent volumes may retain older role passwords.
- Synchronize roles against the existing volume.
- Never expose passwords in evidence.
- Rotate any credential accidentally displayed.
- Do not delete a volume merely to align credentials.

## 13.4 Timezone

Canonical local runtime timezone:

`Asia/Kolkata`

The obsolete alias `Asia/Calcutta` was rejected by PostgreSQL 17 during
connection startup.

## 13.5 Volume preservation

Routine correction must not use destructive volume deletion.

## 13.6 Seed lifecycle

| Seed | Failure/result | Final state |
|---|---|---|
| Initial seed | Persistent target lacked V027 objects | Rolled back |
| V1 | Incorrect assignment sequence across independent rule chains | Withdrawn |
| V2 | Invented calculation-request table name | Withdrawn |
| V3 | PowerShell flattened function records to `o.r` | Withdrawn |
| V4 | Deterministic seed verification passed | Accepted |

V4 evidence:

- legal entities: 1;
- payroll relationships: 1;
- statutory rules: 2;
- independent assignment chains: 2;
- calculated cycles: 1;
- completed calculation requests: 1.

---

# 14. Verification and CI record

## 14.1 Increment CI

| Increment | Run | Result |
|---|---|---|
| S4-01B / V027 | 72 | Passed |
| S4-02 / V028 | 73 | Passed |
| S4-03 / V029 | 74 / ID `30186387658` | First attempt hit hosted-runner Ryuk pull timeout; rerun passed without code change |
| S4-04 / V030 | Exact run number not preserved in final Thread 4 artifacts | Reported green; run number **NOT VERIFIED** |
| S4-05A | 76 / ID `30190848927` | Passed |
| S4-05B | 77 / ID `30197879363` | Passed |
| Final closure head | 81 / ID `30223401466` | Passed |

## 14.2 Thread 4 full local regression evidence

The supplied regression log recorded:

- Flyway V001–V030 application/validation;
- database integration tests;
- backend Maven verification;
- frontend tests/build;
- aggregate OpenAPI validation;
- dependency/security policy checks;
- final diff checks.

The earlier “all green” wording was later restricted by the environment-parity
decision: these results established the ephemeral/source gate, not the
persistent local target or deployed browser path.

## 14.3 Current CI workflow limitation

Current `.github/workflows/ci.yml` includes:

- Maven verify;
- frontend test/build/audit policy;
- OpenAPI;
- fresh Flyway/RLS;
- authentication smoke;
- secret scan;
- dependency review;
- SBOM;
- generic Payroll browser E2E.

It does not invoke:

- `scripts/verify-sprint-4.ps1`;
- `deploy/local/smoke/prepare-sprint-4-local-state.ps1`;
- `deploy/local/smoke/verify-sprint-4-identity-boundaries.mjs`;
- a statutory-specific Playwright test;
- a dedicated statutory Spring Boot API integration test.

---

# 15. Failure learning and permanent prevention

## 15.1 T4-001 through T4-021

| ID | Failure/root cause | Permanent prevention | Durable location reached |
|---|---|---|---|
| T4-001 | PowerShell variable followed by colon parsed as scope syntax | Brace variables before punctuation and parser-check scripts | Thread 4 running handoff |
| T4-002 | Restricted trigger could not lock parent row | Narrow `SECURITY DEFINER`, fixed search path, revoke `PUBLIC` | Migration implementation and handoff |
| T4-003 | Native output treated as character/scalar | Capture arrays explicitly; validate cardinality | Handoff |
| T4-004 | Unqualified `version_no` ambiguity | Qualify mutation targets and query columns | Migration code/tests and handoff |
| T4-005 | Test fixture reused natural key | Explicit fixture identity matrix | Handoff |
| T4-006 | Expected SQL error aborted transaction | Savepoint around expected database failure | Test code and handoff |
| T4-007 | Failure evidence trapped in timestamped logs | Copy concise failure evidence to stable location | Handoff/process artifacts |
| T4-008 | Insert-chain rule executed on update | Guard by trigger operation and test lifecycle update | Migration code/tests |
| T4-009 | Draft SQL referenced nonexistent schema field/status | Audit every referenced object against committed schema | Handoff |
| T4-010 | PL/pgSQL variable collided with query column | `p_`/`v_`, aliases and record qualification | Migration code/tests and handoff |
| T4-011 | Hosted CI timeout pulling Ryuk | Classify infrastructure before changing code | Handoff |
| T4-012 | Replacement ledger reversed excessive history | Reverse only active posting epoch | V030 design/tests and handoff |
| T4-013 | Empty pipeline lost array identity | Array-wrap receiving assignments | Handoff |
| T4-014 | Inline conditional inside hashtable expression | Compute conditional values before object construction | Handoff |
| T4-015 | Generic .NET list binder error | Prefer native PowerShell arrays/raw evidence | Handoff |
| T4-016 | Scanner suggestion conflicted with architecture | Bounded reviewed security decision; no uncontrolled audit fix | AGENTS/CI policy/handoff |
| T4-017 | Existing executable policy not discovered | Search repository policies before remediation | AGENTS/handoff |
| T4-018 | Workflow display name treated as filename | Resolve exact committed path | Handoff |
| T4-019 | PowerShell unnecessarily parsed npm lockfile v3 | Use repository-native Node parser | Executable audit policy and handoff |
| T4-020 | New thread reconstructed state from memory | Running handoff first, then live repository validation | AGENTS, handoff and later thread protocol |
| T4-021 | Documentation drifted behind implementation | Documentation alignment is a closure gate | README/AGENTS/backlog/runbook closure scope |

## 15.2 Runtime and seed failures

The Thread 4-to-5 handoff supplied a later authoritative mapping:

| ID | Failure/root cause | Prevention | Durable location reached |
|---|---|---|---|
| T4-025 | Decimal verifier parser error from unquoted comma-bearing Maven argument | Explicit native executable plus quoted argument arrays | Thread 4-to-5 handoff |
| T4-026 | Persistent role password differed from environment | Synchronize roles against existing volume; protect secrets | Thread 4-to-5 handoff |
| T4-027 | JVM used obsolete `Asia/Calcutta` | Force canonical `Asia/Kolkata` | Thread 4-to-5 handoff |
| T4-028 | Manual smoke started without tenant-aligned data | Deterministic seed is a smoke precondition | Thread 4-to-5 handoff |
| T4-029 | Ephemeral PASS treated as persistent readiness | Report environment gates separately | Checkpoint 25 and handoff |
| T4-030 | Seed V1 used sequence 2 for a different rule chain | Every independent rule chain starts at 1 | Handoff |
| T4-031 | Seed V2 invented a table name | Validate canonical relations before use | Handoff |
| T4-032 | Seed V3 flattened function tuples to `o.r` | Validate function contract in PostgreSQL | Handoff |
| T4-033 | Detached seed package published without target execution | Repository-owned, tested tooling | Handoff; later repository local-smoke scripts |

## 15.3 Failure-register numbering gap

The final Thread 4-to-5 handoff intentionally superseded temporary reused
numbers.

Final meanings of T4-022 through T4-024 are not consistently preserved.

**Classification:** `NOT VERIFIED`

A closure-verifier defect involving standalone validation of the statutory
OpenAPI fragment is evidenced, but its final T4 ID is not reliably established.

**Required prevention:** stable failure IDs must be assigned once in the
repository running handoff or decision register; temporary chat numbering must
not become authority.

---

# 16. Conflicts requiring explicit resolution

## 16.1 Current repository versus authority baseline

| Source | Claim |
|---|---|
| Live `main` | `4b5da975...` |
| Master design | `def3dd2e...` |
| Thread registry | `def3dd2e...` |
| Running handoff superseding checkpoint | `def3dd2e...` |

**Conflict:** living authority metadata is one governance merge behind current
`main`.

## 16.2 Thread registry versus recovered history

Current registry row:

`Thread 4 | NOT VERIFIED`

Recovered evidence proves:

- exact thread purpose;
- exact branch;
- PR #19;
- commit range;
- migration range;
- merged outcome;
- current no-write ownership.

**Conflict:** registry is incomplete, not factually current.

## 16.3 Running handoff internal conflict

The committed running handoff contains:

- an old continuation card with PR #19 open at `6cf39fc...`;
- a later superseding section saying PR #19 merged at `def3dd2...`.

The later section explicitly supersedes the earlier state, but the document
retains both without updating the top metadata.

**Classification:** `DOCUMENTATION CONFLICT`, internally resolved only by the
document’s superseding section.

## 16.4 Closure report versus merged PR

The committed closure report says:

- manual smoke pending;
- merge not yet approved;
- PR #19 must remain open.

Current GitHub says:

- PR #19 merged;
- closure head CI passed;
- PR body claims smoke and identity checks passed.

**Conflict:** closure report was not converted from pre-merge plan to final
post-merge evidence.

## 16.5 Manual-smoke runbook versus PR body

The committed runbook is blank.

The PR body claims completed operational smoke.

**Conflict:** a PR metadata claim exists without a committed completed evidence
record.

## 16.6 PR body internal conflict

PR #19 is merged, but its body still includes:

`PR remains unmerged`

**Conflict:** stale sentence in final PR metadata.

## 16.7 Thread 4 versus Thread 5 ownership

Checkpoint 27 handed the remaining deployed-path smoke and closure to Thread 5.

The same Sprint 4 branch later received the closure commit and merged.

Exact ownership of the final closure commit and merge action is not established
by Thread 4 artifacts.

**Classification:** `NOT VERIFIED / CROSS-THREAD ATTRIBUTION GAP`

## 16.8 Current migration baseline

All current authority sources agree:

- V001–V030 are committed and immutable;
- new migrations begin at V031.

No migration conflict exists.

---

# 17. Unsupported or incomplete facts

The following must remain `NOT VERIFIED`:

1. Current local branch at `C:\dev\hrms-payroll`.
2. Current local working-tree cleanliness.
3. Current local Git index state.
4. Whether the remote Sprint 4 branch is also present locally.
5. Exact conversational thread that executed `b2a2204...`.
6. Exact Thread 5 contribution to final Sprint 4 closure.
7. Exact run number for the V030 commit before S4-05A.
8. Final stable meanings of T4-022 through T4-024.
9. Exact class/module name intended for the missing statutory API integration
   test.
10. Completed manual-smoke tester/reviewer sign-off.
11. CI execution of the repository-owned identity-boundary script.
12. CI execution of the Sprint 4 local-state preparation script.
13. Production readiness of the local development smoke tooling.
14. Any named-country statutory correctness.
15. Any filing, remittance payment, settlement or legal payslip completion.
16. Any Sprint 5 feature selection.
17. Any current active implementation branch after PR #20.
18. Any current migration reservation.
19. Any current implementation owner.
20. Whether the historical Sprint 4 remote branch should be deleted; no such
    action is authorised by this reconciliation.

---

# 18. Work superseded by later merged implementation

| Historical Thread 4 state | Later repository state |
|---|---|
| PR #19 open at six commits | PR #19 merged with seven commits |
| Head `6cf39fc...` | Final PR head `b2a2204...` |
| Option A local/uncommitted | Exact decimal-string implementation merged |
| Closure files local/uncommitted | Closure files merged |
| Repository seed tooling required | Local preparation/verification scripts merged |
| PR metadata stale | PR title/body expanded, though one stale sentence remains |
| README/AGENTS stale | Scope updated |
| Backlog ended before Sprint 4 | Sprint 4 rows merged |
| No repository handoff | Repository handoff merged |
| Thread 4 registry absent | Living thread registry exists, but Thread 4 row remains unresolved |

---

# 19. Unresolved debt in the current repository

## 19.1 Critical automation debt

- No dedicated statutory Spring Boot API integration test.
- No dedicated statutory Playwright/full-stack CI scenario.
- Generic Payroll E2E must not be counted as statutory E2E.
- Sprint 4 local-state and identity-boundary scripts are not invoked by the main
  CI workflow.

## 19.2 Evidence debt

- No committed completed manual-smoke evidence record.
- Closure report remains pre-merge.
- PR body contains a stale “unmerged” statement.
- Failure-register numbering contains a gap.

## 19.3 Governance debt

- Thread 4 is still `NOT VERIFIED` in the registry.
- Current-main SHA metadata is stale in the master design, registry and handoff.
- The running handoff retains obsolete top-level state.
- Exact closure ownership between Thread 4 and Thread 5 is not recorded.

## 19.4 Operational/security debt

Current decision register and master design retain:

- feed-dependent OWASP Dependency Check cache/scheduling follow-up;
- React Router scoped advisory review deadline of 31 October 2026;
- production broker replay/alerting operational debt.

These were not created by the reconciliation and remain current project debt.

---

# 20. Assumptions future threads must not make

Future threads must not assume:

1. Thread 4’s final conversational head is current `main`.
2. PR #19 is still open.
3. The Sprint 4 feature branch is an active development branch.
4. Generic Payroll browser E2E covers statutory execution.
5. Controller tests equal a real API integration test.
6. Testcontainers success proves a persistent deployment target.
7. Spring Boot startup applies Flyway migrations.
8. A blank manual-smoke runbook proves smoke completion.
9. PR metadata alone is complete durable test evidence.
10. Country-neutral statutory infrastructure is an India legal payroll pack.
11. Remittance preparation means filing or payment.
12. Draft payslip means legal/final payslip.
13. `number` is acceptable for statutory money.
14. Existing V001–V030 migrations may be edited.
15. A new migration may use V030 or lower.
16. Local credentials match a persistent volume without verification.
17. `Asia/Calcutta` is acceptable to PostgreSQL 17.
18. A detached seed package is valid without execution against the target.
19. A historical checkpoint overrides current repository evidence.
20. Chat history is project authority.
21. Another thread may write overlapping files without registry ownership.
22. Stage, commit, push, PR update and merge share one authorisation.

---

# 21. Recommended final thread role

## 21.1 Role decision

**Recommended role:** `CLOSED`

### Rationale

- The Sprint 4 implementation branch was merged.
- V027–V030 are committed and immutable.
- PR #19 is closed and merged.
- The branch has no unique commits relative to `main`.
- No current migration or file ownership should remain with the historical
  thread.
- Remaining work is project-wide documentation/automation debt, not an active
  Thread 4 implementation increment.
- Historical recovery is now represented by this reconciliation file.

Thread 4 may be referenced for history but must not resume implementation
ownership without a new registration and explicit scope.

## 21.2 Proposed complete thread-registry row

```markdown
| Thread 4 | CLOSED — historical Sprint 4 implementation owner | Jurisdiction-neutral statutory rule/profile/assignment/evaluation/ledger/balance/reconciliation/remittance-preparation foundation; execution API; permission-aware UI; exact decimal-string money; local operational closure preparation | `feature/sprint-4-statutory-deductions`; PR #19 merged; final head `b2a220461cf5ba581b5f67e7619ec146bf7982ed`; merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` | None | `docs/history/thread-4-reconciliation.md` | Thread 1 consolidates this record, resolves authority-document conflicts and records remaining statutory API/E2E automation debt |
```

## 21.3 Expanded registration fields

| Field | Proposed value |
|---|---|
| Thread | Thread 4 |
| Role | CLOSED |
| Branch/PR | `feature/sprint-4-statutory-deductions`; PR #19 merged |
| Approved scope | Historical Sprint 4 statutory infrastructure and closure |
| Exact file allow-list | None; thread is closed |
| Migration reservation | None |
| Immutable migration range | V001–V030 |
| Verification | Historical CI through run 81; current automation gaps recorded |
| Latest checkpoint | Living Checkpoint 27 plus this reconciliation |
| Blockers | No implementation blocker; documentation and automation debt remain |
| Next authorised action | Thread 1 documentation consolidation |
| Prohibited actions | No Thread 4 implementation write, migration reservation, branch reuse or publication action without new registration |

---

# 22. One recommended next authorised action

Thread 1, acting as the repository governance/recovery owner, should review this
record and prepare one bounded documentation-only reconciliation change that:

- adds `docs/history/thread-4-reconciliation.md`;
- updates the Thread 4 registry row to `CLOSED`;
- updates current-main SHA metadata from `def3dd2...` to the live verified
  baseline;
- reconciles the running handoff’s stale pre-merge sections;
- converts the Sprint 4 closure report into final historical evidence or marks
  the missing manual evidence explicitly;
- records the missing statutory API integration and dedicated statutory
  Playwright work as unresolved debt;
- preserves V001–V030 and all implementation files unchanged.

This reconciliation does not authorise that repository change. It only
recommends it as the next separately authorised action.

---

# 23. Separate operational status

| Status dimension | Current reconciled status |
|---|---|
| Local working tree | NOT VERIFIED |
| Local Git index | NOT VERIFIED |
| Historical Thread 4 implementation commits | Published |
| Final Sprint 4 closure commit | Published |
| Feature-branch push | Completed historically |
| Current push action | Not authorised |
| PR #19 title/body update | Completed historically; contains one stale sentence |
| Current PR update action | Not authorised |
| PR #19 merge | Completed |
| Current merge action | Not applicable |
| Feature branch deletion | Not performed/NOT VERIFIED; not authorised |
| Migration rewrite | Not performed; prohibited |
| New migration reservation | None |
| Current active implementation owner | NOT VERIFIED |
| Thread 4 write ownership | None |

---

# 24. Exact evidence inventory

## 24.1 Uploaded reconciliation command

| File | SHA-256 |
|---|---|
| `thread-start-prompt(4).md` | `791fbbcaca53d139892a6805a9edd76e8d532c9cab5bba00b099b7a19984ebd4` |

## 24.2 Current repository authority files

| Repository file | Blob SHA |
|---|---|
| `AGENTS.md` | `7c7eb8407404679cadb384beea51626d08209565` |
| `docs/design/hrms-payroll-master-design.md` | `96fa55c6f9e5b1a7071f728fb415752e086ee0c8` |
| `docs/design/decision-register.md` | `db513793c7f1513d18b91edee4aefde152163c10` |
| `docs/governance/thread-registry.md` | `af6158895a143c9ea97da9c47b5bd1dc0e975368` |
| `docs/governance/thread-maintenance-protocol.md` | `dcc725e1eaf0acb9751d925d778b2cc193778068` |
| `docs/templates/thread-checkpoint-template.md` | `adb5aaf81dec86818678ae4337029680c5202e60` |
| `backlog/organisation-to-draft-payslip-sprint-backlog.csv` | `7f40794bda6ea467831b5c9f935c32385425ad40` |
| `.github/workflows/ci.yml` | `31299505cb9924e56b62fabee5cd93e0f7c1f521` |
| `docs/quality/sprint-4-closure-report.md` | `8cd7d8e65d0149329309848b77d71307dd477635` |
| `docs/runbooks/sprint-4-manual-smoke.md` | `b67cde532d40102db96b654052822d8d81998253` |

The current handoff blob SHA was not extracted independently from the connector
response and is therefore **NOT VERIFIED** in this inventory.

## 24.3 Current remote GitHub evidence

- Repository default branch: `main`.
- Current latest main commit:
  `4b5da975eb851434957667bdecf138ea9b43f929`.
- PR #19:
  - merged;
  - final head `b2a220461cf5ba581b5f67e7619ec146bf7982ed`;
  - merge commit `def3dd2e212f85c440eee5497e292be2f1f2bf64`;
  - seven commits;
  - 65 changed files.
- Final Sprint 4 CI:
  - workflow `payroll-baseline`;
  - run 81;
  - ID `30223401466`;
  - conclusion success.
- Feature branch comparison:
  - branch still exists;
  - branch head `b2a2204...`;
  - no commits unique to branch;
  - `main` three commits ahead.
- Later governance merge:
  - PR #20 merge commit `4b5da97...`.

## 24.4 Thread 4 entry and architecture evidence

| File | SHA-256 |
|---|---|
| `Payroll-System-Design-Thread-4-Kickoff.md` | `c691994daca859b2dfe02901e4b9ca39c2a954d68b251783b571ae283e0fbe84` |
| `S4-01A-Statutory-Deduction-Foundation-Audit.md` | `37f9ca246b2a1695a294c3a52522015d7ea040043d7d87c40ba24853032c682a` |

## 24.5 Closure and critical-review evidence

| File | SHA-256 |
|---|---|
| `HRMS-Payroll-Sprint-4-Closure-Discovery.txt` | `278122aeb5b3f3beacff402c221046ad9ab7d1009d9a96531c3197eca674dab1` |
| `HRMS-Payroll-Sprint-4-Closure-Approval-Sheet.md` | `63bb0628d3fd407cecae9badf33a9264f4dbcc4e2f3dabf8006eff2eaeda4263` |
| `HRMS-Payroll-Sprint-4-Critical-Review.md` | `e7480f80af966aac29b4c2e5f73d71687e2c93fa9a661febb18e70d8b5ffcaf3` |
| `HRMS-Payroll-Sprint-4-Monetary-API-Decision.md` | `959a64dd5a3385cc59c32354ac73773d95af6b74d36f0c9c8f9ae4b1407f2296` |
| `HRMS-Payroll-S4-Option-A-Corrective-Critical-Review.md` | `18eaa912d29e4e683417183883fda4bfc77953e60235d5cd0c0c30e143229ff1` |
| `HRMS-Payroll-S4-Manual-Smoke-Evidence-Form.md` | `e7153061f26498d0cd9ad7351598a16374ee2193b15fe021d28e69b8d3744b68` |

## 24.6 Persistent deployment evidence

| File | SHA-256 |
|---|---|
| `S4-Persistent-Runtime-Parity-Inspection.txt` | `52e633145bde306fa26f4225c27dea2b4cf2ed3093140a294e2e7d91d0a31664` |
| `S4-Persistent-Runtime-Migration-Summary.txt` | `29ffeee1249c06f0db0c53b7e77deb6da307a26a718545aa68b0ee0b21ac40c4` |
| `S4-Local-Smoke-Seed-v4-Verification.txt` | `187f087eee680c64a4cad771ae0b1a80d641658830463d2ce063bab29463538c` |

## 24.7 Transition evidence

| File | SHA-256 |
|---|---|
| `HRMS-Payroll-Thread-4-Living-Checkpoint-27.md` | `6b3a507ab654b880128beacddd4de97ea3caa11e4803438c1965d86d0efd6be7` |
| `HRMS-Payroll-Thread-4-to-5-Handoff.md` | `5005f41273cd5f081712cc3b90b3c9fa36748038f3d69606b608148ce752ffae` |
| `01-sprint-4-full-regression.log` | `d3e265b8b4f474e19cc9c778ba5deed7cfeef1133d7636b06dff63753a9c12cb` |

## 24.8 Checkpoint inventory

Reviewed historical checkpoint set:

- Living Checkpoints 1–17;
- Checkpoint 18 extracted from
  `HRMS-Payroll-Thread-4-Checkpoint-18.zip`;
- Living Checkpoints 19–27;
- associated checkpoint ZIPs and SHA-256 sidecars where present.

Observed checkpoint-maintenance defects:

- several files retained earlier checkpoint numbers in their internal headings;
- some checkpoints accumulated multiple later updates;
- no standalone Living Checkpoint 18 file existed at the Downloads root;
- temporary failure IDs were reused;
- Checkpoint 27 was accurate only for the pre-closure, pre-merge transition and
  was later superseded by the repository.

---

# 25. Reconciliation closure

Thread 4’s implementation mission is complete and merged.

The thread should be recorded as `CLOSED`, with no active file or migration
ownership.

Its durable implementation outcome is the merged Sprint 4 jurisdiction-neutral
statutory foundation. Its unresolved legacy is not missing core Sprint 4
database/API/UI code; it is incomplete automation and authority maintenance:

- no dedicated statutory API integration test;
- no dedicated statutory full-stack CI test;
- incomplete committed manual-smoke evidence;
- stale authority-document baseline metadata;
- unresolved Thread 4 registry row;
- unclear final publication ownership between Thread 4 and Thread 5.

No feature, migration or repository write should be initiated from this
historical thread. Thread 1 should consolidate this reconciliation into the
living repository authority through a separately approved documentation-only
action.
