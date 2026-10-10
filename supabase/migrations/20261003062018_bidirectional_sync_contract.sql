-- Incremental contract only. Review/test on a disposable database before deploy.
-- No historical migration changes, DROP or remote execution.
-- DEFINER RPCs use explicit staff/admin authorization; private receipts/feed
-- cannot be granted to clients. This intentionally replaces RLS in this boundary.
create schema if not exists private_sync;
revoke all on schema private_sync from public, anon, authenticated;

create table private_sync.changes (
  seq bigint generated always as identity primary key,
  entity text not null,
  entity_id text not null,
  version bigint not null,
  deleted boolean not null,
  data jsonb not null,
  committed_at timestamptz not null default clock_timestamp()
);
alter table private_sync.changes enable row level security;
revoke all on private_sync.changes from public,anon,authenticated;
revoke all on sequence private_sync.changes_seq_seq from public,anon,authenticated;
-- No client grants/policies: feed is exposed only by the checked read RPC.
create index sync_changes_entity_id on private_sync.changes(entity,entity_id,seq);

create table private_sync.receipts (
  key uuid primary key,
  actor_id uuid not null references auth.users(id) on delete restrict,
  request jsonb not null,
  result jsonb not null,
  created_at timestamptz not null default now()
);
alter table private_sync.receipts enable row level security;
revoke all on private_sync.receipts from public,anon,authenticated;

create function private_sync.version_row() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  -- All writers use the same xact lock, including old RPCs and direct DML.
  -- Held through COMMIT: a seq watermark cannot skip a late-committing seq.
  perform pg_advisory_xact_lock(78645231);
  if tg_op='INSERT' then
    if exists(select 1 from private_sync.changes where entity=tg_table_name and entity_id=new.id::text and deleted) then
      raise exception 'deleted_identity_cannot_be_reused' using errcode='22023';
    end if;
    new.sync_version:=1;
  else new.sync_version:=old.sync_version+1; end if;
  return new;
end;
$$;

-- Realtime is an optional invalidation accelerator; polling recovers gaps.
-- Never publish private_sync payloads or replay receipts.
do $$
declare entity text;
begin
  if exists(select 1 from pg_catalog.pg_publication where pubname='supabase_realtime') then
    foreach entity in array array['mesas','ordenes','detalle_orden','productos','promociones','transacciones','movimientos_inventario'] loop
      if not exists(select 1 from pg_catalog.pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=entity) then
        execute format('alter publication supabase_realtime add table public.%I',entity);
      end if;
    end loop;
  end if;
end;
$$;
create function private_sync.capture_change() returns trigger
language plpgsql security definer set search_path='' as $$
declare image jsonb; v bigint;
begin
  perform pg_advisory_xact_lock(78645231);
  if tg_op='DELETE' then image:=to_jsonb(old); v:=old.sync_version+1;
  else image:=to_jsonb(new); v:=new.sync_version; end if;
  insert into private_sync.changes(entity,entity_id,version,deleted,data)
    values(tg_table_name,image->>'id',v,tg_op='DELETE',image);
  return null;
end;
$$;
revoke all on function private_sync.version_row() from public,anon,authenticated;
revoke all on function private_sync.capture_change() from public,anon,authenticated;

do $$
declare entity text;
begin
  perform pg_advisory_xact_lock(78645231);
  foreach entity in array array['clientes','empleados','productos','mesas','promociones',
    'ordenes','detalle_orden','transacciones','pago_detalles','movimientos_inventario',
    'visitas_clientes','auditoria_eventos','historial_pagos_empleados','venta_diaria'] loop
    execute format('alter table public.%I add column sync_version bigint not null default 1 check(sync_version>0)',entity);
    execute format('create trigger sync_version before insert or update on public.%I for each row execute function private_sync.version_row()',entity);
    execute format('create trigger sync_capture after insert or update or delete on public.%I for each row execute function private_sync.capture_change()',entity);
    execute format('insert into private_sync.changes(entity,entity_id,version,deleted,data) select %L,id::text,sync_version,false,to_jsonb(t) from public.%I t',entity,entity);
  end loop;
end;
$$;

create function public.sync_pull(p_after bigint default 0,p_watermark bigint default null,p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path='' as $$
declare role_name text; upper_bound bigint; next_cursor bigint; items jsonb;
begin
  role_name:=public.auth_role();
  if auth.uid() is null or role_name not in ('admin','empleado') then
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

create function public.sync_execute(p_entity text,p_entity_id text,p_operation text,
  p_payload jsonb,p_key uuid,p_expected_version bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare req jsonb; previous private_sync.receipts%rowtype; response jsonb;
  current_row jsonb; cols text; vals text; assignments text; field text;
  exists_row boolean; order_id uuid; order_state text; allocation jsonb;
  existing_payment public.transacciones%rowtype;
begin
  if auth.uid() is null or public.auth_role() not in ('admin','empleado') then
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
revoke all on function public.sync_execute(text,text,text,jsonb,uuid,bigint) from public,anon,authenticated;
grant execute on function public.sync_execute(text,text,text,jsonb,uuid,bigint) to authenticated;
comment on function public.sync_pull(bigint,bigint,integer) is
  'Staff-only change feed; employee payload excludes payroll/employees/audit. Tombstones retained without TTL.';
comment on function public.sync_execute(text,text,text,jsonb,uuid,bigint) is
  'Checked staff/admin commands, optimistic CAS and private immutable receipts; DEFINER required for receipt writes. Existing direct DML grants require separate hardening.';
