-- Upgrade RPC de rentabilidad diaria para consolidar ventas desde transacciones, cortes de caja y órdenes,
-- y exponer compatibilidad dual de llaves JSON para clientes móviles.

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
  v_ventas_tarjeta bigint := 0;
  v_ventas_otro bigint := 0;
  v_num_ordenes integer := 0;

  v_cortes_global bigint := 0;
  v_cortes_efectivo bigint := 0;
  v_cortes_tarjeta bigint := 0;
  v_cortes_otro bigint := 0;
  v_cortes_ordenes integer := 0;

  v_ordenes_global bigint := 0;

  v_gastos bigint := 0;
  v_gastos_efectivo bigint := 0;
  v_gastos_otros bigint := 0;
  v_num_gastos integer := 0;

  v_nomina bigint := 0;
  v_num_nomina integer := 0;

  v_egresos bigint := 0;
  v_ganancia bigint := 0;
  v_caja_esperada bigint := 0;
begin
  v_role := public.auth_role();
  if auth.uid() is null or coalesce(v_role, '') not in ('admin', 'gerente') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;

  -- 1. Ventas desde transacciones de la fecha comercial
  select
    coalesce(sum(monto), 0),
    coalesce(sum(monto) filter (where metodo_pago = 'Efectivo'), 0),
    coalesce(sum(monto) filter (where metodo_pago = 'Tarjeta'), 0),
    coalesce(sum(monto) filter (where metodo_pago not in ('Efectivo', 'Tarjeta')), 0),
    count(distinct orden_id)
  into v_ventas, v_ventas_efectivo, v_ventas_tarjeta, v_ventas_otro, v_num_ordenes
  from public.transacciones
  where (fecha at time zone 'America/Mexico_City')::date = p_fecha;

  -- 2. Ventas consolidadas desde cortes de caja registrados en la fecha
  select
    coalesce(sum(total_global), 0),
    coalesce(sum(total_efectivo), 0),
    coalesce(sum(total_tarjeta), 0),
    coalesce(sum(total_otro), 0),
    coalesce(sum(num_ordenes), 0)
  into v_cortes_global, v_cortes_efectivo, v_cortes_tarjeta, v_cortes_otro, v_cortes_ordenes
  from public.cortes_caja
  where fecha = p_fecha;

  if v_cortes_global > v_ventas then
    v_ventas := v_cortes_global;
    v_ventas_efectivo := v_cortes_efectivo;
    v_ventas_tarjeta := v_cortes_tarjeta;
    v_ventas_otro := v_cortes_otro;
    v_num_ordenes := greatest(v_num_ordenes, v_cortes_ordenes);
  end if;

  -- 3. Respaldo desde órdenes cerradas en caso de transacciones registradas sin corte previo
  select coalesce(sum(total), 0)
  into v_ordenes_global
  from public.ordenes
  where estado = 'Cerrada' and (fecha_cierre at time zone 'America/Mexico_City')::date = p_fecha;

  if v_ordenes_global > v_ventas then
    v_ventas := v_ordenes_global;
  end if;

  -- 4. Gastos operativos registrados en la fecha comercial
  select
    coalesce(sum(monto), 0),
    coalesce(sum(monto) filter (where metodo_pago = 'Efectivo'), 0),
    coalesce(sum(monto) filter (where metodo_pago not in ('Efectivo')), 0),
    count(*)
  into v_gastos, v_gastos_efectivo, v_gastos_otros, v_num_gastos
  from public.gastos
  where fecha = p_fecha;

  -- 5. Nómina pagada en la fecha
  select
    coalesce(sum(monto), 0),
    count(*)
  into v_nomina, v_num_nomina
  from public.historial_pagos_empleados
  where fecha_pago = p_fecha;

  v_egresos := v_gastos + v_nomina;
  v_ganancia := v_ventas - v_egresos;
  v_caja_esperada := v_ventas_efectivo - v_gastos_efectivo - v_nomina;

  return jsonb_build_object(
    'fecha', p_fecha,
    'ventas_totales', v_ventas,
    'ventas_efectivo', v_ventas_efectivo,
    'ventas_tarjeta', v_ventas_tarjeta,
    'ventas_otro', v_ventas_otro,
    'num_ordenes_cobradas', v_num_ordenes,
    'gastos_totales', v_gastos,
    'gastos_efectivo', v_gastos_efectivo,
    'gastos_otros_metodos', v_gastos_otros,
    'num_gastos_registrados', v_num_gastos,
    'nomina_total', v_nomina,
    'num_pagos_nomina', v_num_nomina,
    'egresos_totales', v_egresos,
    'ganancia_neta', v_ganancia,
    'efectivo_en_caja_esperado', v_caja_esperada,
    'ventas_totales_centavos', v_ventas,
    'ventas_efectivo_centavos', v_ventas_efectivo,
    'ventas_tarjeta_centavos', v_ventas_tarjeta,
    'ventas_otro_centavos', v_ventas_otro,
    'gastos_totales_centavos', v_gastos,
    'gastos_efectivo_centavos', v_gastos_efectivo,
    'gastos_otros_metodos_centavos', v_gastos_otros,
    'nomina_total_centavos', v_nomina,
    'egresos_totales_centavos', v_egresos,
    'ganancia_neta_centavos', v_ganancia,
    'efectivo_en_caja_esperado_centavos', v_caja_esperada
  );
end;
$$;

revoke all on function public.obtener_resumen_rentabilidad_diaria(date) from public;
grant execute on function public.obtener_resumen_rentabilidad_diaria(date) to authenticated, service_role, postgres;
