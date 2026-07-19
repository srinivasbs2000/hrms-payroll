# Sprint 0 integration report

Date: 19 July 2026  
Repository: `C:\dev\hrms-payroll`  
Status: completed, not committed

## Outcome

The supplied Maven, React, OpenAPI, Flyway, Compose, documentation and backlog artifacts are integrated into the requested monorepo layout. PostgreSQL 17 and Keycloak 26.7.0 are running on loopback-only host ports. The administrator bootstrap, V001-V013, database verification, RLS/security/architecture checks, real-token authentication smoke, Maven reactor, React test/build, OpenAPI validation and security scans all pass.

No Sprint 1 payroll capability was added. The business endpoint scope and V001-V012 migrations are unchanged.

## Files added or modified

The implementation DOCX already existed and was preserved byte-for-byte. Every other path below was added or modified during integration.

```text
.editorconfig
.gitignore
.java-version
.mvn/wrapper/maven-wrapper.properties
.nvmrc
AGENTS.md
README.md
mvnw
mvnw.cmd
mvnwDebug
mvnwDebug.cmd
pom.xml
backend/calculation-engine/pom.xml
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/CalculationTraceStep.java
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/CalendarDayProration.java
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/FixedGrossToNetCalculator.java
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculationengine/package-info.java
backend/compensation/pom.xml
backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/package-info.java
backend/database-migrations/pom.xml
backend/documents-reporting/pom.xml
backend/documents-reporting/src/main/java/com/acme/hrms/payroll/documentsreporting/package-info.java
backend/employee-payroll/pom.xml
backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/package-info.java
backend/integrations/pom.xml
backend/integrations/src/main/java/com/acme/hrms/payroll/integrations/package-info.java
backend/organisation/pom.xml
backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/package-info.java
backend/payroll-boot/pom.xml
backend/payroll-boot/src/main/java/com/acme/hrms/payroll/PayrollApplication.java
backend/payroll-boot/src/main/resources/application.yml
backend/payroll-boot/src/test/java/com/acme/hrms/payroll/GoldenPayrollTest.java
backend/payroll-operations/pom.xml
backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/package-info.java
backend/platform-core/pom.xml
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/BaseEntity.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/DomainEvent.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/EffectiveRange.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/Money.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/ProblemDetailsAdvice.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/TenantContext.java
backend/security/pom.xml
backend/security/src/main/java/com/acme/hrms/payroll/security/SecurityConfiguration.java
backend/security/src/main/java/com/acme/hrms/payroll/security/TenantClaimFilter.java
backend/security/src/main/java/com/acme/hrms/payroll/security/package-info.java
backend/test-support/pom.xml
backlog/organisation-to-draft-payslip-sprint-backlog.csv
contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
database/flyway/README.md
database/flyway/bootstrap/001_admin_bootstrap.sql
database/flyway/seed/R__development_seed.sql
database/flyway/sql/V001__schemas_and_primitives.sql
database/flyway/sql/V002__tenant_security_audit.sql
database/flyway/sql/V003__organisation.sql
database/flyway/sql/V004__calendar_pay_group.sql
database/flyway/sql/V005__compensation.sql
database/flyway/sql/V006__employee_payroll.sql
database/flyway/sql/V007__payroll_operations.sql
database/flyway/sql/V008__calculation_results.sql
database/flyway/sql/V009__draft_payslip.sql
database/flyway/sql/V010__integration_reliability.sql
database/flyway/sql/V011__forced_row_level_security.sql
database/flyway/sql/V012__immutability_and_grants.sql
database/flyway/verification/verify_vertical_slice.sql
deploy/local/.env.example
deploy/local/README.md
deploy/local/compose.yaml
deploy/local/keycloak/payroll-realm.json
docs/adr/ADR-001-modular-monolith.md
docs/adr/ADR-002-tenancy.md
docs/baseline/deliverable-manifest.json
docs/baseline/sprint-0-integration-report.md
frontend/payroll-web/README.md
frontend/payroll-web/index.html
frontend/payroll-web/package-lock.json
frontend/payroll-web/package.json
frontend/payroll-web/src/App.tsx
frontend/payroll-web/src/features/draft-payslip/DraftPayslipPage.test.tsx
frontend/payroll-web/src/features/draft-payslip/DraftPayslipPage.tsx
frontend/payroll-web/src/features/organisation/SetupPage.tsx
frontend/payroll-web/src/main.tsx
frontend/payroll-web/src/styles.css
frontend/payroll-web/src/test/setup.ts
frontend/payroll-web/src/vite-env.d.ts
frontend/payroll-web/tsconfig.app.json
frontend/payroll-web/tsconfig.json
frontend/payroll-web/vite.config.ts
```

Preserved existing file:

```text
docs/baseline/Payroll_Organisation_to_Draft_Payslip_Implementation_Pack_v1_0.docx
```

## Commands executed

Commands below include inspection, successful verification, and failed diagnostic attempts. Long read-only PowerShell commands are shown with the same inputs and range parameters used during execution.

### Pre-change inspection

