# P5-JRF-01 — Jurisdiction and Registration Foundations

**Status:** LOCAL IMPLEMENTATION VERIFIED — PUBLICATION PENDING
**Execution capability:** `P5-JRF-01`
**Original program mapping:** `P5-A2 — Jurisdiction and Registration Foundations`
**Dependency:** Original P5-A1 complete
**Activation base:** `ff581cafce3be5495d93932abfae3931b139358f`
**Branch:** `feature/p5-jrf-01-jurisdiction-registration-foundations`
**Migration:** V034 implemented locally and reserved to P5-JRF-01 until publication/closure

## 1. Objective

Provide the payroll foundation with effective-dated work-location identity,
deterministic payroll-jurisdiction resolution and a generic, auditable statutory
registration framework.

The capability must be tenant-safe, historically reproducible and suitable for
downstream statutory/tax readiness without implementing jurisdiction-specific
rates or calculations.

## 2. Source implementation candidates

### P5-E01-005 — Work-location identity and effective-dated attributes

Deliver a stable work-location identity and effective-dated version model
sufficient for payroll-relevant geographic and organisational attribution.

Required qualities:

- stable identity plus effective-dated business versions;
- country, state and local-jurisdiction attributes where applicable;
- exact tenant ownership;
- no invalid effective overlap for one location identity;
- location history must remain reproducible;
- work location must remain distinct from legal entity and establishment.

### P5-E01-006 — Payroll-jurisdiction resolution rules

Resolve the applicable payroll jurisdiction from approved payroll-relevant
location inputs rather than residential address alone.

The resolution model must:

- be effective-dated;
- be deterministic for the same approved inputs and date;
- preserve the source/input used for the decision;
- retain the resolved country/state/local jurisdiction;
- support approved override only through explicit controlled evidence;
- expose unresolved or ambiguous outcomes rather than silently guessing.

### P5-E01-007 — Jurisdiction conflict and readiness validation

Introduce foundation validation that can identify:

- missing jurisdiction;
- ambiguous/conflicting jurisdiction;
- expired or inactive location configuration;
- unapproved configuration;
- registration incompatibility relevant to readiness.

Blocking versus warning treatment must be explicit and testable.

This candidate draws from `PLN-E01-005` and the relevant foundation-readiness
portion of `PLN-E01-012`. It does not pull the whole later readiness package
into this increment.

### P5-E01-008 — Statutory-registration type metadata and applicability

Create a generic registration-type model rather than one table/type per scheme.

Registration type metadata should be able to describe, as applicable:

- scheme / obligation type;
- authority;
- jurisdiction level;
- valid owner types (legal entity / PSU / establishment or future approved type);
- whether a parent registration is required;
- identifier/format metadata without hard-coding legal rates;
- applicability dimensions required to select a valid registration;
- lifecycle/approval requirements.

### P5-E01-009 — Statutory-registration instance lifecycle and uniqueness

Registration instances must retain:

- registration type;
- authority/jurisdiction;
- owning legal entity / PSU / establishment as applicable;
- registration identifier;
- parent registration where required;
- effective dates;
- lifecycle status;
- immutable history / version lineage;
- approval and evidence references.

Controls must prevent duplicate conflicting active registrations for the same
tenant, type, owner, jurisdiction and effective period according to the
approved uniqueness model.

An active registration number must never be silently overwritten.

### P5-E01-010 — Registration approval, renewal and expiry controls

Support controlled lifecycle transitions for:

- creation/draft;
- verification;
- approval/activation;
- suspension;
- expiry;
- renewal/supersession;
- authority rejection.

Mandatory controls:

- maker cannot be final approver for the same high-risk registration change;
- rejection retains submitted evidence/payload reference and rejection reason;
- renewal creates controlled successor history rather than rewriting the prior
  approved record;
- impending expiry can produce readiness/operational exceptions;
- expired/suspended registration cannot silently satisfy readiness.

## 3. Cross-cutting requirements

The six candidates form one coherent vertical slice and should align with the
existing foundation patterns already proven by P5-A1:

