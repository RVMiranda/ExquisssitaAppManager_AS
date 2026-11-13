# Release 11 — Módulo de Control de Gastos Operativos y Contraste de Rentabilidad en Corte de Caja

**Proyecto:** Exquisssita Manager (`ExquisssitaAppManager`)  
**Fecha:** 2026-11-12  
**Predecesores:** Release 03 (Seguridad y Roles), Release 04 (Datos y Sincronización), Release 09 (Corte de Caja y Cierres Diarios), Release 10 (Recompensas).  
**Estado:** FASE 1, FASE 2, FASE 3 Y FASE 4 COMPLETADAS (Backend Supabase, Room SQLite v11, Dominio, Repositorio Offline-First, UI Compose y Contraste en Corte de Caja); FASE 5 (Verificación Final) COMPLETADA.  

---

## 1. Justificación y Objetivos de Negocio

El cliente administrador solicitó una herramienta integral para el registro y auditoría de los gastos operativos diarios (ej. insumos, compra de tortillas por kilo a precio de mayoreo pactado, rejas de refresco, carbón, servicios menores). 

El objetivo es contrastar en tiempo real las ventas contra los egresos del día:
$$\text{Ganancia Neta Real} = \text{Ventas Totales} - (\text{Gastos Operativos} + \text{Nómina Pagada})$$

Además, se requiere agilizar la captura mediante un catálogo de **Gastos Frecuentes / Plantillas** (donde compras como la tortilla calculan el costo multiplicando los kilogramos ingresados por su precio preferencial), manteniendo la libertad de registrar cualquier gasto con concepto y monto libre.

---

## 2. Especificación Técnica de Backend (Supabase / PostgreSQL)

### 2.1 Migración `20261112150000_create_expenses_and_profit_contrast.sql`
Desplegada en el proyecto de producción `LaExquisssita (gcszsxeywywsrgnnnljr)`.

#### A. Tabla `public.plantillas_gastos` (Catálogo de Gastos Frecuentes / Fijos)
```sql
create table public.plantillas_gastos (
  id uuid primary key default gen_random_uuid(),
  nombre text not null,
  categoria text not null default 'Insumos',
  precio_unitario bigint not null default 0 check (precio_unitario >= 0),
  unidad_medida text not null default 'kg',
  activo boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sync_version bigint not null default 1
);
```

#### B. Tabla `public.gastos` (Egresos Operativos del Día)
```sql
create table public.gastos (
  id uuid primary key default gen_random_uuid(),
  fecha date not null default (now() at time zone 'America/Mexico_City')::date,
  concepto text not null,
  categoria text not null default 'Insumos',
  monto bigint not null check (monto > 0),
  cantidad numeric(8,2),
  precio_unitario bigint,
  metodo_pago text not null default 'Efectivo' check (metodo_pago in ('Efectivo', 'Transferencia', 'Otro')),
  plantilla_id uuid references public.plantillas_gastos(id) on delete set null,
  notas text,
  registrado_por uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sync_version bigint not null default 1
);
```

#### C. Políticas de Seguridad RLS
- Ambas tablas cuentan con RLS habilitado.
- Operaciones `SELECT`, `INSERT`, `UPDATE`, `DELETE` restringidas a usuarios autenticados con rol `admin` o `gerente`:
  `using (coalesce((select public.auth_role()), '') in ('admin', 'gerente'))`.
- Acceso anónimo o público totalmente revocado.

#### D. Procedimiento Almacenado de Rentabilidad Diaria
`public.obtener_resumen_rentabilidad_diaria(p_fecha date) -> jsonb`
- Centraliza en el servidor la agregación atómica de:
  - Ventas totales y ventas en efectivo (desde `transacciones`).
  - Gastos totales y gastos en efectivo (desde `gastos`).
  - Nómina total pagada en la fecha (desde `historial_pagos_empleados`).
  - Egresos totales (`gastos + nómina`).
  - Ganancia neta real (`ventas - egresos`).
  - Efectivo en caja esperado (`ventas_efectivo - gastos_efectivo - nómina`).

