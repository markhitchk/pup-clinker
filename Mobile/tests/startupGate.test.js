import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {startupGate} from '../src/production/startupGate.js';

const active={status:'VERIFIED',username:'max_puppy4',legalAccepted:true};
const base={account:active,cloudState:'LIVE_SYNCED',setupReady:true,setupProblem:'',gameReady:true};

test('Pup Account and save must verify before leaving startup',()=>{
 assert.equal(startupGate({...base,account:{status:'CHECKING'},cloudState:'NOT_LOADED'}),'loading');
 assert.equal(startupGate({...base,cloudState:'CACHED_VERIFYING',account:{status:'CACHED'}}),'loading');
 assert.equal(startupGate({...base,gameReady:false}),'loading');
 assert.equal(startupGate({...base,setupReady:false}),'loading');
 assert.equal(startupGate(base),'ready');
});
test('missing session asks for username/Discord sign-in before gameplay',()=>{
 assert.equal(startupGate({...base,account:{status:'NEEDS_ACCOUNT'},cloudState:'SIGN_IN_REQUIRED'}),'signin');
 assert.equal(startupGate({...base,account:{status:'NEEDS_DISCORD'},cloudState:'SIGN_IN_REQUIRED'}),'signin');
 assert.equal(startupGate({...base,account:active,cloudState:'SIGN_IN_REQUIRED'}),'signin');
});
test('legal agreements remain required for returning players',()=>{
 assert.equal(startupGate({...base,account:{...active,legalAccepted:false}}),'legal');
 assert.equal(startupGate({...base,account:{...active,legalAccepted:undefined}}),'legal');
});
test('offline startup offers retry instead of rendering a cached, unsigned game',()=>{
 assert.equal(startupGate({...base,cloudState:'CACHED_OFFLINE',account:{status:'CACHED'}}),'connection-error');
 assert.equal(startupGate({...base,cloudState:'CONNECTION_ERROR'}),'connection-error');
 assert.equal(startupGate({...base,account:{status:'DATABASE_LAYOUT_UNSUPPORTED'}}),'connection-error');
 assert.equal(startupGate({...base,account:{status:'NATIVE_MODULE_MISSING'},cloudState:'CONNECTION_ERROR'}),'connection-error');
 assert.equal(startupGate({...base,setupReady:false,setupProblem:'Setup failed'}),'setup-error');
});
test('the game root has no account banner but retains Settings management',()=>{
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 const settings=readFileSync(new URL('../src/components/SettingsScreen.jsx',import.meta.url),'utf8');
 assert.doesNotMatch(app,/accessibilityLabel="Open Pup Account"/);
 assert.doesNotMatch(app,/🔐 '\s*\+\s*\(account\.username/);
 assert.match(app,/const startPhase=betaMode\?startupGate/);
 assert.match(app,/<PuppyStartupScreen/);
 assert.match(app,/requiredAtStartup/);
 assert.match(settings,/Manage Pup Account/);
});
