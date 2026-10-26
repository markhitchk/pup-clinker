/**
 * Server-side port of Kotlin PuppyCasinoRewardEngine v2 and
 * PuppyCasinoPuppyRewardEngine v1.
 *
 * All round IDs and results originate from a committed, signed PupEye round.
 * Save the Kotlin V1 ledgers atomically with the payout, never on the client.
 */
type Values=Record<string,unknown>;
export const CASINO_PUPPY_POOL=[
 "midnight","cloud","aurora","cocoa","snowball","galaxy","neon_buddy",
 "golden_night","v2_frost","v2_honey","v2_biscuit","v2_onyx",
 "v2_domino","v2_chestnut","v2_prism","v2_flurry"
] as const;
type Rarity="common"|"uncommon"|"rare"|"epic"|"legendary";
interface Ledger {schemaVersion:1;dailyEpochDay:number;dailyTicketAwards?:number;dailyPuppyUnlocks?:number;evaluatedRoundIds:string[]}
export interface CasinoSettlement {roundId:string;wager:number;payout:number;guaranteedPuppyId?:string|null}
const rarityWeights:[Rarity,number][]=[
 ["common",60],["uncommon",25],["rare",10],["epic",4],["legendary",1]
];
const int=(x:unknown)=>typeof x==="number"&&Number.isSafeInteger(x)&&x>=0?x:0;
const dateDay=(now:number)=>Math.floor(now/86400000);
const owned=(v:Values)=>new Set(Array.isArray(v.unlocked_puppies)?v.unlocked_puppies.filter(x=>typeof x==="string") as string[]:[]);
const safeAdd=(a:number,b:number)=>Math.min(Number.MAX_SAFE_INTEGER,a+b);
function readLedger(v:Values,key:string,today:number,field:"dailyTicketAwards"|"dailyPuppyUnlocks"):Ledger{
 let parsed:unknown;
 try{parsed=JSON.parse(String(v[key]||""));}catch{/* legacy/absent ledger */}
 const o=parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed as Record<string,unknown>:{};
 const ids=Array.isArray(o.evaluatedRoundIds)?o.evaluatedRoundIds.filter(
   (id):id is string=>typeof id==="string"&&/^[A-Za-z0-9_-]{8,80}$/.test(id)
 ).slice(-512):[];
 const sameDay=o.dailyEpochDay===today;
 return {schemaVersion:1,dailyEpochDay:today,evaluatedRoundIds:[...new Set(ids)],
  [field]:sameDay?Math.min(1,int(o[field])):0};
}
function writeLedger(v:Values,t:Values,key:string,ledger:Ledger){
 v[key]=JSON.stringify(ledger);t[key]="string";
}
async function roll(version:number,purpose:string,roundId:string,bound:number):Promise<number>{
 const input=new TextEncoder().encode(
  (version===2?"puppy-casino-reward-v2:":"puppy-casino-puppy-v1:")+purpose+":"+roundId);
 const hash=new Uint8Array(await crypto.subtle.digest("SHA-256",input));
 let x=0n;for(let i=0;i<8;i++)x=(x<<8n)|BigInt(hash[i]);
 return Number((x&((1n<<63n)-1n))%BigInt(bound));
}
function chance(wager:number,payout:number,kind:"ticket"|"puppy"):number{
 if(wager<=0||payout<=wager)return 0;
 if(payout>=wager*100)return kind==="ticket"?500:10000;
 if(payout>=wager*20)return kind==="ticket"?200:500;
 if(payout>=wager*5)return kind==="ticket"?100:150;
 if(payout>=wager*2)return 50;
 return 25;
}
export function canWheelUnlock(v:Values,now:number):boolean{
 const pool=CASINO_PUPPY_POOL.filter(id=>!owned(v).has(id));
 if(pool.length===0)return false;
 const ledger=readLedger(v,"casino_puppy_reward_ledger_v1",dateDay(now),"dailyPuppyUnlocks");
 return (ledger.dailyPuppyUnlocks||0)<1;
}
export function unownedCasinoPuppies(v:Values):string[]{
 const before=owned(v);
 return CASINO_PUPPY_POOL.filter(id=>!before.has(id));
}
/** Return short presentational bonus details; only server-calculated fields mutate. */
export async function grantCasinoBonuses(v:Values,t:Values,round:CasinoSettlement,now:number){
 if(!/^[A-Za-z0-9_-]{8,80}$/.test(round.roundId)||
  !Number.isSafeInteger(round.wager)||round.wager<20||
  !Number.isSafeInteger(round.payout)||round.payout<0)throw Error("INVALID_CASINO_BONUS_ROUND");
 const today=dateDay(now),winner=round.payout>round.wager;
 const ticketKey="casino_ticket_reward_ledger_v1";
 const ticket=readLedger(v,ticketKey,today,"dailyTicketAwards");
 let awardedTicket:Rarity|null=null;
 if(!ticket.evaluatedRoundIds.includes(round.roundId)){
  ticket.evaluatedRoundIds=[...ticket.evaluatedRoundIds,round.roundId].slice(-512);
  const ticketChance=winner?chance(round.wager,round.payout,"ticket"):0;
  if(ticketChance>0&&await roll(2,"ticket-drop",round.roundId,10000)<ticketChance&&
     (ticket.dailyTicketAwards||0)<1){
   const rr=await roll(2,"ticket-rarity",round.roundId,100);
   let cursor=0;
   for(const [rarity,weight] of rarityWeights){cursor+=weight;if(rr<cursor){awardedTicket=rarity;break;}}
   const key="upgrade_ticket_"+awardedTicket;
   if(int(v[key])<9999){
    v[key]=int(v[key])+1;t[key]="int";
    v.total_tickets_found=safeAdd(int(v.total_tickets_found),1);t.total_tickets_found="long";
    ticket.dailyTicketAwards=1;
   }else awardedTicket=null;
  }
 }
 writeLedger(v,t,ticketKey,ticket);
 const puppyKey="casino_puppy_reward_ledger_v1";
 const puppy=readLedger(v,puppyKey,today,"dailyPuppyUnlocks");
 let awardedPuppy:string|null=null;
 if(!puppy.evaluatedRoundIds.includes(round.roundId)){
  puppy.evaluatedRoundIds=[...puppy.evaluatedRoundIds,round.roundId].slice(-512);
  const pool=unownedCasinoPuppies(v),candidate=round.guaranteedPuppyId;
  if((puppy.dailyPuppyUnlocks||0)<1&&pool.length>0){
   if(candidate&&pool.includes(candidate)){
    awardedPuppy=candidate;
   }else if(!candidate&&winner){
    const puppyChance=chance(round.wager,round.payout,"puppy");
    if(await roll(1,"puppy-drop",round.roundId,10000)<puppyChance){
     awardedPuppy=pool[await roll(1,"puppy-choice",round.roundId,pool.length)];
    }
   }
   if(awardedPuppy){
    v.unlocked_puppies=[...owned(v),awardedPuppy];t.unlocked_puppies="string_set";
    puppy.dailyPuppyUnlocks=1;
   }
  }
 }
 writeLedger(v,t,puppyKey,puppy);
 return {awardedTicket,awardedPuppy};
}
