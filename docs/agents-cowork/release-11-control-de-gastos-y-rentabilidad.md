# Release 11 — Módulo de Control de Gastos Operativos y Contraste de Rentabilidad en Corte de Caja

**Proyecto:** Exquisssita Manager (`ExquisssitaAppManager`)  
**Fecha:** 2026-11-12  
**Predecesores:** Release 03 (Seguridad y Roles), Release 04 (Datos y Sincronización), Release 09 (Corte de Caja y Cierres Diarios), Release 10 (Recompensas).  
**Estado:** FASE 1, FASE 2 Y FASE 3 COMPLETADAS (Backend Supabase, Room SQLite v11, Modelos, Repositorio Offline-First y Casos de Uso); FASES 4–5 LISTAS PARA IMPLEMENTACIÓN.  

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
