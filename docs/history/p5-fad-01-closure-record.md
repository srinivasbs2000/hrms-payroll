# P5-FAD-01 Post-Merge Closure Record

**Date:** 12 August 2026
**Capability:** `P5-FAD-01 — Foundation Approval & Delegation`
**Activation PR/merge:** #53 / `b4267168892eb602764d194eb0f303f8d8233323`
**G01 product commit:** `64a34a3b4a58d3de8ccfd185a7da21102ec78b71`
**G02 product commit:** `f581d582d6bfce8239370e2230a612df28e0024a`
**R3 repair/product head:** `2db3845785b8c178c9660f712056f79e5e5409ed`
**Product PR/merge:** #55 / `a80e7b4da121665a8b1548acada6b96fac4dfa01`
**Hosted product CI:** payroll-baseline run `31537285947` GREEN
**Status-closure PR:** the PR containing this record

## Delivered

P5-FAD-01 closes the remaining reusable Foundation maker-checker application
approval/delegation gap tracked by PLN-E01-011:

- tenant-owned, effective-dated approval-authority assignments;
- exact LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT owner scope;
- VERIFIER / FINAL_APPROVER plus Foundation domain/action scope;
- effective-dated bounded delegation referencing source authority;
- no self-delegation or scope widening;
- source suspension/retirement/expiry invalidates delegated authority;
- endpoint permission and shared authority are both required;
- existing domain maker/checker/final lifecycle remains domain-owned;
- authenticated Keycloak service accounts cannot exercise interactive final approval;
- immutable consumed authority/delegation decision evidence;
- legal authorised-signatory status does not grant application approval access.

The implementation uses V037 and the public `security` contract. No UI product
write or E02 calendar/pay-group expansion was required.

## R3 hardening and runner lessons

Independent review discovered and corrected:

1. authenticated service-account detection had to use the runtime JWT
   `client_id`, not only an actor-id prefix;
2. consumed authority/delegation lineage had to be durably appended to immutable
   audit evidence in the same write transaction;
3. focused real-FAD API tests were required to prove permission-only and
   authority-only negative paths;
4. initial organisation approval needed a narrowly bounded pending-owner
   bootstrap to avoid an authority-creation deadlock;
5. PowerShell runner generation must respect PowerShell 7 syntax and avoid
   `$name` immediately followed by `:` interpolation and fixed-column Git-status parsing;
6. security unit fixtures must represent authenticated runtime state; and
7. Windows long-path cleanup must not invalidate already-green product evidence.

## Canonical story reconciliation

- `PLN-E01-011`: PARTIALLY IMPLEMENTED -> IMPLEMENTED.

Post-reconciliation 450-story totals:

- 19 IMPLEMENTED;
- 153 PARTIALLY IMPLEMENTED;
- 88 NOT EVIDENCED;
- 159 NOT STARTED;
- 31 requiring LEGAL/DOMAIN REVALIDATION.

With PLN-E01-011 closed, the bounded Original P5-A3 foundation
bank/signatory/snapshot/readiness/approval-delegation package is complete.

## Scope boundary retained

P5-FAD-01 does not claim E02 calendar/pay-group functionality, country-specific
legal rules/rates, payment execution, employee bank accounts, retro/off-cycle/
final settlement, accounting/ERP posting, migration/cutover, production
operations or a generic BPM engine.

Legal authorised-signatory authority remains separate from application
authorization.

## Authority release

After this status-closure PR merges:

- P5-FAD-01 is CLOSED;
- active product write owner is NONE;
- V037 is committed and immutable;
- V038 is UNRESERVED;
- no next capability is activated by this closure;
- a fresh R3 reconciliation and separate activation authority are mandatory
  before new product writes.

The product remains greenfield with no evidenced production deployment or live
customer payroll migration.
