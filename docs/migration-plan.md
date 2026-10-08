# Exquisssita Manager — Plan Maestro de Migración e Implementación Android Nativo

> **Documento de Hoja de Ruta, Mapeo Tecnológico y Criterios de Aceptación (DoD)**  
> Proyecto Android Studio Nativo: `ExquisssitaAppManager`  
> Ubicación del Proyecto: `C:\Users\MACHENIKE\AndroidStudioProjects\ExquisssitaAppManager`  
> Referencia Arquitectónica: `exquisssita_manager` (Flutter)  

---

## 1. Stack Tecnológico Nativo Objetivo

El proyecto se desarrolla de forma nativa en **Android Studio**, garantizando máxima fluidez operativa, compatibilidad con periféricos POS y una arquitectura limpia y desacoplada:

- **Lenguaje:** Kotlin 2.0+ con tipado estricto y Null Safety absoluto.
- **UI Toolkit:** Jetpack Compose (BOM moderna), basado en Material 3 y temas personalizados con tokens Pantone.
- **Inyección de Dependencias:** Dagger Hilt 2.51+.
- **Persistencia Local:** Android Room 2.6+ con KSP, consultas reactivas `Flow<T>` y transacciones atómicas.
- **Asincronía:** Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Default`, `Dispatchers.Main.immediate`) y `StateFlow<T>`.
- **Backend SDK:** `io.github.jan-tennert.supabase:supabase-kt` 2.5+ (módulos: `postgrest-kt`, `gotrue-kt`, `realtime-kt`, `storage-kt`).
- **Motor Offline-First:** AndroidX WorkManager 2.9+ con `CoroutineWorker`, `HiltWorker` y constraints de conectividad de red.
- **Escáner QR:** CameraX (`androidx.camera`) + Google ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`).
- **Serialización:** `kotlinx.serialization` (JSON).
- **Testing:** JUnit 5, Kotlinx Coroutines Test, Cash App Turbine (para probar `Flow`), MockK y Compose UI Testing.

---

## 2. Mapeo Arquitectónico Componente a Componente (Flutter ➔ Android)

| Componente en Flutter (`exquisssita_manager`) | Equivalente Android Nativo (`ExquisssitaAppManager`) | Justificación y Ventajas |
| :--- | :--- | :--- |
| **Drift Database** (`drift`) | **Room Database** (`@Database`, `@Entity`, `@Dao`) | Estándar oficial de Android, generación de código en tiempo de compilación con KSP, verificación de queries SQL en compilación. |
| **Riverpod StateNotifier / Notifier** | **Android ViewModel + StateFlow** (`@HiltViewModel`) | Ciclo de vida nativo vinculado a la actividad/pantalla, sobrevive a cambios de configuración, reactividad limpia con `collectAsStateWithLifecycle()`. |
| **Custom SyncEngine (Timer / Dart Streams)** | **AndroidX WorkManager (`SyncWorker`)** | Garantía de ejecución del sistema operativo incluso con la app en segundo plano o reinicio del dispositivo, reintentos con Exponential Backoff nativo. |
| **Flutter Widgets Tree** | **Jetpack Compose Composables** | Paradigma declarativo puro en Kotlin, mayor velocidad de renderizado, layouts adaptativos con `BoxWithConstraints` y `WindowSizeClass`. |
| **mobile_scanner** | **CameraX + Google ML Kit Barcode** | Máximo rendimiento en Android, procesamiento en hilo de análisis independiente, menor uso de batería y lectura ultrarrápida. |
| **supabase_flutter** | **supabase-kt (`postgrest-kt`, `gotrue-kt`)** | Cliente oficial para Kotlin multiplatform, llamadas suspendidas naturales con coroutines, tipos seguros serializables. |
| **Navigator 2.0 / GoRouter** | **Jetpack Navigation Compose (Type-Safe)** | Navegación nativa declarativa con serialización de rutas y argumentos seguros sin cadenas arbitrarias. |

---

## 3. Estructura de Paquetes en Android Studio

