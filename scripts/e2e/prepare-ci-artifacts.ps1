#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryPath = 'C:\dev\hrms-payroll',
    [string]$EnvironmentFile = 'deploy/local/.env',
    [string]$OutputDirectory = 'target/e2e-ci-artifacts'
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

$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory
}
else {
    Join-Path $RepositoryPath $OutputDirectory
}

$frontendPath = Join-Path $RepositoryPath 'frontend/payroll-web'
$resultsSource = Join-Path $frontendPath 'test-results'
$resultsOutput = Join-Path $outputPath 'test-results'
$traceOutput = Join-Path $outputPath 'sanitized-traces'
$serviceOutput = Join-Path $outputPath 'service-logs'
$summaryOutput = Join-Path $outputPath 'playwright-summary'

$jwtPattern = 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
$tokenJsonPattern = '(?i)"(?:access_token|refresh_token|id_token)"\s*:\s*"[^"]{20,}"'
$bearerPattern = '(?i)bearer\s+eyJ[A-Za-z0-9_-]{10,}\.'
$passwordPattern = '(?i)(?:password|passwd)\s*[=:]\s*(?:"[^"]{4,}"|''[^'']{4,}''|[^\s,;]{4,})'
$syntheticPasswordPattern = '(?i)\bchange-me\b'

$textExtensions = @(
    '.css'
    '.html'
    '.js'
    '.json'
    '.log'
    '.md'
    '.stacks'
    '.trace'
    '.txt'
    '.xml'
    '.yaml'
    '.yml'
)

function Test-TextCandidate {
    param([Parameter(Mandatory)][string]$Path)

    $extension = [IO.Path]::GetExtension($Path).ToLowerInvariant()
    return $textExtensions -contains $extension
}

function Protect-TextFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-TextCandidate -Path $Path)) {
        return
    }

    $text = [IO.File]::ReadAllText($Path)
    $protected = $text

    $protected = [regex]::Replace(
        $protected,
        $tokenJsonPattern,
        '"token":"[REDACTED]"'
    )
    $protected = [regex]::Replace(
        $protected,
        $bearerPattern,
        '[REDACTED-BEARER]'
    )
    $protected = [regex]::Replace(
        $protected,
        $jwtPattern,
        '[REDACTED-JWT]'
    )
    $protected = [regex]::Replace(
        $protected,
        $passwordPattern,
        'credential=[REDACTED]'
    )
    $protected = [regex]::Replace(
        $protected,
        $syntheticPasswordPattern,
        '[REDACTED-CREDENTIAL]'
    )

    if ($protected -cne $text) {
        [IO.File]::WriteAllText(
            $Path,
            $protected,
            [Text.UTF8Encoding]::new($false)
        )
    }
}

function Test-SensitiveTextFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-TextCandidate -Path $Path)) {
        return $false
    }

    $text = [IO.File]::ReadAllText($Path)

    return (
        $text -match $jwtPattern -or
        $text -match $tokenJsonPattern -or
        $text -match $bearerPattern -or
        $text -match $passwordPattern -or
        $text -match $syntheticPasswordPattern
    )
}

function Protect-Directory {
    param([Parameter(Mandatory)][string]$Root)

    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        return
    }

    foreach (
        $candidate in @(
            Get-ChildItem -LiteralPath $Root -Recurse -File
        )
    ) {
        Protect-TextFile -Path $candidate.FullName
    }
}

function Assert-SafeDirectory {
    param([Parameter(Mandatory)][string]$Root)

    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        return
    }

    foreach (
        $candidate in @(
            Get-ChildItem -LiteralPath $Root -Recurse -File
        )
    ) {
        if ($candidate.FullName -match '[\\/]\.auth[\\/]') {
            throw (
                'Authentication state entered the prepared artifact: ' +
                $candidate.FullName
            )
        }

        if ($candidate.Name -eq 'trace.zip') {
            throw (
                'Raw Playwright trace entered the prepared artifact: ' +
                $candidate.FullName
            )
        }

        if (Test-SensitiveTextFile -Path $candidate.FullName) {
            throw (
                'Sensitive text remains in the prepared artifact: ' +
                $candidate.FullName
            )
        }
    }
}

