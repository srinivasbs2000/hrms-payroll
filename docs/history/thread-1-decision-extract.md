# Thread 1 Decision Extract

**Project:** HRMS Payroll  
**Extraction type:** Historical decision record, not a conversational summary  
**Evidence cutoff represented by Thread 1 exit:** 22 July 2026  
**Evidence rule:** Unsupported or incomplete points are marked **NOT VERIFIED**. No repository state has been modified.

---

## 0. Evidence classification

| Label | Meaning |
|---|---|
| **VERIFIED — THREAD** | Directly supported by the exported `Payroll System Design - Thread 1.txt` material. |
| **VERIFIED — REPORT** | Supported by a Sprint integration report, CI report, build log or verification evidence supplied with the Thread 1 materials. |
| **VERIFIED — HANDOFF** | Supported by `HRMS_Payroll_Thread_Handoff_Scoping_to_Implementation.md/.docx` or `HRMS_Payroll_Continuation_Prompt.txt`. |
| **THREAD-RECORDED — LOCAL** | Reported for the user's local working tree at the end of Thread 1 but not present in the remote branch/PR checkpoint. |
| **SUMMARY-LOCATOR** | Recalled from the conversation summary and used only to locate a decision; it is not treated as repository proof. |
| **NOT VERIFIED** | The supplied evidence does not establish the point precisely enough. |

### Source precedence recovered from Thread 1

1. Inspect the local working tree and uncommitted diff before making implementation claims.
2. Use the remote repository, branch and PR as the last committed checkpoint.
3. Use migrations, tests, OpenAPI, backlog, ADRs, README and `AGENTS.md` for exact contracts.
4. Use handoffs for sequence and context, not to override contradictory current files.
5. Mark unavailable facts **NOT VERIFIED** rather than inventing names, routes, constraints, commands or test results.

---

# 1. Verified thread checkpoint at entry and exit

## 1.1 Design-entry checkpoint

Thread 1 began as product scoping and detailed design. At that earliest point:

| Item | Recovered state |
|---|---|
| Repository SHA | **NOT VERIFIED** — the earliest design exchanges preceded the runnable repository checkpoint. |
| Feature branch | **NOT VERIFIED** |
| Pull request | **NOT VERIFIED** |
| Migrations | The later vertical-slice package introduced V001–V012, but the exact Git state at the first design message is **NOT VERIFIED**. |
| CI | **NOT VERIFIED** |

The design-entry baseline was product intent rather than a Git checkpoint: an India-first, enterprise-grade payroll operating platform, implemented as an independent payroll subsystem integrated with the wider HRMS.

## 1.2 Implementation-entry checkpoint after Sprint 0 closure

This is the first reliable repository checkpoint recovered from the supplied reports.

| Item | Verified value | Evidence status |
|---|---|---|
| Repository | `srinivasbs2000/hrms-payroll`; local path `C:\dev\hrms-payroll` | VERIFIED — REPORT/HANDOFF |
| Main SHA | `bba8a51d17147443e400f51bc9ccf769b8bd1af8` | VERIFIED — REPORT |
| Branch involved in CI repair | `ci/sprint-0-baseline-repair` | VERIFIED — REPORT |
| Main/repair relationship | Both `main` and `ci/sprint-0-baseline-repair` were reported at `bba8a51`; the repair branch was not merged through a PR | VERIFIED — REPORT |
| First recovered CI-repair commit | `51ee76e` — `fix(ci): make Maven wrapper executable` | VERIFIED — REPORT |
| Last recovered CI-repair commit | `bba8a51` — `fix(ci): allow cold dependency database bootstrap` | VERIFIED — REPORT |
| Intermediate commits | `6a0a6ef` — workflow diagnostics; `9d33940` — reactor preparation before dependency scan | VERIFIED — REPORT |
| Migrations present | V001–V013 | VERIFIED — HANDOFF/REPORT |
| Final push workflow | Run `29690533941` — success | VERIFIED — REPORT |
| Final PR workflow | Run `29690535467`, attempt 1 — success; full rerun attempt 2 — success | VERIFIED — REPORT |
| Full original Sprint 0 commit chain | **NOT VERIFIED** — only the later CI-repair sequence is fully recovered here | NOT VERIFIED |

## 1.3 Sprint 1 checkpoint

| Item | Verified value | Evidence status |
|---|---|---|
| Baseline | `bba8a51d17147443e400f51bc9ccf769b8bd1af8` | VERIFIED — REPORT |
| Feature branch | `feature/sprint-1-organisation-foundation` | VERIFIED — REPORT |
| Pull request | PR #2, created as draft | VERIFIED — REPORT |
| First Sprint 1 capability commit | `b9f6bcf` — `feat(integrations): establish event reliability gate` | VERIFIED — REPORT |
| Second commit | `47654eb` — `feat(organisation): add tenant-safe effective-dated foundation` | VERIFIED — REPORT |
| Third commit | `da5dad3` — `feat(web): add organisation setup lifecycle` | VERIFIED — REPORT |
| Last Sprint 1 evidence commit | `59bcea4` — `docs(payroll): record Sprint 1 verification evidence` | VERIFIED — REPORT |
| CI run | `29700681612` — success | VERIFIED — REPORT |
| Final PR disposition | PR #2 merged into `main` | VERIFIED — HANDOFF |
| Merge commit | `27947e1202ff018c3494a32584487ff3879876ab` | VERIFIED — HANDOFF |
| Migrations delivered | V014–V016 | VERIFIED — REPORT/HANDOFF |

## 1.4 Thread 1 exit checkpoint

The end-of-thread remote checkpoint was later than the Sprint 1 merge and represented completed Sprint 2 work through salary structures, plus local-only V021 work.

| Item | Verified value | Evidence status |
|---|---|---|
| Main/base SHA of PR #3 | `42d4b50a8fae64c12ddfc1fcb5553476d86fb252` | VERIFIED — HANDOFF |
| Active feature branch | `feature/sprint-2-payroll-configuration` | VERIFIED — HANDOFF |
| Pull request | PR #3, **open draft**, not merged, reported mergeable | VERIFIED — HANDOFF |
| PR #3 head | `24f2ed4893a90627eb6be69aa3747eba4343e195` | VERIFIED — HANDOFF |
| Latest remote commit | `feat(salary-structure): implement salary structure foundation` | VERIFIED — HANDOFF |
| First Sprint 2 commit | **NOT VERIFIED** — the handoff states 11 commits ahead but does not identify the first one | NOT VERIFIED |
| Remote comparison | 11 commits ahead, 0 behind | VERIFIED — HANDOFF |
| Remote migrations | V001–V020, with V017–V020 added in Sprint 2 | VERIFIED — HANDOFF |
| Remote CI | Workflow `payroll-baseline`, run 29 / ID `29921705101` — success | VERIFIED — HANDOFF |
| Local-only migration | V021 — Employee Payroll Identity and Assignment Foundation | THREAD-RECORDED — LOCAL |
| V021 remote status | Not committed, not part of PR #3 head or remote CI | VERIFIED — HANDOFF |
| V022 | **NOT VERIFIED** — no V022 migration or accepted responsibility is established in the Thread 1 exit evidence | NOT VERIFIED |

