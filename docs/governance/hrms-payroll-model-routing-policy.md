# HRMS Payroll Model / Reasoning Routing Policy

**Status:** STANDING GOVERNANCE AUTHORITY

## 1. Goal

Use the lowest reasoning level that can safely produce a complete, verified
result while preserving escalation for architecture, reconciliation and
high-risk payroll decisions.

This policy is about reasoning effort, not tool permissions. Repository access,
GitHub boundaries and safety controls remain identical at every level.

## 2. Routing classes

### R1 — ROUTINE / EVIDENCE

**Default:** Instant / Auto

Use for:

- repository status lookup;
- PR/CI metadata inspection;
- log summarization;
- deterministic merge/status verification;
- known-scope command generation;
- simple documentation edits;
- bounded test-failure triage with explicit evidence.

Expected project share: roughly 60–70%.

### R2 — ENGINEERING

**Default:** Medium reasoning

Use for:

- implementation after scope is approved;
- API/service/UI design inside an agreed capability boundary;
- routine schema design that follows established patterns;
- acceptance criteria;
- test strategy;
- non-obvious build/debug work;
- implementation diff review.

Expected project share: roughly 20–30%.

### R3 — ARCHITECTURE / CRITICAL

**Default:** High reasoning

Use for:

- discovery/research that determines product scope;
- cross-epic or cross-module architecture;
- migration ownership/schema decisions with long-lived consequences;
- payroll/statutory/financial correctness design;
- 450-story or program reconciliation;
- critical review before activating a capability;
- high-risk concurrency, RLS, security, audit or irreversible data design;
- legal/domain research where several authoritative sources must be reconciled.

Expected project share: roughly 10%.

## 3. Escalation rule

Do not begin every task at R3.

A thread should explicitly state:

`RECOMMENDED_REASONING_LEVEL: R1 | R2 | R3`

and a one-line reason before substantive work.

If R1/R2 discovers architectural ambiguity, state:

`REASONING_ESCALATION_RECOMMENDED: R3 — <reason>`

After the decision is resolved:

`REASONING_ESCALATION_COMPLETE — RETURN TO R2/R1`

## 4. FOMO control

Higher reasoning does not grant additional repository authority, files, tools or
GitHub permissions.

Use R3 because the decision is genuinely complex or high-impact, not because
the task is part of Payroll.

## 5. Capability lifecycle routing

Recommended pattern:

- package reconstruction/discovery: R3;
- critical scope challenge: R3;
- capability activation decision: R3;
- detailed acceptance design: R2;
- implementation package: R2;
- routine execution/log review: R1;
- unexpected bounded failure: R1 then R2 if needed;
- final high-risk critical review: R3;
- detailed-story reconciliation: R3;
- status-closure package: R2;
- merge verification: R1.

## 6. Context-budget rule

Do not repeatedly restate historical conversation evidence when repository
authority exists.

A new thread should load:

1. `AGENTS.md`;
2. `docs/governance/payroll-program-status.md`;
3. active capability scope;
4. execution norm / lessons checklist;
5. only the relevant detailed-story rows and implementation files.

Load deeper historical handoffs only when needed to resolve a conflict or
recover uncommitted work.

## 7. Thread-start output

Each repository-aware HRMS Payroll thread should include:

- `RECOMMENDED_REASONING_LEVEL`;
- current main;
- active capability/owner;
- migration state;
- exact next controlled action.

The project owner should not need to guess whether High reasoning is warranted.
