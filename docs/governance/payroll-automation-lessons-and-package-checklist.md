# Payroll Automation Lessons and Package Release Checklist

**Status:** STANDING GOVERNANCE AUTHORITY

## 1. Purpose

Turn execution failures into permanent project controls so another thread does
not have to rediscover them.

## 2. Package design rules

Prefer, in this order:

1. standard `git` / `gh` operations;
2. deterministic complete-file payloads;
3. marker/heading-bounded deterministic transformations;
4. custom updater logic only when simpler mechanisms cannot satisfy the bounded
   change safely.

After two failures in one custom update path, stop extending it and redesign the
operation using simpler primitives.

## 3. Mandatory PowerShell semantic tests

Parser success is necessary but not sufficient.

Before release, every executable package must test the behaviors it depends on.

### PS-01 — Script invocation and exit status

Do not assume `$LASTEXITCODE` exists after invoking another `.ps1` with `&`.

For PowerShell-script invocation:

- use normal PowerShell error semantics / `$?` only where appropriate;
- for controlled external process gates, own the process object and read its
  `ExitCode`.

### PS-02 — Native process ownership

For Git, Maven, npm, npx, Java, Docker, `gh`, and similar tools:

- launch through one `System.Diagnostics.Process`;
- capture `StdOut`, `StdErr` and `ExitCode` separately from that process;
- never use a stale/global `$LASTEXITCODE` as authoritative evidence.

Test a deliberate non-zero child process before repository mutation.

### PS-03 — Array argument preservation

An array of file paths must become multiple native arguments, never one
space-joined pathspec.

Release test must include:

- one path;
- multiple paths;
- a path containing spaces;
- `--` separator behavior where used.

### PS-04 — Output cardinality

Helpers used for parsed output must prove:

- zero lines;
- one line;
- multiple lines.

Reject nested-array/sentinel behavior such as `System.Object[]`.

### PS-05 — stdout/stderr separation

Warnings such as Git line-ending notices must not enter:

- changed-path lists;
- branch/SHA comparisons;
- migration ownership;
- allow-list comparisons;
- JSON parsing.

### PS-06 — Encoding and line endings

Generated text must prove:

- valid UTF-8;
- intended BOM policy;
- intended LF/CRLF policy;
- one terminal newline;
- no trailing whitespace;
- no mojibake.

## 4. Repository mutation preflight

Before the first mutation:

- confirm repository root;
- confirm current branch;
- confirm exact local/base SHA;
- fetch and confirm exact `origin/main`;
- verify remote availability with bounded retry;
- verify working tree/index state required by the operation;
- verify target branch absence/presence;
- verify source blob/file hashes when a deterministic patch depends on them;
- verify exact allowed paths;
- verify migration reservation state.

External network failure must fail closed before mutation.

## 5. Post-application recovery rule

If authorized files were correctly applied but a later validation step fails:

- do not blindly reapply;
- do not automatically rollback;
- inspect exact branch, HEAD, index, changed paths and payload hashes;
- preserve correct authorized work;
- create a bounded resume operation from the observed state.

## 6. GitHub/CI failure classification

Before changing application code for a CI failure, classify where it failed:

### Infrastructure/setup failure

Examples:

- `Set up job` fails;
- action metadata cannot be downloaded;
- runner provisioning failure;
- `Service Unavailable`;
- GitHub Actions status incident.

Treatment: do not change product code. Wait/retry after service recovery.

### Tool/bootstrap failure

Examples:

- Java/Node setup action succeeds but dependency/bootstrap command fails.

Treatment: inspect tool/config/dependency evidence.

### Application verification failure

Examples:

- Maven compile/test;
- React test/build;
- migration test;
- OpenAPI validation;
- security/tenant test.

Treatment: bounded technical diagnosis.

This classification must be stated in evidence.

## 7. Publication controls

Commit, push, PR creation, ready-for-review, merge and branch deletion are
separate actions unless one explicit authorization deliberately bundles them.

