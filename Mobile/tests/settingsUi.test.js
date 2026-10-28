import test,{mock} from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import commonjs from '@babel/plugin-transform-modules-commonjs';
import * as theme from '../src/theme.js';
import {freshGame} from '../src/engine/game.js';
import {transition} from '../src/state/reducer.js';
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const callbacks=new Set(),appCallbacks=new Set();
class Value{constructor(v){this.value=v;}setValue(v){this.value=v;}interpolate(){return 0;}}

const native=Object.fromEntries(['View','Text','Pressable','ScrollView','Image','TextInput','Switch','Modal'].map(x=>[x,x]));
native.BackHandler={addEventListener:(_name,fn)=>{callbacks.add(fn);return {remove:()=>callbacks.delete(fn)};}};
native.AppState={currentState:'active',addEventListener:(_name,fn)=>{appCallbacks.add(fn);return {remove:()=>appCallbacks.delete(fn)};}};
native.Animated={Value,View:'View',loop:()=>({start(){},stop(){}}),timing:()=>({start(){},stop(){}})};
native.Easing={linear:x=>x};native.useWindowDimensions=()=>({width:393,height:852});
native.Alert={alert:()=>{}};native.Share={share:async()=>{}};native.Linking={openURL:()=>{}};native.Vibration={vibrate:()=>{}};
function load(relative){
 const file=new URL(relative,import.meta.url),require=createRequire(file);
 const compiled=transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]}).code;
 const module={exports:{}};
 const mocked=id=>id==='react-native-safe-area-context'?{useSafeAreaInsets:()=>({top:24,bottom:24})}:id==='react-native'?native:id==='../theme.js'?theme:id.includes('/assets/puppies')?{PUPPY_IMAGES:{classic:1}}:id.endsWith('.png')?1:id==='./PuppyPointer.jsx'?{default:'PuppyPointer',__esModule:true}:id.endsWith('.jsx')?{default:load(new URL(id,file).href),__esModule:true}:require(id);
 new Function('require','module','exports',compiled)(mocked,module,module.exports);return module.exports.default;
}
const Settings=load('../src/components/SettingsScreen.jsx');
const Inbox=load('../src/components/NotificationInbox.jsx');
const notifications={permission:'denied',request:()=>{},sendTest:()=>{},openSystemSettings:()=>{},check:()=>{}};
const registry={items:[{}],source:'bundled',refresh:()=>{},clearCache:()=>{}};
async function settings(){
 let state={game:freshGame(),ready:true,notice:''},renderer;
 const dispatch=action=>{state=transition(state,action);theme.applyAppearance(state.game,true);renderer.update(render());};
 const render=()=>React.createElement(Settings,{g:state.game,act:dispatch,onOpen:()=>{},registry,notifications,diagnostics:{clear:()=>{}}});
 theme.applyAppearance(state.game,true);await act(()=>{renderer=create(render());});
 return {renderer,state:()=>state,press:async label=>act(()=>renderer.root.findAllByType('Pressable').find(x=>x.props.accessibilityLabel===label).props.onPress())};
}
test('Appearance opens real controls, saves light theme and collapses on Android Back',async()=>{
 const ui=await settings();
 assert.equal(ui.renderer.root.findAllByType('Switch').length,0);
 await ui.press('Appearance');
 assert.ok(ui.renderer.root.findAllByType('Switch').some(x=>x.props.accessibilityLabel==='Reduced motion'));
 const light=ui.renderer.root.findAllByType('Pressable').find(x=>x.findAllByType('Text').some(t=>t.props.children==='Light'));
 await act(()=>light.props.onPress());assert.equal(ui.state().game.themeMode,'light');assert.equal(theme.colors.bg,'#FFFBFF');
 let handled;await act(()=>{handled=[...callbacks].at(-1)();});assert.equal(handled,true);assert.equal(ui.renderer.root.findAllByType('Switch').length,0);
 await act(()=>ui.renderer.unmount());
});
test('Gameplay pointer switch changes a saved preference and leaves wallet intact',async()=>{
 const ui=await settings();await ui.press('Gameplay');
 const pointer=ui.renderer.root.findAllByType('Switch').find(x=>x.props.accessibilityLabel==='Puppy mouse pointer');
 assert.equal(pointer.props.value,true);await act(()=>pointer.props.onValueChange(false));
 assert.equal(ui.state().game.puppyPointerEnabled,false);assert.equal(ui.state().game.treats,0);
 await act(()=>ui.renderer.unmount());
});
test('inbox filtering, marking and clearing operate on real reducer state',async()=>{
 let state={game:freshGame(),ready:true,notice:''},renderer;
 state=transition(state,{type:'testNotification',now:Date.now()});
 const render=()=>React.createElement(Inbox,{visible:true,g:state.game,act:action=>{state=transition(state,action);renderer.update(render());},onClose:()=>{}});
 await act(()=>{renderer=create(render());});
 const labelButton=text=>renderer.root.findAllByType('Pressable').find(p=>p.findAllByType('Text').some(t=>String(t.props.children).includes(text)));
 await act(()=>labelButton('Reward').props.onPress());assert.ok(renderer.root.findAllByType('Text').some(t=>t.props.children==='All caught up!'));
 await act(()=>labelButton('System').props.onPress());assert.ok(renderer.root.findAllByType('Text').some(t=>t.props.children==='Test notification'));
 await act(()=>labelButton('Mark All Read').props.onPress());assert.equal(state.game.notificationInbox[0].read,true);
 await act(()=>labelButton('Clear Read').props.onPress());assert.equal(state.game.notificationInbox.length,0);
 await act(()=>renderer.unmount());
});

