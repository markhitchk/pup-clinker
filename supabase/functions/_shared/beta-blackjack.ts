/**
 * Server-only deterministic Blackjack shoe derived from the service secret.
 * The complete shoe is NEVER written to the Kotlin cloud save or returned to
 * the client: only dealt cards, wager and active hand state are persisted.
 * User-supplied cards, decks, payout and RNG fields are ignored.
 */
type Dict=Record<string,unknown>;
type Intent={type:string;wager?:number};
type Hand={cards:number[];wager:number;stood:boolean;doubled:boolean;fromSplit:boolean;splitAces:boolean};
type Round={version:1;type:"BLACKJACK";roundId:string;wager:number;createdAt:number;drawIndex:number;dealerCards:number[];hands:Hand[];activeHandIndex:number;complete:boolean};
const secret=()=>Deno.env.get("PUPEYE_CASINO_SEED")||Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const isObj=(x:unknown):x is Dict=>Boolean(x)&&typeof x==="object"&&!Array.isArray(x);
const obj=(x:unknown)=>{if(!isObj(x))throw Error("INVALID_SAVE");return x;};
const number=(v:Dict,k:string):number=>{const n=v[k]??0;if(typeof n!=="number"||!Number.isSafeInteger(n)||n<0)throw Error("UNSAFE_SAVE_NUMBER");return n;};
const cap=(a:number,b:number)=>Math.min(Number.MAX_SAFE_INTEGER,a+b);
function check(v:unknown,k:string):asserts v {if(!v)throw Error(k);}
const rank=(id:number)=>id%13+1;
const score=(cards:number[])=>{
 let total=0,aces=0;
 for(const id of cards){const v=rank(id);total+=Math.min(10,v);if(v===1)aces++;}
 while(aces>0&&total+10<=21){total+=10;aces--;}return total;
};
const natural=(cards:number[],fromSplit=false)=>!fromSplit&&cards.length===2&&score(cards)===21;
async function shoe(roundId:string):Promise<number[]>{
 const keyText=secret();check(keyText.length>=24,"BLACKJACK_SERVER_SECRET_UNAVAILABLE");
 const encoder=new TextEncoder();
 const key=await crypto.subtle.importKey("raw",encoder.encode(keyText),
   {name:"HMAC",hash:"SHA-256"},false,["sign"]);
 const cards=Array.from({length:52},(_,i)=>i);
 for(let i=51;i>0;i--){
  const bytes=await crypto.subtle.sign("HMAC",key,encoder.encode("pup-eye-blackjack-v1:"+roundId+":"+i));
  const j=new DataView(bytes).getUint32(0,false)%(i+1);
  [cards[i],cards[j]]=[cards[j],cards[i]];
 }
 return cards;
}
function completedOutcome(round:Round,dealerCards:number[]){
 const dealerTotal=score(dealerCards),dealerBj=natural(dealerCards),dealerBust=dealerTotal>21;
 const hands=round.hands.map(h=>{
  const playerTotal=score(h.cards),bj=natural(h.cards,h.fromSplit);
  const result=playerTotal>21?"BUST":dealerBj&&bj?"PUSH":dealerBj?"LOSS":
    bj?"BLACKJACK":dealerBust||playerTotal>dealerTotal?"WIN":
    playerTotal===dealerTotal?"PUSH":"LOSS";
  const payout=result==="BLACKJACK"?Math.floor(h.wager*5/2):
    result==="WIN"?h.wager*2:result==="PUSH"?h.wager:0;
  return {result,playerTotal,wager:h.wager,payout};
 });
 return {dealerTotal,dealerBlackjack:dealerBj,dealerBust,hands,totalPayout:hands.reduce((n,h)=>n+h.payout,0)};
}
function finish(v:Dict,t:Dict,round:Round,deck:number[]){
 const dealer=[deck[1],deck[3]];
 let cursor=round.drawIndex;
 if(round.hands.some(h=>score(h.cards)<=21)&&!natural(dealer)){
  while(score(dealer)<17){check(cursor<52,"DECK_EXHAUSTED");dealer.push(deck[cursor++]);}
 }
 const outcome=completedOutcome(round,dealer);
 const balance=number(v,"casino_chips_v7");
 check(balance+outcome.totalPayout<=Number.MAX_SAFE_INTEGER,"CASINO_BALANCE_OVERFLOW");
 v.casino_chips_v7=balance+outcome.totalPayout;t.casino_chips_v7="long";
 const finished={...round,dealerCards:dealer,drawIndex:cursor,activeHandIndex:-1,complete:true};
 // The finished state is presentation-only; the wager has already settled.
 v.beta_last_blackjack_result_v1=JSON.stringify({state:finished,outcome});
 t.beta_last_blackjack_result_v1="string";
 v.beta_casino_round_v1="";t.beta_casino_round_v1="string";
}
function activeIndex(round:Round):number {
 return round.hands.findIndex(h=>!h.stood&&score(h.cards)<21&&!h.splitAces);
}
export async function applyBetaBlackjack(raw:unknown,intent:Intent,now=Date.now()):Promise<Dict>{
 const source=obj(raw);
 check(source.format==="puppy-clicker-cloud-save"&&source.version===1,"UNSUPPORTED_KOTLIN_SAVE");
 const stores=obj(source.stores),main=obj(stores.puppy_clicker_save);
 const v={...obj(main.values)},t={...obj(main.types)};
 const updated:Dict={...source,savedAtEpochMs:now,stores:{...stores,puppy_clicker_save:{...main,values:v,types:t}}};
 const pending=v.beta_casino_round_v1;
 if(intent.type==="blackjackStart"){
  check(!pending,"CASINO_ROUND_ALREADY_ACTIVE");
  const wager=intent.wager;check(Number.isSafeInteger(wager)&&typeof wager==="number"&&wager>=20&&wager<=100000&&wager%10===0,"BLACKJACK_INVALID_WAGER");
  check(number(v,"casino_chips_v7")>=wager,"BLACKJACK_INSUFFICIENT_CHIPS");
  const id=crypto.randomUUID(),deck=await shoe(id);
  const player=[deck[0],deck[2]],dealer=[deck[1]];
  const round:Round={version:1,type:"BLACKJACK",roundId:id,wager,createdAt:now,drawIndex:4,dealerCards:dealer,
   hands:[{cards:player,wager,stood:false,doubled:false,fromSplit:false,splitAces:false}],
   activeHandIndex:0,complete:false};
  v.casino_chips_v7=number(v,"casino_chips_v7")-wager;t.casino_chips_v7="long";
  if(natural(player)||natural([deck[1],deck[3]])){
   // Kotlin resolves either initial natural immediately; a dealer Blackjack
   // must never leave Double or Split available to a client.
   finish(v,t,round,deck);
  }else{
   v.beta_casino_round_v1=JSON.stringify(round);t.beta_casino_round_v1="string";
   v.beta_last_blackjack_result_v1="";t.beta_last_blackjack_result_v1="string";
  }
 }else{
  check(typeof pending==="string"&&pending.length>0,"BLACKJACK_ROUND_NOT_FOUND");
  const round=JSON.parse(String(pending)) as Round;
  check(round.type==="BLACKJACK"&&round.version===1&&!round.complete&&round.hands.length>=1&&round.hands.length<=3,"BLACKJACK_INVALID_ROUND");
  const deck=await shoe(round.roundId);
  const h=round.hands[round.activeHandIndex];
  check(h&&h.cards.length>=2&&!h.stood&&!h.splitAces&&score(h.cards)<21,"BLACKJACK_MOVE_UNAVAILABLE");
  let index=round.drawIndex;
  if(intent.type==="blackjackHit"){
   check(index<52,"DECK_EXHAUSTED");h.cards.push(deck[index++]);h.stood=score(h.cards)>=21;
  }else if(intent.type==="blackjackStand")h.stood=true;
  else if(intent.type==="blackjackDouble"){
   check(h.cards.length===2&&number(v,"casino_chips_v7")>=h.wager&&index<52,"BLACKJACK_CANNOT_DOUBLE");
   v.casino_chips_v7=number(v,"casino_chips_v7")-h.wager;t.casino_chips_v7="long";
   h.wager*=2;h.doubled=true;h.cards.push(deck[index++]);h.stood=true;
  }else if(intent.type==="blackjackSplit"){
   check(h.cards.length===2&&round.hands.length<3&&rank(h.cards[0])===rank(h.cards[1])&&
      !(rank(h.cards[0])===1&&h.fromSplit)&&index+1<52&&number(v,"casino_chips_v7")>=h.wager,"BLACKJACK_CANNOT_SPLIT");
   v.casino_chips_v7=number(v,"casino_chips_v7")-h.wager;t.casino_chips_v7="long";
   const aces=rank(h.cards[0])===1;
   const a:Hand={cards:[h.cards[0],deck[index++]],wager:h.wager,stood:aces,doubled:false,fromSplit:true,splitAces:aces};
   const b:Hand={cards:[h.cards[1],deck[index++]],wager:h.wager,stood:aces,doubled:false,fromSplit:true,splitAces:aces};
   a.stood=a.stood||score(a.cards)>=21;b.stood=b.stood||score(b.cards)>=21;
   round.hands.splice(round.activeHandIndex,1,a,b);
  }else throw Error("BLACKJACK_UNKNOWN_ACTION");
  round.drawIndex=index;
  const next=activeIndex(round);
  if(next<0)finish(v,t,round,deck);
  else{
   round.activeHandIndex=next;
   v.beta_casino_round_v1=JSON.stringify(round);t.beta_casino_round_v1="string";
  }
 }
 v.last_seen=now;t.last_seen="long";
 return updated;
}
