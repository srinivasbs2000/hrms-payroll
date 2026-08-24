# Payroll execution optimization tooling foundation

**Status:** Repository tooling authority after merge

This foundation implements TOOL-01, TOOL-02, TOOL-05, TOOL-06 and TOOL-07 from the approved Execution Optimization Standard. The unpublished `governance/execution-optimization-tooling-foundation-v1` local branch is preserved as historical local evidence and is superseded for publication by this redesigned foundation.

## Tools

- `payroll-authority-snapshot.mjs` — exact fetched backend/UI authority, exact remote-tree migration inventory, exact repository-owned capability state, and bounded story/UI extracts.
- `payroll-r3-reconciliation.mjs` — schema-first dual-source story + UI applicability reconciliation with collect-all failures.
- `payroll-evidence-bundle.mjs` — traversal-safe, duplicate-safe evidence ZIP with local/central directory and manifest hash verification.
- `payroll-validation-registry.mjs` — exact validation-key registry; reuse requires exact scope, commit, command, validation version, tool/version, environment, invalidation inputs and hashes.
- `payroll-capability-state.mjs` — predecessor-guarded lifecycle transitions over a governance-published capability record; runtime creation of unknown capabilities is prohibited.
- `Test-PayrollOptimizationTooling.mjs` — repository-owned semantic fixture suite covering remote-SHA-absent fetch, remote-tree authority, UI reconciliation, collect-all failures, validation invalidation, lifecycle negative transitions, ZIP integrity, and paths containing spaces.

## Standing boundaries

1. No tool authorizes product implementation, migration reservation, push, PR, merge, or branch deletion by itself.
2. A remote SHA must be fetched and verified locally before tree/blob reads.
3. Authority Snapshot reads migration and capability state from the exact remote tree, never from the owner's dirty worktree.
4. R3 reconciliation must evaluate both canonical story data and UI applicability for every selected/comparison ID.
5. Validation reuse is exact-key only; omission of validation version/tool/environment is prohibited.
6. Capability lifecycle state is governance-published first and then transitioned; unknown capability bootstrap is prohibited in the runtime tool.
7. Evidence bundle PASS means final ZIP bytes were re-opened and checked against both directory metadata and the internal SHA-256 manifest.
8. Existing `payroll-capability-closure.mjs` remains the authoritative generic post-merge closure engine.
