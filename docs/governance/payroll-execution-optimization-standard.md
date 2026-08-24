# HRMS Payroll Execution Optimization Standard

**Status:** Standing governance authority after merge
**Owner:** Project owner
**Scope:** HRMS Payroll execution orchestration, governance, tooling, evidence, publication and closure
**Does not authorize:** product implementation, migration reservation, API/OpenAPI changes, Keycloak changes, UI changes, push, PR creation or merge by itself

## 1. Purpose

Reduce control-plane waste without weakening Payroll product safety. The project keeps repository/live-GitHub authority, migration immutability, RLS/tenant isolation, security/PII controls, maker-checker/SoD, effective-dated history, OpenAPI verification, real-backend browser E2E for UI-required stories, hosted CI, independent critical review where required, exact-head publication and post-merge reconciliation.

Optimization applies to orchestration, repeated discovery, validation reuse, evidence handling and lifecycle governance.

## 2. Structured-data schema-first rule

Any automation that parses repository-owned CSV, JSON, YAML or other structured governance data must:

1. read the exact authoritative revision first;
2. derive and record the live schema/header;
3. export the complete bounded row/object set needed by the gate;
4. map fields semantically by names/keys rather than guessed positions;
5. validate the semantic map before applying business/governance rules;
6. collect all schema/data mismatches before returning `BLOCKED`;
7. fail closed before mutation if the schema or mapping is ambiguous.

Hard-coded guessed positions, undocumented synonyms or inferred semantics are prohibited.

## 3. Generic-engine-before-custom-runner rule

Use the simplest reusable mechanism in this order:

1. repository-owned reusable engine;
2. standard Git/GitHub CLI/Node/Java/PowerShell primitives already governed by the repository;
3. declarative complete-file payloads guarded by exact preimage hashes;
4. small reusable generic engine with declarative manifest;
5. capability-specific orchestration only when the preceding mechanisms cannot safely represent the bounded operation.

After two failures in the same automation/update strategy, another incremental patch is prohibited. Preserve proven state, collect the complete failure set and redesign with a simpler mechanism.

## 4. Authority Snapshot

Every controlled gate should create one machine-readable authority snapshot and downstream packages should consume it with freshness guards.

Suggested repository-local transient artifact:

`.hrms-payroll/authority-snapshot.json`

Minimum fields:

- backend/program remote main;
- UI remote main where relevant;
- local branch/HEAD/index/worktree fingerprints per repository;
- active capability and owner per repository;
- immutable migration range and next migration reservation state;
- selected/relevant detailed-story rows;
- selected/relevant UI-applicability rows;
- contract/OpenAPI authority;
- expected hosted checks;
- exact source blob hashes used by transformations;
- generated timestamp;
- remote freshness guards.

An authority snapshot is evidence/cache, not a replacement for remote freshness verification before mutation/publication.

## 5. Exact-repository release test

Synthetic tests are useful but cannot alone make a mutating package release-ready.

Before executable handoff, validate against the exact authoritative revision and exact source blobs. Where mutation is involved, execute the full claimed flow in a disposable exact-base worktree or representative Git fixture and separately identify any platform-specific checks that cannot be exercised.

State-model tests must cover every state the package claims to support, including clean base, tracked/untracked/staged states when applicable, preserved partial/resume state, prepared commit replay, unexpected extra paths and missing expected paths.

Final ZIP bytes are authoritative for release validation. Extract the final archive and rerun syntax/self-tests/checksum validation on those exact bytes.

## 6. Validation reuse

Expensive green validation may be reused only when the exact product code/tree and relevant validation inputs are unchanged.

Reusable evidence must record:

- exact commit/tree/file hashes;
- validation command and tool/version;
- environment/runtime relevant to the result;
- scope;
- result;
- timestamp;
- invalidation inputs.

Governance-only, evidence-only or publication-only changes do not force Maven/UI/Playwright reruns when the relevant product hashes are unchanged. Rerun only gates invalidated by changed paths, contract authority, environment or test inputs.

Validation reuse never converts a test that did not actually run into green evidence.

## 7. One Evidence Bundle

Each major controlled execution produces one canonical evidence ZIP for owner handoff.

Minimum logical contents:

- `summary.json`;
- `failure-matrix.json`;
- `state.json`;
- `commands.json`;
- `hashes.json`;
- `authority-snapshot.json` when applicable;
- bounded logs and diff/patch evidence when applicable.

Chat should normally contain only the gate result, classification, exact next action and evidence-bundle link/path.

## 8. Machine-readable capability state

Generic engines must validate predecessor state rather than infer lifecycle from prose.

Approved state model:

- `RECOMMENDED`
- `SELECTED`
- `G01_READ_ONLY`
- `IMPLEMENTATION_AUTHORIZED`
- `BACKEND_LOCAL_GREEN`
- `BACKEND_MERGED`
- `UI_LOCAL_GREEN`
- `UI_MERGED`
- `CLOSURE_READY`
- `CLOSED`

