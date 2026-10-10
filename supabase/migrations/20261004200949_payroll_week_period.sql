-- Existing payroll rows keep NULL period fields: the week of a late payment
-- cannot be reconstructed reliably from fecha_pago alone.
alter table public.historial_pagos_empleados
  add column periodo_inicio date,
  add column fecha_programada date;

alter table public.historial_pagos_empleados
  add constraint historial_pagos_periodo_lunes_check
    check (periodo_inicio is null or extract(isodow from periodo_inicio) = 1),
  add constraint historial_pagos_fecha_programada_check
    check (
      (periodo_inicio is null and fecha_programada is null) or
      (periodo_inicio is not null and fecha_programada between periodo_inicio and periodo_inicio + 6)
    );

create unique index historial_pagos_empleado_periodo_unique
  on public.historial_pagos_empleados (empleado_id, periodo_inicio)
  where periodo_inicio is not null;

create index historial_pagos_empleado_periodo_desc
  on public.historial_pagos_empleados (empleado_id, periodo_inicio desc);
