# Backend de clientes — integración con Manager

La cadena canónica de migraciones y Edge Functions vive en `ExquisssitaAppManager/supabase`.

## Migraciones

Al 24 de octubre de 2026, `supabase migration list --linked` muestra 33 migraciones aplicadas coincidentes en local y remoto. La migración Sprint 06 `20261024201145_customer_push_notifications.sql` está solo en local (34 local / 33 remoto), pendiente de pgTAP y despliegue.

La cadena incluye las migraciones Release 03, QR permanente, canje semestral, baja de cuenta y notificaciones push.

## Baja de cuenta

`delete_customer_account()` preserva las filas `public.clientes` y `auth.users` como tombstones debido a FKs históricas `ON DELETE RESTRICT`. Anonimiza el perfil y Auth, borra identidades y sesiones, revoca el hash/secreto QR, agrega un movimiento de ajuste por el saldo positivo vigente y registra un HMAC del correo con pepper en Vault para que un trigger `BEFORE INSERT ON auth.users` rechace nuevos registros con ese correo. La RPC toma la identidad de `auth.uid()`, rechaza perfiles administrativos y es idempotente.

La marca de baja también se verifica en las políticas RLS de perfil, visitas, movimientos, canjes y dispositivos, y en los RPC de perfil/QR. Así una sesión anterior no puede volver a leer ni reactivar la cuenta. Los movimientos y visitas quedan preservados para auditoría.

## Sprint 06: dispositivos FCM

`public.dispositivos_fcm` contiene un registro por token y dispositivo Android. RLS resuelve la propiedad por `clientes.user_id = auth.uid()`; `cliente_id` es la clave de perfil, no la de Auth. El cliente consulta sus tokens, pero modifica el registro mediante `registrar_dispositivo_fcm` y `eliminar_dispositivo_fcm`; ambos RPC verifican la sesión y el perfil vigente. Los tokens dejan de estar disponibles automáticamente al borrar la cuenta. `obtener_tokens_fcm` solo se expone a `service_role` para entregas dirigidas y difusión.

La migración instala `pg_net` y configura triggers asíncronos para movimientos `visita`/ajuste positivo y promociones nuevas activas o recién activadas. Los triggers envían payloads sin emojis a la Edge Function; apuntan a `fcm_push_function_url` y autentican con `fcm_push_webhook_secret`, ambos en Vault. Eventos no configurados no bloquean operaciones de caja, pero no se despachan: crear ambos secretos antes de habilitar el flujo.

La función `send-push-notification` valida el secreto compartido, carga los tokens activos y usa la cuenta de servicio Firebase para OAuth2 y FCM HTTP v1. El proyecto se valida contra `laexquisssita-app`; las respuestas `UNREGISTERED` eliminan el token obsoleto. La clave Firebase no se guarda en Android.

Configuración segura previa al despliegue:

1. Completar `supabase/functions/.env` a partir de `.env.example` con `FCM_SERVICE_ACCOUNT`, `FCM_PROJECT_ID` y un secreto aleatorio `PUSH_WEBHOOK_SECRET`. El `.env` está ignorado por Git.
2. Ejecutar `supabase secrets set --env-file supabase/functions/.env` en el proyecto vinculado.
3. En SQL Editor, guardar en Vault el URL `https://<project-ref>.supabase.co/functions/v1/send-push-notification` con nombre `fcm_push_function_url` y el mismo secreto de `PUSH_WEBHOOK_SECRET` con nombre `fcm_push_webhook_secret`.
4. Desplegar la función y migración solo después de validar pgTAP, `supabase db advisors` y una entrega de prueba con tokens controlados.

## Verificación

- `:app:testDebugUnitTest` y `:app:assembleDebug`: **BUILD SUCCESSFUL**.
- `supabase migration list --linked`: 33 migraciones aplicadas coincidentes y una migración Sprint 06 solo local.
- `supabase/tests/release05_account_deletion.test.sql`: 13 aserciones pgTAP; pendiente de Docker local.
- `supabase/tests/release06_push_notifications.test.sql`: 9 aserciones pgTAP para registro idempotente, aislamiento de tokens, desvinculación y purga en baja; pendiente de Docker local.
- No se ejecutó `db reset`, `db push` ni `functions deploy`. Validar ambas pruebas en una base local limpia, revisar `supabase db advisors` y verificar FCM antes del despliegue.

`tests/customer_permanent_qr.test.sql` y `tests/release04_semester_redemption.test.sql` son pruebas anteriores de referencia; el despliegue de Release 04 ya figura aplicado en el remoto.
