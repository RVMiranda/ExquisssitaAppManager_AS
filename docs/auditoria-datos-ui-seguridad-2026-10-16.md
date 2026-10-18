# Auditoría de datos, UI y seguridad — 16 de octubre de 2026

**Dictamen:** hay defectos importantes de funcionamiento y aislamiento de sesión que conviene corregir antes de considerar la aplicación lista para operación. No se encontró evidencia de una intrusión; esta revisión tampoco permite garantizar ausencia de fugas ni certificar la seguridad del servidor.

**Alcance y evidencia.** Revisión estática del proyecto Android Kotlin/Compose, repositorios de autenticación, empleados, nómina, órdenes, mesas, pagos, inventario, promociones, lealtad y caja; DAOs/entidades Room, navegación, estados de UI, configuración de compilación, manifiesto, almacenamiento de sesión y migraciones SQL versionadas. Los escenarios siguientes se deducen del código y deben ejecutarse como regresiones. No se ejecutaron operaciones contra producción ni se modificó código funcional o esquema. No se realizó inspección visual en emulador/dispositivo, pentest de infraestructura, análisis exhaustivo del historial Git ni verificación de CVE de todas las dependencias. El estado desplegado de Supabase puede diferir de las migraciones.

**Validación automatizada.** Se intentó `testDebugUnitTest lintDebug`. Inicialmente no había Java en PATH; se localizó el JDK de Android Studio. Tras permitir acceso a la caché de Gradle, el arranque falló con `java.io.IOException: Unable to establish loopback connection`. Reintentar con `--no-daemon` e IPv4 produjo el mismo error. No hay resultado de compilación, pruebas o Lint aprobado en esta auditoría. La prueba instrumentada existente sólo verifica el nombre del paquete; no prueba navegación, logout, acceso a datos ni UI. El archivo PaymentViewModelTest contiene pruebas de estados/fakes, pero no instancia PaymentViewModel, por lo que no cubre el manejo real del resultado de cobro.

**Prioridades:** P1 = corregir antes de liberar por impacto en seguridad, datos o negocio; P2 = corregir en la siguiente iteración controlada. “Confirmado estáticamente” significa que la ruta defectuosa está presente en el código, no que se haya reproducido en el dispositivo o servidor.

## Defectos identificados en el código

### F01 — P1: el logout conserva el panel en el historial de navegación

**Evidencia:** [navigation/AppNavHostImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/navigation/AppNavHostImpl.kt:53), líneas 53, 61 y 81; [features/main/MainScaffold.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/main/MainScaffold.kt:482).

Al iniciar sesión se elimina LOGIN con `popUpTo(LOGIN) { inclusive = true }`. Al salir se intenta eliminar LOGIN otra vez, aunque ya no está en la pila: MAIN permanece debajo del nuevo login. El destino MAIN renderiza MainScaffold aun con sesión nula. El efecto de redirección depende sólo de `uiState.stage`, no de volver a MAIN mediante Atrás. Los ViewModels de empleados/nómina conservan sus listas y no observan el cierre de sesión.

**Impacto:** después de cerrar sesión, Atrás puede recuperar la pantalla anterior con información sensible retenida. Esto no demuestra que el servidor permita escribir sin token, pero sí rompe el cierre visual y el aislamiento de información local.

**Corrección:** retirar todo el grafo autenticado al salir; impedir componer MAIN sin sesión válida; destruir o limpiar ViewModels y datos sensibles en memoria. No borrar indiscriminadamente operaciones pendientes: deben aislarse por propietario.

**Regresión:** abrir nómina, cerrar sesión y pulsar Atrás repetidamente; repetir con expiración y recreación de Activity. Ninguna pantalla privada ni salario debe reaparecer.

### F02 — P1: empleados y nómina pueden sincronizar operaciones de otra cuenta

**Evidencia:** [data/sync/EmployeesPayrollSyncCoordinator.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/sync/EmployeesPayrollSyncCoordinator.kt:41) y línea 68; [core/database/local/entities/PayrollPaymentIntentEntity.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/core/database/local/entities/PayrollPaymentIntentEntity.kt:12); [core/database/local/entities/EmployeeEntity.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/core/database/local/entities/EmployeeEntity.kt:13); [data/repository/AuthRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/AuthRepositoryImpl.kt:212).

