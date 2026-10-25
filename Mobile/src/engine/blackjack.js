// Port of Kotlin PuppyBlackjackEngine.kt physical 52-card deck and rules.
// Guest-only in-memory/debited-round engine; official PupEye settlement still required.
export const MIN_WAGER=20,MAX_WAGER=100000,MAX_HANDS=3;
export const isValidWager=w=>Number.isSafeInteger(w)&&w>=MIN_WAGER&&w<=MAX_WAGER&&w%10===0;
const rank=id=>id%13+1;
const validCard=id=>Number.isInteger(id)&&id>=0&&id<=51;
const validDeck=deck=>Array.isArray(deck)&&deck.length===52&&deck.every(validCard)&&new Set(deck).size===52;
export const cardLabel=id=>{
 if(!validCard(id))return '?';
 const value=rank(id),symbol=['♣','♦','♥','♠'][Math.floor(id/13)];
 return (value===1?'A':value===11?'J':value===12?'Q':value===13?'K':String(value))+symbol;
};
export function handTotal(cards){
 if(!Array.isArray(cards)||!cards.length||!cards.every(validCard))throw RangeError('Invalid Blackjack cards');
 let total=cards.reduce((s,c)=>s+Math.min(10,rank(c)),0);
 let aces=cards.filter(c=>rank(c)===1).length,soft=false;
 while(aces>0&&total+10<=21){total+=10;aces--;soft=true;}
 return {total,soft};
}
const natural=h=>!h.fromSplit&&h.cards.length===2&&handTotal(h.cards).total===21;
const dealerNatural=cards=>cards.length===2&&handTotal(cards).total===21;
export function newRoundFromDeck(wager,shuffledDeck){
 if(!isValidWager(wager)||!validDeck(shuffledDeck))throw RangeError('Invalid Blackjack wager or shuffled deck');
 const deck=[...shuffledDeck],player=[deck.shift()],dealer=[deck.shift()];
 player.push(deck.shift());dealer.push(deck.shift());
 const hand={cards:player,wager,stood:false,doubled:false,fromSplit:false,splitAces:false};
 const complete=natural(hand)||dealerNatural(dealer);
 return {remainingDeck:deck,dealerCards:dealer,hands:[hand],activeHandIndex:complete?-1:0,complete};
}
export function newShuffledRound(wager,random=Math.random){
 const deck=Array.from({length:52},(_,i)=>i);
 for(let i=51;i>0;i--){
  const v=Number(random());
  if(!Number.isFinite(v)||v<0||v>=1)throw RangeError('Invalid shuffle random');
  const j=Math.floor(v*(i+1));
  [deck[i],deck[j]]=[deck[j],deck[i]];
 }
 return newRoundFromDeck(wager,deck);
}
const active=s=>!s.complete&&s.activeHandIndex>=0?s.hands[s.activeHandIndex]:null;
export const canHit=s=>{const h=active(s);return Boolean(h&&!h.stood&&!h.splitAces&&handTotal(h.cards).total<21);};
export const canStand=s=>{const h=active(s);return Boolean(h&&!h.stood);};
export const canDouble=s=>{const h=active(s);return Boolean(h&&!h.stood&&!h.splitAces&&h.cards.length===2);};
export const canSplit=s=>{
 const h=active(s);
 return Boolean(h&&!h.stood&&h.cards.length===2&&s.hands.length<MAX_HANDS&&rank(h.cards[0])===rank(h.cards[1])&&!(rank(h.cards[0])===1&&h.fromSplit));
};
const fail=(state,error)=>({success:false,state,additionalWager:0,error});
const success=(state,additionalWager=0)=>({success:true,state,additionalWager});
function dealerResolve(s){
 if(s.hands.every(h=>handTotal(h.cards).total>21)||dealerNatural(s.dealerCards))return {...s,activeHandIndex:-1,complete:true};
 const deck=[...s.remainingDeck],dealer=[...s.dealerCards];
 while(handTotal(dealer).total<17){
  if(!deck.length)throw RangeError('Blackjack deck exhausted');
  dealer.push(deck.shift());
 }
 return {...s,remainingDeck:deck,dealerCards:dealer,activeHandIndex:-1,complete:true};
}
function advance(s){
 for(let i=Math.max(0,s.activeHandIndex)+1;i<s.hands.length;i++){
  const h=s.hands[i];if(!h.stood&&handTotal(h.cards).total<21&&!h.splitAces)return {...s,activeHandIndex:i};
 }
 const hands=s.hands.map(h=>({...h,stood:h.stood||handTotal(h.cards).total>=21||h.splitAces}));
 const i=hands.findIndex(h=>!h.stood&&handTotal(h.cards).total<21);
 if(i>=0)return {...s,hands,activeHandIndex:i};
 return dealerResolve({...s,hands});
}
export function hit(state){
 if(state.complete)return fail(state,'ROUND_COMPLETE');
 if(!canHit(state))return fail(state,'CANNOT_HIT');
 if(!state.remainingDeck.length)return fail(state,'DECK_EXHAUSTED');
 const index=state.activeHandIndex,deck=[...state.remainingDeck],hands=state.hands.map(h=>({...h,cards:[...h.cards]}));
 const h=hands[index];h.cards.push(deck.shift());
 h.stood=handTotal(h.cards).total>=21;
 return success(advance({...state,remainingDeck:deck,hands}));
}
export function stand(state){
 if(state.complete)return fail(state,'ROUND_COMPLETE');
 if(!canStand(state))return fail(state,'CANNOT_STAND');
 const hands=state.hands.map(h=>({...h,cards:[...h.cards]}));
 hands[state.activeHandIndex].stood=true;
 return success(advance({...state,hands}));
}
export function doubleDown(state){
 if(state.complete)return fail(state,'ROUND_COMPLETE');
 if(!canDouble(state))return fail(state,'CANNOT_DOUBLE');
 if(!state.remainingDeck.length)return fail(state,'DECK_EXHAUSTED');
 const index=state.activeHandIndex,deck=[...state.remainingDeck],hands=state.hands.map(h=>({...h,cards:[...h.cards]}));
 const h=hands[index],additional=h.wager;
 h.cards.push(deck.shift());h.wager+=additional;h.stood=true;h.doubled=true;
 return success(advance({...state,remainingDeck:deck,hands}),additional);
}
export function split(state){
 if(state.complete)return fail(state,'ROUND_COMPLETE');
 if(!canSplit(state))return fail(state,'CANNOT_SPLIT');
 if(state.remainingDeck.length<2)return fail(state,'DECK_EXHAUSTED');
 const index=state.activeHandIndex,deck=[...state.remainingDeck],hands=state.hands.map(h=>({...h,cards:[...h.cards]}));
 const source=hands[index],additional=source.wager,aces=rank(source.cards[0])===1;
 const firstCards=[source.cards[0],deck.shift()],secondCards=[source.cards[1],deck.shift()];
 const a={cards:firstCards,wager:additional,stood:aces||handTotal(firstCards).total>=21,doubled:false,fromSplit:true,splitAces:aces};
 const b={cards:secondCards,wager:additional,stood:aces||handTotal(secondCards).total>=21,doubled:false,fromSplit:true,splitAces:aces};
 hands.splice(index,1,a,b);
 const next={...state,remainingDeck:deck,hands,activeHandIndex:index};
 return success(a.stood?advance(next):next,additional);
}
export function blackjackOutcome(state){
 if(!state?.complete)throw RangeError('Blackjack round not complete');
 const dealer=handTotal(state.dealerCards),dealerBj=dealerNatural(state.dealerCards),bust=dealer.total>21;
 const hands=state.hands.map(h=>{
  const player=handTotal(h.cards),bj=natural(h);
  const result=player.total>21?'BUST':dealerBj&&bj?'PUSH':dealerBj?'LOSS':bj?'BLACKJACK':bust||player.total>dealer.total?'WIN':player.total===dealer.total?'PUSH':'LOSS';
  const payout=result==='BLACKJACK'?Math.floor(h.wager*5/2):result==='WIN'?h.wager*2:result==='PUSH'?h.wager:0;
  return {result,playerTotal:player.total,wager:h.wager,payout};
 });
 return {dealerTotal:dealer.total,dealerBlackjack:dealerBj,dealerBust:bust,hands,totalPayout:hands.reduce((s,h)=>s+h.payout,0)};
}
export function validBlackjackState(s){
 if(!s||typeof s!=='object'||!Array.isArray(s.remainingDeck)||!Array.isArray(s.dealerCards)||!Array.isArray(s.hands)||
   s.hands.length<1||s.hands.length>MAX_HANDS||s.dealerCards.length<2||typeof s.complete!=='boolean')return false;
 const ids=[...s.remainingDeck,...s.dealerCards];
 if(s.hands.some(h=>{
  if(!h||!Array.isArray(h.cards)||h.cards.length<2||!Number.isSafeInteger(h.wager)||h.wager<=0||
    typeof h.stood!=='boolean'||typeof h.fromSplit!=='boolean'||typeof h.splitAces!=='boolean')return true;
  ids.push(...h.cards);return false;
 }))return false;
 if(ids.length!==52||ids.some(id=>!validCard(id))||new Set(ids).size!==52)return false;
 if(s.complete)return s.activeHandIndex===-1;
 return Number.isInteger(s.activeHandIndex)&&s.activeHandIndex>=0&&s.activeHandIndex<s.hands.length;
}
