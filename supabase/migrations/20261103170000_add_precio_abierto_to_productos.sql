-- Migration: 20261103170000_add_precio_abierto_to_productos.sql
-- Description: Add precio_abierto column to public.productos and allow flexible unit price in sync_execute_v2

ALTER TABLE public.productos
  ADD COLUMN IF NOT EXISTS precio_abierto boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.productos.precio_abierto IS
  'If true, this product accepts open/custom unit prices at order time (e.g. bulk meat sales by variable amount).';

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
  product_precio_abierto boolean;
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
      SELECT precio, activo, coalesce(precio_abierto, false)
        INTO catalog_price, product_active, product_precio_abierto
        FROM public.productos
       WHERE id = (p_payload->>'producto_id')::uuid
       FOR SHARE;
      IF NOT FOUND OR product_active IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'product_unavailable' USING ERRCODE = '22023';
      END IF;
      IF product_precio_abierto IS TRUE THEN
        IF (p_payload->>'precio_unitario')::bigint <= 0 THEN
          RAISE EXCEPTION 'invalid_open_price' USING ERRCODE = '22023';
        END IF;
      ELSE
        IF (p_payload->>'precio_unitario')::bigint IS DISTINCT FROM catalog_price THEN
          RAISE EXCEPTION 'price_changed' USING ERRCODE = 'P0001',
            DETAIL = pg_catalog.jsonb_build_object('current_price_cents', catalog_price)::text;
        END IF;
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
  'Checked/idempotent staff write boundary. New order-line prices are compared to the locked active product (or verified > 0 when precio_abierto = true); existing prices are immutable.';
