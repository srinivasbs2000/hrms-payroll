# S3-09B Playwright Acceptance Contract

## Environment

- isolated PostgreSQL fixture on `127.0.0.1:25432`;
- canonical Keycloak issuer on `http://localhost:8081`;
- backend started against the isolated database;
- frontend on `http://localhost:5173`;
- Chromium managed by Playwright 1.61.1.

## Authentication

The suite authenticates through the rendered Keycloak login form. It does not
inject JWTs, call direct-access grants or persist tokens outside the browser
context.

Required assertions:

- unauthenticated application shows `Sign in with Keycloak`;
- administrator and read-only login complete through Keycloak;
- authenticated username and tenant are visible;
- refresh restores the session;
- local storage and session storage do not contain access or refresh tokens;
- logout returns to the login screen.

## Administrator lifecycle

Using deterministic fixture identifiers:

- pay-group version `45100000-0000-0000-0000-000000000001`;
- pay period `44100000-0000-0000-0000-000000000001`.

The browser must:

1. create a regular payroll cycle;
2. resolve population with one included and one excluded worker;
3. display `E2E-EMP-001` as the active population;
4. seal exactly one immutable input snapshot;
5. calculate one result with gross/net INR 90,000 and zero deductions;
6. open the persisted draft payslip;
7. verify the draft legal-status banner and `E2E_BASIC` component;
8. execute recalculation attempt 2 with a reason;
9. preserve both calculation attempts after refresh; and
10. reject a stale second-page recalculation with HTTP 409.

## Read-only behavior

`payroll.smoke` must:

- see payroll cycles and persisted results;
- see no create, resolve, seal, calculate or recalculate controls;
- receive HTTP 403 for a direct write attempt using its own in-memory token;
- retain access after refresh.

## Failure policy

The suite fails for:

- unexpected protected API 401;
- unexpected protected API 403;
- any API 5xx;
- browser page error;
- unhandled console error;
- missing expected persisted evidence.

Traces, screenshots and video are retained only for failed tests. Saved
authentication state is always excluded from Git and CI artifacts.
