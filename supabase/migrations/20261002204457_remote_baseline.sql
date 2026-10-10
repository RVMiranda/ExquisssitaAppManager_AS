SET local check_function_bodies = off;

CREATE TABLE "public"."clientes" (
  "id"              uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "user_id"         uuid,
  "nombre"          text                     NOT NULL,
  "telefono"        text,
  "visitas_totales" integer                  DEFAULT 0,
  "puntos_lealtad"  integer                  DEFAULT 0,
  "fecha_registro"  timestamp with time zone DEFAULT now(),
  "created_at"      timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"      timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "clientes_pkey" PRIMARY KEY (id),
  CONSTRAINT "clientes_puntos_lealtad_check" CHECK ((puntos_lealtad >= 0)),
  CONSTRAINT "clientes_telefono_key" UNIQUE (telefono),
  CONSTRAINT "clientes_visitas_totales_check" CHECK ((visitas_totales >= 0))
);

ALTER TABLE "public"."clientes"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."detalle_orden" (
  "id"              uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "orden_id"        uuid                     NOT NULL,
  "producto_id"     uuid                     NOT NULL,
  "cantidad"        integer                  NOT NULL,
  "precio_unitario" bigint                   NOT NULL,
  "estado_pago"     text                     NOT NULL DEFAULT 'Pendiente'::text,
  "notas"           text,
  "created_at"      timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"      timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "detalle_orden_cantidad_check" CHECK ((cantidad > 0)),
  CONSTRAINT "detalle_orden_estado_pago_check" CHECK ((estado_pago = ANY (ARRAY['Pendiente'::text, 'Pagado'::text, 'Cancelado'::text]))),
  CONSTRAINT "detalle_orden_pkey" PRIMARY KEY (id),
  CONSTRAINT "detalle_orden_precio_unitario_check" CHECK ((precio_unitario >= 0))
);

ALTER TABLE "public"."detalle_orden"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."empleados" (
  "id"         uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "user_id"    uuid,
  "nombre"     text                     NOT NULL,
  "apellido"   text                     NOT NULL,
  "telefono"   text,
  "salario"    bigint                   NOT NULL,
  "dia_pago"   integer                  NOT NULL,
  "activo"     boolean                  NOT NULL DEFAULT true,
  "created_at" timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at" timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "empleados_dia_pago_check" CHECK (((dia_pago >= 1) AND (dia_pago <= 31))),
  CONSTRAINT "empleados_pkey" PRIMARY KEY (id),
  CONSTRAINT "empleados_salario_check" CHECK ((salario > 0))
);

ALTER TABLE "public"."empleados"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."historial_pagos_empleados" (
  "id"          uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "empleado_id" uuid                     NOT NULL,
  "monto"       bigint                   NOT NULL,
  "fecha_pago"  date                     NOT NULL DEFAULT CURRENT_DATE,
  "notas"       text,
  "created_at"  timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "historial_pagos_empleados_monto_check" CHECK ((monto > 0)),
  CONSTRAINT "historial_pagos_empleados_pkey" PRIMARY KEY (id)
);

ALTER TABLE "public"."historial_pagos_empleados"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."mesas" (
  "id"          uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "numero_mesa" text                     NOT NULL,
  "estado"      text                     NOT NULL DEFAULT 'Libre'::text,
  "created_at"  timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"  timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "mesas_estado_check" CHECK ((estado = ANY (ARRAY['Libre'::text, 'Ocupada'::text, 'Reservada'::text]))),
  CONSTRAINT "mesas_numero_mesa_key" UNIQUE (numero_mesa),
  CONSTRAINT "mesas_pkey" PRIMARY KEY (id)
);

ALTER TABLE "public"."mesas"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."ordenes" (
  "id"             uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "mesa_id"        uuid,
  "cliente_id"     uuid,
  "tipo_servicio"  text                     NOT NULL,
  "estado"         text                     NOT NULL DEFAULT 'Abierta'::text,
  "fecha_apertura" timestamp with time zone NOT NULL DEFAULT now(),
  "fecha_cierre"   timestamp with time zone,
  "notas"          text,
  "created_at"     timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"     timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "ordenes_estado_check" CHECK ((estado = ANY (ARRAY['Abierta'::text, 'Cerrada'::text, 'Cancelada'::text]))),
  CONSTRAINT "ordenes_pkey" PRIMARY KEY (id),
  CONSTRAINT "ordenes_tipo_servicio_check" CHECK ((tipo_servicio = ANY (ARRAY['Mesa'::text, 'Para llevar'::text, 'Domicilio'::text])))
);

