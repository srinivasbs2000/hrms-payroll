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

$values = Read-DotEnv -Path $environmentPath
Set-E2eProcessEnvironment `
    -Values $values `
    -PostgresPort $PostgresPort `
    -KeycloakPort $KeycloakPort

$composeArguments = Get-E2eComposeArguments `
    -EnvironmentFile $environmentPath `
    -ComposeFile (Join-Path $RepositoryPath 'deploy/e2e/compose.yaml')

Wait-ForPostgres `
    -RepositoryPath $RepositoryPath `
    -ComposeArguments $composeArguments
Wait-ForKeycloak -Port $KeycloakPort

Invoke-PostgresFile `
    -RepositoryPath $RepositoryPath `
    -ComposeArguments $composeArguments `
    -SqlPath (
        Join-Path $RepositoryPath `
            'database/flyway/e2e/verify_smoke_fixture.sql'
    )

Write-Host 'Payroll E2E fixture verification passed.' -ForegroundColor Green
