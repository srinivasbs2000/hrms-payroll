# HRMS Payroll Project Continuation Handoff

**Repository revision:** Sprint 4 closure alignment<br>
**Updated:** 26 July 2026<br>
**Repository:** `srinivasbs2000/hrms-payroll`<br>
**Local repository:** `C:\dev\hrms-payroll`<br>
**Mandatory status:** This is the first project document to read in every continuation thread. It is subordinate only to current local and remote repository evidence.

## 1. Mandatory introspection entry point

Before answering a continuation request, designing an increment, changing a dependency, generating a migration, producing a verifier, publishing code, or recommending a merge:

1. Locate and read **`docs/runbooks/project-continuation-handoff.md`**.
2. Inspect the local repository at `C:\dev\hrms-payroll`: branch, HEAD, index, status, changed files and complete diff.
3. Inspect the live GitHub repository, active branch and PR.
4. Read the exact repository files governing the requested area: `AGENTS.md`, README, ADRs, backlog, migrations, OpenAPI, tests, runbooks, CI and security policies.
5. Compare this handoff with live evidence. Current repository evidence overrides an older handoff fact.
6. Record disagreements as **DOCUMENTATION CONFLICT**. Do not silently reconcile them.
7. Mark unavailable evidence as **NOT VERIFIED**. Do not infer filenames, routes, migrations, statuses, test results or architecture decisions.
8. Present materially different valid options to the user before selecting one.
9. Do not reconstruct project state from conversation memory alone.

### Evidence labels

| Label | Meaning |
|---|---|
| VERIFIED - REMOTE | Confirmed from the connected GitHub repository, PR, commit, workflow or committed file. |
| VERIFIED - REPORT | Confirmed from an uploaded local verification report or log. |
| THREAD-RECORDED - LOCAL | Reported for the local checkout but not independently visible remotely. |
| DERIVED | Logical conclusion from identified verified evidence. |
| DESIGN BASELINE | Approved decision not necessarily implemented yet. |
| DOCUMENTATION CONFLICT | Authoritative project sources disagree and require explicit resolution. |
| NOT VERIFIED | Exact evidence is unavailable; no assumption is permitted. |

## 2. Source precedence

1. Current local working tree and uncommitted diff.
2. Current remote branch, commit, PR and CI.
3. Committed migrations, code, tests, OpenAPI, ADRs, backlog, README, `AGENTS.md`, CI and security policies.
4. Uploaded verification reports and logs.
5. This running handoff for sequence, decisions and known hazards.
6. Conversation summaries only as locators.

## 3. Verified continuation card

