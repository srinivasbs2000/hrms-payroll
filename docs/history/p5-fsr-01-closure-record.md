# P5-FSR-01 Post-Merge Closure Record

**Date:** 11 August 2026
**Capability:** `P5-FSR-01 — Foundation Snapshot & Readiness Closure`
**Activation merge:** `1f7df0ba489c590abe0f15aa895a08e9185ea03d`
**G01 backend product PR/merge:** #47 / `16d2488252b8a5c3aecd64c0f43fe18b6743d6e8`
**G02 facade authority PR/merge:** #48 / `15f6c88cf8def2810d901d7adb273558d1fc77d4`
**G02 backend product PR/merge:** #49 / `954ed05d11dcb367f6de6e1f3e78aafc17c8beab`
**Runtime-date authority PR/merge:** #50 / `04417f142103c3714cb6346602d7c48f2b1cf3ba`
**UTC session implementation PR/merge:** #51 / `74bbd65449adad7b7058d8afd96097b1e08d2a0a`
**Web product PR/merge:** #13 / `8e8b47c829ac33aa2495ef07fba0ae2afd51e770`
**Status-closure PR:** the PR containing this record

## Delivered

P5-FSR-01 closes the bounded snapshot/readiness portion of Original P5-A3:

- one immutable foundation-configuration snapshot per sealed payroll cycle;
- deterministic snapshot payload/hash and exact approved version lineage;
- history-preserving V035 upgrade with V036 persistence;
- exact input/calculation request/result snapshot identity/hash binding;
- drift rejection and no mutable-current fallback after sealing;
- bounded `FOUNDATION_ONLY` readiness composition for snapshot, employer bank,
  signatory authority and caller-declared full-period registration requirements;
- blocker/warning dimensions, findings and explicit exclusions;
- standalone Foundation Readiness React workspace;
- exact-backend cross-repository browser verification.

## R3 hardening discovered during browser closure

Cross-repository browser verification exposed two general runtime/tooling issues
that were corrected without weakening product rules:

1. Windows Git worktrees can convert Docker-mounted shell scripts to CRLF; the
   disposable E2E worktree now validates/normalizes the mounted shell copy to LF.
2. FBA final approval used the existing UTC application `Clock`, while pgJDBC /
   PostgreSQL session date semantics followed the host JVM zone. PR #51 aligns
   application datasource sessions to UTC and includes a cross-midnight
   non-UTC-host regression test.

The temporary browser-local FBA date workaround used during diagnosis was
explicitly removed before web PR #13 and is not part of the product.

## Canonical story reconciliation

- `PLN-E01-010`: IMPLEMENTED.
- `PLN-E01-012`: IMPLEMENTED for the bounded generic `FOUNDATION_ONLY` contract.
- `PLN-E01-011`: remains PARTIALLY IMPLEMENTED; reusable entity/PSU-scoped
  application approver authorization and effective-dated approval delegation
  remain open.

Post-reconciliation 450-story totals:

- 18 IMPLEMENTED;
- 154 PARTIALLY IMPLEMENTED;
- 88 NOT EVIDENCED;
- 159 NOT STARTED;
- 31 requiring LEGAL/DOMAIN REVALIDATION.

## Scope boundary retained

P5-FSR-01 does not claim country-specific legal obligation inference, rules or
rates; employee bank accounts; payment execution; retro/off-cycle/final
settlement; accounting/ERP posting; migration/cutover; or production operations.

Registration requirements in the generic readiness contract are caller-declared.
An empty requirement list is not a legal conclusion.

## Authority release

After this status-closure PR merges:

- P5-FSR-01 is CLOSED;
- active product write owner is NONE;
- V036 is committed and immutable;
- V037 is UNRESERVED;
- no next capability is activated by this closure;
- a fresh R3 reconciliation and separate activation authority are mandatory
  before new product writes.

The product remains greenfield with no evidenced production deployment or live
customer payroll migration.
