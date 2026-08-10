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