#### E. Supabase Realtime
- Tablas `public.gastos` y `public.plantillas_gastos` añadidas a la publicación `supabase_realtime`.

#### F. Sembrado Inicial de Plantillas
- `Tortilla de comal (kg)` ($24.00/kg)
- `Tortilla de máquina (kg)` ($22.00/kg)
- `Reja Refresco Coca-Cola (24 pzas)` ($290.00/reja)
- `Cilindro Gas LP (30 kg)` ($600.00)
- `Carbón / Leña (costal)` ($150.00)
- `Verduras y recaudería` (Variable)
- `Desechables y bolsas` (Variable)

---

## 3. Delimitación de Cargas de Trabajo (Fases de Implementación en Android)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FASE 2: DATOS LOCALES Y ROOM                    │
│  - core/database/local/entities/ExpenseEntity.kt                       │
│  - core/database/local/entities/ExpenseTemplateEntity.kt               │
│  - core/database/dao/ExpenseDao.kt                                     │
│  - core/database/dao/ExpenseTemplateDao.kt                             │
│  - core/database/AppDatabase.kt (registro de entidades y DAOs)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   FASE 3: MODELOS, REPOSITORIO Y DOMINIO               │
│  - data/model/ExpenseDtos.kt (DTOs de Supabase PostgREST)              │
│  - data/mappers/ExpenseMappers.kt                                      │
│  - domain/entity/Expense.kt, ExpenseTemplate.kt, DailyProfitSummary.kt │
│  - domain/repository/ExpenseRepository.kt                              │
│  - data/repository/ExpenseRepositoryImpl.kt (Offline-First)           │
│  - domain/usecases/expenses/ (Get, Save, Delete, GetProfitSummary)     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   FASE 4: INTERFAZ DE USUARIO (COMPOSE)                │
│  - features/expenses/ExpensesScreen.kt (Lista, Filtro Fecha, Resumen)  │
│  - features/expenses/ExpensesViewModel.kt & ExpensesUiState.kt         │
│  - features/expenses/ExpenseFormDialog.kt (Modal registro/edición)     │
│  - features/expenses/ExpensePresetSelector.kt (Cálculo kg x precio)    │
│  - features/cash_register/ (Integración tarjeta Rentabilidad Diaria)   │
│  - features/main/MainScaffold.kt (Destino navegación EXPENSES)         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Lineamientos de Implementación y Reglas de Calidad

1. **Valores Monetarios:**
   - En base de datos y memoria: centavos enteros de MXN (`bigint` en SQL, `Long` en Kotlin).
   - En presentación: `BigDecimal.valueOf(cents, 2)` con formato de moneda estándar (`$240.00`).
   - Prohibido el uso de tipos de coma flotante binaria (`Double`, `Float`) para montos monetarios.
2. **Offline-First:**
   - La pantalla debe permitir consultar y operar con los gastos cacheados en Room si no hay conexión activa.
3. **Zona Horaria de Negocio:**
   - Todas las fechas de agrupación comercial operan bajo `America/Mexico_City`.
4. **Sin Emojis:**
   - Estricto cumplimiento de cero emojis en código, commits, documentación y mensajes al usuario.

---

## 5. Bitácora de Implementación — Fase 2: Capa Local Room SQLite

Se completó satisfactoriamente la infraestructura de persistencia local en Room para el soporte offline-first del módulo de gastos:

### 5.1 Entidades Creadas
- `ExpenseTemplateEntity` (`expense_template_cache`):
  Almacena en caché local las plantillas y precios de referencia frecuentes (tortillas, refresco, gas, carbón, etc.).
  Campos: `id`, `name`, `category`, `unit_price` (centavos Long), `unit_of_measure`, `is_active`, `sync_version`, `updated_at`.
- `ExpenseEntity` (`expense_cache`):
  Almacena en caché local los gastos operativos diarios.
  Campos: `id`, `date` (formato ISO YYYY-MM-DD con índice), `concept`, `category`, `amount` (centavos Long), `quantity` (Double nullable para kg/piezas), `unit_price` (centavos Long nullable), `payment_method`, `template_id` (nullable con índice), `notes`, `registered_by` (UUID nullable), `sync_version`, `updated_at`, `pending_sync` (Int, 1 si está pendiente de sincronización remota), `is_deleted` (Int, 1 para soft delete offline).

