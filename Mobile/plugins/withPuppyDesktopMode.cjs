// Keep Chromebook and Samsung DeX in true, resizable multi-window mode.
// Runs during Expo prebuild; does not change production identity or signing.
const {withAndroidManifest}=require('expo/config-plugins');

function enableResizableWindowing(manifest){
 const application=manifest?.manifest?.application?.[0];
 if(!application?.$)throw new Error('Puppy desktop mode: expected Android application manifest');
 application.$['android:resizeableActivity']='true';
 const activities=application.activity||[];
 for(const activity of activities){
  if(!activity?.$)continue;
  const name=activity.$['android:name']||'';
  if(name==='.MainActivity'||name.endsWith('.MainActivity')){
   activity.$['android:resizeableActivity']='true';
   // Do not set android:screenOrientation or hardcode a desktop mode flag.
   // Expo and RN use the current window dp to reflow the interface.
  }
 }
 return manifest;
}
function withPuppyDesktopMode(config){
 return withAndroidManifest(config,mod=>{
  mod.modResults=enableResizableWindowing(mod.modResults);
  return mod;
 });
}
module.exports=withPuppyDesktopMode;
module.exports.enableResizableWindowing=enableResizableWindowing;
