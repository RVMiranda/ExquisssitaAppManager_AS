-- Migration: 20261103183000_grant_precio_abierto_productos.sql
-- Description: Grant column-level insert and update privileges for precio_abierto on public.productos to authenticated staff.

GRANT INSERT (precio_abierto) ON public.productos TO authenticated;
GRANT UPDATE (precio_abierto) ON public.productos TO authenticated;
