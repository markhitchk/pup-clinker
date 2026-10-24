// Kotlin PuppyScratchersEngine + PuppyScratchersUi guest-preview interaction.
// This is NOT server-authoritative casino settlement or PupEye validation.
import {SCRATCHERS,scratcherOutcome} from './casinoV7.js';

// Kotlin PuppyScratchersUi scratch field and token geometry.
export const SCRATCH_COLUMNS=48;
export const SCRATCH_ROWS=20;
export const SCRATCH_REVEAL_THRESHOLD=0.68;
export const SCRATCH_FRAME={width:1426,height:1077,cardX:200,cardY:482,cardWidth:1013,cardHeight:443};
export const SCRATCH_COIN_DIAMETER=56;
export const SCRATCH_COIN_ASPECT=960/973;
export const SCRATCH_PUBLISHED_RTP='91%';
export const SCRATCH_PRIZES=Object.freeze([
 {id:'NO_PRIZE',emoji:'🐾',label:'No prize',multiplier:0},
 {id:'REFUND',emoji:'🍪',label:'Treat Refund',multiplier:100},
 {id:'DOUBLE',emoji:'🦴',label:'Double Treats',multiplier:200},
 {id:'BIG_WIN',emoji:'⭐',label:'Big Win',multiplier:500},
 {id:'JACKPOT',emoji:'💎',label:'Jackpot',multiplier:2000}
]);
export const SCRATCH_CARD_META={
 pup_scratch:{shortName:'Pup',tagline:'The classic Puppy Casino scratcher.'},
 bone_bonanza:{shortName:'Bone',tagline:'More double-Treat hits with a lean prize table.'},
 lucky_fetch:{shortName:'Fetch',tagline:'A balanced mid-tier card for bigger Treat stacks.'},
 golden_paw:{shortName:'Gold',tagline:'Higher stakes with more refund protection.'},
 casino_scratch:{shortName:'Casino',tagline:'A high-stakes card with a boosted jackpot chance.'},
 ultra_pup:{shortName:'Ultra',tagline:'The premium scratcher with more big-win and jackpot hits.'}
};
const PRIZES={
 NO_PRIZE:{symbol:null,multiplier:0},
 REFUND:{symbol:'🍪',multiplier:100},
 DOUBLE:{symbol:'🦴',multiplier:200},
 BIG_WIN:{symbol:'⭐',multiplier:500},
 JACKPOT:{symbol:'💎',multiplier:2000}
};

export function validPendingScratch(round){
 if(!round||typeof round!=='object'||Array.isArray(round)||round.type!=='SCRATCHERS'||round.version!==1)return false;
 const card=SCRATCHERS.find(x=>x.wager===round.wager);
 const prize=PRIZES[round.prize];
 if(!card||!prize||round.card!==card.name||!Number.isSafeInteger(round.createdAt)||round.createdAt<=0)return false;
 const expected=prize.symbol?Array(3).fill(prize.symbol):['🐾','🍪','🦴'];
 return Array.isArray(round.symbols)&&round.symbols.length===3&&
  round.symbols.every((s,i)=>s===expected[i])&&
  Number.isSafeInteger(round.payout)&&round.payout===Math.floor(round.wager*prize.multiplier/100);
}

export function newPendingScratch(wager,roll,now=Date.now()){
 if(!SCRATCHERS.some(c=>c.wager===wager)||!Number.isSafeInteger(now)||now<=0)return null;
 try{
  const result=scratcherOutcome(wager,roll);
  const pending={type:'SCRATCHERS',version:1,wager,card:result.card,
   prize:result.prize,symbols:result.symbols,payout:result.payout,createdAt:now};
  return validPendingScratch(pending)?pending:null;
 }catch{return null;}
}

export function scratchCoverage(cells){
 return Math.min(1,cells.size/(SCRATCH_COLUMNS*SCRATCH_ROWS));
}

