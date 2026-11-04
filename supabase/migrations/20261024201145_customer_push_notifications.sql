-- Sprint 06: customer device registration and event-driven FCM dispatch.
-- pg_net executes HTTP requests asynchronously after the current transaction commits.
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

CREATE SCHEMA IF NOT EXISTS private_push;
REVOKE ALL ON SCHEMA private_push FROM PUBLIC, anon, authenticated;

CREATE TABLE public.dispositivos_fcm (
  id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
  cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE CASCADE,
  fcm_token text NOT NULL UNIQUE CHECK (length(btrim(fcm_token)) BETWEEN 1 AND 4096),
  plataforma text NOT NULL DEFAULT 'android' CHECK (plataforma = 'android'),
  modelo_dispositivo text CHECK (modelo_dispositivo IS NULL OR length(modelo_dispositivo) <= 160),
  actualizado_el timestamptz NOT NULL DEFAULT pg_catalog.now(),
  creado_el timestamptz NOT NULL DEFAULT pg_catalog.now()
);
CREATE INDEX dispositivos_fcm_cliente_idx ON public.dispositivos_fcm(cliente_id);
ALTER TABLE public.dispositivos_fcm ENABLE ROW LEVEL SECURITY;

CREATE POLICY dispositivos_fcm_select_own ON public.dispositivos_fcm
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = dispositivos_fcm.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));
CREATE POLICY dispositivos_fcm_insert_own ON public.dispositivos_fcm
  FOR INSERT TO authenticated
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = dispositivos_fcm.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));
CREATE POLICY dispositivos_fcm_update_own ON public.dispositivos_fcm
  FOR UPDATE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = dispositivos_fcm.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ))
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = dispositivos_fcm.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));
CREATE POLICY dispositivos_fcm_delete_own ON public.dispositivos_fcm
  FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.clientes c
     WHERE c.id = dispositivos_fcm.cliente_id
       AND c.user_id = (SELECT auth.uid()) AND c.deleted_at IS NULL
  ));

-- Clients read their own registrations, while changes go through owner-checked RPCs.
REVOKE ALL ON TABLE public.dispositivos_fcm FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.dispositivos_fcm TO authenticated;
GRANT SELECT, DELETE ON TABLE public.dispositivos_fcm TO service_role;

CREATE OR REPLACE FUNCTION public.registrar_dispositivo_fcm(
  p_token text,
  p_modelo text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); customer_id uuid; device_id uuid;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;
  IF p_token IS NULL OR length(btrim(p_token)) = 0 OR length(p_token) > 4096 THEN
    RAISE EXCEPTION 'invalid_fcm_token' USING ERRCODE = '22023';
  END IF;
  IF p_modelo IS NOT NULL AND length(p_modelo) > 160 THEN
    RAISE EXCEPTION 'invalid_device_model' USING ERRCODE = '22023';
  END IF;
  SELECT c.id INTO customer_id FROM public.clientes c
   WHERE c.user_id = actor AND c.deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = '42501'; END IF;

  INSERT INTO public.dispositivos_fcm(cliente_id, fcm_token, modelo_dispositivo)
  VALUES (customer_id, btrim(p_token), nullif(btrim(p_modelo), ''))
  ON CONFLICT (fcm_token) DO UPDATE SET
    cliente_id = EXCLUDED.cliente_id,
    plataforma = EXCLUDED.plataforma,
    modelo_dispositivo = EXCLUDED.modelo_dispositivo,
    actualizado_el = pg_catalog.now()
  RETURNING id INTO device_id;
  RETURN pg_catalog.jsonb_build_object('id', device_id, 'registrado', true);
