import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {newRoundFromDeck,handTotal,canHit,canSplit,canDouble,hit,stand,doubleDown,split,blackjackOutcome,validBlackjackState} from '../src/engine/blackjack.js';
import {transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
const card=(rank,suit=0)=>suit*13+rank-1;
const deck=(...top)=>[...top,...Array.from({length:52},(_,i)=>i).filter(i=>!top.includes(i))];

test('52 unique cards are dealt alternating player/dealer like Kotlin',()=>{
 const a=newRoundFromDeck(20,deck(card(8),card(2),card(3),card(10)));
 assert.deepEqual(a.hands[0].cards,[card(8),card(3)]);
 assert.deepEqual(a.dealerCards,[card(2),card(10)]);
 assert.equal(a.remainingDeck.length,48);
 assert.equal(a.activeHandIndex,0);
});
test('invalid or duplicated physical deck fails closed',()=>{
 assert.throws(()=>newRoundFromDeck(20,[1,1]));
 assert.throws(()=>newRoundFromDeck(15,deck()));
});
test('soft ace and face-card ranks evaluate to source value',()=>{
 assert.deepEqual(handTotal([card(1),card(6)]),{total:17,soft:true});
 assert.deepEqual(handTotal([card(1),card(6),card(10)]),{total:17,soft:false});
});
test('source 3:2 natural blackjack payout, including dealer natural tie',()=>{
 const player=newRoundFromDeck(20,deck(card(1),card(9),card(13),card(7)));
 assert.equal(player.complete,true);
 assert.equal(blackjackOutcome(player).totalPayout,50);
 const both=newRoundFromDeck(20,deck(card(1),card(1,1),card(13),card(13,1)));
 assert.equal(blackjackOutcome(both).totalPayout,20);
});
test('splitting matching ranks consumes one additional wager and creates two hands',()=>{
 const before=newRoundFromDeck(20,deck(card(8),card(3),card(8,1),card(7),card(2),card(3,1)));
 assert.equal(canSplit(before),true);
 const after=split(before);
 assert.equal(after.success,true);assert.equal(after.additionalWager,20);
 assert.equal(after.state.hands.length,2);assert.equal(after.state.remainingDeck.length,46);
 assert.equal(after.state.hands.every(h=>h.fromSplit),true);
});
test('double down draws exactly one card and charges original wager again',()=>{
 const before=newRoundFromDeck(20,deck(card(5),card(8),card(6),card(9),card(10)));
 assert.equal(canDouble(before),true);
 const after=doubleDown(before);
 assert.equal(after.success,true);assert.equal(after.additionalWager,20);
 assert.equal(after.state.hands[0].wager,40);
 assert.equal(after.state.hands[0].cards.length,3);
});
test('bust stops player and does not draw dealer if all hands bust',()=>{
 const before=newRoundFromDeck(20,deck(card(10),card(9),card(9,1),card(10,1),card(8)));
 const after=hit(before);
 assert.equal(after.success,true);assert.equal(after.state.complete,true);
 assert.equal(blackjackOutcome(after.state).totalPayout,0);
 assert.equal(after.state.dealerCards.length,2);
});
test('standing resolves dealer to at least hard 17 and pays a win correctly',()=>{
 const before=newRoundFromDeck(20,deck(card(10),card(5),card(9),card(9,1),card(2)));
 const after=stand(before);
 assert.equal(after.success,true);assert.equal(after.state.complete,true);
 assert.ok(handTotal(after.state.dealerCards).total>=17);
 assert.equal(blackjackOutcome(after.state).totalPayout,40);
});
test('React reducer debits wager exactly once and persists active round within guest save',()=>{
 let game=freshGame();game.casinoChips=100;
 let state={game,ready:true,notice:''};
 state=transition(state,{type:'blackjackStart',wager:20,deck:deck(card(8),card(3),card(8,1),card(7),card(2),card(3,1))});
 assert.equal(state.game.casinoChips,80);
 assert.equal(state.blackjack.hands.length,1);
 assert.equal(validBlackjackState(state.game.casinoRound),true);
 const loaded=parseGuestSave(serializeGuestSave(state.game));
 assert.equal(validBlackjackState(loaded.casinoRound),true);
 state=transition(state,{type:'blackjackSplit'});
 assert.equal(state.game.casinoChips,60);assert.equal(state.blackjack.hands.length,2);
});
test('insufficient chips block double/split with no debit or deck mutation',()=>{
 const g=freshGame();g.casinoChips=20;
 const first=transition({game:g,ready:true,notice:''},{type:'blackjackStart',wager:20,deck:deck(card(8),card(3),card(8,1),card(7),card(2),card(3,1))});
 const second=transition(first,{type:'blackjackSplit'});
 assert.equal(second.game.casinoChips,0);
 assert.deepEqual(second.blackjack,first.blackjack);
});
