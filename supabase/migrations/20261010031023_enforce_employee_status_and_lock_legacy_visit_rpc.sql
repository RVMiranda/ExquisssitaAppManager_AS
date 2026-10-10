-- Keep role claims tied to the deployed source of truth.
-- Employees whose profile is inactive (or missing) no longer pass role-based RLS/RPC checks.
-- Administrators are controlled through their user_roles assignment.
CREATE OR REPLACE FUNCTION public.auth_role()
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN ur.role = 'admin' THEN 'admin'
        WHEN ur.role = 'empleado' AND EXISTS (
            SELECT 1
            FROM public.empleados AS e
            WHERE e.user_id = (SELECT auth.uid())
              AND e.activo IS TRUE
        ) THEN 'empleado'
        ELSE ''
    END
    FROM public.user_roles AS ur
    WHERE ur.user_id = (SELECT auth.uid())
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION public.auth_role() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.auth_role() TO authenticated;

-- The legacy RPC awards points by customer ID and bypasses the QR proof.
-- All customer visits must go through registrar_visita_qr(p_qr_token).
REVOKE ALL ON FUNCTION public.registrar_visita_cliente(uuid)
    FROM PUBLIC, anon, authenticated;
