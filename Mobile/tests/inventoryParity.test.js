import test from 'node:test';
import assert from 'node:assert/strict';
import {ROSTER,UPGRADES,RARITIES} from '../src/engine/game.js';
import {buildInventoryView,filterInventoryPuppies,INVENTORY_CATEGORIES} from '../src/components/inventoryModel.js';

test('website-parity inventory displays only actual owned state',()=>{
 const known=ROSTER[0].id,upgrade=UPGRADES[0].id,rarity=RARITIES[0];
 const original={
  unlockedPuppies:[known,known,'v3_custom_future_pup'],
  favoritePuppies:[known],puppyStyle:known,puppyNames:{[known]:'My Buddy'},
  ownedAccessories:['Bow','Bow','None'],accessory:'Bow',
  ticketInventory:{[rarity]:2},upgrades:{[upgrade]:3},
  treats:123,bones:4,pupCoins:5,casinoChips:6,
 };
 const before=JSON.stringify(original);
 const result=buildInventoryView(original);
 assert.equal(result.counts.puppies,2);
 assert.equal(result.puppies[0].name,'My Buddy');
 assert.equal(result.puppies[0].current,true);
 assert.equal(result.puppies[0].favorite,true);
 assert.equal(result.puppies[1].known,false);
 assert.equal(result.counts.accessories,1);
 assert.equal(result.accessories[0].equipped,true);
 assert.equal(result.counts.tickets,2);
 assert.equal(result.counts.upgrades,1);
 assert.equal(result.upgrades[0].level,3);
 assert.deepEqual(result.wallet.map(item=>item.amount),[123,4,5,6]);
 assert.equal(JSON.stringify(original),before,'inventory must never alter protected saves');
});
test('inventory search includes owned names, base names and IDs',()=>{
 const known=ROSTER[0];
 const puppies=buildInventoryView({unlockedPuppies:[known.id],puppyNames:{[known.id]:'Star'}}).puppies;
 assert.equal(filterInventoryPuppies(puppies,'star').length,1);
 assert.equal(filterInventoryPuppies(puppies,known.id.toUpperCase()).length,1);
 assert.equal(filterInventoryPuppies(puppies,'nonexistent').length,0);
});
test('untrusted or missing balance fields cannot grant items',()=>{
 const result=buildInventoryView({
  unlockedPuppies:['','test',null,99],ownedAccessories:'Bow',
  ticketInventory:{[RARITIES[0]]:-200},
  treats:-1,bones:'1000',pupCoins:Infinity,casinoChips:2.5,
  upgrades:{[UPGRADES[0].id]:NaN}
 });
 assert.equal(result.counts.puppies,1);
 assert.equal(result.counts.accessories,0);
 assert.equal(result.counts.tickets,0);
 assert.equal(result.counts.upgrades,0);
 assert.deepEqual(result.wallet.map(item=>item.amount),[0,0,0,0]);
 assert.deepEqual(INVENTORY_CATEGORIES.map(item=>item.id),['puppies','accessories','tickets','upgrades','wallet']);
});
