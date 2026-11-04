-- Release 10 / Customer App Fix: Grant SELECT on deleted_at and align customer RLS subqueries.
-- When deleted_at was introduced in 20261023030503_customer_account_deletion_privacy.sql,
-- it was added to public.clientes without granting SELECT to 'authenticated'.
-- Furthermore, RLS policies used EXISTS (SELECT 1 FROM public.clientes c ...) which fails
-- under column-level privilege enforcement without table-level SELECT on clientes.

GRANT SELECT (deleted_at) ON TABLE public.clientes TO authenticated;

DROP POLICY IF EXISTS canjes_recompensas_customer_select ON public.canjes_recompensas;
CREATE POLICY canjes_recompensas_customer_select ON public.canjes_recompensas
  FOR SELECT TO authenticated
  USING (cliente_id IN (
    SELECT c.id FROM public.clientes c
     WHERE c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));

DROP POLICY IF EXISTS puntos_movimientos_customer_select ON public.puntos_movimientos;
CREATE POLICY puntos_movimientos_customer_select ON public.puntos_movimientos
  FOR SELECT TO authenticated
  USING (cliente_id IN (
    SELECT c.id FROM public.clientes c
     WHERE c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));

DROP POLICY IF EXISTS visitas_clientes_select_own ON public.visitas_clientes;
CREATE POLICY visitas_clientes_select_own ON public.visitas_clientes
  FOR SELECT TO authenticated
  USING (
    usuario_id = (SELECT auth.uid())
    AND cliente_id IN (
      SELECT c.id FROM public.clientes c
       WHERE c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
    )
  );

NOTIFY pgrst, 'reload schema';