### Exit limitation

Remote GitHub proved the project only through V020. Thread 1 recorded a newer local V021 migration and tests, but the exact SQL, object names and complete local diff were not recoverable from the remote checkpoint. Any claim beyond the recorded V021 intent required local inspection.

---

# 2. Branch and pull-request workflow decisions

## 2.1 Sprint 0 branch/process

### Historical process recovered

The CI repair report records:

- creation/use of `ci/sprint-0-baseline-repair`;
- four focused CI-repair commits;
- pushes that ultimately made `main` and the repair branch point to `bba8a51`;
- repeated remote workflow validation;
- no conventional merge of that repair branch.

### Exact historical branch command recovered

```text
git switch -c ci/sprint-0-baseline-repair
```

### Exact historical push forms recovered

```text
git push origin ci/sprint-0-baseline-repair
git push origin HEAD:main
```

These are historical evidence, not present-day instructions.

### Decision

The direct-to-main repair was a baseline-recovery exception, not the later standard workflow. The standard process thereafter became feature branch → draft PR → evidence → review → explicit merge authorization.

### Branch-base verification

The report used branch, log, remote and SHA comparison commands, including `git rev-parse`, `git ls-remote` and diff inspection. The exact ordered branch-base validation procedure used before creating the repair branch is **NOT VERIFIED**.

## 2.2 Sprint 1 branch/process

### Verified branch and PR actions

The Sprint 1 report records:

```text
git push -u origin feature/sprint-1-organisation-foundation
gh pr create --draft --base main --head feature/sprint-1-organisation-foundation --title "Sprint 1: tenant-safe organisation foundation" --body-file .codex-pr-body.md
gh pr checks 2
gh run watch 29700681612 --exit-status --interval 10
```

### Exact branch-creation command

**NOT VERIFIED.** The evidence proves the branch name and baseline SHA but does not preserve the exact `git switch` or `git checkout` command used to create it.

### Branch-base correctness

Verified facts:

- Sprint 1 report declares baseline `bba8a51...`.
- The feature branch was pushed to origin.
- PR #2 targeted `main`.
- CI validated the resulting branch.
- PR #2 was later merged into `main`.

The exact command sequence used to prove “0 behind” or compare the branch point before development is **NOT VERIFIED**.

## 2.3 Sprint 2 branch/process

### Conditions recovered for Sprint 2 branch creation

Sprint 2 was allowed to begin only after:

1. Sprint 1 was complete and merged through PR #2.
2. The merged organisation, tenancy, security, audit and event-reliability foundation was available in `main`.
3. V001–V016 were treated as immutable.
4. Sprint 2 remained bounded to payroll configuration and employee-payroll foundation, not Sprint 3 calculation.
5. The existing Sprint 2 branch and draft PR were reused for all Sprint 2 increments.

### Exact branch-creation command

**NOT VERIFIED.**

### Verified base correctness at the exit checkpoint

- PR #3 base: `main` at `42d4b50...`.
- PR #3 head: `24f2ed48...`.
- Comparison: 11 commits ahead, 0 behind.

This verifies base alignment at that checkpoint, but not the exact commands used when the branch was originally created.

## 2.4 Draft PR policy

The following process decisions were explicit:

- Create the sprint PR as a **draft**.
- Keep one PR per sprint branch; never create a duplicate PR for the same Sprint 2 work.
- Keep PR #3 draft until the entire Sprint 2 scope, final evidence and independent critical review are complete.
- Pushes to the same branch automatically update the existing PR.
- Do not mark ready for review merely because one vertical slice is green.
- Do not merge automatically.
- Merge requires explicit user authorization after all gates and review are green.

## 2.5 Authorization boundaries

| Action | Recovered boundary |
|---|---|
| Edit local files | Allowed only within the bounded approved unit and after inspecting current state. |
| Stage | Do not stage with failing tests, unrelated changes or an unreviewed diff. Show `git status --short` and categorized files first. Explicit user authorization required by the handoff process. |
| Commit | Verification, clean intentional diff and user approval required. |
| Push | User authorization required; do not push failing or unrelated work. |
| Create/update PR | Reuse existing sprint PR. Update only after the local unit is complete, reviewed and verified. |
| Mark PR ready | Not until complete sprint scope, final evidence and critical review are green, with user authorization. |
| Merge | Never automatic; explicit user authorization required. |

## 2.6 Evidence-delivery rule

A generic “tests passed” statement was rejected. Handoffs and completion reports must state:

- exact branch and SHA;
- working-tree/index status;
- exact commands or historical command forms;
- exact test classes/phases and counts where available;
- migration path exercised;
- positive and negative evidence;
- unresolved risks;
- whether any stage, commit, push, PR update or merge occurred.

---

# 3. Sprint 0, Sprint 1 and Sprint 2 design decisions

## 3.1 Sprint 0 responsibility and story mapping

| Story | Responsibility | Recovered outcome |
|---|---|---|
| S0-01 | Freeze approved versions and dependency policy | Established version/dependency baseline. |
| S0-02 | Monorepo, branch protections and CODEOWNERS | Repository foundation created; exact branch-protection state is **NOT VERIFIED**. |
| S0-03 | ADRs for modular monolith, RLS and outbox | Architecture foundation accepted. |
| S0-04 | Migration pipeline and schema verification | Flyway/bootstrap/verification established. |
| S0-05 | Development OIDC realm and tenant claim | Keycloak realm and claims established. |
| S0-06 | Unit, integration, architecture and frontend gates | Build/test quality gates established. |
| S0-07 | Correlation IDs, structured logs and metrics | Correlation/logging foundation established; complete metrics scope is **NOT VERIFIED**. |
| S0-08 | Docker Compose and golden seed | Local infrastructure and synthetic fixtures established. |

### Sprint 0 migration responsibility

