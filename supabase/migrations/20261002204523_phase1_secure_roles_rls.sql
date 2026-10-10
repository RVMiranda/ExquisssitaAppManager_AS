-- Fase 1: fuente de verdad segura para autorización administrativa.
-- No depende de raw_user_meta_data/user_metadata, que el usuario puede editar.

create table if not exists public.user_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null check (role in ('admin', 'empleado')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.user_roles enable row level security;

create or replace function public.auth_role()
returns text
language sql stable security definer
set search_path = public
as $$
  select coalesce((select role from public.user_roles where user_id = (select auth.uid())), '');
$$;

revoke all on function public.auth_role() from public;
grant execute on function public.auth_role() to authenticated;

insert into public.user_roles (user_id, role)
select id,
       case when raw_user_meta_data->>'rol' = 'admin' then 'admin' else 'empleado' end
from auth.users
on conflict (user_id) do nothing;

drop policy if exists user_roles_self_select on public.user_roles;
create policy user_roles_self_select on public.user_roles
  for select to authenticated
  using (user_id = (select auth.uid()) or public.auth_role() = 'admin');

create or replace function public.set_user_role(target_email text, target_role text)
returns text
language plpgsql security definer
set search_path = public
as $$
declare target_id uuid;
begin
  if public.auth_role() <> 'admin' then
    raise exception 'permiso_denegado' using errcode = '42501';
  end if;
  if target_role not in ('admin', 'empleado') then
    raise exception 'rol_invalido' using errcode = '22023';
  end if;
  select id into target_id from auth.users where email = target_email;
  if target_id is null then return 'Usuario no encontrado'; end if;
  insert into public.user_roles(user_id, role) values (target_id, target_role)
  on conflict (user_id) do update set role = excluded.role, updated_at = now();
  update auth.users
    set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb)
      || jsonb_build_object('rol', target_role)
    where id = target_id;
  return 'Rol actualizado';
end;
$$;

revoke all on function public.set_user_role(text, text) from public;
grant execute on function public.set_user_role(text, text) to authenticated;

create or replace function public.handle_new_user_role()
returns trigger language plpgsql security definer
set search_path = public
as $$
begin
  insert into public.user_roles(user_id, role)
  values (new.id, 'empleado') on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_set_role on auth.users;
create trigger on_auth_user_created_set_role after insert on auth.users
for each row execute function public.handle_new_user_role();

revoke all on function public.handle_new_user_role() from public;

-- Las RPC financieras/stock/lealtad son invocables solo por usuarios autenticados.
revoke execute on function public.decrementar_stock(uuid, integer) from public, anon;
grant execute on function public.decrementar_stock(uuid, integer) to authenticated;
revoke execute on function public.realizar_corte_caja(date, uuid) from public, anon;
grant execute on function public.realizar_corte_caja(date, uuid) to authenticated;
revoke execute on function public.registrar_visita_cliente(uuid) from public, anon;
grant execute on function public.registrar_visita_cliente(uuid) to authenticated;
