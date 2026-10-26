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
const native=Object.fromEntries(['ActivityIndicator','Modal','Pressable','ScrollView','StatusBar','Text','TextInput','View'].map(name=>[name,name]));
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

test('first-party Pup Account registration is available without Discord',async()=>{
 let renderer,verified=0,calls=[];
 native.NativeModules.PuppyBetaAccount={
  status:async()=>({status:'NEEDS_LOGIN'}),
  registerLocal:async(...args)=>{calls.push(args);return {status:'EMAIL_CONFIRMATION_REQUIRED'};},
  loginLocal:async()=>{throw new Error('should not log in')},
 };
 try{
  await act(async()=>{renderer=create(React.createElement(Entry,{onVerified:()=>verified++}));await flush();});
  await act(()=>renderer.root.findByProps({accessibilityLabel:'Register a Pup Account'}).props.onPress());
  await act(()=>{
   renderer.root.findByProps({accessibilityLabel:'Pup Account username'}).props.onChangeText('PuppyFan');
   renderer.root.findByProps({accessibilityLabel:'Pup Account email'}).props.onChangeText('test@example.com');
   renderer.root.findByProps({accessibilityLabel:'Pup Account password'}).props.onChangeText('somelongpassword');
  });
  await act(async()=>{renderer.root.findAllByType('Pressable').find(b=>b.findAllByType('Text').some(t=>t.props.children==='Register Pup Account')).props.onPress();await flush();});
  assert.equal(calls.length,1);
  assert.equal(calls[0][0],'PuppyFan');
  assert.equal(calls[0][1],'test@example.com');
  assert.match(contents(renderer),/Check your email/);
  assert.equal(verified,0);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});

test('username/password login verifies Pup Account and never calls Discord',async()=>{
 let renderer,verified=[],received=[];
 native.NativeModules.PuppyBetaAccount={
  status:async()=>({status:'NEEDS_LOGIN'}),
  loginLocal:async(identifier,password)=>{received.push({identifier,password});return {status:'VERIFIED',username:'PuppyFan',accountId:'account-1',discordUserId:''};},
  beginDiscord:async()=>{throw new Error('Discord not requested')},
 };
 try{
  await act(async()=>{renderer=create(React.createElement(Entry,{onVerified:account=>verified.push(account)}));await flush();});
  await act(()=>{
   renderer.root.findByProps({accessibilityLabel:'Pup Account username or email'}).props.onChangeText('PuppyFan');
   renderer.root.findByProps({accessibilityLabel:'Pup Account password'}).props.onChangeText('somelongpassword');
  });
  await act(async()=>{renderer.root.findAllByType('Pressable').find(b=>b.findAllByType('Text').some(t=>t.props.children==='Sign in with Pup Account')).props.onPress();await flush();});
  assert.equal(received.length,1);assert.equal(received[0].identifier,'PuppyFan');
  assert.equal(verified.length,1);assert.equal(verified[0].status,'VERIFIED');
  assert.match(contents(renderer),/Link Discord \(optional\)/);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});

test('existing email/password sign-in remains available in the same field',async()=>{
 let renderer,received=[];
 native.NativeModules.PuppyBetaAccount={
  status:async()=>({status:'NEEDS_LOGIN'}),
  loginLocal:async(identifier)=>{received.push(identifier);return {status:'VERIFIED',username:'PuppyFan',accountId:'account-1'};},
 };
 try{
  await act(async()=>{renderer=create(React.createElement(Entry));await flush();});
  await act(()=>{
   renderer.root.findByProps({accessibilityLabel:'Pup Account username or email'}).props.onChangeText('test@example.com');
   renderer.root.findByProps({accessibilityLabel:'Pup Account password'}).props.onChangeText('somelongpassword');
  });
  await act(async()=>{renderer.root.findAllByType('Pressable').find(b=>b.findAllByType('Text').some(t=>t.props.children==='Sign in with Pup Account')).props.onPress();await flush();});
  assert.deepEqual(received,['test@example.com']);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});

test('invalid short username fails locally before calling native auth',async()=>{
 let renderer,calls=0;
 native.NativeModules.PuppyBetaAccount={status:async()=>({status:'NEEDS_LOGIN'}),loginLocal:async()=>{calls++;}};
 try{
  await act(async()=>{renderer=create(React.createElement(Entry));await flush();});
  await act(()=>{
   renderer.root.findByProps({accessibilityLabel:'Pup Account username or email'}).props.onChangeText('a');
   renderer.root.findByProps({accessibilityLabel:'Pup Account password'}).props.onChangeText('somelongpassword');
  });
  await act(async()=>{renderer.root.findAllByType('Pressable').find(b=>b.findAllByType('Text').some(t=>t.props.children==='Sign in with Pup Account')).props.onPress();await flush();});
  assert.equal(calls,0);
  assert.match(contents(renderer),/Enter your username or email/);
 }finally{if(renderer)await act(()=>renderer.unmount());}
});
