[CmdletBinding()]
param(
    [switch]$StopDockerWhenDone
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$dockerStartedByScript = $false

function Test-DockerReady {
    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

function Start-DockerForTests {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI was not found. Install or repair Docker Desktop."
    }

    if (Test-DockerReady) {
        Write-Host "Docker is already running."
        return $false
    }

    Write-Host "Docker is not running. Starting Docker Desktop..."

    $desktopCliAvailable = $false

    try {
        & docker desktop version *> $null
        $desktopCliAvailable = $LASTEXITCODE -eq 0
    }
    catch {
        $desktopCliAvailable = $false
    }

    if ($desktopCliAvailable) {
        & docker desktop start --timeout 180

        if ($LASTEXITCODE -ne 0) {
            throw "Docker Desktop did not start successfully."
        }
    }
    else {
        $possiblePaths = @(
            "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
            "$env:LOCALAPPDATA\Programs\Docker\Docker\Docker Desktop.exe"
        )

        $dockerDesktopExe = $possiblePaths |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1

        if (-not $dockerDesktopExe) {
            throw "Docker Desktop executable was not found."
        }

        Start-Process -FilePath $dockerDesktopExe
    }

    $deadline = (Get-Date).AddMinutes(3)

    while (-not (Test-DockerReady)) {
        if ((Get-Date) -ge $deadline) {
            throw "Docker Desktop did not become ready within three minutes."
        }

        Write-Host "Waiting for Docker Desktop..."
        Start-Sleep -Seconds 3
    }

    Write-Host "Docker Desktop is ready."
    return $true
}

function Confirm-LinuxContainers {
    $osType = (& docker info --format '{{.OSType}}').Trim()

    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine the Docker engine type."
    }

    if ($osType -ne "linux") {
        throw @"
Docker Desktop is using Windows containers.

The payroll tests require Linux containers because they use:
postgres:17-alpine

Switch Docker Desktop to Linux containers and run the script again.
"@
    }

    Write-Host "Docker Linux-container engine confirmed."
}

Push-Location $repoRoot

try {
    $dockerStartedByScript = Start-DockerForTests

    Confirm-LinuxContainers

    Write-Host ""
    Write-Host "Running complete Maven verification..."
    Write-Host ""

    & .\mvnw.cmd --batch-mode verify

    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }

    Write-Host ""
    Write-Host "Backend verification passed."
}
finally {
    Pop-Location

    if ($StopDockerWhenDone) {
        if ($dockerStartedByScript) {
            Write-Host "Stopping Docker Desktop because this script started it..."

            & docker desktop stop --timeout 120

            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Docker Desktop could not be stopped cleanly."
            }
        }
        else {
            Write-Host (
                "Docker was already running before verification; " +
                "it has been left running."
            )
        }
    }
}