```powershell
rg --files -uu
Get-ChildItem -Force
Get-Item -LiteralPath 'C:\dev\Working\Payroll_Organisation_to_Draft_Payslip_All_Deliverables_v1_0.zip'
Get-Content -Raw 'C:\Users\Srinivasa Rao\.codex\plugins\cache\openai-primary-runtime\documents\26.715.12143\skills\documents\SKILL.md'
Get-Content -Raw 'C:\Users\Srinivasa Rao\.codex\plugins\cache\openai-primary-runtime\documents\26.715.12143\skills\documents\tasks\read_review.md'
Get-Content on SKILL.md ranges 0-179, 100-179, 180-279, 280-359 and 360-EOF
tar -tf 'C:\dev\Working\Payroll_Organisation_to_Draft_Payslip_All_Deliverables_v1_0.zip'
Expand-Archive -LiteralPath 'C:\dev\Working\Payroll_Organisation_to_Draft_Payslip_All_Deliverables_v1_0.zip' -DestinationPath $temporaryArtifactRoot
Get-FileHash -Algorithm SHA256 for the repository and packaged implementation DOCX
Expand-Archive for hrms-payroll-vertical-slice-maven.zip, payroll-web-react18.zip, payroll-vertical-slice-flyway.zip and payroll-local-dev-compose.zip
& $bundledPython $renderDocx 'docs\baseline\Payroll_Organisation_to_Draft_Payslip_Implementation_Pack_v1_0.docx' --output_dir $temporaryRenderDir --emit_pdf
PowerShell OOXML extraction of DOCX paragraph ranges 0-159, 160-329, 330-499 and 500-658
Get-Content -Raw for deliverable-manifest.json, the backlog CSV, Flyway README, bootstrap, seed and verification SQL
Get-Content -Raw for V001-V006 and V007-V012 in filename order
Get-Content for OpenAPI ranges 0-299, 300-649, 650-899 and 900-1047
Get-Content -Raw for every Maven POM, Maven Java/YAML source, README and ADR
Get-FileHash comparison of standalone and Maven-embedded V001-V012
Get-Content -Raw for every React source/config file; inspect package-lock.json head/tail and tsconfig.app.tsbuildinfo
Get-Content -Raw for every Compose package file
git status --short
java -version
mvn -version
node --version
npm --version
docker version --format '{{.Client.Version}}|{{.Server.Version}}'
docker compose version
Get-ChildItem for installed JDKs, bundled Node/pnpm and cached Maven wrappers
```

The DOCX render command failed because LibreOffice/`soffice` is not installed; the complete document text and metadata were still read. The host tool check found Java 17 only, no global Maven/Node/npm, and a stopped Docker engine.

### Repository integration and wrapper

```powershell
Copy-Item/Expand-Archive operations importing the Maven backend, React app, OpenAPI, Flyway package, Compose package, backlog, manifest and ADRs into the requested layout
Remove-Item -LiteralPath 'backend\database-migrations\src\main\resources\db\migration' -Recurse
Invoke-WebRequest for the Spring Boot 3.5.16 BOM; inspect flyway.version and postgresql.version
Invoke-WebRequest for maven-wrapper-distribution-3.3.4-only-script.zip and its Maven Central checksum
Expand-Archive the verified wrapper distribution and copy mvnw, mvnw.cmd, mvnwDebug and mvnwDebug.cmd
.\mvnw.cmd --version
```

The first wrapper checksum request used a nonexistent `.sha256` URL and returned 404; the published Maven Central SHA-1 was then verified successfully (`640f7c39ee902b630ada7e77086a6ddb9fceae56`). The wrapper reported Maven 3.9.11.

### Infrastructure and database

```powershell
Start-Process -FilePath 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden
docker info --format '{{.ServerVersion}}'
Copy-Item deploy/local/.env.example deploy/local/.env
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml logs --tail 40 postgres keycloak
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down -v
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker inspect --format '{{.State.Health.Status}}' local-postgres-1
Invoke-WebRequest 'http://localhost:8081/realms/payroll/.well-known/openid-configuration'
Get-Content -Raw database/flyway/bootstrap/001_admin_bootstrap.sql | docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -U postgres -d payroll -c "SELECT rolname, rolcanlogin, rolbypassrls FROM pg_roles WHERE rolname IN ('payroll_owner','payroll_migrator','payroll_app') ORDER BY rolname;"
docker volume create local_maven_cache
docker run --rm --network local_default -e FLYWAY_URL=jdbc:postgresql://postgres:5432/payroll -e FLYWAY_USER=payroll_migrator -e FLYWAY_PASSWORD=change-me -v "${PWD}:/workspace" -v local_maven_cache:/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B -pl backend/database-migrations flyway:migrate
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T -e PGPASSWORD=change-me postgres psql -h 127.0.0.1 -U payroll_migrator -d payroll -c "SELECT session_user, current_user; SHOW role;"
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql | docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -U postgres -d payroll -c "SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;"
docker run --rm --network local_default -e FLYWAY_URL=jdbc:postgresql://postgres:5432/payroll -e FLYWAY_USER=payroll_migrator -e FLYWAY_PASSWORD=change-me -v "${PWD}:/workspace" -v local_maven_cache:/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B -pl backend/database-migrations flyway:validate
```

The first PostgreSQL start exposed the PostgreSQL 18 volume target change and was recreated with `/var/lib/postgresql`. The first Flyway run failed creating its history table; the bootstrap was fixed to grant `CREATE` on `public`. The next run failed creating trusted extensions; the bootstrap was fixed to grant database `CREATE` to `payroll_owner`. The final migration and validation runs succeeded.

### Backend verification

```powershell
docker run --rm -v "${PWD}:/workspace" -v local_maven_cache:/root/.m2 -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B verify
```

