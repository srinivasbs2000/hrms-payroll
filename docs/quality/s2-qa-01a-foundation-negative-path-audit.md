# S2-QA-01A — Completed-Foundation Negative-Path Audit

## Scope

This time-boxed audit covers the completed Sprint 0, Sprint 1 and Sprint 2
database foundations through V021. It focuses on defects that would be costly
to discover after the S2-05B employee-payroll application layer is built.

The audit is not the final coverage exercise. Line/branch coverage measurement,
full endpoint authorization matrices, concurrency stress, performance, chaos
and penetration testing remain in S2-QA-01B.

## Findings

### F1 — Child organisation approval did not require an approved parent

**Severity:** Critical
**Affected baseline:** V016 `organisation.approve_version`

A payroll-statutory-unit version could be approved while its exact
legal-entity version remained a draft. An establishment version could likewise
be approved while its exact payroll-statutory-unit version remained a draft.

**Resolution:** V022 replaces the lifecycle command with parent-aware approval
rules and validates actor/timestamp inputs.

### F2 — Parent organisation versions could be shortened beneath children

**Severity:** Critical
**Affected baseline:** V016 `organisation.end_date_version`

The child insert trigger guaranteed containment at creation time, but a later
parent end-date could invalidate the exact child lineage.

**Resolution:** V022 adds both controlled-command predicates and owner-level
defence-in-depth triggers for:

- legal entity → payroll statutory unit;
- legal entity → payroll relationship;
- payroll statutory unit → establishment;
- payroll statutory unit → pay group;
- establishment → payroll assignment.

### F3 — Payroll-cycle periods were not range-bound to pay-group versions

**Severity:** High
**Affected baseline:** V017/V018

The cycle constraint checked that the pay period belonged to the pay-group
calendar, but it did not prove that the period was inside the exact
effective-dated pay-group version.

**Resolution:** V022 adds migration preflight validation, an insert/update
trigger, an owner-level pay-group end-date guard and controlled lifecycle
predicates.

### F4 — Dependent end-date conflicts were not consistently returned as zero-row conflicts

**Severity:** High
**Affected baseline:** V017/V021 lifecycle commands

Some controlled end-date calls relied on trigger exceptions. This is safe at
the database boundary but produces less predictable application error mapping.

**Resolution:** V022 makes organisation, pay-group, relationship and assignment
end-date commands return `0` when exact dependents would be truncated. Existing
triggers remain as defence in depth for direct owner-controlled changes.

## Added negative-path evidence

`FoundationNegativePathMigrationIT` adds seven named integration tests:

1. child organisation approval requires an approved parent;
2. parent end dates cannot truncate exact dependents;
3. payroll-cycle periods must fit their exact pay-group version;
4. cross-tenant employee dependencies are rejected;
5. invalid parent state, range and currency are rejected;
6. approved pay-group and salary assignments cannot overlap;
7. direct history mutation and stale optimistic writes are rejected.

`PayGroupApiIT` adds a representative API test proving that reuse of an
idempotency key with a different payload returns HTTP 409.

## Coverage impact

Before S2-QA-01A:

- backend test inventory: 69;
- database migration/Failsafe tests: 30.

After S2-QA-01A:

- backend test inventory: 77;
- database migration/Failsafe tests: 37.

Expected result: zero failures, errors and skips.

## Deferred to S2-QA-01B

- JaCoCo line and branch reports and thresholds;
- Vitest coverage reports and thresholds;
- complete endpoint permission/validation matrix;
- concurrent approval, correction and end-date races;
- duplicate requests during an in-progress idempotent operation;
- all unsupported formula combinations and percentage dependency cycles;
- load, performance, resilience, chaos and penetration testing;
- final requirement-to-test traceability matrix for Sprint 2.
