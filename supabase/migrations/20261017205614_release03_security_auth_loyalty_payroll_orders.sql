-- Release 03 / B1 + B4: secure customer profile boundaries and stop granting
-- staff roles to every Supabase Auth signup. Existing applied migrations remain
-- immutable; this migration is additive and safe for empty or populated data.

-- Customers are registered accounts. Refuse ambiguous historical ownership
-- before installing the one-profile-per-Auth-user constraint.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM public.clientes
     WHERE user_id IS NOT NULL
     GROUP BY user_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'clientes_duplicate_user_id_requires_manual_reconciliation';
  END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS clientes_user_id_uidx
  ON public.clientes(user_id) WHERE user_id IS NOT NULL;

-- The mobile customer app owns only the profile fields below. Balances,
-- identity, QR digests and sync metadata are server-owned.
DROP POLICY IF EXISTS clientes_admin_delete ON public.clientes;
DROP POLICY IF EXISTS clientes_select_own ON public.clientes;
DROP POLICY IF EXISTS clientes_staff_insert ON public.clientes;
DROP POLICY IF EXISTS clientes_staff_select ON public.clientes;
DROP POLICY IF EXISTS clientes_staff_update ON public.clientes;
DROP POLICY IF EXISTS clientes_update_own ON public.clientes;

CREATE POLICY clientes_select_own
  ON public.clientes FOR SELECT TO authenticated
  USING (user_id = (SELECT auth.uid()));

CREATE POLICY clientes_staff_select
  ON public.clientes FOR SELECT TO authenticated
  USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));

REVOKE ALL PRIVILEGES ON TABLE public.clientes FROM PUBLIC, anon, authenticated;
REVOKE ALL PRIVILEGES (id, user_id, nombre, telefono, visitas_totales,
  puntos_lealtad, fecha_registro, created_at, updated_at, qr_token_hash,
  sync_version) ON TABLE public.clientes FROM PUBLIC, anon, authenticated;
GRANT SELECT (id, user_id, nombre, telefono, visitas_totales, puntos_lealtad,
  fecha_registro, created_at, updated_at, sync_version)
  ON TABLE public.clientes TO authenticated;

-- The employee application cannot create/edit/delete customer profiles by
-- generic synchronization commands. Profile writes have dedicated RPCs.
-- Block historical and future QR digests from the generic change feed.
CREATE OR REPLACE FUNCTION private_sync.capture_change()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE image jsonb; row_version bigint;
BEGIN
  PERFORM pg_catalog.pg_advisory_xact_lock(78645231);
  IF TG_OP = 'DELETE' THEN
    image := pg_catalog.to_jsonb(OLD);
    row_version := OLD.sync_version + 1;
  ELSE
    image := pg_catalog.to_jsonb(NEW);
    row_version := NEW.sync_version;
  END IF;
  IF TG_TABLE_SCHEMA = 'public' AND TG_TABLE_NAME = 'clientes' THEN
    image := image - 'qr_token_hash';
  END IF;
  INSERT INTO private_sync.changes(entity, entity_id, version, deleted, data)
  VALUES (TG_TABLE_NAME, image->>'id', row_version, TG_OP = 'DELETE', image);
  RETURN NULL;
END;
$$;
REVOKE ALL ON FUNCTION private_sync.capture_change() FROM PUBLIC, anon, authenticated;
REVOKE TRIGGER, REFERENCES, TRUNCATE ON ALL TABLES IN SCHEMA public
  FROM PUBLIC, anon, authenticated;
DO $$
BEGIN
  IF current_setting('server_version_num')::integer >= 170000 THEN
    EXECUTE 'REVOKE MAINTAIN ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon, authenticated';
  END IF;
END;
$$;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON SEQUENCES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE MAINTAIN, TRIGGER, REFERENCES, TRUNCATE ON TABLES FROM PUBLIC, anon, authenticated;

