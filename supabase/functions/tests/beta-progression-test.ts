import {assert,assertEquals,assertRejects} from "jsr:@std/assert@1";
import {MAX_PLAYER_XP,MAX_PLAYER_LEVEL,MAX_TAP_REWARD,MAX_UPGRADES_PER_ITEM,xpLevel} from "../_shared/beta-progression.ts";
import {applyBetaLiveGameplay} from "../_shared/beta-live-gameplay.ts";
import {applyBetaEconomy} from "../_shared/beta-economy.ts";

const NOW=1793041485658;
const build=()=>({format:"puppy-clicker-cloud-save",version:1,stores:{
 puppy_clicker_save:{values:{
  puppy_style:"classic",treats:9_000_000_010_023_864,lifetime_treats:9_000_000_010_023_864,
  bones_v7:9_000_000_000_000_016,pup_coins_v7:9_000_000_000_000_018,
  casino_chips_v7:8_999_999_999_999_690,
  total_taps:9_000_000_058,legitimate_taps_v7:9_000_000_058,
  energy:70,happiness:80,fullness:90,cleanliness_v5:90,bond_v5:60,
  upgrade_better_treats:999,upgrade_legendary_snacks:999,
  player_xp_v1:886
 },types:{player_xp_v1:"long"}},
 puppy_seasonal_v1:{values:{event_progress:123},types:{}}
}});
const values=(s:any)=>s.stores.puppy_clicker_save.values;
Deno.test("production save with quadrillion currency receives reasonable XP and bounded tap payouts",()=>{
 const existing=build(),previous=values(existing);
 const updated=applyBetaLiveGameplay(existing,{type:"tap"},NOW),next=values(updated);
 assertEquals(next.player_xp_v1>=887,true);
 assertEquals(xpLevel(886),6);
 assertEquals(xpLevel(MAX_PLAYER_XP),MAX_PLAYER_LEVEL);
 assert(next.treats-previous.treats>0);
 assert(next.treats-previous.treats<=MAX_TAP_REWARD);
 assertEquals(next.bones_v7,previous.bones_v7);
 assertEquals(next.casino_chips_v7,previous.casino_chips_v7);
 assertEquals(next.upgrade_better_treats,999);
 assertEquals(next.upgrade_legendary_snacks,999);
 assertEquals(values(existing).treats,previous.treats);
 assertEquals((updated.stores as any).puppy_seasonal_v1,existing.stores.puppy_seasonal_v1);
});
Deno.test("new upgrades above fair cap are not sold but existing inventory is preserved",async()=>{
 const old=build();const before=values(old).treats;
 await assertRejects(()=>applyBetaEconomy(old,{type:"purchase",upgradeId:"better_treats"},NOW),Error,"UPGRADE_MAXED");
 assertEquals(values(old).upgrade_better_treats,999);
 assertEquals(values(old).treats,before);
 assertEquals(MAX_UPGRADES_PER_ITEM,100);
});
Deno.test("overflowed legacy XP is archived, capped and not used to derive millions of levels",()=>{
 const old=build();
 values(old).player_xp_v1=9_000_000_010_023_864;
 const updated=applyBetaLiveGameplay(old,{type:"tap"},NOW);
 assertEquals(values(updated).player_xp_legacy_backup_v1,9_000_000_010_023_864);
 assertEquals(values(updated).player_xp_v1,MAX_PLAYER_XP);
 assertEquals(xpLevel(values(updated).player_xp_v1),100);
});
