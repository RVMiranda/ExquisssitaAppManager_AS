-- Migracion: Activar Supabase Realtime para sincronizacion completa de la capa de datos
-- Fecha: 2026-11-03

DO $$
BEGIN
    -- Agregar tablas operativas, de lealtad y financieras a la publicacion supabase_realtime
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pago_detalles'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.pago_detalles;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'pago_detalle_stock'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.pago_detalle_stock;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'recompensas'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.recompensas;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'canjes_recompensas'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.canjes_recompensas;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'puntos_movimientos'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.puntos_movimientos;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'visitas_clientes'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.visitas_clientes;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'cortes_caja'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.cortes_caja;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'prestamos_empleados'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.prestamos_empleados;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'abonos_prestamos_empleados'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.abonos_prestamos_empleados;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'venta_diaria'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.venta_diaria;
    END IF;
END $$;
