-- Migración: Módulo de Gastos Operativos, Plantillas de Gastos Frecuentes y Contraste de Rentabilidad
-- Fecha: 2026-11-12
-- Proyecto: La Exquisssita App Manager

-- 1. Tabla de Catálogo de Gastos Frecuentes / Fijos (Plantillas)
create table if not exists public.plantillas_gastos (
  id uuid primary key default gen_random_uuid(),
  nombre text not null,
  categoria text not null default 'Insumos',
  precio_unitario bigint not null default 0 check (precio_unitario >= 0), -- En centavos MXN
  unidad_medida text not null default 'kg',                              -- 'kg', 'reja', 'pieza', 'servicio', 'compra'
  activo boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sync_version bigint not null default 1
);

create index if not exists plantillas_gastos_activo_idx on public.plantillas_gastos (activo);
create index if not exists plantillas_gastos_categoria_idx on public.plantillas_gastos (categoria);

-- 2. Tabla de Gastos Operativos del Día
create table if not exists public.gastos (
  id uuid primary key default gen_random_uuid(),
  fecha date not null default (now() at time zone 'America/Mexico_City')::date,
  concepto text not null,
  categoria text not null default 'Insumos',
  monto bigint not null check (monto > 0),                               -- En centavos MXN
  cantidad numeric(8,2),                                                 -- Cantidad opcional (ej: 10.00 kg)
  precio_unitario bigint,                                                -- Precio unitario en centavos si aplica
  metodo_pago text not null default 'Efectivo' check (metodo_pago in ('Efectivo', 'Transferencia', 'Otro')),
  plantilla_id uuid references public.plantillas_gastos(id) on delete set null,
  notas text,
  registrado_por uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sync_version bigint not null default 1
);

create index if not exists gastos_fecha_idx on public.gastos (fecha desc);
create index if not exists gastos_categoria_idx on public.gastos (categoria);

-- 3. Triggers para updated_at automático
drop trigger if exists set_updated_at_plantillas_gastos on public.plantillas_gastos;
create trigger set_updated_at_plantillas_gastos
  before update on public.plantillas_gastos
  for each row execute function public.trigger_set_updated_at();

drop trigger if exists set_updated_at_gastos on public.gastos;
create trigger set_updated_at_gastos
  before update on public.gastos
  for each row execute function public.trigger_set_updated_at();

-- 4. Seguridad y Row Level Security (RLS)
alter table public.plantillas_gastos enable row level security;
alter table public.gastos enable row level security;

revoke all on table public.plantillas_gastos from public, anon;
revoke all on table public.gastos from public, anon;

grant select, insert, update, delete on table public.plantillas_gastos to authenticated;
grant select, insert, update, delete on table public.gastos to authenticated;

drop policy if exists plantillas_gastos_staff_all on public.plantillas_gastos;
create policy plantillas_gastos_staff_all on public.plantillas_gastos
  for all to authenticated
  using (coalesce((select public.auth_role()), '') in ('admin', 'gerente'))
  with check (coalesce((select public.auth_role()), '') in ('admin', 'gerente'));

drop policy if exists gastos_staff_all on public.gastos;
create policy gastos_staff_all on public.gastos
  for all to authenticated
  using (coalesce((select public.auth_role()), '') in ('admin', 'gerente'))
  with check (coalesce((select public.auth_role()), '') in ('admin', 'gerente'));

-- 5. Publicación en Supabase Realtime
do $$
begin
  if not exists (
    select 1 from pg_publication_tables 
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'plantillas_gastos'
  ) then
    alter publication supabase_realtime add table public.plantillas_gastos;
  end if;

  if not exists (
    select 1 from pg_publication_tables 
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'gastos'
  ) then
    alter publication supabase_realtime add table public.gastos;
  end if;
end $$;

-- 6. RPC para cálculo del Contraste de Rentabilidad Diaria
create or replace function public.obtener_resumen_rentabilidad_diaria(p_fecha date)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
  v_ventas bigint := 0;
  v_ventas_efectivo bigint := 0;
  v_gastos bigint := 0;
  v_gastos_efectivo bigint := 0;
  v_nomina bigint := 0;
  v_egresos bigint := 0;
  v_ganancia bigint := 0;
  v_caja_esperada bigint := 0;
begin
  v_role := public.auth_role();
  if auth.uid() is null or coalesce(v_role, '') not in ('admin', 'gerente') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;

  -- Ventas totales desde transacciones cerradas de la fecha comercial
  select
    coalesce(sum(monto), 0),
    coalesce(sum(monto) filter (where metodo_pago = 'Efectivo'), 0)
  into v_ventas, v_ventas_efectivo
  from public.transacciones
  where (fecha at time zone 'America/Mexico_City')::date = p_fecha;

  -- Gastos operativos registrados en la fecha
  select
    coalesce(sum(monto), 0),
    coalesce(sum(monto) filter (where metodo_pago = 'Efectivo'), 0)
  into v_gastos, v_gastos_efectivo
  from public.gastos
  where fecha = p_fecha;

  -- Nómina pagada en la fecha
  select
    coalesce(sum(monto), 0)
  into v_nomina
  from public.historial_pagos_empleados
  where fecha_pago = p_fecha;

  v_egresos := v_gastos + v_nomina;
  v_ganancia := v_ventas - v_egresos;
  v_caja_esperada := v_ventas_efectivo - v_gastos_efectivo - v_nomina;

  return jsonb_build_object(
    'fecha', p_fecha,
    'ventas_totales', v_ventas,
    'ventas_efectivo', v_ventas_efectivo,
    'gastos_totales', v_gastos,
    'gastos_efectivo', v_gastos_efectivo,
    'nomina_total', v_nomina,
    'egresos_totales', v_egresos,
    'ganancia_neta', v_ganancia,
    'efectivo_en_caja_esperado', v_caja_esperada
  );
end;
$$;

revoke all on function public.obtener_resumen_rentabilidad_diaria(date) from public;
grant execute on function public.obtener_resumen_rentabilidad_diaria(date) to authenticated, service_role, postgres;

-- 7. Sembrado de Plantillas de Gastos Frecuentes Iniciales
insert into public.plantillas_gastos (nombre, categoria, precio_unitario, unidad_medida, activo)
values
  ('Tortilla de comal (kg)', 'Tortillería', 2400, 'kg', true),
  ('Tortilla de máquina (kg)', 'Tortillería', 2200, 'kg', true),
  ('Reja Refresco Coca-Cola (24 pzas)', 'Bebidas', 29000, 'reja', true),
  ('Cilindro Gas LP (30 kg)', 'Servicios', 60000, 'servicio', true),
  ('Carbón / Leña (costal)', 'Insumos', 15000, 'pieza', true),
  ('Verduras y recaudería', 'Insumos', 0, 'compra', true),
  ('Desechables y bolsas', 'Insumos', 0, 'compra', true)
on conflict do nothing;
