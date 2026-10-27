import {MAX_PLAYER_XP,MAX_UPGRADES_PER_ITEM,MAX_REWARD_STREAK_DAYS,xpLevel,addBoundedXp,boundedXp} from "./beta-progression.ts";
import {grantCasinoBonuses,canWheelUnlock,unownedCasinoPuppies} from "./beta-casino-rewards.ts";
/**
 * Server-owned Puppy Clicker economy for signed beta intentions.
 * This is NOT the guest reducer: never trust prices, winnings, XP, randomness,
 * untrusted save JSON, or achievement completion supplied by the Android client.
 * Works on typed Kotlin SharedPreferences snapshots and preserves unknown keys.
 */
type Dict=Record<string,unknown>;
/** Fresh live account: an empty, Kotlin SecurePreferenceCodec-compatible save.
 * Only called for the owner's active, authenticated installation; not a
 * secondary Play companion. This seed is inserted once with a unique key.
 */
export function newBetaKotlinSave(now=Date.now()):Dict{
 const today=Math.floor(now/86400000);
 const values:Dict={
  puppy_name:"Buddy",puppy_style:"classic",unlocked_puppies:["classic","golden","poodle","spotty"],
  accessory:"None",owned_accessories_v7:["None"],
  treats:0,lifetime_treats:0,bones_v7:0,pup_coins_v7:0,casino_chips_v7:0,
  total_taps:0,legitimate_taps_v7:0,total_shop_purchases_v5:0,
  total_tickets_found:0,care_actions_v5:0,player_xp_v1:0,
  happiness:100,fullness:100,energy:100,cleanliness_v5:100,bond_v5:10,
  daily_day_v5:today,daily_tap_start_v5:0,daily_care_start_v5:0,daily_shop_start_v5:0,
  daily_streak_v5:0,daily_tasks_v5:[],achievement_rewarded_v1:[],
  prestige_count_v6:0,prestige_skill_points_v6:0,prestige_total_points_v6:0,
  park_active:false,park_ready_at:0,last_seen:now,
 };
 const types:Dict={};
 for(const [key,val] of Object.entries(values)){
  types[key]=Array.isArray(val)?"string_set":typeof val==="boolean"?"boolean":
   typeof val==="string"?"string":
   ["happiness","fullness","energy","cleanliness_v5","bond_v5","daily_streak_v5",
    "prestige_count_v6","prestige_skill_points_v6"].includes(key)?"int":"long";
 }
 return {format:"puppy-clicker-cloud-save",version:1,savedAtEpochMs:now,
  identity:{},stores:{
   puppy_clicker_save:{values,types},
   puppy_seasonal_v1:{values:{},types:{}},
  }};
}

export type BetaEconomyIntent={type:string;kind?:string;upgradeId?:string;rarity?:string;name?:string;skill?:string;goalId?:string;amount?:number;wager?:number;bet?:{type?:string;number?:number};id?:string;payment?:string};
const MAX=Number.MAX_SAFE_INTEGER;
const defs=[
 {id:"better_treats",power:1,base:35,type:"COOKIE",effect:"CLICK"},
 {id:"chew_toy",power:1,base:100,type:"COOKIE",effect:"AUTO"},
 {id:"golden_bowl",power:5,base:750,type:"COOKIE",effect:"CLICK"},
 {id:"playmate",power:5,base:1500,type:"COOKIE",effect:"AUTO"},
 {id:"lucky_collar",power:3,base:75,type:"TICKET",effect:"CLICK",rarity:"common",tickets:2},
 {id:"training_whistle",power:5,base:125,type:"TICKET",effect:"AUTO",rarity:"uncommon",tickets:2},
 {id:"puppy_power",power:25,base:250,type:"TICKET",effect:"CLICK",rarity:"rare",tickets:2},
 {id:"dog_park_crew",power:25,base:350,type:"TICKET",effect:"AUTO",rarity:"rare",tickets:3},
 {id:"treat_factory",power:100,base:600,type:"TICKET",effect:"AUTO",rarity:"epic",tickets:3},
 {id:"legendary_snacks",power:100,base:900,type:"TICKET",effect:"CLICK",rarity:"legendary",tickets:2},
] as const;
const rarities=["common","uncommon","rare","epic","legendary"];
/**
 * Kotlin PuppyCatalogV6 + assets/test/manifest.json. All other v1/v2
 * puppies are redeemOnly or protected and MUST NOT be minted by beta Gacha.
 */
const FREE_GACHA_PUPPIES=[
 {id:"classic",name:"Buddy"}, {id:"golden",name:"Sunny"},
 {id:"poodle",name:"Mochi"}, {id:"spotty",name:"Pepper"},
 {id:"test_v1_crash_dummy",name:"Crash Test Pup V1"},
 {id:"test_v2_crash_dummy",name:"Crash Test Pup V2"},
].sort((a,b)=>a.id.localeCompare(b.id,"en"));

