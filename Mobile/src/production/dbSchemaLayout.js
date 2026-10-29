// React Native Android accesses Puppy Clicker data through signed Supabase
// Edge Functions/native PupEye modules, not browser-style direct table queries.
// v2 groups the database into accounts, PupEye, game, exchange, admin and web.
// Legacy public tables remain authoritative while existing installations run.
export const ANDROID_DATABASE_CONTRACT = Object.freeze({
 layoutVersion:2,
 canonicalSchema:'public',
 groupedSchemas:Object.freeze(['puppy_accounts','puppy_pupeye','puppy_game','puppy_exchange','puppy_admin','puppy_web']),
 edgeFunction:'pup-account',
 legacyEndpointsCompatible:true,
});
export function supportsDatabaseLayout(version){
 // Older Edge Functions do not report a layout version; keep working.
 return version==null || (Number.isInteger(version) && version>=1 && version<=ANDROID_DATABASE_CONTRACT.layoutVersion);
}
