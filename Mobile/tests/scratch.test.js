import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {initialState,transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
import {SCRATCH_COLUMNS,SCRATCH_ROWS,SCRATCH_REVEAL_THRESHOLD,
 newPendingScratch,validPendingScratch,markScratchTrail,scratchCoverage} from '../src/engine/scratch.js';

function readyWithChips(chips=1000){
 const state=initialState();
 state.ready=true;
 state.game.casinoChips=chips;
 return state;
}

test('Kotlin scratch wager is held until the player reveals the 68% coating',()=>{
 let s=readyWithChips(100);
 s=transition(s,{type:'casino',gameType:'scratch',wager:20,roll:61,now:1000});
 assert.equal(s.game.casinoChips,80);
 assert.equal(s.game.pendingScratch.prize,'REFUND');
 assert.equal(s.game.pendingScratch.payout,20);
 const paused=transition(s,{type:'resume',now:1500});
 assert.equal(paused.game.casinoChips,80,'resuming must not settle an unfinished card');
 const denied=transition(paused,{type:'casino',gameType:'slots',wager:20,rolls:[0,0,0],now:1600});
 assert.equal(denied.game.casinoChips,80,'a second game cannot charge or pay while scratching');
 assert.deepEqual(denied.game.pendingScratch,paused.game.pendingScratch);
 const done=transition(denied,{type:'scratchReveal',now:1700});
 assert.equal(done.game.casinoChips,100);
 assert.equal(done.game.pendingScratch,null);
 const repeated=transition(done,{type:'scratchReveal',now:1800});
 assert.equal(repeated.game.casinoChips,100,'duplicate reveals cannot pay twice');
});

test('unfinished scratch card round trips through guest save and prevents Blackjack',()=>{
 let s=readyWithChips(50);
 s=transition(s,{type:'casino',gameType:'scratch',wager:20,roll:0,now:2000});
 const restored=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(validPendingScratch(restored.pendingScratch),true);
 const resumed=transition(readyWithChips(),{type:'hydrate',game:restored,now:2100});
 assert.equal(resumed.game.casinoChips,30);
 const blocked=transition(resumed,{type:'blackjackStart',wager:20,deck:[],now:2200});
 assert.equal(blocked.game.casinoChips,30);
 assert.equal(blocked.blackjack,null);
 const settled=transition(blocked,{type:'scratchReveal',now:2300});
 assert.equal(settled.game.casinoChips,30,'a losing card must return no Chips');
});

test('malformed or tampered card is rejected before saved guest load or credit',()=>{
 const g=freshGame();
 const valid=newPendingScratch(20,98,1234);
 assert.equal(validPendingScratch(valid),true);
 valid.payout=9999999;
 assert.equal(validPendingScratch(valid),false);
 g.pendingScratch=valid;
 assert.equal(parseGuestSave(serializeGuestSave(g)).pendingScratch,null);
 assert.equal(newPendingScratch(25,20,1234),null);
 assert.equal(newPendingScratch(20,-1,1234),null);
 assert.equal(newPendingScratch(20,20,0),null);
 let s=readyWithChips(20);
 const invalid=transition(s,{type:'casino',gameType:'scratch',wager:25,roll:40,now:1000});
 assert.equal(invalid.game.casinoChips,20,'invalid scratch wagers must never debit');
});

test('drag sampling clears continuous tracks and passes Kotlin reveal threshold',()=>{
 const bounds={width:240,height:100};
 let cells=new Set();
 cells=markScratchTrail(cells,{x:2,y:5},{x:238,y:5},bounds,100);
 assert.ok(cells.size>=15);
 for(let row=1;row<10;row++)
  cells=markScratchTrail(cells,{x:2,y:row*10+5},{x:238,y:row*10+5},bounds,100);
 assert.equal(SCRATCH_COLUMNS*SCRATCH_ROWS,240);
 assert.ok(scratchCoverage(cells)>=SCRATCH_REVEAL_THRESHOLD);
 assert.ok(scratchCoverage(cells)<=1);
 assert.equal(scratchCoverage(new Set()),0);
});

test('guest preview visibly uses the staged original-art Scratchers screen',async()=>{
 const {readFileSync}=await import('node:fs');
 const {fileURLToPath}=await import('node:url');
 const app=readFileSync(fileURLToPath(new URL('../App.js',import.meta.url)),'utf8');
 assert.ok(app.includes('ScratchStage'));
 assert.ok(app.includes("type:'scratchReveal'"));
 assert.ok(app.includes('CASINO_ART.scratchers'));
});
