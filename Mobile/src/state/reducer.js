import {freshGame,tap,care,purchase,switchPuppy,convertTreatsToChips,casinoSpend,casinoPayout,claimDailyReward,startPark,claimPark,prestige,buyPrestigeSkill,buyUpgradeTicket,buyAccessory,handValue} from '../engine/game.js';
import {slotsOutcome,rouletteOutcome,plinkoOutcome,wheelOutcome,scratcherOutcome} from '../engine/casinoV7.js';

export const initialState=()=>({game:freshGame(),blackjack:null,notice:'Local guest mode · no official cloud sync',ready:false});
const clone=game=>JSON.parse(JSON.stringify(game));
const validCard=n=>Number.isInteger(n)&&n>=1&&n<=13;
function casinoRound(action){
 switch(action.gameType){
  case 'slots':return slotsOutcome(action.wager,action.rolls);
  case 'roulette':return rouletteOutcome(action.wager,action.bet,action.roll);
  case 'plinko':return plinkoOutcome(action.wager,action.path);
  case 'wheel':return wheelOutcome(action.wager,action.roll,false);
  case 'scratch':return scratcherOutcome(action.wager,action.roll);
  default:throw new RangeError('Unknown casino game');
 }
}
function scoreBlackjack(player,dealer,wager){
 const p=handValue(player),d=handValue(dealer);
 if(p>21)return {result:'BUST',payout:0};
 if(d>21||p>d)return {result:'WIN',payout:wager*2};
 if(p===d)return {result:'PUSH',payout:wager};
 return {result:'LOSS',payout:0};
}
// Pure reducer: randomness/timestamps are supplied on the dispatched action.
// Thus React may batch taps or re-evaluate the reducer without dropping input.
export function transition(state,action){
 if(action.type==='hydrate')return {...state,game:action.game?clone(action.game):freshGame(),ready:true};
 if(!state.ready)return state;
 const g=clone(state.game), no=()=>state;
 let notice='';
 let blackjack=state.blackjack;
 switch(action.type){
  case 'tap': {
   let i=0;
   const ticket=tap(g,action.now,()=>action.randoms?.[i++]??0.99);
   if(g.totalTaps===state.game.totalTaps)return {...state,notice:'⚡ Your puppy needs rest'};
   if(ticket)notice='🎟️ '+ticket+' Upgrade Ticket found!';
   break;
  }
  case 'care': if(!care(g,action.kind))return {...state,notice:'This care action is unavailable'};notice='💖 Puppy care complete';break;
  case 'purchase': if(!purchase(g,action.upgrade))return {...state,notice:'Need more Treats, Bones or Tickets'};notice='🛍️ Upgrade purchased';break;
  case 'switch': if(!switchPuppy(g,action.id))return no();break;
  case 'exchange':if(!convertTreatsToChips(g,action.amount))return {...state,notice:'Exchange requires Treats in multiples of 10'};notice='🎰 Casino Chips credited';break;
  case 'daily':if(!claimDailyReward(g,action.epochDay))return {...state,notice:'Daily reward already claimed'};notice='🎁 Daily reward collected';break;
  case 'parkStart':if(!startPark(g,action.now))return {...state,notice:'Need 20 energy, or a walk is already active'};notice='🌳 Walk started';break;
  case 'parkClaim':if(!claimPark(g,action.now))return {...state,notice:'Walk is not finished'};notice='🌳 Park reward collected';break;
  case 'prestige':if(!prestige(g))return {...state,notice:'Need at least 50,000 lifetime Treats'};notice='⭐ Prestige complete';blackjack=null;break;
  case 'skill':if(!buyPrestigeSkill(g,action.skill))return {...state,notice:'Not enough skill points or skill at max level'};notice='⭐ Permanent skill unlocked';break;
  case 'ticket':if(!buyUpgradeTicket(g,action.rarity))return {...state,notice:'Not enough Pup Coins'};notice='🎟️ Ticket purchased';break;
  case 'accessory':if(!buyAccessory(g,action.name))return {...state,notice:'Not enough Pup Coins'};g.accessory=action.name;break;
  case 'equip':if(!g.ownedAccessories.includes(action.name))return no();g.accessory=action.name;break;
  case 'rename':{const name=String(action.name||'').trim().slice(0,18);if(!name)return no();g.puppyName=name;break;}
  case 'casino':{
   if(blackjack)return {...state,notice:'Finish Blackjack before another round'};
   if(!Number.isSafeInteger(action.wager)||action.wager<=0||g.casinoChips<action.wager)return {...state,notice:'Not enough Casino Chips'};
   let result;try{result=casinoRound(action);}catch{return {...state,notice:'Invalid casino bet'};}
   if(!casinoSpend(g,action.wager))return no();
   casinoPayout(g,result.payout);
   notice='🎰 '+action.gameType.toUpperCase()+' · '+(result.prize||result.winKind||result.color||'RESULT')+' · +'+result.payout+' Chips';
   break;
  }
  case 'blackjackStart':{
   if(blackjack)return no();
   if(!Number.isSafeInteger(action.wager)||action.wager<20||action.wager>100000||action.wager%10!==0)return {...state,notice:'Invalid Blackjack wager'};
   if(!Array.isArray(action.cards)||action.cards.length!==4||!action.cards.every(validCard))return no();
   if(!casinoSpend(g,action.wager))return {...state,notice:'Not enough Casino Chips'};
   const player=action.cards.slice(0,2),dealer=action.cards.slice(2,4);
   blackjack={wager:action.wager,player,dealer};
   if(handValue(player)===21||handValue(dealer)===21){
     const payout=handValue(player)===21&&handValue(dealer)===21?action.wager:handValue(player)===21?Math.floor(action.wager*2.5):0;
     casinoPayout(g,payout);notice='🃏 Natural Blackjack settled · +'+payout+' Chips';blackjack=null;
   }else notice='🃏 Blackjack dealt · Hit or Stand';
   break;
  }
  case 'blackjackHit':{
   if(!blackjack||!validCard(action.card))return no();
   const player=[...blackjack.player,action.card];
   blackjack={...blackjack,player};
   if(handValue(player)>21){blackjack=null;notice='🃏 BUST · wager lost';}
   else if(handValue(player)===21)notice='🃏 21! Stand to settle';
   break;
  }
  case 'blackjackStand':{
   if(!blackjack||!Array.isArray(action.dealerCards)||action.dealerCards.length>14||!action.dealerCards.every(validCard))return no();
   const dealer=[...blackjack.dealer];
   let pos=0;
   while(handValue(dealer)<17&&pos<action.dealerCards.length)dealer.push(action.dealerCards[pos++]);
   if(handValue(dealer)<17)return {...state,notice:'Dealer draw incomplete'};
   const settled=scoreBlackjack(blackjack.player,dealer,blackjack.wager);
   casinoPayout(g,settled.payout);notice='🃏 '+settled.result+' · Dealer '+handValue(dealer)+' · +'+settled.payout+' Chips';
   blackjack=null;break;
  }
  case 'clearGuest':return {...initialState(),ready:true,notice:'Guest progress reset'};
  case 'dismiss':return {...state,notice:''};
  default:return no();
 }
 g.lastActiveAt=action.now||Date.now();
 return {game:g,ready:true,notice,blackjack};
}
