# Release 03 — Seguridad de backend y datos

**Estado:** migraciones aplicadas desde cero en Supabase local y pruebas ACL locales aprobadas; no desplegada.  
**Auditoría remota:** 2026-10-17, Supabase CLI 2.120.0, consultas de solo lectura.  
**Rama:** `dev` · **Base local/remota anterior:** 27/27 migraciones iguales, última `20261013212639`.  
**Propiedad:** backend SQL y contrato (Codex); no se cambiaron UI, Room ni Gradle.

## Evidencia de auditoría remota

- PostgreSQL remoto `17.6`; las tres migraciones anteriores al paquete no tienen discrepancias de historial local/remoto.
- `public.clientes` tenía grants a `anon` y `authenticated` que incluían DML, `TRUNCATE`, `TRIGGER`, `REFERENCES` y `MAINTAIN`. La política permisiva de `clientes_update_own` dejaba actualizar todas las columnas; `clientes_staff_update` también se combinaba por `OR`.
- `private_sync.capture_change()` guardaba `to_jsonb(NEW/OLD)` íntegro y `sync_pull` devolvía el JSON de eventos históricos sin filtrar `qr_token_hash`.
- `auth.users` tenía `on_auth_user_created_set_role → handle_new_user_role`, que asignaba `empleado` a todo signup.
- `registrar_visita_qr(text)` autorizaba `admin`/`empleado`, no al gerente activo, y actualizaba el saldo directamente sin ledger.
- No había clientes ni visitas; saldo de puntos total y visitas eran cero. No hubo que generar saldos históricos de apertura en remoto.
- Hay tres perfiles de empleado con `user_id`; no había vínculos duplicados. No se leyeron ni exportaron correos o secretos de Auth.
- No se detectaron vistas en `public` o `private_sync`. Los defaults de `postgres` y `supabase_admin` otorgaban ACL amplias de tabla/secuencia/función a `anon` y `authenticated`.
- La migración limita ACL por defecto para objetos creados por `postgres`, que es el rol ejecutor local. No cambia los defaults de `supabase_admin`: PostgreSQL rechazó ese cambio porque el migrador no tiene permiso para administrar los privilegios por defecto de ese rol. Este hallazgo remoto requiere decidir el mecanismo autorizado para corregirlo antes del despliegue.

## Cambios preparados

1. `supabase/migrations/20261017205614_release03_security_auth_loyalty_payroll_orders.sql`
   - Elimina políticas cliente antiguas y otorga solo SELECT de columnas explícitas a `authenticated`; `qr_token_hash` no tiene SELECT.
   - Revoca privilegios peligrosos y secuencias de roles de cliente, endurece `venta_diaria`, quita permisos Data API de funciones trigger/legacy y ajusta privilegios por defecto.
   - Sanitiza eventos nuevos e históricos de cliente en `sync_pull`, con lectura restringida a `admin` y `gerente` activo.
   - Quita el trigger que otorgaba rol de empleado al signup. Comprueba duplicados antes del índice único parcial de `clientes.user_id`.
2. `supabase/migrations/20261017205743_release03_payroll_and_order_price_guards.sql`
   - Índice único parcial para identidad Auth de empleados y RPC de salario con `expectedVersion`; gerente solo puede afectar filas piso sin vínculo Auth.
   - Nómina usa el salario de servidor de empleado activo y la unicidad semanal ya existente.
   - Endurece `sync_execute_v2`: no permite escrituras genéricas de perfil/QR, reclasificación de empleados o cambios de precio histórico. Inserciones de detalle exigen producto activo y precio del catálogo bajo bloqueo.
   - Agrega snapshots financieros de orden y ventas del día, con índices consultables.
3. `supabase/migrations/20261017205843_release03_loyalty_ledger_customer_profiles_rewards.sql`
   - Añade ledger append-only, catálogo de recompensas y recibos de canje; el saldo se actualiza transaccionalmente y nunca desde cliente.
   - Añade ensure/update de perfil verificado, emisión/rotación QR opaca y RPC de visita/canje para `admin`/gerente activo; incluye ajuste de puntos solo para admin, trazado e idempotente.
   - Mantiene visitas y puntos online-only y una visita por cuenta por fecha de negocio CDMX.
