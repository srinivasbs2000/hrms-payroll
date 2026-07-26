[CmdletBinding()]
param(
  [string]$RepositoryPath = 'C:\dev\hrms-payroll',
  [string]$EvidenceDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
  $EvidenceDirectory = Join-Path $RepositoryPath 'target\local-smoke-evidence'
}
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null

$ComposeFile = Join-Path $RepositoryPath 'deploy\local\compose.yaml'
$EnvFile = Join-Path $RepositoryPath 'deploy\local\.env'
$Fixture = Join-Path $RepositoryPath 'deploy\local\smoke\sprint-4-local-state.sql'
$Verification = Join-Path $RepositoryPath 'deploy\local\smoke\verify-sprint-4-local-state.sql'
$FlywayLog = Join-Path $EvidenceDirectory '02A-flyway-parity.log'
$ApplyLog = Join-Path $EvidenceDirectory '02A-balance-year-apply.log'
$VerifyLog = Join-Path $EvidenceDirectory '02A-balance-year-verify.log'

foreach ($required in @($ComposeFile, $EnvFile, $Fixture, $Verification)) {
  if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
    throw "Required file not found: $required"
  }
}

Push-Location $RepositoryPath
try {
  $composeStatus = & docker compose --env-file $EnvFile -f $ComposeFile ps 2>&1
  $composeStatus | Out-File -LiteralPath (
    Join-Path $EvidenceDirectory '02A-compose-status.log'
  ) -Encoding utf8
  if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose status check failed.'
  }

  # Flyway ownership/history belongs to the explicit migration role, not payroll_app.
  $flywaySql = @'
\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
BEGIN TRANSACTION READ ONLY;
SELECT 'flyway_latest_successful_version=' || (
  SELECT version
  FROM public.flyway_schema_history
  WHERE success
  ORDER BY installed_rank DESC
  LIMIT 1
);
SELECT 'flyway_failed_migrations=' || (
  SELECT count(*)
  FROM public.flyway_schema_history
  WHERE NOT success
);
ROLLBACK;
'@

  $flywayOutput = $flywaySql |
    & docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
      bash -lc 'PGPASSWORD="$PAYROLL_MIGRATOR_PASSWORD" psql -h 127.0.0.1 -U payroll_migrator -d payroll -v ON_ERROR_STOP=1' 2>&1
  $flywayExit = $LASTEXITCODE
  $flywayOutput | Out-File -LiteralPath $FlywayLog -Encoding utf8
  if ($flywayExit -ne 0) {
    throw "Flyway parity verification failed. See $FlywayLog"
  }

  $flywayText = ($flywayOutput | Out-String)
  foreach ($requiredLine in @(
    'flyway_latest_successful_version=030',
    'flyway_failed_migrations=0'
  )) {
    if ($flywayText -notmatch [regex]::Escape($requiredLine)) {
      throw "Flyway parity output is missing '$requiredLine'. See $FlywayLog"
    }
  }

  # Business fixture runs with the same least-privileged role as application traffic.
  $fixtureSql = Get-Content -Raw -LiteralPath $Fixture
  $fixtureOutput = $fixtureSql |
    & docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
      bash -lc 'PGPASSWORD="$PAYROLL_APP_PASSWORD" psql -h 127.0.0.1 -U payroll_app -d payroll -v ON_ERROR_STOP=1' 2>&1
  $fixtureExit = $LASTEXITCODE
  $fixtureOutput | Out-File -LiteralPath $ApplyLog -Encoding utf8
  if ($fixtureExit -ne 0) {
    throw "Repository-owned balance-year fixture failed. See $ApplyLog"
  }

  # Business parity is verified under payroll_app and contains no Flyway-table access.
  $verificationSql = Get-Content -Raw -LiteralPath $Verification
  $verificationOutput = $verificationSql |
    & docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
      bash -lc 'PGPASSWORD="$PAYROLL_APP_PASSWORD" psql -h 127.0.0.1 -U payroll_app -d payroll -v ON_ERROR_STOP=1' 2>&1
  $verificationExit = $LASTEXITCODE
  $verificationOutput | Out-File -LiteralPath $VerifyLog -Encoding utf8
  if ($verificationExit -ne 0) {
    throw "Read-only balance-year verification failed. See $VerifyLog"
  }

  $verifyText = ($verificationOutput | Out-String)
  foreach ($requiredLine in @(
    'approved_current_balance_years=1',
    'statutory_result_balance_year_violations=0',
    'calculated_cycles=1',
    'completed_calculation_requests=1',
    'completed_statutory_evaluations=1'
  )) {
    if ($verifyText -notmatch [regex]::Escape($requiredLine)) {
      throw "Business parity output is missing '$requiredLine'. See $VerifyLog"
    }
  }

  Write-Host 'Flyway V030 parity: PASS (payroll_migrator)' -ForegroundColor Green
  Write-Host 'Repository-owned Sprint 4 local state: PASS (payroll_app)' -ForegroundColor Green
  Write-Host "Flyway evidence: $FlywayLog"
  Write-Host "Business evidence: $VerifyLog"
}
finally {
  Pop-Location
}
