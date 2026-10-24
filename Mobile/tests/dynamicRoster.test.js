import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {
 parseRosterManifest,parseRosterIndex,validateRemoteRoster,parseCachedRoster,packRosterCache,
 mergeRoster,categoriesForRoster,ROSTER_ASSET_URL,utf8WithinLimit
} from '../src/engine/dynamicRoster.js';
const read=path=>readFileSync(new URL('../../assets/'+path,import.meta.url),'utf8');

test('Kotlin manifests are accepted as read-only metadata, not ownership grants',()=>{
 const v2=parseRosterManifest('v2',read('v2/manifest.json'));
 const testPups=parseRosterManifest('test',read('test/manifest.json'));
 const discord=parseRosterManifest('discord_rewards',read('discord_rewards/manifest.json'));
 assert.ok(v2.length>=11);
 assert.equal(testPups.length,2);
 assert.equal(testPups[0].free,true);
 assert.equal(discord[0].redeemOnly,true);
 assert.equal(discord[0].free,false);
 const combined=[...v2,...testPups,...discord];
 assert.equal(parseCachedRoster(packRosterCache(combined)).length,combined.length);
 const visible=mergeRoster(combined),groups=categoriesForRoster(visible);
 assert.ok(groups.find(x=>x.id==='discord_rewards'));
 assert.ok(groups.find(x=>x.id==='test'));
 assert.ok(visible.find(x=>x.id==='test_v1_crash_dummy'));
 assert.equal(visible.find(x=>x.id==='v2_discord_pup').free,false);
});
test('dynamic manifest folder, path, ID, count and ownership constraints match Kotlin',()=>{
 const minimal={puppies:[{asset_id:'v3_new_puppy',name:'New Puppy',file:'v3_new_puppy.png'}]};
 assert.equal(parseRosterManifest('v3',JSON.stringify(minimal))[0].redeemOnly,true);
 assert.throws(()=>parseRosterManifest('v1',JSON.stringify(minimal)));
 assert.throws(()=>parseRosterManifest('v3',JSON.stringify({puppies:[{...minimal.puppies[0],file:'../secrets.png'}]})));
 assert.throws(()=>parseRosterManifest('v3',JSON.stringify({puppies:[...minimal.puppies,...minimal.puppies]})));
 assert.throws(()=>parseRosterManifest('v3',JSON.stringify({puppies:[{...minimal.puppies[0],asset_id:'v2_wrong'}]})));
 assert.throws(()=>parseRosterManifest('v3',JSON.stringify({puppies:new Array(501).fill(minimal.puppies[0])})));
 const good=parseRosterManifest('v3',JSON.stringify(minimal));
 assert.equal(good[0].image,ROSTER_ASSET_URL+'/v3/v3_new_puppy.png');
 assert.throws(()=>validateRemoteRoster([{...good[0],image:'https://evil.invalid/pup.png'}]));
});
test('remote assets never change base game engine or local account unlock rules',()=>{
 const remote=parseRosterManifest('v3',JSON.stringify({ownership:'free',puppies:[{asset_id:'v3_surprise',file:'v3_surprise.png'}]}));
 assert.equal(remote[0].free,true);
 const shown=mergeRoster(remote);
 assert.ok(shown.some(p=>p.id==='v3_surprise'));
 assert.ok(!freshGame().unlockedPuppies.includes('v3_surprise'));
});
test('only permitted assets directories can be fetched',()=>{
 const folders=parseRosterIndex(JSON.stringify([
  {type:'dir',name:'v3'},{type:'dir',name:'discord_rewards'},{type:'dir',name:'private'},
  {type:'file',name:'v4'},{type:'dir',name:'test'},{type:'dir',name:'v2'}
 ]));
 assert.deepEqual(folders,['v2','v3','discord_rewards','test']);
 assert.throws(()=>parseRosterIndex('{}'));
 assert.equal(utf8WithinLimit('🐾',4),true);
 assert.equal(utf8WithinLimit('🐾',3),false);
});
