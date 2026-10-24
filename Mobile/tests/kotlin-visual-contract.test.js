import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const ui=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const settings=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt',import.meta.url),'utf8');

test('Kotlin and React Native preserve all five launcher tabs',()=>{
 for(const title of ['Play','Care','Shop','Prestige','Settings']){
  assert.ok(kotlin.includes(title.toUpperCase()+'("'+title+'"'),title+' missing in Kotlin');
  assert.ok(ui.includes("'"+title+"'"),title+' missing in React');
 }
 assert.ok(!ui.includes('<WebView'));
});

test('Play home renders the native wallet, needs pills and central tap stage',()=>{
 assert.ok(kotlin.includes('V6Wallet(state)'));
 for(const label of ['Treats','Bones','Pup Coins','Casino Chips'])
  assert.ok(ui.includes("'"+label+"'"),label);
 for(const icon of ['💖','🍖','⚡','🫧'])assert.ok(ui.includes('icon="'+icon+'"'),icon);
 assert.ok(ui.includes('styles.tapStage'));
 assert.ok(ui.includes('Tap {g.puppyName} · +{format(gain)} 🍪'));
});

test('Care, Shop and Roster retain native filtered pages',()=>{
 assert.ok(kotlin.includes('FilterChip(selected = page == 0'));
 for(const title of ['💖 Care','📅 Daily','🍪 Upgrades','🎟️ Tickets','🪙 Pup Coins'])
  assert.ok(ui.includes(title),title);
 assert.ok(ui.includes("kind:'feed'"));
 assert.ok(ui.includes("kind:'wash'"));
 for(const option of ['Unlocked','Locked','All Categories'])assert.ok(ui.includes(option),option);
});

test('settings layout follows native expandable categories without faking identity',()=>{
 for(const title of ['Profile','Discord','Appearance','Notifications','Privacy & Data','Gameplay','Data Management','Support & Reports','Advanced','Puppy Roster','PupEye Protection','Online Assets','Release Hub','About Puppy Clicker']){
  assert.ok(settings.includes('"'+title+'"'),title+' missing in Kotlin');
  assert.ok(ui.includes("'"+title+"'"),title+' missing in React');
 }
 assert.ok(ui.includes('Guest · Level'));
 assert.ok(ui.includes('original Kotlin app'));
});
