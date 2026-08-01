# Multi-Thread Maintenance Protocol

## 1. Goal

Allow several project threads to work on the same HRMS Payroll programme without
losing decisions, duplicating work or creating conflicting repository changes.

Separate ChatGPT threads do not automatically share complete context. The
repository must therefore carry all durable context.

## 2. Mandatory files at thread start

Every project thread must begin by reading, in this order:

1. `AGENTS.md`
2. `docs/design/hrms-payroll-master-design.md`
3. `docs/design/decision-register.md`
4. `docs/runbooks/project-continuation-handoff.md`
5. `docs/governance/thread-registry.md`
6. exact ADRs, backlog, migrations, contracts, tests and runbooks relevant to
   the requested work

Then validate the documents against:

- local branch, HEAD, index, working tree and complete diff;
- live GitHub branch, PR and CI;
- committed files governing the requested area.

Unknown facts are `NOT VERIFIED`. Conflicts are `DOCUMENTATION CONFLICT`.

## 3. Thread roles

A thread must declare one role:

- `IMPLEMENTATION OWNER` — the only thread allowed to modify its approved file
  allow-list;
- `DESIGN/RESEARCH` — may propose design but does not write overlapping files;
- `REVIEW` — read-only review of a completed diff;
- `RECOVERY/HANDOFF` — extracts history and updates repository documentation
  after reconciliation;
- `PAUSED` or `CLOSED`.

Only one thread may be the implementation owner for overlapping files or one
migration sequence at a time.

## 4. Required thread registration

At the start of durable work, update `thread-registry.md` with:

- thread number/name;
- role;
- branch and PR;
- approved scope;
- exact file allow-list or bounded modules;
- immutable migration range;
- latest checkpoint;
- blockers;
- next authorised action;
- prohibited actions.

A thread must not silently take ownership from another active thread.

## 5. During-thread update rules

### Update the master design when

- approved product or architecture scope changes;
- a cross-cutting rule changes;
- an excluded capability becomes implemented;
- a migration baseline advances after merge;
- a material conflict is resolved.

### Update the decision register when

- a material decision is approved;
- a decision is superseded;
- a temporary exception is introduced or removed;
- a documentation conflict is opened or closed.

### Update the running handoff when

- a commit is published and CI is known;
- branch/PR ownership changes;
- a durable implementation phase completes;
- a material failure changes the plan;
- a thread transition is imminent.

### Update the thread registry when

- a thread starts, pauses, resumes or closes;
- write ownership changes;
- its branch/PR/checkpoint changes;
- its approved file allow-list changes.

Routine diagnostics do not need documentation updates.

## 6. Documentation atomicity

Documentation is part of implementation, not an afterthought.

A code increment that changes architecture, scope, migration baseline, API
contract, permissions, operator workflow or verification policy must include
the applicable repository documentation in the same branch/PR.

Do not claim a capability is implemented in the master design before the
implementation is committed and verified.

## 7. Thread exit protocol

Before leaving a thread:

1. inspect local/remote repository state;
2. run the required verification for the bounded scope;
3. update the running handoff;
4. update the thread registry;
5. update master design/decision register only if their triggers were met;
6. create an immutable checkpoint only when transition criteria are met;
7. state working tree, index, commit, push, PR and merge status separately;
8. record the exact next authorised action and prohibited actions.

## 8. Preventing cross-thread conflicts

- No two implementation threads may edit the same files concurrently.
- No two threads may allocate the same next migration version.
- A design thread may draft proposals, but the implementation owner decides
  when approved repository changes are applied.
- A review thread never rewrites the implementation it reviews.
- A historical recovery thread does not override current code without explicit
  reconciliation.
- If two valid designs differ materially, record both and obtain a user
  decision before implementation.

## 9. Standard evidence labels

- `VERIFIED - LOCAL`
- `VERIFIED - REMOTE`
- `VERIFIED - REPORT`
- `DERIVED`
- `DESIGN BASELINE`
- `THREAD-RECORDED - LOCAL`
- `DOCUMENTATION CONFLICT`
- `NOT VERIFIED`

## 10. Enforcement model

This protocol becomes implicit for repository-aware work by:

- linking it from `AGENTS.md`;
- requiring it in the standard thread-start prompt;
- checking thread-registry ownership before writes;
- treating documentation alignment as a definition-of-done gate.

It cannot make unrelated chat threads automatically read GitHub. The project
owner must seed each existing/new thread once with the standard start prompt.
After that, every continuation should be repository-driven.
