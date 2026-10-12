# Auditoría y plan — Mesas, órdenes y cuentas divididas

**Fecha:** 2026-10-12  
**Estado:** Auditoría dirigida completada; los tres hallazgos de acceso y concurrencia se corrigieron en la migración `20261012024548_fix_orders_staff_access_and_multiple_open_orders.sql`, aplicada y verificada en Supabase remoto. El diff global de esquema continúa pendiente por falta del motor Docker Linux. Sin implementación Android todavía.

## 1. Alcance y decisiones de negocio recibidas

- Una mesa puede tener **más de una orden abierta** al mismo tiempo.
- Una orden puede existir sin mesa (`mesa_id = NULL`), especialmente para pedidos para llevar.
- Una sola orden puede pagarse entre varias personas, con división por productos/cantidades y también en partes iguales o importes personalizados.
- El inventario se descontará en cada pago parcial, proporcional a los productos/cantidades asignados a ese pago. La contribución monetaria y las unidades liquidadas se registrarán por separado.
- Abrir una orden no reserva stock. Como el descuento ocurre al pagar, dos órdenes abiertas pueden competir por las mismas existencias; el pago debe validar stock bajo bloqueo y rechazarse todo-o-nada si ya no alcanza.
- Las órdenes y los comandos deben ser idempotentes para impedir duplicados tras reintentos, pérdida de respuesta o sincronización offline.
- Room debe ser la única fuente local observable y las operaciones deben sincronizarse de manera consistente.

## 2. Resultado de la auditoría local y remota

Se revisaron `supabase/migrations/`, `docs/backend-contract.md`, las interfaces actuales de `TableRepository` y `OrderRepository`, y los archivos de presentación de Mesas y Órdenes.

### Esquema y reglas remotas actuales

1. `public.mesas` tiene `id`, `numero_mesa` único y `estado` limitado a `Libre`, `Ocupada` o `Reservada`.
2. `public.ordenes.mesa_id` admite `NULL`, así que el esquema soporta pedidos sin mesa. `tipo_servicio` acepta `Mesa`, `Para llevar` y `Domicilio`.
3. La migración inicial creaba el índice parcial `ordenes_una_abierta_por_mesa`, que impedía órdenes abiertas múltiples. La nueva migración lo eliminó sin alterar el historial aplicado; varias órdenes distintas pueden compartir mesa.
4. `public.detalle_orden` mantiene cantidad y precio unitario, y `public.pago_detalles` asigna pagos a renglones/cantidades.
5. `public.registrar_pago` es `SECURITY DEFINER`, tiene `search_path` vacío y registra recibos en `private_sync.critical_receipts` con actor y payload. Una repetición igual devuelve el recibo; la misma clave con payload distinto se rechaza. Valida orden abierta, método, total pagado, pagos por renglón y sobrepagos.
6. La RPC actual **ya admite división monetaria por artículo, pagos iguales/personalizados y aportaciones parciales a una misma unidad**: el fuente remoto documenta dos pagos de 40 y 60 centavos para una unidad de 100. El importe asignado puede ser menor que `cantidad * precio_unitario`; la cantidad de `pago_detalles` describe la asignación de ese pago y puede repetirse en transacciones diferentes.
7. `registrar_pago` **no actualiza inventario ni crea movimientos de venta**. La cantidad actual de `pago_detalles` no debe reutilizarse como cantidad descontada de stock.

### Roles y RLS remotos

La consulta remota inicial encontró que las políticas y RPC usaban `admin`/`empleado`, aunque `auth_role()` devolvía `admin` o `gerente` activo. La migración nueva actualizó a `admin`/`gerente` las políticas de lectura y las guardas de `registrar_pago`, `sync_execute`, `sync_execute_v2`, `sync_pull` e `inventory_command`. Se preservaron restricciones internas que son solo de administrador.

La RLS de las cinco tablas auditadas permanece habilitada. Las políticas staff ahora son `TO authenticated` y exigen `auth_role()` igual a `admin` o `gerente`; las lecturas propias de órdenes/detalles del cliente siguen autenticadas y con ownership. No se concedió DML directo: la migración revocó privilegios de `PUBLIC`, `anon` y `authenticated`, y otorgó `SELECT` a `authenticated`. Las mutaciones irán mediante RPC seguras.