The first reactor run failed because `security` did not declare Jakarta Servlet. After adding `jakarta.servlet-api` with `provided` scope, two complete reactor runs succeeded. The final run took 15.700 seconds and passed 13/13 modules and 1/1 test.

### Frontend and OpenAPI verification

```powershell
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm install --package-lock-only --ignore-scripts --registry=https://registry.npmjs.org
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm ci --registry=https://registry.npmjs.org
docker volume create local_node_modules
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -v local_node_modules:/app/node_modules -w /app node:24.14.0-bookworm-slim npm ci --registry=https://registry.npmjs.org
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -v local_node_modules:/app/node_modules -w /app node:24.14.0-bookworm-slim npx --yes npm@11.18.0 ci --registry=https://registry.npmjs.org
pnpm install --dir frontend/payroll-web --lockfile=false
pnpm help approve-builds
pnpm --dir frontend/payroll-web approve-builds esbuild
pnpm --dir frontend/payroll-web rebuild esbuild
pnpm --dir frontend/payroll-web test
pnpm --dir frontend/payroll-web run build
pnpm view @redocly/cli version
pnpm dlx @redocly/cli@2.39.0 lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
pnpm --package=@redocly/cli@2.39.0 dlx redocly lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm audit --omit=dev --audit-level=high
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm audit --audit-level=high
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm install react-router-dom@7.18.1 --save-exact --package-lock-only --ignore-scripts --registry=https://registry.npmjs.org
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -w /app node:24.14.0-bookworm-slim npm install --save-dev vitest@3.2.7 --save-exact --package-lock-only --ignore-scripts --registry=https://registry.npmjs.org
node frontend/payroll-web/node_modules/.pnpm/esbuild@0.28.1/node_modules/esbuild/install.js
node frontend/payroll-web/node_modules/vitest/vitest.mjs run
node frontend/payroll-web/node_modules/typescript/bin/tsc -b
node frontend/payroll-web/node_modules/vite/bin/vite.js build
PowerShell deterministic replacement of the artifact generator's private npm registry prefix with https://registry.npmjs.org/ in package-lock.json
docker run --rm -v "${PWD}\frontend\payroll-web\package.json:/tmp/app/package.json:ro" -v "${PWD}\frontend\payroll-web\package-lock.json:/tmp/app/package-lock.json:ro" -w /tmp/app node:24.14.0-bookworm-slim npm ci --registry=https://registry.npmjs.org
docker volume create local_npm_modules_final
docker run --rm -v "${PWD}\frontend\payroll-web\package.json:/tmp/app/package.json:ro" -v "${PWD}\frontend\payroll-web\package-lock.json:/tmp/app/package-lock.json:ro" -v local_npm_modules_final:/tmp/app/node_modules -w /tmp/app node:24.14.0-bookworm-slim npm ci --registry=https://registry.npmjs.org
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -v local_npm_modules_final:/app/node_modules -w /app node:24.14.0-bookworm-slim npm test
docker run --rm -v "${PWD}\frontend\payroll-web:/app" -v local_npm_modules_final:/app/node_modules -w /app node:24.14.0-bookworm-slim npm run build
```

Initial npm installs on the full Windows bind mount hit npm's `Exit handler never called`/timeout behavior. The final isolated native-filesystem `npm ci`, exact `npm test`, and exact `npm run build` commands succeeded. The initial Redocly `dlx` call was ambiguous because the package exposes two binaries; specifying `redocly` succeeded. The dependency audit identified and then cleared React Router and Vitest advisories through same-major/patch updates.

### Final audits

```powershell
PowerShell Keycloak Admin REST query for the payroll-web tenant_id protocol mapper (token retained only in memory and never printed)
jar tf backend/database-migrations/target/database-migrations-1.0.0-SNAPSHOT.jar
rg scans for private keys, AWS keys, JWTs, passwords, synthetic identifiers and private registry URLs
git check-ignore -v deploy/local/.env
PowerShell OOXML read of docs/baseline/Payroll_Organisation_to_Draft_Payslip_Implementation_Pack_v1_0.docx docProps/core.xml
Get-Content/ConvertFrom-Json and XML parsing for final package.json, Keycloak JSON and all POMs
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
git status --short --untracked-files=all
```

## Final results

- PostgreSQL: 18.4 container healthy on port 5432.
- Keycloak: 26.7.0 running on port 8081; discovery endpoint returned HTTP 200.
- Keycloak tenant mapper: `tenant_id` is configured as an access-token claim.
- Administrator bootstrap: passed; `payroll_app` is `NOBYPASSRLS`.
- Flyway migrate: V001-V012 applied successfully.
- Flyway validate: 12 migrations validated successfully.
- Database verification: both supplied queries returned zero rows.
- Migration JAR: contains exactly 12 `db/migration/V*.sql` resources.
- Maven: 13/13 reactor modules passed; 1 test passed; 0 failed.
- React: 1 test file and 1 test passed.
- Production build: Vite 8.1.5 built 25 modules successfully.
- OpenAPI: valid under Redocly CLI 2.39.0 recommended rules.
- npm audit: 0 vulnerabilities across production and development dependencies.
- Credential/PII audit: no private keys, access tokens, production secrets or personal records found. Password strings are documented local placeholders only; `.env` is ignored. `DEMO001`, `payroll.admin` and Demo Payroll are synthetic fixtures. DOCX metadata contains no personal author name.
- Git: no commit was created.