ALTER TABLE "public"."ordenes"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."productos" (
  "id"                  uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "nombre"              text                     NOT NULL,
  "precio"              bigint                   NOT NULL,
  "categoria"           text                     NOT NULL,
  "controla_inventario" boolean                  NOT NULL DEFAULT false,
  "stock_actual"        integer                  DEFAULT 0,
  "stock_minimo"        integer                  DEFAULT 0,
  "activo"              boolean                  NOT NULL DEFAULT true,
  "created_at"          timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"          timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "productos_pkey" PRIMARY KEY (id),
  CONSTRAINT "productos_precio_check" CHECK ((precio >= 0)),
  CONSTRAINT "productos_stock_actual_check" CHECK ((stock_actual >= 0)),
  CONSTRAINT "productos_stock_minimo_check" CHECK ((stock_minimo >= 0))
);

ALTER TABLE "public"."productos"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."promociones" (
  "id"                uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "titulo"            text                     NOT NULL,
  "descripcion"       text                     NOT NULL,
  "imagen_url"        text,
  "fecha_publicacion" timestamp with time zone NOT NULL DEFAULT now(),
  "fecha_vencimiento" date,
  "activo"            boolean                  NOT NULL DEFAULT true,
  "created_at"        timestamp with time zone NOT NULL DEFAULT now(),
  "updated_at"        timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "promociones_pkey" PRIMARY KEY (id)
);

ALTER TABLE "public"."promociones"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."transacciones" (
  "id"              uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "orden_id"        uuid                     NOT NULL,
  "monto"           bigint                   NOT NULL,
  "metodo_pago"     text                     NOT NULL,
  "fecha"           timestamp with time zone NOT NULL DEFAULT now(),
  "idempotency_key" uuid                     NOT NULL DEFAULT extensions.uuid_generate_v4(),
  "created_at"      timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "transacciones_idempotency_key_key" UNIQUE (idempotency_key),
  CONSTRAINT "transacciones_metodo_pago_check" CHECK ((metodo_pago = ANY (ARRAY['Efectivo'::text, 'Tarjeta'::text, 'Transferencia'::text, 'Otro'::text]))),
  CONSTRAINT "transacciones_monto_check" CHECK ((monto > 0)),
  CONSTRAINT "transacciones_pkey" PRIMARY KEY (id)
);

ALTER TABLE "public"."transacciones"
  ENABLE ROW LEVEL SECURITY;

CREATE TABLE "public"."venta_diaria" (
  "id"             bigint                   GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "fecha"          date                     NOT NULL,
  "total_efectivo" bigint                   NOT NULL DEFAULT 0,
  "total_tarjeta"  bigint                   NOT NULL DEFAULT 0,
  "total_otro"     bigint                   NOT NULL DEFAULT 0,
  "total_global"   bigint                   NOT NULL,
  "num_ordenes"    integer                  NOT NULL DEFAULT 0,
  "hora_cierre"    timestamp with time zone NOT NULL DEFAULT now(),
  "cerrado_por"    uuid,
  CONSTRAINT "venta_diaria_fecha_key" UNIQUE (fecha),
  CONSTRAINT "venta_diaria_pkey" PRIMARY KEY (id)
);

ALTER TABLE "public"."venta_diaria"
  ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.auth_role()
  RETURNS text
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  AS $function$
  SELECT COALESCE(
    auth.jwt() -> 'user_metadata' ->> 'rol',
    ''
  );
$function$;

CREATE OR REPLACE FUNCTION public.decrementar_stock (
  p_producto_id uuid,
  p_cantidad    integer
)
  RETURNS void
  LANGUAGE plpgsql
  SECURITY DEFINER
  AS $function$
DECLARE
  v_stock_actual INTEGER;
