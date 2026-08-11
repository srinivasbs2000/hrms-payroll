# HRMS Payroll Master Design

**Status:** Living approved-design and architecture authority
**Repository:** `srinivasbs2000/hrms-payroll`
**Product reconciliation baseline:** P5-FSR-01 closure merge `940c24d85a11dfaf293fc1d660ede4132fd53acb`; UI product merge remains `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Current repository HEAD:** verify from local Git and live read-only GitHub; do not hard-code a self-referential closure SHA here
**Current product capability:** P5-FAD-01 — Foundation Approval & Delegation (ACTIVE after activation-authority merge)
**Latest merged quality increment:** P5-A3 React test hygiene through PR #33
**Latest merged product increment:** P5-FSR-01 backend final merge `74bbd65449adad7b7058d8afd96097b1e08d2a0a`; UI PR #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`; status closure PR #52 / `940c24d85a11dfaf293fc1d660ede4132fd53acb`
**P5-JRF-01 product-status closure:** PR #39
**P5-FBA-01 product-status closure:** PR #45
**Prior sprint baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`
**Last reconciled:** 11 August 2026 after P5-FSR-01 closure and P5-FAD-01 R3 selection
**Maintainers:** Project owner and the currently authorised capability workstream
**Full product scope authority:** `docs/product/payroll-product-scope-and-epic-catalog.md`
**Companion state document:** `docs/runbooks/project-continuation-handoff.md`

## 1. Purpose and authority

This document owns approved architecture, cross-cutting design rules and the
relationship between the complete Payroll product scope and the implemented
repository baseline.

The complete product capability and epic authority is:

`docs/product/payroll-product-scope-and-epic-catalog.md`

The machine-readable source backlog and delivery lineage are:

- `backlog/payroll-master-implementation-backlog.csv`
- `docs/governance/payroll-feature-delivery-lineage.md`

The current repository is a bounded vertical slice and must not be read as the
complete Payroll product.

## 2. Source-of-truth hierarchy

1. current local working tree and complete uncommitted diff;
2. live remote branch, commit, pull request and CI evidence;
3. committed migrations, code, tests, OpenAPI, security policy and `AGENTS.md`;
4. full product scope/epic catalog and source register;
5. this master design and decision register;
6. continuation handoff and thread registry;
7. quality, closure and reconciliation records;
8. conversation history only as a locator.

Record disagreements as `DOCUMENTATION CONFLICT`. Never silently reduce the
original approved product scope.

## 3. Complete product objective

Build an India-first, enterprise-grade, multi-tenant Payroll operating
platform that can operate independently and integrate with the wider HRMS.

End-to-end product journey:

`configuration -> onboarding/readiness -> inputs -> calculation -> validation -> approval -> payments -> statutory processing -> accounting -> documents/reporting -> reconciliation -> corrections -> audit -> migration/cutover -> BAU`

The complete product contains 18 epics across original releases R1-R4.

## 4. Implemented scope through Sprint 4

### 4.1 Organisation and tenancy

- tenant context and PostgreSQL RLS;
- legal entities, payroll statutory units and establishments;
- stable identities and immutable effective-dated versions;
- hierarchy, lifecycle, audit and transactional outbox evidence.

### 4.2 Payroll configuration and employee payroll

- pay groups and deterministic monthly calendars/periods;
- starter BASIC, HRA and SPECIAL_ALLOWANCE catalog;
- starter salary structures and immutable component lines;
- payroll relationships, assignments and employee payroll profiles;
- pay-group and salary assignments;
- permission-aware APIs and React workspaces.

### 4.3 Regular payroll execution

- fixed monthly starter calculation;
- calendar-day proration;
- controlled cycle and population resolution;
- immutable input snapshots;
- deterministic result, component and trace evidence;
- controlled recalculation;
- non-legal persisted draft-payslip view.

### 4.4 Jurisdiction-neutral statutory evidence

- effective-dated generic statutory rules;
- employee statutory profiles and exact assignments;
- deterministic evaluation;
- append-only ledger, signed corrections and balances;
- reconciliation and remittance-preparation evidence;
- secured APIs and operator workspace.

This does not establish an India legal rule pack.

## 5. Full-product implementation qualification

- 11 epics are `PARTIALLY IMPLEMENTED`;
- 6 epics are `NOT STARTED`;
- E09 is `REQUIRES LEGAL OR DOMAIN REVALIDATION`;
- no original epic is fully implemented against all source acceptance.

This prevents bounded Sprint slices from being misrepresented as complete
product epics.

## 6. Material remaining capabilities

- post-JRF bank, signatory, complete readiness and country-specific statutory/tax configuration;
- CTC, eligibility, simulation and broader salary-structure completion;
- complete bank/statutory/tax readiness;
- general formula and balance engines;
- complete input, cut-off, trial, approval, lock and release operations;
- current India statutory and salary-tax rule packs;
- retro, arrears, off-cycle, recoveries and final settlement;
- payments and banking;
- accounting, costing and ERP integration;
- legal documents, ESS, reporting, analytics and communications;
- complete SoD, access governance, retention, BCP, performance and DR;
- data migration, parallel Payroll, cutover and hypercare.

## 7. Architecture

- Java 21 and Spring Boot modular monolith.
- Maven multi-module repository under `backend/`.
- `backend/payroll-boot` is the composition root.
- React 18, TypeScript and Vite frontend in `srinivasbs2000/hrms-payroll-web`.
- PostgreSQL 17 with Flyway.
- Keycloak/OIDC development identity.
- OpenAPI 3.1 contracts.

Direct cross-module repository access, internal-package imports and
unapproved shared ownership are prohibited. Spring Modulith and ArchUnit
remain mandatory.

### 7.1 Repository ownership after HK-UI-SPLIT-01

The Payroll system is intentionally split across two repositories without
creating a third contract repository:

- `srinivasbs2000/hrms-payroll` is authoritative for product/program
  governance, backend code, PostgreSQL/Flyway, OpenAPI, Keycloak deployment,
  deterministic backend fixtures and backend CI;
- `srinivasbs2000/hrms-payroll-web` is authoritative for the React UI,
  frontend dependency automation, frontend SBOM, frontend CI and browser E2E;
- cross-repository E2E checks out the backend authority separately and supplies
  it through `PAYROLL_BACKEND_REPOSITORY_PATH`;
- the UI does not own or fork API/OpenAPI, database or legal/domain truth; and
- the history-preserving extraction lineage remains recorded in the UI
  repository's `EXTRACTION_PROVENANCE.md`.

## 8. Data and migration rules

- `database/flyway/sql` is the ordered migration authority.
- V001-V036 are committed and immutable.
- V037 is reserved exclusively for P5-FAD-01 after activation-authority merge.
- future migrations are forward-only and separately authorised from V037;
- tenant-owned FKs include tenant ownership;
- stable identity plus immutable effective-dated versions preserve lineage;
- consumed evidence is never rewritten;
- the original 112-table DDL is a logical design source, not a migration to
  apply directly.

The product remains greenfield with no evidenced production deployment or live
customer payroll data. Schema-version and upgrade tests preserve deterministic
developer/CI state and establish a safe future deployment path; they do not
represent a current production migration.

## 9. Security, reliability and API rules

- ENABLE/FORCE RLS for tenant-owned tables;
- runtime roles are non-owner and `NOBYPASSRLS`;
- transactions use `SET LOCAL`;
- identity is issuer plus subject;
- aggregate, idempotency, audit and outbox commit atomically;
- money uses `BigDecimal` and decimal strings;
- writes use idempotency keys where needed;
- optimistic changes use ETag/`If-Match`;
- errors use RFC 9457 and do not leak sensitive existence.

## 10. Deterministic calculation and evidence rules

The same immutable employee/configuration/input evidence and engine version
must reproduce the same result and hash.

Inject `Clock`, retain exact lineage and trace, avoid ordering dependence and
append corrections rather than mutating history.

## 11. Delivery and quality gates

1. static validation/compile;
2. focused unit tests;
3. focused integration tests;
4. migration/RLS/tenant tests when applicable;
5. full Maven `verify`;
6. frontend lint, tests and build when applicable;
7. OpenAPI validation when contracts change;
8. dependency, secret and security checks;
9. planned-versus-actual file review;
10. independent critical review for high-risk Payroll work.

A story is complete only when acceptance, verification, lineage and residual
risk are committed.

## 12. Sprint baseline ledger

| Sprint | Durable outcome | Migration range | Status |
|---|---|---|---|
| Sprint 0 | Repository/security/tenancy baseline | V001-V013 | Merged |
| Sprint 1 | Organisation, reliability, audit and architecture | V014-V016 | PR #2 merged |
| Sprint 2 | Configuration and employee-payroll foundation | V017-V022 | PR #3 merged |
| Sprint 3 | Regular execution and deterministic evidence | V023-V026 | PR #18 merged |
| Sprint 4 | Generic statutory lifecycle and evidence | V027-V030 | PR #19 merged; S4-06A quality closure merged through PR #28 |
| P5-A1 | Organisation hierarchy lifecycle closure | V031 | PR #25 merged |
| P5-A2 | General pay-component catalogue and named payroll bases | V032 | PR #30 merged; authority released |
| P5-A3 | Salary-structure design, CTC policy, typed eligibility and deterministic design-time simulation | V033 | PR #32 merged; PR #33 test-hygiene follow-up merged |
| P5-JRF-01 | Work-location, jurisdiction-resolution and statutory-registration foundations | V034 | PR #36 merged; PR #39 post-merge authority closure; ownership released |
| P5-FBA-01 | Employer banking, authorised signatories, delegated authority and bounded banking readiness | V035 | Backend PR #44 and UI PR #12 merged; PR #45 post-merge authority closure; ownership released |
| P5-FSR-01 | Immutable foundation configuration snapshot and composed foundation readiness closure | V036 reserved | ACTIVE after activation-authority merge; implementation evidence not yet claimed |
| P5-FAD-01 | Shared entity/PSU-scoped application approval authority and effective-dated delegation | V037 reserved | ACTIVE after activation-authority merge; product evidence not yet claimed |
| Governance | Living design and reconciliation controls | None | PR #20, PR #21, PR #26, PR #29, PR #31, PR #39 and PR #45 merged |
| Sprint 4 quality | Secured statutory HTTP/PostgreSQL integration closure | None | PR #28 merged; Thread 7 closed and ownership released |

## 13. Current controlled debt and planning

- Current P5-A3 is merged through PR #32; PR #33 merged the React test-hygiene follow-up.
- P5-JRF-01 and P5-FBA-01 are merged and authority-closed; neither retains
  product ownership.
- V001-V036 are committed and immutable; V037 is reserved exclusively for
  P5-FAD-01 after activation-authority merge.
- The 450 detailed stories reconcile to 18 implemented, 154 partially
  implemented, 88 not evidenced, 159 not started and 31 requiring
  legal/domain revalidation.
- Current execution labels P5-A2 and P5-A3 must not be confused with the
  original PLN-01 packages with the same identifiers.
- Original P5-A2 jurisdiction/registration is complete through P5-JRF-01.
- P5-FSR-01 is merged/status-closed. P5-FAD-01 is the active bounded capability
  for the remaining PLN-E01-011 reusable application approver/delegation gap.
- S4-06B remains planned and not authorised.
- E09 still requires current legal/domain revalidation.
## 14. Documentation model

| Artifact | Responsibility |
|---|---|
| Product scope and epic catalog | Complete intended capability boundary |
| Master backlog | Original 72-row control list and evidence classification |
| Feature delivery lineage | Story/migration/commit/PR/closure mapping |
| Gap assessment | Implemented, partial, missing and revalidation baseline |
| Roadmap | Controlled future sequence and planning gates |
| Master design | Architecture and product-to-implementation relationship |
| Decision register | Stable material decisions |
| Handoff | Current state and next authorised action |
| Thread registry | Ownership and write boundaries |

## 15. Change history

| Date | Change | Evidence |
|---|---|---|
| 1 Aug 2026 | Initial living master design | PR #20 |
| 1 Aug 2026 | Cross-thread completion controls | PR #21 `18d5ca3554ff217140b7e3c443d086d63bd02070` |
| 1 Aug 2026 | Recovered 18-epic/72-row product baseline and recorded PLN-01 | Full-product reconciliation; MDR-028-MDR-033 |
| 3 Aug 2026 | Merged P5-A1 organisation hierarchy closure | PR #25; V031 |
| 4 Aug 2026 | Merged post-P5-A1 authority reconciliation | PR #26; `961465cb551f3757a6f51f1322e6b46c32317b16` |
| 4 Aug 2026 | Activated and completed the S4-06A test-only integration closure under Thread 7 | Planning checkpoint; exact six-file allow-list; PR #28 |
| 4 Aug 2026 | Merged S4-06A statutory API integration quality closure and released Thread 7 ownership | PR #28; merge `12f3210c91ca95f3f331911d4cdc1755f2afd701`; CI run 100 |
| 4 Aug 2026 | Merged final S4-06A authority closure | PR #29; merge `d7b7a7c193b964fb5606e0cb74f92ad6fd6db3e8` |
| 4 Aug 2026 | Activated P5-A2 capability preparation and reserved V032 | P5-A2 scope definition and critical review; exact 46-path maximum boundary |
| 5 Aug 2026 | Merged P5-A2 product implementation and final authority closure | PR #30 merge `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`; PR #31 merge `887347fb23b35ca72c479f377c0f6e3a1bf89722` |
| 5 Aug 2026 | Activated P5-A3 preparation and exclusively reserved V033 | P5-A3 planning package `d704409e9fb4792f15ce05d5ade5cb4f04c80be04e0dc1d31d357402f12e5f77`; independent critical review; exact 69-path maximum boundary |
| 5 Aug 2026 | Merged P5-A3 implementation and quality follow-up | PR #32; PR #33; V033 immutable |
| 8 Aug 2026 | Merged P5-JRF-01 and completed post-merge authority closure | PR #36 product merge `6ee101bd398b745a0078bd0517b4e3797c571c2b`; PR #39 closure; V034 immutable; V035 was then unreserved |
| 10 Aug 2026 | Merged P5-FBA-01 and completed post-merge authority closure | Backend PR #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`; UI PR #12 / `5c45ab41ee3cb4466fac822c04c771f5de0ba119`; PR #45 closure; V035 immutable |
| 10 Aug 2026 | R3-selected and activated P5-FSR-01 governance authority | Primary PLN-E01-010 and PLN-E01-012; V036 reserved after activation-authority merge; no product evidence claimed by activation |

