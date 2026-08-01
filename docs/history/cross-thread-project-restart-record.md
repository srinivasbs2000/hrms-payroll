# Cross-Thread Reconciliation and Project Restart Record

**Project:** HRMS Payroll
**Repository:** `srinivasbs2000/hrms-payroll`
**Evidence cut-off:** 1 August 2026
**Mode:** Read-only cross-thread reconciliation
**Repository changes performed:** None
**Git or pull-request changes performed:** None

## Evidence classifications

- **VERIFIED — REPOSITORY:** Confirmed from current GitHub repository, pull request, commit, workflow, code, test, migration or committed document.
- **VERIFIED — THREAD RECORD:** Confirmed by one or more supplied Thread 2–5 reconciliation records but not independently established as current local state.
- **DERIVED:** Direct conclusion from identified verified evidence.
- **DOCUMENTATION CONFLICT:** Current authority documents disagree with live repository evidence or with another durable authority.
- **NOT VERIFIED:** Exact evidence is unavailable; no assumption is permitted.

---

# 1. Executive restart decision

## 1.1 Current state

- **VERIFIED — REPOSITORY:** Current remote `main` is `4b5da975eb851434957667bdecf138ea9b43f929`, the merge commit for PR #20, `docs(project): establish living design and thread governance`.
- **VERIFIED — REPOSITORY:** The latest merged product implementation is Sprint 4 at `def3dd2e212f85c440eee5497e292be2f1f2bf64`, merged through PR #19.
- **VERIFIED — REPOSITORY:** V001–V030 are committed and immutable. Any future schema work begins at V031 only after explicit registration and approval.
- **VERIFIED — REPOSITORY:** No pull request is currently open.
- **VERIFIED — REPOSITORY:** The latest verified CI is `payroll-baseline` run 83, successful on PR #20 head `20935aa4f73dc7e6262cf4bf5f82a3d0b81c2395`.
- **NOT VERIFIED:** A workflow result directly associated with merge commit `4b5da975...` was not returned by the available connector.
- **NOT VERIFIED:** Current local branch, local HEAD, working tree, Git index and persistent database state were not supplied for this reconciliation.

## 1.2 Restart ruling

- **DERIVED:** Do not begin Sprint 5 feature implementation.
- **DERIVED:** First complete one bounded documentation/governance reconciliation that makes the living documents agree with current `main` and the four recovered thread records.
- **DERIVED:** After that documentation change is merged, start a fresh **Thread 6** as the sole implementation owner.
- **DERIVED:** The first implementation increment should be **S4-06A — Statutory API Integration Closure**, not V031 and not a new payroll feature.
- **DERIVED:** S4-06A should add a real Spring Boot + PostgreSQL 17 HTTP integration test for the statutory lifecycle. It should not change migrations, production application code, OpenAPI, Keycloak, frontend code or dependencies unless the test exposes a separately approved production defect.

---

# 2. Current verified repository checkpoint

