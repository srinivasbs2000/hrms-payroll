# Thread Reconciliation Record

## Record identity

| Field | Value |
|---|---|
| Record | Thread 3 Reconciliation |
| Required filename | `thread-3-reconciliation.md` |
| Thread | `Payroll System Design - Thread 3` |
| Historical role | Sprint 3 implementation owner and closure/handoff owner |
| Recommended final role | **CLOSED** |
| Repository | `srinivasbs2000/hrms-payroll` |
| Local repository | `C:\dev\hrms-payroll` |
| Mode | Read-only reconciliation; no repository or GitHub write was performed |
| Evidence date | 1 August 2026, Asia/Kolkata |

## Evidence labels

| Label | Meaning |
|---|---|
| VERIFIED — REMOTE | Confirmed from GitHub repository, commit, PR, workflow or committed file. |
| VERIFIED — THREAD ARTIFACT | Confirmed from Thread 3 checkpoints/handoffs but not independently visible as current local state. |
| THREAD-RECORDED — LOCAL | Reported from the user's local checkout during Thread 3. |
| DERIVED | Direct conclusion from identified verified evidence. |
| DOCUMENTATION CONFLICT | Current authority sources disagree or are stale. |
| NOT VERIFIED | Required evidence was unavailable and no assumption was made. |

## 1. Authority-file validation

The uploaded thread-start command requires reconciliation-only mode for a historical thread, no implementation or Git write, explicit conflicts, `NOT VERIFIED` markings, and a downloadable file named `thread-3-reconciliation.md`.

| Authority file | Current `main` result | Evidence/status |
|---|---|---|
| `AGENTS.md` | Present; blob `7c7eb8407404679cadb384beea51626d08209565` | VERIFIED — REMOTE |
| `docs/design/hrms-payroll-master-design.md` | Present; blob `96fa55c6f9e5b1a7071f728fb415752e086ee0c8` | VERIFIED — REMOTE |
| `docs/design/decision-register.md` | Present; blob `db513793c7f1513d18b91edee4aefde152163c10` | VERIFIED — REMOTE |
| `docs/runbooks/project-continuation-handoff.md` | Present; blob `1dfabb6d18225fbecc671f10c9b71260ad7df58c` | VERIFIED — REMOTE; contains superseded historical sections |
| `docs/governance/thread-registry.md` | Present; blob `af6158895a143c9ea97da9c47b5bd1dc0e975368` | VERIFIED — REMOTE; Thread 3 still listed `NOT VERIFIED` |
| `docs/governance/thread-maintenance-protocol.md` | Present; blob `dcc725e1eaf0acb9751d925d778b2cc193778068` | VERIFIED — REMOTE |
| `docs/templates/thread-checkpoint-template.md` | Present; blob `adb5aaf81dec86818678ae4337029680c5202e60` | VERIFIED — REMOTE |
| `docs/history/thread-2-reconciliation.md` | Present | VERIFIED — REMOTE; establishes Thread 2→3 ownership boundary |

### Authority conflict: literal current `main`

The master design and registry identify Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` as the verified baseline. Live GitHub shows `main` is two commits ahead at:

```text
4b5da975eb851434957667bdecf138ea9b43f929
```

That commit merged PR #20, `docs(project): establish living design and thread governance`.

**Classification:** DOCUMENTATION CONFLICT where the documents call `def3dd2...` the literal repository HEAD. `def3dd2...` remains the current product implementation baseline; `4b5da...` is the current repository/governance HEAD.

## 2. Exact thread number, title and historical purpose

### Identity

```text
Thread 3
Payroll System Design - Thread 3
```

### Historical purpose

Thread 3 continued Sprint 3 on the branch and PR inherited from Thread 2. Thread 2 had already implemented the first seven Sprint 3 commits through S3-04A/V026 at head `db644298ab3197a6931cd9c6b8d9875ef30d28c5`.

Thread 3 completed the remaining Sprint 3 scope:

- recalculation application/API completion and negative-path hardening;
- secured React payroll-execution workspace;
- persisted-result draft-payslip UI;
- Keycloak browser authentication and permission boundaries;
- deterministic Playwright browser automation;
- isolated PostgreSQL 17/Keycloak E2E fixture and reset path;
- browser CI gate and sanitised failure evidence;
- scoped React Router advisory policy;
- CI credential-propagation correction;
- final green CI, PR review/closure and merge;
- course correction back to pending Payroll functionality rather than further generic authentication/test-infrastructure work.

Thread 3 was an `IMPLEMENTATION OWNER` while active. Its final role is `CLOSED`.

## 3. Entry and exit source of truth

### 3.1 Entry SOT

| Item | State | Status |
|---|---|---|
| Repository | `srinivasbs2000/hrms-payroll` | VERIFIED |
| Local path | `C:\dev\hrms-payroll` | VERIFIED — THREAD ARTIFACT |
| Sprint 3 base `main` | `84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9` | VERIFIED — REMOTE |
| Meaning of base | Sprint 2 PR #3 merge | VERIFIED — REMOTE |
| Existing branch | `feature/sprint-3-payroll-execution` | VERIFIED — REMOTE |
| Existing PR | #18 targeting `main` | VERIFIED — REMOTE |
| Thread 2 handoff head | `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | VERIFIED — repository history |
| Entry migration baseline | V026 | VERIFIED |
| Entry CI | `payroll-baseline` run 55 success | VERIFIED — repository historical record |
| Entry local tree/index | **NOT VERIFIED** |
| Exact normal persistent DB state at entry | **NOT VERIFIED** |

