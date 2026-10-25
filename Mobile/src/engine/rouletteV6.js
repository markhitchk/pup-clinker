// Part 5C — exact source geometry, bets and saved outcomes from
// PuppyRouletteEngine.kt and PuppyRouletteUi.kt. Isolated guest preview.
import {rouletteOutcome} from './casinoV7.js';

export const ROULETTE_V6=Object.freeze({
 presets:[20,100,500,2500],minWager:20,maxWager:100000,increment:10,
 rtp:'97.30%',spinMs:2600,reducedMotionMs:250,
 wheelOrder:[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,
 5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26],
 wheelHeight:204,wheelBase:190,rotor:154,ball:18,ballOrbit:76,
 wheelTarget:360*5+75,ballCruise:-90-360*7,
 tableWidth:1600,tableHeight:900,gridX:190,gridY:245,zeroX:100,zeroWidth:90,
 cellWidth:95,cellHeight:95,outsideY:570,outsideHeight:105,outsideWidth:1140
});
export const ROULETTE_BETS=Object.freeze([
 {id:'RED',label:'Red',multiplier:2},
 {id:'BLACK',label:'Black',multiplier:2},
 {id:'ODD',label:'Odd',multiplier:2},
 {id:'EVEN',label:'Even',multiplier:2},
 {id:'LOW',label:'1–18',multiplier:2},
 {id:'HIGH',label:'19–36',multiplier:2},
 {id:'STRAIGHT',label:'Straight number',multiplier:36}
]);
export const ROULETTE_OUTSIDE=['LOW','EVEN','RED','BLACK','ODD','HIGH'];
export const ROULETTE_RED_NUMBERS=new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
export const rouletteColor=n=>!Number.isSafeInteger(n)||n<0||n>36?null:n===0?'GREEN':ROULETTE_RED_NUMBERS.has(n)?'RED':'BLACK';
export const rouletteColorHex=n=>({RED:'#B92B35',BLACK:'#16181C',GREEN:'#187A45'})[rouletteColor(n)]||'#16181C';
export const rouletteValidWager=w=>Number.isSafeInteger(w)&&w>=20&&w<=100000&&w%10===0;
export const rouletteValidBet=bet=>!!bet&&ROULETTE_BETS.some(x=>x.id===bet.type)&&(
 bet.type==='STRAIGHT'?Number.isSafeInteger(bet.number)&&bet.number>=0&&bet.number<=36:
 bet.number===undefined||bet.number===null);
export const rouletteBetLabel=bet=>bet?.type==='STRAIGHT'?'Number '+bet.number:
 ROULETTE_BETS.find(x=>x.id===bet?.type)?.label||'Unknown bet';
export function rouletteBoardZones(width){
 const scale=Math.max(0,Number.isFinite(width)?width:0)/ROULETTE_V6.tableWidth;
 const rect=(x,y,w,h)=>({left:x*scale,top:y*scale,width:w*scale,height:h*scale});
 const {gridX,gridY,cellWidth,cellHeight,zeroX,zeroWidth,outsideY,outsideHeight,outsideWidth}=ROULETTE_V6;
 return {
  width:1600*scale,height:900*scale,
  zero:rect(zeroX,gridY,zeroWidth,cellHeight*3),
  numbers:Array.from({length:36},(_,i)=>{
   const number=i+1,column=Math.floor(i/3),row=(column+1)*3-number;
   return {number,...rect(gridX+column*cellWidth,gridY+row*cellHeight,cellWidth,cellHeight)};
  }),
  outside:ROULETTE_OUTSIDE.map((type,i)=>({type,...rect(gridX+i*outsideWidth/6,outsideY,outsideWidth/6,outsideHeight)}))
 };
}
export function rouletteWheelTarget(number){
 const i=ROULETTE_V6.wheelOrder.indexOf(number);
 if(i<0)return null;
 const pocketAngle=-90+i*(360/37)+(360/37)/2;
 return {index:i,pocketAngle,wheel:ROULETTE_V6.wheelTarget,
  ball:ROULETTE_V6.wheelTarget+pocketAngle-360*7};
}
export function newRouletteGuestRound(wager,bet,winningNumber,now=Date.now()){
 if(!rouletteValidWager(wager)||!rouletteValidBet(bet)||!Number.isSafeInteger(now)||now<=0)return null;
 try{
  const settlement=rouletteOutcome(wager,bet,winningNumber);
  return {version:1,game:'ROULETTE',wager,bet:{type:bet.type,...(bet.type==='STRAIGHT'?{number:bet.number}:{})},
   winningNumber,color:settlement.color,won:settlement.won,payout:settlement.payout,
   multiplier:settlement.won?(bet.type==='STRAIGHT'?36:2):0,createdAt:now};
 }catch{return null;}
}
export function validPendingRoulette(r){
 if(!r||r.version!==1||r.game!=='ROULETTE'||!rouletteValidWager(r.wager)||
 !rouletteValidBet(r.bet)||!Number.isSafeInteger(r.createdAt)||r.createdAt<=0)return false;
 const expected=newRouletteGuestRound(r.wager,r.bet,r.winningNumber,r.createdAt);
 return !!expected&&expected.color===r.color&&expected.won===r.won&&
 expected.payout===r.payout&&expected.multiplier===r.multiplier;
}
export function rouletteResult(r){
 if(!validPendingRoulette(r))return null;
 return {winningNumber:r.winningNumber,color:r.color,bet:r.bet,won:r.won,payout:r.payout,multiplier:r.multiplier,wager:r.wager};
}
