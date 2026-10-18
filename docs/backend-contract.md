# Exquisssita Manager — Contrato de Backend y Base de Datos Supabase

> **Documento de Especificación de Backend, RLS, RPCs y Sincronización**  
> Proyecto Android Studio Nativo: `ExquisssitaAppManager`  
> Backend Remoto: Supabase / PostgreSQL (Proyecto Compartido)  
> Regla Fundamental: Android es un cliente equivalente. No altera esquemas ni usa `service_role`.

> **Estado de verificación (2026-10-12):** Supabase CLI `2.120.0` confirmó que las 22 migraciones locales y remotas están emparejadas, hasta `20261012024548_fix_orders_staff_access_and_multiple_open_orders.sql`. La migración corrigió acceso de mesas/órdenes/pagos para `admin` y `gerente` activo, revocó acceso anónimo y DML directo, y retiró el índice que impedía varias órdenes abiertas por mesa. El diff global `db diff --linked` sigue pendiente porque Docker Desktop no tiene disponible el motor Linux. Las cuentas gerente se asignarán después, cuando estén creadas.
> **Actualización (2026-10-13):** Se aplicó `20261013212639_cash_register_period_cuts.sql`; las 27 migraciones locales/remotas están emparejadas. El detalle del corte está en la sección 9.
> **Actualización Release 03 (2026-10-17):** auditoría CLI de solo lectura confirma 27 migraciones remotas/locales emparejadas y PostgreSQL 17.6. Tres migraciones correctivas se redactaron localmente (`20261017205614`–`20261017205843`); están **pendientes de prueba aislada e integración remota**. No afirmar que el remoto ya tiene esos permisos/RPC. Informe: `docs/release-03-seguridad-backend.md`.
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
- En PostgreSQL, las cuotas semanales de empleados (`empleados.salario`) y los pagos (`historial_pagos_empleados.monto`) se almacenan como centavos enteros de MXN (`BIGINT`).
- La app captura y muestra pesos; por ejemplo, `$1,200.00 MXN` se transporta y guarda como `120000`.
- Kotlin usa `Long` para los centavos y conversión decimal exacta en los límites de UI/repositorio; no usa `Double` ni `Float` para dinero.
- La cuota es semanal; no se divide entre semanas del mes. El total mensual depende de cuántos días de pago semanales caen en ese mes.

---

## 2. Esquema Detallado de Tablas de Base de Datos

