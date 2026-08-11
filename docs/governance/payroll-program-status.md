# HRMS Payroll Program Status

**Status:** Canonical repository-wide program checkpoint
**Repository:** `srinivasbs2000/hrms-payroll`
**Repository topology:** backend/program authority here; React UI authority in `srinivasbs2000/hrms-payroll-web`
**Product reconciliation baseline:** P5-FSR-01 backend final merge `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI merge `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code a self-referential closure SHA here
**Latest merged product increment:** P5-FSR-01 backend PR #51 / `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI PR #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Latest merged quality increment:** P5-A3 React test hygiene through PR #33
**P5-JRF-01 product-status closure:** PR #39; post-merge authority closed
**P5-FBA-01 product-status closure:** PR #45; post-merge authority closure
**Active product write owner:** P5-FAD-01 implementation workstream after activation-authority merge
**Migration state:** V001–V036 committed and immutable
**Next migration:** V037 reserved exclusively for P5-FAD-01 after activation-authority merge
**Product deployment:** Greenfield; no evidenced production deployment or live customer payroll migration
**Last reconciled:** 11 August 2026 after P5-FSR-01 closure and fresh R3 selection of P5-FAD-01
**Current execution capability:** P5-FAD-01 — Foundation Approval & Delegation (ACTIVE after activation-authority merge)
**P5-FSR-01 product merges:** backend final `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`

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

P5-FSR-01 delivered the bounded immutable foundation-snapshot and readiness
closure of Original P5-A3:

- immutable cycle foundation-configuration snapshot identity/hash and exact
  approved version lineage;
- history-preserving populated V035 upgrade and immutable V036 persistence;
- input, calculation-request and result binding to the exact snapshot;
- drift rejection with no mutable-current fallback after sealing;
- composed `FOUNDATION_ONLY` readiness over configuration snapshot,
  employer-bank, signatory-authority and caller-declared full-period
  registration requirements;
- blocker/warning dimensions, findings and explicit exclusions;
- UTC application/database-session date authority hardened after cross-repo
  browser verification exposed the local-midnight mismatch;
- standalone React Foundation Readiness workspace with exact merged-backend
  browser E2E.

Evidence:

- G01 PR #47 / merge `16d2488252b8a5c3aecd64c0f43fe18b6743d6e8`;
- G02 PR #49 / merge `954ed05d11dcb367f6de6e1f3e78aafc17c8beab`;
- UTC runtime PR #51 / merge `74bbd65449adad7b7058d8afd96097b1e08d2a0a`;
- web PR #13 / merge `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`;
- hosted backend and web CI green, including exact cross-repository browser E2E.

P5-FSR-01 does not implement country-specific statutory rules/rates or legal
obligation inference, employee bank accounts, payment execution, retro/off-cycle
or final settlement, accounting/ERP posting, migration/cutover or production
operations. PLN-E01-011 also remains partial because reusable entity/PSU-scoped
application approver authorization and effective-dated approval delegation were
not added.

### Previous completed milestone — P5-FBA-01

P5-FBA-01 delivered employer bank accounts, authorised signatories and bounded
banking/signatory readiness through backend PR #44 /
`a0234d94ef280a41a744ea6e8483f786a497d211` and web PR #12 /
`5c45ab41ee3cb4466fac822c04c771f5de0ba119`; its post-merge status closure was
PR #45.

## 2A. Previous completed milestone — P5-JRF-01

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

## 2B. Repository separation housekeeping closure

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
- V001-V035 are now immutable; V036 is reserved only by the separately
  activated P5-FSR-01 capability.

## 3. Reconciled detailed-story status

The approved program contains 450 detailed source-linked stories:

| Status | Stories | Percentage |
|---|---:|---:|
| Implemented | 18 | 4.00% |
| Partially implemented | 154 | 34.22% |
| Not evidenced | 88 | 19.56% |
| Not started | 159 | 35.33% |
| Legal/domain revalidation | 31 | 6.89% |
| **Total** | **450** | **100%** |

P5-JRF-01 changed the canonical ledger as follows:

- `PLN-E01-005`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-006`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-007`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-012`: NOT EVIDENCED -> PARTIALLY IMPLEMENTED.

P5-FBA-01 changes the canonical ledger as follows:

- `PLN-E01-008`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-009`: NOT EVIDENCED -> IMPLEMENTED;
- `PLN-E01-011`: remains PARTIALLY IMPLEMENTED with stronger bank/signatory evidence;
- `PLN-E01-012`: remains PARTIALLY IMPLEMENTED with bounded banking/signatory readiness added.

The six execution-candidate IDs `P5-E01-005` through `P5-E01-010` are not
the same numbering scheme as the canonical `PLN-E01-*` rows. In particular,
canonical `PLN-E01-008`, `009` and `010` are bank accounts, signatories
and snapshots. P5-FBA-01 now implements `008` and `009`; `010` remains partially implemented and outside P5-FBA-01.

The original 72 broad control rows remain:

- 44 partially implemented;
- 24 not started;
- 4 requiring legal/domain revalidation.

No broad control row is marked fully complete while material linked stories
remain unfinished.

The machine-readable authority is:

`backlog/payroll-detailed-story-status.csv`
P5-FSR-01 post-merge reconciliation adds:

