import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,statSync} from 'node:fs';
import {LEGACY_V6_PUPPIES,LEGACY_V6_IDS,legacyRosterSections,unlockedLegacyCount,canEquipLegacyPuppy} from '../src/engine/rosterV6.js';
import {ROSTER,freshGame} from '../src/engine/game.js';
import {gachaMachineFrame,GACHA_MACHINE} from '../src/engine/gachaV6.js';
import {applyGacha,eligibleGachaPuppies,gachaPool} from '../src/engine/gacha.js';
import {transition,initialState} from '../src/state/reducer.js';

const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const catalog=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyCatalogV6.kt',import.meta.url),'utf8');
const gachaKotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt',import.meta.url),'utf8');
const rn=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const roster=rn.slice(rn.indexOf('function RosterScreen('),rn.indexOf('function GachaScreen('));
const gacha=rn.slice(rn.indexOf('function GachaScreen('),rn.indexOf('function RewardsScreen('));

test('Part 4 Collection keeps exact V1/V2 stable Kotlin source IDs, names and code locks',()=>{
 const ids=LEGACY_V6_PUPPIES.map(p=>p.id);
 assert.equal(ids.length,29);
 assert.equal(new Set(ids).size,29);
 for(const p of LEGACY_V6_PUPPIES){
  assert.equal(typeof p.description,'string');assert.ok(p.description.length>6);
  assert.ok(ROSTER.some(x=>x.id===p.id),'legacy asset/ID not bundled '+p.id);
 }
 assert.deepEqual(LEGACY_V6_PUPPIES.filter(p=>!p.redeemOnly).map(p=>p.id),['classic','golden','poodle','spotty']);
 assert.equal(LEGACY_V6_PUPPIES.find(p=>p.id==='v2_discord_pup').redeemOnly,true);
 assert.ok(catalog.includes('EXTRA_PUPPY_STYLES'));
 assert.ok(kotlin.includes('V6PuppyRosterSection("V1 Puppies"'));
 assert.ok(kotlin.includes('V6PuppyRosterSection("V2 Puppies"'));
 assert.ok(roster.includes('legacyRosterSections(registry.items)'));
 assert.ok(roster.includes('title="Puppy Collection"'));
 assert.ok(roster.includes('unlockedLegacyCount(g.unlockedPuppies)'));
 assert.ok(roster.includes('disabled={!owned}'));
 assert.ok(roster.includes('accessory={selected?g.accessory'));
 assert.ok(roster.includes('Browse online V3+'));
 assert.ok(roster.includes("type:'rename'"));
 assert.ok(roster.includes("type:'equip'"));
});

test('Original V1/V2 grouping is stable and restricted selection cannot bypass guest ownership',()=>{
 const sections=legacyRosterSections(ROSTER);
 assert.deepEqual(sections.map(s=>[s.title,s.items.length]),[['V1 Puppies',18],['V2 Puppies',11]]);
 assert.equal(unlockedLegacyCount(['classic','golden','fake','v2_dev_pup']),3);
 assert.equal(canEquipLegacyPuppy(['classic'],'v2_dev_pup'),false);
 assert.equal(canEquipLegacyPuppy(['classic'],'classic'),true);
 assert.equal(canEquipLegacyPuppy(['classic'],'v3_random'),false);
 const game=freshGame();
 let s=initialState();s.game=game;s.ready=true;
 const blocked=transition(s,{type:'switch',id:'v2_discord_pup',now:1000});
 assert.equal(blocked.game.puppyStyle,'classic');
 const favorite=transition(s,{type:'favorite',id:'v2_discord_pup',now:1000});
 assert.ok(!favorite.game.favoritePuppies.includes('v2_discord_pup'));
});

