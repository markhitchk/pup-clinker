import {assert,assertEquals,assertRejects} from "jsr:@std/assert@1";
import {applyBetaBlackjack} from "../_shared/beta-blackjack.ts";
const fixedSecret="beta-blackjack-unit-test-only-server-secret-never-client";
const seed=()=>Deno.env.set("PUPEYE_CASINO_SEED",fixedSecret);
const original=()=>({
 format:"puppy-clicker-cloud-save",version:1,stores:{
 puppy_clicker_save:{values:{casino_chips_v7:5000,puppy_style:"classic",treats:123},types:{casino_chips_v7:"long"}},
 puppy_seasonal_v1:{values:{claimed:1},types:{claimed:"int"}}}
});
const get=(s:any)=>s.stores.puppy_clicker_save.values;
Deno.test("Blackjack shoe is private, signed wager debits once and Stand settles at most once",async()=>{
 seed();
 const initial=original();
 let after=await applyBetaBlackjack(initial,{type:"blackjackStart",wager:100},1793041800000);
 // A natural Blackjack can settle immediately on deal. In that case
 // the wallet includes the one-and-only legitimate payout already.
 const startedRound=Boolean(get(after).beta_casino_round_v1);
 if(startedRound)assertEquals(get(after).casino_chips_v7,4900);
 else{
  const outcome=JSON.parse(get(after).beta_last_blackjack_result_v1).outcome;
  assertEquals(get(after).casino_chips_v7,4900+outcome.totalPayout);
 }
 assertEquals(get(initial).casino_chips_v7,5000);
 assertEquals((after.stores as any).puppy_seasonal_v1,initial.stores.puppy_seasonal_v1);
 if(get(after).beta_casino_round_v1){
  let round=JSON.parse(get(after).beta_casino_round_v1);
  assertEquals(round.dealerCards.length,1);
  assert(!JSON.stringify(round).includes("remainingDeck"));
  await assertRejects(()=>applyBetaBlackjack(after,{type:"blackjackStart",wager:100},1793041800010),Error,"CASINO_ROUND_ALREADY_ACTIVE");
  let steps=0;
  while(get(after).beta_casino_round_v1&&steps++<6){
   after=await applyBetaBlackjack(after,{type:"blackjackStand"},1793041800500+steps);
   round=get(after).beta_casino_round_v1?JSON.parse(get(after).beta_casino_round_v1):null;
  }
 }
 assertEquals(get(after).beta_casino_round_v1,"");
 const finished=JSON.parse(get(after).beta_last_blackjack_result_v1);
 assert(finished.outcome.totalPayout>=0);
 assert(finished.state.complete);
 await assertRejects(()=>applyBetaBlackjack(after,{type:"blackjackStand"},1793041801000),Error,"BLACKJACK_ROUND_NOT_FOUND");
});
Deno.test("Blackjack refuses forged payout actions and unaffordable wagers",async()=>{
 seed();
 await assertRejects(()=>applyBetaBlackjack(original(),{type:"blackjackStart",wager:10000},1793041800000),Error,"BLACKJACK_INSUFFICIENT_CHIPS");
 await assertRejects(()=>applyBetaBlackjack(original(),{type:"blackjackHit"},1793041800000),Error,"BLACKJACK_ROUND_NOT_FOUND");
});
