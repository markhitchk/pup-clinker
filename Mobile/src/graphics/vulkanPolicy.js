// React Native Skia v3/Graphite uses Vulkan on Android.
// Decorative rendering is never used for gameplay or account authority.
export function shouldUseVulkanEffects(preferences={},gpuAvailable=false){
 return gpuAvailable===true
  &&preferences.animatedUi!==false
  &&preferences.reducedMotion!==true
  &&preferences.performancePreset!=='battery_saver';
}
export function graphicsBackendLabel(gpuAvailable){
 return gpuAvailable===true?'Vulkan · Skia Graphite':'Standard Android graphics';
}