const contents=root=>root.findAllByType('Text').flatMap(t=>t.props.children).join(' ');
const buttonWith=(root,label)=>root.findAllByType('Pressable').find(p=>p.props.accessibilityLabel===label||p.findAllByType('Text').some(t=>[t.props.children].flat().join('')===label));
test('all Settings destructive routes open the original hold confirmation without changing data',async()=>{
 const ui=await settings();
 try{
  await ui.press('Data Management');await act(()=>buttonWith(ui.renderer.root,'Danger Zone ⌄').props.onPress());
  for(const [label,hold] of [['Reset Settings','Hold to Reset Settings'],['Reset Game Progress','Hold to Reset Progress'],['Delete Local Save Data','Hold to Delete Local Data']]){
   const before=JSON.stringify(ui.state().game);
   await act(()=>buttonWith(ui.renderer.root,label).props.onPress());
   assert.match(contents(ui.renderer.root),label==='Reset Settings'?/PREFERENCES/:/DANGER ZONE/,'The hold confirmation should identify its action clearly');
   assert.ok(ui.renderer.root.findAllByProps({accessibilityLabel:hold}).length);
   assert.equal(JSON.stringify(ui.state().game),before);
   await act(()=>buttonWith(ui.renderer.root,'Cancel').props.onPress());
   assert.equal(JSON.stringify(ui.state().game),before);
  }
 }finally{await act(()=>ui.renderer.unmount());}
});

