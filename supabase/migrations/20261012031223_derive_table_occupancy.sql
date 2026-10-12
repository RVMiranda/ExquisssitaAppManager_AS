-- Occupancy is derived from open orders; only free/reserved states are mutable.
-- Normalize the legacy marker without changing order or reservation records.
UPDATE public.mesas
   SET estado = 'Libre', updated_at = now()
 WHERE estado = 'Ocupada';

ALTER TABLE public.mesas
  ADD CONSTRAINT mesas_occupancy_is_derived
  CHECK (estado <> 'Ocupada');
