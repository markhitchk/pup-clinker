import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,statSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {slotsOutcome,SLOTS} from '../src/engine/casinoV7.js';
import {newSlotsGuestRound,validPendingSlots,slotsFrame,slotDrawIndex,slotsResultLabel,SLOTS_V6,SLOT_SYMBOLS,validSlotsWager} from '../src/engine/slotsV6.js';
import {transition,initialState} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

const nativeUI=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySlotsUi.kt',import.meta.url),'utf8');
const nativeEngine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySlotsEngine.kt',import.meta.url),'utf8');
const component=readFileSync(new URL('../src/components/SlotsV6.jsx',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const ready=(chips=100)=>{const state=initialState();state.ready=true;state.game.casinoChips=chips;return state;};

test('Slots V6 matches launched Kotlin weighted symbols, published RTP, wager presets and geometry',()=>{
 assert.ok(nativeEngine.includes('PUBLISHED_RTP_PERCENT = "92.66%"'));
 assert.ok(nativeEngine.includes('wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)'));
 assert.equal(SLOTS_V6.publishedRtp,'92.66%');
 assert.deepEqual(SLOTS_V6.presets,[20,100,500,2500]);
 assert.deepEqual(SLOT_SYMBOLS.map(s=>s.emoji),SLOTS.map(s=>s.symbol));
 assert.deepEqual(SLOT_SYMBOLS.map(s=>s.weight),[30,25,20,13,8,4]);
 assert.deepEqual(SLOT_SYMBOLS.map(s=>s.multiplier),[7,12,18,35,100,500]);
 assert.equal(SLOT_SYMBOLS.reduce((sum,x)=>sum+x.weight,0),100);
 assert.ok(nativeUI.includes('SLOTS_MACHINE_VISIBLE_WIDTH = 440f'));
 assert.ok(nativeUI.includes('SLOTS_MACHINE_VISIBLE_HEIGHT = 483f'));
 assert.deepEqual(SLOTS_V6.reelTicks,[16,23,30]);
 assert.equal(SLOTS_V6.tickMs,70);
 assert.equal(SLOTS_V6.revealMs,2350);
 const frame=slotsFrame(440);
 assert.equal(frame.height,440/0.86);
 assert.equal(frame.machine.width,440*0.80);
 assert.equal(frame.reels[0].left,440*0.045+440*0.8*57/440);
 assert.equal(frame.reels[1].left,440*0.045+440*0.8*170/440);
 assert.equal(frame.reels[2].left,440*0.045+440*0.8*285/440);
 assert.ok(frame.mascot.top<frame.machine.top);
 assert.ok(frame.lever.top<frame.leverBase.top);
});

test('Slots Kotlin original machine, mascot, lever and six reel PNGs are exact byte matches',()=>{
 for(const name of ['puppy_slots_machine','puppy_slots_mascot','puppy_slots_lever_base','puppy_slots_lever_handle',
 'slot_treat','slot_ball','slot_paw','slot_ticket','slot_puppy','slot_star']){
  const original=new URL('../../App/app/src/main/res/drawable/'+name+'.png',import.meta.url);
  const copied=new URL('../assets/casino/'+name+'.png',import.meta.url);
  assert.equal(statSync(copied).size,statSync(original).size,name+' must be byte-identical');
  assert.ok(readFileSync(copied).equals(readFileSync(original)),name+' PNG changed');
  assert.ok(component.includes(name+'.png'),name+' must be rendered as an original image');
 }
 assert.ok(component.includes('art.machine'));
 assert.ok(component.includes('art.mascot'));
 assert.ok(component.includes('art.leverBase'));
 assert.ok(component.includes('art.lever'));
 assert.ok(component.includes('SLOTS_V6.reelLeft')===false||component.includes('slotsFrame(sceneWidth)'));
 assert.ok(component.includes('slot_reel_'));
 assert.ok(component.includes('Pull Puppy Slots lever'));
 assert.ok(component.includes('Reel 1 → Reel 2 → Reel 3'));
});

test('Spin outcome uses Kotlin deterministic weights and published pair/triple returns',()=>{
 assert.deepEqual(slotsOutcome(20,[0,0,0]),{symbols:['🍪','🍪','🍪'],payout:140,winKind:'TRIPLE'});
 assert.deepEqual(slotsOutcome(100,[0,10,60]),{symbols:['🍪','🍪','🐾'],payout:50,winKind:'PAIR'});
 assert.deepEqual(slotsOutcome(100,[0,31,56]),{symbols:['🍪','🎾','🐾'],payout:0,winKind:'LOSS'});
 assert.deepEqual(slotsOutcome(20,[99,99,99]),{symbols:['⭐','⭐','⭐'],payout:10000,winKind:'TRIPLE'});
 assert.equal(slotsResultLabel(slotsOutcome(100,[0,10,60])),'Pair · 0.5×');
 assert.equal(slotsResultLabel(slotsOutcome(100,[99,99,99])),'Triple · 500×');
 assert.equal(slotDrawIndex(0,16),4);
 assert.equal(slotDrawIndex(1,23),1);
 assert.equal(slotDrawIndex(2,30),4);
 for(const w of [20,100,500,2500,100000])assert.equal(validSlotsWager(w),true);
 for(const w of [0,19,25,100001,1.2])assert.equal(validSlotsWager(w),false);
});

test('Start deducts once, then reveal credits exact payout once; competing Casino games blocked',()=>{
 let state=ready(200);
 state=transition(state,{type:'slotsStart',wager:100,rolls:[0,0,0],now:1730000000000});
 assert.equal(state.game.casinoChips,100);
 assert.equal(state.game.pendingSlots.payout,700);
 assert.equal(state.slotsDisplay,null);
 const blocked=transition(state,{type:'casino',gameType:'roulette',wager:20,bet:'RED',roll:1,now:1730000000001});
 assert.equal(blocked.game.casinoChips,100);
 assert.ok(blocked.game.pendingSlots);
 const blockedPrestige=transition(blocked,{type:'prestige',now:1730000000002});
 assert.ok(blockedPrestige.game.pendingSlots);
 assert.equal(blockedPrestige.game.casinoChips,100);
 state=transition(blockedPrestige,{type:'slotsReveal',now:1730000002350});
 assert.equal(state.game.casinoChips,800);
 assert.equal(state.game.pendingSlots,null);
 assert.equal(state.slotsDisplay.payout,700);
 const repeat=transition(state,{type:'slotsReveal',now:1730000002360});
 assert.equal(repeat.game.casinoChips,800);
 assert.equal(repeat.slotsDisplay.payout,700);
});

test('Pending Slots round persists through restart without invisible/duplicate payout',()=>{
 let game=freshGame();game.casinoChips=100;
 let state={game,ready:true,blackjack:null,blackjackDisplay:null,slotsDisplay:null,notice:''};
 state=transition(state,{type:'slotsStart',wager:20,rolls:[0,40,70],now:1730000000000});
 assert.equal(state.game.casinoChips,80);
 const restored=parseGuestSave(serializeGuestSave(state.game));
 assert.equal(validPendingSlots(restored.pendingSlots),true);
 const hydrated=transition(initialState(),{type:'hydrate',game:restored,now:1730000000100});
 assert.equal(hydrated.game.casinoChips,80);
 assert.equal(hydrated.slotsDisplay,null);
 const done=transition(hydrated,{type:'slotsReveal',now:1730000002350});
 assert.equal(done.game.pendingSlots,null);
 assert.equal(done.game.casinoChips,80); // three different symbols: loss
 const after=parseGuestSave(serializeGuestSave(done.game));
 assert.equal(after.pendingSlots,null);
 assert.equal(after.casinoChips,80);
 const repeat=transition(done,{type:'slotsReveal',now:1730000003000});
 assert.equal(repeat.game.casinoChips,80);
});

test('Modified pending payout or unknown symbols cannot be settled or persisted',()=>{
 let state=ready(100);
 state=transition(state,{type:'slotsStart',wager:20,rolls:[0,0,0],now:1730000000000});
 const changed={...state.game.pendingSlots,payout:9999999};
 assert.equal(validPendingSlots(changed),false);
 const guest=parseGuestSave(serializeGuestSave({...state.game,pendingSlots:changed}));
 assert.equal(guest.pendingSlots,null);
 const blocked=transition({...state,game:{...state.game,pendingSlots:changed}},{type:'slotsReveal'});
 assert.equal(blocked.game.casinoChips,80);
 const invalid=transition(state,{type:'slotsStart',wager:100,rolls:[0,0,0],now:1730000000001});
 assert.equal(invalid.game.casinoChips,80);
 const fresh=transition(ready(100),{type:'slotsStart',wager:20,rolls:[100,0,0],now:1730000000000});
 assert.equal(fresh.game.casinoChips,100);
 assert.equal(fresh.game.pendingSlots,null);
});

test('Slots scene has Kotlin result and rules, linked from Casino without duplicate parent wager bar',()=>{
 assert.ok(app.includes('SlotsV6'));
 assert.ok(app.includes("g.pendingSlots?'slots':'hub'"));
 assert.ok(app.includes('Unfinished Slots Spin'));
 assert.ok(app.includes("type:'slotsStart'")||component.includes("type:'slotsStart'"));
 assert.ok(component.includes("type:'slotsReveal'"));
 assert.ok(component.includes('slotsResultLabel(displayed)'));
 for(const text of ['Published payout table','Published reel weights','How Puppy Slots works','Casino Chip Wallet']){
  assert.ok(component.includes(text),text);
 }
 assert.ok(!component.includes("type:'casino',gameType:'slots'"));
 assert.ok(nativeUI.includes('PuppySlotsScreen('));
});
