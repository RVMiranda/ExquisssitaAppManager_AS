-- Correct order concurrency and staff access without bypassing RLS from Android.
-- Role checks distinguish active managers from ordinary employees; only RPCs mutate these tables.

-- The business rule permits several open orders per table; order UUIDs and command receipts provide idempotency.
DROP INDEX IF EXISTS public.ordenes_una_abierta_por_mesa;

-- Read access is staff-authenticated and role-scoped. All mutations go through audited SECURITY DEFINER RPCs.
ALTER TABLE public.mesas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ordenes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.detalle_orden ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transacciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pago_detalles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS mesas_admin_delete ON public.mesas;
DROP POLICY IF EXISTS mesas_public_select ON public.mesas;
DROP POLICY IF EXISTS mesas_staff_insert ON public.mesas;
DROP POLICY IF EXISTS mesas_staff_update ON public.mesas;
CREATE POLICY mesas_staff_select ON public.mesas
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));

DROP POLICY IF EXISTS ordenes_admin_delete ON public.ordenes;
DROP POLICY IF EXISTS ordenes_clientes_select_own ON public.ordenes;
DROP POLICY IF EXISTS ordenes_staff_insert ON public.ordenes;
DROP POLICY IF EXISTS ordenes_staff_select ON public.ordenes;
DROP POLICY IF EXISTS ordenes_staff_update ON public.ordenes;
CREATE POLICY ordenes_clientes_select_own ON public.ordenes
  FOR SELECT TO authenticated
  USING (cliente_id IN (SELECT c.id FROM public.clientes AS c WHERE c.user_id = (SELECT auth.uid())));
CREATE POLICY ordenes_staff_select ON public.ordenes
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));

DROP POLICY IF EXISTS detalle_orden_clientes_select_own ON public.detalle_orden;
DROP POLICY IF EXISTS detalle_orden_staff_all ON public.detalle_orden;
CREATE POLICY detalle_orden_clientes_select_own ON public.detalle_orden
  FOR SELECT TO authenticated
  USING (orden_id IN (
    SELECT o.id FROM public.ordenes AS o
    JOIN public.clientes AS c ON c.id = o.cliente_id
    WHERE c.user_id = (SELECT auth.uid())
  ));
CREATE POLICY detalle_orden_staff_select ON public.detalle_orden
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));

DROP POLICY IF EXISTS transacciones_staff_insert ON public.transacciones;
DROP POLICY IF EXISTS transacciones_staff_select ON public.transacciones;
CREATE POLICY transacciones_staff_select ON public.transacciones
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));

DROP POLICY IF EXISTS pago_detalles_staff_insert ON public.pago_detalles;
DROP POLICY IF EXISTS pago_detalles_staff_select ON public.pago_detalles;
CREATE POLICY pago_detalles_staff_select ON public.pago_detalles
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));

REVOKE ALL PRIVILEGES ON TABLE public.mesas, public.ordenes, public.detalle_orden,
  public.transacciones, public.pago_detalles FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.mesas, public.ordenes, public.detalle_orden,
  public.transacciones, public.pago_detalles TO authenticated;