const slotSymbols=[
 {emoji:"🍪",weight:30,multi:7},{emoji:"🎾",weight:25,multi:12},
 {emoji:"🐾",weight:20,multi:18},{emoji:"🎟️",weight:13,multi:35},
 {emoji:"🐶",weight:8,multi:100},{emoji:"⭐",weight:4,multi:500},
];
const scratchCards:{[wager:number]:{name:string;odds:number[]}}={
 20:{name:"Pup Scratch",odds:[60,22,12,5,1]},
 100:{name:"Bone Bonanza",odds:[58,14,26,1,1]},
 500:{name:"Lucky Fetch",odds:[55,20,23,1,1]},
 2500:{name:"Golden Paw",odds:[52,26,20,1,1]},
 10000:{name:"Casino Scratch",odds:[58,32,7,1,2]},
 25000:{name:"Ultra Pup",odds:[60,31,5,2,2]},
};
const prizes=[
 {id:"NO_PRIZE",multi:0,symbol:null},{id:"REFUND",multi:100,symbol:"🍪"},
 {id:"DOUBLE",multi:200,symbol:"🦴"},{id:"BIG_WIN",multi:500,symbol:"⭐"},
 {id:"JACKPOT",multi:2000,symbol:"💎"},
];
const wheelPrizes=[
 {id:"MISS",weight:38,multi:0},{id:"HALF",weight:25,multi:50},
 {id:"REFUND",weight:20,multi:100},{id:"DOUBLE",weight:10,multi:200},
 {id:"FIVE_X",weight:4,multi:500},{id:"TEN_X",weight:1,multi:1000},
 {id:"PUPPY_UNLOCK",weight:2,multi:0},
];
const RED=new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
const isObj=(a:unknown):a is Dict=>!!a&&typeof a==="object"&&!Array.isArray(a);
function requireObj(a:unknown):Dict{if(!isObj(a))throw Error("INVALID_KOTLIN_SAVE");return a;}
function num(v:Dict,k:string,fallback=0):number{
 const n=v[k]??fallback;
 if(typeof n!=="number"||!Number.isSafeInteger(n)||n<0)throw Error("UNSAFE_SAVED_COUNTER_"+k);
 return n;
}
function inc(a:number,b:number):number{return Math.min(MAX,a+b);}
function cap100(a:number){return Math.max(0,Math.min(100,a));}
function expect(b:unknown,label:string):asserts b {if(!b)throw Error(label);}
function write(v:Dict,t:Dict,k:string,x:number|string|boolean|string[],type?:string){
 v[k]=x;t[k]=type??(Array.isArray(x)?"string_set":typeof x==="number"?"long":typeof x==="boolean"?"boolean":"string");
}
const setLong=(v:Dict,t:Dict,k:string,x:number)=>write(v,t,k,x,"long");
const setInt=(v:Dict,t:Dict,k:string,x:number)=>write(v,t,k,x,"int");
const addLong=(v:Dict,t:Dict,k:string,amount:number)=>setLong(v,t,k,inc(num(v,k),amount));
const earned=(v:Dict,t:Dict,amount:number)=>{addLong(v,t,"treats",amount);addLong(v,t,"lifetime_treats",amount);};
const uuid=()=>crypto.randomUUID();
function secureInt(limit:number):number {
 expect(Number.isSafeInteger(limit)&&limit>0&&limit<=0x100000000,"RNG_RANGE");
 const domain=0x100000000,threshold=domain-(domain%limit);
 const bytes=new Uint32Array(1);
 do{crypto.getRandomValues(bytes);}while(bytes[0]>=threshold);
 return bytes[0]%limit;
}
function own(v:Dict,k:string):string[]{const x=v[k];return Array.isArray(x)?x.filter(y=>typeof y==="string") as string[]:[];}
const level=(v:Dict)=>xpLevel(num(v,"player_xp_v1",num(v,"lifetime_treats")));
const careScore=(v:Dict)=>Math.floor((num(v,"happiness",100)+num(v,"fullness",100)+num(v,"energy",100)+num(v,"cleanliness_v5",100))/4);
const ticketKey=(r:string)=>"upgrade_ticket_"+r;
const skillKey=(r:string)=>"prestige_skill_"+r.toLowerCase()+"_v6";
function careWrite(v:Dict,t:Dict,now:number){
 const style=String(v.puppy_style??"classic");
 expect(/^[a-z0-9_]{1,40}$/.test(style),"INVALID_PUPPY_STYLE");
 for(const [k,global] of [["energy","energy"],["happiness","happiness"],["fullness","fullness"],["cleanliness","cleanliness_v5"],["bond","bond_v5"]]){
  setInt(v,t,"care_profile_v1_"+style+"_"+k,num(v,global,k==="bond"?10:100));
 }
 setLong(v,t,"care_profile_v1_"+style+"_updated_at",now);
 let bonds:Dict={};try{const j=JSON.parse(String(v.bond_by_puppy_v1??"{}"));if(isObj(j))bonds=j;}catch{/* leave corrupt legacy record untouched otherwise */}
 bonds[style]=num(v,"bond_v5",10);write(v,t,"bond_by_puppy_v1",JSON.stringify(bonds),"string");
}
function awardXp(v:Dict,t:Dict,n:number){
 const old=num(v,"player_xp_v1",num(v,"lifetime_treats"));
 // Preserve historic overflowed XP for support/recovery. Do not overwrite
 // existing Treats or reset anything in the cloud account.
 if(old>MAX_PLAYER_XP&&v.player_xp_legacy_backup_v1===undefined)
  setLong(v,t,"player_xp_legacy_backup_v1",old);
 setLong(v,t,"player_xp_v1",addBoundedXp(old,n));
}
export function awardBetaXp(v:Dict,t:Dict,event:"MANUAL_TAP"|"CARE_ACTION"){
 awardXp(v,t,event==="MANUAL_TAP"?1:5);
}
export function rollBetaDay(v:Dict,t:Dict,now:number){rollDay(v,t,day(now));}
export function settleBetaAchievements(v:Dict,t:Dict){
 // The Kotlin V6 achievements are fixed/immutable and pay once per id.
 const original=own(v,"achievement_rewarded_v1"),awarded=new Set(original);
 const completed=()=>{
  const tapSkill=num(v,skillKey("tap_training"));
  const power=1+defs.filter(u=>u.effect==="CLICK").reduce((sum,u)=>sum+(u.power+tapSkill)*num(v,"upgrade_"+u.id),0);
  return [
   ["first_treat",num(v,"lifetime_treats")>=1],
   ["snack_stash",num(v,"lifetime_treats")>=100],
   ["puppy_pro",num(v,"lifetime_treats")>=1000],
   ["combo_hero",num(v,"best_combo")>=20],
   ["ticket_hunter",num(v,"total_tickets_found")>=1],
   ["big_taps",power>=25],
   ["auto_pup",num(v,"bones_v7")>=50],
   ["happy_home",Math.min(num(v,"happiness",100),num(v,"fullness",100),num(v,"energy",100),num(v,"cleanliness_v5",100))>=90],
   ["level_ten",level(v)>=10],
  ] as const;
 };
 for(let pass=0;pass<10;pass++){
  let found=false;
  for(const [id,ok] of completed())if(ok&&!awarded.has(id)){awarded.add(id);awardXp(v,t,50);found=true;}
  if(!found)break;
 }
 if(awarded.size!==original.length)write(v,t,"achievement_rewarded_v1",[...awarded],"string_set");
}
function day(now:number,offsetMin=0){return Math.floor((now-offsetMin*60000)/86400000);}
function rollDay(v:Dict,t:Dict,today:number){
 if(num(v,"daily_day_v5",0)===today)return;
 setLong(v,t,"daily_day_v5",today);
 setLong(v,t,"daily_tap_start_v5",num(v,"total_taps"));
 setLong(v,t,"daily_care_start_v5",num(v,"care_actions_v5"));
 setLong(v,t,"daily_shop_start_v5",num(v,"total_shop_purchases_v5"));
 write(v,t,"daily_tasks_v5",[],"string_set");
}
function fallbackGoal(dayOfMonth:number,id:string){
 const taps=[50,75,90,100][(dayOfMonth-1)%4],cares=[3,4,5][(dayOfMonth-1)%3];
 return ({
  tap_time:{metric:"taps",target:taps,reward:taps*4},
  good_care:{metric:"care",target:cares,reward:150+cares*40},
  shop_visit:{metric:"shop",target:1,reward:400},
  wellness:{metric:"wellness",target:85,reward:420},
 } as Record<string,{metric:string;target:number;reward:number}>)[id];
}
type ServerGoal={metric:string;target:number;reward:number};
export async function getAuthoritativeGoal(goalId:string,date:Date):Promise<ServerGoal|undefined>{
 expect(/^[a-z0-9_-]{1,48}$/.test(goalId),"INVALID_GOAL_ID");
 const month=date.toISOString().slice(0,7),dayOfMonth=date.getUTCDate();
 try{
  const controller=new AbortController();
  const timeout=setTimeout(()=>controller.abort(),4000);
  let response:Response;
  try{response=await fetch("https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/rewards/"+month+".json",{signal:controller.signal});}
  finally{clearTimeout(timeout);}
  if(!response.ok||Number(response.headers.get("content-length")||0)>512*1024)throw Error("SCHEDULE_UNAVAILABLE");
  const text=await response.text();
  if(text.length>512*1024)throw Error("SCHEDULE_TOO_BIG");
  const j=JSON.parse(text);
  if(j.schemaVersion!==1||j.month!==month)throw Error("SCHEDULE_FORMAT");
  const list=j.days?.[String(dayOfMonth)];
  if(!Array.isArray(list))throw Error("NO_SCHEDULE");
  const item=list.slice(0,12).find((x:unknown)=>isObj(x)&&x.id===goalId&&x.enabled!==false);
  if(!isObj(item))return undefined; // A published schedule may deliberately omit this goal.
  const metric=String(item.type||"").toLowerCase();
  const target=typeof item.target==="number"?item.target:NaN;
  const reward=typeof item.rewardTreats==="number"?item.rewardTreats:NaN;
  if(!["taps","care","shop","wellness","bond","tickets"].includes(metric)||
     !Number.isSafeInteger(target)||target<1||target>1_000_000||
     !Number.isSafeInteger(reward)||reward<1||reward>100_000||
     (["wellness","bond"].includes(metric)&&target>100))throw Error("INVALID_GOAL_DEFINITION");
  return {metric,target,reward};
 }catch{return fallbackGoal(dayOfMonth,goalId);}
}
function ticketTotal(v:Dict){return rarities.reduce((n,k)=>n+num(v,ticketKey(k)),0);}
function priceUpgrade(u:typeof defs[number],owned:number,shopper:number):number{
 // Kotlin PuppyEconomyV7.growCost: exact ceil at every ownership level,
 // saturating at Long.MAX_VALUE before applying the Smart Shopper discount.
 const longMax=(1n<<63n)-1n;
 const rate=BigInt(u.type==="COOKIE"?155:135);
 let grown=BigInt(u.base);
 for(let i=0;i<owned;i++){
  if(grown>(longMax-99n)/rate){grown=longMax;break;}
  grown=(grown*rate+99n)/100n;
 }
 const discount=BigInt(100-Math.min(5,shopper)*5);
 const total=grown>longMax/discount?longMax:grown*discount/100n;
 const price=total>0n?total:1n;
 // The signed React bridge cannot faithfully serialize Kotlin Long
 // balances above Number.MAX_SAFE_INTEGER: never round them into a cheap buy.
 return price>BigInt(MAX)?MAX:Number(price);
}
function shopUpgrade(v:Dict,t:Dict,intent:BetaEconomyIntent){
 const u=defs.find(x=>x.id===intent.upgradeId);expect(u,"UNKNOWN_UPGRADE");
 const owned=num(v,"upgrade_"+u.id),treats=num(v,"treats"),bones=num(v,"bones_v7");
 expect(owned<MAX_UPGRADES_PER_ITEM,"UPGRADE_MAXED");
 const treatPrice=priceUpgrade(u,owned,Math.min(5,num(v,skillKey("smart_shopper"))));
 const boneBase=u.power>=100?12:u.power>=25?7:u.power>=5?3:1;
 const bonePrice=boneBase*(u.type==="TICKET"?2:1);
 expect(treats>=treatPrice&&bones>=bonePrice,"UPGRADE_NOT_AFFORDABLE");
 if(u.type==="TICKET"){
  const sense=Math.min(5,num(v,skillKey("ticket_sense")));
  const discounted=Math.max(1,('tickets' in u?u.tickets:1)+Math.min(2,Math.floor(owned/3))-(sense>=2?1:0)-(sense>=4?1:0));
  const key=ticketKey('rarity' in u?u.rarity:"");
  expect(num(v,key)>=discounted,"UPGRADE_NEEDS_TICKETS");
  setInt(v,t,key,num(v,key)-discounted);
 }
 setLong(v,t,"treats",treats-treatPrice);setLong(v,t,"bones_v7",bones-bonePrice);
 setInt(v,t,"upgrade_"+u.id,owned+1);
 addLong(v,t,"total_shop_purchases_v5",1);
 setInt(v,t,"happiness",cap100(num(v,"happiness",100)+2));
}
function ticketShop(v:Dict,t:Dict,intent:BetaEconomyIntent){
 const rarity=String(intent.rarity||"").toLowerCase();
 expect(rarities.includes(rarity),"UNKNOWN_RARITY");
 const base=({common:250,uncommon:750,rare:2500,epic:7500,legendary:25000} as Record<string,number>)[rarity];
 const purchases=num(v,"ticket_shop_purchase_"+rarity+"_v7");
 const price=Math.floor(base*Math.min(24,4+purchases)/4);
 expect(num(v,"pup_coins_v7")>=price,"TICKET_NOT_AFFORDABLE");
 const key=ticketKey(rarity),owned=num(v,key);
 expect(owned<9999,"TICKET_CAP");
 setLong(v,t,"pup_coins_v7",num(v,"pup_coins_v7")-price);
 setInt(v,t,key,owned+1);setInt(v,t,"ticket_shop_purchase_"+rarity+"_v7",purchases+1);
}
function accessories(v:Dict,t:Dict,intent:BetaEconomyIntent){
 const name=String(intent.name||""),owned=new Set([...own(v,"owned_accessories_v7"),"None"]);
 const prices:Record<string,number>={Bandana:150,Bow:250,Crown:1000};
 expect(name==="None"||Object.hasOwn(prices,name),"UNKNOWN_ACCESSORY");
 if(intent.type==="accessory"&&!owned.has(name)){
  const cost=prices[name];expect(num(v,"pup_coins_v7")>=cost,"ACCESSORY_NOT_AFFORDABLE");
  setLong(v,t,"pup_coins_v7",num(v,"pup_coins_v7")-cost);owned.add(name);
  write(v,t,"owned_accessories_v7",[...owned],"string_set");
 }
 expect(owned.has(name),"ACCESSORY_NOT_OWNED");
 write(v,t,"accessory",name,"string");
}
function stakeAllowed(v:Dict,wager:unknown):asserts wager is number {
 expect(typeof wager==="number"&&Number.isSafeInteger(wager)&&wager>=20&&wager<=100000&&wager%10===0,"INVALID_WAGER");
 expect(num(v,"casino_chips_v7")>=wager,"NOT_ENOUGH_CASINO_CHIPS");
}
const rollSymbol=(roll:number)=>{let count=0;for(const x of slotSymbols){count+=x.weight;if(roll<count)return x;}throw Error("BAD_SLOT_WEIGHTS");};
function newCasinoRound(type:string,intent:BetaEconomyIntent,now:number,v:Dict):Dict{
 const wager=intent.wager as number;
 const base={roundId:uuid(),wager,createdAt:now};
 if(type==="slotsStart"){
  const rolls=[secureInt(100),secureInt(100),secureInt(100)];
  const symbols=rolls.map(r=>rollSymbol(r)),unique=new Set(symbols.map(s=>s.emoji)).size;
  return {...base,version:1,game:"SLOTS",rolls,symbols:symbols.map(s=>s.emoji),
   payout:unique===1?wager*symbols[0].multi:unique===2?Math.floor(wager/2):0,
   winKind:unique===1?"TRIPLE":unique===2?"PAIR":"LOSS"};
 }
 if(type==="rouletteStart"){
  const bet=isObj(intent.bet)?intent.bet:null,btype=String(bet?.type||"");
  expect(["RED","BLACK","EVEN","ODD","LOW","HIGH","STRAIGHT"].includes(btype),"INVALID_ROULETTE_BET");
  if(btype==="STRAIGHT")expect(Number.isSafeInteger(bet?.number)&&Number(bet?.number)>=0&&Number(bet?.number)<=36,"INVALID_ROULETTE_NUMBER");
  const betObj={type:btype,...(btype==="STRAIGHT"?{number:bet?.number}:{})};
  const winningNumber=secureInt(37),color=winningNumber===0?"GREEN":RED.has(winningNumber)?"RED":"BLACK";
  const won=btype==="RED"?color==="RED":btype==="BLACK"?color==="BLACK":
   btype==="EVEN"?winningNumber!==0&&winningNumber%2===0:
   btype==="ODD"?winningNumber%2===1:btype==="LOW"?winningNumber>=1&&winningNumber<=18:
   btype==="HIGH"?winningNumber>=19:winningNumber===bet?.number;
  const multiplier=won?(btype==="STRAIGHT"?36:2):0;
  return {...base,version:1,game:"ROULETTE",bet:betObj,winningNumber,color,won,multiplier,payout:wager*multiplier};
 }
 if(type==="plinkoStart"){
  const pathRight=Array.from({length:8},()=>secureInt(2)===1);
  const bounceJitterPermille=Array.from({length:8},()=>secureInt(17)-8);
  const binIndex=pathRight.filter(Boolean).length;
  const multiplierHundredths=[5,25,50,100,150,100,50,25,5][binIndex];
  return {...base,version:2,game:"PLINKO",pathRight,bounceJitterPermille,binIndex,
   multiplierHundredths,payout:Math.floor(wager*multiplierHundredths/100)};
 }
 if(type==="scratchStart"){
  const card=scratchCards[wager];expect(card,"UNSUPPORTED_SCRATCH_CARD");
  const roll=secureInt(100);let sum=0,prize=prizes[0];
  for(let i=0;i<card.odds.length;i++){sum+=card.odds[i];if(roll<sum){prize=prizes[i];break;}}
  return {...base,type:"SCRATCHERS",version:1,card:card.name,prize:prize.id,
   symbols:prize.symbol?Array(3).fill(prize.symbol):["🐾","🍪","🦴"],
   payout:Math.floor(wager*prize.multi/100)};
 }
 if(type==="wheelStart"){
  const roll=secureInt(100);let sum=0,prize=wheelPrizes[0];
  for(const p of wheelPrizes){sum+=p.weight;if(roll<sum){prize=p;break;}}
  const available=unownedCasinoPuppies(v);
  const eligible=canWheelUnlock(v,now)&&available.length>0;
  if(prize.id==="PUPPY_UNLOCK"&&!eligible)prize=wheelPrizes[2];
  const puppyStyleId=prize.id==="PUPPY_UNLOCK"?available[secureInt(available.length)]:null;
  return {...base,type:"LUCKY_WHEEL",version:1,roll,prize:prize.id,
   segmentIndex:wheelPrizes.findIndex(p=>p.id===prize.id),puppyStyleId,
   payout:Math.floor(wager*prize.multi/100)};
 }
 throw Error("UNSUPPORTED_CASINO_GAME");
}
async function casino(v:Dict,t:Dict,intent:BetaEconomyIntent,now:number){
 const current=v.beta_casino_round_v1;
 const round=typeof current==="string"&&current?requireObj(JSON.parse(current)):null;
 if(intent.type.endsWith("Start")){
  expect(!round&&!v.casino_active_round_v1,"CASINO_ROUND_ALREADY_ACTIVE");
  stakeAllowed(v,intent.wager);
  if(intent.type==="scratchStart")expect(Object.hasOwn(scratchCards,intent.wager as number),"UNSUPPORTED_SCRATCH_CARD");
  if(["plinkoStart","scratchStart","wheelStart"].includes(intent.type))
   expect((intent.wager as number)%20===0,"INVALID_CASINO_WAGER_INCREMENT");
  const created=newCasinoRound(intent.type,intent,now,v);
  setLong(v,t,"casino_chips_v7",num(v,"casino_chips_v7")-(intent.wager as number));
  write(v,t,"beta_casino_round_v1",JSON.stringify(created),"string");
  write(v,t,"beta_last_casino_result_v1","","string");
 }else {
  const names:Record<string,string>={slotsReveal:"SLOTS",rouletteReveal:"ROULETTE",plinkoReveal:"PLINKO",scratchReveal:"SCRATCHERS",wheelReveal:"LUCKY_WHEEL"};
  expect(round&&round.game===names[intent.type] || round?.type===names[intent.type],"CASINO_ROUND_NOT_FOUND");
  expect(Number.isSafeInteger(round?.payout)&&Number(round.payout)>=0,"CORRUPT_CASINO_ROUND");
  setLong(v,t,"casino_chips_v7",inc(num(v,"casino_chips_v7"),Number(round.payout)));
  write(v,t,"beta_casino_round_v1","","string");
  const bonus=await grantCasinoBonuses(v,t,{
   roundId:String(round.roundId),wager:Number(round.wager),payout:Number(round.payout),
   guaranteedPuppyId:round.type==="LUCKY_WHEEL"&&round.prize==="PUPPY_UNLOCK"?String(round.puppyStyleId):null
  },now);
  write(v,t,"beta_last_casino_result_v1",JSON.stringify({...round,bonus}),"string");
 }
}
/**
 * Signed, non-economic cloud write diagnostic. This intentionally does not
 * call settleBetaAchievements; pressing Test Cloud Save must not mint XP or
 * alter currencies, puppies, achievements, care or casino state.
 */
