-- La sobrecarga nueva debe requerir el séptimo argumento para que las llamadas
-- de seis argumentos sigan resolviendo sin ambigüedad a la firma heredada.
drop function if exists public.registrar_movimiento_inventario(
  uuid, integer, text, uuid, uuid, text, bigint
);

create function public.registrar_movimiento_inventario(
  p_producto_id uuid,
  p_cantidad integer,
  p_tipo text,
  p_idempotency_key uuid,
  p_referencia_id uuid,
  p_notas text,
  p_expected_version bigint
)
returns jsonb
language sql
security definer
set search_path = ''
as $$
  select private_sync.inventory_command(
    jsonb_build_object(
      'p_producto_id', p_producto_id,
      'p_cantidad', p_cantidad,
      'p_tipo', p_tipo,
      'p_idempotency_key', p_idempotency_key,
      'p_referencia_id', p_referencia_id,
      'p_notas', p_notas,
      'p_expected_version', p_expected_version
    )
  );
$$;

revoke all on function public.registrar_movimiento_inventario(
  uuid, integer, text, uuid, uuid, text, bigint
) from public, anon, authenticated;
grant execute on function public.registrar_movimiento_inventario(
  uuid, integer, text, uuid, uuid, text, bigint
) to authenticated;

comment on function public.registrar_movimiento_inventario(
  uuid, integer, text, uuid, uuid, text, bigint
) is 'Registers inventory movement with an explicit expected version for adjustment conflict detection.';
