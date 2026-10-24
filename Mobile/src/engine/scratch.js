// Kotlin PuppyScratchersEngine + PuppyScratchersUi guest-preview interaction.
// This is NOT server-authoritative casino settlement or PupEye validation.
import {SCRATCHERS,scratcherOutcome} from './casinoV7.js';

export const SCRATCH_COLUMNS=24;
export const SCRATCH_ROWS=10;
export const SCRATCH_REVEAL_THRESHOLD=0.68;
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

// Kotlin uses 48x20 coverage and a coin-physics Canvas; tiles are a deliberate
// performance-limited preview fallback. Interpolation prevents gaps on fast swipes.
export function markScratchTrail(previous,from,to,bounds,elapsedMs=16){
 if(!bounds||bounds.width<=0||bounds.height<=0||!from||!to)return previous;
 if(![bounds.width,bounds.height,from.x,from.y,to.x,to.y].every(Number.isFinite))return previous;
 const cells=new Set(previous);
 const cellW=bounds.width/SCRATCH_COLUMNS,cellH=bounds.height/SCRATCH_ROWS;
 const distance=Math.hypot(to.x-from.x,to.y-from.y);
 const speed=distance/Math.max(1,elapsedMs);
 const radius=Math.max(Math.min(cellW,cellH)*1.45,
  Math.min(bounds.width,bounds.height)*(0.075+Math.min(0.025,speed/120)));
 const steps=Math.max(1,Math.ceil(distance/(Math.min(cellW,cellH)*0.6)));
 for(let n=0;n<=steps;n++){
  const f=n/steps, x=Math.min(bounds.width,Math.max(0,from.x+(to.x-from.x)*f));
  const y=Math.min(bounds.height,Math.max(0,from.y+(to.y-from.y)*f));
  const left=Math.max(0,Math.floor((x-radius)/cellW));
  const right=Math.min(SCRATCH_COLUMNS-1,Math.floor((x+radius)/cellW));
  const top=Math.max(0,Math.floor((y-radius)/cellH));
  const bottom=Math.min(SCRATCH_ROWS-1,Math.floor((y+radius)/cellH));
  for(let row=top;row<=bottom;row++)for(let col=left;col<=right;col++){
   const dx=(col+0.5)*cellW-x,dy=(row+0.5)*cellH-y;
   if(dx*dx+dy*dy<=radius*radius)cells.add(row*SCRATCH_COLUMNS+col);
  }
 }
 return cells;
}
