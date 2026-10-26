$ErrorActionPreference = "Stop"

function Import-RepoDotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Path. Copy .env.example to .env and set local-only credentials."
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            continue
        }

        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "env:$name" -Value $value
    }
}

function Get-KeycloakUser {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    $encodedUsername = [Uri]::EscapeDataString($Username)
    $users = @(Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/users?username=$encodedUsername&exact=true" `
        -Headers $Headers)
    if ($users.Count -gt 1) {
        throw "More than one Keycloak user has the username '$Username'."
    }
    if ($users.Count -eq 0) {
        return $null
    }
    return $users[0]
}

function Ensure-DemoAccount {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$FirstName,
        [Parameter(Mandatory = $true)][string]$LastName,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][string]$ProfileType,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    $user = Get-KeycloakUser -Username $Username -Headers $Headers
    if ($null -eq $user) {
        $body = @{
            username = $Username
            enabled = $true
            emailVerified = $true
            firstName = $FirstName
            lastName = $LastName
            email = "$Username@smartgym.local"
            attributes = @{
                developmentOnly = @("true")
                projectDemoSeed = @("true")
            }
            credentials = @(@{
                type = "password"
                value = $env:SEED_ACCOUNT_PASSWORD
                temporary = $false
            })
        } | ConvertTo-Json -Depth 8
        Invoke-RestMethod `
            -Method Post `
            -Uri "$keycloakBase/admin/realms/$realm/users" `
            -Headers $Headers `
            -ContentType "application/json" `
            -Body $body
        $user = Get-KeycloakUser -Username $Username -Headers $Headers
        if ($null -eq $user) {
            throw "Keycloak did not return the created account '$Username'."
        }
    } elseif ($Username.StartsWith("demo-")) {
        $isSeedAccount = @($user.attributes.projectDemoSeed) -contains "true"
        if (-not $isSeedAccount) {
            throw "Refusing to change an existing unmarked account named '$Username'. Rename it or review it manually."
        }
        Invoke-RestMethod `
            -Method Put `
            -Uri "$keycloakBase/admin/realms/$realm/users/$($user.id)/reset-password" `
            -Headers $Headers `
            -ContentType "application/json" `
            -Body (@{
                type = "password"
                value = $env:SEED_ACCOUNT_PASSWORD
                temporary = $false
            } | ConvertTo-Json)
    }

    $realmRoles = @(Invoke-RestMethod `
        -Method Get `
        -Uri "$keycloakBase/admin/realms/$realm/users/$($user.id)/role-mappings/realm" `
        -Headers $Headers)
    if (-not ($realmRoles | Where-Object name -eq $Role)) {
        $roleRepresentation = Invoke-RestMethod `
            -Method Get `
            -Uri "$keycloakBase/admin/realms/$realm/roles/$Role" `
            -Headers $Headers
        Invoke-RestMethod `
            -Method Post `
            -Uri "$keycloakBase/admin/realms/$realm/users/$($user.id)/role-mappings/realm" `
            -Headers $Headers `
            -ContentType "application/json" `
            -Body (ConvertTo-Json -InputObject @($roleRepresentation) -Depth 5)
    }

    return [pscustomobject]@{
        subject = $user.id
        username = $Username
        displayName = "$FirstName $LastName"
        profileType = $ProfileType
    }
}

function Invoke-ComposeSql {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Sql
    )

    $temporarySql = [IO.Path]::GetTempFileName()
    try {
        Set-Content -LiteralPath $temporarySql -Value $Sql -Encoding UTF8
        Get-Content -LiteralPath $temporarySql -Raw |
            docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U $Username -d $Database -f -
        if ($LASTEXITCODE -ne 0) {
            throw "Seed SQL failed for database '$Database' (psql exit code $LASTEXITCODE)."
        }
    } finally {
        Remove-Item -LiteralPath $temporarySql -Force -ErrorAction SilentlyContinue
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Import-RepoDotEnv -Path (Join-Path $repoRoot ".env")
if ((Resolve-Path -LiteralPath ".").Path -ne $repoRoot) {
    throw "Run this script from the repository root so Docker Compose uses the intended local project."
}

foreach ($name in @(
    "KEYCLOAK_ADMIN_USERNAME",
    "KEYCLOAK_ADMIN_PASSWORD",
    "PROVISIONING_TOKEN",
    "SEED_ACCOUNT_PASSWORD"
)) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Set $name in repository .env before running this development-only seed."
    }
}
if ($env:SEED_ACCOUNT_PASSWORD.Length -lt 12 -or $env:SEED_ACCOUNT_PASSWORD.Length -gt 72) {
    throw "SEED_ACCOUNT_PASSWORD must be between 12 and 72 characters."
}

$keycloakPort = 8180
if ($env:KEYCLOAK_HOST_PORT -match "^\d+$") {
    $keycloakPort = [int]$env:KEYCLOAK_HOST_PORT
}
$keycloakBase = if (-not [string]::IsNullOrWhiteSpace($env:KEYCLOAK_BASE_URL)) {
    $env:KEYCLOAK_BASE_URL.TrimEnd("/")
} else {
    "http://localhost:$keycloakPort"
}
$apiBase = "http://localhost:8081"
$realm = "gym"
$trainingDbUser = if ([string]::IsNullOrWhiteSpace($env:TRAINING_DB_USER)) {
    "training_service"
} else {
    $env:TRAINING_DB_USER
}

try {
    $null = Invoke-RestMethod -Method Get -Uri "$keycloakBase/realms/master/.well-known/openid-configuration" -TimeoutSec 8
    $null = Invoke-RestMethod -Method Get -Uri "$apiBase/v3/api-docs" -TimeoutSec 8
} catch {
    throw "Keycloak or the membership API is not reachable. Start the local Compose stack before seeding."
}

$activePlanCount = docker compose exec -T postgres psql -U smartgym -d smartgym -Atc `
    "SELECT count(*) FROM subscription_plan WHERE active = TRUE;"
if ($LASTEXITCODE -ne 0 -or [int]$activePlanCount -lt 1) {
    throw "The local membership database must have at least one active subscription plan before seeding."
}
$trainingDatabaseCheck = docker compose exec -T postgres psql -U $trainingDbUser -d training_db -Atc "SELECT 1;"
if ($LASTEXITCODE -ne 0 -or $trainingDatabaseCheck.Trim() -ne "1") {
    throw "The local training_db is unavailable. Start the Compose training-db-init service before seeding."
}

$adminToken = Invoke-RestMethod `
    -Method Post `
    -Uri "$keycloakBase/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "password"
        client_id = "admin-cli"
        username = $env:KEYCLOAK_ADMIN_USERNAME
        password = $env:KEYCLOAK_ADMIN_PASSWORD
    }
