$ErrorActionPreference = 'Stop'
$cliOutput = & npx --yes supabase@2.120.0 status -o env 2>$null
$settings = @{}
foreach ($line in $cliOutput) {
    if ($line -match '^([^=]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
}
$apiUrl = $settings['API_URL'].Trim().Trim('"')
$anonKey = $settings['ANON_KEY'].Trim().Trim('"')
$serviceKey = $settings['SERVICE_ROLE_KEY'].Trim().Trim('"')
if (-not $apiUrl -or -not $anonKey -or -not $serviceKey) {
    throw 'Supabase local no está iniciado o el CLI no devolvió sus variables locales.'
}

$tag = [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = "Local-Test-$([guid]::NewGuid().ToString('N'))!"
$users = @{}
$adminHeaders = @{
    apikey = $serviceKey
    Authorization = "Bearer $serviceKey"
    'Content-Type' = 'application/json'
}
$anonHeaders = @{ apikey = $anonKey; 'Content-Type' = 'application/json' }

function New-TestUser([string]$name) {
    $email = "release03-$name-$tag@example.test"
    $body = @{ email = $email; password = $password; email_confirm = $true } | ConvertTo-Json
    $created = Invoke-RestMethod -Method Post -Uri "$apiUrl/auth/v1/admin/users" -Headers $adminHeaders -Body $body
    $users[$name] = @{ id = $created.id; email = $email }
}

function Get-AccessToken([string]$name) {
    $body = @{ email = $users[$name].email; password = $password } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$apiUrl/auth/v1/token?grant_type=password" -Headers $anonHeaders -Body $body
    return $response.access_token
}

function Assert-RpcError([string]$name, [string]$token, [string]$expected) {
    $headers = @{ apikey = $anonKey; Authorization = "Bearer $token"; 'Content-Type' = 'application/json' }
    try {
        Invoke-RestMethod -Method Post -Uri "$apiUrl/rest/v1/rpc/registrar_visita_qr" -Headers $headers -Body '{"p_qr_token":"0000000000000000000000000000000000000000000000000000000000000000"}' | Out-Null
        throw "${name}: se esperaba $expected pero el RPC tuvo éxito."
    } catch {
        $response = $_.ErrorDetails.Message
        if (-not $response) { $response = $_.Exception.Message }
        if ($response -notmatch [regex]::Escape($expected)) { throw "${name}: error inesperado; esperado '$expected', recibido '$response'." }
        Write-Output "PASS $name -> $expected"
    }
}

try {
    New-TestUser 'admin'
    New-TestUser 'gerente-activo'
    New-TestUser 'gerente-inactivo'
    New-TestUser 'empleado'
    New-TestUser 'sin-rol'

    $adminId = $users['admin'].id
    $managerId = $users['gerente-activo'].id
    $inactiveId = $users['gerente-inactivo'].id
    $employeeId = $users['empleado'].id
    $sql = @"
INSERT INTO public.user_roles(user_id, role) VALUES
 ('$adminId', 'admin'), ('$managerId', 'gerente'), ('$inactiveId', 'gerente'), ('$employeeId', 'empleado');
"@
    $sqlPath = Join-Path $env:TEMP "release03-auth-fixtures-$tag.sql"
    Set-Content -LiteralPath $sqlPath -Value $sql -Encoding utf8
    $queryOutput = & npx --yes supabase@2.120.0 db query --local --file $sqlPath
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron crear fixtures de roles locales: $queryOutput" }

    $sql = @"
INSERT INTO public.empleados(user_id, nombre, apellido, salario, dia_pago, activo) VALUES
 ('$managerId', 'Test', 'Manager', 120000, 1, true),
 ('$inactiveId', 'Test', 'Inactive', 120000, 1, false);
"@
    Set-Content -LiteralPath $sqlPath -Value $sql -Encoding utf8
    $queryOutput = & npx --yes supabase@2.120.0 db query --local --file $sqlPath
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron crear fixtures de roles locales: $queryOutput" }
    Remove-Item -LiteralPath $sqlPath -Force

    Assert-RpcError 'admin autorizado' (Get-AccessToken 'admin') 'qr_customer_not_found'
    Assert-RpcError 'gerente activo autorizado' (Get-AccessToken 'gerente-activo') 'qr_customer_not_found'
    Assert-RpcError 'gerente inactivo denegado' (Get-AccessToken 'gerente-inactivo') 'not_authorized'
    Assert-RpcError 'empleado denegado' (Get-AccessToken 'empleado') 'not_authorized'
    Assert-RpcError 'usuario sin rol denegado' (Get-AccessToken 'sin-rol') 'not_authorized'

    $profileHeaders = @{ apikey = $anonKey; Authorization = "Bearer $(Get-AccessToken 'sin-rol')"; 'Content-Type' = 'application/json' }
    $profileBody = @{ p_nombre = "Cliente Release03-$tag"; p_telefono = $null } | ConvertTo-Json
    $profile = Invoke-RestMethod -Method Post -Uri "$apiUrl/rest/v1/rpc/ensure_customer_profile" -Headers $profileHeaders -Body $profileBody
    if (-not $profile.id) { throw 'No se creó el perfil de cliente verificado.' }
    Write-Output 'PASS cliente verificado puede crear su perfil mediante RPC'

    $ownProfile = Invoke-RestMethod -Method Get -Uri "$apiUrl/rest/v1/clientes?select=id,nombre,puntos_lealtad&user_id=eq.$($users['sin-rol'].id)" -Headers $profileHeaders
    if ($ownProfile.Count -ne 1 -or $ownProfile[0].id -ne $profile.id) { throw 'El cliente no pudo consultar únicamente su perfil.' }
    Write-Output 'PASS cliente consulta su perfil propio'

    $qr = Invoke-RestMethod -Method Post -Uri "$apiUrl/rest/v1/rpc/rotate_customer_qr_token" -Headers $profileHeaders -Body '{}'
    $managerHeaders = @{ apikey = $anonKey; Authorization = "Bearer $(Get-AccessToken 'gerente-activo')"; 'Content-Type' = 'application/json' }
    $visitBody = @{ p_qr_token = $qr.qr_token } | ConvertTo-Json
    $visit = Invoke-RestMethod -Method Post -Uri "$apiUrl/rest/v1/rpc/registrar_visita_qr" -Headers $managerHeaders -Body $visitBody
    if (-not $visit.registered -or $visit.puntos_lealtad -ne 10) { throw 'La primera visita no acreditó los 10 puntos esperados.' }
    $duplicate = Invoke-RestMethod -Method Post -Uri "$apiUrl/rest/v1/rpc/registrar_visita_qr" -Headers $managerHeaders -Body $visitBody
    if (-not $duplicate.already_registered) { throw 'La visita repetida del mismo día no fue reconocida como duplicada.' }
    $balance = Invoke-RestMethod -Method Get -Uri "$apiUrl/rest/v1/clientes?select=visitas_totales,puntos_lealtad&user_id=eq.$($users['sin-rol'].id)" -Headers $profileHeaders
    if ($balance[0].visitas_totales -ne 1 -or $balance[0].puntos_lealtad -ne 10) { throw 'El duplicado alteró el saldo o el conteo de visitas.' }
    Write-Output 'PASS QR estable + visita única diaria + ledger acredita 10 puntos una sola vez'

    $secretSelect = "$apiUrl/rest/v1/clientes?select=qr_token_hash"
    try {
        Invoke-RestMethod -Method Get -Uri $secretSelect -Headers $profileHeaders | Out-Null
        throw 'La API expuso qr_token_hash.'
    } catch {
        if ($_.ErrorDetails.Message -notmatch 'permission denied|qr_token_hash|column') { throw }
        Write-Output 'PASS PostgREST no expone qr_token_hash'
    }
}
finally {
    if ($users.Count -gt 0) {
        $ids = ($users.Values | ForEach-Object { "'$( $_.id )'" }) -join ','
        $cleanupSql = 'DO $$ BEGIN ' +
            'ALTER TABLE public.puntos_movimientos DISABLE TRIGGER USER; ' +
            'ALTER TABLE public.visitas_clientes DISABLE TRIGGER USER; ' +
            'ALTER TABLE public.canjes_recompensas DISABLE TRIGGER USER; ' +
            "DELETE FROM public.canjes_recompensas WHERE cliente_id IN (SELECT id FROM public.clientes WHERE nombre = 'Cliente Release03-$tag'); " +
            "DELETE FROM public.puntos_movimientos WHERE cliente_id IN (SELECT id FROM public.clientes WHERE nombre = 'Cliente Release03-$tag'); " +
            "DELETE FROM public.visitas_clientes WHERE cliente_id IN (SELECT id FROM public.clientes WHERE nombre = 'Cliente Release03-$tag'); " +
            "DELETE FROM public.clientes WHERE nombre = 'Cliente Release03-$tag'; " +
            "DELETE FROM public.empleados WHERE user_id IN ($ids); " +
            'ALTER TABLE public.puntos_movimientos ENABLE TRIGGER USER; ' +
            'ALTER TABLE public.visitas_clientes ENABLE TRIGGER USER; ' +
            'ALTER TABLE public.canjes_recompensas ENABLE TRIGGER USER; ' +
            'END $$;'
        $cleanupPath = Join-Path $env:TEMP "release03-auth-cleanup-$tag.sql"
        Set-Content -LiteralPath $cleanupPath -Value $cleanupSql -Encoding utf8
        try {
            $cleanupOutput = & npx --yes supabase@2.120.0 db query --local --file $cleanupPath
            if ($LASTEXITCODE -ne 0) { throw "No se pudieron limpiar los fixtures locales: $cleanupOutput" }
        } finally { Remove-Item -LiteralPath $cleanupPath -Force -ErrorAction SilentlyContinue }
    }
    foreach ($user in $users.Values) {
        try { Invoke-RestMethod -Method Delete -Uri "$apiUrl/auth/v1/admin/users/$($user.id)" -Headers $adminHeaders | Out-Null } catch { }
    }
}
