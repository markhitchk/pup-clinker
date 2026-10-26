import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync,existsSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import commonjs from '@babel/plugin-transform-modules-commonjs';
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const file=new URL('../src/production/useAccountOnboarding.js',import.meta.url);
const require=createRequire(file);
let native,flow;
const bridge={NativeModules:{}};
function load(){
 // A missing implementation is an explicit behavior failure in the red run.
 assert.ok(existsSync(file),'Authenticated onboarding hook is missing');
 const module={exports:{}};
 const compiled=transformSync(readFileSync(file,'utf8'),{babelrc:false,configFile:false,plugins:[commonjs]}).code;
 new Function('require','module','exports',compiled)(id=>id==='react-native'?bridge:require(id),module,module.exports);
 return module.exports.useAccountOnboarding;
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
async function mount(snapshot={},activation=true,account={status:'VERIFIED',accountId:'account-1',username:'PuppyFan'}){
 const writes=[];let finished=0;
 native={readSetup:async()=>snapshot,saveSetup:async json=>{writes.push(JSON.parse(json));},finishSetup:async()=>{finished++;return account;}};
 bridge.NativeModules.PuppyBetaAccount=native;
 const useFlow=load();
 function Harness(){flow=useFlow({enabled:true,account,activateAccount:async()=>activation});return null;}
 let renderer;await act(async()=>{renderer=create(React.createElement(Harness));await flush();});
 return {renderer,writes,finished:()=>finished,close:()=>act(()=>renderer.unmount())};
}
test('restart resumes native onboarding step and preferences without creating a guest identity',async()=>{
 const ui=await mount({setupStep:2,birthdayMonth:2,birthdayDay:29,themeMode:'light',uiScale:'large'});
 try{assert.equal(flow.ready,true);assert.equal(flow.setup.setupStep,2);assert.equal(flow.setup.themeMode,'light');assert.equal(flow.setup.birthdayDay,29);assert.equal(flow.setup.anonymousDiagnosticsEnabled,false);assert.equal(flow.setup.guestName,undefined);}
 finally{await ui.close();}
});
test('account setup cannot continue when cloud activation fails',async()=>{
 const ui=await mount({setupStep:1},false);
 try{await act(()=>flow.next());assert.equal(flow.setup.setupStep,1);assert.match(flow.problem,/cloud/i);assert.equal(ui.finished(),0);}
 finally{await ui.close();}
});
test('a device-transfer block cannot pass account setup',async()=>{
 const ui=await mount({setupStep:1},true,{status:'DEVICE_TRANSFER_REQUIRED'});
 try{await act(()=>flow.next());assert.equal(flow.setup.setupStep,1);assert.equal(ui.finished(),0);}
 finally{await ui.close();}
});
test('invalid birthday blocks personalization; skipping saves month and day only',async()=>{
 const ui=await mount({setupStep:2,birthdayMonth:2,birthdayDay:30});
 try{await act(()=>flow.next());assert.equal(flow.setup.setupStep,2);await act(()=>flow.update({birthdayMonth:0,birthdayDay:0}));await act(()=>flow.next());assert.equal(flow.setup.setupStep,3);assert.equal(ui.writes.at(-1).birthdayMonth,0);assert.equal(ui.writes.at(-1).birthdayYear,undefined);}
 finally{await ui.close();}
});
test('privacy review and notification refusal still permit account-verified completion',async()=>{
 const ui=await mount({setupStep:3});
 try{
  await act(()=>flow.next());assert.equal(flow.setup.privacyConsentVersion,5);
  await act(()=>flow.update({dailyRewardNotifications:false,gameEventNotifications:false,updateNotifications:false}));
  await act(()=>flow.next());assert.equal(flow.setup.setupStep,5);
  await act(()=>flow.finish());assert.equal(ui.finished(),1);assert.equal(flow.setup.setupComplete,true);
  assert.equal(flow.setup.crashReportsEnabled,false);assert.equal(flow.setup.supportIdentityEnabled,false);
 }finally{await ui.close();}
});
test('premature completion cannot call native finish',async()=>{
 const ui=await mount({setupStep:1});
 try{await act(()=>flow.finish());assert.equal(ui.finished(),0);assert.equal(flow.setup.setupComplete,false);}
 finally{await ui.close();}
});
test('failed final cloud verification leaves setup incomplete',async()=>{
 const ui=await mount({setupStep:5,privacyConsentVersion:5},false);
 try{await act(()=>flow.finish());assert.equal(ui.finished(),0);assert.equal(flow.setup.setupComplete,false);}
 finally{await ui.close();}
});
test('a failed preference write cannot advance to the next setup step',async()=>{
 const ui=await mount({setupStep:3});
 native.saveSetup=async()=>{throw new Error('disk write failed');};
 try{await act(()=>flow.next());assert.equal(flow.setup.setupStep,3);assert.match(flow.problem,/save/i);}
 finally{await ui.close();}
});
test('two queued failed edits restore the last persisted choices',async()=>{
 const ui=await mount({setupStep:2,themeMode:'system',uiScale:'compact'});
 const rejects=[];native.saveSetup=()=>new Promise((_resolve,reject)=>rejects.push(reject));
 let first,second;
 try{
  await act(async()=>{first=flow.update({themeMode:'dark'});second=flow.update({uiScale:'large'});await flush();});
  await act(async()=>{rejects.shift()(new Error('write failed'));await flush();});
  await act(async()=>{rejects.shift()(new Error('write failed'));await Promise.all([first,second]);});
  assert.equal(flow.setup.themeMode,'system');assert.equal(flow.setup.uiScale,'compact');
 }finally{await ui.close();}
});
