// Direct parity port of PuppyMonthlyRewards.kt and V6ViewModel.claimDailyTask.
// Data-only schedules may change goals, not reward currencies or game logic.
// All credits below are LOCAL GUEST preview credits, never protected PupEye saves.
export const MONTHLY_REWARDS_URL='https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/rewards';
export const MONTHLY_REWARDS_CACHE_KEY='puppy_monthly_rewards_v1';
export const MONTHLY_REWARDS_REFRESH_MS=15*60*1000;
export const MONTHLY_REWARDS_MAX_BYTES=512*1024;
const MAX_GOALS_PER_DAY=12;
const METRICS=new Set(['taps','care','shop','wellness','bond','tickets']);
const isWhole=(n,min,max)=>Number.isSafeInteger(n)&&n>=min&&n<=max;

export function monthKey(date=new Date()){
 if(!(date instanceof Date)||!Number.isFinite(date.getTime()))throw new RangeError('Invalid local date');
 return date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0');
}
// Matches java.time.LocalDate.toEpochDay using the DEVICE'S local date, not UTC.
export function localEpochDay(date=new Date()){
 return Math.floor(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())/86400000);
}
export function fallbackGoals(day){
 const taps=[50,75,90,100][Math.max(day-1,0)%4];
 const care=[3,4,5][Math.max(day-1,0)%3];
 return [
  {id:'tap_time',metric:'taps',emoji:'🐾',title:'Tap Time',description:'Tap your puppy '+taps+' times',target:taps,rewardTreats:taps*4},
  {id:'good_care',metric:'care',emoji:'💖',title:'Good Care',description:'Complete '+care+' care actions',target:care,rewardTreats:150+care*40},
  {id:'shop_visit',metric:'shop',emoji:'🛍️',title:'Shop Visit',description:'Buy 1 upgrade',target:1,rewardTreats:400},
  {id:'wellness',metric:'wellness',emoji:'✨',title:'Healthy Pup',description:'Reach 85% wellness',target:85,rewardTreats:420}
 ];
}
export function fallbackSchedule(date=new Date()){
 const days={};
 const count=new Date(date.getFullYear(),date.getMonth()+1,0).getDate();
 for(let day=1;day<=count;day++)days[day]=fallbackGoals(day);
 return {month:monthKey(date),days,source:'fallback'};
}
export function parseMonthlySchedule(raw,date=new Date(),source='github'){
 if(typeof raw!=='string'||raw.length>MONTHLY_REWARDS_MAX_BYTES)throw new RangeError('Invalid monthly rewards size');
 const root=JSON.parse(raw);
 if(!root||root.schemaVersion!==1||root.month!==monthKey(date)||
    !root.days||typeof root.days!=='object'||Array.isArray(root.days))throw new Error('Unsupported or mismatched monthly rewards schema');
 const dayCount=new Date(date.getFullYear(),date.getMonth()+1,0).getDate();
 const days={};
 for(const [dayText,items] of Object.entries(root.days)){
  if(!/^(?:[1-9]|[12][0-9]|3[01])$/.test(dayText))continue;
  const day=Number(dayText);
  if(day>dayCount||!Array.isArray(items))continue;
  const goals=[],seen=new Set();
  // Kotlin inspects at most the first 12 entries, including disabled entries.
  for(const item of items.slice(0,MAX_GOALS_PER_DAY)){
   if(!item||typeof item!=='object'||Array.isArray(item)||item.enabled===false)continue;
   if(typeof item.id!=='string'||!/^[a-z0-9_-]{1,48}$/.test(item.id)||seen.has(item.id))continue;
   if(typeof item.type!=='string'||!METRICS.has(item.type.toLowerCase()))continue;
   if(!isWhole(item.target,1,1000000)||!isWhole(item.rewardTreats,1,100000))continue;
   const metric=item.type.toLowerCase();
   if((metric==='wellness'||metric==='bond')&&item.target>100)continue;
   seen.add(item.id);
   const text=(value,otherwise,max)=>String(typeof value==='string'?value:otherwise).slice(0,max);
   goals.push({id:item.id,metric,emoji:text(item.emoji,'🎯',8),title:text(item.title,item.id,64),
    description:text(item.description,'',140),target:item.target,rewardTreats:item.rewardTreats});
  }
  if(goals.length)days[day]=goals;
 }
 if(!Object.keys(days).length)throw new Error('No valid monthly reward days');
 return {month:root.month,days,source};
}
export function goalsForDay(schedule,date=new Date()){
 return (schedule?.month===monthKey(date)&&Array.isArray(schedule.days?.[date.getDate()])&&
  schedule.days[date.getDate()].length)?schedule.days[date.getDate()]:fallbackGoals(date.getDate());
}
export function progressForGoal(g,goal,date=new Date()){
 const today=localEpochDay(date);
 const isToday=g.dailyDay===today;
 switch(goal.metric){
  case 'taps':return isToday?Math.max(0,g.totalTaps-g.dailyTapStart):0;
  case 'care':return isToday?Math.max(0,g.careActions-g.dailyCareStart):0;
  case 'shop':return isToday?Math.max(0,g.totalShopPurchases-g.dailyShopStart):0;
  case 'wellness':return Math.max(0,Math.min(100,Math.floor((g.happiness+g.fullness+g.energy+g.cleanliness)/4)));
  case 'bond':return g.bond;
  case 'tickets':return Object.values(g.ticketInventory||{}).reduce((a,b)=>a+(Number.isSafeInteger(b)?b:0),0);
  default:return 0;
 }
}
export function rollDailyDay(g,date=new Date()){
 const day=localEpochDay(date);
 if(g.dailyDay===day)return false;
 g.dailyDay=day;
 g.dailyTapStart=g.totalTaps;
 g.dailyCareStart=g.careActions;
 g.dailyShopStart=g.totalShopPurchases;
 g.claimedDailyTasks=[];
 return true;
}
export function claimDailyGoal(g,goalId,schedule,date=new Date()){
 rollDailyDay(g,date);
 if(typeof goalId!=='string'||g.claimedDailyTasks.includes(goalId))return false;
 const goal=goalsForDay(schedule,date).find(item=>item.id===goalId);
 if(!goal||progressForGoal(g,goal,date)<goal.target)return false;
 g.treats=Math.min(Number.MAX_SAFE_INTEGER,g.treats+goal.rewardTreats);
 g.lifetimeTreats=Math.min(Number.MAX_SAFE_INTEGER,g.lifetimeTreats+goal.rewardTreats);
 g.bones=Math.min(Number.MAX_SAFE_INTEGER,g.bones+1);
 g.pupCoins=Math.min(Number.MAX_SAFE_INTEGER,g.pupCoins+1);
 g.claimedDailyTasks=[...g.claimedDailyTasks,goalId];
 return true;
}
