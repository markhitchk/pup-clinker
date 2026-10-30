import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {ANDROID_DATABASE_CONTRACT,supportsDatabaseLayout} from '../src/production/dbSchemaLayout.js';
import {PRODUCTION_FUNCTION_URL,classifyVerifiedAccount} from '../src/production/authority.js';

test('Android keeps secure Pup Account Edge endpoint during grouped DB rollout',()=>{
 assert.equal(ANDROID_DATABASE_CONTRACT.layoutVersion,3);
 assert.equal(ANDROID_DATABASE_CONTRACT.compatibilitySchema,'public');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.accounts,'puppy_accounts');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.pupeye,'puppy_pupeye');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.game,'puppy_game');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.exchange,'puppy_exchange');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.admin,'puppy_admin');
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchemas.web,'puppy_web');
 assert.equal(Object.hasOwn(ANDROID_DATABASE_CONTRACT,'canonicalSchema'),false);
 assert.equal(ANDROID_DATABASE_CONTRACT.groupedSchemas.length,6);
 assert.equal(PRODUCTION_FUNCTION_URL,'https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/pup-account');
 assert.equal(supportsDatabaseLayout(undefined),true);
 assert.equal(supportsDatabaseLayout(2),true);
 assert.equal(supportsDatabaseLayout(3),true);
 assert.equal(supportsDatabaseLayout('3'),false);
 assert.equal(supportsDatabaseLayout('4'),false);
 assert.equal(supportsDatabaseLayout(0),false);
 assert.equal(supportsDatabaseLayout(4),false);
 assert.equal(supportsDatabaseLayout(NaN),false);
});

test('Android blocks unsupported new database contracts but accepts old servers',()=>{
 assert.equal(classifyVerifiedAccount({databaseLayoutVersion:4}).status,'DATABASE_LAYOUT_UNSUPPORTED');
 assert.equal(classifyVerifiedAccount({verified:false}).status,'ACCOUNT_NOT_VERIFIED');
});

test('native signed account status and cloud bootstrap forward server layout version',()=>{
 const native=readFileSync(new URL('../plugins/puppy-production/PuppyBetaAccountPackage.kt',import.meta.url),'utf8');
 const game=readFileSync(new URL('../src/state/useGame.js',import.meta.url),'utf8');
 const entry=readFileSync(new URL('../src/production/BetaAccountEntry.jsx',import.meta.url),'utf8');
 assert.equal((native.match(/putInt\("databaseLayoutVersion"/g)||[]).length,2);
 assert.match(game,/supportsDatabaseLayout\(active\.databaseLayoutVersion\)/);
 assert.match(game,/supportsDatabaseLayout\(result\.account\.databaseLayoutVersion\)/);
 assert.match(entry,/supportsDatabaseLayout\(result\.databaseLayoutVersion\)/);
});
