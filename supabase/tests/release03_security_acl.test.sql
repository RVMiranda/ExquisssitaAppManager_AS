BEGIN;
SELECT plan(20);

SELECT ok(NOT has_table_privilege('anon', 'public.clientes', 'SELECT'),
  'anonymous role has no table-level customer access');
SELECT ok(has_column_privilege('authenticated', 'public.clientes', 'nombre', 'SELECT'),
  'authenticated can read an explicitly granted customer profile column');
SELECT ok(NOT has_column_privilege('authenticated', 'public.clientes', 'qr_token_hash', 'SELECT'),
  'authenticated cannot select a QR digest');
SELECT ok(NOT has_column_privilege('authenticated', 'public.clientes', 'puntos_lealtad', 'UPDATE'),
  'authenticated cannot write a customer points balance');
SELECT ok(NOT has_table_privilege('authenticated', 'public.clientes', 'INSERT'),
  'authenticated cannot insert customer rows directly');
SELECT ok(NOT has_table_privilege('authenticated', 'public.clientes', 'DELETE'),
  'authenticated cannot delete customer rows directly');
SELECT ok(NOT has_table_privilege('anon', 'public.clientes', 'TRUNCATE'),
  'anonymous role cannot truncate customer rows');
SELECT ok(NOT has_table_privilege('authenticated', 'public.clientes', 'TRUNCATE'),
  'authenticated role cannot truncate customer rows');
SELECT ok(NOT has_table_privilege('anon', 'public.clientes', 'MAINTAIN'),
  'anonymous role cannot maintain customer tables');
SELECT ok(NOT has_table_privilege('authenticated', 'public.clientes', 'TRIGGER'),
  'authenticated role cannot create or alter customer triggers');
SELECT ok(NOT has_table_privilege('authenticated', 'public.clientes', 'REFERENCES'),
  'authenticated role cannot create foreign keys against customer data');
SELECT ok(NOT has_table_privilege('authenticated', 'public.visitas_clientes', 'INSERT'),
  'visits can only be inserted by the checked RPC');
SELECT ok(NOT has_table_privilege('authenticated', 'public.puntos_movimientos', 'INSERT'),
  'points ledger can only be appended by the checked RPC');
SELECT ok(NOT has_table_privilege('authenticated', 'public.puntos_movimientos', 'TRUNCATE'),
  'points ledger cannot be truncated by the app');
SELECT ok(NOT has_column_privilege('authenticated', 'public.canjes_recompensas', 'solicitud_hash', 'SELECT'),
  'customers and staff cannot read redemption request fingerprints');
SELECT ok(NOT has_table_privilege('anon', 'public.recompensas', 'SELECT'),
  'anonymous visitors cannot access the rewards catalog');
SELECT ok(has_function_privilege('authenticated', 'public.update_floor_employee_salary(uuid,bigint,bigint)', 'EXECUTE'),
  'authenticated operators can call salary RPC, which applies row-specific authorization');
SELECT ok(NOT has_function_privilege('anon', 'public.update_floor_employee_salary(uuid,bigint,bigint)', 'EXECUTE'),
  'anonymous users cannot call salary RPC');
SELECT ok(NOT has_function_privilege('anon', 'public.registrar_visita_qr(text)', 'EXECUTE'),
  'anonymous users cannot register visits');
SELECT ok(NOT EXISTS (
  SELECT 1 FROM pg_trigger
   WHERE tgrelid = 'auth.users'::regclass
     AND tgname = 'on_auth_user_created_set_role'
     AND NOT tgisinternal
), 'Auth signup no longer auto-assigns a staff role');

SELECT * FROM finish();
ROLLBACK;
