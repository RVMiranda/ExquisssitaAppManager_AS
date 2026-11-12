-- Migración: Restringir que los usuarios administradores se registren o aparezcan como empleados de piso
-- Los administradores (dueños/gerencia) gestionan el personal y nómina, pero no son empleados de piso.

-- 1. Función y trigger para impedir inserción o vinculación de administradores en empleados
create or replace function public.check_empleado_not_admin()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.user_id is not null then
    if exists (select 1 from public.user_roles where user_id = new.user_id and role = 'admin') then
      raise exception 'Los administradores no pueden registrarse como empleados de piso' using errcode = '22023';
    end if;
  end if;
  return new;
end;
$$;

revoke all on function public.check_empleado_not_admin() from public;
grant execute on function public.check_empleado_not_admin() to authenticated, service_role;

drop trigger if exists trg_check_empleado_not_admin on public.empleados;
create trigger trg_check_empleado_not_admin
  before insert or update of user_id on public.empleados
  for each row execute function public.check_empleado_not_admin();

-- 2. Función y trigger para eliminar automáticamente registros de empleados si el rol pasa a ser admin
create or replace function public.handle_admin_role_assigned()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.role = 'admin' then
    delete from public.empleados where user_id = new.user_id;
  end if;
  return new;
end;
$$;

revoke all on function public.handle_admin_role_assigned() from public;
grant execute on function public.handle_admin_role_assigned() to authenticated, service_role;

drop trigger if exists trg_admin_role_assigned on public.user_roles;
create trigger trg_admin_role_assigned
  after insert or update of role on public.user_roles
  for each row execute function public.handle_admin_role_assigned();

-- 3. Limpieza de seguridad: asegurar que ningún usuario con rol 'admin' permanezca en empleados
delete from public.empleados
where user_id in (select user_id from public.user_roles where role = 'admin');
