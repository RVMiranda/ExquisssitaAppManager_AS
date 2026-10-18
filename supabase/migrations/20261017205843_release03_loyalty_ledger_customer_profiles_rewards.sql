-- Release 03 / B5 + customer-auth part of B4. All loyalty writes are online,
-- owner/actor-checked RPCs. Balance columns remain a cache of the append-only
-- ledger and are never writable from PostgREST or generic sync.

UPDATE public.clientes SET puntos_lealtad = 0 WHERE puntos_lealtad IS NULL;
UPDATE public.clientes SET visitas_totales = 0 WHERE visitas_totales IS NULL;
ALTER TABLE public.clientes ALTER COLUMN puntos_lealtad SET DEFAULT 0;
ALTER TABLE public.clientes ALTER COLUMN puntos_lealtad SET NOT NULL;
ALTER TABLE public.clientes ALTER COLUMN visitas_totales SET DEFAULT 0;
ALTER TABLE public.clientes ALTER COLUMN visitas_totales SET NOT NULL;

CREATE TABLE public.puntos_movimientos (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE RESTRICT,
  puntos_delta integer NOT NULL CHECK (puntos_delta <> 0),
  tipo text NOT NULL CHECK (tipo IN ('apertura', 'visita', 'canje', 'ajuste')),
  motivo text,
  referencia_id uuid,
  idempotency_key uuid NOT NULL UNIQUE,
  solicitud_hash text,
  registrado_por uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT puntos_movimientos_referencia_tipo_unique UNIQUE (tipo, referencia_id)
);
ALTER TABLE public.puntos_movimientos ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.recompensas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  titulo text NOT NULL CHECK (length(btrim(titulo)) > 0),
  descripcion text NOT NULL DEFAULT '',
  costo_puntos integer NOT NULL CHECK (costo_puntos > 0),
  activo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  sync_version bigint NOT NULL DEFAULT 1 CHECK (sync_version > 0)
);
ALTER TABLE public.recompensas ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.canjes_recompensas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE RESTRICT,
  recompensa_id uuid NOT NULL REFERENCES public.recompensas(id) ON DELETE RESTRICT,
  puntos_costo integer NOT NULL CHECK (puntos_costo > 0),
  idempotency_key uuid NOT NULL UNIQUE,
  solicitud_hash text NOT NULL,
  canjeado_por uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.canjes_recompensas ENABLE ROW LEVEL SECURITY;

-- Reconcile the current stored balance once before installing the ledger
-- trigger. Current remote audit found no customers, but this handles forward
-- deployment onto a populated copy without crediting old visits again.
INSERT INTO public.puntos_movimientos(
  cliente_id, puntos_delta, tipo, referencia_id, idempotency_key
)
SELECT c.id, c.puntos_lealtad, 'apertura', c.id, gen_random_uuid()
  FROM public.clientes c WHERE c.puntos_lealtad > 0
ON CONFLICT (tipo, referencia_id) DO NOTHING;

CREATE OR REPLACE FUNCTION private_sync.apply_loyalty_delta()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE balance integer;
BEGIN
  SELECT c.puntos_lealtad INTO balance FROM public.clientes c
   WHERE c.id = NEW.cliente_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_not_found' USING ERRCODE = 'P0002'; END IF;
  IF balance + NEW.puntos_delta < 0 THEN
    RAISE EXCEPTION 'insufficient_loyalty_points' USING ERRCODE = '22023';
  END IF;
  UPDATE public.clientes SET puntos_lealtad = balance + NEW.puntos_delta,
    updated_at = pg_catalog.now() WHERE id = NEW.cliente_id;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_sync.apply_loyalty_delta() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER puntos_movimientos_apply_balance
  BEFORE INSERT ON public.puntos_movimientos
  FOR EACH ROW EXECUTE FUNCTION private_sync.apply_loyalty_delta();

CREATE OR REPLACE FUNCTION private_sync.reject_loyalty_mutation()
RETURNS trigger
LANGUAGE plpgsql SET search_path = ''
AS $$
BEGIN
  RAISE EXCEPTION 'append_only' USING ERRCODE = '42501';