### 5.2 DAOs Creados
- `ExpenseTemplateDao`:
  - `getAllActiveFlow()`: Flow reactivo para alimentar selectores y autocompletado en UI.
  - `getAllFlow()`: Flow reactivo con todas las plantillas.
  - `upsertAll()`, `upsert()`, `deleteById()`, `deleteAll()`.
- `ExpenseDao`:
  - `getExpensesByDateFlow(date)`: Flow reactivo filtrado por día operativo excluyendo marcados para eliminación (`is_deleted = 0`).
  - `getExpensesByDateRangeFlow(startDate, endDate)`: Flow para reportes semanales/mensuales.
  - `getTotalExpensesByDateFlow(date)`: Agregación SQL reactiva de monto total de egresos en centavos para el día.
  - `getTotalCashExpensesByDateFlow(date)`: Agregación SQL reactiva de gastos pagados en efectivo para contraste con caja.
  - `getPendingSyncExpenses()`: Consulta de registros locales con `pending_sync = 1`.
  - `upsertAll()`, `upsert()`, `deleteById()`, `markAsDeleted()`, `deleteSyncedNotIn()`.

### 5.3 Migración Room 10 -> 11 y Configuración de Base de Datos
- `AppDatabase.kt`:
  - Versión incrementada de 10 a 11.
  - Registro de `ExpenseEntity` y `ExpenseTemplateEntity` en `@Database(entities = [...])`.
  - Exposición de funciones abstractas `expenseDao()` y `expenseTemplateDao()`.
- `DatabaseModule.kt`:
  - Definición de `MIGRATION_10_11` con sentencias SQL exactas DDL para creación de `expense_cache` y `expense_template_cache` junto con sus índices.
  - Inyección de `MIGRATION_10_11` en el constructor de `Room.databaseBuilder`.
  - Provisión Hilt de `@Provides @Singleton fun provideExpenseDao(...)` y `provideExpenseTemplateDao(...)`.
- Validación:
  - Compilación exitosa de fuentes debug (`compileDebugSources`) con Gradle y KSP Room.
  - Generación automática del archivo de verificación de esquema `app/schemas/com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase/11.json`.

---

## 6. Bitácora de Implementación — Fase 3: Modelos, Repositorio Offline-First y Dominio

### 6.1 Modelos de Dominio y DTOs
- `domain/entity/Expense.kt`:
  - `Expense`: Modelo inmutable con montos en centavos enteros MXN (`amountCents: Long`), soporte opcional de `quantity` (`Double`) y `unitPriceCents` (`Long`), enum de método de pago (`ExpensePaymentMethod`: `EFECTIVO`, `TRANSFERENCIA`, `OTRO`), y categorías estándar (`Insumos`, `Servicios`, `Mantenimiento`, `Desechables`, `Otro`).
  - `ExpenseTemplate`: Plantilla o catálogo de gastos frecuentes con precio sugerido.
  - `DailyProfitSummary`: Agregado que contrasta ventas totales contra egresos operativos y nómina pagada.
- `data/model/ExpenseDtos.kt`:
  - `ExpenseDto`, `CreateExpenseRequestDto`, `UpdateExpenseRequestDto`, `ExpenseTemplateDto`, `DailyProfitSummaryDto`, `DailyProfitRpcParams`.
- `data/mappers/ExpenseMappers.kt`:
  - Funciones de mapeo bidireccionales entre entidades Room, DTOs de PostgREST y entidades de dominio.

