# P5-JRF-01 Manual Smoke Guide

This guide is for an operator/developer smoke pass after the application,
PostgreSQL and Keycloak synthetic environment are running. It is not a
substitute for automated integration tests.

## Preconditions

- V001-V034 applied and validated;
- application health endpoint is UP;
- synthetic admin token is available;
- tenant context is present;
- the operator has the expected JRF permissions.

## Smoke sequence

1. Create a country/state payroll jurisdiction and approve it with an
   independent approver.
2. Create a work location using the business jurisdiction selector and approve
   it.
3. Preview jurisdiction resolution and confirm the approved work location is
   selected when no override exists.
4. Create and approve an explicit override; preview again and confirm override
   precedence.
5. Create a registration type with `JAVA_REGEX_V1` metadata and approved owner
   kinds.
6. Create a registration, verify it with a second principal and activate it
   with a third principal.
7. Confirm routine registration views show only the masked identifier.
8. With `statutory-registration.identifier.read`, invoke exact reveal and
   confirm the response is non-cacheable.
9. Create a future renewal/successor draft and confirm the current active
   registration remains visible/effective.
10. Evaluate bounded readiness.
11. For a child registration, suspend the exact parent and confirm readiness
    becomes BLOCKED with `PARENT_REGISTRATION_INVALID`.
12. Confirm a cross-tenant principal cannot read/write the created JRF data.

## Negative checks

- maker self-approval must fail;
- maker/verifier/final approver collapsing to one actor must fail;
- invalid `JAVA_REGEX_V1` metadata must produce the configured RFC9457 422
  response;
- unrelated parent jurisdiction must fail;
- direct cross-tenant access must fail;
- exact identifiers must not appear in application logs, audit payloads or
  outbox payloads.

Record any smoke evidence separately from production/customer data. This
project remains greenfield with no evidenced production deployment.
