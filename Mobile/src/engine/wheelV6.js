// Part 5F: Kotlin PuppyLuckyWheelEngine.kt and PuppyLuckyWheelUi.kt.
// Local guest-only casino: NEVER claim authoritative puppy ownership.
import {WHEEL_PRIZES,wheelOutcome} from './casinoV7.js';
export const WHEEL_V6=Object.freeze({
 wagers:[20,100,500,2500],minWager:20,maxWager:100000,increment:20,
 chipRtp:'82.5%',spinMs:2650,rotationMs:2500,reducedMotionMs:250,
 referenceWidth:420,artAspect:420/447,discRatio:.82,discY:.075,
 pointerRatio:.12,pointerAspect:100/160,pointerY:.018,
 pointerClicks:18,pointerClickTimings:[30,38,32],pointerDelayBase:25,
 pointerDelayStep:2,turns:6,latestVersion:1
});
export const WHEEL_SEGMENTS=Object.freeze(WHEEL_PRIZES.map((p,i)=>Object.freeze({
 id:p.id,index:i,weight:p.weight,emoji:p.emoji,multiplierHundredths:p.multi,
 label:['MISS','0.5×','1×','2×','5×','10×','PUP'][i],
 description:['No Treat Prize','0.5× Treats','1× Treats','2× Treats','5× Treats','10× Treats','Pup Unlock'][i]
})));
export const WHEEL_PROTECTED_REWARD_POOL=Object.freeze([
 'midnight','cloud','aurora','cocoa','snowball','galaxy','neon_buddy','golden_night',
 'v2_frost','v2_honey','v2_biscuit','v2_onyx','v2_domino','v2_chestnut','v2_prism','v2_flurry'
]);
// Those 16 IDs are source-only metadata; never grant them from client-side previews.
export const WHEEL_GUEST_UNLOCKS_ENABLED=false;
export const validWheelWager=w=>Number.isSafeInteger(w)&&w>=WHEEL_V6.minWager&&
 w<=WHEEL_V6.maxWager&&w%WHEEL_V6.increment===0;
export function wheelCenterAngle(index){
 if(!Number.isSafeInteger(index)||index<0||index>=WHEEL_SEGMENTS.length)return null;
 const before=WHEEL_SEGMENTS.slice(0,index).reduce((n,p)=>n+p.weight,0);
 return -90+before*360/100+WHEEL_SEGMENTS[index].weight*180/100;
}
export function wheelTargetDegrees(index){
 const a=wheelCenterAngle(index);
 return a===null?null:WHEEL_V6.turns*360+(-90-a);
}
export function wheelLabelPosition(index,size){
 const segment=WHEEL_SEGMENTS[index];
 if(!segment||!Number.isFinite(size)||size<=0)return null;
 const angle=wheelCenterAngle(index)*Math.PI/180;
 const radius=size*(segment.weight<=2?.38:segment.weight<=5?.335:segment.weight<=10?.30:.27);
 const width=size*(segment.weight<=2?.12:segment.weight<=5?.14:.17);
 return {left:size/2+radius*Math.cos(angle)-width/2,
  top:size/2+radius*Math.sin(angle)-8,width,fontSize:segment.weight<=2?7:segment.weight<=5?8:segment.weight<=10?9:10};
}
export function wheelFrame(width){
 // Kotlin LuckyWheelBoard uses Modifier.fillMaxWidth() — the 420 reference\n // is an aspect-ratio basis, not an upper width cap.\n const w=Math.max(1,Number.isFinite(width)?width:320);
 const h=w/WHEEL_V6.artAspect,disc=w*WHEEL_V6.discRatio,ptr=w*WHEEL_V6.pointerRatio;
 return {width:w,height:h,disc:{left:(w-disc)/2,top:h*WHEEL_V6.discY,width:disc,height:disc},
  pointer:{left:(w-ptr)/2,top:h*WHEEL_V6.pointerY,width:ptr,height:ptr/WHEEL_V6.pointerAspect}};
}
export function newWheelGuestRound(wager,roll,now=Date.now()){
 if(!validWheelWager(wager)||!Number.isSafeInteger(roll)||roll<0||roll>=100||
 !Number.isSafeInteger(now)||now<=0)return null;
 try{
  // Native falls back to a full refund when the 2% unlock is not eligible.
  const result=wheelOutcome(wager,roll,false);
  const index=WHEEL_SEGMENTS.findIndex(s=>s.id===result.prize);
  if(index<0||result.prize==='PUPPY_UNLOCK')return null;
  return {type:'LUCKY_WHEEL',version:WHEEL_V6.latestVersion,
   wager,roll,prize:result.prize,segmentIndex:index,
   payout:result.payout,puppyStyleId:null,createdAt:now};
 }catch{return null;}
}
export function validPendingWheel(record){
 if(!record||record.type!=='LUCKY_WHEEL'||record.version!==WHEEL_V6.latestVersion||
 record.puppyStyleId!==null)return false;
 const expected=newWheelGuestRound(record.wager,record.roll,record.createdAt);
 return !!expected&&expected.prize===record.prize&&
 expected.segmentIndex===record.segmentIndex&&expected.payout===record.payout;
}
export const wheelResult=round=>validPendingWheel(round)?{
 wager:round.wager,prize:round.prize,segmentIndex:round.segmentIndex,
 payout:round.payout,puppyStyleId:null
}:null;
export const wheelResultLabel=result=>{
 const segment=WHEEL_SEGMENTS.find(s=>s.id===result?.prize);
 return segment?segment.description+' · '+result.payout+' Chips returned':'Choose a wager and spin.';
};
