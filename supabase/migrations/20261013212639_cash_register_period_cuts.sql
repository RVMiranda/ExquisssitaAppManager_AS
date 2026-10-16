-- Per-period, append-only cash cuts. The generic sync receipt supplies
-- actor-scoped idempotency; this function is callable only through sync_execute_v2.
create table public.cortes_caja (
  id uuid primary key default gen_random_uuid(),
  fecha date not null,
  numero_corte smallint not null check (numero_corte between 1 and 5),
  periodo_inicio timestamptz not null,
  periodo_fin timestamptz not null,
  total_efectivo bigint not null default 0 check (total_efectivo >= 0),
  total_tarjeta bigint not null default 0 check (total_tarjeta >= 0),
  total_otro bigint not null default 0 check (total_otro >= 0),
  total_global bigint not null default 0 check (total_global >= 0),
  num_ordenes integer not null default 0 check (num_ordenes >= 0),
  hora_cierre timestamptz not null default now(),
  cerrado_por uuid references public.empleados(id) on delete set null,
  sync_version bigint not null default 1,
  constraint cortes_caja_periodo_valido check (periodo_fin > periodo_inicio),
  constraint cortes_caja_fecha_numero_key unique (fecha, numero_corte)
);

create index cortes_caja_fecha_cierre_idx
  on public.cortes_caja (fecha, hora_cierre desc);

alter table public.cortes_caja enable row level security;
revoke all on table public.cortes_caja from public, anon, authenticated;
grant select on table public.cortes_caja to authenticated;
create policy cortes_caja_staff_select on public.cortes_caja
  for select to authenticated
  using (coalesce((select public.auth_role()), '') in ('admin', 'gerente'));

-- The daily row remains a compatibility/summary table; individual cut data is
-- stored in cortes_caja and never overwritten.
drop policy if exists venta_diaria_admin_all on public.venta_diaria;
drop policy if exists venta_diaria_empleado_select on public.venta_diaria;
revoke insert, update, delete, truncate, references, trigger on public.venta_diaria from anon, authenticated;
grant select on public.venta_diaria to authenticated;
create policy venta_diaria_staff_select on public.venta_diaria
  for select to authenticated
  using (coalesce((select public.auth_role()), '') in ('admin', 'gerente'));

drop function if exists public.realizar_corte_caja_seguro(date);

create function public.realizar_corte_caja_seguro(p_fecha date)
returns public.cortes_caja
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_role text;
  v_actor_employee uuid;
  v_cut public.cortes_caja%rowtype;
  v_period_start timestamptz;
  v_period_end timestamptz;
  v_next_number integer;
begin
  v_role := public.auth_role();
  if auth.uid() is null or coalesce(v_role, '') not in ('admin', 'gerente') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;
  if p_fecha is distinct from (now() at time zone 'America/Mexico_City')::date then
    raise exception 'cash_cut_current_business_date_only' using errcode = '22023';
  end if;

  -- Serialize by business date as defense in depth; the sync wrapper also
  -- globally serializes all financial/operational commands.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('cash-cut:' || p_fecha::text, 0));

  if exists (select 1 from public.ordenes o where o.estado = 'Abierta') then
    raise exception 'open_orders_exist' using errcode = 'P0001';
  end if;

  select coalesce(max(c.numero_corte), 0) + 1
    into v_next_number
    from public.cortes_caja c
   where c.fecha = p_fecha;
  if v_next_number > 5 then
    raise exception 'cash_cut_daily_limit_reached' using errcode = 'P0001';
  end if;

  select c.periodo_fin into v_period_start
    from public.cortes_caja c
   where c.fecha = p_fecha
   order by c.numero_corte desc
   limit 1;
  if v_period_start is null then
    v_period_start := p_fecha::timestamp at time zone 'America/Mexico_City';
  end if;
  v_period_end := clock_timestamp();
  if v_period_end <= v_period_start then
    raise exception 'cash_cut_empty_period' using errcode = '22023';
  end if;

  select e.id into v_actor_employee
    from public.empleados e
   where e.user_id = auth.uid() and e.activo = true
   order by e.created_at
   limit 1;

  insert into public.cortes_caja (
    fecha, numero_corte, periodo_inicio, periodo_fin,
    total_efectivo, total_tarjeta, total_otro, total_global,
    num_ordenes, hora_cierre, cerrado_por
  )
  select p_fecha, v_next_number, v_period_start, v_period_end,
    coalesce(sum(t.monto) filter (where t.metodo_pago = 'Efectivo'), 0),
    coalesce(sum(t.monto) filter (where t.metodo_pago = 'Tarjeta'), 0),
    coalesce(sum(t.monto) filter (where t.metodo_pago not in ('Efectivo', 'Tarjeta')), 0),
    coalesce(sum(t.monto), 0),
    (select count(*)::integer from public.ordenes o
      where o.estado = 'Cerrada' and o.fecha_cierre >= v_period_start and o.fecha_cierre < v_period_end),
    v_period_end, v_actor_employee
  from public.transacciones t
  where t.fecha >= v_period_start and t.fecha < v_period_end
  returning * into v_cut;

  insert into public.venta_diaria (
    fecha, total_efectivo, total_tarjeta, total_otro,
    total_global, num_ordenes, hora_cierre, cerrado_por
  )
  select p_fecha,
    coalesce(sum(c.total_efectivo), 0), coalesce(sum(c.total_tarjeta), 0),
    coalesce(sum(c.total_otro), 0), coalesce(sum(c.total_global), 0),
    coalesce(sum(c.num_ordenes), 0), max(c.hora_cierre), v_actor_employee
  from public.cortes_caja c
  where c.fecha = p_fecha
  on conflict (fecha) do update set
    total_efectivo = excluded.total_efectivo,
    total_tarjeta = excluded.total_tarjeta,
    total_otro = excluded.total_otro,
    total_global = excluded.total_global,
    num_ordenes = excluded.num_ordenes,
    hora_cierre = excluded.hora_cierre,
    cerrado_por = excluded.cerrado_por,
    sync_version = public.venta_diaria.sync_version + 1;

  return v_cut;
end;
$$;

revoke all on function public.realizar_corte_caja_seguro(date) from public, anon, authenticated;
grant execute on function public.realizar_corte_caja_seguro(date) to postgres;
revoke all on function public.realizar_corte_caja(date, uuid) from public, anon, authenticated;

comment on table public.cortes_caja is
  'Append-only cash register cuts. Each row totals a non-overlapping period since the prior cut.';
comment on function public.realizar_corte_caja_seguro(date) is
  'Creates one current Mexico City business-day cut, up to five per day, only when no order is open. Call through sync_execute_v2 for idempotency.';
