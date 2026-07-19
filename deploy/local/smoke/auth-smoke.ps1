param(
  [string]$KeycloakBaseUrl = 'http://127.0.0.1:8081',
  [string]$ApplicationBaseUrl = 'http://127.0.0.1:8080'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertFrom-Base64Url([string]$Value) {
  $padded = $Value.Replace('-', '+').Replace('_', '/')
  switch ($padded.Length % 4) {
    2 { $padded += '==' }
    3 { $padded += '=' }
  }
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded))
}

function Assert-Contains($Values, [string]$Expected, [string]$ClaimName) {
  if (@($Values) -notcontains $Expected) {
    throw "Access token claim '$ClaimName' does not contain the expected value."
  }
}

$envPath = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path -LiteralPath $envPath)) {
  throw 'deploy/local/.env is required. Copy it from .env.example and keep it ignored.'
}

$local = ConvertFrom-StringData (Get-Content -Raw -LiteralPath $envPath)
$expectedIssuer = "$KeycloakBaseUrl/realms/payroll"
$accessToken = $null
$tokenResponse = $null

try {
  $tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$expectedIssuer/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
      client_id = 'payroll-web'
      grant_type = 'password'
      scope = 'openid'
      username = $local.KEYCLOAK_SMOKE_USERNAME
      password = $local.KEYCLOAK_SMOKE_PASSWORD
    }

  $accessToken = [string]$tokenResponse.access_token
  if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw 'Keycloak did not return an access token.'
  }

  $segments = $accessToken.Split('.')
  if ($segments.Length -ne 3) {
    throw 'Keycloak returned a malformed JWT.'
  }
  $claims = ConvertFrom-Json (ConvertFrom-Base64Url $segments[1])

  if ($claims.iss -ne $expectedIssuer) { throw 'Unexpected issuer claim.' }
  Assert-Contains $claims.aud 'payroll-api' 'aud'
  if ($claims.tenant_id -ne $local.KEYCLOAK_SMOKE_TENANT_ID) { throw 'Unexpected tenant_id claim.' }
  Assert-Contains $claims.realm_access.roles 'PAYROLL_OPERATOR' 'realm_access.roles'
  Assert-Contains $claims.permissions 'payroll.read' 'permissions'

  $correlationId = [guid]::NewGuid().ToString()
  $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$ApplicationBaseUrl/internal/baseline/auth-smoke" -Headers @{
    Authorization = "Bearer $accessToken"
    'X-Correlation-ID' = $correlationId
  }
  $body = $response.Content | ConvertFrom-Json
  if ($response.StatusCode -ne 200 -or $body.status -ne 'ok') { throw 'The secured application endpoint did not succeed.' }
  if ($body.tenantId -ne $local.KEYCLOAK_SMOKE_TENANT_ID) { throw 'The application used an unexpected tenant context.' }
  if ([string]$response.Headers['X-Correlation-ID'] -ne $correlationId) { throw 'The application did not preserve the correlation ID.' }

  [pscustomobject]@{
    Result = 'PASS'
    Issuer = $claims.iss
    Audience = (@($claims.aud) -join ',')
    TenantId = $claims.tenant_id
    Roles = (@($claims.realm_access.roles) -join ',')
    Permissions = (@($claims.permissions) -join ',')
    SecuredEndpoint = '/internal/baseline/auth-smoke (HTTP 200)'
    CorrelationIdReused = $true
    RawTokenPrintedOrPersisted = $false
  } | Format-List
} finally {
  $accessToken = $null
  $tokenResponse = $null
  $claims = $null
}
