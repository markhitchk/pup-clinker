import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1";
import { applyBetaLiveGameplay } from "../_shared/beta-live-gameplay.ts";

const source=()=>({
 format:"puppy-clicker-cloud-save",version:1,savedAtEpochMs:1,identity:{playerId:"test"},
 stores:{
  puppy_clicker_save:{
   values:{puppy_style:"classic",puppy_name:"Buddy",treats:1000,lifetime_treats:2000,
    bones_v7:1,pup_coins_v7:5,total_taps:0,legitimate_taps_v7:0,energy:70,
    happiness:80,fullness:40,cleanliness_v5:60,bond_v5:50,upgrade_better_treats:2,
    protected_entitlement:"original-preserved"},
   types:{puppy_style:"string",puppy_name:"string",treats:"long",lifetime_treats:"long",
    energy:"int",protected_entitlement:"string"}
  },
  puppy_seasonal_v1:{values:{eventProgress:51},types:{eventProgress:"int"}}
 }
});
const main=(data:any)=>data.stores.puppy_clicker_save.values;
Deno.test("signed beta tap recalculates reward from Kotlin upgrade counts",()=>{
 const before=source();const after=applyBetaLiveGameplay(before,{type:"tap"},1793041485658);
 const state=main(after);
 assertEquals(state.treats,1003); // 1 base + 2 upgrades, no wellness bonus
 assertEquals(state.lifetime_treats,2003);
 assertEquals(state.total_taps,1);
 assertEquals(state.legitimate_taps_v7,1);
 assertEquals(state.beta_last_tap_at_v1,1793041485658);
 assertEquals(state.care_profile_v1_classic_energy,70);
 assertEquals(state.protected_entitlement,"original-preserved");
 assertEquals((after.stores as any).puppy_seasonal_v1,before.stores.puppy_seasonal_v1);
 assertEquals(main(before).treats,1000,"does not mutate the input save");
 assertEquals(((after.stores as any).puppy_clicker_save as any).types.total_taps,"long");
});
Deno.test("beta care matches Kotlin feed costs and preserves unrelated protected fields",()=>{
 const before=source();const after=applyBetaLiveGameplay(before,{type:"care",kind:"feed"},1793041486000);
 assertEquals(main(after).treats,980);
 assertEquals(main(after).fullness,65);
 assertEquals(main(after).happiness,85);
 assertEquals(main(after).cleanliness_v5,59);
 assertEquals(main(after).bond_v5,51);
 assertEquals(main(after).care_actions_v5,1);
 assertEquals(main(after).protected_entitlement,"original-preserved");
});
Deno.test("rejects unknown gameplay, invalid formats and exhausted energy",()=>{
 assertThrows(()=>applyBetaLiveGameplay(source(),{type:"casino"},10000));
 assertThrows(()=>applyBetaLiveGameplay({...source(),version:2},{type:"tap"},10000));
 const tired=source();(main(tired) as any).energy=10;
 assertThrows(()=>applyBetaLiveGameplay(tired,{type:"tap"},1793041485658));
 const limited=source();(main(limited) as any).beta_last_tap_at_v1=1793041485600;
 assertThrows(()=>applyBetaLiveGameplay(limited,{type:"tap"},1793041485650));
});
