# Sprint 4 Closure Report

## Decision

**Implementation status:** complete<br>
**Automated verification status:** green at the implementation head<br>
**Closure-alignment status:** ready for local verification<br>
**Review readiness:** conditional on closure verification, manual statutory smoke and critical review<br>
**Merge readiness:** not yet approved<br>
**PR:** #19 — keep open and unmerged until every closure gate is complete

## Authoritative state reviewed

| Item | Value |
|---|---|
| Repository | `srinivasbs2000/hrms-payroll` |
| Branch | `feature/sprint-4-statutory-deductions` |
| Implementation head before closure documentation | `6cf39fc1734a50a514cfee22db2fd78bd41b80cc` |
| Base | `main` at `73c356662b1888194a72c7006a66bd91443550ca` |
| Pull request | `#19` |
| PR state at implementation head | open, mergeable, unmerged |
| Commits before closure documentation | 6 |
| Changed files before closure documentation | 51 |
| Latest migration | V030 |
| Final implementation workflow | `payroll-baseline` run 77 |
| Workflow run ID | `30197879363` |

## Delivered Sprint 4 scope

- V027 jurisdiction-neutral statutory rule identities and immutable approved versions;
- employee and employer liability portions, thresholds, caps and slab validation;
- V028 employee statutory profiles, registrations and exact rule assignments;
- V029 idempotent deterministic evaluation against exact payroll and statutory lineage;
- immutable statutory input snapshots and per-result totals;
- V030 append-only ledger posting, active posting epochs and signed corrections;
- PTD/cycle/YTD balance snapshots;
- zero-variance reconciliation and remittance preparation summaries;
- tenant isolation, RLS, least privilege and immutable-history enforcement;
- controlled evaluation, posting and correction APIs;
- dedicated permissions, Keycloak mappings, audit and outbox evidence;
- statutory bounded-context and aggregate OpenAPI contracts; and
- permission-aware statutory execution and evidence UI.

## Explicit exclusions retained

- jurisdiction-specific rates and legal tax interpretation;
- statutory filing, returns, acknowledgements or authority integration;
- remittance payment or settlement;
- retro payroll;
- off-cycle payroll;
- final settlement;
- banking and payment files;
- accounting and GL integration; and
- legal/final payslip publication.

## Automated evidence at the implementation head

`payroll-baseline` run 77 completed successfully. Green jobs included:

- full Maven verification;
- fresh Flyway installation, validation and RLS tests;
- frontend dependency installation, tests and production build;
- scoped npm-audit policy enforcement;
- aggregate OpenAPI validation;
- synthetic real-token authentication smoke;
- Payroll browser E2E;
- dependency review;
- CycloneDX backend/frontend SBOM generation; and
- Gitleaks secret scan.

## React Router scoped audit decision

The frontend remains on declarative `BrowserRouter` mode with
`react-router-dom` and `react-router` pinned to `7.18.1`.

The executable policy is
`frontend/payroll-web/scripts/verify-npm-audit.mjs`, invoked by
`.github/workflows/ci.yml`. It permits only `GHSA-qwww-vcr4-c8h2` while the
application remains outside RSC, Framework, Data and server modes, and rejects
additional high or critical advisories and prohibited source/dependency
patterns. The exception review deadline is **2026-10-31**.

The withdrawn downgrade to 7.11.0 must not be used. Raw `npm audit` output is
policy input, not the final architecture-aware risk decision.

## Closure gaps addressed by this documentation slice

Before closure alignment:

- README described only Sprint 1–3 and labelled the system non-statutory;
- `AGENTS.md` retained the original prohibition on any statutory addition;
- the backlog ended at Sprint 3;
- no repository-resident project continuation handoff existed;
- no Sprint 4 closure report, manual statutory smoke or local regression script existed; and
- PR #19 metadata still described only the first V027 increment.

This closure slice aligns repository documentation and local verification. PR
metadata remains a separate GitHub write and must not be changed until this
closure commit and its CI are green and the user explicitly authorises it.

## Remaining manual and review gates

The existing browser E2E job passed at the implementation head, but no verified
Sprint 4 change added a statutory-specific Playwright scenario. Therefore the
integrated statutory operator path remains a manual pre-merge gate.

Before merge:

1. run `scripts/verify-sprint-4.ps1` and retain the result;
2. execute `docs/runbooks/sprint-4-manual-smoke.md` once against the exact reviewed commit;
3. perform an independent critical review of the complete Sprint 4 diff;
4. confirm the branch still targets the reviewed SHA and all required checks are green;
5. update PR #19 title/body only after explicit authorisation; and
6. request a separate explicit merge decision.

## Recommendation

After the closure documentation commit passes CI and the manual smoke and
critical review are complete, update PR #19 metadata to describe the complete
Sprint 4 delivery. Merge remains a separate controlled action. Do not rewrite
V001–V030.

## CR-S4-001 resolution in progress

The independent critical review found that statutory money crossed the
browser/API boundary as JSON numbers even though the approved repository rule
requires decimal strings. Option A was approved.

The bounded correction enforces:

- exact decimal-string OpenAPI money;
- strict string-only Jackson deserialization into `BigDecimal`;
- plain decimal-string serialization for every public statutory money field;
- frontend string DTOs and command payloads;
- no `Number(...)` conversion for statutory corrections; and
- exact-decimal tests including `0.1000`, `-10.1250` and
  `1234567890123.4567`.

Manual smoke and closure publication remain blocked until the corrective
verification is green.
