import test from 'node:test';
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
const callbacks=new Set();
const native=Object.fromEntries(['View','Text','Pressable','ScrollView','Image','TextInput','Switch','Modal'].map(x=>[x,x]));
native.BackHandler={addEventListener:(_name,fn)=>{callbacks.add(fn);return {remove:()=>callbacks.delete(fn)};}};
native.Alert={alert:()=>{}};native.Share={share:async()=>{}};native.Linking={openURL:()=>{}};native.Vibration={vibrate:()=>{}};
function load(relative){
 const file=new URL(relative,import.meta.url),require=createRequire(file);
 const compiled=transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]}).code;
 const module={exports:{}};
 const mocked=id=>id==='react-native'?native:id==='../theme.js'?theme:id.includes('/assets/puppies')?{PUPPY_IMAGES:{classic:1}}:id.endsWith('.png')?1:id==='./PuppyPointer.jsx'?{default:'PuppyPointer',__esModule:true}:require(id);
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
