import test from 'node:test';
import assert from 'node:assert/strict';
import {projectKotlinCloudSave} from '../src/production/kotlinCloudPreview.js';
import {freshGame} from '../src/engine/game.js';
test('projects an authenticated Kotlin cloud save as read-only presentation state',()=>{
 const source={format:'puppy-clicker-cloud-save',version:1,stores:{
  puppy_clicker_save:{
   values:{puppy_style:'poodle',puppy_name:'Mochi',unlocked_puppies:['classic','golden','poodle','spotty'],
    treats:250,bones_v7:40,pup_coins_v7:2,casino_chips_v7:110,total_taps:55,energy:72,
    upgrade_better_treats:2,prestige_count_v6:1},
   types:{treats:'long',energy:'int',unlocked_puppies:'string_set'}
  },puppy_seasonal_v1:{values:{},types:{}}
 }};
 const before=JSON.stringify(source);
 const game=projectKotlinCloudSave(JSON.stringify(source),'VerifiedDiscord');
 assert.equal(game.puppyStyle,'poodle');
 assert.equal(game.puppyName,'Mochi');
 assert.equal(game.treats,250);
 assert.equal(game.bones,40);
 assert.equal(game.upgrades.better_treats,2);
 assert.equal(game.energy,72);
 assert.equal(game.onboardingComplete,true);
 assert.equal(game.guestName,'VerifiedDiscord');
 assert.equal(JSON.stringify(source),before,'Cloud snapshot must never be modified');
 assert.equal(freshGame().treats,0,'Default engine must not be mutated');
});
test('refuses malformed and incompatible cloud data rather than inventing progression',()=>{
 for(const input of ['','{}','null','{\"format\":\"puppy-clicker-cloud-save\",\"version\":1}',
 '{"format":"other","version":1,"stores":{}}']){
  assert.throws(()=>projectKotlinCloudSave(input));
 }
});

test('reads current Kotlin ticket, prestige, XP and per-puppy care keys',()=>{
 const source={format:'puppy-clicker-cloud-save',version:1,stores:{
  puppy_clicker_save:{values:{
   puppy_style:'classic',puppy_name:'Buddy',
   upgrade_ticket_common:9,ticket_shop_purchase_rare_v7:2,
   prestige_skill_tap_training_v6:4,player_xp_v1:775,
   care_profile_v1_classic_energy:68,care_profile_v1_classic_fullness:80,
   care_profile_v1_classic_happiness:90,care_profile_v1_classic_cleanliness:60,
   care_profile_v1_classic_bond:31,care_profile_v1_classic_updated_at:1790000000000,
  },types:{}},
  puppy_seasonal_v1:{values:{},types:{}}
 }};
 const result=projectKotlinCloudSave(JSON.stringify(source));
 assert.equal(result.ticketInventory.COMMON,9);
 assert.equal(result.ticketShopPurchases.RARE,2);
 assert.equal(result.prestigeSkills.TAP_TRAINING,4);
 assert.equal(result.xp,775);
 assert.equal(result.careProfiles.classic.energy,68);
});
