create or replace function public.realizar_corte_caja_seguro(p_fecha date)
returns table (
  total_efectivo bigint,
  total_tarjeta bigint,
  total_otro bigint,
  total_global bigint,
  num_ordenes integer,
  ya_existia boolean
)
language plpgsql
security invoker
set search_path = public, auth
as $$
declare
  v_corte public.venta_diaria%rowtype;
  v_cerrado_por uuid;
begin
  if public.auth_role() <> 'admin' then
    raise exception 'not_authorized' using errcode = '42501';
  end if;
  select * into v_corte from public.venta_diaria where fecha = p_fecha for update;
  if found then
    return query select v_corte.total_efectivo, v_corte.total_tarjeta,
      v_corte.total_otro, v_corte.total_global, v_corte.num_ordenes, true;
    return;
  end if;

  select id into v_cerrado_por from public.empleados where user_id = auth.uid() limit 1;
  select coalesce(sum(case when metodo_pago = 'Efectivo' then monto else 0 end), 0),
         coalesce(sum(case when metodo_pago = 'Tarjeta' then monto else 0 end), 0),
         coalesce(sum(case when metodo_pago not in ('Efectivo', 'Tarjeta') then monto else 0 end), 0),
         coalesce(sum(monto), 0)
    into total_efectivo, total_tarjeta, total_otro, total_global
  from public.transacciones
  where (fecha at time zone 'America/Mexico_City')::date = p_fecha;
  select count(*)::integer into num_ordenes from public.ordenes
  where estado = 'Cerrada' and (fecha_cierre at time zone 'America/Mexico_City')::date = p_fecha;

  insert into public.venta_diaria(fecha, total_efectivo, total_tarjeta, total_otro, total_global, num_ordenes, cerrado_por)
  values (p_fecha, total_efectivo, total_tarjeta, total_otro, total_global, num_ordenes, v_cerrado_por)
  returning * into v_corte;
  ya_existia := false;
  return next;
exception when unique_violation then
  select * into v_corte from public.venta_diaria where fecha = p_fecha;
  return query select v_corte.total_efectivo, v_corte.total_tarjeta,
    v_corte.total_otro, v_corte.total_global, v_corte.num_ordenes, true;
end;
$$;

revoke all on function public.realizar_corte_caja_seguro(date) from public, anon;
grant execute on function public.realizar_corte_caja_seguro(date) to authenticated;

comment on function public.realizar_corte_caja_seguro(date) is
  'Cierre diario administrativo idempotente por fecha local del negocio.';