| Migration | Responsibility |
|---|---|
| V001 | Schemas, PostgreSQL primitives and shared types |
| V002 | Tenant, security and audit foundations |
| V003 | Original legal entity, PSU and establishment structures |
| V004 | Original calendar, period and pay-group structures |
| V005 | Original pay-component and salary-structure structures |
| V006 | Original payroll relationship, assignment, profile, pay-group assignment and salary assignment |
| V007 | Payroll cycle and operations foundations |
| V008 | Payroll result, component result and calculation trace foundations |
| V009 | Draft-payslip persistence foundation |
| V010 | Original outbox/inbox reliability structures |
| V011 | ENABLE/FORCE RLS and tenant policies |
| V012 | Runtime grants and append-only/immutability controls |
| V013 | Vertical-slice baseline hardening |

### Sprint 0A decisions

- PostgreSQL 17 became the standard baseline.
- Runtime principals had to be non-superuser, non-createdb, non-createrole and non-bypassrls.
- Production configuration had to fail closed without external credentials and identity configuration.
- Real Keycloak-token smoke tests had to validate issuer, audience, tenant and permission claims without printing or persisting the token.
- Local ports were loopback-bound.
- V013 added baseline hardening and verification.

### Sprint 0B decisions

- Add and validate the Payroll Implementation Foundation Pack.
- ADR-004: stable organisation identity plus immutable effective-dated versions.
- ADR-005: immutable input snapshots and append-only draft supersession.
- `AGENTS.md` became the repository governance source for verification order, definition of done and high-risk critical review.
- Commit the baseline before Sprint 1.

## 3.2 Sprint 1 responsibility and story mapping

| Story | Decision and delivered responsibility |
|---|---|
| S1-00 | Prove outbox publication and inbox consumption idempotency before organisation event persistence. |
| S1-01 | Every application repository access runs in a transaction that sets validated tenant context through `SET LOCAL`; PostgreSQL RLS remains the final isolation boundary. |
| S1-02 | Enforce exact OIDC permission authorities and emit tenant/permission claims. |
| S1-03 | Implement tenant-safe legal entity and payroll statutory unit identity/version lifecycles. |
| S1-04 | Implement establishment identity/version lifecycle and exact-version hierarchy. |
| S1-05 | Append actor/correlation/before-after/timestamp audit evidence atomically. |
| S1-06 | Enforce Spring Modulith and ArchUnit module boundaries. |

### Sprint 1 migrations

| Migration | Decision |
|---|---|
| V014 | Harden event reliability: outbox dispatch metadata, inbox retry metadata, tenant-safe dead letters and tenant-owned idempotency. |
| V015 | Convert legacy organisation rows into stable identities plus immutable versions, backfill exact hierarchy, add half-open ranges, non-overlap, RLS and tenant-safe FKs. |
| V016 | Add narrow approval/end-date lifecycle commands and parent range containment; keep direct runtime mutation revoked. |

### Sprint 1 application/API decisions

- API lifecycle included create identity/first draft, list/current/as-of, history, add version, correct eligible future draft, approve and end-date.
- End-date used optimistic concurrency with `If-Match`.
- Every write required `Idempotency-Key`.
- Calls accepted/returned `X-Correlation-ID`.
- Errors used RFC 9457 problem details.
- Aggregate, idempotency result, audit and outbox event committed in one tenant transaction.
- Repository infrastructure remained owned by the organisation module.
- Exact organisation permissions:
  - `organisation.read`
  - `organisation.create`
  - `organisation.version.create`
  - `organisation.version.correct`
  - `organisation.version.end-date`
  - `organisation.approve`
  - `audit.read`

### Sprint 1 UI and browser-security decisions

- React organisation setup supported hierarchy, history and lifecycle actions.
- Controls were permission-sensitive.
- Loading, empty, unauthorised and problem-detail states were explicit.
- Access tokens remained memory-only in `window.payrollSession`.
- Production identity-provider login/session plumbing was deferred; the baseline UI consumed an in-memory token from the host shell.

## 3.3 Sprint 2 responsibility and story mapping

### Canonical backlog mapping

| Story | Canonical capability |
|---|---|
| S2-01 | Monthly payroll calendar and period generation |
| S2-02 | Pay group and proration policy |
| S2-03 | Basic, HRA and Special Allowance component catalogue |
| S2-04 | Salary structure and dependency validation |
| S2-05 | Payroll relationship, assignment and payroll profile |
| S2-06 | Salary and pay-group assignment |

### Documentation conflict

PR #3 used S2-01 for Pay Group and S2-02 for Payroll Calendar, matching migration implementation order V017 then V018. The canonical backlog used the reverse numbering.

**Decision:** Engineering references must use capability names plus migration numbers until the documentation is corrected. The extraction does not choose one numbering as “correct.”

### Sprint 2 migration responsibility

| Migration | Responsibility and status |
|---|---|
| V017 | Pay-group stable identity and immutable effective-dated versions; preserve historical UUIDs as version IDs. Remote PR #3. |
| V018 | Controlled monthly calendar and deterministic 12-period generation. Remote PR #3. |
| V019 | Pay-component identity/version lifecycle, formula invariants, approval metadata and immutability. Remote PR #3. |
| V020 | Salary-structure identity/version lifecycle, exact version lineage, dependency and line-shape controls. Remote PR #3. |
| V021 | Employee Payroll Identity and Assignment Foundation. Local/uncommitted at Thread 1 exit. |
| V022 | **NOT VERIFIED** |

## 3.4 V006-to-V021 upgrade strategy

### Frozen-source decision

- V006 must not be edited.
- V017–V020 must not be rewritten.
- V021 must be forward-only.
- Versioned migrations must fail loudly rather than using permissive `IF NOT EXISTS` clauses to hide drift.

### Legacy V006 source model

| V006 table | Original responsibility |
|---|---|
| `payroll_relationship` | Employee/payroll legal-entity relationship, dates and employee number |
| `payroll_assignment` | Assignment under relationship, establishment and dates |
| `employee_payroll_profile` | Relationship-level profile, INR and payroll status |
| `pay_group_assignment` | Effective-dated pay-group assignment |
| `salary_assignment` | Effective-dated salary-structure assignment, monthly amount and currency |

### V021 upgrade intent

Thread 1 recorded that V021 should:

- preserve existing V006 UUIDs as historical version IDs wherever practical;
- introduce stable employee-payroll identity rows and immutable effective-dated versions;
- reference exact legal-entity and establishment versions;
- align pay-group assignments with exact V017 pay-group versions;
- align salary assignments with exact V020 salary-structure versions;
- preserve downstream lineage rather than rewriting historical references;
- add approval metadata and lifecycle chains;
- add forced RLS and tenant-safe relationships;
- restrict runtime privileges and direct mutation.

