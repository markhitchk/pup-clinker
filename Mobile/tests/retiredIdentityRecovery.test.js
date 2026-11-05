import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const native=readFileSync(new URL('../plugins/puppy-production/PuppyBetaAccountPackage.kt',import.meta.url),'utf8');
const startup=readFileSync(new URL('../src/production/PuppyStartupScreen.jsx',import.meta.url),'utf8');
const state=readFileSync(new URL('../src/state/useGame.js',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');

test('retired identity is only reissued after server confirmation and player approval',()=>{
 const start=native.indexOf('@ReactMethod fun replaceRetiredLocalIdentity(');
 const end=native.indexOf('private fun session(key:String)',start);
 assert.ok(start>=0&&end>start);
 const operation=native.slice(start,end);
 assert.match(operation,/register\(key\)/);
 assert.match(operation,/e\.code !in setOf\("PLAYER_ID_RETIRED","FRIEND_CODE_RETIRED"\)/);
 assert.match(operation,/if\(!retired\)throw ApiError\("IDENTITY_RECOVERY_NOT_NEEDED"\)/);
 assert.match(operation,/newLocalPlayerIdentity\(\)/);
 assert.match(operation,/sessionFile\.delete\(\)/);
 assert.match(operation,/accountPreviewFile\.delete\(\)/);
 assert.doesNotMatch(operation,/stateFile\.delete|deleteEntry|saveData|service_role|cloudSave/);
 assert.match(startup,/Create New Beta Identity/);
 assert.match(startup,/Alert\.alert/);
 assert.match(startup,/onRecoverIdentity/);
 assert.match(app,/recoverRetiredIdentityAtStartup/);
});
test('a retired beta session does not enable gameplay or use cached signed preview as authority',()=>{
 const position=state.indexOf('if(/PLAYER_ID_RETIRED|FRIEND_CODE_RETIRED/.test(code))');
 const offline=state.indexOf('if(cachedPreview.current&&!revoked)');
 assert.ok(position>=0&&offline>position);
 const section=state.slice(position,offline);
 assert.match(section,/liveVerified\.current=false/);
 assert.match(section,/cachedPreview\.current=false/);
 assert.match(section,/IDENTITY_RECOVERY_REQUIRED/);
 assert.match(state,/if\(!beta\|\|account\?\.status!=='IDENTITY_RECOVERY_REQUIRED'\)/);
 assert.match(state,/replaceRetiredLocalIdentity\(PUBLISHABLE_KEY\)/);
 assert.match(state,/setAccount\(\{status:'NEEDS_ACCOUNT'\}\)/);
 assert.match(state,/setCloudState\('SIGN_IN_REQUIRED'\)/);
});
