-- Keep the automatic RLS event trigger internal to Postgres.
-- Client roles must never be able to invoke this SECURITY DEFINER helper as an RPC.
revoke execute on function public.rls_auto_enable() from public;
revoke execute on function public.rls_auto_enable() from anon;
revoke execute on function public.rls_auto_enable() from authenticated;