## 16. P5-A2 delivered architecture and authority closure

P5-A2 completed the general compensation catalogue foundation with behavioural
component classification, stable named payroll-base identities, effective-dated
base versions and append-only exact memberships. Runtime configuration remains
tenant scoped, maker-checker controlled, audited and outbox-published. Legal
classifications and rates remain rule-pack data outside this capability.

PR #30 merged implementation commit `c30cb1f2f0c16cd78387bb9551b93825bc7ef688` as
`aeb4b1560e7c7d6147bb288ef989b15ad1be4946`; post-merge workflow run `30957450623` succeeded. The
feature branch is retained as historical evidence. No active P5-A2 write owner
remains. V032 is committed and immutable. V033 was unreserved at P5-A2 closure; its current reservation is governed by the active P5-A3 authority.

## 17. Historical P5-A3 activation architecture

P5-A3 is bounded to salary-structure design, versioned CTC policies, typed
effective-dated eligibility-rule configuration and deterministic design-time
simulation, comparison and validation. It remains separate from official
payroll execution and the future general calculation engine.

At activation, the preparation branch was
`feature/p5-a3-salary-structure-ctc-eligibility-simulation` and V033 was
exclusively reserved. That reservation was consumed by the merged V033
implementation through PR #32 and is now historical. The exact 69-path maximum
boundary and blocking critical-review controls are recorded in
`docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md`.

