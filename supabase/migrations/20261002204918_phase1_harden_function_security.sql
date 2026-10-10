-- Endurecimiento posterior a los advisors de Supabase.

alter function public.auth_role() set search_path = public, auth, extensions;
alter function public.decrementar_stock(uuid, integer) set search_path = public, auth, extensions;
alter function public.realizar_corte_caja(date, uuid) set search_path = public, auth, extensions;
alter function public.registrar_visita_cliente(uuid) set search_path = public, auth, extensions;
alter function public.set_user_role(text, text) set search_path = public, auth, extensions;
alter function public.handle_new_user_role() set search_path = public, auth, extensions;

-- Las funciones internas no deben ser endpoints RPC anónimos.
revoke execute on function public.auth_role() from anon;
revoke execute on function public.handle_new_user_role() from anon, authenticated;
revoke execute on function public.set_user_role(text, text) from anon;
revoke execute on function public.decrementar_stock(uuid, integer) from anon;
revoke execute on function public.realizar_corte_caja(date, uuid) from anon;
revoke execute on function public.registrar_visita_cliente(uuid) from anon;

-- La función de cambio de rol solo se expone a usuarios autenticados;
-- el cuerpo valida además que sean admin.
grant execute on function public.set_user_role(text, text) to authenticated;
