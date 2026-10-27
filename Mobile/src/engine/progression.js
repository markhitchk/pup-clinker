import {xpLevel,addBoundedXp} from "./balancedProgression.js";
// Source parity: PuppyAchievementsV6.kt and PuppyProgression.kt.
// This XP belongs solely to the isolated, untrusted guest preview.
const bounded=n=>Number.isFinite(n)?Math.min(Number.MAX_SAFE_INTEGER,Math.max(0,Math.trunc(n))):0;
const eventXp={MANUAL_TAP:1,CARE_ACTION:5,DAILY_TASK:25,ACHIEVEMENT:50,NEW_PUPPY:100,EVENT_REWARD:50};
export const guestLevel=g=>xpLevel(g.xp);
export const ACHIEVEMENTS=[
 {id:'first_treat',title:'First Treat',emoji:'🐾',target:1,description:'Earn your first lifetime Treat.',progress:g=>g.lifetimeTreats},
 {id:'snack_stash',title:'Snack Stash',emoji:'🍪',target:100,description:'Earn 100 lifetime Treats.',progress:g=>g.lifetimeTreats},
 {id:'puppy_pro',title:'Puppy Pro',emoji:'🏆',target:1000,description:'Earn 1,000 lifetime Treats.',progress:g=>g.lifetimeTreats},
 {id:'combo_hero',title:'Combo Hero',emoji:'🔥',target:20,description:'Reach a 20 tap combo.',progress:g=>g.bestCombo},
 {id:'ticket_hunter',title:'Ticket Hunter',emoji:'🎟️',target:1,description:'Find your first Upgrade Ticket.',progress:g=>g.totalTicketsFound},
 {id:'big_taps',title:'Big Taps',emoji:'💪',target:25,description:'Reach 25 Treats per tap.',progress:g=>g.clickPower},
 {id:'auto_pup',title:'Bone Collector',emoji:'🦴',target:50,description:'Collect 50 Bones through active play.',progress:g=>g.bones},
 {id:'happy_home',title:'Happy Home',emoji:'💖',target:90,description:'Keep all four care meters at 90 or higher.',progress:g=>Math.min(g.happiness,g.fullness,g.energy,g.cleanliness)},
 {id:'level_ten',title:'Best Friend',emoji:'💜',target:10,description:'Reach player level 10.',progress:g=>guestLevel(g)}
].map(x=>({...x,xpReward:50}));
export function grantGuestXp(g,event){
 const amount=eventXp[event];
 if(!amount)return false;
 g.xp=addBoundedXp(g.xp,amount);
 return true;
}
export const achievementStatuses=g=>{
 const rewarded=new Set(Array.isArray(g.achievementRewardedIds)?g.achievementRewardedIds:[]);
 return ACHIEVEMENTS.map(a=>{
  const progress=bounded(a.progress(g));
  return {...a,progress,completed:progress>=a.target,xpRewarded:rewarded.has(a.id)};
 });
};
export function settleGuestAchievements(g){
 if(!Array.isArray(g.achievementRewardedIds))g.achievementRewardedIds=[];
 const rewarded=new Set(g.achievementRewardedIds);
 const granted=[];
 // XP from an achievement can itself cause level ten; re-evaluate until fixed point.
 for(let pass=0;pass<ACHIEVEMENTS.length;pass++){
  let any=false;
  for(const a of ACHIEVEMENTS){
   if(rewarded.has(a.id)||bounded(a.progress(g))<a.target)continue;
   rewarded.add(a.id);granted.push(a.id);grantGuestXp(g,'ACHIEVEMENT');any=true;
  }
  if(!any)break;
 }
 g.achievementRewardedIds=[...rewarded];
 return granted;
}
