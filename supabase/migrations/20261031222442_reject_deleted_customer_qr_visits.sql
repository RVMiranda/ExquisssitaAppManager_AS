-- Keep manager QR visits aligned with customer-account deletion.
-- A SECURITY DEFINER RPC is required because qr_token_hash is intentionally
-- not selectable by authenticated clients; authorization remains enforced by
-- the RPC body and EXECUTE remains limited to authenticated.
CREATE OR REPLACE FUNCTION public.consultar_cliente_qr(p_qr_token text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  role_name text := (SELECT public.auth_role());
  customer public.clientes%ROWTYPE;
BEGIN
  IF actor IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;

  IF p_qr_token IS NULL OR pg_catalog.length(p_qr_token) <> 64 THEN
    RAISE EXCEPTION 'invalid_qr_token' USING ERRCODE = '22023';
  END IF;

  SELECT c.* INTO customer
  FROM public.clientes AS c
  WHERE c.qr_token_hash = pg_catalog.encode(
          extensions.digest(p_qr_token, 'sha256'), 'hex'
        )
    AND c.deleted_at IS NULL;

  IF NOT FOUND OR customer.user_id IS NULL OR NOT EXISTS (
    SELECT 1
    FROM auth.users AS u
    WHERE u.id = customer.user_id
      AND u.email_confirmed_at IS NOT NULL
  ) THEN
    RETURN NULL;
  END IF;

  RETURN pg_catalog.jsonb_build_object(
    'id', customer.id,
    'nombre', customer.nombre,
    'telefono', customer.telefono,
    'visitas_totales', customer.visitas_totales,
    'puntos_lealtad', customer.puntos_lealtad,
    'fecha_registro', customer.fecha_registro,
    'created_at', customer.created_at
  );
END;
$$;

REVOKE ALL ON FUNCTION public.consultar_cliente_qr(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.consultar_cliente_qr(text) TO authenticated;
COMMENT ON FUNCTION public.consultar_cliente_qr(text) IS
  'Admin/active-manager only. Retrieves a verified, non-deleted customer by QR token without exposing its digest.';

CREATE OR REPLACE FUNCTION public.registrar_visita_qr(p_qr_token text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  role_name text := (SELECT public.auth_role());
  customer public.clientes%ROWTYPE;
  business_date date;
  visit_id uuid;
  points integer := 10;
BEGIN
  IF actor IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;

  IF p_qr_token IS NULL OR pg_catalog.length(p_qr_token) <> 64 THEN
    RAISE EXCEPTION 'invalid_qr_token' USING ERRCODE = '22023';
  END IF;

  business_date := (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date;

  SELECT c.* INTO customer
  FROM public.clientes AS c
  WHERE c.qr_token_hash = pg_catalog.encode(
          extensions.digest(p_qr_token, 'sha256'), 'hex'
        )
    AND c.deleted_at IS NULL
  FOR UPDATE;

  IF NOT FOUND OR customer.user_id IS NULL OR NOT EXISTS (
    SELECT 1
    FROM auth.users AS u
    WHERE u.id = customer.user_id
      AND u.email_confirmed_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'qr_customer_not_found' USING ERRCODE = 'P0002';
  END IF;

  INSERT INTO public.visitas_clientes (
    cliente_id, usuario_id, fecha, puntos_otorgados, registrado_por
  )
  VALUES (customer.id, customer.user_id, business_date, points, actor)
  ON CONFLICT (usuario_id, fecha) WHERE usuario_id IS NOT NULL DO NOTHING
  RETURNING id INTO visit_id;

  IF visit_id IS NULL THEN
    RETURN pg_catalog.jsonb_build_object(
      'registered', false,
      'already_registered', true,
      'cliente_id', customer.id,
      'fecha', business_date,
      'visitas_totales', customer.visitas_totales,
      'puntos_lealtad', customer.puntos_lealtad
    );
  END IF;

  INSERT INTO public.puntos_movimientos (
    cliente_id, puntos_delta, tipo, motivo,
    referencia_id, idempotency_key, registrado_por
  )
  VALUES (
    customer.id, points, 'visita', 'Visita acreditada',
    visit_id, visit_id, actor
  );

  UPDATE public.clientes
  SET visitas_totales = visitas_totales + 1
  WHERE id = customer.id
  RETURNING * INTO customer;

  RETURN pg_catalog.jsonb_build_object(
    'registered', true,
    'already_registered', false,
    'cliente_id', customer.id,
    'fecha', business_date,
    'puntos_otorgados', points,
    'visitas_totales', customer.visitas_totales,
    'puntos_lealtad', customer.puntos_lealtad
  );
END;
$$;

REVOKE ALL ON FUNCTION public.registrar_visita_qr(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_visita_qr(text) TO authenticated;
COMMENT ON FUNCTION public.registrar_visita_qr(text) IS
  'Admin/active-manager only. A verified, non-deleted QR customer can earn one visit and ledger credit per CDMX business date.';

NOTIFY pgrst, 'reload schema';
