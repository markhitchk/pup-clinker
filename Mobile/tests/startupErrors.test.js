import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {publicStartupIssue,classifyStartupFailure,requiresPupAccountSignIn,STARTUP_ERRORS} from '../src/production/startupErrors.js';

test('public startup codes have stable human-readable messages and recovery',()=>{
 for(const [code,definition] of Object.entries(STARTUP_ERRORS)){
  assert.match(code,/^(?:PUPPY|PUPEYE)-[A-Z]+-\d{3}$/);
  assert.ok(definition.title&&definition.message&&definition.recovery);
  assert.deepEqual(publicStartupIssue(code),{code,...definition});
 }
});
test('unrecognized or malicious error codes can never enter the player-facing UI',()=>{
 const raw='BETA_db_password=secret://token\nstack=file:///data/user/0';
 const issue=publicStartupIssue(raw);
 assert.equal(issue.code,'PUPPY-START-999');
 assert.doesNotMatch(JSON.stringify(issue),/password=|secret:|stack=|file:\/\//);
});
test('startup classifies network, auth, device, integrity, cloud, native and render errors',()=>{
 const classify=args=>classifyStartupFailure(args).code;
 assert.equal(classify({source:'setup'}),'PUPPY-START-101');
 assert.equal(classify({account:{status:'NATIVE_MODULE_MISSING'}}),'PUPPY-START-102');
 assert.equal(classify({cloudState:'CACHED_OFFLINE'}),'PUPPY-NET-701');
 assert.equal(classify({error:{code:'BETA_PUP_ACCOUNT_LOGIN_REQUIRED'},cloudState:'CACHED_OFFLINE'}),'PUPPY-AUTH-801');
 assert.equal(requiresPupAccountSignIn({code:'BETA_PUP_ACCOUNT_LOGIN_REQUIRED'}),true);
 assert.equal(requiresPupAccountSignIn({code:'BETA_PUP_ACCOUNT_DISCORD_REQUIRED'}),true);
 assert.equal(requiresPupAccountSignIn({code:'BETA_BETA_LINK_REQUIRED'}),true);
 assert.equal(requiresPupAccountSignIn({code:'BETA_TRANSPORT_FAILURE'}),false);
 assert.equal(classify({error:{code:'BETA_TRANSPORT_FAILURE'},cloudState:'CONNECTION_ERROR'}),'PUPPY-NET-702');
 assert.equal(classify({error:{code:'BETA_SESSION_EXPIRED'}}),'PUPPY-AUTH-801');
 assert.equal(classify({error:{code:'BETA_OWNER_DEVICE_UNAVAILABLE'}}),'PUPEYE-ID-401');
 assert.equal(classify({error:{code:'BETA_PUP_ACCOUNT_BLOCKED'}}),'PUPEYE-SERVER-701');
 assert.equal(classify({error:{code:'INVALID_CLOUD_SAVE_JSON'}}),'PUPPY-SAVE-205');
 assert.equal(classify({error:{code:'DATABASE_LAYOUT_UNSUPPORTED'}}),'PUPPY-DB-301');
 assert.equal(classify({account:{status:'DATABASE_LAYOUT_UNSUPPORTED'}}),'PUPPY-DB-301');
 assert.equal(classify({source:'render'}),'PUPPY-CRASH-902');
 assert.equal(classify({source:'native'}),'PUPPY-CRASH-901');
});
test('existing main game is protected by an error boundary and loads crash markers first',()=>{
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 const error=readFileSync(new URL('../src/production/PuppyErrorBoundary.jsx',import.meta.url),'utf8');
 const ui=readFileSync(new URL('../src/production/PuppyStartupScreen.jsx',import.meta.url),'utf8');
 assert.match(app,/<PuppyErrorBoundary>/);
 assert.match(app,/readStartupCrash/);
 assert.match(app,/clearStartupCrash/);
 assert.match(app,/startPhase===/);
 assert.match(error,/getDerivedStateFromError/);
 assert.match(ui,/Share Error Code/);
 assert.match(ui,/publicStartupIssue/);
 assert.doesNotMatch(ui,/error\.message|error\.stack|saveJson|revision|sessionToken/);
});
test('Android records only an allowlisted local crash code before React starts',()=>{
 const native=readFileSync(new URL('../plugins/puppy-production/PuppyStartupCrashStore.kt',import.meta.url),'utf8');
 const bridge=readFileSync(new URL('../plugins/puppy-production/PuppyBetaAccountPackage.kt',import.meta.url),'utf8');
 const plugin=readFileSync(new URL('../plugins/withPuppyProductionAuthority.cjs',import.meta.url),'utf8');
 assert.match(native,/setDefaultUncaughtExceptionHandler/);
 assert.match(native,/prior\.uncaughtException/);
 assert.match(native,/allowed\.contains\(code\)/);
 assert.doesNotMatch(native,/printStackTrace|toString\(\).*error|putString\("message"/);
 assert.match(bridge,/fun readStartupCrash/);
 assert.match(bridge,/fun clearStartupCrash/);
 assert.match(plugin,/PuppyStartupCrashStore\.install\(this\)/);
 assert.match(plugin,/PuppyStartupCrashStore\.kt/);
 const require=createRequire(import.meta.url);
 const {installNativeCrashRecorder}=require('../plugins/withPuppyProductionAuthority.cjs');
 const sample='class MainApplication {\n  override fun onCreate() {\n    super.onCreate()\n  }\n}';
 const once=installNativeCrashRecorder(sample);
 assert.match(once,/PuppyStartupCrashStore\.install\(this\)/);
 assert.equal(installNativeCrashRecorder(once),once,'native onCreate install is idempotent');
});
