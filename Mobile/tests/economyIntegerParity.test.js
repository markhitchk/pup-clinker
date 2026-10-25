import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,UPGRADES,cost,tap,purchase} from '../src/engine/game.js';
import {serializeGuestSave,parseGuestSave} from '../src/state/guestSave.js';

const upgrade=id=>UPGRADES.find(u=>u.id===id);
const LONG_MAX=(1n<<63n)-1n;
// Independent Kotlin PuppyEconomyV7.growCost / mulDivFloor reference.
function kotlinCost(id,owned,shopper=0){
 const u=upgrade(id),mult=BigInt(u.rarity?135:155);
 let n=BigInt(u.base);
 for(let i=0;i<owned;i++){
  if(n>(LONG_MAX-99n)/mult){n=LONG_MAX;break;}
  n=(n*mult+99n)/100n;
 }
 const discount=BigInt(100-5*shopper);
 return n>LONG_MAX/discount?LONG_MAX:n*discount/100n;
}

test('Kotlin V7 published upgrade prices and Smart Shopper discount',()=>{
 const g=freshGame(),normal=upgrade('better_treats'),rare=upgrade('lucky_collar');
 assert.equal(cost(g,normal),35);
 g.upgrades.better_treats=1;assert.equal(cost(g,normal),55);
 g.upgrades.better_treats=2;assert.equal(cost(g,normal),86);
 g.upgrades.better_treats=1;g.prestigeSkills.SMART_SHOPPER=2;
 assert.equal(cost(g,normal),49);
 g.prestigeSkills.SMART_SHOPPER=0;g.upgrades.lucky_collar=1;
 assert.equal(cost(g,rare),102);
});

test('higher tier costs remain exactly equal to Kotlin Long math while safe',()=>{
 const g=freshGame();
 for(const u of UPGRADES){
  for(const owned of [0,1,2,3,10,20,45,60,70,80,100]){
   g.upgrades[u.id]=owned;
   for(const shopper of [0,2,5]){
    g.prestigeSkills.SMART_SHOPPER=shopper;
    const expected=kotlinCost(u.id,owned,shopper);
    if(expected<=BigInt(Number.MAX_SAFE_INTEGER))
     assert.equal(cost(g,u),Number(expected),u.id+' owned '+owned+' shopper '+shopper);
    else
     assert.ok(cost(g,u)>Number.MAX_SAFE_INTEGER,u.id+' should not be purchasable in guest integer wallet');
   }
  }
 }
});

test('unaffordable Kotlin Long tier cannot be purchased with max guest balance',()=>{
 const g=freshGame();g.upgrades.better_treats=100;
 g.treats=Number.MAX_SAFE_INTEGER;g.bones=Number.MAX_SAFE_INTEGER;
 const before=g.treats;
 assert.ok(!Number.isSafeInteger(cost(g,upgrade('better_treats'))));
 assert.equal(purchase(g,upgrade('better_treats')),false);
 assert.equal(g.treats,before);
});

test('tap reward ticket inventory hard-caps at Kotlin 9999 while counting found drops',()=>{
 const g=freshGame();g.ticketInventory.COMMON=9999;
 const beforeSerial=g.ticketDropSerial;
 assert.equal(tap(g,1000,()=>0),'COMMON');
 assert.equal(g.ticketInventory.COMMON,9999);
 assert.equal(g.totalTicketsFound,1);
 assert.equal(g.ticketDropSerial,beforeSerial+1);
 const loaded=parseGuestSave(serializeGuestSave(g));
 assert.equal(loaded.ticketInventory.COMMON,9999);
});

test('tap, Bones, Pup Coins and care counter use bounded guest integers',()=>{
 const g=freshGame();
 g.totalTaps=Number.MAX_SAFE_INTEGER-1;
 g.legitimateTaps=Number.MAX_SAFE_INTEGER-1;
 tap(g,1000,()=>0.99);
 assert.equal(g.totalTaps,Number.MAX_SAFE_INTEGER);
 assert.equal(g.legitimateTaps,Number.MAX_SAFE_INTEGER);
});
