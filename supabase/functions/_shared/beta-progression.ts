/** Keep this server policy in sync with Mobile/src/engine/balancedProgression.js.
 * Never reduce existing currency, protected inventories or unlocks.
 */
export const MAX_PLAYER_LEVEL=100;
export const MAX_PLAYER_XP=250_000;
export const XP_PER_LEVEL_SQUARE=25;
export const MAX_UPGRADES_PER_ITEM=100;
export const MAX_TAP_REWARD=10_000;
export const MAX_ACTIVE_TAP_BONUS=2_500;
export const MAX_REWARD_STREAK_DAYS=365;
export const boundedXp=(n:number):number=>Number.isSafeInteger(n)&&n>0?Math.min(MAX_PLAYER_XP,n):0;
export const xpLevel=(n:number):number=>Math.min(MAX_PLAYER_LEVEL,1+Math.floor(Math.sqrt(boundedXp(n)/XP_PER_LEVEL_SQUARE)));
export const addBoundedXp=(xp:number,award:number):number=>Math.min(MAX_PLAYER_XP,boundedXp(xp)+(Number.isSafeInteger(award)&&award>0?award:0));
export const cappedTapReward=(n:number):number=>Math.max(1,Math.min(MAX_TAP_REWARD,Number.isSafeInteger(n)?n:1));
export const cappedActiveBonus=(n:number):number=>Math.max(0,Math.min(MAX_ACTIVE_TAP_BONUS,Number.isSafeInteger(n)?n:0));
