import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import configPlugin from '../app.config.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');
const base=JSON.parse(read('../app.json')).expo;

test('beta launcher routes to authenticated PupEye/Discord bootstrap, never guest gameplay',()=>{
 const root=read('../App.js');
 const entry=read('../src/production/ProductionEntry.jsx');
 const beta=read('../src/production/BetaAccountEntry.jsx');
 assert.match(root,/export default function App\(\)\{return <SafeAreaProvider><ProductionEntry\/><\/SafeAreaProvider>;\}/);
 assert.match(entry,/<BetaAccountEntry\/>/);
 assert.match(beta,/Connect with Discord/);
 assert.match(beta,/cloudRead\(/);
 assert.doesNotMatch(beta,/useGame|GUEST_SAVE_KEY|AsyncStorage|serializeGuestSave|PuppyApp|freshGame/);
 assert.doesNotMatch(beta,/mayEnterGameplay\(true\)|onboardingComplete=true/);
});

test('beta native module has isolated Keystore identity and signed Supabase calls',()=>{
 const native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const plugin=read('../plugins/withPuppyProductionAuthority.cjs');
 for(const phrase of [
  'com.harleytg.puppyclicker.beta','pupeye_install_signing_ec_v2',
  'puppy_clicker_save_aes_v1','AndroidKeyStore','KeyPairGenerator',
  'AES/GCM/NoPadding','SHA256withECDSA','PuppyClicker/PupEye/request/v1',
  'pupeye-register','pupeye-auth-discord','cloud-save-read',
  'state','verifier','code_challenge_method','S256',
  'X-Pupeye-Session'
 ])assert.ok(native.includes(phrase),phrase);
 assert.match(plugin,/PuppyBetaAccountPackage.kt/);
 assert.match(plugin,/PuppyBetaAccountPackage\(\)/);
 assert.doesNotMatch(native,/cloud-save-write|service_role|sb_secret_|delete\(.*sessionFile/);
});

test('beta package registers the Discord callback without altering Play configuration',()=>{
 const vars=['PUPPY_NATIVE_BETA','PUPPY_NATIVE_PRODUCTION','PUPPY_NATIVE_RELEASE_APPROVED'];
 const prev=Object.fromEntries(vars.map(k=>[k,process.env[k]]));
 try{
  for(const k of vars)delete process.env[k];
  process.env.PUPPY_NATIVE_BETA='1';
  const config=configPlugin({config:base});
  assert.equal(config.android.package,'com.harleytg.puppyclicker.beta');
  assert.equal(config.scheme,'puppyclickerbeta');
  assert.equal(config.android.intentFilters[0].data[0].scheme,'discord-1547982688898777118');
  process.env.PUPPY_NATIVE_BETA='0';
  process.env.PUPPY_NATIVE_PRODUCTION='1';
  process.env.PUPPY_NATIVE_RELEASE_APPROVED='1';
  const play=configPlugin({config:base});
  assert.equal(play.android.package,'com.harleytg.puppyclicker');
  assert.equal(play.scheme,'puppyclicker');
  assert.equal(play.android.intentFilters,undefined);
 }finally{
  for(const [key,value] of Object.entries(prev)){
   if(value===undefined)delete process.env[key];else process.env[key]=value;
  }
 }
});