BEGIN
  -- Lock exclusivo en el registro del producto
  SELECT stock_actual INTO v_stock_actual
  FROM productos
  WHERE id = p_producto_id
  FOR UPDATE;

  IF v_stock_actual IS NULL THEN
    RAISE EXCEPTION 'producto_no_encontrado'
      USING ERRCODE = 'P0001';
  END IF;

  IF v_stock_actual < p_cantidad THEN
    RAISE EXCEPTION 'stock_insuficiente: disponible=%, solicitado=%',
      v_stock_actual, p_cantidad
      USING ERRCODE = 'P0002';
  END IF;

  UPDATE productos
  SET stock_actual = stock_actual - p_cantidad
  WHERE id = p_producto_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.realizar_corte_caja (
  p_fecha       date,
  p_cerrado_por uuid
)
  RETURNS TABLE (
    total_efectivo bigint,
    total_tarjeta  bigint,
    total_otro     bigint,
    total_global   bigint,
    num_ordenes    integer
  )
  LANGUAGE plpgsql
  SECURITY DEFINER
  AS $function$
DECLARE
  v_efectivo  BIGINT;
  v_tarjeta   BIGINT;
  v_otro      BIGINT;
  v_total     BIGINT;
  v_ordenes   INTEGER;
BEGIN
  -- Solo admin puede ejecutar esto (verificado por RLS, pero doble check)
  IF auth_role() != 'admin' THEN
    RAISE EXCEPTION 'permiso_denegado'
      USING ERRCODE = 'P0003';
  END IF;

  -- Ya existe un corte para esta fecha?
  IF EXISTS (SELECT 1 FROM venta_diaria WHERE fecha = p_fecha) THEN
    RAISE EXCEPTION 'corte_ya_existe_para_fecha: %', p_fecha
      USING ERRCODE = 'P0004';
  END IF;

  -- Calcular totales desde transacciones del día
  SELECT
    COALESCE(SUM(CASE WHEN metodo_pago = 'Efectivo' THEN monto ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN metodo_pago = 'Tarjeta'  THEN monto ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN metodo_pago NOT IN ('Efectivo', 'Tarjeta') THEN monto ELSE 0 END), 0),
    COALESCE(SUM(monto), 0)
  INTO v_efectivo, v_tarjeta, v_otro, v_total
  FROM transacciones
  WHERE DATE(fecha AT TIME ZONE 'America/Mexico_City') = p_fecha;

  -- Contar órdenes cerradas del día
  SELECT COUNT(*)::INTEGER INTO v_ordenes
  FROM ordenes
  WHERE estado = 'Cerrada'
    AND DATE(fecha_cierre AT TIME ZONE 'America/Mexico_City') = p_fecha;

  -- Insertar snapshot
  INSERT INTO venta_diaria (
    fecha, total_efectivo, total_tarjeta, total_otro,
    total_global, num_ordenes, cerrado_por
  )
  VALUES (
    p_fecha, v_efectivo, v_tarjeta, v_otro,
    v_total, v_ordenes, p_cerrado_por
  );

  RETURN QUERY SELECT v_efectivo, v_tarjeta, v_otro, v_total, v_ordenes;
END;
$function$;

CREATE OR REPLACE FUNCTION public.registrar_visita_cliente (
  p_cliente_id uuid
)
  RETURNS jsonb
  LANGUAGE plpgsql
  SECURITY DEFINER
  AS $function$
DECLARE
  v_cliente clientes%ROWTYPE;
  v_ultima_visita TIMESTAMPTZ;
BEGIN
  -- Obtener cliente con lock
  SELECT * INTO v_cliente
  FROM clientes
  WHERE id = p_cliente_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'cliente_no_encontrado'
      USING ERRCODE = 'P0005';
  END IF;

  -- Verificar que no se haya registrado una visita hoy
  -- (usando updated_at como proxy — en producción podría usarse una tabla separada de visitas)
  IF DATE(v_cliente.updated_at AT TIME ZONE 'America/Mexico_City') = CURRENT_DATE
     AND v_cliente.visitas_totales > 0 THEN
    RAISE EXCEPTION 'visita_ya_registrada_hoy'
      USING ERRCODE = 'P0006';
  END IF;

  -- Incrementar visitas y puntos
  UPDATE clientes
  SET
    visitas_totales = visitas_totales + 1,
    puntos_lealtad  = puntos_lealtad + 10,  -- 10 puntos por visita (configurable)
    updated_at      = NOW()
  WHERE id = p_cliente_id
  RETURNING * INTO v_cliente;

  RETURN jsonb_build_object(
    'visitas_totales', v_cliente.visitas_totales,
    'puntos_lealtad',  v_cliente.puntos_lealtad
  );
