import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame, tap, care, purchase, prestige, ROSTER, UPGRADES} from '../src/engine/game.js';
import {slotsOutcome,rouletteOutcome,plinkoOutcome} from '../src/engine/casinoV7.js';

test('new native profile uses the Android four-currency ledger and roster', ()=>{
  const g=freshGame();
  assert.deepEqual([g.treats,g.bones,g.pupCoins,g.casinoChips],[0,0,0,0]);
  assert.equal(g.puppyStyle,'classic');
  assert.ok(ROSTER.length>=25);
});
test('twenty healthy-puppy taps award care bonus and one Bone without idle income',()=>{
  const g=freshGame();
  for(let i=1;i<=20;i++)tap(g,1000+i*1100,()=>0.99);
  assert.equal(g.treats,40); // +1 base and +1 healthy-care bonus on each tap
  assert.equal(g.bones,1);
  assert.equal(g.autoPerSecond,0);
});
test('care updates the active puppy, not a global shared copy',()=>{
  const g=freshGame();g.energy=45;g.treats=100;g.bones=2;
  assert.equal(care(g,'rest'),true);
  assert.equal(g.energy,75);
});
test('shop refuses purchases without required currencies',()=>{
  const g=freshGame();
  assert.equal(purchase(g,UPGRADES[0]),false);
});
test('prestige below threshold is rejected',()=>{
  const g=freshGame();
  assert.equal(prestige(g),false);
});
test('casino payouts mirror source V7 weights',()=>{
  assert.deepEqual(slotsOutcome(20,[0,0,0]).payout,140);
  assert.equal(rouletteOutcome(20,'RED',1).payout,40);
  assert.equal(plinkoOutcome(20,Array(8).fill(false)).payout,1);
});
