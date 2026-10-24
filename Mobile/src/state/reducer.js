import {freshGame,tap,care,purchase,switchPuppy,convertTreatsToChips,casinoSpend,casinoPayout,claimDailyReward,startPark,claimPark,prestige,buyPrestigeSkill,buyUpgradeTicket,buyAccessory,handValue} from '../engine/game.js';
import {slotsOutcome,rouletteOutcome,plinkoOutcome,wheelOutcome} from '../engine/casinoV7.js';
import {applyGacha} from '../engine/gacha.js';
import {grantGuestXp,settleGuestAchievements} from '../engine/progression.js';
import {applyOfflineCare,switchCareProfile} from '../engine/careDecay.js';
import {newRoundFromDeck,hit as bjHit,stand as bjStand,doubleDown as bjDouble,split as bjSplit,blackjackOutcome,validBlackjackState} from '../engine/blackjack.js';
import {ONBOARDING_STEPS,validBirthday,normalizeLocalGuestName} from '../engine/onboarding.js';
import {fallbackSchedule,rollDailyDay,claimDailyGoal,localEpochDay} from '../engine/monthlyRewards.js';
import {newPendingScratch,validPendingScratch} from '../engine/scratch.js';

export const initialState=()=>({game:freshGame(),blackjack:null,notice:'Local guest mode · no official cloud sync',ready:false,monthlySchedule:fallbackSchedule()});
const clone=game=>JSON.parse(JSON.stringify(game));
const validCard=n=>Number.isInteger(n)&&n>=1&&n<=13;
function casinoRound(action){
 switch(action.gameType){
  case 'slots':return slotsOutcome(action.wager,action.rolls);
  case 'roulette':return rouletteOutcome(action.wager,action.bet,action.roll);
  case 'plinko':return plinkoOutcome(action.wager,action.path);
  case 'wheel':return wheelOutcome(action.wager,action.roll,false);
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
 if(action.type==='monthlySchedule')return {...state,monthlySchedule:action.schedule};
 if(action.type==='hydrate'){
  const game=action.game?clone(action.game):freshGame();
  if(action.game)applyOfflineCare(game,action.now||Date.now());
  // Kotlin normalizes the active LocalDate on load. Daily progress must not
  // display the previous day's counters until the first minute timer fires.
  rollDailyDay(game,new Date(action.now||Date.now()));
  const blackjack=validBlackjackState(game.casinoRound)?game.casinoRound:null;
  game.casinoRound=blackjack;
  game.pendingScratch=validPendingScratch(game.pendingScratch)?game.pendingScratch:null;
  return {...state,game,blackjack,ready:true};
 }
 if(!state.ready)return state;
 const g=clone(state.game), no=()=>state;
 let notice='';
 let blackjack=state.blackjack;
 let gachaResult=state.gachaResult||null;
 switch(action.type){
  case 'onboardNext':
   if(g.onboardingComplete||g.onboardingStep>=ONBOARDING_STEPS.length-1)return state;
   g.onboardingStep++;
   break;
  case 'onboardBack':
   if(g.onboardingComplete||g.onboardingStep<=0)return state;
   g.onboardingStep--;
   break;
  case 'onboardPlayer':{
   const name=normalizeLocalGuestName(action.name);
   if(!name)return {...state,notice:'Enter a local preview name'};
   g.guestName=name;break;
  }
  case 'onboardBirthday':
   if(!validBirthday(action.month,action.day))return {...state,notice:'Choose a valid month and day or skip'};
   g.birthdayMonth=action.month;g.birthdayDay=action.day;break;
  case 'setPreference':
   if(!['hapticsEnabled','animationsEnabled','compactNumbers'].includes(action.name)||typeof action.value!=='boolean')return no();
   g[action.name]=action.value;
   notice='⚙️ Gameplay preference updated';
   break;
  case 'onboardNotifications':
   // Preference only, no Android permission is requested before native integration.
   g.notificationsOptIn=action.enabled===true;
   break;
  case 'onboardFinish':
   if(g.onboardingComplete||g.onboardingStep!==ONBOARDING_STEPS.length-1)return state;
   g.onboardingComplete=true;
   notice='🐶 Welcome, '+g.guestName+'! Your local preview is ready.';
   break;
  case 'resume':applyOfflineCare(g,action.now||Date.now());rollDailyDay(g,new Date(action.now||Date.now()));break;
  case 'rollDailyDay':if(!rollDailyDay(g,new Date(action.now||Date.now())))return no();break;
  case 'tap': {
   if((action.now||Date.now())<(g.cooldownUntilMs||0))return {...state,notice:'🐶👁️ Fair-play cooldown active'};
   rollDailyDay(g,new Date(action.now||Date.now()));
   let i=0;
   const ticket=tap(g,action.now,()=>action.randoms?.[i++]??0.99);
   if(g.totalTaps===state.game.totalTaps)return {...state,notice:'⚡ Your puppy needs rest'};
   grantGuestXp(g,'MANUAL_TAP');
   if(ticket)notice='🎟️ '+ticket+' Upgrade Ticket found!';
   break;
  }
  case 'care': rollDailyDay(g,new Date(action.now||Date.now()));if(!care(g,action.kind))return {...state,notice:'This care action is unavailable'};grantGuestXp(g,'CARE_ACTION');notice='💖 Puppy care complete';break;
  case 'purchase': {
   // Never settle from caller-provided upgrade price/effect fields.
   const upgradeId=typeof action.upgradeId==='string'?action.upgradeId:action.upgrade?.id;
   rollDailyDay(g,new Date(action.now||Date.now()));
   if(!purchase(g,{id:upgradeId}))return {...state,notice:'Upgrade unavailable or insufficient Treats, Bones or Tickets'};
   notice='🛍️ Upgrade purchased';break;
  }
  case 'switch': if(!switchCareProfile(g,action.id,action.now||Date.now()))return no();break;
  case 'favorite':{
   if(!Array.isArray(g.favoritePuppies)||!g.unlockedPuppies.includes(action.id))return no();
   g.favoritePuppies=g.favoritePuppies.includes(action.id)?g.favoritePuppies.filter(id=>id!==action.id):[...g.favoritePuppies,action.id];
   break;
  }
  case 'gacha':{
   const result=applyGacha(g,action.payment,action.roll);
   if(!result.success)return {...state,notice:'Gacha unavailable: '+result.failure};
   if(result.isNewUnlock)grantGuestXp(g,'NEW_PUPPY');
   gachaResult=result;notice=result.isNewUnlock?'🐶 NEW PUPPY: '+result.puppyName:'🐶 Duplicate capsule: '+result.puppyName;
   break;
  }
  case 'dismissGacha':return {...state,gachaResult:null};
  case 'exchange':if(!convertTreatsToChips(g,action.amount))return {...state,notice:'Exchange requires Treats in multiples of 10'};notice='🎰 Casino Chips credited';break;
  case 'daily':{
   // Kotlin resolves LocalDate.now() inside claimDailyReward, not from a UI-supplied day.
   const date=new Date(action.now||Date.now());
   rollDailyDay(g,date);
   if(!claimDailyReward(g,localEpochDay(date)))return {...state,notice:'Daily reward already claimed'};
   notice='🎁 Daily reward collected';break;
  }
  case 'claimDailyGoal':if(!claimDailyGoal(g,action.goalId,state.monthlySchedule,new Date(action.now||Date.now())))return {...state,notice:'Goal incomplete or already claimed'};notice='🎯 Daily goal completed · Treats, Bone and Pup Coin credited';break;
  case 'parkStart':if(!startPark(g,action.now))return {...state,notice:'Need 20 energy, or a walk is already active'};notice='🌳 Walk started';break;
  case 'parkClaim':if(!claimPark(g,action.now))return {...state,notice:'Walk is not finished'};notice='🌳 Park reward collected';break;
  case 'prestige':if(blackjack||g.pendingScratch)return {...state,notice:'Finish your current Casino round before prestiging'};if(!prestige(g,localEpochDay(new Date(action.now||Date.now()))))return {...state,notice:'Need at least 50,000 lifetime Treats'};notice='⭐ Prestige complete';blackjack=null;break;
  case 'skill':if(!buyPrestigeSkill(g,action.skill))return {...state,notice:'Not enough skill points or skill at max level'};notice='⭐ Permanent skill unlocked';break;
  case 'ticket':if(!buyUpgradeTicket(g,action.rarity))return {...state,notice:'Not enough Pup Coins'};notice='🎟️ Ticket purchased';break;
  case 'accessory':if(!buyAccessory(g,action.name))return {...state,notice:'Not enough Pup Coins'};g.accessory=action.name;break;
  case 'equip':if(!g.ownedAccessories.includes(action.name))return no();g.accessory=action.name;break;
  case 'rename':{const name=String(action.name||'').trim().slice(0,18);if(!name)return no();g.puppyName=name;break;}
  case 'casino':{
   if(blackjack)return {...state,notice:'Finish Blackjack before another round'};
   if(g.pendingScratch)return {...state,notice:'Finish the current Scratchers card before another wager'};
   if(action.gameType==='scratch'){
    if(!Number.isSafeInteger(action.wager)||g.casinoChips<action.wager)return {...state,notice:'Not enough Casino Chips'};
    const pending=newPendingScratch(action.wager,action.roll,action.now||Date.now());
    if(!pending)return {...state,notice:'Invalid Scratchers wager'};
    if(!casinoSpend(g,action.wager))return no();
    g.pendingScratch=pending;
    notice='🎟️ Scratch your card to reveal the committed prize';
    break;
   }
   if(!Number.isSafeInteger(action.wager)||action.wager<=0||g.casinoChips<action.wager)return {...state,notice:'Not enough Casino Chips'};
   let result;try{result=casinoRound(action);}catch{return {...state,notice:'Invalid casino bet'};}
   if(!casinoSpend(g,action.wager))return no();
   casinoPayout(g,result.payout);
   notice='🎰 '+action.gameType.toUpperCase()+' · '+(result.prize||result.winKind||result.color||'RESULT')+' · +'+result.payout+' Chips';
   break;
  }
  case 'scratchReveal':{
   if(!validPendingScratch(g.pendingScratch))return {...state,notice:'No valid Scratchers card to settle'};
   const payout=g.pendingScratch.payout;
   const prize=g.pendingScratch.prize;
   casinoPayout(g,payout);
   g.pendingScratch=null;
   notice='🎟️ '+prize.replaceAll('_',' ')+' · +'+payout+' Casino Chips';
   break;
  }
  case 'blackjackStart':{
   if(g.pendingScratch)return {...state,notice:'Finish your Scratchers card before dealing Blackjack'};
   if(blackjack)return {...state,notice:'Finish your active Blackjack round'};
   if(!Number.isSafeInteger(action.wager)||g.casinoChips<action.wager)return {...state,notice:'Not enough Casino Chips'};
   let next;try{next=newRoundFromDeck(action.wager,action.deck);}catch{return {...state,notice:'Invalid wager or card deck'};}
   if(!casinoSpend(g,action.wager))return no();
   if(next.complete){
    const result=blackjackOutcome(next);casinoPayout(g,result.totalPayout);
    notice='🃏 Natural Blackjack settled · +'+result.totalPayout+' Chips';blackjack=null;
   }else{blackjack=next;notice='🃏 Deal complete · Hit, Stand, Double or Split';}
   break;
  }
  case 'blackjackHit':
  case 'blackjackStand':
  case 'blackjackDouble':
  case 'blackjackSplit':{
   if(!blackjack||!validBlackjackState(blackjack))return {...state,notice:'No valid Blackjack round'};
   const op=action.type==='blackjackHit'?bjHit:action.type==='blackjackStand'?bjStand:action.type==='blackjackDouble'?bjDouble:bjSplit;
   let result;try{result=op(blackjack);}catch{return {...state,notice:'Blackjack deck error'};}
   if(!result.success)return {...state,notice:'Blackjack move unavailable: '+result.error};
   if(result.additionalWager){
    if(!casinoSpend(g,result.additionalWager))return {...state,notice:'Not enough Chips to '+(action.type==='blackjackSplit'?'split':'double')};
   }
   blackjack=result.state;
   if(blackjack.complete){
    const outcome=blackjackOutcome(blackjack);
    casinoPayout(g,outcome.totalPayout);
    notice='🃏 '+outcome.hands.map(h=>h.result).join(' / ')+' · +'+outcome.totalPayout+' Chips';
    blackjack=null;
   }else notice='🃏 '+action.type.replace('blackjack','')+' · Hand '+(blackjack.activeHandIndex+1)+' / '+blackjack.hands.length;
   break;
  }
  case 'clearGuest':return {...initialState(),monthlySchedule:state.monthlySchedule,ready:true,notice:'Guest progress reset'};
  case 'dismiss':return {...state,notice:''};
  default:return no();
 }
 const now=action.now||Date.now();
 g.lastActiveAt=now;
 g.casinoRound=blackjack;
 if(!g.careUpdatedAtByPuppy||typeof g.careUpdatedAtByPuppy!=='object')g.careUpdatedAtByPuppy={};
 g.careUpdatedAtByPuppy[g.puppyStyle]=now;
 if(!g.careProfiles||typeof g.careProfiles!=='object')g.careProfiles={};
 g.careProfiles[g.puppyStyle]={happiness:g.happiness,fullness:g.fullness,energy:g.energy,cleanliness:g.cleanliness,bond:g.bond};
 const granted=settleGuestAchievements(g);
 if(granted.length)notice=(notice?notice+' · ':'')+'🏆 '+granted.length+' achievement'+(granted.length===1?'':'s')+' earned (+50 XP each)';
 return {...state,game:g,ready:true,notice,blackjack,gachaResult};
}
