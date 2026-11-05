import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {
 WEBSITE_INVENTORY_SECTIONS,WEBSITE_INVENTORY_CURRENCIES,
 WEBSITE_INVENTORY_ACCESSORYICONS,WEBSITE_INVENTORY_RARITYICONS
} from '../src/production/websiteInventoryContract.js';
import {
 INVENTORY_CATEGORIES,INVENTORY_CURRENCIES,buildInventoryModel
} from '../src/components/inventoryModel.js';
import {suggestPuppyName} from '../src/production/websiteNameSuggestions.js';

const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');

test('Part 4 Roster and Prestige have a single website-authored RN JSX source',()=>{
 const app=read('App.js');
 const ui=read('src/components/WebsiteRosterPrestigeScreens.jsx');
 assert.ok(app.includes('createWebsiteRosterPrestigeScreens'));
 for(const name of ['RosterScreen','PrestigeScreen']){
  assert.ok(ui.includes('function '+name+'('),'missing website '+name);
  assert.ok(!app.includes('function '+name+'('),'independent native '+name);
 }
 for(const text of ['current-puppy-hero','puppy-roster-grid','Puppy Collection',
  'Generate random puppy name','Inspect ','Use Puppy','How to Unlock',
  'Permanent skill tree','Prestige this run?','Finish the current Casino round'])
  assert.ok((ui+'\n'+read('src/components/PuppyDetailsDialog.jsx')).includes(text),text);
 assert.doesNotMatch(ui,/<(?:div|article|section|button)\b/);
 assert.ok(ui.includes("act({type:'favorite'"));
 assert.ok(ui.includes("act({type:'rename'"));
 assert.ok(ui.includes("onUse?onUse("));
 assert.ok(ui.includes("act({type:'prestige'"));
});

test('Puppy profile remains website-owned while name ideas cannot mutate saves',()=>{
 const profile=read('src/components/PuppyDetailsDialog.jsx');
 assert.ok(profile.includes("from '../production/websiteNameSuggestions.js'"));
 assert.ok(profile.includes('Generate random puppy name'));
 assert.ok(profile.includes('Save puppy name'));
 assert.ok(profile.includes('Confirm use puppy'));
 const suggestion=suggestPuppyName('Mochi');
 assert.ok(suggestion.length>0&&suggestion.length<=18);
 assert.notEqual(suggestion.toLowerCase(),'mochi');
 const nameModule=read('src/production/websiteNameSuggestions.js');
 assert.ok(nameModule.includes('Math.random'));
 assert.doesNotMatch(nameModule,/passwordAlphabet|getRandomValues|generateStrongPassword/);
});

test('Inventory uses the real website category, rarity and wallet presentation contract',()=>{
 assert.deepEqual(INVENTORY_CATEGORIES.map(x=>[x.id,x.icon,x.label]),WEBSITE_INVENTORY_SECTIONS);
 assert.deepEqual(INVENTORY_CURRENCIES.map(x=>[x.icon,x.label,x.key]),WEBSITE_INVENTORY_CURRENCIES);
 assert.equal(WEBSITE_INVENTORY_ACCESSORYICONS.Crown,'👑');
 assert.equal(WEBSITE_INVENTORY_RARITYICONS.LEGENDARY,'🟡');
 const app=read('App.js');
 const inventory=read('src/components/WebsiteInventoryScreen.jsx');
 assert.ok(inventory.includes("onOpen('roster')"));
 assert.ok(inventory.includes("onOpen('account-details')"));
 assert.ok(app.includes("if(secondary==='inventory')return <WebsiteInventoryScreen"));
 const game=freshGame(),before=JSON.stringify(game);
 const view=buildInventoryModel(game);
 assert.ok(view.counts.wallet===4);
 assert.equal(JSON.stringify(game),before,'Inventory must not mutate signed progress');
 assert.doesNotMatch(inventory,/cloud-save-write|gameplayAction\(|act\(|dispatch\(/);
});

test('Puppy Code redemption remains signed-only: no invented local entitlements',()=>{
 const shop=read('src/components/WebsiteCareShopScreens.jsx');
 assert.ok(shop.includes('🎫 Redeem Code'));
 assert.ok(shop.includes('signed live entitlement service'));
 assert.doesNotMatch(shop,/act\(\{type:'redeem'|setRedeemed\(|forceUnlock|unlockedPuppies\.push/);
});
