# Payroll Capability Closure Standard

**Status:** STANDING GOVERNANCE AUTHORITY
**Applies to:** Every HRMS Payroll capability whose product implementation has reached merge and requires post-merge reconciliation/closure
**Execution authority:** `scripts/governance/Invoke-PayrollCapabilityClosure.ps1` + `scripts/governance/payroll-capability-closure.mjs`
**Related authorities:** `AGENTS.md`, `docs/governance/hrms-payroll-execution-norm.md`, `docs/governance/payroll-automation-lessons-and-package-checklist.md`

## 1. Purpose

Capability closure is a first-class governed phase. It is not an ad-hoc documentation
cleanup and it is not a second product implementation cycle.

The closure phase must:

- prove the exact merged backend/UI/product authority;
- reconcile only the canonical stories justified by that merged evidence;
- update all standing governance authorities consistently;
- release capability write ownership and migration reservation;
- preserve the operator's local checkout and working state;
- publish one bounded governance closure commit/PR;
- wait for the declared hosted closure checks;
- merge only the exact reviewed closure head; and
- leave next-capability selection/activation as a separate subsequent action.

The permanent objective is one reusable closure engine with capability-specific
data, not a newly coded closure runner for every capability.

## 2. Separation of phases

The following phases are separate and must not be conflated:

1. product implementation;
2. independent product review/verification;
3. product publication and product merge;
4. post-merge capability closure;
5. fresh next-capability selection;
6. next-capability activation.

A closure must not replay already-green product implementation gates merely to
reconstruct evidence. It consumes the exact merged evidence as authority.

A closure must not activate the next capability, reserve the next migration, or
create a new product write owner. Those actions require a separately governed
selection/activation increment.

## 3. Closure readiness gate

Closure may start only when all applicable product repositories are merged and
the required evidence is available.

The closure manifest must identify, for every applicable repository:

- repository identity;
- product PR number;
- exact product head SHA;
- exact merge SHA;
- whether that repository is required for closure.

For UI-required stories, both backend and UI merged evidence are mandatory,
including the required real-backend/browser evidence defined by the UI
applicability authority.

The closure engine verifies live PR/merge identities through the project
owner's authenticated `gh` environment before any GitHub mutation.

## 4. Authoritative closure base

The closure base is the exact live backend/program `main` that contains all
merged product evidence being reconciled.

The engine must:

1. resolve `origin`;
2. resolve live `refs/heads/main`;
3. compare it with the manifest's `expectedBaseSha`;
4. fetch that exact ref before attempting to address the commit/tree;
5. verify the fetched object exists locally; and
6. fail closed if live `main` drifted before closure publication.

`git ls-remote` proves remote identity; it does not prove the commit/tree is
already present in the local object database. A fetch is mandatory before using
a remote-only SHA as a local tree/commit.

## 5. Permanent package shape

After this standard is installed, a normal capability-closure artifact is
**data-only**.

It contains:

- `closure-manifest.json`;
- zero or more complete-file payloads under `payload/`.

It does **not** contain a newly authored capability-specific `.mjs` or `.ps1`
closure implementation.

The project owner executes the repository-owned launcher, for example:

```powershell
& "C:\dev\hrms-payroll\scripts\governance\Invoke-PayrollCapabilityClosure.ps1" `
  -ManifestPath "$HOME\Downloads\<closure-package>\closure-manifest.json"
