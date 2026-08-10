# P5-FSR-01 — Foundation Snapshot & Readiness Closure Scope Authority

**Status:** ACTIVE after activation-authority merge
**Reasoning level:** R3 — cross-program capability selection, architecture, migration ownership and cross-repository sequencing
**Repository authority:** `srinivasbs2000/hrms-payroll`
**UI repository:** `srinivasbs2000/hrms-payroll-web`
**Activation branch:** `docs/p5-fsr-01-activation-authority`
**Product branch:** `feature/p5-fsr-01-foundation-snapshot-readiness-closure` created only from activation-merged `main`
**Migration reservation:** V036 exclusively reserved after activation-authority merge
**Primary canonical stories:** PLN-E01-010, PLN-E01-012
**Cross-cutting story:** PLN-E01-011 remains PARTIALLY IMPLEMENTED unless separate evidence closes its remaining reusable application-approval/delegation gap

## 1. R3 selection rationale

P5-FBA-01 closed employer banking and authorised-signatory foundation but
explicitly excluded immutable configuration snapshots and complete foundation
readiness. The canonical ledger therefore still has a direct Foundation epic
reproducibility/readiness gap before broader calendar expansion, country legal
rules, payments, retro, settlement or accounting should be selected.

P5-FSR-01 closes only that bounded gap. It does not reopen completed JRF/FBA
stories and does not silently absorb later capabilities.

## 2. Required product outcomes

### 2.1 Immutable foundation configuration snapshot

At payroll-cycle sealing/calculation time the system must capture the exact
approved foundation configuration versions used by that payroll execution with
stable snapshot identity and deterministic content hash. Historical payroll
must be reproducible without rereading mutable current master state.

The snapshot must preserve exact version lineage needed by the active bounded
foundation, including the payroll-cycle/pay-group/calendar/PSU/legal-employer
context and the approved organisation/jurisdiction/registration and other
calculation-relevant configuration versions actually consumed. Employer bank
and authorised-signatory state remains readiness evidence unless a later
authorised payment capability makes it an execution input. Missing,
conflicting, expired, unapproved or drifted required configuration blocks the
seal rather than producing a partial snapshot.

### 2.2 Calculation binding

The official calculation path must bind to the exact foundation-configuration
snapshot identity/hash. Once consumed by calculation/result evidence, later
configuration changes must not alter the historical calculation contract.
There is no fallback to mutable current configuration after sealing.

### 2.3 Composed foundation readiness

Expose a composed foundation-readiness contract that aggregates authoritative
foundation findings already owned by their domains. It must distinguish
blocking findings from warnings and report why a PSU/pay group is not ready.

The composition may include incorporation/legal-employer identity,
jurisdiction/registration, pay-group/calendar, employer bank/signatory and
configuration-snapshot readiness only where those dependencies are required by
the bounded foundation contract.

Readiness must not claim that country-specific statutory rules/rates, employee
bank accounts, payment execution, retro/off-cycle/final settlement, accounting,
migration/cutover or production operations are ready.

## 3. Architecture authority

`payroll-operations` owns cycle-time configuration sealing because it already
owns payroll-cycle population and immutable input sealing. Cross-domain capture
must follow the V024 pattern: a controlled database-level sealing operation may
read exact approved version identifiers across schemas under tenant/RLS
controls and persist immutable snapshot evidence. Do not create module-internal
Java dependencies merely to reach organisation/compensation/statutory internals.

The calculation engine consumes only the public snapshot identity/hash contract
needed for deterministic calculation/result lineage.

All new tenant-owned persistence follows stable UUID identity, tenant-safe
foreign keys, FORCE RLS, runtime least privilege and append-only history rules.
V001-V035 are immutable.

## 4. Maximum implementation boundary

The implementation runner must narrow this boundary to an exact file allow-list
before mutation. It may not write outside the following maximum path set.

### Backend/program repository

