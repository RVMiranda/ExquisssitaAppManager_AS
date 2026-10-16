-- Promotion content is managed by active admins and managers in the manager app.
-- Keep the public read policy for the customer-facing promotions catalogue.
alter table public.promociones
  add column if not exists terminos_condiciones text;

drop policy if exists promociones_admin_insert on public.promociones;
drop policy if exists promociones_staff_insert on public.promociones;
create policy promociones_staff_insert
  on public.promociones
  for insert
  to authenticated
  with check (public.auth_role() in ('admin', 'gerente'));

drop policy if exists promociones_admin_update on public.promociones;
drop policy if exists promociones_staff_update on public.promociones;
create policy promociones_staff_update
  on public.promociones
  for update
  to authenticated
  using (public.auth_role() in ('admin', 'gerente'))
  with check (public.auth_role() in ('admin', 'gerente'));

comment on column public.promociones.terminos_condiciones is
  'Condiciones textuales de uso de la promoción, por ejemplo: Solo válida en consumo en sucursal.';
