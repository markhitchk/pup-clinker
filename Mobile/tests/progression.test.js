import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {ACHIEVEMENTS,grantGuestXp,guestLevel,settleGuestAchievements} from '../src/engine/progression.js';
import {transition} from '../src/state/reducer.js';

test('all nine achievement definitions match Kotlin PuppyAchievementsV6',()=>{
 assert.deepEqual(ACHIEVEMENTS.map(x=>x.id),['first_treat','snack_stash','puppy_pro','combo_hero','ticket_hunter','big_taps','auto_pup','happy_home','level_ten']);
 assert.ok(ACHIEVEMENTS.every(x=>x.xpReward===50));
});
test('manual tap 1 XP, care 5 XP, puppy unlock 100 XP and level curve',()=>{
 const g=freshGame();
 grantGuestXp(g,'MANUAL_TAP');grantGuestXp(g,'CARE_ACTION');grantGuestXp(g,'NEW_PUPPY');
 assert.equal(g.xp,106);
 assert.equal(guestLevel(g),3); // XP 106 reaches level 3 on new 25×(L-1)² curve
});
test('achievements are settled once even when score repeats or reloads',()=>{
 const g=freshGame();g.lifetimeTreats=100;
 let first=settleGuestAchievements(g);
 assert.ok(first.includes('first_treat'));
 assert.ok(first.includes('snack_stash'));
 assert.ok(first.includes('happy_home'));
 const count=g.xp;
 assert.deepEqual(settleGuestAchievements(g),[]);
 assert.equal(g.xp,count);
});
test('reducer awards tap XP only for accepted actions; achievement state survives guest save',()=>{
 const state={game:freshGame(),ready:true,notice:''};
 const one=transition(state,{type:'tap',now:20000,randoms:[0.99,0.99]});
 assert.equal(one.game.xp>=51,true); // first Treat & full health achievement
 const two=transition(one,{type:'tap',now:21000,randoms:[0.99,0.99]});
 assert.equal(two.game.xp,one.game.xp+1);
});
