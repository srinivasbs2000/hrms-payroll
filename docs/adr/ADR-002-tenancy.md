# ADR-002: Shared database with forced RLS

Status: Accepted. Every tenant-owned table contains `tenant_id`; composite foreign keys prevent cross-tenant references; `FORCE ROW LEVEL SECURITY` protects all runtime access; the application role is non-owner and `NOBYPASSRLS`.