### 6.2 Repositorio de Gastos (`ExpenseRepositoryImpl`)
- `domain/repository/ExpenseRepository.kt`:
  - `getExpensesFlow(date)`: Emisión reactiva local `Flow<List<Expense>>` filtrada por actor y día operativo.
  - `refreshExpenses(date)`: Sincronización remota desde `gastos` en Supabase con reconciliación de caché (`deleteSyncedNotIn`).
  - `saveExpense(expense)`: Soporte dual (inserción remota si hay conectividad activa; persistencia local con `PENDING_INSERT`/`PENDING_UPDATE` en caso de modo offline).
  - `deleteExpense(localId)`: Eliminación remota directa o marcado local `PENDING_DELETE`.
  - `syncPendingExpenses()`: Proceso de vaciado hacia Supabase al reestablecerse la red.
  - `getTemplatesFlow()` y `refreshTemplates()`: Catálogo de plantillas frecuentes.
  - `getDailyProfitSummary(date)`: Invocación remota del RPC `obtener_resumen_rentabilidad_diaria(p_fecha)` con fallback offline automático calculado desde las tablas locales de `Room` (`dailyClosureDao` y `expenseDao`).

### 6.3 Casos de Uso de Dominio e Inyección de Dependencias
- `domain/usecases/expenses/`:
  - `GetExpensesUseCase.kt`
  - `SaveExpenseUseCase.kt` (valida concepto no vacío, monto > 0 o cálculo automático cantidad x precio unitario)
  - `DeleteExpenseUseCase.kt`
  - `GetExpenseTemplatesUseCase.kt`
  - `GetDailyProfitSummaryUseCase.kt`
- `core/di/ExpensesModule.kt`:
  - Módulo Hilt `@InstallIn(SingletonComponent::class)` que vincula `ExpenseRepositoryImpl` con `ExpenseRepository`.

### 6.4 Pruebas Unitarias
- `app/src/test/java/com/mirandadevsource/exquisssitaappmanager/domain/usecases/expenses/SaveExpenseUseCaseTest.kt`:
  - Prueba de rechazo de concepto vacío.
  - Prueba de rechazo de monto no positivo sin cantidad ni precio unitario.
  - Prueba de cálculo derivado de monto total a partir de cantidad (10 kg) y precio unitario ($24.00/kg) resultando en $240.00.
  - Prueba de registro manual válido con limpieza de espacios en blanco.
- Ejecución completa de suite de pruebas unitarias (`testDebugUnitTest`): 100% exitosa sin fallos ni regresiones.

---

## 7. Bitácora de Implementación — Fase 4: Interfaz de Usuario (Jetpack Compose) y Contraste en Corte de Caja

### 7.1 Pantalla Principal de Gastos (`ExpensesScreen.kt` & `ExpensesViewModel.kt`)
- `features/expenses/ExpensesUiState.kt`:
  - Estado reactivo para la pantalla: `selectedDate`, `expenses`, `templates`, `profitSummary`, `selectedCategoryFilter`, `isFormDialogOpen`, `editingExpense`, `expenseToDelete`, `totalExpensesCents`, `cashExpensesCents`, `otherExpensesCents`.
- `features/expenses/ExpensesViewModel.kt`:
  - Vinculación con `AuthRepository` (detección de rol `admin` y `gerente`) y `NetworkMonitor` (estado de red en vivo).
  - Observación reactiva local de gastos por fecha operativa (`America/Mexico_City`).
  - Sincronización en segundo plano con Supabase y recarga automática de plantillas y rentabilidad diaria.
  - Orquestación de creación, edición, eliminación y manejo de mensajes de éxito/error.
- `features/expenses/ExpensesScreen.kt`:
  - Cabecera con título, estado de conectividad (badge ambar si está sin conexión), y botón de refresco.
  - Selector de fecha con atajo "Ir a Hoy" y diálogo de calendario `DatePickerDialog`.
  - Tarjeta de resumen de egresos: Total Egresos del Día, desglose en efectivo de caja vs otros métodos, y badge dinámico de ganancia neta real cuando hay ventas registradas.
  - Fila de chips de filtro por categoría (`Todas`, `Insumos`, `Servicios`, `Mantenimiento`, `Desechables`, `Otro`).
  - Lista de tarjetas de gasto con badge de categoría, desglose `cantidad x precio unitario` (ej. `10 kg x $24.00`), método de pago, monto formateado, indicador de `Pendiente de sincronizar` si aplica, y botones de edición y eliminación.
  - Botón flotante `FloatingActionButton` (+) para registrar egresos rápidamente.
  - Diálogo de confirmación para eliminar gastos.

