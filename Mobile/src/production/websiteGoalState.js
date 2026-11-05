import {UPGRADES} from '../engine/game.js';
import {MAX_UPGRADES_PER_ITEM} from '../engine/balancedProgression.js';
import {progressForGoal} from '../engine/monthlyRewards.js';

// Only server-confirmed game state is used to decide whether Claim is enabled.
// Pending, optimistic taps are displayed separately while they are uploading.
export function hasCompletedUpgradeShop(game){
 const owned=game?.upgrades;
 return !!owned&&UPGRADES.length===10&&UPGRADES.every(upgrade=>
  Number.isSafeInteger(owned[upgrade.id])&&owned[upgrade.id]>=MAX_UPGRADES_PER_ITEM);
}
const routeByMetric={taps:'play',care:'care',shop:'shop',wellness:'care',bond:'care',tickets:'shop'};
const remainingLabel={taps:'taps',care:'care actions',shop:'upgrades',wellness:'wellness points',bond:'bond points',tickets:'tickets'};
export function webGoalState(game,goal,date){
 const maxedShop=goal.id==='shop_visit'&&goal.metric==='shop'&&goal.target===1&&hasCompletedUpgradeShop(game);
 const confirmed=Math.max(0,progressForGoal(game,goal,date));
 const progress=maxedShop?Math.max(confirmed,1):confirmed;
 const complete=progress>=goal.target;
 const remaining=Math.max(0,goal.target-progress);
 const destination=routeByMetric[goal.metric]||'rewards';
 const status=maxedShop
  ?'All 10 upgrades are maxed. Your Shop Visit goal is ready to claim.'
  :complete?'Goal complete — claim your reward.'
  :goal.metric==='shop'?'Buy an upgrade in the Shop to unlock this reward.'
  :`${remaining} ${remainingLabel[goal.metric]||'steps'} remaining before this reward can be claimed.`;
 const continueLabel={play:'Go to Play',care:'Go to Care',shop:'Go to Shop',rewards:'View Rewards'}[destination];
 return {progress,complete,remaining,status,destination,continueLabel,maxedShop};
}