Las colas de empleados/nómina carecen de identidad del operador. El coordinador carga todos los pendientes y los envía con la sesión Supabase vigente. El cierre de sesión limpia credenciales, pero no aísla estas colas. Empleados y nómina llaman al coordinador al consultar sus datos.

**Escenario:** A edita salario o registra una intención de nómina sin red, sale, B inicia sesión y consulta empleados. Los pendientes de A se procesan con los permisos de B; `registrado_por` en la RPC de nómina será B. Si A fue revocado, sus operaciones aún podrían ejecutarse mediante B.

**Corrección:** guardar actorUserId y ámbito de datos en cada intención; verificar identidad remota/local antes de cada envío; no reatribuir operaciones al usuario nuevo. Aplicar el aislamiento también a cachés y trabajos en curso.

**Regresión:** dos cuentas autorizadas en el mismo dispositivo; comprobar que B no transmite pendientes de A y que la autoría en servidor se mantiene.

### F03 — P1: pagos rechazados o pendientes se presentan como exitosos

**Evidencia:** [features/payments/PaymentViewModel.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/payments/PaymentViewModel.kt:249); [data/repository/PaymentRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/PaymentRepositoryImpl.kt:132).

El repositorio retorna `Result.success(PaymentCommand(...))` tanto para CONFIRMED como para REJECTED y PENDING_SYNC. El ViewModel trata cualquier Result.success como “registrado correctamente”, limpia el efectivo e invoca onPaymentSuccess sin inspeccionar el estado.

**Impacto:** el operador puede interpretar que cobró correctamente aunque Supabase rechazó por stock/saldo o todavía no confirmó el pago. Las protecciones SQL no corrigen este mensaje engañoso.

**Corrección:** manejar explícitamente los tres estados; sólo confirmar visualmente y ejecutar el callback de éxito final para CONFIRMED. Mantener aviso persistente y conciliación para pendientes, y mostrar motivo de rechazo.

**Regresión:** devolución de REJECTED por stock insuficiente, PENDING_SYNC por pérdida de red y CONFIRMED. Verificar texto, navegación, saldo y posibilidad de reintento.

### F04 — P1: Lealtad y QR es una simulación accesible desde el menú operativo

**Evidencia:** [features/loyalty/LoyaltyScanScreen.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/loyalty/LoyaltyScanScreen.kt:232), líneas 288, 313 y 447; [features/main/MainScaffold.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/main/MainScaffold.kt:508).

La consulta construye “Cliente Demo” o “Carlos López” sin consultar el repositorio. Registrar visita incrementa puntos y visitas mediante `customer.copy` en memoria y muestra una confirmación. Canjear puntos tiene un callback vacío. LoyaltyViewModel.kt y QrScannerScreen.kt sólo contienen la declaración de paquete; no hay implementación de cámara conectada.

**Impacto:** se informa una acreditación que no existe en servidor y se muestran resultados ficticios al buscar un teléfono. Al abandonar la pantalla se pierde el estado.

**Corrección:** conectar casos de uso y estados reales, implementar captura de QR, eliminar datos de demostración de la ruta operativa y deshabilitar funciones todavía no implementadas.

**Regresión:** cliente real/inexistente, QR inválido, visita duplicada, red ausente y reapertura de pantalla; verificar persistencia en una base de pruebas.

### F05 — P1: respuestas sin paginar se tratan como instantáneas completas y eliminan detalles de la caché

**Evidencia:** [data/repository/OrderRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/OrderRepositoryImpl.kt:62), especialmente línea 69; [supabase/config.toml](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/config.toml:17); [data/repository/PromotionsRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/PromotionsRepositoryImpl.kt:32).

refreshOrders consulta todas las órdenes y todos los detalles sin paginación ni orden estable. Luego borra detalles locales ausentes de esa respuesta. La API local declara un máximo de 1000 filas; el límite desplegado no se verificó. Promociones también reemplaza toda la caché con una sola respuesta. Mesas, catálogo, empleados y consultas de nómina tienen lecturas sin paginar.

