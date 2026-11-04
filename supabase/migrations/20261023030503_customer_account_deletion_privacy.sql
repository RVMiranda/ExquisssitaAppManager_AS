-- Sprint 05: customer-initiated account deletion with an auditable loyalty balance.
-- Keep the customer and Auth rows as tombstones because historical visits,
-- redemptions, receipts, and manager audit rows use RESTRICT foreign keys.

ALTER TABLE public.clientes
  ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE TABLE IF NOT EXISTS public.correos_bloqueados_registro (
  email_hash text PRIMARY KEY CHECK (email_hash ~ '^[0-9a-f]{64}$'),
  blocked_at timestamptz NOT NULL DEFAULT pg_catalog.now()
);
ALTER TABLE public.correos_bloqueados_registro ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.correos_bloqueados_registro FROM PUBLIC, anon, authenticated;

-- Keep the keyed email fingerprint resistant to dictionary attacks. The key
-- itself is encrypted in Vault and never exposed through the Data API.
CREATE TABLE IF NOT EXISTS private_customer.registration_email_block_key (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  secret_id uuid NOT NULL UNIQUE
);
ALTER TABLE private_customer.registration_email_block_key ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private_customer.registration_email_block_key FROM PUBLIC, anon, authenticated;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM private_customer.registration_email_block_key WHERE singleton) THEN
    INSERT INTO private_customer.registration_email_block_key(singleton, secret_id)
    VALUES (true, vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'),
      'customer-registration-email-block-key', 'Pepper for blocked customer registration emails'));
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION private_customer.registration_email_hash(p_email text)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE normalized_email text := lower(btrim(p_email)); pepper text;
BEGIN
  IF normalized_email IS NULL OR normalized_email = '' THEN RETURN NULL; END IF;
  SELECT s.decrypted_secret INTO pepper
    FROM private_customer.registration_email_block_key k
    JOIN vault.decrypted_secrets s ON s.id = k.secret_id
   WHERE k.singleton;
  IF pepper IS NULL THEN RAISE EXCEPTION 'email_block_key_unavailable' USING ERRCODE = 'P0001'; END IF;
  RETURN encode(extensions.hmac(normalized_email, pepper, 'sha256'), 'hex');
END;
$$;
REVOKE ALL ON FUNCTION private_customer.registration_email_hash(text) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION private_customer.reject_blocked_signup_email()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE normalized_email text;
BEGIN
  normalized_email := lower(btrim(NEW.email));
  IF normalized_email IS NOT NULL AND normalized_email <> '' AND EXISTS (
    SELECT 1 FROM public.correos_bloqueados_registro b
     WHERE b.email_hash = private_customer.registration_email_hash(normalized_email)
  ) THEN
    RAISE EXCEPTION 'email_registration_blocked' USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_customer.reject_blocked_signup_email() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS block_deleted_customer_signup ON auth.users;
CREATE TRIGGER block_deleted_customer_signup
  BEFORE INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION private_customer.reject_blocked_signup_email();

-- A deleted customer cannot use an already-issued JWT to read their old data.
DROP POLICY IF EXISTS clientes_select_own ON public.clientes;
CREATE POLICY clientes_select_own ON public.clientes
  FOR SELECT TO authenticated
  USING (user_id = (SELECT auth.uid()) AND deleted_at IS NULL);
DROP POLICY IF EXISTS visitas_clientes_select_own ON public.visitas_clientes;
CREATE POLICY visitas_clientes_select_own ON public.visitas_clientes
  FOR SELECT TO authenticated
  USING (
    usuario_id = (SELECT auth.uid())
    AND EXISTS (
      SELECT 1 FROM public.clientes c
       WHERE c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
    )
  );
DROP POLICY IF EXISTS puntos_movimientos_customer_select ON public.puntos_movimientos;
CREATE POLICY puntos_movimientos_customer_select ON public.puntos_movimientos
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = puntos_movimientos.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));
DROP POLICY IF EXISTS canjes_recompensas_customer_select ON public.canjes_recompensas;
CREATE POLICY canjes_recompensas_customer_select ON public.canjes_recompensas
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = canjes_recompensas.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));