| Item | Reconciled fact | Classification |
|---|---|---|
| Repository | `srinivasbs2000/hrms-payroll` | VERIFIED — REPOSITORY |
| Default branch | `main` | VERIFIED — REPOSITORY |
| Current remote `main` | `4b5da975eb851434957667bdecf138ea9b43f929` | VERIFIED — REPOSITORY |
| Current commit meaning | PR #20 living-design/thread-governance merge | VERIFIED — REPOSITORY |
| Product implementation baseline | Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` | VERIFIED — REPOSITORY |
| Latest merged sprint | Sprint 4 — jurisdiction-neutral statutory lifecycle | VERIFIED — REPOSITORY |
| Open PRs | None | VERIFIED — REPOSITORY |
| Latest verified PR-head CI | Run 83 on `20935aa4...`, success | VERIFIED — REPOSITORY |
| CI on current merge SHA | Not established by available evidence | NOT VERIFIED |
| Migration range | V001–V030 | VERIFIED — REPOSITORY |
| Migration immutability | V001–V030 immutable | VERIFIED — REPOSITORY |
| Next possible migration | V031, unreserved | VERIFIED — REPOSITORY / DESIGN BASELINE |
| Active implementation branch | None established | VERIFIED — REPOSITORY |
| Current local branch/HEAD | Not supplied | NOT VERIFIED |
| Current local working tree/index | Not supplied | NOT VERIFIED |
| Current persistent PostgreSQL/Flyway state | Not supplied | NOT VERIFIED |

## 2.1 Existing remote branches

The repository still contains historical Sprint, foundation, CI-repair, governance and Dependabot branches. The existence of a branch does not make it an active implementation branch.

Notable historical refs include:

- `ci/sprint-0-baseline-repair`
- `foundation/sprint-0-baseline`
- `feature/sprint-1-organisation-foundation`
- `feature/sprint-3-payroll-execution`
- `feature/sprint-4-statutory-deductions`
- `docs/living-master-design`
- multiple Dependabot branches
- `main`

**DERIVED:** Historical merged branches must not be reused as current implementation branches.

---

# 3. Thread-by-thread disposition

## 3.1 Thread 1

| Dimension | Reconciled result |
|---|---|
| Historical purpose | Original product/design authority, Sprint 0–2 recovery and cross-thread governance bootstrap |
| Completed work | Thread 1 decision extraction; living master-design/governance bootstrap through PR #20 |
| Superseded work | Historical Thread 1 implementation checkpoints through V021 are superseded by later merged Sprint 2–4 repository state |
| Unresolved work | Cross-thread consolidation, current-baseline correction, encoding repair, registry completion and handoff normalization |
| Final role now | **RECOVERY/HANDOFF — ACTIVE FOR ONE GOVERNANCE CONSOLIDATION** |
| Write ownership | Documentation/governance allow-list only after approval |
| Migration reservation | None |
| Implementation ownership | None |

## 3.2 Thread 2

| Dimension | Reconciled result |
|---|---|
| Historical purpose | Complete Sprint 2 and implement early Sprint 3 through V026 |
| Completed/merged | Sprint 2 PR #3; V021–V022; employee-payroll services/API/UI; Sprint 3 V023–V026 and related APIs |
| Superseded | Remaining Sprint 3 work was completed by Thread 3 |
| Unresolved work | No Thread 2 implementation work remains |
| Final role | **CLOSED** |
| Write ownership | None |
| Migration reservation | None |

## 3.3 Thread 3

| Dimension | Reconciled result |
|---|---|
| Historical purpose | Complete and merge the remaining Sprint 3 regular-payroll execution path |
| Completed/merged | Recalculation application path, execution UI, draft-payslip UI, browser auth, Playwright E2E, CI gate and PR #18 merge |
| Superseded | “Next migration V027” and Sprint 3 handoff state were superseded by Sprint 4 |
| Unresolved work | Historical metadata and registry only |
| Final role | **CLOSED** |
| Write ownership | None |
| Migration reservation | None |

## 3.4 Thread 4

| Dimension | Reconciled result |
|---|---|
| Historical purpose | Implement Sprint 4 jurisdiction-neutral statutory foundation through V030, API and UI |
| Completed/merged | V027–V030, statutory module, lifecycle/evaluation/ledger, exact money, API, permissions and UI |
| Superseded | Open-PR and local-only closure state was superseded by PR #19 merge |
| Unresolved work | Dedicated statutory real-HTTP integration test; dedicated statutory browser scenario; final durable smoke evidence; historical authority cleanup |
| Final role | **CLOSED** |
| Write ownership | None |
| Migration reservation | None |

## 3.5 Thread 5

| Dimension | Reconciled result |
|---|---|
| Historical purpose | Finish Sprint 4 publication/merge, audit verification claims and initiate multi-thread recovery |
| Completed/merged | Sprint 4 closure transition and process audit; PR #19 is merged; reconciliation process established |
| Attribution caveat | Exact conversational ownership of final closure commit and merge between Threads 4 and 5 is not independently provable |
| Unresolved work | Carry forward automation and process debt until living documents are corrected |
| Final role | **RECOVERY/HANDOFF — NO WRITE OWNERSHIP** |
| Migration reservation | None |
| Implementation ownership | None |
| Exit condition | Close after Thread 6 accepts the approved implementation handoff |

## 3.6 Recommended implementation owner

**Recommended owner:** **Thread 6 — IMPLEMENTATION OWNER**

Rationale:

1. Threads 2–4 are complete and historical.
2. Thread 5 is a recovery/process-audit thread rather than a clean active implementation context.
3. Reusing an old merged feature branch would recreate the state ambiguity this governance work was meant to eliminate.
4. A new thread can start solely from current repository authority files and one exact bounded story.
5. Thread 6 can be registered before any write and can own one new branch with no migration reservation.

---

# 4. Cross-thread conflict matrix

| ID | Conflict | Sources | Classification | Resolution |
|---|---|---|---|---|
| XTR-001 | Literal repository HEAD | Live `main` is `4b5da975...`; master design and registry call `def3dd2e...` current baseline | DOCUMENTATION CONFLICT | Record both: repository HEAD `4b5da...`; product implementation baseline `def3dd2...` |
| XTR-002 | Running handoff current state | Top card says PR #19 open at `6cf39fc...`; later section says merged; live repo is later again | DOCUMENTATION CONFLICT | Replace current card with one concise authoritative checkpoint; move old cards to history |
| XTR-003 | Thread registry | Threads 2–5 remain `NOT VERIFIED` | DOCUMENTATION CONFLICT | Replace with reconciled rows |
| XTR-004 | Encoding | README, AGENTS and handoff contain mojibake such as `â€“`, `Â·` and corrupted quotes | DOCUMENTATION CONFLICT | UTF-8 cleanup and publication-time mojibake scan |
| XTR-005 | Sprint 3 story labels | Historical slice labels such as S3-04A differ from canonical backlog meaning | DOCUMENTATION CONFLICT | Identify history by capability + migration + commit/PR; do not use story label alone |
| XTR-006 | Sprint 4 closure attribution | Thread 4 handed remaining work to Thread 5; repository proves final commit/merge but not conversational ownership | NOT VERIFIED | Record shared transition and avoid assigning sole authorship |
| XTR-007 | “Fully automated” claim | Migration/unit/controller/manual evidence exists, but no dedicated statutory Spring Boot HTTP/PostgreSQL IT | DOCUMENTATION CONFLICT | Track as open quality debt; implement S4-06A |
| XTR-008 | Statutory browser coverage | Generic Payroll Playwright exists; PR #19 added no statutory-specific E2E spec | DOCUMENTATION CONFLICT | Track separately as S4-06B after S4-06A |
| XTR-009 | Manual smoke evidence | PR body claims live checks; committed runbook is blank and closure report remains pre-merge | DOCUMENTATION CONFLICT | Mark smoke as PR-recorded but not durably completed; do not fabricate sign-off |
| XTR-010 | PR metadata | PR #19 body says it remains unmerged although it is merged | DOCUMENTATION CONFLICT | Preserve as historical metadata defect; correct living documents, not history |
| XTR-011 | Current CI | Run 83 proves PR #20 head; direct merge-SHA result unavailable | NOT VERIFIED | Record latest verified CI precisely; verify locally before next publication |
| XTR-012 | Next feature | Backlog ends at Sprint 4; no Sprint 5 story is approved | NOT VERIFIED / JOINT DECISION | Close known quality debt before feature selection |
| XTR-013 | ADR file-by-file validation | Reconciliations report ADR alignment; connector did not enumerate every ADR file directly | NOT VERIFIED | Do not claim a complete ADR inventory review; current master design/code/tests show no detected architectural conflict |

---

# 5. Consolidated delivery ledger

| Sprint | Capability delivered | Migration range | Merge evidence | Verification status | Remaining debt |
|---|---|---|---|---|---|
| Sprint 0 | Repository, security, tenancy, migration, OIDC, CI and vertical-slice baseline | V001–V013 | Main baseline before PR #2 | Historical reports; baseline merged | Cached OWASP data service and production broker operations |
| Sprint 1 | Event reliability, organisation identity/version lifecycle, RLS, audit, API/UI | V014–V016 | PR #2, merge `27947e120...` | PR evidence and historical CI green | No Sprint 1 functional debt identified |
| Sprint 2 | Pay groups, calendars, components, salary structures and employee payroll | V017–V022 | PR #3, merge `84530e1f...` | Run 35 success; PR quality evidence green | No Sprint 2 functional debt identified |
| Sprint 3 | Cycles, population, sealed inputs, deterministic calculation, recalculation, draft payslip and browser E2E | V023–V026 | PR #18, merge `73c35666...` | Run 63 success | Historical metadata cleanup only |
| Sprint 4 | Jurisdiction-neutral statutory rules, profiles, evaluation, ledger, balances, reconciliation, API/UI and exact money | V027–V030 | PR #19, merge `def3dd2e...` | Run 81 success | Missing real statutory HTTP/PostgreSQL IT; missing statutory-specific browser E2E; incomplete durable smoke record |
| Governance | Master design, decision register, registry, protocol and historical recovery | None | PR #20, merge `4b5da975...` | Run 83 success on PR head | Stale baseline metadata, incomplete registry, mojibake, stale handoff |

---

# 6. Repository authority findings

## 6.1 Master design

**VERIFIED — REPOSITORY:** The master design correctly distinguishes approved scope, architecture and long-lived rules from the running handoff.

Required corrections:

1. Replace “Current verified baseline” with two fields:
   - current repository HEAD: `4b5da975...`;
   - current product implementation baseline: `def3dd2e...`.
2. Add controlled quality debt:
   - missing statutory real-HTTP/PostgreSQL integration test;
   - missing statutory-specific browser E2E.
3. Record that new feature selection remains unapproved after automation closure.
4. Add a change-history row for cross-thread reconciliation.

## 6.2 Decision register

Recommended new entries:

| Proposed ID | Decision | Type | Status |
|---|---|---|---|
| MDR-021 | Record repository HEAD separately from the latest product implementation merge | Process | APPROVED |
| MDR-022 | Sprint 4 is functionally merged but not fully automated until a statutory HTTP/PostgreSQL IT and statutory E2E exist | Quality | OPEN/CONTROLLED DEBT |
| MDR-023 | Documentation publication must preserve UTF-8 and fail on common mojibake markers | Process | APPROVED |
| MDR-024 | When historical story labels diverge, identify work by capability, migration and commit/PR | Process | APPROVED |
| MDR-025 | Normalize PowerShell output cardinality before indexing/string methods and keep diagnostic output outside data pipelines | Process | APPROVED/IMPLEMENTED POLICY |

Candidate failure-prevention rules such as exact-blob patch generation and inherited-uniqueness audits should be placed in implementation/runbook guidance unless the owner approves them as project-wide material decisions.

## 6.3 Thread registry

Replace the bootstrap placeholders with the rows proposed in section 7. Register Thread 6 only after the documentation reconciliation is approved and before its first write.

## 6.4 Running handoff

The handoff must become a concise current-state document, not an accumulated sequence of contradictory “current” cards.

Required current checkpoint:

- remote `main`: `4b5da975...`;
- product baseline: `def3dd2e...`;
- no open PR;
- no active feature branch;
- V001–V030 immutable;
- V031 unreserved;
- latest verified CI: run 83 on PR #20 head;
- local state: NOT VERIFIED until inspected;
- current stage: cross-thread documentation reconciliation;
- next proposed implementation: S4-06A after documentation merge;
- prohibited actions and authorization boundaries.

Historical Sprint 4 cards should be moved to a history section or clearly marked superseded.

## 6.5 README

Required:

- repair mojibake;
- preserve Sprint 1–4 scope;
- stop referring to the blank Sprint 4 smoke template as a current pre-merge gate;
- state accurately that the statutory UI/operator path exists but dedicated API integration and statutory-specific E2E remain quality debt until S4-06A/S4-06B complete.

## 6.6 AGENTS.md

Required:

- repair mojibake;
- retain all current architecture, security and delivery rules;
- add a test-taxonomy rule: migration IT, controller/unit tests, real HTTP/database integration and browser E2E are distinct and cannot be substituted for one another;
- add UTF-8/mojibake verification for documentation publication;
- preserve the rule that high-risk changes receive independent critical review.

## 6.7 Backlog

The backlog currently ends at S4-05B.

Proposed additions, subject to approval:

| Sprint | Story ID | Epic | Priority | Story | Acceptance |
|---|---|---|---|---|---|
| Sprint 4 Closure | S4-06A | Statutory quality | Must | Add real Spring Boot/PostgreSQL statutory API integration coverage | Real secured HTTP lifecycle, RLS, permissions, idempotency, concurrency, exact money and database evidence pass under Maven Failsafe |
| Sprint 4 Closure | S4-06B | Statutory quality | Must | Add statutory-specific Playwright operator journey | Administrator and read-only statutory journeys run against an isolated full stack in CI with sanitized evidence |

Do not select a Sprint 5 feature until at least S4-06A is complete and S4-06B is explicitly scheduled or completed.

## 6.8 Sprint 4 closure evidence

- Convert `docs/quality/sprint-4-closure-report.md` from a pre-merge plan into final historical evidence.
- State that PR #19 merged and run 81 passed.
- Preserve the truth that a dedicated statutory API IT and dedicated statutory browser scenario were absent.
- Do not fabricate manual-smoke tester/reviewer values.
- Mark `docs/runbooks/sprint-4-manual-smoke.md` as a historical reusable checklist whose completed signed record is not committed.

---

# 7. Proposed exact thread-registry rows

```markdown
| Thread 1 | RECOVERY/HANDOFF — active cross-thread governance consolidation | Original design and Sprint 0–2 recovery; living design bootstrap; current cross-thread reconciliation | Historical PR #20 merged; proposed documentation branch `docs/cross-thread-reconciliation`; no active PR yet | Approved documentation/governance allow-list only; no application files and no migration | `docs/history/thread-1-decision-extract.md` plus approved cross-thread restart record | Apply approved living-document reconciliation, verify, then separately request publication actions |

