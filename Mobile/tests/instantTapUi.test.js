import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {freshGame} from '../src/engine/game.js';
import {projectPendingTapDisplay} from '../src/state/optimisticTaps.js';

const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');

test('unconfirmed taps appear immediately without changing the authoritative save',()=>{
 const server=freshGame();
 const before=JSON.stringify(server);
 const pending=[{id:1,at:1000},{id:2,at:1200}];
 const display=projectPendingTapDisplay(server,pending);
 assert.equal(display.treats,4);
 assert.equal(display.totalTaps,2);
 assert.equal(display.lifetimeTreats,4);
 assert.equal(server.treats,0);
 assert.equal(JSON.stringify(server),before);
 assert.strictEqual(projectPendingTapDisplay(server,[]),server);
 // Confirmation reconciles by removing only the acknowledged tap and
 // projecting the remainder on top of the server's saved snapshot.
 const committed={...server,treats:2,lifetimeTreats:2,totalTaps:1,legitimateTaps:1};
 const reconciled=projectPendingTapDisplay(committed,[pending[1]]);
 assert.equal(reconciled.treats,4);
 assert.equal(reconciled.totalTaps,2);
});

test('cooldowns and tired puppies never receive a speculative treat reward',()=>{
 const server=freshGame();
 assert.equal(projectPendingTapDisplay({...server,energy:10},[{id:1,at:1000}]).treats,0);
 assert.equal(projectPendingTapDisplay({...server,cooldownUntilMs:5000},[{id:1,at:1000}]).treats,0);
});

test('signed request and error handling remain server-authoritative and private',()=>{
 const hook=read('../src/state/useGame.js');
 const root=read('../App.js');
 const settings=read('../src/components/SettingsScreen.jsx');
 assert.match(hook,/native\.gameplayAction\(JSON\.stringify\(intent\),PUBLISHABLE_KEY\)/);
 assert.match(hook,/result\.saved!==true/);
 assert.match(hook,/setPendingTapIntents\(current=>current\.filter\(item=>item\.id!==tapIntentId\)\)/);
 assert.match(root,/\.\.\.gameForDisplay/);
 assert.doesNotMatch(root,/Cloud saved · revision|syncStatus\.errorCode/);
 assert.doesNotMatch(settings,/syncStatus\.revision|syncStatus\.errorCode|syncStatus\.errorDetail/);
 assert.doesNotMatch(hook,/ToastAndroid\.show\('Cloud save failed: '\+code/);
});
