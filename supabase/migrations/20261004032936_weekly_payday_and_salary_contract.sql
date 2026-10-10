-- Weekly payroll contract. Monetary values remain integer cents in storage;
-- the client presents and accepts pesos and converts at the repository boundary.
alter table public.empleados
  drop constraint if exists empleados_dia_pago_check;
alter table public.empleados
  add constraint empleados_dia_pago_check check (dia_pago between 1 and 7);
