# Local development infrastructure

From the repository root:

```powershell
Copy-Item deploy/local/.env.example deploy/local/.env
# Replace every placeholder value in deploy/local/.env.
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
```

PostgreSQL 17 mounts the canonical administrator bootstrap from `database/flyway/bootstrap`. Apply V001-V013 through the Maven `database-migrations` module as documented in the root README. PostgreSQL is bound to `127.0.0.1:15432` by default and Keycloak to `127.0.0.1:8081`. Keycloak imports a development realm with a public SPA client, a synthetic temporary-password administrator, and a non-production smoke principal. Access tokens contain the `tenant_id`, `payroll-api` audience, realm roles and permissions required by the resource server.

With the backend running locally and `BASELINE_AUTH_SMOKE_ENABLED=true`, execute:

```powershell
.\deploy\local\smoke\auth-smoke.ps1
```

The script obtains a real development-realm token in memory, validates its issuer, audience, tenant, roles and permissions, then calls the permission-protected baseline endpoint. It prints only a sanitized claim/result summary and never prints or persists the raw token.

Everything in this directory is local-development configuration. The ignored `.env` supplies database, administrator and smoke-test credentials; do not reuse its placeholders outside a developer workstation. Production Spring configuration has no fallback credentials and forces the smoke endpoint off.
