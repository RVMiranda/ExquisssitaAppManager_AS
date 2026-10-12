-- Keep money allocations (pago_detalles) separate from the units consumed from stock.
CREATE TABLE public.pago_detalle_stock (
  id uuid PRIMARY KEY DEFAULT extensions.uuid_generate_v4(),
  transaccion_id uuid NOT NULL REFERENCES public.transacciones(id) ON DELETE RESTRICT,
  detalle_orden_id uuid NOT NULL REFERENCES public.detalle_orden(id) ON DELETE RESTRICT,
  producto_id uuid NOT NULL REFERENCES public.productos(id) ON DELETE RESTRICT,
  cantidad integer NOT NULL CHECK (cantidad > 0),
  movimiento_id uuid NOT NULL REFERENCES public.movimientos_inventario(id) ON DELETE RESTRICT,
  idempotency_key uuid NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (transaccion_id, detalle_orden_id),
  UNIQUE (movimiento_id)
);
CREATE INDEX pago_detalle_stock_detail_idx
  ON public.pago_detalle_stock(detalle_orden_id, created_at);
CREATE INDEX pago_detalle_stock_transaction_idx
  ON public.pago_detalle_stock(transaccion_id);
ALTER TABLE public.pago_detalle_stock ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.pago_detalle_stock FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.pago_detalle_stock TO authenticated;
CREATE POLICY pago_detalle_stock_staff_select ON public.pago_detalle_stock
  FOR SELECT TO authenticated
  USING (coalesce((SELECT public.auth_role()), '') IN ('admin', 'gerente'));
CREATE TRIGGER pago_detalle_stock_append_only
  BEFORE UPDATE OR DELETE ON public.pago_detalle_stock
  FOR EACH ROW EXECUTE FUNCTION private_sync.reject_ledger_mutation();
CREATE TRIGGER pago_detalle_stock_no_truncate
  BEFORE TRUNCATE ON public.pago_detalle_stock
  FOR EACH STATEMENT EXECUTE FUNCTION private_sync.reject_ledger_mutation();
COMMENT ON TABLE public.pago_detalle_stock IS
  'Unidades de inventario liquidadas por pago y detalle. Separado de las asignaciones monetarias de pago_detalles.';

