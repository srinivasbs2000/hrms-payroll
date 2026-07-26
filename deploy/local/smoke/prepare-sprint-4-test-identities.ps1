[CmdletBinding(DefaultParameterSetName = 'Prepare')]
param(
  [string]$RepositoryPath = 'C:\dev\hrms-payroll',

  [Parameter(ParameterSetName = 'Prepare', Mandatory)]
  [string]$StatePath,

  [Parameter(ParameterSetName = 'Cleanup', Mandatory)]
  [switch]$Cleanup,

  [Parameter(ParameterSetName = 'Cleanup', Mandatory)]
  [string]$CleanupStatePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$KeycloakBase = 'http://localhost:8081'
$Realm = 'payroll'
$ClientId = 'payroll-web'
$SyntheticPassword = 'change-me'
$MarkerName = 'hrms_payroll_smoke_harness'
$MarkerValue = 'step03-v2'
$EnvFile = Join-Path $RepositoryPath 'deploy\local\.env'

$Definitions = @(
  [ordered]@{
    username = 'payroll.no-stat-read'
    firstName = 'Payroll'
    lastName = 'No Statutory Read'
    email = 'payroll.no-stat-read@example.invalid'
    tenantId = '00000000-0000-0000-0000-000000000001'
    permissions = @('payroll-cycle.read')
  },
  [ordered]@{
    username = 'payroll.cross-tenant'
    firstName = 'Payroll'
    lastName = 'Cross Tenant'
    email = 'payroll.cross-tenant@example.invalid'
    tenantId = '00000000-0000-0000-0000-000000000002'
    permissions = @(
      'payroll-cycle.read',
      'payroll-result.read',
      'statutory-evaluation.read',
      'statutory-ledger.read',
      'statutory-balance.read',
      'statutory-reconciliation.read',
      'statutory-remittance.read'
    )
  }
)

function Get-PropertyValue {
  param(
    [Parameter(Mandatory)]$Object,
    [Parameter(Mandatory)][string]$Name
  )

  if ($null -eq $Object) {
    return $null
  }

  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property) {
    return $null
  }

  return $property.Value
}

function Get-PropertyNames {
  param([Parameter(Mandatory)]$Object)

  if ($null -eq $Object) {
    return @()
  }

  return @($Object.PSObject.Properties.Name)
}

function Read-DotEnv {
  param([Parameter(Mandatory)][string]$Path)

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Environment file not found: $Path"
  }

  $values = @{}
  foreach ($rawLine in Get-Content -LiteralPath $Path) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith('#')) {
      continue
    }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) {
      continue
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if (
      ($value.StartsWith('"') -and $value.EndsWith('"')) -or
      ($value.StartsWith("'") -and $value.EndsWith("'"))
    ) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    $values[$name] = $value
  }

  return $values
}

function Get-AdminHeaders {
  $local = Read-DotEnv -Path $EnvFile
  foreach ($required in @('KEYCLOAK_ADMIN', 'KEYCLOAK_ADMIN_PASSWORD')) {
    if (
      -not $local.ContainsKey($required) -or
      [string]::IsNullOrWhiteSpace($local[$required])
    ) {
      throw "Missing $required in $EnvFile"
    }
  }

  $token = Invoke-RestMethod `
    -Method Post `
    -Uri "$KeycloakBase/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
      client_id = 'admin-cli'
      username = $local.KEYCLOAK_ADMIN
      password = $local.KEYCLOAK_ADMIN_PASSWORD
      grant_type = 'password'
    } `
    -TimeoutSec 20

  $accessToken = Get-PropertyValue -Object $token -Name 'access_token'
  if ([string]::IsNullOrWhiteSpace([string]$accessToken)) {
    throw 'Keycloak admin token was not returned.'
  }

  return @{ Authorization = "Bearer $accessToken" }
}

function Get-UserProfileRaw {
  param([Parameter(Mandatory)][hashtable]$Headers)

  $response = Invoke-WebRequest `
    -Method Get `
    -Uri "$KeycloakBase/admin/realms/$Realm/users/profile" `
    -Headers $Headers `
    -TimeoutSec 20 `
    -UseBasicParsing

  if ($response.StatusCode -ne 200) {
    throw "User-profile configuration read returned HTTP $($response.StatusCode)."
  }

  return [string]$response.Content
}