### What was not established

The following remained **NOT VERIFIED** at Thread 1 exit:

- exact V021 table names after renames;
- which V006 UUID became stable identity versus exact version for each aggregate;
- exact columns, functions, triggers, grants and permission strings;
- whether relationship, assignment and profile all received separate identity/version tables;
- exact split between S2-05 and S2-06;
- complete negative-test inventory;
- whether any employee-payroll Java, OpenAPI or UI changes were already local.

## 3.5 Preserved UUID and exact-lineage decision

The migration pattern established by V015, V017 and V020 was:

1. Preserve the old UUID as the historical version identifier where practical.
2. Create a new stable identity identifier for the enduring business concept.
3. Make future operational references distinguish:
   - stable identity for navigation and aggregate identity;
   - exact version ID for payroll lineage and historical reconstruction.
4. Do not make old payroll data appear as though it used a newer version.

This pattern was carried explicitly into the V021 design intent.

## 3.6 Effective dating

- Half-open ranges: `[effective_from, effective_to)`.
- Approved ranges for the same identity cannot overlap.
- Parent-child version relationships must use exact versions and range containment.
- Future-effective versions are the normal change path.
- Backdated or retroactive changes require controlled impact assessment and approval.
- Once used in approved payroll, history is immutable.
- Corrections create new versions, controlled corrections, reversals or later adjustments; they do not rewrite prior state.

## 3.7 RLS and runtime-role model

- Every tenant-owned table includes tenant ownership.
- Tenant-safe composite foreign keys include `tenant_id`.
- RLS is both ENABLED and FORCED.
- `payroll_app` is non-owner, non-superuser and `NOBYPASSRLS`.
- Application transactions set `app.tenant_id` through `SET LOCAL` before data access.
- RLS remains the final database isolation boundary.
- Cross-tenant reads, writes and references must fail.

## 3.8 Grants, lifecycle and immutability

- Runtime direct `UPDATE`/`DELETE` on immutable version/history tables is revoked.
- Narrow reviewed lifecycle functions perform approval and end-dating.
- New lifecycle operations require new reviewed commands rather than restoring broad mutation grants.
- Optimistic concurrency uses `version_no`, ETag and `If-Match`.
- Approval/end-date commands must validate predecessor state, tenant, parent status and effective-range compatibility.
- Audit and outbox evidence are part of the same transaction as the lifecycle change.

## 3.9 Sprint 2 application/API/OpenAPI/Keycloak/UI decisions

### V017–V020 remote status

The handoff records that Pay Group, Payroll Calendar, Pay Component and Salary Structure had:

- database migrations;
- backend services/controllers/repositories;
- API integration tests;
- OpenAPI updates;
- Keycloak permission updates;
- React pages and focused tests;
- runbooks for Pay Group, Payroll Calendar and Pay Component;
- consolidated regression utilities.

### Salary Structure exact permission set recovered

- `compensation.structure.read`
- `compensation.structure.create`
- `compensation.structure.version.create`
- `compensation.structure.version.correct`
- `compensation.structure.version.end-date`
- `compensation.structure.approve`

`payroll.admin` received lifecycle permissions; `payroll.smoke` received read access.

### Employee-payroll OpenAPI minimum already present

The committed baseline included at least:

- `POST /payroll-relationships`
  - permission: `employee-payroll.relationship.create`
- `POST /payroll-assignments/{assignmentId}/salary-assignments`
  - permission: `employee-payroll.salary.assign`

The complete employee-payroll lifecycle contract and exact permission set were not finished at Thread 1 exit.

### V021 completion status

At exit:

- database migration work was reported locally;
- focused migration tests were reported green;
- application/service/API completion was not done;
- OpenAPI lifecycle expansion was not done;
- Keycloak employee-payroll permission inventory was not complete;
- frontend employee-payroll workflow was not complete;
- runbook and final Sprint 2 report were absent.

---

# 4. Verification decisions

## 4.1 Failsafe versus Surefire

### Permanent rule

`mvn test` is insufficient for this project because it does not execute Failsafe-managed `*IT` integration tests.

A valid integration verification must use Maven `verify` and visibly include:

- `maven-failsafe-plugin:integration-test`;
- the integration-test summary;
- `maven-failsafe-plugin:verify`;
- `BUILD SUCCESS`.

### Why this rule exists

A prior `mvn test` run appeared green while migration/API integration tests were skipped. That false green was treated as a material process failure.

## 4.2 Required verification layers

The recovered order is:

1. affected compile and static validation;
2. targeted unit tests;
3. targeted integration tests;
4. migration and legacy-upgrade tests;
5. RLS/FK/role/grant catalogue verification;
6. API integration tests;
7. permission and authentication tests;
8. architecture rules;
9. full Maven reactor verification;
10. frontend lint;
11. focused and complete frontend tests;
12. frontend production build;
13. OpenAPI lint when changed;
14. dependency/security checks;
15. final `git diff --check`, status and categorized diff;
16. independent read-only critical review for high-risk work.

## 4.3 Migration verification

Required evidence:

- fresh installation from V001 to the new migration;
- upgrade from the immediately previous committed migration;
- Flyway validation;
- preservation of old UUIDs/data where promised;
- no changes to earlier committed migration files;
- correct RLS, FKs, grants and role properties;
- no failed migration rows;
- deterministic PostgreSQL 17/Testcontainers environment.

For V021 specifically:

- fresh V001→V021;
- legacy V020→V021;
- V006 data/UUID preservation;
- exact V017/V020 lineage;
- overlap and range enforcement;
- privilege enforcement.

## 4.4 API verification

Required:

- authenticated success paths;
- unauthenticated `401`;
- authenticated but unauthorized `403`;
- missing resource `404`;
- stale ETag/`If-Match` `409`;
- idempotency replay and key/payload mismatch;
- invalid lifecycle or business data `422`;
- tenant isolation;
- RFC 9457 problem details;
- correlation-ID propagation;
- atomic audit/outbox/idempotency evidence.

## 4.5 RLS and permission verification

Required:

- ENABLE RLS;
- FORCE RLS;
- expected tenant policy;
- non-owner/NOBYPASSRLS runtime role;
- tenant-aware composite FKs;
- direct immutable history mutation denied;
- narrow lifecycle function access only;
- exact Keycloak permission claims;
- UI controls hidden/disabled consistently with backend authorization;
- cross-tenant data cannot be observed through error details.

## 4.6 Frontend verification

Required:

- focused feature tests;
- full React test suite;
- `lint`, not merely tests/build;
- production build;
- permission-sensitive actions;
- loading, empty, unauthorized and problem states;
- no persistent token/payroll payload storage;
- accessible error rendering.