```text
app/src/main/java/com/mirandadevsource/exquisssitaappmanager/
├── core/
│   ├── common/           # Result, Resource, Constants, MoneyUtils
│   ├── database/         # AppDatabase, Room Converters, Migrations
│   ├── di/               # Hilt Modules (DatabaseModule, NetworkModule, RepositoryModule)
│   ├── network/          # SupabaseClientProvider, ConnectivityMonitor
│   ├── permissions/      # PermissionChecker, RoleManager
│   └── sync/             # SyncWorker, SyncCoordinator, SyncQueueScheduler
├── data/
│   ├── local/            # Room Entities y DAOs por feature
│   ├── remote/           # Remote DTOs (@Serializable) y Remote Data Sources
│   └── repository/       # Implementación de repositorios (Single Source of Truth)
├── domain/
│   ├── model/            # Entidades de dominio puras (sin anotaciones de BD ni serialización)
│   ├── repository/       # Interfaces de repositorios
│   └── usecase/          # Casos de uso de negocio con operator fun invoke()
├── features/
│   ├── home/             # Vista Inicio / Resumen (Resumen de hoy, KPIs, Atención requerida)
│   ├── tables/           # Salón de mesas, órdenes, pedidos para llevar, detalle de comanda
│   ├── payments/         # Cobro POS, división de cuentas por ítems, cálculo de cambio
│   ├── inventory/        # Catálogo de menú, Kardex de inventario, ajustes de stock
│   ├── employees/        # Directorio de personal, turnos y nómina semanal
│   ├── loyalty/          # Escáner QR de clientes con CameraX y entrada manual
│   ├── promotions/       # Listado y alta de promociones activas/inactivas/vencidas
│   ├── cashregister/     # Corte de caja, arqueo por método y cierre de turno
│   ├── reports/          # Métricas de ventas y gráficos de rendimiento
│   └── settings/         # Configuración de usuario, sincronización manual y tema
├── navigation/           # AppNavGraph, AppDestinations, TopAppBar & NavigationDrawer
└── ui/
    ├── components/       # Flat Buttons, Flat Cards, Chip Filters, Steppers, Status Badges
    └── theme/            # ColorScheme (Tokens Pantone), Typography (tnum), Shape, Theme.kt
```

---

## 4. Fases Detalladas de Implementación

### Fase 0 — Diagnóstico y Catálogo de Versiones
- Verificar `libs.versions.toml` con versiones estables de Kotlin, Compose BOM, Room, Hilt, Supabase-kt y CameraX.
- Asegurar configuración de JDK 17 o superior.
- Verificar que las credenciales de Supabase (URL y anon key) provengan de `BuildConfig` y nunca se exponga `service_role`.

### Fase 1 — Fundación de Arquitectura y Sistema de Diseño Visual
- **Hilt Application:** Configurar `ExquisssitaApp`.
- **Tema Visual Flat (Tokens Pantone):** Configurar `Color.kt` y `Theme.kt` con *Vanilla Custard*, *Flamingo Plume*, *Brandy Brown*, *Chocolate Fondant*, *Apricot Illusion* y *Dress Blues*. Desactivar sombras y gradientes.
- **Tipografía con Números Tabulares:** Configurar `Typography.kt` habilitando `FontFeature.enable("tnum")` para cifras monetarias y contadores.
- **Navegación Responsive Unificada:**
  - En móvil: Implementar `TopAppBar` estándar (botón Drawer, título, indicador de nube y botón de escaneo QR Brandy Brown) + `ModalNavigationDrawer` con los 10 módulos.
  - En tablet/desktop: Implementar `NavigationRail` / `Sidebar` persistente.
- **Componentes Base Flat:** `ExquisssitaCard` (borde 1px, sin sombra), `ExquisssitaButton` (Brandy Brown sólido), `FilterChipFlat` y `StatusBadge`.

### Fase 2 — Persistencia Local con Room (Single Source of Truth)
- Implementar las 18 entidades Room correspondientes a las tablas de backend.
- Crear los DAOs con consultas reactivas (`Flow<List<T>>`):
  - `MesaDao`, `OrdenDao`, `DetalleOrdenDao`, `TransaccionDao`, `ProductoDao`, `MovimientoInventarioDao`, `EmpleadoDao`, `HistorialPagoDao`, `PromocionDao`, `ClienteDao`, `VisitaDao`, `CorteCajaDao`, `SyncQueueDao`.
