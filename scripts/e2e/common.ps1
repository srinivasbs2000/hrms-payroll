#requires -Version 7.0

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-DotEnv {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file does not exist: $Path"
    }

    $result = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }

        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            throw "Invalid .env entry in ${Path}: $line"
        }

        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        $result[$name] = $value
    }

    return $result
}

function Set-E2eProcessEnvironment {
    param(
        [Parameter(Mandatory)][hashtable]$Values,
        [int]$PostgresPort = 25432,
        [int]$KeycloakPort = 8081
    )

    $required = @(
        'POSTGRES_DB'
        'POSTGRES_ADMIN_PASSWORD'
        'PAYROLL_APP_PASSWORD'
        'PAYROLL_MIGRATOR_PASSWORD'
        'KEYCLOAK_ADMIN'
        'KEYCLOAK_ADMIN_PASSWORD'
    )

    foreach ($name in $required) {
        if (-not $Values.ContainsKey($name) -or
            [string]::IsNullOrWhiteSpace([string]$Values[$name])) {
            throw "Required value '$name' is missing from the local .env file."
        }

        Set-Item -Path "Env:$name" -Value ([string]$Values[$name])
    }

    $env:E2E_POSTGRES_PORT = [string]$PostgresPort
    $env:E2E_KEYCLOAK_PORT = [string]$KeycloakPort
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [switch]$Capture
    )

    Push-Location $WorkingDirectory
    try {
        if ($Capture) {
            $output = & $Command @Arguments 2>&1
            $exitCode = $LASTEXITCODE
            if ($exitCode -ne 0) {
                throw "$Command $($Arguments -join ' ') failed with exit code $exitCode.`n$($output -join [Environment]::NewLine)"
            }
            return @($output)
        }

        & $Command @Arguments
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "$Command $($Arguments -join ' ') failed with exit code $exitCode."
        }
    }
    finally {
        Pop-Location
    }
}

function Get-E2eComposeArguments {
    param(
        [Parameter(Mandatory)][string]$EnvironmentFile,
        [Parameter(Mandatory)][string]$ComposeFile
    )

    return @(
        'compose'
        '--project-name'
        'hrms-payroll-e2e'
        '--env-file'
        $EnvironmentFile
        '-f'
        $ComposeFile
    )
}

function Wait-ForPostgres {
    param(
        [Parameter(Mandatory)][string]$RepositoryPath,
        [Parameter(Mandatory)][string[]]$ComposeArguments
    )

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        & docker @ComposeArguments exec -T postgres `
            pg_isready -U postgres -d payroll *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }

    & docker @ComposeArguments logs postgres
    throw 'E2E PostgreSQL did not become ready.'
}

function Wait-ForKeycloak {
    param([int]$Port = 8081)

    $url = "http://localhost:$Port/realms/payroll/.well-known/openid-configuration"
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 |
                Out-Null
            return
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "E2E Keycloak did not become ready at $url."
}

function Set-E2eKeycloakPasswords {
    param(
        [Parameter(Mandatory)][string]$RepositoryPath,
        [Parameter(Mandatory)][string[]]$ComposeArguments,
        [Parameter(Mandatory)][string]$BootstrapUsername,
        [Parameter(Mandatory)][string]$BootstrapPassword,
        [Parameter(Mandatory)][string]$AdminPassword,
        [Parameter(Mandatory)][string]$SmokePassword
    )

    $kcadm = '/opt/keycloak/bin/kcadm.sh'

    Invoke-Native `
        -Command 'docker' `
        -Arguments (
            $ComposeArguments + @(
                'exec'
                '-T'
                'keycloak'
                $kcadm
                'config'
                'credentials'
                '--server'
                'http://localhost:8080'
                '--realm'
                'master'
                '--user'
                $BootstrapUsername
                '--password'
                $BootstrapPassword
            )
        ) `
        -WorkingDirectory $RepositoryPath

    foreach ($credential in @(
        @{
            Username = 'payroll.admin'
            Password = $AdminPassword
        }
        @{
            Username = 'payroll.smoke'
            Password = $SmokePassword
        }
    )) {
        Invoke-Native `
            -Command 'docker' `
            -Arguments (
                $ComposeArguments + @(
                    'exec'
                    '-T'
                    'keycloak'
                    $kcadm
                    'set-password'
                    '-r'
                    'payroll'
                    '--username'
                    $credential.Username
                    '--new-password'
                    $credential.Password
                )
            ) `
            -WorkingDirectory $RepositoryPath
    }
}

function Invoke-PostgresFile {
    param(
        [Parameter(Mandatory)][string]$RepositoryPath,
        [Parameter(Mandatory)][string[]]$ComposeArguments,
        [Parameter(Mandatory)][string]$SqlPath
    )

    $sql = Get-Content -Raw -LiteralPath $SqlPath
    Push-Location $RepositoryPath
    try {
        $output = @(
            $sql |
                & docker @ComposeArguments exec -T postgres `
                    psql `
                    -X `
                    -v ON_ERROR_STOP=1 `
                    -v VERBOSITY=verbose `
                    -U postgres `
                    -d payroll 2>&1
        )
        $exitCode = $LASTEXITCODE

        foreach ($line in $output) {
            Write-Host $line
        }

        if ($exitCode -ne 0) {
            $details = if ($output.Count -eq 0) {
                '<PostgreSQL returned no diagnostic output>'
            }
            else {
                $output -join [Environment]::NewLine
            }

            throw (
                "PostgreSQL script failed: $SqlPath`n" +
                "Exit code: $exitCode`n" +
                $details
            )
        }
    }
    finally {
        Pop-Location
    }
}
