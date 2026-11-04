-- Enforce one redemption per reward and customer in each CDMX calendar semester.
-- The existing advisory lock serializes reward redemptions before checking the ledger.
CREATE OR REPLACE FUNCTION public.redeem_loyalty_reward(
  p_qr_token text, p_recompensa_id uuid, p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  role_name text := (SELECT public.auth_role());
  customer public.clientes%ROWTYPE;
  reward public.recompensas%ROWTYPE;
  previous public.canjes_recompensas%ROWTYPE;
  redemption public.canjes_recompensas%ROWTYPE;
  request_hash text;
  business_date date;
  period_start date;
  period_end date;
BEGIN
  IF actor IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_qr_token IS NULL OR length(p_qr_token) <> 64 OR p_recompensa_id IS NULL
     OR p_idempotency_key IS NULL THEN
    RAISE EXCEPTION 'invalid_command' USING ERRCODE = '22023';
  END IF;

  request_hash := encode(extensions.digest(p_qr_token || ':' || p_recompensa_id::text, 'sha256'), 'hex');
  PERFORM pg_catalog.pg_advisory_xact_lock(78645231);

  -- Preserve safe retries of the original request before applying the semester rule.
  SELECT * INTO previous FROM public.canjes_recompensas WHERE idempotency_key = p_idempotency_key;
  IF FOUND THEN
    IF previous.canjeado_por <> actor OR previous.solicitud_hash <> request_hash THEN
      RAISE EXCEPTION 'idempotency_mismatch' USING ERRCODE = '22023';
    END IF;
    RETURN pg_catalog.jsonb_build_object('status', 'confirmed', 'canje_id', previous.id,
      'cliente_id', previous.cliente_id, 'recompensa_id', previous.recompensa_id,
      'puntos_costo', previous.puntos_costo, 'already_processed', true);
  END IF;

  SELECT * INTO reward FROM public.recompensas WHERE id = p_recompensa_id AND activo FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'reward_unavailable' USING ERRCODE = '22023'; END IF;

  SELECT * INTO customer FROM public.clientes
   WHERE qr_token_hash = encode(extensions.digest(p_qr_token, 'sha256'), 'hex')
   FOR UPDATE;
  IF NOT FOUND OR customer.user_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM auth.users u WHERE u.id = customer.user_id AND u.email_confirmed_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'qr_customer_not_found' USING ERRCODE = 'P0002';
  END IF;

  business_date := (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date;
  period_start := pg_catalog.make_date(
    extract(year FROM business_date)::integer,
    CASE WHEN extract(month FROM business_date) <= 6 THEN 1 ELSE 7 END,
    1
  );
  period_end := (period_start + interval '6 months')::date;

  IF EXISTS (
    SELECT 1
      FROM public.canjes_recompensas c
     WHERE c.cliente_id = customer.id
       AND c.recompensa_id = reward.id
       AND c.created_at >= (period_start::timestamp AT TIME ZONE 'America/Mexico_City')
       AND c.created_at < (period_end::timestamp AT TIME ZONE 'America/Mexico_City')
  ) THEN
    RAISE EXCEPTION 'reward_already_redeemed_this_period' USING ERRCODE = '22023';
  END IF;

  INSERT INTO public.canjes_recompensas(cliente_id, recompensa_id, puntos_costo,
    idempotency_key, solicitud_hash, canjeado_por)
  VALUES (customer.id, reward.id, reward.costo_puntos, p_idempotency_key,
    request_hash, actor) RETURNING * INTO redemption;
  INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo, referencia_id,
    idempotency_key, registrado_por)
  VALUES (customer.id, -reward.costo_puntos, 'canje', 'Canje: ' || reward.titulo, redemption.id,
    p_idempotency_key, actor);
  RETURN pg_catalog.jsonb_build_object('status', 'confirmed', 'canje_id', redemption.id,
    'cliente_id', customer.id, 'recompensa_id', reward.id,
    'puntos_costo', reward.costo_puntos, 'already_processed', false,
    'puntos_restantes', customer.puntos_lealtad - reward.costo_puntos);
END;
$$;
REVOKE ALL ON FUNCTION public.redeem_loyalty_reward(text, uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.redeem_loyalty_reward(text, uuid, uuid) TO authenticated;
COMMENT ON FUNCTION public.redeem_loyalty_reward(text, uuid, uuid) IS
  'Online-only idempotent redemption. Rejects a second redemption of the same reward by the same customer within the current Jan-Jun or Jul-Dec America/Mexico_City semester.';

