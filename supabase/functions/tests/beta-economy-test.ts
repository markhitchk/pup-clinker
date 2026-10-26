import {assert,assertEquals,assertRejects} from "jsr:@std/assert@1";
import {applyBetaEconomy} from "../_shared/beta-economy.ts";
function make(){
 return {format:"puppy-clicker-cloud-save",version:1,savedAtEpochMs:1,
  stores:{puppy_clicker_save:{values:{
   puppy_style:"classic",treats:20000,lifetime_treats:20000,
   bones_v7:100,pup_coins_v7:3000,casino_chips_v7:4000,
   upgrade_better_treats:0,energy:70,happiness:80,fullness:90,
   cleanliness_v5:90,bond_v5:60,player_xp_v1:0,
   daily_day_v5:20400,daily_claim_day:0
  },types:{}},puppy_seasonal_v1:{values:{gifts:123},types:{gifts:"int"}}}};
}
function data(x:any){return x.stores.puppy_clicker_save.values;}
const NOW=1793040000000;
Deno.test("Kotlin upgrade purchase charges authoritative costs, retains seasonal save and awards earned XP",async()=>{
 const base=make(),updated=await applyBetaEconomy(base,{type:"purchase",upgradeId:"better_treats"},NOW);
 assertEquals(data(updated).treats,19965);
 assertEquals(data(updated).bones_v7,99);
 assertEquals(data(updated).upgrade_better_treats,1);
 assertEquals(data(updated).total_shop_purchases_v5,1);
 assertEquals(updated.stores.puppy_seasonal_v1,base.stores.puppy_seasonal_v1);
 assertEquals(data(base).upgrade_better_treats,0);
 assertEquals(data(updated).player_xp_v1,150); // first treat, snack stash, puppy pro achievements
 assertEquals((updated.stores.puppy_clicker_save as any).types.treats,"long");
});
Deno.test("Kotlin daily gift is once-only and advances streak without double credit",async()=>{
 const first=await applyBetaEconomy(make(),{type:"daily"},NOW);
 assertEquals(data(first).bones_v7,102);
 assertEquals(data(first).pup_coins_v7,3003);
 assertEquals(data(first).daily_streak_v5,1);
 await assertRejects(()=>applyBetaEconomy(first,{type:"daily"},NOW+1000),Error,"DAILY_ALREADY_CLAIMED");
});
Deno.test("Shop ticket and accessory purchases use live saved balances",async()=>{
 const t=await applyBetaEconomy(make(),{type:"ticket",rarity:"COMMON"},NOW);
 assertEquals(data(t).pup_coins_v7,2750);
 assertEquals(data(t).upgrade_ticket_common,1);
 const a=await applyBetaEconomy(t,{type:"accessory",name:"Bandana"},NOW+100);
 assertEquals(data(a).pup_coins_v7,2600);
 assertEquals(data(a).accessory,"Bandana");
 assertEquals(data(a).owned_accessories_v7,["None","Bandana"]);
 await assertRejects(()=>applyBetaEconomy(a,{type:"purchase",upgradeId:"unknown"},NOW),Error,"UNKNOWN_UPGRADE");
});
Deno.test("Casino rounds are server-drawn, debited once and settled once; caller RNG ignored",async()=>{
 const first=await applyBetaEconomy(make(),{type:"slotsStart",wager:100},NOW);
 const stake=data(first),r=JSON.parse(String(stake.beta_casino_round_v1));
 assertEquals(stake.casino_chips_v7,3900);
 assertEquals(r.wager,100);
 assert(r.roundId.length>8);
 assert(Array.isArray(r.rolls));
 await assertRejects(()=>applyBetaEconomy(first,{type:"slotsStart",wager:100},NOW+1),Error,"CASINO_ROUND_ALREADY_ACTIVE");
 const settled=await applyBetaEconomy(first,{type:"slotsReveal"},NOW+2000);
 assertEquals(data(settled).casino_chips_v7,3900+r.payout);
 assertEquals(data(settled).beta_casino_round_v1,"");
 assertEquals(JSON.parse(String(data(settled).beta_last_casino_result_v1)).roundId,r.roundId);
 await assertRejects(()=>applyBetaEconomy(settled,{type:"slotsReveal"},NOW+2001),Error,"CASINO_ROUND_NOT_FOUND");
});
Deno.test("Casino rejects client-controlled wager or invalid roulette and scratch bet",async()=>{
 await assertRejects(()=>applyBetaEconomy(make(),{type:"wheelStart",wager:21},NOW),Error,"INVALID_WAGER");
 await assertRejects(()=>applyBetaEconomy(make(),{type:"rouletteStart",wager:20,bet:{type:"STRAIGHT",number:99}},NOW),Error,"INVALID_ROULETTE_NUMBER");
 await assertRejects(()=>applyBetaEconomy(make(),{type:"scratchStart",wager:60},NOW),Error,"UNSUPPORTED_SCRATCH_CARD");
});
