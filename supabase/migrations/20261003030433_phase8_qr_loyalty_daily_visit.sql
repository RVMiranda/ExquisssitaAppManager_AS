create extension if not exists pgcrypto with schema extensions;

alter table public.clientes
  add column if not exists qr_token_hash text;

create unique index if not exists clientes_qr_token_hash_uidx
  on public.clientes(qr_token_hash)
  where qr_token_hash is not null;

alter table public.visitas_clientes
  add column if not exists usuario_id uuid references auth.users(id) on delete restrict;

create unique index if not exists visitas_clientes_usuario_fecha_uidx
  on public.visitas_clientes(usuario_id, fecha)
  where usuario_id is not null;

comment on column public.clientes.qr_token_hash is
  'Hash SHA-256 de un token QR opaco; nunca se almacena el token en claro.';
comment on column public.visitas_clientes.usuario_id is
  'Identidad de la cuenta del cliente; base de la regla una visita diaria por usuario.';

create or replace function public.registrar_visita_qr(p_qr_token text)
returns jsonb
language plpgsql
security invoker
set search_path = public, extensions
as $$
declare
  v_cliente record;
  v_fecha date := (now() at time zone 'America/Mexico_City')::date;
  v_visita_id uuid;
  v_puntos integer := 10;
begin
  if public.auth_role() not in ('admin', 'empleado') then
    raise exception 'not_authorized' using errcode = '42501';
  end if;
  if p_qr_token is null or length(p_qr_token) < 32 or length(p_qr_token) > 512 then
    raise exception 'invalid_qr_token';
  end if;

  select c.id, c.user_id, c.visitas_totales, c.puntos_lealtad
    into v_cliente
  from public.clientes c
  where c.qr_token_hash = encode(extensions.digest(p_qr_token, 'sha256'), 'hex')
  for update;
  if not found or v_cliente.user_id is null then
    raise exception 'qr_customer_not_found';
  end if;

  insert into public.visitas_clientes (cliente_id, usuario_id, fecha, puntos_otorgados, registrado_por)
    values (v_cliente.id, v_cliente.user_id, v_fecha, v_puntos, auth.uid())
    on conflict (usuario_id, fecha) where usuario_id is not null do nothing
    returning id into v_visita_id;

  if v_visita_id is null then
    return jsonb_build_object(
      'registered', false,
      'already_registered', true,
      'cliente_id', v_cliente.id,
      'fecha', v_fecha
    );
  end if;

  update public.clientes
  set visitas_totales = visitas_totales + 1,
      puntos_lealtad = puntos_lealtad + v_puntos,
      updated_at = now()
  where id = v_cliente.id;

  return jsonb_build_object(
    'registered', true,
    'already_registered', false,
    'cliente_id', v_cliente.id,
    'fecha', v_fecha,
    'puntos_otorgados', v_puntos,
    'visitas_totales', v_cliente.visitas_totales + 1,
    'puntos_lealtad', v_cliente.puntos_lealtad + v_puntos
  );
end;
$$;

revoke all on function public.registrar_visita_qr(text) from public, anon;
grant execute on function public.registrar_visita_qr(text) to authenticated;

comment on function public.registrar_visita_qr(text) is
  'Registra como máximo una visita y puntos por usuario y fecha local de negocio.';
