// Guest-only casino outcomes from Puppy-Clicker ddadb7f; server settlement still needed.
// Deterministic browser ports of Android PuppySlotsEngine/PuppyRouletteEngine/
// PuppyScratchersEngine/PuppyPlinkoEngine/PuppyLuckyWheelEngine.
// These outcomes are for isolated web-play only. Server-authoritative
// settlement is required before cross-device/global progression can use them.
const int=(n,min,max)=>Number.isSafeInteger(n)&&n>=min&&n<=max;
const assertWager=(amount,incr=10)=>{
  if(!int(amount,20,100_000)||amount%incr!==0)throw new RangeError('Invalid Casino Chip wager');
};
export const SLOTS=[
  {symbol:'🍪',weight:30,triple:7},
  {symbol:'🎾',weight:25,triple:12},
  {symbol:'🐾',weight:20,triple:18},
  {symbol:'🎟️',weight:13,triple:35},
  {symbol:'🐶',weight:8,triple:100},
  {symbol:'⭐',weight:4,triple:500}
];
function slotAt(roll){
 if(!int(roll,0,99))throw new RangeError('Invalid reel roll');
 let cur=0;for(const item of SLOTS){cur+=item.weight;if(roll<cur)return item;}throw Error('Invalid symbol weights');
}
export function slotsOutcome(wager,rolls){
 assertWager(wager);
 if(!Array.isArray(rolls)||rolls.length!==3)throw new RangeError('Expected three reels');
 const icons=rolls.map(slotAt);
 const unique=new Set(icons.map(x=>x.symbol)).size;
 const payout=unique===1?wager*icons[0].triple:unique===2?Math.floor(wager/2):0;
 return {symbols:icons.map(x=>x.symbol),payout,winKind:unique===1?'TRIPLE':unique===2?'PAIR':'LOSS'};
}
const RED=new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
export function rouletteOutcome(wager,bet,number){
 assertWager(wager);
 if(!int(number,0,36))throw new RangeError('Invalid roulette pocket');
 const color=number===0?'GREEN':RED.has(number)?'RED':'BLACK';
 const type=typeof bet==='string'?bet:bet?.type;
 const winning=typeof bet==='string'?null:bet?.number;
 const match=type==='RED'?color==='RED':
  type==='BLACK'?color==='BLACK':
  type==='EVEN'?number!==0&&number%2===0:
  type==='ODD'?number%2===1:
  type==='LOW'?number>=1&&number<=18:
  type==='HIGH'?number>=19&&number<=36:
  type==='STRAIGHT'?int(winning,0,36)&&number===winning:false;
 if(!['RED','BLACK','EVEN','ODD','LOW','HIGH','STRAIGHT'].includes(type))throw new RangeError('Invalid roulette bet');
 return {number,color,won:match,payout:match?wager*(type==='STRAIGHT'?36:2):0};
}
export const SCRATCHERS=[
 {id:'pup_scratch',name:'Pup Scratch',emoji:'🐾',wager:20,odds:[60,22,12,5,1]},
 {id:'bone_bonanza',name:'Bone Bonanza',emoji:'🦴',wager:100,odds:[58,14,26,1,1]},
 {id:'lucky_fetch',name:'Lucky Fetch',emoji:'🎾',wager:500,odds:[55,20,23,1,1]},
 {id:'golden_paw',name:'Golden Paw',emoji:'🌟',wager:2500,odds:[52,26,20,1,1]},
 {id:'casino_scratch',name:'Casino Scratch',emoji:'🎰',wager:10000,odds:[58,32,7,1,2]},
 {id:'ultra_pup',name:'Ultra Pup',emoji:'💎',wager:25000,odds:[60,31,5,2,2]}
];
const PRIZES=[
 {id:'NO_PRIZE',multi:0,icon:'🐾'},
 {id:'REFUND',multi:100,icon:'🍪'},
 {id:'DOUBLE',multi:200,icon:'🦴'},
 {id:'BIG_WIN',multi:500,icon:'⭐'},
 {id:'JACKPOT',multi:2000,icon:'💎'}
];
export function scratcherOutcome(wager,roll){
 assertWager(wager,20);if(!int(roll,0,99))throw new RangeError('Invalid scratch roll');
 const card=SCRATCHERS.find(x=>x.wager===wager),odds=card?.odds||SCRATCHERS[0].odds;
 let cursor=0,index=0;
 for(let i=0;i<odds.length;i++){cursor+=odds[i];if(roll<cursor){index=i;break;}}
 const prize=PRIZES[index];
 return {card:card?.name||'Classic',prize:prize.id,symbols:prize.id==='NO_PRIZE'?['🐾','🍪','🦴']:Array(3).fill(prize.icon),payout:Math.floor(wager*prize.multi/100)};
}
export const PLINKO_MULTIPLIERS=[5,25,50,100,150,100,50,25,5];
export function plinkoOutcome(wager,pathRight){
 assertWager(wager,20);
 if(!Array.isArray(pathRight)||pathRight.length!==8||pathRight.some(x=>typeof x!=='boolean'))throw new RangeError('Plinko requires eight bounces');
 const binIndex=pathRight.filter(Boolean).length;
 const multiplierHundredths=PLINKO_MULTIPLIERS[binIndex];
 return {pathRight:[...pathRight],binIndex,multiplierHundredths,payout:Math.floor(wager*multiplierHundredths/100)};
}
export const WHEEL_PRIZES=[
 {id:'MISS',weight:38,multi:0,emoji:'🐾'},
 {id:'HALF',weight:25,multi:50,emoji:'🍪'},
 {id:'REFUND',weight:20,multi:100,emoji:'🦴'},
 {id:'DOUBLE',weight:10,multi:200,emoji:'⭐'},
 {id:'FIVE_X',weight:4,multi:500,emoji:'🌟'},
 {id:'TEN_X',weight:1,multi:1000,emoji:'💎'},
 {id:'PUPPY_UNLOCK',weight:2,multi:0,emoji:'🐶'}
];
export function wheelOutcome(wager,roll,unlockAvailable=false){
 assertWager(wager,20);if(!int(roll,0,99))throw new RangeError('Invalid wheel roll');
 let cursor=0,chosen;
 for(const p of WHEEL_PRIZES){cursor+=p.weight;if(roll<cursor){chosen=p;break;}}
 const prize=chosen.id==='PUPPY_UNLOCK'&&!unlockAvailable?WHEEL_PRIZES[2]:chosen;
 return {prize:prize.id,emoji:prize.emoji,payout:Math.floor(wager*prize.multi/100)};
}