$headers = @{ Authorization = "Bearer $($adminToken.access_token)" }

$accounts = [Collections.Generic.List[object]]::new()
$accounts.Add((Ensure-DemoAccount -Username "gym-admin" -FirstName "Gym" -LastName "Administrator" -Role "ADMIN" -ProfileType "EMPLOYEE" -Headers $headers))
$accounts.Add((Ensure-DemoAccount -Username "gym-coach" -FirstName "Gym" -LastName "Coach" -Role "COACH" -ProfileType "EMPLOYEE" -Headers $headers))
$accounts.Add((Ensure-DemoAccount -Username "member-a" -FirstName "Member" -LastName "A" -Role "MEMBER" -ProfileType "MEMBER" -Headers $headers))
$accounts.Add((Ensure-DemoAccount -Username "member-b" -FirstName "Member" -LastName "B" -Role "MEMBER" -ProfileType "MEMBER" -Headers $headers))
$accounts.Add((Ensure-DemoAccount -Username "demo-coach-01" -FirstName "Demo" -LastName "Coach" -Role "COACH" -ProfileType "EMPLOYEE" -Headers $headers))

for ($index = 1; $index -le 15; $index++) {
    $suffix = "{0:D2}" -f $index
    $accounts.Add((Ensure-DemoAccount `
        -Username "demo-member-$suffix" `
        -FirstName "Demo" `
        -LastName "Member $suffix" `
        -Role "MEMBER" `
        -ProfileType "MEMBER" `
        -Headers $headers))
}