**Impacto:** al superar el límite, partidas válidas desaparecen localmente y saldos/listados quedan incompletos. No se está borrando el servidor, pero el cálculo y visualización locales dejan de representar sus datos.

**Corrección:** usar paginación estable y confirmar que se descargó la instantánea completa antes de reconciliar eliminaciones, o consumir el feed incremental con cursor/tombstones. Separar órdenes activas del histórico cuando corresponda.

**Regresión:** más de 1000 detalles con partidas de una orden activa fuera de la primera página; refrescar y comprobar que no desaparecen ni cambia su total.

### F06 — P2: iniciar offline deja la sesión bloqueada en ese modo tras recuperar red

**Evidencia:** [data/repository/AuthRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/AuthRepositoryImpl.kt:108); [features/auth/AuthViewModelImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/auth/AuthViewModelImpl.kt:67); [data/repository/OrderSyncSupport.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/OrderSyncSupport.kt:34); [core/network/NetworkMonitor.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/core/network/NetworkMonitor.kt:19).

restoreSession fija isOffline=true cuando restaura sin red. Sólo se llama durante la inicialización del ViewModel; NetworkMonitor consulta conectividad puntualmente y no notifica recuperación. hasRemoteSession bloquea envíos si isOffline sigue verdadero, incluso si Supabase ya tiene sesión. Inventario aplica un bloqueo equivalente y caja exige sesión online.

**Impacto:** órdenes/pagos/inventario pueden quedarse pendientes hasta reiniciar o volver a autenticarse. Tampoco existe un observador que actualice el estado propio de autorización al invalidarse la sesión del SDK; el servidor conserva su responsabilidad de rechazar accesos.

**Corrección:** observar conectividad y sesión SDK, revalidar perfil/rol al reconectar y reanudar únicamente las operaciones del actor validado.

**Regresión:** iniciar en modo avión con sesión almacenada, crear pendiente, activar red y verificar recuperación sin reiniciar.

### F07 — P2: una edición antigua de empleado puede deshacer cambios más recientes, incluida una baja

**Evidencia:** [data/sync/EmployeesPayrollSyncCoordinator.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/sync/EmployeesPayrollSyncCoordinator.kt:44); [data/repository/EmployeeRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/EmployeeRepositoryImpl.kt:159); [data/model/EmployeeDtos.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/model/EmployeeDtos.kt:26).

Se guarda syncVersion, pero los UPDATE sólo filtran por id. El DTO envía el perfil completo, incluyendo salario y activo. Una edición offline antigua puede sobrescribir salario o reactivar un perfil que otro operador desactivó mientras tanto.

**Corrección:** RPC/UPDATE condicional con versión esperada y conflicto explícito; separar cambios de campos y desactivación. No resolver conflictos automáticamente sobrescribiendo la fila completa.

**Regresión:** A edita teléfono offline con activo=true; B desactiva al empleado online; sincronizar A debe detectar conflicto y conservar la baja.

### F08 — P2: las reglas de lealtad no incorporaron el rol gerente

**Evidencia:** [20261003030433_phase8_qr_loyalty_daily_visit.sql](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/migrations/20261003030433_phase8_qr_loyalty_daily_visit.sql:34); [20261002204457_remote_baseline.sql](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/migrations/20261002204457_remote_baseline.sql:456); [20261010200756_reject_missing_roles_for_sensitive_actions.sql](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/migrations/20261010200756_reject_missing_roles_for_sensitive_actions.sql:15).

La versión final de auth_role devuelve gerente para un gerente activo y ya no devuelve empleado para el personal común. Sin embargo, registrar_visita_qr y las políticas staff de clientes/visitas siguen admitiendo admin/empleado. No se encontró una migración posterior que alinee esas reglas.

**Impacto:** una vez conectada la UI real, el gerente no podrá registrar visitas; tampoco tendrá lectura general/alta de clientes por estas políticas.

**Corrección:** definir la matriz de roles para lealtad y actualizar coherentemente función, grants y RLS; probar tanto permisos permitidos como denegados.

