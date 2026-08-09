# P5-FBA-01 Activation Record

**Date:** 10 August 2026
**Capability:** `P5-FBA-01 — Foundation Banking & Authority`
**Owner authorization:** Explicitly authorized by the project owner
**Activation baseline:** `0cae307b0f5e7bcd05b47836e6e4df24c8701add`
**UI baseline:** `dc8f17cfbabe0a3322f24a3dc0457509fe1e7d01`
**Implementation branch:** `feature/p5-fba-01-foundation-banking-authority`
**Migration:** `V035` reserved exclusively to P5-FBA-01

The owner authorized R3 capability activation, scope reconciliation,
architecture review, path ownership and V035 reservation decision, followed by
direct R2 implementation planning without redundant confirmation.

Primary story ownership:

- `PLN-E01-008`
- `PLN-E01-009`

Directly bounded cross-cutting ownership:

- banking/signatory maker-checker portions of `PLN-E01-011`;
- banking/signatory readiness portions of `PLN-E01-012`.

Explicitly excluded:

- `PLN-E01-010` configuration snapshots;
- complete readiness closure;
- employee personal bank accounts;
- payment execution/file generation/bank integration;
- payroll calculation changes;
- country-specific legal rates/rules;
- unrelated dependency upgrades;
- production cutover;
- V001-V034 rewrites.

The architecture and exact maximum path ownership are defined by
`docs/planning/pln-01/p5-fba-01-foundation-banking-authority-scope.md`.

Assistant/agent GitHub access remains strictly read-only. Repository mutations
are owner-executed through deterministic local packages.
