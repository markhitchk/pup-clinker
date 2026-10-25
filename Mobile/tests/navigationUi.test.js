import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import commonjs from '@babel/plugin-transform-modules-commonjs';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {initialState,transition} from '../src/state/reducer.js';
import {mergeRoster,categoriesForRoster} from '../src/engine/dynamicRoster.js';
import {ROSTER} from '../src/engine/game.js';
import * as theme from '../src/theme.js';
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const backHandlers=new Set();
const native=Object.fromEntries(['View','Text','TextInput','Pressable','Image','ImageBackground','ScrollView','Modal','StatusBar'].map(n=>[n,n]));
class Value{constructor(v){this.value=v;}setValue(v){this.value=v;}stopAnimation(){}interpolate(){return 0;}}
const animation=()=>({start(){},stop(){}});
native.Animated={Value,View:'View',Image:'Image',createAnimatedComponent:x=>x,loop:animation,timing:animation,spring:animation,sequence:animation,parallel:animation,delay:animation};
native.Easing={ease:x=>x,inOut:x=>x};native.Alert={alert(){}};native.Vibration={vibrate(){}};
native.BackHandler={addEventListener:(_event,fn)=>{backHandlers.add(fn);return {remove:()=>backHandlers.delete(fn)};}};
native.useWindowDimensions=()=>({width:393,height:852});native.useColorScheme=()=> 'dark';
let fixture={},latestState;
function useGame(){const [state,dispatch]=React.useReducer(transition,null,()=>{const state=initialState();state.ready=true;state.game.onboardingComplete=true;state.game.animationsEnabled=false;state.game.animatedUi=false;Object.assign(state.game,fixture);return state;});latestState=state;return {state,act:dispatch};}
const file=new URL('../App.js',import.meta.url),require=createRequire(file),module={exports:{}};
const source=transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]}).code;
new Function('require','module','exports',source)(id=>{
 if(id==='react-native')return native;
 if(id==='react-native-safe-area-context')return {SafeAreaProvider:({children})=>children,useSafeAreaInsets:()=>({top:24,bottom:24})};
 if(id.endsWith('/theme.js'))return theme;
 if(id==='./src/state/useGame.js')return {useGame};
 if(id==='./src/state/useRoster.js')return {useRoster:()=>({items:mergeRoster(),source:'bundled',categories:categoriesForRoster(mergeRoster()),refresh(){},clearCache(){}})};
 if(id==='./src/state/useNotifications.js')return {useNotifications:()=>({check(){},request(){}})};
 if(id==='./src/state/useLegacyCompatibility.js')return {useLegacyCompatibility:()=>({loading:false,status:'preview-fresh-install',requiresMigration:false,legacyDataDetected:false})};
 if(id==='./src/state/useLocalDiagnostics.js')return {useLocalDiagnostics:()=>({summary:'',clear(){}})};
 if(id==='./src/assets/puppies.js')return {PUPPY_IMAGES:Object.fromEntries(ROSTER.map(p=>[p.id,1]))};
 if(id==='./src/assets/gacha.js')return {GACHA_ART:{}};
 if(id.endsWith('.png'))return 1;
 if(id==='./src/components/SettingsScreen.jsx')return {__esModule:true,default:()=>React.createElement('Text',null,'Settings page')};
 if(id==='./src/components/PuppyPointer.jsx')return {__esModule:true,default:({children,...p})=>React.createElement('View',p,children)};
 if(id.startsWith('./src/components/'))return {__esModule:true,default:()=>null};
 return require(id);
},module,module.exports);
const App=module.exports.default;
const text=root=>root.findAllByType('Text').map(t=>t.props.children).flat().join(' ');
async function render(values={}){fixture=values;let renderer;await act(()=>{renderer=create(React.createElement(App));});return renderer;}
test('primary navigation preserves Play, Care, Roster, Shop and Rewards with working routes',async()=>{
 const renderer=await render();
 try{
  const tabs=()=>renderer.root.findAllByType('Pressable').filter(p=>p.props.accessibilityRole==='tab');
  const names=tabs().map(p=>text(p));assert.ok(names.some(n=>n.includes('Roster')),'Roster is missing from the primary bar');assert.ok(names.some(n=>n.includes('Rewards')),'Rewards is missing from the primary bar');
  assert.equal(names.length,5);assert.ok(!names.some(n=>n.includes('Settings')||n.includes('Prestige')));
  await act(()=>tabs().find(p=>text(p).includes('Roster')).props.onPress());assert.match(text(renderer.root),/Puppy Roster/);
  await act(()=>tabs().find(p=>text(p)==='🎁 Rewards').props.onPress());assert.match(text(renderer.root),/Today’s goals/);assert.match(text(renderer.root),/Puppy Casino/);
 }finally{await act(()=>renderer.unmount());}
});
test('header Settings keeps the current primary destination and hides its bar until Back',async()=>{
 const renderer=await render();
 try{
  await act(()=>renderer.root.findAllByType('Pressable').find(p=>p.props.accessibilityRole==='tab'&&text(p).includes('Shop')).props.onPress());
  await act(()=>renderer.root.findByProps({accessibilityLabel:'Open settings'}).props.onPress());
  assert.equal(renderer.root.findAllByType('Pressable').filter(p=>p.props.accessibilityRole==='tab').length,0,'Internal Settings must hide the primary bar');
  let handled;await act(()=>{handled=[...backHandlers].at(-1)?.();});assert.equal(handled,true);assert.match(text(renderer.root),/Puppy Shop/);
 }finally{await act(()=>renderer.unmount());}
});
test('Play uses a single app brand, a transparent stage and a live next-reward card',async()=>{
 const renderer=await render();
 try{
  const body=text(renderer.root);assert.match(body,/Next Reward/);
  assert.equal(renderer.root.findAllByType('Text').filter(t=>t.props.children==='Puppy Clicker').length,1,'The fixed header must not duplicate the legacy Play header');
 }finally{await act(()=>renderer.unmount());}
});

