-- Depends on all three Release 03 migrations in ExquisssitaAppManager.
-- Add this file to the canonical Manager migration chain before deployment.
-- Never apply it alone to the shared production database.
BEGIN;

CREATE EXTENSION IF NOT EXISTS supabase_vault WITH SCHEMA vault;
CREATE SCHEMA IF NOT EXISTS private_customer;
REVOKE ALL ON SCHEMA private_customer FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA private_customer TO authenticated;

CREATE TABLE private_customer.qr_secrets (
  cliente_id uuid PRIMARY KEY REFERENCES public.clientes(id) ON DELETE CASCADE,
  secret_id uuid NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE private_customer.qr_secrets ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private_customer.qr_secrets FROM PUBLIC, anon, authenticated;

-- Privileged implementation stays outside the exposed API schema. The only
-- authority comes from the verified JWT identity, never caller-supplied IDs.
CREATE FUNCTION private_customer.get_or_create_qr()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  actor uuid := (SELECT auth.uid());
  customer_id uuid;
  existing_hash text;
  vault_id uuid;
  token text;
BEGIN
  IF actor IS NULL OR NOT EXISTS (
    SELECT 1 FROM auth.users WHERE id = actor AND email_confirmed_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'verified_customer_required' USING ERRCODE = '42501';
  END IF;

  SELECT id, qr_token_hash INTO customer_id, existing_hash
    FROM public.clientes WHERE user_id = actor FOR UPDATE;
  IF customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = 'P0002';
  END IF;

  SELECT secret_id INTO vault_id FROM private_customer.qr_secrets WHERE cliente_id = customer_id;
  IF vault_id IS NOT NULL THEN
    SELECT decrypted_secret INTO token FROM vault.decrypted_secrets WHERE id = vault_id;
    IF token IS NULL OR token !~ '^[0-9a-f]{64}$'
      OR existing_hash IS DISTINCT FROM encode(extensions.digest(token, 'sha256'), 'hex') THEN
      -- Fail closed: never silently issue another card after corruption or rotation.
      RAISE EXCEPTION 'customer_qr_recovery_unavailable' USING ERRCODE = 'P0001';
    END IF;
  ELSE
    IF existing_hash IS NOT NULL THEN
      -- Existing hash-only cards require a separate verified recovery procedure.
      -- It is impossible to recover their plaintext by hashing again.
      RAISE EXCEPTION 'legacy_qr_requires_recovery' USING ERRCODE = 'P0001';
    END IF;
    token := encode(extensions.gen_random_bytes(32), 'hex');
    vault_id := vault.create_secret(token, 'customer-qr-' || customer_id::text, 'Permanent customer card');
    INSERT INTO private_customer.qr_secrets(cliente_id, secret_id) VALUES (customer_id, vault_id);
    UPDATE public.clientes
      SET qr_token_hash = encode(extensions.digest(token, 'sha256'), 'hex')
      WHERE id = customer_id;
  END IF;
  RETURN jsonb_build_object('cliente_id', customer_id, 'qr_token', token);
END;
$$;
REVOKE ALL ON FUNCTION private_customer.get_or_create_qr() FROM PUBLIC, anon, authenticated;
-- The public SECURITY INVOKER wrapper executes this helper as the caller.
-- It remains outside PostgREST's exposed schemas and accepts no caller ID.
GRANT EXECUTE ON FUNCTION private_customer.get_or_create_qr() TO authenticated;

CREATE FUNCTION public.get_or_create_customer_qr_token()
RETURNS jsonb LANGUAGE sql SECURITY INVOKER SET search_path = ''
AS $$ SELECT private_customer.get_or_create_qr(); $$;
REVOKE ALL ON FUNCTION public.get_or_create_customer_qr_token() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_or_create_customer_qr_token() TO authenticated;

-- Pre-release legacy API must not invalidate permanent cards from old clients.
REVOKE ALL ON FUNCTION public.rotate_customer_qr_token() FROM PUBLIC, anon, authenticated;
COMMENT ON FUNCTION public.get_or_create_customer_qr_token() IS
  'Owner-only idempotent QR recovery; unchanged across installs/devices; no rotation.';
NOTIFY pgrst, 'reload schema';
COMMIT;