async function holdUi(action='delete'){
 const Dialog=load('../src/components/DangerHoldDialog.jsx');
 let confirmed=0,dismissed=0,renderer;
 await act(()=>{renderer=create(React.createElement(Dialog,{action,g:freshGame(),onDismiss:()=>dismissed++,onConfirmed:()=>confirmed++}));});
 const button=()=>renderer.root.findAllByType('View').find(v=>v.props.onResponderGrant);
 await act(()=>button().props.onLayout({nativeEvent:{layout:{width:300,height:68}}}));
 const touch=(x=20,y=20,count=1)=>({nativeEvent:{locationX:x,locationY:y,touches:Array.from({length:count},(_,i)=>({identifier:i}))}});
 return {renderer,button,touch,confirmed:()=>confirmed,dismissed:()=>dismissed};
}
test('a danger hold confirms once after ten uninterrupted seconds and a tap cannot confirm',async()=>{
 let now=0;mock.method(performance,'now',()=>now);mock.timers.enable({apis:['setInterval']});
 const ui=await holdUi();
 try{
  await act(()=>ui.button().props.onResponderGrant(ui.touch()));now=9900;await act(()=>mock.timers.tick(9900));assert.equal(ui.confirmed(),0);
  await act(()=>ui.button().props.onResponderRelease());now=10000;await act(()=>mock.timers.tick(100));assert.equal(ui.confirmed(),0);
  await act(()=>ui.button().props.onResponderGrant(ui.touch()));now=19999;await act(()=>mock.timers.tick(9999));assert.equal(ui.confirmed(),0);
  now=20000;await act(()=>mock.timers.tick(50));assert.equal(ui.confirmed(),1);
  now=40000;await act(()=>mock.timers.tick(20000));assert.equal(ui.confirmed(),1);
 }finally{await act(()=>ui.renderer.unmount());mock.restoreAll();mock.timers.reset();}
});
test('moving off, multitouch, losing app focus and unmount cancel without resuming a danger hold',async()=>{
 let now=0;mock.method(performance,'now',()=>now);mock.timers.enable({apis:['setInterval']});
 const ui=await holdUi('settings');
 try{
  for(const cancel of [()=>ui.button().props.onResponderMove(ui.touch(301)),()=>ui.button().props.onResponderStart(ui.touch(20,20,2)),()=>{native.AppState.currentState='background';for(const cb of appCallbacks)cb('background');},()=>ui.button().props.onResponderTerminate()]){
   native.AppState.currentState='active';await act(()=>ui.button().props.onResponderGrant(ui.touch()));now+=9000;await act(()=>mock.timers.tick(9000));
   await act(cancel);native.AppState.currentState='active';for(const cb of appCallbacks)cb('active');now+=11000;await act(()=>mock.timers.tick(11000));assert.equal(ui.confirmed(),0);
  }
  await act(()=>ui.button().props.onResponderGrant(ui.touch()));await act(()=>ui.renderer.unmount());now+=10000;await act(()=>mock.timers.tick(10000));assert.equal(ui.confirmed(),0);
 }finally{await act(()=>ui.renderer.unmount());mock.restoreAll();mock.timers.reset();native.AppState.currentState='active';}
});
test('closing a held danger dialog cancels the timer before it can dispatch',async()=>{
 let now=0;mock.method(performance,'now',()=>now);mock.timers.enable({apis:['setInterval']});
 const ui=await holdUi('progress');
 try{
  await act(()=>ui.button().props.onResponderGrant(ui.touch()));now=9900;await act(()=>mock.timers.tick(9900));
  await act(()=>ui.renderer.root.findByType('Modal').props.onRequestClose());
  now=25000;await act(()=>mock.timers.tick(15100));assert.equal(ui.confirmed(),0);assert.equal(ui.dismissed(),1);
 }finally{await act(()=>ui.renderer.unmount());mock.restoreAll();mock.timers.reset();}
});
test('AFK notification Claim sends a signed claim intent instead of locally crediting Treats',async()=>{
 let state={game:freshGame(),ready:true,notice:''},renderer;
 const now=Date.now(),id='afk:'+(now-500000)+':'+now;
 state.game.notificationInbox=[{id,category:'pup',title:'Your puppies saved some Treats!',body:'Your puppies saved 5 Treats while you were away.',createdAt:now,read:false,action:'afkClaim'}];
 const calls=[];
 const render=()=>React.createElement(Inbox,{visible:true,g:state.game,act:action=>calls.push(action),onClose:()=>{}});
 await act(()=>{renderer=create(render());});
 try{
  const claim=renderer.root.findAllByType('Pressable').find(p=>String(p.props.accessibilityLabel).startsWith('Claim '));
  assert.ok(claim,'server-authored AFK rewards retain a Claim control');
  await act(()=>claim.props.onPress());
  assert.deepEqual(calls,[{type:'afkClaim',id}]);
  assert.equal(state.game.treats,0,'UI never manufactures rewards');
 }finally{await act(()=>renderer.unmount());}
});
test('daily reward notification navigates to Rewards and does not claim without confirmation',async()=>{
 let state={game:freshGame(),ready:true,notice:''},renderer,route=null,closed=0;
 state=transition(state,{type:'checkNotifications',now:Date.now()});
 const render=()=>React.createElement(Inbox,{visible:true,g:state.game,act:action=>{state=transition(state,action);},onClose:()=>closed++,onNavigate:r=>route=r});
 await act(()=>{renderer=create(render());});
 try{
  assert.equal(renderer.root.findAllByType('Pressable').filter(p=>String(p.props.accessibilityLabel).startsWith('Claim ')).length,0);
  const daily=renderer.root.findAllByType('Pressable').find(p=>String(p.props.accessibilityLabel).includes('Daily Puppy Reward available'));
  assert.ok(daily);
  await act(()=>daily.props.onPress());
  assert.equal(route,'rewards');assert.equal(closed,1);assert.equal(state.game.treats,0);
 }finally{await act(()=>renderer.unmount());}
});
test('inbox and danger windows explicitly draw edge to edge before applying safe insets',async()=>{
 const ui=await holdUi();let inbox;
 try{
  await act(()=>{inbox=create(React.createElement(Inbox,{visible:true,g:freshGame(),act(){},onClose(){}}));});
  for(const renderer of [ui.renderer,inbox]){
   const modal=renderer.root.findByType('Modal');
   assert.equal(modal.props.statusBarTranslucent,true,'Native Modal must not add a second status-bar inset');
   assert.equal(modal.props.navigationBarTranslucent,true,'The modal must share the full-window safe-area coordinate system');
  }
 }finally{await act(()=>{ui.renderer.unmount();inbox?.unmount();});}
});
