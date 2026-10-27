import test from 'node:test';
import assert from 'node:assert/strict';
import {MAX_PLAYER_XP,MAX_PLAYER_LEVEL,MAX_TAP_REWARD,MAX_UPGRADES_PER_ITEM,
 xpLevel,levelXp,nextXpTarget,addBoundedXp} from '../src/engine/balancedProgression.js';
import {freshGame,sanitizeGame,level,tap,UPGRADES} from '../src/engine/game.js';
import {guestLevel,grantGuestXp} from '../src/engine/progression.js';
import {projectKotlinCloudSave} from '../src/production/kotlinCloudPreview.js';

test('balanced levels advance from XP, not Treats; 886 XP is level 6',()=>{
 assert.equal(xpLevel(0),1);
 assert.equal(xpLevel(24),1);
 assert.equal(xpLevel(25),2);
 assert.equal(xpLevel(100),3);
 assert.equal(xpLevel(886),6);
 assert.equal(nextXpTarget(886),900);
 assert.equal(xpLevel(levelXp(100)),100);
 assert.equal(xpLevel(Number.MAX_SAFE_INTEGER),MAX_PLAYER_LEVEL);
 assert.equal(addBoundedXp(MAX_PLAYER_XP,100),MAX_PLAYER_XP);
});
test('existing cloud currency, ownership and collection are never truncated',()=>{
 const big=9_000_000_010_023_864;
 const raw={format:'puppy-clicker-cloud-save',version:1,stores:{
  puppy_clicker_save:{types:{},values:{
   treats:big,lifetime_treats:big,casino_chips_v7:big,bones_v7:big,
   pup_coins_v7:big,total_taps:9_000_000_058,energy:15,
   player_xp_v1:886,puppy_style:'classic',
   upgrade_better_treats:999,upgrade_legendary_snacks:999,
   unlocked_puppies:['classic','v2_discord_pup']
  }}
 }};
 const game=projectKotlinCloudSave(JSON.stringify(raw));
 assert.equal(game.treats,big);
 assert.equal(game.bones,big);
 assert.equal(game.pupCoins,big);
 assert.equal(game.casinoChips,big);
 assert.equal(game.upgrades.better_treats,999);
 assert.equal(game.upgrades.legendary_snacks,999);
 assert(game.unlockedPuppies.includes('v2_discord_pup'));
 assert.equal(game.xp,886);
 assert.equal(level(game),6);
 assert.equal(guestLevel(game),6);
 assert(game.clickPower<=MAX_TAP_REWARD);
});
test('legacy maximum XP stays readable but no longer generates absurd levels or grants',()=>{
 const g=sanitizeGame({...freshGame(),xp:9_000_000_010_023_864,lifetimeTreats:9_000_000_010_023_864});
 assert.equal(g.xp,MAX_PLAYER_XP);
 assert.equal(level(g),100);
 grantGuestXp(g,'MANUAL_TAP');
 assert.equal(g.xp,MAX_PLAYER_XP);
});
test('new upgrades and payouts have fair-play caps without deleting old ownership',()=>{
 const g=sanitizeGame({...freshGame(),
  treats:1000000,bones:1000,energy:100,
  upgrades:Object.fromEntries(UPGRADES.map(u=>[u.id,999]))});
 assert.equal(g.upgrades.better_treats,999);
 assert(g.clickPower<=MAX_TAP_REWARD);
 assert.equal(g.activeBonus<=2500,true);
 const before=g.treats;
 tap(g,Date.now(),()=>0.99);
 assert(g.treats-before<=MAX_TAP_REWARD);
 assert.equal(MAX_UPGRADES_PER_ITEM,100);
});