### F09 — P2: el alta local de cliente genera QR que la RPC de visitas no acepta

**Evidencia:** [data/repository/LoyaltyRepositoryImpl.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/repository/LoyaltyRepositoryImpl.kt:86); [data/model/LoyaltyDtos.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/data/model/LoyaltyDtos.kt:26); [20261003030433_phase8_qr_loyalty_daily_visit.sql](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/migrations/20261003030433_phase8_qr_loyalty_daily_visit.sql:46).

createCustomer inserta nombre, teléfono y hash, sin user_id; la columna es nullable y no tiene valor por defecto en el baseline. La RPC exige que el cliente encontrado tenga user_id. No se encontró un trigger versionado que establezca ese vínculo.

**Impacto:** con las migraciones revisadas, el QR recién emitido para un cliente sin cuenta no puede registrar visita.

**Corrección:** decidir si lealtad requiere cuenta Auth y crear un flujo seguro de vinculación, o cambiar explícitamente el modelo de unicidad de visitas para clientes sin cuenta. No vincularlos al usuario operador.

**Regresión:** crear cliente y registrar inmediatamente su primera visita usando el QR generado.

### F10 — P2: “Partes iguales” recalcula sobre el saldo restante sin descontar participantes

**Evidencia:** [features/payments/PaymentUiState.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/payments/PaymentUiState.kt:144); [features/payments/PaymentViewModel.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/features/payments/PaymentViewModel.kt:249).

La cuota es saldoPendiente / splitCount y splitCount no disminuye al confirmar. Una cuenta de $300 entre tres personas propone $100, luego $66.67 y después $44.45, salvo que el operador cambie manualmente el divisor.

**Corrección:** conservar un plan de división con importes y participantes restantes, distribuir los centavos de manera determinista y avanzar sólo al confirmar cada cuota.

**Regresión:** $300 entre 3 y $100 entre 3; exactamente tres cobros deben liquidar el total con diferencia máxima de un centavo por cuota.

## Riesgos de seguridad y endurecimiento pendientes de comprobación

### R01 — P1 condicionada a grants desplegados: clientes pueden modificar campos de lealtad propios

**Evidencia:** [20261002204457_remote_baseline.sql](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/supabase/migrations/20261002204457_remote_baseline.sql:471).

clientes_update_own limita la fila por user_id, pero no las columnas. Si authenticated tiene UPDATE a nivel de tabla —como ocurre con exposición automática habilitada— un cliente puede intentar alterar puntos_lealtad, visitas_totales y qr_token_hash de su propia fila. No se encontró restricción por columna ni trigger que impida esos cambios directos. La lista de campos prohibidos de sync_execute no protege llamadas directas a la tabla.

**Estado:** defecto de defensa en las migraciones; explotabilidad pendiente de comprobar con ACL reales en entorno de pruebas. RLS controla filas y no sustituye los grants por columna.

**Corrección/verificación:** comprobar grants, permitir únicamente columnas de perfil editables y reservar puntos/visitas/hash a RPCs específicas. Probar UPDATE directo con cuenta cliente y verificar denegación sin alterar datos reales.

### R02 — P2: reglas de transferencia no excluyen datos sensibles

**Evidencia:** [AndroidManifest.xml](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/AndroidManifest.xml:9), [data_extraction_rules.xml](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/res/xml/data_extraction_rules.xml:9), [core/di/DatabaseModule.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/core/di/DatabaseModule.kt:37).

allowBackup=false es positivo, pero dataExtractionRules conserva la plantilla sin exclusiones y sin device-transfer. Room usa SQLite sin cifrado adicional de aplicación, con salarios, teléfonos y operaciones. Android documenta que en determinados fabricantes allowBackup=false puede no impedir transferencias dispositivo a dispositivo.

**Estado:** riesgo dependiente del dispositivo y de acceso al almacenamiento; no significa que otra app pueda leer Room normalmente. Las credenciales sí tienen cifrado Keystore separado.

**Corrección/verificación:** excluir explícitamente base de datos, preferencias sensibles y archivos privados de cloud-backup y device-transfer, y probar en dispositivos objetivo. Evaluar cifrado de datos sensibles según el modelo de amenaza y definir retención/aislamiento por cuenta.