## Unresolved issues and warnings

1. Flyway 11.7.2, the version managed by the approved Spring Boot 3.5.16 BOM, warns that its tested PostgreSQL support matrix ends at PostgreSQL 17. The PostgreSQL 18.4 migration and checksum validation nevertheless completed successfully. A Flyway upgrade should be handled as a separately reviewed dependency-baseline change.
2. LibreOffice/`soffice` is unavailable on this workstation, so the implementation DOCX could not be re-rendered for visual QA. Its full text and metadata were read, and the supplied manifest records the original 20-page validation.

There are no unresolved compilation, test, migration, contract, infrastructure or dependency-audit failures.

---

# Sprint 0A hardening addendum

Date: 19 July 2026  
Status: completed, running locally, not committed  
This addendum supersedes the earlier Sprint 0 final-results and unresolved-issues sections above.

## Outcome

Sprint 0A hardened the existing starter without adding Sprint 1 endpoints or payroll capabilities. V001-V012 remain unchanged. V013 adds tenant-safe relationship consistency and immutability. PostgreSQL 17 and Keycloak are running on loopback-only ports, V001-V013 are applied and validated, exhaustive catalog verification passes, and all backend, frontend, OpenAPI, RLS, security, architecture, secret and fallback dependency checks pass.

The separately referenced Foundation Pack was not supplied in the repository, the combined delivery ZIP, `C:\dev\Working`, or elsewhere under `C:\dev`. The complete Organisation-to-Draft-Payslip Implementation Pack was read, including all 209 paragraphs and 21 tables; its embedded Foundation-derived architecture and security rules were used. DOCX rendering was attempted but LibreOffice/`soffice` is unavailable, so no new visual render QA claim is made.

## Files changed for Sprint 0A

```text
.github/workflows/ci.yml
.gitignore
AGENTS.md
README.md
backend/calculation-engine/pom.xml
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/CanonicalPayloadHasher.java
backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/FixedGrossToNetCalculator.java
backend/calculation-engine/src/test/java/com/acme/hrms/payroll/calculation/DeterminismFoundationTest.java
backend/database-migrations/pom.xml
backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java
backend/payroll-boot/pom.xml
backend/payroll-boot/src/main/resources/application-prod.yml
backend/payroll-boot/src/main/resources/application.yml
backend/payroll-boot/src/test/java/com/acme/hrms/payroll/ArchitectureRulesTest.java
backend/platform-core/pom.xml
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/BaseEntity.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/CorrelationContext.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/DomainEventFactory.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/PayrollAuditingConfiguration.java
backend/platform-core/src/main/java/com/acme/hrms/payroll/platform/ProblemDetailsAdvice.java
backend/platform-core/src/test/java/com/acme/hrms/payroll/platform/DomainEventFactoryTest.java
backend/security/pom.xml
backend/security/src/main/java/com/acme/hrms/payroll/security/AudienceValidator.java
backend/security/src/main/java/com/acme/hrms/payroll/security/CorrelationIdFilter.java
backend/security/src/main/java/com/acme/hrms/payroll/security/PayrollJwtAuthenticationConverter.java
backend/security/src/main/java/com/acme/hrms/payroll/security/SecurityConfiguration.java
backend/security/src/main/java/com/acme/hrms/payroll/security/SecurityProblemWriter.java
backend/security/src/main/java/com/acme/hrms/payroll/security/TenantClaimFilter.java
backend/security/src/test/java/com/acme/hrms/payroll/security/SecurityBaselineTest.java
contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
database/flyway/README.md
database/flyway/bootstrap/001_admin_bootstrap.sql
database/flyway/sql/V013__vertical_slice_baseline_hardening.sql
database/flyway/verification/verify_vertical_slice.sql
deploy/local/.env.example
deploy/local/README.md
deploy/local/compose.yaml
deploy/local/keycloak/payroll-realm.json
deploy/local/postgres/001_admin_bootstrap.sh
docs/adr/ADR-003-postgresql-17-baseline.md
docs/adr/ADR-004-effective-dated-organisation-entities.md
docs/baseline/sprint-0-integration-report.md
```

Ignored/generated local files were also refreshed: `deploy/local/.env` gained `POSTGRES_PORT=15432`; Maven `target/` outputs include `target/bom.json` and `target/bom.xml`; frontend outputs include `dist/`, `node_modules/` and `bom-frontend.json`. These are ignored and are not source deliverables.

## Commands executed for Sprint 0A

The following ledger includes successful commands and failed diagnostics. File changes were made with `apply_patch`; no commit command was run.