### 7.2 Modal de Registro y Edición (`ExpenseFormDialog.kt`)
- Selección rápida de plantillas frecuentes (ej. `Tortilla de comal`, `Tortilla de máquina`, `Reja Refresco Coca-Cola`, `Cilindro Gas LP`).
- Selector de categoría interactivo.
- Modo dual de captura:
  - **Monto total directo** (ej. $290.00).
  - **Por cantidad y precio unitario** (ej. 10 kg x $24.00 = $240.00 total calculado automáticamente).
- Selector de método de pago (`Efectivo`, `Transferencia`, `Otro`).
- Campo para notas o detalles (ej. "Dinero puesto por el patrón").
- Validación previa al guardado (concepto obligatorio, monto > 0).

### 7.3 Integración del Contraste de Rentabilidad en Corte de Caja (`CashRegisterScreen.kt`)
- `CashRegisterUiState.kt` y `CashRegisterViewModel.kt`:
  - Integración de `GetDailyProfitSummaryUseCase` al cargar cortes o realizar un corte nuevo.
- `CashRegisterScreen.kt`:
  - Enriquecimiento de `DaySummaryCard`:
    - Ventas Totales (+): `$X.XX` (verde).
    - Gastos Operativos (-): `-$Y.XX` (rojo).
    - Nómina Pagada (-): `-$Z.XX` (rojo).
    - Tarjeta destacada de **Ganancia Neta Real**: `$ (X - Y - Z).XX` con detalle de **Efectivo esperado en caja**.
    - Botón de navegación directa hacia "Gestionar Gastos" (`EXPENSES`).

### 7.4 Navegación Global (`MainScaffold.kt`)
- Registro de `MainNavDestination.EXPENSES` ("Control de Gastos", `Icons.Outlined.Receipt`, restringido a gerencia y administración).
- Integración en el drawer lateral dentro del grupo `Finanzas` (junto con `Corte de Caja`).
- Enrutamiento hacia `ExpensesScreen` y soporte de retroceso (`BackHandler`).
- Navegación fluida entre `CashRegisterScreen` y `ExpensesScreen`.

### 7.5 Verificación y Calidad
- Compilación de fuentes debug (`compileDebugSources`) completada exitosamente sin advertencias.
- Suite completa de pruebas unitarias (`testDebugUnitTest`): 100% aprobada.

---

## 8. Diagnóstico y Corrección de Incidencias en Producción (PostgREST PGRST203 y Salud de BD)

### 8.1 Error `PGRST203` en `registrar_pago_nomina`
- **Causa Raíz:** En la migración `20261101160000_employee_loans_and_payroll_deductions.sql`, se añadió una nueva sobrecarga de 6 parámetros para `public.registrar_pago_nomina(p_empleado_id, p_fecha_inicio, p_fecha_fin, p_metodo_pago, p_monto_deduccion_prestamo, p_prestamo_id)` con valores por defecto en los parámetros 5 y 6. Sin embargo, no se eliminó la función preexistente de 4 parámetros `public.registrar_pago_nomina(p_empleado_id, p_fecha_inicio, p_fecha_fin, p_metodo_pago)`.
  Al invocar la RPC desde la aplicación con 4 argumentos, PostgREST no pudo discernir cuál de las dos funciones candidatas ejecutar, arrojando el error `PGRST203: could not choose the best candidate function`.
- **Solución:** Se creó la migración `supabase/migrations/20261112210000_drop_overloaded_payroll_payment_rpc.sql` que eliminó la sobrecarga obsoleta de 4 parámetros:
  `DROP FUNCTION IF EXISTS public.registrar_pago_nomina(uuid, date, date, text);`
  dejando únicamente la función unificada de 6 parámetros con defaults.
- **Validación:** Se aplicó con `npx supabase db push --remote --yes` y se probó la invocación de la RPC vía PostgREST con `curl.exe`. La ambigüedad quedó completamente eliminada (código HTTP 400 por validación de UUIDs de prueba, confirmando resolución unívoca de la función en lugar de `PGRST203`).

