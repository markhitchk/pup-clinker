import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {commitVerifiedPupCredentials} from '../src/production/androidAutofill.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');

test('password manager commit runs only after verified signed Pup Account auth',async()=>{
 const called=[];
 const native={commitPupAutofill:async()=>{called.push('commit');return true;}};
 assert.equal(await commitVerifiedPupCredentials(native,{status:'VERIFIED'}),true);
 assert.deepEqual(called,['commit']);
 for(const state of [{status:'NEEDS_ACCOUNT'},{status:'DEVICE_TRANSFER_REQUIRED'},null]){
  assert.equal(await commitVerifiedPupCredentials(native,state),false);
 }
 assert.deepEqual(called,['commit'],'never offer to save invalid or blocked logins');
});

test('missing or unavailable password manager never blocks login or registration',async()=>{
 assert.equal(await commitVerifiedPupCredentials(undefined,{status:'VERIFIED'}),false);
 assert.equal(await commitVerifiedPupCredentials({}, {status:'VERIFIED'}),false);
 assert.equal(await commitVerifiedPupCredentials({commitPupAutofill:async()=>{throw Error('unavailable');}},{status:'VERIFIED'}),false);
});

test('Android sign-in and registration expose explicit password manager hints',()=>{
 const src=read('../src/production/BetaAccountEntry.jsx');
 for(const [id,hint] of [
  ['pup-signin-username','username'],
  ['pup-signin-password','current-password'],
  ['pup-register-password','new-password'],
  ['pup-register-confirm-password','new-password']
 ]){
  const pattern=new RegExp('nativeID="'+id+'"[^>]*autoComplete="'+hint+'"');
  assert.match(src,pattern,id);
  const tag=src.slice(src.indexOf('nativeID="'+id+'"'),src.indexOf('nativeID="'+id+'"')+260);
  assert.match(tag,/importantForAutofill="yes"/,id);
 }
 assert.match(src,/nativeID="pup-register-username"/);
 assert.match(src,/autoComplete=\{linkNative\?'off':'username-new'\}/);
 assert.match(src,/importantForAutofill=\{linkNative\?'no':'yes'\}/);
 const verified=src.indexOf("await commitVerifiedPupCredentials(api(),result)");
 const cleared=src.indexOf("setPassword('');setConfirmPassword('');",verified);
 assert.ok(verified>0&&cleared>verified,'commit before password state cleared');
});

test('native OS autofill bridge never sends or stores password credentials',()=>{
 const kt=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const segment=kt.slice(kt.indexOf('@ReactMethod fun commitPupAutofill'),kt.indexOf('@ReactMethod fun signInPupAccount'));
 assert.match(segment,/getSystemService\(android.view.autofill.AutofillManager::class.java\)/);
 assert.match(segment,/manager\.commit\(\)/);
 assert.match(segment,/manager\?\.isEnabled/);
 assert.doesNotMatch(segment,/SharedPreferences|putString|password|write\(/i);
});
