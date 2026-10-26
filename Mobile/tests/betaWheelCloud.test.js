import test from 'node:test';
import assert from 'node:assert/strict';
import {projectKotlinCloudSave} from '../src/production/kotlinCloudPreview.js';
import {validPendingWheel,validAuthoritativeWheel,wheelResult} from '../src/engine/wheelV6.js';
import {initialState,transition} from '../src/state/reducer.js';

const when=1793055000000;
const outcome={
 type:'LUCKY_WHEEL',version:1,roundId:'e61db2a7-2b44-41a3-8d47-30366a2f9014',
 wager:100,createdAt:when,roll:99,prize:'PUPPY_UNLOCK',segmentIndex:6,
 puppyStyleId:'midnight',payout:0
};
function kotlin(round){
 return JSON.stringify({format:'puppy-clicker-cloud-save',version:1,
  stores:{puppy_clicker_save:{values:{
   puppy_style:'classic',unlocked_puppies:['classic','golden','poodle','spotty'],
   casino_chips_v7:120,beta_casino_round_v1:round?JSON.stringify(round):'',
   beta_last_casino_result_v1:round?'':JSON.stringify({...outcome,bonus:{awardedPuppy:'midnight'}})
  },types:{}},puppy_seasonal_v1:{values:{},types:{}}}
 });
}
test('signed PupEye rare puppy wheel round survives Kotlin projection and resumes without guest authority',()=>{
 const game=projectKotlinCloudSave(kotlin(outcome));
 assert.equal(validPendingWheel(game.pendingWheel),false,'never permit guest-only wheel mint');
 assert.equal(validAuthoritativeWheel(game.pendingWheel),true);
 assert.equal(wheelResult(game.pendingWheel).puppyStyleId,'midnight');
 const hydrated=transition(initialState(),{type:'hydrate',game,now:when+1000,readOnly:true});
 assert.equal(hydrated.game.pendingWheel.puppyStyleId,'midnight');
 assert.equal(hydrated.game.casinoChips,120);
 const forbidden=transition(hydrated,{type:'wheelReveal',now:when+2650});
 assert.equal(forbidden.game.casinoChips,120);
 assert.ok(forbidden.game.pendingWheel);
});
test('signed rare wheel result is rendered while forged puppy types are refused',()=>{
 const game=projectKotlinCloudSave(kotlin(null));
 const hydrated=transition(initialState(),{type:'hydrate',game,now:when+1000,readOnly:true});
 assert.equal(hydrated.wheelDisplay.prize,'PUPPY_UNLOCK');
 const forged={...outcome,authority:'PUPEYE',puppyStyleId:'dev_pup'};
 assert.equal(validAuthoritativeWheel(forged),false);
 assert.equal(validAuthoritativeWheel({...outcome,authority:'PUPEYE',payout:2000}),false);
 assert.equal(validAuthoritativeWheel({...outcome,authority:'PUPEYE',segmentIndex:1}),false);
});