### 8.2 Corrección en `delete_customer_account` (PostgreSQL 17)
- **Causa Raíz:** El linter de base de datos (`npx supabase db lint --remote`) identificó que la función `public.delete_customer_account` intentaba actualizar `confirmed_at = NULL` en la tabla `auth.users`, lo cual viola la restricción de PostgreSQL 17 (código `428C9`: `column "confirmed_at" can only be updated to DEFAULT`).
- **Solución:** Se creó la migración `supabase/migrations/20261112213000_fix_delete_customer_account_confirmed_at.sql` sustituyendo `confirmed_at = NULL` por `confirmed_at = DEFAULT`.
- **Validación:** Se desplegó a producción y se ejecutó `npx supabase db lint --remote`, certificando cero errores en el esquema de base de datos.

### 8.3 Diagnóstico de Salud de Base de Datos y Supabase Realtime
- **Diagnóstico del Estado "Unhealthy (Realtime: Unhealthy)":**
  El reporte visual del panel de control de Supabase con advertencia en Realtime correspondió a la reanudación reciente del proyecto desde su estado de reposo (tal como indica la propia consola: *"Recently restored projects can take up to 5 minutes to become fully operational"*).
- **Verificación desde CLI:**
  - `npx supabase projects list`: El proyecto `LaExquisssita` (`gcszsxeywywsrgnnnljr`) se encuentra en estado `ACTIVE_HEALTHY`.
  - Se verificó la réplica lógica y publicación `supabase_realtime`: las 20 tablas del proyecto (incluidas las nuevas tablas `gastos` y `plantillas_gastos`) cuentan con clave primaria definida y replicación activa.
  - El motor de base de datos PostgreSQL, la API PostgREST, el servicio Auth y el almacenamiento Storage operan con estado `Healthy`.

---

## 9. Refinamiento de Interfaz de Gastos, Pestaña de Plantillas y Unificación Visual

### 9.1 Unificación de la Paleta de Color
- Se corrigió la discrepancia visual donde la pantalla de gastos utilizaba un fondo `ApricotIllusion` no estándar.
- Se homologó el contenedor principal al fondo general del tema (`VanillaCustard` `0xFFFAF3E0`), manteniendo las tarjetas de contenido con superficie blanca (`Color.White`), bordes suaves en `ApricotIllusion` y texto contrastado en `ChocolateFondant`.
- Se corrigieron los fondos de diálogos modales (`ExpenseFormDialog`, `ExpenseTemplateFormDialog`, `AlertDialog` y `DatePickerDialog`) a `VanillaCustard` para alinearse con el patrón de diseño del resto de módulos (`CashRegisterScreen`, `EmployeesScreen`).

### 9.2 División en Dos Pestañas: Gastos y Plantillas
- Se implementó un selector de pestañas (`PrimaryTabRow`) en la cabecera de `ExpensesScreen`:
  1. **Pestaña de Gastos:**
     - Registro y auditoría oficial de los egresos diarios del negocio.
     - Selector de fecha operativa con atajo a "Hoy" y diálogo de calendario.
     - Resumen de egresos (total del día, efectivo de caja vs transferencias) y contraste en vivo de ganancia neta real frente a ventas cobradas.
     - Filtros por categoría (`Todas`, `Insumos`, `Servicios`, `Mantenimiento`, `Desechables`, `Otro`).
     - Lista de gastos registrados con edición, eliminación y estado offline.
     - Botón flotante para registrar nuevos gastos.
  2. **Pestaña de Plantillas:**
     - Catálogo de insumos y elementos habituales adquiridos por el negocio (tortillas por kilo, rejas de refresco, cilindros de gas, carbón, desechables).
     - Visualización de precio unitario pactado/base o indicación de precio variable/libre.
     - Control para activar o desactivar plantillas del catálogo de selección rápida.
     - Filtros por categoría y acciones de edición y eliminación.
     - Botón flotante para crear nuevas plantillas.

