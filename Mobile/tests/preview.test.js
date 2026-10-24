import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import configPlugin from '../app.config.js';
const base=JSON.parse(readFileSync(new URL('../app.json',import.meta.url),'utf8')).expo;
test('production package ID remains intact if preview flag absent',()=>{
 const original=process.env.PUPPY_NATIVE_PREVIEW;
 delete process.env.PUPPY_NATIVE_PREVIEW;
 try{assert.equal(configPlugin({config:base}).android.package,'com.harleytg.puppyclicker');}
 finally{if(original!==undefined)process.env.PUPPY_NATIVE_PREVIEW=original;}
});
test('preview installs alongside Kotlin release and never claims release version code',()=>{
 const original=process.env.PUPPY_NATIVE_PREVIEW;
 process.env.PUPPY_NATIVE_PREVIEW='1';
 try{
  const app=configPlugin({config:base});
  assert.equal(app.android.package,'com.harleytg.puppyclicker.preview');
  assert.equal(app.android.versionCode,1);
  assert.equal(app.extra.migrationStatus,'guest-preview-side-by-side');
 }finally{if(original===undefined)delete process.env.PUPPY_NATIVE_PREVIEW;else process.env.PUPPY_NATIVE_PREVIEW=original;}
});