function Set-UserProfileRaw {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$Json
  )

  $response = Invoke-WebRequest `
    -Method Put `
    -Uri "$KeycloakBase/admin/realms/$Realm/users/profile" `
    -Headers $Headers `
    -ContentType 'application/json' `
    -Body $Json `
    -TimeoutSec 20 `
    -UseBasicParsing

  if ($response.StatusCode -notin @(200, 204)) {
    throw "User-profile configuration update returned HTTP $($response.StatusCode); expected 204."
  }
}

function Normalize-Json {
  param([Parameter(Mandatory)][string]$Json)

  return (
    $Json |
      ConvertFrom-Json |
      ConvertTo-Json -Depth 100 -Compress
  )
}

function Enable-AdminEditPolicy {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$OriginalProfileJson
  )

  $profile = $OriginalProfileJson | ConvertFrom-Json
  $property = $profile.PSObject.Properties['unmanagedAttributePolicy']
  if ($null -eq $property) {
    $profile |
      Add-Member `
        -MemberType NoteProperty `
        -Name 'unmanagedAttributePolicy' `
        -Value 'ADMIN_EDIT'
  }
  else {
    $property.Value = 'ADMIN_EDIT'
  }

  $updatedJson = $profile | ConvertTo-Json -Depth 100 -Compress
  Set-UserProfileRaw -Headers $Headers -Json $updatedJson

  $verifiedRaw = Get-UserProfileRaw -Headers $Headers
  $verified = $verifiedRaw | ConvertFrom-Json
  $policy = Get-PropertyValue `
    -Object $verified `
    -Name 'unmanagedAttributePolicy'

  if ([string]$policy -ne 'ADMIN_EDIT') {
    throw "Keycloak user-profile policy verification failed. Actual policy: '$policy'."
  }

  return $updatedJson
}

function Write-State {
  param(
    [Parameter(Mandatory)][System.Collections.IDictionary]$State,
    [Parameter(Mandatory)][string]$Path
  )

  $directory = Split-Path -Parent $Path
  if (-not [string]::IsNullOrWhiteSpace($directory)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
  }

  $State |
    ConvertTo-Json -Depth 100 |
    Set-Content -LiteralPath $Path -Encoding utf8
}

function Find-ExactUser {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$Username
  )

  $encoded = [uri]::EscapeDataString($Username)
  $matches = @(
    Invoke-RestMethod `
      -Method Get `
      -Uri "$KeycloakBase/admin/realms/$Realm/users?username=$encoded&exact=true&briefRepresentation=false" `
      -Headers $Headers `
      -TimeoutSec 20
  )

  if ($matches.Count -gt 1) {
    throw "Expected at most one Keycloak user named '$Username'; found $($matches.Count)."
  }

  if ($matches.Count -eq 0) {
    return $null
  }

  return $matches[0]
}

function Get-FullUser {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$UserId
  )

  return Invoke-RestMethod `
    -Method Get `
    -Uri "$KeycloakBase/admin/realms/$Realm/users/$UserId" `
    -Headers $Headers `
    -TimeoutSec 20
}

function Get-Definition {
  param([Parameter(Mandatory)][string]$Username)

  $matches = @(
    $Definitions |
      Where-Object { [string]$_['username'] -eq $Username }
  )

  if ($matches.Count -ne 1) {
    throw "Synthetic identity definition not found for '$Username'."
  }

  return $matches[0]
}

function Test-ExactHarnessIdentity {
  param(
    [Parameter(Mandatory)]$User,
    [Parameter(Mandatory)][System.Collections.IDictionary]$Definition
  )

  return (
    [string](Get-PropertyValue -Object $User -Name 'username') -eq
      [string]$Definition['username'] -and
    [string](Get-PropertyValue -Object $User -Name 'firstName') -eq
      [string]$Definition['firstName'] -and
    [string](Get-PropertyValue -Object $User -Name 'lastName') -eq
      [string]$Definition['lastName'] -and
    [string](Get-PropertyValue -Object $User -Name 'email') -eq
      [string]$Definition['email']
  )
}

function Get-AttributeValues {
  param(
    [Parameter(Mandatory)]$Attributes,
    [Parameter(Mandatory)][string]$Name
  )

  if ($null -eq $Attributes) {
    return @()
  }

  $property = $Attributes.PSObject.Properties[$Name]
  if ($null -eq $property) {
    return @()
  }

  return @($property.Value | ForEach-Object { [string]$_ })
}

