# Exquisssita Manager — Contrato de Backend y Base de Datos Supabase

> **Documento de Especificación de Backend, RLS, RPCs y Sincronización**  
> Proyecto Android Studio Nativo: `ExquisssitaAppManager`  
> Backend Remoto: Supabase / PostgreSQL (Proyecto Compartido)  
> Regla Fundamental: Android es un cliente equivalente. No altera esquemas ni usa `service_role`.

> **Estado de verificación (2026-10-10):** Los nombres de tablas y columnas en la sección 2 se contrastaron con el esquema remoto mediante Supabase CLI y con el volcado remoto del 2026-10-07 que conserva el proyecto Flutter. Este documento describe el esquema desplegado, no el modelo ideal que se quiere alcanzar. Las migraciones locales nuevas de Android no se consideran aplicadas hasta que `supabase migration list --linked` las muestre en ambos lados.
> Los fragmentos de `CREATE TABLE` de esta sección resumen columnas/tipos/defaults; no pretenden reproducir todas las PK, FK, constraints, grants o políticas. Consultar la migración remota correspondiente antes de inferir esas reglas.

---

## 1. Arquitectura de Backend y Principios de Integridad

El backend opera sobre una instancia gestionada de **Supabase / PostgreSQL**, aprovechando:
- **Supabase Auth:** Autenticación segura de personal basada en email/contraseña.
- **PostgreSQL 15+:** Motor relacional con integridad referencial estricta, restricciones `CHECK`, claves foráneas y disparadores.
- **Row Level Security (RLS):** Toda tabla pública tiene RLS habilitado. La seguridad se evalúa en el motor de base de datos mediante la función `auth_role()`.
- **Funciones RPC Atómicas:** Operaciones financieras y de inventario se ejecutan mediante procedimientos almacenados con control de transacciones y bloqueos explícitos (`SELECT FOR UPDATE`).
- **Supabase Realtime:** Publicación de eventos de inserción, actualización y borrado sobre 9 tablas clave para acelerar la actualización local.

### 1.1 Identificadores Primarios (UUID v4)
Todas las entidades sincronizables utilizan **UUID v4** como clave primaria:
- Los UUID se generan **en el cliente Android** (`UUID.randomUUID().toString()`) antes de persistir localmente en Room.
- Esto permite crear órdenes, líneas de detalle, pagos y movimientos en modo **completamente offline** sin requerir un viaje de ida y vuelta a secuencias o generadores de IDs remotos.

### 1.2 Regla de Tipos Monetarios
- En PostgreSQL: Columnas monetarias definidas como `BIGINT` (ej. `precio_cents`, `monto_cents`).
- En Kotlin (Android): Tipos de datos `Long`.
- Cero uso de `Double` o `Float` en columnas de dinero en base de datos ni en transporte de red (DTOs).

---

## 2. Esquema Detallado de Tablas de Base de Datos

### 2.1 Tabla `user_roles`
Mapea la identidad de Supabase Auth (`auth.users`) con el rol operativo en la base de datos.
```sql
CREATE TABLE public.user_roles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('admin', 'empleado')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```
> [!NOTE]
> - La tabla `public.user_roles` **no contiene columna `activo`**.
> - La restricción `CHECK` actual en la base de datos remota admite los roles `'admin'` y `'empleado'`.
> - Las políticas de seguridad (`user_roles_self_select`) permiten a un usuario autenticado leer su propio registro (`user_id = auth.uid()`), y a un usuario con rol `'admin'` consultar todos los roles.

### 2.2 Tabla `empleados`
Directorio del personal para control operativo y nómina semanal.
```sql
CREATE TABLE public.empleados (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    nombre TEXT NOT NULL,
    apellido TEXT NOT NULL,
    telefono TEXT,
    salario BIGINT NOT NULL,
    dia_pago INTEGER NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```
> [!NOTE]
> - La columna de relación con Auth es **`user_id`** (no `auth_user_id`).
> - La columna de remuneración es **`salario BIGINT`**; no se llama `salario_base_cents` y no debe asumirse escala en centavos sin validar cómo se guardaron los datos existentes.
> - El estado activo del empleado se gestiona mediante la columna **`activo`** en esta tabla. Si `activo = false`, el cliente bloquea el acceso del colaborador no administrativo.
> - La columna `user_id` no cuenta con constraint `UNIQUE` en el esquema remoto verificado. Antes de resolver perfiles con `.single()`, comprobar que no existan vínculos duplicados.


