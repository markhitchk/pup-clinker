import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import commonjs from '@babel/plugin-transform-modules-commonjs';

globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const listeners=new Set();
const native=Object.fromEntries(['ActivityIndicator','Modal','Pressable','ScrollView','StatusBar','Text','View'].map(name=>[name,name]));
native.StyleSheet={create:styles=>styles};
native.NativeModules={};
native.Linking={
 getInitialURL:async()=>null,
 addEventListener:(_name,fn)=>{listeners.add(fn);return {remove:()=>listeners.delete(fn)};}
};
const file=new URL('../src/production/BetaAccountEntry.jsx',import.meta.url);
const require=createRequire(file),module={exports:{}};
const compiled=transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[[jsx,{runtime:'automatic'}],commonjs]}).code;
new Function('require','module','exports',compiled)(id=>
 id==='react-native'?native:
 id==='react-native-safe-area-context'?{useSafeAreaInsets:()=>({top:24,bottom:24})}:require(id),module,module.exports);
const Entry=module.exports.default;
const contents=renderer=>renderer.root.findAllByType('Text').flatMap(t=>t.props.children).join(' ');
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const transferError=code=>Object.assign(new Error('Your Discord Pup Account is on the Play app. An authorized device transfer is required.'),{code});

for(const code of ['BETA_PUP_ACCOUNT_ON_OTHER_DEVICE','BETA_DISCORD_ALREADY_LINKED']){
 test(`Discord callback displays the transfer block from native error code ${code}`,async()=>{
  let renderer,verified=0;
  native.NativeModules.PuppyBetaAccount={
   status:async()=>({status:'NEEDS_DISCORD'}),
   completeDiscord:async()=>{throw transferError(code);}
  };
  try{
   await act(async()=>{renderer=create(React.createElement(Entry,{onVerified:()=>verified++}));await flush();});
   await act(async()=>{for(const fn of listeners)fn({url:'discord-1547982688898777118:/authorize/callback?code=test&state=test'});await flush();});
   assert.match(contents(renderer),/DEVICE TRANSFER REQUIRED/);
   assert.match(contents(renderer),/authorized.*transfer/i);
   assert.doesNotMatch(contents(renderer),/Confirm you selected/);
   assert.equal(verified,0);
  }finally{if(renderer)await act(()=>renderer.unmount());}
 });
}

test('account status uses the native code to identify a transfer block',async()=>{
 let renderer;
 native.NativeModules.PuppyBetaAccount={status:async()=>{throw transferError('BETA_PUP_ACCOUNT_ON_OTHER_DEVICE');}};
 try{
  await act(async()=>{renderer=create(React.createElement(Entry));await flush();});
  assert.match(contents(renderer),/DEVICE TRANSFER REQUIRED/);
  assert.doesNotMatch(contents(renderer),/server could not verify/);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});
