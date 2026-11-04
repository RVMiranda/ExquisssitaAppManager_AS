-- Migration: 20261101160000_employee_loans_and_payroll_deductions.sql
-- Description: Employee loans (prestamos/adelantos), partial payments (pago_parcial),
--              and payroll deduction integration.

-- 1. Create table public.prestamos_empleados
CREATE TABLE IF NOT EXISTS public.prestamos_empleados (
    id UUID PRIMARY KEY DEFAULT extensions.uuid_generate_v4(),
    empleado_id UUID NOT NULL REFERENCES public.empleados(id) ON DELETE RESTRICT,
    monto_centavos BIGINT NOT NULL CHECK (monto_centavos > 0),
    saldo_pendiente_centavos BIGINT NOT NULL CHECK (saldo_pendiente_centavos >= 0 AND saldo_pendiente_centavos <= monto_centavos),
    fecha_prestamo DATE NOT NULL DEFAULT CURRENT_DATE,
    notas TEXT,
    estado TEXT NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'pago_parcial', 'liquidado', 'cancelado')),
    registrado_por UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS idx_prestamos_empleado_id ON public.prestamos_empleados (empleado_id);
CREATE INDEX IF NOT EXISTS idx_prestamos_estado ON public.prestamos_empleados (estado);
CREATE INDEX IF NOT EXISTS idx_prestamos_fecha ON public.prestamos_empleados (fecha_prestamo);

-- 2. Create table public.abonos_prestamos_empleados
CREATE TABLE IF NOT EXISTS public.abonos_prestamos_empleados (
    id UUID PRIMARY KEY DEFAULT extensions.uuid_generate_v4(),
    prestamo_id UUID NOT NULL REFERENCES public.prestamos_empleados(id) ON DELETE CASCADE,
    monto_centavos BIGINT NOT NULL CHECK (monto_centavos > 0),
    saldo_anterior_centavos BIGINT NOT NULL,
    saldo_restante_centavos BIGINT NOT NULL,
    fecha_abono DATE NOT NULL DEFAULT CURRENT_DATE,
    tipo TEXT NOT NULL DEFAULT 'pago_directo' CHECK (tipo IN ('deduccion_nomina', 'pago_directo', 'ajuste')),
    nomina_pago_id UUID REFERENCES public.historial_pagos_empleados(id) ON DELETE SET NULL,
    notas TEXT,
    registrado_por UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.now()
);

CREATE INDEX IF NOT EXISTS idx_abonos_prestamo_id ON public.abonos_prestamos_empleados (prestamo_id);
CREATE INDEX IF NOT EXISTS idx_abonos_nomina_pago_id ON public.abonos_prestamos_empleados (nomina_pago_id);

