import {assert,assertEquals,assertRejects} from "jsr:@std/assert@1";
import {applyBetaEconomy,newBetaKotlinSave} from "../_shared/beta-economy.ts";
import {applyBetaLiveGameplay} from "../_shared/beta-live-gameplay.ts";
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
 assertEquals((updated.stores as any).puppy_seasonal_v1,base.stores.puppy_seasonal_v1);
 assertEquals(data(base).upgrade_better_treats,0);
 assertEquals(data(updated).player_xp_v1,200); // first treat, snack stash, puppy pro and bone collector
 assertEquals(((updated.stores as any).puppy_clicker_save as any).types.treats,"long");
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

Deno.test("Gacha spends on the server, awards only Kotlin free pups and keeps protected IDs locked",async()=>{
 const before=make();
 const first=await applyBetaEconomy(before,{type:"gacha",payment:"TREATS"},NOW);
 const reward=JSON.parse(String(data(first).beta_last_gacha_result_v1));
 assert(["classic","golden","poodle","spotty","test_v1_crash_dummy","test_v2_crash_dummy"].includes(reward.puppyId));
 assertEquals(data(first).treats,17500);
 assertEquals(data(first).unlocked_puppies.includes("dev_pup"),false);
 assertEquals(data(first).unlocked_puppies.includes("v2_discord_pup"),false);
 assertEquals(data(first).player_xp_v1,300); // 100 unlock XP + 200 achievements from existing progression
 assertEquals(data(before).treats,20000);
 await assertRejects(()=>applyBetaEconomy(first,{type:"gacha",payment:"COMMON_TICKET"},NOW+200),Error,"GACHA_INSUFFICIENT_TICKET");
});
Deno.test("Gacha cannot mint protected puppy, and one owned puppy can be selected and renamed",async()=>{
 const first=await applyBetaEconomy(make(),{type:"gacha",payment:"TREATS"},NOW);
 const id=JSON.parse(String(data(first).beta_last_gacha_result_v1)).puppyId;
 const switched=await applyBetaEconomy(first,{type:"switch",id},NOW+1);
 assertEquals(data(switched).puppy_style,id);
 const renamed=await applyBetaEconomy(switched,{type:"rename",name:"Mochi Test"},NOW+2);
 assertEquals(data(renamed)["puppy_name_for_"+id],"Mochi Test");
 await assertRejects(()=>applyBetaEconomy(renamed,{type:"switch",id:"dev_pup"},NOW+3),Error,"PUPPY_LOCKED");
});

Deno.test("fresh Pup Account receives a Kotlin-restorable typed save and immediately earns live tap XP",()=>{
 const seed=newBetaKotlinSave(NOW);
 const main=(seed.stores as any).puppy_clicker_save;
 assertEquals(main.values.treats,0);
 assertEquals(main.types.puppy_style,"string");
 assertEquals(main.types.unlocked_puppies,"string_set");
 assertEquals(main.types.player_xp_v1,"long");
 assertEquals((seed.stores as any).puppy_seasonal_v1.types,{});
 for(const [key,value] of Object.entries(main.values))assert(typeof main.types[key]==="string",key);
 const tapped=applyBetaLiveGameplay(seed,{type:"tap"},NOW+1000);
 assertEquals((tapped.stores as any).puppy_clicker_save.values.total_taps,1);
 assertEquals((tapped.stores as any).puppy_clicker_save.values.player_xp_v1>=1,true);
 assertEquals(main.values.total_taps,0);
});
