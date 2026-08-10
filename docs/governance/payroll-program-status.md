# HRMS Payroll Program Status

**Status:** Canonical repository-wide program checkpoint
**Repository:** `srinivasbs2000/hrms-payroll`
**Repository topology:** backend/program authority here; React UI authority in `srinivasbs2000/hrms-payroll-web`
**Product reconciliation baseline:** P5-JRF-01 product merge on `main` at `6ee101bd398b745a0078bd0517b4e3797c571c2b`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code a self-referential closure SHA here
**Latest merged product increment:** P5-JRF-01 through PR #36 / `6ee101bd398b745a0078bd0517b4e3797c571c2b`
**Latest merged quality increment:** P5-A3 React test hygiene through PR #33
**P5-JRF-01 product-status closure:** PR #39; post-merge authority closed
**Active product write owner:** `P5-FBA-01 — Foundation Banking & Authority`
**Migration state:** V001–V034 committed and immutable
**Next migration:** V035 reserved exclusively to `P5-FBA-01`; V001-V034 remain immutable
**Product deployment:** Greenfield; no evidenced production deployment or live customer payroll migration
**Last reconciled:** 10 August 2026 after P5-FBA-01 G05 verification and G06 R3 pre-publication review
**Current execution capability:** `P5-FBA-01` — ACTIVE; G01-G05 GREEN; G06 PRODUCT PUBLICATION PENDING
**G05 verified heads:** backend `0c3be43bc8268d60b97973e9faa4f98e716ec26f`; UI `062e3a1e43e311a79687ae5645ae2934b8e5cb35`; evidence SHA-256 `0f06ffbf06c886740d309007cba20fd1f988f728d35d0bebfc75d29e2a003e4d`

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

P5-JRF-01 delivered Original P5-A2 jurisdiction and registration foundations:

- stable work-location identity and immutable effective-dated versions;
- extensible payroll-jurisdiction hierarchy and exact parent-version lineage;
- deterministic approved override -> work location -> establishment fallback
  resolution with conflict/unresolved outcomes;
- immutable jurisdiction-resolution evidence;
- generic registration-type metadata and valid owner kinds;
- registration identity/version lifecycle, uniqueness and exact parent lineage;
- maker/verifier/final-approver segregation, rejection, suspension, expiry and
  renewal-successor history;
- `JAVA_REGEX_V1` application/domain identifier validation semantics;
- routine masked identifiers with permission-controlled audited reveal;
- bounded jurisdiction/registration readiness;
- aligned PostgreSQL/RLS, backend, OpenAPI, Keycloak and React operator flows.

Evidence:

- activation base: `ff581cafce3be5495d93932abfae3931b139358f`;
- publication commit: `c8ab727787a23b0b211caf27c2158300a38a8eab`;
- PR #36 merge/main: `6ee101bd398b745a0078bd0517b4e3797c571c2b`;
- V034 committed;
- local G03-C full regression GREEN;
- hosted PR #36 CI: 9/9 checks GREEN, including Maven, frontend, OpenAPI,
  Flyway/RLS, auth smoke, dependency review, SBOM, browser E2E and secret scan.

The final secret-scan correction changed only two synthetic test
`Idempotency-Key` literals, preserved the exact 82-path publication delta and
passed Gitleaks 8.24.3 over the complete PR history.

P5-JRF-01 did not implement bank accounts, authorised signatories, complete
configuration snapshots, complete foundation readiness, country-specific legal
rates/rules, filing/remittance, payroll calculation changes or production
deployment.

## 2A. Repository separation housekeeping closure

HK-UI-SPLIT-01 changed repository topology only; it did not change Payroll
business functionality, API semantics, OpenAPI semantics, database migrations,
Keycloak claim/permission semantics or the canonical story ledger.

Closure state:

- backend/program repository: `srinivasbs2000/hrms-payroll`;
- standalone UI repository: `srinivasbs2000/hrms-payroll-web`;
- UI history preserved by `git subtree split` with provenance retained;
- frontend lint/test/build/audit, frontend SBOM, npm dependency automation and
  browser E2E are UI-repository owned;
- browser E2E consumes the authoritative backend through
  `PAYROLL_BACKEND_REPOSITORY_PATH`;
- backend CI no longer duplicates frontend-owned gates;
- the embedded `frontend/payroll-web` source copy is removed by 01D;
- V001-V034 remain immutable and V035 remains unreserved.

## 3. Reconciled detailed-story status

The approved program contains 450 detailed source-linked stories:

| Status | Stories | Percentage |
|---|---:|---:|
| Implemented | 14 | 3.11% |
| Partially implemented | 156 | 34.67% |
| Not evidenced | 90 | 20.00% |
| Not started | 159 | 35.33% |
| Legal/domain revalidation | 31 | 6.89% |
| **Total** | **450** | **100%** |

