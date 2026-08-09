# Statutory execution UI runbook

## Purpose

The `/statutory` workspace exposes the controlled Sprint 4 statutory workflow
without allowing direct mutation of statutory tables.

## Required sequence

1. Select a payroll cycle in `CALCULATED` status.
2. Evaluate the exact active completed payroll calculation request.
3. Post the completed statutory evaluation into the append-only ledger.
4. Enter a correction only when an approved signed delta is required.
5. Review evaluation results, ledger entries, PTD/YTD balances,
   zero-variance reconciliation and remittance preparation summaries.

## Permission model

Read access is independently controlled by:

- `statutory-evaluation.read`
- `statutory-ledger.read`
- `statutory-balance.read`
- `statutory-reconciliation.read`
- `statutory-remittance.read`

Commands are independently controlled by:

- `statutory-evaluation.execute`
- `statutory-ledger.post`
- `statutory-ledger.correct`

The workspace also requires `payroll-cycle.read`. When
`payroll-result.read` is available, completed calculation requests are
loaded to help populate the evaluation command. A calculation request ID
can still be entered explicitly.

## Correction rules

A correction requires:

- an existing statutory result;
- at least one non-zero signed employee or employer delta; and
- a reason containing between 8 and 500 characters.

Corrections append evidence. They never edit or delete prior ledger entries.

## Local validation

From the standalone UI repository `C:\dev\hrms-payroll-web`:

```powershell
npm ci --ignore-scripts
node scripts/verify-npm-audit.mjs --self-test
node scripts/verify-npm-audit.mjs
npm run lint
npm test
npm run build
```

The scoped audit policy is the UI repository's final npm security gate and
requires zero high or critical advisories. It verifies the declared browser
routing mode and the dependency state required by the current security policy.
A compatible security fix is preferred over an allow-listed high/critical
exception.

The automated S4-05B verifier also runs the full Maven regression suite,
exact payload checks, exact changed-file checks and `git diff --check`.

## Exclusions

This workspace does not implement statutory configuration CRUD, authority
filing, remittance settlement/payment, legal payslips, retro payroll or
off-cycle payroll.
