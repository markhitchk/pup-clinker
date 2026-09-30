-- Keep the automatic RLS event trigger internal to Postgres when the helper exists.
-- Some fresh/local Supabase databases do not have this pre-existing helper, so the migration
-- must remain safe on both upgraded production projects and clean CI databases.
do $$
begin
  if to_regprocedure('public.rls_auto_enable()') is not null then
    execute 'revoke execute on function public.rls_auto_enable() from public';
    execute 'revoke execute on function public.rls_auto_enable() from anon';
    execute 'revoke execute on function public.rls_auto_enable() from authenticated';
  end if;
end
$$;
