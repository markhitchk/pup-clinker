import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {WHEEL_PRIZES,wheelOutcome} from '../src/engine/casinoV7.js';
import {
 WHEEL_V6,WHEEL_SEGMENTS,WHEEL_PROTECTED_REWARD_POOL,WHEEL_GUEST_UNLOCKS_ENABLED,
 wheelCenterAngle,wheelTargetDegrees,wheelLabelPosition,wheelFrame,
 validWheelWager,newWheelGuestRound,validPendingWheel,wheelResult,wheelResultLabel
} from '../src/engine/wheelV6.js';
import {initialState,transition} from '../src/state/reducer.js';
import {serializeGuestSave,parseGuestSave} from '../src/state/guestSave.js';
const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyLuckyWheelUi.kt',import.meta.url),'utf8');
const engine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyLuckyWheelEngine.kt',import.meta.url),'utf8');
const reward=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyCasinoPuppyRewards.kt',import.meta.url),'utf8');
const component=readFileSync(new URL('../src/components/LuckyWheelV6.jsx',import.meta.url),'utf8');
const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
function ready(chips=200){const s=initialState();s.ready=true;s.game.casinoChips=chips;return s;}

test('Part 5F matches Kotlin seven slices, probabilities, chip payouts and source wagers',()=>{
 assert.ok(engine.includes('PUBLISHED_TREAT_RTP_PERCENT = "82.5%"'));
 assert.equal(WHEEL_V6.chipRtp,'82.5%');
 assert.deepEqual(WHEEL_V6.wagers,[20,100,500,2500]);
 assert.deepEqual(WHEEL_SEGMENTS.map(x=>x.id),WHEEL_PRIZES.map(x=>x.id));
 assert.deepEqual(WHEEL_SEGMENTS.map(x=>x.weight),[38,25,20,10,4,1,2]);
 assert.deepEqual(WHEEL_SEGMENTS.map(x=>x.multiplierHundredths),[0,50,100,200,500,1000,0]);
 assert.equal(WHEEL_SEGMENTS.reduce((n,x)=>n+x.weight,0),100);
 assert.deepEqual(WHEEL_SEGMENTS.map(s=>s.label),['MISS','0.5×','1×','2×','5×','10×','PUP']);
 for(const w of [20,100,500,2500,100000])assert.equal(validWheelWager(w),true);
 for(const w of [0,19,30,21,100001,1.2])assert.equal(validWheelWager(w),false);
 assert.ok(engine.includes('MAX_WAGER_TREATS = 100_000L'));
 assert.ok(engine.includes('WAGER_INCREMENT_TREATS = 20L'));
 assert.ok(kotlin.includes('tween(2_500, easing = FastOutSlowInEasing)'));
 assert.ok(kotlin.includes('delay(if (state.animationsEnabled) 2_650 else 250)'));
 assert.equal(WHEEL_V6.rotationMs,2500);
 assert.equal(WHEEL_V6.spinMs,2650);
 assert.equal(WHEEL_V6.reducedMotionMs,250);
 assert.equal(WHEEL_V6.pointerClicks,18);
 assert.deepEqual(WHEEL_V6.pointerClickTimings,[30,38,32]);
});

test('wedge center angle, landed rotation and narrow-label geometry match Kotlin',()=>{
 assert.ok(kotlin.includes('PuppyCasinoCartoonMath.segmentCenterDegrees'));
 assert.equal(wheelCenterAngle(0),-90+38*1.8);
 assert.equal(wheelCenterAngle(6),-90+98*3.6+2*1.8);
 assert.equal(wheelTargetDegrees(0),2160-90-wheelCenterAngle(0));
 assert.equal(wheelTargetDegrees(6),2160-90-wheelCenterAngle(6));
 assert.equal(wheelTargetDegrees(8),null);
 const f=wheelFrame(420);
 assert.equal(f.width,420);
 assert.equal(f.height,447);
 assert.equal(f.disc.width,420*.82);
 assert.equal(f.disc.left,(420-420*.82)/2);
 assert.equal(f.disc.top,447*.075);
 assert.equal(f.pointer.width,420*.12);
 assert.equal(f.pointer.top,447*.018);
 assert.equal(wheelFrame(800).width,420);
 assert.equal(wheelFrame(300).width,300);
 const normal=wheelLabelPosition(0,344.4),narrow=wheelLabelPosition(6,344.4);
 assert.ok(normal.width>narrow.width);
 assert.equal(normal.fontSize,10);
 assert.equal(narrow.fontSize,7);
 assert.ok(narrow.left>=0&&narrow.top>=0);
 assert.equal(wheelLabelPosition(7,200),null);
 assert.ok(kotlin.includes('modifier.aspectRatio(420f / 447f)'));
 assert.ok(kotlin.includes('val wheelSize = maxWidth * 0.82f'));
 assert.ok(kotlin.includes('val wheelY = maxHeight * 0.075f'));
});

