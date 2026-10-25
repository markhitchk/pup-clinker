const {withMainApplication,withDangerousMod}=require('expo/config-plugins');
const fs=require('node:fs');
const path=require('node:path');

const registration='add(com.harleytg.puppyproduction.PuppyProductionAuthorityPackage())';
function registerProductionAuthority(contents){
 if(contents.includes(registration))return contents;
 const marker=/PackageList\(this\)\.packages\.apply\s*\{/;
 if(!marker.test(contents))throw Error('Pup Account: unsupported Kotlin MainApplication template');
 return contents.replace(marker,match=>match+'\n              '+registration);
}
function withPuppyProductionAuthority(config){
 config=withMainApplication(config,mod=>{
  if(mod.modResults.language!=='kt')throw Error('Pup Account requires Kotlin MainApplication');
  mod.modResults.contents=registerProductionAuthority(mod.modResults.contents);
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
  return mod;
 }]);
}
module.exports=withPuppyProductionAuthority;
module.exports.registerProductionAuthority=registerProductionAuthority;