function Test-ExactStringSet {
  param(
    [Parameter(Mandatory)]
    [AllowEmptyCollection()]
    [string[]]$Actual,

    [Parameter(Mandatory)]
    [AllowEmptyCollection()]
    [string[]]$Expected
  )

  $actualSet = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal
  )
  $expectedSet = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal
  )

  foreach ($value in @($Actual)) {
    $null = $actualSet.Add([string]$value)
  }

  foreach ($value in @($Expected)) {
    $null = $expectedSet.Add([string]$value)
  }

  return $actualSet.SetEquals($expectedSet)
}

function Set-SyntheticPassword {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$UserId
  )

  $payload = @{
    type = 'password'
    value = $SyntheticPassword
    temporary = $false
  } | ConvertTo-Json -Compress

  Invoke-RestMethod `
    -Method Put `
    -Uri "$KeycloakBase/admin/realms/$Realm/users/$UserId/reset-password" `
    -Headers $Headers `
    -ContentType 'application/json' `
    -Body $payload `
    -TimeoutSec 20 | Out-Null
}

function Add-StateUser {
  param(
    [Parameter(Mandatory)][System.Collections.IDictionary]$State,
    [Parameter(Mandatory)][System.Collections.IDictionary]$Entry,
    [Parameter(Mandatory)][string]$Path
  )

  $users = @($State['users'])
  $matchIndex = -1

  for ($index = 0; $index -lt $users.Count; $index += 1) {
    if (
      [string]$users[$index]['username'] -eq
      [string]$Entry['username']
    ) {
      $matchIndex = $index
      break
    }
  }

  if ($matchIndex -lt 0) {
    $State['users'] = @($users) + @($Entry)
  }
  else {
    $users[$matchIndex] = $Entry
    $State['users'] = @($users)
  }

  Write-State -State $State -Path $Path
}

