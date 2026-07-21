# Durable OWASP Dependency-Check Data Service

**Status:** Deferred  
**Priority:** Security/platform backlog  
**Revisit:** After Sprint 2 payroll-configuration scope  
**Current PR control:** GitHub Dependency Review, Dependabot, npm audit and CycloneDX SBOM

## Context

OWASP Dependency-Check was removed from pull-request CI because the initial NVD bootstrap downloads hundreds of thousands of records and does not provide a reliable restart checkpoint for an interrupted first bootstrap.

The per-commit implementation also made ordinary pull-request validation dependent on:

- NVD service availability;
- API-key validity and activation;
- long bootstrap downloads;
- ephemeral GitHub-hosted runners;
- cache completion after the scan.

This item is intentionally deferred, not abandoned.

## Target architecture

Implement Dependency-Check using durable shared vulnerability data rather than downloading NVD data in every build.

Evaluate and record an architecture decision between:

1. **Central PostgreSQL Dependency-Check database**
   - one scheduled updater with write access;
   - persistent storage and backups;
   - CI scanners use read-only database credentials.

2. **Internal NVD mirror**
   - one scheduled mirror/update process;
   - persistent object or file storage;
   - Dependency-Check scanners consume the internal feed.

In either design:

- only the updater owns the NVD API key;
- PR workflows never call NVD directly;
- scanner jobs run read-only/offline against durable data;
- updater progress survives process and runner restarts;
- dataset freshness is monitored and enforced;
- JSON and HTML reports are retained;
- the agreed CVSS threshold remains a blocking gate;
- GitHub Dependency Review remains the fast change-level PR control.

## Acceptance criteria

- [ ] Architecture decision recorded: PostgreSQL data service versus NVD mirror.
- [ ] Initial bootstrap runs outside pull-request CI.
- [ ] Incremental updater is scheduled, idempotent and restart-safe.
- [ ] Persistent data survives runner and process termination.
- [ ] CI does not download the complete NVD dataset.
- [ ] CI uses read-only/offline access to the durable dataset.
- [ ] NVD credentials are available only to the updater.
- [ ] Dataset age and last successful update are observable.
- [ ] Stale-data threshold and alerting are defined.
- [ ] Dependency-Check JSON and HTML reports are uploaded.
- [ ] Builds fail at the agreed severity threshold without false-green handling.
- [ ] Runbook covers bootstrap, updates, recovery, backup and key rotation.
- [ ] Cost, maintenance and security ownership are assigned.

## Non-goals for Sprint 2

- Reintroducing NVD downloads into pull-request workflows.
- Treating an incomplete H2 directory as a resumable checkpoint.
- Making feature delivery depend on a fragile external-data bootstrap.
- Removing GitHub Dependency Review after OWASP is introduced.

## Implementation notes

Prefer a separate scheduled workflow or dedicated service for updates. The updater and scanners must be independently testable. Before implementation, verify the chosen Dependency-Check version, database schema compatibility and supported centralized-data configuration.
