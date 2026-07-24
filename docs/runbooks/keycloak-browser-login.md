# Keycloak Browser Login

## Purpose

The payroll web application uses the official Keycloak JavaScript adapter and
the Authorization Code flow with PKCE. Tokens are held in browser memory only.

## Local endpoints

- Payroll UI: `http://localhost:5173`
- Keycloak: `http://localhost:8081`
- Keycloak administration: `http://localhost:8081/admin/`

The default browser-client configuration can be overridden at Vite startup:

```powershell
$env:VITE_KEYCLOAK_URL = 'http://localhost:8081'
$env:VITE_KEYCLOAK_REALM = 'payroll'
$env:VITE_KEYCLOAK_CLIENT_ID = 'payroll-web'
npm run dev
```

## Full-access smoke user

Use `payroll.admin` for the complete Sprint 3 browser smoke. On a fresh realm
import, its seed password is temporary. Keycloak will require the password to
be changed during the first browser login.

The seed value is development-only and is defined in
`deploy/local/keycloak/payroll-realm.json`. Do not reuse it outside the local
environment.

If the password was previously changed and is no longer known:

1. Open the Keycloak administration console.
2. Sign in using `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD` from the ignored
   `deploy/local/.env`.
3. Select realm `payroll`.
4. Open **Users**, select `payroll.admin`, then **Credentials**.
5. Set a new local password and choose whether it is temporary.
6. Return to the payroll UI and use **Sign in with Keycloak**.

## Read-only user

`payroll.smoke` is intentionally read-only. It can inspect configured data and
persisted results, but cannot create cycles, resolve population, seal inputs,
calculate or recalculate payroll.

## Expected browser behavior

1. An unauthenticated visit displays a branded sign-in screen.
2. **Sign in with Keycloak** redirects to the payroll realm.
3. Successful login returns to the original payroll URL.
4. Navigation contains only modules allowed by the token's `permissions` claim.
5. The header displays the username, tenant and a **Sign out** action.
6. Token refresh updates the in-memory API session.
7. Browser refresh restores the session through Keycloak SSO.
8. Sign out clears the local in-memory session and ends the Keycloak session.

No access token or refresh token is written to local storage or session
storage.

## Troubleshooting

### Authentication unavailable

Confirm the Keycloak container is running:

```powershell
docker compose `
  --env-file deploy/local/.env `
  -f deploy/local/compose.yaml `
  ps keycloak
```

### Invalid redirect URI

The local realm client must include:

- redirect URI `http://localhost:5173/*`
- web origin `http://localhost:5173`

### Backend returns 401 after login

The local frontend and backend both default to the canonical issuer:

`http://localhost:8081/realms/payroll`

JWT issuer comparison is exact. A token issued through `127.0.0.1` will not
match a backend configured for `localhost`, even though both names reach the
same machine.

When overriding the Keycloak host, set both sides to the same exact value
before startup:

```powershell
$env:VITE_KEYCLOAK_URL = 'http://localhost:8081'
$env:OIDC_ISSUER = 'http://localhost:8081/realms/payroll'
```

After changing either value, restart the backend and frontend, sign out of the
old Keycloak session, and sign in again so the new token contains the canonical
issuer.