create or replace function public.registrar_pago(p_orden_id uuid,p_monto bigint,p_metodo_pago text,p_idempotency_key uuid,p_asignaciones jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  req jsonb := jsonb_build_object('order',p_orden_id,'amount',p_monto,'method',p_metodo_pago,'allocations',p_asignaciones);
  receipt private_sync.critical_receipts%rowtype;
  prior public.transacciones%rowtype;
  order_state text; total bigint; paid bigint; line_paid bigint; allocated bigint := 0;
  item jsonb; detail public.detalle_orden%rowtype; tx uuid; amount bigint; qty integer;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','gerente') then raise exception 'not_authorized' using errcode='42501'; end if;
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

create or replace function private_sync.inventory_command(p jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_key uuid := (p->>'p_idempotency_key')::uuid; product uuid := (p->>'p_producto_id')::uuid;
  qty integer := (p->>'p_cantidad')::integer; kind text := p->>'p_tipo';
  expected bigint := (p->>'p_expected_version')::bigint;
  receipt private_sync.critical_receipts%rowtype; row public.productos%rowtype; movement uuid; version bigint;
  original public.movimientos_inventario%rowtype; restored bigint;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','gerente') then raise exception 'not_authorized' using errcode='42501'; end if;
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

create or replace function public.sync_execute(p_entity text,p_entity_id text,p_operation text,
  p_payload jsonb,p_key uuid,p_expected_version bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare req jsonb; previous private_sync.receipts%rowtype; response jsonb;
  current_row jsonb; cols text; vals text; assignments text; field text;
  exists_row boolean; order_id uuid; order_state text; allocation jsonb;
  existing_payment public.transacciones%rowtype;
begin
  if auth.uid() is null or public.auth_role() not in ('admin','gerente') then
    raise exception 'not_authorized' using errcode='42501';
  end if;
  if p_key is null or p_entity is null or p_entity_id is null or p_operation is null
    or p_payload is null or jsonb_typeof(p_payload)<>'object' or p_expected_version is null then
    raise exception 'invalid_command' using errcode='22023';
  end if;
  perform pg_advisory_xact_lock(78645231);
  req:=jsonb_build_object('entity',p_entity,'id',p_entity_id,'operation',p_operation,
    'payload',p_payload,'expected',p_expected_version);
  select * into previous from private_sync.receipts where key=p_key;
  if found then
    if previous.actor_id<>auth.uid() then raise exception 'not_authorized' using errcode='42501'; end if;
    if previous.request<>req then raise exception 'idempotency_payload_mismatch' using errcode='22023'; end if;
    return previous.result;
  end if;
  if p_entity='registrar_pago' then
    if p_operation<>'INSERT' or (p_payload->>'p_idempotency_key')::uuid is distinct from p_key then
      raise exception 'invalid_command' using errcode='22023';
    end if;
    order_id:=(p_payload->>'p_orden_id')::uuid;
    select * into existing_payment from public.transacciones where idempotency_key=p_key;
    if found then
      -- A legacy write lacks a receipt certifying allocations/actor. Require
      -- review; never infer that a different payload is the same payment.
      raise exception 'legacy_payment_requires_review' using errcode='22023';
    end if;
    select estado into order_state from public.ordenes where id=order_id for update;
    if order_state is distinct from 'Abierta' or jsonb_typeof(p_payload->'p_asignaciones') is distinct from 'array'
      or (p_payload->>'p_monto')::bigint is null then
      raise exception 'invalid_payment' using errcode='22023';
    end if;
    if exists(select 1 from jsonb_array_elements(p_payload->'p_asignaciones') a group by a->>'detalle_orden_id' having count(*)>1) then
      raise exception 'duplicate_allocation' using errcode='22023';
    end if;
    for allocation in select * from jsonb_array_elements(p_payload->'p_asignaciones') loop
      if (allocation->>'cantidad')::integer is null or (allocation->>'monto')::bigint is null
        or not exists(select 1 from public.detalle_orden where id=(allocation->>'detalle_orden_id')::uuid
          and orden_id=order_id and estado_pago<>'Cancelado') then
        raise exception 'invalid_allocation' using errcode='22023';
      end if;
    end loop;
    response:=public.registrar_pago((p_payload->>'p_orden_id')::uuid,
      (p_payload->>'p_monto')::bigint,p_payload->>'p_metodo_pago',p_key,p_payload->'p_asignaciones');
  elsif p_entity='registrar_movimiento_inventario' then
    if p_operation<>'INSERT' or (p_payload->>'p_idempotency_key')::uuid is distinct from p_key then
      raise exception 'invalid_command' using errcode='22023';
    end if;
    if exists(select 1 from public.movimientos_inventario where idempotency_key=p_key) then
      raise exception 'legacy_movement_requires_review' using errcode='22023';
    end if;
    response:=public.registrar_movimiento_inventario((p_payload->>'p_producto_id')::uuid,
      (p_payload->>'p_cantidad')::integer,p_payload->>'p_tipo',p_key,
      (p_payload->>'p_referencia_id')::uuid,p_payload->>'p_notas');
  elsif p_entity='realizar_corte_caja_seguro' then
    if p_operation<>'INSERT' then raise exception 'invalid_command' using errcode='22023'; end if;
    select to_jsonb(r) into response from public.realizar_corte_caja_seguro((p_payload->>'p_fecha')::date) r;
  else
    if p_entity not in ('mesas','ordenes','detalle_orden','promociones','empleados','clientes','productos','historial_pagos_empleados')
      or p_operation not in ('INSERT','UPDATE','DELETE') then
      raise exception 'unsupported_command' using errcode='22023';
    end if;
    if p_entity='historial_pagos_empleados' and p_operation<>'INSERT' then
      raise exception 'append_only' using errcode='42501';
    end if;
    if p_entity in ('empleados','historial_pagos_empleados','promociones') and public.auth_role()<>'admin' then
      raise exception 'not_authorized' using errcode='42501';
    end if;
    if p_operation='DELETE' and (public.auth_role()<>'admin' or p_entity='detalle_orden') then
      raise exception 'not_authorized' using errcode='42501';
    end if;
    if p_payload ?| array['sync_version','created_at','updated_at','visitas_totales','puntos_lealtad','qr_token_hash','stock_actual'] then
      raise exception 'server_owned_fields' using errcode='42501';
    end if;
    execute format('select to_jsonb(t) from public.%I t where id::text=$1 for update',p_entity)
      into current_row using p_entity_id;
    exists_row:=current_row is not null;
    if p_entity='ordenes' then
      if p_payload->>'estado'='Cerrada' or current_row->>'estado' in ('Cerrada','Cancelada')
        or exists(select 1 from public.transacciones where orden_id=p_entity_id::uuid) then
        raise exception 'financial_order_requires_review' using errcode='42501';
      end if;
    elsif p_entity='detalle_orden' then
      order_id:=coalesce((current_row->>'orden_id')::uuid,(p_payload->>'orden_id')::uuid);
      select estado into order_state from public.ordenes where id=order_id for update;
      if order_state is distinct from 'Abierta'
        or p_payload ? 'estado_pago'
        or (exists_row and p_payload ? 'orden_id' and p_payload->>'orden_id'<>current_row->>'orden_id')
        or exists(select 1 from public.pago_detalles where detalle_orden_id=p_entity_id::uuid) then
        raise exception 'financial_detail_requires_review' using errcode='42501';
      end if;
    end if;
    if exists_row and (current_row->>'sync_version')::bigint<>p_expected_version
      or not exists_row and p_expected_version<>0 then
      raise exception 'version_conflict' using errcode='P0004';
    end if;
    if p_operation='DELETE' then
      if not exists_row then raise exception 'version_conflict' using errcode='P0004'; end if;
      execute format('delete from public.%I where id::text=$1 returning jsonb_build_object(''version'',sync_version+1)',p_entity)
        into response using p_entity_id;
    else
      if p_payload ? 'id' and p_payload->>'id'<>p_entity_id then
        raise exception 'invalid_identity' using errcode='22023';
      end if;
      p_payload:=p_payload||jsonb_build_object('id',p_entity_id);
      cols:='';vals:='';assignments:='';
      for field in select jsonb_object_keys(p_payload) loop
        if not exists(select 1 from pg_catalog.pg_attribute where attrelid=to_regclass('public.'||p_entity) and attname=field and attnum>0 and not attisdropped) then
          raise exception 'invalid_field' using errcode='22023';
        end if;
        cols:=cols||format('%I,',field);vals:=vals||format('r.%I,',field);
        if field<>'id' then assignments:=assignments||format('%I=r.%I,',field,field); end if;
      end loop;
      if exists_row then
        if p_entity='historial_pagos_empleados' then raise exception 'append_only' using errcode='42501'; end if;
        execute format('update public.%I t set %s from jsonb_populate_record(null::public.%I,$1) r where t.id::text=$2 returning jsonb_build_object(''version'',t.sync_version)',
          p_entity,rtrim(assignments,','),p_entity) into response using p_payload,p_entity_id;
      else
        if p_operation='UPDATE' then raise exception 'version_conflict' using errcode='P0004'; end if;
        execute format('insert into public.%I(%s) select %s from jsonb_populate_record(null::public.%I,$1) r returning jsonb_build_object(''version'',sync_version)',
          p_entity,rtrim(cols,','),rtrim(vals,','),p_entity) into response using p_payload;
      end if;
    end if;
    if response is null then raise exception 'not_authorized' using errcode='42501'; end if;
  end if;
  insert into private_sync.receipts(key,actor_id,request,result) values(p_key,auth.uid(),req,response);
  return response;
end;
$$;

create or replace function public.sync_execute_v2(p_entity text,p_entity_id text,p_operation text,p_payload jsonb,p_key uuid,p_expected_version bigint default 0)
returns jsonb language plpgsql security definer set search_path='' as $$
declare req jsonb; previous private_sync.receipts%rowtype; response jsonb;
begin
  if auth.uid() is null or coalesce(public.auth_role(),'') not in ('admin','gerente') then raise exception 'not_authorized' using errcode='42501'; end if;
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

create or replace function public.sync_pull(p_after bigint default 0,p_watermark bigint default null,p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path='' as $$
declare role_name text; upper_bound bigint; next_cursor bigint; items jsonb;
begin
  role_name:=public.auth_role();
  if auth.uid() is null or coalesce(role_name,'') not in ('admin','gerente') then
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