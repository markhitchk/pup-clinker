import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {freshGame} from '../src/engine/game.js';
import {inventorySnapshot,inventoryCategories} from '../src/graphics/inventoryModel.js';

const root=join(dirname(fileURLToPath(import.meta.url)),'..');
test('inventory reads actual ownership without changing saves',()=>{
 const game=freshGame();
 game.unlockedPuppies=['classic','golden','v2_harleytg','v2_harleytg','future_pup'];
 game.puppyStyle='v2_harleytg';game.puppyName='Harley';
 game.ownedAccessories=['None','Bow','Crown','Bow'];game.accessory='Crown';
 game.ticketInventory={COMMON:2,RARE:4};
 game.upgrades.better_treats=3;game.treats=12345;game.pupCoins=19;
 const before=JSON.stringify(game);
 const data=inventorySnapshot(game);
 assert.equal(data.counts.puppies,4);
 assert.equal(data.puppies.find(x=>x.id==='v2_harleytg').name,'Harley');
 assert.equal(data.puppies.find(x=>x.id==='future_pup').known,false);
 assert.equal(data.accessories.find(x=>x.name==='Crown').equipped,true);
 assert.equal(data.counts.accessories,2);
 assert.equal(data.counts.tickets,6);
 assert.equal(data.upgrades[0].level,3);
 assert.equal(data.wallet.find(x=>x.key==='treats').value,12345);
 assert.equal(JSON.stringify(game),before,'inventory projection must not write into game state');
});
test('inventory accepts partial cloud data and all five categories',()=>{
 const data=inventorySnapshot({unlockedPuppies:['classic',null,'classic'],ticketInventory:{COMMON:-5,RARE:'oops'}});
 assert.equal(data.counts.puppies,1);
 assert.equal(data.counts.tickets,0);
 assert.equal(data.counts.upgrades,0);
 assert.equal(data.wallet.length,4);
 assert.deepEqual(inventoryCategories.map(x=>x[0]),['puppies','accessories','tickets','upgrades','wallet']);
});
test('Android Inventory is accessible from header, Settings and the native router',()=>{
 const app=readFileSync(join(root,'App.js'),'utf8');
 const settings=readFileSync(join(root,'src/components/SettingsScreen.jsx'),'utf8');
 const view=readFileSync(join(root,'src/components/InventoryScreen.jsx'),'utf8');
 assert.match(app,/secondary==='inventory'/);
 assert.match(app,/accessibilityLabel="Open inventory"/);
 assert.match(settings,/onOpen\('inventory'\)/);
 assert.match(view,/accessibilityRole="tab"/);
 assert.match(view,/testID="android-inventory-screen"/);
 assert.doesNotMatch(view,/act\(\{type:/);
});
