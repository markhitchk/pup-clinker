import {assert,assertEquals} from "jsr:@std/assert@1";
import {grantCasinoBonuses,canWheelUnlock,CASINO_PUPPY_POOL} from "../_shared/beta-casino-rewards.ts";
const BASE=1793040000000;
const values=()=>({unlocked_puppies:["classic","golden","poodle","spotty"],total_tickets_found:0});
Deno.test("Kotlin casino 100x profitable round grants one eligible puppy and persists both ledgers",async()=>{
 const v:any=values(),t:any={};
 const first=await grantCasinoBonuses(v,t,{roundId:"round_test_aaaaaaaaaa",wager:20,payout:2000},BASE);
 assert(first.awardedPuppy&&CASINO_PUPPY_POOL.includes(first.awardedPuppy as typeof CASINO_PUPPY_POOL[number]));
 assert(v.unlocked_puppies.includes(first.awardedPuppy));
 assertEquals(t.unlocked_puppies,"string_set");
 assertEquals(JSON.parse(v.casino_puppy_reward_ledger_v1).dailyPuppyUnlocks,1);
 assertEquals(JSON.parse(v.casino_ticket_reward_ledger_v1).evaluatedRoundIds,["round_test_aaaaaaaaaa"]);
 assertEquals(canWheelUnlock(v,BASE),false);
 const reapply=await grantCasinoBonuses(v,t,{roundId:"round_test_aaaaaaaaaa",wager:20,payout:2000},BASE);
 assertEquals(reapply.awardedPuppy,null);
 const next=await grantCasinoBonuses(v,t,{roundId:"round_test_bbbbbbbbbb",wager:20,payout:2000},BASE+1);
 assertEquals(next.awardedPuppy,null);
 assertEquals(JSON.parse(v.casino_puppy_reward_ledger_v1).dailyPuppyUnlocks,1);
});
Deno.test("Kotlin guaranteed wheel segment respects pool and daily cap",async()=>{
 const v:any=values(),t:any={};
 const reward=await grantCasinoBonuses(v,t,{
  roundId:"round_test_wheel_01",wager:100,payout:0,guaranteedPuppyId:CASINO_PUPPY_POOL[0]
 },BASE);
 assertEquals(reward.awardedPuppy,CASINO_PUPPY_POOL[0]);
 assertEquals(reward.awardedTicket,null);
 assertEquals(v.casino_chips_v7,undefined); // Rewards cannot change wallet.
 assertEquals(JSON.parse(v.casino_puppy_reward_ledger_v1).dailyPuppyUnlocks,1);
});
