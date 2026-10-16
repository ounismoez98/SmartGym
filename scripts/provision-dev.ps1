$ErrorActionPreference = "Stop"

$requiredVariables = @(
    "KEYCLOAK_ADMIN_USERNAME",
    "KEYCLOAK_ADMIN_PASSWORD",
    "DEV_ADMIN_PASSWORD",
    "DEV_COACH_PASSWORD",
    "DEV_MEMBER_A_PASSWORD",
    "DEV_MEMBER_B_PASSWORD",
    "PROVISIONING_TOKEN"
)

foreach ($name in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Set $name in the current PowerShell environment before provisioning."
    }
}

$keycloakBase = "http://localhost:8080"
$apiBase = "http://localhost:8081"
$realm = "smart-gym"
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
