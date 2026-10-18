-- Release 03 / B2 + B3. Operator authorization and historical prices are
-- checked atomically by the database. App clients cannot supply a new price.

-- A linked Auth identity is an operator record (including inactive operators),
-- while an unlinked directory row is floor staff. The Manager app cannot edit
-- user_id and generic sync is explicitly prevented from doing so below.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM public.empleados
     WHERE user_id IS NOT NULL
     GROUP BY user_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'empleados_duplicate_user_id_requires_manual_reconciliation';
  END IF;
END;
$$;
CREATE UNIQUE INDEX IF NOT EXISTS empleados_user_id_uidx
  ON public.empleados(user_id) WHERE user_id IS NOT NULL;

-- No direct employee UPDATE can express a salary restriction by destination
-- row; use the expected-version RPC for all app clients. Admin's existing
-- SECURITY DEFINER sync path remains available for non-salary directory edits.
REVOKE UPDATE ON TABLE public.empleados FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (nombre, apellido, telefono, salario, dia_pago, activo, user_id)
  ON TABLE public.empleados FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.update_floor_employee_salary(
  p_empleado_id uuid,
  p_salario bigint,
  p_expected_version bigint
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor_role text; target public.empleados%ROWTYPE;
BEGIN
  actor_role := (SELECT public.auth_role());
  IF auth.uid() IS NULL OR coalesce(actor_role, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_empleado_id IS NULL OR p_salario IS NULL OR p_salario <= 0
     OR p_expected_version IS NULL OR p_expected_version <= 0 THEN
    RAISE EXCEPTION 'invalid_salary_command' USING ERRCODE = '22023';
  END IF;

  SELECT e.* INTO target FROM public.empleados AS e
   WHERE e.id = p_empleado_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'employee_not_found' USING ERRCODE = 'P0002';
  END IF;
  IF target.sync_version <> p_expected_version THEN
    RAISE EXCEPTION 'version_conflict' USING ERRCODE = 'P0004';
  END IF;
  IF actor_role = 'gerente' AND target.user_id IS NOT NULL THEN
    -- Every admin/manager profile is Auth-linked, even when inactive. The
    -- linking column is not client writable and is not editable by sync RPC.
    RAISE EXCEPTION 'operator_salary_protected' USING ERRCODE = '42501';
  END IF;

  UPDATE public.empleados SET salario = p_salario
   WHERE id = target.id RETURNING sync_version INTO target.sync_version;
  RETURN jsonb_build_object('status', 'confirmed', 'empleado_id', target.id,
    'salario', p_salario, 'version', target.sync_version);
END;
$$;
REVOKE ALL ON FUNCTION public.update_floor_employee_salary(uuid, bigint, bigint)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_floor_employee_salary(uuid, bigint, bigint)
  TO authenticated;
COMMENT ON FUNCTION public.update_floor_employee_salary(uuid, bigint, bigint) IS
  'Atomic optimistic-concurrency salary update. Managers may only update unlinked floor-staff rows; linked operator salaries are protected.';

-- Payroll amount comes from the locked employee row. Inactive employees cannot
-- receive a new weekly payment; historical payments remain untouched.
CREATE OR REPLACE FUNCTION public.registrar_pago_nomina(
  p_empleado_id uuid,
  p_periodo_inicio date,
  p_fecha_pago date DEFAULT NULL,
  p_notas text DEFAULT NULL
)
RETURNS public.historial_pagos_empleados
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor_id uuid := (SELECT auth.uid());
  role_name text := (SELECT public.auth_role());
  employee public.empleados%ROWTYPE;
  payment public.historial_pagos_empleados%ROWTYPE;
  payment_date date;
BEGIN
  IF actor_id IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_empleado_id IS NULL OR p_periodo_inicio IS NULL
     OR extract(isodow FROM p_periodo_inicio) <> 1 THEN
    RAISE EXCEPTION 'invalid_payroll_period' USING ERRCODE = '22023';
  END IF;
  SELECT e.* INTO employee FROM public.empleados AS e
   WHERE e.id = p_empleado_id FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'employee_not_found' USING ERRCODE = 'P0002'; END IF;
  IF employee.activo IS DISTINCT FROM true THEN
    RAISE EXCEPTION 'employee_inactive' USING ERRCODE = '22023';
  END IF;
  payment_date := coalesce(p_fecha_pago,
    (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date);
  INSERT INTO public.historial_pagos_empleados (
    empleado_id, monto, fecha_pago, notas, periodo_inicio,
    fecha_programada, registrado_por
  ) VALUES (
    employee.id, employee.salario, payment_date, nullif(pg_catalog.btrim(p_notas), ''),
    p_periodo_inicio, p_periodo_inicio + (employee.dia_pago - 1), actor_id
  ) RETURNING * INTO payment;
  RETURN payment;
EXCEPTION WHEN unique_violation THEN
  RAISE EXCEPTION 'payroll_period_already_paid' USING ERRCODE = '23505';
END;
$$;
REVOKE ALL ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text)
  TO authenticated;

-- Keep the generic v1 implementation private to the v2 boundary. The v2
-- wrapper checks write invariants before calling it and returns durable receipts
-- for retries before evaluating mutable product catalogue state.
CREATE OR REPLACE FUNCTION public.sync_execute_v2(
  p_entity text, p_entity_id text, p_operation text, p_payload jsonb,
  p_key uuid, p_expected_version bigint DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  req jsonb; previous private_sync.receipts%ROWTYPE; response jsonb;
  order_line public.detalle_orden%ROWTYPE; catalog_price bigint; product_active boolean;
BEGIN
  IF auth.uid() IS NULL OR coalesce((SELECT public.auth_role()), '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_entity IS NULL OR p_payload IS NULL OR jsonb_typeof(p_payload) IS DISTINCT FROM 'object' THEN
    RAISE EXCEPTION 'invalid_command' USING ERRCODE = '22023';
  END IF;

  req := pg_catalog.jsonb_build_object('entity', p_entity, 'id', p_entity_id,
    'operation', p_operation, 'payload', p_payload, 'expected', p_expected_version);
  IF p_key IS NOT NULL THEN
    SELECT * INTO previous FROM private_sync.receipts WHERE key = p_key;
    IF FOUND THEN
      IF previous.actor_id <> auth.uid() THEN
        RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
      END IF;
      IF previous.request <> req THEN
        RAISE EXCEPTION 'idempotency_mismatch' USING ERRCODE = '22023';
      END IF;
      RETURN previous.result;
    END IF;
  END IF;

  IF p_entity = 'clientes' THEN
    RAISE EXCEPTION 'customer_profile_requires_explicit_rpc' USING ERRCODE = '42501';
  END IF;
  IF p_entity = 'empleados' AND (
       p_payload ? 'user_id' OR p_payload ? 'es_personal_piso' OR p_payload ? 'sync_version'
       OR p_payload ? 'created_at' OR p_payload ? 'updated_at'
  ) THEN
    RAISE EXCEPTION 'employee_identity_is_server_managed' USING ERRCODE = '42501';
  END IF;
  IF p_entity = 'empleados' AND (SELECT public.auth_role()) = 'gerente' THEN
    RAISE EXCEPTION 'employee_directory_update_requires_admin' USING ERRCODE = '42501';
  END IF;

  IF p_entity = 'registrar_pago' OR p_entity = 'registrar_movimiento_inventario' THEN
    IF p_operation IS DISTINCT FROM 'INSERT' OR p_key IS NULL OR p_entity_id IS NULL
       OR p_expected_version IS NULL
       OR (p_payload->>'p_idempotency_key')::uuid IS DISTINCT FROM p_key THEN
      RAISE EXCEPTION 'invalid_command' USING ERRCODE = '22023';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(78645231);
    req := pg_catalog.jsonb_build_object('entity', p_entity, 'id', p_entity_id,
      'operation', p_operation, 'payload', p_payload, 'expected', p_expected_version);
    IF p_entity = 'registrar_pago' THEN
      response := public.registrar_pago((p_payload->>'p_orden_id')::uuid,
        (p_payload->>'p_monto')::bigint, p_payload->>'p_metodo_pago', p_key,
        p_payload->'p_asignaciones');
    ELSE
      response := private_sync.inventory_command(p_payload);
    END IF;
    INSERT INTO private_sync.receipts(key, actor_id, request, result)
      VALUES (p_key, auth.uid(), req, response);
    RETURN response;
  END IF;

  IF p_entity = 'detalle_orden' THEN
    IF p_operation = 'INSERT' THEN
      SELECT precio, activo INTO catalog_price, product_active
        FROM public.productos
       WHERE id = (p_payload->>'producto_id')::uuid
       FOR SHARE;
      IF NOT FOUND OR product_active IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'product_unavailable' USING ERRCODE = '22023';
      END IF;
      IF (p_payload->>'precio_unitario')::bigint IS DISTINCT FROM catalog_price THEN
        RAISE EXCEPTION 'price_changed' USING ERRCODE = 'P0001',
          DETAIL = pg_catalog.jsonb_build_object('current_price_cents', catalog_price)::text;
      END IF;
    ELSIF p_operation = 'UPDATE' THEN
      SELECT * INTO order_line FROM public.detalle_orden
       WHERE id = p_entity_id::uuid FOR UPDATE;
      IF NOT FOUND THEN RAISE EXCEPTION 'version_conflict' USING ERRCODE = 'P0004'; END IF;
      IF p_payload ? 'precio_unitario'
         AND (p_payload->>'precio_unitario')::bigint IS DISTINCT FROM order_line.precio_unitario THEN
        RAISE EXCEPTION 'historical_price_immutable' USING ERRCODE = '42501';
      END IF;
      IF p_payload ? 'producto_id'
         AND (p_payload->>'producto_id')::uuid IS DISTINCT FROM order_line.producto_id THEN
        RAISE EXCEPTION 'order_line_product_immutable' USING ERRCODE = '42501';
      END IF;
    END IF;
  END IF;

  RETURN public.sync_execute(p_entity, p_entity_id, p_operation, p_payload, p_key, p_expected_version);
END;
$$;
REVOKE ALL ON FUNCTION public.sync_execute_v2(text, text, text, jsonb, uuid, bigint)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sync_execute_v2(text, text, text, jsonb, uuid, bigint)
  TO authenticated;
COMMENT ON FUNCTION public.sync_execute_v2(text, text, text, jsonb, uuid, bigint) IS
  'Checked/idempotent staff write boundary. New order-line prices are compared to the locked active product; existing prices are immutable.';

-- Compact read models use one database snapshot and are paginated by the caller
-- at the order/day level instead of summing full history in Android.
CREATE OR REPLACE FUNCTION public.order_financial_snapshot(p_order_id uuid)
RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $$
DECLARE result jsonb;
BEGIN
  IF auth.uid() IS NULL OR coalesce((SELECT public.auth_role()), '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_order_id IS NULL THEN RAISE EXCEPTION 'invalid_order_id' USING ERRCODE = '22023'; END IF;
  SELECT pg_catalog.jsonb_build_object(
    'order', pg_catalog.to_jsonb(o),
    'details', coalesce((SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(d) ORDER BY d.created_at, d.id)
      FROM public.detalle_orden d WHERE d.orden_id = o.id), '[]'::jsonb),
    'payments', coalesce((SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(t) ORDER BY t.fecha, t.id)
      FROM public.transacciones t WHERE t.orden_id = o.id), '[]'::jsonb),
    'allocations', coalesce((SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(pd) ORDER BY pd.created_at, pd.id)
      FROM public.pago_detalles pd JOIN public.transacciones t ON t.id = pd.transaccion_id
      WHERE t.orden_id = o.id), '[]'::jsonb),
    'total_cents', coalesce((SELECT sum(d.cantidad::bigint * d.precio_unitario)
      FROM public.detalle_orden d WHERE d.orden_id = o.id AND d.estado_pago <> 'Cancelado'), 0),
    'paid_cents', coalesce((SELECT sum(t.monto) FROM public.transacciones t WHERE t.orden_id = o.id), 0),
    'remaining_cents', greatest(0,
      coalesce((SELECT sum(d.cantidad::bigint * d.precio_unitario)
        FROM public.detalle_orden d WHERE d.orden_id = o.id AND d.estado_pago <> 'Cancelado'), 0)
      - coalesce((SELECT sum(t.monto) FROM public.transacciones t WHERE t.orden_id = o.id), 0))
  ) INTO result FROM public.ordenes o WHERE o.id = p_order_id;
  IF result IS NULL THEN RAISE EXCEPTION 'order_not_found' USING ERRCODE = 'P0002'; END IF;
  RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION public.order_financial_snapshot(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.order_financial_snapshot(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.daily_sales_summary(p_business_date date)
RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $$
DECLARE start_at timestamptz; end_at timestamptz; summary jsonb;
BEGIN
  IF auth.uid() IS NULL OR coalesce((SELECT public.auth_role()), '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_business_date IS NULL THEN RAISE EXCEPTION 'invalid_business_date' USING ERRCODE = '22023'; END IF;
  start_at := p_business_date::timestamp AT TIME ZONE 'America/Mexico_City';
  end_at := (p_business_date + 1)::timestamp AT TIME ZONE 'America/Mexico_City';
  SELECT pg_catalog.jsonb_build_object(
    'business_date', p_business_date,
    'payment_count', count(t.id),
    'order_count', count(DISTINCT t.orden_id),
    'total_cents', coalesce(sum(t.monto), 0),
    'cash_cents', coalesce(sum(t.monto) FILTER (WHERE t.metodo_pago = 'Efectivo'), 0),
    'card_cents', coalesce(sum(t.monto) FILTER (WHERE t.metodo_pago = 'Tarjeta'), 0),
    'other_cents', coalesce(sum(t.monto) FILTER (WHERE t.metodo_pago NOT IN ('Efectivo','Tarjeta')), 0)
  ) INTO summary FROM public.transacciones t WHERE t.fecha >= start_at AND t.fecha < end_at;
  RETURN summary;
END;
$$;
REVOKE ALL ON FUNCTION public.daily_sales_summary(date) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.daily_sales_summary(date) TO authenticated;

CREATE INDEX IF NOT EXISTS ordenes_estado_apertura_id_idx
  ON public.ordenes(estado, fecha_apertura, id);
CREATE INDEX IF NOT EXISTS detalle_orden_orden_id_idx
  ON public.detalle_orden(orden_id, id);
CREATE INDEX IF NOT EXISTS transacciones_fecha_orden_id_idx
  ON public.transacciones(fecha, orden_id, id);
CREATE INDEX IF NOT EXISTS clientes_nombre_telefono_idx
  ON public.clientes(nombre, telefono, id);
