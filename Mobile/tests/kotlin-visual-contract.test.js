import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const ui=app+readFileSync(new URL('../src/components/SettingsScreen.jsx',import.meta.url),'utf8');
const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const settings=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt',import.meta.url),'utf8');

test('React navigation matches the shipped Kotlin destination model and build patches',()=>{
 const navigation=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyNavigationModel.kt',import.meta.url),'utf8');
 const pipeline=readFileSync(new URL('../../App/tools/seasonal.gradle.kts',import.meta.url),'utf8');
 const patch=readFileSync(new URL('../../App/tools/patch_main_ui_revamp.py',import.meta.url),'utf8');
 for(const title of ['Play','Care','Roster','Shop','Rewards'])assert.ok(navigation.includes('("'+title+'",'),title);
 assert.ok(pipeline.includes('commandLine("python3", rosterNavigationPatch.absolutePath'));
 assert.ok(pipeline.includes('commandLine("python3", mainUiRevampPatch.absolutePath'));
 assert.ok(patch.includes('V6Tab.PLAY -> PuppyRevampedPlayScreen('));
 assert.ok(!ui.includes('<WebView'));
});

test('Shop and Roster retain native filtered pages',()=>{
 for(const title of ['🍪 Upgrades','🎟️ Tickets','🪙 Pup Coins'])
  assert.ok(ui.includes(title),title);
 assert.ok(ui.includes("type:'care',kind,now:Date.now()"));
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
