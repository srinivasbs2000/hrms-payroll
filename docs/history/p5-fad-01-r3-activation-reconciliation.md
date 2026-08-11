# P5-FAD-01 R3 Activation Reconciliation

**Date:** 11 August 2026
**Capability selected:** `P5-FAD-01 — Foundation Approval & Delegation`
**Selection baseline:** backend `940c24d85a11dfaf293fc1d660ede4132fd53acb`
**Primary canonical story:** `PLN-E01-011`
**Migration reservation after activation merge:** V037
**Activation effect on story status:** none

## Why this capability is next

P5-FSR-01 is merged and status-closed. The canonical Foundation epic now has
PLN-E01-010 and PLN-E01-012 implemented. The only remaining Original P5-A3
foundation-governance core is PLN-E01-011.

PLN-E01-011 is already partially implemented because domain workflows enforce
maker/verifier/final-approver separation. The remaining canonical gap is
specific: reusable entity/PSU-scoped application approver authorization plus
effective-dated approval delegation.

Calendar/Pay-Group E02 is not selected yet because it contains approval and
publication workflows of its own. Expanding E02 before the reusable approval
authority exists would encourage another domain-specific authorization model.

## R3 architectural decision

The shared application-approval authority belongs to the `security` module and
is exposed only through a narrow public facade.

Business modules may consume that public contract. The `security` module must
not import organisation, compensation or statutory internal Java packages.

The approval authority is an additional authorization gate. It does not replace
existing domain lifecycle transitions, audit evidence, permissions, maker/checker
segregation or final-approval state machines.

Legal authorised-signatory authority is a separate business/legal concept and
must not grant payroll-system approval access.

## Bounded outcome

The capability may add tenant-safe, effective-dated approval-authority
assignments and delegations scoped to LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT,
with role/domain/action scope and exact actor identity.

Delegation must have explicit start/end dates and may not widen the delegator's
authority, scope, role or effective period.

Foundation high-risk approval paths in currently implemented domains must be
able to require both:
1. their existing endpoint permission/lifecycle rules; and
2. a valid shared approval authority for the relevant owner scope.

## Explicit non-selection

This activation does not select:
- E02 calendar/pay-group functional expansion;
- country-specific statutory rules or legal conclusions;
- payment execution;
- employee bank accounts;
- retro/off-cycle/final settlement;
- accounting;
- migration/cutover;
- production operations.

No story status changes until product evidence merges and post-merge
reconciliation is performed.