Se retiró `mesas_public_select`; la verificación remota confirma que `anon` ya no tiene SELECT y que `authenticated` conserva SELECT sin INSERT. Se preservaron las políticas de lectura de órdenes propias del cliente externo, ahora explícitas para `authenticated`.

Después de la migración, los asesores siguen reportando warnings de `SECURITY DEFINER` ejecutables por `authenticated` en RPC previstas y políticas SELECT múltiples en `ordenes`/`detalle_orden` porque coexisten lectura staff y lectura propia del cliente. Las funciones inspeccionadas usan `search_path = ''`; las RPC retienen validación de identidad/rol y ACL deliberadas. Las políticas usan `(SELECT auth.uid())`. No se revocaron endpoints necesarios ni se eliminaron las políticas de ownership del cliente. Estos avisos deben revisarse por función y con pruebas de autorización del backend completo.

### Estado del código Android

- `features/tables/*`, `features/orders/*`, `domain/repository/TableRepository.kt` y `OrderRepository.kt` siguen como stubs de paquete.
- No hay todavía entidades/DAO Room ni repositorios de Mesas/Órdenes.
- Room ya existe y el paquete de inventario la elevó a versión 3. El nuevo incremento debe extender la base y agregar migraciones no destructivas desde las versiones soportadas, no redefinir `AppDatabase` ni habilitar una migración destructiva.
- El código de inventario establece una regla compatible para operaciones críticas: intención local distinta de hecho remoto confirmado; clave estable en reintentos; stock remoto nunca mutado de manera optimista.

## 3. Resultado de la verificación con Supabase CLI

- Se obtuvo Supabase CLI `2.120.0` por `npx` y se consultó la ayuda real de `migration list`, `db query` y `db advisors`.
- `supabase migration list --linked` verificó **22 migraciones locales y remotas emparejadas**, desde `20261002204457` hasta `20261012024548`.
- Se consultaron remotamente las políticas RLS, grants, índices, seguridad/checks de rol de funciones y el fuente de `registrar_pago`.
- `db advisors --linked --type security/performance` devolvió las advertencias descritas en este documento.
- `supabase db diff --linked --schema public,private_sync` no pudo crear la base sombra porque Docker no expone el motor `dockerDesktopLinuxEngine`. **El historial coincidente no prueba por sí solo que todo el esquema sea idéntico**; se contrastaron los objetos relevantes con consultas dirigidas.
- `supabase db push --linked --dry-run` mostró solo la migración nueva; `db push --linked` la aplicó y las consultas posteriores verificaron el índice, RLS, ACL y guardas de rol.
- El encabezado y la sección 3.5 de `backend-contract.md` registran el resultado aplicado y el diff global pendiente.

Comandos de verificación remota ejecutados:

```powershell
npx --yes supabase@2.120.0 migration list --linked
npx --yes supabase@2.120.0 db query --linked "<consulta SELECT de auditoría>"
npx --yes supabase@2.120.0 db advisors --linked --type security
npx --yes supabase@2.120.0 db advisors --linked --type performance
```

Antes de cambios remotos adicionales, completar el diff de esquema cuando Docker esté disponible. Toda migración futura debe revisarse con `npx --yes supabase@2.120.0 db push --linked --dry-run` antes de aplicarse.

## 4. Regla única de sincronización propuesta

**Room será la única fuente de estado observable dentro del dispositivo.** Las pantallas/ViewModels llaman casos de uso; los casos de uso llaman repositorios; los repositorios escriben en Room y mantienen un outbox. Ninguna pantalla escribe directo a Supabase ni mantiene una copia mutable paralela de la orden o de la ocupación.

### Apertura de orden y varias órdenes por mesa

