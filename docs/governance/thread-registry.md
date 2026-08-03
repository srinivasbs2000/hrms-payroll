# HRMS Payroll Thread Registry

**Last verified:** 2 August 2026
**Repository baseline:** `main` at `d2df2e7a9cc597ea6e4a15de4ed9d1d040de8462`
**Product implementation baseline:** Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64`

Only one thread or write-capable process may own overlapping files or the next
migration number. A thread not explicitly registered as active has no write
ownership.

## Thread ledger

| Thread | Role/status | Scope | Branch/PR | Write ownership | Migration | Next action |
|---|---|---|---|---|---|---|
| Thread 1 | DESIGN/PLANNING — inactive | Full-product authority and PLN-01 source | Historical PR #22 | None | None | Reference only |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | Historical PR #3/#18 | None | Historical | Reference only |
| Thread 3 | CLOSED | Sprint 3 completion/E2E | PR #18 merged | None | Historical | Reference only |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | PR #19 merged | None | Historical | Reference only |
| Thread 5 | CLOSED | Recovery/handoff/process audit | No active PR | None | None | Reference only |
| Thread 6 | IMPLEMENTATION OWNER — active | P5-A1 Foundation hierarchy closure only | Local `feature/p5-a1-foundation-hierarchy-closure`; PR none | Exact allow-list below | V031 reserved | Run v1.1 parser gate, then apply, verify and critically review uncommitted diff |

## Thread 6 exact allow-list

### New files

1. `database/flyway/sql/V031__organisation_hierarchy_closure.sql`
2. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/OrganisationHierarchyClosureMigrationIT.java`
3. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationRetirementRequest.java`
4. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationProblemException.java`
5. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationProblemAdvice.java`
6. `docs/governance/hrms-payroll-execution-norm.md`
7. `docs/history/thread-6-p5-a1-implementation-record.md`
8. `docs/runbooks/organisation-hierarchy-closure.md`

### Modified files

9. `AGENTS.md`
10. `docs/design/decision-register.md`
11. `docs/runbooks/project-continuation-handoff.md`
12. `docs/governance/thread-registry.md`
13. `database/flyway/README.md`
14. `database/flyway/verification/verify_vertical_slice.sql`
15. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationWriteRequest.java`
16. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationView.java`
17. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationController.java`
18. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/internal/application/OrganisationService.java`
19. `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/internal/infrastructure/OrganisationRepository.java`
20. `backend/organisation/src/test/java/com/acme/hrms/payroll/organisation/OrganisationContractTest.java`
21. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/OrganisationApiIT.java`
22. `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
23. `deploy/local/keycloak/payroll-realm.json`
24. `frontend/payroll-web/src/features/organisation/organisation-api.ts`
25. `frontend/payroll-web/src/features/organisation/SetupPage.tsx`
26. `frontend/payroll-web/src/features/organisation/SetupPage.test.tsx`

No other repository path is authorized.

## Acceptance controls

- V001-V030 remain byte-for-byte unchanged.
- V031 is forward-only and preserves existing identity/version UUIDs.
- Existing Sprint 0-4 behavior remains green.
- Thread 6 remains the sole owner of V031 and listed paths.
- The Git index remains empty and HEAD remains the approved base.
- No commit, push, PR, merge or branch deletion occurs without separate approval.
- The non-Codex standing execution norm applies to every future thread.
- Every PowerShell apply/rollback script passes the real parser before execution.

## Deferred work

S4-06A remains paused and P5-A2 is not authorized. Neither may be folded into the
P5-A1 diff as recovery convenience.
