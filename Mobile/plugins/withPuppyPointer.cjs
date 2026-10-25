const {withAndroidManifest,withMainApplication,withDangerousMod}=require('expo/config-plugins');
const fs=require('node:fs');
const path=require('node:path');
const registration='add(com.harleytg.puppypointer.PuppyPointerPackage())';
function registerPackage(source){
 if(source.includes(registration))return source;
 const pattern=/PackageList\(this\)\.packages\.apply\s*\{/;
 if(!pattern.test(source))throw new Error('Puppy pointer: unsupported Kotlin PackageList template');
 return source.replace(pattern,match=>match+'\n              '+registration);
}
function withPuppyPointer(config){
 // The Kotlin app uses the original drawable directly for both launcher icons.
 // An adaptive foreground would enlarge/crop this same artwork differently.
 config=withAndroidManifest(config,mod=>{
  const application=mod.modResults.manifest.application?.[0];
  if(!application)throw new Error('Puppy branding: application manifest is missing');
  application.$['android:icon']='@drawable/source_logo';
  application.$['android:roundIcon']='@drawable/source_logo';
  return mod;
 });
 config=withMainApplication(config,mod=>{
  if(mod.modResults.language!=='kt')throw new Error('Puppy pointer requires Kotlin MainApplication');
  mod.modResults.contents=registerPackage(mod.modResults.contents);return mod;
 });
 return withDangerousMod(config,['android',async mod=>{
  const root=mod.modRequest.projectRoot,dest=path.join(mod.modRequest.platformProjectRoot,'app/src/main');
  const java=path.join(dest,'java/com/harleytg/puppypointer');fs.mkdirSync(java,{recursive:true});
  fs.copyFileSync(path.join(__dirname,'puppy-pointer/PuppyPointerPackage.kt'),path.join(java,'PuppyPointerPackage.kt'));
  const res=path.join(dest,'res/drawable-nodpi');fs.mkdirSync(res,{recursive:true});
  fs.copyFileSync(path.join(root,'assets/logos/puppy_clicker.png'),path.join(res,'source_logo.png'));
  for(const name of fs.readdirSync(path.join(root,'assets/pointers'))){
   if(/^puppy_pointer_(light|dark)_[a-z_]+\.png$/.test(name))fs.copyFileSync(path.join(root,'assets/pointers',name),path.join(res,name));
  }
  return mod;
 }]);
}
module.exports=withPuppyPointer;
module.exports.registerPackage=registerPackage;
