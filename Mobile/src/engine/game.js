// Android-derived V7 browser engine from Puppy-Clicker merge ddadb7f; provisional, not authoritative.
// Ported from pup-clinker/Web and Android's GameViewModel: web-specific schema v1.
export const SOURCE = 'https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets';
export const logo = SOURCE + '/logos/puppy_clicker.png';
const v1 = [
  ['classic','Buddy / Classic'],['golden','Sunny'],['poodle','Mochi'],['spotty','Pepper'],
  ['midnight','Midnight'],['cloud','Cloud'],['aurora','Aurora'],['cocoa','Cocoa'],
  ['snowball','Snowball'],['galaxy','Galaxy'],['neon_buddy','Neon Buddy'],
  ['golden_night','Golden Night'],['halloween','Pumpkin Pup'],['santa','Santa Paws'],
  ['birthday','Birthday Buddy'],['dev_pup','Dev Pup'],['secret_snoot','Secret Snoot'],
  ['classic_forever','Classic Forever']
];
const v2 = [
  ['v2_frost','Frost'],['v2_honey','Honey'],['v2_biscuit','Biscuit'],
  ['v2_onyx','Onyx'],['v2_domino','Domino'],['v2_chestnut','Chestnut'],
  ['v2_prism','Prism'],['v2_flurry','Flurry'],['v2_harleytg','HarleyTG Puppy'],
  ['v2_dev_pup','Dev Pup'],['v2_rory','Rory'],['v2_discord_pup','Discord Pup']
];
export const ROSTER = [...v1.map(([id,name],i)=>({id,name,number:i+1,image:SOURCE+'/v1/v1_'+id+'.png'})),
  ...v2.map(([id,name],i)=>({id,name,number:i+19,image:SOURCE+(id==='v2_discord_pup'?'/discord_rewards/':'/v2/')+id+'.png'}))];
export const UPGRADES = [
  {id:'better_treats',name:'Better Treats',emoji:'🦴',description:'+1 per tap',effect:'CLICK',amount:1,base:35},
  {id:'chew_toy',name:'Chew Toy',emoji:'🧸',description:'+1 bonus every 10 active taps',effect:'AUTO',amount:1,base:100},
  {id:'golden_bowl',name:'Golden Bowl',emoji:'🥣',description:'+5 per tap',effect:'CLICK',amount:5,base:750},
  {id:'playmate',name:'Playmate',emoji:'🐕',description:'+5 bonus every 10 active taps',effect:'AUTO',amount:5,base:1500},
  {id:'lucky_collar',name:'Lucky Collar',emoji:'🍀',description:'+3 per tap',effect:'CLICK',amount:3,base:75,rarity:'COMMON',tickets:2},
  {id:'training_whistle',name:'Training Whistle',emoji:'📯',description:'+5 bonus every 10 active taps',effect:'AUTO',amount:5,base:125,rarity:'UNCOMMON',tickets:2},
  {id:'puppy_power',name:'Puppy Power',emoji:'⚡',description:'+25 per tap',effect:'CLICK',amount:25,base:250,rarity:'RARE',tickets:2},
  {id:'dog_park_crew',name:'Dog Park Crew',emoji:'🌳',description:'+25 bonus every 10 active taps',effect:'AUTO',amount:25,base:350,rarity:'RARE',tickets:3},
  {id:'treat_factory',name:'Treat Factory',emoji:'🏭',description:'+100 bonus every 10 active taps',effect:'AUTO',amount:100,base:600,rarity:'EPIC',tickets:3},
  {id:'legendary_snacks',name:'Legendary Snacks',emoji:'✨',description:'+100 per tap',effect:'CLICK',amount:100,base:900,rarity:'LEGENDARY',tickets:2},
];