### 2.1 Tabla `user_roles`
Mapea la identidad de Supabase Auth (`auth.users`) con el rol operativo en la base de datos.
```sql
CREATE TABLE public.user_roles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('admin', 'gerente', 'empleado')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);
```
> [!NOTE]
> - La tabla `public.user_roles` **no contiene columna `activo`**.
> - Los roles de acceso a la app son `admin` y `gerente`. `empleado` se conserva solo para compatibilidad histórica y no autoriza el acceso a esta app.
> - El alta y asignación de cuentas gerente se hace desde un entorno administrativo de confianza; nunca se concede el rol desde la app.
> - `user_roles_self_select` permite leer el propio rol y al administrador leer todos los roles.

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
> - `salario BIGINT` es la cuota semanal en centavos de MXN. La UI usa pesos y convierte `$1,200.00` a `120000`.
> - Todo empleado de piso puede tener un perfil sin cuenta (`user_id = NULL`). Solo administrador y gerentes autorizados inician sesión.
> - Para que un gerente inicie sesión debe existir una cuenta Auth, una asignación `user_roles.role = 'gerente'` y un perfil activo de `empleados` vinculado por `user_id`.
> - `activo = false` deshabilita el acceso si el empleado tiene cuenta. Para el personal sin cuenta, es una baja lógica del directorio.
> - La columna `user_id` no tiene constraint `UNIQUE` en el esquema remoto; verificar que no haya vínculos duplicados antes de resolver perfil con `.single()`.


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
    fecha_programada DATE,
    registrado_por UUID
);
```
`monto` almacena centavos de MXN. La app no escribe en esta tabla directamente: llama `registrar_pago_nomina`, que usa la cuota semanal del empleado, deriva su fecha programada y registra quién hizo el pago. Cada empleado admite un pago por semana, identificada por un lunes en `periodo_inicio`. Los registros anteriores a la auditoría pueden tener `registrado_por = NULL`.

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
Resumen agregado por fecha. Cada fila resume la suma de los cortes individuales de `public.cortes_caja`; no representa un corte único.
```sql
CREATE TABLE public.venta_diaria (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
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

### 2.11.1 Tabla `cortes_caja` (migración desplegada 2026-10-13)
Cada fila representa el periodo no solapado desde el corte anterior (o desde la medianoche de negocio para el primero). Importes en centavos MXN.
```sql
CREATE TABLE public.cortes_caja (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE NOT NULL,
    numero_corte SMALLINT NOT NULL CHECK (numero_corte BETWEEN 1 AND 5),
    periodo_inicio TIMESTAMPTZ NOT NULL,
    periodo_fin TIMESTAMPTZ NOT NULL,
    total_efectivo BIGINT NOT NULL DEFAULT 0,
    total_tarjeta BIGINT NOT NULL DEFAULT 0,
    total_otro BIGINT NOT NULL DEFAULT 0,
    total_global BIGINT NOT NULL DEFAULT 0,
    num_ordenes INTEGER NOT NULL DEFAULT 0,
    hora_cierre TIMESTAMPTZ NOT NULL DEFAULT now(),
    cerrado_por UUID REFERENCES public.empleados(id) ON DELETE SET NULL,
    sync_version BIGINT NOT NULL DEFAULT 1,
    UNIQUE (fecha, numero_corte)
);
```
La migración `20261013212639_cash_register_period_cuts.sql` habilita RLS y lectura solo de `admin`/`gerente` activos. No permite DML desde el cliente. La RPC exige la fecha local actual, rechaza si existe cualquier orden `Abierta`, permite máximo cinco cortes y llama a `sync_execute_v2` para recibo idempotente por actor y clave. `venta_diaria` se actualiza atómicamente como agregado diario. La CLI `2.120.0` confirmó que las 27 migraciones locales/remotas están emparejadas; la consulta remota confirmó tabla, políticas, ACL y cero cortes de producción. Al auditar, había dos órdenes abiertas, por lo que la RPC financiera no se ejecutó en remoto.

### 2.12 Tabla `promociones`
Ofertas, 2x1 y beneficios temporales.
```sql
CREATE TABLE public.promociones (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    titulo TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    terminos_condiciones TEXT,
    imagen_url TEXT,
    fecha_publicacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_vencimiento DATE,
    activo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 1
);
```

`terminos_condiciones` es opcional y contiene texto libre para restricciones o reglas de uso (por ejemplo, “Solo válida en consumo en sucursal”). La lectura pública del catálogo se conserva; las altas y ediciones desde Manager requieren un usuario autenticado con rol activo `admin` o `gerente`. La baja operativa se realiza con `activo = false`; el borrado físico permanece restringido a `admin`.

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

La firma remota es `public.auth_role() RETURNS text`, con `search_path` vacío. `admin` depende de `public.user_roles`; `gerente` requiere además un perfil de empleado activo vinculado por `user_id`. Los roles históricos `empleado` no reciben autorización para esta app.

### 3.2 Políticas de Row Level Security (RLS) para Autenticación y Perfiles
Las políticas activas en la base de datos remota de Supabase garantizan que:
1. **`public.user_roles` (`user_roles_self_select`):**
   - Comando: `SELECT` para rol `{authenticated}`.
   - Condición (`USING`): `((user_id = (SELECT auth.uid())) OR (auth_role() = 'admin'))`.
   - Permite que cualquier usuario autenticado consulte su propio rol asignado, y que un usuario con rol `'admin'` consulte todos los roles.
2. **`public.empleados` (`empleados_admin_manager_manage`):** `admin` y `gerente` activo pueden leer/crear/actualizar perfiles de empleados. La app solo tiene privilegios de inserción/actualización en campos de directorio, salario, día de pago y estado; no puede cambiar `user_id`, asignar cuentas ni borrar filas. Las bajas se hacen con `activo = false`.
3. **`public.historial_pagos_empleados`:** `admin` y `gerente` activo pueden leer todo el historial. No hay política de lectura propia para empleados ni DML directo desde el cliente. El registro se realiza únicamente mediante `registrar_pago_nomina`.
4. **`public.productos` (`productos_admin_manager_manage`):** La migración `20261011211500_grant_products_catalog_write_access` otorga `INSERT` y `UPDATE` al rol `authenticated` sobre las columnas descriptivas del catálogo: `id`, `nombre`, `precio`, `categoria`, `controla_inventario`, `stock_minimo`, `activo`. Las columnas `stock_actual` y `sync_version` permanecen estrictamente de solo lectura (`SELECT`) para clientes de la aplicación y solo mutan mediante la RPC atómica `registrar_movimiento_inventario`. RLS restringe la escritura a usuarios con rol `admin` o `gerente`.

### 3.3 Reglas de Negocio para el Ciclo de Vida de Autenticación
- **Alta de personal de piso:** Administrador y gerentes pueden crear perfiles desde la app. Se crean sin usuario Auth (`user_id = NULL`); no tienen login ni acceso a datos.
- **Cuentas de app:** Solo administrador y dos gerentes autorizados tienen cuentas Auth. Los gerentes se crean en Supabase Auth y se asignan mediante `set_user_role` desde una sesión administrativa; después se vinculan a un perfil activo. La app no incluye registro.
- **Deshabilitación:** Para dar de baja a una persona, se marca `activo = false`; la app no elimina filas de empleados ni pagos.
- **Sin recuperación de contraseña:** La app **no incluye enlace ni flujo de "Recuperar contraseña"**. En caso de olvido, el administrador debe restablecerla directamente en Supabase.
- **Límite de sesión (10 horas):** La sesión en la aplicación tiene una vigencia máxima estricta de 10 horas (`36,000,000 ms`). Cumplido este tiempo, la app expira la sesión local, elimina tokens y requiere credenciales nuevamente.
- **Soporte Offline-First para la sesión:** Para operar sin conexión, la sesión validada y el perfil de usuario se persisten cifrados en almacenamiento seguro local (`EncryptedSessionManager` / `SecureAuthStore`). Si la app inicia sin conexión a internet y la última sesión guardada está dentro del margen de las 10 horas, se habilita el acceso en modo local (`isOffline = true`).

### 3.4 Roles reconocidos por la base remota

La base permite `admin`, `gerente` y `empleado` (compatibilidad histórica). Solo `admin` y `gerente` son roles autorizados para esta app. Cajero y mesero todavía no forman parte del contrato autorizado.

La matriz siguiente es una intención de producto, no una garantía de autorización desplegada. La autorización efectiva depende de las políticas RLS y RPC revisadas en Supabase.

| Módulo | Admin | Gerente | Cajero | Mesero | Empleado |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Mesas (Lectura/Update)** | Total | Total | Total | Total | Solo lectura |
| **Órdenes (Crear/Modificar)** | Total | Total | Total | Total | Denegado |
| **Cancelar Orden** | Permitido | Permitido | Denegado | Denegado | Denegado |
| **Cobro / Transacciones** | Total | Total | Total | Denegado | Denegado |
| **Catálogo Menú (Escritura)** | Total | Total | Denegado | Denegado | Denegado |
| **Inventario / Ajustes** | Total | Total | Solo lectura | Solo lectura | Denegado |
| **Nómina y Empleados** | Total | Total | Denegado | Denegado | Sin acceso |
| **Corte de Caja (Cierre)** | Total | Total | Su turno | Denegado | Denegado |
| **Promociones** | Total | Total | Solo lectura | Solo lectura | Solo lectura |

### 3.5 Auditoría remota dirigida de Mesas, Órdenes y Pagos (2026-10-12)

La auditoría previa detectó los hallazgos siguientes; se resolvieron en la migración `20261012024548_fix_orders_staff_access_and_multiple_open_orders.sql` y se verificaron en el remoto con `supabase db query --linked`:

- El índice parcial `ordenes_una_abierta_por_mesa` limitaba a una orden abierta por mesa. Fue eliminado; los UUID de orden y recibos idempotentes distinguen comandas diferentes y reintentos de la misma intención.
- `public.ordenes.mesa_id` admite `NULL`, compatible con pedidos para llevar sin mesa.
- Las políticas staff ahora son `TO authenticated` y permiten lectura a `admin`/`gerente` activo. Las políticas de lectura propia para clientes siguen limitadas a usuarios autenticados.
- `mesas_public_select` y las demás políticas de escritura directa fueron retiradas de las tablas afectadas. Se revocaron los privilegios de `PUBLIC`, `anon` y `authenticated`, y se concedió únicamente `SELECT` a `authenticated`; las mutaciones deben pasar por RPC seguras.
- Las guardas de `registrar_pago`, `sync_execute`, `sync_execute_v2`, `sync_pull` y `private_sync.inventory_command` aceptan `admin` y `gerente` activo. Se mantuvieron validaciones específicas de administrador para nómina, empleados, promociones, ajustes y borrados restringidos.
- La RPC remota `registrar_pago` ya permite contribuciones iguales/personalizadas y pagos parciales de una misma unidad en transacciones distintas (ejemplo del cuerpo remoto: 40 + 60 centavos para una unidad de 100). Es idempotente mediante `private_sync.critical_receipts`, pero actualmente no registra movimientos de inventario. `pago_detalles.cantidad` puede repetirse entre transacciones y no representa unidades de stock consumidas.
- Al volver a ejecutar los asesores persisten avisos de RPC `SECURITY DEFINER` intencionales accesibles a `authenticated` y de políticas SELECT múltiples en órdenes/detalles, donde coexisten la lectura staff y el ownership del cliente. Las funciones inspeccionadas usan `search_path` vacío y checks de rol; las políticas usan `(SELECT auth.uid())` para evitar evaluación por fila. Revisar los avisos por función/ACL, no quitando indiscriminadamente el acceso necesario.

Verificación posterior: el CLI confirmó el índice ausente, RLS habilitado en las cinco tablas, ningún `SELECT` anónimo, `SELECT` autenticado disponible, `INSERT` directo denegado y las cuatro guardas RPC enumeradas con `gerente`. El historial local/remoto está emparejado en 22 migraciones. Esto no reemplaza el diff completo del esquema: `supabase db diff --linked --schema public,private_sync` sigue pendiente porque el entorno no encontró `dockerDesktopLinuxEngine`.

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
| `registrar_movimiento_inventario` | Firma heredada de seis argumentos (`p_producto_id`, `p_cantidad`, `p_tipo`, `p_idempotency_key`, `p_referencia_id`, `p_notas`) y sobrecarga nueva de siete que añade `p_expected_version bigint` obligatorio | Movimiento idempotente; los ajustes manuales deben enviar la versión observada. La heredada se conserva para compatibilidad. Solo `authenticated`; admin/gerente pasan la autorización interna. `anon` no tiene permiso. |
| `realizar_corte_caja_seguro` | `p_fecha date` | Crea un corte del periodo actual, solo mediante `sync_execute_v2`; requiere cero órdenes abiertas y admite cinco por fecha local. |
| `sync_execute_v2` | `p_entity text, p_entity_id text, p_operation text, p_payload jsonb, p_key uuid, p_expected_version bigint` | Escritura sincronizada idempotente. |
| `sync_pull` | `p_after bigint, p_watermark bigint, p_limit integer` | Lectura paginada del registro de cambios. |
| `set_user_role` | `target_email text, target_role text` | Asignación administrativa de rol. |
| `registrar_pago_nomina` | `p_empleado_id uuid, p_periodo_inicio date, p_fecha_pago date, p_notas text` | Registra la cuota semanal en centavos, deriva el día programado del empleado y evita duplicar el pago del periodo. Solo `admin` y `gerente` activos. |

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

La migración remota `20261004034839_employees_payroll_realtime.sql` registra nueve tablas en `supabase_realtime`. La tabla de cortes se consulta bajo demanda y no depende de Realtime.

---

## 6. Mapeo de Tipos: Postgres ➔ Kotlin Remote DTO ➔ Room Entity

| Entidad / Campo | Tipo PostgreSQL | Tipo DTO Kotlin (`@Serializable`) | Tipo Room Entity | Tipo Dominio |
| :--- | :--- | :--- | :--- | :--- |
| `id` / IDs foráneos | `UUID` | `String` | `String` | `String` (o Value Object) |
| `empleados.salario`, `historial_pagos_empleados.monto` | `BIGINT` | `Long` | `Long` | Centavos MXN; la UI captura/presenta pesos |
| Otros importes remotos | `BIGINT` | `Long` | `Long` | Confirmar escala por campo antes de implementar |
| Porcentajes / Índices | `INT` / `SMALLINT` | `Int` | `Int` | `Int` |
| Banderas lógicas | `BOOLEAN` | `Boolean` | `Boolean` | `Boolean` |
| Textos / Notas | `TEXT` / `VARCHAR` | `String?` | `String?` | `String?` |
| Marcas de tiempo | `TIMESTAMPTZ` | `String` (ISO 8601) | `Long` (Epoch ms) | `Instant` |
| Fechas sin hora | `DATE` | `String` (`YYYY-MM-DD`) | `String` | `LocalDate` |
| Desgloses / JSON | `JSONB` | `JsonObject` o DTO typed | `String` (Converters) | Value Object |

## 7. Historial de migraciones y sincronización Flutter/Android

El proyecto Flutter conserva el origen de las 14 migraciones históricas bajo `C:\Users\MACHENIKE\Projects\exquisssita_manager\supabase\migrations`. Se copiaron sin cambios a este repositorio; los SHA-256 de los 14 pares coinciden. La migración Android experimental de perfiles se descartó porque referenciaba `auth_user_id`, `salario_base_cents` y `user_roles.activo`, inexistentes en el esquema real. La migración Android experimental de lealtad se retiró porque duplicaba `20261003030433_phase8_qr_loyalty_daily_visit.sql` y permitía acreditar clientes sin vínculo a Auth.

La migración `20261010031023_enforce_employee_status_and_lock_legacy_visit_rpc.sql` endureció `auth_role()` y revocó la RPC histórica de visitas. `20261010195445_payroll_manager_employee_access.sql` agrega `gerente`, permisos limitados por rol/columna para empleados, historial de nómina de solo lectura y la RPC de pago semanal. `20261010200756_reject_missing_roles_for_sensitive_actions.sql` hace que `auth_role()` devuelva cadena vacía cuando no hay asignación y endurece la comprobación de administrador en `set_user_role`. El rol `empleado` se conserva por compatibilidad pero no autoriza esta app.

Verificación completada el 2026-10-10 (actualización tras alineación de inventario):

1. `supabase migration list --linked`: las 20 versiones aparecen emparejadas local/remoto, desde `20261002204457` hasta `20261010223749`.
2. `supabase db reset --local`: reconstrucción desde cero exitosa aplicando las 17 migraciones en orden.
3. `supabase db push --linked --dry-run`: solo propuso `20261010195445_payroll_manager_employee_access.sql`; se aplicó con `supabase db push --linked` y volvió a aparecer emparejada en ambos historiales.
4. Comprobación local/remota: mismo hash normalizado de `auth_role`; `search_path` vacío; ejecución de `registrar_visita_cliente(uuid)` denegada a `anon` y `authenticated`; ejecución de `registrar_visita_qr(text)` permitida solo a `authenticated`; la RPC exige `clientes.user_id` y el índice diario sigue instalado.
5. `supabase db diff --linked --schema public,private_sync` solo reporta cuatro funciones ya existentes (`decrementar_stock`, `realizar_corte_caja`, `registrar_visita_cliente`, `trigger_set_updated_at`). Se compararon sus cuerpos SQL después de normalizar CRLF/LF: los cuatro hashes coinciden entre local y remoto. Es ruido de fin de línea del diff de pg-delta, no una discrepancia funcional o de migraciones.
6. La verificación remota confirma que solo admin/gerente leen nómina; el cliente no tiene DML directo sobre historial, no puede borrar empleados ni editar `user_id`, y la RPC de nómina no es ejecutable por `anon`.
7. La reconstrucción local y una prueba transaccional revertida confirmaron que un gerente activo ve empleados y registra `120000` centavos semanales; con `dia_pago = 3` (miércoles) y periodo iniciado lunes, la RPC deriva miércoles como fecha programada. Los salarios de prueba `100` se normalizaron a `10000` centavos; los `120000` se conservaron.
8. `20261010222310_align_inventory_manager_permissions.sql` alinea lectura de productos/movimientos y autorización de `private_sync.inventory_command` con `admin`/`gerente`; conserva ajuste solo para `admin`, revoca DML directo de movimientos y mantiene denegado el acceso `anon`.
9. `20261010223202_expose_inventory_adjustment_version.sql` agrega la RPC de siete argumentos para pasar `p_expected_version`. `20261010223749_require_expected_version_for_adjustment_rpc.sql` hace obligatorio ese último argumento, evitando ambigüedad con la firma heredada. En remoto, CLI verificó ejecución de ambas firmas por `authenticated`, denegación de la firma nueva a `anon` y la lista local/remota sincronizada.
10. El chequeo remoto posterior confirmó `authenticated` con SELECT a productos/movimientos, sin INSERT directo a movimientos; `anon` sin SELECT de productos ni ejecución de la RPC de inventario. El precio remoto `productos.precio` es `BIGINT` y la app lo modela como centavos, conservando precisión entera.
11. La auditoría remota de `productos.activo` confirmó que desactivar un producto no pone `stock_actual` en cero: solo existen triggers de `updated_at`, captura de cambios y `sync_version`. La RPC actual de inventario no filtra por `activo`, por lo que admite movimientos sobre productos inactivos si cumplen las demás validaciones. No se debe resetear stock sin movimiento de kardex; la regla para inactivos requiere decisión y posible migración aparte.
12. La capa Android de inventario subió Room a versión 3 con catálogo, caché append-only de movimientos y outbox local de comandos vinculada al usuario que la creó. Las ventas siguen reservadas al flujo de Órdenes; los movimientos pendientes no modifican el stock confirmado local.

Esta carpeta `docs/agents-cowork/` está ignorada por Git deliberadamente; mantener esa regla. Si se comparte el contexto con otro agente, enviar el resumen por el canal de trabajo, no cambiar el `.gitignore`.

## 8. Actualización — paquete de Mesas, Órdenes y Pagos (2026-10-12)

La migración `20261012025547_add_atomic_payment_stock_allocations.sql` está aplicada en remoto. Agrega `public.pago_detalle_stock`, separado de `pago_detalles`, con RLS y lectura para `admin`/`gerente`; extiende `registrar_pago` para confirmar cada reducción de stock en la misma transacción que el pago; y agrega `cancelar_detalle_orden(uuid,uuid)`, idempotente y limitada a partidas abiertas sin pagos. `cantidad_stock` es entero, no puede exceder las unidades asignadas y el acumulado por detalle no supera la cantidad pedida. Pago y movimiento se revierten juntos ante error.

La migración `20261012031223_derive_table_occupancy.sql` también está aplicada. Normaliza el marcador histórico `Ocupada` a `Libre` e impide volver a persistirlo; Android deriva ocupación desde órdenes abiertas. Consulta remota confirmó constraint instalada, cero mesas `Ocupada`, RLS activa en `pago_detalle_stock`, y permisos de ejecución authenticated para ambas RPC (cada función valida internamente admin/gerente). `supabase migration list --linked` muestra 24 migraciones locales/remotas emparejadas hasta `20261012031223`.

Android agrega Room v4 con caché de mesas/órdenes/detalles/pagos/outbox. Los repositorios observan Room, refrescan con PostgREST, guardan intención y cambio optimista en una transacción Room, y reintentan el mismo JSON y clave por actor. Los pagos offline son intenciones pendientes; solo aparecen confirmados tras respuesta remota y reconciliación. No hay descuento local de stock no confirmado. Orden pagada o partida con pago no se puede cancelar desde sincronización genérica; reembolsos esperan definición de negocio.

Validación pendiente: compilación y ejecución Android, pruebas locales de Supabase con Docker Linux, pruebas de concurrencia/RLS con sesiones reales de admin/gerente y `db diff` global. Gradle no pudo compilar Kotlin porque el entorno no estableció su conexión loopback. No se agregó WorkManager; el reintento se activa mediante `refresh*`/`syncPending*` cuando la app vuelve a consultar o sincronizar.

## 9. Actualización — corte de caja por periodos (2026-10-13)

Se implementó y desplegó `20261013212639_cash_register_period_cuts.sql`. `cortes_caja` registra periodos que no se solapan, con límite de cinco por día local. El corte rechaza cualquier orden con estado `Abierta`, exige `admin` o `gerente` activo, es solo para la fecha de negocio actual y actualiza `venta_diaria` como agregado del día. El cliente solo tiene SELECT RLS de cortes/resúmenes; los comandos usan la envoltura idempotente `sync_execute_v2`, y no hay ejecución directa de la RPC de corte para anon/authenticated.

La revisión remota encontró dos órdenes abiertas, por lo que no se disparó una operación financiera real. No había cortes ni filas `venta_diaria` previos al despliegue. La tabla y funciones quedaron instaladas; las migraciones local/remoto están emparejadas hasta `20261013212639`.

En Android, el paquete de datos ahora define `DailyClosure` y `CashRegisterDaySummary`, repositorio de creación/lectura, DTO y mapeo, outbox idempotente, caché Room v6 y binding Hilt. La compilación Kotlin quedó pendiente porque Gradle falla al crear la conexión loopback del daemon, incluso con el JBR de Android Studio. No se ejecutaron pruebas unitarias ni una RPC de corte sobre producción.

## 10. Contrato Release 03 — seguridad, perfiles cliente y operaciones atómicas (2026-10-17)

### Estado

La auditoría remota confirmó que las 27 migraciones anteriores están sincronizadas. Encontró privilegios excesivos en `clientes` (incluyendo `TRUNCATE` y `MAINTAIN` para `anon`/`authenticated`), feed histórico con `qr_token_hash`, trigger que asigna `empleado` en cada signup Auth, y `registrar_visita_qr` sin permiso para `gerente`. No había clientes ni visitas, así que no hay saldos de lealtad que reconciliar; había tres empleados con `user_id` y ningún vínculo duplicado. Release 03 añade migraciones locales; hasta que se prueben y desplieguen, las reglas de esta sección son el contrato deseado y no describen todavía el remoto.

### Perfiles y Auth de clientes

- El signup público sigue habilitado para la aplicación de clientes; el acceso anónimo sigue deshabilitado. El trigger `on_auth_user_created_set_role` deja de asignar rol operativo. Personal administrador/gerente se aprovisiona por el flujo administrativo confiable.
- Un perfil cliente se crea con `ensure_customer_profile(p_nombre, p_telefono)` después de confirmar el correo. La función es idempotente por `auth.uid()`; nunca vincula perfiles por teléfono. `clientes.user_id` tendrá índice único parcial tras la comprobación de duplicados.
- `update_customer_profile` permite al titular cambiar solo nombre y teléfono. Balance, visitas, identidad, QR y versión no son editables mediante tabla o `sync_execute`.
- `rotate_customer_qr_token()` emite un token aleatorio opaco de 256 bits; entrega el valor una sola vez y guarda SHA-256. Una nueva emisión revoca el QR anterior. El hash no se concede por SELECT y se filtra tanto en capturas nuevas como en páginas históricas de `sync_pull`.
- El cliente solo lee su perfil, visitas, ledger y canjes. Los operadores `admin`/`gerente` leen el directorio operativo y registran visitas/canjes en línea. QR no acredita visitas ni canjes sin conexión.

### Nómina y empleados

- Un perfil `empleados.user_id IS NULL` es personal de piso sin identidad de operador. El vínculo `user_id` no es editable por clientes ni por la sincronización genérica. Un vínculo de operador, incluso inactivo, queda protegido ante el gerente.
- `update_floor_employee_salary(p_empleado_id, p_salario, p_expected_version)` bloquea el registro y valida versión en una transacción. `admin` puede administrar; `gerente` solo modifica salario en filas de piso sin vínculo Auth. El gerente no cambia salarios de perfiles vinculados ni usa `sync_execute` para editar el directorio.
- `registrar_pago_nomina` conserva la cuota semanal en centavos desde el perfil bloqueado, requiere empleado activo y deja idempotencia semanal en el índice existente. Ninguna operación cambia el historial previo.

### Órdenes, precios y agregados

- Al insertar una nueva partida con `sync_execute_v2`, la base lee el precio de `productos` bajo `FOR SHARE`, exige producto activo y compara el importe enviado con el precio canónico en centavos. Un precio offline obsoleto devuelve `price_changed` con el valor actual en el detalle del error; la app no cobra un importe revalorizado silenciosamente.
- El precio de una partida existente es histórico: `precio_unitario` y `producto_id` no se pueden reescribir por UPDATE genérico. Cambiar producto implica cancelar una partida todavía no pagada y crear otra con precio vigente.
- `order_financial_snapshot(p_order_id)` devuelve orden, partidas, transacciones, total, pagado y saldo pendiente en una sola lectura de base de datos. `daily_sales_summary(p_business_date)` agrupa pagos por fecha de negocio CDMX y método. Ambos requieren rol de operador activo.
- Se agregan índices de estado/fecha de órdenes, partidas por orden, transacciones por fecha/orden y búsqueda de clientes por nombre/teléfono.

### Lealtad y canjes

- `puntos_movimientos` es el ledger append-only. `clientes.puntos_lealtad` es caché derivada: una entrada positiva `apertura` reconcilia saldos que existían antes del ledger; nuevas visitas acreditan 10 puntos mediante `registrar_visita_qr`, y los canjes descuentan el costo almacenado en `recompensas`.
- La visita queda limitada a una por cuenta cliente y fecha CDMX. Se acepta `admin` o `gerente` activo y se exige cuenta cliente verificada, vinculada y token QR vigente. Idempotencia/concurrencia se resuelven en PostgreSQL.
- Solo `admin` gestiona recompensas con `manage_loyalty_reward` y `expectedVersion`, y ejecuta ajustes de saldo trazados con `adjust_loyalty_points` e idempotency key. `redeem_loyalty_reward` valida QR, recompensa activa, costo de servidor y saldo bajo bloqueo. El catálogo representa recompensas en puntos, no descuentos monetarios ni devoluciones de órdenes.

### Sesiones y configuración

`supabase/config.toml` local conserva signup email de clientes, deshabilita sesiones anónimas, requiere confirmación y contraseñas de al menos 8 caracteres, y simula timebox Auth de 10 horas. Esto **no aplica esos valores al proyecto remoto**. Antes del piloto, configurar en Auth remoto confirmación, contraseñas, redirect URLs exactos de la app cliente y límites de envío/autenticación; la app Manager mantiene su propio límite local de 10 horas.

### Migraciones nuevas (locales, no desplegadas)

1. `20261017205614_release03_security_auth_loyalty_payroll_orders.sql` — ACL, perfiles, feed sanitizado, signup/roles.
2. `20261017205743_release03_payroll_and_order_price_guards.sql` — salarios/nómina, precio canónico, snapshots e índices.
3. `20261017205843_release03_loyalty_ledger_customer_profiles_rewards.sql` — perfil/QR cliente, ledger, visitas y canjes.

La prueba `supabase/tests/release03_security_acl.test.sql` comprueba ACL esenciales. No se debe usar `db push --linked` hasta que las migraciones pasen una reconstrucción local con Docker, pruebas SQL y revisión del informe ACL antes/después.