if ($accounts.Count -ne 20) {
    throw "Expected exactly 20 development accounts but prepared $($accounts.Count)."
}

$provisioningProfiles = @($accounts | ForEach-Object {
    @{
        subject = $_.subject
        username = $_.username
        displayName = $_.displayName
        profileType = $_.profileType
    }
})
$coaches = @($provisioningProfiles | Where-Object profileType -eq "EMPLOYEE")
$members = @($provisioningProfiles | Where-Object profileType -eq "MEMBER")
$assignments = for ($index = 0; $index -lt $members.Count; $index++) {
    @{
        coachSubject = $coaches[1 + ($index % 2)].subject
        memberSubject = $members[$index].subject
    }
}
$profilePayload = @{
    profiles = $provisioningProfiles
    coachAssignments = @($assignments)
} | ConvertTo-Json -Depth 8

$provisioned = Invoke-RestMethod `
    -Method Post `
    -Uri "$apiBase/api/dev/provision" `
    -Headers @{ "X-Provisioning-Key" = $env:PROVISIONING_TOKEN } `
    -ContentType "application/json" `
    -Body $profilePayload

$profilesByUsername = @{}
foreach ($profile in $provisioned.profiles) {
    $profilesByUsername[$profile.username] = $profile
}
foreach ($account in $accounts) {
    if (-not $profilesByUsername.ContainsKey($account.username)) {
        throw "Membership provisioning did not return profile '$($account.username)'."
    }
    $profile = $profilesByUsername[$account.username]
    if ($account.profileType -eq "MEMBER" -and -not $profile.memberProfileId) {
        throw "Membership provisioning did not return a member profile for '$($account.username)'."
    }
    if ($account.profileType -eq "EMPLOYEE" -and -not $profile.employeeProfileId) {
        throw "Membership provisioning did not return an employee profile for '$($account.username)'."
    }
}

foreach ($account in $provisioningProfiles) {
    if ($account.subject -notmatch "^[0-9a-fA-F-]{36}$") {
        throw "Membership provisioning returned an invalid subject for '$($account.username)'."
    }
}

$memberRows = [Collections.Generic.List[string]]::new()
$index = 0
foreach ($profile in $members) {
    $index++
    $resolved = $profilesByUsername[$profile.username]
    if ($resolved.memberProfileId -notmatch "^[0-9a-fA-F-]{36}$") {
        throw "Membership provisioning returned an invalid profile identifier for '$($profile.username)'."
    }
    $memberRows.Add("($index, '$($resolved.memberProfileId)'::uuid, '$($profile.subject)', '$($profile.displayName.Replace("'", "''"))')")
}
$adminSubject = ($provisioningProfiles | Where-Object username -eq "gym-admin").subject
if ($adminSubject -notmatch "^[0-9a-fA-F-]{36}$") {
    throw "The administrator subject is not a valid UUID."
}
$membershipSql = @"
BEGIN;
CREATE TEMP TABLE seed_members (
    seed_index integer PRIMARY KEY,
    member_id uuid NOT NULL,
    subject varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL
) ON COMMIT DROP;
INSERT INTO seed_members VALUES
$($memberRows -join ",`n");

WITH plan AS (
    SELECT id, price_cents, currency FROM subscription_plan
    WHERE active = TRUE ORDER BY code LIMIT 1
), seed AS (
    SELECT m.*, p.id AS plan_id,
           (60 + floor(random() * 240)::int) AS days_ago,
           ('71000000-0000-4000-8000-' || lpad(m.seed_index::text, 12, '0'))::uuid AS subscription_id
    FROM seed_members m CROSS JOIN plan p
)
INSERT INTO subscription (id, member_id, plan_id, starts_at, ends_at, status, created_at, updated_at)
SELECT subscription_id, member_id, plan_id,
       CURRENT_TIMESTAMP - days_ago * INTERVAL '1 day',
       CURRENT_TIMESTAMP - (days_ago - 30) * INTERVAL '1 day',
       'EXPIRED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM seed
