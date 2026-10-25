import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {initialState,transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

const ready=g=>({...initialState(),game:g,ready:true});

test('Kotlin 1600ms timeout clears combo but never mutates last care timestamp',()=>{
 const g=freshGame();g.combo=7;g.lastTapMs=1000;
 g.lastActiveAt=1500;g.careUpdatedAtByPuppy.classic=1500;
 const s=ready(g);
 assert.equal(transition(s,{type:'comboTimeout',now:2600}),s);
 const expired=transition(s,{type:'comboTimeout',now:2601});
 assert.equal(expired.game.combo,0);
 assert.equal(expired.game.bestCombo,0);
 assert.equal(expired.game.lastTapMs,1000);
 assert.equal(expired.game.lastActiveAt,1500);
 assert.equal(expired.game.careUpdatedAtByPuppy.classic,1500);
 assert.equal(transition(expired,{type:'comboTimeout',now:3500}),expired);
});

test('Kotlin foreground care tick subtracts 1,2,1,1 and never the bond or wallet',()=>{
 const g=freshGame();g.treats=200;g.bones=4;g.happiness=3;g.fullness=1;
 g.energy=10;g.cleanliness=0;g.bond=79;
 const s=transition(ready(g),{type:'foregroundCareTick',now:150000});
 assert.deepEqual([s.game.happiness,s.game.fullness,s.game.energy,s.game.cleanliness,s.game.bond],[2,0,9,0,79]);
 assert.deepEqual([s.game.treats,s.game.bones],[200,4]);
 assert.deepEqual(s.game.careProfiles.classic,
  {happiness:2,fullness:0,energy:9,cleanliness:0,bond:79});
 assert.equal(s.game.careUpdatedAtByPuppy.classic,150000);
 assert.equal(s.game.lastActiveAt,150000);
 const saved=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(saved.careProfiles.classic.energy,9);
 assert.equal(saved.careUpdatedAtByPuppy.classic,150000);
});

test('foreground decay touches the selected puppy only',()=>{
 const g=freshGame();g.puppyStyle='golden';
 g.careProfiles.classic={happiness:12,fullness:13,energy:14,cleanliness:15,bond:16};
 g.careUpdatedAtByPuppy.classic=5000;
 const s=transition(ready(g),{type:'foregroundCareTick',now:10000});
 assert.deepEqual(s.game.careProfiles.classic,g.careProfiles.classic);
 assert.equal(s.game.careUpdatedAtByPuppy.classic,5000);
 assert.equal(s.game.careUpdatedAtByPuppy.golden,10000);
});

test('app resume expires stale combo and applies the per-puppy offline care clock',()=>{
 const g=freshGame();g.combo=5;g.lastTapMs=1000;
 g.lastActiveAt=1000;g.careUpdatedAtByPuppy.classic=1000;
 const s=transition(ready(g),{type:'resume',now:120000+1000});
 assert.equal(s.game.combo,0);
 assert.equal(s.game.happiness,100-40);
 assert.equal(s.game.fullness,100-60);
 assert.equal(s.game.energy,100-30);
 assert.equal(s.game.cleanliness,100-30);
});