test('three original wheel PNGs bundled byte-for-byte; labels and pointer animate separately',()=>{
 for(const p of ['puppy_lucky_wheel_disc','puppy_lucky_wheel_frame','puppy_lucky_wheel_pointer']){
  const k=readFileSync(new URL('../../App/app/src/main/res/drawable/'+p+'.png',import.meta.url));
  const r=readFileSync(new URL('../assets/casino/'+p+'.png',import.meta.url));
  assert.ok(k.equals(r),p+' changed from the Kotlin original');
  assert.ok(component.includes(p+'.png'));
 }
 for(const snippet of ['WheelBoard','ART.disc','ART.frame','ART.pointer','wheelLabelPosition',
  'unrotate','Animated.timing(rotation','WHEEL_V6.pointerClicks','rotationMs',
  'testID="lucky_wheel_board"','wheelTargetDegrees','wheelResultLabel']){
  assert.ok(component.includes(snippet),snippet);
 }
 assert.ok(!component.includes('<WebView'));
});

test('each roll uses Kotlin weighted picks and official ineligible-puppy fallback',()=>{
 const w=100;
 const samples=[
  [0,'MISS',0],[37,'MISS',0],
  [38,'HALF',50],[62,'HALF',50],
  [63,'REFUND',100],[82,'REFUND',100],
  [83,'DOUBLE',200],[92,'DOUBLE',200],
  [93,'FIVE_X',500],[96,'FIVE_X',500],
  [97,'TEN_X',1000],[98,'REFUND',100],[99,'REFUND',100]
 ];
 for(const [roll,prize,payout] of samples){
  const r=newWheelGuestRound(w,roll,1730000000000);
  assert.equal(r.prize,prize,'roll '+roll);
  assert.equal(r.payout,payout);
  assert.equal(r.segmentIndex,WHEEL_SEGMENTS.findIndex(x=>x.id===prize));
  assert.equal(validPendingWheel(r),true);
  assert.equal(r.puppyStyleId,null);
  assert.deepEqual(wheelResult(r).puppyStyleId,null);
 }
 assert.equal(newWheelGuestRound(20,-1,1730000000000),null);
 assert.equal(newWheelGuestRound(20,100,1730000000000),null);
 assert.equal(newWheelGuestRound(25,45,1730000000000),null);
 assert.equal(newWheelGuestRound(20,45,0),null);
 assert.ok(wheelResultLabel(wheelResult(newWheelGuestRound(100,38,1730000000000))).includes('0.5×'));
 assert.equal(wheelOutcome(100,98,false).prize,'REFUND');
 assert.equal(WHEEL_GUEST_UNLOCKS_ENABLED,false);
});

test('guest preview never mints protected 16 Kotlin casino pup IDs or bypasses role ownership',()=>{
 const match=reward.match(/val eligibleStyleIds: List<String> = listOf\(([\s\S]*?)\)/);
 assert.ok(match,'missing native eligible roster');
 const ids=[...match[1].matchAll(/"([^"]+)"/g)].map(m=>m[1]);
 assert.deepEqual(ids,[...WHEEL_PROTECTED_REWARD_POOL]);
 const g=freshGame();
 const original=[...g.unlockedPuppies];
 let s=ready(200);
 s=transition(s,{type:'wheelStart',wager:100,roll:99,now:1730000000000});
 assert.equal(s.game.casinoChips,100);
 assert.equal(s.game.pendingWheel.prize,'REFUND');
 s=transition(s,{type:'wheelReveal',now:1730000002650});
 assert.equal(s.game.casinoChips,200);
 assert.deepEqual(s.game.unlockedPuppies,original);
 assert.ok(component.includes('No special puppy IDs are granted locally.'));
 assert.ok(component.includes('WHEEL_GUEST_UNLOCKS_ENABLED'));
});

test('accepted saved wheel wager debits once, reveals payout once; repeat is harmless',()=>{
 let s=ready(200);
 s=transition(s,{type:'wheelStart',wager:100,roll:85,now:1730000000000});
 assert.equal(s.game.casinoChips,100);
 assert.equal(s.game.pendingWheel.prize,'DOUBLE');
 assert.equal(s.game.pendingWheel.payout,200);
 assert.equal(s.wheelDisplay,null);
 const blocked=transition(s,{type:'casino',gameType:'wheel',wager:20,roll:97,now:1730000000001});
 assert.equal(blocked.game.casinoChips,100);
 const other=transition(s,{type:'slotsStart',wager:20,rolls:[0,0,0],now:1730000000001});
 assert.equal(other.game.casinoChips,100);
 assert.equal(other.game.pendingSlots,null);
 const prestige=transition(s,{type:'prestige',now:1730000000001});
 assert.ok(prestige.game.pendingWheel);
 s=transition(s,{type:'wheelReveal',now:1730000002650});
 assert.equal(s.game.casinoChips,300);
 assert.equal(s.game.pendingWheel,null);
 assert.equal(s.wheelDisplay.payout,200);
 const repeated=transition(s,{type:'wheelReveal',now:1730000003000});
 assert.equal(repeated.game.casinoChips,300);
 assert.equal(repeated.wheelDisplay.payout,200);
});

