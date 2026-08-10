# P5-FSR-01 R3 Activation Reconciliation

**Date:** 10 August 2026
**Classification:** Governance activation evidence; no product implementation
**Activation base:** `df491d1d62fade2b2e68a395bf5ea9b6a72f6590`
**Web baseline:** `5c45ab41ee3cb4466fac822c04c771f5de0ba119`
**Selected capability:** P5-FSR-01 — Foundation Snapshot & Readiness Closure

## Repository preflight

Owner-returned local evidence proved both repositories were on `main`, local
HEAD equaled `origin/main`, and no staged/unstaged paths were reported:

- backend/program: `df491d1d62fade2b2e68a395bf5ea9b6a72f6590`;
- web: `5c45ab41ee3cb4466fac822c04c771f5de0ba119`.

Live read-only GitHub reconciliation confirmed P5-FBA-01 backend PR #44, web PR
#12 and backend status-closure PR #45 were merged and no product capability was
active at that closure.

## Documentation conflict repaired by this activation

At the activation base, canonical program status, handoff, thread registry and
Flyway README correctly recorded P5-FBA-01 closure, V035 immutable and V036
unreserved. `AGENTS.md` and the master design still described the pre-FBA
V034/V035 state, and the decision register lacked durable FBA/FSR authority.

This activation reconciles those authorities before product implementation.
No committed migration or product source file is modified.

## R3 selection

The canonical story ledger leaves PLN-E01-010 immutable configuration snapshots
and PLN-E01-012 complete/composed foundation readiness partially implemented.
P5-FBA-01 explicitly excluded those outcomes. P5-FSR-01 is therefore selected
before broader P5-A4 expansion, country statutory rules, payments, retro,
settlement or accounting.

PLN-E01-011 remains partial and is only cross-cutting. The capability must not
claim full reusable approval-delegation closure unless separate implementation
evidence proves that acceptance.

## Activation decisions

- P5-FSR-01 becomes active only when this governance authority merges.
- V036 is then reserved exclusively for P5-FSR-01.
- Product implementation branch is created only from activation-merged `main`.
- Activation changes no canonical story status.
- V001-V035 remain byte-for-byte immutable.
- Product implementation must remain within the maximum boundary in the scope authority and every runner must narrow to an exact allow-list.

## Pre-mortem

The required pre-mortem covers five known failure classes before the first
implementation runner: Windows execution semantics, fixture lifecycle, Maven
and runtime fidelity, cross-repository dependency binding, and hosted-CI
ordering. Detailed controls are committed in the P5-FSR-01 scope authority.

The P5-FBA-01 lessons are retained: prefer standard Git/complete payloads,
validate exact source provenance, separate stdout/stderr, use true child-process
exit codes, treat immutable-fixture replay as a lifecycle problem rather than a
blind rerun, merge backend before authoritative web CI when web tests backend
`main`, and classify check-registration lag/Maven Central 429 as infrastructure
or external dependency conditions rather than product regressions.