CREATE OR REPLACE FUNCTION public.registrar_pago(
  p_orden_id uuid,
  p_monto bigint,
  p_metodo_pago text,
  p_idempotency_key uuid,
  p_asignaciones jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  req jsonb := jsonb_build_object('order', p_orden_id, 'amount', p_monto, 'method', p_metodo_pago, 'allocations', p_asignaciones);
  receipt private_sync.critical_receipts%rowtype;
  prior public.transacciones%rowtype;
  detail public.detalle_orden%rowtype;
  product public.productos%rowtype;
  movement_result jsonb;
  order_state text;
  total bigint;
  paid bigint;
  line_paid bigint;
  allocated bigint := 0;
  stock_allocated bigint;
  requested_stock bigint;
  item jsonb;
  product_id uuid;
  tx uuid;
  amount bigint;
  qty integer;
  stock_qty integer;
  movement_key uuid;
  movement_id uuid;
BEGIN
  IF auth.uid() IS NULL OR coalesce(public.auth_role(), '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_idempotency_key IS NULL OR p_orden_id IS NULL OR p_monto IS NULL OR p_monto <= 0
    OR p_metodo_pago IS NULL OR p_metodo_pago NOT IN ('Efectivo', 'Tarjeta', 'Transferencia', 'Otro')
    OR jsonb_typeof(p_asignaciones) IS DISTINCT FROM 'array' OR jsonb_array_length(p_asignaciones) = 0 THEN
    RAISE EXCEPTION 'invalid_command' USING ERRCODE = '22023';
  END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(78645231);
  SELECT * INTO receipt FROM private_sync.critical_receipts WHERE key = p_idempotency_key;
  IF FOUND THEN
    IF receipt.actor_id <> auth.uid() OR receipt.entity <> 'payment' OR receipt.request <> req THEN
      RAISE EXCEPTION 'idempotency_mismatch' USING ERRCODE = '22023';
    END IF;
    RETURN receipt.result;
  END IF;
  SELECT * INTO prior FROM public.transacciones WHERE idempotency_key = p_idempotency_key;
  IF FOUND THEN RAISE EXCEPTION 'legacy_payment_requires_review' USING ERRCODE = '22023'; END IF;

  SELECT estado INTO order_state FROM public.ordenes WHERE id = p_orden_id FOR UPDATE;
  IF order_state IS DISTINCT FROM 'Abierta' THEN
    RETURN private_sync.record_critical_result(
      p_idempotency_key, 'payment', req, jsonb_build_object('status', 'rejected', 'reason', 'order_unavailable')
    );
  END IF;
  IF EXISTS (
    SELECT 1 FROM jsonb_array_elements(p_asignaciones) a
    GROUP BY a->>'detalle_orden_id' HAVING count(*) > 1
  ) THEN RAISE EXCEPTION 'duplicate_allocation' USING ERRCODE = '22023'; END IF;

  SELECT coalesce(sum(d.cantidad::bigint * d.precio_unitario), 0)
    INTO total FROM public.detalle_orden d
   WHERE d.orden_id = p_orden_id AND d.estado_pago <> 'Cancelado';
  SELECT coalesce(sum(t.monto), 0) INTO paid
    FROM public.transacciones t WHERE t.orden_id = p_orden_id;
  IF p_monto > total - paid THEN
    RETURN private_sync.record_critical_result(
      p_idempotency_key, 'payment', req, jsonb_build_object('status', 'rejected', 'reason', 'overpayment')
    );
  END IF;

  -- Validate every amount and stock allocation before creating any ledger rows.
  FOR item IN
    SELECT a FROM jsonb_array_elements(p_asignaciones) a ORDER BY a->>'detalle_orden_id'
  LOOP
    qty := coalesce(nullif(item->>'cantidad', '')::integer, 0);
    amount := coalesce(nullif(item->>'monto', '')::bigint, 0);
    stock_qty := coalesce(nullif(item->>'cantidad_stock', '')::integer, 0);
    movement_key := nullif(item->>'stock_idempotency_key', '')::uuid;
    SELECT * INTO detail FROM public.detalle_orden
     WHERE id = (item->>'detalle_orden_id')::uuid AND orden_id = p_orden_id AND estado_pago <> 'Cancelado'
     FOR UPDATE;
    IF NOT FOUND OR qty <= 0 OR qty > detail.cantidad OR amount < 0 OR stock_qty < 0 OR stock_qty > qty
      OR (amount = 0 AND stock_qty = 0) OR amount > qty::bigint * detail.precio_unitario THEN
      RAISE EXCEPTION 'invalid_allocation' USING ERRCODE = '22023';
    END IF;
    SELECT coalesce(sum(pd.monto), 0) INTO line_paid
      FROM public.pago_detalles pd WHERE pd.detalle_orden_id = detail.id;
    IF amount > detail.cantidad::bigint * detail.precio_unitario - line_paid THEN
      RETURN private_sync.record_critical_result(
        p_idempotency_key, 'payment', req, jsonb_build_object('status', 'rejected', 'reason', 'detail_overpayment')
      );
    END IF;
    SELECT * INTO product FROM public.productos WHERE id = detail.producto_id;
    IF NOT FOUND OR (stock_qty > 0 AND (product.controla_inventario IS DISTINCT FROM true OR product.activo IS DISTINCT FROM true)) THEN
      RAISE EXCEPTION 'invalid_stock_allocation' USING ERRCODE = '22023';
    END IF;
    SELECT coalesce(sum(ps.cantidad), 0) INTO stock_allocated
      FROM public.pago_detalle_stock ps WHERE ps.detalle_orden_id = detail.id;
    IF stock_allocated + stock_qty > detail.cantidad THEN
      RAISE EXCEPTION 'stock_allocation_exceeds_order_quantity' USING ERRCODE = '22023';
    END IF;
    IF stock_qty > 0 AND (movement_key IS NULL OR movement_key = p_idempotency_key) THEN
      RAISE EXCEPTION 'stock_idempotency_key_required' USING ERRCODE = '22023';
    END IF;
    IF stock_qty = 0 AND movement_key IS NOT NULL THEN
      RAISE EXCEPTION 'unexpected_stock_idempotency_key' USING ERRCODE = '22023';
    END IF;
    IF stock_qty > 0 THEN
      SELECT count(*) INTO requested_stock
        FROM jsonb_array_elements(p_asignaciones) a
       WHERE (a->>'stock_idempotency_key')::uuid = movement_key;
      IF requested_stock > 1 THEN RAISE EXCEPTION 'duplicate_stock_idempotency_key' USING ERRCODE = '22023'; END IF;
    END IF;
    allocated := allocated + amount;
  END LOOP;
  IF allocated <> p_monto THEN RAISE EXCEPTION 'allocation_amount_mismatch' USING ERRCODE = '22023'; END IF;

  -- Lock all affected products in stable order, then reject insufficient stock before any mutation.
  FOR product_id IN
    SELECT DISTINCT d.producto_id
      FROM jsonb_array_elements(p_asignaciones) a
      JOIN public.detalle_orden d ON d.id = (a->>'detalle_orden_id')::uuid
     WHERE coalesce(nullif(a->>'cantidad_stock', '')::integer, 0) > 0
     ORDER BY d.producto_id
  LOOP
    SELECT * INTO product FROM public.productos WHERE id = product_id FOR UPDATE;
    SELECT coalesce(sum(nullif(a->>'cantidad_stock', '')::integer), 0) INTO requested_stock
      FROM jsonb_array_elements(p_asignaciones) a
      JOIN public.detalle_orden d ON d.id = (a->>'detalle_orden_id')::uuid
     WHERE d.producto_id = product_id;
    IF product.activo IS DISTINCT FROM true OR product.controla_inventario IS DISTINCT FROM true THEN
      RAISE EXCEPTION 'product_unavailable' USING ERRCODE = '22023';
    END IF;
    IF product.stock_actual IS NULL OR product.stock_actual < requested_stock THEN
      RETURN private_sync.record_critical_result(
        p_idempotency_key, 'payment', req, jsonb_build_object('status', 'rejected', 'reason', 'insufficient_stock')
      );
    END IF;
  END LOOP;

  INSERT INTO public.transacciones(orden_id, monto, metodo_pago, idempotency_key)
    VALUES (p_orden_id, p_monto, p_metodo_pago, p_idempotency_key) RETURNING id INTO tx;
  INSERT INTO public.pago_detalles(transaccion_id, detalle_orden_id, cantidad, monto)
    SELECT tx, (a->>'detalle_orden_id')::uuid, (a->>'cantidad')::integer, (a->>'monto')::bigint
      FROM jsonb_array_elements(p_asignaciones) a
     WHERE (a->>'monto')::bigint > 0;

  -- The stock movement and this payment are part of the same PostgreSQL transaction.
  FOR item IN
    SELECT a FROM jsonb_array_elements(p_asignaciones) a
     WHERE coalesce(nullif(a->>'cantidad_stock', '')::integer, 0) > 0
     ORDER BY a->>'detalle_orden_id'
  LOOP
    SELECT * INTO detail FROM public.detalle_orden WHERE id = (item->>'detalle_orden_id')::uuid;
    stock_qty := (item->>'cantidad_stock')::integer;
    movement_key := (item->>'stock_idempotency_key')::uuid;
    movement_result := private_sync.inventory_command(jsonb_build_object(
      'p_producto_id', detail.producto_id,
      'p_cantidad', -stock_qty,
      'p_tipo', 'venta',
      'p_idempotency_key', movement_key,
      'p_referencia_id', p_orden_id,
      'p_notas', 'Venta de orden ' || p_orden_id::text || ', transacción ' || tx::text
    ));
    IF movement_result->>'status' <> 'confirmed' THEN
      RAISE EXCEPTION 'inventory_movement_failed' USING ERRCODE = 'P0001';
    END IF;
    movement_id := (movement_result->>'movement_id')::uuid;
    INSERT INTO public.pago_detalle_stock(
      transaccion_id, detalle_orden_id, producto_id, cantidad, movimiento_id, idempotency_key
    ) VALUES (
      tx, detail.id, detail.producto_id, stock_qty, movement_id, movement_key
    );
  END LOOP;

  UPDATE public.detalle_orden d SET estado_pago = 'Pagado', updated_at = now()
   WHERE d.orden_id = p_orden_id AND d.estado_pago <> 'Cancelado'
     AND (SELECT coalesce(sum(pd.monto), 0) FROM public.pago_detalles pd WHERE pd.detalle_orden_id = d.id)
       >= d.cantidad::bigint * d.precio_unitario;

  IF paid + p_monto = total AND EXISTS (
    SELECT 1
      FROM public.detalle_orden d
      JOIN public.productos pr ON pr.id = d.producto_id
     WHERE d.orden_id = p_orden_id AND d.estado_pago <> 'Cancelado'
       AND pr.controla_inventario IS TRUE
       AND (SELECT coalesce(sum(pd.monto), 0) FROM public.pago_detalles pd WHERE pd.detalle_orden_id = d.id)
         >= d.cantidad::bigint * d.precio_unitario
       AND (SELECT coalesce(sum(ps.cantidad), 0) FROM public.pago_detalle_stock ps WHERE ps.detalle_orden_id = d.id)
         <> d.cantidad
  ) THEN
    RAISE EXCEPTION 'stock_allocation_incomplete' USING ERRCODE = '22023';
  END IF;
  IF paid + p_monto = total THEN
    UPDATE public.ordenes SET estado = 'Cerrada', fecha_cierre = now(), updated_at = now() WHERE id = p_orden_id;
  END IF;
  RETURN private_sync.record_critical_result(
    p_idempotency_key, 'payment', req,
    jsonb_build_object('status', 'confirmed', 'transaction_id', tx)
  );
END;
$$;

REVOKE ALL ON FUNCTION public.registrar_pago(uuid, bigint, text, uuid, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.registrar_pago(uuid, bigint, text, uuid, jsonb) TO authenticated;

-- Cancels only an unpaid line. It is idempotent and cannot silently reverse a payment.
CREATE OR REPLACE FUNCTION public.cancelar_detalle_orden(p_detalle_orden_id uuid, p_idempotency_key uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  req jsonb := jsonb_build_object('detail_id', p_detalle_orden_id);
  receipt private_sync.critical_receipts%rowtype;
  line public.detalle_orden%rowtype;
  order_state text;
BEGIN
  IF auth.uid() IS NULL OR coalesce(public.auth_role(), '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_detalle_orden_id IS NULL OR p_idempotency_key IS NULL THEN
    RAISE EXCEPTION 'invalid_command' USING ERRCODE = '22023';
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(78645231);
  SELECT * INTO receipt FROM private_sync.critical_receipts WHERE key = p_idempotency_key;
  IF FOUND THEN
    IF receipt.actor_id <> auth.uid() OR receipt.entity <> 'cancel_detail' OR receipt.request <> req THEN
      RAISE EXCEPTION 'idempotency_mismatch' USING ERRCODE = '22023';
    END IF;
    RETURN receipt.result;
  END IF;
  SELECT * INTO line FROM public.detalle_orden WHERE id = p_detalle_orden_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN private_sync.record_critical_result(p_idempotency_key, 'cancel_detail', req,
      jsonb_build_object('status', 'rejected', 'reason', 'detail_not_found'));
  END IF;
  SELECT estado INTO order_state FROM public.ordenes WHERE id = line.orden_id FOR UPDATE;
  IF order_state IS DISTINCT FROM 'Abierta' OR line.estado_pago <> 'Pendiente'
    OR EXISTS (SELECT 1 FROM public.pago_detalles WHERE detalle_orden_id = line.id)
    OR EXISTS (SELECT 1 FROM public.pago_detalle_stock WHERE detalle_orden_id = line.id) THEN
    RETURN private_sync.record_critical_result(p_idempotency_key, 'cancel_detail', req,
      jsonb_build_object('status', 'rejected', 'reason', 'line_has_payment_or_order_closed'));
  END IF;
  UPDATE public.detalle_orden SET estado_pago = 'Cancelado', updated_at = now() WHERE id = line.id;
  RETURN private_sync.record_critical_result(p_idempotency_key, 'cancel_detail', req,
    jsonb_build_object('status', 'confirmed', 'detail_id', line.id));
END;
$$;

REVOKE ALL ON FUNCTION public.cancelar_detalle_orden(uuid, uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.cancelar_detalle_orden(uuid, uuid) TO authenticated;
