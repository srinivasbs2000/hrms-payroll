# HRMS Payroll Program Status

**Status:** Canonical repository-wide program checkpoint
**Repository:** `srinivasbs2000/hrms-payroll`
**Verified repository baseline:** `b922d6d388214ab83cf365a35516468f8045ca4f`
**Latest merged product increment:** Current P5-A3 through PR #32
**Latest merged quality increment:** P5-A3 React test hygiene through PR #33
**Latest merged status closure:** PR #34 / `b922d6d388214ab83cf365a35516468f8045ca4f`
**Active product write owner:** None
**Migration state:** V001–V033 committed and immutable
**Next migration:** V034 unreserved
**Product deployment:** Greenfield; no evidenced production deployment or live customer payroll migration
**Last reconciled:** 6 August 2026
**Planned next execution capability:** `P5-JRF-01` — PLANNED / NOT ACTIVATED

## 1. Mandatory starting point

Every new HRMS Payroll thread, session, assistant or write-capable process must
read this file before reading the continuation handoff or proposing work.

Validate this checkpoint against:

1. local `git status`, branch and HEAD;
2. live read-only GitHub `main`, pull requests and CI evidence;
3. `backlog/payroll-detailed-story-status.csv`;
4. `docs/governance/payroll-feature-delivery-lineage.md`;
5. `docs/runbooks/project-continuation-handoff.md`;
6. the exact capability-scope authority named in this file;
7. `docs/governance/payroll-automation-lessons-and-package-checklist.md`;
8. `docs/governance/hrms-payroll-model-routing-policy.md`.

Conversation history and thread names are locators only. They are not the
project-status authority.

## 2. Latest completed milestone

Current P5-A3 delivered:

- schema-1 salary-structure lifecycle and controlled approval;
- versioned CTC policies and four distinguishable cost views;
- typed, effective-dated eligibility-rule configuration;
- deterministic design-time simulation, comparison and validation;
- immutable validation evidence and exact passing fingerprints;
- aligned backend, OpenAPI, Keycloak and React workbench behavior.

Evidence:

- PR #32 merged as `b4f3013e1d7404d09eac64a305ad3736e5a28a5c`;
- PR #33 merged as `23df1f7a11f4090cef8715eba7104f5b1138b760`;
- PR #34 merged as `b922d6d388214ab83cf365a35516468f8045ca4f`;
- V033 is committed;
- 220 backend tests passed before publication;
- PostgreSQL 17.10 Testcontainers and Flyway V001–V033 passed;
- frontend lint, tests and production build passed;
- PR #33 removed React asynchronous-test warnings without production changes.

P5-A3 did not implement official payroll calculation changes, legal/statutory
rates, employee compensation assignments, live eligibility persistence,
flexible-benefit elections, multi-currency, deployment or production migration.

## 3. Reconciled detailed-story status

The approved program contains 450 detailed source-linked stories:

| Status | Stories | Percentage |
|---|---:|---:|
| Implemented | 11 | 2.44% |
| Partially implemented | 155 | 34.44% |
| Not evidenced | 94 | 20.89% |
| Not started | 159 | 35.33% |
| Legal/domain revalidation | 31 | 6.89% |
| **Total** | **450** | **100%** |

The original 72 broad control rows remain:

- 44 partially implemented;
- 24 not started;
- 4 requiring legal/domain revalidation.

No broad control row is marked fully complete while material linked stories
remain unfinished.

The machine-readable authority is:

`backlog/payroll-detailed-story-status.csv`

## 4. Execution-label to original-package mapping

Execution labels and original PLN-01 package identifiers are separate fields:

| Completed execution increment | Primary original package mapping |
|---|---|
| Current P5-A1 — organisation hierarchy closure | Original P5-A1 |
| Current P5-A2 — component catalogue and named bases | Primarily original P5-B1, plus selected P5-B3 lifecycle/workbench controls |
| Current P5-A3 — salary structure, CTC, eligibility and simulation | Primarily original P5-B4, P5-B5 and selected P5-B6 controls |

The original packages named P5-A2 and P5-A3 are not complete merely because
execution increments reused those labels.

## 5. Current original-package position

- Original P5-A1: complete.
- Original P5-A2 — jurisdiction and registration foundations: not started.
- Original P5-A3 — foundation bank, authority, snapshots and readiness:
  partially evidenced; bank/signatory/readiness scope remains.
- Original P5-A4 — pay groups, period generation and milestone rules:
  partially implemented and dependency-ready.
- Original P5-A5: partially implemented and dependent on P5-A4.
- Original P5-B1: substantially implemented; gap closure remains.
- Original P5-B2 and P5-B3: partially implemented.
- Original P5-B4, P5-B5 and P5-B6: partially implemented by current P5-A3;
  material remaining scope is recorded in the story ledger.
- P5-C1 through P5-C5: incomplete.

## 6. Planned execution identity for Original P5-A2

The Payroll Program Reconciliation Gate recommends:

**Original P5-A2 — Jurisdiction and Registration Foundations**

The unambiguous execution identity is:

- **Execution capability:** `P5-JRF-01`
- **Title:** Jurisdiction and Registration Foundations
- **Scope authority:** `docs/planning/pln-01/p5-jrf-01-jurisdiction-registration-foundations-scope.md`
- **State:** `PLANNED / NOT ACTIVATED`
- **Product write owner:** None
- **Migration:** V034 remains unreserved

The scope authority contains the six reviewed candidates P5-E01-005 through
P5-E01-010 and explicit exclusions.

Reasons for sequencing:

1. Original P5-A1 is complete.
2. All six original P5-A2 candidates remain not evidenced.
3. It unlocks original P5-A3 bank/authority/readiness.
4. It unlocks completion of employee statutory/tax profiles under P5-C4.
5. Work-location, jurisdiction resolution and registration lifecycle are
   foundational for later India statutory, readiness and compliance work.

This planning authority does not authorize product implementation.

## 7. Immediate next action

After the P5-JRF-01 governance-authority publication is merged and independently
verified:

1. synchronize the working thread from repository authority;
2. perform the critical design/readiness review for `P5-JRF-01`;
3. resolve the ten design questions in the capability scope;
4. define the exact product file/module boundary;
5. obtain separate explicit activation and V034-reservation authorization before
   any product implementation.

Do not interpret `PLANNED / NOT ACTIVATED` as implementation authority.

## 8. Remaining full-product scope

The payroll product continues through:

- complete P5 foundation/configuration/readiness gaps;
- P6 calculation engine and payroll operations;
- P7 India statutory rule packs after legal/domain revalidation;
- P8 balances, retro, off-cycle and final settlement;
- P9 payments and banking;
- P10 accounting and ERP integration;
- P11 payslips, ESS, reporting and communications;
- P12 audit, security, performance, resilience and DR;
- P13 migration and parallel payroll;
- P14 cutover and hypercare.

## 9. Standing closure rule

Every future product increment is incomplete until:

1. product merge and post-merge evidence are verified;
2. the detailed-story reconciliation delta is performed;
3. this program-status file and supporting authorities are updated;
4. the status-closure PR is merged;
5. active ownership and migration reservation are explicitly closed;
6. only then is the next product package selected and authorized.