function New-SanitizedTrace {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination
    )

    $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) (
        'hrms-payroll-trace-' + [guid]::NewGuid().ToString('N')
    )
    $inspectionRoot = Join-Path ([IO.Path]::GetTempPath()) (
        'hrms-payroll-trace-check-' + [guid]::NewGuid().ToString('N')
    )

    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    New-Item -ItemType Directory -Path $inspectionRoot | Out-Null

    try {
        [IO.Compression.ZipFile]::ExtractToDirectory(
            $Source,
            $temporaryRoot
        )

        Get-ChildItem -LiteralPath $temporaryRoot -Recurse -File |
            Where-Object {
                $_.Name.EndsWith('.network') -or
                $_.FullName -match '[\\/]\.auth[\\/]'
            } |
            Remove-Item -Force

        Protect-Directory -Root $temporaryRoot
        Assert-SafeDirectory -Root $temporaryRoot

        New-Item -ItemType Directory `
            -Path (Split-Path -Parent $Destination) `
            -Force |
            Out-Null

        [IO.Compression.ZipFile]::CreateFromDirectory(
            $temporaryRoot,
            $Destination,
            [IO.Compression.CompressionLevel]::Optimal,
            $false
        )

        [IO.Compression.ZipFile]::ExtractToDirectory(
            $Destination,
            $inspectionRoot
        )

        foreach (
            $candidate in @(
                Get-ChildItem -LiteralPath $inspectionRoot -Recurse -File
            )
        ) {
            if ($candidate.Name.EndsWith('.network')) {
                throw (
                    'Network recording remains in sanitized trace: ' +
                    $Destination
                )
            }
        }

        Assert-SafeDirectory -Root $inspectionRoot
    }
    finally {
        Remove-Item -LiteralPath $temporaryRoot `
            -Recurse `
            -Force `
            -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $inspectionRoot `
            -Recurse `
            -Force `
            -ErrorAction SilentlyContinue
    }
}

function Write-SanitizedHtmlSummary {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][int]$TraceCount
    )

    New-Item -ItemType Directory -Path $summaryOutput -Force | Out-Null

    $artifactFiles = @(
        Get-ChildItem -LiteralPath $Root -Recurse -File |
            Where-Object {
                $_.FullName -notlike (
                    (Join-Path $summaryOutput '*')
                )
            } |
            Sort-Object FullName
    )

    $listItems = @(
        foreach ($artifactFile in $artifactFiles) {
            $relative = [IO.Path]::GetRelativePath(
                $Root,
                $artifactFile.FullName
            )
            $encoded = [Net.WebUtility]::HtmlEncode($relative)
            "<li><code>$encoded</code></li>"
        }
    )

    if ($listItems.Count -eq 0) {
        $listItems = @('<li>No failure files were present.</li>')
    }

    $generated = [Net.WebUtility]::HtmlEncode(
        [DateTime]::UtcNow.ToString('O')
    )

    $html = @"
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Payroll browser E2E sanitized summary</title>
</head>
<body>
  <h1>Payroll browser E2E sanitized summary</h1>
  <p>Generated UTC: <code>$generated</code></p>
  <p>Sanitized traces: <code>$TraceCount</code></p>
  <p>
    The original embedded Playwright HTML report is deliberately excluded
    because it can contain archived network and authentication data.
  </p>
  <h2>Prepared evidence</h2>
  <ul>
$($listItems -join [Environment]::NewLine)
  </ul>
</body>
</html>
"@

    [IO.File]::WriteAllText(
        (Join-Path $summaryOutput 'index.html'),
        $html,
        [Text.UTF8Encoding]::new($false)
    )
}

if (Test-Path -LiteralPath $outputPath) {
    Remove-Item -LiteralPath $outputPath -Recurse -Force
}

New-Item -ItemType Directory -Path $resultsOutput -Force | Out-Null
New-Item -ItemType Directory -Path $traceOutput -Force | Out-Null
New-Item -ItemType Directory -Path $serviceOutput -Force | Out-Null

