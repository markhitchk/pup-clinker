import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import configPlugin from '../app.config.js';
const base=JSON.parse(readFileSync(new URL('../app.json',import.meta.url),'utf8')).expo;
test('default build is always a separate preview, never an accidental Play update',()=>{
 const original=process.env.PUPPY_NATIVE_PREVIEW;
 delete process.env.PUPPY_NATIVE_PREVIEW;
 try{assert.equal(configPlugin({config:base}).android.package,'com.harleytg.puppyclicker.preview');}
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

test('production Android identity requires explicit post-parity release approval',()=>{
 const prev=process.env.PUPPY_NATIVE_PREVIEW,prod=process.env.PUPPY_NATIVE_PRODUCTION,approved=process.env.PUPPY_NATIVE_RELEASE_APPROVED;
 delete process.env.PUPPY_NATIVE_PREVIEW;
 process.env.PUPPY_NATIVE_PRODUCTION='1';
 delete process.env.PUPPY_NATIVE_RELEASE_APPROVED;
 try{
  assert.throws(()=>configPlugin({config:base}),/production migration not approved/i);
  process.env.PUPPY_NATIVE_RELEASE_APPROVED='1';
  assert.equal(configPlugin({config:base}).android.package,'com.harleytg.puppyclicker');
  assert.equal(configPlugin({config:base}).android.versionCode,35);
 }finally{
  for(const [key,value] of [['PUPPY_NATIVE_PREVIEW',prev],['PUPPY_NATIVE_PRODUCTION',prod],['PUPPY_NATIVE_RELEASE_APPROVED',approved]])if(value===undefined)delete process.env[key];else process.env[key]=value;
 }
});
