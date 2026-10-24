import {localEpochDay} from './monthlyRewards.js';
export const INBOX_LIMIT=100;
const categories=['reward','pup','system'];
export function sanitizeInbox(input){
 if(!Array.isArray(input))return [];
 const ids=new Set();
 return input.filter(n=>n&&typeof n.id==='string'&&n.id.length<=100&&!ids.has(n.id)&&ids.add(n.id)&&categories.includes(n.category)&&typeof n.title==='string'&&typeof n.body==='string'&&Number.isSafeInteger(n.createdAt)&&n.createdAt>0).slice(0,INBOX_LIMIT).map(n=>({id:n.id,title:n.title.slice(0,180),body:n.body.slice(0,1000),category:n.category,createdAt:n.createdAt,read:n.read===true,action:['daily','parkClaim'].includes(n.action)?n.action:null}));
}
function append(g,item){
 if(g.notificationSeenIds.includes(item.id))return;
 g.notificationSeenIds=[item.id,...g.notificationSeenIds].slice(0,500);
 g.notificationInbox=[{...item,read:false},...g.notificationInbox].slice(0,INBOX_LIMIT);
}
export function reconcileInbox(g,now){
 const day=localEpochDay(new Date(now));
 g.notificationInbox=g.notificationInbox.map(n=>{
  const staleDaily=n.action==='daily'&&(n.id!==`daily:${day}`||g.lastDailyClaimDay===day);
  const stalePark=n.action==='parkClaim'&&(!g.parkActive||g.parkReadyAtMs>now);
  return staleDaily||stalePark?{...n,action:null}:n;
 });
}
export function checkInbox(g,now){
 const day=localEpochDay(new Date(now));
 if(g.dailyRewardNotifications&&g.lastDailyClaimDay!==day)append(g,{id:`daily:${day}`,category:'reward',title:'Daily Puppy Reward available',body:'Your daily Puppy Clicker reward is ready to claim.',createdAt:now,action:'daily'});
 if(g.gameEventNotifications&&g.parkActive&&g.parkReadyAtMs<=now)append(g,{id:`park:${g.parkReadyAtMs}`,category:'pup',title:'Dog Park Adventure complete',body:'Your puppy is back from the park. Open Rewards to collect.',createdAt:now,action:'parkClaim'});
 reconcileInbox(g,now);
}
export function addTestNotification(g,now){append(g,{id:`test:${now}`,category:'system',title:'Test notification',body:'Puppy Clicker notifications are working.',createdAt:now,action:null});}
// A concrete schedule plan, also used for cancellation on category changes.
export function reminderPlan(g,now){
 const plan=[];
 if(g.dailyRewardNotifications){const day=localEpochDay(new Date(now));const next=new Date(now);next.setHours(10,0,0,0);if(next.getTime()<=now||g.lastDailyClaimDay===day)next.setDate(next.getDate()+1);plan.push({id:'puppy-daily',category:'reward',title:'Daily Puppy Reward',body:'Check your daily Puppy Clicker reward.',at:next.getTime()});}
 if(g.gameEventNotifications&&g.parkActive&&g.parkReadyAtMs>now)plan.push({id:'puppy-park',category:'pup',title:'Dog Park Adventure complete',body:'Your puppy is ready to come home. Open Rewards.',at:g.parkReadyAtMs});
 if(g.puppyAttentionNotifications)plan.push({id:'puppy-attention',category:'pup',title:'Your puppy misses you!',body:'Come back to care for your puppy.',at:now+4*60*60*1000});
 return plan;
}