-- These operational summaries/logs are never mutated by the client directly.
-- Their writes are performed by owner-rights RPCs or database triggers.
DROP POLICY IF EXISTS venta_diaria_admin_all ON public.venta_diaria;
DROP POLICY IF EXISTS venta_diaria_empleado_select ON public.venta_diaria;
DROP POLICY IF EXISTS venta_diaria_staff_select ON public.venta_diaria;
REVOKE ALL PRIVILEGES ON TABLE public.venta_diaria, public.auditoria_eventos
  FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.venta_diaria TO authenticated;
CREATE POLICY venta_diaria_staff_select ON public.venta_diaria
  FOR SELECT TO authenticated
  USING ((SELECT public.auth_role()) IN ('admin', 'gerente'));

-- Trigger-only and legacy compatibility routines are not Data API endpoints.
REVOKE ALL ON FUNCTION public.trigger_set_updated_at() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.decrementar_stock(uuid, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.realizar_corte_caja(date, uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.registrar_visita_cliente(uuid) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull(
  p_after bigint DEFAULT 0,
  p_watermark bigint DEFAULT NULL,
  p_limit integer DEFAULT 200
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE role_name text; upper_bound bigint; next_cursor bigint; items jsonb;
BEGIN
  role_name := public.auth_role();
  IF auth.uid() IS NULL OR coalesce(role_name, '') NOT IN ('admin', 'gerente') THEN
    RAISE EXCEPTION 'not_authorized' USING ERRCODE = '42501';
  END IF;
  IF p_after IS NULL OR p_after < 0 OR p_limit IS NULL OR p_limit < 1 OR p_limit > 200 THEN
    RAISE EXCEPTION 'invalid_cursor' USING ERRCODE = '22023';
  END IF;
  SELECT coalesce(max(seq), 0) INTO upper_bound FROM private_sync.changes;
  IF p_watermark IS NOT NULL THEN
    IF p_watermark < p_after OR p_watermark > upper_bound THEN
      RAISE EXCEPTION 'invalid_watermark' USING ERRCODE = '22023';
    END IF;
    upper_bound := p_watermark;
  END IF;
  IF p_after > upper_bound THEN
    RAISE EXCEPTION 'cursor_reset_required' USING ERRCODE = '22023';
  END IF;
  SELECT coalesce(max(seq), upper_bound) INTO next_cursor
    FROM (SELECT seq FROM private_sync.changes
           WHERE seq > p_after AND seq <= upper_bound ORDER BY seq LIMIT p_limit) page;
  SELECT coalesce(jsonb_agg(jsonb_build_object(
      'seq', seq, 'entity', entity, 'entity_id', entity_id,
      'version', version, 'deleted', deleted,
      'data', CASE WHEN entity = 'clientes' THEN data - 'qr_token_hash' ELSE data END
    ) ORDER BY seq), '[]'::jsonb)
    INTO items
    FROM private_sync.changes
   WHERE seq > p_after AND seq <= next_cursor
     AND entity NOT IN ('auditoria_eventos');
  RETURN jsonb_build_object('changes', items, 'cursor', next_cursor,
    'watermark', upper_bound, 'has_more', next_cursor < upper_bound);
END;
$$;
REVOKE ALL ON FUNCTION public.sync_pull(bigint, bigint, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sync_pull(bigint, bigint, integer) TO authenticated;
COMMENT ON FUNCTION public.sync_pull(bigint, bigint, integer) IS
  'Admin/active-manager change feed; strips historical and new customer QR digests and excludes audit events.';

-- Auth signups must never infer staff authority. Staff accounts are provisioned
-- through the trusted admin workflow (user_roles + linked employee profile).
DROP TRIGGER IF EXISTS on_auth_user_created_set_role ON auth.users;
REVOKE ALL ON FUNCTION public.handle_new_user_role() FROM PUBLIC, anon, authenticated;

-- Public signup remains available for the separate customer app; anonymous
-- Auth sign-in remains disabled. Local Auth requires email verification.
COMMENT ON INDEX public.clientes_user_id_uidx IS
  'At most one customer profile may be linked to an Auth identity; null remains valid for legacy/unlinked profiles.';
