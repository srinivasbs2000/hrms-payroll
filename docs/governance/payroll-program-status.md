# HRMS Payroll Program Status

**Status:** Canonical repository-wide program checkpoint
**Repository:** `srinivasbs2000/hrms-payroll`
**Repository topology:** backend/program authority here; React UI authority in `srinivasbs2000/hrms-payroll-web`
**Product reconciliation baseline:** P5-E2E-UI-01-B02-R01-G01 backend PR #66 / `246ca75983b37293b74fdb4baa44e093fa546f8f`; P5-E2E-UI-01 UI closure evidence PR #15 / `2a42f3909a2ee249ca26be8fb0e14e945f8903a9`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code a self-referential closure SHA here
**Latest merged product increment:** P5-E2E-UI-01-B02-R01-G01 product PR #66 / `246ca75983b37293b74fdb4baa44e093fa546f8f`
**Latest merged quality increment:** P5-E2E-UI-01 G05 UI PR #15 / `2a42f3909a2ee249ca26be8fb0e14e945f8903a9`, hosted `payroll-web-ci` all five checks GREEN
**P5-JRF-01 product-status closure:** PR #39; post-merge authority closed
**P5-FBA-01 product-status closure:** PR #45; post-merge authority closure
**Active product write owner:** P5-E2E-UI-01-B02-G01 after this reconciliation merges; bounded backend Java/HTTP contract exposure only
**Migration state:** V001–V039 committed and immutable
**Next migration:** V040 unreserved; no capability owns it
**Product deployment:** Greenfield; no evidenced production deployment or live customer payroll migration
**Last reconciled:** 14 August 2026 after R01-G01 database-contract publication and merge
**Current execution capability:** P5-E2E-UI-01-B02-G01 — Remaining backend contract exposure resumes after R01 closure
**P5-FAD-01 product merge:** PR #55 / `a80e7b4da121665a8b1548acada6b96fac4dfa01`
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

P5-A4 delivered the remaining Calendar & Pay Groups capability for
`PLN-E02-001` through `PLN-E02-010`:

- versioned pay groups plus deterministic PSU/establishment population routing;
- stable/versioned payroll-calendar identity and history-preserving successors;
- deterministic contiguous period generation across monthly, fortnightly,
  weekly, daily and authorised custom frequencies;
- input, calculation, approval, release and payment milestones;
- weekend/holiday adjustment with original and adjusted evidence;
- append-only publish/amend/retire lifecycle and immutable published history;
- fail-closed assignment/cycle compatibility and governed publication;
- tenant-safe operational views and lifecycle HTTP evidence.

Evidence: product PR #58 / merge `6ce57213c8d77e76d8addee55a92f0349229a314`, R3
`80441eb433afc15e89abbb940ab9f4a9c1eb2f26` and hosted run
`31634393939` with 7/7 required checks GREEN.

### Previous completed milestone — P5-FAD-01

P5-FAD-01 delivered the remaining reusable Foundation application-approval
authority and effective-dated delegation controls for PLN-E01-011:

- tenant-owned, effective-dated LE/PSU-scoped VERIFIER/FINAL_APPROVER authority;
- bounded source-authority delegation with no scope widening;
- endpoint permission AND shared authority required;
- maker/final-approver separation retained in domain lifecycle rules;
- authenticated Keycloak service accounts denied interactive final approval;
- immutable consumed authority/delegation decision evidence;
- narrowly bounded pending-owner bootstrap for initial organisation approval;
- no E02 expansion and no legal-signatory/application-access conflation.

Evidence: product PR #55 / merge
`a80e7b4da121665a8b1548acada6b96fac4dfa01`, exact product head
`2db3845785b8c178c9660f712056f79e5e5409ed`, local full Maven verification,
independent R3 review and hosted payroll-baseline run `31537285947` GREEN.

### Previous completed milestone — P5-FSR-01

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
| Implemented | 21 | 4.67% |
| Partially implemented | 155 | 34.44% |
| Not evidenced | 84 | 18.67% |
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

P5-FAD-01 post-merge reconciliation then closes the remaining Foundation
approval/delegation gap:

- `PLN-E01-011`: PARTIALLY IMPLEMENTED -> IMPLEMENTED through PR #55 /
  `a80e7b4da121665a8b1548acada6b96fac4dfa01`.

