import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join,dirname} from 'node:path';
import {shouldUseVulkanEffects,graphicsBackendLabel} from '../src/graphics/vulkanPolicy.js';

const root=join(dirname(fileURLToPath(import.meta.url)),'..');
const app=JSON.parse(readFileSync(join(root,'app.json'),'utf8')).expo;
const pkg=JSON.parse(readFileSync(join(root,'package.json'),'utf8'));

test('Vulkan effects require an available GPU and normal motion',()=>{
 assert.equal(shouldUseVulkanEffects({},true),true);
 assert.equal(shouldUseVulkanEffects({performancePreset:'quality'},true),true);
 for(const options of [{reducedMotion:true},{animatedUi:false},{performancePreset:'battery_saver'}]){
  assert.equal(shouldUseVulkanEffects(options,true),false);
 }
 assert.equal(shouldUseVulkanEffects({},false),false);
 assert.equal(graphicsBackendLabel(false),'Standard Android graphics');
 assert.match(graphicsBackendLabel(true),/Vulkan/);
});

test('Skia Graphite requires a custom native APK and min Android SDK 26',()=>{
 assert.equal(pkg.dependencies['react-native-skia'],'3.10.0');
 assert.equal(Boolean(pkg.dependencies['@shopify/react-native-skia']),false);
 assert.equal(app.plugins.some(plugin=>Array.isArray(plugin)&&plugin[0]==='expo-build-properties'&&plugin[1].android.minSdkVersion===26),true);
 assert.equal(app.plugins.includes('./plugins/withPuppyVulkan.cjs'),true);
});

test('Vulkan layer preserves native touch controls and opt-out fallback',()=>{
 const source=readFileSync(join(root,'src/components/VulkanYardEffects.jsx'),'utf8');
 assert.match(source,/Skia\.gpu\?\.isAvailable===true/);
 assert.match(source,/pointerEvents="none"/);
 assert.match(source,/backingView:'TextureView'/);
 const plugin=readFileSync(join(root,'plugins/withPuppyVulkan.cjs'),'utf8');
 assert.match(plugin,/tools:node/);
 assert.match(plugin,/android\.hardware\.vulkan\.version/);
});
