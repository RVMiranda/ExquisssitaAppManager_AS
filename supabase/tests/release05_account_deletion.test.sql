BEGIN;
SELECT plan(13);

CREATE TEMP TABLE deletion_test_values (customer_id uuid, qr_token text, result jsonb);
GRANT SELECT, INSERT, UPDATE ON deletion_test_values TO authenticated;


INSERT INTO auth.users(id, email, email_confirmed_at, confirmed_at)
VALUES ('e5000000-0000-0000-0000-000000000001', 'delete.customer@example.invalid', now(), now());
INSERT INTO public.user_roles(user_id, role)
VALUES ('e5000000-0000-0000-0000-000000000001', 'empleado');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e5000000-0000-0000-0000-000000000001', true);
INSERT INTO deletion_test_values(customer_id)
SELECT (public.ensure_customer_profile('Cuenta para eliminar', '5550000001')->>'id')::uuid;
UPDATE deletion_test_values SET qr_token = public.get_or_create_customer_qr_token()->>'qr_token';
RESET ROLE;

INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo, idempotency_key)
SELECT customer_id, 40, 'apertura', 'Saldo de prueba', 'e5000000-0000-0000-0000-000000000002'
FROM deletion_test_values;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e5000000-0000-0000-0000-000000000001', true);
UPDATE deletion_test_values SET result = public.delete_customer_account();
SELECT is((SELECT result->>'status' FROM deletion_test_values), 'deleted',
  'Account deletion succeeds for the authenticated owner');
RESET ROLE;

SELECT is((SELECT count(*)::integer FROM public.clientes c
  JOIN deletion_test_values t ON t.customer_id = c.id
  WHERE c.deleted_at IS NOT NULL AND c.nombre = 'Usuario dado de baja'
    AND c.telefono IS NULL AND c.qr_token_hash IS NULL), 1,
  'Customer PII and QR digest are anonymized');
SELECT is((SELECT count(*)::integer FROM public.clientes c
  JOIN deletion_test_values t ON t.customer_id = c.id WHERE c.puntos_lealtad = 0), 1,
  'Customer balance is zero after deletion');
SELECT is((SELECT count(*)::integer FROM public.puntos_movimientos m
  JOIN deletion_test_values t ON t.customer_id = m.cliente_id
  WHERE m.tipo = 'ajuste' AND m.puntos_delta = -40
    AND m.motivo = 'Baja de cuenta: cancelación de saldo'), 1,
  'A compensating adjustment preserves the ledger audit trail');
SELECT is((SELECT count(*)::integer FROM auth.users
  WHERE id = 'e5000000-0000-0000-0000-000000000001'
    AND deleted_at IS NOT NULL AND email IS NULL AND phone IS NULL
    AND raw_user_meta_data = '{}'::jsonb AND encrypted_password IS NULL), 1,
  'Auth credentials and direct identity data are revoked/anonymized');
SELECT is((SELECT count(*)::integer FROM private_customer.qr_secrets q
  JOIN deletion_test_values t ON t.customer_id = q.cliente_id), 0,
  'Permanent QR recovery secret is removed');
SELECT is((SELECT count(*)::integer FROM public.user_roles
  WHERE user_id = 'e5000000-0000-0000-0000-000000000001'), 0,
  'Stale employee roles are removed');
SELECT throws_ok(
  $$INSERT INTO public.puntos_movimientos(cliente_id, puntos_delta, tipo, motivo, idempotency_key)
    SELECT customer_id, 10, 'ajuste', 'Ajuste posterior a la baja', 'e5000000-0000-0000-0000-000000000003'
      FROM deletion_test_values$$,
  '42501', 'customer_account_deleted',
  'Manager and server-side writers cannot add ledger entries after deletion'
);

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'e5000000-0000-0000-0000-000000000001', true);
SELECT is((SELECT count(id)::integer FROM public.clientes), 0,
  'RLS hides the deleted profile from its former session');
SELECT throws_ok(
  $$SELECT public.ensure_customer_profile('Reactivar cuenta', NULL)$$,
  '42501', 'customer_account_deleted',
  'A still-issued access token cannot recreate the deleted profile'
);
SELECT throws_ok(
  $$SELECT public.get_or_create_customer_qr_token()$$,
  '42501', 'customer_account_deleted',
  'A still-issued access token cannot recover a revoked QR'
);
UPDATE deletion_test_values SET result = public.delete_customer_account();
SELECT is((SELECT result->>'status' FROM deletion_test_values), 'already_deleted',
  'A retried deletion is idempotent');
RESET ROLE;

SELECT throws_ok(
  $$INSERT INTO auth.users(id, email, email_confirmed_at)
    VALUES ('e5000000-0000-0000-0000-000000000009', 'DELETE.CUSTOMER@example.invalid', now())$$,
  'P0001', 'email_registration_blocked',
  'Email registration is blocked case-insensitively after deletion'
);

SELECT * FROM finish();
ROLLBACK;
