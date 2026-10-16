-- Keep public catalogue reads while limiting Data API writes to authenticated
-- callers. RLS further limits insert/update to active admins and managers and
-- delete to admins.
revoke all privileges on table public.promociones from anon, authenticated;
grant select on table public.promociones to anon;
grant select, insert, update, delete on table public.promociones to authenticated;