### 3.2 Exit SOT

| Item | State | Status |
|---|---|---|
| Historical branch | `feature/sprint-3-payroll-execution` | VERIFIED — PR metadata |
| PR | #18, closed and merged | VERIFIED — REMOTE |
| Final feature head | `ebd2603d91551c6f9e60dc57e2d3500948015703` | VERIFIED — REMOTE |
| Merge commit | `73c356662b1888194a72c7006a66bd91443550ca` | VERIFIED — REMOTE |
| Final CI | `payroll-baseline` run 63, ID `30123496514`, success | VERIFIED — REMOTE |
| PR size | 15 commits, 107 changed files | VERIFIED — REMOTE |
| Migration baseline | V001–V026 committed; V023–V026 immutable after merge | VERIFIED |
| Current remote existence of historical branch | **NOT VERIFIED** |
| Post-merge local branch/tree/index | Reported `main`/clean during Thread 3; current state **NOT VERIFIED** |
| Exact current persistent DB state | **NOT VERIFIED** |
| Isolated E2E environment | Prepared and verified with synthetic data | VERIFIED — THREAD ARTIFACT |
| Normal persistent PostgreSQL volume during isolated smoke | Preserved/not reset | VERIFIED — THREAD ARTIFACT |

### 3.3 Commit ranges

Full Sprint 3 PR:

```text
84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9
..
ebd2603d91551c6f9e60dc57e2d3500948015703
```

Topology: 15 commits ahead, 0 behind, merge base `84530e1...`.

Thread 3 continuation after Thread 2 handoff:

```text
db644298ab3197a6931cd9c6b8d9875ef30d28c5
..
ebd2603d91551c6f9e60dc57e2d3500948015703
```

Topology: 8 commits ahead, 0 behind.

### 3.4 First and last relevant commits