A capability may skip a repository-specific state only when that repository is explicitly not applicable. Every transition must identify the authority/evidence permitting it.

## 9. GOV-01 — Governed Fast Lane

The fast lane is authorized only for bounded, coherent, non-legal/non-statutory execution capabilities after a complete R3 selection and a complete read-only G01 verdict.

Eligibility requires all of the following:

- bounded coherent story set;
- selected-story UI applicability revalidated;
- established architecture reuse or one unambiguous bounded architecture decision;
- no unresolved country-specific legal/statutory/tax truth in the selected scope;
- no competing architecture requiring a separate design program;
- established security/RLS/tenant model;
- deterministic migration verdict (`NO_MIGRATION_REQUIRED` or exact additive migration requirement);
- exact backend/UI ownership allow-lists;
- exact test, security, contract and real-browser evidence plan;
- no blocking unknowns after G01.

When eligible, one governance PR may combine:

`R3 selection + G01 verdict + activation + implementation authority`

That combined authority may reserve the next migration only when G01 proves the migration is required and the PR explicitly reserves it.

The fast lane does **not** bundle product implementation, product publication, hosted CI, merge or closure into the governance PR. Product code remains prohibited until the combined authority is merged.

If any eligibility condition fails, use the normal sequence with separate activation/G01 governance.

## 10. GOV-02 — Contract-first backend/UI delivery

After G01 freezes the contract boundary, analyze backend and UI together once as one capability delivery plan covering:

- persistence/API/OpenAPI contract;
- permissions/security/RLS;
- backend service behavior;
- UI routes/pages/workbenches;
- failure states;
- browser journeys;
- test/E2E plan;
- exact per-repository allow-lists.

Backend and UI remain separate repository commits/PRs and retain independent hosted checks. Contract-first planning removes duplicate analysis; it does not collapse repository ownership or quality gates.

## 11. GOV-03 — Generic post-merge closure

Post-merge closure remains mandatory. Use the repository-owned capability closure engine and declarative manifest. Do not create capability-specific closure implementations when the standing engine can represent the operation.

If the standing engine lacks a required capability, extend the engine through a separate governance/tooling change before preparing the closure manifest.

## 12. Script and automation learnings remain mandatory

This standard supplements and does not weaken:

- `docs/governance/hrms-payroll-execution-norm.md`;
- `docs/governance/payroll-automation-lessons-and-package-checklist.md`;
- the current cross-thread Script/Automation Learnings Instructions.

In particular, automation must preserve:

- real parser/runtime validation for generated PowerShell;
- semantic rather than cosmetic prose checks;
- precompute-before-write;
- independent local/remote state validation;
- exact path inventories including tracked + untracked before staging;
- process-owned exit codes and stdout/stderr separation;
- Windows paths with spaces and encoding/CRLF safety;
- network preflight before mutation;
- exact hosted-check names/cardinality where required;
- consolidated failure diagnosis;
- resume from preserved state;
- fail-closed migration/security boundaries;
- no product changes for automation/validator/environment failures.

## 13. Current P5-EOR-01 disposition at adoption

The completed post-EIP R3 reconciliation selected the provisional bounded candidate:

`P5-EOR-01 — Employee Payroll Onboarding, Readiness, Holds & Snapshot Completion`

Selected stories:

- `PLN-E05-003`
- `PLN-E05-004`
- `PLN-E05-017`
- `PLN-E05-019`
- `PLN-E05-020`

Excluded statutory/tax-sensitive stories remain outside this candidate.

At this governance checkpoint:

- P5-EOR-01 is **selected for read-only G01 fast-lane eligibility evaluation only**;
- it is **not activated for product write**;
- backend/UI product owner remains `NONE`;
- V052 remains unreserved;
- the held local commit `8240ad83c4e325590164101620c667bc05713f3a` on `governance/p5-eor-01-activation` must not be pushed/PR'd/merged and is superseded for publication by the optimized governance path;
- its final fast-lane eligibility is pending G01, specifically the deterministic migration verdict and whether employee snapshot completion can remain inside the existing E05/public contract or requires a separately governed E06 contract amendment.

## 14. Next controlled action after this standard is merged

Run one read-only P5-EOR-01 G01 contract/architecture/schema/API/UI verdict using the authority snapshot and R3 evidence.

G01 must return at least:

- final fast-lane eligibility `ELIGIBLE` or `NOT_ELIGIBLE`;
- deterministic migration verdict;
- snapshot/E06 split verdict;
- exact backend/UI allow-lists;
- exact contract/OpenAPI/permission boundaries;
- exact browser journeys and validation plan;
- exact authority invalidation/freshness guards;
- no product mutation.

Only an eligible, merged combined authority may move the capability to `IMPLEMENTATION_AUTHORIZED`.
