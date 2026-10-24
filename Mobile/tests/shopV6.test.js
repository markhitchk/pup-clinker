import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 freshGame,UPGRADES,RARITIES,MAX_TICKETS_PER_RARITY,cost,boneCost,ticketCost,
 ticketShopCost,prestigePointsAvailable,skillCost,purchase,buyUpgradeTicket,prestige
} from '../src/engine/game.js';
import {transition,initialState} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
import {
 TICKET_RARITY_PRESENTATION,PRESTIGE_SKILL_PRESENTATION,upgradesForShopTab,
 upgradeOffer,ticketShopOffer,accessoryOffer,prestigeOffer,canBuySkill
} from '../src/engine/shopV6.js';

const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const v5=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV5ViewModel.kt',import.meta.url),'utf8');
const economy=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyEconomyV7.kt',import.meta.url),'utf8');
const wallet=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/GameViewModel.kt',import.meta.url),'utf8');
const coins=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt',import.meta.url),'utf8');
const rn=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const shop=rn.slice(rn.indexOf('function ShopScreen('),rn.indexOf('function PrestigeScreen('));
const prestigeScreen=rn.slice(rn.indexOf('function PrestigeScreen('),rn.indexOf('function RosterScreen('));

test('Kotlin revamped Shop uses the full-width Redeem entry and three purchase categories',()=>{
 for(const title of ['🍪 Upgrades','🎟️ Tickets','🪙 Pup Coins','🎫 Redeem Code']){
  assert.ok(coins.includes(title==='🎫 Redeem Code'?'Redeem Code':title));assert.ok(shop.includes(title),title);
 }
 assert.ok(coins.includes('PuppyRevampedShopScreen'));
 assert.ok(coins.includes('PuppyTicketInventoryCard(state)'));
 assert.ok(coins.includes('PuppyTicketUpgradeCard'));
 assert.ok(coins.includes('PuppyCookieUpgradeCard'));
 assert.ok(shop.includes('shopTab===\'tickets\''));
 assert.ok(shop.includes('upgradeOffer(g,u.id)'));
 assert.ok(shop.includes('ticketShopOffer(g,rarity)'));
 assert.ok(shop.includes('accessoryOffer(g,name)'));
 assert.ok(shop.includes('PageHeader withLogo title="Puppy Shop"'));
 assert.ok(shop.includes('styles.revampedShopRedeem'));
 const buttons=['Buy 1','Upgrade','Need {o.tickets}','Ticket inventory','Owned {o.owned}'];
 for(const x of buttons)assert.ok(shop.includes(x),x);
 assert.equal(upgradesForShopTab('upgrades').length,4);
 assert.equal(upgradesForShopTab('tickets').length,6);
 assert.deepEqual(upgradesForShopTab('coins'),[]);
 assert.ok(coins.includes('internal fun PuppyPupCoinShop('));
});
test('Kotlin ticket names, emojis, max inventory and tiered prices match',()=>{
 assert.ok(wallet.includes('COMMON("Common", "🎟️"'));
 assert.equal(TICKET_RARITY_PRESENTATION.COMMON.emoji,'🎟️');
 assert.equal(TICKET_RARITY_PRESENTATION.LEGENDARY.emoji,'🟡');
 assert.ok(economy.includes('ticketShopCost(rarity: TicketRarity, purchased: Int)'));
 assert.equal(MAX_TICKETS_PER_RARITY,9999);
 assert.deepEqual([0,1,2,3,100].map(n=>ticketShopCost('COMMON',n)),[250,312,375,437,1500]);
 for(const r of RARITIES)assert.ok(TICKET_RARITY_PRESENTATION[r]);
 const g=freshGame();g.pupCoins=10_000;
 g.ticketInventory.COMMON=9999;
 assert.equal(ticketShopOffer(g,'COMMON').canBuy,false);
 assert.equal(buyUpgradeTicket(g,'COMMON'),false);
 assert.equal(g.pupCoins,10_000);
 g.ticketInventory.COMMON=9998;
 assert.equal(buyUpgradeTicket(g,'COMMON'),true);
 assert.equal(g.ticketInventory.COMMON,9999);
 assert.equal(g.pupCoins,9750);
 assert.equal(ticketShopOffer(g,'COMMON').price,312);
 const restored=parseGuestSave(serializeGuestSave({...g,ticketInventory:{...g.ticketInventory,COMMON:100000}}));
 assert.equal(restored.ticketInventory.COMMON,9999);
});

