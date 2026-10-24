import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const ui=readFileSync(new URL('../App.js',import.meta.url),'utf8');
const kotlin=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt',import.meta.url),'utf8');
const settings=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt',import.meta.url),'utf8');

const revamp=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt',import.meta.url),'utf8');
const navigation=readFileSync(new URL('../../App/app/src/main/java/com/harleytg/puppyclicker/PuppyNavigationModel.kt',import.meta.url),'utf8');

test('React Android presents Kotlin current five main destinations, with settings and prestige internal',()=>{
 for(const title of ['Play','Care','Roster','Shop','Rewards']){
  assert.ok(navigation.includes('("'+title+'"'),title+' missing from Kotlin destination model');
  assert.ok(ui.includes("'"+title+"'"),title+' missing in React');
 }
 assert.ok(ui.includes("['roster','🐶','Roster']"));
 assert.ok(ui.includes("['rewards','🎁','Rewards']"));
 assert.ok(ui.includes("secondary==='prestige'"));
 assert.ok(ui.includes("secondary==='settings'"));
 assert.ok(!ui.includes('<WebView'));
});

test('current Kotlin revamped Play screenshot contract is present',()=>{
 assert.ok(revamp.includes('PuppyRevampedPlayScreen'));
 assert.ok(revamp.includes('PuppyWalletCard(state)'));
 assert.ok(revamp.includes('Next Reward ·'));
 assert.ok(ui.includes('function PlayScreen({g,act,clock,schedule,availableHeight})'));
 assert.ok(ui.includes('const viewport=availableHeight||Math.max(0,height-160);'));
 assert.ok(ui.includes('const tiny=viewport<590,compact=viewport<700;'));
 assert.ok(ui.includes('SafeAreaProvider,useSafeAreaInsets'));
 assert.ok(ui.includes('height:48'));
 assert.ok(ui.includes('revampedTabIndicatorActive'));
 assert.ok(ui.includes('inGame&&!secondary?<View style={styles.bottomBar}>'));
 assert.ok(ui.includes('<PageHeader title="Play"'));
 assert.ok(ui.includes('styles.revampedTapStage'));
 assert.ok(ui.includes('styles.revampedTapButton'));
 assert.ok(ui.includes('styles.revampedEnergy'));
 assert.ok(ui.includes('styles.nextRewardCard'));
 assert.ok(ui.includes('Next Reward · '));
 assert.ok(ui.includes('goalsForDay(schedule,today)'));
 assert.ok(ui.includes('progressForGoal(g,nextGoal,today)'));
 assert.ok(ui.includes('styles.revampedAppHeader'));
 assert.ok(ui.includes('accessibilityLabel="Notifications"'));
 assert.ok(ui.includes('accessibilityLabel="Settings"'));
 assert.ok(!ui.slice(ui.indexOf('function PlayScreen('),ui.indexOf('// Part 2:')).includes('<NeedPill'));
 for(const label of ['Treats','Bones','Pup Coins','Casino Chips'])assert.ok(ui.includes("'"+label+"'"));
});

test('Kotlin dedicated Care, Shop filters and Roster collection remain reachable',()=>{
 assert.ok(revamp.includes('PuppyRevampedCareScreen'));
 assert.ok(revamp.includes('PuppyRevampedShopScreen'));
 for(const title of ['🍪 Upgrades','🎟️ Tickets','🪙 Pup Coins'])assert.ok(ui.includes(title),title);
 for(const kind of ['feed','play','wash','rest','cuddle'])assert.ok(ui.includes("careAction('"+kind+"'"));
 assert.ok(ui.includes("type:'care',kind,now:Date.now()"));
 assert.ok(ui.includes('styles.revampedCareMeterTrack'));
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


test('Kotlin fixed notifications entry opens a modal, not an unverified full-page inbox',()=>{
 assert.ok(ui.includes('const [notificationsVisible,setNotificationsVisible]=useState(false)'));
 assert.ok(ui.includes('onPress={()=>setNotificationsVisible(true)}'));
 assert.ok(ui.includes('visible={notificationsVisible} transparent animationType="fade"'));
 assert.ok(ui.includes('kotlinNotificationsDialog'));
});
