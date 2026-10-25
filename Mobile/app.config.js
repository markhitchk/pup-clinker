// Beta is a separate install. Production must remain an explicitly approved,
// correctly signed in-place upgrade so no existing Kotlin player data is lost.
// PUPPY_NATIVE_PREVIEW is retained only as an alias for older CI invocations.
export default ({config})=>{
 const beta=process.env.PUPPY_NATIVE_BETA==='1'||
  process.env.PUPPY_NATIVE_PREVIEW==='1'||process.env.PUPPY_NATIVE_PRODUCTION!=='1';
 if(!beta&&process.env.PUPPY_NATIVE_RELEASE_APPROVED!=='1'){
  throw new Error('Puppy Clicker production migration not approved: pass parity, cloud data, PupEye, original signing and Play internal tests first');
 }
 return {
  ...config,
  name:beta?'Puppy Clicker [Beta]':config.name,
  android:{
   ...config.android,
   package:beta?'com.harleytg.puppyclicker.beta':config.android.package,
   versionCode:beta?1:config.android.versionCode
  },
  extra:{...config.extra,migrationStatus:beta?'beta-account-migration-locked':'production-authentication-required'}
 };
};