const pressLabel=async(renderer,label)=>act(()=>renderer.root.findAllByType('Pressable').find(p=>p.props.accessibilityLabel===label).props.onPress({stopPropagation(){}}));
const goTab=async(renderer,label)=>act(()=>renderer.root.findAllByType('Pressable').find(p=>p.props.accessibilityRole==='tab'&&text(p)===({Care:'💖 Care',Roster:'🐶 Roster',Rewards:'🎁 Rewards'}[label]||label)).props.onPress());
test('Care exposes compact meters and a 3+2 action grid with real costs and care effects',async()=>{
 const renderer=await render({treats:100,bones:5,energy:40,fullness:30,happiness:50,cleanliness:30});
 try{
  await goTab(renderer,'Care');
  assert.equal(renderer.root.findAllByType('Text').filter(t=>t.props.children==='Puppy Clicker').length,1);
  for(const label of ['Happiness','Fullness','Energy','Cleanliness','Bond'])assert.equal(renderer.root.findAllByProps({accessibilityRole:'progressbar',accessibilityLabel:label}).length,1,'Missing compact '+label+' meter');
  const feed=renderer.root.findByProps({accessibilityLabel:'Feed, costs 20 Treats'});
  assert.equal(feed.parent.findAllByType('Pressable').length,3,'Feed, Play and Clean share the first row');
  const rest=renderer.root.findByProps({accessibilityLabel:'Rest, free, restores 30 energy'});assert.equal(rest.parent.findAllByType('Pressable').length,2);
  await act(()=>feed.props.onPress());assert.equal(latestState.game.treats,80);assert.equal(latestState.game.fullness,55);
  await act(()=>rest.props.onPress());assert.equal(latestState.game.energy,70);
  await pressLabel(renderer,'Clean, costs 10 Treats');assert.equal(latestState.game.treats,70);assert.equal(latestState.game.cleanliness,64);
 }finally{await act(()=>renderer.unmount());}
});
test('Roster searches native asset IDs and keeps rename, accessory and card favorites on the main screen',async()=>{
 const renderer=await render();
 try{
  await goTab(renderer,'Roster');
  assert.ok(renderer.root.findAllByProps({accessibilityLabel:'Open Puppy Exchange'}).length);
  assert.match(text(renderer.root),/Current Puppy/);assert.match(text(renderer.root),/Accessory:\s+None/);
  const search=renderer.root.findByProps({accessibilityLabel:'Search puppies or ID'});
  await act(()=>search.props.onChangeText('V1_POODLE'));assert.equal(renderer.root.findAllByType('Pressable').filter(p=>String(p.props.accessibilityLabel).startsWith('Inspect ')).length,1);
  await pressLabel(renderer,'Add Buddy to favorites');assert.ok(latestState.game.favoritePuppies.includes('classic'));
  await pressLabel(renderer,'Rename current puppy');
  await act(()=>renderer.root.findByProps({accessibilityLabel:'Rename puppy'}).props.onChangeText('Biscuit'));
  await pressLabel(renderer,'Save puppy name');assert.equal(latestState.game.puppyName,'Biscuit');
  await pressLabel(renderer,'Choose accessory');
  assert.ok(renderer.root.findAllByProps({accessibilityLabel:'Equip None'}).length);
 }finally{await act(()=>renderer.unmount());}
});
test('Care keeps unaffordable actions disabled and shows the tired threshold correctly',async()=>{
 const renderer=await render({treats:0,bones:0,energy:10});
 try{
  await goTab(renderer,'Care');
  for(const label of ['Feed, costs 20 Treats','Play, costs 1 Bone and 12 energy','Clean, costs 10 Treats'])assert.equal(renderer.root.findByProps({accessibilityLabel:label}).props.disabled,true);
  assert.match(text(renderer.root),/Rest above 10% energy/);
  await pressLabel(renderer,'Rest, free, restores 30 energy');assert.equal(latestState.game.energy,40);assert.equal(latestState.game.treats,0);assert.equal(latestState.game.bones,0);
 }finally{await act(()=>renderer.unmount());}
});
