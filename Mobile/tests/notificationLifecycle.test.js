import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {transformSync} from '@babel/core';
import commonjs from '@babel/plugin-transform-modules-commonjs';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {freshGame} from '../src/engine/game.js';
import {localEpochDay} from '../src/engine/monthlyRewards.js';

globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const listeners=new Set(),scheduled=new Map();
const platform={OS:'android'};
const native={Platform:platform,Linking:{openSettings:()=>{}},AppState:{currentState:'active',addEventListener:(_name,fn)=>{listeners.add(fn);return {remove:()=>listeners.delete(fn)};}}};
const notifications={
 AndroidImportance:{DEFAULT:3},SchedulableTriggerInputTypes:{DATE:'date',DAILY:'daily',TIME_INTERVAL:'timeInterval'},
 getPermissionsAsync:async()=>({granted:true,canAskAgain:true}),setNotificationChannelAsync:async()=>{},
 requestPermissionsAsync:async()=>({granted:true,canAskAgain:true}),setNotificationHandler:()=>{},
 addNotificationReceivedListener:()=>({remove:()=>{}}),addNotificationResponseReceivedListener:()=>({remove:()=>{}}),
 getLastNotificationResponseAsync:async()=>null,clearLastNotificationResponseAsync:async()=>{},
 cancelScheduledNotificationAsync:async id=>scheduled.delete(id),scheduleNotificationAsync:async request=>{scheduled.set(request.identifier,request);return request.identifier;}
};
const file=new URL('../src/state/useNotifications.js',import.meta.url),require=createRequire(file),module={exports:{}};
new Function('require','module','exports',transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[commonjs]}).code)(id=>id==='react-native'?native:id==='expo-notifications'?notifications:require(id),module,module.exports);
const useNotifications=module.exports.useNotifications;
const dispatch=()=>{};
function Harness({game,ready}){useNotifications({game,ready,act:dispatch,onOpen:()=>{}});return null;}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('daily schedule honors tomorrow after a morning claim; background attention cancels on resume and save deletion',async()=>{
 const originalNow=Date.now,now=new Date(2026,9,24,9).getTime();Date.now=()=>now;
 let renderer;
 try{
  const game={...freshGame(),lastDailyClaimDay:localEpochDay(new Date(now)),puppyAttentionNotifications:true};
  await act(async()=>{renderer=create(React.createElement(Harness,{game,ready:true}));await flush();});
  const daily=scheduled.get('puppy-daily');assert.ok(daily);
  assert.equal(daily.trigger.type,'date');assert.equal(daily.trigger.date.getDate(),25);assert.equal(daily.trigger.date.getHours(),10);
  assert.equal(scheduled.has('puppy-attention'),false);
  await act(async()=>{for(const fn of listeners)fn('background');await flush();});
  const attention=scheduled.get('puppy-attention');assert.ok(attention);
  assert.equal(attention.trigger.type,'timeInterval');assert.equal(attention.trigger.seconds,7200);
  assert.equal(attention.trigger.repeats,true);assert.equal(attention.trigger.channelId,'pup');
  await act(async()=>{for(const fn of listeners)fn('active');await flush();});assert.equal(scheduled.has('puppy-attention'),false);
  await act(async()=>{renderer.update(React.createElement(Harness,{game,ready:false}));await flush();});assert.equal(scheduled.size,0);
 }finally{Date.now=originalNow;if(renderer)await act(()=>renderer.unmount());}
});
