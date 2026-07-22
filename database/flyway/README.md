# Payroll vertical-slice Flyway package

`sql/` is the canonical source for the ordered V001-V020 migrations. The Maven `backend/database-migrations` module packages these files at `db/migration` and exposes the Flyway Maven plugin; do not create a second migration copy in the module.

Apply `bootstrap/001_admin_bootstrap.sql` once as a database administrator, then run Flyway as `payroll_migrator`. Application traffic uses `payroll_app`, which is non-owner and has no `BYPASSRLS` privilege.

```powershell
$local = ConvertFrom-StringData (Get-Content -Raw deploy/local/.env)
$env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$($local.POSTGRES_PORT)/payroll"
$env:FLYWAY_USER = 'payroll_migrator'
$env:FLYWAY_PASSWORD = $local.PAYROLL_MIGRATOR_PASSWORD
.\mvnw.cmd -pl backend/database-migrations flyway:migrate
```

Migration order:

1. schemas and primitives
2. tenant/security/audit
3. organisation
4. calendars and pay groups
5. compensation
6. employee payroll
7. payroll operations
8. calculation results and trace
9. draft payslip snapshots
10. outbox/inbox reliability
11. forced RLS
12. immutability and grants
13. vertical-slice composite consistency, sealed-snapshot/draft-payslip immutability and DDL hardening
14. event reliability and write-idempotency gate
15. legal-entity, payroll-statutory-unit and establishment identity/version model
16. tenant-safe hierarchy range checks and controlled approval/end-date commands
17. pay-group identity/version history, dependency checks and controlled lifecycle commands
18. controlled monthly payroll calendars, deterministic periods and cycle-calendar lineage
19. pay-component version lifecycle, formula invariants and controlled approval/end-date commands
20. salary-structure identity/version history, immutable lines and assignment lineage

All effective ranges are half-open: `[effective_from, effective_to)`. The application sets `app.tenant_id` with `SET LOCAL` at every transaction boundary. The seed is synthetic and development-only; it is deliberately not included in the automatic Flyway location. Local and CI migration tests use PostgreSQL 17 under ADR-003.
