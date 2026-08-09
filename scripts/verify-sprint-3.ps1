#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryPath = 'C:\dev\hrms-payroll',
    [string]$FrontendRepositoryPath = 'C:\dev\hrms-payroll-web'
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
        Write-Host ('> ' + $Command + ' ' + ($Arguments -join ' ')) `
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
if (
    -not (
        Test-Path `
            -LiteralPath $FrontendRepositoryPath `
            -PathType Container
    )
) {
    throw "Frontend repository path does not exist: $FrontendRepositoryPath"
}

$RepositoryPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
$frontend = (
    Resolve-Path -LiteralPath $FrontendRepositoryPath
).Path
$maven = Join-Path $RepositoryPath 'mvnw.cmd'
$openApi = 'contracts/openapi/payroll-vertical-slice-openapi-v1.yaml'

if (-not (Test-Path -LiteralPath (Join-Path $frontend 'package.json'))) {
    throw "Frontend package.json does not exist: $frontend"
}

Invoke-Checked -Command 'npm.cmd' -Arguments @('ci') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' -Arguments @('run', 'lint') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' -Arguments @('test') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'npm.cmd' -Arguments @('run', 'build') `
    -WorkingDirectory $frontend
Invoke-Checked -Command 'node.exe' `
    -Arguments @('scripts/verify-npm-audit.mjs') `
    -WorkingDirectory $frontend

Invoke-Checked -Command $maven -Arguments @('--batch-mode', 'verify') `
    -WorkingDirectory $RepositoryPath

Invoke-Checked -Command 'npx.cmd' -Arguments @(
    '--yes',
    '--package=@redocly/cli@2.39.0',
    'redocly',
    'lint',
    $openApi
) -WorkingDirectory $RepositoryPath

Invoke-Checked -Command 'git.exe' -Arguments @('diff', '--check') `
    -WorkingDirectory $RepositoryPath

Write-Host ''
Write-Host 'Sprint 3 cross-repository regression passed.' -ForegroundColor Green
Write-Host 'Backend repository status:'
& git.exe -C $RepositoryPath status --short --untracked-files=all
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read backend Git status.'
}
Write-Host 'Frontend repository status:'
& git.exe -C $frontend status --short --untracked-files=all
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read frontend Git status.'
}