export const RARITIES = ['COMMON','UNCOMMON','RARE','EPIC','LEGENDARY'];
export const PRESTIGE_SKILLS = ['TAP_TRAINING','AUTO_TRAINING','SMART_SHOPPER','TICKET_SENSE'];
const cap=(value,min=0,max=Number.MAX_SAFE_INTEGER)=>Math.max(min,Math.min(max,Number.isFinite(+value)?Math.trunc(+value):min));
const careFields=['happiness','fullness','energy','cleanliness','bond'];
export const freshProfile=()=>({happiness:100,fullness:100,energy:100,cleanliness:100,bond:10});
export const freshGame=()=>({
  puppyName:'Buddy',puppyStyle:'classic',unlockedPuppies:['classic','golden','poodle','spotty'],
  favoritePuppies:[],accessory:'None',ownedAccessories:['None'],
  treats:0,lifetimeTreats:0,bones:0,pupCoins:0,casinoChips:0,
  clickPower:1,activeBonus:0,autoPerSecond:0,legitimateTaps:0,
  upgrades:Object.fromEntries(UPGRADES.map(u=>[u.id,0])),
  ticketInventory:Object.fromEntries(RARITIES.map(r=>[r,0])),
  ticketShopPurchases:Object.fromEntries(RARITIES.map(r=>[r,0])),
  totalTicketsFound:0,ticketDropSerial:0,lastTicketDrop:null,
  totalTaps:0,combo:0,bestCombo:0,lastTapMs:0,
  ...freshProfile(),careProfiles:{},careUpdatedAtByPuppy:{},
  xp:0,achievementRewardedIds:[],gachaPulls:0,
  careActions:0,totalShopPurchases:0,
  dailyStreak:0,lastDailyClaimDay:null,claimedDailyTasks:[],
  dailyDay:null,dailyTapStart:0,dailyCareStart:0,dailyShopStart:0,
  prestigeCount:0,skillPoints:0,totalPrestigePointsEarned:0,
  prestigeSkills:Object.fromEntries(PRESTIGE_SKILLS.map(s=>[s,0])),
  parkActive:false,parkReadyAtMs:0,redeemedCodeIds:[],
  lastActiveAt:Date.now()
});
export function sanitizeGame(input){
  const b=freshGame();
  if(!input||typeof input!=='object'||Array.isArray(input))return b;
  for(const key of Object.keys(b))if(Object.hasOwn(input,key))b[key]=input[key];
  const counters=['treats','lifetimeTreats','bones','pupCoins','casinoChips','clickPower','activeBonus','legitimateTaps','totalTicketsFound','ticketDropSerial','totalTaps','combo','bestCombo','lastTapMs','careActions','totalShopPurchases','xp','gachaPulls','prestigeCount','skillPoints','totalPrestigePointsEarned','lastActiveAt','parkReadyAtMs','dailyStreak','dailyTapStart','dailyCareStart','dailyShopStart'];
  for(const key of counters)b[key]=cap(b[key],key==='clickPower'?1:0);
  for(const key of careFields)b[key]=cap(b[key],0,100);
  const ids=new Set(ROSTER.map(p=>p.id));
  b.unlockedPuppies=[...new Set(Array.isArray(b.unlockedPuppies)?b.unlockedPuppies.filter(id=>ids.has(id)):[])];
  if(!b.unlockedPuppies.includes('classic'))b.unlockedPuppies.unshift('classic');
  b.favoritePuppies=[...new Set(Array.isArray(b.favoritePuppies)?b.favoritePuppies.filter(id=>ids.has(id)):[])];
  if(!b.unlockedPuppies.includes(b.puppyStyle))b.puppyStyle='classic';
  b.puppyName=String(b.puppyName||'Buddy').slice(0,18);
  b.upgrades=Object.fromEntries(UPGRADES.map(u=>[u.id,cap(b.upgrades?.[u.id],0,1000)]));
  b.ticketInventory=Object.fromEntries(RARITIES.map(r=>[r,cap(b.ticketInventory?.[r],0,99999)]));
  b.ticketShopPurchases=Object.fromEntries(RARITIES.map(r=>[r,cap(b.ticketShopPurchases?.[r])]));
  b.prestigeSkills=Object.fromEntries(PRESTIGE_SKILLS.map(s=>[s,cap(b.prestigeSkills?.[s],0,5)]));
  b.ownedAccessories=[...new Set(['None',...(Array.isArray(b.ownedAccessories)?b.ownedAccessories:[]).filter(s=>['Bandana','Bow','Crown'].includes(s))])];
  if(!b.ownedAccessories.includes(b.accessory))b.accessory='None';
  b.careProfiles=Object.fromEntries(Object.entries(b.careProfiles&&typeof b.careProfiles==='object'?b.careProfiles:{}).filter(([id])=>ids.has(id)).map(([id,p])=>[id,Object.fromEntries(careFields.map(k=>[k,cap(p?.[k],0,100)]))]));
  b.achievementRewardedIds=Array.isArray(b.achievementRewardedIds)?[...new Set(b.achievementRewardedIds.filter(id=>typeof id==='string'&&id.length<60))].slice(0,50):[];
  b.careUpdatedAtByPuppy=Object.fromEntries(Object.entries(b.careUpdatedAtByPuppy&&typeof b.careUpdatedAtByPuppy==='object'?b.careUpdatedAtByPuppy:{}).filter(([id])=>ids.has(id)).map(([id,at])=>[id,cap(at)]));
  b.claimedDailyTasks=Array.isArray(b.claimedDailyTasks)?b.claimedDailyTasks.filter(v=>typeof v==='string').slice(0,50):[];
  b.redeemedCodeIds=Array.isArray(b.redeemedCodeIds)?b.redeemedCodeIds.filter(v=>typeof v==='string').slice(0,100):[];
  b.autoPerSecond=0;
  b.activeBonus=UPGRADES.filter(u=>u.effect==='AUTO').reduce((n,u)=>n+(u.amount+b.prestigeSkills.AUTO_TRAINING)*b.upgrades[u.id],0);
  if(b.dailyDay!==null)b.dailyDay=cap(b.dailyDay);
  if(b.lastDailyClaimDay!==null)b.lastDailyClaimDay=cap(b.lastDailyClaimDay);
  return b;
}
export const level=g=>1+Math.floor(Math.sqrt(g.lifetimeTreats/100));
export const wellness=g=>Math.floor((g.happiness+g.fullness+g.energy+g.cleanliness)/4);
export const careTapBonus=g=>wellness(g)>=85&&g.bond>=50?2:wellness(g)>=70?1:0;
export const mood=g=>wellness(g)>=90&&g.bond>=70?'Best friend':wellness(g)>=85?'Thrilled':wellness(g)>=65?'Happy':wellness(g)>=40?'Okay':wellness(g)>=20?'Needs care':'Very tired';
export const format=n=>new Intl.NumberFormat('en-US',{notation:Math.abs(n)>=10000?'compact':'standard',maximumFractionDigits:1}).format(Math.floor(n||0));
export function withGain(g,amount){const gain=cap(amount);g.treats=cap(g.treats+gain);g.lifetimeTreats=cap(g.lifetimeTreats+gain);}
export function tap(g,now=Date.now(),rng=Math.random){
  // Android refuses tired-puppy taps. Browser fair play is not PupEye attestation.
  if(g.energy<=10)return null;
  const next=++g.totalTaps;g.legitimateTaps++;
  g.combo=now-g.lastTapMs<=900?Math.min(50,g.combo+1):1;
  g.bestCombo=Math.max(g.bestCombo,g.combo);g.lastTapMs=now;
  const bonus=g.legitimateTaps%10===0?g.activeBonus:0;
  withGain(g,g.clickPower+careTapBonus(g)+bonus);
  if(g.legitimateTaps%20===0)g.bones=cap(g.bones+1);
  if(g.legitimateTaps%250===0)g.pupCoins=cap(g.pupCoins+1);
  if(next%12===0)g.energy=Math.max(0,g.energy-1);
  if(next%15===0)g.happiness=Math.min(100,g.happiness+1);
  if(Math.floor(rng()*500)===0){
    const roll=Math.floor(rng()*10000);
    const rarity=roll<7800?'COMMON':roll<9350?'UNCOMMON':roll<9850?'RARE':roll<9980?'EPIC':'LEGENDARY';
    g.ticketInventory[rarity]=cap(g.ticketInventory[rarity]+1);
    g.totalTicketsFound++;g.ticketDropSerial++;g.lastTicketDrop=rarity;
    return rarity;
  }
  return null;
}
export function passive(g,_seconds){g.autoPerSecond=0;g.lastActiveAt=Date.now();}
// Integer-only V7 cost growth (ceil at each purchase level).
export function cost(g,u){
  let price=u.base;
  const multiplier=u.rarity?135:155;
  for(let i=0;i<cap(g.upgrades[u.id],0,1000);i++)price=Math.ceil(price*multiplier/100);
  const discount=100-5*cap(g.prestigeSkills?.SMART_SHOPPER,0,5);
  return Math.max(1,Math.floor(price*discount/100));
}
export const boneCost=u=>(u.amount>=100?12:u.amount>=25?7:u.amount>=5?3:1)*(u.rarity?2:1);
export function ticketCost(g,u){
  if(!u.rarity)return 0;
  const base=u.tickets+Math.min(2,Math.floor((g.upgrades[u.id]||0)/3));
  const sense=cap(g.prestigeSkills?.TICKET_SENSE,0,5);
  return Math.max(1,base-(sense>=2?1:0)-(sense>=4?1:0));
}
export function purchase(g,u){
  const amount=cost(g,u),bones=boneCost(u),tickets=ticketCost(g,u);
  if(g.treats<amount||g.bones<bones||(u.rarity&&(g.ticketInventory[u.rarity]||0)<tickets))return false;
  g.treats-=amount;g.bones-=bones;if(u.rarity)g.ticketInventory[u.rarity]-=tickets;
  g.upgrades[u.id]=(g.upgrades[u.id]||0)+1;
  const tapSkill=cap(g.prestigeSkills?.TAP_TRAINING,0,5),activeSkill=cap(g.prestigeSkills?.AUTO_TRAINING,0,5);
  if(u.effect==='CLICK')g.clickPower+=u.amount+tapSkill;
  g.activeBonus=UPGRADES.filter(x=>x.effect==='AUTO').reduce((sum,x)=>sum+(x.amount+activeSkill)*g.upgrades[x.id],0);
  g.autoPerSecond=0;g.totalShopPurchases++;g.happiness=Math.min(100,g.happiness+2);
  return true;
}
const CARE_COSTS={feed:20,play:1,wash:10,rest:0,cuddle:0};
export function care(g,kind){
  if(!(kind in CARE_COSTS))return false;
  if(kind==='feed'){
    if(g.treats<20||g.fullness>=100)return false;
    g.treats-=20;g.fullness=Math.min(100,g.fullness+25);g.happiness=Math.min(100,g.happiness+5);
    g.bond=Math.min(100,g.bond+1);g.cleanliness=Math.max(0,g.cleanliness-1);
  }else if(kind==='play'){
    if(g.bones<1||g.energy<12||(g.happiness>=100&&g.bond>=100))return false;
    g.bones--;g.happiness=Math.min(100,g.happiness+20);g.fullness=Math.max(0,g.fullness-4);
    g.energy-=12;g.cleanliness=Math.max(0,g.cleanliness-3);g.bond=Math.min(100,g.bond+3);
  }else if(kind==='wash'){
    if(g.treats<10||g.cleanliness>=100)return false;
    g.treats-=10;g.cleanliness=Math.min(100,g.cleanliness+35);
    g.happiness=Math.min(100,g.happiness+4);g.bond=Math.min(100,g.bond+2);
  }else if(kind==='rest'){
    if(g.energy>=100)return false;
    g.energy=Math.min(100,g.energy+30);g.happiness=Math.min(100,g.happiness+3);
  }else if(kind==='cuddle'){
    if(g.happiness>=100&&g.bond>=100)return false;
    g.happiness=Math.min(100,g.happiness+8);g.bond=Math.min(100,g.bond+2);
  }
  g.careActions++;
  return true;
}
export function switchPuppy(g,id){
  if(!g.unlockedPuppies.includes(id)||!ROSTER.some(p=>p.id===id))return false;
  g.careProfiles[g.puppyStyle]=Object.fromEntries(careFields.map(k=>[k,g[k]]));
  const profile=g.careProfiles[id]||freshProfile();
  for(const key of careFields)g[key]=profile[key];
  g.puppyStyle=id;g.puppyName=ROSTER.find(p=>p.id===id).name.split(' / ')[0];
  return true;
}
export function convertTreatsToChips(g,amount){
  if(!Number.isSafeInteger(amount)||amount<=0||amount%10!==0||g.treats<amount)return false;
  g.treats-=amount;g.casinoChips=cap(g.casinoChips+amount/10);return true;
}
export function casinoSpend(g,chips){
  if(!Number.isSafeInteger(chips)||chips<=0||g.casinoChips<chips)return false;
  g.casinoChips-=chips;return true;
}
export function casinoPayout(g,chips){g.casinoChips=cap(g.casinoChips+cap(chips));}
export const ticketShopCost=(rarity,purchased)=>{
  const base={COMMON:250,UNCOMMON:750,RARE:2500,EPIC:7500,LEGENDARY:25000}[rarity];
  if(!base)return null;
  return Math.floor(base*Math.min(24,4+Math.max(0,Math.floor(purchased)))/4);
};
export function buyUpgradeTicket(g,rarity){
  const price=ticketShopCost(rarity,g.ticketShopPurchases[rarity]||0);
  if(price===null||g.pupCoins<price)return false;
  g.pupCoins-=price;g.ticketInventory[rarity]++;g.ticketShopPurchases[rarity]++;return true;
}
export const accessoryPrices={Bandana:150,Bow:250,Crown:1000};
export function buyAccessory(g,name){
  if(name==='None')return true;
  if(!(name in accessoryPrices))return false;
  if(g.ownedAccessories.includes(name))return true;
  if(g.pupCoins<accessoryPrices[name])return false;
  g.pupCoins-=accessoryPrices[name];g.ownedAccessories.push(name);return true;
}
export function claimDailyReward(g,epochDay=Math.floor(Date.now()/86400000)){
  if(g.lastDailyClaimDay===epochDay)return false;
  const streak=g.lastDailyClaimDay===epochDay-1?Math.min(365,g.dailyStreak+1):1;
  const reward=150+streak*25+level(g)*10;
  withGain(g,reward);g.bones+=2;g.pupCoins+=3;
  g.lastDailyClaimDay=epochDay;g.dailyStreak=streak;g.happiness=Math.min(100,g.happiness+8);g.bond=Math.min(100,g.bond+1);
  return true;
}
export function startPark(g,now=Date.now()){
  if(g.parkActive||g.energy<20)return false;
  g.parkActive=true;g.parkReadyAtMs=now+60000;g.energy-=20;return true;
}
export function claimPark(g,now=Date.now()){
  if(!g.parkActive||now<g.parkReadyAtMs)return false;
  withGain(g,250+level(g)*50);
  g.happiness=Math.min(100,g.happiness+12);
  g.fullness=Math.max(0,g.fullness-5);g.cleanliness=Math.max(0,g.cleanliness-5);
  g.bond=Math.min(100,g.bond+3);g.parkActive=false;g.parkReadyAtMs=0;return true;
}
export function drawCard(){return 1+Math.floor(Math.random()*13);}
export function handValue(cards){
  let aces=0,score=0;
  for(const card of cards){if(card===1){aces++;score+=11;}else score+=Math.min(card,10);}
  while(score>21&&aces){score-=10;aces--;}return score;
}


