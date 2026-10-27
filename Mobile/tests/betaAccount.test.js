import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import configPlugin from '../app.config.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');
const base=JSON.parse(read('../app.json')).expo;

test('beta launches full Puppy Clicker screen hierarchy, with optional Discord sheet',()=>{
 const root=read('../App.js');
 const beta=read('../src/production/BetaAccountEntry.jsx');
 const hook=read('../src/state/useGame.js');
 assert.match(root,/<PuppyApp betaMode\/>/);
 assert.match(root,/<BetaAccountEntry visible=\{accountOpen\}/);
 assert.match(root,/PlayScreen g=/);
 assert.match(root,/RosterScreen g=/);
 assert.match(root,/SettingsScreen g=/);
 assert.match(root,/CasinoScreen g=/);
 assert.match(beta,/Connect with Discord/);
 assert.match(beta,/cloudRead\(/);
 assert.match(beta,/Modal visible=\{visible\}/);
 assert.match(hook,/if\(beta\)return; \/\/ Never persist a beta session as guest-only progression/);
 assert.match(hook,/projectKotlinCloudSave/);
 assert.doesNotMatch(root,/Cloud saved · revision|Cloud loaded · verify saving in Settings|syncStatus\.errorCode/);
 assert.match(root,/gameForDisplay/);
 assert.doesNotMatch(root,/Read-only original cloud progress · protected actions unavailable/);
 assert.match(root,/readOnly=\{betaMode&&!\(cloudState==='LIVE_SYNCED'/);
 assert.match(root,/disabled=\{readOnly\|\|!complete\}/);
 assert.match(hook,/ToastAndroid\.show\(/);
 assert.doesNotMatch(beta,/GUEST_SAVE_KEY|serializeGuestSave|mayEnterGameplay\(true\)/);
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

test('existing Play Pup Account can authorize signed beta companion without device transfer',()=>{
 const native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const beta=read('../src/production/BetaAccountEntry.jsx');
 const endpoint=read('../../supabase/functions/pup-beta-link/index.ts');
 const migration=read('../../supabase/migrations/20261025214600_pup_beta_companion_readonly.sql');
 for(const action of ['beta-link-discord','beta-account-status','beta-cloud-read'])
  assert.ok(native.includes(action)&&endpoint.includes(action),action);
 assert.match(native,/linkedFromPlay/);
 assert.match(native,/BETA_ACCOUNT_NOT_FOUND/);
 assert.match(endpoint,/discord\.com\/api\/v10\/users\/@me/);
 assert.match(endpoint,/authenticateSession\(req, admin, envelope\)/);
 assert.match(endpoint,/verifyOwner\(admin, account/);
 assert.match(endpoint,/assertGlobalEnforcementAllowed/);
 assert.match(endpoint,/account\.active_installation_uuid/);
 assert.match(endpoint,/MAX_BETA_GRANT_MS/);
 assert.doesNotMatch(endpoint,/cloud-save-write|revoke.*installation|delete.*pup_account_saves/);
 assert.match(migration,/enable row level security/);
 assert.match(migration,/revoke all on table public\.pup_beta_account_links from anon, authenticated/);
 assert.match(beta,/Signed progression, shop, rewards, and casino actions synchronize with Play/);
});

test('native Pup Account credentials are independent from Discord and never stored as guest progress',()=>{
 const bridge=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const screen=read('../src/production/BetaAccountEntry.jsx');
 const endpoint=read('../../supabase/functions/pup-credentials/index.ts');
 const account=read('../../supabase/functions/pup-account/index.ts');
 const migration=read('../../supabase/migrations/20261025221000_pup_native_credentials.sql');
 for(const required of ['createPupAccount','signInPupAccount','native-sign-up','native-sign-in','native-accept-legal'])
  assert.ok(bridge.includes(required)||endpoint.includes(required),required);
 assert.match(bridge,/post\("pup-credentials"/);
 assert.match(screen,/secureTextEntry/);
 assert.match(screen,/PUP USERNAME/);
 assert.match(screen,/CREATE|Create Pup Account/);
 assert.match(endpoint,/signInWithPassword/);
 assert.match(endpoint,/auth\.admin\.createUser/);
 assert.match(endpoint,/email_provider_disabled/);
 assert.match(endpoint,/PUP_PASSWORD_SETUP_UNAVAILABLE/);
 assert.match(endpoint,/terms_accepted_at/);
 assert.match(endpoint,/assertGlobalEnforcementAllowed/);
 assert.match(endpoint,/pup_credential_links/);
 assert.match(endpoint,/pup_beta_account_links/);
 assert.doesNotMatch(endpoint,/cloud-save-write|password_hash|bcrypt/);
 assert.match(account,/if \(!existing\.discord_user_id\) return existing/);
 assert.match(migration,/alter column discord_user_id drop not null/);
 assert.match(migration,/enable row level security/);
 assert.match(migration,/revoke all on public\.pup_credential_links from anon, authenticated/);
});

test('signed profile edits remain isolated from approved beta gameplay actions',()=>{
 const native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const beta=read('../../supabase/functions/pup-beta-link/index.ts');
 const account=read('../../supabase/functions/pup-account/index.ts');
 const hook=read('../src/state/useGame.js');
 const settings=read('../src/components/SettingsScreen.jsx');
 assert.match(native,/@ReactMethod fun updateDisplayName/);
 assert.match(native,/beta-account-update/);
 assert.match(beta,/BETA_ACCOUNT_DISPLAY_NAME_UPDATED/);
 assert.match(beta,/verifyOwner\(admin, account, envelope.action\)/);
 assert.match(account,/account-update/);
 assert.match(hook,/updateAccountName/);
 assert.match(settings,/onUpdateAccountName/);
 assert.match(beta,/beta-gameplay/);
 assert.match(native,/@ReactMethod fun gameplayAction/);
 assert.match(hook,/BETA_LIVE_ACTIONS/);
});

test('startup uses one signed bootstrap and an encrypted display-only cache',()=>{
 const native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const hook=read('../src/state/useGame.js');
 const sheet=read('../src/production/BetaAccountEntry.jsx');
 const endpoint=read('../../supabase/functions/pup-beta-link/index.ts');
 const own=read('../../supabase/functions/pup-account/index.ts');
 for(const phrase of ['@ReactMethod fun bootstrap','@ReactMethod fun cachedBootstrap','@ReactMethod fun clearCachedBootstrap','account_preview_v1.pup'])
  assert.ok(native.includes(phrase),phrase);
 assert.match(native,/write\(accountPreviewFile/);
 assert.match(hook,/native\.cachedBootstrap\(\)/);
 assert.match(hook,/native\.bootstrap\(PUBLISHABLE_KEY\)/);
 assert.doesNotMatch(hook,/native\.cloudRead\(PUBLISHABLE_KEY\)/);
 assert.match(sheet,/if\(visible\)void check\(\)/);
 assert.match(endpoint,/beta-bootstrap/);
 assert.match(own,/account-bootstrap/);
 assert.match(hook,/CACHED_OFFLINE/);
});

test('beta cloud save exposes signed commit receipt and safe non-economic verification',()=>{
 const root=read('../App.js'),hook=read('../src/state/useGame.js');
 const settings=read('../src/components/SettingsScreen.jsx'),native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const endpoint=read('../../supabase/functions/pup-beta-link/index.ts');
 const economy=read('../../supabase/functions/_shared/beta-economy.ts');
 assert.match(settings,/Check Cloud Backup/);
 assert.doesNotMatch(settings,/Last confirmed update|Save error|Revision /);
 assert.match(hook,/result\.saved!==true/);
 assert.match(hook,/setSyncStatus/);
 assert.match(hook,/syncProbe/);
 assert.match(native,/putBoolean\("saved"/);
 assert.match(native,/putString\("savedAt"/);
 assert.match(endpoint,/applyBetaSyncProbe/);
 assert.match(endpoint,/saved: true, revision/);
 assert.match(economy,/beta_last_sync_probe_at_v1/);
 assert.doesNotMatch(root,/Account synced · live gameplay/);
});