P5-JRF-01 changed the canonical ledger as follows:

- `PLN-E01-005`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-006`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-007`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-012`: NOT EVIDENCED -> PARTIALLY IMPLEMENTED.

The six execution-candidate IDs `P5-E01-005` through `P5-E01-010` are not
the same numbering scheme as the canonical `PLN-E01-*` rows. In particular,
canonical `PLN-E01-008`, `009` and `010` are bank accounts, signatories
and snapshots and remain unchanged because those were explicit exclusions.

The original 72 broad control rows remain:

- 44 partially implemented;
- 24 not started;
- 4 requiring legal/domain revalidation.

No broad control row is marked fully complete while material linked stories
remain unfinished.

The machine-readable authority is:

`backlog/payroll-detailed-story-status.csv`

## 4. Execution-label to original-package mapping

| Completed execution increment | Primary original package mapping |
|---|---|
| Current P5-A1 — organisation hierarchy closure | Original P5-A1 |
| Current P5-A2 — component catalogue and named bases | Primarily original P5-B1, plus selected P5-B3 lifecycle/workbench controls |
| Current P5-A3 — salary structure, CTC, eligibility and simulation | Primarily original P5-B4, P5-B5 and selected P5-B6 controls |
| P5-JRF-01 — jurisdiction and registration foundations | Original P5-A2 |

Execution labels and original PLN-01 package identifiers remain separate fields.
Reuse of a label does not imply completion of the original package with the same
textual identifier.

## 5. Current original-package position

- Original P5-A1: complete.
- Original P5-A2 — jurisdiction and registration foundations: complete through
  P5-JRF-01 / PR #36.
- Original P5-A3 — foundation bank, authority, snapshots and readiness:
  partially evidenced; bank/signatory/snapshot and complete readiness scope remains.
- Original P5-A4 — pay groups, period generation and milestone rules:
  partially implemented and dependency-ready.
- Original P5-A5: partially implemented and dependent on P5-A4.
- Original P5-B1: substantially implemented; gap closure remains.
- Original P5-B2 and P5-B3: partially implemented.
- Original P5-B4, P5-B5 and P5-B6: partially implemented by current P5-A3;
  material remaining scope is recorded in the story ledger.
- P5-C1 through P5-C5: incomplete.

## 6. Closed execution identity

**P5-JRF-01 — Jurisdiction and Registration Foundations**

- **Original program mapping:** Original P5-A2
- **Scope authority:** `docs/planning/pln-01/p5-jrf-01-jurisdiction-registration-foundations-scope.md`
- **State:** MERGED / CLOSED
- **Product write owner:** None
- **Historical branch:** `feature/p5-jrf-01-jurisdiction-registration-foundations` retained
- **Activation base:** `ff581cafce3be5495d93932abfae3931b139358f`
- **Publication commit:** `c8ab727787a23b0b211caf27c2158300a38a8eab`
- **PR/merge:** PR #36 / `6ee101bd398b745a0078bd0517b4e3797c571c2b`
- **Migration:** V034 committed and immutable
- **Temporary dependency-security exception authority:** released by this closure

## 6A. Active execution identity

**P5-FBA-01 — Foundation Banking & Authority**

- **Original program mapping:** Original P5-A3
- **Primary canonical stories:** PLN-E01-008 and PLN-E01-009
- **Cross-cutting partial scope:** bank/signatory portions of PLN-E01-011 and PLN-E01-012
- **Scope authority:** `docs/planning/pln-01/p5-fba-01-foundation-banking-authority-scope.md`
- **Activation base:** `0cae307b0f5e7bcd05b47836e6e4df24c8701add`
- **Implementation branch:** `feature/p5-fba-01-foundation-banking-authority`
- **Migration:** V035 reserved exclusively to P5-FBA-01
- **Backend owner:** organisation foundation plus bounded database/OpenAPI/Keycloak/payroll-boot integration
- **UI owner:** standalone `srinivasbs2000/hrms-payroll-web`
- **Explicit exclusion:** PLN-E01-010 snapshots and complete foundation-readiness closure

## 7. Immediate next action

G01-G05 are green. Execute G06 R3 product publication, post-merge story reconciliation and status closure.

1. G01 — V035 database/RLS/crypto foundation.
2. G02 — employer bank-account backend with masked read and audited reveal.
3. G03 — signatory/delegated authority and bounded banking readiness.
4. G04 — OpenAPI, Keycloak and backend integration.
5. G05 — standalone React UI and cross-repository E2E.
6. G06 — R3 critical review, product publication, story reconciliation and status closure.

Do not implement PLN-E01-010 snapshots, payment execution or complete readiness
inside P5-FBA-01. V035 remains exclusively reserved until capability closure.

## 8. Remaining full-product scope

The payroll product continues through:

- remaining P5 foundation/configuration/readiness gaps;
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