4. `supabase/tests/release03_security_acl.test.sql` verifica ACL y permisos de RPC clave.
5. `supabase/config.toml` pide confirmación de email, contraseña mínima de 8 caracteres, signup email habilitado, signup anónimo deshabilitado y timebox local de 10 horas.
6. `docs/backend-contract.md` incorpora el contrato Release 03 y diferencia claramente el estado local del remoto.

## Validación realizada / límites

- `npx --yes supabase@2.120.0 --help`, `db query --help`, `migration new --help`: consultados antes de usar los subcomandos.
- `npx --yes supabase@2.120.0 migration list --linked`: confirmó 27/27 previas sincronizadas; ahora lista las tres nuevas como local-only.
- Consultas remotas de catálogo, ACL, RLS, políticas, triggers, propietarios/search_path, vistas, sequences, ACL defaults y agregados de lealtad: solo lectura.
- `npx --yes supabase@2.120.0 db reset --local --yes`: aprobado el 2026-10-17; reconstruyó la base local desde las 30 migraciones y aplicó las tres migraciones release-03. El primer intento detectó el cambio de default privileges sobre `supabase_admin`; se eliminó de la migración y el reset completo posterior pasó. No se ejecutó `db reset` vinculado.
- `npx --yes supabase@2.120.0 test db --local`: PASS, 1 archivo pgTAP, 20/20 pruebas (`release03_security_acl.test.sql`).
- `supabase/tests/release03_local_auth_api_test.ps1`: PASS contra Auth/PostgREST local con usuarios desechables creados y borrados por el script. Admin y gerente activo atraviesan la autorización; gerente inactivo, empleado y usuario sin rol son rechazados. Un cliente verificado crea/lee su perfil, emite su QR y obtiene una visita con 10 puntos; repetirla el mismo día no altera el saldo. PostgREST rechaza la lectura de `qr_token_hash`.
- Docker Desktop ya responde desde el host y los servicios de Supabase local están arriba; el servicio `vector` aparecía reiniciándose, pero el reset y pgTAP terminaron correctamente.
- La matriz JWT/API cubre roles de escaneo, perfil propio, emisión de QR, visita diaria y ocultamiento del hash. Aún faltan casos API de salario/nómina, precio de orden, canje/concurrencia y aislamiento entre dos perfiles. Tampoco se ejecutaron `EXPLAIN (ANALYZE, BUFFERS)` representativos. No se aplicaron migraciones al remoto.

## Antes de integrar o desplegar

1. Extender la matriz JWT/API al feed QR histórico, conflicto `price_changed`, salario protegido, nómina duplicada, concurrencia de visita/canje, saldo insuficiente y aislamiento entre dos clientes; la matriz inicial de roles/perfil/visita pasó.
2. Revisar planes `EXPLAIN (ANALYZE, BUFFERS)` con volúmenes sintéticos representativos y publicar el informe ACL antes/después.
3. Resolver cómo restringir con seguridad los default privileges de `supabase_admin` sin ejecutar cambios como el rol equivocado; confirmar el comportamiento en staging/remote antes de la migración.
4. Confirmar que la app de datos consume las firmas nuevas: `update_floor_employee_salary`, `order_financial_snapshot`, `daily_sales_summary`, `ensure_customer_profile`, `rotate_customer_qr_token`, `manage_loyalty_reward`, `registrar_visita_qr` y `redeem_loyalty_reward`.
5. Configurar manualmente en Auth remoto confirmación de email, longitud/strength de contraseña, límites y redirect URLs de la app de clientes. El cambio en `config.toml` solo afecta Supabase local.
6. Desplegar únicamente tras revisión explícita de migraciones; no usar `db reset` ni aplicar estas migraciones al proyecto compartido durante esta fase.

## Recuperación

Las migraciones solo agregan cambios hacia adelante y no eliminan datos operativos. La instalación del ledger reconcilia el saldo guardado en entradas `apertura` sin volver a sumar visitas antiguas. Si una prueba local falla, reparar una nueva migración local y reconstruir el entorno desechable. En remoto, no ejecutar `migration repair`, `db reset` ni rollbacks improvisados; diagnosticar primero y preparar migración compensatoria revisada.