P5-A3 must preserve existing structure/assignment UUID lineage, the V025/V026
golden calculation behaviour and all P5-A2 component/base semantics. It must not
encode current legal rates or conclusions, persist live employee eligibility,
or mutate official payroll results.

## 18. P5-A3 implemented architecture

V033 extends the compensation configuration model with schema-1 salary
structures, versioned CTC policies, typed eligibility rules and immutable
validation evidence. Existing schema-0 structure/line UUIDs and V021 assignment
lineage remain intact.

The design-time simulator resolves exact approved component, policy, rule and
base-membership versions. It produces deterministic annual/monthly values,
cost-view reconciliation, hashes, warnings and blockers. It is not part of the
official payroll execution path and does not mutate payroll results, traces,
cycles or employee assignments.

The implementation is consolidated in the existing salary-structure
controller/service/view model rather than the larger initially anticipated
class split. This is an approved implementation consolidation, not missing
scope.

Local G07 verification covered OpenAPI, frontend, Maven, PostgreSQL 17,
migrations through V033 and 220 backend tests at the pre-publication checkpoint.
P5-A3 subsequently merged through PR #32 and its React test-hygiene follow-up
merged through PR #33.

<!-- PROGRAM-RECONCILIATION-2026-08-06 -->
## 19. Program reconciliation after P5-A3

