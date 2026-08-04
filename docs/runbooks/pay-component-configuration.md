# Pay-component catalogue configuration

## Purpose

Configure the stable identity and complete behavioural meaning of a payroll
component without changing the existing calculation-direction contract.

## Lifecycle

1. Create the identity with immutable code, display name, `componentType`,
   ownership scope, optional country ownership, protection, and confidentiality.
2. Create a complete schema-1 draft version containing formula metadata and all
   behavioural classifications.
3. Have a different authenticated principal approve the draft.
4. Add future versions rather than rewriting approved history.
5. Correct only a non-superseded future draft; the replacement points to the
   superseded version.
6. End-date through `If-Match` optimistic concurrency.
7. Retire only when no active/future approved version, salary-structure line, or
   approved named-base membership blocks retirement.

## Legacy rows

Existing component versions remain schema 0 and retain their approval history.
They are readable with `classificationStatus=LEGACY_INCOMPLETE`. A schema-0
legacy draft cannot be newly approved. Correct it by creating a complete
schema-1 draft.

## API permissions

- `compensation.component.read`
- `compensation.component.create`
- `compensation.component.version.create`
- `compensation.component.version.correct`
- `compensation.component.version.end-date`
- `compensation.component.approve`
- `compensation.component.retire`
- `audit.read`

Every mutation requires `Idempotency-Key`; end-date and retirement also require
`If-Match`.