```

The launcher and engine are versioned once in the repository and are changed
only through a separately reviewed governance/tooling increment.

## 6. Manifest contract

The manifest is declarative authority for one closure. At minimum it records:

- schema version;
- capability identity/title;
- backend/program repository identity;
- exact expected base `main`;
- exact closure branch;
- exact product PR/head/merge authorities;
- story-ledger reconciliation changes when applicable;
- complete-file governance payloads and exact source blob/payload hashes;
- exact changed-path allow-list;
- final semantic text assertions;
- migration boundary after closure;
- closure commit message;
- closure PR title/body;
- exact hosted checks required;
- merge method;
- pass marker.

The manifest must never encode a generic instruction such as "update all
governance files". Paths and expected effects are explicit.

## 7. Canonical story-ledger rule

The detailed story ledger is reconciled semantically, not by guessing reporting
taxonomy from one column.

For every row changed by closure, the manifest identifies:

- exact canonical story ID;
- exact expected pre-values for the fields relied upon;
- exact post-values to set.

The engine must prove:

- the configured ledger cardinality;
- unique story IDs;
- every target row exists exactly once;
- every target row matches its expected pre-values;
- only declared target rows/fields change; and
- all undeclared rows remain unchanged.

A closure must not infer repository-wide reporting categories from unrelated
metadata columns unless an explicit, separately governed mapping exists.

Program-summary totals are reconciled from the approved story delta or explicit
post-closure authority. They are not reconstructed by an undocumented heuristic.

Technical capability controls that are not canonical story rows must not be
invented as new canonical rows during closure.

## 8. Complete governance target construction

All governance targets must be constructed and validated before the first
publication mutation.

Preferred closure input for living Markdown/governance files is a complete-file
payload generated from the exact closure base.

For every payload, the manifest pins:

- repository path;
- exact source blob SHA (or explicit new-file state);
- payload relative path;
- payload SHA-256.

Before commit construction the engine verifies every source blob and every
payload hash.

Final semantic assertions are evaluated across all target authorities
collectively. This prevents a package from updating one "current" checkpoint
while leaving another contradictory current-state section stale.

## 9. Aggregate preflight

Closure preflight is aggregate.

Before push/PR creation, report all detectable contract problems together,
including:

- wrong base;
- wrong/missing product authority;
- source blob drift;
- payload hash drift;
- missing/duplicate story IDs;
- story pre-value mismatches;
- changed-path allow-list mismatches;
- failed final text assertions;
- migration-boundary contradictions.

Do not deliberately stop at the first independent content error when the
remaining checks can safely be evaluated read-only.

No remote publication mutation occurs until the aggregate preflight is green.

## 10. Branch-free commit construction

The standard closure engine must not switch, reset, clean, stash, rebase or
rewrite the project owner's current checkout.

It constructs the closure commit from the exact fetched base tree using a
temporary Git index:

1. `git read-tree <base>`;
2. hash validated target content;
3. update only declared index entries;
4. `git write-tree`;
5. `git commit-tree` with the exact base as parent.

The user's branch, HEAD and working-tree/index inventory are captured before
execution and compared after execution.

Dirty local state may be preserved because closure construction is branch-free,
but an in-progress merge/rebase/cherry-pick or similar repository operation is a
fail-closed condition.

## 11. Closure commit contract

Before publication, the constructed/resumed closure head must prove:

- exactly one parent: the manifest base;
- exact commit subject;
- exact changed-path allow-list;
- `git diff --check` clean;
- exact payload/blob content;
- exact story-ledger mutation contract;
- all final semantic assertions green.

A remote closure branch is never trusted merely because its name matches.
Its commit is revalidated against this contract.

## 12. Resume-safe publication state machine

The standard engine recognizes these states:

### BUILD_PUSH_CREATE

No closure branch and no closure PR exist.

- construct validated closure commit;
- push once without force;
- create one PR.

### RESUME_BRANCH

The exact closure branch exists but no PR exists.

- fetch and revalidate the remote closure head;
- do not rebuild/reapply;
- create the PR from the already-valid head.

### RESUME_PR

One open closure PR exists.

- verify repo/base/head/branch identity;
- revalidate the head commit contract;
- continue hosted checks/merge only.

### ALREADY_MERGED

The matching closure PR is already merged.

- verify the PR head contract;
- verify the merge commit;
- verify the merge is an ancestor of current `main`;
- return success without replay.

### FAIL_CLOSED

Fail without force/rewrite when, among other cases:

- live base drifted before an unmerged closure;
- closure branch exists with a different contract;
- multiple matching PRs exist;
- matching PR is closed but not merged;
- required hosted check fails;
- user repository operation is in progress.

Successful prior boundaries are preserved.

## 13. Hosted closure checks

The manifest declares the exact hosted checks required for the closure head.

The engine must:

- tolerate eventual check-registration delay;
- poll until all declared checks are registered and completed;
- treat a completed non-success required check as failure;
- continue to verify closure branch identity and base-main stability while
  waiting;
- avoid dummy commits merely to retrigger expected checks.

For a governance-only closure, the closure PR's normal repository checks are
sufficient. Already-green product publication is not replayed locally.

## 14. Exact-head merge and final verification

Immediately before merge:

- live `main` must still equal the manifest base;
- remote closure branch must still equal the validated closure head;
- the PR must still be open and target `main`.

Merge uses exact-head protection (`--match-head-commit`) and the manifest's
approved merge method.

After merge:

- read the PR again;
- resolve its merge commit;
- fetch live `main`;
- verify the closure head is a parent of the merge commit;
- verify the merge is present in live `main`.

Branch deletion is not part of standard closure unless separately authorized.

## 15. Closure authority release

The final governance payload must explicitly record:

- capability CLOSED;
- active product write owner NONE;
- all migrations consumed by the capability immutable;
- next migration unreserved unless a separate activation already governs it;
- final canonical story reconciliation;
- exact product PR/merge evidence;
- no next capability activated by the closure.

## 16. Evidence contract

Every closure execution produces external evidence under
`C:\dev\hrms-payroll-artifacts` unless the manifest explicitly selects another
approved location.

Evidence records at least:

- capability and manifest identity;
- pre-run branch/HEAD/status fingerprint;
- live/fetched base;
- verified product authorities;
- closure state classification;
- closure head;
- exact changed paths;
- story-ledger delta;
- payload/source hashes;
- hosted check outcomes;
- closure PR;
- merge commit;
- final live `main`;
- post-run branch/HEAD/status fingerprint;
- explicit PASS/FAIL marker.

## 17. Failure and repair rule

A closure failure is classified before repair:

- **manifest/payload preparation defect** — repair only the data package;
- **closure-engine defect** — stop capability-specific patching and repair the
  repository-owned engine in a separate governance/tooling increment;
- **repository authority drift** — rebuild the closure manifest/payload from the
  new authoritative base;
- **hosted infrastructure failure** — preserve the valid closure head/PR and
  resume after the infrastructure condition is resolved;
- **product defect** — only when closure evidence actually proves the merged
  product is wrong; do not infer a product defect from closure automation.

Never create v2/v3/v4-style capability-specific engine forks after this
standard is installed.

## 18. Engine-change rule

If a future legitimate closure requirement cannot be represented by the
manifest schema/engine:

1. do not embed the new behavior in that capability's downloaded closure pack;
2. propose the smallest reusable engine/schema extension;
3. publish it as a separate governance/tooling PR;
4. run the engine's repository self-tests and hosted checks;
5. merge that tooling increment;
6. then prepare the capability's data-only closure package against the updated
   engine.

This keeps closure automation cumulative and prevents repeated rediscovery.

## 19. Required preparation review

Before delivering a closure manifest/payload to the project owner, the assistant
must validate the package against the exact live closure base and run the
repository closure-engine self-test.

At minimum preparation proves:

- manifest schema/required fields;
- all source blobs;
- all payload SHA-256 values;
- all story-row preconditions;
- all final authority assertions;
- exact allow-list;
- path-with-spaces handling for the invocation path;
- success and negative self-test cases.

A downstream fail-closed gate remains protection, not first-time package
testing.

## 20. P5-SSC-01 learning incorporated

P5-SSC-01 closure established the permanent design:

- v1 exposed Windows argument re-serialization of a path containing spaces;
- v1.1 exposed remote-SHA/local-object confusion;
- v2/v3 exposed incorrect assumptions about canonical story reporting
  categories and the risk of validating a summary taxonomy against the wrong
  CSV semantics;
- v4 succeeded by fetching first, validating the exact seven canonical story
  changes, aggregate-preflighting all authorities, constructing the commit
  branch-free, resuming safely, waiting exact hosted checks, merging exact head
  and preserving the user's checkout.

Those lessons are now requirements of the standing closure process, not
capability-specific history.
