# S3-09 Durable Smoke Data and Browser Automation Strategy

## Decision

Smoke data and end-to-end browser automation are permanent engineering
capabilities, not Sprint 3 cleanup work.

Every future sprint must:

1. extend the synthetic fixture when a new executable business path needs data;
2. add or update Playwright assertions for the new behavior;
3. keep the complete historical E2E suite green; and
4. publish failure evidence without publishing credentials or tokens.

## Environment isolation

S3-09 uses the Compose project `hrms-payroll-e2e` and a dedicated PostgreSQL
volume. The reset operation never truncates or reuses the developer's normal
PostgreSQL volume.

The E2E stack uses:

- PostgreSQL 17 on `127.0.0.1:25432`;
- Keycloak 26.7.0 on `http://localhost:8081`;
- backend on `http://localhost:8080`; and
- frontend on `http://localhost:5173`.

The standard Keycloak and application ports are retained because the realm
redirect URI, browser client and backend issuer are deliberately exact.

## Fixture layering

The reset order is:

1. administrator bootstrap;
2. immutable Flyway migrations V001–V026;
3. ordered files under `database/flyway/e2e/fixtures`;
4. fixture verification.

The repository documentation previously referenced a development seed, but no
standalone seed file exists at the reviewed Sprint 3 head. S3-09 therefore owns
an explicit executable fixture rather than relying on an undocumented record.

E2E fixtures remain outside Flyway and must not use `V` migration prefixes.

## Data principles

- synthetic data only;
- deterministic codes and UUIDs;
- one primary executable tenant matching the Keycloak tenant claim;
- at least one included and one excluded payroll worker;
- no real names, salaries, credentials or personal information;
- no bypass of business validation merely to load data;
- no weakening of RLS, immutability or least privilege;
- reset from a fresh E2E volume rather than attempting fragile in-place cleanup.

## Automation layers

### S3-09A

- isolated environment;
- deterministic reset;
- base seed discovery;
- future-sprint overlay mechanism;
- minimum fixture verification.

### S3-09B

- Playwright 1.61.1;
- real Keycloak browser login;
- administrator and read-only projects;
- payroll cycle, population, sealing, calculation and draft-payslip checks;
- recalculation, refresh and stale-version checks;
- token-storage and unexpected-response assertions.

### S3-09C

- GitHub Actions browser job;
- one worker in CI;
- Chromium installation with dependencies;
- failure-only sanitized HTML summary, traces, screenshots and service logs;
- no authentication state or raw token artifact upload.

## Future sprint entry and exit gates

A sprint may start implementation with the existing fixture. A sprint cannot
close when its new end-to-end behavior lacks:

- deterministic synthetic inputs;
- a repeatable reset path;
- browser/API assertions;
- negative-path coverage where applicable; and
- green historical E2E regression.

This contract continues beyond Sprint 3.

## S3-09B local browser suite

The browser suite uses Playwright Test with real Keycloak redirects and two
separate authenticated browser states:

- `payroll.admin` for the controlled payroll lifecycle;
- `payroll.smoke` for read-only and direct-write denial checks.

Authentication state is written only under ignored
`frontend/payroll-web/e2e/.auth`. Tests must never print an access token,
copy it to a Node process or upload the authentication state as an artifact.

The administrator project creates and executes the July 2026 fixture cycle
through the UI. The read-only project depends on that completed project and
verifies the persisted evidence without exposing write controls.

Every future sprint must add browser assertions to this suite when it adds a
user-visible executable path.

## S3-09C CI enforcement

The existing `payroll-baseline` workflow owns a required browser job named
`Payroll browser E2E`. It executes on every pull request and on pushes to
`main`; no path filter may silently skip historical payroll browser coverage.

The job:

1. installs Java 21 and Node 24.14;
2. installs backend modules from a clean checkout;
3. installs Playwright's pinned Chromium and Linux dependencies;
4. recreates and verifies the isolated `hrms-payroll-e2e` fixture;
5. runs the complete administrator and read-only browser suite with one worker;
6. prepares failure evidence only when the job fails; and
7. always removes the isolated Compose project and volume.

Because the administrator test mutates a deterministic cycle, CI retries remain
disabled. A retry without a fixture reset would execute against already-mutated
payroll state and could hide the original failure.

Raw Playwright traces are not CI artifacts. The artifact preparation script
removes network recordings, excludes `.auth`, creates sanitized trace archives,
and scans the staged artifact for token-shaped content before upload.

After the first successful `Payroll browser E2E` check exists, repository
administration must add that exact check name to the protected `main` branch.

## Scoped React Router npm audit policy

The frontend remains on exact `react-router-dom` and `react-router` version
7.18.1. The npm advisory `GHSA-qwww-vcr4-c8h2` applies to unstable React
Server Components mode. This payroll frontend uses declarative
`BrowserRouter` mode and does not install React Router Framework, Data Router,
server-rendering, or RSC packages.

`frontend/payroll-web/scripts/verify-npm-audit.mjs` executes npm audit and
permits only advisory source `1124282` at the exact GitHub advisory URL. The
policy fails closed when:

- any additional high or critical advisory appears;
- the advisory source, URL, dependency, severity, or affected range changes;
- `react-router` becomes a direct dependency;
- the direct/inherited package relationship changes;
- Router Framework, Data Router, server-rendering, or RSC markers appear;
- the locked router versions change from 7.18.1; or
- the review deadline of 31 October 2026 is reached.

This is not a blanket npm audit suppression. Both the local Sprint 3
regression and GitHub Actions run the same policy script. The exception must be
removed when a compatible patched release is adopted.
