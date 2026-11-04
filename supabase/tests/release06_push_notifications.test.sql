BEGIN;
SELECT plan(9);

CREATE TEMP TABLE push_test_values (
  customer_id uuid,
  other_customer_id uuid,
  token text,
  initial_result jsonb,
  duplicate_result jsonb,
  removal_result boolean
);
GRANT SELECT, INSERT, UPDATE ON push_test_values TO authenticated;

INSERT INTO auth.users(id, email, email_confirmed_at, confirmed_at)
VALUES
  ('e6000000-0000-0000-0000-000000000001', 'push.customer@example.invalid', now(), now()),
  ('e6000000-0000-0000-0000-000000000002', 'push.other@example.invalid', now(), now());
INSERT INTO public.user_roles(user_id, role)
VALUES ('e6000000-0000-0000-0000-000000000001', 'empleado'),
       ('e6000000-0000-0000-0000-000000000002', 'empleado');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e6000000-0000-0000-0000-000000000001', true);
INSERT INTO push_test_values(customer_id, token)
SELECT (public.ensure_customer_profile('Push customer', NULL)->>'id')::uuid,
       'fcm-test-token-customer-1';
UPDATE push_test_values
   SET initial_result = public.registrar_dispositivo_fcm(token, 'Test device')
 WHERE customer_id IS NOT NULL;
UPDATE push_test_values
   SET duplicate_result = public.registrar_dispositivo_fcm(token, 'Updated test device')
 WHERE customer_id IS NOT NULL;
SELECT is((SELECT count(*)::integer FROM public.dispositivos_fcm), 1,
  'Authenticated customer registers one device');
SELECT is((SELECT initial_result->>'id' FROM push_test_values),
          (SELECT duplicate_result->>'id' FROM push_test_values),
  'Registration is idempotent for the same FCM token');
RESET ROLE;

SELECT is((SELECT modelo_dispositivo FROM public.dispositivos_fcm
  WHERE fcm_token = 'fcm-test-token-customer-1'), 'Updated test device',
  'Idempotent registration refreshes device metadata');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e6000000-0000-0000-0000-000000000002', true);
INSERT INTO push_test_values(other_customer_id)
SELECT (public.ensure_customer_profile('Other customer', NULL)->>'id')::uuid;
SELECT is((SELECT count(*)::integer FROM public.dispositivos_fcm), 0,
  'RLS hides another customer device token');
SELECT throws_ok(
  $$INSERT INTO public.dispositivos_fcm(cliente_id, fcm_token)
    SELECT other_customer_id, 'fcm-direct-insert-forbidden' FROM push_test_values$$,
  '42501', 'permission denied for table dispositivos_fcm',
  'Device creation must use the owner-checked registration RPC'
);
UPDATE push_test_values
   SET removal_result = public.eliminar_dispositivo_fcm('fcm-test-token-customer-1')
 WHERE other_customer_id IS NOT NULL;
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.dispositivos_fcm
  WHERE fcm_token = 'fcm-test-token-customer-1'), 1,
  'A customer cannot unlink another account device');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e6000000-0000-0000-0000-000000000001', true);
UPDATE push_test_values
   SET removal_result = public.eliminar_dispositivo_fcm(token)
 WHERE customer_id IS NOT NULL;
SELECT is((SELECT removal_result FROM push_test_values WHERE customer_id IS NOT NULL), true,
  'Owner can unlink its device');
SELECT is((SELECT count(*)::integer FROM public.dispositivos_fcm), 0,
  'Unlinked device token is removed');
UPDATE push_test_values
   SET initial_result = public.registrar_dispositivo_fcm(token, 'Test device')
 WHERE customer_id IS NOT NULL;
SELECT public.delete_customer_account();
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.dispositivos_fcm
  WHERE fcm_token = 'fcm-test-token-customer-1'), 0,
  'Account deletion automatically purges all registered devices');

SELECT * FROM finish();
ROLLBACK;
