-- Release 12: Add push notifications for loyalty reward redemptions.
-- Extends private_push.notify_loyalty_points() to trigger on tipo = 'canje'
-- so the customer receives an instant notification with the reward title and points spent.

CREATE OR REPLACE FUNCTION private_push.notify_loyalty_points()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  title text;
  body text;
  abs_points integer;
  reward_name text;
BEGIN
  IF NEW.tipo NOT IN ('visita', 'ajuste', 'canje') THEN
    RETURN NEW;
  END IF;

  IF NEW.tipo = 'canje' THEN
    abs_points := pg_catalog.abs(NEW.puntos_delta);
    reward_name := pg_catalog.replace(coalesce(NEW.motivo, 'Recompensa'), 'Canje: ', '');
    title := '¡Recompensa canjeada!';
    body := pg_catalog.format('Canjeaste %s puntos por: %s.', abs_points, reward_name);
  ELSIF NEW.puntos_delta > 0 AND NEW.tipo = 'visita' THEN
    title := 'Visita registrada';
    body := pg_catalog.format('Acumulaste %s puntos en tu visita.', NEW.puntos_delta);
  ELSIF NEW.puntos_delta > 0 THEN
    title := 'Puntos actualizados';
    body := pg_catalog.format('Se agregaron %s puntos a tu cuenta.', NEW.puntos_delta);
  ELSE
    RETURN NEW;
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

NOTIFY pgrst, 'reload schema';