END;
$function$;

CREATE OR REPLACE FUNCTION public.trigger_set_updated_at()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  AS $function$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$function$;

ALTER TABLE "public"."clientes"
  ADD CONSTRAINT "clientes_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

ALTER TABLE "public"."empleados"
  ADD CONSTRAINT "empleados_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

ALTER TABLE "public"."historial_pagos_empleados"
  ADD CONSTRAINT "historial_pagos_empleados_empleado_id_fkey" FOREIGN KEY (empleado_id) REFERENCES public.empleados(id) ON DELETE RESTRICT;

ALTER TABLE "public"."ordenes"
  ADD CONSTRAINT "ordenes_cliente_id_fkey" FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE SET NULL;

ALTER TABLE "public"."ordenes"
  ADD CONSTRAINT "ordenes_mesa_id_fkey" FOREIGN KEY (mesa_id) REFERENCES public.mesas(id) ON DELETE RESTRICT;

ALTER TABLE "public"."detalle_orden"
  ADD CONSTRAINT "detalle_orden_orden_id_fkey" FOREIGN KEY (orden_id) REFERENCES public.ordenes(id) ON DELETE CASCADE;

ALTER TABLE "public"."detalle_orden"
  ADD CONSTRAINT "detalle_orden_producto_id_fkey" FOREIGN KEY (producto_id) REFERENCES public.productos(id) ON DELETE RESTRICT;

ALTER TABLE "public"."transacciones"
  ADD CONSTRAINT "transacciones_orden_id_fkey" FOREIGN KEY (orden_id) REFERENCES public.ordenes(id) ON DELETE RESTRICT;

ALTER TABLE "public"."venta_diaria"
  ADD CONSTRAINT "venta_diaria_cerrado_por_fkey" FOREIGN KEY (cerrado_por) REFERENCES public.empleados(id) ON DELETE SET NULL;

CREATE INDEX idx_clientes_telefono ON public.clientes USING btree (telefono);

CREATE INDEX idx_clientes_user_id ON public.clientes USING btree (user_id);

CREATE INDEX idx_detalle_orden_estado_pago ON public.detalle_orden USING btree (estado_pago);

CREATE INDEX idx_detalle_orden_orden_id ON public.detalle_orden USING btree (orden_id);

CREATE INDEX idx_detalle_orden_producto_id ON public.detalle_orden USING btree (producto_id);

CREATE INDEX idx_empleados_dia_pago ON public.empleados USING btree (dia_pago);

CREATE INDEX idx_empleados_user_id ON public.empleados USING btree (user_id);

CREATE INDEX idx_historial_pagos_empleado_id ON public.historial_pagos_empleados USING btree (empleado_id);

CREATE INDEX idx_historial_pagos_fecha ON public.historial_pagos_empleados USING btree (fecha_pago);

CREATE INDEX idx_ordenes_cliente_id ON public.ordenes USING btree (cliente_id);

CREATE INDEX idx_ordenes_estado ON public.ordenes USING btree (estado);

CREATE INDEX idx_ordenes_fecha_apertura ON public.ordenes USING btree (fecha_apertura);

CREATE INDEX idx_ordenes_mesa_id ON public.ordenes USING btree (mesa_id);

CREATE INDEX idx_productos_activo ON public.productos USING btree (activo);

CREATE INDEX idx_productos_categoria ON public.productos USING btree (categoria);

CREATE INDEX idx_promociones_activo ON public.promociones USING btree (activo);

CREATE INDEX idx_promociones_fecha_vencimiento ON public.promociones USING btree (fecha_vencimiento);

CREATE INDEX idx_transacciones_fecha ON public.transacciones USING btree (fecha);

CREATE INDEX idx_transacciones_metodo_pago ON public.transacciones USING btree (metodo_pago);

CREATE INDEX idx_transacciones_orden_id ON public.transacciones USING btree (orden_id);

CREATE INDEX idx_venta_diaria_fecha ON public.venta_diaria USING btree (fecha);

