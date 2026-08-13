# P5-E2E-UI-01 — Existing Story UI Gap Closure

**Status:** ACTIVATED only after this authority merges
**Execution capability:** P5-E2E-UI-01
**Program/governance repository:** `srinivasbs2000/hrms-payroll`
**UI product repository:** `srinivasbs2000/hrms-payroll-web`
**Backend activation baseline:** `9394cc35660a45cb14febd781b484b4c3bcbc8a3`
**UI activation baseline:** `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Migration:** NONE; V039 remains unreserved
**Backend product write authority:** NONE
**UI product write authority after activation:** P5-E2E-UI-01 only
**Selected-story UI applicability revalidated:** YES

## 1. Business objective

Close the 11 end-to-end usability gaps created when valid backend/domain
capabilities were previously treated as complete without all required human
operator/admin UI and browser evidence.

Backend/domain evidence remains valid. This capability does not reimplement
green backend work.

## 2. Exact selected stories and UI classification

| Story | UI classification | Activation rationale |
|---|---|---|
| PLN-E01-011 | REQUIRED_ADMIN_OR_SECURITY_UI | Foundation approvers/security administrators need a usable surface for scoped approval authority and effective-dated delegation. |
| PLN-E02-001 | REQUIRED_PRODUCT_UI | Payroll foundation administrators must configure and operate versioned pay groups. |
| PLN-E02-002 | REQUIRED_PRODUCT_UI | Payroll operations administrators must author and inspect pay-group population routing. |
| PLN-E02-003 | REQUIRED_PRODUCT_UI | Payroll calendar administrators must operate calendar identity/version history. |
| PLN-E02-004 | REQUIRED_PRODUCT_UI | Payroll calendar administrators must generate and inspect contiguous pay periods. |
| PLN-E02-005 | REQUIRED_PRODUCT_UI | Supported monthly/fortnightly/weekly/daily/custom frequencies must be operator-configurable where authorized. |
| PLN-E02-006 | REQUIRED_PRODUCT_UI | Operators must configure and inspect input/calculation/approval/release/payment milestone rules and generated milestones. |
| PLN-E02-007 | REQUIRED_PRODUCT_UI | Operators must configure holiday/weekend movement policy and inspect original/adjusted date evidence. |
| PLN-E02-008 | REQUIRED_PRODUCT_UI | Authorized operators must publish, amend and retire calendars and inspect lifecycle/version history. |
| PLN-E02-009 | REQUIRED_OPERATIONAL_UI | Operators need proactive pay-group/calendar compatibility and blocking-condition visibility. |
| PLN-E02-010 | REQUIRED_OPERATIONAL_UI | Operators need consolidated operational visibility for pay groups, calendars, periods, milestones, routing and lifecycle state. |

None of the 11 selected stories is `NOT_REQUIRED_DIRECTLY`.

## 3. UI product scope

The UI implementation must use the already merged backend contracts wherever
they are sufficient.

Primary UI ownership is bounded to:

- `src/App.tsx` where route/navigation changes are required;
- `src/features/pay-group/**`;
- `src/features/payroll-calendar/**`;
- a bounded foundation approval/delegation feature surface under `src/features/**`;
- story-specific UI styles/components required only by these workspaces;
- story-specific frontend unit/component tests;
- story-specific `e2e/**` browser journeys and existing E2E support only where
  required to run those journeys.

No unrelated UI redesign is authorized.

## 4. Required user journeys

### Foundation approval/delegation

An authorized human administrator can inspect and operate the existing scoped
application approval/delegation contract without using raw technical identifiers
where business selectors are available. Permissions, maker-checker and service
account restrictions remain enforced by the backend.

### Pay groups and population routing

An authorized payroll administrator can create/inspect/version pay groups,
understand their PSU/frequency/calendar relationship, configure or inspect
population routing and see incompatibility/blocking conditions before use.

### Calendars, periods and frequencies

An authorized calendar administrator can operate the full merged calendar model,
including supported frequencies, version lineage, period generation and
published-state visibility.

### Milestones and working-day adjustment

An authorized calendar administrator can configure/inspect the five milestone
rules, inspect generated milestones, configure holiday/weekend movement
behavior, and see original versus adjusted dates.

### Publish / amend / retire

An authorized operator can publish a valid draft, start an amendment successor,
inspect version/lifecycle history and retire with a required reason using the
existing backend lifecycle contract.

### Operational visibility

An operator can inspect pay group, routing, calendar, publication, periods,
milestones and compatibility/blocking state from normal application UI.

## 5. Backend contract rule

Backend repository code, migrations, OpenAPI and permissions are READ-ONLY
inputs to this capability.

If UI implementation proves that an acceptance criterion cannot be completed
because the merged backend contract is genuinely missing or defective:

1. stop the affected UI story at the demonstrated contract boundary;
2. record exact evidence;
3. do not reserve V039;
4. do not modify backend code under this authority; and
5. obtain a separately bounded backend amendment authority before any backend
   product write.

A UI inconvenience is not by itself a backend contract defect.

## 6. Story completion gate

A selected story returns to `IMPLEMENTED` only when its applicable:

- UI workflow/operational surface is complete;
- authorization behavior is correct;
- frontend tests and production build are green;
- real-browser E2E against the authoritative merged backend is green; and
- story-level UI evidence is updated in
  `backlog/payroll-story-ui-applicability.csv`.

Partial completion keeps that story `PARTIALLY IMPLEMENTED`.

## 7. Cross-repository verification

Required before product closure:

1. UI lint/type/static gate;
2. targeted UI tests;
3. production UI build;
4. story-specific browser E2E using the authoritative backend repository;
5. exact backend SHA pinned by E2E;
6. UI hosted CI;
7. final UI diff review;
8. independent R3 review of security/lifecycle semantics;
9. backend governance reconciliation after UI merge; and
10. final story ledger counts reconciled.

## 8. Explicit prohibitions

This activation does not authorize:

- P5-A5/E03;
- V039 or any Flyway migration;
- backend product writes;
- backend OpenAPI changes;
- backend permission redesign;
- unrelated React redesign;
- country-specific legal/rate work;
- payment execution, retro/off-cycle/final settlement, accounting or cutover;
- story closure without browser evidence.

## 9. Product branch after activation

Authorized UI product branch:

`feature/p5-e2e-ui-01-story-ui-gap-closure`

Backend/program repository remains on governance/main except for later
status/evidence reconciliation.

## 10. Activation outcome

After this authority merges:

- P5-E2E-UI-01 is ACTIVE;
- UI repository is the only product write owner;
- all 11 selected stories remain PARTIALLY IMPLEMENTED until proven complete;
- V039 remains unreserved;
- P5-A5/E03 remains inactive;
- next controlled action is the bounded UI implementation gate.