```powershell
# Required-source and skill reads
Get-Content -Raw documents/SKILL.md and tasks/read_review.md
Get-Content SKILL.md in four ordered ranges (one initial range script failed, then the four reads succeeded)
Get-ChildItem -Recurse for Foundation Pack candidates under C:\dev\Working and C:\dev
Open the supplied combined ZIP and list Foundation matches and archive entries
Get-Content -Raw README.md, AGENTS.md and docs/baseline/sprint-0-integration-report.md
Get-Content -Raw all backend/frontend tests and the Keycloak realm JSON
Get-Content -Raw database/flyway/README.md, bootstrap, verification SQL and V001-V012
Get-Content -Raw contracts/openapi/payroll-vertical-slice-openapi-v1.yaml
Bundled Python/python-docx extraction of all 209 implementation-pack paragraphs and all 21 tables
Bundled render_docx.py ... --emit_pdf --verbose
Get-Content -Raw for every Maven POM, Java source and backend YAML configuration
Get-Content -Raw deploy/local compose, environment example and repository ignores

# Build and test diagnostics/final runs
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B verify
docker run ... maven:3.9.11-eclipse-temurin-21 mvn -B -pl backend/security -am test
Get-Content backend/security/target/surefire-reports/*
docker run -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ... mvn -B -pl backend/database-migrations -am verify
docker run -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ... mvn -B verify

# Local runtime and database
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml config
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
Get-NetTCPConnection -LocalPort 5432
Get-Process -Id 6496
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml logs --tail 80 postgres keycloak
docker inspect --format '{{.State.Health.Status}}' local-postgres-1
docker run --network local_default -e FLYWAY_URL=jdbc:postgresql://postgres:5432/payroll -e FLYWAY_USER=payroll_migrator -e FLYWAY_PASSWORD=change-me ... mvn -B -pl backend/database-migrations flyway:migrate flyway:validate
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql | docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
Invoke-WebRequest http://127.0.0.1:8081/realms/payroll/.well-known/openid-configuration

# Frontend and contract
pnpm --dir frontend/payroll-web test / run build diagnostics (failed because the existing modules directory required a TTY purge)
pnpm dlx Redocly diagnostics (failed because the package now exposes two binaries and because of sandbox realpath access)
docker run ... node:24.14.0-bookworm-slim npm ci
docker run ... node:24.14.0-bookworm-slim npm test
docker run ... node:24.14.0-bookworm-slim npm run build
docker run ... node:24.14.0-bookworm-slim npx --yes --package=@redocly/cli@2.39.0 redocly lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml

# Security, dependency and SBOM checks
rg scans for direct Instant.now(), the literal system audit actor, likely embedded secrets and personal-data markers
Get-FileHash -Algorithm SHA256 database/flyway/sql/V001__*.sql through V012__*.sql
docker run ... zricethezav/gitleaks:v8.28.0 detect --source=/repo --no-git --redact --verbose
docker run ... mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
docker run ... npx --yes @cyclonedx/cyclonedx-npm --output-file bom-frontend.json
docker run ... mvn -B org.owasp:dependency-check-maven:12.1.8:check -DfailBuildOnCVSS=7 -Dformats=JSON
docker run ... aquasec/trivy:0.67.2 fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --no-progress /repo
git status --short
```

Notable failed diagnostic runs were retained in this ledger: the first Maven pass found and led to fixing incorrect security imports; the first security context test lacked Spring MVC; the first nested Testcontainers run could not reach Ryuk; the first composite-FK assertion collided with an existing unique hash; the first loopback database start found a host PostgreSQL process already using 5432; the first audience test exposed a null `aud` edge case; OWASP Dependency-Check timed out after 604 seconds while updating its vulnerability feed. Each repository defect was fixed and rerun; the external OWASP feed timeout is documented under risks.

## Sprint 0A verification results (historical)

- Local services: PostgreSQL `17.10` healthy at `127.0.0.1:15432`; Keycloak `26.7.0` at `127.0.0.1:8081`; discovery HTTP 200.
- Flyway: 13 migrations applied from empty and 13 validated; no PostgreSQL-version support warning.
- Database verification: all catalog assertion blocks passed.
- RLS evidence: 30/30 tenant-owned tables report `relrowsecurity=true`, `relforcerowsecurity=true` and policy `tenant_isolation` with tenant `USING` and `WITH CHECK` expressions.
- FK evidence: 45 tenant-bearing FKs were listed; no tenant-owned-to-tenant-owned FK omitted `tenant_id`. V013 consistency FKs were present.
- Runtime-role evidence: `payroll_app`, `payroll_migrator` and `payroll_owner` are not superuser, create-role, create-database or bypass-RLS roles. `payroll_app` owns no schema/table, cannot assume `payroll_owner`, cannot create database/schema objects, and has per-table runtime DML grants matching the baseline.
- Testcontainers RLS: 3/3 passed, covering no tenant, Tenant A, Tenant B, cross-tenant SELECT/INSERT/UPDATE/DELETE, cross-tenant FK, composite consistency FK, DDL, role assumption, BYPASSRLS attributes and immutable snapshot/payslip mutation.
- Security: 8/8 passed, covering valid tenant and permission, role/permission mapping, missing tenant, malformed tenant, expired bearer token, missing permission, missing audience and filter ordering after bearer authentication/before authorization and repository boundary.
- Correlation/audit: fixed correlation IDs are reused in response headers and RFC 9457 bodies; domain-event factory uses the same thread context and injected clock; logging MDC uses `correlation_id`; JPA auditing uses injected `Clock` and authenticated issuer+subject or an explicit service identity. No direct `Instant.now()` or literal `system` audit actor remains in application code.
- Determinism: 2/2 tests passed for BASIC/HRA/SPECIAL ordered components and canonical map/decimal SHA-256 hashing.
- Architecture: 3/3 ArchUnit tests passed: module cycles `0`, forbidden internal imports `0`, repository ownership violations `0`.
- Maven: 13/13 reactor modules passed; final run 74.3 seconds. Tests: platform 1, security 8, calculation 2, database integration 3, architecture 3 and golden payroll 1; 18 total, 0 failed.
- React: 1/1 Vitest test passed; TypeScript and Vite production build passed (25 modules).
- OpenAPI: Redocly CLI 2.39.0 passed with zero warnings after applying `X-Correlation-ID` to every path. Reusable 401, 403, 404, 409, 422, 429, 500 and 503 responses are present.
- Secrets: Gitleaks scanned approximately 699 KB and found no leaks. Pattern scans found no production credentials or personal records.
- Dependencies: npm audit found 0 vulnerabilities. Trivy scanned all 13 Maven POMs plus `package-lock.json` for HIGH/CRITICAL issues and reported 0 findings. OWASP Dependency-Check did not complete its first vulnerability-feed update within 10 minutes.
- SBOM: backend CycloneDX 1.6 JSON/XML generated with 147 components; frontend CycloneDX JSON generated. CI publishes both as artifacts.
- Git: no commit created.