## 4.7 OpenAPI verification

Required:

- Redocly validation;
- valid YAML structure;
- route/operation consistency with controller behavior;
- reusable problem responses;
- security and permission metadata;
- `Idempotency-Key`, `X-Correlation-ID`, ETag/`If-Match` where applicable;
- decimal money represented without binary floating point.

## 4.8 Negative-path decisions

The supplied evidence establishes the following negative-path categories as mandatory:

| Category | Required rejection or protection |
|---|---|
| Tenant isolation | Cross-tenant read/write/FK reference |
| Effective dating | Overlapping approved ranges; child outside parent range |
| Exact lineage | Referencing draft, inactive or wrong exact parent version |
| Lifecycle | Invalid predecessor state; unauthorized approval/end-date |
| Concurrency | Stale `If-Match` |
| Idempotency | Same key with different canonical payload |
| Immutability | Direct update/delete of history |
| Permissions | Missing or incorrect authority |
| Employee assignments | Overlapping regular pay-group or salary assignments |
| Salary | Unapproved structure, incompatible range, non-INR or invalid monthly amount in the current slice |
| Migration | Backfill order that violates newly added constraints |
| UI | Unauthorized controls and API problem details |
| Integration tests | Test command that silently skips Failsafe ITs |

The exact local V021 negative-test class/method list remained **NOT VERIFIED**.

## 4.9 Exact definition of done at Thread 1 exit

A bounded Sprint 2 unit was done only when all of the following were true:

- Fresh V001→V021 migration green.
- Legacy V020→V021 upgrade green with UUID/data preservation.
- Flyway validation green.
- V001–V020 unchanged.
- RLS/FKs/roles/grants catalogue green.
- Positive and negative migration tests green.
- Complete bounded service/API/UI/permissions/OpenAPI/runbook.
- Atomic idempotency/audit/outbox evidence.
- Concurrency, lifecycle and immutability enforced.
- Full Maven, frontend, OpenAPI, security and regression gates green.
- Working diff clean and intentional.
- Independent critical review green.
- Evidence and residual risks documented.
- No stage, commit, push, PR update or merge without explicit user approval.

---

# 5. Failures and lessons

## 5.1 Verified failure matrix

| Failure ID | Symptom/failed approach | Root cause | Permanent prevention rule | Reached handoff | Reached `AGENTS.md` |
|---|---|---|---|---|---|
| F-01 | V017 failed when a `NOT NULL` constraint was applied | Backfill/order issue in legacy upgrade | Exercise legacy upgrade and complete backfill before strict constraints | Yes | NOT VERIFIED |
| F-02 | RLS catalogue test referenced an old column | Schema rename was not reflected in verification | Update catalogue tests in the same change as schema renames | Yes | NOT VERIFIED |
| F-03 | V019 approval-metadata check failed on existing rows | Approval metadata was populated after adding the constraint | Backfill valid metadata before adding validating constraints | Yes | NOT VERIFIED |
| F-04 | Frontend tests/build passed but lint failed | Vitest mocks lacked required generic types | Run lint as an independent required gate | Yes | Verification order is reported in `AGENTS.md`; exact lint wording NOT VERIFIED |
| F-05 | `mvn test` gave a false green | Failsafe `*IT` tests were skipped | Use `clean verify` and inspect Failsafe phases | Yes | Yes in the repository verification policy, according to the handoff |
| F-06 | Dependency Check failed with NVD update/null/no-data errors | Feed, API-key, cache or cold-data environment problem | Separate feed availability from vulnerability findings; use cached/scheduled data service | Yes | NOT VERIFIED; backlog/runbook exists |
| F-07 | Linux CI could not run `./mvnw` | Wrapper committed as mode `100644` | Preserve executable mode `100755` | Yes | NOT VERIFIED |
| F-08 | Independent dependency scan could not resolve reactor snapshots | Clean runner lacked locally installed reactor artifacts | Prepare/install reactor before module-independent scans | Yes | NOT VERIFIED |
| F-09 | Cold NVD initialization exceeded timeout | Initial database load can take tens of minutes | Cache/schedule Dependency Check; use dependency review for deterministic PR gating | Yes | NOT VERIFIED |
| F-10 | A workflow rerun could not validate a newly fixed workflow commit | GitHub reruns the old SHA | Push the corrected SHA and validate that SHA; do not treat an old-sha rerun as proof | CI report | NOT VERIFIED |

## 5.2 Additional Thread 1 implementation lessons recorded in conversation context

These were material in the S2-04 implementation conversation but are not all present in the formal handoff failure table.

| Failure ID | Symptom | Root cause | Prevention | Handoff status |
|---|---|---|---|---|
| F-11 | Generated PowerShell scripts repeatedly failed parsing | Unescaped interpolation, malformed here-strings, marker assumptions and empty-array handling | Run reusable parser preflight before any generated PowerShell script; prefer smaller bounded scripts | Reusable `Test-PowerShellScript.ps1` reached the repository/PR inventory; lesson text in formal handoff is NOT VERIFIED |
| F-12 | OpenAPI YAML failed Redocly parsing | `requestBodies:` was concatenated onto the preceding line | Validate OpenAPI immediately after generation and inspect insertion boundaries | NOT VERIFIED in formal handoff |
| F-13 | Backend compile failed on ambiguous `JdbcTemplate.query` overload | Lambda matched multiple overloads | Use an explicit callback type where Java overload resolution is ambiguous | NOT VERIFIED in formal handoff |
| F-14 | Migration test expected a constraint name but PostgreSQL returned a trigger message | Assertion targeted implementation metadata rather than the actual externally visible database error | Assert the stable business/trigger message where that is the contract | NOT VERIFIED in formal handoff |

These additional lessons should be treated as Thread-recorded until independently reconciled against commits and test history.

## 5.3 Vulnerability-policy decision

The project separated:

### Deterministic per-commit checks

- GitHub dependency review on PRs;
- high-severity policy;
- Dependabot for Maven/npm/GitHub Actions;
- `npm audit`;
- Gitleaks;
- SBOM generation.

### Feed-dependent checks

- OWASP Dependency Check using a centralized, scheduled or cached NVD data service.

A feed outage must not be disguised as a successful vulnerability scan, and a real vulnerability must not be silenced by unconditional success.

---

# 6. Handoff and checkpoint execution

## 6.1 Vertical-slice artifact delivery checkpoint

**Artifact:** Organisation-to-Draft-Payslip Vertical Slice package.

Captured:

