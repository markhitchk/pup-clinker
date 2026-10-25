import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,ROSTER} from '../src/engine/game.js';
import {gachaPool,applyGacha,eligibleGachaPuppies} from '../src/engine/gacha.js';

test('Android redeem-only puppies never enter random gacha pool',()=>{
  assert.deepEqual(eligibleGachaPuppies(ROSTER).map(p=>p.id),['classic','golden','poodle','spotty','test_v1_crash_dummy','test_v2_crash_dummy']);
});
test('gacha selects missing free puppies before owned duplicates',()=>{
  const g=freshGame();g.unlockedPuppies=['classic'];
  assert.deepEqual(gachaPool(ROSTER,g.unlockedPuppies).map(p=>p.id),['golden','poodle','spotty','test_v1_crash_dummy','test_v2_crash_dummy']);
});
test('gacha checks all currency gates before mutation',()=>{
  const g=freshGame();const original=structuredClone(g);
  assert.equal(applyGacha(g,'TREATS',0).success,false);
  assert.equal(applyGacha(g,'COMMON_TICKET',0).success,false);
  assert.deepEqual(g,original);
});
test('one Common Ticket unlocks one eligible puppy without leaking restricted rewards',()=>{
  const g=freshGame();g.unlockedPuppies=['classic'];g.ticketInventory.COMMON=1;
  const result=applyGacha(g,'COMMON_TICKET',1);
  assert.equal(result.success,true);
  assert.equal(result.puppyId,'poodle'); // golden,poodle,spotty sorted; roll 1 picks poodle
  assert.equal(result.isNewUnlock,true);
  assert.equal(g.ticketInventory.COMMON,0);
  assert.equal(g.gachaPulls,1);
  assert.ok(g.unlockedPuppies.includes('poodle'));
  assert.ok(!g.unlockedPuppies.includes('v2_dev_pup'));
});
test('gacha unlocks Kotlin bundled free TEST pup, then follows all-owned duplicate fallback',()=>{
  const g=freshGame();g.treats=5000;
  const result=applyGacha(g,'TREATS',0);
  assert.equal(result.success,true);
  assert.equal(result.isNewUnlock,true);
  assert.equal(result.puppyId,'test_v1_crash_dummy');
  assert.equal(g.treats,2500);
  assert.equal(g.unlockedPuppies.length,5);
  g.unlockedPuppies.push('test_v2_crash_dummy');
  const duplicate=applyGacha(g,'TREATS',0);
  assert.equal(duplicate.success,true);
  assert.equal(duplicate.isNewUnlock,false);
  assert.equal(g.treats,0);
  assert.equal(g.unlockedPuppies.length,6);
});
