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


// Browser-inspired native Play layout follows the app's UI Scale setting.
export function v2PlayLayout(height,uiScale='default'){
 const h=Math.max(0,Number(height)||0);
 const scale=uiScale==='compact'?0.94:uiScale==='large'?1.08:1;
 const short=h<570;
 const gap=Math.max(4,Math.round((short?6:10)*scale));
 const headingHeight=Math.round(32*scale);
 const walletHeight=Math.round((short?65:82)*scale);
 const rewardHeight=Math.round((short?50:60)*scale);
 const nameHeight=Math.round((short?43:55)*scale);
 const energyHeight=Math.round((short?31:39)*scale);
 const tapHeight=Math.max(44,Math.round((short?55:68)*scale));
 // Use one unscaled height budget before applying UI Scale. Otherwise
 // Compact would paradoxically produce a bigger puppy on short screens
 // because smaller controls free more space.
 const baselineReserved=short?336:412;
 const basePortrait=Math.max(72,Math.min(260,Math.floor((h-baselineReserved)*0.87)));
 const portrait=Math.max(72,Math.round(basePortrait*scale));
 const minStageHeight=Math.max(225,portrait+nameHeight+energyHeight+tapHeight+Math.round(32*scale));
 return {scale,short,gap,headingHeight,walletHeight,rewardHeight,nameHeight,energyHeight,tapHeight,portrait,minStageHeight};
}
