$ErrorActionPreference = "Stop"

function Import-RepoDotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Path. Copy .env.example to .env in the repository root and set local secrets."
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }

        $equalsIndex = $line.IndexOf("=")
        if ($equalsIndex -lt 1) {
            return
        }

        $name = $line.Substring(0, $equalsIndex).Trim()
        $value = $line.Substring($equalsIndex + 1).Trim()
        if ($value.Length -ge 2) {
            $first = $value[0]
            $last = $value[$value.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        Set-Item -Path "env:$name" -Value $value
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Import-RepoDotEnv -Path (Join-Path $repoRoot ".env")

function Test-KeycloakBaseUrl {
    param([Parameter(Mandatory = $true)][string]$BaseUrl)

    try {
        $wellKnown = Invoke-RestMethod `
            -Method Get `
            -Uri "$($BaseUrl.TrimEnd('/'))/realms/master/.well-known/openid-configuration" `
            -TimeoutSec 8
        return ($wellKnown.issuer -match "/realms/master")
    } catch {
        return $false
    }
}

function Resolve-KeycloakBaseUrl {
    param([int]$Port = 8080)

    if (-not [string]::IsNullOrWhiteSpace($env:KEYCLOAK_BASE_URL)) {
        $explicit = $env:KEYCLOAK_BASE_URL.Trim().TrimEnd("/")
        if (-not (Test-KeycloakBaseUrl -BaseUrl $explicit)) {
            throw "KEYCLOAK_BASE_URL is set to '$explicit' but Keycloak is not reachable there (master realm OpenID configuration failed)."
        }
        return $explicit
    }

    $localhostBase = "http://localhost:$Port"
    if (Test-KeycloakBaseUrl -BaseUrl $localhostBase) {
        return $localhostBase
    }

    $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" } |
        Select-Object -ExpandProperty IPAddress -Unique

    foreach ($address in $addresses) {
        $candidate = "http://${address}:$Port"
        if (Test-KeycloakBaseUrl -BaseUrl $candidate) {
            Write-Warning "Using $candidate for Keycloak because $localhostBase is not Keycloak (another process often Jenkins/WSL may be bound to localhost on port $Port)."
            return $candidate
        }
    }

    throw @"
Could not reach Keycloak on port $Port.

$localhostBase is in use but is not Keycloak. HTTP 403 with 'No valid crumb' usually means Jenkins (or similar) is answering on localhost:$Port while Docker publishes Keycloak on the same port via another interface.

Fix options:
  - Stop the conflicting service on localhost:$Port, then rerun this script; or
  - Set KEYCLOAK_BASE_URL in .env (example: http://172.x.x.x:$Port — use an address where /realms/master/.well-known/openid-configuration returns JSON); or
  - Change Keycloak's host port in docker-compose.yml and align the frontend Keycloak URL.

Ensure the api container is up before the final provisioning step: docker compose ps && docker compose logs api
"@
}

$requiredVariables = @(
    "KEYCLOAK_ADMIN_USERNAME",
    "KEYCLOAK_ADMIN_PASSWORD",
    "DEV_ADMIN_PASSWORD",
    "DEV_COACH_PASSWORD",
    "DEV_MEMBER_A_PASSWORD",
    "DEV_MEMBER_B_PASSWORD",
    "KEYCLOAK_PROVISIONER_SECRET",
    "PROVISIONING_TOKEN"
)

foreach ($name in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Set $name in the repository .env file (see .env.example) or in the current PowerShell session before provisioning."
    }
}

$keycloakPort = 8080
if (-not [string]::IsNullOrWhiteSpace($env:KEYCLOAK_HOST_PORT) -and $env:KEYCLOAK_HOST_PORT -match "^\d+$") {
    $keycloakPort = [int]$env:KEYCLOAK_HOST_PORT
}
$keycloakBase = Resolve-KeycloakBaseUrl -Port $keycloakPort
$apiBase = "http://localhost:8081"
$realm = "gym"
$adminTokenResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$keycloakBase/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "password"
        client_id = "admin-cli"
        username = $env:KEYCLOAK_ADMIN_USERNAME
        password = $env:KEYCLOAK_ADMIN_PASSWORD
    }
$headers = @{ Authorization = "Bearer $($adminTokenResponse.access_token)" }

$provisionerClients = Invoke-RestMethod `
    -Method Get `
    -Uri "$keycloakBase/admin/realms/$realm/clients?clientId=smartgym-provisioner" `
    -Headers $headers
if ($provisionerClients.Count -eq 0) {
    $clientBody = @{
        clientId = "smartgym-provisioner"
        name = "Smart Gym user provisioning service"
        enabled = $true
        protocol = "openid-connect"
        publicClient = $false
        clientAuthenticatorType = "client-secret"
        secret = $env:KEYCLOAK_PROVISIONER_SECRET
        standardFlowEnabled = $false
        directAccessGrantsEnabled = $false
        serviceAccountsEnabled = $true
    } | ConvertTo-Json -Depth 6
    Invoke-RestMethod `
        -Method Post `
        -Uri "$keycloakBase/admin/realms/$realm/clients" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $clientBody
    $provisionerClients = Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/clients?clientId=smartgym-provisioner" `
        -Headers $headers
} else {
    $provisionerClient = $provisionerClients | Select-Object -First 1
    $clientBody = @{
        id = $provisionerClient.id
        clientId = "smartgym-provisioner"
        name = "Smart Gym user provisioning service"
        enabled = $true
        protocol = "openid-connect"
        publicClient = $false
        clientAuthenticatorType = "client-secret"
        secret = $env:KEYCLOAK_PROVISIONER_SECRET
        standardFlowEnabled = $false
        directAccessGrantsEnabled = $false
        serviceAccountsEnabled = $true
    } | ConvertTo-Json -Depth 6
    Invoke-RestMethod `
        -Method Put `
        -Uri "$keycloakBase/admin/realms/$realm/clients/$($provisionerClient.id)" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $clientBody
    Invoke-RestMethod `
        -Method Put `
        -Uri "$keycloakBase/admin/realms/$realm/clients/$($provisionerClient.id)/client-secret" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{ value = $env:KEYCLOAK_PROVISIONER_SECRET } | ConvertTo-Json)
}

$provisionerClient = $provisionerClients | Select-Object -First 1
$serviceAccount = Invoke-RestMethod `
    -Method Get `
    -Uri "$keycloakBase/admin/realms/$realm/clients/$($provisionerClient.id)/service-account-user" `
    -Headers $headers
$realmManagement = Invoke-RestMethod `
    -Method Get `
    -Uri "$keycloakBase/admin/realms/$realm/clients?clientId=realm-management" `
    -Headers $headers | Select-Object -First 1
$managementRoles = @("manage-users", "view-users", "view-realm") | ForEach-Object {
    Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/clients/$($realmManagement.id)/roles/$_" `
        -Headers $headers
}
Invoke-RestMethod `
    -Method Post `
    -Uri "$keycloakBase/admin/realms/$realm/users/$($serviceAccount.id)/role-mappings/clients/$($realmManagement.id)" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body (ConvertTo-Json -InputObject @($managementRoles) -Depth 6)

$accounts = @(
    @{ Username = "gym-admin"; Name = "Gym Administrator"; FirstName = "Gym"; LastName = "Administrator"; Email = "gym-admin@smartgym.local"; Role = "ADMIN"; Password = $env:DEV_ADMIN_PASSWORD; ProfileType = "EMPLOYEE" },
    @{ Username = "gym-coach"; Name = "Gym Coach"; FirstName = "Gym"; LastName = "Coach"; Email = "gym-coach@smartgym.local"; Role = "COACH"; Password = $env:DEV_COACH_PASSWORD; ProfileType = "EMPLOYEE" },
    @{ Username = "member-a"; Name = "Member A"; FirstName = "Member"; LastName = "A"; Email = "member-a@smartgym.local"; Role = "MEMBER"; Password = $env:DEV_MEMBER_A_PASSWORD; ProfileType = "MEMBER" },
    @{ Username = "member-b"; Name = "Member B"; FirstName = "Member"; LastName = "B"; Email = "member-b@smartgym.local"; Role = "MEMBER"; Password = $env:DEV_MEMBER_B_PASSWORD; ProfileType = "MEMBER" }
)

$provisioned = @{}
foreach ($account in $accounts) {
    $encodedUsername = [Uri]::EscapeDataString($account.Username)
    $users = Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/users?username=$encodedUsername&exact=true" `
        -Headers $headers

    if ($users.Count -eq 0) {
        $userBody = @{
            username = $account.Username
            enabled = $true
            emailVerified = $true
            email = $account.Email
            firstName = $account.FirstName
            lastName = $account.LastName
            attributes = @{ developmentOnly = @("true") }
            credentials = @(@{ type = "password"; value = $account.Password; temporary = $false })
        } | ConvertTo-Json -Depth 8
        Invoke-RestMethod `
            -Method Post `
            -Uri "$keycloakBase/admin/realms/$realm/users" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $userBody
        $users = Invoke-RestMethod `
            -Method Get `
            -Uri "$keycloakBase/admin/realms/$realm/users?username=$encodedUsername&exact=true" `
            -Headers $headers
    } else {
        $userId = $users[0].id
        $updateBody = @{
            enabled = $true
            emailVerified = $true
            email = $account.Email
            firstName = $account.FirstName
            lastName = $account.LastName
            attributes = @{ developmentOnly = @("true") }
        } | ConvertTo-Json -Depth 8
        Invoke-RestMethod `
            -Method Put `
            -Uri "$keycloakBase/admin/realms/$realm/users/$userId" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $updateBody
        Invoke-RestMethod `
            -Method Put `
            -Uri "$keycloakBase/admin/realms/$realm/users/$userId/reset-password" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body (@{ type = "password"; value = $account.Password; temporary = $false } | ConvertTo-Json)
    }

    $user = $users | Select-Object -First 1
    $provisioned[$account.Username] = $user
    $role = Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/roles/$($account.Role)" `
        -Headers $headers
    $roleMappingBody = ConvertTo-Json -InputObject @($role) -Depth 5
    Invoke-RestMethod `
        -Method Post `
        -Uri "$keycloakBase/admin/realms/$realm/users/$($user.id)/role-mappings/realm" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $roleMappingBody
}

$profilePayload = @{
    profiles = @(
        @{ subject = $provisioned["gym-admin"].id; username = "gym-admin"; displayName = "Gym Administrator"; profileType = "EMPLOYEE" },
        @{ subject = $provisioned["gym-coach"].id; username = "gym-coach"; displayName = "Gym Coach"; profileType = "EMPLOYEE" },
        @{ subject = $provisioned["member-a"].id; username = "member-a"; displayName = "Member A"; profileType = "MEMBER" },
        @{ subject = $provisioned["member-b"].id; username = "member-b"; displayName = "Member B"; profileType = "MEMBER" }
    )
    coachAssignments = @(
        @{ coachSubject = $provisioned["gym-coach"].id; memberSubject = $provisioned["member-a"].id },
        @{ coachSubject = $provisioned["gym-coach"].id; memberSubject = $provisioned["member-b"].id }
    )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
    -Method Post `
    -Uri "$apiBase/api/dev/provision" `
    -Headers @{ "X-Provisioning-Key" = $env:PROVISIONING_TOKEN } `
    -ContentType "application/json" `
    -Body $profilePayload | Out-Null

Write-Output "Provisioned development-only accounts and business profiles:"
$accounts | ForEach-Object { Write-Output " - $($_.Username) ($($_.Role))" }
