// Part 5B — Kotlin PuppySlotsEngine / PuppySlotsUi exact source constants.
// This is a guest-preview round; NOT PupEye authoritative casino settlement.
import {SLOTS,slotsOutcome} from './casinoV7.js';
export const SLOTS_V6=Object.freeze({
 presets:[20,100,500,2500],minWager:20,maxWager:100000,increment:10,
 publishedRtp:'92.66%',reelCount:3,
 cardAspect:0.86,machineWidth:0.80,machineLeft:0.045,machineTop:0.17,
 originalWidth:440,originalHeight:483,reelTop:180/483,reelHeight:182/483,
 reelLeft:[57/440,170/440,285/440],reelWidth:[97/440,98/440,98/440],
 mascotWidth:0.48,mascotX:0.26,mascotYOffset:-0.16,
 leverX:0.91,leverBaseY:0.49,leverY:0.16,
 reelTicks:[16,23,30],tickMs:70,revealMs:2350,reducedMotionRevealMs:250,
 pairReturn:'0.5×'
});
export const SLOT_SYMBOLS=Object.freeze([
 {id:'TREAT',emoji:'🍪',name:'Treat',weight:30,multiplier:7},
 {id:'BALL',emoji:'🎾',name:'Ball',weight:25,multiplier:12},
 {id:'PAW',emoji:'🐾',name:'Paw',weight:20,multiplier:18},
 {id:'TICKET',emoji:'🎟️',name:'Ticket',weight:13,multiplier:35},
 {id:'PUPPY',emoji:'🐶',name:'Puppy',weight:8,multiplier:100},
 {id:'STAR',emoji:'⭐',name:'Star',weight:4,multiplier:500}
]);
export const SLOTS_RULES=[
 'Three reels are rolled independently from the published weights.',
 'A triple uses that symbol’s listed multiplier.',
 'Any pair that is not a triple returns 0.5× the wager.',
 'No matching pair returns 0 Chips.',
 'The Ticket symbol is visual in Slots; this engine pays Casino Chips only.',
 'The exact symbols and payout are committed to the save before reveal.'
];
export const validSlotsWager=w=>Number.isSafeInteger(w)&&w>=SLOTS_V6.minWager&&w<=SLOTS_V6.maxWager&&w%SLOTS_V6.increment===0;
export function slotsFrame(width){
 const w=Math.max(1,Number.isFinite(width)?width:320);
 const h=w/SLOTS_V6.cardAspect,mw=w*SLOTS_V6.machineWidth,mh=mw*SLOTS_V6.originalHeight/SLOTS_V6.originalWidth;
 const left=w*SLOTS_V6.machineLeft,top=h*SLOTS_V6.machineTop;
 const mascotWidth=mw*SLOTS_V6.mascotWidth;
 const at=(x,y,width,height)=>({left:x,top:y,width,height});
 return {width:w,height:h,
  machine:at(left,top,mw,mh),
  mascot:at(left+mw*SLOTS_V6.mascotX,top-mh*0.16,mascotWidth,mascotWidth*131/180),
  reels:SLOTS_V6.reelLeft.map((v,i)=>at(left+mw*v,top+mh*SLOTS_V6.reelTop,
   mw*SLOTS_V6.reelWidth[i],mh*SLOTS_V6.reelHeight)),
  leverBase:at(left+mw*SLOTS_V6.leverX,top+mh*SLOTS_V6.leverBaseY,mw*0.17,mw*0.17*120/81),
  lever:at(left+mw*SLOTS_V6.leverX,top+mh*SLOTS_V6.leverY,mw*0.15,mw*0.15*180/66)};
}
export const slotsMultiplier=r=>r.winKind==='PAIR'?'0.5×':r.winKind==='TRIPLE'?
 (SLOT_SYMBOLS.find(s=>s.emoji===r.symbols[0])?.multiplier||0)+'×':'0×';
export const slotsResultLabel=r=>r.winKind==='PAIR'?'Pair · '+slotsMultiplier(r):r.winKind==='TRIPLE'?'Triple · '+slotsMultiplier(r):'No match';
export function newSlotsGuestRound(wager,rolls,now=Date.now()){
 if(!validSlotsWager(wager)||!Number.isSafeInteger(now)||now<=0)return null;
 try{
  const result=slotsOutcome(wager,rolls);
  return {version:1,game:'SLOTS',wager,rolls:[...rolls],symbols:result.symbols,payout:result.payout,winKind:result.winKind,createdAt:now};
 }catch{return null;}
}
export function validPendingSlots(round){
 if(!round||round.version!==1||round.game!=='SLOTS'||!Number.isSafeInteger(round.createdAt)||round.createdAt<=0||!validSlotsWager(round.wager)||
 !Array.isArray(round.rolls)||round.rolls.length!==3)return false;
 try{
  const expected=slotsOutcome(round.wager,round.rolls);
  return JSON.stringify(round.symbols)===JSON.stringify(expected.symbols)&&round.winKind===expected.winKind&&
   Number.isSafeInteger(round.payout)&&round.payout===expected.payout;
 }catch{return false;}
}
export const slotDrawIndex=(reel,tick)=>((tick+reel*2)%SLOT_SYMBOLS.length);
export function slotsResultFromRound(round){
 if(!validPendingSlots(round))return null;
 return {symbols:round.symbols,payout:round.payout,winKind:round.winKind,wager:round.wager};
}
