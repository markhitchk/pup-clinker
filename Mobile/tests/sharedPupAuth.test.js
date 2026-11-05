import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JOIN_SOURCE_OPTIONS,registrationError,loginError,passwordRules} from '../src/shared/usePupAuthForm.js';

const sample={username:'SunnyPaw_1234',password:'StrongPup1!',confirm:'StrongPup1!',
 contactEmail:'',terms:true,privacy:true};
test('website-matched registration policy requires complex and confirmed passwords',()=>{
 assert.equal(registrationError(sample),'');
 assert.match(registrationError({...sample,password:'weakpassword',confirm:'weakpassword'}),/uppercase and lowercase/);
 assert.match(registrationError({...sample,confirm:'Wrong1!'}),/Passwords do not match/);
 assert.match(registrationError({...sample,username:'x'}),/3–20/);
 assert.match(registrationError({...sample,contactEmail:'not-an-email'}),/support email/);
 assert.match(registrationError({...sample,privacy:false}),/both/);
 assert.ok(passwordRules('StrongPup1!').every(rule=>rule.passed));
 assert.equal(loginError('PupName','password'),'');
 assert.match(loginError('',''),/username and password/);
});
test('native signup retains website discovery fields and device-signed transport',()=>{
 assert.equal(JOIN_SOURCE_OPTIONS.length,9);
 const jsx=readFileSync(new URL('../src/production/BetaAccountEntry.jsx',import.meta.url),'utf8');
 const kotlin=readFileSync(new URL('../plugins/puppy-production/PuppyBetaAccountPackage.kt',import.meta.url),'utf8');
 const backend=readFileSync(new URL('../../supabase/functions/pup-credentials/index.ts',import.meta.url),'utf8');
 assert.ok(jsx.includes('registrationError({username,password,confirm'));
 assert.ok(jsx.includes('JOIN_SOURCE_OPTIONS.map'));
 assert.ok(jsx.includes('autoComplete="new-password"'));
 assert.ok(jsx.includes("await api().createPupAccount("));
 assert.ok(kotlin.includes('joinSource:String,joinDetails:String,key:String,p:Promise'));
 assert.ok(kotlin.includes('envelope("native-sign-up",body)'));
 assert.ok(backend.includes('parseJoinSource(envelope.payload)'));
 assert.ok(backend.includes('pup_registration_sources'));
});
