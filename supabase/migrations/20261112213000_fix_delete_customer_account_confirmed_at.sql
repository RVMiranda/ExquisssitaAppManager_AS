-- Migration: 20261112213000_fix_delete_customer_account_confirmed_at.sql
-- Fixes PostgreSQL 17 generated column error in delete_customer_account where confirmed_at can only be updated to DEFAULT.

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
  -- Notice: confirmed_at is updated to DEFAULT to satisfy Postgres 17 generated column rules.
  UPDATE auth.users SET email = NULL, encrypted_password = NULL, email_confirmed_at = NULL,
    confirmed_at = DEFAULT, phone = NULL, phone_confirmed_at = NULL,
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