Post-FAD totals are 19 implemented / 153 partially implemented / 88 not
evidenced / 159 not started / 31 legal-domain revalidation = 450.

Country-specific registration obligation inference is not silently counted as
foundation readiness: generic registration requirements remain caller-declared
and an empty requirement list is explicitly not a legal conclusion.


## 3A. End-to-end story/UI reconciliation

The 13 August 2026 end-to-end reconciliation audits all 29 stories previously
marked IMPLEMENTED against the authoritative React UI baseline
`8e8b47c829ac33aa2495ef07fba0ae2afd51e770` and classifies UI applicability
for all 450 detailed stories.

Result:

- 18 remain IMPLEMENTED with required end-to-end UI/operational evidence;
- 11 are downgraded to PARTIALLY IMPLEMENTED because required UI is partial or
  missing;
- all remaining 421 stories now have explicit UI applicability;
- `backlog/payroll-story-ui-applicability.csv` is mandatory for future
  activation and closure;
- P5-A5/E03 must not activate until the 11 current end-to-end UI gaps are closed
  or an explicit owner-approved sequencing decision supersedes this gate.

The backend/product merges remain valid historical evidence. This reconciliation
changes story completion classification; it does not roll back backend code.
## 4. Execution-label to original-package mapping

| Completed execution increment | Primary original package mapping |
|---|---|
| Current P5-A1 — organisation hierarchy closure | Original P5-A1 |
| Current P5-A2 — component catalogue and named bases | Primarily original P5-B1, plus selected P5-B3 lifecycle/workbench controls |
| Current P5-A3 — salary structure, CTC, eligibility and simulation | Primarily original P5-B4, P5-B5 and selected P5-B6 controls |
| P5-JRF-01 — jurisdiction and registration foundations | Original P5-A2 |
| P5-FBA-01 — employer banking and authorised-signatory authority | Original P5-A3 (bank/signatory slice) |
| P5-FSR-01 — immutable foundation snapshot and composed readiness closure | Original P5-A3 (snapshot/readiness slice) |
| P5-FAD-01 — shared application approval authority and delegation | Original P5-A3 (approval/delegation slice) |
| P5-A4 — pay groups, periods and calendar lifecycle | Original P5-A4 |

Execution labels and original PLN-01 package identifiers remain separate fields.
Reuse of a label does not imply completion of the original package with the same
textual identifier.

## 5. Current original-package position

- Original P5-A1: complete.
- Original P5-A2 — jurisdiction and registration foundations: complete through
  P5-JRF-01 / PR #36.
- Original P5-A3 — foundation bank, authority, snapshots and readiness:
  complete through P5-FBA-01, P5-FSR-01 and P5-FAD-01; employer banking,
  legal signatory authority, immutable snapshots/readiness and reusable
  application approver/delegation controls are all implemented within their
  bounded generic Foundation contracts.
- Original P5-A4 — pay groups, period generation and milestone rules: backend/domain
  capability is complete through P5-A4 / product PR #58, but end-to-end story
  completion is PARTIAL until the required E02 operator UI gaps are closed.
- Original P5-A5: partially implemented and dependency-unblocked by P5-A4 closure; separate R3 selection and activation remain mandatory.
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

## 7A. Closed execution identity — P5-FAD-01

**P5-FAD-01 — Foundation Approval & Delegation**

- **Original program mapping:** Original P5-A3 approval/delegation slice
- **Primary canonical story:** PLN-E01-011
- **State:** MERGED / CLOSED after this status-closure PR
- **Scope authority:** `docs/planning/pln-01/p5-fad-01-foundation-approval-delegation-scope.md`
- **Activation merge:** PR #53 / `b4267168892eb602764d194eb0f303f8d8233323`
- **Product head:** `2db3845785b8c178c9660f712056f79e5e5409ed`
- **Product merge:** PR #55 / `a80e7b4da121665a8b1548acada6b96fac4dfa01`
- **Historical product branch:** `feature/p5-fad-01-foundation-approval-delegation` retained
- **Migration:** V037 committed and immutable
- **Product write owner:** None after this status closure
- **Story implemented:** PLN-E01-011
- **Story totals:** 19 implemented / 153 partial / 88 not evidenced / 159 not started / 31 legal-domain revalidation
- **Next migration:** V038 unreserved
- **Next capability:** not selected by this closure

