# Exquisssita Manager — Contrato de Backend y Base de Datos Supabase

> **Documento de Especificación de Backend, RLS, RPCs y Sincronización**  
> Proyecto Android Studio Nativo: `ExquisssitaAppManager`  
> Backend Remoto: Supabase / PostgreSQL (Proyecto Compartido)  
> Regla Fundamental: Android es un cliente equivalente. No altera esquemas ni usa `service_role`.

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
Mapea la identidad segura de Supabase Auth con el rol asignado en el restaurante.
```sql
CREATE TABLE public.user_roles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL CHECK (role IN ('admin', 'gerente', 'cajero', 'mesero', 'empleado')),
    activo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.2 Tabla `empleados`
Directorio del personal para control operativo y nómina semanal.
```sql
CREATE TABLE public.empleados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    telefono VARCHAR(20),
    salario_base_cents BIGINT NOT NULL CHECK (salario_base_cents >= 0),
    dia_pago SMALLINT NOT NULL CHECK (dia_pago BETWEEN 1 AND 7), -- 1: Lunes, 7: Domingo
    activo BOOLEAN NOT NULL DEFAULT true,
    notas TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.3 Tabla `historial_pagos_empleados`
Registro append-only de pagos de nómina por periodo semanal.
```sql
CREATE TABLE public.historial_pagos_empleados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empleado_id UUID NOT NULL REFERENCES public.empleados(id) ON DELETE RESTRICT,
    monto_cents BIGINT NOT NULL CHECK (monto_cents > 0),
    fecha_pago TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    periodo_inicio DATE NOT NULL,
    periodo_fin DATE NOT NULL,
    notas TEXT,
    registrado_por UUID REFERENCES auth.users(id),
    idempotency_key UUID UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.4 Tabla `productos`
Catálogo del menú con clasificación de control de existencias.
```sql
CREATE TABLE public.productos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    categoria VARCHAR(50) NOT NULL,
    precio_cents BIGINT NOT NULL CHECK (precio_cents >= 0),
    activo BOOLEAN NOT NULL DEFAULT true,
    controla_stock BOOLEAN NOT NULL DEFAULT false,
    stock_actual INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 0,
    imagen_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.5 Tabla `movimientos_inventario`
