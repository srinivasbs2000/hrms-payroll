# S4-05B statutory execution and evidence UI

## Scope

S4-05B adds a permission-aware React workspace over the S4-05A API contract.

Delivered behavior:

- payroll-cycle selection;
- statutory evaluation command;
- statutory ledger posting command;
- signed correction command;
- evaluation and statutory-result evidence;
- ledger batches and append-only entries;
- PTD, cycle and YTD balance evidence;
- zero-variance reconciliation evidence;
- remittance preparation summaries;
- permission-filtered navigation and commands;
- unit tests for permission rejection, evidence loading, versioned
  evaluation and correction validation.

## Architecture

The feature remains local to
`frontend/payroll-web/src/features/statutory`.

The UI:

- calls only documented S4-05A endpoints;
- sends `If-Match` with the selected payroll-cycle version;
- generates a new `Idempotency-Key` for each command;
- reloads cycle and statutory evidence after each successful command;
- does not write statutory tables directly;
- does not infer authority-specific legal semantics.

## Security

The primary navigation requires `statutory-evaluation.read`.

Each evidence section checks its dedicated read permission. Each command
checks its dedicated execute/post/correct permission. The page does not call
an endpoint when its corresponding read permission is absent.

The frontend remains on declarative `BrowserRouter` mode with
`react-router-dom` and `react-router` pinned to `7.18.1`. The repository's
fail-closed audit policy permits only `GHSA-qwww-vcr4-c8h2` while the
application remains outside React Router RSC, Framework, Data and server
modes. It rejects additional high or critical advisories, prohibited
dependencies and prohibited source patterns. The exception expires on
2026-10-31 and must be reviewed or removed before that date.

## Verification contract

The S4-05B verifier must pass:

- exact eight-file working-tree scope;
- exact payload hashes;
- PowerShell parser and strict-mode cardinality checks;
- committed audit-policy and CI-workflow blob checks;
- exact React Router `7.18.1` dependency and lockfile checks;
- scoped audit-policy self-tests;
- scoped live npm-audit policy validation;
- frontend lint;
- frontend unit tests;
- frontend production build;
- full Maven clean verify;
- `git diff --check`.

Raw `npm audit --audit-level=high` is diagnostic input, not the repository's
final risk decision. The executable policy is
`frontend/payroll-web/scripts/verify-npm-audit.mjs`.

No stage, commit, push or merge is performed by the implementation or
verification scripts.

## Exclusions

- no database migration;
- no backend or OpenAPI change;
- no statutory configuration UI;
- no filing, payment or settlement;
- no legal/final payslip;
- no retro or off-cycle statutory processing.