END;
$$;
REVOKE ALL ON FUNCTION private_sync.reject_loyalty_mutation() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER puntos_movimientos_append_only
  BEFORE UPDATE OR DELETE ON public.puntos_movimientos
  FOR EACH ROW EXECUTE FUNCTION private_sync.reject_loyalty_mutation();
CREATE TRIGGER puntos_movimientos_no_truncate
  BEFORE TRUNCATE ON public.puntos_movimientos
  FOR EACH STATEMENT EXECUTE FUNCTION private_sync.reject_loyalty_mutation();
CREATE TRIGGER visitas_clientes_append_only
  BEFORE UPDATE OR DELETE ON public.visitas_clientes
  FOR EACH ROW EXECUTE FUNCTION private_sync.reject_loyalty_mutation();
CREATE TRIGGER visitas_clientes_no_truncate
  BEFORE TRUNCATE ON public.visitas_clientes
  FOR EACH STATEMENT EXECUTE FUNCTION private_sync.reject_loyalty_mutation();
CREATE TRIGGER canjes_recompensas_append_only
  BEFORE UPDATE OR DELETE ON public.canjes_recompensas
  FOR EACH ROW EXECUTE FUNCTION private_sync.reject_loyalty_mutation();
CREATE TRIGGER canjes_recompensas_no_truncate
  BEFORE TRUNCATE ON public.canjes_recompensas
  FOR EACH STATEMENT EXECUTE FUNCTION private_sync.reject_loyalty_mutation();

CREATE INDEX puntos_movimientos_cliente_fecha_idx
  ON public.puntos_movimientos(cliente_id, created_at DESC, id);
CREATE INDEX canjes_recompensas_cliente_fecha_idx
  ON public.canjes_recompensas(cliente_id, created_at DESC, id);

DROP POLICY IF EXISTS visitas_clientes_staff_insert ON public.visitas_clientes;
DROP POLICY IF EXISTS visitas_clientes_staff_select ON public.visitas_clientes;
CREATE POLICY visitas_clientes_staff_select ON public.visitas_clientes
  FOR SELECT TO authenticated
  USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));
CREATE POLICY visitas_clientes_select_own ON public.visitas_clientes
  FOR SELECT TO authenticated USING (usuario_id = (SELECT auth.uid()));

CREATE POLICY puntos_movimientos_staff_select ON public.puntos_movimientos
  FOR SELECT TO authenticated
  USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));
CREATE POLICY puntos_movimientos_customer_select ON public.puntos_movimientos
  FOR SELECT TO authenticated
  USING (cliente_id IN (SELECT c.id FROM public.clientes c WHERE c.user_id = (SELECT auth.uid())));
CREATE POLICY recompensas_active_select ON public.recompensas
  FOR SELECT TO authenticated USING (activo OR (SELECT public.auth_role()) = 'admin');
CREATE POLICY canjes_recompensas_staff_select ON public.canjes_recompensas
  FOR SELECT TO authenticated
  USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));
CREATE POLICY canjes_recompensas_customer_select ON public.canjes_recompensas
  FOR SELECT TO authenticated
  USING (cliente_id IN (SELECT c.id FROM public.clientes c WHERE c.user_id = (SELECT auth.uid())));

REVOKE ALL PRIVILEGES ON TABLE public.visitas_clientes, public.puntos_movimientos,
  public.recompensas, public.canjes_recompensas FROM PUBLIC, anon, authenticated;
REVOKE ALL PRIVILEGES (id, cliente_id, fecha, puntos_otorgados, registrado_por,
  created_at, usuario_id, sync_version) ON TABLE public.visitas_clientes FROM PUBLIC, anon, authenticated;
GRANT SELECT (id, cliente_id, fecha, puntos_otorgados, registrado_por, created_at,
  usuario_id, sync_version) ON TABLE public.visitas_clientes TO authenticated;
REVOKE ALL PRIVILEGES (id, cliente_id, puntos_delta, tipo, motivo, referencia_id,
  idempotency_key, solicitud_hash, registrado_por, created_at) ON TABLE public.puntos_movimientos FROM PUBLIC, anon, authenticated;
GRANT SELECT (id, cliente_id, puntos_delta, tipo, motivo, referencia_id, created_at)
  ON TABLE public.puntos_movimientos TO authenticated;
