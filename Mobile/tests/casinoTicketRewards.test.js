import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {freshGame} from '../src/engine/game.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
import {initialState,transition} from '../src/state/reducer.js';
import {
 deterministicCasinoTicketRoll,casinoTicketChance,casinoTicketRarity,
 nextGuestCasinoRoundId,applyGuestCasinoTicketReward
} from '../src/engine/casinoTicketRewards.js';

test('Kotlin SHA-256 first-63-bit rolls agree with Node crypto for both purposes',()=>{
 for(const id of ['round_award_0044','round_award_0009','round_stability01','guest_000000000001']){
  for(const [purpose,bound] of [['ticket-drop',10000],['ticket-rarity',100]]){
   const hash=createHash('sha256').update('puppy-casino-reward-v2:'+purpose+':'+id).digest();
   const expected=Number((hash.readBigUInt64BE(0)&0x7fffffffffffffffn)%BigInt(bound));
   assert.equal(deterministicCasinoTicketRoll(id,purpose,bound),expected,id+' '+purpose);
  }
 }
});
test('Kotlin published profit bands and cumulative rarity weights are retained',()=>{
 const cases=[[100,100,0],[100,150,25],[100,200,50],[100,500,100],[100,2000,200],[100,10000,500]];
 for(const [w,p,chance] of cases)assert.equal(casinoTicketChance(w,p),chance);
 assert.ok(['COMMON','UNCOMMON','RARE','EPIC','LEGENDARY'].includes(casinoTicketRarity('round_award_0044')));
});
test('Kotlin golden winning round receives a single local bonus ticket',()=>{
 const g=freshGame(),today=20000,id='round_award_0044';
 const first=applyGuestCasinoTicketReward(g,{roundId:id,wager:100,payout:10000},today);
 assert.equal(first.status,'AWARDED');
 assert.equal(g.totalTicketsFound,1);
 assert.equal(g.ticketInventory[first.rarity],1);
 const repeated=applyGuestCasinoTicketReward(g,{roundId:id,wager:100,payout:10000},today);
 assert.equal(repeated.status,'ALREADY_EVALUATED');
 assert.equal(g.totalTicketsFound,1);
 const saved=parseGuestSave(serializeGuestSave(g));
 assert.equal(applyGuestCasinoTicketReward(saved,{roundId:id,wager:100,payout:10000},today).status,'ALREADY_EVALUATED');
 assert.equal(saved.ticketInventory[first.rarity],1);
});
test('Kotlin daily cap, new day and loss consumption rules',()=>{
 const g=freshGame(),today=20000;
 const winning={roundId:'round_award_0044',wager:100,payout:10000};
 assert.equal(applyGuestCasinoTicketReward(g,winning,today).status,'AWARDED');
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:'round_award_0001',wager:100,payout:10000},today).status,'DAILY_CAP_REACHED');
 assert.equal(g.totalTicketsFound,1);
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:'round_award_0001',wager:100,payout:10000},today+1).status,'ALREADY_EVALUATED');
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:'round_award_0034',wager:100,payout:10000},today+1).status,'AWARDED');
 assert.equal(g.totalTicketsFound,2);
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:'round_loss0001',wager:100,payout:0},today+1).status,'NOT_ELIGIBLE');
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:'round_loss0001',wager:100,payout:0},today+1).status,'ALREADY_EVALUATED');
});
test('Kotlin inventory 9999 cap consumes a winning event without giving currency',()=>{
 const g=freshGame(),id='round_award_0009',rarity=casinoTicketRarity(id);
 g.ticketInventory[rarity]=9999;
 assert.equal(applyGuestCasinoTicketReward(g,{roundId:id,wager:100,payout:10000},20000).status,'INVENTORY_CAP_REACHED');
 assert.equal(g.ticketInventory[rarity],9999);
 assert.equal(g.totalTicketsFound,0);
});
test('unique persisted casino round IDs and once-only guest slot settlement',()=>{
 const g=freshGame();
 assert.equal(nextGuestCasinoRoundId(g),'guest_000000000001');
 assert.equal(nextGuestCasinoRoundId(g),'guest_000000000002');
 g.casinoChips=100;
 let s={...initialState(),game:g,ready:true};
 s=transition(s,{type:'slotsStart',wager:20,rolls:[99,99,99],now:1000});
 assert.ok(s.game.pendingSlots?.roundId);
 assert.equal(s.game.casinoChips,80);
 const roundId=s.game.pendingSlots.roundId;
 s=transition(s,{type:'slotsReveal',now:2500});
 assert.equal(s.game.pendingSlots,null);
 assert.equal(s.game.casinoChips,10080);
 assert.ok(s.game.casinoTicketLedger.evaluatedRoundIds.includes(roundId));
 const n=s.game.casinoChips;
 s=transition(s,{type:'slotsReveal',now:3000});
 assert.equal(s.game.casinoChips,n);
});
test('all remaining staged games retain their guest round IDs after restart',()=>{
 const cases=[
  ['scratchStart',{wager:20,roll:0},'pendingScratch'],
  ['rouletteStart',{wager:20,bet:{type:'RED'},winningNumber:1},'pendingRoulette'],
  ['plinkoStart',{wager:20,pathRight:Array(8).fill(false),bounceJitterPermille:Array(8).fill(0)},'pendingPlinko'],
  ['wheelStart',{wager:20,roll:50},'pendingWheel']
 ];
 for(const [type,args,key] of cases){
  const g=freshGame();g.casinoChips=100;
  const s=transition({...initialState(),game:g,ready:true},{type,...args,now:1000});
  const round=s.game[key];
  assert.ok(round?.roundId,type);
  const restored=parseGuestSave(serializeGuestSave(s.game));
  assert.equal(restored[key]?.roundId,round.roundId,type);
 }
});
