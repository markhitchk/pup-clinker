// Default to a separately installable migration-QA package; never ship an accidental
// replacement for the signed, data-bearing Kotlin Play Store app.
// Production migration needs both an explicit production target and release approval.
export default ({config})=>{
 const production=process.env.PUPPY_NATIVE_PRODUCTION==='1'&&process.env.PUPPY_NATIVE_PREVIEW!=='1';
 if(production&&process.env.PUPPY_NATIVE_RELEASE_APPROVED!=='1'){
  throw new Error('Puppy Clicker production migration not approved: pass parity, cloud data, PupEye, original signing and Play internal tests first');
 }
 const preview=!production;
 return {
  ...config,
  name:preview?'Puppy Clicker Migration QA':config.name,
  android:{
   ...config.android,
   package:preview?'com.harleytg.puppyclicker.preview':config.android.package,
   versionCode:preview?1:config.android.versionCode
  },
  extra:{...config.extra,migrationStatus:preview?'production-migration-qa-locked':'production-authentication-required'}
 };
};