ON CONFLICT (id) DO UPDATE SET member_id = EXCLUDED.member_id, plan_id = EXCLUDED.plan_id,
    starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at, status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

WITH seed AS (
    SELECT m.*, s.id AS subscription_id, p.price_cents, p.currency,
           ('72000000-0000-4000-8000-' || lpad(m.seed_index::text, 12, '0'))::uuid AS payment_id
    FROM seed_members m
    JOIN subscription s ON s.id = ('71000000-0000-4000-8000-' || lpad(m.seed_index::text, 12, '0'))::uuid
    JOIN subscription_plan p ON p.id = s.plan_id
)
INSERT INTO payment (id, member_id, subscription_id, amount_cents, currency, method, status,
                     corrects_payment_id, reason, created_by, created_at, paid_at)
SELECT payment_id, member_id, subscription_id, price_cents, currency,
       (ARRAY['CASH', 'CARD', 'TRANSFER'])[1 + floor(random() * 3)::int],
       'RECORDED', NULL, NULL, '$adminSubject',
       CURRENT_TIMESTAMP - (30 + floor(random() * 200)::int) * INTERVAL '1 day',
       CURRENT_TIMESTAMP - (30 + floor(random() * 200)::int) * INTERVAL '1 day'
FROM seed
ON CONFLICT (id) DO UPDATE SET member_id = EXCLUDED.member_id, subscription_id = EXCLUDED.subscription_id,
    amount_cents = EXCLUDED.amount_cents, currency = EXCLUDED.currency, method = EXCLUDED.method,
    created_by = EXCLUDED.created_by, paid_at = EXCLUDED.paid_at;

WITH seed AS (
    SELECT m.*, ('73000000-0000-4000-8000-' || lpad(m.seed_index::text, 12, '0'))::uuid AS feedback_id,
           (2 + floor(random() * 4)::int) AS rating
    FROM seed_members m
)
INSERT INTO feedbacks (id, keycloak_subject, member_name, rating, category, comment, sentiment,
                       summary, created_at, urgency, action_recommendation, confidence_score)
SELECT feedback_id, subject, display_name, rating,
       (ARRAY['CLEANLINESS', 'EQUIPMENT', 'CLASSES', 'STAFF', 'SERVICE'])[1 + floor(random() * 5)::int],
       (ARRAY[
           'The gym has a welcoming atmosphere and helpful staff.',
           'The equipment was available and the training area felt clean.',
           'The class schedule could offer more evening options.',
           'I had a positive experience during my visit.'
       ])[1 + floor(random() * 4)::int],
       CASE WHEN rating >= 4 THEN 'POSITIVE' WHEN rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END,
       'Development sample feedback; synthetic data.',
       CURRENT_TIMESTAMP - floor(random() * 120)::int * INTERVAL '1 day',
       CASE WHEN rating <= 2 THEN 'MEDIUM' ELSE 'LOW' END,
       'Review this synthetic sample with the member experience team.',
       0.80 + random() * 0.19
FROM seed
ON CONFLICT (id) DO UPDATE SET keycloak_subject = EXCLUDED.keycloak_subject,
    member_name = EXCLUDED.member_name, rating = EXCLUDED.rating, category = EXCLUDED.category,
    comment = EXCLUDED.comment, sentiment = EXCLUDED.sentiment, summary = EXCLUDED.summary,
    created_at = EXCLUDED.created_at, urgency = EXCLUDED.urgency,
    action_recommendation = EXCLUDED.action_recommendation, confidence_score = EXCLUDED.confidence_score;

