-- Tablas de integridad para la aplicación administrativa.
-- Todas las cantidades monetarias están expresadas en centavos.

create table public.pago_detalles (
  id uuid primary key default extensions.uuid_generate_v4(),
  transaccion_id uuid not null references public.transacciones(id) on delete restrict,
  detalle_orden_id uuid not null references public.detalle_orden(id) on delete restrict,
  cantidad integer not null check (cantidad > 0),
  monto bigint not null check (monto > 0),
  created_at timestamptz not null default now(),
  unique (transaccion_id, detalle_orden_id)
);

create table public.movimientos_inventario (
  id uuid primary key default extensions.uuid_generate_v4(),
  producto_id uuid not null references public.productos(id) on delete restrict,
  cantidad integer not null check (cantidad <> 0),
  tipo text not null check (tipo in ('entrada', 'venta', 'ajuste', 'cancelacion', 'merma')),
  referencia_id uuid,
  notas text,
  creado_por uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  idempotency_key uuid not null unique default extensions.uuid_generate_v4()
);

create table public.visitas_clientes (
  id uuid primary key default extensions.uuid_generate_v4(),
  cliente_id uuid not null references public.clientes(id) on delete restrict,
  fecha date not null default (now() at time zone 'America/Mexico_City')::date,
  puntos_otorgados integer not null default 10 check (puntos_otorgados >= 0),
  registrado_por uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (cliente_id, fecha)
);

create table public.auditoria_eventos (
  id uuid primary key default extensions.uuid_generate_v4(),
  actor_id uuid references auth.users(id) on delete set null,
  entidad text not null,
  entidad_id uuid,
  accion text not null check (accion in ('crear', 'actualizar', 'eliminar', 'pagar', 'cancelar', 'sincronizar', 'ajustar')),
  datos jsonb not null default '{}'::jsonb,
  dispositivo_id text,
  offline boolean not null default false,
  created_at timestamptz not null default now()
);

create unique index ordenes_una_abierta_por_mesa
  on public.ordenes(mesa_id)
  where mesa_id is not null and estado = 'Abierta';

create index pago_detalles_transaccion_idx on public.pago_detalles(transaccion_id);
create index pago_detalles_detalle_idx on public.pago_detalles(detalle_orden_id);
create index movimientos_inventario_producto_idx on public.movimientos_inventario(producto_id, created_at desc);
create index visitas_clientes_fecha_idx on public.visitas_clientes(fecha desc);
create index auditoria_eventos_entidad_idx on public.auditoria_eventos(entidad, entidad_id, created_at desc);

alter table public.pago_detalles enable row level security;
alter table public.movimientos_inventario enable row level security;
alter table public.visitas_clientes enable row level security;
alter table public.auditoria_eventos enable row level security;

create policy pago_detalles_staff_select on public.pago_detalles
  for select to authenticated using (public.auth_role() in ('admin', 'empleado'));
create policy pago_detalles_staff_insert on public.pago_detalles
  for insert to authenticated with check (public.auth_role() in ('admin', 'empleado'));

create policy movimientos_inventario_staff_select on public.movimientos_inventario
  for select to authenticated using (public.auth_role() in ('admin', 'empleado'));
create policy movimientos_inventario_staff_insert on public.movimientos_inventario
  for insert to authenticated with check (public.auth_role() in ('admin', 'empleado'));

create policy visitas_clientes_staff_select on public.visitas_clientes
  for select to authenticated using (public.auth_role() in ('admin', 'empleado'));
create policy visitas_clientes_staff_insert on public.visitas_clientes
  for insert to authenticated with check (public.auth_role() in ('admin', 'empleado'));

create policy auditoria_eventos_admin_select on public.auditoria_eventos
  for select to authenticated using (public.auth_role() = 'admin');
create policy auditoria_eventos_staff_insert on public.auditoria_eventos
  for insert to authenticated with check (public.auth_role() in ('admin', 'empleado'));

comment on table public.pago_detalles is 'Asignación inmutable de transacciones a productos/cantidades para división de cuenta.';
comment on table public.movimientos_inventario is 'Libro de movimientos de inventario; no se corrige borrando filas.';
comment on table public.visitas_clientes is 'Una visita máxima por cliente y fecha local del negocio.';
comment on table public.auditoria_eventos is 'Trazabilidad de operaciones administrativas y sincronización offline.';
