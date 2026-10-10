-- App access is restricted to admins and active managers.
-- Floor employees remain directory records without Supabase Auth accounts.

ALTER TABLE public.user_roles
    DROP CONSTRAINT IF EXISTS user_roles_role_check;

ALTER TABLE public.user_roles
    ADD CONSTRAINT user_roles_role_check
    CHECK (role IN ('admin', 'gerente', 'empleado'));

-- The current 100 values were identified as test salaries entered in pesos.
-- Normalize those test rows to cents; the 120000 test amount already represents
-- MXN 1200.00 and remains unchanged.
UPDATE public.empleados
   SET salario = 10000
 WHERE salario = 100;

CREATE OR REPLACE FUNCTION public.auth_role()
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN ur.role = 'admin' THEN 'admin'
        WHEN ur.role = 'gerente' AND EXISTS (
            SELECT 1
            FROM public.empleados AS e
            WHERE e.user_id = (SELECT auth.uid())
              AND e.activo IS TRUE
        ) THEN 'gerente'
        ELSE ''
    END
    FROM public.user_roles AS ur
    WHERE ur.user_id = (SELECT auth.uid())
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION public.auth_role() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.auth_role() TO authenticated;

CREATE OR REPLACE FUNCTION public.set_user_role(target_email text, target_role text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    target_id uuid;
BEGIN
    IF (SELECT public.auth_role()) <> 'admin' THEN
        RAISE EXCEPTION 'permiso_denegado' USING ERRCODE = '42501';
    END IF;
    IF target_role NOT IN ('admin', 'gerente', 'empleado') THEN
        RAISE EXCEPTION 'rol_invalido' USING ERRCODE = '22023';
    END IF;

    SELECT u.id
      INTO target_id
      FROM auth.users AS u
     WHERE u.email = target_email;

    IF target_id IS NULL THEN
        RETURN 'Usuario no encontrado';
    END IF;

    INSERT INTO public.user_roles (user_id, role)
    VALUES (target_id, target_role)
    ON CONFLICT (user_id) DO UPDATE
        SET role = EXCLUDED.role,
            updated_at = pg_catalog.now();

    UPDATE auth.users
       SET raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb)
           || pg_catalog.jsonb_build_object('rol', target_role)
     WHERE id = target_id;

    RETURN 'Rol actualizado';
END;
$$;

REVOKE ALL ON FUNCTION public.set_user_role(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_user_role(text, text) TO authenticated;

-- Role assignments are provisioned by a trusted administrator through Supabase.
-- A manager can read their own role but cannot grant roles or promote accounts.
REVOKE ALL PRIVILEGES ON TABLE public.user_roles FROM anon, authenticated;
GRANT SELECT ON TABLE public.user_roles TO authenticated;

DROP POLICY IF EXISTS user_roles_self_select ON public.user_roles;
CREATE POLICY user_roles_self_select
    ON public.user_roles
    FOR SELECT
    TO authenticated
    USING (
        user_id = (SELECT auth.uid())
        OR (SELECT public.auth_role()) = 'admin'
    );

-- Employee profiles are managed in-app by admin/manager users. The column-level
-- grants intentionally omit user_id, so a manager can create floor staff records
-- but cannot grant an Auth identity or promote somebody to manager.
REVOKE ALL PRIVILEGES ON TABLE public.empleados FROM anon, authenticated;
GRANT SELECT ON TABLE public.empleados TO authenticated;
GRANT INSERT (nombre, apellido, telefono, salario, dia_pago, activo)
    ON TABLE public.empleados TO authenticated;
GRANT UPDATE (nombre, apellido, telefono, salario, dia_pago, activo)
    ON TABLE public.empleados TO authenticated;

DROP POLICY IF EXISTS empleados_admin_all ON public.empleados;
DROP POLICY IF EXISTS empleados_select_own ON public.empleados;
CREATE POLICY empleados_admin_manager_manage
    ON public.empleados
    FOR ALL
    TO authenticated
    USING ((SELECT public.auth_role()) IN ('admin', 'gerente'))
    WITH CHECK ((SELECT public.auth_role()) IN ('admin', 'gerente'));

-- Payroll history is readable only by app operators and is append-only through
-- the RPC below. Floor staff have no login and receive no self-read policy.
ALTER TABLE public.historial_pagos_empleados
    ADD COLUMN IF NOT EXISTS registrado_por uuid
        REFERENCES auth.users(id) ON DELETE SET NULL;

REVOKE ALL PRIVILEGES ON TABLE public.historial_pagos_empleados FROM anon, authenticated;
GRANT SELECT ON TABLE public.historial_pagos_empleados TO authenticated;

DROP POLICY IF EXISTS pagos_empleados_admin_all ON public.historial_pagos_empleados;
DROP POLICY IF EXISTS pagos_empleados_select_own ON public.historial_pagos_empleados;
CREATE POLICY pagos_empleados_admin_manager_select
    ON public.historial_pagos_empleados
    FOR SELECT
    TO authenticated
    USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));