test('wheel guest save restores committed spin, same payout and no double-settlement',()=>{
 let s=ready(200);
 s=transition(s,{type:'wheelStart',wager:100,roll:97,now:1730000000000});
 const saved=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(saved.casinoChips,100);
 assert.equal(validPendingWheel(saved.pendingWheel),true);
 const hydrated=transition(initialState(),{type:'hydrate',game:saved,now:1730000000100});
 assert.equal(hydrated.game.casinoChips,100);
 assert.equal(hydrated.wheelDisplay,null);
 const done=transition(hydrated,{type:'wheelReveal',now:1730000002650});
 assert.equal(done.game.casinoChips,1100);
 assert.equal(done.wheelDisplay.prize,'TEN_X');
 assert.equal(done.game.pendingWheel,null);
 const post=parseGuestSave(serializeGuestSave(done.game));
 assert.equal(post.casinoChips,1100);
 assert.equal(post.pendingWheel,null);
 const second=transition(done,{type:'wheelReveal',now:1730000002700});
 assert.equal(second.game.casinoChips,1100);
});

test('mutated spin, payout, puppy ID, segment or future schema are never paid',()=>{
 let s=ready(200);
 s=transition(s,{type:'wheelStart',wager:100,roll:85,now:1730000000000});
 for(const altered of [
  {...s.game.pendingWheel,payout:100000000},
  {...s.game.pendingWheel,segmentIndex:6},
  {...s.game.pendingWheel,prize:'PUPPY_UNLOCK'},
  {...s.game.pendingWheel,puppyStyleId:'dev_pup'},
  {...s.game.pendingWheel,version:3},
  {...s.game.pendingWheel,roll:-1}
 ]){
  assert.equal(validPendingWheel(altered),false);
  const game={...s.game,pendingWheel:altered};
  assert.equal(parseGuestSave(serializeGuestSave(game)).pendingWheel,null);
  const denied=transition({...s,game},{type:'wheelReveal',now:1730000002650});
  assert.equal(denied.game.casinoChips,100);
 }
});

test('Casino page uses the dedicated wheel screen, saved resume card and no legacy instant payout',()=>{
 assert.ok(app.includes("import LuckyWheelV6 from './src/components/LuckyWheelV6.jsx'"));
 assert.ok(app.includes("g.pendingWheel?'wheel':'hub'"));
 assert.ok(app.includes('Unfinished Lucky Pup Wheel Spin'));
 assert.ok(app.includes("page==='wheel'?<LuckyWheelV6"));
 assert.ok(!component.includes("type:'casino',gameType:'wheel'"));
 for(const t of ['Casino Chip Wallet','Lucky Pup Wheel','LUCKY PUP WHEEL',
  'Published wheel','82.5%','WHEEL_V6.wagers','wheelStart','wheelReveal']){
  assert.ok(component.includes(t)||t==='82.5%'&&component.includes('WHEEL_V6.chipRtp'),t);
 }
});

test('all other Casino screens visibly disable wagers during a committed wheel spin',()=>{
 const files=[
  'SlotsV6.jsx','RouletteV6.jsx','PlinkoV6.jsx','ScratchersV6.jsx'
 ];
 for(const name of files){
  const source=readFileSync(new URL('../src/components/'+name,import.meta.url),'utf8');
  assert.ok(source.includes('game.pendingWheel'),name+' UI must block active wheel wager');
 }
 assert.ok(app.includes('g.pendingWheel'), 'Casino navigation restores pending wheel');
 const s=ready(200);
 const playing=transition(s,{type:'wheelStart',wager:20,roll:63,now:1730000000000});
 for(const action of [
  {type:'scratchStart',wager:20,roll:0},
  {type:'slotsStart',wager:20,rolls:[0,0,0]},
  {type:'rouletteStart',wager:20,bet:{type:'RED'},winningNumber:1},
  {type:'plinkoStart',wager:20,pathRight:Array(8).fill(false),bounceJitterPermille:Array(8).fill(0)},
  {type:'blackjackStart',wager:20,deck:[]}
 ]){
  const attempted=transition(playing,{...action,now:1730000000010});
  assert.equal(attempted.game.casinoChips,180,'wheel pending debit bypass: '+action.type);
  assert.deepEqual(attempted.game.pendingWheel,playing.game.pendingWheel);
 }
});
