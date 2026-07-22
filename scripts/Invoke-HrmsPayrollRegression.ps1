[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$RepoRoot = "C:\dev\hrms-payroll",

    [Parameter(Mandatory = $false)]
    [string]$ExpectedBranch = "",

    [Parameter(Mandatory = $false)]
    [switch]$SkipClean,

    [Parameter(Mandatory = $false)]
    [switch]$SkipFrontend,

    [Parameter(Mandatory = $false)]
    [switch]$SkipOpenApi
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-ExitCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$StepName
    )

    if ($LASTEXITCODE -ne 0) {
        throw (
            "{0} failed with exit code {1}." -f
            $StepName,
            $LASTEXITCODE
        )
    }
}

function Write-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ""
    Write-Host ("==> {0}" -f $Message) -ForegroundColor Cyan
}

if (-not (Test-Path -LiteralPath $RepoRoot -PathType Container)) {
    throw ("Repository root not found: {0}" -f $RepoRoot)
}

$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

Write-Step "Repository validation"

git -C $RepoRoot rev-parse --is-inside-work-tree |
    Out-Null
Assert-ExitCode "Git repository check"

$CurrentBranch = (
    git -C $RepoRoot branch --show-current
).Trim()
Assert-ExitCode "Git branch check"

if (
    -not [string]::IsNullOrWhiteSpace($ExpectedBranch) -and
    $CurrentBranch -ne $ExpectedBranch
) {
    throw (
        "Expected branch '{0}' but found '{1}'." -f
        $ExpectedBranch,
        $CurrentBranch
    )
}

git -C $RepoRoot diff --check
Assert-ExitCode "Initial git diff --check"

$MavenWrapper = Join-Path $RepoRoot "mvnw.cmd"

if (-not (Test-Path -LiteralPath $MavenWrapper -PathType Leaf)) {
    throw ("Maven wrapper not found: {0}" -f $MavenWrapper)
}

Write-Step "Complete backend Maven verification"

Push-Location $RepoRoot
try {
    if ($SkipClean) {
        & $MavenWrapper verify
    }
    else {
        & $MavenWrapper clean verify
    }

    Assert-ExitCode "Maven verification"
}
finally {
    Pop-Location
}

$RealmRelative = "deploy/local/keycloak/payroll-realm.json"
$RealmPath = Join-Path $RepoRoot $RealmRelative

if (Test-Path -LiteralPath $RealmPath -PathType Leaf) {
    Write-Step "Keycloak realm JSON validation"

    $Realm = Get-Content -Raw -LiteralPath $RealmPath |
        ConvertFrom-Json

    if ($null -eq $Realm.realm) {
        throw (
            "Keycloak realm JSON does not contain a realm property: {0}" -f
            $RealmRelative
        )
    }

    Write-Host "Keycloak realm JSON is valid." -ForegroundColor Green
}
else {
    Write-Host ""
    Write-Host (
        "Keycloak realm file not present; JSON validation skipped: {0}" -f
        $RealmRelative
    ) -ForegroundColor Yellow
}

if (-not $SkipOpenApi) {
    $OpenApiRelative =
        "contracts/openapi/payroll-vertical-slice-openapi-v1.yaml"
    $OpenApiPath = Join-Path $RepoRoot $OpenApiRelative

    if (-not (Test-Path -LiteralPath $OpenApiPath -PathType Leaf)) {
        throw ("OpenAPI contract not found: {0}" -f $OpenApiRelative)
    }

    Write-Step "OpenAPI validation"

    Push-Location $RepoRoot
    try {
        & npx.cmd `
            --yes `
            --package="@redocly/cli@2.39.0" `
            redocly lint `
            $OpenApiRelative

        Assert-ExitCode "Redocly OpenAPI lint"
    }
    finally {
        Pop-Location
    }
}

if (-not $SkipFrontend) {
    $FrontendRoot = Join-Path $RepoRoot "frontend/payroll-web"
    $PackageJson = Join-Path $FrontendRoot "package.json"

    if (-not (Test-Path -LiteralPath $PackageJson -PathType Leaf)) {
        throw ("Frontend package.json not found: {0}" -f $PackageJson)
    }

    Push-Location $FrontendRoot
    try {
        Write-Step "Frontend lint"
        & npm.cmd run lint
        Assert-ExitCode "Frontend lint"

        Write-Step "Frontend production build"
        & npm.cmd run build
        Assert-ExitCode "Frontend production build"

        Write-Step "Complete frontend test suite"
        & npm.cmd test
        Assert-ExitCode "Frontend test suite"
    }
    finally {
        Pop-Location
    }
}

Write-Step "Final repository validation"

git -C $RepoRoot diff --check
Assert-ExitCode "Final git diff --check"

Write-Host ""
Write-Host "Working-tree status:"
git -C $RepoRoot status --short
Assert-ExitCode "git status"

Write-Host ""
Write-Host "Change summary:"
git -C $RepoRoot diff --stat
Assert-ExitCode "git diff --stat"

Write-Host ""
Write-Host "Full HRMS Payroll regression passed." -ForegroundColor Green
Write-Host "No files were staged or committed."