export function applyBetaSyncProbe(raw:unknown,now=Date.now()):Dict{
 const source=requireObj(raw);
 expect(source.format==="puppy-clicker-cloud-save"&&source.version===1,"UNSUPPORTED_KOTLIN_SAVE");
 const stores=requireObj(source.stores),main=requireObj(stores.puppy_clicker_save);
 const values={...requireObj(main.values)},types={...requireObj(main.types)};
 setLong(values,types,"beta_last_sync_probe_at_v1",now);
 return {...source,savedAtEpochMs:now,stores:{...stores,puppy_clicker_save:{...main,values,types}}};
}
/** Handles rewards, shops and casino using *only* the stored server-signed Kotlin save. */
export async function applyBetaEconomy(raw:unknown,intent:BetaEconomyIntent,now=Date.now()):Promise<Dict>{
 const source=requireObj(raw);expect(source.format==="puppy-clicker-cloud-save"&&source.version===1,"UNSUPPORTED_KOTLIN_SAVE");
 const stores=requireObj(source.stores),main=requireObj(stores.puppy_clicker_save);
 const v={...requireObj(main.values)},t={...requireObj(main.types)};
 const result={...source,savedAtEpochMs:now,stores:{...stores,puppy_clicker_save:{...main,values:v,types:t}}};
 const nowDate=new Date(now),today=day(now),dayOfMonth=nowDate.getUTCDate();
 // Do not permit a client-controlled timestamp, balance, randomness or price.
 switch(intent.type){
  case "purchase":rollDay(v,t,today);shopUpgrade(v,t,intent);careWrite(v,t,now);break;
  case "ticket":ticketShop(v,t,intent);break;
  case "accessory":case "equip":accessories(v,t,intent);break;
  case "exchange":{
   const amount=intent.amount;expect(typeof amount==="number"&&Number.isSafeInteger(amount)&&amount>0&&amount%10===0,"INVALID_EXCHANGE");
   expect(num(v,"treats")>=amount,"EXCHANGE_NOT_AFFORDABLE");
   setLong(v,t,"treats",num(v,"treats")-amount);
   addLong(v,t,"casino_chips_v7",amount/10);break;
  }
  case "gacha":{
   const payment=intent.payment;
   expect(payment==="TREATS"||payment==="COMMON_TICKET","INVALID_GACHA_PAYMENT");
   const unlocked=new Set([...own(v,"unlocked_puppies"),"classic","golden","poodle","spotty"]);
   const missing=FREE_GACHA_PUPPIES.filter(p=>!unlocked.has(p.id));
   const pool=missing.length?missing:FREE_GACHA_PUPPIES.filter(p=>unlocked.has(p.id));
   expect(pool.length>0,"GACHA_NO_PUPPIES");
   if(payment==="TREATS"){
    expect(num(v,"treats")>=2500,"GACHA_INSUFFICIENT_TREATS");
    setLong(v,t,"treats",num(v,"treats")-2500);
   }else{
    const key=ticketKey("common");
    expect(num(v,key)>=1,"GACHA_INSUFFICIENT_TICKET");
    setInt(v,t,key,num(v,key)-1);
   }
   const choice=pool[secureInt(pool.length)];
   const isNewUnlock=!unlocked.has(choice.id);
   if(isNewUnlock){
    unlocked.add(choice.id);
    write(v,t,"unlocked_puppies",[...unlocked],"string_set");
    awardXp(v,t,100);
   }
   addLong(v,t,"beta_gacha_pulls_v1",1);
   write(v,t,"beta_last_gacha_result_v1",JSON.stringify({
    id:uuid(),success:true,puppyId:choice.id,puppyName:choice.name,
    payment,isNewUnlock
   }),"string");
   break;
  }
  case "switch":{
   const id=intent.id;expect(typeof id==="string"&&/^[a-z0-9_]{1,40}$/.test(id),"INVALID_PUPPY_ID");
   const unlocked=new Set([...own(v,"unlocked_puppies"),"classic","golden","poodle","spotty"]);
   expect(unlocked.has(id),"PUPPY_LOCKED");
   const outgoing=String(v.puppy_style||"classic");
   if(outgoing!==id){
    careWrite(v,t,now);
    // Preserve each puppy's own care and name, as in Kotlin setPuppyStyle.
    const profile=(name:string,global:string,initial:number)=>
      num(v,"care_profile_v1_"+id+"_"+name,initial);
    for(const [field,key,initial] of [
      ["happiness","happiness",100],["fullness","fullness",100],
      ["energy","energy",100],["cleanliness","cleanliness_v5",100],["bond","bond_v5",10],
    ] as const)setInt(v,t,key,profile(field,key,initial));
    write(v,t,"puppy_style",id,"string");
    const name=String(v["puppy_name_for_"+id]||
      FREE_GACHA_PUPPIES.find(p=>p.id===id)?.name||"Puppy");
    write(v,t,"puppy_name",name.slice(0,18),"string");
    careWrite(v,t,now);
   }
   break;
  }
  case "rename":{
   const name=typeof intent.name==="string"?intent.name.trim().replace(/\n/g," ").slice(0,18):"";
   expect(name.length>0,"PUPPY_NAME_REQUIRED");
   const style=String(v.puppy_style||"classic");
   write(v,t,"puppy_name",name,"string");
   write(v,t,"puppy_name_for_"+style,name,"string");
   break;
  }
  case "prestige":{
   expect(!v.beta_casino_round_v1&&!v.casino_active_round_v1,"FINISH_CASINO_FIRST");
   const lifetime=num(v,"lifetime_treats");
   expect(lifetime>=50000,"PRESTIGE_NOT_UNLOCKED");
   const gained=Math.min(8,1+Math.floor((lifetime-50000)/100000));
   for(const key of ["treats","lifetime_treats","bones_v7","legitimate_taps_v7",
     "total_shop_purchases_v5","total_taps","care_actions_v5"]){
    setLong(v,t,key,0);
   }
   for(const key of ["happiness","fullness","energy","cleanliness_v5"])
    setInt(v,t,key,100);
   for(const u of defs)setInt(v,t,"upgrade_"+u.id,0);
   for(const rarity of rarities)setInt(v,t,ticketKey(rarity),0);
   setInt(v,t,"best_combo",0);
   setInt(v,t,"daily_streak_v5",0);
   setLong(v,t,"daily_claim_day",0);
   setLong(v,t,"daily_day_v5",today);
   setLong(v,t,"daily_tap_start_v5",0);
   setLong(v,t,"daily_care_start_v5",0);
   setLong(v,t,"daily_shop_start_v5",0);
   write(v,t,"daily_tasks_v5",[],"string_set");
   setLong(v,t,"fair_play_cooldown_until",0);
   write(v,t,"park_active",false,"boolean");
   setLong(v,t,"park_ready_at",0);
   setInt(v,t,"prestige_count_v6",num(v,"prestige_count_v6")+1);
   setInt(v,t,"prestige_skill_points_v6",num(v,"prestige_skill_points_v6")+gained);
   addLong(v,t,"prestige_total_points_v6",gained);
   careWrite(v,t,now);
   break;
  }
  case "skill":{
   const skill=String(intent.skill||"").toLowerCase();
   expect(["tap_training","auto_training","smart_shopper","ticket_sense"].includes(skill),"UNKNOWN_SKILL");
   const k=skillKey(skill),owned=num(v,k),price=1+Math.floor(owned/2);
   expect(owned<5&&num(v,"prestige_skill_points_v6")>=price,"SKILL_UNAVAILABLE");
   setInt(v,t,k,owned+1);setInt(v,t,"prestige_skill_points_v6",num(v,"prestige_skill_points_v6")-price);break;
  }
  case "daily":{
   expect(Number(v.daily_claim_day??0)!==today&&Number(v.daily_claim_day??0)<today,"DAILY_ALREADY_CLAIMED");
   const last=Number(v.daily_claim_day??0),streak=last===today-1?Math.min(365,num(v,"daily_streak_v5")+1):1;
   const gameLevel=level(v),reward=150+Math.min(MAX_REWARD_STREAK_DAYS,streak)*25+gameLevel*10;
   earned(v,t,reward);addLong(v,t,"bones_v7",2);addLong(v,t,"pup_coins_v7",3);
   setLong(v,t,"daily_claim_day",today);setInt(v,t,"daily_streak_v5",streak);
   setInt(v,t,"happiness",cap100(num(v,"happiness",100)+8));setInt(v,t,"bond_v5",cap100(num(v,"bond_v5",10)+1));
   careWrite(v,t,now);break;
  }
  case "claimDailyGoal":{
   rollDay(v,t,today);
   const goal=await getAuthoritativeGoal(String(intent.goalId||""),nowDate);
   expect(goal,"UNKNOWN_DAILY_GOAL");
   const claims=own(v,"daily_tasks_v5");expect(!claims.includes(String(intent.goalId)),"GOAL_ALREADY_CLAIMED");
   let progress=0;
   if(goal.metric==="taps")progress=Math.max(0,num(v,"total_taps")-num(v,"daily_tap_start_v5"));
   if(goal.metric==="care")progress=Math.max(0,num(v,"care_actions_v5")-num(v,"daily_care_start_v5"));
   if(goal.metric==="shop")progress=Math.max(0,num(v,"total_shop_purchases_v5")-num(v,"daily_shop_start_v5"));
   if(goal.metric==="wellness")progress=careScore(v);
   if(goal.metric==="bond")progress=num(v,"bond_v5",10);
   if(goal.metric==="tickets")progress=ticketTotal(v);
   expect(progress>=goal.target,"GOAL_NOT_COMPLETE");
   earned(v,t,goal.reward);addLong(v,t,"bones_v7",1);addLong(v,t,"pup_coins_v7",1);
   awardXp(v,t,25);
   write(v,t,"daily_tasks_v5",[...claims,String(intent.goalId)],"string_set");break;
  }
  case "parkStart":{
   expect(v.park_active!==true&&num(v,"energy",100)>=20,"PARK_NOT_READY");
   write(v,t,"park_active",true,"boolean");setLong(v,t,"park_ready_at",now+60000);
   setInt(v,t,"energy",num(v,"energy",100)-20);careWrite(v,t,now);break;
  }
  case "parkClaim":{
   expect(v.park_active===true&&num(v,"park_ready_at")<=now,"PARK_NOT_READY");
   earned(v,t,250+level(v)*50);
   setInt(v,t,"happiness",cap100(num(v,"happiness",100)+12));
   setInt(v,t,"fullness",cap100(num(v,"fullness",100)-5));
   setInt(v,t,"cleanliness_v5",cap100(num(v,"cleanliness_v5",100)-5));
   setInt(v,t,"bond_v5",cap100(num(v,"bond_v5",10)+3));
   write(v,t,"park_active",false,"boolean");setLong(v,t,"park_ready_at",0);
   careWrite(v,t,now);break;
  }
  case "slotsStart":case "rouletteStart":case "plinkoStart":case "scratchStart":case "wheelStart":
  case "slotsReveal":case "rouletteReveal":case "plinkoReveal":case "scratchReveal":case "wheelReveal":
   await casino(v,t,intent,now);break;
  default:throw Error("BETA_ACTION_NOT_PORTED");
 }
 settleBetaAchievements(v,t);
 setLong(v,t,"last_seen",now);
 return result;
}