### R03 — P2: la configuración acepta cualquier JWT como si fuera anon

**Evidencia:** [core/network/SupabaseConfig.kt](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/src/main/java/com/mirandadevsource/exquisssitaappmanager/core/network/SupabaseConfig.kt:23); [app/build.gradle.kts](C:/Users/MACHENIKE/AndroidStudioProjects/ExquisssitaAppManager/app/build.gradle.kts:36).

El validador sólo comprueba prefijo eyJ y dos puntos. Un JWT legacy service_role cumpliría esa condición. El build incorpora directamente la clave configurada en BuildConfig sin comprobar su clase.

**Estado:** riesgo de configuración accidental, no fuga observada. La clave local revisada es sb_publishable_; local.properties está ignorado por Git. No se encontraron coincidencias de claves sb_secret_, tokens sbp_, JWT completos o claves privadas PEM en el escaneo de archivos actualmente versionados. El escaneo por patrones no garantiza ausencia de todos los secretos ni cubre el historial completo.

**Corrección:** fallar la compilación ante claves secret/service_role; preferir exclusivamente claves publicables o, si se conserva compatibilidad, exigir claim role=anon en el JWT de configuración. Esa inspección es una barrera de configuración, no validación criptográfica de autorización. Nunca incorporar claves administrativas al APK. La URL y la clave publicable son identificadores públicos; excluirlas de Git por sí solo no las vuelve secretas dentro del APK.

## Aspectos correctamente encaminados

- Separación por UI, dominio, repositorios y persistencia; Hilt centraliza dependencias.
- Sesión y metadatos cifrados con AES-GCM y clave en AndroidKeyStore; no se observó persistencia propia de contraseñas.
- Configuración Supabase exige HTTPS; no se identificaron implementaciones que deshabiliten validación TLS.
- auth_role consulta user_roles y valida actividad para gerentes; las RPC críticas comprueban identidad/rol. No se usa user_metadata como autorización corriente. La migración inicial sí lo usó para importar roles existentes: conviene revisar ese inventario histórico de administradores.
- Pagos/inventario usan claves de idempotencia, recibos privados, límites y transacciones SQL; comandos de órdenes/inventario incluyen actor.
- Las migraciones recientes retiran escritura directa de tablas financieras, protegen el esquema privado y fijan search_path de funciones sensibles.
- Existen migraciones Room explícitas y pruebas de reglas de negocio; la cobertura requiere ampliarse a integración y comportamiento real de ViewModels.

Estos controles reducen riesgos, pero no compensan las rutas defectuosas descritas.

## Orden recomendado de corrección y aceptación

1. Cerrar el acceso residual después del logout y aislar colas/cachés por actor.
2. Distinguir pagos confirmados, pendientes y rechazados; añadir pruebas del ViewModel real.
3. Corregir paginación antes de usar volúmenes de operación mayores.
4. Retirar la simulación de lealtad y resolver su contrato de roles/identidad antes de activarla.
5. Incorporar reconexión, control de versiones en empleados y división de cuenta estable.
6. Verificar grants desplegados, exclusiones de transferencia y barreras de claves; ejecutar en staging una matriz de anon, cliente, empleado, gerente activo/inactivo y admin.
7. Desbloquear Gradle, ejecutar unit tests/Lint, migraciones Room 1→6, pruebas instrumentadas de logout y UI en distintos tamaños/fuentes, y pruebas SQL de idempotencia, permisos y concurrencia. Simular pérdida de respuesta tras confirmar un pago y cambio de cuenta con pendientes.

**Fuentes oficiales contrastadas:** [Seguridad de la Data API de Supabase](https://supabase.com/docs/guides/api/securing-your-api) para la distinción grants/RLS y exposición por defecto; [Android Auto Backup](https://developer.android.com/identity/data/autobackup) para las limitaciones de allowBackup y reglas de transferencia. Se intentó consultar el índice changelog.md de Supabase; el lector web no pudo procesar su tipo text/markdown. No se implementaron cambios basados en ese índice.

