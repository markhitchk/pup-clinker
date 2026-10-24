import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

test('batched tap actions apply sequentially without stale closures',()=>{
 let state={game:freshGame(),notice:'',ready:true};
 for(let n=0;n<25;n++)state=transition(state,{type:'tap',now:1000+n*1100,randoms:[0.99,0.99]});
 assert.equal(state.game.totalTaps,25);assert.equal(state.game.treats,50); // healthy-care bonus remains active
 assert.equal(state.game.bones,1);
});
test('insufficient casino chips never debit balance or pay prize',()=>{
 const game=freshGame();
 const next=transition({game,ready:true,notice:''},{type:'casino',wager:20,payout:100});
 assert.equal(next.game.casinoChips,0);
 assert.equal(next.game,game);
});
test('malformed save is harmless and never accepted as a trusted account',()=>{
 const restored=parseGuestSave('{broken');
 assert.equal(restored.puppyStyle,'classic');
 const valid=parseGuestSave(serializeGuestSave(freshGame()));
 assert.equal(valid.puppyStyle,'classic');
 assert.equal(JSON.parse(serializeGuestSave(valid)).kind,'guest-only');
});
test('unauthenticated save cannot claim admin/server provenance',()=>{
 const g=freshGame();
 const saved=JSON.parse(serializeGuestSave(g));
 assert.equal(saved.kind,'guest-only');
 assert.equal(saved.serverVerified,undefined);
});