REVOKE ALL PRIVILEGES (id, titulo, descripcion, costo_puntos, activo, created_at,
  updated_at, sync_version) ON TABLE public.recompensas FROM PUBLIC, anon, authenticated;
GRANT SELECT (id, titulo, descripcion, costo_puntos, activo, created_at, updated_at,
  sync_version) ON TABLE public.recompensas TO authenticated;
REVOKE ALL PRIVILEGES (id, cliente_id, recompensa_id, puntos_costo,
  idempotency_key, solicitud_hash, canjeado_por, created_at)
  ON TABLE public.canjes_recompensas FROM PUBLIC, anon, authenticated;
GRANT SELECT (id, cliente_id, recompensa_id, puntos_costo, created_at)
  ON TABLE public.canjes_recompensas TO authenticated;

-- Profile creation is only possible for a verified Auth identity and never
-- links by phone. Existing profiles are returned unchanged on retries.
CREATE OR REPLACE FUNCTION public.ensure_customer_profile(p_nombre text, p_telefono text DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); confirmed_at timestamptz; profile public.clientes%ROWTYPE;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;
  SELECT u.email_confirmed_at INTO confirmed_at FROM auth.users u WHERE u.id = actor;
  IF confirmed_at IS NULL THEN RAISE EXCEPTION 'email_not_verified' USING ERRCODE = '42501'; END IF;
  IF p_nombre IS NULL OR length(btrim(p_nombre)) = 0 OR length(p_nombre) > 160 THEN
    RAISE EXCEPTION 'invalid_customer_name' USING ERRCODE = '22023';
  END IF;
  INSERT INTO public.clientes(user_id, nombre, telefono)
  VALUES (actor, btrim(p_nombre), nullif(btrim(p_telefono), ''))
  ON CONFLICT (user_id) WHERE user_id IS NOT NULL DO NOTHING;
  SELECT c.* INTO profile FROM public.clientes c WHERE c.user_id = actor;
  RETURN pg_catalog.jsonb_build_object('id', profile.id, 'nombre', profile.nombre,
    'telefono', profile.telefono, 'puntos_lealtad', profile.puntos_lealtad,
    'visitas_totales', profile.visitas_totales);
END;
$$;
REVOKE ALL ON FUNCTION public.ensure_customer_profile(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ensure_customer_profile(text, text) TO authenticated;

CREATE OR REPLACE FUNCTION public.update_customer_profile(p_nombre text, p_telefono text DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); profile public.clientes%ROWTYPE; confirmed_at timestamptz;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;
  SELECT u.email_confirmed_at INTO confirmed_at FROM auth.users u WHERE u.id = actor;
  IF confirmed_at IS NULL THEN RAISE EXCEPTION 'email_not_verified' USING ERRCODE = '42501'; END IF;
  IF p_nombre IS NULL OR length(btrim(p_nombre)) = 0 OR length(p_nombre) > 160 THEN
    RAISE EXCEPTION 'invalid_customer_profile' USING ERRCODE = '22023';
  END IF;
  UPDATE public.clientes SET nombre = btrim(p_nombre), telefono = nullif(btrim(p_telefono), '')
   WHERE user_id = actor RETURNING * INTO profile;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002'; END IF;
  RETURN pg_catalog.jsonb_build_object('id', profile.id, 'nombre', profile.nombre,
    'telefono', profile.telefono, 'puntos_lealtad', profile.puntos_lealtad,
    'visitas_totales', profile.visitas_totales);
END;
$$;
REVOKE ALL ON FUNCTION public.update_customer_profile(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_customer_profile(text, text) TO authenticated;

CREATE OR REPLACE FUNCTION public.rotate_customer_qr_token()
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); token text; profile_id uuid;
BEGIN
  IF actor IS NULL OR NOT EXISTS (
    SELECT 1 FROM auth.users u WHERE u.id = actor AND u.email_confirmed_at IS NOT NULL
  ) THEN RAISE EXCEPTION 'verified_customer_required' USING ERRCODE = '42501'; END IF;
  SELECT id INTO profile_id FROM public.clientes WHERE user_id = actor FOR UPDATE;
  IF profile_id IS NULL THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002'; END IF;
  token := encode(extensions.gen_random_bytes(32), 'hex');
  UPDATE public.clientes SET qr_token_hash = encode(extensions.digest(token, 'sha256'), 'hex')
   WHERE id = profile_id;
  RETURN pg_catalog.jsonb_build_object('cliente_id', profile_id, 'qr_token', token);
END;
$$;
REVOKE ALL ON FUNCTION public.rotate_customer_qr_token() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rotate_customer_qr_token() TO authenticated;

CREATE OR REPLACE FUNCTION public.registrar_visita_qr(p_qr_token text)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid()); role_name text := (SELECT public.auth_role());
  customer public.clientes%ROWTYPE; business_date date;
  visit_id uuid; points integer := 10;