P5-FAD-01 did not expand E02 calendars/pay groups, legal-signatory authority,
country legal rules/rates, payment execution, retro/off-cycle/final settlement,
accounting, migration/cutover or production operations.

After this closure merges, perform a fresh R3 reconciliation before assigning
new product ownership or reserving V038.

## 8. Remaining full-product scope

The payroll product continues through:

- remaining P5 calendar/pay-group and later configuration gaps;
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

<!-- P5-A4-PROGRAM-STATUS-CLOSURE -->
### P5-A4 canonical ledger reconciliation

P5-A4 product PR #58 / `6ce57213c8d77e76d8addee55a92f0349229a314` changes:

- `PLN-E02-001` -> IMPLEMENTED;
- `PLN-E02-002` -> IMPLEMENTED;
- `PLN-E02-003` -> IMPLEMENTED;
- `PLN-E02-004` -> IMPLEMENTED;
- `PLN-E02-005` -> IMPLEMENTED;
- `PLN-E02-006` -> IMPLEMENTED;
- `PLN-E02-007` -> IMPLEMENTED;
- `PLN-E02-008` -> IMPLEMENTED;
- `PLN-E02-009` -> IMPLEMENTED;
- `PLN-E02-010` -> IMPLEMENTED.

The resulting 450-story ledger is 29 implemented, 147 partially implemented,
84 not evidenced, 159 not started and 31 requiring legal/domain revalidation.
V038 is immutable. V039 is unreserved. P5-A4 retains no write ownership.
Original P5-A5/E03 is dependency-unblocked but is not activated by this closure.

<!-- P5-E2E-UI-01-ACTIVATION -->
## P5-E2E-UI-01 activation authority

P5-E2E-UI-01 is the bounded end-to-end UI gap-closure capability selected after
the 13 August 2026 450-story reconciliation.

- exact stories: PLN-E01-011 and PLN-E02-001 through PLN-E02-010;
- selected-story UI applicability revalidated: YES;
- UI product repository: `srinivasbs2000/hrms-payroll-web`;
- UI baseline: `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`;
- backend/program baseline: `9394cc35660a45cb14febd781b484b4c3bcbc8a3`;
- backend product writes: prohibited;
- migration: none; V039 remains unreserved;
- story statuses remain 18 IMPLEMENTED / 158 PARTIALLY IMPLEMENTED at activation;
- P5-A5/E03 remains inactive.

Scope authority:
`docs/planning/pln-01/p5-e2e-ui-01-story-ui-gap-closure-scope.md`.

<!-- P5-E2E-UI-01-B01-ACTIVATION -->
## P5-E2E-UI-01-B01 bounded backend amendment

Real-browser P5-E2E-UI-01 evidence demonstrated that V037 approval-authority
state functions are reached with `java.time.Instant` JDBC parameters that pgJDBC
cannot bind to the existing `timestamptz` argument. Source review proves the
same defect affects suspend, retire and delegation revoke.

B01 is separately bounded backend amendment authority under the existing
P5-E2E-UI-01 backend-defect rule. It authorizes only:

- `ApprovalAuthorityRepository.java` JDBC time binding for the three state calls;
- `ApprovalAuthorityEnforcementApiIT.java` regression coverage.

No migration, OpenAPI or permission change is authorized. V039 remains
unreserved. The existing UI working tree stays open and is not replayed.

Scope authority:
`docs/planning/pln-01/p5-e2e-ui-01-b01-approval-state-time-binding-amendment.md`.

<!-- P5-E2E-UI-01-G06-RECONCILIATION -->
## P5-E2E-UI-01 G06 post-G05 reconciliation

UI PR #15 merged at `2a42f3909a2ee249ca26be8fb0e14e945f8903a9` from exact G05 commit `16c1eea7eadd45979fdf879ff86ef04878bbb3ef`;
hosted `payroll-web-ci` completed all five checks successfully. Backend
authority remained `28f8a7208d31546cc3bec3fa31004fe4e5a1bc8b` during G05.

Story outcomes after applying the story-completion gate conservatively:

- IMPLEMENTED: `PLN-E01-011`, `PLN-E02-001`, `PLN-E02-005`;
- remain PARTIALLY IMPLEMENTED: `PLN-E02-002`, `003`, `004`, `006`,
  `007`, `008`, `009`, `010`.

