import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame,tap,sanitizeGame} from '../src/engine/game.js';
import {initialState,transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
import {V6_PLAY_LAYOUT,v6PlaySizing,v6Format,v6WalletFormat,v6CooldownSeconds,v6PlayTapLabel} from '../src/engine/playV6.js';

const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const react=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const launchedPlay=kotlin.slice(kotlin.indexOf('private fun V6Play('),kotlin.indexOf('private fun V6CareAndDaily('));
const reactPlay=react.slice(react.indexOf('function PlayScreen('),react.indexOf('function CareScreen('));

test('legacy V6 sizing helper remains backward compatible for old callers',()=>{
 assert.deepEqual(v6PlaySizing(360),{circle:258,portrait:238,stageHeight:365});
 assert.deepEqual(v6PlaySizing(280),{circle:248,portrait:228,stageHeight:365});
 assert.deepEqual(v6PlaySizing(500),{circle:258,portrait:238,stageHeight:365});
 assert.equal(V6_PLAY_LAYOUT.stageRadius,30);
 assert.equal(V6_PLAY_LAYOUT.idleHalfCycleMs,1700);
 assert.equal(V6_PLAY_LAYOUT.tapPressedScale,0.89);
 assert.equal(V6_PLAY_LAYOUT.ticketVisibleMs,2750);
});

test('Play wallet and label use Kotlin formatV6 thresholds and precision',()=>{
 assert.ok(kotlin.includes('value >= 1_000L -> "%.1fK"'));
 assert.deepEqual([0,999,1000,1250,1000000,1234567].map(n=>v6Format(n,true)),
  ['0','999','1.0K','1.3K','1.00M','1.23M']);
 assert.equal(v6Format(1234567,false),'1,234,567');
 assert.equal(v6Format(1000000000,true),'1.00B');
 assert.equal(v6Format(1000000000000,true),'1.00T');
 const g=freshGame();g.clickPower=1500;
 assert.equal(v6PlayTapLabel(g,1000),'Tap Buddy · +1.5K 🍪');
 g.compactNumbers=false;
 assert.equal(v6PlayTapLabel(g,1000),'Tap Buddy · +1,500 🍪');
 // Kotlin displays clickPower, not the separate care bonus.
 g.clickPower=1;
 assert.equal(v6PlayTapLabel(g,1000),'Tap Buddy · +1 🍪');
});

test('time-bound Kotlin cooldown prevents taps and survives guest save',()=>{
 const g=freshGame();g.cooldownUntilMs=4500;
 assert.equal(v6CooldownSeconds(g,1000),4);
 assert.equal(v6PlayTapLabel(g,1000),'🐶👁️ Fair-play cooldown · 4s');
 assert.equal(v6CooldownSeconds(g,4500),0);
 assert.equal(tap(g,2000,()=>0.99),null);
 assert.equal(g.totalTaps,0);
 const cached=parseGuestSave(serializeGuestSave(g));
 assert.equal(cached.cooldownUntilMs,4500);
 const s=initialState();s.ready=true;s.game=cached;
 const before=transition(s,{type:'tap',now:2000,randoms:[0.99,0.99]});
 assert.equal(before.game.totalTaps,0);
 const after=transition(before,{type:'tap',now:4500,randoms:[0.99,0.99]});
 assert.equal(after.game.totalTaps,1);
 assert.equal(sanitizeGame({...g,cooldownUntilMs:-999}).cooldownUntilMs,0);
});

test('trillion-scale Pup Account Treats remain visibly responsive without changing Kotlin legacy formatter',()=>{
 const stored=9_000_000_010_023_864;
 const next=stored+1_588_842;
 assert.equal(v6Format(stored,true),v6Format(next,true),'legacy format hides million-Treat gains');
 assert.notEqual(v6WalletFormat(stored,true),v6WalletFormat(next,true),'live wallet must update after server confirms each tap');
 assert.notEqual(v6WalletFormat(stored,true),v6WalletFormat(stored+10_000,true),'balanced 10k payout must remain visible each tap');
 assert.equal(v6WalletFormat(stored,true),'9000.000010024T');
 assert.equal(v6WalletFormat(next,true),'9000.000011613T');
 assert.equal(v6WalletFormat(stored,false),'9,000,000,010,023,864');
 assert.equal(v6WalletFormat(1_234_567_890,true),'1.2346B');
 assert.equal(v6WalletFormat(NaN,true),'0');
 assert.equal(v6WalletFormat(-20,true),'0');
 assert.equal(v6WalletFormat(1000,true),'1.0K');
 assert.ok(9_000_000_010_023_864+1_588_842<Number.MAX_SAFE_INTEGER);
 const wallet=react.slice(react.indexOf('function Wallet('),react.indexOf('function NeedPill('));
 assert.match(wallet,/v6WalletFormat\(value/);
 assert.match(wallet,/Exact balance/);
 assert.match(wallet,/adjustsFontSizeToFit/);
});
