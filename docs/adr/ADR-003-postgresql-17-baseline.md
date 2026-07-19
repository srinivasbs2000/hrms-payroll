# ADR-003: Standardise the baseline on PostgreSQL 17

Status: Accepted (19 July 2026).

## Context

The Sprint 0 local stack used PostgreSQL 18 with Flyway 11.7.2. Flyway reported that PostgreSQL 18 was newer than its tested support range. Sprint 0A requires local and CI migration behavior to be explicit and warning-free.

## Decision

Local Compose, Testcontainers and CI use PostgreSQL 17. Flyway remains at the Spring Boot 3.5.16 managed baseline (11.7.2). Production deployment must use the same PostgreSQL major until a reviewed dependency upgrade explicitly certifies PostgreSQL 18.

## Consequences

- A new local PostgreSQL volume is required when moving from the prior PostgreSQL 18 container.
- Migration SQL remains forward-only and V001-V012 remain byte-for-byte unchanged.
- PostgreSQL 18 adoption requires a new ADR, a supported Flyway upgrade, fresh-install validation, upgrade rehearsal and RLS regression evidence.
