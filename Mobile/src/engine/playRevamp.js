// Shipped layout: seasonal.gradle.kts runs patch_roster_navigation.py followed
// by patch_main_ui_revamp.py; it launches PuppyRevampedPlayScreen, not V6Play.
import {goalsForDay,progressForGoal,localEpochDay} from './monthlyRewards.js';
export const MAIN_TABS=Object.freeze([['play','🐾','Play'],['care','💖','Care'],['roster','🐶','Roster'],['shop','🛍️','Shop'],['rewards','🎁','Rewards']]);
export function revampedPlayLayout(height){
 const compact=height<700,tiny=height<590;
 return {compact,tiny,portrait:tiny?122:compact?150:185,gap:tiny?5:8,tapHeight:tiny?44:50,stageRadius:22};
}
export function nextPlayReward(g,schedule,now){
 const date=new Date(now),claimed=g.dailyDay===localEpochDay(date)?g.claimedDailyTasks:[];
 const goal=goalsForDay(schedule,date).find(goal=>!claimed.includes(goal.id));
 if(!goal)return {title:'Today complete',progress:1,target:1,rewardTreats:0,allClaimed:true};
 return {title:'Next Reward · '+goal.title,progress:Math.min(goal.target,Math.max(0,progressForGoal(g,goal,date))),target:goal.target,rewardTreats:goal.rewardTreats,allClaimed:false};
}

// Android Play uses measured space between system/header bars and bottom tabs.
// Reserve space for stats, energy, tap and reward before sizing the artwork.
export function androidPlayLayout(viewportHeight){
 const height=Math.max(0,Number(viewportHeight)||0);
 const base=revampedPlayLayout(height);
 const reserved=39+76+50+3*base.gap;
 const portrait=Math.max(68,Math.min(base.portrait,Math.floor(height-reserved-96)));
 return {...base,portrait,minStageHeight:height<440?145:180};
}
