import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {projectKotlinCloudSave} from '../src/production/kotlinCloudPreview.js';
import {sanitizeInbox} from '../src/engine/inbox.js';

function cloud(values){
 return JSON.stringify({format:'puppy-clicker-cloud-save',version:1,stores:{
  puppy_clicker_save:{values:{puppy_style:'golden',puppy_name:'Sunny',
   unlocked_puppies:['classic','golden','poodle','spotty'],...values},types:{}}
 }});
}
test('Kotlin cloud projection displays signed offline treats and preserves favorites',()=>{
 const now=1793140000000,id='afk:1793139500000:1793140000000';
 const g=projectKotlinCloudSave(cloud({favorite_puppies:['golden'],
  beta_afk_notifications_v1:JSON.stringify([{id,amount:5,createdAt:now,claimed:false}])}),'tester');
 assert.deepEqual(g.favoritePuppies,['golden']);
 assert.equal(g.notificationInbox.length,1);
 assert.equal(g.notificationInbox[0].action,'afkClaim');
 assert.match(g.notificationInbox[0].body,/5 Treats/);
 assert.equal(g.treats,0,'server must settle the award after the user claims');
});
test('claimed offline rewards show in history without a second Claim action',()=>{
 const id='afk:1793139500000:1793140000000';
 const g=projectKotlinCloudSave(cloud({
  beta_afk_notifications_v1:JSON.stringify([{id,amount:1,createdAt:1793140000000,claimed:true}])
 }));
 assert.equal(g.notificationInbox.length,1);
 assert.equal(g.notificationInbox[0].action,null);
 assert.equal(g.notificationInbox[0].read,true);
});
test('malformed local notification rewards cannot escalate into AFK currency claims',()=>{
 assert.equal(sanitizeInbox([{id:'fake',category:'pup',title:'Treats',body:'Free treats',
  createdAt:-5,action:'afkClaim'}]).length,0);
 assert.equal(sanitizeInbox([{id:'test',category:'pup',title:'Treats',body:'Free treats',
  createdAt:1,action:'mintTreats'}])[0].action,null);
});
test('roster uses a preview modal with original name, ownership and confirmation controls',()=>{
 const source=readFileSync(new URL('../src/components/PuppyDetailsDialog.jsx',import.meta.url),'utf8');
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 for(const label of ['Puppy Details','How to Unlock','Retry Art','Use Puppy','Rename Puppy','Original roster name','Cancel','Done'])
  assert.ok(source.includes(label),label);
 assert.match(app,/setDetailsId\(p.id\)/);
 assert.match(app,/<PuppyDetailsDialog/);
 assert.match(app,/onToggleFavorite/);
});
test('AFK server uses signed server time, caps rewards, and marks claims exactly once',()=>{
 const econ=readFileSync(new URL('../../supabase/functions/_shared/beta-economy.ts',import.meta.url),'utf8');
 const bridge=readFileSync(new URL('../plugins/puppy-production/PuppyBetaAccountPackage.kt',import.meta.url),'utf8');
 const server=readFileSync(new URL('../../supabase/functions/pup-beta-link/index.ts',import.meta.url),'utf8');
 for(const intent of ['afkStart','afkSettle','afkClaim']) {
  assert.ok(econ.includes('case "'+intent+'"'));
  assert.ok(server.includes('"'+intent+'"'));
  assert.ok(bridge.includes('"'+intent+'"'));
 }
 assert.match(econ,/Math\.min\(7000/);
 assert.match(econ,/AFK_ALREADY_CLAIMED/);
 assert.doesNotMatch(econ,/intent\.amount\b[^\n]*earned/);
});
