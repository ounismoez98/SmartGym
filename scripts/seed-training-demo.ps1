$ErrorActionPreference = "Stop"

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"')
        }
    }
    return $values
}

function Get-RequiredValue {
    param(
        [hashtable]$Values,
        [string]$Name
    )

    $value = $Values[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing $Name in .env. Set it before provisioning demo data."
    }
    return $value
}

$environment = Read-DotEnv (Join-Path (Get-Location) ".env")
$adminUsername = Get-RequiredValue $environment "KEYCLOAK_ADMIN_USERNAME"
$adminPassword = Get-RequiredValue $environment "KEYCLOAK_ADMIN_PASSWORD"
$provisioningToken = Get-RequiredValue $environment "PROVISIONING_TOKEN"
$trainingDbUser = $environment["TRAINING_DB_USER"]
if ([string]::IsNullOrWhiteSpace($trainingDbUser)) {
    $trainingDbUser = "training_service"
}

$keycloakBase = "http://localhost:8080"
$realm = "smart-gym"
$adminToken = Invoke-RestMethod `
    -Method Post `
    -Uri "$keycloakBase/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "password"
        client_id = "admin-cli"
        username = $adminUsername
        password = $adminPassword
    }
$headers = @{ Authorization = "Bearer $($adminToken.access_token)" }

$accounts = @(
    @{ Username = "gym-coach"; DisplayName = "Gym Coach"; ProfileType = "EMPLOYEE" },
    @{ Username = "member-a"; DisplayName = "Member A"; ProfileType = "MEMBER" },
    @{ Username = "member-b"; DisplayName = "Member B"; ProfileType = "MEMBER" }
)
$usersByName = @{}
foreach ($account in $accounts) {
    $encodedUsername = [Uri]::EscapeDataString($account.Username)
    $users = Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/users?username=$encodedUsername&exact=true" `
        -Headers $headers
    $user = $users | Select-Object -First 1
    if ($null -eq $user) {
        throw "Keycloak account '$($account.Username)' does not exist. Run scripts\provision-dev.ps1 first."
    }
    $usersByName[$account.Username] = $user
}

$profilePayload = @{
    profiles = @(
        @{
            subject = $usersByName["gym-coach"].id
            username = "gym-coach"
            displayName = "Gym Coach"
            profileType = "EMPLOYEE"
        },
        @{
            subject = $usersByName["member-a"].id
            username = "member-a"
            displayName = "Member A"
            profileType = "MEMBER"
        },
        @{
            subject = $usersByName["member-b"].id
            username = "member-b"
            displayName = "Member B"
            profileType = "MEMBER"
        }
    )
    coachAssignments = @(
        @{
            coachSubject = $usersByName["gym-coach"].id
            memberSubject = $usersByName["member-a"].id
        },
        @{
            coachSubject = $usersByName["gym-coach"].id
            memberSubject = $usersByName["member-b"].id
        }
    )
} | ConvertTo-Json -Depth 8

$provisionedProfiles = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/api/dev/provision" `
    -Headers @{ "X-Provisioning-Key" = $provisioningToken } `
    -ContentType "application/json" `
    -Body $profilePayload

$coach = $provisionedProfiles.profiles | Where-Object username -eq "gym-coach" | Select-Object -First 1
$memberA = $provisionedProfiles.profiles | Where-Object username -eq "member-a" | Select-Object -First 1
$memberB = $provisionedProfiles.profiles | Where-Object username -eq "member-b" | Select-Object -First 1
if (-not $coach.employeeProfileId -or -not $memberA.memberProfileId -or -not $memberB.memberProfileId) {
    throw "Membership API did not return the expected profile identifiers. Rebuild the api service with the current source and retry."
}

$sqlPath = Join-Path (Get-Location) "scripts\seed-training-demo.sql"
Get-Content -Raw $sqlPath | docker compose exec -T postgres psql `
    -U $trainingDbUser `
    -d training_db `
    -v "coach_id=$($coach.employeeProfileId)" `
    -v "member_a_id=$($memberA.memberProfileId)" `
    -v "member_b_id=$($memberB.memberProfileId)" `
    -v "coach_subject=$($coach.subject)" `
    -f -
if ($LASTEXITCODE -ne 0) {
    throw "Training demo-data seeding failed (psql exit code $LASTEXITCODE)."
}

Write-Output "Seeded the development exercise catalogue, coach review states, published programmes, and Member A workout history."
Write-Output "Sign in as gym-coach to explore assigned-member programme/review actions."
Write-Output "Sign in as member-a or member-b to explore published programmes; member-a has sample workout history."
