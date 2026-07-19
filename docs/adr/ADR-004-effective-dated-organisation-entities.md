# ADR-004: Identity-plus-version effective-dated organisation entities

Status: Accepted for Sprint 1 design (19 July 2026).

## Context

Legal entities, payroll statutory units and establishments affect historical payroll lineage. Updating a row in place would make a past snapshot appear to have been calculated under current organisation attributes. A single identifier that is reused for both the enduring organisation concept and each historical state also makes references ambiguous: a payroll record must identify the exact state that was effective, while users and integrations need a stable identity for the organisation concept.

## Decision

The organisation model is **identity plus version**, not in-place mutation.

- Each tenant-owned legal entity has one stable legal-entity identity and one or more immutable legal-entity versions.
- Each tenant-owned payroll statutory unit has one stable statutory-unit identity and one or more immutable statutory-unit versions. Its versioned relationship to the applicable legal entity is tenant-safe and effective-dated.
- Each tenant-owned establishment has one stable establishment identity and one or more immutable establishment versions. Its versioned relationship to the applicable payroll statutory unit is tenant-safe and effective-dated.

Version rows use half-open ranges `[effective_from, effective_to)`. A correction or approved change creates a successor version and closes the prior range; it never rewrites a version referenced by a sealed snapshot or payroll result. Commands and APIs use the stable identity when addressing the enduring organisation concept, while snapshot lineage and other historical references store the exact version identifier used for calculation.

The V003 baseline tables predate the command implementation and do not authorise in-place updates. Before Sprint 1 organisation writes are enabled, the physical model must distinguish stable identity keys from version keys and add tenant-safe foreign keys and non-overlap enforcement appropriate to the three hierarchies. Application validation will check effective ranges, and database exclusion constraints will protect concurrent writes.

## Consequences

- API commands must distinguish correction/version creation from mutable draft editing.
- Snapshot lineage records the exact legal-entity, payroll-statutory-unit and establishment version identifiers used.
- Repository queries always include tenant and as-of date, with deterministic tie handling.
- Sprint 1 must not add in-place updates to organisation rows that are already referenced by sealed payroll inputs.
- The stable identity answers “which organisation concept”; the version identifier answers “which approved state at the payroll as-of date”.