- Word implementation pack;
- Flyway package;
- OpenAPI;
- Maven skeleton;
- React skeleton;
- Docker Compose;
- Sprint backlog;
- validation manifest;
- stated exclusions.

Missing at that point:

- dependency-resolving full Maven build due offline Maven Central;
- live repository integration state;
- Git branch/PR/commit history.

## 6.2 Sprint 0 integration report

Captured:

- monorepo integration;
- environment versions;
- V001–V012 and later Sprint 0B additions;
- database and build verification;
- OpenAPI/frontend checks;
- Docker/Keycloak setup;
- files and command ledger;
- unresolved warnings.

What should have been clearer:

- one canonical final report rather than multiple near-duplicate Sprint 0 copies;
- exact transition from “completed, not committed” to the committed baseline;
- exact first repository commit and complete commit chain.

## 6.3 Sprint 0 CI repair report

Captured:

- wrapper executable defect;
- reactor preparation defect;
- NVD cache/timeout behavior;
- exact repair commits;
- workflow run IDs and outcomes;
- final main SHA.

Missing:

- an explicit statement that direct push to `main` was an exception and not the standard future workflow;
- a consolidated link from the main Sprint 0 report to the CI repair report.

## 6.4 Sprint 1 integration report

Captured:

- baseline branch/SHA;
- S1-00 through S1-06;
- V014–V016;
- API, UI, Keycloak and event-reliability behavior;
- complete verification matrix;
- commit sequence;
- draft PR #2 and CI run;
- residual risks.

Later state not captured in the original report:

- PR #2 merge;
- merge commit `27947e...`;
- final post-merge main SHA;
- any post-merge reconciliation.

The later Thread 1 handoff supplied the merge fact.

## 6.5 S2-04 completion/regression checkpoint

Captured in the conversation and repository inventory:

- V020 salary-structure migration;
- backend/API/OpenAPI/Keycloak/frontend completion;
- full consolidated regression;
- reusable PowerShell parser preflight;
- reusable HRMS Payroll regression script;
- no staging/commit side effects in regression.

What was not captured in a formal Sprint 2 report:

- all S2-01 through S2-04 commit SHAs in one ledger;
- per-capability acceptance evidence;
- complete PR #3 permission inventory;
- S2-01/S2-02 numbering correction.

## 6.6 Complete Thread Handoff — 22 July 2026

**Artifacts:**

- `HRMS_Payroll_Thread_Handoff_Scoping_to_Implementation.md`
- `HRMS_Payroll_Thread_Handoff_Scoping_to_Implementation.docx`
- `HRMS_Payroll_Continuation_Prompt.txt`

Captured:

- source precedence;
- architecture rules;
- repository structure;
- Sprint 0/1 history;
- PR #3 remote checkpoint;
- V001–V021 ledger;
- local-only V021 status;
- Failsafe rule;
- verification sequence and definition of done;
- known failures;
- delivery authorization boundaries;
- documentation conflicts;
- continuation warning.

Missing or explicitly unresolved:

- exact V021 SQL and local diff;
- V021 Java/API/OpenAPI/UI inventory;
- V022;
- exact S2-05/S2-06 split;
- final Sprint 2 report;
- corrected README and PR body;
- exact 12-iteration/14-stage reconciliation;
- complete periodic checkpoint list/cadence.

## 6.7 Periodic handoff cadence

The user required downloadable artifacts and later adopted transition handoffs. The exact number, naming convention and cadence of all intermediate periodic checkpoints in Thread 1 is **NOT VERIFIED** from the supplied material.

---

# 7. Deferred debt carried into Thread 3

## 7.1 Sprint 2 implementation debt

At Thread 1 exit, the following remained:

- inspect and reconcile the complete local V021 diff;
- prove V001–V020 unchanged;
- complete V021 negative-path audit;
- complete full Maven reactor verification after V021;
- implement employee-payroll application/service/API;
- complete OpenAPI lifecycle contract;
- complete Keycloak permission mapping;
- complete employee-payroll frontend;
- add runbook;
- perform independent critical review;
- commit/push/update PR #3 only after approval;
- create final Sprint 2 report.

## 7.2 Documentation debt

- Resolve S2-01/S2-02 backlog-versus-PR numbering conflict.
- Update README from “Sprint 1 foundation” wording.
- Update PR #3 body to reflect V021 status once committed.
- Add V021 runbook/API/frontend inventory.
- Record all Sprint 2 commit SHAs and final CI.
- Do not reconstruct the 12-iteration/14-stage mapping from memory.

## 7.3 Reliability and security debt

- Implement production broker-specific outbox ordering, scheduling, alerting and authorized replay.
- Establish centralized/scheduled cached OWASP Dependency Check data service.
- Preserve dependency review and deterministic PR gates.
- Configure Mockito as an explicit agent before a JDK disables dynamic attachment.
- Track GitHub Action runtime deprecations rather than suppressing them.
- Production identity-provider login/session plumbing remained outside the organisation UI baseline.

## 7.4 Assumptions later threads must not make

Later threads must not assume:

1. V021 was committed or included in PR #3.
2. Remote CI proved V021.
3. V021 object, column, function, trigger or permission names.
4. Which V006 UUID became identity versus version.
5. V021 completed both S2-05 and S2-06.
6. V022 exists.
7. Employee-payroll API/UI was complete.
8. Passing `mvn test` means integration tests ran.
9. An ephemeral Testcontainers pass proves persistent Compose database parity.
10. An NVD feed failure is an application-code failure.
11. A green migration test proves the entire vertical slice is done.
12. Sprint 3 may begin before Sprint 2 closure and handoff evidence.
13. PR #3 should be replaced by another PR.
14. Prior migrations may be edited to simplify current work.
15. Stable identity alone is enough for payroll lineage; exact version IDs are mandatory.
16. Money may use floating point.
17. Access tokens or payroll payloads may be persisted in browser storage.
18. The canonical backlog numbering can be inferred from migration order.

---

# 8. Decision table

