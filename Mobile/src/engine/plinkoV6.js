// Part 5D — exact Kotlin PuppyPlinkoEngine / PuppyPlinkoUi constants and
// deterministic guest-only round validation. No Supabase/PupEye ledger.
import {plinkoOutcome,PLINKO_MULTIPLIERS} from './casinoV7.js';

export const PLINKO_V6=Object.freeze({
 rows:8,binCount:9,betPresets:[20,100,500,2500],
 minWager:20,maxWager:100000,wagerIncrement:20,
 rtp:'97.3%',maxJitter:220,
 dropMs:2200,ballMs:2050,reducedMotionMs:250,
 maxBoardWidth:460,boardAspect:4/5,stageHeadroom:96,boardTop:58,
 headerWidthFraction:0.92,headerAspect:3,
 innerCenterX:0.50,xStep:0.042,launchY:0.205,pocketY:0.89,
 bounceLift:0.009,ballFraction:0.10,
 pegsTop:0.105,pegsWidth:0.84,pegsHeight:0.73,
 binOverlayWidth:0.88,binAspect:4,binBottom:0.028,binPadding:56/800
});
export const PLINKO_BINS=Object.freeze([...PLINKO_MULTIPLIERS]);
export const validPlinkoWager=w=>Number.isSafeInteger(w)&&w>=PLINKO_V6.minWager&&w<=PLINKO_V6.maxWager&&w%PLINKO_V6.wagerIncrement===0;
export function plinkoLabel(multiplierHundredths){
 if(!Number.isSafeInteger(multiplierHundredths)||multiplierHundredths<0)return '0×';
 return String(multiplierHundredths/100)+'×';
}
export function newPlinkoGuestRound(wager,pathRight,jitter,now=Date.now()){
 if(!validPlinkoWager(wager)||!Array.isArray(pathRight)||pathRight.length!==8||
 pathRight.some(x=>typeof x!=='boolean')||!Array.isArray(jitter)||jitter.length!==8||
 jitter.some(x=>!Number.isSafeInteger(x)||Math.abs(x)>PLINKO_V6.maxJitter)||
 !Number.isSafeInteger(now)||now<=0)return null;
 try{
  const o=plinkoOutcome(wager,pathRight);
  return {version:2,game:'PLINKO',wager,pathRight:[...pathRight],
   bounceJitterPermille:[...jitter],binIndex:o.binIndex,
   multiplierHundredths:o.multiplierHundredths,payout:o.payout,createdAt:now};
 }catch{return null;}
}
export function validPendingPlinko(r){
 if(!r||r.version!==2||r.game!=='PLINKO')return false;
 const checked=newPlinkoGuestRound(r.wager,r.pathRight,r.bounceJitterPermille,r.createdAt);
 return !!checked&&checked.binIndex===r.binIndex&&checked.multiplierHundredths===r.multiplierHundredths&&checked.payout===r.payout;
}
export const plinkoResult=r=>validPendingPlinko(r)?{
 pathRight:[...r.pathRight],bounceJitterPermille:[...r.bounceJitterPermille],
 binIndex:r.binIndex,multiplierHundredths:r.multiplierHundredths,payout:r.payout,wager:r.wager
}:null;
export const plinkoFrame=contentWidth=>{
 const width=Math.min(PLINKO_V6.maxBoardWidth,Math.max(1,Number.isFinite(contentWidth)?contentWidth:360));
 const height=width/PLINKO_V6.boardAspect;
 const stageHeight=height+PLINKO_V6.stageHeadroom;
 const binWidth=width*PLINKO_V6.binOverlayWidth;
 return {boardWidth:width,boardHeight:height,stageHeight,
  boardTop:PLINKO_V6.boardTop,headerWidth:width*PLINKO_V6.headerWidthFraction,
  headerHeight:width*PLINKO_V6.headerWidthFraction/PLINKO_V6.headerAspect,
  bin:{left:(width-binWidth)/2,top:height-height*PLINKO_V6.binBottom-binWidth/PLINKO_V6.binAspect,
   width:binWidth,height:binWidth/PLINKO_V6.binAspect}};
};
export function plinkoBallAt(outcome,progress){
 const p=PLINKO_V6;
 const safe=Math.max(0,Math.min(1,Number.isFinite(progress)?progress:0));
 const scaled=safe*(p.rows+1);
 const completed=Math.min(p.rows,Math.floor(scaled));
 const fraction=Math.max(0,Math.min(1,scaled-completed));
 const path=outcome?.pathRight||[];
 const jitter=outcome?.bounceJitterPermille||[];
 let x=p.innerCenterX;
 for(let i=0;i<Math.min(completed,path.length);i++)x+=(path[i]?1:-1)*p.xStep;
 if(completed<p.rows&&completed<path.length){
  x+=(path[completed]?1:-1)*p.xStep*fraction;
 }
 const arc=Math.sin(fraction*Math.PI);
 x+=p.xStep*(jitter[completed]||0)/1000*arc;
 const y=p.launchY+(p.pocketY-p.launchY)*safe-(completed<p.rows?p.bounceLift*arc:0);
 return {x,y};
}
export function plinkoAnimationSamples(outcome){
 const inputRange=[],xs=[],ys=[];
 for(let segment=0;segment<PLINKO_V6.rows+1;segment++){
  for(const fraction of [0,0.5]){
   const at=(segment+fraction)/(PLINKO_V6.rows+1);
   const point=plinkoBallAt(outcome,at);
   inputRange.push(at);xs.push(point.x);ys.push(point.y);
  }
 }
 inputRange.push(1);const last=plinkoBallAt(outcome,1);xs.push(last.x);ys.push(last.y);
 return {inputRange,xs,ys};
}