DELETE FROM feedback_themes
WHERE feedback_id IN (
    SELECT ('73000000-0000-4000-8000-' || lpad(seed_index::text, 12, '0'))::uuid
    FROM seed_members
);
INSERT INTO feedback_themes (feedback_id, theme)
SELECT ('73000000-0000-4000-8000-' || lpad(seed_index::text, 12, '0'))::uuid,
       (ARRAY['Staff', 'Equipment', 'Cleanliness', 'Classes', 'Facilities'])[1 + floor(random() * 5)::int]
FROM seed_members;

WITH seed AS (
    SELECT m.*, 880000 + seed_index AS food_id
    FROM seed_members m
)
INSERT INTO nutrition_foods (id, brand, calories, carbohydrate_grams, fat_grams, fiber_grams,
                             name, protein_grams, serving_size_grams, sodium_mg, subject)
SELECT food_id, 'SmartGym Demo Kitchen',
       180 + floor(random() * 250), 15 + floor(random() * 35), 3 + floor(random() * 18),
       2 + floor(random() * 9), 'Demo grain bowl ' || seed_index,
       8 + floor(random() * 28), 100, 80 + floor(random() * 500), subject
FROM seed
ON CONFLICT (id) DO UPDATE SET brand = EXCLUDED.brand, calories = EXCLUDED.calories,
    carbohydrate_grams = EXCLUDED.carbohydrate_grams, fat_grams = EXCLUDED.fat_grams,
    fiber_grams = EXCLUDED.fiber_grams, name = EXCLUDED.name, protein_grams = EXCLUDED.protein_grams,
    serving_size_grams = EXCLUDED.serving_size_grams, sodium_mg = EXCLUDED.sodium_mg,
    subject = EXCLUDED.subject;

WITH seed AS (
    SELECT m.*, 880000 + seed_index AS food_id, 890000 + seed_index AS journal_id
    FROM seed_members m
)
INSERT INTO nutrition_food_journal (id, created_at, entry_date, food_id, meal_type, notes, servings, subject)
SELECT journal_id, CURRENT_TIMESTAMP,
       CURRENT_DATE - floor(random() * 30)::int,
       food_id, (ARRAY['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'])[1 + floor(random() * 4)::int],
       'Development sample journal entry; synthetic data.', 0.75 + random() * 1.5, subject
FROM seed
ON CONFLICT (id) DO UPDATE SET created_at = EXCLUDED.created_at, entry_date = EXCLUDED.entry_date,
    food_id = EXCLUDED.food_id, meal_type = EXCLUDED.meal_type, notes = EXCLUDED.notes,
    servings = EXCLUDED.servings, subject = EXCLUDED.subject;

WITH seed AS (
    SELECT m.*, 900000 + seed_index AS hydration_id
    FROM seed_members m
)
INSERT INTO nutrition_hydration_logs (id, subject, consumed_at, water_ml)
SELECT hydration_id, subject,
       CURRENT_TIMESTAMP - floor(random() * 20)::int * INTERVAL '1 day',
       200 + floor(random() * 1000)::int
FROM seed
ON CONFLICT (id) DO UPDATE SET subject = EXCLUDED.subject,
    consumed_at = EXCLUDED.consumed_at, water_ml = EXCLUDED.water_ml;
COMMIT;

SELECT setval(pg_get_serial_sequence('public.nutrition_foods', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM nutrition_foods), 1), TRUE);
SELECT setval(pg_get_serial_sequence('public.nutrition_food_journal', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM nutrition_food_journal), 1), TRUE);
SELECT setval(pg_get_serial_sequence('public.nutrition_hydration_logs', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM nutrition_hydration_logs), 1), TRUE);
"@

$trainingMembers = [Collections.Generic.List[string]]::new()
$index = 0
foreach ($profile in $members) {
    $index++
    $resolved = $profilesByUsername[$profile.username]
    $trainingMembers.Add("($index, '$($resolved.memberProfileId)'::uuid)")
}
$trainingSql = @"
BEGIN;
CREATE TEMP TABLE seed_members (seed_index integer PRIMARY KEY, member_id uuid NOT NULL) ON COMMIT DROP;
INSERT INTO seed_members VALUES
$($trainingMembers -join ",`n");