function Ensure-ManagedUser {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][System.Collections.IDictionary]$Definition,
    [Parameter(Mandatory)][System.Collections.IDictionary]$State,
    [Parameter(Mandatory)][string]$Path
  )

  $existing = Find-ExactUser `
    -Headers $Headers `
    -Username ([string]$Definition['username'])

  $createdDuringRun = $false
  if ($null -eq $existing) {
    $createPayload = @{
      username = $Definition['username']
      enabled = $true
      firstName = $Definition['firstName']
      lastName = $Definition['lastName']
      email = $Definition['email']
      emailVerified = $true
      requiredActions = @()
    } | ConvertTo-Json -Depth 20

    $response = Invoke-WebRequest `
      -Method Post `
      -Uri "$KeycloakBase/admin/realms/$Realm/users" `
      -Headers $Headers `
      -ContentType 'application/json' `
      -Body $createPayload `
      -TimeoutSec 20 `
      -UseBasicParsing

    if ($response.StatusCode -ne 201) {
      throw "Keycloak user creation returned HTTP $($response.StatusCode) for $($Definition['username'])."
    }

    $location = [string]$response.Headers.Location
    if ([string]::IsNullOrWhiteSpace($location)) {
      throw "Keycloak did not return a Location header for $($Definition['username'])."
    }

    $userId = ($location.TrimEnd('/') -split '/')[-1]
    $createdDuringRun = $true
  }
  else {
    $userId = [string](Get-PropertyValue -Object $existing -Name 'id')
    $fullExisting = Get-FullUser -Headers $Headers -UserId $userId
    if (-not (Test-ExactHarnessIdentity -User $fullExisting -Definition $Definition)) {
      throw "Refusing to modify pre-existing user '$($Definition['username'])' because its identifying fields do not exactly match this harness."
    }
  }

  $stateEntry = [ordered]@{
    username = [string]$Definition['username']
    id = $userId
    tenantId = [string]$Definition['tenantId']
    firstName = [string]$Definition['firstName']
    lastName = [string]$Definition['lastName']
    email = [string]$Definition['email']
    createdDuringRun = $createdDuringRun
  }
  Add-StateUser -State $State -Entry $stateEntry -Path $Path

  $attributes = @{
    tenant_id = @([string]$Definition['tenantId'])
    permissions = @($Definition['permissions'] | ForEach-Object { [string]$_ })
    $MarkerName = @($MarkerValue)
  }

  $updatePayload = @{
    id = $userId
    username = $Definition['username']
    enabled = $true
    firstName = $Definition['firstName']
    lastName = $Definition['lastName']
    email = $Definition['email']
    emailVerified = $true
    requiredActions = @()
    attributes = $attributes
  } | ConvertTo-Json -Depth 20

  Invoke-RestMethod `
    -Method Put `
    -Uri "$KeycloakBase/admin/realms/$Realm/users/$userId" `
    -Headers $Headers `
    -ContentType 'application/json' `
    -Body $updatePayload `
    -TimeoutSec 20 | Out-Null

  Set-SyntheticPassword -Headers $Headers -UserId $userId

  $verified = Get-FullUser -Headers $Headers -UserId $userId
  if (-not (Test-ExactHarnessIdentity -User $verified -Definition $Definition)) {
    throw "Synthetic identity fields were not persisted for '$($Definition['username'])'."
  }

  $verifiedAttributes = Get-PropertyValue `
    -Object $verified `
    -Name 'attributes'

  if ($null -eq $verifiedAttributes) {
    $properties = (Get-PropertyNames -Object $verified) -join ', '
    throw "Keycloak returned no attributes property for '$($Definition['username'])'. Returned properties: $properties"
  }

  [string[]]$tenantValues = @(
    Get-AttributeValues `
      -Attributes $verifiedAttributes `
      -Name 'tenant_id'
  )
  [string[]]$permissionValues = @(
    Get-AttributeValues `
      -Attributes $verifiedAttributes `
      -Name 'permissions'
  )
  [string[]]$markerValues = @(
    Get-AttributeValues `
      -Attributes $verifiedAttributes `
      -Name $MarkerName
  )

  if (
    -not (Test-ExactStringSet `
      -Actual $tenantValues `
      -Expected @([string]$Definition['tenantId']))
  ) {
    throw "Tenant attribute verification failed for '$($Definition['username'])'."
  }

  if (
    -not (Test-ExactStringSet `
      -Actual $permissionValues `
      -Expected @($Definition['permissions'] | ForEach-Object { [string]$_ }))
  ) {
    throw "Permissions attribute verification failed for '$($Definition['username'])'."
  }

  if (
    -not (Test-ExactStringSet `
      -Actual $markerValues `
      -Expected @($MarkerValue))
  ) {
    throw "Harness marker verification failed for '$($Definition['username'])'."
  }

  return $stateEntry
}

function Decode-JwtPayload {
  param([Parameter(Mandatory)][string]$Token)

  $parts = $Token.Split('.')
  if ($parts.Count -lt 2) {
    throw 'Access token is not a JWT.'
  }

  $payload = $parts[1].Replace('-', '+').Replace('_', '/')
  switch ($payload.Length % 4) {
    2 { $payload += '==' }
    3 { $payload += '=' }
    0 { }
    default { throw 'JWT payload has invalid Base64URL length.' }
  }

  $bytes = [Convert]::FromBase64String($payload)
  $json = [Text.Encoding]::UTF8.GetString($bytes)
  return $json | ConvertFrom-Json
}

function Verify-TokenClaims {
  param(
    [Parameter(Mandatory)][System.Collections.IDictionary]$Definition
  )

  $response = Invoke-RestMethod `
    -Method Post `
    -Uri "$KeycloakBase/realms/$Realm/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
      client_id = $ClientId
      username = $Definition['username']
      password = $SyntheticPassword
      grant_type = 'password'
    } `
    -TimeoutSec 20

  $token = [string](Get-PropertyValue -Object $response -Name 'access_token')
  if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Direct-grant access token was not returned for '$($Definition['username'])'."
  }

  $claims = Decode-JwtPayload -Token $token
  $tenantClaim = [string](Get-PropertyValue -Object $claims -Name 'tenant_id')
  [string[]]$permissionClaim = @(
    (Get-PropertyValue -Object $claims -Name 'permissions') |
      ForEach-Object { [string]$_ }
  )

  if ($tenantClaim -ne [string]$Definition['tenantId']) {
    throw "Token tenant claim verification failed for '$($Definition['username'])'."
  }

  if (
    -not (Test-ExactStringSet `
      -Actual $permissionClaim `
      -Expected @($Definition['permissions'] | ForEach-Object { [string]$_ }))
  ) {
    throw "Token permissions claim verification failed for '$($Definition['username'])'."
  }

  return [ordered]@{
    username = [string]$Definition['username']
    tenantId = $tenantClaim
    permissions = @($permissionClaim)
  }
}

