-- Grant insert and update privileges on catalog product descriptive fields to authenticated staff.
-- Product physical stock (stock_actual) and sync_version remain restricted to the atomic inventory RPC.

grant insert (id, nombre, precio, categoria, controla_inventario, stock_minimo, activo) on public.productos to authenticated;
grant update (nombre, precio, categoria, controla_inventario, stock_minimo, activo) on public.productos to authenticated;
