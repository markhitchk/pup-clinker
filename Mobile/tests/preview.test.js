import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import configPlugin from '../app.config.js';
const base=JSON.parse(readFileSync(new URL('../app.json',import.meta.url),'utf8')).expo;
const vars=['PUPPY_NATIVE_BETA','PUPPY_NATIVE_PREVIEW','PUPPY_NATIVE_PRODUCTION','PUPPY_NATIVE_RELEASE_APPROVED'];
function withEnv(overrides,fn){
 const old=Object.fromEntries(vars.map(k=>[k,process.env[k]]));
 try{
  for(const k of vars)delete process.env[k];
  for(const [key,val] of Object.entries(overrides))process.env[key]=val;
  fn();
 }finally{
  for(const [key,val] of Object.entries(old))
   if(val===undefined)delete process.env[key];else process.env[key]=val;
 }
}
test('default installable Android build uses requested beta identity',()=>{
 withEnv({},()=>{
  const app=configPlugin({config:base});
  assert.equal(app.name,'Puppy Clicker [Beta]');
  assert.equal(app.android.package,'com.harleytg.puppyclicker.beta');
  assert.equal(app.android.versionCode,1);
  assert.equal(app.extra.migrationStatus,'beta-account-migration-locked');
 });
});
test('explicit beta and legacy preview flags are always isolated from Play',()=>{
 withEnv({PUPPY_NATIVE_BETA:'1',PUPPY_NATIVE_PRODUCTION:'1'},()=>{
  assert.equal(configPlugin({config:base}).android.package,'com.harleytg.puppyclicker.beta');
 });
 withEnv({PUPPY_NATIVE_PREVIEW:'1'},()=>{
  assert.equal(configPlugin({config:base}).name,'Puppy Clicker [Beta]');
 });
});
test('production identity requires explicit post-parity release approval',()=>{
 withEnv({PUPPY_NATIVE_PRODUCTION:'1'},()=>{
  assert.throws(()=>configPlugin({config:base}),/production migration not approved/i);
  process.env.PUPPY_NATIVE_RELEASE_APPROVED='1';
  const app=configPlugin({config:base});
  assert.equal(app.android.package,'com.harleytg.puppyclicker');
  assert.equal(app.name,'Puppy Clicker');
  assert.equal(app.android.versionCode,35);
 });
});
