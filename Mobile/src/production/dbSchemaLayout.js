// Puppy Clicker v3: physical tables live in six private grouped schemas.
// React Native uses signed, device-bound PupEye Edge Functions. The legacy
// public endpoints are security-invoker compatibility views, not source tables.
// Never perform ON CONFLICT against a view or put service-role keys in Android.
export const ANDROID_DATABASE_CONTRACT = Object.freeze({
 layoutVersion:3,
 canonicalSchemas:Object.freeze({
  accounts:'puppy_accounts',
  pupeye:'puppy_pupeye',
  game:'puppy_game',
  exchange:'puppy_exchange',
  admin:'puppy_admin',
  web:'puppy_web',
 }),
 compatibilitySchema:'public',
 groupedSchemas:Object.freeze(['puppy_accounts','puppy_pupeye','puppy_game','puppy_exchange','puppy_admin','puppy_web']),
 edgeFunction:'pup-account',
 legacyEndpointsCompatible:true,
});
export function supportsDatabaseLayout(version){
 // Older signed server replies do not announce a layout version.
 // Reject unknown, malformed or future layouts rather than risking progress.
 return version==null || (Number.isInteger(version) && version>=1 && version<=ANDROID_DATABASE_CONTRACT.layoutVersion);
}