- El cliente genera el UUID de la orden y un `command_id`/`idempotency_key` aleatorio antes de guardarla localmente.
- Una transacción Room guarda el borrador local y el comando de apertura juntos. El comando conserva autor, payload inmutable, fecha, estado y clave en cada reintento.
- Una RPC de apertura/aceptación valida el comando y escribe orden + primer conjunto de detalles de manera atómica, o acepta una orden ya creada con UUID de cliente. Una repetición con la misma clave y payload devuelve el mismo resultado; la misma clave con payload diferente se rechaza. La orden queda con identidad estable en local y remoto.
- No debe existir unicidad de orden abierta por `mesa_id`; el UUID y la clave idempotente protegen contra duplicados de una misma intención sin limitar órdenes distintas en una mesa.
- Pedidos para llevar se sincronizan igual, pero con `mesa_id = NULL`.

### Estado de mesas derivado

- La lista local de órdenes abiertas es la fuente para calcular ocupación: una mesa está **Ocupada** si tiene una o más órdenes abiertas. El contador/listado debe mostrar todas las órdenes activas de esa mesa.
- La disponibilidad no se muta por separado al crear/cerrar una orden. Eso evita que una actualización de estado pierda una orden concurrente o que la mesa quede libre mientras hay otra orden pendiente.
- `Reservada` es una condición distinta que sí requiere persistencia propia. Una reserva no debe sobrescribir el indicador derivado `Ocupada`: el modelo de lectura debe poder expresar ambas condiciones (por ejemplo, reservado y con órdenes activas).
- Las transiciones orden/detalle (abrir, añadir, cambiar/cancelar renglones, cerrar/cancelar) deben usar RPC o comandos idempotentes y `sync_version`/bloqueos cuando exista concurrencia; no hacer escrituras directas que eludan las reglas del servidor.

### Outbox y orden de sincronización

- Cada acción del usuario crea su clave una sola vez; los reintentos y recuperación tras cierre inesperado conservan esa clave y payload exactos.
- El outbox se serializa por cuenta Auth y orden: primero apertura, después detalles/modificaciones, después pago/cierre. No se envían comandos de un actor con la sesión de otro.
- Una respuesta perdida no provoca una nueva intención: al reconectar se reenvía la misma clave para obtener el recibo/resultado original.
- Solo una confirmación del backend convierte un comando local en operación remota confirmada. Las pendientes y los rechazos se muestran aparte; no se reportan como sincronizadas.
- Un cambio de cuenta bloquea los comandos de la cuenta anterior hasta que regrese ese actor; nunca se reasignan.
- La cache Room conserva estados remotos confirmados y pendientes por separado, y las migraciones conservan datos existentes.

## 5. Pagos y división de cuenta

La RPC remota existente distribuye un pago entre renglones mediante `{detalle_orden_id, cantidad, monto}` y valida el total. Admite pagos parciales, cantidades/artículos, importes iguales/personalizados y varias contribuciones monetarias a la misma unidad a través de transacciones distintas. No permite repetir el mismo detalle dos veces en una sola transacción. La cantidad de estas asignaciones no es un ledger de stock.

Para soportar división por persona sin dobles cobros, cada pago tendrá una clave idempotente propia y llamará la RPC transaccional. La app calculará la parte restante y el servidor seguirá siendo autoridad para bloquear sobrepago y cerrar la orden al cubrir el saldo completo.

El alcance acordado incluye división por artículos/cantidades y división de importe en partes iguales o personalizadas. La capacidad monetaria base ya existe y se debe preservar. El cambio pendiente es separar explícitamente las cantidades que afectan inventario de las cantidades asociadas a aportaciones de pago, con una asignación de stock y validación acumulada que impidan descontar una unidad dos veces.

El cliente definió que el inventario se descuenta en cada pago parcial, proporcional a productos/cantidades asignados a ese pago. El contrato debe separar el importe aportado de la cantidad de stock consumida: pueden existir pagos de importe parcial por una unidad, por lo que la contribución monetaria no determina por sí sola el consumo de una unidad entera. La asignación de stock debe ser entera, no negativa y acumulada por detalle para no superar la cantidad vendida. Cada pago debe registrar atómicamente el pago, la cantidad asignada, el movimiento de venta y sus recibos idempotentes. Cada movimiento referenciará la orden y tendrá clave hija estable y única por pago/producto/detalle. Un reintento no puede descontar de nuevo.

