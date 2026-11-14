import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join,dirname} from 'node:path';
import {websitePasswordRules,DISCOVERY_SOURCES,validDiscoverySource} from '../src/production/pupAuthRules.js';

const here=join(dirname(fileURLToPath(import.meta.url)),'..');
const read=path=>readFileSync(join(here,path),'utf8');
test('Android registration follows current website five-part password policy',()=>{
 for(const value of ['short','no-upper1!','NoDigits!','NoSymbol9']){
  assert.equal(websitePasswordRules(value).every(rule=>rule.passed),false,value);
 }
 assert.equal(websitePasswordRules('Secure-password9!').every(rule=>rule.passed),true);
 assert.deepEqual(websitePasswordRules('').map(rule=>rule.label),
  ['8–128 characters','Uppercase letter','Lowercase letter','Number','Special character']);
});
test('optional join source uses the nine website categories and safe bounded details',()=>{
 assert.equal(DISCOVERY_SOURCES.length,9);
 assert.equal(validDiscoverySource('', ''),true);
 assert.equal(validDiscoverySource('youtube','CreatorName'),true);
 assert.equal(validDiscoverySource('discord','x'.repeat(121)),false);
 assert.equal(validDiscoverySource('invalid',''),false);
 assert.equal(validDiscoverySource('', 'CreatorName'),false);
 assert.equal(validDiscoverySource('friend','name\nhidden'),false);
});
test('Android auth retains a native PupEye signed boundary and safe password generation',()=>{
 const screen=read('src/production/BetaAccountEntry.jsx');
 const native=read('plugins/puppy-production/PuppyBetaAccountPackage.kt');
 const backend=read('../supabase/functions/pup-credentials/index.ts');
 assert.match(screen,/PUPPY CLICKER · SET UP YOUR ACCOUNT/);
 assert.match(screen,/Welcome back to your puppy world/);
 assert.match(screen,/Your puppy journey starts here/);
 assert.match(screen,/accessibilityLabel="Confirm password"/);
 assert.match(screen,/accessibilityLabel="Register with Discord"/);
 assert.match(screen,/accessibilityLabel="Recover Pup Account"/);
 assert.match(screen,/useWindowDimensions/);
 assert.match(screen,/pupAuthRules/);
 assert.match(native,/@ReactMethod fun suggestStrongPassword/);
 assert.match(native,/private val random=SecureRandom\(\)/);
 assert.match(native,/signedPost/);
 assert.match(native,/put\("joinSource",joinSource\)/);
 assert.match(backend,/createdAccount && source/);
 assert.match(backend,/pup_registration_sources/);
 assert.doesNotMatch(screen,/service_role|sb_secret_/);
 assert.doesNotMatch(screen,/WebView/);
});