-- 3. Extend historial_pagos_empleados with deduction tracking if columns do not exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'historial_pagos_empleados' 
          AND column_name = 'descuento_prestamo'
    ) THEN
        ALTER TABLE public.historial_pagos_empleados 
        ADD COLUMN descuento_prestamo BIGINT NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'historial_pagos_empleados' 
          AND column_name = 'prestamo_id'
    ) THEN
        ALTER TABLE public.historial_pagos_empleados 
        ADD COLUMN prestamo_id UUID REFERENCES public.prestamos_empleados(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 4. Enable RLS on newly created tables
ALTER TABLE public.prestamos_empleados ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.abonos_prestamos_empleados ENABLE ROW LEVEL SECURITY;

-- 5. RLS Policies
DROP POLICY IF EXISTS "prestamos_admin_gerente_all" ON public.prestamos_empleados;
CREATE POLICY "prestamos_admin_gerente_all" ON public.prestamos_empleados
    FOR ALL
    TO authenticated
    USING (public.auth_role() IN ('admin', 'gerente'))
    WITH CHECK (public.auth_role() IN ('admin', 'gerente'));

DROP POLICY IF EXISTS "abonos_admin_gerente_all" ON public.abonos_prestamos_empleados;
CREATE POLICY "abonos_admin_gerente_all" ON public.abonos_prestamos_empleados
    FOR ALL
    TO authenticated
    USING (public.auth_role() IN ('admin', 'gerente'))
    WITH CHECK (public.auth_role() IN ('admin', 'gerente'));

-- Grants
GRANT SELECT, INSERT, UPDATE ON TABLE public.prestamos_empleados TO authenticated;
GRANT SELECT, INSERT ON TABLE public.abonos_prestamos_empleados TO authenticated;
REVOKE ALL ON TABLE public.prestamos_empleados FROM anon, PUBLIC;
REVOKE ALL ON TABLE public.abonos_prestamos_empleados FROM anon, PUBLIC;

-- 6. RPC: crear_prestamo_empleado
CREATE OR REPLACE FUNCTION public.crear_prestamo_empleado(
    p_empleado_id UUID,
    p_monto_centavos BIGINT,
    p_fecha DATE DEFAULT NULL,
    p_notas TEXT DEFAULT NULL
)
RETURNS public.prestamos_empleados
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := (SELECT auth.uid());
    v_role TEXT := (SELECT public.auth_role());
    v_employee public.empleados%ROWTYPE;
    v_fecha_prestamo DATE;
    v_prestamo public.prestamos_empleados%ROWTYPE;
BEGIN
    IF v_actor_id IS NULL OR coalesce(v_role, '') NOT IN ('admin', 'gerente') THEN
        RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
    END IF;

    IF p_empleado_id IS NULL OR p_monto_centavos IS NULL OR p_monto_centavos <= 0 THEN
        RAISE EXCEPTION 'invalid_loan_amount' USING ERRCODE = '22023';
    END IF;

    SELECT * INTO v_employee FROM public.empleados WHERE id = p_empleado_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'employee_not_found' USING ERRCODE = 'P0002';
    END IF;

    IF v_employee.activo IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'employee_inactive' USING ERRCODE = '22023';
    END IF;

    v_fecha_prestamo := coalesce(p_fecha, (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date);

    INSERT INTO public.prestamos_empleados (
        empleado_id,
        monto_centavos,
        saldo_pendiente_centavos,
        fecha_prestamo,
        notas,
        estado,
        registrado_por
    ) VALUES (
        v_employee.id,
        p_monto_centavos,
        p_monto_centavos,
        v_fecha_prestamo,
        nullif(pg_catalog.btrim(p_notas), ''),
        'pendiente',
        v_actor_id
    ) RETURNING * INTO v_prestamo;

    RETURN v_prestamo;
END;
$$;

REVOKE ALL ON FUNCTION public.crear_prestamo_empleado(UUID, BIGINT, DATE, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_prestamo_empleado(UUID, BIGINT, DATE, TEXT) TO authenticated;

-- 7. RPC: registrar_abono_prestamo
CREATE OR REPLACE FUNCTION public.registrar_abono_prestamo(
    p_prestamo_id UUID,
    p_monto_centavos BIGINT,
    p_tipo TEXT DEFAULT 'pago_directo',
    p_notas TEXT DEFAULT NULL
)
RETURNS public.prestamos_empleados
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := (SELECT auth.uid());
    v_role TEXT := (SELECT public.auth_role());
    v_loan public.prestamos_empleados%ROWTYPE;
    v_nuevo_saldo BIGINT;
    v_nuevo_estado TEXT;
    v_fecha DATE;
BEGIN
    IF v_actor_id IS NULL OR coalesce(v_role, '') NOT IN ('admin', 'gerente') THEN
        RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
    END IF;

    IF p_prestamo_id IS NULL OR p_monto_centavos IS NULL OR p_monto_centavos <= 0 THEN
        RAISE EXCEPTION 'invalid_payment_amount' USING ERRCODE = '22023';
    END IF;

    SELECT * INTO v_loan FROM public.prestamos_empleados WHERE id = p_prestamo_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'loan_not_found' USING ERRCODE = 'P0002';
    END IF;

    IF v_loan.estado NOT IN ('pendiente', 'pago_parcial') THEN
        RAISE EXCEPTION 'loan_not_payable' USING ERRCODE = '22023';
    END IF;

    IF p_monto_centavos > v_loan.saldo_pendiente_centavos THEN
        RAISE EXCEPTION 'payment_exceeds_balance' USING ERRCODE = '22023';
    END IF;

    v_nuevo_saldo := v_loan.saldo_pendiente_centavos - p_monto_centavos;
    v_nuevo_estado := CASE WHEN v_nuevo_saldo = 0 THEN 'liquidado' ELSE 'pago_parcial' END;
    v_fecha := (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date;

    UPDATE public.prestamos_empleados
    SET saldo_pendiente_centavos = v_nuevo_saldo,
        estado = v_nuevo_estado,
        updated_at = pg_catalog.now()
    WHERE id = v_loan.id
    RETURNING * INTO v_loan;

    INSERT INTO public.abonos_prestamos_empleados (
        prestamo_id,
        monto_centavos,
        saldo_anterior_centavos,
        saldo_restante_centavos,
        fecha_abono,
        tipo,
        notas,
        registrado_por
    ) VALUES (
        v_loan.id,
        p_monto_centavos,
        v_loan.saldo_pendiente_centavos + p_monto_centavos,
        v_nuevo_saldo,
        v_fecha,
        coalesce(p_tipo, 'pago_directo'),
        nullif(pg_catalog.btrim(p_notas), ''),
        v_actor_id
    );

    RETURN v_loan;
END;
$$;

REVOKE ALL ON FUNCTION public.registrar_abono_prestamo(UUID, BIGINT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_abono_prestamo(UUID, BIGINT, TEXT, TEXT) TO authenticated;

-- 8. Enhanced RPC: registrar_pago_nomina with optional loan deduction
CREATE OR REPLACE FUNCTION public.registrar_pago_nomina(
    p_empleado_id UUID,
    p_periodo_inicio DATE,
    p_fecha_pago DATE DEFAULT NULL,
    p_notas TEXT DEFAULT NULL,
    p_descuento_prestamo BIGINT DEFAULT 0,
    p_prestamo_id UUID DEFAULT NULL
)
RETURNS public.historial_pagos_empleados
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    actor_id UUID := (SELECT auth.uid());
    role_name TEXT := (SELECT public.auth_role());
    employee public.empleados%ROWTYPE;
    payment public.historial_pagos_empleados%ROWTYPE;
    payment_date DATE;
    v_loan public.prestamos_empleados%ROWTYPE;
    v_nuevo_saldo BIGINT;
    v_nuevo_estado TEXT;
    v_descuento BIGINT := coalesce(p_descuento_prestamo, 0);
BEGIN
    IF actor_id IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
        RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
    END IF;

    IF p_empleado_id IS NULL OR p_periodo_inicio IS NULL
       OR extract(isodow FROM p_periodo_inicio) <> 1 THEN
        RAISE EXCEPTION 'invalid_payroll_period' USING ERRCODE = '22023';
    END IF;

    IF v_descuento < 0 THEN
        RAISE EXCEPTION 'invalid_deduction_amount' USING ERRCODE = '22023';
    END IF;

    SELECT e.* INTO employee FROM public.empleados AS e
     WHERE e.id = p_empleado_id FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'employee_not_found' USING ERRCODE = 'P0002'; END IF;

    IF employee.activo IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'employee_inactive' USING ERRCODE = '22023';
    END IF;

    IF v_descuento > employee.salario THEN
        RAISE EXCEPTION 'deduction_exceeds_salary' USING ERRCODE = '22023';
    END IF;

    -- Validar préstamo si se solicitó descuento
    IF v_descuento > 0 THEN
        IF p_prestamo_id IS NULL THEN
            RAISE EXCEPTION 'loan_id_required_for_deduction' USING ERRCODE = '22023';
        END IF;

        SELECT * INTO v_loan FROM public.prestamos_empleados
         WHERE id = p_prestamo_id AND empleado_id = employee.id FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'loan_not_found' USING ERRCODE = 'P0002';
        END IF;

        IF v_loan.estado NOT IN ('pendiente', 'pago_parcial') THEN
            RAISE EXCEPTION 'loan_not_payable' USING ERRCODE = '22023';
        END IF;

        IF v_descuento > v_loan.saldo_pendiente_centavos THEN
            RAISE EXCEPTION 'deduction_exceeds_loan_balance' USING ERRCODE = '22023';
        END IF;
    END IF;

    payment_date := coalesce(p_fecha_pago,
        (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date);

    INSERT INTO public.historial_pagos_empleados (
        empleado_id, monto, fecha_pago, notas, periodo_inicio,
        fecha_programada, registrado_por, descuento_prestamo, prestamo_id
    ) VALUES (
        employee.id, employee.salario, payment_date, nullif(pg_catalog.btrim(p_notas), ''),
        p_periodo_inicio, p_periodo_inicio + (employee.dia_pago - 1), actor_id,
        v_descuento, p_prestamo_id
    ) RETURNING * INTO payment;

    -- Si hubo descuento de préstamo, actualizar saldo y asentar abono
    IF v_descuento > 0 AND p_prestamo_id IS NOT NULL THEN
        v_nuevo_saldo := v_loan.saldo_pendiente_centavos - v_descuento;
        v_nuevo_estado := CASE WHEN v_nuevo_saldo = 0 THEN 'liquidado' ELSE 'pago_parcial' END;

        UPDATE public.prestamos_empleados
        SET saldo_pendiente_centavos = v_nuevo_saldo,
            estado = v_nuevo_estado,
            updated_at = pg_catalog.now()
        WHERE id = v_loan.id;

        INSERT INTO public.abonos_prestamos_empleados (
            prestamo_id,
            monto_centavos,
            saldo_anterior_centavos,
            saldo_restante_centavos,
            fecha_abono,
            tipo,
            nomina_pago_id,
            notas,
            registrado_por
        ) VALUES (
            v_loan.id,
            v_descuento,
            v_loan.saldo_pendiente_centavos,
            v_nuevo_saldo,
            payment_date,
            'deduccion_nomina',
            payment.id,
            'Deducción de nómina para periodo ' || p_periodo_inicio::text,
            actor_id
        );
    END IF;

    RETURN payment;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION 'payroll_period_already_paid' USING ERRCODE = '23505';
END;
$$;

REVOKE ALL ON FUNCTION public.registrar_pago_nomina(UUID, DATE, DATE, TEXT, BIGINT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_pago_nomina(UUID, DATE, DATE, TEXT, BIGINT, UUID) TO authenticated;
