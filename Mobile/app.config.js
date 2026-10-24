// The production identity remains the original Google Play package.
// Set PUPPY_NATIVE_PREVIEW=1 for sideloadable debug testing alongside the Play install.
export default ({config})=>{
 const preview=process.env.PUPPY_NATIVE_PREVIEW==='1';
 return {
  ...config,
  name:preview?'Puppy Clicker Preview':config.name,
  android:{
   ...config.android,
   package:preview?'com.harleytg.puppyclicker.preview':config.android.package,
   versionCode:preview?1:config.android.versionCode,
  },
  extra:{...config.extra,migrationStatus:preview?'guest-preview-side-by-side':'guest-preview-not-production'}
 };
};
