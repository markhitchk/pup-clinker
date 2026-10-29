import test from 'node:test';
import assert from 'node:assert/strict';
import {ANDROID_DATABASE_CONTRACT,supportsDatabaseLayout} from '../src/production/dbSchemaLayout.js';
import {PRODUCTION_FUNCTION_URL,classifyVerifiedAccount} from '../src/production/authority.js';

test('Android keeps secure Pup Account Edge endpoint during grouped DB rollout',()=>{
 assert.equal(ANDROID_DATABASE_CONTRACT.layoutVersion,2);
 assert.equal(ANDROID_DATABASE_CONTRACT.canonicalSchema,'public');
 assert.equal(ANDROID_DATABASE_CONTRACT.groupedSchemas.length,6);
 assert.equal(PRODUCTION_FUNCTION_URL,'https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/pup-account');
 assert.equal(supportsDatabaseLayout(undefined),true);
 assert.equal(supportsDatabaseLayout(2),true);
 assert.equal(supportsDatabaseLayout(3),false);
 assert.equal(supportsDatabaseLayout('3'),false);
});

test('Android blocks unsupported new database contracts but accepts old servers',()=>{
 assert.equal(classifyVerifiedAccount({databaseLayoutVersion:3}).status,'DATABASE_LAYOUT_UNSUPPORTED');
 assert.equal(classifyVerifiedAccount({verified:false}).status,'ACCOUNT_NOT_VERIFIED');
});
