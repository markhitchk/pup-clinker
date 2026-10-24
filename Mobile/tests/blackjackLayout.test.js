import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {blackjackTable,blackjackFan,blackjackSharedCardWidth,BLACKJACK_TABLE_ASPECT} from '../src/engine/blackjackLayout.js';

const source=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyBlackjackLayout.kt',import.meta.url),'utf8');
const near=(a,b)=>assert.ok(Math.abs(a-b)<.00001,a+' != '+b);
test('React Blackjack geometry retains Kotlin bitmap dimensions and painted-table crop',()=>{
 for(const literal of ['SOURCE_WIDTH = 1122f','SOURCE_HEIGHT = 1402f','ART_TOP = 86f','ART_HEIGHT = 1188f','TABLE_ASPECT = SOURCE_WIDTH / ART_HEIGHT'])
  assert.ok(source.includes(literal),literal);
 near(BLACKJACK_TABLE_ASPECT,1122/1188);
 const m=blackjackTable(336);
 near(m.width,336);
 near(m.height,336*1188/1122);
 near(m.artworkTop,336*86/1122);
 near(m.artworkHeight,336*1402/1122);
 near(m.dealer.left,336*.25);
 near(m.dealer.top,m.artworkHeight*.245-m.artworkTop);
 near(m.player.left,336*.18);
 near(m.player.top,m.artworkHeight*.585-m.artworkTop);
 near(m.cardWidth,Math.min(336*.13,56));
});
test('card fans fit into their exact dealer/player zones, including large split hands',()=>{
 const metrics=blackjackTable(430);
 const shared=blackjackSharedCardWidth(metrics,6,10);
 for(const [zone,count] of [[metrics.dealer,6],[metrics.player,10]]){
  const fan=blackjackFan(zone.width,shared,count);
  assert.ok(fan.width<=zone.width+.001);
  assert.ok(fan.step>=0);
 }
 assert.deepEqual(blackjackFan(100,50,0),{cardWidth:50,step:0,width:0});
 assert.deepEqual(blackjackFan(100,50,1),{cardWidth:50,step:0,width:50});
 assert.throws(()=>blackjackTable(0));
 assert.throws(()=>blackjackFan(300,45,-1));
});
test('Blackjack art is composed with Kotlin-specified absolute zones, not CSS approximations',()=>{
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 for(const expected of ['blackjackTable(', 'blackjackSharedCardWidth(', 'blackjackFan(', 'artworkTop','metrics.dealer','metrics.player'])
  assert.ok(app.includes(expected),expected);
});
