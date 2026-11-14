const {withAndroidManifest}=require('expo/config-plugins');

// Skia Graphite requests Vulkan 1.1 by default. Puppy Clicker must remain
// installable on devices without Vulkan: the JS yard effect checks
// Skia.gpu.isAvailable and retains the standard React Native stage.
function allowVulkanFallback(results){
 const manifest=results.manifest;
 manifest.$=manifest.$||{};
 manifest.$['xmlns:tools']='http://schemas.android.com/tools';
 const features=manifest['uses-feature']||[];
 const name='android.hardware.vulkan.version';
 const feature=features.find(item=>item.$?.['android:name']===name);
 if(feature){
  feature.$['tools:node']='remove';
 }else{
  features.push({$: {'android:name':name,'tools:node':'remove'}});
 }
 manifest['uses-feature']=features;
 return results;
}
function withPuppyVulkan(config){
 return withAndroidManifest(config,mod=>{
  mod.modResults=allowVulkanFallback(mod.modResults);
  return mod;
 });
}
module.exports=withPuppyVulkan;
module.exports.allowVulkanFallback=allowVulkanFallback;
