import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalizeAvatarUrl} from '../src/production/avatarUrl.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');

test('direct public hosted image URLs can be used as profile pictures',()=>{
 for(const url of ['https://i.imgur.com/example.jpg','https://i.imgur.com/example.PNG',
  'https://i.ibb.co/example/photo.png','https://i.postimg.cc/example/photo.webp',
  'https://cdn.discordapp.com/avatars/user/photo.webp?size=256']){
  assert.equal(normalizeAvatarUrl(url),url);
 }
});

test('public hosting post links, private hosts and non-HTTPS are rejected',()=>{
 for(const url of ['https://imgur.com/a/example','https://imgur.com/example',
   'https://imgbb.com/','https://ibb.co/example','https://postimg.cc/abc',
   'https://i.imgur.com/video.mp4','https://i.imgur.com/example',
   'http://i.imgur.com/photo.png','https://localhost/photo.png',
   'https://192.168.1.5/picture.jpg','data:image/png;base64,test','javascript:alert(1)',
   null,'','https://example.com:8443/photo.png']){
  assert.equal(normalizeAvatarUrl(url),null,String(url));
 }
});

test('React Native account details expose hosted image picker links and signed save/remove',()=>{
 const screen=read('../src/components/AccountDetailsScreen.jsx');
 const hook=read('../src/state/useGame.js');
 const app=read('../App.js');
 assert.match(screen,/Upload on Imgur/);
 assert.match(screen,/Upload on ImgBB/);
 assert.match(screen,/Upload on Postimages/);
 assert.match(screen,/onSaveAvatarUrl\(value\)/);
 assert.match(screen,/save\(null\)/);
 assert.match(screen,/Profile picture preview/);
 assert.match(hook,/updateAccountAvatar/);
 assert.match(hook,/PuppyBetaAccount\?\.updateAvatarUrl/);
 assert.match(app,/onSaveAvatarUrl=\{updateAccountAvatar\}/);
});

test('PupEye server endpoints do not fetch avatar URLs or grant economy permissions',()=>{
 const validator=read('../../supabase/functions/_shared/profile-avatar.ts');
 const owner=read('../../supabase/functions/pup-account/index.ts');
 const companion=read('../../supabase/functions/pup-beta-link/index.ts');
 const bridge=read('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 assert.match(validator,/normalizePublicAvatarUrl/);
 assert.match(validator,/PUP_AVATAR_URL_INVALID/);
 assert.doesNotMatch(validator,/fetch\(/);
 assert.match(owner,/account-avatar-update/);
 assert.match(owner,/active_installation_uuid/);
 assert.match(companion,/beta-avatar-update/);
 assert.match(companion,/verifyOwner\(admin, account, envelope.action\)/);
 assert.match(companion,/profile_avatar_url: avatarUrl, profile_avatar_path: null/);
 assert.match(bridge,/@ReactMethod fun updateAvatarUrl/);
 assert.match(bridge,/signedPost\("pup-beta-link","beta-avatar-update"/);
 assert.match(bridge,/signedPost\("pup-account","account-avatar-update"/);
 assert.match(bridge,/accountPreviewFile.delete\(\)/);
});
