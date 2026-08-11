# Payroll vertical-slice Flyway package

`sql/` is the canonical source for the ordered V001-V036 migrations. The Maven
`backend/database-migrations` module packages these files at `db/migration` and
exposes the Flyway Maven plugin; do not create a second migration copy in the
module.

V001-V035 are committed and byte-for-byte immutable. V032 is
`V032__compensation_catalogue_named_bases.sql`, merged through PR #30.

V033 is `V033__salary_structure_ctc_eligibility_simulation.sql`, merged through
PR #32. V034 is `V034__jurisdiction_registration_foundations.sql`, merged
through PR #36 / `6ee101bd398b745a0078bd0517b4e3797c571c2b`. V034 is committed and immutable.
V035 is `V035__foundation_banking_authority.sql`, merged through P5-FBA-01
backend PR #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`. V035 is committed and
immutable. V036 is `V036__foundation_snapshot_readiness_closure.sql`, delivered
through P5-FSR-01 G01 backend PR #47 / `16d2488252b8a5c3aecd64c0f43fe18b6743d6e8`
and retained unchanged through later FSR hardening. V036 is committed and
immutable. V001-V036 are now immutable. V037 is reserved exclusively for
P5-FAD-01 — Foundation Approval & Delegation after this activation-authority
merge. Activation creates no V037 SQL; product implementation must create V037
only from activation-merged `main` and within the committed P5-FAD-01 scope.

V033 implements the P5-A3 configuration-design foundation: schema-1 salary
structures, versioned CTC policies, typed eligibility rules and deterministic
design-time simulation/validation evidence. V034 implements P5-JRF-01:
work-location identity/version, payroll-jurisdiction hierarchy and resolution
evidence, generic statutory-registration type/instance/version lifecycle,
bounded readiness and associated tenant/RLS integrity. V035 implements employer
bank-account and authorised-signatory identity/version, encryption metadata,
controlled lifecycle, delegated-authority scope and bounded banking readiness.
None of V033-V035 changes the V025/V026 official starter calculator.

Apply `bootstrap/001_admin_bootstrap.sql` once as a database administrator, then
run Flyway as `payroll_migrator`. Application traffic uses `payroll_app`, which
is non-owner and has no `BYPASSRLS` privilege.

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
21. employee payroll relationship/assignment identity history, controlled profiles and exact pay-group/salary assignment lineage
22. completed-foundation negative-path hardening for organisation parents, exact pay-group cycle ranges and dependent end-dating
23. controlled regular payroll cycles, immutable population-resolution decisions and exact executable configuration lineage
24. immutable canonical input snapshots, exact population lineage, drift detection and controlled cycle sealing
25. deterministic fixed monthly non-statutory calculation, immutable result/trace evidence and controlled cycle completion
26. controlled recalculation attempts, immutable supersession lineage and atomic active-result replacement
27. jurisdiction-neutral statutory rule identities, effective-dated versions, liability portions and deterministic slabs
28. employee statutory profiles, effective-dated registration/classification and exact rule eligibility/exemption assignments
29. approved statutory assessment-base classifications, immutable statutory snapshots and deterministic FIXED/PERCENTAGE/SLAB evaluation evidence
30. append-only statutory ledger postings, approved balance years, PTD/YTD snapshots, corrections, reconciliation and remittance-ready evidence
31. organisation identity lifecycle, maker-checker approval, responsibility/establishment classifications, safe version allocation and controlled retirement
32. P5-A2 compensation catalogue, named payroll bases, exact component/base memberships, maker-checker and controlled retirement
33. P5-A3 salary-structure design, versioned CTC policy, typed eligibility rules, deterministic design-time simulation and immutable validation evidence
34. P5-JRF-01 work-location identity/version, payroll-jurisdiction hierarchy/resolution evidence, generic statutory-registration lifecycle and bounded readiness
35. P5-FBA-01 employer bank-account/signatory identity/version, encrypted bank secret metadata, delegated-authority scope and bounded banking readiness
36. P5-FSR-01 immutable foundation-configuration snapshot, exact input/calculation binding and history-preserving V035 upgrade

This remains a greenfield product with no evidenced production deployment.
Forward-only migrations and populated-upgrade tests protect reproducible local,
CI and future deployment paths; they do not indicate a current production
migration.

All effective ranges are half-open: `[effective_from, effective_to)`. The
application sets `app.tenant_id` with `SET LOCAL` at every transaction boundary.
The seed is synthetic and development-only; it is deliberately not included in
the automatic Flyway location. Local and CI migration tests use PostgreSQL 17
under ADR-003.