### 9.3 Formulario de Plantillas (`ExpenseTemplateFormDialog.kt`)
- Permite capturar insumos frecuentes con:
  - Nombre libre (ej. "Tortilla de comal", "Reja Coca-Cola 24 pzas").
  - Categoría (`Insumos`, `Servicios`, `Mantenimiento`, `Desechables`, `Otro`).
  - Unidad de medida (campo libre con sugerencias rápidas: `kg`, `pza`, `reja`, `costal`, `litro`, `servicio`).
  - Precio unitario pactado / base en centavos MXN (con libertad de dejarlo en `$0.00` para compras de precio variable).
  - Switch para definir si la plantilla está activa en el catálogo rápido.

### 9.4 Registro Oficial de Gastos (`ExpenseFormDialog.kt`)
- Opción destacada para elegir entre "Gasto libre / nuevo" o seleccionar cualquier plantilla frecuente predefinida.
- Al seleccionar una plantilla, se autocompleta el concepto, categoría y precio unitario si está configurado.
- Soporte dual de captura:
  - **Por cantidad y precio unitario:** cálculo dinámico del total en tiempo real (ej. 10 kg x $24.00 = $240.00).
  - **Monto total directo:** captura directa en pesos (ej. $290.00).
- Método de pago (`Efectivo`, `Transferencia`, `Otro`) y notas o detalles adicionales.

---

## 10. Corrección y Consolidación del Contraste de Rentabilidad (Ventas vs Gastos y Nómina)

### 10.1 Causa Raíz de la Ausencia de Datos en Contraste
1. **Desalineación de llaves JSON en RPC vs DTO:** La función SQL `obtener_resumen_rentabilidad_diaria` retornaba las llaves `ventas_totales`, `gastos_totales`, `egresos_totales`, etc., mientras que el DTO `DailyProfitSummaryDto` esperaba `ventas_totales_centavos`, `gastos_totales_centavos`. Esto causaba que la deserialización fallara o asignara ceros.
2. **Cálculo de Ventas en Backend:** La versión original de la función solo agregaba `transacciones`, pero en el flujo operativo del negocio los cortes de caja (`cortes_caja`) consolidan turnos y pedidos cerrados. Si no había transacciones sueltas en la fecha pero sí cortes realizados, las ventas aparecían en 0.
3. **Manejo de Errores en Repositorio:** Si la llamada remota fallaba, el repositorio retornaba error en lugar de calcular automáticamente la rentabilidad a partir de los datos locales cacheados en Room (`dailyClosureDao` y `expenseDao`).

### 10.2 Soluciones Implementadas
1. **Migración `20261112220000_upgrade_profit_contrast_rpc.sql`:**
   - Desplegada a producción en Supabase.
   - Consolida ventas tomando el mayor valor entre `transacciones`, `cortes_caja` y `ordenes` cerradas de la fecha comercial.
   - Retorna ambas convenciones de llaves JSON (`ventas_totales` y `ventas_totales_centavos`, etc.) garantizando 100% de compatibilidad.
   - Permisos de ejecución ratificados para `authenticated`, `service_role` y `postgres`.
2. **DTO Resiliente (`DailyProfitSummaryDto.kt` y `ExpenseMappers.kt`):**
   - Resolución dual de llaves (`resolvedVentasTotales`, `resolvedGastosTotales`, etc.).
3. **Fusión Híbrida en Repositorio (`ExpenseRepositoryImpl.kt`):**
   - Combina los datos del backend con la caché local de Room para asegurar que cualquier corte o gasto reciente se refleje de inmediato.
   - En caso de fallo de red o error del servidor, conmuta de forma transparente al cálculo local en Room sin retornar nulo.
4. **Presentación Visual Garantizada en `CashRegisterScreen.kt` y `ExpensesScreen.kt`:**
   - En `CashRegisterScreen`, la sección "Contraste de Rentabilidad del Día" utiliza `effectiveProfit`, mostrándose siempre que existan ventas en el corte o gastos operativos del día, calculando la ganancia neta real y el efectivo esperado en caja.
   - En `ExpensesScreen`, el badge de ganancia neta o balance del día se muestra si existen ventas o egresos registrados.



