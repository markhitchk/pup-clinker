import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=path=>fs.readFileSync(new URL('../'+path,import.meta.url),'utf8');
test('React Native mounts web Discord handoff at app root',()=>{
 const app=read('App.js');
 assert.match(app,/import WebDiscordHandoff/);
 assert.match(app,/<WebDiscordHandoff\/>/);
});
test('Android web handoff forwards codes only using the one allowed redirect',()=>{
 const handoff=read('src/production/WebDiscordHandoff.jsx');
 const account=read('src/production/BetaAccountEntry.jsx');
 assert.match(handoff,/discord-1547982688898777118/);
 assert.match(handoff,/relay-submit/);
 assert.doesNotMatch(handoff,/NativeModules\.PuppyBetaAccount|device_key|access_token/);
 assert.match(account,/if\(isWebDiscordCallback\(url\)\|\|/);
});
