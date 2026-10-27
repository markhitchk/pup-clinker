import {awardBetaXp,rollBetaDay,settleBetaAchievements} from "./beta-economy.ts";
import {MAX_UPGRADES_PER_ITEM,cappedTapReward,cappedActiveBonus} from "./beta-progression.ts";
/**
 * Server-authoritative subset of the Kotlin V6 economy. The client submits
 * intent only. Never accept client-provided balances, RNG, XP or save data.
 * All unrecognized Kotlin preference fields and seasonal stores are retained.
 */
type Dict=Record<string,unknown>;
type LiveIntent={type:string;kind?:string;id?:string};
const MAX=Number.MAX_SAFE_INTEGER;
const upgrades:[string,number,"CLICK"|"AUTO"][]=[
 ["better_treats",1,"CLICK"],["chew_toy",1,"AUTO"],["golden_bowl",5,"CLICK"],
 ["playmate",5,"AUTO"],["lucky_collar",3,"CLICK"],["training_whistle",5,"AUTO"],
 ["puppy_power",25,"CLICK"],["dog_park_crew",25,"AUTO"],["treat_factory",100,"AUTO"],
 ["legendary_snacks",100,"CLICK"],
];
function object(value:unknown):Dict {
 if(!value||typeof value!=="object"||Array.isArray(value))throw new Error("INVALID_KOTLIN_SAVE");
 return value as Dict;
}
function int(values:Dict,key:string,fallback=0):number{
 const v=values[key]??fallback;
 if(typeof v!=="number"||!Number.isSafeInteger(v)||v<0)throw new Error("UNSAFE_KOTLIN_NUMBER");
 return v;
}
function add(a:number,b:number){return Math.min(MAX,a+b);}
function floor100(value:number){return Math.min(100,Math.max(0,value));}
function set(values:Dict,types:Dict,key:string,value:string|number|boolean|string[],type?:string){
 values[key]=value;
 types[key]=type??(typeof value==="number"?"long":typeof value==="boolean"?"boolean":Array.isArray(value)?"string_set":"string");
}
function setLong(values:Dict,types:Dict,key:string,value:number){set(values,types,key,value,"long");}
function setInt(values:Dict,types:Dict,key:string,value:number){set(values,types,key,value,"int");}
function careWrite(v:Dict,t:Dict,now:number){
 const id=String(v.puppy_style??"classic");
 if(!/^[a-z0-9_]{1,36}$/.test(id))throw new Error("INVALID_PUPPY_STYLE");
 for(const field of ["happiness","fullness","energy","cleanliness","bond"]){
  const global=field==="cleanliness"?"cleanliness_v5":field==="bond"?"bond_v5":field;
  setInt(v,t,"care_profile_v1_"+id+"_"+field,int(v,global,field==="bond"?10:100));
 }
 setLong(v,t,"care_profile_v1_"+id+"_updated_at",now);
}
export function applyBetaLiveGameplay(raw:unknown,intent:LiveIntent,now=Date.now()):Dict{
 const source=object(raw);
 if(source.format!=="puppy-clicker-cloud-save"||source.version!==1)throw new Error("UNSUPPORTED_KOTLIN_SAVE");
 const stores=object(source.stores),main=object(stores.puppy_clicker_save);
 const original=object(main.values),originalTypes=object(main.types);
 const values:Dict={...original},types:Dict={...originalTypes};
 const result:Dict={...source,stores:{...stores,puppy_clicker_save:{...main,values,types}},savedAtEpochMs:now};
 const kind=intent?.kind;
 rollBetaDay(values,types,now);
 if(intent?.type==="tap"){
  const last=int(values,"beta_last_tap_at_v1",0);
  if(now-last<65)throw new Error("PUP_TAP_RATE_LIMIT");
  if(int(values,"fair_play_cooldown_until",0)>now)throw new Error("PUPEYE_COOLDOWN");
  if(int(values,"energy",100)<=10)throw new Error("PUP_NEEDS_REST");
  const total=add(int(values,"total_taps"),1);
  const legitimate=add(int(values,"legitimate_taps_v7",int(values,"total_taps")),1);
  const tapSkill=int(values,"prestige_skill_tap_training_v6");
  const autoSkill=int(values,"prestige_skill_auto_training_v6");
  let clickPower=1,activeBonus=0;
  for(const [id,amount,effect] of upgrades){
   const owned=int(values,"upgrade_"+id);
   // Existing high counts remain in cloud history, but only a bounded
   // number contributes to new Treat rewards.
   const effective=Math.min(MAX_UPGRADES_PER_ITEM,owned);
   if(effect==="CLICK")clickPower+=(amount+tapSkill)*effective;
   else activeBonus+=(amount+autoSkill)*effective;
  }
  const wellness=Math.floor((int(values,"happiness",100)+int(values,"fullness",100)+int(values,"energy",100)+int(values,"cleanliness_v5",100))/4);
  const bonus=wellness>=85&&int(values,"bond_v5",10)>=50?2:wellness>=70?1:0;
  const payout=cappedTapReward(clickPower+bonus+(legitimate%10===0?cappedActiveBonus(activeBonus):0));
  if(!Number.isSafeInteger(payout)||payout<1)throw new Error("INVALID_TAP_REWARD");
  setLong(values,types,"treats",add(int(values,"treats"),payout));
  setLong(values,types,"lifetime_treats",add(int(values,"lifetime_treats"),payout));
  setLong(values,types,"total_taps",total);
  setLong(values,types,"legitimate_taps_v7",legitimate);
  if(legitimate%20===0)setLong(values,types,"bones_v7",add(int(values,"bones_v7"),1));
  if(legitimate%250===0)setLong(values,types,"pup_coins_v7",add(int(values,"pup_coins_v7"),1));
  if(total%12===0)setInt(values,types,"energy",Math.max(0,int(values,"energy",100)-1));
  if(total%15===0)setInt(values,types,"happiness",floor100(int(values,"happiness",100)+1));
  const combo=now-last<=900?Math.min(50,int(values,"beta_combo_v1")+1):1;
  setInt(values,types,"beta_combo_v1",combo);
  setInt(values,types,"best_combo",Math.max(int(values,"best_combo"),combo));
  setLong(values,types,"beta_last_tap_at_v1",now);
  // Kotlin PuppyEconomyV7: 1/500 drop, rarity roll [0,10000) thresholds.
  if(Math.floor(Math.random()*500)===0){
   const roll=Math.floor(Math.random()*10000);
   const rarity=roll<7800?"common":roll<9350?"uncommon":roll<9850?"rare":roll<9980?"epic":"legendary";
   const key="upgrade_ticket_"+rarity,owned=int(values,key);
   if(owned<9999){
    setInt(values,types,key,owned+1);
    setLong(values,types,"total_tickets_found",add(int(values,"total_tickets_found"),1));
   }
  }
  careWrite(values,types,now);
  awardBetaXp(values,types,"MANUAL_TAP");
 }else if(intent?.type==="care"){
  let treats=int(values,"treats"),bones=int(values,"bones_v7");
  let h=int(values,"happiness",100),f=int(values,"fullness",100),e=int(values,"energy",100),
      c=int(values,"cleanliness_v5",100),b=int(values,"bond_v5",10);
  if(kind==="feed"){
   if(treats<20||f>=100)throw new Error("CARE_ACTION_UNAVAILABLE");
   treats-=20;f=floor100(f+25);h=floor100(h+5);b=floor100(b+1);c=floor100(c-1);
  }else if(kind==="play"){
   if(bones<1||e<12||(h>=100&&b>=100))throw new Error("CARE_ACTION_UNAVAILABLE");
   bones--;h=floor100(h+20);f=floor100(f-4);e=floor100(e-12);c=floor100(c-3);b=floor100(b+3);
  }else if(kind==="wash"){
   if(treats<10||c>=100)throw new Error("CARE_ACTION_UNAVAILABLE");
   treats-=10;c=floor100(c+35);h=floor100(h+4);b=floor100(b+2);
  }else if(kind==="rest"){
   if(e>=100)throw new Error("CARE_ACTION_UNAVAILABLE");
   e=floor100(e+30);h=floor100(h+3);
  }else if(kind==="cuddle"){
   if(h>=100&&b>=100)throw new Error("CARE_ACTION_UNAVAILABLE");
   h=floor100(h+8);b=floor100(b+2);
  }else throw new Error("INVALID_CARE_ACTION");
  setLong(values,types,"treats",treats);
  setLong(values,types,"bones_v7",bones);
  setInt(values,types,"happiness",h);
  setInt(values,types,"fullness",f);
  setInt(values,types,"energy",e);
  setInt(values,types,"cleanliness_v5",c);
  setInt(values,types,"bond_v5",b);
  setLong(values,types,"care_actions_v5",add(int(values,"care_actions_v5"),1));
  careWrite(values,types,now);
  awardBetaXp(values,types,"CARE_ACTION");
 }else throw new Error("BETA_GAMEPLAY_NOT_PORTED");
 settleBetaAchievements(values,types);
 setLong(values,types,"last_seen",now);
 return result;
}