| Item | Current fact | Evidence class |
|---|---|---|
| Main baseline | `73c356662b1888194a72c7006a66bd91443550ca` | VERIFIED - REMOTE |
| Active branch | `feature/sprint-4-statutory-deductions` | VERIFIED - REMOTE |
| Active PR | PR #19, open, mergeable, not merged | VERIFIED - REMOTE |
| Current remote head | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` | VERIFIED - REMOTE |
| Current PR size | 6 commits, 51 changed files | VERIFIED - REMOTE |
| S4-05B commit | `feat(statutory): add execution and evidence workspace` | VERIFIED - REMOTE |
| CI | `payroll-baseline` run #77, ID `30197879363`, completed successfully | VERIFIED - REMOTE |
| CI gates | Dependency review, Flyway/RLS, frontend test/build/audit policy, auth smoke, Maven verify, browser E2E, SBOM, secret scan and OpenAPI all passed | VERIFIED - REMOTE |
| Committed migrations | V001-V030; immutable | VERIFIED - REMOTE |
| Sprint 4 delivered | S4-01/V027, S4-02/V028, S4-03/V029, S4-04/V030, S4-05A API and S4-05B UI | VERIFIED - REMOTE |
| Merge status | Do not merge PR #19 until closure alignment and final review are complete | PROJECT CONTROL |
| Next feature increment | Not selected from verified backlog evidence | NOT VERIFIED / JOINT DECISION |

## 4. Sprint 4 completion ledger

| Increment | State | Delivered |
|---|---|---|
| S4-01 / V027 | Committed and green | Statutory rule identities, versions, portions and controlled lifecycle. |
| S4-02 / V028 | Committed and green | Employee statutory profiles and exact assignment lineage. |
| S4-03 / V029 | Committed and green | Deterministic statutory classification/evaluation and immutable evidence. |
| S4-04 / V030 | Committed and green | Append-only ledger, balances, reconciliation and remittance preparation. |
| S4-05A | Committed and green | Evaluation, posting and correction API; permissions, OpenAPI and evidence reads. |
| S4-05B | Committed at `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` and CI green | Permission-aware React statutory execution and evidence workspace. |

## 5. React Router scoped audit decision

- Keep `react-router-dom` and transitive `react-router` pinned to `7.18.1`.
- The executable policy is `frontend/payroll-web/scripts/verify-npm-audit.mjs`.
- CI invokes it from `.github/workflows/ci.yml`.
- The policy permits only `GHSA-qwww-vcr4-c8h2` while the frontend remains declarative `BrowserRouter` and outside RSC, Framework, Data and server modes.
- It rejects additional high/critical advisories and prohibited dependencies/source patterns.
- Review deadline: **2026-10-31**.
- The withdrawn downgrade to 7.11.0 must never be used.
- Raw `npm audit` is diagnostic input, not the final architecture-aware decision.

## 6. Verified documentation conflicts after S4-05B

### Conflict A - PR metadata is stale

PR #19 still has the title **â€œSprint 4: V027 statutory rule foundationâ€** and a body that describes only the first statutory-rule increment. The branch now contains all Sprint 4 increments through S4-05B.

**Required resolution:** update the PR title and body only after the full Sprint 4 closure scope and evidence are agreed.

### Conflict B - README is stale

The committed README still describes a Sprint 1-3 vertical slice and says statutory deductions and tax are intentionally excluded. Sprint 4 statutory foundation, execution API and UI are now committed and green.

**Required resolution:** update README scope, execution flow, verification instructions and exclusions. Retain explicit exclusions for jurisdiction-specific rules, filing, payment/settlement, legal/final payslips, retro and off-cycle payroll unless separately approved.

### Conflict C - AGENTS.md scope is stale

`AGENTS.md` still says the approved starter scope must not add statutory deductions such as PF, ESI, professional tax or salary TDS. The repository now contains a country-neutral statutory foundation and execution capability.

**Required resolution:** distinguish the implemented country-neutral statutory engine from still-excluded jurisdiction-specific rates, filings, legal calculations and settlement. Do not weaken security, tenant, review or verification rules.

### Conflict D - repository backlog is stale or incomplete

The README identifies `backlog/` as Sprint 0-3 only. No verified Sprint 4 closure backlog item was located through the connected repository search.

**Required resolution:** inspect the local `backlog/` directory and exact files before selecting or naming the next story. Record the outcome here.

### Conflict E - running handoff is not yet repository-resident

This file is the approved repository-resident running handoff location.

**Resolution:** the approved repository path is `docs/runbooks/project-continuation-handoff.md`.

## 7. Current stage - Sprint 4 closure alignment

**APPROVED stage:** align README, repository instructions, backlog, closure evidence, manual smoke, this handoff and the local full-regression script before PR metadata or merge decisions.

No invented closure story ID is used. Verified implemented Sprint 4 increments are recorded with blank Story Points because no historical estimates were found.

### Candidate closure scope for joint approval

1. Add the running handoff to an agreed repository path.
2. Update README from Sprint 1-3 to Sprint 1-4 current state.
3. Update `AGENTS.md` to distinguish generic statutory infrastructure from jurisdiction-specific payroll law.
4. Add or update the Sprint 4 backlog/closure report based on the actual local backlog structure.
5. Add a Sprint 4 manual smoke/closure runbook if not already present.
6. Perform an independent critical review of the full PR diff.
7. Update PR #19 title/body to describe the complete Sprint 4 delivery and evidence.
8. Decide merge only after all closure gates pass.

Items 1-5 change repository files and require an exact allow-list after local inspection. Item 7 is a GitHub metadata write and requires explicit authorization. Item 8 remains a separate explicit decision.

## 8. No-guesswork implementation protocol

1. Start from verified local and remote state.
2. Read exact repository contracts before design.
3. Search for existing executable policy before proposing replacement logic.
4. Use repository-native validators for ecosystem-native files.
5. Label statements VERIFIED, DERIVED or NOT VERIFIED.
6. Ask before choosing among materially different valid options.
7. Keep each increment bounded with an exact file allow-list.
8. Apply without Git writes; verify; review evidence; publish only after explicit authorization.
9. Never merge a sprint PR before all increments and closure verification complete.
10. Update this handoff after every committed increment and before every thread transition.

## 9. Cumulative failure-learning register

| ID | Failure | Permanent prevention |
|---|---|---|
| T4-001 | PowerShell variable followed by colon parsed as scope | Use `${variable}` before punctuation and parser-check scripts. |
| T4-002 | Restricted trigger could not lock parent row | Use narrowly scoped SECURITY DEFINER functions with fixed search_path and revoked PUBLIC. |
| T4-003 | Native output treated as character/scalar | Capture native output with `@(...)`, check cardinality, then cast. |
| T4-004 | Unqualified `version_no` caused ambiguity | Qualify mutation targets and visible query columns. |
| T4-005 | Fixture reused a natural key | Maintain an explicit test-fixture identity matrix. |
| T4-006 | Expected SQL error aborted transaction | Use a JDBC savepoint for every expected database error. |
| T4-007 | Failure evidence was trapped in timestamped logs | Copy a concise failure report to Downloads root. |
| T4-008 | INSERT-only chain rule ran on UPDATE | Guard creation-chain logic by `TG_OP` and test lifecycle updates. |
| T4-009 | Draft SQL assumed a nonexistent field/status | Audit every referenced element against committed schema. |
| T4-010 | PL/pgSQL variable collided with query column | Use `p_`/`v_` prefixes, aliases and record-qualified fields. |
| T4-011 | Hosted CI infrastructure timeout | Classify infrastructure failures before changing code. |
| T4-012 | Replacement ledger reversed excessive history | Reverse only the active posting epoch. |
| T4-013 | Empty pipeline result lost array identity | Wrap collection output at receiving assignment with `@(...)`. |
| T4-014 | Inline `if` used inside hashtable property expression | Compute conditional values before constructing hashtables. |
| T4-015 | Generic .NET list triggered PowerShell binder error | Prefer plain PowerShell arrays and raw evidence. |
| T4-016 | Scanner suggestion conflicted with architecture | Use bounded reviewed security decisions; never uncontrolled `npm audit fix`. |
| T4-017 | Existing policy not discovered before remediation | Search policies, ADRs, CI and handoffs before changing dependencies or gates. |
| T4-018 | Workflow display name treated as filename | Resolve exact committed paths; never derive from display names. |
| T4-019 | PowerShell parsed npm lockfile v3 unnecessarily | Delegate ecosystem-native parsing to repository-native validator. |
| T4-020 | New-thread state reconstructed without a running authority | Begin every thread with this handoff and then validate against live evidence. |
| T4-021 | Repository documentation drifted behind delivered Sprint 4 | Treat README, AGENTS, backlog and PR metadata alignment as a closure gate. |

## 10. New-thread continuation protocol

A continuation thread must receive or locate this exact document before project work begins.

Paste-ready starter:

```text
Continue the HRMS Payroll project. First locate and read HRMS_Payroll_Project_Running_Handoff. Do not reconstruct state from conversation memory and do not guess.

