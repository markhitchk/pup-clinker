const {withMainApplication,withDangerousMod}=require('expo/config-plugins');
const fs=require('node:fs');
const path=require('node:path');
const registration='add(com.harleytg.puppycompat.PuppyCompatibilityPackage())';

function registerPackage(source){
 if(source.includes(registration))return source;
 const pattern=/PackageList\(this\)\.packages\.apply\s*\{/;
 if(!pattern.test(source))throw new Error('Puppy compatibility requires the Expo Kotlin PackageList template');
 return source.replace(pattern,match=>match+'\n              '+registration);
}

function withPuppyCompatibility(config){
 config=withMainApplication(config,mod=>{
  if(mod.modResults.language!=='kt')throw new Error('Puppy compatibility requires Kotlin MainApplication');
  mod.modResults.contents=registerPackage(mod.modResults.contents);
  return mod;
 });
 return withDangerousMod(config,['android',async mod=>{
  const dest=path.join(mod.modRequest.platformProjectRoot,'app/src/main/java/com/harleytg/puppycompat');
  fs.mkdirSync(dest,{recursive:true});
  fs.copyFileSync(path.join(__dirname,'puppy-compatibility/PuppyCompatibilityPackage.kt'),
    path.join(dest,'PuppyCompatibilityPackage.kt'));
  return mod;
 }]);
}
module.exports=withPuppyCompatibility;
module.exports.registerPackage=registerPackage;