export const prestigePointsAvailable=g=>g.lifetimeTreats<50000?0:Math.min(8,1+Math.floor((g.lifetimeTreats-50000)/100000));
export const skillCost=(g,skill)=>1+Math.floor((g.prestigeSkills[skill]||0)/2);
export function prestige(g){
  const points=prestigePointsAvailable(g);
  if(points<=0)return false;
  g.treats=0;g.lifetimeTreats=0;g.bones=0;g.clickPower=1;g.activeBonus=0;g.autoPerSecond=0;
  g.legitimateTaps=0;g.upgrades=Object.fromEntries(UPGRADES.map(u=>[u.id,0]));
  g.totalShopPurchases=0;g.ticketInventory=Object.fromEntries(RARITIES.map(r=>[r,0]));
  g.lastTicketDrop=null;
  for(const field of ['happiness','fullness','energy','cleanliness'])g[field]=100;
  g.careProfiles[g.puppyStyle]={happiness:100,fullness:100,energy:100,cleanliness:100,bond:g.bond};
  g.careActions=0;g.totalTaps=0;g.combo=0;g.bestCombo=0;g.lastTapMs=0;
  g.parkActive=false;g.parkReadyAtMs=0;g.lastDailyClaimDay=null;g.dailyStreak=0;
  g.dailyTapStart=0;g.dailyCareStart=0;g.dailyShopStart=0;g.claimedDailyTasks=[];
  g.prestigeCount++;g.skillPoints=cap(g.skillPoints+points);
  g.totalPrestigePointsEarned=cap(g.totalPrestigePointsEarned+points);
  // Pup Coins, Casino Chips, accessories, unlocked puppies and permanent skills survive.
  return true;
}
export function buyPrestigeSkill(g,skill){
  if(!PRESTIGE_SKILLS.includes(skill))return false;
  const current=g.prestigeSkills[skill]||0,price=skillCost(g,skill);
  if(current>=5||g.skillPoints<price)return false;
  g.skillPoints-=price;g.prestigeSkills[skill]=current+1;
  return true;
}