test('Treat growth, Bone price and ticket discounts mirror Kotlin engine formulas',()=>{
 const g=freshGame();g.treats=2000;g.bones=100;g.ticketInventory.COMMON=9;
 const regular=UPGRADES.find(u=>u.id==='better_treats');
 assert.ok(v5.includes('V5_UPGRADES = listOf('));
 assert.equal(cost(g,regular),35);
 assert.equal(boneCost(regular),1);
 assert.equal(purchase(g,regular),true);
 assert.equal(cost(g,regular),55);
 assert.equal(purchase(g,regular),true);
 assert.equal(cost(g,regular),86);
 g.prestigeSkills.SMART_SHOPPER=3;
 assert.equal(cost(g,regular),73);
 const ticket=UPGRADES.find(u=>u.id==='lucky_collar');
 assert.equal(boneCost(ticket),2);
 assert.equal(ticketCost(g,ticket),2);
 g.prestigeSkills.TICKET_SENSE=4;
 assert.equal(ticketCost(g,ticket),1);
 assert.equal(upgradeOffer(g,ticket.id).tickets,1);
 assert.equal(upgradeOffer(g,ticket.id).canBuy,true);
 assert.equal(upgradeOffer(g,'missing-id'),null);
 assert.ok(economy.includes('val percent = (100L - shopper * 5L)'));
});

test('Reducer resolves official upgrade by id: forged payout and price fields cannot alter reward',()=>{
 const g=freshGame();g.treats=100;g.bones=5;
 let state=initialState();state.ready=true;state.game=g;
 const suspicious={id:'better_treats',base:0,amount:10000000,effect:'CLICK',rarity:null};
 state=transition(state,{type:'purchase',upgrade:suspicious,now:Date.UTC(2026,9,23,12)});
 assert.equal(state.game.treats,65,'canonical 35 treat cost must be charged');
 assert.equal(state.game.bones,4);
 assert.equal(state.game.clickPower,2,'only canonical +1 tap power should apply');
 const bad=transition(state,{type:'purchase',upgradeId:'fabricated',now:Date.UTC(2026,9,23,12)});
 assert.equal(bad.game.treats,65);
 const next=transition(bad,{type:'purchase',upgradeId:'better_treats',now:Date.UTC(2026,9,23,12)});
 assert.equal(next.game.treats,10,'second purchase uses grown 55 treat price');
 assert.equal(next.game.clickPower,3);
});

test('Pup Coin accessories and buying tickets respect ownership, price and permanence',()=>{
 let g=freshGame();g.pupCoins=1000;
 assert.deepEqual({owned:accessoryOffer(g,'None').owned,equipped:accessoryOffer(g,'None').equipped},{owned:true,equipped:true});
 assert.equal(accessoryOffer(g,'Bow').price,250);
 assert.equal(accessoryOffer(g,'Bow').canBuy,true);
 assert.equal(accessoryOffer(g,'unknown'),null);
 let state=initialState();state.game=g;state.ready=true;
 state=transition(state,{type:'accessory',name:'Bow',now:1000});
 assert.equal(state.game.pupCoins,750);
 assert.equal(state.game.accessory,'Bow');
 assert.ok(state.game.ownedAccessories.includes('Bow'));
 const once=transition(state,{type:'accessory',name:'Bow',now:1100});
 assert.equal(once.game.pupCoins,750);
 const unequip=transition(once,{type:'equip',name:'None',now:1200});
 assert.equal(unequip.game.accessory,'None');
 assert.equal(accessoryOffer(unequip.game,'Bow').canBuy,true);
 const oldWallet=unequip.game.pupCoins;
 const ticket=transition(unequip,{type:'ticket',rarity:'COMMON',now:1300});
 assert.equal(ticket.game.pupCoins,oldWallet-250);
 assert.equal(ticket.game.ticketInventory.COMMON,1);
});

