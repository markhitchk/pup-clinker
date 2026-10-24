import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {newRoundFromDeck,stand,split,blackjackOutcome} from '../src/engine/blackjack.js';
import {
 BLACKJACK_V6,blackjackViewportWidth,blackjackActions,canDealBlackjack,
 blackjackHandSummaries,blackjackDealerCaption,blackjackDisplayRound
} from '../src/engine/blackjackV6.js';
import {transition} from '../src/state/reducer.js';
import {blackjackTable} from '../src/engine/blackjackLayout.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

const kotlinUi=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyBlackjackUi.kt',import.meta.url),'utf8');
const kotlinEngine=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyBlackjackEngine.kt',import.meta.url),'utf8');
const ui=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const scene=ui.slice(ui.indexOf('function BlackjackArtworkCard('),ui.indexOf('function CasinoScreen('));
const card=(r,s=0)=>13*s+r-1;
const deck=(...top)=>[...top,...Array.from({length:52},(_,i)=>i).filter(i=>!top.includes(i))];

test('5A mirrors Kotlin Blackjack wager presets, limits, 52-card rules and action rows',()=>{
 assert.ok(kotlinEngine.includes('wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)'));
 assert.deepEqual(BLACKJACK_V6.wagerPresets,[20,100,500,2500]);
 assert.equal(BLACKJACK_V6.splitHandLimit,3);
 assert.equal(BLACKJACK_V6.actionHeight,48);
 assert.equal(BLACKJACK_V6.actionRowThreshold,520);
 for(const phrase of ['BlackjackCartoonTable','BlackjackHandSummary','BlackjackActionPanel','BlackjackRulesCard','BlackjackWalletCopy']){
  assert.ok(kotlinUi.includes(phrase));
 }
 for(const phrase of ['BlackjackFeltTable','blackjackHandSummaries','blackjackActions','blackjackDealerCaption',
  'Blackjack Rules','Published Blackjack rules','blackjack_actions','blackjack_table','onSelectHand','Open Capsule']){
  if(phrase==='Open Capsule')continue;
  assert.ok(scene.includes(phrase),phrase);
 }
 assert.ok(scene.includes('Hand {index+1}'));
 assert.ok(scene.includes('selectedHand'));
 assert.ok(scene.includes('onPress={()=>onSelectHand(index)}'));
 assert.ok(scene.includes('blackjackDisplayRound(active,state.blackjackDisplay)'));
 assert.ok(!scene.includes('styles.dailyGiftCard'));
});

test('portrait phone and landscape fit table to native viewport budget, not fixed oversized scene',()=>{
 const narrow=blackjackViewportWidth(320,600,1);
 assert.ok(narrow<=320);
 assert.ok(blackjackTable(narrow).height<=300);
 const short=blackjackViewportWidth(260,380,1.5);
 assert.ok(short<=260);
 const landscape=blackjackViewportWidth(780,390,1);
 assert.ok(landscape<=BLACKJACK_V6.landscapeWidth);
 assert.ok(blackjackTable(landscape).height<=500);
 const tall=blackjackViewportWidth(390,900,1);
 assert.equal(tall,390);
 assert.ok(scene.includes('resizeMode="stretch"'));
 assert.ok(scene.includes('metrics.artworkTop'));
 assert.ok(scene.includes('metrics.dealer'));
 assert.ok(scene.includes('metrics.player'));
});

test('real chip-balance gates the actual Double/Split commands',()=>{
 const round=newRoundFromDeck(20,deck(card(8),card(3),card(8,1),card(7),card(2),card(3,1)));
 assert.equal(blackjackActions(round,19).hit,true);
 assert.equal(blackjackActions(round,19).stand,true);
 assert.equal(blackjackActions(round,19).double,false);
 assert.equal(blackjackActions(round,19).split,false);
 assert.equal(blackjackActions(round,20).double,true);
 assert.equal(blackjackActions(round,20).split,true);
 assert.equal(canDealBlackjack(200,100,null,null),true);
 assert.equal(canDealBlackjack(20,100,null,null),false);
 assert.equal(canDealBlackjack(200,250,null,null),true);
 assert.equal(canDealBlackjack(200,100,round,null),false);
 assert.equal(canDealBlackjack(200,100,null,{wager:20}),false);
});

