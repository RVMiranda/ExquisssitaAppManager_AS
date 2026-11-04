-- Release 10 / Push Notifications & Phone Integrity:
-- 1. Dispatch push notifications on promotion update if content changed.
-- 2. Add destination = 'promotions' in push data payload for deep linking.
-- 3. Enforce 10-digit numeric check for non-null phone numbers in public.clientes.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'clientes_telefono_format_check'
  ) THEN
    ALTER TABLE public.clientes
      ADD CONSTRAINT clientes_telefono_format_check
      CHECK (telefono IS NULL OR (length(btrim(telefono)) = 10 AND btrim(telefono) ~ '^[0-9]{10}$'));
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION private_push.notify_active_promotion()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  promotion_title text;
  promotion_body text;
  notification_title text;
  is_update boolean;
BEGIN
  -- If the promotion is not active, or is already expired, do not notify.
  IF NEW.activo IS NOT TRUE
     OR (NEW.fecha_vencimiento IS NOT NULL
       AND NEW.fecha_vencimiento < (pg_catalog.now() AT TIME ZONE 'America/Mexico_City')::date) THEN
    RETURN NEW;
  END IF;

  is_update := (TG_OP = 'UPDATE' AND OLD.activo IS TRUE);

  -- For updates on already active promotions, only dispatch if customer-facing content changed.
  IF is_update THEN
    IF OLD.titulo IS NOT DISTINCT FROM NEW.titulo
       AND OLD.descripcion IS NOT DISTINCT FROM NEW.descripcion
       AND OLD.descuento IS NOT DISTINCT FROM NEW.descuento
       AND OLD.imagen_url IS NOT DISTINCT FROM NEW.imagen_url
       AND OLD.fecha_vencimiento IS NOT DISTINCT FROM NEW.fecha_vencimiento THEN
      RETURN NEW;
    END IF;
    notification_title := 'Promoción actualizada en La Exquisssita';
  ELSE
    notification_title := 'Nueva promoción en La Exquisssita';
  END IF;

  promotion_title := left(btrim(NEW.titulo), 120);
  promotion_body := left(btrim(NEW.descripcion), 240);

  PERFORM private_push.dispatch_event(pg_catalog.jsonb_build_object(
    'event_id', NEW.id::text || ':' || NEW.updated_at::text,
    'event_type', 'promotion_activated',
    'title', notification_title,
    'body', promotion_title || CASE WHEN promotion_body = '' THEN '' ELSE ': ' || promotion_body END,
    'data', pg_catalog.jsonb_build_object(
      'type', 'promotion',
      'destination', 'promotions',
      'promotion_id', NEW.id::text,
      'promotion_title', promotion_title,
      'image_url', coalesce(NEW.imagen_url, '')
    )
  ));
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private_push.notify_active_promotion() FROM PUBLIC, anon, authenticated;

NOTIFY pgrst, 'reload schema';
