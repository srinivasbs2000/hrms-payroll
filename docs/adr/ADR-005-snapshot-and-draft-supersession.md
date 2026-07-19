# ADR-005: Sealed snapshots and draft-payslip supersession

Status: Accepted for baseline closure (19 July 2026).

## Context

Payroll source data remains editable while an operator is assembling and correcting inputs. Treating those mutable rows as a calculation snapshot would make reproducibility depend on later edits. Likewise, changing a generated draft payslip in place would erase what was previously reviewed.

## Decision

Mutable source inputs are collected in domain-owned source and staging structures. Staging may be corrected while validation is incomplete. The sealing transaction validates tenant, assignment, payroll cycle, effective dates, required components and canonical payload construction before it inserts exactly one immutable `payroll_ops.input_snapshot`. An `input_snapshot` is never used as a staging record and is never inserted before validation succeeds. Any later source correction is staged and validated again, producing a new sealed snapshot with its own identifier and canonical hash.

Draft payslips are append-only document versions. Regeneration inserts a new `documents.draft_payslip` row; it does not update or delete the prior draft. Supersession is represented by version lineage and deterministic ordering (and, when the document command model is implemented, an explicit append-only supersession reference). The superseded draft remains available for audit and must never be presented as the current draft. Sprint 1 must not weaken the V013 mutation triggers to implement supersession.

## Consequences

- Calculation requests reference only sealed snapshot identifiers.
- Validation failure leaves no partially sealed `input_snapshot`.
- Snapshot payloads and hashes are canonical and immutable.
- A new draft is generated from an immutable payroll result and becomes current by append-only version selection.
- Historical drafts remain immutable evidence; no in-place status, HTML or payload mutation is permitted.
