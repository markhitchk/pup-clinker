/**
 * Puppy Clicker balanced progression v2.
 *
 * Cloud accounts retain ALL existing currency, unlocks and historical counters.
 * New XP and earned tap power are bounded; no balance resets or retroactive
 * reductions are performed. This intentionally departs from Kotlin's
 * unbounded lifetime-Treat-based level formula.
 */
export const MAX_PLAYER_LEVEL=100;
export const MAX_PLAYER_XP=250_000;
export const XP_PER_LEVEL_SQUARE=25;
export const MAX_UPGRADES_PER_ITEM=100;
export const MAX_TAP_REWARD=10_000;
export const MAX_ACTIVE_TAP_BONUS=2_500;
export const MAX_REWARD_STREAK_DAYS=365;
export const levelXp=level=>XP_PER_LEVEL_SQUARE*(Math.max(1,Math.min(MAX_PLAYER_LEVEL,Math.floor(level||1)))-1)**2;
export const boundedXp=value=>Number.isSafeInteger(value)&&value>0?Math.min(MAX_PLAYER_XP,value):0;
export const xpLevel=value=>Math.min(MAX_PLAYER_LEVEL,1+Math.floor(Math.sqrt(boundedXp(value)/XP_PER_LEVEL_SQUARE)));
export const nextXpTarget=value=>xpLevel(value)>=MAX_PLAYER_LEVEL?MAX_PLAYER_XP:levelXp(xpLevel(value)+1);
export const addBoundedXp=(current,award)=>Math.min(MAX_PLAYER_XP,boundedXp(current)+(Number.isSafeInteger(award)&&award>0?award:0));
export const cappedTapReward=amount=>Math.max(1,Math.min(MAX_TAP_REWARD,Number.isSafeInteger(amount)?amount:1));
export const cappedActiveBonus=amount=>Math.max(0,Math.min(MAX_ACTIVE_TAP_BONUS,Number.isSafeInteger(amount)?amount:0));
