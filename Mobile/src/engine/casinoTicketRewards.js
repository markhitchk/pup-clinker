// Guest-only port of PuppyCasinoRewardEngine.kt (policy version 2).
// No server grants or protected inventory are created by this module.
const POLICY_VERSION=2;
const MAX_EVALUATED=512;
const MAX_DAILY_AWARDS=1;
const MAX_PER_RARITY=9999;
const RARITY_WEIGHTS=[['COMMON',60],['UNCOMMON',25],['RARE',10],['EPIC',4],['LEGENDARY',1]];
const ROUND_ID=/^[A-Za-z0-9_-]{8,80}$/;

// SHA-256 on the ASCII policy input, without an Expo/native dependency.
// Kotlin hashes "puppy-casino-reward-v2:<purpose>:<roundId>" and uses
// the low 63 bits of the first eight digest bytes.
const K=[
 0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
 0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
 0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
 0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
 0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
 0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
 0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
 0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
];
const rotr=(n,k)=>(n>>>k)|(n<<(32-k));
function firstDigestWords(value){
 const bytes=Array.from(value,c=>c.charCodeAt(0));
 if(bytes.some(c=>c>127))throw new RangeError('Policy input must be ASCII');
 const bits=bytes.length*8;
 bytes.push(0x80);
 while(bytes.length%64!==56)bytes.push(0);
 bytes.push(0,0,0,0,(bits>>>24)&255,(bits>>>16)&255,(bits>>>8)&255,bits&255);
 const h=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
 for(let block=0;block<bytes.length;block+=64){
  const w=new Array(64);
  for(let i=0;i<16;i++){const p=block+i*4;w[i]=((bytes[p]<<24)|(bytes[p+1]<<16)|(bytes[p+2]<<8)|bytes[p+3])>>>0;}
  for(let i=16;i<64;i++){
   const a=w[i-15],b=w[i-2],s0=rotr(a,7)^rotr(a,18)^(a>>>3),s1=rotr(b,17)^rotr(b,19)^(b>>>10);
   w[i]=(w[i-16]+s0+w[i-7]+s1)>>>0;
  }
  let [a,b,c,d,e,f,g,z]=h;
  for(let i=0;i<64;i++){
   const s1=rotr(e,6)^rotr(e,11)^rotr(e,25);
   const choose=(e&f)^(~e&g);
   const t1=(z+s1+choose+K[i]+w[i])>>>0;
   const s0=rotr(a,2)^rotr(a,13)^rotr(a,22);
   const majority=(a&b)^(a&c)^(b&c);
   const t2=(s0+majority)>>>0;
   z=g;g=f;f=e;e=(d+t1)>>>0;d=c;c=b;b=a;a=(t1+t2)>>>0;
  }
  for(let i=0;i<8;i++)h[i]=(h[i]+[a,b,c,d,e,f,g,z][i])>>>0;
 }
 return h;
}
export function deterministicCasinoTicketRoll(roundId,purpose,bound){
 if(typeof roundId!=='string'||!ROUND_ID.test(roundId)||!['ticket-drop','ticket-rarity'].includes(purpose)||
  !Number.isSafeInteger(bound)||bound<1||bound>10000)throw new RangeError('Invalid casino reward roll');
 const digest=firstDigestWords('puppy-casino-reward-v'+POLICY_VERSION+':'+purpose+':'+roundId);
 const high=digest[0]&0x7fffffff,low=digest[1]>>>0;
 // Exact unsigned 63-bit modulus with safe integer intermediates.
 return ((high%bound)*(4294967296%bound)+(low%bound))%bound;
}
export function casinoTicketChance(wager,payout){
 if(!Number.isSafeInteger(wager)||!Number.isSafeInteger(payout)||wager<=0||payout<=wager)return 0;
 if(payout/wager>=100)return 500;
 if(payout/wager>=20)return 200;
 if(payout/wager>=5)return 100;
 if(payout/wager>=2)return 50;
 return 25;
}
export function casinoTicketRarity(roundId){
 const n=deterministicCasinoTicketRoll(roundId,'ticket-rarity',100);
 let cursor=0;
 for(const [rarity,weight] of RARITY_WEIGHTS){cursor+=weight;if(n<cursor)return rarity;}
 return 'COMMON';
}
export function sanitizeCasinoTicketLedger(value){
 const obj=value&&typeof value==='object'&&!Array.isArray(value)?value:{};
 const source=Array.isArray(obj.evaluatedRoundIds)?obj.evaluatedRoundIds:[];
 const ids=[...new Set(source.filter(id=>typeof id==='string'&&ROUND_ID.test(id)))].slice(-MAX_EVALUATED);
 return {
  evaluatedRoundIds:ids,
  dailyEpochDay:Number.isSafeInteger(obj.dailyEpochDay)?obj.dailyEpochDay:null,
  dailyTicketAwards:Number.isSafeInteger(obj.dailyTicketAwards)?Math.max(0,Math.min(MAX_DAILY_AWARDS,obj.dailyTicketAwards)):0
 };
}
// Assign a persistent, local-only ID when accepting a wager, not when settling.
// This counter is not an authoritative transaction or identity credential.
export function nextGuestCasinoRoundId(g){
 const counter=Number.isSafeInteger(g.casinoRoundSerial)?g.casinoRoundSerial:0;
 if(counter>=Number.MAX_SAFE_INTEGER)throw new RangeError('Guest round counter exhausted');
 g.casinoRoundSerial=counter+1;
 return 'guest_'+String(g.casinoRoundSerial).padStart(12,'0');
}
export function guestRoundId(round){
 if(typeof round?.roundId==='string'&&ROUND_ID.test(round.roundId))return round.roundId;
 const kind=String(round?.game||round?.type||'round').replace(/[^A-Za-z0-9_-]/g,'');
 return Number.isSafeInteger(round?.createdAt)&&round.createdAt>0?
  'legacy_'+kind+'_'+round.createdAt:null;
}
export function applyGuestCasinoTicketReward(g,round,today){
 const roundId=guestRoundId(round);
 if(!roundId||!Number.isSafeInteger(today)||!Number.isSafeInteger(round?.wager)||!Number.isSafeInteger(round?.payout))
  return {status:'INVALID_ROUND'};
 const ledger=sanitizeCasinoTicketLedger(g.casinoTicketLedger);
 if(ledger.dailyEpochDay!==today){ledger.dailyEpochDay=today;ledger.dailyTicketAwards=0;}
 if(ledger.evaluatedRoundIds.includes(roundId)){g.casinoTicketLedger=ledger;return {status:'ALREADY_EVALUATED'};}
 ledger.evaluatedRoundIds=[...ledger.evaluatedRoundIds,roundId].slice(-MAX_EVALUATED);
 const chance=casinoTicketChance(round.wager,round.payout);
 let status='NOT_ELIGIBLE',rarity=null;
 if(chance){
  const roll=deterministicCasinoTicketRoll(roundId,'ticket-drop',10000);
  if(roll>=chance)status='NO_DROP';
  else if(ledger.dailyTicketAwards>=MAX_DAILY_AWARDS)status='DAILY_CAP_REACHED';
  else {
   rarity=casinoTicketRarity(roundId);
   if((g.ticketInventory?.[rarity]||0)>=MAX_PER_RARITY)status='INVENTORY_CAP_REACHED';
   else {
    g.ticketInventory[rarity]=(g.ticketInventory[rarity]||0)+1;
    g.lastTicketDrop=rarity;
    g.ticketDropSerial=Math.min(Number.MAX_SAFE_INTEGER,(g.ticketDropSerial||0)+1);
    g.totalTicketsFound=Math.min(Number.MAX_SAFE_INTEGER,(g.totalTicketsFound||0)+1);
    ledger.dailyTicketAwards++;
    status='AWARDED';
   }
  }
 }
 g.casinoTicketLedger=ledger;
 return {status,rarity,chance};
}
