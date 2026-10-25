import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
const start=()=>({game:freshGame(),ready:true,notice:''});

test('appearance selections survive guest save and restart without altering balances',()=>{
 let s=start();s.game.treats=47;
 for(const [name,value] of Object.entries({themeMode:'light',accentHex:'#8b5cf6',uiScale:'large',highContrast:true,reducedMotion:true,performancePreset:'battery_saver',puppyPointerEnabled:false}))
  s=transition(s,{type:'setPreference',name,value});
 const g=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(g.themeMode,'light');assert.equal(g.accentHex,'#8B5CF6');assert.equal(g.uiScale,'large');
 assert.equal(g.highContrast,true);assert.equal(g.reducedMotion,true);assert.equal(g.performancePreset,'battery_saver');
 assert.equal(g.puppyPointerEnabled,false);assert.equal(g.treats,47);
});
test('invalid preferences and protected properties cannot be written through settings',()=>{
 for(const [name,value] of [['themeMode','bogus'],['uiScale',{}],['accentHex','#xx0000'],['highContrast','false'],['treats',999],['__proto__',{}]]){
  const s=start();assert.deepEqual(transition(s,{type:'setPreference',name,value}),s);
 }
});
test('reset settings preserves game, player name and birthday while restoring preferences',()=>{
 let s=start();s.game.treats=71;s.game.guestName='Puppy Fan';s.game.birthdayMonth=10;s.game.birthdayDay=24;
 s=transition(s,{type:'setPreference',name:'themeMode',value:'light'});
 s=transition(s,{type:'resetSettings'});
 assert.equal(s.game.themeMode,'system');assert.equal(s.game.treats,71);assert.equal(s.game.guestName,'Puppy Fan');assert.equal(s.game.birthdayDay,24);
});
test('old guest saves receive safe new defaults',()=>{
 const g=parseGuestSave(JSON.stringify({kind:'guest-only',schema:1,game:{puppyName:'Buddy',treats:123}}));
 assert.equal(g.themeMode,'system');assert.equal(g.puppyPointerEnabled,true);assert.equal(g.anonymousDiagnosticsEnabled,false);assert.equal(g.treats,123);
});

test('changing UI preferences and reading notifications never rewrites puppy care clocks',()=>{
 const s=start();s.game.lastActiveAt=100;s.game.careUpdatedAtByPuppy.classic=100;
 const next=transition(s,{type:'setPreference',name:'highContrast',value:true,now:5000});
 assert.equal(next.game.lastActiveAt,100);assert.equal(next.game.careUpdatedAtByPuppy.classic,100);
 const checked=transition(next,{type:'checkNotifications',now:5000});
 assert.equal(checked.game.lastActiveAt,100);
});
test('declining onboarding notifications disables all reminder categories',()=>{
 const s=transition(start(),{type:'onboardNotifications',enabled:false});
 assert.equal(s.game.dailyRewardNotifications,false);assert.equal(s.game.gameEventNotifications,false);assert.equal(s.game.updateNotifications,false);assert.equal(s.game.puppyAttentionNotifications,false);
});

test('malformed appearance flags fall back to safe defaults during restore',()=>{
 const raw={kind:'guest-only',schema:1,game:{themeMode:'danger',accentHex:'red',uiScale:['large'],highContrast:'false',reducedMotion:1}};
 const g=parseGuestSave(JSON.stringify(raw));assert.equal(g.themeMode,'system');assert.equal(g.accentHex,'#00B8F0');assert.equal(g.uiScale,'default');assert.equal(g.highContrast,false);assert.equal(g.reducedMotion,false);
});