Then inspect the local repository at C:\dev\hrms-payroll: branch, HEAD, Git index, status, changed files and complete diff. Inspect the live GitHub repository srinivasbs2000/hrms-payroll and PR #19. Read the exact repository policies, README, ADRs, backlog, migrations, tests, OpenAPI, runbooks and CI files relevant to the next action.

Classify each material statement as VERIFIED, DERIVED or NOT VERIFIED. Record conflicts instead of silently resolving them. Ask before choosing among materially different valid options. Do not create a new PR, rewrite V001-V030, change dependencies, stage, commit, push, update PR metadata or merge unless the running handoff and explicit user authorization support that action.
```

## 11. Current stop condition

S4-05B is committed and CI green. The closure-alignment files are the current bounded increment. PR #19 remains open and unmerged.

Before publication or merge:

- inspect the local backlog directory and repository documentation;
- agree the exact closure scope and repository path for this handoff;
- define an exact file allow-list;
- verify locally before any Git write;
- keep PR metadata and merge as separate explicitly authorized actions.


## 12. Closure alignment approval

The user approved:

- repository handoff path `docs/runbooks/project-continuation-handoff.md`;
- verified Sprint 4 backlog rows with blank Story Points; and
- a seven-file closure package covering README, AGENTS, backlog, closure report, manual smoke, this handoff and `scripts/verify-sprint-4.ps1`.

This approval does not authorise staging, commit, push, PR metadata update or merge.

## 13. Checkpoint and thread-load policy

The project uses a continuously maintained running handoff plus sparse,
transition-based checkpoints.

### Create or update a checkpoint only when

1. an architecture or scope decision is approved and changes the execution plan;
2. an implementation phase reaches a durable verified state;
3. a material failure changes architecture, scope, security posture or the next action;
4. a commit is published and its exact CI result is known;
5. critical review or manual smoke changes merge readiness; or
6. a thread transition is imminent or context-loss risk is high.

### Do not create a checkpoint for

- routine commands or status messages;
- retries that keep the same scope and design;
- a verifier correction that does not change project state;
- duplicate evidence already captured in a verification summary; or
- exploratory diagnostics that do not alter the plan.

### Failure handling

Add a failure-register entry without creating a new checkpoint when the failure
is local, understood and resolved inside the same approved phase. Create a
checkpoint only when the failure changes the plan, exposes a new risk, leaves
the repository in a materially different state or must be carried into another
thread.

### Minimum checkpoint content

Each checkpoint records the branch, HEAD, PR state, working-tree/index state,
approved scope, immutable migrations, decisions, evidence, blockers, next
authorised action, prohibited actions and only the failure-register delta since
the prior checkpoint.

When adjacent gates belong to the same phaseâ€”for example automated
verification, critical review and manual smokeâ€”combine them into one phase
checkpoint unless an intervening material failure changes the plan.

The running handoff remains the first document for every continuation. A
checkpoint is an immutable transition snapshot; it never replaces the running
handoff or the detailed verification evidence.

<!-- LIVING-DESIGN-AUTHORITY:START -->
## Living master-design authority and superseding remote checkpoint

The approved product and architecture authority is
`docs/design/hrms-payroll-master-design.md`. Material decisions are indexed in
`docs/design/decision-register.md`.

This running handoff continues to own current branch, PR, CI, working-tree,
blocker and next-action state. It must not duplicate the complete master design.

### Superseding remote checkpoint â€” 1 August 2026

The earlier continuation card in this document predates the Sprint 4 merge.
Connected GitHub evidence established:

- PR #19 is merged;
- merge commit and current verified `main` baseline:
  `def3dd2e212f85c440eee5497e292be2f1f2bf64`;
- V001â€“V030 are committed and immutable;
- no post-Sprint-4 active feature branch or pull request is established by this
  bootstrap and must be marked `NOT VERIFIED` until live inspection.

Current local working-tree and branch state must still be inspected before any
write.

All project threads must register ownership in
`docs/governance/thread-registry.md` and follow
`docs/governance/thread-maintenance-protocol.md`. Only one thread may own
overlapping writes or the next Flyway migration number.
<!-- LIVING-DESIGN-AUTHORITY:END -->
