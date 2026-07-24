#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryPath = 'C:\dev\hrms-payroll',
    [string]$EnvironmentFile = 'deploy/local/.env',
    [switch]$KeepDatabase
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

$composeArguments = Get-E2eComposeArguments `
    -EnvironmentFile $environmentPath `
    -ComposeFile (Join-Path $RepositoryPath 'deploy/e2e/compose.yaml')

$arguments = $composeArguments + @('down','--remove-orphans')
if (-not $KeepDatabase) {
    $arguments += '--volumes'
}

Invoke-Native `
    -Command 'docker' `
    -Arguments $arguments `
    -WorkingDirectory $RepositoryPath

Write-Host 'Payroll E2E services stopped.' -ForegroundColor Green
if ($KeepDatabase) {
    Write-Host 'The isolated E2E database volume was preserved.'
}
else {
    Write-Host 'The isolated E2E database volume was removed.'
}
