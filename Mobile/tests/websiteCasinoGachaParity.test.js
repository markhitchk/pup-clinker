import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import commonjs from '@babel/plugin-transform-modules-commonjs';
import {freshGame,ROSTER} from '../src/engine/game.js';
import {gachaPool,GACHA_TREAT_COST} from '../src/engine/gacha.js';
import {GACHA_MACHINE,gachaMachineFrame} from '../src/engine/gachaV6.js';
import {fairplayPresentation,accountStandingPresentation} from '../src/production/websitePupEyePresentation.js';

globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
const file=new URL('../src/components/WebsiteCasinoGachaScreens.jsx',import.meta.url);
const require=createRequire(file),module={exports:{}};
class Value{constructor(initial){this.value=initial;}interpolate(){return '0deg';}setValue(v){this.value=v;}stopAnimation(){}}
const native=Object.fromEntries(['View','Text','Image','Modal','Pressable'].map(id=>[id,id]));
native.useWindowDimensions=()=>({width:393,height:852});
native.Animated={Value,Image:'Image',sequence:()=>({start(){},stop(){}}),timing:()=>({start(){},stop(){}}),spring:()=>({start(){},stop(){}})};
native.Easing={ease:v=>v,inOut:v=>v};
const compiled=transformSync(read('src/components/WebsiteCasinoGachaScreens.jsx'),{
 babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]
}).code;
new Function('require','module','exports',compiled)(
 id=>id==='react-native'?native:require(id),module,module.exports);
const Txt=({children})=>React.createElement('Text',null,children);
const Button=({children,onPress,disabled=false})=>React.createElement('Pressable',{
 accessibilityRole:'button',onPress,disabled},React.createElement('Text',null,children));
