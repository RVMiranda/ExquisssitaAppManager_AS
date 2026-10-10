-- A missing user_roles row made auth_role() return NULL. SQL/PLpgSQL IF
-- conditions with NULL are not true, so sensitive functions must reject NULL
-- explicitly instead of relying on a plain NOT IN / <> comparison.

CREATE OR REPLACE FUNCTION public.auth_role()
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT COALESCE((
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
        LIMIT 1
    ), '');
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
    IF (SELECT public.auth_role()) IS DISTINCT FROM 'admin' THEN
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
        empleado_id, monto, fecha_pago, notas, periodo_inicio,
        fecha_programada, registrado_por
    ) VALUES (
        v_empleado.id, v_empleado.salario, v_fecha_pago,
        NULLIF(btrim(p_notas), ''), p_periodo_inicio,
        p_periodo_inicio + (v_empleado.dia_pago - 1), v_actor_id
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