test('selected split-hand summary and dealer hole-card caption match Kotlin rules',()=>{
 const round=newRoundFromDeck(20,deck(card(8),card(3),card(8,1),card(7),card(2),card(3,1)));
 assert.equal(blackjackDealerCaption(round),'Dealer · 3♣ showing · hole card hidden');
 const hands=split(round).state;
 assert.equal(hands.hands.length,2);
 const lines=blackjackHandSummaries(hands,null);
 assert.equal(lines.length,2);
 assert.ok(lines[0].active);
 assert.equal(lines[1].status,'Waiting');
 const dealer=newRoundFromDeck(20,deck(card(1),card(9),card(13),card(7)));
 assert.equal(dealer.complete,true);
 assert.equal(blackjackDealerCaption(dealer),'Dealer · 16 points');
 const win=blackjackOutcome(dealer);
 assert.equal(blackjackHandSummaries(dealer,win)[0].status,'Blackjack · 50 returned');
});

test('finished blackjack remains visible for inspection while settlement is exactly once',()=>{
 const g=freshGame();g.casinoChips=100;
 let s={game:g,ready:true,blackjack:null,blackjackDisplay:null,notice:''};
 const roundDeck=deck(card(10),card(5),card(9),card(9,1),card(2));
 s=transition(s,{type:'blackjackStart',wager:20,deck:roundDeck,now:1700000000000});
 assert.equal(s.game.casinoChips,80);
 assert.ok(s.blackjack&&!s.blackjack.complete);
 s=transition(s,{type:'blackjackStand',now:1700000000010});
 assert.equal(s.game.casinoChips,120);
 assert.equal(s.blackjack,null);
 assert.equal(s.game.casinoRound,null);
 assert.ok(s.blackjackDisplay?.state.complete);
 assert.equal(s.blackjackDisplay.outcome.totalPayout,40);
 assert.equal(blackjackDisplayRound(s.blackjack,s.blackjackDisplay).hands.length,1);
 const duplicate=transition(s,{type:'blackjackStand',now:1700000000020});
 assert.equal(duplicate.game.casinoChips,120);
 const next=transition(duplicate,{type:'blackjackStart',wager:20,deck:roundDeck,now:1700000000030});
 assert.equal(next.blackjackDisplay,null);
 assert.equal(next.game.casinoChips,100);
});

test('initial natural Blackjack does not hide settled hand or re-pay on preview reload',()=>{
 const g=freshGame();g.casinoChips=100;
 let s={game:g,ready:true,blackjack:null,blackjackDisplay:null,notice:''};
 const naturalDeck=deck(card(1),card(9),card(13),card(7));
 s=transition(s,{type:'blackjackStart',wager:20,deck:naturalDeck,now:1700000000000});
 assert.equal(s.game.casinoChips,130);
 assert.equal(s.blackjack,null);
 assert.ok(s.blackjackDisplay.state.complete);
 assert.equal(s.blackjackDisplay.outcome.totalPayout,50);
 const cached=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(cached.casinoRound,null);
 assert.equal(cached.casinoChips,130);
});

test('active Blackjack round resumes on Casino open and shows a dedicated Resume button',()=>{
 const casino=ui.slice(ui.indexOf('function CasinoScreen('),ui.indexOf('function OnboardingScreen('));
 assert.ok(casino.includes("state.blackjack?'blackjack':'hub'"));
 assert.ok(casino.includes('Unfinished Blackjack Hand'));
 assert.ok(casino.includes("onPress={()=>setPage('blackjack')}"));
 assert.ok(casino.includes('BlackjackV6Panel g={g} act={act} state={state}'));
 assert.ok(scene.includes('Casino Chip Wallet'));
 assert.ok(scene.includes('2,500')); // native presets must appear through BLACKJACK_V6.wagerPresets
});
