import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {rouletteOutcome} from '../src/engine/casinoV7.js';
import {
 ROULETTE_V6,ROULETTE_BETS,ROULETTE_OUTSIDE,rouletteColor,
 rouletteColorHex,rouletteBoardZones,rouletteWheelTarget,rouletteValidWager,
 rouletteValidBet,rouletteBetLabel,newRouletteGuestRound,validPendingRoulette,rouletteResult
} from '../src/engine/rouletteV6.js';
import {initialState,transition} from '../src/state/reducer.js';
import {serializeGuestSave,parseGuestSave} from '../src/state/guestSave.js';
const original=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyRouletteUi.kt',import.meta.url),'utf8');
const engine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyRouletteEngine.kt',import.meta.url),'utf8');
const component=readFileSync(new URL('../src/components/RouletteV6.jsx',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const ready=(chips=100)=>{const s=initialState();s.ready=true;s.game.casinoChips=chips;return s;};
const bet=(type,number)=>({type,...(type==='STRAIGHT'?{number}:{})});

test('5C follows launched Kotlin Roulette 37-pocket sequence, RTP and all seven bets',()=>{
 assert.ok(engine.includes('PUBLISHED_RTP_PERCENT = "97.30%"'));
 assert.ok(engine.includes('val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)'));
 assert.equal(ROULETTE_V6.rtp,'97.30%');
 assert.deepEqual(ROULETTE_V6.presets,[20,100,500,2500]);
 assert.deepEqual(ROULETTE_V6.wheelOrder,
 [0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26]);
 assert.equal(new Set(ROULETTE_V6.wheelOrder).size,37);
 assert.deepEqual(ROULETTE_BETS.map(b=>b.id),['RED','BLACK','ODD','EVEN','LOW','HIGH','STRAIGHT']);
 assert.deepEqual(ROULETTE_OUTSIDE,['LOW','EVEN','RED','BLACK','ODD','HIGH']);
 assert.ok(original.includes('private val EuropeanRouletteWheelOrder'));
 for(const wager of [20,100,500,2500,100000])assert.equal(rouletteValidWager(wager),true);
 for(const wager of [0,10,19,21,100001])assert.equal(rouletteValidWager(wager),false);
 assert.equal(rouletteValidBet(bet('STRAIGHT',0)),true);
 assert.equal(rouletteValidBet(bet('STRAIGHT',36)),true);
 assert.equal(rouletteValidBet(bet('STRAIGHT',37)),false);
 assert.equal(rouletteValidBet({type:'RED',number:4}),false);
 assert.equal(rouletteValidBet(bet('RED')),true);
 assert.equal(rouletteBetLabel(bet('STRAIGHT',0)),'Number 0');
 assert.equal(rouletteBetLabel(bet('LOW')),'1–18');
});

test('board overlays match actual 1600 x 900 Kotlin painted-table source coordinates',()=>{
 const b=rouletteBoardZones(320);
 assert.deepEqual([b.width,b.height],[320,180]);
 assert.deepEqual(b.zero,{left:20,top:49,width:18,height:57});
 assert.equal(b.numbers.length,36);
 const n1=b.numbers.find(n=>n.number===1);
 const n3=b.numbers.find(n=>n.number===3);
 const n36=b.numbers.find(n=>n.number===36);
 assert.deepEqual([n1.left,n1.top,n1.width,n1.height],[38,87,19,19]);
 assert.deepEqual([n3.left,n3.top],[38,49]);
 assert.deepEqual([n36.left,n36.top],[38+11*19,49]);
 assert.equal(b.outside.length,6);
 assert.deepEqual(b.outside.map(x=>x.type),ROULETTE_OUTSIDE);
 assert.equal(b.outside[0].top,114);
 assert.equal(b.outside[5].left,38+5*38);
 for(const expected of ['ROULETTE_TABLE_WIDTH = 1600f','ROULETTE_TABLE_HEIGHT = 900f','ROULETTE_GRID_X = 190f',
 'ROULETTE_GRID_Y = 245f','ROULETTE_ZERO_WIDTH = 90f']){
  assert.ok(original.includes(expected));
 }
 assert.ok(component.includes('rouletteBoardZones(width)'));
 assert.ok(component.includes('frame.numbers.map('));
 assert.ok(component.includes('frame.outside.map('));
 assert.ok(component.includes('Bet on 0'));
});

test('published single-zero payouts, colors and straight bet are source-equivalent',()=>{
 assert.equal(rouletteColor(0),'GREEN');
 assert.equal(rouletteColor(1),'RED');
 assert.equal(rouletteColor(2),'BLACK');
 assert.equal(rouletteColor(36),'RED');
 assert.equal(rouletteColorHex(0),'#187A45');
 assert.equal(rouletteColor(37),null);
 assert.equal(rouletteOutcome(100,bet('STRAIGHT',0),0).payout,3600);
 assert.equal(rouletteOutcome(100,bet('STRAIGHT',0),1).payout,0);
 for(const type of ['RED','BLACK','ODD','EVEN','LOW','HIGH']){
  assert.equal(rouletteOutcome(20,bet(type),0).payout,0);
 }
 assert.equal(rouletteOutcome(100,bet('RED'),1).payout,200);
 assert.equal(rouletteOutcome(100,bet('BLACK'),2).payout,200);
 assert.equal(rouletteOutcome(100,bet('EVEN'),4).payout,200);
 assert.equal(rouletteOutcome(100,bet('ODD'),5).payout,200);
 assert.equal(rouletteOutcome(100,bet('LOW'),18).payout,200);
 assert.equal(rouletteOutcome(100,bet('HIGH'),19).payout,200);
 assert.equal(newRouletteGuestRound(100,bet('STRAIGHT',0),0,1730000000000).multiplier,36);
});

test('rotor and ball targets follow Kotlin spins and correct winning pocket',()=>{
 assert.equal(ROULETTE_V6.spinMs,2600);
 assert.equal(ROULETTE_V6.reducedMotionMs,250);
 assert.equal(ROULETTE_V6.wheelHeight,204);
 assert.equal(ROULETTE_V6.wheelBase,190);
 assert.equal(ROULETTE_V6.rotor,154);
 assert.equal(ROULETTE_V6.ball,18);
 assert.equal(ROULETTE_V6.wheelTarget,1875);
 const zero=rouletteWheelTarget(0);
 assert.equal(zero.index,0);
 assert.ok(Math.abs(zero.pocketAngle-(-90+180/37))<0.0001);
 assert.equal(zero.ball,zero.wheel+zero.pocketAngle-2520);
 assert.equal(rouletteWheelTarget(36).index,13);
 assert.equal(rouletteWheelTarget(37),null);
 for(const png of ['puppy_roulette_wheel_base.png','puppy_roulette_rotor.png','puppy_roulette_ball.png','puppy_roulette_table.png']){
  assert.ok(component.includes(png));
 }
 assert.ok(component.includes('rouletteWheelTarget(shown.winningNumber)'));
 assert.ok(component.includes('Animated.timing(wheel'));
 assert.ok(component.includes('Animated.timing(ball'));
 assert.ok(original.includes('durationMillis = 2_400')===false||original.includes('tween(2_400'));
});

test('start debits once, roulette result settles once and blocks competing casino rounds',()=>{
 let s=ready(200);
 s=transition(s,{type:'rouletteStart',wager:100,bet:bet('RED'),winningNumber:1,now:1730000000000});
 assert.equal(s.game.casinoChips,100);
 assert.ok(validPendingRoulette(s.game.pendingRoulette));
 assert.equal(s.rouletteDisplay,null);
 const blockedCasino=transition(s,{type:'casino',gameType:'slots',wager:20,rolls:[0,0,0],now:1730000000010});
 assert.equal(blockedCasino.game.casinoChips,100);
 const blockedBlackjack=transition(s,{type:'blackjackStart',wager:20,deck:[],now:1730000000010});
 assert.equal(blockedBlackjack.game.casinoChips,100);
 const blockedPrestige=transition(s,{type:'prestige',now:1730000000010});
 assert.equal(blockedPrestige.game.pendingRoulette.winningNumber,1);
 s=transition(s,{type:'rouletteReveal',now:1730000002600});
 assert.equal(s.game.casinoChips,300);
 assert.equal(s.game.pendingRoulette,null);
 assert.equal(s.rouletteDisplay.winningNumber,1);
 const duplicate=transition(s,{type:'rouletteReveal',now:1730000002650});
 assert.equal(duplicate.game.casinoChips,300);
 const forged=transition(duplicate,{type:'casino',gameType:'roulette',wager:20,bet:bet('RED'),roll:1});
 assert.equal(forged.game.casinoChips,300,'legacy instant Roulette payout remains unavailable');
});

test('guest save round trip can resume pending roulette without double-paying',()=>{
 let s=ready(100);
 s=transition(s,{type:'rouletteStart',wager:20,bet:bet('STRAIGHT',0),winningNumber:0,now:1730000000000});
 assert.equal(s.game.casinoChips,80);
 const restored=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(validPendingRoulette(restored.pendingRoulette),true);
 const hydrated=transition(initialState(),{type:'hydrate',game:restored,now:1730000000300});
 assert.equal(hydrated.game.casinoChips,80);
 const settled=transition(hydrated,{type:'rouletteReveal',now:1730000002600});
 assert.equal(settled.game.casinoChips,800);
 assert.equal(settled.game.pendingRoulette,null);
 assert.equal(parseGuestSave(serializeGuestSave(settled.game)).casinoChips,800);
 assert.equal(transition(settled,{type:'rouletteReveal'}).game.casinoChips,800);
});

test('tampered outcome, illegal bet/pocket, insufficient balance are rejected without debit',()=>{
 let s=ready(100);
 const bad=transition(s,{type:'rouletteStart',wager:100,bet:bet('STRAIGHT',37),winningNumber:1,now:1730000000000});
 assert.equal(bad.game.casinoChips,100);
 assert.equal(bad.game.pendingRoulette,null);
 const badPocket=transition(s,{type:'rouletteStart',wager:100,bet:bet('RED'),winningNumber:37,now:1730000000000});
 assert.equal(badPocket.game.casinoChips,100);
 const notEnough=transition(s,{type:'rouletteStart',wager:500,bet:bet('RED'),winningNumber:1,now:1730000000000});
 assert.equal(notEnough.game.casinoChips,100);
 s=transition(s,{type:'rouletteStart',wager:20,bet:bet('RED'),winningNumber:1,now:1730000000000});
 const tampered={...s.game.pendingRoulette,payout:9999999};
 assert.equal(validPendingRoulette(tampered),false);
 assert.equal(parseGuestSave(serializeGuestSave({...s.game,pendingRoulette:tampered})).pendingRoulette,null);
 const denied=transition({...s,game:{...s.game,pendingRoulette:tampered}},{type:'rouletteReveal',now:1730000002600});
 assert.equal(denied.game.casinoChips,80);
 assert.equal(newRouletteGuestRound(20,{type:'RED',number:10},1,1730000000000),null);
});

test('React Roulette UI is dedicated and exposes selection, picker, RTP, rules and safe pending resume',()=>{
 for(const term of ['Puppy Roulette','European single-zero roulette','97.30%','rouletteBoardZones(width)','RouletteBoardTarget',
  'Choose landing number','0–36','Published Roulette rules','rouletteReveal','rouletteStart',
  'ROULETTE_V6.presets','Casino Chip Wallet','IMAGES.ball','IMAGES.rotor']){
  assert.ok(component.includes(term)||term==='97.30%'&&component.includes('ROULETTE_V6.rtp'),term);
 }
 assert.ok(app.includes('RouletteV6'));
 assert.match(app,/g\.pendingRoulette\?'roulette':/);
 assert.ok(app.includes('Unfinished Roulette Spin'));
 assert.ok(!component.includes("type:'casino',gameType:'roulette'"));
 assert.ok(!component.includes('<WebView'));
});
