import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,tap,prestige} from '../src/engine/game.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

// Kotlin PuppyClickerV6ViewModel.loadState reconstructs both bonuses from the
// purchased V5 upgrades and the permanent prestige skills on every load.
test('guest restore reconstructs tap and active bonus from purchased upgrades',()=>{
 const game=freshGame();
 game.upgrades.better_treats=3;
 game.upgrades.lucky_collar=2;
 game.upgrades.chew_toy=4;
 game.prestigeSkills.TAP_TRAINING=2;
 game.prestigeSkills.AUTO_TRAINING=1;
 game.clickPower=999999; // Stale cached strength must never award free Treats.
 game.activeBonus=999999;
 const restored=parseGuestSave(serializeGuestSave(game));
 assert.equal(restored.clickPower,20); // 1 + 3*(1+2) + 2*(3+2)
 assert.equal(restored.activeBonus,8); // 4*(1+1)
 assert.equal(restored.treats,0);
 tap(restored,1_000,()=>0.99);
 assert.equal(restored.treats,21); // 20 tap strength + 1 healthy-care bonus
});

test('restored prestige run keeps skill levels but removes old upgrade power',()=>{
 const game=freshGame();
 game.lifetimeTreats=50_000;
 game.upgrades.golden_bowl=2;
 game.prestigeSkills.TAP_TRAINING=3;
 game.clickPower=999;
 assert.equal(prestige(game,20_000),true);
 const restored=parseGuestSave(serializeGuestSave(game));
 assert.equal(restored.prestigeSkills.TAP_TRAINING,3);
 assert.equal(restored.upgrades.golden_bowl,0);
 assert.equal(restored.clickPower,1);
 assert.equal(restored.prestigeCount,1);
});

test('Kotlin starter roster remains unlocked after incomplete guest saves',()=>{
 const game=freshGame();
 game.unlockedPuppies=['classic'];
 const restored=parseGuestSave(JSON.stringify({kind:'guest-only',schema:1,game}));
 for(const id of ['classic','golden','poodle','spotty']){
  assert.ok(restored.unlockedPuppies.includes(id),id+' should be unlocked');
 }
});
