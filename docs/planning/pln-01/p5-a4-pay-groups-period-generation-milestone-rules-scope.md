# P5-A4 — Pay Groups, Period Generation & Milestone Rules

## Authority

- Status: **CLOSED after product merge and post-merge semantic reconciliation**
- Package: **Original P5-A4 — Pay Groups, Period Generation & Milestone Rules**
- Canonical activation base: `850e934b5ab839b349d0130e021f03276f9c90c6`
- Business-story boundary: `PLN-E02-001` through `PLN-E02-010` only.
- Activation branch: `docs/p5-a4-activation-authority`
- Product branch after activation merge: `feature/p5-a4-pay-groups-period-generation-milestone-rules`
- Migration authority: `V038` exclusively for P5-A4 if additive schema change is required.
- `V001` through `V037` are committed and immutable.
- Active product write ownership transfers to P5-A4 only after this activation PR is merged.

## R3 current-main reconciliation

The E02 ledger on the activation base records:

| Story | Capability | Current implementation state |
|---|---|---|
| PLN-E02-001 | Configure versioned pay groups | PARTIALLY IMPLEMENTED |
| PLN-E02-002 | Define pay-group population routing rules | PARTIALLY IMPLEMENTED |
| PLN-E02-003 | Configure payroll calendar identities and versions | PARTIALLY IMPLEMENTED |
| PLN-E02-004 | Generate contiguous pay periods | PARTIALLY IMPLEMENTED |
| PLN-E02-005 | Support multiple payroll frequencies | NOT EVIDENCED |
| PLN-E02-006 | Generate cut-offs, approval dates and payment dates | NOT EVIDENCED |
| PLN-E02-007 | Apply holiday and weekend adjustment policies | NOT EVIDENCED |
| PLN-E02-008 | Publish, amend and retire payroll calendars | PARTIALLY IMPLEMENTED |
| PLN-E02-009 | Validate pay-group and calendar compatibility | PARTIALLY IMPLEMENTED |
| PLN-E02-010 | Provide calendar and pay-group operational views | NOT EVIDENCED |

Existing implementation is authoritative and must be extended rather than replaced. In particular, `V004__calendar_pay_group.sql` already provides tenant-owned payroll calendar, pay-period and pay-group foundations, and the existing pay-group runbook documents the later Sprint 2 foundation as monthly/INR/calendar-day-proration with period generation and richer calendar selection deliberately deferred.

## Product objective

Close the remaining E02 Calendar & Pay Groups capability as one bounded package:

1. preserve stable, tenant-scoped and effective-dated pay-group identities/versions;
2. complete deterministic employee/population routing with governed overrides and PSU compatibility;
3. provide stable payroll-calendar identity with effective-dated versions;
4. generate contiguous, non-overlapping, correctly named/numbered periods;
5. support the required frequency set from the E02 ledger, including authorised custom frequency behavior;
6. generate input, calculation, approval, release and payment milestones;
7. apply explicit weekend/holiday movement policy while preserving original and adjusted dates/evidence;
8. publish/amend/retire calendars without rewriting published historical periods;
9. block incompatible pay-group/calendar combinations before assignment or cycle initiation; and
10. expose the operational read model/API evidence required by PLN-E02-010.

## Non-negotiable invariants

- Multi-tenancy, tenant-safe FKs, RLS/FORCE RLS and audit requirements remain mandatory.
- Effective-dated ranges are explicit and overlap-safe.
- Published historical periods are immutable; amendments are versioned/effective-dated.
- Period generation is deterministic and idempotent for the same approved calendar version/range.
- Regular periods are contiguous and non-overlapping.
- Frequency, timezone, currency, PSU and period-coverage compatibility fail closed.
- Holiday/weekend adjustments preserve both the calculated date and the applied movement rule.
- Generated milestone dates remain distinguishable by business meaning; final-settlement timing is not silently conflated with regular payroll payment dates.
- Existing pay-group salary-component eligibility separation remains intact; salary structures/eligibility own component eligibility.
- Existing Foundation Approval & Delegation authority is reused where approval authority is required; application approval is not replaced by endpoint permission alone.

## Explicit exclusions

This package does **not** widen into:

- E03 Component Catalogue or downstream P5-A5 work;
- salary-structure/component eligibility ownership already closed under P5-A2/P5-A3;
- payroll calculation, result, payment, accounting, statutory-return or year-end behavior;
- redesign of organisation, jurisdiction, banking or approval foundations already closed;
- rewriting committed migrations V001–V037.

Original P5-A5 remains dependency-blocked until P5-A4 closes.

## Delivery gates

### G00 — Activation / boundary

This activation commit is governance-only. It creates no product behavior and no SQL migration. Product write begins only after activation merge.

### G01 — Existing-foundation completion

Reconcile and complete pay-group, routing, calendar identity/version and compatibility behavior without regressing existing versioned pay-group semantics.

### G02 — Period/frequency/milestone engine

Implement deterministic period generation, frequency handling, milestone generation and holiday/weekend adjustment under V038 only where schema additions are needed.

### G03 — API / operational evidence

Close publication lifecycle, compatibility blocking and operational read/API evidence, plus UI only where the current product contract and story acceptance criteria require it.

### R3 — Independent review

Independently prove story-by-story E02-001..010 coverage, migration immutability, tenant/RLS boundaries, lifecycle immutability, deterministic generation, compatibility failures, approval separation, architecture, targeted/full Maven verification, API/browser evidence where applicable, and no E03+ widening.

### Closure

Only after product merge and post-merge reconciliation may E02 story statuses be updated from evidence and P5-A4 be marked CLOSED. V038 becomes immutable at product merge if used; if unused, its reservation is explicitly released during closure.

<!-- P5-A4-POST-MERGE-CLOSURE -->
## Post-merge closure

- activation PR: #57; canonical activated main: `25531485e4d29287905765825b48728566455b81`;
- G01: `f038bdfb48162706b6ad1dd46358cc8a2a5c0c2a`;
- G02: `840c1060b3da27fda05d722372978ac2c925ca3b`;
- G03: `155563bd2ebfd6da27299b9b60f3a25691f398b8`;
- independent R3 hardening: `80441eb433afc15e89abbb940ab9f4a9c1eb2f26`;
- product PR: #58;
- product merge: `6ce57213c8d77e76d8addee55a92f0349229a314`;
- hosted product run: `31634393939` — 7/7 required checks GREEN;
- `PLN-E02-001` through `PLN-E02-010` reconcile to IMPLEMENTED;
- V038 is committed and immutable;
- V039 is unreserved;
- P5-A4 retains no active product path ownership after this closure merges;
- Original P5-A5/E03 is not activated by this closure.
