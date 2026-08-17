# HRMS Payroll execution and response norm

**Status:** Standing project authority
**Effective:** 2 August 2026
**Owner:** Project owner
**Applies to:** Every current and future HRMS Payroll thread, package, sprint,
implementation increment, recovery action and handoff.

## Default implementation mode

The default is a controlled, non-Codex workflow. ChatGPT prepares deterministic
file payloads and PowerShell launchers; the project owner runs them locally and
returns the resulting evidence. The project owner must not have to restate this
rule in a later conversation.

## Mandatory assistant GitHub read-only mode

Assistant and agent GitHub access is strictly read-only for this project,
regardless of connector schemas or reported repository permissions. Connected
GitHub tools may be used only to inspect repository metadata, branches, commits,
pull requests, reviews, checks, workflow runs, artifacts, issues and diffs.

Every GitHub mutation must be performed by the project owner through an exact
local package using the owner's authenticated `git`/`gh` environment. This
includes branch/ref changes, commits, pushes, repository-file updates, pull
request creation or editing, comments, labels, reviewer actions, workflow
reruns, ready/draft transitions, auto-merge and merge. The assistant prepares
and validates the package, receives evidence, and verifies the resulting remote
state read-only. It must not first attempt a connector mutation.

Codex CLI, Codex desktop, Codex IDE extensions, Codex cloud tasks and API-key-
backed Codex execution are prohibited unless the project owner gives explicit,
task-specific override authorization. Generic approvals such as `Approved`,
`Proceed` or `Start implementation` do not authorize Codex.

## Local path conventions

- Download and extraction root: `$HOME\Downloads`
- Repository root: `C:\dev\hrms-payroll`
- Evidence root: `C:\dev\hrms-payroll-artifacts`
- Generated scripts accept `-RepoRoot` as an alias for `-RepositoryPath`.
- Companion files are resolved relative to `$PSScriptRoot`.
- User-facing commands quote paths and support Windows profile names containing
  spaces.

## Required package behavior

Implementation packages normally include a PowerShell launcher, complete file
payloads or deterministic patches, a manifest, SHA-256 checksums, a runbook,
exact scope/prohibited actions, expected output, failure behavior and evidence
instructions.

## Mandatory PowerShell parser gate

Every generated `.ps1` file, including rollback and evidence scripts, must pass
the real PowerShell parser through
`[System.Management.Automation.Language.Parser]::ParseFile(...)` before it may
be executed. Delimiter balancing, regex scans and visual review do not replace
parser validation.

Every downloadable implementation package must provide a validator-first
execution path and fail closed when that gate was skipped. The repository
validator is `scripts/Test-PowerShellScript.ps1`. Ambiguous interpolation such
as `$Path:` is prohibited; use `${Path}:` or `$($Path):` whenever punctuation or
adjacent characters could change variable parsing.

The package release checklist must validate every `.ps1`, scan for unbraced
`$name:` patterns while excluding valid scoped variables, and record the parser
result separately from runtime tests.

Unless separately authorized, launchers do not stage, commit, push, create or
update a pull request, merge, delete branches, rewrite committed migrations or
modify paths outside the approved allow-list.

## Mandatory native-command cardinality gate

PowerShell native-command helpers must preserve zero/one/many output cardinality.
They must emit flat string records and must not use `return ,$output` for captured
command output. Variable-cardinality callers use `[string[]] @(helper-or-command)`.

Package release validation must exercise empty, one-line and multi-line output
paths and reject nested array sentinel values such as `System.Object[]`. Parser
validation remains mandatory but does not replace this semantic gate.

## Required response behavior

Every HRMS Payroll response ends with:

1. `What you need to do now`
2. `What I need from you`

Every downloadable artifact is classified as executable, implementation
payload, evidence, checkpoint, reference-only or superseded. The response states
whether to download, extract, execute, retain, archive, ignore or delete it; the
exact command; expected output; and the evidence to return. When there is no
action or return item, the response says so explicitly.

## Capability closure execution norm

Post-merge capability closure must use
`docs/governance/payroll-capability-closure-standard.md` and the repository-owned
launcher `scripts/governance/Invoke-PayrollCapabilityClosure.ps1`.

After the standing engine is installed, a normal closure download is data-only:
`closure-manifest.json` plus any complete-file payloads. Do not ship a new
capability-specific closure implementation script for each capability.

The closure runner must be branch-free, fetch-first, exact-base guarded,
aggregate-preflighted, resume-safe and exact-head merged. It must preserve the
project owner's pre-run branch, HEAD and working-tree/index inventory and must
not use `switch`, `reset`, `clean`, `stash`, rebase or force-push as part of
normal closure.

A failure in a later closure stage preserves every already-green boundary.
Engine defects are corrected in a separate reusable governance/tooling
increment; manifest/payload defects are corrected in the data package only.

## History

Sprint 0 and Sprint 1 used Codex and exhausted the available quota in roughly
four to five hours. Subsequent work used bounded ChatGPT-generated payloads and
local scripts successfully. This standing rule preserves that controlled model.

## Network-backed Git preflight

Remote Git validation is a mandatory pre-mutation gate for packages that depend
on GitHub state.

- Test the configured `origin` using `git ls-remote`.
- Verify the exact approved `main` SHA.
- Verify required remote branch absence or presence.
- Retry a bounded number of times for transient HTTPS failures.
- Complete all remote checks before branch creation or file changes.
- Fail closed when the remote cannot be verified.
- Never allow an offline bypass for safety-critical base or branch validation.
- Distinguish external network failures from script defects in evidence.

## Mandatory native-process stream separation

When native-command output is consumed as structured data, stdout and stderr
must be captured separately. Diagnostic warnings, progress messages and line-
ending notices must never be merged into path, SHA, branch, count or allow-list
comparisons.

Git metadata helpers must return separate `StdOut`, `StdErr` and `ExitCode`
fields. Package semantic validation must exercise a command that produces both
stdout data and stderr warnings and prove that only stdout reaches the data
pipeline. `2>&1` is permitted only for human-readable logs whose output is not
parsed or compared.

Recovery after a post-application validation false positive must preserve the
already-applied authorized payload. A resume package validates the exact branch,
base SHA, index state, changed-path set and payload hashes before continuing; it
must not reapply blindly or require an unnecessary rollback.

## Mandatory native process exit-code ownership

Every generated PowerShell execution gate must obtain the exit code from the same launched `System.Diagnostics.Process` object that produced stdout and stderr. `$LASTEXITCODE` must not be used as the authoritative result for Git, Maven, npm, npx, Java, Docker or PowerShell child-process gates.

Semantic validation must launch a process that deliberately exits with a known non-zero code while producing separate stdout and stderr, then verify all three values from the process object before repository mutation.