Every package must state which are authorized and which are prohibited.

## 8. Required release checklist

Before providing an executable package:

- [ ] approved scope/allow-list reconciled;
- [ ] every `.ps1` parsed with the repository parser;
- [ ] semantic process exit-code test passed;
- [ ] one/many native argument test passed;
- [ ] zero/one/many output test passed;
- [ ] stdout/stderr separation test passed;
- [ ] Windows path-with-spaces test passed;
- [ ] remote preflight occurs before mutation;
- [ ] exact base/head fail-closed guards present;
- [ ] `git diff --check` planned;
- [ ] migration SQL boundary explicitly checked;
- [ ] resume behavior documented;
- [ ] authorized GitHub actions explicit;
- [ ] prohibited GitHub actions explicit;
- [ ] expected evidence/log file explicit.

## 9. Learning capture trigger

Add a new lesson only when a failure reveals a generalizable control.

Do not add one-off noise. A lesson belongs here when it can prevent the same
class of failure in another package or thread.

## 10. Artifact-release validation is evidence, not delegation

A package is not release-ready merely because it contains a validator that will
run on the project owner's machine.

Before release:

- run the real parser/runtime validation in the assistant environment whenever
  that runtime is available;
- if the required runtime is unavailable, prefer a simpler mechanism that can
  actually be validated;
- if neither is possible, state that the artifact is not release-ready rather
  than shifting first validation to the project owner.

A downstream fail-closed gate still protects the repository, but it does not
replace pre-release artifact validation.

## 11. Patch provenance must use exact repository content

Never generate a Git patch from truncated search snippets, normalized synthetic
files or reconstructed one-line placeholders.

A patch must be produced from the exact repository blob/tree it claims to
modify and must pass `git apply --check` against that exact baseline before
release.

After a patch-provenance failure, prefer complete-file payloads guarded by exact
source blob hashes rather than attempting another synthesized patch.

## 12. Cross-repository main-bound CI requires ordered publication

When a downstream repository's hosted integration job explicitly checks out an
upstream repository at `main`, the downstream PR cannot be required to become
green before the upstream product PR merges.

Publication packages must:

- identify cross-repository jobs bound to an upstream default branch;
- merge the upstream product PR only after its own hosted gates are green;
- rerun, rather than dummy-commit, the downstream integration workflow after
  upstream `main` advances;
- require the downstream integration job to prove it consumed the new upstream
  main before downstream merge; and
- never interpret the expected pre-upstream-merge failure as a downstream
  product regression.

P5-FBA-01 exposed this defect when the standalone UI PR correctly rejected an
upstream backend `main` that still ended at V034 and lacked the FBA runtime.

## 13. Secret-scan false positives require exact suppression

When a secret scanner flags deterministic synthetic test data:

- inspect the scanner rule, file, commit and exact finding before suppression;
- confirm that the value is test-only and is not a credential, token, password,
  encryption key or production secret;
- prefer exact scanner fingerprints in the repository's existing ignore
  mechanism;
- never disable the scanner rule or broadly ignore the test path merely to make
  CI green; and
- preserve the original test semantics unless the test value itself is unsafe.

P5-FBA-01 used exact Gitleaks fingerprints for ten synthetic
`Idempotency-Key` literals after the hosted SARIF/job evidence proved they
were false positives.

## 14. Hosted CI registration and external dependency failures are not product defects

Publication automation must treat hosted-check registration as eventually
consistent. After a push, poll for check registration before invoking a
fail-fast watcher; an immediate `no checks reported` response is not evidence
that CI failed or did not trigger.

When a hosted job fails before project tests execute because an external
artifact repository returns a transport/rate-limit error such as HTTP 429:

- classify the failure from the hosted job log before changing product code;
- require evidence that the intended module/tests were not executed;
- rerun only the failed hosted job/run when the other gates remain valid; and
- never create a source-code or migration change solely to compensate for an
  external package-repository rate limit.