## V013 migration

```sql
ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_tenant_assignment_fk
  FOREIGN KEY (tenant_id, payroll_assignment_id)
  REFERENCES employee_payroll.payroll_assignment (tenant_id, id);

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_tenant_population_fk
  FOREIGN KEY (tenant_id, payroll_cycle_id, payroll_assignment_id)
  REFERENCES payroll_ops.population_member (tenant_id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_calc.calculation_request
  ADD CONSTRAINT calculation_request_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_consistency_uk
  UNIQUE (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_tenant_request_cycle_fk
  FOREIGN KEY (tenant_id, calculation_request_id, payroll_cycle_id)
  REFERENCES payroll_calc.calculation_request (tenant_id, id, payroll_cycle_id);

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_tenant_snapshot_cycle_assignment_fk
  FOREIGN KEY (tenant_id, input_snapshot_id, payroll_cycle_id, payroll_assignment_id)
  REFERENCES payroll_ops.input_snapshot (tenant_id, id, payroll_cycle_id, payroll_assignment_id);

CREATE TRIGGER input_snapshot_immutable
  BEFORE UPDATE OR DELETE ON payroll_ops.input_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER draft_payslip_immutable
  BEFORE UPDATE OR DELETE ON documents.draft_payslip
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

REVOKE UPDATE, DELETE ON payroll_ops.input_snapshot, documents.draft_payslip FROM payroll_app;

REVOKE CREATE ON SCHEMA platform, security, audit, organisation, compensation,
  employee_payroll, payroll_ops, payroll_calc, documents, integration FROM payroll_app;
```

## Decoded synthetic JWT claim summary

No raw token was generated or printed. The in-memory synthetic JWT claim object used by the automated security test decoded to:

```text
iss: http://localhost:8081/realms/payroll
sub: operator
aud: [payroll-api]
tenant_id: 00000000-0000-0000-0000-000000000001
realm_access.roles: [PAYROLL_OPERATOR]
permissions: [payroll.read]
iat: 2026-07-19T00:00:00Z
exp: 2026-07-19T01:00:00Z
mapped authorities: [ROLE_PAYROLL_OPERATOR, payroll.read]
```

Keycloak maps the user attribute `tenant_id` into access tokens and adds the `payroll-api` audience. The production resource server independently validates issuer, timestamps and audience before the tenant filter runs.

## Risks recorded at the end of Sprint 0A (superseded below)

1. The external Foundation Pack was not supplied. Sprint 0A used all Foundation-derived requirements embedded in the implementation pack; the missing source pack should be reconciled if later provided.
2. OWASP Dependency-Check timed out during initial feed retrieval. Trivy and npm audit are clean, and CI retains the OWASP job; provide an NVD API key/warmed cache for reliable CI latency.
3. Spring Boot 3.5 marks `@MockBean` deprecated and Mockito warns about future JDK dynamic-agent restrictions. Tests pass today; migrate to the replacement bean-override annotation and configured Mockito agent during the next dependency-baseline update.
4. The development Keycloak realm contains only a synthetic temporary-password user. The realm and Compose stack are development-only and production configuration does not import them; a production IdP/secret manager remains external work.
5. A host PostgreSQL service occupies port 5432, so Compose defaults to loopback port 15432. The exact README commands account for this.
6. The original implementation DOCX could not be rendered because LibreOffice is unavailable; its text/tables were completely extracted, but visual QA was not repeated.

## Sprint 0B final baseline closure

Sprint 0B completed on 19 July 2026 without adding Sprint 1 business behaviour. This section supersedes earlier provisional statements about the missing Foundation Pack and the OWASP feed timeout.

### Files added or changed in Sprint 0B

```text
AGENTS.md
README.md
pom.xml
backend/database-migrations/pom.xml
backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java
backend/payroll-boot/src/main/java/com/acme/hrms/payroll/BaselineAuthSmokeController.java
backend/payroll-boot/src/main/resources/application-prod.yml
backend/payroll-boot/src/main/resources/application.yml
backend/security/src/main/java/com/acme/hrms/payroll/security/SecurityConfiguration.java
backend/test-support/pom.xml
backlog/organisation-to-draft-payslip-sprint-backlog.csv
deploy/local/.env.example
deploy/local/README.md
deploy/local/keycloak/payroll-realm.json
deploy/local/smoke/auth-smoke.ps1
docs/adr/ADR-004-effective-dated-organisation-entities.md
docs/adr/ADR-005-snapshot-and-draft-supersession.md
docs/baseline/Payroll_Implementation_Foundation_Pack_v1_0.docx
docs/baseline/deliverable-manifest.json
docs/baseline/sprint-0-integration-report.md
```