| Thread 2 | CLOSED — historical Sprint 2 and early Sprint 3 implementation owner | Completed Sprint 2 configuration/employee-payroll closure and delivered Sprint 3 cycle, population, snapshots, deterministic calculation and controlled recalculation foundation through V026 | PR #3 merged; PR #18 later merged; Thread 2 exit `db644298ab3197a6931cd9c6b8d9875ef30d28c5` | None | `docs/history/thread-2-reconciliation.md` updated from approved record | Historical reference only; no implementation |

| Thread 3 | CLOSED — historical Sprint 3 completion owner | Completed recalculation application path, execution/draft-payslip UI, browser authentication, Playwright E2E and PR #18 closure | `feature/sprint-3-payroll-execution`; PR #18 merged; final head `ebd2603d91551c6f9e60dc57e2d3500948015703`; merge `73c356662b1888194a72c7006a66bd91443550ca` | None | `docs/history/thread-3-reconciliation.md` | Historical reference only; no implementation |

| Thread 4 | CLOSED — historical Sprint 4 implementation owner | Implemented V027–V030 jurisdiction-neutral statutory rules, profiles, evaluation, ledger, balances, reconciliation, API/UI and exact-money correction | `feature/sprint-4-statutory-deductions`; PR #19 merged; final head `b2a220461cf5ba581b5f67e7619ec146bf7982ed`; merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` | None | `docs/history/thread-4-reconciliation.md` | Historical reference; unresolved automation debt transferred to new implementation owner |