const child=name=>()=>React.createElement('Text',null,'COMPONENT '+name);
const pages=[
 ['slots','🎰','Puppy Slots','Slots',1],
 ['roulette','🎡','Puppy Roulette','Roulette',1],
 ['blackjack','🃏','Puppy Blackjack','Blackjack',1],
 ['plinko','🔵','Puppy Plinko','Plinko',1],
 ['scratchers','🎟️','Puppy Scratchers','Scratchers',1],
 ['wheel','🎠','Lucky Pup Wheel','Wheel',1]
];
const ui=module.exports.createWebsiteCasinoGachaScreens({
 Txt,Button,styles:{},C:{accentInk:'#00E5FF',surface:'#12151F',line:'#333',text:'#FFF'},
 ROSTER,GACHA_ART:{machine:1,knob:1,capsuleClosed:1,capsuleOpen:1,capsuleOpenTilt:1},
 GACHA_MACHINE,gachaMachineFrame,gachaPool,GACHA_TREAT_COST,
 v6Format:value=>String(value),Portrait:child('Portrait'),
 PuppyPointer:({children})=>React.createElement('View',null,children),
 CASINO_PAGES:pages,format:value=>String(value),
 SlotsV6:child('Slots'),RouletteV6:child('Roulette'),PlinkoV6:child('Plinko'),
 ScratchersV6:child('Scratchers'),LuckyWheelV6:child('Wheel'),
 BlackjackV6Panel:child('Blackjack')
});
const text=renderer=>renderer.root.findAllByType('Text').map(x=>String(x.props.children||'')).join(' ');
function press(renderer,label){
 const p=renderer.root.findAllByType('Pressable').find(x=>
  x.findAllByType('Text').some(t=>String(t.props.children||'').includes(label)));
 assert.ok(p,'Missing native action '+label);
 return p;
}
test('website Gacha sends only payment intent to signed action boundary, never a client roll',async()=>{
 const g=freshGame();g.treats=5000;g.ticketInventory.COMMON=1;g.animationsEnabled=false;g.gameplayBusy=false;
 const results=[],state={gachaResult:null};let renderer;
 try{
  await act(()=>{renderer=create(React.createElement(ui.GachaScreen,{
   g,state,registry:{items:[]},act:action=>results.push(action),onBack:()=>{}
  }));});
  assert.match(text(renderer),/Puppy Gacha/);
  assert.equal(press(renderer,'Pull with Treats').props.disabled,false);
  await act(()=>press(renderer,'Pull with Treats').props.onPress());
  assert.deepEqual(results,[{type:'gacha',payment:'TREATS'}]);
  assert.ok(!results[0].roll);
  await act(()=>renderer.update(React.createElement(ui.GachaScreen,{
   g:{...g,gameplayBusy:true},state,registry:{items:[]},
   act:action=>results.push(action),onBack:()=>{}
  })));
  assert.equal(press(renderer,'Pull with Treats').props.disabled,true);
  assert.equal(press(renderer,'Use Common Ticket').props.disabled,true);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});
test('website Casino presents explicit simulated currency consent and six playable destinations',async()=>{
 const g=freshGame();g.gameplayBusy=false;const actions=[];let renderer;
 try{
  await act(()=>{renderer=create(React.createElement(ui.CasinoScreen,{
   g,state:{blackjack:null},bet:20,setBet:()=>{},act:a=>actions.push(a),onBack:()=>{}
  }));});
  assert.match(text(renderer),/Simulated Gambling/);
  assert.match(text(renderer),/No real money is used/);
  await act(()=>press(renderer,'Continue').props.onPress());
  for(const [id,emoji,title,label] of pages){
   // A new mounted screen emulates returning to the Casino from Rewards.
   await act(()=>renderer.update(React.createElement(ui.CasinoScreen,{
    key:id,g,state:{blackjack:null},bet:20,setBet:()=>{},
    act:a=>actions.push(a),onBack:()=>{}
   })));
   await act(()=>press(renderer,'Continue').props.onPress());
   assert.match(text(renderer),new RegExp(title),'missing '+id+' from casino hub');
   await act(()=>press(renderer,title).props.onPress());
   assert.match(text(renderer),new RegExp('COMPONENT '+label),'missing '+id+' game');
  }
  assert.deepEqual(actions,[]);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});
test('unverified PupEye status is never interpreted as a clean or unbanned account',()=>{
 const clear=fairplayPresentation(undefined);
 assert.equal(clear.status,'unavailable');
 assert.equal(clear.title,'Status unavailable');
 assert.equal(accountStandingPresentation({status:'BANNED'}).tone,'danger');
 assert.equal(accountStandingPresentation({status:'VERIFIED'}).title,'Verified Pup Account');
 const settings=read('src/components/SettingsScreen.jsx');
 assert.ok(settings.includes("fairplayPresentation(null)"));
 assert.ok(settings.includes("accountStandingPresentation(account)"));
 assert.ok(settings.includes("assets/logos/PupEye.png"));
 assert.ok(settings.includes("puppy-clicker.vercel.app/pupeye/status"));
 assert.doesNotMatch(settings,/riskScore|reviewCaseEvidence|internalRisk/);
});
test('signed Android transport and casino outcome must not be forged by website presentation',()=>{
 const app=read('App.js'),screen=read('src/components/WebsiteCasinoGachaScreens.jsx');
 const hook=read('src/state/useGame.js');
 assert.ok(app.includes('createWebsiteCasinoGachaScreens'));
 assert.ok(!app.includes('function CasinoScreen('));
 assert.ok(!app.includes('function GachaScreen('));
 assert.ok(screen.includes('function CasinoIntroDialog('));
 assert.ok(screen.includes("act({type:'gacha',payment})"));
 assert.ok(!screen.includes('Math.random()'));
 assert.ok(screen.includes('canRecoverLegacyCasino===true'));
 assert.ok(hook.includes('canRecoverLegacyCasino:false'));
 assert.ok(hook.includes('gameplayBusy:pendingProtectedActions>0'));
 assert.ok(hook.includes('native.gameplayAction(JSON.stringify(intent),PUBLISHABLE_KEY)'));
});