$traceIndex = 0

if (Test-Path -LiteralPath $resultsSource -PathType Container) {
    foreach (
        $resultFile in @(
            Get-ChildItem -LiteralPath $resultsSource -Recurse -File
        )
    ) {
        $relative = [IO.Path]::GetRelativePath(
            $resultsSource,
            $resultFile.FullName
        )

        if ($relative -match '(^|[\\/])\.auth([\\/]|$)') {
            continue
        }

        if ($resultFile.Name -eq 'trace.zip') {
            $traceIndex++
            $traceDestination = Join-Path $traceOutput (
                'trace-{0:D2}-sanitized.zip' -f $traceIndex
            )

            New-SanitizedTrace `
                -Source $resultFile.FullName `
                -Destination $traceDestination

            continue
        }

        if ($resultFile.Extension -eq '.zip') {
            continue
        }

        $resultDestination = Join-Path $resultsOutput $relative

        New-Item -ItemType Directory `
            -Path (Split-Path -Parent $resultDestination) `
            -Force |
            Out-Null

        Copy-Item `
            -LiteralPath $resultFile.FullName `
            -Destination $resultDestination `
            -Force

        Protect-TextFile -Path $resultDestination
    }
}

$composeFile = Join-Path $RepositoryPath 'deploy/e2e/compose.yaml'

if (
    (Test-Path -LiteralPath $environmentPath -PathType Leaf) -and
    (Test-Path -LiteralPath $composeFile -PathType Leaf) -and
    ($null -ne (Get-Command docker -ErrorAction SilentlyContinue))
) {
    $composeArguments = Get-E2eComposeArguments `
        -EnvironmentFile $environmentPath `
        -ComposeFile $composeFile

    Push-Location $RepositoryPath

    try {
        try {
            @(
                & docker @composeArguments ps --all 2>&1 |
                    ForEach-Object { [string]$_ }
            ) | Set-Content `
                -LiteralPath (Join-Path $serviceOutput 'compose-ps.txt') `
                -Encoding utf8

            @(
                & docker @composeArguments logs --no-color 2>&1 |
                    ForEach-Object { [string]$_ }
            ) | Set-Content `
                -LiteralPath (Join-Path $serviceOutput 'compose.log') `
                -Encoding utf8
        }
        catch {
            @(
                'Unable to collect Docker Compose diagnostics.'
                [string]$_.Exception.Message
            ) | Set-Content `
                -LiteralPath (
                    Join-Path $serviceOutput 'compose-collection-error.txt'
                ) `
                -Encoding utf8
        }
    }
    finally {
        Pop-Location
    }
}

$backendLog = Join-Path $RepositoryPath 'target/e2e-backend.log'

if (Test-Path -LiteralPath $backendLog -PathType Leaf) {
    Copy-Item `
        -LiteralPath $backendLog `
        -Destination (Join-Path $serviceOutput 'backend.log') `
        -Force
}

Protect-Directory -Root $serviceOutput

$manifest = @(
    "Repository: $RepositoryPath"
    "Generated UTC: $([DateTime]::UtcNow.ToString('O'))"
    'Original Playwright HTML report uploaded: no'
    'Sanitized HTML summary uploaded: yes'
    'Raw trace archives uploaded: no'
    'Authentication state uploaded: no'
    "Sanitized trace count: $traceIndex"
)

$manifest | Set-Content `
    -LiteralPath (Join-Path $outputPath 'artifact-manifest.txt') `
    -Encoding utf8

Write-SanitizedHtmlSummary `
    -Root $outputPath `
    -TraceCount $traceIndex

Protect-Directory -Root $summaryOutput
Assert-SafeDirectory -Root $outputPath

Write-Host ''
Write-Host 'Sanitized E2E failure artifact prepared.' -ForegroundColor Green
Write-Host "Output: $outputPath"
Write-Host "Sanitized traces: $traceIndex"
Write-Host 'Sanitized HTML summary: included'
Write-Host 'Original embedded Playwright HTML report: excluded'
Write-Host 'Raw traces: excluded'
Write-Host 'Authentication state: excluded'