-- Freeze the append-only loyalty ledger after customer deletion. This covers
-- Manager adjustments and other server-side writers that bypass customer RLS.
CREATE OR REPLACE FUNCTION private_sync.apply_loyalty_delta()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE balance integer; customer_deleted_at timestamptz;
BEGIN
  SELECT c.puntos_lealtad, c.deleted_at INTO balance, customer_deleted_at
    FROM public.clientes c WHERE c.id = NEW.cliente_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_not_found' USING ERRCODE = 'P0002'; END IF;
  IF customer_deleted_at IS NOT NULL THEN
    RAISE EXCEPTION 'customer_account_deleted' USING ERRCODE = '42501';
  END IF;
  IF balance + NEW.puntos_delta < 0 THEN
    RAISE EXCEPTION 'insufficient_loyalty_points' USING ERRCODE = '22023';
  END IF;
  UPDATE public.clientes SET puntos_lealtad = balance + NEW.puntos_delta,
    updated_at = pg_catalog.now() WHERE id = NEW.cliente_id;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_sync.apply_loyalty_delta() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.ensure_customer_profile(p_nombre text, p_telefono text DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); confirmed_at timestamptz; profile public.clientes%ROWTYPE;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;
  IF EXISTS (SELECT 1 FROM public.clientes WHERE user_id = actor AND deleted_at IS NOT NULL) THEN
    RAISE EXCEPTION 'customer_account_deleted' USING ERRCODE = '42501';
  END IF;
  SELECT u.email_confirmed_at INTO confirmed_at FROM auth.users u WHERE u.id = actor AND u.deleted_at IS NULL;
  IF confirmed_at IS NULL THEN RAISE EXCEPTION 'email_not_verified' USING ERRCODE = '42501'; END IF;
  IF p_nombre IS NULL OR length(btrim(p_nombre)) = 0 OR length(p_nombre) > 160 THEN
    RAISE EXCEPTION 'invalid_customer_name' USING ERRCODE = '22023';
  END IF;
  INSERT INTO public.clientes(user_id, nombre, telefono)
  VALUES (actor, btrim(p_nombre), nullif(btrim(p_telefono), ''))
  ON CONFLICT (user_id) WHERE user_id IS NOT NULL DO NOTHING;
  SELECT c.* INTO profile FROM public.clientes c WHERE c.user_id = actor AND c.deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_account_deleted' USING ERRCODE = '42501'; END IF;
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
  IF EXISTS (SELECT 1 FROM public.clientes WHERE user_id = actor AND deleted_at IS NOT NULL) THEN
    RAISE EXCEPTION 'customer_account_deleted' USING ERRCODE = '42501';
  END IF;
  SELECT u.email_confirmed_at INTO confirmed_at FROM auth.users u WHERE u.id = actor AND u.deleted_at IS NULL;
  IF confirmed_at IS NULL THEN RAISE EXCEPTION 'email_not_verified' USING ERRCODE = '42501'; END IF;
  IF p_nombre IS NULL OR length(btrim(p_nombre)) = 0 OR length(p_nombre) > 160 THEN
    RAISE EXCEPTION 'invalid_customer_profile' USING ERRCODE = '22023';
  END IF;
  UPDATE public.clientes SET nombre = btrim(p_nombre), telefono = nullif(btrim(p_telefono), '')
   WHERE user_id = actor AND deleted_at IS NULL RETURNING * INTO profile;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002'; END IF;
  RETURN pg_catalog.jsonb_build_object('id', profile.id, 'nombre', profile.nombre,
    'telefono', profile.telefono, 'puntos_lealtad', profile.puntos_lealtad,
    'visitas_totales', profile.visitas_totales);