- `database/flyway/sql/V036__foundation_snapshot_readiness_closure.sql`
- `database/flyway/README.md`
- `database/flyway/e2e/fixtures/S03_001__sprint_3_executable_payroll.sql`
- `database/flyway/e2e/verify_smoke_fixture.sql`
- `database/flyway/verification/verify_vertical_slice.sql`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/FoundationSnapshotReadinessMigrationIT.java`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java`
- `backend/payroll-operations/pom.xml`
- `backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/**`
- `backend/payroll-operations/src/test/java/com/acme/hrms/payroll/payrolloperations/**`
- `backend/calculation-engine/src/main/java/com/acme/hrms/payroll/calculation/**`
- `backend/calculation-engine/src/test/java/com/acme/hrms/payroll/calculation/**`
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/**`
- `contracts/openapi/payroll-operations-openapi-v1.yaml`
- `contracts/openapi/payroll-calculation-openapi-v1.yaml`
- `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
- `deploy/local/keycloak/payroll-realm.json`
- `deploy/local/smoke/auth-smoke.ps1`
- `scripts/verify-foundation-snapshot-readiness-contracts.mjs`
- `.github/workflows/ci.yml` only if an existing gate must register the new bounded verification command; no unrelated CI redesign
- `.gitleaksignore` only for an exact synthetic fingerprint demonstrated by the capability history; no broad suppression
- P5-FSR-01 scope, quality/review, status, lineage, handoff and registry documents required by the standing closure protocol

### UI repository

- `src/App.tsx`
- `src/App.test.tsx`
- `src/features/foundation-readiness/**`
- `e2e/foundation-snapshot-readiness.spec.ts`
- `e2e/fsr-actors.setup.ts` only if distinct actor setup is required
- `e2e/start-backend.mjs`
- `playwright.config.ts`

### G02 R3 path amendment — 10 August 2026

To preserve the modular-monolith boundary and MDR-065 while composing existing
authoritative readiness findings, G02 may additionally write exactly these two
public facade paths:

- `backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/BankingReadinessFacade.java`
- `backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/RegistrationReadinessFacade.java`

These facades are contract adapters only. They may delegate to their own
module's existing bounded readiness service and expose its existing public
request/view types. They must not duplicate, weaken or redefine organisation
banking/signatory rules or statutory registration readiness rules. No other
organisation or statutory-deductions production path is added to P5-FSR-01
ownership by this amendment.

### R3 runtime-date consistency amendment — 11 August 2026

Cross-repository browser verification exposed a latent Foundation Banking &
Authority date-boundary inconsistency after local midnight. Application
lifecycle approval uses the injected UTC `Clock`, while pgJDBC initializes the
PostgreSQL session time zone from the JVM default. V035 and the current FBA read
path compare `timestamptz` values through `::date`, so a non-UTC JVM can evaluate
the same approval instant on a different calendar date from the application
approval guard.

To preserve one runtime date authority without editing immutable V035 or adding
a migration, P5-FSR-01 may additionally write exactly:

- `backend/payroll-boot/src/main/resources/application.yml`
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/RuntimeTimeAuthorityApiIT.java`

The only authorised production change in `application.yml` is to initialize
every application datasource connection with PostgreSQL session `TimeZone` UTC,
aligning database date semantics with the existing `Clock.systemUTC()` authority.
The test may prove both application-clock and database-session UTC authority even
when the host/JVM default time zone is non-UTC.

This amendment does not change FBA lifecycle rules, V035/V036 SQL, the half-open
effective-date model, country statutory scope, payment execution scope or story
status. The UI must not redefine backend approval-date semantics; the temporary
browser-local FBA date experiment must not be published.

No other path is owned by this capability without a new R3 authority update.
The presence of a directory wildcard above is a maximum ownership boundary, not
permission for a runner to change every file below it.

## 5. Explicit exclusions

P5-FSR-01 does not implement:

- country-specific PF/EPS/EDLI/ESI/PT/LWF/NPS/TDS rules, rates or legal conclusions;
- employee bank-account management;
- payment files, bank integrations or payment execution;
- retroactive payroll, off-cycle payroll or final settlement;
- accounting/ERP posting;
- broad multi-frequency calendar expansion unrelated to snapshot/readiness closure;
- production data migration, cutover or hypercare;
- a generic formula/rules engine;
- full PLN-E01-011 closure unless separate evidence proves reusable entity/PSU-scoped application approver authorization plus effective-dated approval delegation across the required foundation domains.

## 6. Pre-mortem before first implementation runner

### Windows execution

Failure modes: paths containing spaces, PowerShell scalar/array collapse,
stderr contaminating parsed stdout, stale `$LASTEXITCODE`, quoting/interpolation
errors and unsafe partial patching.

Controls: prefer standard Git/complete payloads; any PowerShell must pass the
real parser before execution, use process-object exit codes with separated
stdout/stderr, preserve zero/one/many cardinality and exercise a path containing
spaces. Every runner verifies exact base SHA, clean index/worktree and exact
allow-list before mutation.

### Fixture lifecycle

Failure modes: Sprint 3/FBA fixtures may not satisfy V036 prerequisites; replay
against immutable snapshots may fail; partially sealed state may make a rerun
non-deterministic.

Controls: audit the fixture lifecycle before changing it; preserve the ordered
population-resolved -> configuration-sealed -> input-sealed -> calculated
lifecycle; provide deterministic reset/resume behavior; test missing, expired,
unapproved, conflicting and drifted configuration plus partial-seal replay.

### Maven/runtime fidelity

Failure modes: stale locally installed Maven artifacts, E2E launching old
classes, migration absent from the runtime DB/classpath or focused tests passing
against a different reactor state.

Controls: build from the current Maven reactor source, prove V036/new runtime
classes are loaded before E2E, run targeted module tests then full wrapper
`verify`, and bind evidence to the exact Git SHA.

### Cross-repository dependencies

Failure modes: UI E2E starts an older backend; UI redefines backend semantics;
frontend evidence is bound to a different backend commit.

Controls: backend owns API/OpenAPI/database truth; UI E2E uses
`PAYROLL_BACKEND_REPOSITORY_PATH`; record the exact backend SHA in cross-repo
evidence and freeze backend contract before publishing the UI increment.

### Hosted-CI ordering

Failure modes: web CI bound to backend `main` fails before backend product merge;
check-registration lag is misclassified as failure; Maven Central 429 is treated
as a product defect.

Controls: backend product PR goes green and merges first; then rerun web CI
against advanced backend `main`; poll boundedly for check registration; classify
registration lag and external artifact 429 separately from product defects.

## 7. Verification and closure gates

Before product publication the capability must prove:

1. exact-base and exact-allow-list preflight;
2. V036 clean install and populated upgrade with V001-V035 unchanged;
3. tenant/RLS and immutable-history negative paths;
4. deterministic configuration snapshot identity/hash and drift rejection;
5. calculation binding to exact configuration snapshot lineage;
6. composed readiness blockers/warnings without global-readiness overclaim;
7. deterministic fixture reset/resume/replay;
8. targeted Maven tests and full `mvnw verify`;
9. OpenAPI/Keycloak/auth-smoke validation where changed;
10. backend-hosted CI green before web-hosted CI is treated as authoritative;
11. browser E2E against the exact merged backend main;
12. independent R3 critical review before publication;
13. post-merge semantic story reconciliation and status-closure PR.

Activation alone changes no story status. V036 is released only by the final
status-closure authority after P5-FSR-01 product merge and reconciliation.
