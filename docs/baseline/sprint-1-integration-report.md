# Sprint 1 integration report

Date: 2026-07-19  
Branch: `feature/sprint-1-organisation-foundation`  
Baseline: `bba8a51d17147443e400f51bc9ccf769b8bd1af8`

## Outcome and scope

Sprint 1 tenant, security, audit, event-reliability and organisation foundations are implemented. V001-V013 and the pre-existing payroll business endpoints were not changed. No Sprint 2 or Sprint 3 feature was added: pay groups, calendars, compensation, employee payroll setup, payroll calculation, statutory payroll, retro/off-cycle, final settlement, banking and accounting remain outside this delivery.

The work maps to the backlog as follows:

- S1-00: the transactional outbox/inbox gate passed before organisation event persistence was introduced.
- S1-01: every application repository call runs inside a transaction that sets the validated tenant with `SET LOCAL`; PostgreSQL RLS remains the final isolation boundary.
- S1-02: exact permission authorities are enforced and the development realm emits the tenant and permission claims required by the UI and API.
- S1-03/S1-04: legal entity, payroll statutory unit and establishment use stable identities and immutable effective-dated versions, with an exact-version hierarchy.
- S1-05: organisation writes append audit evidence with authenticated actor, correlation ID, before/after metadata and injected-clock timestamps.
- S1-06: Spring Modulith and ArchUnit gates reject cycles, forbidden internal imports and repository ownership violations.

## Migration changes

- `V014__event_reliability_gate.sql` hardens outbox dispatch metadata, inbox retry metadata, tenant-safe dead letters and the tenant-owned idempotency store.
- `V015__organisation_identity_versions.sql` converts the three legacy organisation tables into version tables without changing V001-V013, creates stable identity tables, backfills exact relationships, adds half-open effective ranges, approved-range exclusion constraints, RLS/FORCE/policies and explicit grants.
- `V016__organisation_lifecycle_commands.sql` enforces parent effective-range containment and exposes narrowly granted approval/end-date commands. Direct runtime UPDATE/DELETE on organisation history remains revoked.

All 16 migrations applied to an empty PostgreSQL 17.10 database and `flyway:validate` passed. The repository verification script reported 34 tenant-owned tables with ENABLE RLS, FORCE RLS and `tenant_isolation`, plus 58 tenant-aware FK definitions. `payroll_app`, `payroll_migrator` and `payroll_owner` are non-superuser, non-createdb, non-createrole and non-bypassrls.

## API and application changes

The OpenAPI contract and controller support create identity/first draft, list/current/as-of retrieval, history, add version, correct eligible future draft, approve, end-date with `If-Match`, audit history and effective-dated hierarchy for all three organisation kinds. Every write requires `Idempotency-Key`; all calls accept/return `X-Correlation-ID`; reusable RFC 9457 responses cover 401, 403, 404, 409, 422, 429, 500 and 503.

Permissions are exactly `organisation.read`, `organisation.create`, `organisation.version.create`, `organisation.version.correct`, `organisation.version.end-date`, `organisation.approve` and `audit.read`. Repository infrastructure remains owned by the organisation module. Aggregate, audit, idempotency result and outbox event commit in one tenant-scoped transaction.

The React organisation setup provides effective-date hierarchy navigation, all three create flows, version history, add/correct/end-date/approve actions, permission-sensitive controls, and loading, empty, unauthorised and problem-detail error states. Access tokens remain memory-only in `window.payrollSession`.

## Event-reliability evidence

`OutboxInboxReliabilityIT` proves aggregate/outbox atomic rollback and commit, stable event/envelope identity, tenant/correlation/causation propagation, consumer retry after rollback, tenant-event-consumer inbox deduplication, and one consumer effect after publish-before-ack redelivery. Retry, poison-message, dead-letter and authorized replay policy is in `docs/runbooks/event-reliability.md`.

## Verification results

