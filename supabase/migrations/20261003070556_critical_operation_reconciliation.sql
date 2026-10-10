-- Incremental, local proposal. No destructive DDL or historical rewrites.
-- Money remains BIGINT cents. Intent receipts include accepted AND rejected outcomes.
create table private_sync.critical_receipts (
  key uuid primary key, actor_id uuid not null references auth.users(id),
  entity text not null, request jsonb not null, result jsonb not null,
  created_at timestamptz not null default now()
);
alter table private_sync.critical_receipts enable row level security;
revoke all on private_sync.critical_receipts from public, anon, authenticated;

create or replace function private_sync.record_critical_result(p_key uuid,p_entity text,p_request jsonb,p_result jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
  insert into private_sync.critical_receipts(key,actor_id,entity,request,result)
    values(p_key,auth.uid(),p_entity,p_request,p_result);
  return p_result;
end $$;
revoke all on function private_sync.record_critical_result(uuid,text,jsonb,jsonb) from public,anon,authenticated;

create or replace function public.registrar_pago(p_orden_id uuid,p_monto bigint,p_metodo_pago text,p_idempotency_key uuid,p_asignaciones jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  req jsonb := jsonb_build_object('order',p_orden_id,'amount',p_monto,'method',p_metodo_pago,'allocations',p_asignaciones);
  receipt private_sync.critical_receipts%rowtype;
  prior public.transacciones%rowtype;
  order_state text; total bigint; paid bigint; line_paid bigint; allocated bigint := 0;
  item jsonb; detail public.detalle_orden%rowtype; tx uuid; amount bigint; qty integer;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','empleado') then raise exception 'not_authorized' using errcode='42501'; end if;
  if p_idempotency_key is null or p_orden_id is null or p_monto is null or p_monto<=0
    or p_metodo_pago is null or p_metodo_pago not in ('Efectivo','Tarjeta','Transferencia','Otro')
    or jsonb_typeof(p_asignaciones) is distinct from 'array' or jsonb_array_length(p_asignaciones)=0 then
    raise exception 'invalid_command' using errcode='22023';
  end if;
  -- Same ordering as sync change-feed triggers; serializes receipts and mutations.
  perform pg_catalog.pg_advisory_xact_lock(78645231);
  select * into receipt from private_sync.critical_receipts where key=p_idempotency_key;
  if found then
    if receipt.actor_id<>auth.uid() or receipt.entity<>'payment' or receipt.request<>req then raise exception 'idempotency_mismatch' using errcode='22023'; end if;
    return receipt.result;
  end if;
  select * into prior from public.transacciones where idempotency_key=p_idempotency_key;
  if found then raise exception 'legacy_payment_requires_review' using errcode='22023'; end if;
  select estado into order_state from public.ordenes where id=p_orden_id for update;
  if order_state is distinct from 'Abierta' then
    return private_sync.record_critical_result(p_idempotency_key,'payment',req,jsonb_build_object('status','rejected','reason','order_unavailable'));
  end if;
  if exists(select 1 from jsonb_array_elements(p_asignaciones) a group by a->>'detalle_orden_id' having count(*)>1) then raise exception 'duplicate_allocation' using errcode='22023'; end if;
  select coalesce(sum(d.cantidad::bigint*d.precio_unitario),0) into total from public.detalle_orden d where d.orden_id=p_orden_id and d.estado_pago<>'Cancelado';
  select coalesce(sum(t.monto),0) into paid from public.transacciones t where t.orden_id=p_orden_id;
  if p_monto>total-paid then
    return private_sync.record_critical_result(p_idempotency_key,'payment',req,jsonb_build_object('status','rejected','reason','overpayment'));
  end if;
  for item in select a from jsonb_array_elements(p_asignaciones) a order by a->>'detalle_orden_id' loop
    qty:=(item->>'cantidad')::integer; amount:=(item->>'monto')::bigint;
    select * into detail from public.detalle_orden where id=(item->>'detalle_orden_id')::uuid and orden_id=p_orden_id and estado_pago<>'Cancelado' for update;
    if not found or qty is null or amount is null or qty<=0 or qty>detail.cantidad or amount<=0 or amount>qty::bigint*detail.precio_unitario then raise exception 'invalid_allocation' using errcode='22023'; end if;
    select coalesce(sum(pd.monto),0) into line_paid from public.pago_detalles pd where pd.detalle_orden_id=detail.id;
    -- Quantity describes this allocation, not cumulative ownership of units.
    -- A 40-cent and a 60-cent abono may both reference the same 100-cent unit.
    if amount>detail.cantidad::bigint*detail.precio_unitario-line_paid then
      return private_sync.record_critical_result(p_idempotency_key,'payment',req,jsonb_build_object('status','rejected','reason','detail_overpayment'));
    end if;
    allocated:=allocated+amount;
  end loop;
  if allocated<>p_monto then raise exception 'allocation_amount_mismatch' using errcode='22023'; end if;
  insert into public.transacciones(orden_id,monto,metodo_pago,idempotency_key) values(p_orden_id,p_monto,p_metodo_pago,p_idempotency_key) returning id into tx;
  insert into public.pago_detalles(transaccion_id,detalle_orden_id,cantidad,monto)
    select tx,(a->>'detalle_orden_id')::uuid,(a->>'cantidad')::integer,(a->>'monto')::bigint from jsonb_array_elements(p_asignaciones) a;
  update public.detalle_orden d set estado_pago='Pagado',updated_at=now() where d.orden_id=p_orden_id and d.estado_pago<>'Cancelado'
    and (select coalesce(sum(pd.monto),0) from public.pago_detalles pd where pd.detalle_orden_id=d.id)>=d.cantidad::bigint*d.precio_unitario;
  if paid+p_monto=total then update public.ordenes set estado='Cerrada',fecha_cierre=now(),updated_at=now() where id=p_orden_id; end if;
  return private_sync.record_critical_result(p_idempotency_key,'payment',req,jsonb_build_object('status','confirmed','transaction_id',tx));
end $$;
revoke all on function public.registrar_pago(uuid,bigint,text,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.registrar_pago(uuid,bigint,text,uuid,jsonb) to authenticated;

create or replace function private_sync.inventory_command(p jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_key uuid := (p->>'p_idempotency_key')::uuid; product uuid := (p->>'p_producto_id')::uuid;
  qty integer := (p->>'p_cantidad')::integer; kind text := p->>'p_tipo';
  expected bigint := (p->>'p_expected_version')::bigint;
  receipt private_sync.critical_receipts%rowtype; row public.productos%rowtype; movement uuid; version bigint;
  original public.movimientos_inventario%rowtype; restored bigint;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','empleado') then raise exception 'not_authorized' using errcode='42501'; end if;
  if v_key is null or product is null or qty is null or qty=0 or kind is null or kind not in ('entrada','venta','ajuste','cancelacion','merma')
    or (kind in ('venta','merma') and qty>0) or (kind in ('entrada','cancelacion') and qty<0) then raise exception 'invalid_command' using errcode='22023'; end if;
  perform pg_catalog.pg_advisory_xact_lock(78645231);
  select * into receipt from private_sync.critical_receipts r where r.key=v_key;
  if found then
    if receipt.actor_id<>auth.uid() or receipt.entity<>'inventory' or receipt.request<>p then raise exception 'idempotency_mismatch' using errcode='22023'; end if;
    return receipt.result;
  end if;
  if exists(select 1 from public.movimientos_inventario m where m.idempotency_key=v_key) then raise exception 'legacy_movement_requires_review' using errcode='22023'; end if;
  select * into row from public.productos where id=product for update;
  if not found or row.stock_actual is null or row.controla_inventario is distinct from true then
    return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','rejected','reason','product_unavailable'));
  end if;
  if kind='ajuste' and (public.auth_role()<>'admin' or expected is null) then raise exception 'version_required' using errcode='22023'; end if;
  if expected is not null and expected<>row.sync_version then
    return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','rejected','reason','stock_version_conflict'));
  end if;
  if kind='cancelacion' then
    select * into original from public.movimientos_inventario where id=(p->>'p_referencia_id')::uuid and producto_id=product and cantidad<0;
    if not found then
      return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','rejected','reason','invalid_compensation'));
    end if;
    select coalesce(sum(cantidad),0) into restored from public.movimientos_inventario where tipo='cancelacion' and referencia_id=original.id;
    if restored+qty>-(original.cantidad::bigint) then
      return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','rejected','reason','compensation_exceeds_original'));
    end if;
  end if;
  if qty::bigint+row.stock_actual<0 or qty::bigint+row.stock_actual>2147483647 then
    return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','rejected','reason','insufficient_stock'));
  end if;
  insert into public.movimientos_inventario(producto_id,cantidad,tipo,referencia_id,notas,creado_por,idempotency_key)
    values(product,qty,kind,(p->>'p_referencia_id')::uuid,p->>'p_notas',auth.uid(),v_key) returning id into movement;
  update public.productos set stock_actual=stock_actual+qty,updated_at=now() where id=product returning sync_version into version;
  return private_sync.record_critical_result(v_key,'inventory',p,jsonb_build_object('status','confirmed','movement_id',movement,'product_version',version));
end $$;
revoke all on function private_sync.inventory_command(jsonb) from public,anon,authenticated;

create or replace function public.registrar_movimiento_inventario(p_producto_id uuid,p_cantidad integer,p_tipo text,p_idempotency_key uuid,p_referencia_id uuid default null,p_notas text default null)
returns jsonb language sql security definer set search_path='' as $$
  select private_sync.inventory_command(jsonb_build_object('p_producto_id',p_producto_id,'p_cantidad',p_cantidad,'p_tipo',p_tipo,'p_idempotency_key',p_idempotency_key,'p_referencia_id',p_referencia_id,'p_notas',p_notas));
$$;
revoke all on function public.registrar_movimiento_inventario(uuid,integer,text,uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.registrar_movimiento_inventario(uuid,integer,text,uuid,uuid,text) to authenticated;

create function public.sync_execute_v2(p_entity text,p_entity_id text,p_operation text,p_payload jsonb,p_key uuid,p_expected_version bigint default 0)
returns jsonb language plpgsql security definer set search_path='' as $$
declare req jsonb; previous private_sync.receipts%rowtype; response jsonb;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','empleado') then raise exception 'not_authorized' using errcode='42501'; end if;
  if p_entity is null or p_payload is null or jsonb_typeof(p_payload) is distinct from 'object' then raise exception 'invalid_command' using errcode='22023'; end if;
  if p_entity not in ('registrar_pago','registrar_movimiento_inventario') then
    return public.sync_execute(p_entity,p_entity_id,p_operation,p_payload,p_key,p_expected_version);
  end if;
  if p_operation is distinct from 'INSERT' or p_key is null or p_entity_id is null or p_expected_version is null or (p_payload->>'p_idempotency_key')::uuid is distinct from p_key then raise exception 'invalid_command' using errcode='22023'; end if;
  perform pg_catalog.pg_advisory_xact_lock(78645231);
  req:=jsonb_build_object('entity',p_entity,'id',p_entity_id,'operation',p_operation,'payload',p_payload,'expected',p_expected_version);
  select * into previous from private_sync.receipts where key=p_key;
  if found then
    if previous.actor_id<>auth.uid() then raise exception 'not_authorized' using errcode='42501'; end if;
    if previous.request<>req then raise exception 'idempotency_mismatch' using errcode='22023'; end if;
    return previous.result;
  end if;
  if p_entity='registrar_pago' then
    response:=public.registrar_pago((p_payload->>'p_orden_id')::uuid,(p_payload->>'p_monto')::bigint,p_payload->>'p_metodo_pago',p_key,p_payload->'p_asignaciones');
  else response:=private_sync.inventory_command(p_payload); end if;
  insert into private_sync.receipts(key,actor_id,request,result) values(p_key,auth.uid(),req,response);
  return response;
end $$;
revoke all on function public.sync_execute_v2(text,text,text,jsonb,uuid,bigint) from public,anon,authenticated;
grant execute on function public.sync_execute_v2(text,text,text,jsonb,uuid,bigint) to authenticated;
-- Only v2 may delegate generic commands to the historical dispatcher.
revoke execute on function public.sync_execute(text,text,text,jsonb,uuid,bigint) from public,anon,authenticated;

-- Ledger records cannot be changed, even by another existing DEFINER RPC.
create function private_sync.reject_ledger_mutation() returns trigger language plpgsql set search_path='' as $$
begin raise exception 'append_only' using errcode='42501'; end $$;
revoke all on function private_sync.reject_ledger_mutation() from public,anon,authenticated;
create trigger transactions_append_only before update or delete on public.transacciones for each row execute function private_sync.reject_ledger_mutation();
create trigger allocations_append_only before update or delete on public.pago_detalles for each row execute function private_sync.reject_ledger_mutation();
create trigger inventory_append_only before update or delete on public.movimientos_inventario for each row execute function private_sync.reject_ledger_mutation();
create trigger critical_receipts_append_only before update or delete on private_sync.critical_receipts for each row execute function private_sync.reject_ledger_mutation();
create trigger transactions_no_truncate before truncate on public.transacciones for each statement execute function private_sync.reject_ledger_mutation();
create trigger allocations_no_truncate before truncate on public.pago_detalles for each statement execute function private_sync.reject_ledger_mutation();
create trigger inventory_no_truncate before truncate on public.movimientos_inventario for each statement execute function private_sync.reject_ledger_mutation();
revoke all on public.transacciones,public.pago_detalles,public.movimientos_inventario,public.productos from public,anon,authenticated;
grant select on public.transacciones,public.pago_detalles,public.movimientos_inventario,public.productos to authenticated;
create policy products_staff_read_boundary on public.productos as restrictive for select to authenticated
  using (coalesce(public.auth_role(),'') in ('admin','empleado'));
-- Historical stock functions bypass the ledger/preconditions. Disable client invocation.
revoke execute on function public.decrementar_stock(uuid,integer) from public,anon,authenticated;

comment on function public.sync_execute_v2(text,text,text,jsonb,uuid,bigint) is 'Staff-only audited critical commands; durable accepted/rejected receipts, original-key replay. No automatic financial reversals.';

-- Deny authenticated identities without an assigned role, including pull.
create or replace function public.sync_pull(p_after bigint default 0,p_watermark bigint default null,p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path='' as $$
declare role_name text; upper_bound bigint; next_cursor bigint; items jsonb;
begin
  role_name:=public.auth_role();
  if auth.uid() is null or coalesce(role_name,'') not in ('admin','empleado') then
    raise exception 'not_authorized' using errcode='42501';
  end if;
  if p_after is null or p_after<0 or p_limit is null or p_limit<1 or p_limit>200 then
    raise exception 'invalid_cursor' using errcode='22023';
  end if;
  select coalesce(max(seq),0) into upper_bound from private_sync.changes;
  if p_watermark is not null then
    if p_watermark<p_after or p_watermark>upper_bound then
      raise exception 'invalid_watermark' using errcode='22023';
    end if;
    upper_bound:=p_watermark;
  end if;
  if p_after>upper_bound then raise exception 'cursor_reset_required' using errcode='22023'; end if;
  -- Paginate over ALL seqs; filter payload visibility after choosing the page.
  -- This advances across hidden rows without leaking their contents.
  select coalesce(max(seq),upper_bound) into next_cursor from
    (select seq from private_sync.changes where seq>p_after and seq<=upper_bound order by seq limit p_limit) page;
  select coalesce(jsonb_agg(jsonb_build_object('seq',seq,'entity',entity,
    'entity_id',entity_id,'version',version,'deleted',deleted,'data',data) order by seq),'[]'::jsonb)
    into items from private_sync.changes
    where seq>p_after and seq<=next_cursor
      and (role_name='admin' or entity not in ('empleados','historial_pagos_empleados','auditoria_eventos'));
  return jsonb_build_object('changes',items,'cursor',next_cursor,
    'watermark',upper_bound,'has_more',next_cursor<upper_bound);
end;
$$;
revoke all on function public.sync_pull(bigint,bigint,integer) from public,anon,authenticated;
grant execute on function public.sync_pull(bigint,bigint,integer) to authenticated;


