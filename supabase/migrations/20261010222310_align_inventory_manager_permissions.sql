-- Alinear el módulo de inventario con los únicos roles de la app Manager.
-- Los empleados de piso no inician sesión en esta aplicación.

create or replace function private_sync.inventory_command(p jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_key uuid := (p->>'p_idempotency_key')::uuid;
  product uuid := (p->>'p_producto_id')::uuid;
  qty integer := (p->>'p_cantidad')::integer;
  kind text := p->>'p_tipo';
  expected bigint := (p->>'p_expected_version')::bigint;
  receipt private_sync.critical_receipts%rowtype;
  row public.productos%rowtype;
  movement uuid;
  version bigint;
  original public.movimientos_inventario%rowtype;
  restored bigint;
begin
  if auth.uid() is null
    or coalesce(public.auth_role(), '') not in ('admin', 'gerente') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;

  if v_key is null or product is null or qty is null or qty = 0
    or kind is null
    or kind not in ('entrada', 'venta', 'ajuste', 'cancelacion', 'merma')
    or (kind in ('venta', 'merma') and qty > 0)
    or (kind in ('entrada', 'cancelacion') and qty < 0) then
    raise exception 'invalid_command' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(78645231);

  select *
  into receipt
  from private_sync.critical_receipts r
  where r.key = v_key;

  if found then
    if receipt.actor_id <> auth.uid()
      or receipt.entity <> 'inventory'
      or receipt.request <> p then
      raise exception 'idempotency_mismatch' using errcode = '22023';
    end if;
    return receipt.result;
  end if;

  if exists (
    select 1
    from public.movimientos_inventario m
    where m.idempotency_key = v_key
  ) then
    raise exception 'legacy_movement_requires_review' using errcode = '22023';
  end if;

  select *
  into row
  from public.productos
  where id = product
  for update;

  if not found or row.stock_actual is null
    or row.controla_inventario is distinct from true then
    return private_sync.record_critical_result(
      v_key,
      'inventory',
      p,
      jsonb_build_object('status', 'rejected', 'reason', 'product_unavailable')
    );
  end if;

  -- Los ajustes manuales requieren autorización administrativa y versión esperada.
  if kind = 'ajuste'
    and (public.auth_role() <> 'admin' or expected is null) then
    raise exception 'version_required' using errcode = '22023';
  end if;

  if expected is not null and expected <> row.sync_version then
    return private_sync.record_critical_result(
      v_key,
      'inventory',
      p,
      jsonb_build_object('status', 'rejected', 'reason', 'stock_version_conflict')
    );
  end if;

  if kind = 'cancelacion' then
    select *
    into original
    from public.movimientos_inventario
    where id = (p->>'p_referencia_id')::uuid
      and producto_id = product
      and cantidad < 0;

    if not found then
      return private_sync.record_critical_result(
        v_key,
        'inventory',
        p,
        jsonb_build_object('status', 'rejected', 'reason', 'invalid_compensation')
      );
    end if;

    select coalesce(sum(cantidad), 0)
    into restored
    from public.movimientos_inventario
    where tipo = 'cancelacion'
      and referencia_id = original.id;

    if restored + qty > -(original.cantidad::bigint) then
      return private_sync.record_critical_result(
        v_key,
        'inventory',
        p,
        jsonb_build_object('status', 'rejected', 'reason', 'compensation_exceeds_original')
      );
    end if;
  end if;

  if qty::bigint + row.stock_actual < 0
    or qty::bigint + row.stock_actual > 2147483647 then
    return private_sync.record_critical_result(
      v_key,
      'inventory',
      p,
      jsonb_build_object('status', 'rejected', 'reason', 'insufficient_stock')
    );
  end if;

  insert into public.movimientos_inventario (
    producto_id,
    cantidad,
    tipo,
    referencia_id,
    notas,
    creado_por,
    idempotency_key
  )
  values (
    product,
    qty,
    kind,
    (p->>'p_referencia_id')::uuid,
    p->>'p_notas',
    auth.uid(),
    v_key
  )
  returning id into movement;

  update public.productos
  set stock_actual = stock_actual + qty,
      updated_at = now()
  where id = product
  returning sync_version into version;

  return private_sync.record_critical_result(
    v_key,
    'inventory',
    p,
    jsonb_build_object(
      'status', 'confirmed',
      'movement_id', movement,
      'product_version', version
    )
  );
end;
$$;

-- Repetir el revocado explícitamente: el libro se modifica solo dentro de la RPC.
revoke all on function private_sync.inventory_command(jsonb)
  from public, anon, authenticated;
revoke insert, update, delete on public.movimientos_inventario
  from public, anon, authenticated;
grant select on public.movimientos_inventario to authenticated;

drop policy if exists movimientos_inventario_staff_insert
  on public.movimientos_inventario;
drop policy if exists movimientos_inventario_staff_select
  on public.movimientos_inventario;
create policy movimientos_inventario_manager_select
  on public.movimientos_inventario
  for select
  to authenticated
  using (coalesce(public.auth_role(), '') in ('admin', 'gerente'));

drop policy if exists products_staff_read_boundary on public.productos;
create policy products_staff_read_boundary
  on public.productos
  as restrictive
  for select
  to authenticated
  using (coalesce(public.auth_role(), '') in ('admin', 'gerente'));

drop policy if exists productos_staff_insert on public.productos;
create policy productos_staff_insert
  on public.productos
  for insert
  to authenticated
  with check (coalesce(public.auth_role(), '') in ('admin', 'gerente'));

drop policy if exists productos_staff_update on public.productos;
create policy productos_staff_update
  on public.productos
  for update
  to authenticated
  using (coalesce(public.auth_role(), '') in ('admin', 'gerente'))
  with check (coalesce(public.auth_role(), '') in ('admin', 'gerente'));

comment on function private_sync.inventory_command(jsonb) is
  'Inventory command for active admin/manager roles; validates idempotency, stock version and append-only movement.';