| Gate | Result |
| --- | --- |
| Complete Maven reactor | PASS; 29 tests, 0 failures, 0 errors, 0 skipped |
| Security token/filter tests | PASS; 8 tests |
| Flyway/RLS/reliability Testcontainers tests | PASS; 8 tests on PostgreSQL 17 |
| Organisation API Testcontainers lifecycle | PASS; 2 tests including cross-tenant isolation, idempotency and stale `If-Match` 409 |
| Architecture rules | PASS; 3 tests |
| React | PASS; 7 tests in 2 files |
| React production build | PASS; TypeScript and Vite |
| OpenAPI | PASS; Redocly recommended rules, zero errors/warnings |
| Fresh local V001-V016 migrate/validate | PASS on PostgreSQL 17.10 |
| SQL RLS/grant/FK verification | PASS; 34 tenant tables, 58 tenant-safe FK definitions |
| Real Keycloak token smoke | PASS; two secured endpoints returned 200 |
| Gitleaks 8.28.0 | PASS; no leaks |
| npm audit, all and production-only | PASS; 0 vulnerabilities |
| OWASP Dependency-Check 12.1.8 | PASS at CVSS 7 threshold |
| CycloneDX SBOM | PASS; Maven JSON/XML and frontend JSON generated |

The synthetic JWT claim summary was: issuer `http://127.0.0.1:8081/realms/payroll`; audience `payroll-api`; tenant `00000000-0000-0000-0000-000000000001`; role `PAYROLL_OPERATOR`; permissions `payroll.read` and `organisation.read`. The token was never printed or persisted. The same generated correlation ID was returned by `/internal/baseline/auth-smoke` and `/api/v1/organisation-hierarchy`.

Dependency-Check updated NVD/KEV data and passed. The local run had no NVD API key and reported that authenticated OSS Index analysis was unavailable; CI should supply `NVD_API_KEY` for reliable NVD throughput. CycloneDX reported only schema-validator informational warnings for metadata keywords. The frontend SBOM tool emitted deprecation notices for its own transient CLI dependencies, not the application dependency audit.

## Files changed

Sprint 1 changes:

```text
AGENTS.md
README.md
backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/OutboxInboxReliabilityIT.java
backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java
backend/integrations/pom.xml
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/CanonicalJsonHasher.java
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/IdempotencyStore.java
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/OutboxWriter.java
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/internal/infrastructure/JdbcIdempotencyStore.java
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/internal/infrastructure/JdbcOutboxWriter.java
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/package-info.java
backend/integrations/src/test/java/com/acme/hrms/payroll/integrations/CanonicalJsonHasherTest.java
backend/organisation/pom.xml
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationAggregate.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationController.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationHierarchy.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationKind.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationView.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/OrganisationWriteRequest.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/internal/application/OrganisationService.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/internal/infrastructure/OrganisationRepository.java
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/package-info.java
backend/organisation/src/test/java/com/acme/hrms/payroll/organisation/OrganisationContractTest.java
backend/payroll-boot/pom.xml
backend/payroll-boot/src/test/java/com/acme/hrms/payroll/ArchitectureRulesTest.java
backend/payroll-boot/src/test/java/com/acme/hrms/payroll/OrganisationApiIT.java
backend/platform-core/pom.xml
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/AuditReader.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/AuditWriter.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/AuthenticatedActor.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/ConflictException.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/DomainEvent.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/DomainEventFactory.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/ProblemDetailsAdvice.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/ResourceNotFoundException.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/TenantTransactionExecutor.java
backend/platform-core/src/test/java/com/acme/hrms/payroll/platform/TenantTransactionExecutorTest.java
backend/security/src/test/java/com/acme/hrms/payroll/security/SecurityBaselineTest.java
contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
database/flyway/README.md
database/flyway/sql/V014__event_reliability_gate.sql
database/flyway/sql/V015__organisation_identity_versions.sql
database/flyway/sql/V016__organisation_lifecycle_commands.sql
database/flyway/verification/verify_vertical_slice.sql
deploy/local/keycloak/payroll-realm.json
deploy/local/smoke/auth-smoke.ps1
docs/baseline/sprint-1-integration-report.md
docs/runbooks/event-reliability.md
frontend/payroll-web/src/App.tsx
frontend/payroll-web/src/features/organisation/SetupPage.test.tsx
frontend/payroll-web/src/features/organisation/SetupPage.tsx
frontend/payroll-web/src/features/organisation/organisation-api.ts
frontend/payroll-web/src/styles.css
```

`docs/baseline/sprint-0-integration-report.md` also remains modified by the user's pre-existing baseline commit-ID annotation. It is preserved but is not a Sprint 1 change and must not be included in Sprint 1 commits without explicit review.

