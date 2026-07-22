[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$ScriptPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
    throw ("PowerShell script not found: {0}" -f $ScriptPath)
}

$ResolvedPath = (Resolve-Path -LiteralPath $ScriptPath).Path
$Tokens = $null
$ParseErrors = $null

[System.Management.Automation.Language.Parser]::ParseFile(
    $ResolvedPath,
    [ref]$Tokens,
    [ref]$ParseErrors
) | Out-Null

if ($ParseErrors.Count -gt 0) {
    Write-Host ""
    Write-Host "PowerShell parser validation failed:" -ForegroundColor Red

    foreach ($ParseError in $ParseErrors) {
        $Extent = $ParseError.Extent

        Write-Host (
            "  Line {0}, column {1}: {2}" -f
            $Extent.StartLineNumber,
            $Extent.StartColumnNumber,
            $ParseError.Message
        ) -ForegroundColor Red
    }

    throw (
        "PowerShell parser validation failed for: {0}" -f
        $ResolvedPath
    )
}

Write-Host (
    "PowerShell parser validation passed: {0}" -f
    $ResolvedPath
) -ForegroundColor Green