- `PLN-E01-010`: PARTIALLY IMPLEMENTED -> IMPLEMENTED;
- `PLN-E01-012`: PARTIALLY IMPLEMENTED -> IMPLEMENTED for the bounded generic
  `FOUNDATION_ONLY` readiness contract;
- `PLN-E01-011`: remains PARTIALLY IMPLEMENTED; reusable entity/PSU-scoped
  application approver authorization and effective-dated delegation remain open.

Country-specific registration obligation inference is not silently counted as
foundation readiness: generic registration requirements remain caller-declared
and an empty requirement list is explicitly not a legal conclusion.


## 4. Execution-label to original-package mapping

| Completed execution increment | Primary original package mapping |
|---|---|
| Current P5-A1 — organisation hierarchy closure | Original P5-A1 |
| Current P5-A2 — component catalogue and named bases | Primarily original P5-B1, plus selected P5-B3 lifecycle/workbench controls |
| Current P5-A3 — salary structure, CTC, eligibility and simulation | Primarily original P5-B4, P5-B5 and selected P5-B6 controls |
| P5-JRF-01 — jurisdiction and registration foundations | Original P5-A2 |
| P5-FBA-01 — employer banking and authorised-signatory authority | Original P5-A3 (bank/signatory slice) |
| P5-FSR-01 — immutable foundation snapshot and composed readiness closure | Original P5-A3 (snapshot/readiness slice) |

Execution labels and original PLN-01 package identifiers remain separate fields.
Reuse of a label does not imply completion of the original package with the same
textual identifier.

## 5. Current original-package position

- Original P5-A1: complete.
- Original P5-A2 — jurisdiction and registration foundations: complete through
  P5-JRF-01 / PR #36.
- Original P5-A3 — foundation bank, authority, snapshots and readiness:
  the bank/signatory/snapshot/readiness core is implemented through P5-FBA-01 and P5-FSR-01; the package remains partially implemented only because PLN-E01-011 reusable application approver/delegation controls remain open.
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

## 6A. Closed execution identity — P5-FBA-01

**P5-FBA-01 — Foundation Banking & Authority**

- **Original program mapping:** Original P5-A3 bank/signatory slice
- **State:** MERGED / CLOSED after status-closure PR #45
- **Backend product PR/merge:** #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`
- **UI product PR/merge:** #12 / `5c45ab41ee3cb4466fac822c04c771f5de0ba119`
- **Historical implementation branch:** `feature/p5-fba-01-foundation-banking-authority` retained
- **Migration:** V035 committed and immutable
- **Product write owner:** None after status closure
- **Stories implemented:** PLN-E01-008, PLN-E01-009
- **Stories remaining partial:** PLN-E01-011, PLN-E01-012
- **Explicit remaining Original P5-A3 core:** PLN-E01-010 snapshots and complete readiness

## 7. Closed execution identity — P5-FSR-01

**P5-FSR-01 — Foundation Snapshot & Readiness Closure**

- **Original program mapping:** Original P5-A3 snapshot/readiness slice
- **State:** MERGED / CLOSED after this status-closure PR
- **Scope authority:** `docs/planning/pln-01/p5-fsr-01-foundation-snapshot-readiness-closure-scope.md`
- **Backend G01 merge:** PR #47 / `16d2488252b8a5c3aecd64c0f43fe18b6743d6e8`
- **Backend G02 merge:** PR #49 / `954ed05d11dcb367f6de6e1f3e78aafc17c8beab`
- **Backend final runtime-hardening merge:** PR #51 / `74bbd65449adad7b7058d8afd96097b1e08d2a0a`
- **UI product merge:** PR #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
- **Historical product branch:** `feature/p5-fsr-01-foundation-snapshot-readiness-closure` retained
- **Migration:** V036 committed and immutable
- **Product write owner:** None after this status closure
- **Stories implemented:** PLN-E01-010, PLN-E01-012
- **Story remaining partial:** PLN-E01-011
- **Next migration:** V037 unreserved
- **Next capability:** not selected by this closure

After this closure merges, perform a fresh R3 reconciliation before assigning
any new product ownership or migration number.

## 7A. Active execution identity — P5-FAD-01

**P5-FAD-01 — Foundation Approval & Delegation**

- **Selection baseline:** P5-FSR-01 closure merge `940c24d85a11dfaf293fc1d660ede4132fd53acb`
- **Primary canonical story:** PLN-E01-011
- **State:** ACTIVE after this activation-authority merge; activation changes no story status
- **Scope authority:** `docs/planning/pln-01/p5-fad-01-foundation-approval-delegation-scope.md`
- **Activation branch:** `docs/p5-fad-01-activation-authority`
- **Product branch:** `feature/p5-fad-01-foundation-approval-delegation`
- **Migration:** V037 reserved exclusively after activation merge
- **Backend owner:** shared application-approval authority in `security`, with exact domain integrations determined by pre-implementation audit
- **UI owner:** bounded approval-authority administration/E2E only if required by the product increment
- **Story totals:** unchanged at 18 implemented / 154 partial / 88 not evidenced / 159 not started / 31 legal-domain revalidation

P5-FAD-01 closes only the reusable entity/PSU-scoped application approver and
effective-dated delegation gap. It does not expand E02 calendars/pay groups and
does not redesign legal authorised-signatory authority.

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
