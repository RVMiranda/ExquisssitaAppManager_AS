-- Operación financiera atómica para pagos completos, parciales y divididos.
-- SECURITY INVOKER: respeta las políticas RLS del usuario autenticado.
create or replace function public.registrar_pago(
  p_orden_id uuid,
  p_monto bigint,
  p_metodo_pago text,
  p_idempotency_key uuid,
  p_asignaciones jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_existing uuid;
  v_transaction_id uuid;
  v_order_total bigint;
  v_paid_before bigint;
  v_allocation_total bigint := 0;
  v_detail record;
  v_requested_qty integer;
  v_requested_amount bigint;
  v_detail_total bigint;
  v_detail_paid bigint;
  v_item jsonb;
begin
  if public.auth_role() not in ('admin', 'empleado') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;
  if p_monto <= 0 then raise exception 'invalid_amount'; end if;
  if p_metodo_pago not in ('Efectivo', 'Tarjeta', 'Transferencia', 'Otro') then
    raise exception 'invalid_payment_method';
  end if;
  if jsonb_typeof(p_asignaciones) <> 'array' or jsonb_array_length(p_asignaciones) = 0 then
    raise exception 'invalid_allocations';
  end if;

  select id into v_existing
  from public.transacciones
  where idempotency_key = p_idempotency_key;
  if v_existing is not null then
    return jsonb_build_object('transaction_id', v_existing, 'idempotent', true);
  end if;

  perform 1 from public.ordenes where id = p_orden_id for update;
  if not found then raise exception 'order_not_found'; end if;
  select coalesce(sum(d.cantidad * d.precio_unitario), 0)
    into v_order_total
  from public.detalle_orden d where d.orden_id = p_orden_id;
  select coalesce(sum(t.monto), 0) into v_paid_before
  from public.transacciones t where t.orden_id = p_orden_id;
  if v_paid_before + p_monto > v_order_total then raise exception 'overpayment'; end if;

  for v_detail in
    select d.id, d.cantidad, d.precio_unitario
    from public.detalle_orden d
    where d.orden_id = p_orden_id
    for update
  loop
    select coalesce(sum(pd.cantidad), 0), coalesce(sum(pd.monto), 0)
      into v_requested_qty, v_detail_paid
    from public.pago_detalles pd
    where pd.detalle_orden_id = v_detail.id;
    for v_item in
      select item
      from jsonb_array_elements(p_asignaciones) item
      where (item->>'detalle_orden_id')::uuid = v_detail.id
    loop
      v_requested_qty := (v_item->>'cantidad')::integer;
      v_requested_amount := (v_item->>'monto')::bigint;
      if v_requested_qty <= 0 or v_requested_amount <= 0 then raise exception 'invalid_allocation'; end if;
      if v_requested_qty + coalesce((select sum(pd.cantidad) from public.pago_detalles pd where pd.detalle_orden_id = v_detail.id), 0) > v_detail.cantidad then
        raise exception 'quantity_already_paid';
      end if;
      v_detail_total := v_detail.cantidad * v_detail.precio_unitario;
      if v_requested_amount + v_detail_paid > v_detail_total then raise exception 'detail_overpayment'; end if;
      v_allocation_total := v_allocation_total + v_requested_amount;
    end loop;
  end loop;
  if v_allocation_total <> p_monto then raise exception 'allocation_amount_mismatch'; end if;

  insert into public.transacciones (orden_id, monto, metodo_pago, idempotency_key)
    values (p_orden_id, p_monto, p_metodo_pago, p_idempotency_key)
    returning id into v_transaction_id;
  insert into public.pago_detalles (transaccion_id, detalle_orden_id, cantidad, monto)
    select v_transaction_id, (item->>'detalle_orden_id')::uuid,
           (item->>'cantidad')::integer, (item->>'monto')::bigint
    from jsonb_array_elements(p_asignaciones) item;

  update public.detalle_orden d set estado_pago = 'Pagado', updated_at = now()
  where d.orden_id = p_orden_id
    and (select coalesce(sum(pd.monto), 0) from public.pago_detalles pd where pd.detalle_orden_id = d.id)
        >= d.cantidad * d.precio_unitario;
  if v_paid_before + p_monto = v_order_total then
    update public.ordenes set estado = 'Cerrada', fecha_cierre = now(), updated_at = now()
    where id = p_orden_id;
  end if;
  return jsonb_build_object('transaction_id', v_transaction_id, 'idempotent', false);
end;
$$;

revoke all on function public.registrar_pago(uuid, bigint, text, uuid, jsonb) from public, anon;
grant execute on function public.registrar_pago(uuid, bigint, text, uuid, jsonb) to authenticated;

comment on function public.registrar_pago(uuid, bigint, text, uuid, jsonb)
  is 'Registra un pago con asignaciones atómicas, idempotencia y bloqueo de orden/detalles.';