| Thread 5 | RECOVERY/HANDOFF — no write ownership | Sprint 4 closure transition, process audit and multi-thread recovery; identified missing statutory HTTP/PostgreSQL IT | Historical PR #19 merged; no active Thread 5 branch or PR verified | None | `docs/history/thread-5-reconciliation.md` | Hand approved automation-closure scope to Thread 6, then close |
```

## Proposed Thread 6 registration after approval

```markdown
| Thread 6 | IMPLEMENTATION OWNER — S4-06A statutory API integration closure | Add real Spring Boot/PostgreSQL 17 statutory HTTP lifecycle integration test without production or migration changes | Proposed branch `quality/s4-06a-statutory-api-integration`; no PR until separately authorised | `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java` and approved evidence/handoff files only | Start checkpoint to be created after live local inspection | Implement and verify S4-06A; stop if a production defect is exposed |
```

---

# 8. Exact next bounded implementation increment

## 8.1 Identity

| Field | Proposal |
|---|---|
| Capability | Sprint 4 Automation Closure |
| Story | **S4-06A — Statutory API Integration Closure** |
| Owner | New **Thread 6** |
| Branch | `quality/s4-06a-statutory-api-integration` |
| Base | Current synchronized `main` after governance reconciliation |
| Migration reservation | **NONE** |
| Production-code changes | Prohibited initially |
| Dependency changes | Prohibited initially |
| OpenAPI/Keycloak/frontend changes | Prohibited initially |

## 8.2 Rationale

The statutory module was introduced in PR #19. Its changed-file inventory contains migration integration tests and unit/controller/serialization tests, but no `backend/payroll-boot` statutory `*ApiIT` comparable to existing `PayrollOperationsApiIT` and `PayrollRecalculationApiIT`.

The current `payroll-boot` POM already includes:

- Spring Boot test;
- Spring Security test;
- PostgreSQL Testcontainers;
- Flyway core/PostgreSQL;
- Failsafe `integration-test` and `verify`.

Therefore a new `*IT` test is automatically covered by the existing Maven verification path and should not require a POM or CI change.

## 8.3 Exact initial file allow-list

Implementation files:

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java` — new.