- Crear `TypeConverters` seguros para `Instant` (Epoch ms), `LocalDate` (String ISO) y listas/JSON.
- Escribir pruebas unitarias con base de datos en memoria (`Room.inMemoryDatabaseBuilder`).

### Fase 3 — Cliente Supabase, Autenticación y Resolución de Rol
- Configurar módulo Hilt `NetworkModule` con `createSupabaseClient`.
- Implementar `AuthRepository`:
  - Inicio de sesión con correo y contraseña.
  - Resolución del rol consultando `user_roles` (nunca `user_metadata`).
  - Cache local del rol y permisos en Room / DataStore seguro.
- Crear DTOs remotos con `@Serializable` correspondientes a todas las tablas y mapeadores bidireccionales `Dto <-> Entity <-> Domain`.

### Fase 4 — Motor Offline-First y Sincronización en Segundo Plano
- Diseñar `SyncQueueEntity`:
  ```kotlin
  @Entity(tableName = "sync_queue")
  data class SyncQueueEntity(
      @PrimaryKey val id: String, // UUID v4
      val entity: String,         // "orden", "pago", "movimiento", etc.
      val entityId: String,
      val operation: String,      // "INSERT", "UPDATE", "DELETE", "RPC"
      val payloadJson: String,
      val idempotencyKey: String, // UUID v4 único
      val createdAt: Long,
      val attempts: Int = 0,
      val lastAttemptAt: Long? = null,
      val status: String,         // "pending", "processing", "completed", "failed", "requires_review"
      val errorMessage: String? = null
  )
  ```
- Implementar `SyncWorker` con WorkManager:
  - Constraint: `NetworkType.CONNECTED`.
  - Reintentos automáticos con backoff exponencial.
  - Manejo de fallos transitorios vs permanentes.
- Configurar `RealtimeManager` para escuchar cambios en las 9 tablas suscritas e insertar/actualizar directamente en Room.

### Fase 5 — Módulo: Mesas y Órdenes (Captura 2)
- **UI Salón de Mesas:**
  - Encabezado con título `Mesas y órdenes`, subtítulo `Estado del salón en tiempo real` y botón `+ Nueva orden` en Brandy Brown.
  - Tabs `Mesas` vs `Para llevar (N)`.
  - Filter chips planos: `Todas`, `Libres`, `Ocupadas`, `Reservadas`.
  - Grid de tarjetas de mesa con badges por estado (Libre, Ocupada, Reservada), temporizador, mesero responsable, total en negrita y pill de sincronización (`Sincronizada` vs `Pendiente`).
- **Detalle de Comanda:** Lista de renglones con stepper numérico `[-] [N] [+]` e indicador de notas de preparación.
- **Regla de Negocio Crítica:** Implementar botón explícito **"Liberar Mesa"** cuando la comanda está saldada pero los clientes continúan sentados.

### Fase 6 — Módulo: Menú e Inventario
- Catálogo de productos organizado por categorías con pastillas planas (`Tacos`, `Bebidas`, `Especialidades`, `Postres`).
- Clasificación de productos controlados (`controla_stock = true`) y no controlados.
- **Kardex de Movimientos:** Historial inmutable en `movimientos_inventario`.
- Hoja emergente de media pantalla para ajuste manual rápido de stock (`entrada`, `ajuste`, `merma`).
- Integración con RPC atómico `fn_registrar_movimiento_inventario` para evitar saldos negativos concurrentes.

### Fase 7 — Módulo: Pagos y División de Cuentas (Captura 3)
- **UI de Cobro POS:**
  - Breadcrumb con mesa y orden (ej. `Mesa 01 • Orden #1842`).
  - Tarjeta `Productos seleccionados` con checkboxes individuales por producto y acción rápida `Seleccionar todos`.
  - Caja informativa de `Productos ya pagados` (para pagos parciales previos).
  - Tarjeta `Resumen de pago`: Total orden, Total pagado y `Total pendiente` en tipografía grande Brandy Brown `#7E392F`.
  - Selector de método de pago (`Efectivo`, `Tarjeta`, `Transferencia`).
  - Atajos rápidos de denominaciones de billetes en efectivo (`$50`, `$100`, `$200`, `$500`, `$1000`, `Exacto`).
  - Cálculo de cambio en tiempo real.