P5-FBA-01 exposed both conditions during G06 publication: GitHub check
registration lag caused an early resume stop, and Maven Central HTTP 429 stopped
the Flyway/RLS job before the database-migrations module ran.
## 15. Windows worktrees and Docker-mounted POSIX scripts

A Git working tree on Windows may convert an LF-only tracked shell script to
CRLF even when the committed blob is correct. When that working copy is
bind-mounted into a Linux container, a shebang can become `bash\r` and fail
before the application or migration under test starts.

For disposable cross-platform worktrees:

- verify the committed blob identity before changing the working copy;
- normalize only Docker-mounted POSIX scripts to LF when the checkout policy can
  introduce CRLF;
- assert no carriage-return byte remains before container startup; and
- never treat this disposable normalization as a source change.

P5-FSR-01 exposed this during isolated PostgreSQL bootstrap on Windows.

## 16. Failure cleanup must run before process exit

Do not invoke `System.exit`, `exit`, or an equivalent terminal process action
from a catch/failure branch when cleanup is implemented in `finally`.

A deterministic package that owns Docker services, temporary worktrees or other
external state must:

- record the primary failure;
- run cleanup in a true finally path;
- make cleanup failure visible in the final exit result; and
- verify stale state can be detected and removed on resume.

P5-FSR-01 exposed this when an early browser-runner failure could otherwise
leave an isolated Docker/worktree state behind.

## 17. One runtime date authority must span application and database sessions

If application lifecycle rules use an injected `Clock` while SQL casts
`timestamptz` to `date`, JVM/JDBC/PostgreSQL session time zones can create
different business dates around midnight.

Runtime design and tests must:

- define one authoritative application time zone;
- initialize application database sessions to compatible date semantics;
- avoid making the browser guess the server approval date; and
- include a cross-midnight test with a non-authoritative host/JVM zone.

P5-FSR-01 kept the existing UTC application `Clock` and aligned Hikari
PostgreSQL sessions to UTC after browser E2E exposed the mismatch.
## 18. Heterogeneous target baselines require per-target proof

A bulk updater must never infer that related files share the same textual anchor,
field layout or test fixture merely because they belong to the same capability.

Before release:

- pin every existing mutation target to its exact committed Git blob;
- build the complete transformation for every target before writing the first file;
- distinguish local test fixtures from inherited/shared test support explicitly;
- run non-trivial product mutation and verification in a disposable Git worktree;
- keep the owner branch untouched until compile, targeted tests, full verification
  and exact path-integrity gates all pass; and
- fast-forward the owner branch only to the already validated disposable commit.

P5-FAD-01 G02 v1.0 exposed this class of defect when one standalone API test
declared `JwtDecoder` locally while several related JRF API tests inherited that
fixture from `JrfApiITSupport`. The runner correctly failed before mutation, but
the package should have proven the heterogeneous baselines before release.

## 19. PowerShell 7 runner contracts must be syntax-safe before release

Windows runner generation must target the project owner's actual shell contract,
not generic Windows PowerShell behavior.

For PowerShell 7+ packages:

- invoke with `pwsh`, not `powershell.exe`;
- assume downloaded artifacts are under `$HOME\Downloads` unless the owner says otherwise;
- never interpolate a simple variable directly before `:`; use `${name}` or a
  format expression;
- do not parse `git status --porcelain` by fixed character positions;
- prefer Git commands that return paths only when validating allow-lists; and
- self-report PowerShell version, resolved Downloads path and repository root
  before mutation.

P5-FAD-01 critical-review runners exposed the simple-variable-before-colon parser defect and
fixed-column native-output parsing defect before closure.

## 20. Security test fixtures must represent the runtime authentication state

A unit fixture for an authenticated security principal must exercise the same
authentication object/state that production middleware supplies.

For JWT resource-server authorization:

- distinguish the token from a generic principal representation;
- make authenticated/unauthenticated state explicit in the fixture;
- inspect runtime claims from the actual JWT token object used by the framework;
- retain separate integration coverage through the real security filter chain.