INSERT INTO exercises (id, name, description, instructions, primary_muscle_group, difficulty,
                       equipment_category, active, created_at, updated_at, version)
VALUES
    ('16000000-0000-4000-8000-000000000001', 'Demo bodyweight squat', 'Synthetic demo lower-body exercise.',
     'Stand comfortably, sit back under control, and return to standing.', 'QUADRICEPS', 'BEGINNER',
     'BODYWEIGHT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('16000000-0000-4000-8000-000000000002', 'Demo push-up', 'Synthetic demo upper-body exercise.',
     'Keep a steady body line and press with control.', 'CHEST', 'BEGINNER',
     'BODYWEIGHT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('16000000-0000-4000-8000-000000000003', 'Demo dumbbell row', 'Synthetic demo pulling exercise.',
     'Brace comfortably and row the weight under control.', 'BACK', 'BEGINNER',
     'DUMBBELL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
    instructions = EXCLUDED.instructions, primary_muscle_group = EXCLUDED.primary_muscle_group,
    difficulty = EXCLUDED.difficulty, equipment_category = EXCLUDED.equipment_category,
    active = TRUE, updated_at = CURRENT_TIMESTAMP;

WITH seed AS (
    SELECT m.*, ('74000000-0000-4000-8000-' || lpad(seed_index::text, 12, '0'))::uuid AS workout_id
    FROM seed_members m
)
INSERT INTO workout_logs (id, member_id, programme_id, programme_session_id, performed_at,
                          notes, created_at, updated_at, version)
SELECT workout_id, member_id, NULL, NULL,
       CURRENT_TIMESTAMP - floor(random() * 28)::int * INTERVAL '1 day',
       'Development sample workout; synthetic data.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM seed
ON CONFLICT (id) DO UPDATE SET member_id = EXCLUDED.member_id, programme_id = NULL,
    programme_session_id = NULL, performed_at = EXCLUDED.performed_at, notes = EXCLUDED.notes,
    updated_at = CURRENT_TIMESTAMP;

WITH seed AS (
    SELECT m.seed_index, set_number,
           ('75000000-0000-4000-8000-' || lpad((m.seed_index * 10 + set_number)::text, 12, '0'))::uuid AS set_id,
           ('74000000-0000-4000-8000-' || lpad(m.seed_index::text, 12, '0'))::uuid AS workout_id,
           (ARRAY[
               '16000000-0000-4000-8000-000000000001'::uuid,
               '16000000-0000-4000-8000-000000000002'::uuid,
               '16000000-0000-4000-8000-000000000003'::uuid
           ])[set_number] AS exercise_id
    FROM seed_members m
    CROSS JOIN generate_series(1, 3) AS set_number
)
INSERT INTO workout_sets (id, workout_log_id, exercise_id, set_number, repetitions,
                          duration_seconds, load_kg, notes)
SELECT set_id, workout_id, exercise_id, set_number, 8 + floor(random() * 8)::int,
       NULL, 5 + floor(random() * 30)::int, 'Synthetic demo set.'
FROM seed
ON CONFLICT (id) DO UPDATE SET workout_log_id = EXCLUDED.workout_log_id,
    exercise_id = EXCLUDED.exercise_id, set_number = EXCLUDED.set_number,
    repetitions = EXCLUDED.repetitions, duration_seconds = NULL,
    load_kg = EXCLUDED.load_kg, notes = EXCLUDED.notes;
COMMIT;
"@

Invoke-ComposeSql -Database "smartgym" -Username "smartgym" -Sql $membershipSql
Invoke-ComposeSql -Database "training_db" -Username $trainingDbUser -Sql $trainingSql

Write-Output "Seed complete: 20 development accounts and 190 synthetic database records."
Write-Output "Account mix: 1 admin, 2 coaches, and 17 members."
Write-Output "New demo accounts use the shared SEED_ACCOUNT_PASSWORD from the ignored .env file."
Write-Output "The seed is additive and repeatable; it does not delete existing records or reset the four base account passwords."