Generated `target/`, `dist/`, `node_modules/`, SBOM outputs and `deploy/local/.env` are ignored and must not be staged.

## Command ledger

Read-only inspection used `rg --files`, `rg -n`, `Get-ChildItem`, `Get-Content -Raw`, `git status --short`, `git diff`, `git log`, `git branch`, `git remote -v`, DOCX ZIP/XML inspection and the document rendering helper. The document rendering helper could not render pages because LibreOffice is absent; full document text and structure were read successfully.

The following command forms were executed; repeated verification commands are shown with their repetition purpose:

```text
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B -pl backend/database-migrations -am verify
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B -pl backend/payroll-boot -am -Dtest=ArchitectureRulesTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrganisationApiIT -Dfailsafe.failIfNoSpecifiedTests=false verify (focused, repeated after lifecycle/concurrency additions)
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B verify (complete reactor, repeated after fixes and as final gate)
docker run ... node:24.14.0-bookworm-slim npm test (initial and final)
docker run ... node:24.14.0-bookworm-slim npm run build (initial and final)
pnpm dlx @redocly/cli@2.39.0 lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml (failed: multiple binaries)
pnpm --package=@redocly/cli@2.39.0 dlx redocly lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
git check-ignore -v deploy/local/.env
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down -v
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker inspect --format '{{.State.Health.Status}}' local-postgres-1 (polled to healthy)
docker run --network local_default ... mvn -B -pl backend/database-migrations flyway:migrate flyway:validate
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql | docker compose ... exec -T postgres psql -U postgres -d payroll
Invoke-WebRequest http://127.0.0.1:8081/realms/payroll/.well-known/openid-configuration (readiness loop)
docker run -d --name payroll-auth-smoke ... mvn -B -f backend/payroll-boot/pom.xml spring-boot:run (failed before runtime JDBC fix; then started with stale installed module)
Invoke-WebRequest http://127.0.0.1:8080/actuator/health (readiness loop)
./deploy/local/smoke/auth-smoke.ps1 (first failed on stale module 404; final PASS)
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B -DskipTests install
docker rm -f payroll-auth-smoke
docker run -d --name payroll-auth-smoke ... mvn -B -f backend/payroll-boot/pom.xml spring-boot:run (current reactor artifacts)
docker run ... zricethezav/gitleaks:v8.28.0 detect --source=/repo --no-git --redact --verbose (first false-positive fixture; final no leaks)
docker run ... node:24.14.0-bookworm-slim npm audit --audit-level=high
docker run ... node:24.14.0-bookworm-slim npm audit --omit=dev --audit-level=high
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B org.owasp:dependency-check-maven:12.1.8:check -DfailBuildOnCVSS=7 -Dformats=JSON,HTML
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
docker run ... node:24.14.0-bookworm-slim npx --yes @cyclonedx/cyclonedx-npm --output-file bom-frontend.json
rg -n 'Instant\.now\(' backend --glob '!**/target/**'
rg -n '"system"' backend --glob '!**/target/**'
git status --short --ignored
git diff --check
```

Secrets were supplied from the ignored development `.env` or environment variables and are intentionally omitted from this report. No raw JWT was emitted by any command.

## Remaining risks

1. Event dispatch is deliberately a reliability foundation, not a production broker deployment. Broker-specific ordering, retry scheduling, alerting and authorized replay operations must be implemented against the runbook before external publication.
2. The organisation UI is a functional baseline; production identity-provider login/session plumbing is outside this sprint. It consumes only an in-memory access token supplied by the host shell.
3. Database approval and end-date commands are intentionally narrow. Any future lifecycle operation requires a new reviewed command rather than restoring direct table mutation grants.
4. Mockito emits a future-JDK dynamic-agent warning on Java 21. The suite passes, but an explicit agent should be configured before adopting a JDK that disables dynamic attachment.
5. Local Dependency-Check lacked authenticated OSS Index access. The required CI secret is `NVD_API_KEY`; OSS Index credentials are optional unless that analyzer becomes a required policy gate.
6. Remote CI status is pending until the reviewed Sprint 1 commits are pushed. Nothing is merged.

Proposed commit sequence:

1. `feat(integrations): establish event reliability gate`
2. `feat(organisation): add tenant-safe effective-dated foundation`
3. `feat(web): add organisation setup lifecycle`
4. `docs(payroll): record Sprint 1 verification evidence`
