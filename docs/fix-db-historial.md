# Supabase: historial y sincronización de base de datos

**Fecha:** 2026-10-10  
**Proyecto Supabase:** `gcszsxeywywsrgnnnljr` (`LaExquisssita`)  
**Alcance:** recuperar las migraciones del proyecto Flutter, alinear el historial Android con el remoto y fijar las reglas de acceso de empleados y visitas QR.

## Estado final

- Las 14 migraciones de `C:\Users\MACHENIKE\Projects\exquisssita_manager\supabase\migrations` se copiaron a `supabase/migrations` del proyecto Android. No se movieron del proyecto Flutter. Se compararon los 14 pares mediante SHA-256 y todos coinciden.
- Se reconstruyó la base local con `supabase db reset --local`. Las migraciones se aplicaron de forma secuencial sin errores.
- Se verificó con `supabase migration list --linked` que las 15 migraciones locales están registradas también en el proyecto remoto. La última es `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc`.
- `supabase db diff --linked --schema public,private_sync` mostró cuatro definiciones de funciones. Se compararon sus cuerpos SQL normalizando finales de línea CRLF/LF y los hashes son iguales entre local y remoto; no se detectó diferencia funcional en esas funciones.
- No se ejecutó `db reset --linked`. El reset afectó solo a la base local. En el remoto se aplicó una sola migración de seguridad, después de confirmar el dry-run.

## Historial presente en Android

Las primeras 14 versiones provienen sin edición del proyecto Flutter:

1. `20261002204457_remote_baseline`
2. `20261002204523_phase1_secure_roles_rls`
3. `20261002204918_phase1_harden_function_security`
4. `20261002205021_phase1_fix_trigger_search_path`
5. `20261002213231_phase2_business_integrity_tables`
6. `20261003024202_phase6_atomic_payment`
7. `20261003025226_phase7_atomic_inventory`
8. `20261003030433_phase8_qr_loyalty_daily_visit`
9. `20261003034251_phase10_idempotent_cash_register`
10. `20261003062018_bidirectional_sync_contract`
11. `20261003070556_critical_operation_reconciliation`
12. `20261004032936_weekly_payday_and_salary_contract`
13. `20261004034839_employees_payroll_realtime`
14. `20261004200949_payroll_week_period`
15. `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc` (cambio nuevo, aplicado local y remoto)

La lista CLI antes del cambio mostraba las 14 primeras versiones en ambos lados, más dos archivos Android pendientes. Los dos archivos Android antiguos se revisaron y retiraron antes del reset:

- `20261008212844_auth_employee_profiles.sql` usaba columnas que no existen en el esquema remoto (`auth_user_id`, `salario_base_cents`, `user_roles.activo`) y habilitaba roles no aceptados por el `CHECK` remoto. No se aplicó.
- `20261009121000_loyalty_qr_daily_visit.sql` duplicaba el contrato ya desplegado y quitaba el índice diario por cuenta; además permitía acreditar una visita a clientes no registrados. No se aplicó.

## Cambio de seguridad consolidado

La migración `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc.sql` hace estos cambios:

1. Reemplaza `public.auth_role()` con una versión `SECURITY DEFINER` cuyo `search_path` está vacío y cuyos nombres de esquema/tabla están calificados.
2. Devuelve el rol `empleado` solo si existe una fila vinculada mediante `empleados.user_id` y `empleados.activo = true`. Un empleado deshabilitado o sin perfil activo ya no pasa las políticas que dependen de `auth_role()`.
3. Mantiene el rol `admin` basado en `public.user_roles`; esa tabla solo admite `admin` y `empleado` y no tiene una columna `activo`.
4. Revoca la ejecución de `public.registrar_visita_cliente(uuid)` a `PUBLIC`, `anon` y `authenticated`. Esa función antigua permitía acreditar por ID sin demostrar posesión del QR.
5. Conserva `public.registrar_visita_qr(p_qr_token text)`, que calcula el hash para localizar `clientes.qr_token_hash`, requiere `clientes.user_id` y respeta el límite diario desplegado. Los clientes registrados en la app pueden acumular visitas/puntos; las promociones generales continúan siendo un flujo separado.

La verificación previa encontró cero usuarios con rol `empleado` en `public.user_roles`, por lo que la nueva condición no cambia el acceso de cuentas de empleado existentes. La migración se probó al reconstruir el esquema local y se aplicó al remoto tras confirmar que el dry-run proponía solamente esta versión.

## Verificaciones ejecutadas

```text
supabase migration list --linked
  15 versiones: local = remoto

supabase db reset --local
  Finalizó correctamente; aplicó las 15 migraciones

supabase db push --linked --dry-run
  Solo proponía 20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc.sql

supabase db push --linked
  Aplicó esa migración

Verificación SQL local y remota
  mismo hash normalizado de auth_role
  search_path de auth_role = vacío
  registrar_visita_cliente: ejecución denegada a anon/authenticated
  registrar_visita_qr: ejecución autorizada a authenticated
  registrar_visita_qr: exige vínculo de cliente a auth.users
  índice visitas_clientes_usuario_fecha_uidx presente
```

El diff de pg-delta todavía imprime cuatro `CREATE OR REPLACE FUNCTION` por finales de línea distintos. La comparación directa de `prosrc` después de normalizar CRLF/LF produjo los mismos hashes para `decrementar_stock`, `realizar_corte_caja`, `registrar_visita_cliente` y `trigger_set_updated_at` en ambos servidores. Se trata de una diferencia textual, no de lógica. No editar ni reescribir las migraciones históricas para quitar ese ruido.

## Cómo continuar

- Mantener las migraciones históricas en el repo Flutter y Android; son copias sincronizadas del mismo origen.
- Para cada cambio futuro, usar `supabase migration new <nombre>`, aplicar primero en local y reconstruir con `supabase db reset --local`.
- Revisar `supabase db diff --linked` y `supabase migration list --linked` antes del push; aplicar solo migraciones nuevas y aprobadas.
- Nunca usar `supabase db reset --linked` para sincronizar un proyecto compartido; ese modo reconstruye/destruye el esquema remoto.
- No insertar usuarios directamente en `auth.users`. Crear la cuenta mediante Supabase Auth y vincular el perfil usando `user_roles`/`empleados.user_id` o `clientes.user_id`, según la app.
- La interfaz de personal todavía debe integrarse con la regla de cuenta de cliente: empleados buscan y acreditan por QR; la app de clientes crea/vincula la cuenta y conserva el token QR estable.

## Nota para Antigravity

Usar este archivo y `docs/backend-contract.md` como contexto actualizado. El contrato real de lealtad usa `clientes.qr_token_hash`, `clientes.user_id`, `puntos_lealtad`, `visitas_totales` y `visitas_clientes.fecha`. El token QR se envía a la RPC, pero solo se almacena su SHA-256. No restaurar las migraciones Android descartadas ni cambiar la regla de cliente registrado para ganar puntos.
