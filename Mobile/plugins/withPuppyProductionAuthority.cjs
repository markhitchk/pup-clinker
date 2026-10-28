const {withMainApplication,withDangerousMod}=require('expo/config-plugins');
const fs=require('node:fs');
const path=require('node:path');

const registration='add(com.harleytg.puppyproduction.PuppyProductionAuthorityPackage())';
const betaRegistration='add(com.harleytg.puppyproduction.PuppyBetaAccountPackage())';
const nativeCrashInstall='com.harleytg.puppyproduction.PuppyStartupCrashStore.install(this)';
function installNativeCrashRecorder(contents){
 if(contents.includes(nativeCrashInstall))return contents;
 const marker=/override fun onCreate\(\)\s*\{/;
 if(!marker.test(contents))throw Error('Pup Account: unsupported Android onCreate template');
 return contents.replace(marker,match=>match+'\n    '+nativeCrashInstall);
}
function registerProductionAuthority(contents){
 if(contents.includes(registration)&&contents.includes(betaRegistration))return contents;
 const marker=/PackageList\(this\)\.packages\.apply\s*\{/;
 if(!marker.test(contents))throw Error('Pup Account: unsupported Kotlin MainApplication template');
 return contents.replace(marker,match=>match+'\n              '+registration+'\n              '+betaRegistration);
}
function withPuppyProductionAuthority(config){
 config=withMainApplication(config,mod=>{
  if(mod.modResults.language!=='kt')throw Error('Pup Account requires Kotlin MainApplication');
  mod.modResults.contents=installNativeCrashRecorder(registerProductionAuthority(mod.modResults.contents));
  return mod;
 });
 return withDangerousMod(config,['android',async mod=>{
  const java=path.join(mod.modRequest.platformProjectRoot,
   'app/src/main/java/com/harleytg/puppyproduction');
  fs.mkdirSync(java,{recursive:true});
  fs.copyFileSync(
   path.join(__dirname,'puppy-production/PuppyProductionAuthorityPackage.kt'),
   path.join(java,'PuppyProductionAuthorityPackage.kt')
  );
  fs.copyFileSync(
   path.join(__dirname,'puppy-production/PuppyBetaAccountPackage.kt'),
   path.join(java,'PuppyBetaAccountPackage.kt')
  );
  fs.copyFileSync(
   path.join(__dirname,'puppy-production/PuppyStartupCrashStore.kt'),
   path.join(java,'PuppyStartupCrashStore.kt')
  );
  return mod;
 }]);
}
module.exports=withPuppyProductionAuthority;
module.exports.registerProductionAuthority=registerProductionAuthority;

module.exports.installNativeCrashRecorder=installNativeCrashRecorder;
