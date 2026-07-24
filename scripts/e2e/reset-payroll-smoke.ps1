#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryPath = 'C:\dev\hrms-payroll',
    [string]$EnvironmentFile = 'deploy/local/.env',
    [int]$PostgresPort = 25432,
    [int]$KeycloakPort = 8081
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepositoryPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
. (Join-Path $PSScriptRoot 'common.ps1')

$environmentPath = if ([IO.Path]::IsPathRooted($EnvironmentFile)) {
    $EnvironmentFile
}
else {
    Join-Path $RepositoryPath $EnvironmentFile
}

$composeFile = Join-Path $RepositoryPath 'deploy/e2e/compose.yaml'
$composeArguments = Get-E2eComposeArguments `
    -EnvironmentFile $environmentPath `
    -ComposeFile $composeFile

$values = Read-DotEnv -Path $environmentPath
Set-E2eProcessEnvironment `
    -Values $values `
    -PostgresPort $PostgresPort `
    -KeycloakPort $KeycloakPort

Write-Host 'Resetting isolated payroll E2E environment...' -ForegroundColor Cyan
Invoke-Native -Command 'docker' `
    -Arguments ($composeArguments + @(
        'down'
        '--volumes'
        '--remove-orphans'
    )) `
    -WorkingDirectory $RepositoryPath

Invoke-Native -Command 'docker' `
    -Arguments ($composeArguments + @('up','-d')) `
    -WorkingDirectory $RepositoryPath

Wait-ForPostgres `
    -RepositoryPath $RepositoryPath `
    -ComposeArguments $composeArguments
Wait-ForKeycloak -Port $KeycloakPort

$adminPassword = if (
    [string]::IsNullOrWhiteSpace($env:E2E_PAYROLL_ADMIN_PASSWORD)
) {
    'change-me'
}
else {
    $env:E2E_PAYROLL_ADMIN_PASSWORD
}

$smokePassword = if (
    [string]::IsNullOrWhiteSpace($env:E2E_PAYROLL_SMOKE_PASSWORD)
) {
    'change-me'
}
else {
    $env:E2E_PAYROLL_SMOKE_PASSWORD
}

Write-Host 'Preparing deterministic Keycloak E2E users...' `
    -ForegroundColor Cyan
Set-E2eKeycloakPasswords `
    -RepositoryPath $RepositoryPath `
    -ComposeArguments $composeArguments `
    -BootstrapUsername ([string]$values.KEYCLOAK_ADMIN) `
    -BootstrapPassword ([string]$values.KEYCLOAK_ADMIN_PASSWORD) `
    -AdminPassword $adminPassword `
    -SmokePassword $smokePassword

$previousJavaToolOptions = [string]$env:JAVA_TOOL_OPTIONS
$previousFlywayUrl = [string]$env:FLYWAY_URL
$previousFlywayUser = [string]$env:FLYWAY_USER
$previousFlywayPassword = [string]$env:FLYWAY_PASSWORD

try {
    $cleanJavaOptions = (
        $previousJavaToolOptions `
            -replace '(?i)(^|\s)-Duser\.timezone=(?:"[^"]*"|\S+)', ' '
    ).Trim()
    $env:JAVA_TOOL_OPTIONS = (
        "$cleanJavaOptions -Duser.timezone=Asia/Kolkata"
    ).Trim()

    $env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$PostgresPort/payroll"
    $env:FLYWAY_USER = 'payroll_migrator'
    $env:FLYWAY_PASSWORD = [string]$values.PAYROLL_MIGRATOR_PASSWORD

    Invoke-Native `
        -Command (Join-Path $RepositoryPath 'mvnw.cmd') `
        -Arguments @(
            '--batch-mode'
            '-pl'
            'backend/database-migrations'
            'flyway:migrate'
            'flyway:validate'
        ) `
        -WorkingDirectory $RepositoryPath

    $overlayRoot = Join-Path $RepositoryPath 'database/flyway/e2e/fixtures'
    $overlays = @(
        Get-ChildItem -LiteralPath $overlayRoot -File -Filter '*.sql' |
            Sort-Object Name
    )

    foreach ($overlay in $overlays) {
        Write-Host "Applying E2E overlay: $($overlay.Name)" `
            -ForegroundColor Cyan
        Invoke-PostgresFile `
            -RepositoryPath $RepositoryPath `
            -ComposeArguments $composeArguments `
            -SqlPath $overlay.FullName
    }

    Invoke-PostgresFile `
        -RepositoryPath $RepositoryPath `
        -ComposeArguments $composeArguments `
        -SqlPath (
            Join-Path $RepositoryPath `
                'database/flyway/e2e/verify_smoke_fixture.sql'
        )
}
finally {
    if ([string]::IsNullOrEmpty($previousJavaToolOptions)) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    }
    else {
        $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    }

    if ([string]::IsNullOrEmpty($previousFlywayUrl)) {
        Remove-Item Env:FLYWAY_URL -ErrorAction SilentlyContinue
    }
    else {
        $env:FLYWAY_URL = $previousFlywayUrl
    }

    if ([string]::IsNullOrEmpty($previousFlywayUser)) {
        Remove-Item Env:FLYWAY_USER -ErrorAction SilentlyContinue
    }
    else {
        $env:FLYWAY_USER = $previousFlywayUser
    }

    if ([string]::IsNullOrEmpty($previousFlywayPassword)) {
        Remove-Item Env:FLYWAY_PASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:FLYWAY_PASSWORD = $previousFlywayPassword
    }
}

Write-Host ''
Write-Host 'Isolated payroll E2E fixture reset completed.' -ForegroundColor Green
Write-Host "PostgreSQL: 127.0.0.1:$PostgresPort"
Write-Host "Keycloak: http://localhost:$KeycloakPort"
Write-Host 'Tenant fixture: DEMO001'
Write-Host 'Normal local PostgreSQL volume was not used or modified.'