### 2.3 Tabla `historial_pagos_empleados`
Registro append-only de pagos de nómina por periodo semanal.
```sql
CREATE TABLE public.historial_pagos_empleados (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    empleado_id UUID NOT NULL,
    monto BIGINT NOT NULL,
    fecha_pago DATE NOT NULL DEFAULT CURRENT_DATE,
    notas TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1,
    periodo_inicio DATE,
    fecha_programada DATE
);
```
No existen en el remoto `monto_cents`, `periodo_fin`, `registrado_por` ni `idempotency_key` para esta tabla.

### 2.4 Tabla `productos`
Catálogo del menú con clasificación de control de existencias.
```sql
CREATE TABLE public.productos (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    nombre TEXT NOT NULL,
    precio BIGINT NOT NULL,
    categoria TEXT NOT NULL,
    controla_inventario BOOLEAN NOT NULL DEFAULT false,
    stock_actual INTEGER DEFAULT 0,
    stock_minimo INTEGER DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```
El importe se llama `precio` (BIGINT); el indicador desplegado es `controla_inventario`.

### 2.5 Tabla `movimientos_inventario`
Kardex inmutable para trazabilidad de existencias físicas.
```sql
CREATE TABLE public.movimientos_inventario (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    producto_id UUID NOT NULL,
    cantidad INTEGER NOT NULL,
    tipo TEXT NOT NULL,
    referencia_id UUID,
    notas TEXT,
    creado_por UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key UUID NOT NULL DEFAULT uuid_generate_v4(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.6 Tabla `mesas`
Salón y distribución física.
```sql
CREATE TABLE public.mesas (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    numero_mesa TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'Libre',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.7 Tabla `ordenes`
Comandas abiertas, completadas o canceladas.
```sql
CREATE TABLE public.ordenes (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    mesa_id UUID,
    cliente_id UUID,
    tipo_servicio TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'Abierta',
    fecha_apertura TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_cierre TIMESTAMPTZ,
    notas TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.8 Tabla `detalle_orden`
Renglones de productos agregados a una comanda.
```sql
CREATE TABLE public.detalle_orden (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    orden_id UUID NOT NULL,
    producto_id UUID NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario BIGINT NOT NULL,
    estado_pago TEXT NOT NULL DEFAULT 'Pendiente',
    notas TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.9 Tabla `transacciones`
Comprobantes de pago de una comanda (admite pagos parciales o divididos).
```sql
CREATE TABLE public.transacciones (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    orden_id UUID NOT NULL,
    monto BIGINT NOT NULL,
    metodo_pago TEXT NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key UUID NOT NULL DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.10 Tabla `pago_detalles`
Asocia una transacción con los renglones específicos cubiertos en la comanda.
```sql
CREATE TABLE public.pago_detalles (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    transaccion_id UUID NOT NULL,
    detalle_orden_id UUID NOT NULL,
    cantidad INTEGER NOT NULL,
    monto BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.11 Tabla `venta_diaria`
Snapshot diario de ventas (no existe `public.cortes_caja` en el esquema remoto).
```sql
CREATE TABLE public.venta_diaria (
    id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    total_efectivo BIGINT NOT NULL DEFAULT 0,
    total_tarjeta BIGINT NOT NULL DEFAULT 0,
    total_otro BIGINT NOT NULL DEFAULT 0,
    total_global BIGINT NOT NULL,
    num_ordenes INTEGER NOT NULL DEFAULT 0,
    hora_cierre TIMESTAMPTZ NOT NULL DEFAULT now(),
    cerrado_por UUID,
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.12 Tabla `promociones`
Ofertas, 2x1 y beneficios temporales.
```sql
CREATE TABLE public.promociones (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    titulo TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    imagen_url TEXT,
    fecha_publicacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_vencimiento DATE,
    activo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

### 2.13 Tablas `clientes` y `visitas_clientes`
Módulo de lealtad y escaneo QR.
```sql
CREATE TABLE public.clientes (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    user_id UUID,
    nombre TEXT NOT NULL,
    telefono TEXT,
    visitas_totales INTEGER DEFAULT 0,
    puntos_lealtad INTEGER DEFAULT 0,
    fecha_registro TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    qr_token_hash TEXT,
    sync_version BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE public.visitas_clientes (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    cliente_id UUID NOT NULL,
    fecha DATE NOT NULL DEFAULT ((now() AT TIME ZONE 'America/Mexico_City'))::date,
    puntos_otorgados INTEGER NOT NULL DEFAULT 10,
    registrado_por UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_id UUID,
    sync_version BIGINT NOT NULL DEFAULT 1
);
```
Columnas reales: `clientes.qr_token_hash` guarda el hash del token, no el token/QR en claro; `clientes.user_id` vincula al usuario registrado en la app de clientes; `puntos_lealtad` y `visitas_totales` son los acumulados; `visitas_clientes.fecha` es la fecha local de negocio. La cuenta del cliente es requisito para acreditar visitas/puntos. El catálogo de promociones públicas es independiente y puede ser consultado sin cuenta, sujeto a las políticas de lectura.

Índices únicos relevantes verificados: `clientes_qr_token_hash_uidx` (parcial cuando el hash no es nulo), `visitas_clientes_cliente_id_fecha_key` y `visitas_clientes_usuario_fecha_uidx` (parcial cuando `usuario_id` no es nulo). La RPC vigente `registrar_visita_qr(p_qr_token text)` exige un cliente con `user_id` vinculado y evita una segunda acreditación en el mismo día de negocio por cuenta. No insertar clientes desde la app de empleados sin vínculo Auth: la app de clientes debe crear/vincular su perfil y emitir el QR opaco; la pantalla de empleado solo busca y acredita.

### 2.14 Esquema Privado `private_sync.critical_receipts`
Recibos idempotentes de operaciones críticas. El esquema privado también contiene `changes` y `receipts`.
```sql
CREATE TABLE private_sync.critical_receipts (
    key UUID NOT NULL,
    actor_id UUID NOT NULL,
    entity TEXT NOT NULL,
    request JSONB NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
`private_sync.receipts` contiene `key`, `actor_id`, `request`, `result`, `created_at`. `private_sync.changes` contiene `seq`, `entity`, `entity_id`, `version`, `deleted`, `data`, `committed_at`.

---

## 3. Modelo de Seguridad y Row Level Security (RLS)

### 3.1 Función de Resolución Segura de Rol `auth_role()`
La aplicación **nunca confía en `raw_user_meta_data` del JWT** porque dicho objeto no es la fuente de verdad autoritativa para el control de acceso en la base de datos. El rol se resuelve mediante la función de base de datos `public.auth_role()`:

La firma remota es `public.auth_role() RETURNS text`. La migración `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc` fijó `search_path = ''` y calificó los objetos usados. El rol `empleado` solo se devuelve cuando existe su perfil `public.empleados` vinculado por `user_id` con `activo = true`; `admin` depende de la asignación en `public.user_roles`.

### 3.2 Políticas de Row Level Security (RLS) para Autenticación y Perfiles
Las políticas activas en la base de datos remota de Supabase garantizan que:
1. **`public.user_roles` (`user_roles_self_select`):**
   - Comando: `SELECT` para rol `{authenticated}`.
   - Condición (`USING`): `((user_id = (SELECT auth.uid())) OR (auth_role() = 'admin'))`.
   - Permite que cualquier usuario autenticado consulte su propio rol asignado, y que un usuario con rol `'admin'` consulte todos los roles.
2. **`public.empleados` (`empleados_select_own` y `empleados_admin_all`):**
   - **`empleados_select_own`:** Comando `SELECT` con condición `(auth.uid() = user_id)`. Permite al empleado leer únicamente su propia ficha operativa.
   - **`empleados_admin_all`:** Comando `ALL` (SELECT, INSERT, UPDATE, DELETE) con condición `(auth_role() = 'admin')`. Concede acceso administrativo total a directores.

### 3.3 Reglas de Negocio para el Ciclo de Vida de Autenticación
- **Alta de cuentas:** El alta de usuarios (empleados y administradores) se realiza **exclusivamente desde la consola/backend de Supabase** (creación de usuario en `auth.users`, inserción en `public.user_roles` y ficha en `public.empleados`). La app no dispone de pantalla de registro ni flujo `sign_up`.
- **Deshabilitación (Soft Delete):** Para dar de baja a un empleado, se marca `activo = false` en `public.empleados`. Al autenticarse, si el empleado tiene `activo = false`, la app rechaza la sesión con error de acceso no autorizado. Para borrado definitivo, se elimina el registro (hard delete) en cascada.
- **Sin recuperación de contraseña:** La app **no incluye enlace ni flujo de "Recuperar contraseña"**. En caso de olvido, el administrador debe restablecerla directamente en Supabase.
- **Límite de sesión (10 horas):** La sesión en la aplicación tiene una vigencia máxima estricta de 10 horas (`36,000,000 ms`). Cumplido este tiempo, la app expira la sesión local, elimina tokens y requiere credenciales nuevamente.
- **Soporte Offline-First para la sesión:** Para operar sin conexión, la sesión validada y el perfil de usuario se persisten cifrados en almacenamiento seguro local (`EncryptedSessionManager` / `SecureAuthStore`). Si la app inicia sin conexión a internet y la última sesión guardada está dentro del margen de las 10 horas, se habilita el acceso en modo local (`isOffline = true`).

### 3.4 Roles reconocidos por la base remota

La restricción actual de `public.user_roles.role` acepta `admin` y `empleado`. Los roles gerente, cajero y mesero que aparecen en propuestas de UI/reglas todavía no forman parte del contrato de base de datos; no deben usarse como valores autorizados hasta una migración acordada.

La matriz siguiente es una intención de producto, no una garantía de autorización desplegada. La autorización efectiva depende de las políticas RLS y RPC revisadas en Supabase.

| Módulo | Admin | Gerente | Cajero | Mesero | Empleado |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Mesas (Lectura/Update)** | Total | Total | Total | Total | Solo lectura |
| **Órdenes (Crear/Modificar)** | Total | Total | Total | Total | Denegado |
| **Cancelar Orden** | Permitido | Permitido | Denegado | Denegado | Denegado |
| **Cobro / Transacciones** | Total | Total | Total | Denegado | Denegado |
| **Catálogo Menú (Escritura)** | Total | Total | Denegado | Denegado | Denegado |
| **Inventario / Ajustes** | Total | Total | Solo lectura | Solo lectura | Denegado |
| **Nómina y Empleados** | Total | Total | Denegado | Denegado | Solo su perfil |
| **Corte de Caja (Cierre)** | Total | Total | Su turno | Denegado | Denegado |
| **Promociones** | Total | Total | Solo lectura | Solo lectura | Solo lectura |

---

## 4. Funciones RPC Críticas y Atomicidad

Para salvaguardar la consistencia contable y evitar condiciones de carrera, las operaciones críticas no se componen mediante múltiples inserts sueltos desde la app, sino mediante llamadas RPC transaccionales:

> **No usar los ejemplos históricos siguientes como SQL desplegado.** Se conserva el contrato de las funciones públicas vigentes aquí. Las funciones `fn_registrar_pago_atomico` y `fn_registrar_movimiento_inventario` no existen con esos nombres en la base remota.

### 4.1 RPC desplegadas

| Función remota | Argumentos | Uso |
| --- | --- | --- |
| `registrar_visita_qr` | `p_qr_token text` | Valida el token QR y acredita visita/puntos a cliente registrado. |
| `registrar_visita_cliente` | `p_cliente_id uuid` | RPC histórica; ejecución revocada a `PUBLIC`, `anon` y `authenticated`. No usar. |
| `registrar_pago` | `p_orden_id uuid, p_monto bigint, p_metodo_pago text, p_idempotency_key uuid, p_asignaciones jsonb` | Pago idempotente y asignaciones de detalles. |
| `registrar_movimiento_inventario` | `p_producto_id uuid, p_cantidad integer, p_tipo text, p_idempotency_key uuid, p_referencia_id uuid, p_notas text` | Movimiento de inventario. |
| `realizar_corte_caja_seguro` | `p_fecha date` | Corte de caja diario seguro. |
| `sync_execute_v2` | `p_entity text, p_entity_id text, p_operation text, p_payload jsonb, p_key uuid, p_expected_version bigint` | Escritura sincronizada idempotente. |
| `sync_pull` | `p_after bigint, p_watermark bigint, p_limit integer` | Lectura paginada del registro de cambios. |
| `set_user_role` | `target_email text, target_role text` | Asignación administrativa de rol. |

Para QR, la app envía el token en claro solo como argumento HTTPS de la RPC; la DB calcula SHA-256 y compara con `clientes.qr_token_hash`. El QR debe ser estable y no contener datos personales. Solo el cliente con `clientes.user_id` vinculado puede recibir puntos y visitas. Clientes sin registro pueden consultar promociones públicas según las políticas vigentes, pero no participan en el programa de lealtad.

### 4.2 Referencias históricas de diseño (no desplegadas)
```sql
CREATE OR REPLACE FUNCTION public.fn_registrar_pago_atomico(
    p_idempotency_key UUID,
    p_orden_id UUID,
    p_detalle_ids UUID[],
    p_monto_cents BIGINT,
    p_metodo_pago VARCHAR,
    p_monto_recibido_cents BIGINT,
    p_cambio_cents BIGINT
)
RETURNS JSONB AS $$
DECLARE
    v_transaccion_id UUID;
    v_total_orden BIGINT;
    v_total_pagado BIGINT;
    v_detalle_id UUID;
BEGIN
    -- 1. Idempotencia: Verificar si ya fue procesada
    IF EXISTS (SELECT 1 FROM private_sync.critical_receipts WHERE idempotency_key = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'already_processed', 'idempotency_key', p_idempotency_key);
    END IF;

    -- 2. Registrar Transacción
    v_transaccion_id := gen_random_uuid();
    INSERT INTO public.transacciones (
        id, orden_id, monto_cents, metodo_pago,
        monto_recibido_cents, cambio_cents, creado_por, idempotency_key
    ) VALUES (
        v_transaccion_id, p_orden_id, p_monto_cents, p_metodo_pago,
        p_monto_recibido_cents, p_cambio_cents, auth.uid(), p_idempotency_key
    );

    -- 3. Marcar detalles pagados y vincular a pago_detalles
    FOREACH v_detalle_id IN ARRAY p_detalle_ids LOOP
        UPDATE public.detalle_orden
        SET estado_pago = 'pagado', updated_at = now()
        WHERE id = v_detalle_id;

        INSERT INTO public.pago_detalles (transaccion_id, detalle_orden_id, monto_cents)
        SELECT v_transaccion_id, id, subtotal_cents
        FROM public.detalle_orden WHERE id = v_detalle_id;
    END LOOP;

    -- 4. Actualizar total pagado en orden
    SELECT total_cents, total_pagado_cents INTO v_total_orden, v_total_pagado
    FROM public.ordenes WHERE id = p_orden_id FOR UPDATE;

    v_total_pagado := v_total_pagado + p_monto_cents;

    UPDATE public.ordenes
    SET total_pagado_cents = v_total_pagado,
        estado = CASE WHEN v_total_pagado >= v_total_orden THEN 'cerrada' ELSE 'abierta' END,
        cerrada_en = CASE WHEN v_total_pagado >= v_total_orden THEN now() ELSE NULL END,
        updated_at = now()
    WHERE id = p_orden_id;

    -- 5. Registrar en recibos críticos
    INSERT INTO private_sync.critical_receipts (
        idempotency_key, operation_type, entity_id, payload_hash, result_status
    ) VALUES (
        p_idempotency_key, 'pago', v_transaccion_id, md5(p_idempotency_key::text), 'success'
    );

    RETURN jsonb_build_object('status', 'success', 'transaccion_id', v_transaccion_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

### 4.2 RPC: `fn_registrar_movimiento_inventario`
Garantiza que dos meseros o terminales no descuenten simultáneamente un producto dejándolo en números negativos:
```sql
CREATE OR REPLACE FUNCTION public.fn_registrar_movimiento_inventario(
    p_producto_id UUID,
    p_tipo VARCHAR,
    p_cantidad INT,
    p_motivo TEXT,
    p_referencia_id UUID
)
RETURNS JSONB AS $$
DECLARE
    v_stock_actual INT;
    v_nuevo_stock INT;
    v_controla_stock BOOLEAN;
BEGIN
    SELECT stock_actual, controla_stock INTO v_stock_actual, v_controla_stock
    FROM public.productos WHERE id = p_producto_id FOR UPDATE;

    IF NOT v_controla_stock THEN
        RETURN jsonb_build_object('status', 'not_tracked');
    END IF;

    v_nuevo_stock := v_stock_actual + p_cantidad;

    IF v_nuevo_stock < 0 THEN
        RAISE EXCEPTION 'Stock insuficiente para el producto % (Actual: %, Solicitado: %)',
            p_producto_id, v_stock_actual, p_cantidad;
    END IF;

    UPDATE public.productos SET stock_actual = v_nuevo_stock, updated_at = now()
    WHERE id = p_producto_id;

    INSERT INTO public.movimientos_inventario (
        producto_id, tipo, cantidad, stock_previo, stock_posterior,
        motivo, referencia_id, creado_por
    ) VALUES (
        p_producto_id, p_tipo, p_cantidad, v_stock_actual, v_nuevo_stock,
        p_motivo, p_referencia_id, auth.uid()
    );

    RETURN jsonb_build_object('status', 'success', 'stock_actual', v_nuevo_stock);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

---

## 5. Publicación de Supabase Realtime

La migración remota `20261004034839_employees_payroll_realtime.sql` registra estas nueve tablas en `supabase_realtime`: `empleados`, `productos`, `mesas`, `promociones`, `ordenes`, `detalle_orden`, `transacciones`, `historial_pagos_empleados` y `movimientos_inventario`. No incluye `cortes_caja` ni `visitas_clientes`.

---

## 6. Mapeo de Tipos: Postgres ➔ Kotlin Remote DTO ➔ Room Entity

| Entidad / Campo | Tipo PostgreSQL | Tipo DTO Kotlin (`@Serializable`) | Tipo Room Entity | Tipo Dominio |
| :--- | :--- | :--- | :--- | :--- |
| `id` / IDs foráneos | `UUID` | `String` | `String` | `String` (o Value Object) |
| Importes remotos | `BIGINT` | `Long` | `Long` | `Long` (unidad/escala por confirmar por campo; las columnas no usan sufijo `_cents`) |
| Porcentajes / Índices | `INT` / `SMALLINT` | `Int` | `Int` | `Int` |
| Banderas lógicas | `BOOLEAN` | `Boolean` | `Boolean` | `Boolean` |
| Textos / Notas | `TEXT` / `VARCHAR` | `String?` | `String?` | `String?` |
| Marcas de tiempo | `TIMESTAMPTZ` | `String` (ISO 8601) | `Long` (Epoch ms) | `Instant` |
| Fechas sin hora | `DATE` | `String` (`YYYY-MM-DD`) | `String` | `LocalDate` |
| Desgloses / JSON | `JSONB` | `JsonObject` o DTO typed | `String` (Converters) | Value Object |

## 7. Historial de migraciones y sincronización Flutter/Android

El proyecto Flutter conserva el origen de las 14 migraciones históricas bajo `C:\Users\MACHENIKE\Projects\exquisssita_manager\supabase\migrations`. Se copiaron sin cambios a este repositorio; los SHA-256 de los 14 pares coinciden. La migración Android experimental de perfiles se descartó porque referenciaba `auth_user_id`, `salario_base_cents` y `user_roles.activo`, inexistentes en el esquema real. La migración Android experimental de lealtad se retiró porque duplicaba `20261003030433_phase8_qr_loyalty_daily_visit.sql` y permitía acreditar clientes sin vínculo a Auth.

La migración correctiva `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc.sql` es el único cambio nuevo: `auth_role()` ahora usa `search_path` vacío, solo reconoce a un empleado con ficha vinculada y `activo = true`, y la RPC histórica `registrar_visita_cliente(uuid)` ya no se puede ejecutar desde `anon` ni `authenticated`. La RPC de QR y el índice diario existentes se conservan.

Verificación completada el 2026-10-10:

1. `supabase migration list --linked`: las 15 versiones aparecen emparejadas local/remoto, desde `20261002204457` hasta `20261010031023`.
2. `supabase db reset --local`: reconstrucción desde cero exitosa aplicando las 15 migraciones en orden.
3. `supabase db push --linked --dry-run`: solo propuso la migración correctiva anterior; se aplicó con `supabase db push --linked` y volvió a aparecer emparejada en ambos historiales.
4. Comprobación local/remota: mismo hash normalizado de `auth_role`; `search_path` vacío; ejecución de `registrar_visita_cliente(uuid)` denegada a `anon` y `authenticated`; ejecución de `registrar_visita_qr(text)` permitida solo a `authenticated`; la RPC exige `clientes.user_id` y el índice diario sigue instalado.
5. `supabase db diff --linked --schema public,private_sync` solo reporta cuatro funciones ya existentes (`decrementar_stock`, `realizar_corte_caja`, `registrar_visita_cliente`, `trigger_set_updated_at`). Se compararon sus cuerpos SQL después de normalizar CRLF/LF: los cuatro hashes coinciden entre local y remoto. Es ruido de fin de línea del diff de pg-delta, no una discrepancia funcional o de migraciones.

Esta carpeta `docs/agents-cowork/` está ignorada por Git deliberadamente; mantener esa regla. Si se comparte el contexto con otro agente, enviar el resumen por el canal de trabajo, no cambiar el `.gitignore`.