END;
$$;
REVOKE ALL ON FUNCTION public.update_customer_profile(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_customer_profile(text, text) TO authenticated;

-- The secret QR is destroyed from Vault and the customer digest is revoked.
CREATE OR REPLACE FUNCTION private_customer.get_or_create_qr()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  customer_id uuid;
  existing_hash text;
  vault_id uuid;
  token text;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'verified_customer_required' USING ERRCODE = '42501'; END IF;
  IF EXISTS (SELECT 1 FROM public.clientes WHERE user_id = actor AND deleted_at IS NOT NULL) THEN
    RAISE EXCEPTION 'customer_account_deleted' USING ERRCODE = '42501';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = actor AND email_confirmed_at IS NOT NULL AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'verified_customer_required' USING ERRCODE = '42501';
  END IF;
  SELECT id, qr_token_hash INTO customer_id, existing_hash
    FROM public.clientes WHERE user_id = actor AND deleted_at IS NULL FOR UPDATE;
  IF customer_id IS NULL THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002'; END IF;

  SELECT secret_id INTO vault_id FROM private_customer.qr_secrets WHERE cliente_id = customer_id FOR UPDATE;
  IF vault_id IS NOT NULL THEN
    SELECT decrypted_secret INTO token FROM vault.decrypted_secrets WHERE id = vault_id;
    IF token IS NULL OR token !~ '^[0-9a-f]{64}$'
      OR existing_hash IS DISTINCT FROM encode(extensions.digest(token, 'sha256'), 'hex') THEN
      RAISE EXCEPTION 'customer_qr_recovery_unavailable' USING ERRCODE = 'P0001';
    END IF;
  ELSE
    IF existing_hash IS NOT NULL THEN
      RAISE EXCEPTION 'legacy_qr_requires_recovery' USING ERRCODE = 'P0001';
    END IF;
    token := encode(extensions.gen_random_bytes(32), 'hex');
    vault_id := vault.create_secret(token, 'customer-qr-' || customer_id::text, 'Permanent customer card');
    INSERT INTO private_customer.qr_secrets(cliente_id, secret_id) VALUES (customer_id, vault_id);
    UPDATE public.clientes SET qr_token_hash = encode(extensions.digest(token, 'sha256'), 'hex')
     WHERE id = customer_id;
  END IF;
  RETURN pg_catalog.jsonb_build_object('cliente_id', customer_id, 'qr_token', token);
END;
$$;
REVOKE ALL ON FUNCTION private_customer.get_or_create_qr() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private_customer.get_or_create_qr() TO authenticated;

CREATE OR REPLACE FUNCTION public.delete_customer_account()
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  original_email text;
  customer public.clientes%ROWTYPE;
  vault_secret_id uuid;
  balance integer;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;

  IF EXISTS (SELECT 1 FROM public.user_roles WHERE user_id = actor AND role IN ('admin', 'gerente'))
     OR EXISTS (SELECT 1 FROM public.empleados WHERE user_id = actor AND activo) THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;

  SELECT email INTO original_email FROM auth.users WHERE id = actor FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'auth_user_not_found' USING ERRCODE = '42501'; END IF;
  SELECT * INTO customer FROM public.clientes WHERE user_id = actor FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002'; END IF;

  IF customer.deleted_at IS NOT NULL THEN
    DELETE FROM auth.sessions WHERE user_id = actor;
    RETURN pg_catalog.jsonb_build_object('status', 'already_deleted', 'cliente_id', customer.id);
  END IF;

  IF original_email IS NOT NULL AND btrim(original_email) <> '' THEN
    INSERT INTO public.correos_bloqueados_registro(email_hash)
    VALUES (private_customer.registration_email_hash(original_email))
    ON CONFLICT (email_hash) DO NOTHING;
  END IF;

  balance := customer.puntos_lealtad;
  IF balance > 0 THEN
    INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo,
      idempotency_key, registrado_por)
    VALUES (customer.id, -balance, 'ajuste', 'Baja de cuenta: cancelación de saldo',
      extensions.gen_random_uuid(), actor);
  END IF;

  SELECT secret_id INTO vault_secret_id FROM private_customer.qr_secrets
   WHERE cliente_id = customer.id FOR UPDATE;
  IF vault_secret_id IS NOT NULL THEN
    DELETE FROM vault.secrets WHERE id = vault_secret_id;
    DELETE FROM private_customer.qr_secrets WHERE cliente_id = customer.id;
  END IF;

  UPDATE public.clientes SET nombre = 'Usuario dado de baja', telefono = NULL,
    qr_token_hash = NULL, deleted_at = pg_catalog.now(), updated_at = pg_catalog.now(),
    sync_version = sync_version + 1
   WHERE id = customer.id;

  -- Keep Auth's immutable ID for foreign-keyed historical records, but use
  -- Supabase's native soft-delete marker and erase all authentication PII.
  UPDATE auth.users SET email = NULL, encrypted_password = NULL, email_confirmed_at = NULL,
    confirmed_at = NULL, phone = NULL, phone_confirmed_at = NULL,
    raw_user_meta_data = '{}'::jsonb, raw_app_meta_data = '{}'::jsonb,
    confirmation_token = '', confirmation_sent_at = NULL,
    recovery_token = '', recovery_sent_at = NULL,
    email_change_token_new = '', email_change = '', email_change_sent_at = NULL,
    email_change_token_current = '', email_change_confirm_status = 0,
    phone_change = '', phone_change_token = '', phone_change_sent_at = NULL,
    reauthentication_token = '', reauthentication_sent_at = NULL,
    invited_at = NULL, last_sign_in_at = NULL, banned_until = NULL,
    deleted_at = pg_catalog.now(), updated_at = pg_catalog.now()
   WHERE id = actor;

  DELETE FROM public.user_roles WHERE user_id = actor;
  DELETE FROM auth.identities WHERE user_id = actor;
  DELETE FROM auth.sessions WHERE user_id = actor;
  DELETE FROM auth.one_time_tokens WHERE user_id = actor;
  DELETE FROM auth.mfa_factors WHERE user_id = actor;
  DELETE FROM auth.mfa_recovery_code_sets WHERE user_id = actor;
  DELETE FROM auth.webauthn_credentials WHERE user_id = actor;
  DELETE FROM auth.webauthn_challenges WHERE user_id = actor;
  DELETE FROM auth.oauth_authorizations WHERE user_id = actor;
  DELETE FROM auth.oauth_consents WHERE user_id = actor;

  RETURN pg_catalog.jsonb_build_object('status', 'deleted', 'cliente_id', customer.id,
    'puntos_cancelados', balance);
END;
$$;
REVOKE ALL ON FUNCTION public.delete_customer_account() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_customer_account() TO authenticated;
COMMENT ON FUNCTION public.delete_customer_account() IS
  'Customer-only idempotent soft delete. Anonymizes customer/Auth identity, revokes QR and sessions, blocks email re-registration, and appends a zero-balance loyalty adjustment.';

NOTIFY pgrst, 'reload schema';
