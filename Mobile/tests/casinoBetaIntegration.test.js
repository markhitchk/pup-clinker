import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {projectKotlinCloudSave} from '../src/production/kotlinCloudPreview.js';
import {initialState,transition} from '../src/state/reducer.js';
import {newSlotsGuestRound} from '../src/engine/slotsV6.js';
import {newRouletteGuestRound} from '../src/engine/rouletteV6.js';
import {newPlinkoGuestRound} from '../src/engine/plinkoV6.js';
import {newPendingScratch} from '../src/engine/scratch.js';
import {newWheelGuestRound} from '../src/engine/wheelV6.js';
const ts=1793250000000;
function kotlinRound(round){
 return JSON.stringify({format:'puppy-clicker-cloud-save',version:1,
  stores:{puppy_clicker_save:{values:{
   puppy_style:'classic',unlocked_puppies:['classic','golden','poodle','spotty'],
   casino_chips_v7:15000,beta_casino_round_v1:JSON.stringify({...round,roundId:'39461f6c-bd08-4c4d-8e4f-53959753060a'})
  },types:{}}}});
}
test('all five server-backed spin/reveal rounds hydrate from the Kotlin cloud save',()=>{
 const rounds=[
  ['slots',newSlotsGuestRound(100,[15,35,68],ts),'pendingSlots'],
  ['roulette',newRouletteGuestRound(100,{type:'RED'},3,ts),'pendingRoulette'],
  ['plinko',newPlinkoGuestRound(100,[true,false,true,false,true,false,true,false],[0,0,0,0,0,0,0,0],ts),'pendingPlinko'],
  ['scratchers',newPendingScratch(100,12,ts),'pendingScratch'],
  ['wheel',newWheelGuestRound(100,12,ts),'pendingWheel']
 ];
 for(const [name,round,field] of rounds){
  assert.ok(round,name+' fixture');
  const projected=projectKotlinCloudSave(kotlinRound(round),'Pup Account');
  const state=transition(initialState(),{type:'hydrate',game:projected,readOnly:true,now:ts+1000});
  assert.ok(state.game[field],name+' must remain resumable from signed cloud state');
  assert.equal(state.game.casinoChips,15000);
 }
});
test('blackjack server hand resumes with a presentation-only unknown hole card',()=>{
 const round={type:'BLACKJACK',version:1,wager:100,createdAt:ts,drawIndex:4,
  dealerCards:[7],hands:[{cards:[0,25],wager:100,stood:false,doubled:false,fromSplit:false,splitAces:false}],
  activeHandIndex:0,complete:false};
 const game=projectKotlinCloudSave(kotlinRound(round));
 const state=transition(initialState(),{type:'hydrate',game,readOnly:true,now:ts+1000});
 assert.ok(state.blackjack,'Server Blackjack must not disappear when the hole card is hidden');
 assert.equal(state.blackjack.dealerCards.length,2);
 assert.equal(state.game.casinoChips,15000);
});
test('original Kotlin active casino wager blocks beta round starts without losing cloud progress',()=>{
 const json=JSON.parse(kotlinRound(newSlotsGuestRound(100,[15,35,68],ts)));
 json.stores.puppy_clicker_save.values.casino_active_round_v1='{"type":"BLACKJACK","status":"ACTIVE"}';
 const game=projectKotlinCloudSave(JSON.stringify(json));
 assert.equal(game.legacyCasinoRoundActive,true);
 const state=transition(initialState(),{type:'hydrate',game,readOnly:true,now:ts+1000});
 assert.equal(state.game.legacyCasinoRoundActive,true);
});
test('casino button guards distinguish authorized beta, saving and companion sessions',()=>{
 const root=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 assert.match(root,/casinoReadOnly:betaMode&&account\?\.gameplayWritable!==true/);
 assert.match(root,/gameplayBusy:betaMode&&\['SAVING','VERIFYING_SAVE'\]/);
 assert.match(root,/onPress:\(\)=>backRef\.current\(\)/);
 assert.doesNotMatch(root,/\[accepted,onBack\]/);
 for(const name of ['SlotsV6','RouletteV6','PlinkoV6','ScratchersV6','LuckyWheelV6']){
  const source=readFileSync(new URL('../src/components/'+name+'.jsx',import.meta.url),'utf8');
  assert.match(source,/!game\.casinoReadOnly&&!game\.casinoLegacyRoundActive&&!game\.gameplayBusy/,name+' must not submit unavailable wagers');
 }
 assert.match(root,/casinoChips,wager,active/);
});