- Invocación local inmediata + encolado de RPC `fn_registrar_pago_atomico` con `idempotency_key`.

### Fase 8 — Módulo: Empleados y Nómina Semanal
- Directorio del personal con nombre, apellido, teléfono, salario semanal y día de pago asignado (1 = Lunes a 7 = Domingo).
- **Banner de Alerta Superior:** Detección en tiempo real del día actual:
  > *"Hoy corresponde pagar a [N] empleados"*
- Formulario de media pantalla para registrar pago con notas opcionales.
- Registro append-only en `historial_pagos_empleados` con prevención de pagos dobles en la misma semana.

### Fase 9 — Módulo: Promociones (Captura 4)
- Encabezado con título `Promociones`, subtítulo `Publicación y vigencia de beneficios` y botón `+ Crear promoción`.
- Pestañas con contadores: `Activas (N)`, `Inactivas (N)`, `Vencidas (N)`.
- Tarjetas con icono de etiqueta en contenedor Apricot Illusion, badge `Publicada`, fechas de vigencia y botones de acción (`Editar` outline y `Despublicar` text).

### Fase 10 — Módulo: QR y Fidelización de Clientes
- **En Móvil:** Pantalla de escaneo con CameraX y visor de encuadre minimalista.
- **Fallback / Desktop:** Campo de captura manual de código de cliente.
- Validación de formato y comprobación de restricción de una sola visita diaria mediante constraint `uq_cliente_visita_diaria`.
- Confirmación visual de acumulación de puntos.

### Fase 11 — Módulo: Corte de Caja y Arqueo Diario
- Resumen del turno en curso: Total facturado, cantidad de operaciones.
- Desglose por método de pago (`Efectivo`, `Tarjeta`, `Transferencia`).
- Campo para captura de efectivo real contado en gaveta y cálculo de sobrante/faltante.
- Invocación de RPC de cierre definitivo de turno con bloqueo de transacciones históricas.

### Fase 12 — Configuración, Monitor de Sincronización y Endurecimiento
- Pantalla de usuario activo, rol y cierre de sesión seguro.
- **Monitor de Sincronización:**
  - Botón visible destacado: **"Sincronizar ahora"**.
  - Lista de operaciones pendientes, en proceso, fallidas y marcadas como `requires_review`.
  - Acción de reintento manual para operaciones fallidas.
- Batería completa de pruebas unitarias (Turbine, MockK), pruebas de navegación y pruebas de interfaz Compose.
- Verificación de `./gradlew lint` y configuración de optimización ProGuard/R8.

---

## 5. Criterios de Aceptación y Definition of Done (DoD)

Una fase se considera **completada y aprobada** únicamente cuando cumple con los siguientes criterios:

1. **Compilación y Análisis Estático:**
   - `./gradlew compileDebugKotlin` compila sin errores.
   - `./gradlew lintVitalRelease` sin advertencias críticas o errores de arquitectura.
2. **Pruebas Automatizadas:**
   - Casos de uso y repositorios cuentan con pruebas unitarias con JUnit y MockK.
   - Los flujos de StateFlow en ViewModels están probados con Turbine.
3. **Cumplimiento Estricto del Sistema de Diseño:**
   - Cero sombras difusas (`elevation = 0.dp` / sin halos borrosos).
   - Cero gradientes decorativos.
   - Cero emojis.
   - Colores 100% basados en la paleta oficial de tokens Pantone.
   - Precios y cifras configuradas con números tabulares (`tnum`).
4. **Respeto Absoluto de las Reglas Financieras:**
   - Todos los montos se operan y almacenan en `Long` (centavos). Cero `Double` o `Float`.
5. **Comportamiento Offline-First:**
   - La pantalla renderiza datos locales de Room de inmediato sin spinner bloqueante.
   - Las operaciones realizadas sin conexión a internet se guardan en Room y en `sync_queue` con badge `Pendiente` en la UI.
6. **Seguridad y RLS:**
   - No se utiliza `service_role` en ninguna parte de la app.
   - Los permisos se comprueban mediante `auth_role()` y reglas RLS en el backend.
