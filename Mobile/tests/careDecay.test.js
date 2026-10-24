import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {decayCareProfile,applyOfflineCare,switchCareProfile} from '../src/engine/careDecay.js';

test('Kotlin per-puppy decay rates exactly match 120 elapsed minutes',()=>{
 assert.deepEqual(decayCareProfile({happiness:100,fullness:100,energy:100,cleanliness:100,bond:35},120),{
   happiness:60,fullness:40,energy:70,cleanliness:70,bond:35
 });
});
test('Kotlin caps offline decay to 240 minutes, protecting against clock leaps',()=>{
 const profile={happiness:100,fullness:100,energy:100,cleanliness:100,bond:99};
 assert.deepEqual(decayCareProfile(profile,50000),decayCareProfile(profile,240));
});
test('applying offline care changes only current puppy meters, not currencies or bond',()=>{
 const g=freshGame();g.lastActiveAt=1_000;g.treats=170;
 applyOfflineCare(g,1_000+120*60000);
 assert.equal(g.happiness,60);assert.equal(g.fullness,40);
 assert.equal(g.energy,70);assert.equal(g.cleanliness,70);
 assert.equal(g.bond,10);assert.equal(g.treats,170);
});
test('switching to an inactive puppy applies only that puppy stored care decay',()=>{
 const g=freshGame();g.lastActiveAt=1_000;
 g.careProfiles.golden={happiness:100,fullness:100,energy:100,cleanliness:100,bond:17};
 g.careUpdatedAtByPuppy={golden:1_000};
 const ok=switchCareProfile(g,'golden',1_000+120*60000);
 assert.equal(ok,true);assert.equal(g.energy,70);assert.equal(g.fullness,40);assert.equal(g.bond,17);
 assert.equal(g.careProfiles.classic.happiness,100);
});
