import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';

const native=readFileSync(new URL('../plugins/puppy-compatibility/PuppyCompatibilityPackage.kt',import.meta.url),'utf8');
const plugin=readFileSync(new URL('../plugins/withPuppyCompatibility.cjs',import.meta.url),'utf8');
const config=JSON.parse(readFileSync(new URL('../app.json',import.meta.url),'utf8'));

test('React-native migration bridge refers to the original Kotlin app encryption, identity and session names',()=>{
 for(const marker of ['pupeye/authority_state_v2.pup','pupeye/supabase_session_v1.pup',
  'pupeye_install_signing_ec_v2','puppy_clicker_save_aes_v1','PCE1',
  'PuppyClicker/device-save/v1','puppy_clicker_save','puppy_discord_signup_v1'])
  assert.ok(native.includes(marker),marker);
});

test('legacy bridge is read-only and never generates keys, wipes save data or returns tokens',()=>{
 assert.doesNotMatch(native,/generateKeyPair\\(|generateKey\\(|\\.delete\\(|\\.edit\\(|\.clear\\(|putString\\("sessionToken"/);
 assert.match(native,/productionPackage \\|\\| legacyData/);
 assert.match(native,/legacy-integrity-review-required/);
 assert.match(native,/cloudSavePreserved/);
});

test('Expo prebuild installs the Android compatibility package',()=>{
 assert.ok(config.expo.plugins.includes('./plugins/withPuppyCompatibility.cjs'));
 const register=requireCompat();
 assert.match(register('PackageList(this).packages.apply {'),/PuppyCompatibilityPackage/);
 assert.match(plugin,/withDangerousMod/);
});

function requireCompat(){
 const mod=requireModule();
 return mod.registerPackage;
}
function requireModule(){
 // ESM package has a CJS Expo plugin.
 return createRequire(import.meta.url)('../plugins/withPuppyCompatibility.cjs');
}