Evidence/state files, updated only at durable boundaries:

2. `docs/quality/s4-06a-statutory-api-integration.md` — new.
3. `docs/runbooks/project-continuation-handoff.md` — current checkpoint only.
4. `docs/governance/thread-registry.md` — Thread 6 ownership/status only.

The backlog row should be added during the preceding governance reconciliation, not opportunistically by the implementation thread.

Any need to modify another file is a scope change requiring an explicit stop, evidence and approval.

## 8.4 Acceptance criteria

The new integration test must:

1. start PostgreSQL 17 Testcontainers;
2. create the least-privilege owner/migrator/runtime role model;
3. migrate V001–V030 using `payroll_migrator`;
4. run the Spring Boot application test context as `payroll_app`;
5. seed clearly synthetic two-tenant organisation, payroll, calculation and statutory data;
6. call actual statutory HTTP endpoints through Spring Security and the application/service/repository/database-function path;
7. exercise:
   - evaluation;
   - evaluation/result reads;
   - ledger posting;
   - ledger-entry reads;
   - balance reads;
   - reconciliation reads;
   - remittance-summary reads;
   - signed correction;
8. verify exact quoted decimal-string money, including `-10.1250` and `0.1000`;
9. verify database evidence:
   - immutable input/evaluation/result history;
   - append-only ledger;
   - signed correction delta;
   - zero-variance reconciliation;
   - expected remittance summaries;
   - one audit and one outbox effect for exact replay;
