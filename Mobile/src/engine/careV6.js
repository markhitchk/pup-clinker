// Part 2 — launcher V6CareAndDaily contract.
// Source: App/app/src/main/java/com/harleytg/puppyclicker/
//   PuppyClickerV6Activity.kt, PuppyClickerV6ViewModel.kt, PuppyCareSystem.kt.
// These values describe LOCAL GUEST preview only. No native notifications,
// original signed save identity or server-authoritative grants are implied.
import {level} from './game.js';
import {localEpochDay} from './monthlyRewards.js';

export const V6_CARE_LAYOUT=Object.freeze({
 portraitSize:125,
 careMeterHeight:6,
 careMeterPadding:11,
 careMeterSpacing:7,
 careActionSpacing:8,
 dailyStreakRadius:24,
 parkDurationMs:60000,
 parkEnergyCost:20
});
export const V6_CARE_ACTIONS=Object.freeze([
 {kind:'feed',icon:'🍖',title:'Feed',row:0},
 {kind:'play',icon:'🎾',title:'Play',row:0},
 {kind:'rest',icon:'💤',title:'Rest',row:1},
 {kind:'wash',icon:'🫧',title:'Groom',row:1},
 {kind:'cuddle',icon:'🤗',title:'Cuddle · build bond',row:2}
]);
export function canPerformCare(g,kind){
 if(!g)return false;
 switch(kind){
  case 'feed':return g.treats>=20&&g.fullness<100;
  case 'play':return g.bones>=1&&g.energy>=12&&(g.happiness<100||g.bond<100);
  case 'rest':return g.energy<100;
  case 'wash':return g.treats>=10&&g.cleanliness<100;
  case 'cuddle':return g.happiness<100||g.bond<100;
  default:return false;
 }
}
export function dailyGiftPreview(g,now=Date.now()){
 const today=localEpochDay(new Date(now));
 const claimed=g.lastDailyClaimDay===today;
 const streak=g.lastDailyClaimDay===today-1?Math.min(365,g.dailyStreak+1):1;
 return {today,claimed,streak,rewardTreats:150+streak*25+level(g)*10,bones:2,pupCoins:3};
}
export function parkPreview(g,now=Date.now()){
 const active=g.parkActive===true;
 const remainingSeconds=active?Math.max(0,Math.ceil((g.parkReadyAtMs-now)/1000)):0;
 const ready=active&&remainingSeconds===0;
 const canStart=!active&&g.energy>=V6_CARE_LAYOUT.parkEnergyCost;
 return {
  active,ready,canStart,remainingSeconds,disabled:!ready&&!canStart,
  label:ready?'Claim':active?'Away':'Go',
  detail:ready?'Adventure reward ready.':active?'Back in '+remainingSeconds+'s':'60 seconds · uses 20 energy'
 };
}
