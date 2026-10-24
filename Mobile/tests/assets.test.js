import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {ROSTER} from '../src/engine/game.js';
const root=join(dirname(fileURLToPath(import.meta.url)),'..');
test('all 30 V1/V2 and Discord puppy portraits are bundled for offline React Native display',()=>{
 const map=readFileSync(join(root,'src/assets/puppies.js'),'utf8');
 assert.equal(ROSTER.length,30);
 for(const p of ROSTER){
  const file=p.id.startsWith('v2_')?p.id==='v2_discord_pup'?'discord_rewards/'+p.id+'.png':'v2/'+p.id+'.png':'v1/v1_'+p.id+'.png';
  const path=join(root,'assets',file);
  assert.equal(existsSync(path),true,'Missing '+p.id);
  const png=readFileSync(path);
  assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a',p.id+' must be valid PNG');
  assert.ok(map.includes(p.id+":require('../../assets/"+file+"')"),'No static Metro require for '+p.id);
 }
});
test('React Native portraits reference bundled map, not fetch each remote image',()=>{
 const app=readFileSync(join(root,'App.js'),'utf8');
 assert.match(app,/PUPPY_IMAGES\[puppy\.id\]/);
});