The canonical current checkpoint is
`docs/governance/payroll-program-status.md`. The detailed story authority is
`backlog/payroll-detailed-story-status.csv`.

The reconciliation separates current execution labels from original PLN-01
package identifiers. It recommended original P5-A2 jurisdiction and registration
foundations as the next package. That recommendation was fulfilled by
P5-JRF-01, which merged through PR #36 and completed post-merge authority
closure through PR #39.

Every future product increment requires a repository-wide status-closure update
before the next increment is selected.

## 20. P5-JRF-01 implemented architecture

P5-JRF-01 adds a dedicated work-location identity/version model, an extensible
payroll-jurisdiction hierarchy, deterministic resolution with immutable
evidence, and a generic statutory-registration type/instance/version lifecycle.

Generic resolution precedence is approved override, approved work location,
establishment-derived fallback, then unresolved. Material disagreement is
surfaced as conflict rather than silently resolved. Residential address is not
used as a fallback.

Registration activation preserves maker, independent verifier and independent
final-approver evidence. Parent registrations reference exact versions and must
use the same jurisdiction or an approved ancestor. Renewal and other successor
drafts append history and do not hide the current effective approved/active
version before the successor itself becomes effective and approved.

PostgreSQL 17 remains intentional. FORCE RLS, tenant-safe foreign keys,
exclusion/uniqueness constraints and narrow controlled row-lock functions are
defense-in-depth integrity controls. Runtime direct table UPDATE remains
prohibited where the capability uses EXECUTE-only lock functions.