// Native 56dp Pup Coin, weighted follow and rotation physics.
export const clamp=(n,lo,hi)=>Math.max(lo,Math.min(hi,n));
export const scratchRadius=(speed,bounds,coinRadius=SCRATCH_COIN_DIAMETER/2)=>{
 const cell=Math.min(bounds.width/SCRATCH_COLUMNS,bounds.height/SCRATCH_ROWS);
 const boost=clamp(Math.max(0,speed)/2.4,0,1);
 return Math.max(cell*1.45,coinRadius*(0.34+boost*0.10));
};
export function scratchCoinAt(previous,target,delta,dt,bounds,coinDiameter=SCRATCH_COIN_DIAMETER){
 const radius=Math.min(coinDiameter/2,Math.max(1,bounds.width/2),Math.max(1,bounds.height/2));
 const aspect=SCRATCH_COIN_ASPECT;
 const coinBounds={x0:radius*aspect,x1:Math.max(radius*aspect,bounds.width-radius*aspect),y0:radius,y1:Math.max(radius,bounds.height-radius)};
 const to={x:clamp(target.x,coinBounds.x0,coinBounds.x1),y:clamp(target.y,coinBounds.y0,coinBounds.y1)};
 const from=previous||{...to,rotation:0};
 const speed=Math.hypot(delta.x,delta.y)/clamp(dt,1,50);
 const follow=clamp(0.68+speed/12,0.68,0.88);
 return {x:clamp(from.x+(to.x-from.x)*follow,coinBounds.x0,coinBounds.x1),
  y:clamp(from.y+(to.y-from.y)*follow,coinBounds.y0,coinBounds.y1),
  rotation:clamp((from.rotation||0)*0.82+delta.x*.10+delta.y*.02,-28,28)};
}
export function markScratchTrail(previous,from,to,bounds,elapsedMs=16,coinRadius=SCRATCH_COIN_DIAMETER/2){
 if(!bounds||bounds.width<=0||bounds.height<=0||!from||!to)return previous;
 if(![bounds.width,bounds.height,from.x,from.y,to.x,to.y].every(Number.isFinite))return previous;
 const cells=new Set(previous);
 const cellW=bounds.width/SCRATCH_COLUMNS,cellH=bounds.height/SCRATCH_ROWS;
 const distance=Math.hypot(to.x-from.x,to.y-from.y);
 const speed=distance/clamp(elapsedMs,1,50);
 const radius=scratchRadius(speed,bounds,coinRadius);
 const steps=Math.max(1,Math.ceil(distance/Math.max(4,radius*.42)));
 for(let n=0;n<=steps;n++){
  const f=n/steps;
  const x=clamp(from.x+(to.x-from.x)*f,0,bounds.width),y=clamp(from.y+(to.y-from.y)*f,0,bounds.height);
  const left=clamp(Math.floor((x-radius)/cellW),0,SCRATCH_COLUMNS-1);
  const right=clamp(Math.floor((x+radius)/cellW),0,SCRATCH_COLUMNS-1);
  const top=clamp(Math.floor((y-radius)/cellH),0,SCRATCH_ROWS-1);
  const bottom=clamp(Math.floor((y+radius)/cellH),0,SCRATCH_ROWS-1);
  for(let row=top;row<=bottom;row++)for(let col=left;col<=right;col++){
   const dx=(col+.5)*cellW-x,dy=(row+.5)*cellH-y;
   if(dx*dx+dy*dy<=radius*radius)cells.add(row*SCRATCH_COLUMNS+col);
  }
 }
 return cells;
}
export function addAccessibleScratchProgress(previous,fraction){
 const cells=new Set(previous),total=SCRATCH_COLUMNS*SCRATCH_ROWS;
 const target=Math.min(total,Math.ceil(fraction*total));
 // The same mask model advances in consistent 10% chunks for TalkBack.
 for(let i=0;i<total&&cells.size<target;i++){
  const col=i%SCRATCH_COLUMNS,row=Math.floor(i/SCRATCH_COLUMNS);
  cells.add(row*SCRATCH_COLUMNS+col);
 }
 return cells;
}
