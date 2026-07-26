#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryPath = 'C:\dev\hrms-payroll'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$nativePreference = Get-Variable `
    -Name PSNativeCommandUseErrorActionPreference `
    -ErrorAction SilentlyContinue
if ($null -ne $nativePreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        Write-Host ''
        Write-Host ("> " + $Command + " " + ($Arguments -join ' ')) `
            -ForegroundColor Cyan
        & $Command @Arguments
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "$Command failed with exit code $exitCode."
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $RepositoryPath -PathType Container)) {
    throw "Repository path does not exist: $RepositoryPath"
}

$frontend = Join-Path $RepositoryPath 'frontend\payroll-web'
$maven = Join-Path $RepositoryPath 'mvnw.cmd'
$aggregateOpenApi = 'contracts/openapi/payroll-vertical-slice-openapi-v1.yaml'
$statutoryFragment = 'contracts/openapi/statutory-deductions-openapi-v1.yaml'

Invoke-Checked -Command 'npm.cmd' `
    -Arguments @('ci', '--ignore-scripts') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'node.exe' `
    -Arguments @('scripts/verify-npm-audit.mjs', '--self-test') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'node.exe' `
    -Arguments @('scripts/verify-npm-audit.mjs') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' `
    -Arguments @('run', 'lint') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' `
    -Arguments @('test') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' `
    -Arguments @('run', 'build') `
    -WorkingDirectory $frontend

Invoke-Checked -Command $maven `
    -Arguments @('--batch-mode', 'verify') `
    -WorkingDirectory $RepositoryPath

$aggregatePath = Join-Path $RepositoryPath $aggregateOpenApi
$fragmentPath = Join-Path $RepositoryPath $statutoryFragment

if (-not (Test-Path -LiteralPath $fragmentPath -PathType Leaf)) {
    throw "Statutory OpenAPI fragment does not exist: $statutoryFragment"
}

$aggregateText = Get-Content -Raw -LiteralPath $aggregatePath
$statutoryReference = './statutory-deductions-openapi-v1.yaml#'
if (-not $aggregateText.Contains($statutoryReference)) {
    throw (
        'Aggregate OpenAPI does not reference the statutory fragment: ' +
        $statutoryReference
    )
}

Invoke-Checked -Command 'npx.cmd' -Arguments @(
    '--yes',
    '--package=@redocly/cli@2.39.0',
    'redocly',
    'lint',
    $aggregateOpenApi
) -WorkingDirectory $RepositoryPath

Invoke-Checked -Command 'git.exe' `
    -Arguments @('diff', '--check') `
    -WorkingDirectory $RepositoryPath

Write-Host ''
Write-Host 'Sprint 4 full regression passed.' -ForegroundColor Green
Write-Host 'Current repository status:'
& git.exe -C $RepositoryPath status --short --untracked-files=all
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read Git status.'
}