La división monetaria igual/personalizada y el reparto de cantidades son conceptos separados. La RPC debe validar, bajo bloqueo de la orden y sus detalles, el saldo restante, stock asignado acumulado, producto disponible e idempotencia. El pago de una parte del costo puede registrarse sin inventario consumido; cuando una asignación de pago liquide cantidades enteras, solo esas unidades se descuentan. Nunca se admitirán fracciones en el kardex.

## 6. Plan de implementación del paquete de datos

### Fase A — cerrar el contrato antes de implementar

1. Historial local/remoto comparado en 22 migraciones; encender Docker y completar el diff global de esquema.
2. **Completado:** auditoría y corrección remota de grants, RLS, checks de rol, índice de concurrencia y funciones críticas para Mesas/Órdenes/Pagos.
3. Acordar el contrato de asignación de cantidades de stock independiente de `pago_detalles.cantidad`, que en el esquema actual representa cantidad de asignación de pago.
4. Definir política de cancelación/restauración, especialmente después de pagos o descuento confirmado de stock.
5. Definir estados permitidos de orden, línea, pago, mesa y reserva, incluida la regla para cancelar órdenes con pagos existentes.

### Fase B — backend mínimo y comprobable

1. **Completado:** se creó y aplicó una migración nueva e incremental, sin reescribir migraciones históricas.
2. **Completado:** permisos de lectura de Mesas/Órdenes/Detalles/Pagos alineados a `admin`/`gerente`; `anon` fuera de las tablas operativas auditadas y DML directo restringido.
3. **Completado en esquema remoto:** se retiró el índice de una orden abierta por mesa. Aún debe cubrirse con pruebas automatizadas al habilitar el entorno local de Supabase.
4. Diseñar RPC/recibos idempotentes para abrir orden y registrar/modificar renglones, con payload inmutable y respuesta reconciliable.
5. Extender `registrar_pago` para conservar las contribuciones iguales/personalizadas existentes, asignar inventario en cantidades enteras y registrar pago/movimientos de venta atómicamente.
6. Probar concurrencia y reintentos con dos dispositivos/actores en entorno local de Supabase, y validar RLS con admin, gerente, anon y usuario sin rol.

### Fase C — Android/Room vertical slice

1. Crear entidades y DAO mínimos de mesa, orden, detalle y outbox, extendiendo `AppDatabase` con migración versionada desde v3.
2. Implementar primero lectura de mesas/órdenes desde Room con refresco remoto y estados derivados.
3. Implementar apertura online y offline, luego agregar productos/renglones, luego cambios/cancelación, cada paso con clave idempotente.
4. Integrar pagos/división una vez cerrada la regla y usar el recibo remoto para reconciliar.
5. Conectar cada incremento a la UI de Antigravity mediante contratos acordados; validar compilación, pruebas locales de Room/RPC y ejecución en dispositivo.

## 7. Criterios de aceptación del primer flujo

- Una mesa admite varias órdenes abiertas distintas.
- El reintento del mismo comando no duplica orden, renglón, movimiento de inventario ni pago.
- El mismo idempotency key con payload distinto es rechazado.
- Una orden sin mesa se crea, se restaura y se sincroniza sin mesa ficticia.
- La mesa aparece ocupada mientras quede al menos una orden abierta; al cerrar la última, deja de aparecer ocupada, salvo que tenga una reserva activa.
- Admin y gerente pueden operar solo lo autorizado; anon y cuentas sin rol no pueden leer ni mutar información operativa.
- El pago parcial y la división aprobada no permiten sobrepago ni liquidación duplicada; la orden cierra solo cuando el saldo queda en cero.
- La app reinicia desde Room y reanuda los mismos comandos sin crear nuevas claves ni borrar datos previos.

## 8. Pendientes para continuar

1. Iniciar Docker Desktop con motor Linux y completar `db diff --linked --schema public,private_sync`.
2. Acordar política de cancelación/restauración postpago y estados finales de orden/línea/reserva.
3. Crear las cuentas gerente y validar permisos efectivos con sesión Auth real; aún no hay cuentas gerente para una prueba funcional con identidad real.

