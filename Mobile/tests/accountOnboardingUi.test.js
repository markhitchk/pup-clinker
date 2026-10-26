import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync,existsSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import commonjs from '@babel/plugin-transform-modules-commonjs';
import * as theme from '../src/theme.js';
import {DEFAULT_PREFERENCES} from '../src/engine/settings.js';
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const file=new URL('../src/production/AccountOnboarding.jsx',import.meta.url),require=createRequire(file);
const native=Object.fromEntries(['View','Text','Pressable','ScrollView','Image','Switch','Modal','StatusBar','ActivityIndicator','KeyboardAvoidingView'].map(x=>[x,x]));
native.Platform={OS:'android'};native.Linking={openURL:async()=>{}};
native.BackHandler={addEventListener:()=>({remove(){}})};
const contents=r=>r.root.findAllByType('Text').flatMap(t=>t.props.children).join(' ').replace(/\s+/g,' ');
async function render(step,account=null){
 assert.ok(existsSync(file),'Kotlin Pup Account onboarding screen is missing');
 const module={exports:{}};
 new Function('require','module','exports',transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]}).code)(id=>
  id==='react-native'?native:id==='react-native-safe-area-context'?{useSafeAreaInsets:()=>({top:24,bottom:24})}:id.endsWith('/theme.js')?theme:id.endsWith('.png')?1:require(id),module,module.exports);
 let calls={connect:0,modes:[],next:0,review:0,request:0,finish:0,updates:[]},renderer;
 const flow={setup:{...DEFAULT_PREFERENCES,setupStep:step,birthdayMonth:0,birthdayDay:0},ready:true,busy:false,problem:'',update:async p=>calls.updates.push(p),next:async()=>calls.next++,back(){},review:async()=>calls.review++,finish:async()=>calls.finish++};
 await act(()=>{renderer=create(React.createElement(module.exports.default,{flow,account,onConnect:mode=>{calls.connect++;calls.modes.push(mode);},notifications:{permission:'undetermined',request:async()=>{calls.request++;return false;}}}));});
 return {renderer,calls,close:()=>act(()=>renderer.unmount())};
}
test('Welcome has original branding and starts the six-step flow',async()=>{
 const ui=await render(0);try{assert.match(contents(ui.renderer),/Step 1 of 6/);assert.match(contents(ui.renderer),/PupEye/);assert.match(contents(ui.renderer),/Roadmap/);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Get Started'}).props.onPress());assert.equal(ui.calls.next,1);assert.equal(ui.calls.request,0);}finally{await ui.close();}
});
test('Player Setup signs into Pup Account instead of asking for a guest nickname',async()=>{
 const ui=await render(1);try{assert.match(contents(ui.renderer),/Set Up Your Account/);assert.match(contents(ui.renderer),/Pup Account/);assert.doesNotMatch(contents(ui.renderer),/guest profile|Guest name/i);assert.equal(ui.renderer.root.findAllByType('TextInput').length,0);assert.equal(ui.renderer.root.findAllByProps({accessibilityLabel:'Continue with Pup Account'}).length,0);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Sign in with Pup Account'}).props.onPress());
 await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Create Pup Account'}).props.onPress());
 await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Sign in with Discord'}).props.onPress());
 assert.equal(ui.calls.connect,3);assert.deepEqual(ui.calls.modes,['signin','signup','discord']);}finally{await ui.close();}
});
test('connected Player Setup loads cloud via Continue',async()=>{
 const ui=await render(1,{status:'VERIFIED',username:'PuppyFan'});try{await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Continue with Pup Account'}).props.onPress());assert.equal(ui.calls.next,1);}finally{await ui.close();}
});
test('Personalize exposes theme, original accents, scale, motion and optional birthday',async()=>{
 const ui=await render(2);try{assert.match(contents(ui.renderer),/Birthday/);assert.match(contents(ui.renderer),/UI Scale/);assert.match(contents(ui.renderer),/Reduced Motion/);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Skip birthday'}).props.onPress());assert.deepEqual(ui.calls.updates.at(-1),{birthdayMonth:0,birthdayDay:0});}finally{await ui.close();}
});
test('privacy is optional and notifications do not prompt before their own step',async()=>{
 const ui=await render(3);try{assert.equal(ui.renderer.root.findAllByType('Switch').length,3);assert.ok(ui.renderer.root.findAllByType('Switch').every(s=>s.props.value===false));await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Continue'}).props.onPress());assert.equal(ui.calls.request,0);assert.equal(ui.calls.next,1);}finally{await ui.close();}
});
test('notification Not Now advances without requesting Android permission',async()=>{
 const ui=await render(4);try{assert.equal(ui.renderer.root.findAllByType('Switch').length,3);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Not Now'}).props.onPress());assert.equal(ui.calls.next,1);assert.equal(ui.calls.request,0);}finally{await ui.close();}
});
test('Ready displays native account identity and offers Review Setup',async()=>{
 const ui=await render(5,{status:'VERIFIED',username:'PuppyFan',playerId:'PC-EXAMPLE',friendCode:'PUP-ABCD-EFGH-JKLM'});try{const text=contents(ui.renderer);assert.match(text,/PC-EXAMPLE/);assert.match(text,/PUP-ABCD-EFGH-JKLM/);assert.match(text,/@PuppyFan/);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Review Setup'}).props.onPress());assert.equal(ui.calls.review,1);await act(()=>ui.renderer.root.findByProps({accessibilityLabel:'Start Playing'}).props.onPress());assert.equal(ui.calls.finish,1);}finally{await ui.close();}
});
