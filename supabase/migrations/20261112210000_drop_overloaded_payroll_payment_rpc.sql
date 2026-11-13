-- Migration: 20261112210000_drop_overloaded_payroll_payment_rpc.sql
-- Fixes PostgREST error PGRST203 ("could not choose the best candidate function")
--
-- Context:
-- An earlier migration introduced a 6-parameter version of registrar_pago_nomina with
-- default parameters for loan deductions, but left the legacy 4-parameter version in place.
-- PostgREST cannot disambiguate calls passing 4 parameters because both signatures match.
-- Dropping the legacy overload allows PostgREST to resolve the unified 6-parameter function cleanly.

DROP FUNCTION IF EXISTS public.registrar_pago_nomina(uuid, date, date, text);

-- Ensure execute permissions on the unified function
REVOKE ALL ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text, bigint, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text, bigint, uuid) TO authenticated, service_role;

COMMENT ON FUNCTION public.registrar_pago_nomina(uuid, date, date, text, bigint, uuid) IS
'Registers an employee payroll payment with optional loan deduction. Unified signature resolving PGRST203 overload conflict.';
