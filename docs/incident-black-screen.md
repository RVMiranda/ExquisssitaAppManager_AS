# Reporte de incidente: pantalla negra al iniciar

**Proyecto:** Exquisssita Manager (Android nativo, Kotlin y Jetpack Compose)  
**Estado:** resuelto en el AVD usado para la verificación; pendiente confirmar en otro AVD o dispositivo físico.  
**Alcance:** inicio de la actividad y renderizado del login. No valida todavía la autenticación contra Supabase.

## Síntoma

Después de compilar e instalar la app en el AVD `Pixel_10_Pro`, Android mostraba una pantalla negra al primer arranque. No aparecía una excepción fatal ni un error de compilación. En ejecuciones anteriores, llevar la app al fondo y volver a abrirla hacía que el formulario apareciera.

## Diagnóstico comprobado

- `uiautomator dump` mostraba que Compose había construido el formulario: título, correo, contraseña y botón Entrar.
- En el arranque afectado, `dumpsys window` mostraba la ventana de `MainActivity` con `mDrawState=HAS_DRAWN`, pero `Surface: shown=false` y `mShownAlpha=0.0`.
- SurfaceFlinger mostraba la superficie de la actividad oculta por su capa padre asociada a `starting_reveal`. La actividad seguía en estado `RESUMED`; no se encontró un crash de la app.
- Al llevar la app al launcher y volver a abrirla, Android creó una superficie visible y se mostró el login con los colores del tema.

La evidencia sitúa el fallo en la transición de ventana/compositor del AVD. No apunta a un problema de `compileSdk`, `targetSdk`, compatibilidad Java, Supabase ni al contenido de la pantalla Compose.

## Cambios y solución aplicada

1. Se verificó que `MainActivity` instala `setContent`, aplica el tema y monta `AppNavHost`.
2. Se probó el callback de salida de AndroidX SplashScreen. No liberó la capa atascada.
3. Se retiraron el tema puente `Theme.App.Starting`, el callback explícito y la dependencia de compatibilidad de SplashScreen. La actividad quedó usando directamente `Theme.ExquisssitaAppManager`; Android conserva su splash nativo.
4. Se reinició el AVD con `adb reboot`, sin borrar los datos del emulador.
5. Se abrió la app tras el reinicio y se capturó el formulario completo en `app/build/after-reboot-plus10.png`. En esa comprobación SurfaceFlinger reportó la superficie de `MainActivity` visible.

La pantalla volvió a renderizarse luego del reinicio del AVD. Como el reinicio también limpia el estado interno del emulador, no se puede atribuir el resultado exclusivamente al cambio del tema; la causa operacional más probable fue un estado de transición/superficie atascado en ese AVD.

## Estado del proyecto tras el incidente

- El aviso de Android Studio para abrir **Project Structure** es informativo. No se cambió ningún valor en ese diálogo.
- Se conservan `minSdk 24`, `compileSdk 37`, `targetSdk 34` y compatibilidad de fuente/bytecode Java 11.
- La pantalla de login quedó visualmente comprobada después del reinicio del AVD.
- No se probaron credenciales ni se confirmó un login remoto en Supabase durante este diagnóstico.
- No cambiar SDKs para intentar corregir una pantalla negra sin nueva evidencia. Si reaparece, guardar captura, `adb logcat`, `dumpsys window windows` y `dumpsys SurfaceFlinger` antes de reiniciar el emulador.

## Pendientes antes de continuar con Auth

1. Confirmar el primer arranque en frío en un AVD distinto (preferiblemente API 35/36 estable) o en un dispositivo físico compatible.
2. Reconciliar el contrato `docs/backend-contract.md` con el esquema real. El diagnóstico compartido con el equipo describe `user_roles` sin `activo` y `empleados.user_id`; el documento del repositorio todavía describe columnas/roles distintos. Tratar esa diferencia como pendiente de verificación de solo lectura con Supabase CLI antes de modificar DTOs, políticas o migraciones.
3. Mantener fuera del cliente cualquier `service_role`; la app solo usa la clave publicable/anon configurada localmente.
4. Probar login online (válido, inválido, sin conexión y cuenta inactiva/sin rol) solo después de confirmar esquema, RLS y configuración local.

## Propuesta de colaboración en paralelo

Para reducir conflictos, acordar primero el contrato de Auth y asignar un dueño por área/archivos:

| Paquete | Responsable sugerido | Alcance y salida |
| --- | --- | --- |
| Verificación de backend | Antigravity | Revisar esquema remoto, roles, vínculo empleado-Auth y RLS con Supabase CLI en modo de lectura. Entregar discrepancias y propuesta; no aplicar cambios remotos. |
| Cliente y repositorio Auth | Codex | Tras aprobar el contrato, trabajar en `core/network`, `core/di` y `data/repository`; usar únicamente clave publicable y mapear errores de login. |
| UI y navegación Auth | Agente que tome presentación | Trabajar en `features/auth` y `navigation`; no tocar cliente/repositorio ni migraciones. Validar estados de carga, error, sesión y autofill. |
| Persistencia/offline | Fase posterior | Añadir almacenamiento mínimo de sesión/datos locales después de que login y autorización online estén validados; no guardar contraseñas. |

Cada paquete debe definir archivos de propiedad, interfaz de entrada/salida y criterios de aceptación. Integrar primero el contrato de backend, después el flujo online de extremo a extremo y finalmente persistencia/offline; validar cada integración en Supabase local/remoto según el procedimiento acordado.