| Position | Commit | Meaning | Status |
|---|---|---|---|
| Inherited entry head | `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | S3-04A/V026 foundation inherited from Thread 2 | VERIFIED |
| Exact first Thread 3-authored commit after `db644...` | **NOT VERIFIED** from currently retrieved commit metadata | NOT VERIFIED |
| Earliest independently retrieved Thread 3 closure commit | `558b2de2f12e846c3f8c2cc4cd684cf30af3a349` — `docs(sprint-3): align closure evidence and smoke runbook` | VERIFIED |
| Browser suite | `af72a41396ea7857eb2fd1778a285367530b9806` — `test(e2e): add deterministic payroll browser suite` | VERIFIED |
| Browser CI gate | `dda03c14f2f5824463f41b40283e06ec1c149da5` — `ci(e2e): add payroll browser regression gate` | VERIFIED |
| Final Thread 3 commit | `ebd2603d91551c6f9e60dc57e2d3500948015703` — `fix(ci): propagate payroll E2E credentials` | VERIFIED |
| Merge | `73c356662b1888194a72c7006a66bd91443550ca` | PR #18 merge | VERIFIED |

## 4. Branch creation process

### Exact method established by repository topology

After Sprint 2 merge:

1. `main` was synchronized to exact Sprint 2 merge SHA `84530e1...`.
2. Sprint 3 branch was created from that exact `main`.
3. Branch name: `feature/sprint-3-payroll-execution`.
4. One PR targeted `main`: PR #18.
5. No duplicate Sprint 3 PR was used.
6. PR remained unmerged until explicit user authorization.

The exact original shell command transcript is **NOT VERIFIED**. Branch base, merge base and zero-behind topology prove the resulting origin.

### Clean-tree and synchronization controls

Binding process:

- fetch remote state;
- switch to `main` and fast-forward only;
- verify exact expected base SHA;
- verify empty index and clean tree;
- create/switch the feature branch from exact `main`;
- verify branch and HEAD before applying changes.

### Draft policy

- Historical policy required an incomplete sprint PR to remain draft.
- Sprint 3 closure evidence at `558b2...` records PR #18 as open, draft, mergeable and unmerged.
- Exact initial PR-creation command and initial draft flag are **NOT VERIFIED**.
- PR #18 was non-draft at merge.

### Verification before push

- exact branch and expected HEAD;
- exact dirty-file allow-list;
- no pre-existing staged files;
- `git diff --check`;
- focused backend tests;
- Failsafe-aware Maven verification;
- migration/RLS/privilege tests;
- frontend lint, tests and production build;
- OpenAPI validation;
- dependency/security checks;
- exact staged scope;
- explicit user authorization.

### Verification before merge

- exact reviewed PR head unchanged;
- all checks green;
- browser E2E green;
- mergeable against `main`;
- V001–V026 unchanged;
- clean local tree/index;
- separate explicit merge authorization;
- no implied feature-branch deletion.

## 5. Sprint 3 architecture and story decisions

### 5.1 Cycle and population

- regular payroll cycles only;
- exact pay-group, pay-period and executable configuration lineage;
- controlled lifecycle and version;
- immutable population-resolution attempts;
- included/excluded member decisions with evidence;
- cross-tenant employees excluded;
- stale/lifecycle-invalid writes rejected.

### 5.2 Input snapshot

- seal inputs before calculation;
- capture exact version IDs, not only stable business IDs;
- retain employee, assignment, pay-group and salary-structure lineage;
- canonical values, per-snapshot hashes and combined input-set hash;
- append-only snapshots;
- drift produces new evidence, not mutation.

### 5.3 Deterministic calculation

- fixed monthly regular payroll;
- BASIC, HRA and SPECIAL_ALLOWANCE approved scope;
- calendar-day proration;
- no statutory deduction in Sprint 3;
- deterministic ordering, exact decimal handling and persisted request identity;
- persisted employee result, component result and calculation trace.

### 5.4 Result and trace

- request/result/component/trace committed atomically;
- immutable historical evidence;
- input and salary-structure lineage retained;
- component-level explanation;
- no partial success represented as complete payroll evidence.

### 5.5 Recalculation

- separate controlled attempt;
- mandatory reason;
- expected cycle version/optimistic concurrency;
- idempotent request handling;
- new active result supersedes prior active result atomically;
- prior results remain immutable/readable;
- stale actions return conflict rather than false success.

### 5.6 Draft payslip

- real view from persisted calculation evidence;
- earnings/deductions/gross/net reconciliation;
- employee, assignment, snapshot, structure and trace lineage;
- explicit warning:

```text
DRAFT · NOT FOR PAYMENT · NOT A LEGAL PAYSLIP
```

A legally publishable final payslip was excluded. A separate standalone immutable document aggregate at the Thread 3 boundary is **NOT VERIFIED**; the verified implementation is a persisted-result draft-payslip view.

### 5.7 API scope

Affected contracts:

```text
contracts/openapi/payroll-operations-openapi-v1.yaml
contracts/openapi/payroll-calculation-openapi-v1.yaml
contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
```

Verified capabilities:

- create/read payroll cycle;
- resolve/read population;
- seal/read input snapshots;
- execute initial calculation;
- execute controlled recalculation;
- read calculation attempts, summaries, details, components and traces;
- read historical/superseded evidence.

The literal complete path and operation-ID inventory was not re-parsed and is **NOT VERIFIED** in this record.

### 5.8 Permissions

Payroll operations:

```text
payroll-cycle.read
payroll-cycle.create
payroll-cycle.population.resolve
payroll-cycle.inputs.read
payroll-cycle.inputs.seal
```

Calculation/result:

```text
payroll-calculation.execute
payroll-calculation.recalculate
payroll-result.read
payroll-result.trace.read
```

### 5.9 UI scope

- Keycloak sign-in/sign-out and session restoration;
- tenant/user context;
- permission-aware navigation;
- `/payroll-execution` workspace;
- cycle selection/creation;
- population resolution;
- input sealing;
- calculation and recalculation;
- lifecycle/version feedback;
- stale-version conflict display;
- result totals/history;
- persisted draft-payslip detail;
- administrator trace visibility;
- read-only user with no write controls.

### 5.10 Immutability, idempotency and concurrency

| Concern | Binding decision |
|---|---|
| Historical evidence | Append-only |
| Corrections | New evidence linked to prior evidence |
| Idempotency | Exact replay cannot duplicate business effect, audit or outbox |
| Concurrency | Expected-version/`If-Match` controls |
| Recalculation | New attempt; never update old result in place |
| Active result | Atomic replacement |
| Tenant isolation | Composite tenant FKs plus ENABLE/FORCE RLS |
| Runtime role | Non-owner `NOBYPASSRLS` |
| Tenant context | Transaction-scoped `SET LOCAL app.tenant_id` |

### 5.11 Explicit exclusions

- statutory deductions and tax;
- retro and off-cycle payroll;
- recoveries;
- final settlement;
- banking/payment files;
- accounting/GL;
- legal/final payslip publication.

## 6. Migrations and story mapping

| Migration | Responsibility | Historical mapping | Thread 3 relationship | Current status |
|---|---|---|---|---|
| V023 | Cycle/population resolution | S3-01 | Inherited from Thread 2; Thread 3 completed visible path/E2E | Merged; immutable |
| V024 | Immutable input sealing | S3-02 | Inherited; completed UI/E2E | Merged; immutable |
| V025 | Deterministic starter calculation | Spans historical S3-03/04/05 naming | Inherited; completed UI/payslip/E2E | Merged; immutable |
| V026 | Controlled recalculation/supersession | Historical S3-04A; conflicts with later backlog numbering | Entry foundation; Thread 3 completed application/closure | Merged; immutable |

**Permanent mapping rule:** use migration, capability, commit and PR; story number alone is insufficient where historical labels diverged.

## 7. Flyway and persistent-target policy

### Runtime Flyway

Spring Boot runtime Flyway remains disabled because:

- runtime role is `payroll_app`;
- migration role is `payroll_migrator`;
- application startup must not have DDL/schema-owner authority;
- persistent schema change is an explicit deployment action;
- restart must not silently mutate schema;
- migration failure must remain visible and separate from runtime startup.

### Local migration

Persistent local migration is an explicit action as `payroll_migrator`. `payroll_app` remains a non-owner, `NOBYPASSRLS` runtime role.

### PostgreSQL 17

PostgreSQL 17 is required across Testcontainers, CI, persistent Compose and E2E Compose. Another major version is not equivalent evidence.

### Testcontainers versus persistent Compose

| Target | Proves | Does not prove |
|---|---|---|
| Fresh Testcontainers PG17 | Clean V001→latest, schema assertions, RLS, privileges, immutability | Existing developer-volume upgrade |
| Upgrade Testcontainers | Selected historical upgrade paths | Actual persistent local state |
| Persistent Compose | Real local roles, credentials, Flyway history and volume | Isolation from local drift |
| Isolated E2E Compose | Deterministic synthetic full-stack behavior | Persistent-volume parity |

Binding rule: Testcontainers success is necessary but does not prove persistent Compose parity.

### Volume preservation

- normal persistent PostgreSQL volume must not be deleted for routine verification;
- defects must not be hidden by database recreation;
- normal shutdown preserves the volume;
- isolated E2E volume is separate and disposable;
- Thread 3 used isolated E2E after placeholder credentials were found, preserving the normal volume.

### Seed and correction

- versioned migrations are not browser-test seed containers;
- E2E fixtures live outside Flyway and never use `V` prefixes;
- deterministic synthetic UUIDs/data only;
- no real employee, salary or credential data;
- fixtures may not weaken RLS or constraints;
- existing migrations are never rewritten for correction;
- persistent correction is forward-only or separately approved.

## 8. Testing and automation

### API integration patterns present

Verified tests included:

```text
PayrollPopulationResolutionMigrationIT
RowLevelSecurityIT
PayrollOperationsApiIT
PayrollRecalculationApiIT
PayrollOperationsHttpSupportTest
PayrollCalculationHttpSupportTest
```

Patterns covered migrated PostgreSQL, tenant context, lifecycle success/failure, idempotent replay, optimistic conflict, cross-tenant denial, immutable history, audit/outbox and direct database assertions.

Whether every API IT used a separately bound external network port rather than an in-process Spring HTTP mechanism is **NOT VERIFIED**.

### Browser E2E

- Playwright 1.61.1;
- real Keycloak browser redirects;
- admin and read-only projects;
- deterministic isolated fixture;
- cycle/population/seal/calculate/draft-payslip/recalculate/stale-version path;
- token-storage and unexpected-response checks;
- one worker;
- zero retries;
- sanitised failure artifacts only.

### Manual-smoke boundary

Initial Thread 3 state treated manual smoke as a pre-merge gate. The binding course correction is:

- deterministic Playwright is the routine acceptance gate;
- do not add a duplicate manual blocker for the same path;
- manual testing is reserved for exploratory UX, visual/usability, accessibility, production acceptance or unresolved automation differences;
- manual smoke never substitutes for missing automated API/browser coverage.

### Automation carried into Sprint 4+

Each new executable Payroll capability must add deterministic fixtures, backend tests, migration/RLS/privilege tests, API IT, frontend tests where applicable, Playwright coverage, permission-negative tests, idempotency/concurrency tests, full historical regression and CI enforcement.

### Definition of done

1. approved acceptance criteria implemented completely;
2. forward-only migration when required;
3. prior migrations unchanged;
4. fresh/upgrade migration and Flyway validation pass;
5. RLS, tenant FKs, ownership, grants and immutability pass;
6. focused unit and integration tests pass;
7. Failsafe phases visibly execute;
8. Maven `verify` passes;
9. frontend lint/test/build pass when affected;
10. OpenAPI validation passes when affected;
11. dependency, secret, security and SBOM gates pass;
12. browser E2E covers visible behavior;
13. final diff reviewed;
14. high-risk changes receive independent critical review under current governance;
15. residual risks documented;
16. no unrelated changes;
17. no Git/PR write without explicit authorization.

## 9. Failures, root causes and permanent controls

| ID | Failure | Root cause | Permanent control | Repository reach |
|---|---|---|---|---|
| T3-001 | React Router 7.11.0 downgrade remained unsafe | npm's suggested fix considered one advisory range while 7.11.0 carried an older high advisory | Retain 7.18.1; exact fail-closed policy; Router 8 handled with React 19 separately | Code, CI, handoff |
| T3-002 | Raw audit blocked on GHSA-qwww-vcr4-c8h2 although app did not use RSC | Package scanner lacked app-mode awareness | Exact advisory/source/range checks plus source/dependency architecture guards and expiry | Code, CI, docs |
| T3-003 | Windows `spawnSync npm.cmd` failed | `.cmd` not invoked through `cmd.exe`/`ComSpec` | OS-specific invocation and self-tests | Policy code |
| T3-004 | Sanitizer matched `[REDACTED]` and retained unsafe embedded HTML | Replacement marker and original report embedding were not handled safely | Standalone sanitised summary; exclude original HTML; strip network/auth; final scan | E2E scripts/docs |
| T3-005 | Browser CI lacked admin/smoke password variables | Reset-step environment did not propagate to Playwright step | Mask and export through `$GITHUB_ENV` | Final CI commit |
| T3-006 | CI run 62 failed; run 63 passed | Workflow environment defect | Treat workflow environment as tested code; preserve exact final evidence | CI/history |
| T3-007 | Preflight rejected `PAYROLL_APP_PASSWORD=change-me` | Placeholder remained in ignored `.env` | Reject placeholders; generate local-only values; never print/commit | Thread artifact/runbook |
| T3-008 | Normal persistent credentials risked mismatch with existing DB roles | Volume could retain earlier passwords | Use isolated E2E smoke; preserve normal persistent volume | E2E process |
| T3-009 | Retries could hide first failure | State-mutating cycle would already be advanced | One worker, zero retries, fresh reset | Playwright/strategy |
| T3-010 | GitHub integration merge returned 403 | Integration lacked merge permission | User-authenticated CLI with exact-head/check guards | Handoff process |
| T3-011 | Manual smoke duplicated Playwright and displaced Payroll roadmap | Conservative gate outlived its purpose | Automated acceptance routine; manual only when non-duplicative | Thread decision |
| T3-012 | Packages/scripts risked HEAD mismatch | Intermediate repository state changed | Exact branch/HEAD/file guards; stop on mismatch | Process practice |
| T3-013 | PR body/handoff became stale after later commits and CI | State not updated after each durable transition | Living handoff update after commit/CI/transition | Current AGENTS/protocol |
| T3-014 | Story labels diverged from backlog | Slice numbering reused | Identify by migration/capability/SHA/PR | Historical reconciliation rule |
| T3-015 | Raw trace/auth state could expose tokens | Playwright evidence contains sensitive data | Never upload `.auth` or raw trace/network data | CI/E2E policy |
| T3-016 | Persistent target state was under-recorded | Focus remained on CI and isolated E2E | Future handoffs record Flyway history, seed, volume, branch and index separately | Protocol partially covers; history missing |

## 10. Checkpoints and handoff execution

### Checkpoints/artifacts created

- S3-09C closure checkpoint;
- Sprint 3 merge-readiness checkpoint;
- core Payroll implementation resumption plan;
- manual-smoke execution pack;
- isolated manual-smoke environment preparation pack;
- guarded PR #18 merge/main-sync package;
- Thread 3 comprehensive handoff and starter prompt;
- post-merge checklist;
- GHSA-qwww-vcr4-c8h2 consistency handoff and cross-thread checklist.

### Local deployment context carried forward

```text
Repository: C:\dev\hrms-payroll
Persistent PostgreSQL: normally 127.0.0.1:15432
Keycloak: http://localhost:8081
Backend: http://localhost:8080
Frontend: http://localhost:5173
E2E project: hrms-payroll-e2e
E2E PostgreSQL: 127.0.0.1:25432
E2E tenant: E2E001
E2E tenant ID: 00000000-0000-0000-0000-000000000001
E2E users: payroll.admin, payroll.smoke
```

Credential values were intentionally not carried forward.

### Missing/stale handoff information

- exact first Thread 3-authored commit after `db644...`: **NOT VERIFIED**;
- exact original branch-creation commands: **NOT VERIFIED**;
- initial PR creation draft flag: **NOT VERIFIED**;
- exact normal persistent DB Flyway history at Thread 3 exit: **NOT VERIFIED**;
- detailed manual-smoke cycle/request/result/hash/screenshot evidence absent;
- PR #18 body stale at merge: cites run 59/intermediate SHA, while final head passed run 63;
- protected-branch requirement for `Payroll browser E2E`: **NOT VERIFIED**;
- current local branch/tree/index: **NOT VERIFIED**;
- current thread/migration write owner after governance bootstrap: **NOT VERIFIED**.

## 11. Conflicts

### Current repository

| Conflict | Classification | Resolution for this record |
|---|---|---|
| Authority docs say `main` baseline `def3dd2...`; live HEAD is `4b5da...` | DOCUMENTATION CONFLICT | Use `4b5da...` as repository HEAD and `def3dd2...` as product implementation baseline |
| Registry lists Thread 3 `NOT VERIFIED` | DOCUMENTATION CONFLICT | Proposed corrected row below; no repository update performed |
| Running handoff preserves pre-merge PR #19 state before later superseding section | DOCUMENTATION CONFLICT | Use later superseding section and live GitHub |
| PR #18 body cites run 59/intermediate SHA | DOCUMENTATION CONFLICT/stale metadata | Use final head `ebd2603...` and run 63 |

### Master design and decision register

No material product/architecture conflict was found. Thread 3 aligns with the current master design and MDR-006 through MDR-013, MDR-015, MDR-017 and MDR-020.

The temporary React Router exception is implemented and documented but has no dedicated MDR row. Whether to add one is a future documentation decision.

### Other threads

- Thread 2 owned Sprint 3 through `db644...`.
- Thread 3 owned the remaining eight commits and merge closure.
- Sprint 4 superseded Thread 3's “next migration V027” state.
- No active historical ownership remains for Thread 2 or Thread 3.

## 12. Work superseded by later implementation

| Thread 3 statement/artifact | Current state |
|---|---|
| Next migration V027 | Superseded; V027–V030 merged, next is V031 subject to reservation |
| Statutory deductions excluded | Partly superseded: jurisdiction-neutral statutory infrastructure implemented; country-specific legal rules remain excluded |
| `scripts/verify-sprint-3.ps1` as full baseline | Historical only; current full baseline is Sprint 4 verification |
| Thread 3 README/AGENTS scope | Superseded by Sprint 4 and governance updates |
| Thread 3 handoff as authority | Superseded by living master design, decision register, running handoff and registry |
| PR #18 open/unmerged | Superseded; merged |
| `main` at `73c356...` | Superseded; product baseline `def3dd2...`, repository HEAD `4b5da...` |

## 13. Deferred debt into Sprint 4 and current repository

### Functional debt

- country-specific statutory rule packs and legal interpretation;
- statutory returns, acknowledgements and settlement;
- retro payroll;
- off-cycle/supplementary payroll;
- recoveries and salary advances;
- final settlement;
- bank/payment processing;
- accounting/GL;
- legal/final payslip publication.

### Platform/quality debt

- React 19 + React Router 8 migration;
- router exception review/removal by 2026-10-31;
- protected-branch browser-check enforcement: **NOT VERIFIED**;
- automated persistent-volume migration parity;
- accessibility, selected visual regression and scheduled cross-browser coverage;
- load/endurance testing;
- cached/scheduled OWASP Dependency Check data;
- production broker replay/alerting;
- current local environment checkpoint: **NOT VERIFIED**.

### Documentation debt

- publish this record to `docs/history/` after authorization;
- replace Thread 3 registry row;
- resolve literal current-HEAD wording in authority docs;
- consider dedicated decision-register entry for the router exception;
- record current post-Sprint-4 active ownership.

## 14. Assumptions future threads must not make

1. Chat memory is not repository truth.
2. `def3dd2...` is not the literal current `main`; live HEAD is `4b5da...` at this evidence cut-off.
3. Thread 3 has no current write ownership or migration reservation.
4. V027 is not available; next migration is V031 subject to registry reservation.
5. PR #18 and PR #19 are merged.
6. Historical feature-branch existence is not assumed.
7. Current local branch/index/tree/persistent DB are not assumed.
8. Manual smoke does not replace missing automated API/browser coverage.
9. React Router must not be downgraded to 7.11.0.
10. The scoped router exception cannot be copied blindly to another repository.
11. Country-neutral statutory infrastructure is not an India legal rule pack.
12. V001–V030 are immutable.
13. V031/overlapping files require registered ownership before writes.
14. No stage, commit, push, PR update or merge without explicit authorization.
15. Never upload `.env`, credentials, tokens, Playwright `.auth` or raw traces.

## 15. Recommended final thread role

```text
CLOSED
```

Reason: PR #18 is merged, Thread 3's implementation scope is complete and later work has superseded its transition state. It holds no active file ownership or migration reservation.

## 16. Proposed complete row for `docs/governance/thread-registry.md`

```markdown
| Thread 3 | CLOSED — former Sprint 3 IMPLEMENTATION OWNER | Completed remaining Sprint 3 regular-payroll execution after Thread 2 handoff: recalculation application/negative paths, secured React execution and persisted draft payslip, Keycloak browser auth, deterministic Playwright E2E, CI gate, sanitised evidence and scoped React Router policy; PR #18 merged | `feature/sprint-3-payroll-execution`; PR #18 merged; final head `ebd2603d91551c6f9e60dc57e2d3500948015703`; merge `73c356662b1888194a72c7006a66bd91443550ca` | None | `docs/history/thread-3-reconciliation.md` after authorised publication | Thread 1 consolidates this record into living authority documents; no implementation work |
```

Additional registration fields:

| Field | Value |
|---|---|
| Approved scope | Historical reconciliation only |
| File allow-list | None |
| Migration reservation | `NONE` |
| Verification | Read-only authority/PR/commit/CI evidence |
| Blocker | Current local tree/index/persistent state unavailable |
| Prohibited actions | No repository writes, branch/PR changes, migration reservation, stage, commit, push or merge |

## 17. One recommended next authorised action

Thread 1 should review this record against live `main` and, only after explicit authorization, publish it as `docs/history/thread-3-reconciliation.md` and replace the Thread 3 `NOT VERIFIED` registry row with the proposed `CLOSED` row.

No implementation branch or migration reservation should be created as part of that action.

## 18. Separate execution status

| State/action | Status |
|---|---|
| Working tree | **NOT VERIFIED** |
| Git index | **NOT VERIFIED** |
| Repository files modified | **NO** |
| Branch created/switched | **NO** |
| Migration reserved | **NO** |
| Commit created | **NO** |
| Push performed | **NO** |
| PR metadata updated | **NO** |
| Merge performed | **NO** |
| Implementation begun | **NO** |
| Downloadable reconciliation generated outside repository | **YES** |

## 19. Decision table

| Decision | Thread 3 ruling | Current state |
|---|---|---|
| Branch base | Exact Sprint 2 merged `main` | Durable process |
| One sprint branch/PR | Required | Durable process |
| Git writes | Explicit authorization only | Current authority |
| Migrations | V001–V026 immutable after Sprint 3 | Superseded baseline now V001–V030 immutable |
| Runtime Flyway | Disabled | Current authority |
| Local Flyway | Explicit `payroll_migrator` action | Current authority |
| PostgreSQL | Version 17 | Current authority |
| Persistent volume | Preserve; do not delete for routine correction | Current authority |
| Calculation | Fixed monthly regular, deterministic, exact-decimal | Current master design |
| Recalculation | New immutable attempt and supersession | Current master design |
| Draft payslip | Persisted evidence, non-legal/non-payment | Current master design |
| Browser E2E | Permanent capability, extend with new visible paths | Current strategy |
| Manual smoke | Non-duplicative only | Thread 3 course correction |
| Router | Stay on 7.18.1 with fail-closed exception | Current code/handoff |
| Router 7.11.0 | Rejected | Current policy |
| Router 8 | Separate React 19 migration | Deferred |
| Generic testing work | Must not displace Payroll roadmap | Binding course correction |

## 20. Exact current-handoff delta

| Dimension | Thread 3 exit | Current verified state | Delta |
|---|---|---|---|
| Repository `main` | `73c356662b1888194a72c7006a66bd91443550ca` after PR #18 merge | `4b5da975eb851434957667bdecf138ea9b43f929` | Sprint 4 plus governance PR #20 merged |
| Product baseline | Sprint 3 | Sprint 4 merge `def3dd2...` | Statutory evidence foundation added |
| Migrations | V001–V026 | V001–V030 | Next is V031 subject to reservation |
| PR #18 | Merged | Merged | No change |
| PR #19 | Not created at Thread 3 exit | Merged at `def3dd2...` | Sprint 4 complete |
| CI | Run 63 success | Sprint 3 historical run 63 remains valid; current main-head CI not returned | Current CI **NOT VERIFIED** |
| Registry | No living registry existed | Thread 3 listed `NOT VERIFIED` | This record proposes `CLOSED` row |
| Local tree/index | Reported clean/main | Current state unavailable | **NOT VERIFIED** |
| Persistent DB | V026 expected; exact state under-recorded | Current state unavailable | **NOT VERIFIED** |
| Statutory scope | Excluded | Country-neutral V027–V030 implemented | Country-specific legal rules still excluded |
| Router policy | Scoped 7.18.1 exception | Same current policy | Review due 2026-10-31 |

## 21. Exact source and evidence inventory

### Uploaded command

- `thread-start-prompt(3).md` — reconciliation requirements and read-only constraints.

### Current authority files

- `AGENTS.md` — blob `7c7eb8407404679cadb384beea51626d08209565`.
- `docs/design/hrms-payroll-master-design.md` — blob `96fa55c6f9e5b1a7071f728fb415752e086ee0c8`.
- `docs/design/decision-register.md` — blob `db513793c7f1513d18b91edee4aefde152163c10`.
- `docs/runbooks/project-continuation-handoff.md` — blob `1dfabb6d18225fbecc671f10c9b71260ad7df58c`.
- `docs/governance/thread-registry.md` — blob `af6158895a143c9ea97da9c47b5bd1dc0e975368`.
- `docs/governance/thread-maintenance-protocol.md` — blob `dcc725e1eaf0acb9751d925d778b2cc193778068`.
- `docs/templates/thread-checkpoint-template.md` — blob `adb5aaf81dec86818678ae4337029680c5202e60`.
- `docs/history/thread-2-reconciliation.md` — current `main` historical ownership evidence.

### Live GitHub evidence

- Current `main`: `4b5da975eb851434957667bdecf138ea9b43f929`.
- `def3dd2...` to `main`: ahead by 2, behind by 0.
- PR #18: closed/merged; base `84530e1...`; head `ebd2603...`; merge `73c356...`.
- PR #18 run 63: success, ID `30123496514`.
- PR #18 exact changed-file inventory: 107 paths.
- Thread 3 compare `db644...` to `ebd2603...`: 8 commits ahead, 0 behind.
- PR #19: closed/merged; base `73c356...`; head `b2a220...`; merge `def3dd2...`.
- Current main-head PR workflow list: none returned.
- Current main combined status list: none returned; current CI therefore **NOT VERIFIED**.

### Retrieved commits

- `558b2de2f12e846c3f8c2cc4cd684cf30af3a349` — closure evidence/runbook alignment.
- `af72a41396ea7857eb2fd1778a285367530b9806` — deterministic browser suite.
- `dda03c14f2f5824463f41b40283e06ec1c149da5` — browser CI gate.
- `ebd2603d91551c6f9e60dc57e2d3500948015703` — CI credential propagation.
- `4b5da975eb851434957667bdecf138ea9b43f929` — living design/thread-governance merge.

### Permission sources

- `PayrollOperationsPermissions.java` at Sprint 3 merge — blob `c8e123a8da01c9e0b3c409e7301c47ee87f19590`.
- `PayrollCalculationPermissions.java` at Sprint 3 merge — blob `83132e400df579c7549694399837f4d1ee8c0f6d`.

### Thread artifacts used as historical locators

- S3-09C closure and merge-readiness checkpoints;
- run 62 diagnosis and credential fix;
- run 63 green confirmation;
- manual preflight and isolated-E2E preparation;
- merge authorization/fallback package;
- core Payroll resumption plan;
- React Router consistency handoff.

Thread artifacts do not override current repository evidence.

## Final reconciliation conclusion

Thread 3 completed the latter eight commits of Sprint 3 after the Thread 2 handoff, secured and automated the regular-payroll execution path, and merged PR #18. Its implementation is durable and merged; its transition state has been superseded by Sprint 4 and the living-governance bootstrap. Thread 3 should be registered **CLOSED**, with no write ownership and no migration reservation.
