import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {plinkoOutcome} from '../src/engine/casinoV7.js';
import {PLINKO_V6,PLINKO_BINS,validPlinkoWager,plinkoLabel,newPlinkoGuestRound,
 validPendingPlinko,plinkoResult,plinkoFrame,plinkoBallAt,plinkoAnimationSamples} from '../src/engine/plinkoV6.js';
import {initialState,transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

const kotlinEngine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyPlinkoEngine.kt',import.meta.url),'utf8');
const kotlinUI=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyPlinkoUi.kt',import.meta.url),'utf8');
const component=readFileSync(new URL('../src/components/PlinkoV6.jsx',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const ready=(chips=200)=>{const s=initialState();s.game.casinoChips=chips;s.ready=true;return s;};
const start=(s,path=Array(8).fill(false),jitter=Array(8).fill(0))=>transition(s,{
 type:'plinkoStart',wager:100,pathRight:path,bounceJitterPermille:jitter,now:1730000000000
});

test('Part 5D source: eight 50/50 bounces, nine bins, original wagers and published RTP',()=>{
 assert.ok(kotlinEngine.includes('const val ROWS = 8'));
 assert.ok(kotlinEngine.includes('const val BIN_COUNT = ROWS + 1'));
 assert.ok(kotlinEngine.includes('PUBLISHED_RTP_PERCENT = "97.3%"'));
 assert.equal(PLINKO_V6.rows,8);assert.equal(PLINKO_V6.binCount,9);
 assert.equal(PLINKO_V6.rtp,'97.3%');
 assert.deepEqual(PLINKO_V6.betPresets,[20,100,500,2500]);
 assert.deepEqual(PLINKO_BINS,[5,25,50,100,150,100,50,25,5]);
 assert.equal(PLINKO_V6.maxJitter,220);
 for(const wager of [20,100,500,2500,100000])assert.equal(validPlinkoWager(wager),true);
 for(const wager of [0,19,30,100001,-20,25])assert.equal(validPlinkoWager(wager),false);
 assert.equal(plinkoLabel(5),'0.05×');
 assert.equal(plinkoLabel(25),'0.25×');
 assert.equal(plinkoLabel(100),'1×');
 assert.equal(plinkoLabel(150),'1.5×');
 assert.ok(kotlinUI.includes('delay(if (state.animationsEnabled) 2_200 else 250)'));
 assert.ok(kotlinUI.includes('progress.animateTo(1f, tween(2_050'));
 assert.equal(PLINKO_V6.dropMs,2200);
 assert.equal(PLINKO_V6.ballMs,2050);
 assert.equal(PLINKO_V6.reducedMotionMs,250);
});

test('exact Kotlin payout follows physical right-bounce count, not an independent winning-bin roll',()=>{
 const left=Array(8).fill(false),right=Array(8).fill(true);
 assert.equal(plinkoOutcome(100,left).binIndex,0);
 assert.equal(plinkoOutcome(100,left).payout,5);
 assert.equal(plinkoOutcome(100,right).binIndex,8);
 assert.equal(plinkoOutcome(100,right).payout,5);
 const center=[true,false,true,false,true,false,true,false];
 assert.equal(plinkoOutcome(100,center).binIndex,4);
 assert.equal(plinkoOutcome(100,center).payout,150);
 for(let n=0;n<=8;n++){
  const path=Array.from({length:8},(_,i)=>i<n);
  const r=newPlinkoGuestRound(100,path,Array(8).fill(0),1730000000000);
  assert.equal(r.binIndex,n);
  assert.equal(r.multiplierHundredths,PLINKO_BINS[n]);
  assert.equal(r.payout,Math.floor(PLINKO_BINS[n]));
  assert.equal(validPendingPlinko(r),true);
 }
});

test('Plinko ball stays inside source art and follows eight saved bounces with saved visual jitter',()=>{
 const frame=plinkoFrame(360);
 assert.equal(frame.boardWidth,360);
 assert.equal(frame.boardHeight,450);
 assert.equal(frame.stageHeight,546);
 assert.equal(frame.boardTop,58);
 assert.equal(frame.headerWidth,331.2);
 assert.equal(frame.bin.width,360*.88);
 assert.equal(plinkoFrame(600).boardWidth,460);
 const path=[true,true,false,true,false,false,true,false];
 const sample=newPlinkoGuestRound(100,path,[120,-120,0,60,0,0,0,0],1730000000000);
 assert.deepEqual(plinkoBallAt(sample,0),{x:.5,y:.205});
 const mid=plinkoBallAt(sample,0.5/9);
 assert.ok(mid.x>.5);
 assert.ok(mid.y>.205);
 assert.ok(plinkoBallAt(sample,1).x>.45&&plinkoBallAt(sample,1).x<.55);
 assert.ok(Math.abs(plinkoBallAt(sample,1).y-.89)<1e-8);
 const animation=plinkoAnimationSamples(sample);
 assert.equal(animation.inputRange[0],0);
 assert.equal(animation.inputRange.at(-1),1);
 assert.equal(animation.inputRange.length,19);
 assert.equal(animation.xs.length,19);assert.equal(animation.ys.length,19);
 assert.ok(animation.inputRange.every((x,i,a)=>i===0||x>a[i-1]));
 assert.ok(kotlinUI.includes('val xStep = maxWidth * 0.042f'));
 assert.ok(kotlinUI.includes('val launchY = maxHeight * 0.205f'));
 assert.ok(kotlinUI.includes('val pocketCenterY = maxHeight * 0.89f'));
});

test('seven original Plinko assets are byte-identical and in correct source layer order',()=>{
 const drawables=['puppy_plinko_pegs','puppy_plinko_ball','puppy_plinko_glass',
 'puppy_plinko_frame','puppy_plinko_bins','puppy_plinko_header','puppy_plinko_drop_button'];
 for(const name of drawables){
  const kotlin=readFileSync(new URL('../../App/app/src/main/res/drawable/'+name+'.png',import.meta.url));
  const react=readFileSync(new URL('../assets/casino/'+name+'.png',import.meta.url));
  assert.ok(kotlin.equals(react),'original drawable mismatch: '+name);
  assert.ok(component.includes(name+'.png'),'render missing source art '+name);
 }
 const order=['source={art.pegs}','source={art.ball}','source={art.glass}','source={art.frame}','source={art.bins}'];
 assert.ok(order.every((x,i)=>i===0||component.indexOf(x)>component.indexOf(order[i-1])));
 for(const name of ['plinkoBallAt','plinkoAnimationSamples','plinkoFrame']){
  assert.ok(name==='plinkoBallAt'||component.includes(name));
 }
 assert.ok(component.includes('translateX'));
 assert.ok(component.includes('translateY'));
 assert.ok(component.includes('binHit===i'));
 assert.ok(component.includes('numberOfLines={1}'));
});

test('accepted Plinko wager debits once; saved result settles exactly once after reveal',()=>{
 let s=start(ready(200));
 assert.equal(s.game.casinoChips,100);
 assert.equal(s.game.pendingPlinko.binIndex,0);
 assert.equal(s.game.pendingPlinko.payout,5);
 assert.equal(s.plinkoDisplay,null);
 s=transition(s,{type:'plinkoReveal',now:1730000002200});
 assert.equal(s.game.casinoChips,105);
 assert.equal(s.game.pendingPlinko,null);
 assert.equal(s.plinkoDisplay.binIndex,0);
 assert.equal(s.plinkoDisplay.payout,5);
 const replay=transition(s,{type:'plinkoReveal',now:1730000002300});
 assert.equal(replay.game.casinoChips,105);
 assert.equal(replay.plinkoDisplay.payout,5);
});

test('saved Plinko path resumes from guest save; interrupted bet never disappears or repays',()=>{
 const path=Array.from({length:8},(_,i)=>i%2===0),jitter=[1,-1,12,-21,220,0,7,-220];
 const s=start(ready(200),path,jitter);
 const saved=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(saved.casinoChips,100);
 assert.equal(validPendingPlinko(saved.pendingPlinko),true);
 assert.deepEqual(saved.pendingPlinko.pathRight,path);
 assert.deepEqual(saved.pendingPlinko.bounceJitterPermille,jitter);
 const hydrated=transition(initialState(),{type:'hydrate',game:saved,now:1730000000300});
 assert.equal(hydrated.game.casinoChips,100);
 assert.equal(hydrated.plinkoDisplay,null);
 const finished=transition(hydrated,{type:'plinkoReveal',now:1730000002200});
 assert.equal(finished.game.casinoChips,250);
 assert.equal(finished.game.pendingPlinko,null);
 const after=parseGuestSave(serializeGuestSave(finished.game));
 assert.equal(after.casinoChips,250);
 assert.equal(after.pendingPlinko,null);
});

test('tampering, invalid bounce jitter and competing rounds cannot mint Casino Chips',()=>{
 const invalid=transition(ready(200),{type:'plinkoStart',wager:100,pathRight:Array(8).fill(true),
  bounceJitterPermille:Array(8).fill(221),now:1730000000000});
 assert.equal(invalid.game.casinoChips,200);
 const invalidPath=transition(ready(200),{type:'plinkoStart',wager:100,pathRight:Array(7).fill(true),
  bounceJitterPermille:Array(8).fill(0),now:1730000000000});
 assert.equal(invalidPath.game.casinoChips,200);
 const s=start(ready(200));
 const changed={...s.game.pendingPlinko,payout:123456};
 assert.equal(validPendingPlinko(changed),false);
 const restored=parseGuestSave(serializeGuestSave({...s.game,pendingPlinko:changed}));
 assert.equal(restored.pendingPlinko,null);
 const forged=transition({...s,game:{...s.game,pendingPlinko:changed}},{type:'plinkoReveal'});
 assert.equal(forged.game.casinoChips,100);
 const roulette=transition(s,{type:'rouletteStart',wager:20,bet:{type:'RED'},winningNumber:1,now:1730000000100});
 assert.equal(roulette.game.casinoChips,100);
 const slots=transition(s,{type:'slotsStart',wager:20,rolls:[0,0,0],now:1730000000100});
 assert.equal(slots.game.casinoChips,100);
 assert.equal(slots.game.pendingSlots,null);
 const blackjack=transition(s,{type:'blackjackStart',wager:20,deck:[],now:1730000000100});
 assert.equal(blackjack.game.casinoChips,100);
 const prestige=transition(s,{type:'prestige',now:1730000000100});
 assert.equal(prestige.game.pendingPlinko.payout,5);
 const legacy=transition(s,{type:'casino',gameType:'plinko',wager:20,path:Array(8).fill(true),now:1730000000100});
 assert.equal(legacy.game.casinoChips,100);
 assert.equal(legacy.game.pendingPlinko.payout,5);
});

test('Pup Plinko screen and Casino hub use the dedicated stage, source wager and resume flow',()=>{
 assert.ok(app.includes("import PlinkoV6 from './src/components/PlinkoV6.jsx'"));
 assert.ok(app.includes("g.pendingPlinko?'plinko':'hub'"));
 assert.ok(app.includes('Unfinished Plinko Drop'));
 assert.ok(app.includes('pending={g.pendingPlinko}'));
 for(const value of ['Pup Plinko','Casino Chip Wallet','Puppy Plinko','Drop Ball','How Pup Plinko works',
 'PLINKO_V6.betPresets','type:\'plinkoStart\'','type:\'plinkoReveal\'','art.drop','PLINKO_V6.dropMs']){
  assert.ok(component.includes(value),value);
 }
 assert.ok(!component.includes("type:'casino',gameType:'plinko'"));
 assert.ok(!component.includes('<WebView'));
});
