import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');
const api=read('../../supabase/functions/pup-account/index.ts');
const discord=read('../../supabase/functions/pupeye-auth-discord/index.ts');
const schema=read('../../supabase/migrations/20261026030000_pup_account_local_credentials.sql');
const native=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
const ui=read('../src/production/BetaAccountEntry.jsx');

test('local login uses Supabase Auth verification plus signed PupEye session',()=>{
 assert.match(api,/authenticateSession\(req, admin, envelope\)/);
 assert.match(api,/admin\.auth\.getUser\(match\[1\]\)/);
 assert.match(api,/email_confirmed_at/);
 assert.match(api,/PUP_ACCOUNT_ON_OTHER_DEVICE/);
 assert.match(api,/PUP_ACCOUNT_IDENTITY_CONFLICT/);
 assert.match(api,/auth_user_id.*user\.id/);
 assert.match(native,/registerLocal\(username:String,email:String,password:String,key:String,p:Promise\)/);
 assert.match(native,/loginLocal\(email:String,password:String,key:String,p:Promise\)/);
 assert.match(native,/session\(key\)/);
 assert.match(native,/envelope\("auth-local",JSONObject\(\)\)/);
 assert.match(native,/Authorization","Bearer /);
 assert.doesNotMatch(native,/putString\("password"/);
 assert.doesNotMatch(native,/putString\("access_token"/);
 assert.doesNotMatch(api,/password_hash\s*:/);
});
test('existing account and username are protected from duplicate linking',()=>{
 assert.match(schema,/auth_user_id uuid references auth\.users/);
 assert.match(schema,/unique index if not exists pup_accounts_auth_user_unique/);
 assert.match(schema,/unique index if not exists pup_accounts_local_username_unique/);
 assert.match(schema,/lower\(local_username\)/);
 assert.match(api,/byUser\.player_uuid !== session\.player\.id/);
 assert.match(api,/byPlayer\.auth_user_id !== user\.id/);
 assert.match(api,/byPlayer\.discord_user_id !== session\.player\.discord_user_id/);
 assert.match(discord,/accountByPlayer\?\.local_username \?\? username/);
});
test('Pup Account registration and login do not require Discord or guests',()=>{
 assert.match(ui,/Sign in with Pup Account/);
 assert.match(ui,/Register Pup Account/);
 assert.match(ui,/Continue with Discord \(optional\)/);
 assert.match(ui,/secureTextEntry/);
 assert.doesNotMatch(ui,/GUEST_SAVE_KEY|saveGuest|unverified gameplay/);
});