Kardex inmutable para trazabilidad de existencias físicas.
```sql
CREATE TABLE public.movimientos_inventario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id UUID NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('entrada', 'venta', 'ajuste', 'merma', 'cancelacion')),
    cantidad INT NOT NULL, -- Positivo en entrada/cancelación, negativo en venta/merma
    stock_previo INT NOT NULL,
    stock_posterior INT NOT NULL,
    motivo TEXT,
    referencia_id UUID, -- ID de orden o transacción si aplica
    creado_por UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.6 Tabla `mesas`
Salón y distribución física.
```sql
CREATE TABLE public.mesas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero INT UNIQUE NOT NULL,
    capacidad INT NOT NULL DEFAULT 4,
    estado VARCHAR(20) NOT NULL DEFAULT 'libre' CHECK (estado IN ('libre', 'ocupada', 'reservada')),
    ubicacion VARCHAR(50) DEFAULT 'Salon Principal',
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.7 Tabla `ordenes`
Comandas abiertas, completadas o canceladas.
```sql
CREATE TABLE public.ordenes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mesa_id UUID REFERENCES public.mesas(id) ON DELETE SET NULL,
    tipo VARCHAR(20) NOT NULL DEFAULT 'mesa' CHECK (tipo IN ('mesa', 'para_llevar')),
    estado VARCHAR(20) NOT NULL DEFAULT 'abierta' CHECK (estado IN ('abierta', 'cerrada', 'cancelada')),
    cliente_id UUID REFERENCES public.clientes(id) ON DELETE SET NULL,
    total_cents BIGINT NOT NULL DEFAULT 0 CHECK (total_cents >= 0),
    total_pagado_cents BIGINT NOT NULL DEFAULT 0 CHECK (total_pagado_cents >= 0),
    notas TEXT,
    abierta_por UUID REFERENCES auth.users(id),
    cerrada_en TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.8 Tabla `detalle_orden`
Renglones de productos agregados a una comanda.
```sql
CREATE TABLE public.detalle_orden (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id UUID NOT NULL REFERENCES public.ordenes(id) ON DELETE CASCADE,
    producto_id UUID NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    precio_unitario_cents BIGINT NOT NULL CHECK (precio_unitario_cents >= 0),
    subtotal_cents BIGINT NOT NULL CHECK (subtotal_cents >= 0),
    estado_pago VARCHAR(20) NOT NULL DEFAULT 'pendiente' CHECK (estado_pago IN ('pendiente', 'pagado')),
    notas TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.9 Tabla `transacciones`
Comprobantes de pago de una comanda (admite pagos parciales o divididos).
```sql
CREATE TABLE public.transacciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id UUID NOT NULL REFERENCES public.ordenes(id) ON DELETE RESTRICT,
    monto_cents BIGINT NOT NULL CHECK (monto_cents > 0),
    metodo_pago VARCHAR(30) NOT NULL CHECK (metodo_pago IN ('efectivo', 'tarjeta', 'transferencia')),
    monto_recibido_cents BIGINT NOT NULL CHECK (monto_recibido_cents >= monto_cents),
    cambio_cents BIGINT NOT NULL DEFAULT 0 CHECK (cambio_cents >= 0),
    creado_por UUID REFERENCES auth.users(id),
    idempotency_key UUID UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.10 Tabla `pago_detalles`
Asocia una transacción con los renglones específicos cubiertos en la comanda.
```sql
CREATE TABLE public.pago_detalles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaccion_id UUID NOT NULL REFERENCES public.transacciones(id) ON DELETE CASCADE,
    detalle_orden_id UUID NOT NULL REFERENCES public.detalle_orden(id) ON DELETE RESTRICT,
    monto_cents BIGINT NOT NULL CHECK (monto_cents > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.11 Tabla `cortes_caja`
Arqueo de turno y cierre financiero.
```sql
CREATE TABLE public.cortes_caja (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha_apertura TIMESTAMPTZ NOT NULL,
    fecha_cierre TIMESTAMPTZ,
    total_esperado_cents BIGINT NOT NULL DEFAULT 0,
    total_real_cents BIGINT NOT NULL DEFAULT 0,
    diferencia_cents BIGINT NOT NULL DEFAULT 0,
    desglose_metodos JSONB NOT NULL DEFAULT '{}'::jsonb,
    cerrado_por UUID REFERENCES auth.users(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'abierto' CHECK (estado IN ('abierto', 'cerrado')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.12 Tabla `promociones`
Ofertas, 2x1 y beneficios temporales.
```sql
CREATE TABLE public.promociones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    tipo_descuento VARCHAR(30) NOT NULL DEFAULT 'porcentaje',
    valor INT NOT NULL DEFAULT 0,
    imagen_url TEXT,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT true,
    publicada BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```

### 2.13 Tablas `clientes` y `visitas_clientes`
Módulo de lealtad y escaneo QR.
```sql
CREATE TABLE public.clientes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    qr_code VARCHAR(100) UNIQUE NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    puntos_acumulados INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE TABLE public.visitas_clientes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id UUID NOT NULL REFERENCES public.clientes(id) ON DELETE RESTRICT,
    fecha_visita DATE NOT NULL DEFAULT CURRENT_DATE,
    puntos_otorgados INT NOT NULL DEFAULT 10,
    registrado_por UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_cliente_visita_diaria UNIQUE (cliente_id, fecha_visita)
);
```

### 2.14 Esquema Privado `private_sync.critical_receipts`
Control de idempotencia para la cola de sincronización.
```sql
CREATE SCHEMA IF NOT EXISTS private_sync;

CREATE TABLE private_sync.critical_receipts (
    idempotency_key UUID PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    result_status VARCHAR(20) NOT NULL DEFAULT 'success'
);
```

---

## 3. Modelo de Seguridad y Row Level Security (RLS)

### 3.1 Función de Resolución Segura de Rol `auth_role()`
La aplicación **nunca confía en `raw_user_meta_data` del JWT** porque dicho objeto puede ser manipulado en el cliente si existieran fugas o configuraciones permisivas. El rol se resuelve mediante una función `SECURITY DEFINER` que consulta directamente `user_roles`:

```sql
CREATE OR REPLACE FUNCTION public.auth_role()
RETURNS VARCHAR AS $$
BEGIN
    RETURN (
        SELECT role FROM public.user_roles
        WHERE user_id = auth.uid() AND activo = true
        LIMIT 1
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

### 3.2 Matriz de Permisos por Rol

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

### 4.1 RPC: `fn_registrar_pago_atomico`
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

Se encuentran agregadas a la publicación `supabase_realtime` las 9 tablas operativas de sincronización instantánea:
1. `mesas` (Reflejo en tiempo real de mesa ocupada/libre en todas las tablets).
2. `ordenes` (Nuevas comandas abiertas o cerradas).
3. `detalle_orden` (Nuevos tacos o bebidas agregadas a la comanda por meseros).
4. `transacciones` (Notificación de cobro inmediato).
5. `productos` (Cambio de precios o disponibilidad).
6. `movimientos_inventario` (Actualizaciones de stock).
7. `promociones` (Vigencia y estados de promociones activas).
8. `cortes_caja` (Aperturas y cierres de turno).
9. `visitas_clientes` (Registro de lealtad QR).

---

## 6. Mapeo de Tipos: Postgres ➔ Kotlin Remote DTO ➔ Room Entity

| Entidad / Campo | Tipo PostgreSQL | Tipo DTO Kotlin (`@Serializable`) | Tipo Room Entity | Tipo Dominio |
| :--- | :--- | :--- | :--- | :--- |
| `id` / IDs foráneos | `UUID` | `String` | `String` | `String` (o Value Object) |
| Montos de dinero | `BIGINT` | `Long` | `Long` | `Long` (Céntimos) |
| Porcentajes / Índices | `INT` / `SMALLINT` | `Int` | `Int` | `Int` |
| Banderas lógicas | `BOOLEAN` | `Boolean` | `Boolean` | `Boolean` |
| Textos / Notas | `TEXT` / `VARCHAR` | `String?` | `String?` | `String?` |
| Marcas de tiempo | `TIMESTAMPTZ` | `String` (ISO 8601) | `Long` (Epoch ms) | `Instant` |
| Fechas sin hora | `DATE` | `String` (`YYYY-MM-DD`) | `String` | `LocalDate` |
| Desgloses / JSON | `JSONB` | `JsonObject` o DTO typed | `String` (Converters) | Value Object |
