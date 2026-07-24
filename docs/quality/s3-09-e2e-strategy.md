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
- failure-only HTML report, trace, screenshots and service logs;
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
`frontend/payroll-web/playwright/.auth`. Tests must never print an access token,
copy it to a Node process or upload the authentication state as an artifact.

The administrator project creates and executes the July 2026 fixture cycle
through the UI. The read-only project depends on that completed project and
verifies the persisted evidence without exposing write controls.

Every future sprint must add browser assertions to this suite when it adds a
user-visible executable path.