CREATE TRIGGER set_updated_at_clientes
  BEFORE UPDATE ON public.clientes
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_detalle_orden
  BEFORE UPDATE ON public.detalle_orden
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_empleados
  BEFORE UPDATE ON public.empleados
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_mesas
  BEFORE UPDATE ON public.mesas
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_ordenes
  BEFORE UPDATE ON public.ordenes
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_productos
  BEFORE UPDATE ON public.productos
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE TRIGGER set_updated_at_promociones
  BEFORE UPDATE ON public.promociones
  FOR EACH ROW
  EXECUTE FUNCTION public.trigger_set_updated_at();

CREATE POLICY "clientes_admin_delete" ON "public"."clientes"
  FOR DELETE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "clientes_select_own" ON "public"."clientes"
  FOR SELECT
  TO PUBLIC
  USING ((auth.uid() = user_id));

CREATE POLICY "clientes_staff_insert" ON "public"."clientes"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "clientes_staff_select" ON "public"."clientes"
  FOR SELECT
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "clientes_staff_update" ON "public"."clientes"
  FOR UPDATE
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "clientes_update_own" ON "public"."clientes"
  FOR UPDATE
  TO PUBLIC
  USING ((auth.uid() = user_id))
  WITH CHECK ((auth.uid() = user_id));

CREATE POLICY "detalle_orden_clientes_select_own" ON "public"."detalle_orden"
  FOR SELECT
  TO PUBLIC
  USING ((orden_id IN ( SELECT o.id
   FROM (public.ordenes o
     JOIN public.clientes c ON ((c.id = o.cliente_id)))
  WHERE (c.user_id = auth.uid()))));

CREATE POLICY "detalle_orden_staff_all" ON "public"."detalle_orden"
  FOR ALL
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "empleados_admin_all" ON "public"."empleados"
  FOR ALL
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "empleados_select_own" ON "public"."empleados"
  FOR SELECT
  TO PUBLIC
  USING ((auth.uid() = user_id));

CREATE POLICY "pagos_empleados_admin_all" ON "public"."historial_pagos_empleados"
  FOR ALL
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "pagos_empleados_select_own" ON "public"."historial_pagos_empleados"
  FOR SELECT
  TO PUBLIC
  USING ((empleado_id IN ( SELECT empleados.id
   FROM public.empleados
  WHERE (empleados.user_id = auth.uid()))));

CREATE POLICY "mesas_admin_delete" ON "public"."mesas"
  FOR DELETE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "mesas_public_select" ON "public"."mesas"
  FOR SELECT
  TO PUBLIC
  USING (true);

CREATE POLICY "mesas_staff_insert" ON "public"."mesas"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "mesas_staff_update" ON "public"."mesas"
  FOR UPDATE
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "ordenes_admin_delete" ON "public"."ordenes"
  FOR DELETE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "ordenes_clientes_select_own" ON "public"."ordenes"
  FOR SELECT
  TO PUBLIC
  USING ((cliente_id IN ( SELECT clientes.id
   FROM public.clientes
  WHERE (clientes.user_id = auth.uid()))));

CREATE POLICY "ordenes_staff_insert" ON "public"."ordenes"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "ordenes_staff_select" ON "public"."ordenes"
  FOR SELECT
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "ordenes_staff_update" ON "public"."ordenes"
  FOR UPDATE
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "productos_admin_delete" ON "public"."productos"
  FOR DELETE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "productos_public_select" ON "public"."productos"
  FOR SELECT
  TO PUBLIC
  USING (true);

CREATE POLICY "productos_staff_insert" ON "public"."productos"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "productos_staff_update" ON "public"."productos"
  FOR UPDATE
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "promociones_admin_delete" ON "public"."promociones"
  FOR DELETE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "promociones_admin_insert" ON "public"."promociones"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = 'admin'::text));

CREATE POLICY "promociones_admin_update" ON "public"."promociones"
  FOR UPDATE
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "promociones_public_select" ON "public"."promociones"
  FOR SELECT
  TO PUBLIC
  USING (true);

CREATE POLICY "transacciones_staff_insert" ON "public"."transacciones"
  FOR INSERT
  TO PUBLIC
  WITH CHECK ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "transacciones_staff_select" ON "public"."transacciones"
  FOR SELECT
  TO PUBLIC
  USING ((public.auth_role() = ANY (ARRAY['admin'::text, 'empleado'::text])));

CREATE POLICY "venta_diaria_admin_all" ON "public"."venta_diaria"
  FOR ALL
  TO PUBLIC
  USING ((public.auth_role() = 'admin'::text));