BEGIN
  IF actor IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_qr_token IS NULL OR length(p_qr_token) <> 64 THEN
    RAISE EXCEPTION 'invalid_qr_token' USING ERRCODE = '22023';
  END IF;
  business_date := (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date;
  SELECT c.* INTO customer FROM public.clientes c
   WHERE c.qr_token_hash = encode(extensions.digest(p_qr_token, 'sha256'), 'hex')
   FOR UPDATE;
  IF NOT FOUND OR customer.user_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM auth.users u WHERE u.id = customer.user_id AND u.email_confirmed_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'qr_customer_not_found' USING ERRCODE = 'P0002';
  END IF;
  INSERT INTO public.visitas_clientes(cliente_id, usuario_id, fecha, puntos_otorgados, registrado_por)
  VALUES (customer.id, customer.user_id, business_date, points, actor)
  ON CONFLICT (usuario_id, fecha) WHERE usuario_id IS NOT NULL DO NOTHING
  RETURNING id INTO visit_id;
  IF visit_id IS NULL THEN
    RETURN pg_catalog.jsonb_build_object('registered', false, 'already_registered', true,
      'cliente_id', customer.id, 'fecha', business_date);
  END IF;
  INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo,
    referencia_id, idempotency_key, registrado_por)
  VALUES (customer.id, points, 'visita', 'Visita acreditada', visit_id, visit_id, actor);
  UPDATE public.clientes SET visitas_totales = visitas_totales + 1 WHERE id = customer.id
    RETURNING * INTO customer;
  RETURN pg_catalog.jsonb_build_object('registered', true, 'already_registered', false,
    'cliente_id', customer.id, 'fecha', business_date, 'puntos_otorgados', points,
    'visitas_totales', customer.visitas_totales, 'puntos_lealtad', customer.puntos_lealtad);