END;
$$;
REVOKE ALL ON FUNCTION public.registrar_dispositivo_fcm(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_dispositivo_fcm(text, text) TO authenticated;

CREATE OR REPLACE FUNCTION public.eliminar_dispositivo_fcm(p_token text)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE actor uuid := (SELECT auth.uid()); customer_id uuid;
BEGIN
  IF actor IS NULL THEN RAISE EXCEPTION 'not_authenticated' USING ERRCODE = '42501'; END IF;
  SELECT c.id INTO customer_id FROM public.clientes c
   WHERE c.user_id = actor AND c.deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer_profile_not_found' USING ERRCODE = '42501'; END IF;
  DELETE FROM public.dispositivos_fcm d
   WHERE d.cliente_id = customer_id AND d.fcm_token = p_token;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.eliminar_dispositivo_fcm(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.eliminar_dispositivo_fcm(text) TO authenticated;

-- Only the Edge Function's service credential may enumerate active device tokens.
CREATE OR REPLACE FUNCTION public.obtener_tokens_fcm(p_cliente_id uuid DEFAULT NULL)
RETURNS TABLE(fcm_token text)
LANGUAGE sql SECURITY DEFINER SET search_path = ''
AS $$
  SELECT d.fcm_token
    FROM public.dispositivos_fcm d
    JOIN public.clientes c ON c.id = d.cliente_id
   WHERE c.deleted_at IS NULL
     AND (p_cliente_id IS NULL OR c.id = p_cliente_id)
$$;
REVOKE ALL ON FUNCTION public.obtener_tokens_fcm(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.obtener_tokens_fcm(uuid) TO service_role;

-- A customer's account tombstone removes all device endpoints immediately.
CREATE OR REPLACE FUNCTION private_push.purge_deleted_customer_devices()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
BEGIN
  DELETE FROM public.dispositivos_fcm WHERE cliente_id = NEW.id;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_push.purge_deleted_customer_devices() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER clientes_purge_fcm_devices_on_delete
  AFTER UPDATE OF deleted_at ON public.clientes
  FOR EACH ROW
  WHEN (OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL)
  EXECUTE FUNCTION private_push.purge_deleted_customer_devices();

-- Configure these two secrets in Vault after migration:
-- fcm_push_function_url = https://<project-ref>.supabase.co/functions/v1/send-push-notification
-- fcm_push_webhook_secret = the same high-entropy value configured in Edge Function secrets.
CREATE OR REPLACE FUNCTION private_push.dispatch_event(p_event jsonb)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE endpoint text; webhook_secret text;
BEGIN
  SELECT s.decrypted_secret INTO endpoint FROM vault.decrypted_secrets s
   WHERE s.name = 'fcm_push_function_url' ORDER BY s.created_at DESC LIMIT 1;
  SELECT s.decrypted_secret INTO webhook_secret FROM vault.decrypted_secrets s
   WHERE s.name = 'fcm_push_webhook_secret' ORDER BY s.created_at DESC LIMIT 1;
  IF endpoint IS NULL OR webhook_secret IS NULL THEN
    RAISE WARNING 'FCM webhook is not configured; event % was not dispatched', p_event->>'event_id';
    RETURN;
  END IF;
  PERFORM net.http_post(
    url := endpoint,
    body := p_event,
    headers := pg_catalog.jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-webhook-secret', webhook_secret
    ),
    timeout_milliseconds := 5000
  );
END;
$$;
REVOKE ALL ON FUNCTION private_push.dispatch_event(jsonb) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION private_push.notify_loyalty_points()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE title text; body text;
BEGIN
  IF NEW.puntos_delta <= 0 OR NEW.tipo NOT IN ('visita', 'ajuste') THEN RETURN NEW; END IF;
  IF NEW.tipo = 'visita' THEN
    title := 'Visita registrada';
    body := pg_catalog.format('Acumulaste %s puntos en tu visita.', NEW.puntos_delta);
  ELSE
    title := 'Puntos actualizados';
    body := pg_catalog.format('Se agregaron %s puntos a tu cuenta.', NEW.puntos_delta);
  END IF;
  PERFORM private_push.dispatch_event(pg_catalog.jsonb_build_object(
    'event_id', NEW.id::text,
    'event_type', 'loyalty_points',
    'cliente_id', NEW.cliente_id::text,
    'title', title,
    'body', body,
    'data', pg_catalog.jsonb_build_object(
      'type', 'loyalty_points',
      'movement_id', NEW.id::text,
      'movement_type', NEW.tipo,
      'points_delta', NEW.puntos_delta::text
    )
  ));
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_push.notify_loyalty_points() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER puntos_movimientos_notify_fcm
  AFTER INSERT ON public.puntos_movimientos
  FOR EACH ROW EXECUTE FUNCTION private_push.notify_loyalty_points();

CREATE OR REPLACE FUNCTION private_push.notify_active_promotion()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE promotion_title text; promotion_body text;
BEGIN
  IF NEW.activo IS NOT TRUE OR (TG_OP = 'UPDATE' AND OLD.activo IS TRUE)
     OR (NEW.fecha_vencimiento IS NOT NULL
       AND NEW.fecha_vencimiento < (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date) THEN
    RETURN NEW;
  END IF;
  promotion_title := left(btrim(NEW.titulo), 120);
  promotion_body := left(btrim(NEW.descripcion), 240);
  PERFORM private_push.dispatch_event(pg_catalog.jsonb_build_object(
    'event_id', NEW.id::text || ':' || NEW.updated_at::text,
    'event_type', 'promotion_activated',
    'title', 'Nueva promoción en La Exquisssita',
    'body', promotion_title || CASE WHEN promotion_body = '' THEN '' ELSE ': ' || promotion_body END,
    'data', pg_catalog.jsonb_build_object(
      'type', 'promotion',
      'promotion_id', NEW.id::text,
      'promotion_title', promotion_title,
      'image_url', coalesce(NEW.imagen_url, '')
    )
  ));
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_push.notify_active_promotion() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER promociones_notify_fcm_when_activated
  AFTER INSERT OR UPDATE ON public.promociones
  FOR EACH ROW EXECUTE FUNCTION private_push.notify_active_promotion();

NOTIFY pgrst, 'reload schema';
