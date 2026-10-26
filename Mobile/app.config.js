// Beta is a separate install. Production must remain an explicitly approved,
// correctly signed in-place upgrade so no existing Kotlin player data is lost.
// PUPPY_NATIVE_PREVIEW is retained only as an alias for older CI invocations.
export default ({config})=>{
 // The beta package can be installed alongside Play, but must not downgrade
 // an earlier beta. CI supplies a monotonic UTC-minute Android versionCode.
 const requestedBetaCode=Number(process.env.PUPPY_BETA_VERSION_CODE||1000);
 if(!Number.isSafeInteger(requestedBetaCode)||requestedBetaCode<1000||requestedBetaCode>2100000000)
  throw new Error('Invalid Puppy Clicker beta versionCode');
 const beta=process.env.PUPPY_NATIVE_BETA==='1'||
  process.env.PUPPY_NATIVE_PREVIEW==='1'||process.env.PUPPY_NATIVE_PRODUCTION!=='1';
 if(!beta&&process.env.PUPPY_NATIVE_RELEASE_APPROVED!=='1'){
  throw new Error('Puppy Clicker production migration not approved: pass parity, cloud data, PupEye, original signing and Play internal tests first');
 }
 return {
  ...config,
  name:beta?'Puppy Clicker [Beta]':config.name,
  scheme:beta?'puppyclickerbeta':config.scheme,
  android:{
   ...config.android,
   package:beta?'com.harleytg.puppyclicker.beta':config.android.package,
   intentFilters:beta?[{
    action:'VIEW',
    data:[{scheme:'discord-1547982688898777118',pathPrefix:'/authorize/callback'}],
    category:['BROWSABLE','DEFAULT']
   }]:config.android.intentFilters,
   versionCode:beta?requestedBetaCode:config.android.versionCode
  },
  extra:{...config.extra,migrationStatus:beta?'beta-account-migration-locked':'production-authentication-required'}
 };
};