10. verify idempotent replay does not duplicate business, audit or outbox effects;
11. verify stale `If-Match` returns conflict;
12. verify same idempotency key with a different payload is rejected;
13. verify unauthenticated access returns 401;
14. verify missing command/read permissions return 403;
15. verify cross-tenant evidence is hidden through secure empty/404 behavior;
16. verify numeric JSON money tokens are rejected;
17. verify zero/zero correction and short reason are rejected;
18. leave V001–V030, production code, OpenAPI, Keycloak and frontend unchanged.

## 8.5 Verification gates

Required:

1. compile/static validation for `payroll-boot`;
2. focused Failsafe execution of `StatutoryApiIT`;
3. confirmation that Failsafe `integration-test` and `verify` phases executed;
4. full Maven reactor `verify`;
5. existing migration/RLS verification;
6. existing OpenAPI validation, because no contract change is expected;
7. `git diff --check`;
8. exact file allow-list review;
9. independent read-only critical review of:
   - statutory lifecycle assertions;
   - decimal exactness;
   - RLS/cross-tenant tests;
   - idempotency/audit/outbox assertions;
   - absence of production/migration changes.

Frontend checks are not required for S4-06A unless frontend files unexpectedly change, which is outside the approved initial scope.

## 8.6 Stop conditions

Stop and request a new bounded decision when:

- the test exposes a production defect;
- a migration change appears necessary;
- an OpenAPI mismatch is discovered;
- permissions or Keycloak mappings must change;
- the existing application cannot be seeded without changing production code;
- dependency or CI workflow changes appear necessary.

Do not “fix while here.” Record the defect and propose a separate increment.

## 8.7 Planned but not authorised follow-up

**S4-06B — Statutory-specific Playwright E2E** should follow S4-06A. It is not part of the first increment and should receive its own branch/allow-list or be added to the same quality branch only after a separate approval.

---

# 9. Repository update plan