- UUID stable identities where identity/version separation is used;
- effective ranges use repository conventions;
- tenant-safe FKs and FORCE RLS for tenant-owned data;
- non-owner runtime role;
- maker-checker for high-risk activation;
- optimistic concurrency where public writes can race;
- idempotency for retryable writes;
- audit and outbox evidence in the same successful transaction;
- RFC 9457-compatible problem mapping where REST contracts are added;
- OpenAPI, permissions and UI must agree;
- append/history semantics rather than destructive rewrite;
- exact negative-path, cross-tenant and concurrency tests.

## 4. Explicit exclusions

P5-JRF-01 must not silently expand into:

- employer bank accounts;
- authorised signatories/delegated authority;
- full configuration snapshots;
- complete foundation-readiness dashboard;
- country-specific PF/EPS/EDLI/ESI/PT/LWF/NPS/TDS rates or formulas;
- statutory filing/return generation;
- statutory remittance/payment;
- employee statutory profiles;
- payroll calculation changes;
- employee payroll-assignment changes;
- minimum-wage rate tables/calculation;
- retro payroll;
- production migration/deployment.

Those remain mapped to later original packages/epics unless separately approved.

## 5. Required design questions before activation

The implementation thread must answer these before V034 is reserved:

1. Does the existing `payroll_org` organisation identity/version pattern extend
   cleanly to work locations, or should location have a smaller bounded model?
2. What exact jurisdiction hierarchy is represented now: country/state/local,
   and how is future district/zone/municipal granularity extended without schema
   churn?
3. What is the deterministic precedence for assigned work location,
   establishment-derived jurisdiction and approved override?
4. Is jurisdiction resolution persisted as an immutable decision/evidence
   record, recomputed on demand, or both?
5. What registration uniqueness dimensions are enforceable in PostgreSQL?
6. Which registration lifecycle states require maker-checker?
7. How are parent/sub-code registration relationships represented?
8. Which events are needed now versus deferred to avoid speculative contracts?
9. What exact readiness API/UI belongs in this package rather than Original
   P5-A3?
10. What existing schemas/classes/tests can be extended without violating module
    boundaries?

## 6. Acceptance boundary

P5-JRF-01 is implementation-complete only when all six candidates have
end-to-end evidence, not merely DDL.

Required evidence categories:

- migration/persistence constraints if schema changes;
- backend contract/service behavior;
- OpenAPI where public API changes;
- Keycloak/permission alignment where permissions change;
- frontend configuration/readiness flows where approved;
- tenant isolation / FORCE RLS;
- lifecycle maker-checker;
- effective-date and overlap behavior;
- idempotency and concurrency;
- audit/outbox lineage;
- focused integration tests;
- required full backend/frontend/OpenAPI regression;
- final critical review;
- reconciled detailed-story ledger after merge.

## 7. Activation and local implementation closure

The project owner explicitly activated `P5-JRF-01` from
`ff581cafce3be5495d93932abfae3931b139358f` on branch
`feature/p5-jrf-01-jurisdiction-registration-foundations`, reserved V034 and
approved the reviewed maximum file/module boundary and explicit exclusions.

The local implementation resolves the ten pre-activation design questions:

- work location uses a dedicated identity/version model;
- jurisdiction hierarchy is extensible through level code/rank and exact parent version;
- generic precedence is approved override -> approved work location ->
  establishment fallback -> unresolved, with material disagreement surfaced as conflict;
- resolution supports preview plus immutable persisted evidence;
- PostgreSQL enforces tenant-safe registration uniqueness and effective overlap rules;
- maker/verifier/final-approver segregation protects activation and suspension;
- parent registrations reference exact versions and require same/ancestor jurisdiction;
- only the approved bounded event set is published;
- readiness remains bounded to jurisdiction/registration foundation concerns;
- organisation, statutory-deductions and payroll-boot remain the approved module boundary.

AC-G03-B1 v1.3 and AC-G03-B2 v1.2 are locally GREEN. G03-C is the
pre-publication full-regression, documentation and critical-review closure.
V034 remains uncommitted and reserved until publication and authority closure.

Country-specific legal rule packs, filing, remittance, bank/signatory scope,
complete readiness, payroll calculation changes, employee assignment changes,
minimum wage, retro and production deployment remain excluded.