| Decision ID | Exact decision | Rationale | Evidence | Implementation status at Thread 1 exit | Conflict | Current handoff action |
|---|---|---|---|---|---|---|
| D-001 | Build a complete payroll operating platform, not only a monthly calculator | Payroll includes configuration, operations, calculation, payment, statutory, accounting, correction and audit | Thread 1 charter | PARTIALLY IMPLEMENTED | None | Preserve full-product boundary while building bounded slices |
| D-002 | India-first, multi-tenant, multi-entity payroll | Initial statutory pack and operating complexity are India-specific | Thread 1 charter | DESIGN BASELINE | None | Keep statutory work outside current narrow slice until scheduled |
| D-003 | Payroll is independently operable but integrated with wider HRMS | Payroll must continue using approved snapshots even when upstream HRMS is unavailable | Thread 1 charter | PARTIALLY IMPLEMENTED | None | Keep clear data ownership and event contracts |
| D-004 | Modular monolith with `payroll-boot` composition root | Strong boundaries without distributed-system overhead | Handoff/ADR baseline | IMPLEMENTED | None | Continue ArchUnit/Modulith enforcement |
| D-005 | Cross-module access only through public APIs | Prevent hidden coupling and ownership violations | Handoff/Sprint 1 | IMPLEMENTED | None | Reject internal/repository/JPA imports across modules |
| D-006 | PostgreSQL 17 and forward-only Flyway migrations | Reproducible schema evolution and production-aligned database | Handoff/Sprint reports | IMPLEMENTED | None | Keep all committed migrations immutable |
| D-007 | Stable identity separate from immutable exact version | Navigation needs enduring identity; payroll lineage needs exact historical state | ADR-004/V015/V017/V020 | IMPLEMENTED through V020; V021 LOCAL | None | Apply same pattern only after inspecting V021 |
| D-008 | Preserve legacy UUIDs as historical version IDs where practical | Avoid breaking historical and downstream references | V017/V020/V021 handoff | IMPLEMENTED V017/V020; V021 NOT REMOTE | Exact V021 mapping unknown | Add explicit legacy-to-version mapping to handoff |
| D-009 | Half-open effective ranges and non-overlap for approved versions | Deterministic as-of resolution and no ambiguous history | Handoff | IMPLEMENTED | None | Maintain range and containment negative tests |
| D-010 | Store exact version IDs in sealed lineage | Stable IDs cannot prove which state was used | Handoff/Thread design | PARTIALLY IMPLEMENTED | None | Verify all employee-payroll downstream FKs |
| D-011 | Tenant-safe composite FKs plus ENABLE/FORCE RLS | Application filtering alone is insufficient | Sprint 0/1 | IMPLEMENTED | None | Continue catalog and cross-tenant tests |
| D-012 | `payroll_app` is non-owner and NOBYPASSRLS; transactions use `SET LOCAL` | RLS must remain effective at runtime | Sprint 1 | IMPLEMENTED | None | Verify every new module repository path |
| D-013 | Immutable history; no broad runtime UPDATE/DELETE | Approved payroll/configuration evidence must not be rewritten | V012/V016 | IMPLEMENTED | None | Add narrow commands for new lifecycle operations |
| D-014 | Optimistic locking via `version_no`, ETag and `If-Match` | Prevent lost lifecycle updates | Sprint 1 API | IMPLEMENTED in established lifecycles | V021 lifecycle incomplete | Carry to employee-payroll API |
| D-015 | Every write uses idempotency key and canonical payload hash | Retries must not duplicate payroll changes | Sprint 1 | IMPLEMENTED pattern | V021 app incomplete | Apply atomically with employee-payroll writes |
| D-016 | Audit, aggregate, idempotency and outbox commit atomically | Avoid orphaned or contradictory evidence | Sprint 1 | IMPLEMENTED pattern | V021 app incomplete | Verify rollback/commit tests |
| D-017 | Transactional outbox/inbox with dedup, retry and dead letter | Reliable event publication and consumption | V014/S1-00 | FOUNDATION IMPLEMENTED | Broker operations deferred | Retain deferred production-broker debt |
| D-018 | RFC 9457 problem details | Stable machine-readable error contract | Sprint 1 | IMPLEMENTED | None | Reuse exact error catalog |
| D-019 | BigDecimal/decimal strings; current slice INR; no float | Payroll arithmetic must be exact and reproducible | Handoff | IMPLEMENTED policy | None | Add serialization/scale tests |
| D-020 | Inject `Clock`; snapshots drive deterministic calculations | Server date/time must not alter results | Thread design | PARTIALLY IMPLEMENTED | None | Preserve in future calculations |
| D-021 | Tokens remain memory-only in browser | Reduce credential exposure | Sprint 1 UI | IMPLEMENTED baseline | Production login deferred | Do not introduce persistent browser storage |
| D-022 | Create draft PRs and keep them draft until full sprint closure | Allows incremental review without premature merge | Sprint reports/handoff | IMPLEMENTED | None | Reuse PR #3 |
| D-023 | No stage/commit/push/PR/merge without green gates and user authorization | Prevent accidental repository writes and partial delivery | Handoff/AGENTS | IMPLEMENTED PROCESS | None | State write status in every handoff |
| D-024 | `mvn test` is not sufficient; use `verify` and inspect Failsafe | Integration tests otherwise silently skip | Failure F-05 | IMPLEMENTED PROCESS | None | Preserve visible phase evidence |
| D-025 | Frontend lint is a separate required gate | Tests/build did not catch typed mock violations | Failure F-04 | IMPLEMENTED PROCESS | None | Keep lint in regression |
| D-026 | Separate deterministic security checks from feed-dependent NVD scans | Feed availability is not vulnerability status | CI failures/handoff | PARTIALLY IMPLEMENTED | Cached ODC service deferred | Maintain backlog/runbook |
| D-027 | Calendar and Pay Group story numbering must not be guessed | Backlog and PR labels conflict | Handoff | UNRESOLVED DOCUMENTATION | S2-01/S2-02 conflict | Correct before Sprint 2 closure |
| D-028 | V021 upgrades V006 forward-only | Historical migrations are frozen | Handoff | LOCAL/NOT REMOTE | Exact mapping unknown | Inspect local SQL and document mapping |
| D-029 | V021 may span S2-05 and part of S2-06, but the split comes from SQL/tests | Capability labels cannot override actual migration scope | Handoff | NOT VERIFIED | Scope split unresolved | Record exact split after local inspection |
| D-030 | V022 is not established by Thread 1 evidence | Prevent invented continuation | Extraction evidence | NOT VERIFIED | User prompt asks V001–V022 but source ends at V021 | Mark explicitly absent until repository proves it |
| D-031 | High-risk changes require independent critical review | Effective dating, RLS and data transforms can pass tests yet remain unsafe | AGENTS/handoff | IMPLEMENTED PROCESS | None | Include reviewer findings in closure report |
| D-032 | Reusable parser preflight before generated PowerShell scripts | Repeated parser failures delayed delivery | Thread-recorded S2-04 | IMPLEMENTED SCRIPT | Formal handoff lesson incomplete | Add to repository handoff/governance |
| D-033 | Reusable full regression before commit | Repeatable whole-project quality gate | S2-04 completion | IMPLEMENTED SCRIPT | None | Keep sprint-specific acceptance tests in addition |
| D-034 | Core HR remains source for person/employment/assignment; payroll references and snapshots | Avoid duplicating the full HR master | Thread 1 Iteration 3 | DESIGN BASELINE | None | Preserve ownership in V021/API |
| D-035 | Employee payroll key is relationship plus assignment | Supports multiple assignments and employers | Thread 1 Iteration 3 | PARTIALLY IMPLEMENTED/LOCAL V021 | Exact schema unknown | Verify actual aggregate model |
| D-036 | Approved payroll and employee snapshots are immutable | Later profile correction must not alter past payroll | Thread 1 | DESIGN/FOUNDATION IMPLEMENTED | None | Store exact source/version references |
| D-037 | Payroll result and payment transaction are separate | Correct payroll can coexist with failed payment | Thread 1 charter | DESIGN BASELINE | None | Preserve in later payment sprint |
| D-038 | Original 12-iteration/14-stage mapping must not be reconstructed from memory | Source reconciliation was incomplete | Handoff | NOT VERIFIED | Original transcript lists 14 proposed iterations; later consolidation says 12 | Retain both facts and request source artifact before reconciling |