function Remove-ManagedUsersAndRestoreProfile {
  param(
    [Parameter(Mandatory)][hashtable]$Headers,
    [Parameter(Mandatory)][string]$Path
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Identity state file not found: $Path"
  }

  $state = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
  $errors = [System.Collections.Generic.List[string]]::new()

  foreach ($item in @($state.users)) {
    try {
      $definition = Get-Definition -Username ([string]$item.username)
      $existing = Find-ExactUser `
        -Headers $Headers `
        -Username ([string]$item.username)

      if ($null -eq $existing) {
        Write-Host "Synthetic user already absent: $($item.username)"
        continue
      }

      $existingId = [string](Get-PropertyValue -Object $existing -Name 'id')
      if ($existingId -ne [string]$item.id) {
        throw "User ID changed for '$($item.username)'."
      }

      $full = Get-FullUser -Headers $Headers -UserId $existingId
      if (-not (Test-ExactHarnessIdentity -User $full -Definition $definition)) {
        throw "Identifying fields no longer match for '$($item.username)'."
      }

      Invoke-RestMethod `
        -Method Delete `
        -Uri "$KeycloakBase/admin/realms/$Realm/users/$existingId" `
        -Headers $Headers `
        -TimeoutSec 20 | Out-Null

      Write-Host "Synthetic user removed: $($item.username)"
    }
    catch {
      $errors.Add("User cleanup failed for '$($item.username)': $($_.Exception.Message)")
    }
  }

  $profileChanged = [bool](
    Get-PropertyValue -Object $state -Name 'userProfileChanged'
  )
  $originalProfileJson = [string](
    Get-PropertyValue -Object $state -Name 'originalUserProfileJson'
  )

  if ($profileChanged) {
    try {
      if ([string]::IsNullOrWhiteSpace($originalProfileJson)) {
        throw 'Original user-profile JSON is missing from state.'
      }

      Set-UserProfileRaw `
        -Headers $Headers `
        -Json $originalProfileJson

      $restoredRaw = Get-UserProfileRaw -Headers $Headers
      if (
        (Normalize-Json -Json $restoredRaw) -ne
        (Normalize-Json -Json $originalProfileJson)
      ) {
        throw 'Restored user-profile configuration does not match the saved original configuration.'
      }

      Write-Host 'Original Keycloak user-profile configuration restored.'
    }
    catch {
      $errors.Add("User-profile restoration failed: $($_.Exception.Message)")
    }
  }

  if ($errors.Count -gt 0) {
    throw ($errors -join [Environment]::NewLine)
  }
}

$headers = Get-AdminHeaders

if ($PSCmdlet.ParameterSetName -eq 'Cleanup') {
  Remove-ManagedUsersAndRestoreProfile `
    -Headers $headers `
    -Path $CleanupStatePath
  exit 0
}

$originalProfileJson = Get-UserProfileRaw -Headers $headers
$originalProfile = $originalProfileJson | ConvertFrom-Json
$originalPolicy = [string](
  Get-PropertyValue `
    -Object $originalProfile `
    -Name 'unmanagedAttributePolicy'
)

$state = [ordered]@{
  schemaVersion = 2
  realm = $Realm
  generatedAt = [DateTimeOffset]::Now.ToString('o')
  originalUserProfileJson = $originalProfileJson
  originalUnmanagedAttributePolicy = $originalPolicy
  activeUnmanagedAttributePolicy = $originalPolicy
  userProfileChanged = $false
  users = @()
  tokenClaims = @()
}

Write-State -State $state -Path $StatePath

try {
  if ($originalPolicy -notin @('ADMIN_EDIT', 'ENABLED')) {
    $null = Enable-AdminEditPolicy `
      -Headers $headers `
      -OriginalProfileJson $originalProfileJson
    $state['userProfileChanged'] = $true
    $state['activeUnmanagedAttributePolicy'] = 'ADMIN_EDIT'
    Write-State -State $state -Path $StatePath
    Write-Host "Temporary Keycloak user-profile policy enabled: ADMIN_EDIT"
  }
  else {
    Write-Host "Existing Keycloak user-profile policy supports administrator attribute writes: $originalPolicy"
  }

  foreach ($definition in $Definitions) {
    $null = Ensure-ManagedUser `
      -Headers $headers `
      -Definition $definition `
      -State $state `
      -Path $StatePath
    Write-Host "Synthetic user persisted and verified: $($definition.username)"
  }

  $claims = @()
  foreach ($definition in $Definitions) {
    $claims += Verify-TokenClaims -Definition $definition
    Write-Host "Synthetic token claims verified: $($definition.username)"
  }

  $state['tokenClaims'] = $claims
  Write-State -State $state -Path $StatePath
  Write-Host "Synthetic identity state written: $StatePath"
}
catch {
  try {
    Remove-ManagedUsersAndRestoreProfile `
      -Headers $headers `
      -Path $StatePath
  }
  catch {
    Write-Warning "Emergency cleanup/restoration failed: $($_.Exception.Message)"
  }

  throw
}