CREATE OR REPLACE FUNCTION public.registrar_pago_nomina(
    p_empleado_id uuid,
    p_periodo_inicio date,
    p_fecha_pago date DEFAULT NULL,
    p_notas text DEFAULT NULL
)
RETURNS public.historial_pagos_empleados
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id uuid := (SELECT auth.uid());
    v_role text := (SELECT public.auth_role());
    v_empleado public.empleados%ROWTYPE;
    v_pago public.historial_pagos_empleados%ROWTYPE;
    v_fecha_pago date;
BEGIN
    IF v_actor_id IS NULL OR v_role NOT IN ('admin', 'gerente') THEN
        RAISE EXCEPTION 'Solo el administrador o un gerente activo puede registrar nómina.'
            USING ERRCODE = '42501';
    END IF;

    IF p_empleado_id IS NULL OR p_periodo_inicio IS NULL THEN
        RAISE EXCEPTION 'El empleado y el inicio del periodo son obligatorios.'
            USING ERRCODE = '22023';
    END IF;

    IF EXTRACT(ISODOW FROM p_periodo_inicio) <> 1 THEN
        RAISE EXCEPTION 'El periodo semanal debe iniciar en lunes.'
            USING ERRCODE = '22023';
    END IF;

    SELECT e.*
      INTO v_empleado
      FROM public.empleados AS e
     WHERE e.id = p_empleado_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe el empleado indicado.'
            USING ERRCODE = 'P0002';
    END IF;

    v_fecha_pago := COALESCE(
        p_fecha_pago,
        (now() AT TIME ZONE 'America/Mexico_City')::date
    );

    INSERT INTO public.historial_pagos_empleados (
        empleado_id,
        monto,
        fecha_pago,
        notas,
        periodo_inicio,
        fecha_programada,
        registrado_por
    ) VALUES (
        v_empleado.id,
        v_empleado.salario,
        v_fecha_pago,
        NULLIF(btrim(p_notas), ''),
        p_periodo_inicio,
        p_periodo_inicio + (v_empleado.dia_pago - 1),
        v_actor_id
    )
    RETURNING * INTO v_pago;

    RETURN v_pago;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION 'Ya existe un pago registrado para ese empleado y periodo semanal.'
            USING ERRCODE = '23505';
END;
$$;

REVOKE ALL ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text)
    TO authenticated;

COMMENT ON COLUMN public.empleados.salario IS
    'Cuota semanal del empleado, almacenada como centavos de MXN. La app captura y muestra pesos.';
COMMENT ON COLUMN public.historial_pagos_empleados.monto IS
    'Importe semanal pagado, almacenado como centavos de MXN.';
COMMENT ON COLUMN public.historial_pagos_empleados.registrado_por IS
    'Usuario Auth que registró el pago; NULL en registros históricos previos a esta migración.';
COMMENT ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text) IS
    'Registra una cuota semanal fija en centavos; deriva el monto y fecha programada del perfil empleado, con un único pago por periodo.';
