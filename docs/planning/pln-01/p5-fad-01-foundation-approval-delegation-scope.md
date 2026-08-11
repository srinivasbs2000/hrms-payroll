# P5-FAD-01 — Foundation Approval & Delegation Scope Authority

**Status:** CLOSED after product merge and post-merge semantic reconciliation
**Reasoning level:** R3 — cross-foundation authorization architecture and migration ownership
**Repository authority:** `srinivasbs2000/hrms-payroll`
**UI repository:** `srinivasbs2000/hrms-payroll-web`
**Activation branch:** `docs/p5-fad-01-activation-authority`
**Product branch:** `feature/p5-fad-01-foundation-approval-delegation` retained as historical product branch
**Migration reservation:** V037 committed/immutable; V038 unreserved after closure
**Primary canonical story:** PLN-E01-011
**Activation effect on story status:** none
**Closure effect on story status:** PLN-E01-011 -> IMPLEMENTED

## 1. Required product outcome

Provide one reusable application-approval authority for high-risk Foundation
configuration workflows.

The authority must prove whether the authenticated actor is allowed to act as
VERIFIER or FINAL_APPROVER for the relevant LEGAL_ENTITY or
PAYROLL_STATUTORY_UNIT, foundation domain and approval action on the decision
date.

Existing domain maker/checker/final-approver lifecycle state machines remain in
their owning modules. Shared approval authority is an additional authorization
precondition and must not mutate domain state itself.

## 2. Authority assignment

Authority assignments must be:
- tenant-owned;
- stable UUID identified;
- effective-dated with half-open ranges;
- scoped to exactly one LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT;
- scoped to approval role and foundation domain/action;
- bound to an exact authenticated actor identity;
- auditable and history preserving;
- inactive outside their effective dates.

A service identity must not silently receive interactive final-approval
authority.

## 3. Delegation

Approval delegation must:
- reference an existing authority source;
- identify delegator and delegate actor;
- have explicit start and end dates;
- remain within the source authority effective period;
- never widen owner scope, approval role, domain or action;
- forbid self-delegation;
- preserve the original authority/delegation chain in decision evidence;
- stop authorizing immediately when the source authority is suspended,
  retired or no longer effective.

## 4. Domain integration rule

Currently implemented high-risk Foundation approval workflows that already
perform maker/verifier/final-approver lifecycle transitions must consume the
shared authority gate where the canonical story requires entity/PSU-scoped
approval.

The first implementation audit must enumerate the actual approval endpoints
before mutation and freeze an exact integration allow-list.

At minimum audit:
- organisation legal-entity / PSU / establishment high-risk approval paths;
- employer bank-account and authorised-signatory approval;
- statutory registration approval;
- compensation catalogue / salary-structure high-risk approval paths that are
  already implemented.

Do not expand Calendar/Pay-Group E02 functionality in this capability. Future
E02 approval workflows should reuse this authority once separately activated.

## 5. Architecture authority

`security` owns application approval-authority assignment, delegation,
resolution and the public authorization facade.

Business modules may depend on the public `security` contract. No business
module may import `security.internal` packages.

`security` must not depend on organisation/compensation/statutory internal Java
packages. Tenant-safe database FKs or narrowly controlled cross-schema
validation may be used where required to preserve LE/PSU scope integrity
without creating a Java dependency cycle.

The application endpoint permission and the shared approval-authority decision
are both required. Neither substitutes for the other.

Legal authorised-signatory authority remains separate from application
authorization and must not confer system permissions.

## 6. Persistence and migration authority

V001-V036 are immutable.

V037 is reserved exclusively for P5-FAD-01 after this activation authority
merges. Activation itself creates no V037 SQL.

Expected V037 concerns may include:
- tenant-safe authority assignment identity/version or equivalent immutable
  effective-dated model;
- tenant-safe effective-dated delegation;
- non-overlap / effective-range integrity;
- FORCE RLS and least-privilege grants;
- immutable consumed approval-decision evidence where needed.

Exact DDL is product-implementation work and is not authorized by activation
alone.

## 7. Maximum product boundary

A product runner must reduce this maximum boundary to an exact file allow-list
before mutation.

Backend/program maximum:
- `database/flyway/sql/V037__foundation_approval_delegation.sql`
- `database/flyway/README.md`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/**`
- `backend/security/pom.xml`
- `backend/security/src/main/java/com/acme/hrms/payroll/security/**`
- `backend/security/src/test/java/com/acme/hrms/payroll/security/**`
- owning high-risk Foundation domain POMs only where a new public `security`
  dependency is required
- exact existing approval service/controller/repository tests in organisation,
  statutory-deductions and compensation identified by the pre-implementation
  endpoint audit
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/**`
- relevant OpenAPI contracts only for new approval-authority administration or
  read APIs
- `deploy/local/keycloak/payroll-realm.json` only for exact new permissions
- exact CI/smoke verification registration only where required
- standing scope/quality/status/lineage/handoff documentation.

UI maximum:
- `src/App.tsx`
- `src/App.test.tsx`
- `src/features/foundation-approval-authority/**`
- exact Playwright actor/setup/spec/config files required for approval-authority
  administration and scoped approval E2E.

No other path is owned without an R3 authority amendment.

## 8. Explicit exclusions

P5-FAD-01 does not implement:
- new calendar/pay-group business functionality;
- country-specific statutory rules/rates/legal conclusions;
- payment execution or banking integration;
- employee bank accounts;
- retro/off-cycle/final settlement;
- accounting/ERP posting;
- migration/cutover/production operations;
- a generic BPM/workflow engine;
- legal-signatory delegation redesign.

## 9. Mandatory verification

Before product publication prove:
1. exact-base and exact allow-list;
2. V037 clean install and upgrade with V001-V036 unchanged;
3. tenant isolation / FORCE RLS;
4. assignment effective-date and owner-scope enforcement;
5. delegation cannot widen authority;
6. source authority expiry/suspension invalidates delegation;
7. maker cannot become final approver through direct or delegated authority;
8. wrong LE/PSU scope blocks approval;
9. endpoint permission alone is insufficient without shared authority;
10. shared authority alone is insufficient without endpoint permission;
11. existing domain lifecycle/audit evidence remains intact;
12. legal signatory authority does not grant application approval;
13. architecture rules show no forbidden internal-module dependency;
14. targeted tests plus full Maven verify;
15. exact merged-backend browser E2E if UI is implemented;
16. independent R3 review;
17. post-merge PLN-E01-011 reconciliation/status closure.

Activation alone changes no story status.

## 10. Closure result

P5-FAD-01 product evidence is merged and independently reviewed:

- activation authority: PR #53 /
  `b4267168892eb602764d194eb0f303f8d8233323`;
- G01 shared authority core: `64a34a3b4a58d3de8ccfd185a7da21102ec78b71`;
- G02 owner-scoped domain integration:
  `f581d582d6bfce8239370e2230a612df28e0024a`;
- R3 repair/product head:
  `2db3845785b8c178c9660f712056f79e5e5409ed`;
- product PR #55 / merge
  `a80e7b4da121665a8b1548acada6b96fac4dfa01`;
- hosted payroll-baseline run `31537285947`: GREEN.

Post-merge reconciliation marks PLN-E01-011 IMPLEMENTED. V037 is immutable;
V038 is unreserved. No next capability is activated and no product path remains
owned by P5-FAD-01 after this closure merges.
