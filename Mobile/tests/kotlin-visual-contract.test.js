import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const ui=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const settings=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt',import.meta.url),'utf8');

test('Kotlin and React Native preserve all five launcher tabs',()=>{
 for(const title of ['Play','Care','Shop','Prestige','Settings']){
  assert.match(kotlin,new RegExp('\\b'+title.toUpperCase()+'\\("'+title+'"'));
  assert.match(ui,new RegExp("'"+title+"'"));
 }
 assert.doesNotMatch(ui,/<WebView\\b/);
});

test('Play home renders the native wallet, needs pills and central tap stage',()=>{
 assert.match(kotlin,/V6Wallet\\(state\\)/);
 for(const label of ['Treats','Bones','Pup Coins','Casino Chips'])
  assert.match(ui,new RegExp("'"+label+"'"));
 for(const icon of ['💖','🍖','⚡','🫧'])assert.ok(ui.includes('icon="'+icon+'"'));
 assert.match(ui,/styles\\.tapStage/);
 assert.match(ui,/Tap \\{g\\.puppyName\\} · \\+\\{format\\(gain\\)\\} 🍪/);
});

test('Care and Shop retain native filtered pages',()=>{
 assert.match(kotlin,/FilterChip\\(selected = page == 0/);
 for(const title of ['💖 Care','📅 Daily','🍪 Upgrades','🎟️ Tickets','🪙 Pup Coins'])
  assert.ok(ui.includes(title),title);
 assert.match(ui,/kind:'feed'/);
 assert.match(ui,/kind:'wash'/);
});

test('settings layout follows native expandable categories without faking identity',()=>{
 for(const title of ['Profile','Discord','Appearance','Notifications','Privacy & Data','Gameplay','Data Management','Support & Reports','Advanced','Puppy Roster','PupEye Protection','Online Assets','Release Hub','About Puppy Clicker']){
  assert.ok(settings.includes('"'+title+'"'),title);
  assert.ok(ui.includes("'"+title+"'"),title);
 }
 assert.match(ui,/Guest · Level/);
 assert.match(ui,/original Kotlin app/);
});