Registration identifier format metadata is application-level
`JAVA_REGEX_V1`. Routine APIs expose masked identifiers; exact reveal uses the
dedicated `statutory-registration.identifier.read` permission and produces
audit evidence without copying the identifier into audit/outbox payloads.

AC-G03-B1 v1.3 and AC-G03-B2 v1.2 were locally GREEN before publication.
Country-specific legal rates/formulas, filing, remittance, bank/signatory scope,
complete readiness, employee statutory profiles, payroll calculation/assignment
changes, minimum wage, retro and production deployment remain outside this
capability.

P5-JRF-01 publication commit `c8ab727787a23b0b211caf27c2158300a38a8eab`
merged through PR #36 as `6ee101bd398b745a0078bd0517b4e3797c571c2b`
with all nine hosted checks GREEN. PR #39 completed the post-merge story-ledger
and authority closure. V034 is committed and immutable. P5-FBA-01 subsequently
consumed V035 and closed through PR #45; V035 is now committed and immutable.


## 21. P5-FBA-01 implemented architecture

P5-FBA-01 completes the employer bank-account and authorised-signatory slice of
Original P5-A3. It adds stable tenant-safe identities, immutable effective-dated
versions, legal-entity/PSU ownership, encrypted bank secret metadata, masked
routine reads, audited restricted reveal, maker/verifier/final-approver
segregation and purpose/currency/amount-scoped legal authority.

Banking readiness is intentionally bounded to bank/signatory prerequisites and
does not mark a PSU or pay group globally payroll-ready. Legal signatory
authority remains separate from application access. Backend PR #44 and UI PR
#12 merged the capability; PR #45 closed post-merge authority. V035 is committed
and immutable. PLN-E01-008 and PLN-E01-009 are implemented; PLN-E01-011 and
PLN-E01-012 remain partial and PLN-E01-010 remains open.

## 22. P5-FSR-01 activated architecture

P5-FSR-01 — Foundation Snapshot & Readiness Closure is the R3-selected bounded
next capability. Its primary canonical stories are PLN-E01-010 immutable
configuration snapshots and PLN-E01-012 composed foundation readiness.
PLN-E01-011 remains partial unless implementation evidence separately proves the
remaining reusable application approver/delegation controls.

`payroll-operations` owns cycle-time foundation configuration sealing because it
already owns payroll-cycle population and input sealing. Cross-domain capture
will use controlled database-level version selection/sealing, consistent with
the V024 immutable-input-snapshot pattern, rather than importing other modules'
internal implementation types. The calculation path must bind to the exact
foundation-configuration snapshot identity/hash and must not silently fall back
to mutable current configuration.

Composed foundation readiness may aggregate already-authoritative organisation,
jurisdiction/registration, employer-bank/signatory, pay-group/calendar and
configuration-snapshot evidence. It must distinguish blocking findings from
warnings and must not claim payment, country-specific statutory-rule, retro,
off-cycle, settlement, accounting or production-cutover readiness.

V036 is reserved exclusively for P5-FSR-01 after activation-authority merge.
The activation increment itself creates no V036 SQL or product implementation;
implementation starts only from the activation-merged `main` under the exact
scope authority.
