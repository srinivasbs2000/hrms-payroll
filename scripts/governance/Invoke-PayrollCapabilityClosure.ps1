param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$ManifestPath,

    [Alias("RepoRoot")]
    [ValidateNotNullOrEmpty()]
    [string]$RepositoryPath = "C:\dev\hrms-payroll",

    [switch]$PreflightOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ResolvedRepositoryPath = (Resolve-Path -LiteralPath $RepositoryPath).Path
$ResolvedManifestPath = (Resolve-Path -LiteralPath $ManifestPath).Path
$ValidatorPath = Join-Path $ResolvedRepositoryPath "scripts\Test-PowerShellScript.ps1"
$EnginePath = Join-Path $ResolvedRepositoryPath "scripts\governance\payroll-capability-closure.mjs"

if (-not (Test-Path -LiteralPath $ValidatorPath -PathType Leaf)) {
    throw ("PowerShell parser validator not found: {0}" -f $ValidatorPath)
}
if (-not (Test-Path -LiteralPath $EnginePath -PathType Leaf)) {
    throw ("Capability closure engine not found: {0}" -f $EnginePath)
}

# Validate the exact repository-owned launcher before invoking the engine.
& $ValidatorPath $PSCommandPath

Write-Host ("Payroll closure repository: {0}" -f $ResolvedRepositoryPath)
Write-Host ("Payroll closure manifest: {0}" -f $ResolvedManifestPath)
Write-Host ("PowerShell: {0}" -f $PSVersionTable.PSVersion)

$ProcessInfo = [System.Diagnostics.ProcessStartInfo]::new()
$ProcessInfo.FileName = "node"
$ProcessInfo.UseShellExecute = $false
$ProcessInfo.RedirectStandardOutput = $false
$ProcessInfo.RedirectStandardError = $false
$ProcessInfo.WorkingDirectory = $ResolvedRepositoryPath
[void]$ProcessInfo.ArgumentList.Add($EnginePath)
[void]$ProcessInfo.ArgumentList.Add("--repo-root")
[void]$ProcessInfo.ArgumentList.Add($ResolvedRepositoryPath)
[void]$ProcessInfo.ArgumentList.Add("--manifest")
[void]$ProcessInfo.ArgumentList.Add($ResolvedManifestPath)
if ($PreflightOnly) {
    [void]$ProcessInfo.ArgumentList.Add("--preflight-only")
}

$Process = [System.Diagnostics.Process]::new()
$Process.StartInfo = $ProcessInfo
try {
    if (-not $Process.Start()) {
        throw "Unable to start Node capability-closure engine."
    }
    $Process.WaitForExit()
    $ExitCode = $Process.ExitCode
}
finally {
    $Process.Dispose()
}

if ($ExitCode -ne 0) {
    throw ("Capability closure engine failed with exit code {0}." -f $ExitCode)
}
