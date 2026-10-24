import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {SCRATCHERS,scratcherOutcome} from '../src/engine/casinoV7.js';
import {
 SCRATCH_COLUMNS,SCRATCH_ROWS,SCRATCH_REVEAL_THRESHOLD,SCRATCH_FRAME,
 SCRATCH_COIN_DIAMETER,SCRATCH_COIN_ASPECT,SCRATCH_PUBLISHED_RTP,
 SCRATCH_PRIZES,SCRATCH_CARD_META,markScratchTrail,scratchCoverage,
 scratchCoinAt,scratchRadius,addAccessibleScratchProgress,newPendingScratch,validPendingScratch
} from '../src/engine/scratch.js';
import {initialState,transition} from '../src/state/reducer.js';
import {serializeGuestSave,parseGuestSave} from '../src/state/guestSave.js';

const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyScratchersUi.kt',import.meta.url),'utf8');
const engine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyScratchersEngine.kt',import.meta.url),'utf8');
const stage=readFileSync(new URL('../src/components/ScratchStage.jsx',import.meta.url),'utf8');
const ui=readFileSync(new URL('../src/components/ScratchersV6.jsx',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');

test('Kotlin canvas grid and original panel/coin source geometry match Part 5E',()=>{
 assert.ok(kotlin.includes('SCRATCH_COLUMNS = 48'));
 assert.ok(kotlin.includes('SCRATCH_ROWS = 20'));
 assert.ok(kotlin.includes('SCRATCH_BORDER_WIDTH = 1426f'));
 assert.ok(kotlin.includes('SCRATCH_BORDER_HEIGHT = 1077f'));
 assert.equal(SCRATCH_COLUMNS,48);
 assert.equal(SCRATCH_ROWS,20);
 assert.equal(SCRATCH_COLUMNS*SCRATCH_ROWS,960);
 assert.equal(SCRATCH_REVEAL_THRESHOLD,.68);
 assert.equal(SCRATCH_COIN_DIAMETER,56);
 assert.equal(SCRATCH_COIN_ASPECT,960/973);
 assert.deepEqual(SCRATCH_FRAME,{width:1426,height:1077,cardX:200,cardY:482,cardWidth:1013,cardHeight:443});
 for(const value of ['SCRATCH_FRAME.cardX','SCRATCH_FRAME.cardY','SCRATCH_FRAME.cardWidth','SCRATCH_FRAME.cardHeight']){
  assert.ok(stage.includes(value),value);
 }
 const kotlinCoin=readFileSync(new URL('../../App/app/src/main/res/drawable/pup_coin.png',import.meta.url));
 const port=readFileSync(new URL('../assets/casino/pup_coin.png',import.meta.url));
 assert.ok(kotlinCoin.equals(port),'Pup Coin PNG must be byte identical to original Kotlin drawable');
 assert.ok(stage.includes('pup_coin.png'));
 assert.ok(stage.includes('SCRATCH_COLUMNS'));
 assert.ok(stage.includes('SCRATCH_ROWS'));
 assert.ok(kotlin.includes('CompositingStrategy.Offscreen'));
 assert.ok(kotlin.includes('BlendMode.Clear'));
});

test('Kotlin six cards, short names, odds, original mascot colors and 91% RTP match',()=>{
 assert.equal(SCRATCH_PUBLISHED_RTP,'91%');
 assert.ok(engine.includes('PUBLISHED_RTP_PERCENT = "91%"'));
 assert.equal(SCRATCHERS.length,6);
 assert.deepEqual(SCRATCHERS.map(c=>c.wager),[20,100,500,2500,10000,25000]);
 assert.deepEqual(SCRATCHERS.map(c=>SCRATCH_CARD_META[c.id].shortName),['Pup','Bone','Fetch','Gold','Casino','Ultra']);
 assert.deepEqual(SCRATCH_PRIZES.map(p=>p.multiplier),[0,100,200,500,2000]);
 for(const card of SCRATCHERS){
  assert.equal(card.odds.reduce((x,y)=>x+y,0),100);
  assert.ok(SCRATCH_CARD_META[card.id].tagline.length>10);
 }
 assert.ok(ui.includes('SCRATCH_PUBLISHED_RTP'));
 assert.ok(ui.includes('SCRATCH_CARD_META'));
 assert.ok(ui.includes('How Pup Plinko')===false);
 assert.ok(ui.includes('Every card keeps the published 91%'));
 assert.ok(ui.includes('Scratch cards'));
 assert.ok(ui.includes('Buy '));
});

test('native weighted token follow, bounded physical rotation and speed-dependent brush radius',()=>{
 const bounds={width:360,height:150};
 const slow=scratchRadius(.05,bounds,28);
 const fast=scratchRadius(3,bounds,28);
 assert.ok(fast>slow);
 assert.ok(slow>0);
 let coin=scratchCoinAt(null,{x:0,y:0},{x:0,y:0},16,bounds);
 assert.ok(coin.x>=28*SCRATCH_COIN_ASPECT);
 assert.ok(coin.y>=28);
 const next=scratchCoinAt(coin,{x:360,y:150},{x:320,y:150},16,bounds);
 assert.ok(next.x>coin.x&&next.x<=360-28*SCRATCH_COIN_ASPECT);
 assert.ok(next.y>coin.y&&next.y<=150-28);
 assert.ok(next.rotation<=28&&next.rotation>=-28);
 const prev=next;
 coin=scratchCoinAt(prev,{x:360,y:150},{x:-10000,y:-10000},1,bounds);
 assert.ok(coin.rotation<=28&&coin.rotation>=-28);
 assert.ok(kotlin.includes('(coinRotation * 0.82f)'));
 assert.ok(kotlin.includes('speedPxPerMs / 12f'));
 assert.ok(kotlin.includes('speedPxPerMs / 2.4f'));
 assert.ok(stage.includes('scratchCoinAt('));
 assert.ok(stage.includes('coinRef.current'));
});

test('48x20 mask clears interpolated trails and reveals at the original 68% threshold',()=>{
 const bounds={width:300,height:125};
 let cells=new Set();
 const original=markScratchTrail(cells,{x:10,y:10},{x:290,y:10},bounds,16);
 assert.ok(original.size>25);
 assert.equal(cells.size,0);
 const slow=markScratchTrail(new Set(),{x:50,y:60},{x:60,y:60},bounds,100);
 const fast=markScratchTrail(new Set(),{x:50,y:60},{x:60,y:60},bounds,1);
 assert.ok(fast.size>=slow.size);
 for(let row=0;row<SCRATCH_ROWS;row++){
  const y=(row+.5)*bounds.height/SCRATCH_ROWS;
  cells=markScratchTrail(cells,{x:0,y},{x:bounds.width,y},bounds,20);
 }
 assert.ok(scratchCoverage(cells)>=SCRATCH_REVEAL_THRESHOLD);
 assert.equal(scratchCoverage(new Set()),0);
 assert.equal(scratchCoverage(new Set(Array.from({length:960},(_,i)=>i))),1);
 assert.ok(stage.includes("testID=\"puppy-scratch-coating\""));
 assert.ok(stage.includes('SCRATCH_REVEAL_THRESHOLD'));
});

test('TalkBack clears measurable coating increments, not instant hidden-payout button',()=>{
 let cells=new Set();
 for(let i=0;i<6;i++)cells=addAccessibleScratchProgress(cells,scratchCoverage(cells)+.1);
 assert.ok(scratchCoverage(cells)<.68);
 cells=addAccessibleScratchProgress(cells,scratchCoverage(cells)+.1);
 assert.ok(scratchCoverage(cells)>=.68);
 assert.ok(stage.includes("name:'increment'"));
 assert.ok(!stage.includes("name:'activate'"));
 assert.ok(stage.includes('accessibilityStep'));
});

test('scratch purchases are persisted; reveal credits once and retains read-only display',()=>{
 const s=initialState();s.ready=true;s.game.casinoChips=100;
 let accepted=transition(s,{type:'scratchStart',wager:20,roll:61,now:1730000000000});
 assert.equal(accepted.game.casinoChips,80);
 assert.equal(accepted.game.pendingScratch.prize,'REFUND');
 assert.equal(accepted.scratchDisplay,null);
 const saved=parseGuestSave(serializeGuestSave(accepted.game));
 assert.ok(validPendingScratch(saved.pendingScratch));
 const hydrated=transition(initialState(),{type:'hydrate',game:saved,now:1730000000010});
 assert.equal(hydrated.game.casinoChips,80);
 const done=transition(hydrated,{type:'scratchReveal',now:1730000002000});
 assert.equal(done.game.casinoChips,100);
 assert.equal(done.game.pendingScratch,null);
 assert.equal(done.scratchDisplay.prize,'REFUND');
 assert.equal(done.scratchDisplay.payout,20);
 assert.equal(transition(done,{type:'scratchReveal',now:1730000003000}).game.casinoChips,100);
 assert.equal(parseGuestSave(serializeGuestSave(done.game)).casinoChips,100);
 let next=transition(done,{type:'scratchStart',wager:20,roll:0,now:1730000004000});
 assert.equal(next.scratchDisplay,null);
 assert.equal(next.game.casinoChips,80);
});

test('fraud-like incorrect symbols or forged payout cannot be settled from persisted guest round',()=>{
 const g=freshGame();
 let valid=newPendingScratch(100,99,1730000000000);
 assert.ok(validPendingScratch(valid));
 valid.payout=100000000;
 assert.equal(validPendingScratch(valid),false);
 assert.equal(parseGuestSave(serializeGuestSave({...g,pendingScratch:valid})).pendingScratch,null);
 let s=initialState();s.ready=true;s.game.casinoChips=100;
 s=transition(s,{type:'scratchStart',wager:20,roll:0,now:1730000000000});
 const bad={...s.game.pendingScratch,symbols:['💎','💎','💎']};
 assert.equal(validPendingScratch(bad),false);
 const denied=transition({...s,game:{...s.game,pendingScratch:bad}},{type:'scratchReveal'});
 assert.equal(denied.game.casinoChips,80);
 assert.equal(newPendingScratch(25,1,1730000000000),null);
});

test('Scratchers replaces legacy Casino generic controls and preserves native page hierarchy',()=>{
 assert.ok(app.includes("import ScratchersV6 from './src/components/ScratchersV6.jsx'"));
 assert.ok(app.includes("page==='scratchers'?<ScratchersV6"));
 assert.ok(ui.includes('ScratchStage'));
 assert.ok(ui.includes('Scratch Token'));
 assert.ok(ui.includes('Card Odds & Rules'));
 assert.ok(ui.includes('Casino Chip Wallet'));
 assert.ok(ui.includes('Pup Scratchers'));
 assert.ok(ui.includes("type:'scratchStart'"));
 assert.ok(ui.includes("type:'scratchReveal'"));
 assert.ok(!ui.includes("gameType:'scratch'"));
 assert.ok(!ui.includes('<WebView'));
});
