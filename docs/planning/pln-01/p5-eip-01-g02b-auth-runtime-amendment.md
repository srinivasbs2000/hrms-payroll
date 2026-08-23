# P5-EIP-01 G02B — Bounded Auth Runtime Amendment

**Authority type:** bounded G02B UI product-path amendment
**Backend/program authority base:** `7ade2c199c0eca1351e8907a6e43fbfe8b567b7a`
**UI authority base:** `9dbf0d2f700764e2fe577f89142cd6784028f70c`
**Preserved local G02B worktree:** `C:\dev\hrms-payroll-web-worktrees\p5-eip-01-g02b`
**Preserved local G02B branch:** `feature/p5-eip-01-g02b-identity-payment-ui`
**Canonical story status:** unchanged
**G02C status:** not started

## 1. Trigger evidence

G02B source validation is green for lint, focused tests, full UI regression and
production build. Isolated PostgreSQL/Keycloak reset through V051 is green.
Real-backend browser E2E then proved a bounded auth-runtime mismatch:

- the P5-EIP isolated Keycloak is intentionally started on a free runtime port;
- the existing shared auth test helper is hard-coded to localhost:8081;
- the frontend Keycloak configuration reads Vite environment metadata through an
  indirect bare-import.meta alias and falls back to localhost:8081;
- the backend is issuer-bound to the isolated Keycloak runtime port; and
- the first authenticated payroll-relationship API request consequently returns
  HTTP 401 when the browser token issuer and backend issuer differ.

This is a configuration-path / E2E authority gap. It does not authorize a
security weakening, issuer bypass, ambient-Keycloak dependency or bearer-token
injection workaround.

## 2. Exact additional UI write authority

After this amendment PR merges, P5-EIP-01 G02B additionally owns only:

- `src/auth/keycloak-client.ts`;
- `src/auth/keycloak-client.test.ts`.

The original G02B UI authority remains unchanged:

- `src/features/employee-payroll/**`;
- `e2e/p5-eip-01*.ts`.

No other `src/auth/**`, `e2e/support/**`, `vite.config.ts`, shared Playwright
configuration, backend product path, migration, OpenAPI contract, Keycloak realm
file or governance scope is added to product ownership.

## 3. Permitted correction semantics

The two additional auth files may only:

1. make `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM` and
   `VITE_KEYCLOAK_CLIENT_ID` readable through a Vite-recognizable direct
   `import.meta.env` access while preserving existing defaults;
2. add unit evidence proving default configuration remains unchanged and a
   supplied Vite Keycloak URL is honored.

Under the already-authorized `e2e/p5-eip-01*.ts` boundary, G02B may replace
the shared hard-coded admin setup dependency with a P5-EIP-specific login journey
that uses the runtime-selected Keycloak/UI ports.

## 4. Explicit prohibitions

This amendment does not authorize:

- accepting tokens from multiple issuers;
- disabling issuer, signature, audience or permission validation;
- use of the ambient service currently occupying port 8081;
- stopping or modifying that ambient service;
- modifying `e2e/support/auth.ts` or `vite.config.ts`;
- static secrets or persisted browser bearer tokens;
- backend/database/API/migration changes;
- a new migration after V051;
- story-status closure or G02C work;
- force push, rebase or duplicate G02B product PR.

## 5. Expiry and closure

This authority becomes effective only after this governance PR is hosted-green
and merged. It expires when the P5-EIP-01 G02B UI product increment is
hosted-green and merged, or earlier if G02B is abandoned. Any further
cross-boundary file requirement needs a new bounded amendment.
