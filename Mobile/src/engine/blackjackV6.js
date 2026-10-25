// Part 5A: Presentation contract from PuppyBlackjackUi.kt and
// PuppyBlackjackLayout.kt. All casino wallet mutations remain guest-only.
import {blackjackTable,BLACKJACK_TABLE_ASPECT} from './blackjackLayout.js';
import {handTotal,cardLabel,isValidWager,canHit,canStand,canDouble,canSplit} from './blackjack.js';
export const BLACKJACK_V6=Object.freeze({
 wagerPresets:Object.freeze([20,100,500,2500]),
 tableMaxWidth:560,landscapeWidth:430,landscapeThreshold:600,
 viewportReserve:300,largeTextReserve:370,tableMinHeight:280,tableMaxHeight:500,
 actionRowThreshold:520,actionHeight:48,actionGap:8,actionRowGap:6,
 dealerStand:17,splitHandLimit:3,
 chipWalletLabel:'🐾 Casino Chip Wallet',
 rules:Object.freeze([
  ['Dealer','Stands on soft 17'],
  ['Natural Blackjack','3:2 profit · 2.5× total'],
  ['Normal win','2× total'],
  ['Push','1× wager returned'],
  ['Double','First two cards · one card then stand'],
  ['Split','Up to 3 player hands'],
  ['Split aces','One card each']
 ])
});
export function blackjackViewportWidth(contentWidth,viewportHeight,fontScale=1){
 const w=Number.isFinite(contentWidth)?contentWidth:360;
 const h=Number.isFinite(viewportHeight)?viewportHeight:700;
 const availableHeight=Math.max(BLACKJACK_V6.tableMinHeight,Math.min(BLACKJACK_V6.tableMaxHeight,h-(fontScale>1.3?BLACKJACK_V6.largeTextReserve:BLACKJACK_V6.viewportReserve)));
 const wide=w>=BLACKJACK_V6.landscapeThreshold&&w>h;
 return Math.max(1,Math.min(w,wide?BLACKJACK_V6.landscapeWidth:BLACKJACK_V6.tableMaxWidth,availableHeight*BLACKJACK_TABLE_ASPECT));
}
export const blackjackActions=(round,chips)=>({
 hit:!!round&&!round.complete&&canHit(round),
 stand:!!round&&!round.complete&&canStand(round),
 double:!!round&&!round.complete&&canDouble(round)&&chips>=round.hands[round.activeHandIndex].wager,
 split:!!round&&!round.complete&&canSplit(round)&&chips>=round.hands[round.activeHandIndex].wager
});
export const canDealBlackjack=(chips,wager,round,pendingScratch)=>!round&&!pendingScratch&&isValidWager(wager)&&chips>=wager;
export function blackjackHandSummaries(round,outcome){
 if(!round)return [];
 return round.hands.map((hand,index)=>{
  const value=handTotal(hand.cards),result=outcome?.hands?.[index];
  const status=result?result.result.charAt(0)+result.result.slice(1).toLowerCase()+' · '+result.payout+' returned':
    value.total>21?'Bust':hand.stood?'Standing':
    index===round.activeHandIndex&&!round.complete?'Your turn':round.complete?'Round complete':'Waiting';
  return {index,total:value.total,soft:value.soft,wager:hand.wager,status,active:index===round.activeHandIndex&&!round.complete};
 });
}
export const blackjackDealerCaption=round=>!round?'Choose a wager to deal.':round.complete?
 'Dealer · '+handTotal(round.dealerCards).total+' points':
 'Dealer · '+cardLabel(round.dealerCards[0])+' showing · hole card hidden';
export const blackjackDisplayRound=(active,last)=>active||last?.state||null;
