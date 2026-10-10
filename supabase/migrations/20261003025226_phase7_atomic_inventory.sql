create or replace function public.registrar_movimiento_inventario(
  p_producto_id uuid,
  p_cantidad integer,
  p_tipo text,
  p_idempotency_key uuid,
  p_referencia_id uuid default null,
  p_notas text default null
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_existing uuid;
  v_stock integer;
  v_controls boolean;
  v_movement_id uuid;
  v_new_stock integer;
begin
  if public.auth_role() not in ('admin', 'empleado') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;
  if p_cantidad = 0 then raise exception 'invalid_quantity'; end if;
  if p_tipo not in ('entrada', 'venta', 'ajuste', 'cancelacion', 'merma') then
    raise exception 'invalid_movement_type';
  end if;
  if p_tipo in ('venta', 'merma') and p_cantidad > 0 then
    raise exception 'movement_sign_mismatch';
  end if;
  if p_tipo in ('entrada', 'cancelacion') and p_cantidad < 0 then
    raise exception 'movement_sign_mismatch';
  end if;

  select id into v_existing from public.movimientos_inventario
  where idempotency_key = p_idempotency_key;
  if v_existing is not null then
    return jsonb_build_object('movement_id', v_existing, 'idempotent', true);
  end if;

  select stock_actual, controla_inventario into v_stock, v_controls
  from public.productos where id = p_producto_id for update;
  if not found then raise exception 'product_not_found'; end if;
  if not v_controls and p_tipo in ('venta', 'merma') then
    raise exception 'product_does_not_track_inventory';
  end if;
  v_new_stock := v_stock + p_cantidad;
  if v_new_stock < 0 then raise exception 'insufficient_stock'; end if;

  insert into public.movimientos_inventario
    (producto_id, cantidad, tipo, referencia_id, notas, creado_por, idempotency_key)
  values
    (p_producto_id, p_cantidad, p_tipo, p_referencia_id, p_notas, auth.uid(), p_idempotency_key)
  returning id into v_movement_id;
  update public.productos set stock_actual = v_new_stock, updated_at = now()
  where id = p_producto_id;
  return jsonb_build_object(
    'movement_id', v_movement_id, 'stock_actual', v_new_stock, 'idempotent', false
  );
end;
$$;

revoke all on function public.registrar_movimiento_inventario(uuid, integer, text, uuid, uuid, text)
  from public, anon;
grant execute on function public.registrar_movimiento_inventario(uuid, integer, text, uuid, uuid, text)
  to authenticated;

comment on function public.registrar_movimiento_inventario(uuid, integer, text, uuid, uuid, text)
  is 'Registra un movimiento y actualiza stock con bloqueo de producto e idempotencia.';