Current detailed-story totals are 21 IMPLEMENTED / 155 PARTIALLY IMPLEMENTED /
84 NOT EVIDENCED / 159 NOT STARTED / 31 legal-domain revalidation = 450.

P5-E2E-UI-01 therefore reaches its current UI-authority contract boundary but is
not a full 11-story closure. P5-A5/E03 remains inactive. V039 remains unreserved.

**Next controlled action:** independently audit and activate a separately bounded
`P5-E2E-UI-01-B02` backend contract amendment for the demonstrated remaining
contract gaps; do not perform backend product writes under G06.

<!-- P5-E2E-UI-01-B02-ACTIVATION -->
## P5-E2E-UI-01-B02 activation

The post-G06 read-only audit confirmed that the remaining E02 blockers are
contract-exposure gaps over existing V038 persistence/function foundations.

B02 is activated to expose only the bounded calendar configuration, pay-group
routing administration and compatibility/readiness contracts required by the
eight remaining E02 rows.

No story status changes in this activation. Program totals remain 21
IMPLEMENTED / 155 PARTIALLY IMPLEMENTED / 84 NOT EVIDENCED / 159 NOT STARTED /
31 LEGAL/DOMAIN REVALIDATION = 450.

No migration is authorized or expected. V039 remains unreserved. If B02-G01
proves a schema requirement, that sub-boundary must stop for separate review.
P5-A5/E03 remains inactive.

Next controlled action after this activation merges: B02-G01 backend contract
implementation preflight against the activation merge SHA.
<!-- P5-E2E-UI-01-B02-R01-ACTIVATION -->
## P5-E2E-UI-01-B02-R01 activation

B02-G01 preflight proved that V038 cannot express safe effective end-dating of
an existing pay-group routing rule: direct routing-table UPDATE is revoked from
`payroll_app`, while V038 exposes create and retire but no effective-end
function.

R01 is activated as the separately reviewed, smallest database-contract
amendment. The activation itself is governance-only and does not reserve V039.

After this activation merges, R01-G01 may reserve V039 only if live/local
migration preflight still proves it is unreserved, and only for the bounded
routing effective-end function/grant/test contract. No table, column, index,
history rewrite or routing-policy redesign is authorized.

Story totals remain unchanged. P5-A5/E03 remains inactive.

**Next controlled action after activation merge:** R01-G01 database-contract
implementation preflight against the activation merge SHA.

<!-- P5-E2E-UI-01-B02-R01-G01-CLOSURE -->
## P5-E2E-UI-01-B02-R01-G01 database-contract closure

R01-G01 preflight v1.1 superseded v1.0 and proved that V039 remained the next
unreserved migration at activation merge `3c42d057e4e0bf941af7589d62087721bf88ea81`.
The bounded implementation commit `6d528362b6d9ccb5066f5c033caa8035b0f6ab82`
merged through PR #66 at `246ca75983b37293b74fdb4baa44e093fa546f8f`.

Closure evidence:

- V039 adds only the governed routing-rule effective-end function and minimum
  `payroll_app` EXECUTE grant;
- direct routing-table DML remains revoked;
- tenant mismatch, invalid range, stale version, inactive rule, replacement
  routing and audit/version behavior are covered;
- targeted migration verification ran 7 tests with no failures, errors or
  skips, and full Maven verification passed;
- hosted `payroll-baseline` passed the exact seven required checks;
- V038 remained immutable.

R01-G01 is CLOSED and retains no write ownership. V001–V039 are committed and
immutable; V040 is unreserved. Story totals remain 21 IMPLEMENTED / 155
PARTIALLY IMPLEMENTED / 84 NOT EVIDENCED / 159 NOT STARTED / 31 LEGAL/DOMAIN
REVALIDATION because the database contract alone does not complete the eight
remaining E02 end-to-end stories. P5-A5/E03 remains inactive.

**Next controlled action after this reconciliation merges:** resume
P5-E2E-UI-01-B02-G01 backend Java/HTTP artifact-contract preflight against
`246ca75983b37293b74fdb4baa44e093fa546f8f`. B02-G03 UI work remains blocked
until B02-G02 backend publication is complete.