END;
$$;
REVOKE ALL ON FUNCTION public.registrar_visita_qr(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_visita_qr(text) TO authenticated;
COMMENT ON FUNCTION public.registrar_visita_qr(text) IS
  'Admin/active-manager only. A verified QR customer can earn one visit and ledger credit per CDMX business date.';

CREATE OR REPLACE FUNCTION public.manage_loyalty_reward(
  p_id uuid, p_titulo text, p_descripcion text, p_costo_puntos integer,
  p_activo boolean, p_expected_version bigint
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE current_reward public.recompensas%ROWTYPE; result public.recompensas%ROWTYPE;
BEGIN
  IF auth.uid() IS NULL OR (SELECT public.auth_role()) IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_id IS NULL OR p_titulo IS NULL OR length(btrim(p_titulo)) = 0
     OR length(p_titulo) > 160 OR p_descripcion IS NULL OR p_costo_puntos IS NULL
     OR p_costo_puntos <= 0 OR p_activo IS NULL OR p_expected_version IS NULL THEN
    RAISE EXCEPTION 'invalid_reward' USING ERRCODE = '22023';
  END IF;
  SELECT * INTO current_reward FROM public.recompensas WHERE id = p_id FOR UPDATE;
  IF NOT FOUND THEN
    IF p_expected_version <> 0 THEN RAISE EXCEPTION 'version_conflict' USING ERRCODE = 'P0004'; END IF;
    INSERT INTO public.recompensas(id, titulo, descripcion, costo_puntos, activo)
    VALUES (p_id, btrim(p_titulo), btrim(p_descripcion), p_costo_puntos, p_activo)
    RETURNING * INTO result;
  ELSE
    IF current_reward.sync_version <> p_expected_version THEN
      RAISE EXCEPTION 'version_conflict' USING ERRCODE = 'P0004';
    END IF;
    UPDATE public.recompensas SET titulo = btrim(p_titulo), descripcion = btrim(p_descripcion),
      costo_puntos = p_costo_puntos, activo = p_activo, updated_at = pg_catalog.now(),
      sync_version = sync_version + 1
    WHERE id = p_id RETURNING * INTO result;
  END IF;
  RETURN pg_catalog.to_jsonb(result);
END;
$$;
REVOKE ALL ON FUNCTION public.manage_loyalty_reward(uuid, text, text, integer, boolean, bigint)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.manage_loyalty_reward(uuid, text, text, integer, boolean, bigint)
  TO authenticated;

CREATE OR REPLACE FUNCTION public.adjust_loyalty_points(
  p_cliente_id uuid, p_puntos_delta integer, p_motivo text, p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); previous public.puntos_movimientos%ROWTYPE;
  request_hash text; movement public.puntos_movimientos%ROWTYPE; balance integer;
BEGIN
  IF actor IS NULL OR (SELECT public.auth_role()) IS DISTINCT FROM 'admin' THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_cliente_id IS NULL OR p_puntos_delta IS NULL OR p_puntos_delta = 0
     OR p_motivo IS NULL OR length(btrim(p_motivo)) = 0 OR length(p_motivo) > 500
     OR p_idempotency_key IS NULL THEN
    RAISE EXCEPTION 'invalid_loyalty_adjustment' USING ERRCODE = '22023';
  END IF;
  request_hash := encode(extensions.digest(
    p_cliente_id::text || ':' || p_puntos_delta::text || ':' || btrim(p_motivo), 'sha256'), 'hex');
  PERFORM pg_catalog.pg_advisory_xact_lock(78645231);
  SELECT * INTO previous FROM public.puntos_movimientos WHERE idempotency_key = p_idempotency_key;
  IF FOUND THEN
    IF previous.registrado_por <> actor OR previous.solicitud_hash <> request_hash THEN
      RAISE EXCEPTION 'idempotency_mismatch' USING ERRCODE = '22023';
    END IF;
    SELECT puntos_lealtad INTO balance FROM public.clientes WHERE id = previous.cliente_id;
    RETURN pg_catalog.jsonb_build_object('status', 'confirmed', 'already_processed', true,
      'movement_id', previous.id, 'puntos_restantes', balance);
  END IF;
  INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo, referencia_id,
    idempotency_key, solicitud_hash, registrado_por)
  VALUES (p_cliente_id, p_puntos_delta, 'ajuste', btrim(p_motivo), NULL,
    p_idempotency_key, request_hash, actor) RETURNING * INTO movement;
  SELECT puntos_lealtad INTO balance FROM public.clientes WHERE id = p_cliente_id;
  RETURN pg_catalog.jsonb_build_object('status', 'confirmed', 'already_processed', false,
    'movement_id', movement.id, 'puntos_restantes', balance);
END;
$$;
REVOKE ALL ON FUNCTION public.adjust_loyalty_points(uuid, integer, text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.adjust_loyalty_points(uuid, integer, text, uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.redeem_loyalty_reward(
  p_qr_token text, p_recompensa_id uuid, p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid()); role_name text := (SELECT public.auth_role());
  customer public.clientes%ROWTYPE; reward public.recompensas%ROWTYPE;
  previous public.canjes_recompensas%ROWTYPE; request_hash text; redemption public.canjes_recompensas%ROWTYPE;
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
  'Online-only idempotent reward redemption. Server resolves QR and reward cost, locks balance, and rejects insufficient points.';

COMMENT ON TABLE public.puntos_movimientos IS
  'Append-only points ledger. Opening rows reconcile the prior cached balance; visits and redemptions write only through RPC.';
COMMENT ON TABLE public.recompensas IS
  'Points-only rewards; no monetary discounts or implicit order refunds are represented.';
COMMENT ON TABLE public.canjes_recompensas IS
  'Append-only redemption receipts; the current reward cost is snapshotted at redemption.';
