import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {act,create} from 'react-test-renderer';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import commonjs from '@babel/plugin-transform-modules-commonjs';

globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const file=new URL('../src/production/websiteLanguage.js',import.meta.url);
const require=createRequire(file),mod={exports:{}};
let value='es';
const saves=[];
const storage={
 getItem:async key=>{assert.equal(key,'puppy-clicker:language:v1');return value;},
 setItem:async(key,next)=>{assert.equal(key,'puppy-clicker:language:v1');value=next;saves.push(next);}
};
const code=transformSync(readFileSync(file,'utf8'),{
 babelrc:false,configFile:false,plugins:[commonjs]
}).code;
new Function('require','module','exports',code)(id=>
 id==='@react-native-async-storage/async-storage'?{__esModule:true,default:storage}:require(id),mod,mod.exports);

test('website language preference hydrates Spanish and updates Rewards without changing saved game',async()=>{
 const samples=[];
 let current;
 function Consumer(){
  current=mod.exports.useWebsiteLanguage();
  samples.push(current.language);
  return React.createElement('Text',null,current.t('rewards'));
 }
 let renderer;
 try{
  await act(async()=>{
   renderer=create(React.createElement(Consumer));
   await Promise.resolve();
   await Promise.resolve();
  });
  assert.equal(current.language,'es');
  assert.equal(current.t('rewards'),'Recompensas');
  await act(async()=>{current.setLanguage('en');await Promise.resolve();});
  assert.equal(current.language,'en');
  assert.equal(current.t('rewards'),'Rewards');
  assert.deepEqual(saves,['en']);
  assert.equal(value,'en');
  assert.ok(samples.includes('es'));
 }finally{if(renderer)await act(()=>renderer.unmount());}
});
