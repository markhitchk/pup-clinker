import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,ROSTER} from '../src/engine/game.js';
import {gachaPool,applyGacha,eligibleGachaPuppies} from '../src/engine/gacha.js';

test('Android redeem-only puppies never enter random gacha pool',()=>{
  assert.deepEqual(eligibleGachaPuppies(ROSTER).map(p=>p.id),['classic','golden','poodle','spotty']);
});
test('gacha selects missing free puppies before owned duplicates',()=>{
  const g=freshGame();g.unlockedPuppies=['classic'];
  assert.deepEqual(gachaPool(ROSTER,g.unlockedPuppies).map(p=>p.id),['golden','poodle','spotty']);
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
test('gacha follows Android all-owned fallback: duplicate possible, no duplicate grant',()=>{
  const g=freshGame();g.treats=2500;
  const result=applyGacha(g,'TREATS',0);
  assert.equal(result.success,true);
  assert.equal(result.isNewUnlock,false);
  assert.equal(g.treats,0);
  assert.equal(g.unlockedPuppies.length,4);
});