## Phase A Lite — Cross-thread authority/history reconciliation

Branch:

`docs/cross-thread-reconciliation`

Exact allow-list:

1. `docs/governance/thread-registry.md`
2. `docs/runbooks/project-continuation-handoff.md`
3. `docs/quality/sprint-4-closure-report.md`
4. `docs/history/thread-2-reconciliation.md`
5. `docs/history/thread-3-reconciliation.md`
6. `docs/history/thread-4-reconciliation.md`
7. `docs/history/thread-5-reconciliation.md`
8. `docs/history/cross-thread-project-restart-record.md`

This reduced subset preserves the cross-thread restart authority, closes the
historical thread records and unblocks Thread 6. The earlier proposed changes
to AGENTS, README, master design, decision register, backlog and manual-smoke
wording are deliberately deferred. Their known conflicts remain recorded and
may be handled as non-blocking housekeeping after S4-06A.

No application code, migration, dependency, OpenAPI, Keycloak, frontend or CI
file is included.

## Phase B — S4-06A implementation

Create Thread 6 and branch only after Phase A is merged and current local state is verified.

Initial allow-list is the four files specified in section 8.3.

---

# 10. Actions prohibited until separately authorised

The following remain prohibited:

- modifying any repository file;
- starting application implementation;
- creating or switching a branch;
- reserving V031;
- modifying V001–V030;
- modifying dependencies;
- staging;
- committing;
- pushing;
- creating or updating a pull request;
- marking a PR ready;
- merging;
- deleting historical branches;
- changing branch protection;
- claiming current local state without native Git evidence;
- claiming Sprint 4 is fully automated before S4-06A and S4-06B evidence.

Authorization for one action does not imply authorization for the next.

---

# 11. Approval checklist

The project owner must approve or amend each item:

- [ ] Accept current repository HEAD `4b5da975...` and product baseline `def3dd2e...` as separate facts.
- [ ] Accept Thread 1 as temporary governance owner for one documentation reconciliation.
- [ ] Accept Threads 2, 3 and 4 as CLOSED.
- [ ] Accept Thread 5 as RECOVERY/HANDOFF with no write ownership, closing after Thread 6 handoff.
- [ ] Accept a new Thread 6 as the sole implementation owner.
- [ ] Approve the Phase A branch name `docs/cross-thread-reconciliation`.
- [x] Approve the reduced 8-file Phase A Lite authority/history allow-list as a subset of the previously approved scope.
- [ ] Approve UTF-8/mojibake correction as part of Phase A.
- [x] Approve MDR-021 through MDR-024.
- [x] Approve MDR-025 after the Phase A v1.0 scalar-output failure.
- [ ] Approve backlog stories S4-06A and S4-06B.
- [x] Approve S4-06A as the next bounded implementation increment.
- [x] Approve branch `quality/s4-06a-statutory-api-integration`.
- [x] Confirm S4-06A reserves no migration and cannot modify production code initially.
- [x] Accept S4-06B as planned but not yet authorised.
- [ ] Supply current local branch/HEAD/tree/index evidence before Phase A writes.
- [ ] Keep stage, commit, push, PR update and merge as separate later authorisations.

---

# 12. Final reconciliation ruling

The project implementation through Sprint 4 is not lost and does not require reconstruction. The inconsistency is concentrated in governance metadata, historical thread ownership and incomplete quality evidence.

The correct restart path is:

1. reconcile living documents on a documentation-only branch;
2. merge that authority correction after verification;
3. start a fresh Thread 6;
4. complete S4-06A without migration or production-code changes;
5. complete or schedule S4-06B;
6. only then select the next product feature from the master design and an approved expanded backlog.

---

## Phase A execution simplification

Three custom-updater attempts failed for recurring script-engineering reasons:
PowerShell scalar output, brittle text preimages, and a wrapper that treated
harmless CRLF warnings as failure while also generating trailing whitespace.
All failed attempts rolled back before publication.

The project therefore retired the custom Phase A updater. Phase A Lite uses
deterministic full-file payload copy for eight authority/history files. Native
commands are judged by exit code, trailing whitespace is rejected before
packaging, and the remaining six documentation cleanups are deferred so S4-06A
can proceed after the authority/history merge.