test('Kotlin skill descriptions and prestige controls reset only run-specific progress',()=>{
 assert.ok(kotlin.includes('V6PrestigeCenter(state, vm'));
 assert.ok(prestigeScreen.includes('PRESTIGE_SKILL_PRESENTATION[skill]'));
 assert.ok(prestigeScreen.includes('prestigeOffer(g,activeCasinoRound)'));
 assert.ok(prestigeScreen.includes('Permanent skill tree'));
 assert.equal(PRESTIGE_SKILL_PRESENTATION.AUTO_TRAINING.title,'Active Training');
 for(const skill of ['TAP_TRAINING','AUTO_TRAINING','SMART_SHOPPER','TICKET_SENSE']){
  assert.ok(PRESTIGE_SKILL_PRESENTATION[skill]);
 }
 const g=freshGame();
 g.treats=10000;g.lifetimeTreats=150000;g.bones=10;
 g.pupCoins=300;g.casinoChips=40;g.redeemedCodeIds=['CODE_X'];
 g.unlockedPuppies.push('aurora');g.ownedAccessories.push('Crown');g.accessory='Crown';
 g.prestigeSkills.TAP_TRAINING=3;g.ticketInventory.COMMON=2;
 g.ticketShopPurchases.COMMON=7;g.dailyStreak=4;g.totalTaps=50;
 assert.equal(prestigePointsAvailable(g),2);
 assert.equal(prestigeOffer(g,true).canPrestige,false);
 assert.equal(prestigeOffer(g,false).canPrestige,true);
 const day=20400;
 assert.equal(prestige(g,day),true);
 assert.equal(g.prestigeCount,1);assert.equal(g.skillPoints,2);
 assert.equal(g.treats,0);assert.equal(g.lifetimeTreats,0);assert.equal(g.bones,0);
 assert.equal(g.ticketInventory.COMMON,0);assert.equal(g.totalTaps,0);
 assert.equal(g.pupCoins,300);assert.equal(g.casinoChips,40);
 assert.equal(g.ticketShopPurchases.COMMON,7);
 assert.equal(g.accessory,'Crown');assert.ok(g.unlockedPuppies.includes('aurora'));
 assert.deepEqual(g.redeemedCodeIds,['CODE_X']);
 assert.equal(g.prestigeSkills.TAP_TRAINING,3);
 assert.equal(g.dailyDay,day);
 assert.equal(prestigePointsAvailable(g),0);
 assert.equal(skillCost(g,'TAP_TRAINING'),2);
 assert.equal(canBuySkill(g,'TAP_TRAINING'),true);
});

test('Cannot prestige while a Blackjack round or pending Scratcher is active',()=>{
 let state=initialState();state.ready=true;state.game.lifetimeTreats=50000;
 state.game.pendingScratch={type:'SCRATCHERS',version:1,wager:20,card:'Pup Scratch',prize:'NO_PRIZE',symbols:['🐾','🍪','🦴'],payout:0,createdAt:1234};
 const before=state.game.lifetimeTreats;
 const blocked=transition(state,{type:'prestige',now:Date.UTC(2026,9,23,12)});
 assert.equal(blocked.game.lifetimeTreats,before);
 assert.equal(blocked.game.prestigeCount,0);
 assert.ok(prestigeScreen.includes("onPress={()=>Alert.alert('⭐ Prestige this run?'"));
 assert.ok(prestigeScreen.includes('disabled={!offer.canPrestige}'));
});

test('Client preview never executes Puppy Code redemption without verified save migration',()=>{
 assert.ok(shop.includes('redemption is disabled in this guest preview'));
 assert.ok(!shop.includes("type:'redeem'"));
 assert.ok(!shop.includes('redeemCode('));
});
