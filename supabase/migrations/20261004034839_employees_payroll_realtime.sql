do $$
begin
  if exists (
    select 1 from pg_catalog.pg_publication where pubname = 'supabase_realtime'
  ) and not exists (
    select 1 from pg_catalog.pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'empleados'
  ) then
    alter publication supabase_realtime add table public.empleados;
  end if;

  if exists (
    select 1 from pg_catalog.pg_publication where pubname = 'supabase_realtime'
  ) and not exists (
    select 1 from pg_catalog.pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'historial_pagos_empleados'
  ) then
    alter publication supabase_realtime add table public.historial_pagos_empleados;
  end if;
end
$$;