P5-FAD-01 exposed this when the first service-account classifier unit fixture
constructed a `JwtAuthenticationToken` that did not represent the authenticated
runtime state.

## 21. Cleanup failure must not invalidate already-green product evidence

Disposable worktree cleanup is operational hygiene, not product correctness.

If compile, targeted tests, full verification, integrity gates and the repair
commit have already passed:

- record the validated commit before cleanup;
- make cleanup best-effort and separately visible;
- use Windows extended-path deletion where normal deletion hits path-length
  limits;
- prune stale Git worktree metadata; and
- resume from the validated commit instead of rerunning expensive verification.

P5-FAD-01 exposed this after all Maven/runtime gates and the repair commit were
green but normal Windows recursive deletion failed on a long disposable path.

## 22. Capability closure must be manifest-driven, branch-free and aggregate-preflighted

Post-merge capability closure is now governed by
`docs/governance/payroll-capability-closure-standard.md` and the repository-owned
closure engine under `scripts/governance/`.

Permanent controls:

- after the standing engine is installed, normal capability closure artifacts
  contain data only (`closure-manifest.json` plus payloads), not newly authored
  closure `.mjs`/`.ps1` implementations;
- use argument-vector process APIs for every native invocation so Windows paths
  containing spaces remain one argument;
- `git ls-remote` is not local object availability: fetch the exact authority
  before using its SHA as a local tree/commit;
- build closure commits branch-free from the fetched base tree using a temporary
  Git index so the project owner's branch, HEAD and working inventory remain
  untouched;
- validate all source blobs, payload hashes, story-row preconditions, changed
  paths and final governance assertions together before the first publication
  mutation;
- reconcile canonical story rows by exact IDs/field deltas and preserve every
  undeclared row; never infer the repository-wide reporting taxonomy from an
  unrelated CSV metadata field;
- when program-summary totals are updated, use the approved explicit story delta
  or explicit post-closure authority rather than an undocumented heuristic;
- revalidate any existing closure branch/PR against the complete commit contract
  before resuming;
- treat branch creation, PR creation, hosted checks and merge as a resume-safe
  state machine; never replay an already-green boundary merely because a later
  stage failed; and
- if the reusable engine itself lacks a required capability, extend it through a
  separate governance/tooling PR before preparing the closure manifest.

P5-SSC-01 closure exposed the full class of defects: path argument
re-serialization, remote-SHA/local-object confusion, and incorrect assumptions
about how the 450-story reporting categories are represented. Its v4 closure
succeeded only after fetch-first authority, exact story-delta validation,
aggregate semantic preflight, branch-free commit construction, exact hosted
checks and exact-head merge were combined. Those behaviors are now permanent
project controls.

## 23. Executable artifact bytes must be validated after final ZIP extraction

Generated executable artifacts must be validated from the exact bytes delivered
to the project owner, not only from the source strings used to construct them.

Before release:

- create the final ZIP, extract it into a fresh directory, and validate the
  extracted files rather than the pre-ZIP staging directory;
- reject BOMs or unexpected leading bytes unless explicitly required by the
  target runtime;
- for `.cmd`, require the first non-empty command to be the intended launcher
  preamble (normally `@echo off`) and reject stray standalone tokens;
- for PowerShell, require the first executable token to be a valid script
  preamble (`#requires`, `using`, `param`, or an approved script attribute) and
  run the repository parser against the exact extracted file;
- run syntax/self-tests for every Node executable from the extracted package;
- regenerate and verify SHA-256 manifests only after final artifact bytes are
  fixed; and
- never assume a template delimiter, escaping character or language-string
  artifact was removed correctly without inspecting final bytes.

The first Payroll capability-closure-standard installer exposed this when a
literal standalone backslash was emitted as byte zero in the `.cmd`, installer
PowerShell and permanent closure launcher. The failure was packaging-generation
noise, not repository state or missing project context.
