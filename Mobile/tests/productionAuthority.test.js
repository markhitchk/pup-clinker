import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 classifyOriginalInstall,classifyVerifiedAccount,mayEnterGameplay,KOTLIN_PACKAGE,
 PRODUCTION_FUNCTION_URL
} from '../src/production/authority.js';

const probe=(change={})=>({
 packageName:KOTLIN_PACKAGE,
 hasOriginalSave:true,hasSealedSave:true,hasAuthorityState:true,
 hasOriginalSession:true,hasOriginalSigningKey:true,hasOriginalEncryptionKey:true,
 ...change
});

test('production root cannot launch guest gameplay or use AsyncStorage state',()=>{
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 const root=app.match(/export default function App\(\)\{[^\n]+\}/)?.[0]||'';
 assert.match(root,/<ProductionEntry\/>/);
 assert.doesNotMatch(root,/<PuppyApp\/>/);
 const entry=readFileSync(new URL('../src/production/ProductionEntry.jsx',import.meta.url),'utf8');
 assert.doesNotMatch(entry,/useGame|serializeGuestSave|AsyncStorage|guestSave|casinoSpend/);
 assert.equal(mayEnterGameplay(),false);
});

test('production bridge refuses separate beta package and missing legacy saves',()=>{
 assert.equal(classifyOriginalInstall(null).status,'NATIVE_UNAVAILABLE');
 assert.equal(classifyOriginalInstall(probe({packageName:'com.harleytg.puppyclicker.beta'})).status,'SIGNED_UPGRADE_REQUIRED');
 assert.equal(classifyOriginalInstall(probe({hasOriginalSave:false})).status,'ORIGINAL_SAVE_MISSING');
 assert.equal(classifyOriginalInstall(probe({hasSealedSave:false})).status,'ORIGINAL_SAVE_MISSING');
 assert.equal(classifyOriginalInstall(probe({hasOriginalSigningKey:false})).status,'PUPEYE_IDENTITY_INCOMPLETE');
 assert.equal(classifyOriginalInstall(probe({hasOriginalEncryptionKey:false})).status,'PUPEYE_IDENTITY_INCOMPLETE');
 assert.equal(classifyOriginalInstall(probe({hasOriginalSession:false})).status,'DISCORD_REAUTH_REQUIRED');
 assert.equal(classifyOriginalInstall(probe()).status,'LEGACY_IDENTITY_PRESENT');
 for(const x of [null,probe({hasOriginalSession:false}),probe()])
  assert.equal(classifyOriginalInstall(x).canPlay,false);
});

test('verified Pup Account and cloud data cannot bypass incomplete economy migration',()=>{
 assert.equal(classifyVerifiedAccount({verified:false}).status,'ACCOUNT_NOT_VERIFIED');
 assert.equal(classifyVerifiedAccount({verified:true,username:'A',accountId:'id',cloudSaveExists:false}).status,'CLOUD_RESTORE_REQUIRED');
 assert.equal(classifyVerifiedAccount({verified:true,username:'A',accountId:'id',cloudSaveExists:true,cloudRevision:0}).status,'CLOUD_RESTORE_REQUIRED');
 const account=classifyVerifiedAccount({
  verified:true,accountId:'id',username:'Account',cloudSaveExists:true,cloudRevision:3
 });
 assert.equal(account.status,'SERVER_ACCOUNT_CONFIRMED');
 assert.equal(account.canPlay,false);
 assert.equal(mayEnterGameplay(account),false);
});

test('native handoff uses Kotlin original identity and pinned production Pup Account endpoint only',()=>{
 const native=readFileSync(new URL('../plugins/puppy-production/PuppyProductionAuthorityPackage.kt',import.meta.url),'utf8');
 const plugin=readFileSync(new URL('../plugins/withPuppyProductionAuthority.cjs',import.meta.url),'utf8');
 const appConfig=readFileSync(new URL('../app.json',import.meta.url),'utf8');
 for(const snippet of [
  'pupeye/authority_state_v2.pup','pupeye/supabase_session_v1.pup',
  'pupeye_install_signing_ec_v2','puppy_clicker_save_aes_v1',
  'AES/GCM/NoPadding','SHA256withECDSA','PuppyClicker/PupEye/request/v1',
  'X-Pupeye-Session','account-status',PRODUCTION_FUNCTION_URL
 ])assert.ok(native.includes(snippet),snippet);
 assert.ok(plugin.includes('PuppyProductionAuthorityPackage.kt'));
 assert.ok(appConfig.includes('withPuppyProductionAuthority.cjs'));
 assert.doesNotMatch(native,/KeyPairGenerator|createUser|cloud-save-write|install.*delete\(/i);
});