CREATE POLICY "venta_diaria_empleado_select" ON "public"."venta_diaria"
  FOR SELECT
  TO PUBLIC
  USING ((public.auth_role() = 'empleado'::text));

GRANT EXECUTE ON FUNCTION "public"."auth_role"() TO PUBLIC, "anon", "authenticated";

REVOKE ALL ON FUNCTION "public"."auth_role"() FROM "postgres";

GRANT EXECUTE ON FUNCTION "public"."auth_role"() TO "postgres";

GRANT EXECUTE ON FUNCTION "public"."auth_role"() TO "service_role";

GRANT EXECUTE ON FUNCTION "public"."decrementar_stock"(uuid, integer) TO PUBLIC, "anon", "authenticated";

REVOKE ALL ON FUNCTION "public"."decrementar_stock"(uuid, integer) FROM "postgres";

GRANT EXECUTE ON FUNCTION "public"."decrementar_stock"(uuid, integer) TO "postgres";

GRANT EXECUTE ON FUNCTION "public"."decrementar_stock"(uuid, integer) TO "service_role";

GRANT EXECUTE ON FUNCTION "public"."realizar_corte_caja"(date, uuid) TO PUBLIC, "anon", "authenticated";

REVOKE ALL ON FUNCTION "public"."realizar_corte_caja"(date, uuid) FROM "postgres";

GRANT EXECUTE ON FUNCTION "public"."realizar_corte_caja"(date, uuid) TO "postgres";

GRANT EXECUTE ON FUNCTION "public"."realizar_corte_caja"(date, uuid) TO "service_role";

GRANT EXECUTE ON FUNCTION "public"."registrar_visita_cliente"(uuid) TO PUBLIC, "anon", "authenticated";

REVOKE ALL ON FUNCTION "public"."registrar_visita_cliente"(uuid) FROM "postgres";

GRANT EXECUTE ON FUNCTION "public"."registrar_visita_cliente"(uuid) TO "postgres";

GRANT EXECUTE ON FUNCTION "public"."registrar_visita_cliente"(uuid) TO "service_role";

GRANT EXECUTE ON FUNCTION "public"."trigger_set_updated_at"() TO PUBLIC, "anon", "authenticated";

REVOKE ALL ON FUNCTION "public"."trigger_set_updated_at"() FROM "postgres";

GRANT EXECUTE ON FUNCTION "public"."trigger_set_updated_at"() TO "postgres";

GRANT EXECUTE ON FUNCTION "public"."trigger_set_updated_at"() TO "service_role";

REVOKE ALL ON SEQUENCE "public"."venta_diaria_id_seq" FROM "anon";

GRANT SELECT, UPDATE, USAGE ON SEQUENCE "public"."venta_diaria_id_seq" TO "anon";

REVOKE ALL ON SEQUENCE "public"."venta_diaria_id_seq" FROM "authenticated";

GRANT SELECT, UPDATE, USAGE ON SEQUENCE "public"."venta_diaria_id_seq" TO "authenticated";

REVOKE ALL ON SEQUENCE "public"."venta_diaria_id_seq" FROM "postgres";

GRANT SELECT, UPDATE, USAGE ON SEQUENCE "public"."venta_diaria_id_seq" TO "postgres";

REVOKE ALL ON SEQUENCE "public"."venta_diaria_id_seq" FROM "service_role";

GRANT SELECT, UPDATE, USAGE ON SEQUENCE "public"."venta_diaria_id_seq" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."clientes" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."clientes" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."clientes" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."clientes" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."detalle_orden" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."detalle_orden" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."detalle_orden" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."detalle_orden" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."empleados" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."empleados" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."empleados" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."empleados" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."historial_pagos_empleados" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."historial_pagos_empleados" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."historial_pagos_empleados" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."historial_pagos_empleados" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."mesas" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."mesas" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."mesas" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."mesas" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."ordenes" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."ordenes" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."ordenes" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."ordenes" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."productos" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."productos" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."productos" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."productos" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."promociones" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."promociones" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."promociones" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."promociones" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."transacciones" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."transacciones" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."transacciones" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."transacciones" TO "service_role";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."venta_diaria" TO "anon", "authenticated";

REVOKE ALL ON TABLE "public"."venta_diaria" FROM "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."venta_diaria" TO "postgres";

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."venta_diaria" TO "service_role";