The ignored development file `deploy/local/.env` was refreshed with the synthetic smoke-test identity. Generated and ignored evidence includes Maven/Dependency-Check outputs, frontend `dist/`, backend and frontend CycloneDX SBOMs, and `target/sprint-0-complete.diff`. None is staged.

### Foundation Pack evidence

- File: `Payroll_Implementation_Foundation_Pack_v1_0.docx`
- Size: 463,200 bytes.
- SHA-256: `1be26ef7c32c875e55386dec3b8265f18762d724c927a2ea01528445ea8b715b`.
- Manifest entry: present and checksum-matched.
- OOXML structural QA: ZIP CRC valid; 23 package entries; document, styles, numbering and core properties present; 800 paragraphs, 51 tables, 122 headings, 4 inline shapes and one section parsed successfully.
- Visual render: not performed because LibreOffice/`soffice` is unavailable in the workspace runtime. The source DOCX was not altered.

### Architecture and lifecycle decisions

ADR-004 selects an identity-plus-version model for each tenant-owned legal entity, payroll statutory unit and establishment. Stable identity keys address the enduring organisation concept. Immutable version keys identify the exact effective-dated state and use half-open ranges `[effective_from, effective_to)`. Sealed payroll lineage stores exact version IDs. Before Sprint 1 enables organisation writes, the physical model must separate identity/version keys and enforce tenant-safe hierarchy FKs and non-overlap constraints.

ADR-005 records that mutable source inputs are collected and corrected only in domain source/staging structures. The sealing transaction completes tenant, assignment, cycle, effective-date, required-component and canonical-payload validation before inserting one immutable `input_snapshot`. A correction produces a new sealed snapshot. Draft payslips are append-only: regeneration creates a successor draft, prior drafts remain immutable audit evidence, and current selection follows deterministic lineage/order rather than in-place mutation.

### Event reliability entry gate

`RowLevelSecurityIT.inboxIdempotencyIsTenantAndConsumerScoped` proves the database inbox identity is unique per tenant, message and consumer: the same tuple is rejected with SQLSTATE `23505`, while a different consumer or tenant is accepted. Backlog item `S1-00` is the first Sprint 1 task and must prove transactional outbox persistence, stable event identity, duplicate dispatch and tenant/consumer inbox deduplication before any real domain-event publication.

### Real-token authentication smoke

The smoke obtained a signed access token from the imported development Keycloak realm, kept it only in process memory, decoded claims only for validation, called `GET /internal/baseline/auth-smoke`, and cleared token variables in `finally`. The endpoint is conditional on `BASELINE_AUTH_SMOKE_ENABLED=true` and is forcibly disabled by the production profile.

```text
Result: PASS
Issuer: http://127.0.0.1:8081/realms/payroll
Audience: payroll-api
TenantId: 00000000-0000-0000-0000-000000000001
Roles: PAYROLL_OPERATOR
Permissions: payroll.read
SecuredEndpoint: /internal/baseline/auth-smoke (HTTP 200)
CorrelationIdReused: True
RawTokenPrintedOrPersisted: False
```

The temporary application container was removed after the check. PostgreSQL and Keycloak remain bound to `127.0.0.1` for local review.

### Commands executed for Sprint 0B

The ledger includes successful checks and failed diagnostics. Source changes were made with `apply_patch`; no commit command was run.

```powershell
# Required reads and Foundation Pack QA
Get-Content -Raw README.md, AGENTS.md, docs/baseline/sprint-0-integration-report.md
Get-Content -Raw the documents skill SKILL.md and tasks/read_review.md
Get-Content -Raw the Foundation/implementation pack metadata, ADR-004, V001-V013, OpenAPI, realm and all backend/frontend tests
Get-ChildItem -Recurse for Payroll_Implementation_Foundation_Pack_v1_0.docx under C:\dev\Working and C:\dev
Get-FileHash -Algorithm SHA256 docs/baseline/Payroll_Implementation_Foundation_Pack_v1_0.docx
Bundled Python ZIP/CRC and python-docx structural inspection
render_docx.py ... --emit_pdf (failed: LibreOffice/soffice is unavailable)

# Local database, migration and RLS/security evidence
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down -v
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
docker inspect --format '{{.State.Health.Status}}' local-postgres-1
docker run --network local_default ... mvn -B -pl backend/database-migrations flyway:migrate flyway:validate
Get-Content -Raw database/flyway/verification/verify_vertical_slice.sql | docker compose ... exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d payroll
docker compose ... exec -T postgres psql catalog queries for migration count, RLS/FORCE/policies, tenant FKs, grants and role attributes
docker run -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ... mvn -B -pl backend/database-migrations -am verify

# Backend verification and real-token smoke
docker run -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ... mvn -B verify
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d --force-recreate keycloak
Invoke-WebRequest http://127.0.0.1:8081/realms/payroll/.well-known/openid-configuration
docker run -d --name payroll-auth-smoke --network local_default -p 127.0.0.1:8080:8080 ... mvn -B -f backend/payroll-boot/pom.xml spring-boot:run
Invoke-WebRequest http://127.0.0.1:8080/actuator/health
& ./deploy/local/smoke/auth-smoke.ps1 (blocked by host execution policy; no token issued)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/local/smoke/auth-smoke.ps1
docker rm -f payroll-auth-smoke

# Frontend and OpenAPI
docker run ... node:24.14.0-bookworm-slim npm test
docker run ... node:24.14.0-bookworm-slim npm run build
pnpm dlx @redocly/cli@2.39.0 lint ... (failed: package exposes multiple binaries)
pnpm --package=@redocly/cli@2.39.0 dlx redocly lint contracts/openapi/payroll-vertical-slice-openapi-v1.yaml

# Dependency, secret and SBOM scans
docker run ... zricethezav/gitleaks:v8.28.0 detect --source=/repo --no-git --redact --verbose
docker run ... node:24.14.0-bookworm-slim npm audit --audit-level=high
docker run ... node:24.14.0-bookworm-slim npm audit --omit=dev --audit-level=high
docker run ... aquasec/trivy:0.67.2 fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 /repo
docker run ... mvn -B org.owasp:dependency-check-maven:12.1.8:check -DfailBuildOnCVSS=7 -Dformats=JSON
docker run ... mvn -B dependency:tree (used to identify and upgrade vulnerable runtime versions)
docker run ... mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
docker run ... npx --yes @cyclonedx/cyclonedx-npm --output-file bom-frontend.json (failed against host pnpm layout)
docker run ... -v local_node_modules:/app/node_modules ... npx --yes @cyclonedx/cyclonedx-npm --output-file bom-frontend.json

# Review/index evidence
git status --short
git diff --stat
git diff --cached --name-only
git status --ignored --short
git check-ignore -v deploy/local/.env target/bom.json target/bom.xml frontend/payroll-web/bom-frontend.json frontend/payroll-web/dist/index.html frontend/payroll-web/node_modules
Temporary bare Git index plus git add -N and git diff --no-textconv --output=target/sprint-0-complete.diff
```

