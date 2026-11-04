BEGIN;
SELECT plan(4);

INSERT INTO auth.users (id, email, email_confirmed_at)
VALUES ('d4000000-0000-0000-0000-000000000001', 'semester-customer@example.invalid', now()),
       ('d4000000-0000-0000-0000-000000000002', 'semester-manager@example.invalid', now());
INSERT INTO public.user_roles(user_id, role)
VALUES ('d4000000-0000-0000-0000-000000000002', 'admin');
INSERT INTO public.recompensas(id, titulo, descripcion, costo_puntos, activo)
VALUES ('d4000000-0000-0000-0000-000000000003', 'Semestral test', '', 20, true),
       ('d4000000-0000-0000-0000-000000000009', 'Another semester test', '', 20, true);

CREATE TEMP TABLE semester_test_values (qr_token text, customer_id uuid, redeemed jsonb);
GRANT SELECT, INSERT, UPDATE ON semester_test_values TO authenticated;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-000000000001', true);
INSERT INTO semester_test_values(customer_id)
SELECT (public.ensure_customer_profile('Semestral Test', NULL)->>'id')::uuid;
UPDATE semester_test_values
SET qr_token = public.get_or_create_customer_qr_token()->>'qr_token';

RESET ROLE;
INSERT INTO public.puntos_movimientos(
  cliente_id, puntos_delta, tipo, motivo, idempotency_key, registrado_por
)
SELECT customer_id, 100, 'ajuste', 'Saldo de prueba', 'd4000000-0000-0000-0000-000000000004',
       'd4000000-0000-0000-0000-000000000002'
FROM semester_test_values;

-- A receipt from seven months ago belongs to an earlier semester and must not block.
INSERT INTO public.canjes_recompensas(
  cliente_id, recompensa_id, puntos_costo, idempotency_key, solicitud_hash, canjeado_por, created_at
)
SELECT customer_id, 'd4000000-0000-0000-0000-000000000003', 20,
       'd4000000-0000-0000-0000-000000000005',
       repeat('a', 64), 'd4000000-0000-0000-0000-000000000002', now() - interval '7 months'
FROM semester_test_values;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-000000000002', true);
UPDATE semester_test_values
SET redeemed = public.redeem_loyalty_reward(
  qr_token, 'd4000000-0000-0000-0000-000000000003', 'd4000000-0000-0000-0000-000000000006'
);

SELECT is((SELECT redeemed->>'status' FROM semester_test_values), 'confirmed',
  'A redemption from a previous semester does not block the current semester');
SELECT is((public.redeem_loyalty_reward(
  (SELECT qr_token FROM semester_test_values),
  'd4000000-0000-0000-0000-000000000003',
  'd4000000-0000-0000-0000-000000000006'
))->>'already_processed', 'true',
  'Idempotent retry of the original request remains successful');
SELECT is((public.redeem_loyalty_reward(
  (SELECT qr_token FROM semester_test_values),
  'd4000000-0000-0000-0000-000000000009',
  'd4000000-0000-0000-0000-00000000000a'
))->>'status', 'confirmed',
  'A different reward can still be redeemed once during the active semester');
SELECT throws_ok(
  $$SELECT public.redeem_loyalty_reward(
    (SELECT qr_token FROM semester_test_values),
    'd4000000-0000-0000-0000-000000000003',
    'd4000000-0000-0000-0000-000000000008'
  )$$,
  '22023',
  'reward_already_redeemed_this_period',
  'Second redemption of same reward in active semester is rejected'
);

SELECT * FROM finish();
ROLLBACK;