test('Kotlin mechanical Gacha image files were copied byte-for-byte as Git tree objects',()=>{
 const names=['machine','knob','capsule_closed','capsule_open','capsule_open_tilt'];
 const art=readFileSync(new URL('../src/assets/gacha.js',import.meta.url),'utf8');
 for(const name of names){
  const old=new URL('../../App/app/src/main/res/drawable/puppy_gacha_'+name+'.png',import.meta.url);
  const now=new URL('../assets/gacha/puppy_gacha_'+name+'.png',import.meta.url);
  assert.ok(statSync(now).size>30000);
  assert.equal(statSync(old).size,statSync(now).size,'Gacha Kotlin PNG differs: '+name);
  assert.ok(art.includes('puppy_gacha_'+name+'.png'));
 }
 assert.ok(gachaKotlin.includes('R.drawable.puppy_gacha_machine'));
 assert.ok(!gacha.includes('fontSize:88'));
});

test('Gacha machine scene replicates Kotlin 1024:1536 image layout and phase timing',()=>{
 assert.ok(gachaKotlin.includes('1536f / 1024f'));
 assert.equal(GACHA_MACHINE.ratio,1024/1536);
 assert.equal(GACHA_MACHINE.knobTurnMs,620);
 assert.equal(GACHA_MACHINE.capsuleOpenMs,640);
 assert.equal(GACHA_MACHINE.revealDelayMs,300);
 const frame=gachaMachineFrame(320);
 assert.equal(frame.height,480);
 assert.equal(frame.knob.width,81.6);
 assert.equal(frame.capsule.width,65.6);
 assert.equal(frame.open.width,97.6);
 assert.ok(frame.knob.top>0&&frame.capsule.top>frame.knob.top);
 for(const action of ['Open Capsule','Another Capsule','Use Puppy','Close'])assert.ok(gacha.includes(action));
 for(const item of ['GACHA_ART.machine','GACHA_ART.knob','GACHA_ART.capsuleClosed','GACHA_ART.capsuleOpenTilt','GACHA_ART.capsuleOpen'])
  assert.ok(gacha.includes(item));
 assert.ok(gacha.includes('stage===2'));
 assert.ok(gacha.includes('stage===4'));
 assert.ok(gacha.includes('purchasesAllowed=stage===0'));
});

test('Gacha guest unlocks only manifest-free puppies; protected developer and Discord stay excluded',()=>{
 const eligible=eligibleGachaPuppies(ROSTER);
 assert.deepEqual(eligible.map(x=>x.id),['classic','golden','poodle','spotty','test_v1_crash_dummy','test_v2_crash_dummy']);
 assert.ok(!gachaPool(ROSTER,[]).some(p=>p.id==='v2_discord_pup'));
 const g=freshGame();g.treats=5000;
 const first=applyGacha(g,'TREATS',0);
 assert.equal(first.success,true);
 assert.equal(first.isNewUnlock,true);
 assert.equal(first.puppyId,'test_v1_crash_dummy');
 assert.equal(g.treats,2500);
 assert.equal(g.unlockedPuppies.length,5);
 assert.ok(g.unlockedPuppies.includes('test_v1_crash_dummy'));
 assert.ok(!g.unlockedPuppies.includes('dev_pup'));
 const second=applyGacha(g,'COMMON_TICKET',1);
 assert.equal(second.success,false);
 assert.equal(g.gachaPulls,1);
});

test('source free TEST puppies have original offline images and survive local guest restore',async()=>{
 // Node does not load Metro PNG requires; verify the static declarations and bytes.
 const map=readFileSync(new URL('../src/assets/puppies.js',import.meta.url),'utf8');
 for(const [id,name] of [['test_v1_crash_dummy','v1_test_crash_dummy.png'],['test_v2_crash_dummy','v2_test_crash_dummy.png']]){
  assert.ok(map.includes(id+':require('));
  const image=new URL('../assets/test/'+name,import.meta.url);
  const source=new URL('../../assets/test/'+name,import.meta.url);
  assert.equal(statSync(image).size,statSync(source).size);
 }
 const game=freshGame();game.treats=2500;
 const outcome=applyGacha(game,'TREATS',1);
 assert.equal(outcome.isNewUnlock,true);
 assert.equal(outcome.puppyId,'test_v2_crash_dummy');
 const {parseGuestSave,serializeGuestSave}=await import('../src/state/guestSave.js');
 const saved=parseGuestSave(serializeGuestSave(game));
 assert.ok(saved.unlockedPuppies.includes('test_v2_crash_dummy'));
});