Additional failed diagnostics that led to fixes: the first Keycloak password grant reported an incomplete synthetic user profile; the first application start attempted Flyway metadata access as `payroll_app`; the next start exposed a final controller incompatible with method-security proxying; initial OWASP runs found newly disclosed Log4j, Tomcat, pgjdbc and Jackson vulnerabilities; and Testcontainers 2 required its renamed PostgreSQL package/import. Each repository issue was corrected and its affected check rerun successfully. No raw token was emitted during any attempt.

### Final Sprint 0B verification results

- Maven: 13/13 modules passed in 60 seconds; 19 tests passed, 0 failed (platform 1, security 8, deterministic hashing/order 2, database/RLS/idempotency 4, architecture 3, golden 1).
- Flyway: clean PostgreSQL 17.10 installation applied and validated V001-V013; 13/13 migrations; no PostgreSQL 18 support warning.
- Database verification: every assertion block passed. RLS evidence remains 30/30 tenant-owned tables with ENABLE RLS, FORCE RLS and tenant policy; 45 tenant-bearing FKs were enumerated; runtime role/grant/immutability assertions passed.
- Real authentication: Keycloak 26.7.0 token and secured application HTTP 200 passed with the sanitized claim summary above.
- React: 1/1 Vitest test passed; TypeScript/Vite production build passed with 25 modules transformed.
- OpenAPI: Redocly CLI 2.39.0 valid, zero warnings.
- Architecture: 3/3 ArchUnit tests passed; no module cycles, forbidden internal imports or repository ownership violations.
- Secrets: Gitleaks v8.28.0 scanned approximately 4.51 MB and found no leaks.
- Dependencies: npm audits (all and production-only) found 0 vulnerabilities; refreshed Trivy 0.67.2 reported 0 HIGH/CRITICAL findings across 13 Maven POMs and the npm lockfile; OWASP Dependency-Check 12.1.8 passed every module at `failBuildOnCVSS=7` after upgrading patched runtime dependencies.
- SBOM: backend CycloneDX 1.6 JSON/XML generated with 108 components; frontend CycloneDX JSON generated successfully against the isolated npm tree.
- Git: the real index is empty, no commit exists, and ignored `.env`, `target/`, `dist/`, `node_modules/` and SBOM files are not staged. Because the repository has no initial commit, normal `git diff` is empty; a complete review patch was generated through a temporary bare index at ignored path `target/sprint-0-complete.diff` (111 files, 8,714 insertions).

### Remaining risks after Sprint 0B

1. LibreOffice is unavailable, so the newly added Foundation Pack could not be page-rendered for visual QA. OOXML integrity and structural parsing passed; visual confirmation remains a reviewer action on a host with Word or LibreOffice.
2. The Keycloak smoke identity, password grant and credentials are development-only. The realm import is not a production identity design, and the production profile disables the smoke endpoint.
3. The database inbox uniqueness foundation is proven, but end-to-end crash/retry dispatch is intentionally not implemented in Sprint 0. Backlog item `S1-00` blocks real domain-event publication until that proof exists.
4. OWASP Dependency-Check and Trivy use their standard runtime/production scan scope; Testcontainers remains test-only. Keep Testcontainers current and review its shaded transport dependencies as vulnerability feeds evolve.
5. Mockito reports its documented future-JDK dynamic-agent warning. Java 21 tests pass; configure an explicit agent before moving to a JDK that disables dynamic attachment.
6. There is no initial Git commit, so Git cannot show an ordinary tracked before/after diff. The temporary-index patch is complete for the current 111-file baseline and did not modify the real index.

Proposed baseline commit message: `chore(payroll): close Sprint 0 baseline` and the commit id :bba8a51d17147443e400f51bc9ccf769b8bd1af8