---

# 9. Handover delta

The current repository handoff should be amended with the following exact additions or corrections.

## 9.1 Additions

1. **Thread entry/exit checkpoint table**
   - Sprint 0 implementation baseline: `bba8a51...`
   - Sprint 1 PR #2 commit sequence and merge `27947e...`
   - Sprint 2 PR #3 base/head `42d4b50...` → `24f2ed48...`
   - CI run `29921705101`
   - clear statement that remote evidence stops at V020.

2. **First/last commit qualification**
   - State that `51ee76e` is only the first recovered CI-repair commit, not necessarily the first project commit.
   - State that the first Sprint 2 commit is not recovered.

3. **V022 status**
   - Add: “V022 is NOT VERIFIED in Thread 1 evidence and must not be assumed.”

4. **Historical branch-command qualification**
   - Add the recovered Sprint 0 repair command and Sprint 1 push/draft-PR commands as historical evidence.
   - Mark exact Sprint 1 and Sprint 2 branch-creation commands NOT VERIFIED.

5. **PowerShell prevention rule**
   - Add the reusable `Test-PowerShellScript.ps1` preflight requirement.
   - Record that generated PowerShell must pass parser validation before execution.
   - Record preference for smaller bounded scripts.

6. **Generic regression rule**
   - Add `Invoke-HrmsPayrollRegression.ps1` as the reusable full-project gate.
   - Clarify that it does not replace feature-specific acceptance tests.

7. **S2-04 implementation lessons**
   - OpenAPI insertion boundaries must be validated immediately.
   - Java overloaded callback methods may require explicit types.
   - Database-error assertions must target the stable externally visible contract.
   - Mark these as Thread-recorded unless commit/test evidence is attached.

8. **V021 proof requirements**
   - Add a legacy mapping table requirement:
     `V006 object/UUID/FK → V021 identity/version role`.
   - Add explicit proof of exact V017 pay-group and V020 salary-structure version lineage.
   - Add all local V021 file paths, test classes and test counts after inspection.

9. **Handoff artifact inventory**
   - Link the Sprint 0 report, CI repair report, Sprint 1 report, S2-04 regression evidence, complete Thread handoff and continuation prompt.
   - Identify which is authoritative for committed versus local-only state.

10. **Authorization state**
    - Every handoff should state separately:
      - working tree;
      - Git index;
      - commit;
      - push;
      - PR update;
      - merge.

## 9.2 Corrections

1. Correct or explicitly preserve the unresolved S2-01/S2-02 numbering conflict.
2. Update README wording that still identifies the repository as only a Sprint 1 foundation.
3. Update PR #3 “Remaining” only after V021 is committed; do not describe local-only work as remote.
4. Do not claim a final Sprint 2 report exists until one is created and committed.
5. Do not claim the original 12-iteration/14-stage mapping is recovered.
6. Do not list V022 as planned or implemented without repository evidence.
7. Distinguish:
   - `THREAD-RECORDED — LOCAL` V021 test results;
   - remote CI proof through V020.
8. State that Sprint 1’s original report ended with a draft PR, while the later handoff establishes its eventual merge.

## 9.3 Conflicts requiring a user or source decision

| Conflict | Required resolution |
|---|---|
| S2-01/S2-02 labels | Choose canonical backlog numbering or formally amend the backlog; do not infer from migration order. |
| 14 proposed iterations versus later 12-iteration consolidation | Supply the consolidated blueprint/source reconciliation before recording an exact map. |
| V021 coverage of S2-05/S2-06 | Derive from local SQL/tests, then approve the documented split. |
| Sprint 0 direct-to-main repair versus normal PR policy | Record it as an explicit historical exception or adopt a formal emergency-main repair policy. |
| V022 responsibility | Supply repository/backlog evidence before assigning a purpose. |

---

# 10. Final unsupported-items register

The following are explicitly **NOT VERIFIED** from the supplied Thread 1 evidence:

- Git SHA and branch at the first product-design message.
- Complete original project commit history before the recovered Sprint 0 CI repair commits.
- Exact Sprint 1 branch-creation command.
- Exact Sprint 2 branch-creation command.
- Exact branch-base verification command sequence for Sprint 1 or Sprint 2.
- First Sprint 2 commit SHA.
- Exact current local V021 SQL/table/function/trigger/grant names.
- Exact V006 UUID-to-identity/version mapping.
- Complete V021 negative-test inventory.
- Exact V021 API, Keycloak and UI permission set.
- Whether any partial V021 Java/OpenAPI/UI files existed locally.
- Exact split of V021 between S2-05 and S2-06.
- Any V022 migration or story responsibility.
- Exact original 12-iteration/14-stage mapping.
- Complete list and cadence of all intermediate Thread 1 checkpoints.
- Whether every failure-prevention rule was copied verbatim into `AGENTS.md`.

---

# 11. Exit statement

Thread 1 ended with:

- Sprint 0 foundation established and hardened;
- Sprint 1 merged into `main`;
- Sprint 2 remote draft PR #3 green through V020;
- V021 recorded as local, uncommitted and database-focused;
- employee-payroll application/API/OpenAPI/Keycloak/UI work incomplete;
- no basis to claim V022;
- strict instruction to inspect the local working tree before continuing;
- strict prohibition on staging, committing, pushing, updating PR metadata or merging without green evidence and explicit user authorization.
