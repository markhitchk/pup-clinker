import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,sanitizeGame} from '../src/engine/game.js';
import {transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

test('Kotlin gameplay preferences default to haptics, animations and compact numbers',()=>{
 const g=freshGame();
 assert.deepEqual([g.hapticsEnabled,g.animationsEnabled,g.compactNumbers],[true,true,true]);
 const edited=sanitizeGame({...g,hapticsEnabled:false,animationsEnabled:false,compactNumbers:false});
 assert.deepEqual([edited.hapticsEnabled,edited.animationsEnabled,edited.compactNumbers],[false,false,false]);
 assert.equal(sanitizeGame({...g,hapticsEnabled:'not boolean'}).hapticsEnabled,true);
});
test('only allowlisted guest gameplay settings can be changed',()=>{
 let state={...transition({game:freshGame(),ready:true,notice:''},{type:'setPreference',name:'hapticsEnabled',value:false})};
 assert.equal(state.game.hapticsEnabled,false);
 state=transition(state,{type:'setPreference',name:'animationsEnabled',value:false});
 assert.equal(state.game.animationsEnabled,false);
 state=transition(state,{type:'setPreference',name:'compactNumbers',value:false});
 assert.equal(state.game.compactNumbers,false);
 const next=transition(state,{type:'setPreference',name:'serverVerified',value:true});
 assert.equal(next,state);
 assert.equal(next.game.serverVerified,undefined);
});
test('settings survive a guest save without becoming a trusted account',()=>{
 const g=freshGame();g.hapticsEnabled=false;g.compactNumbers=false;
 const restored=parseGuestSave(serializeGuestSave(g));
 assert.equal(restored.hapticsEnabled,false);
 assert.equal(restored.compactNumbers,false);
 assert.equal(JSON.parse(serializeGuestSave(restored)).kind,'guest-only');
});

test('Developer V2 flag is allowlisted, persists locally, and resets safely',()=>{
 const g=freshGame();
 assert.equal(g.playLayoutV2Enabled,false);
 const enabled=transition({game:g,ready:true,notice:''},{type:'setPreference',name:'playLayoutV2Enabled',value:true});
 assert.equal(enabled.game.playLayoutV2Enabled,true);
 const saved=parseGuestSave(serializeGuestSave(enabled.game));
 assert.equal(saved.playLayoutV2Enabled,true);
 assert.equal(sanitizeGame({...saved,playLayoutV2Enabled:'true'}).playLayoutV2Enabled,false);
 assert.equal(transition(enabled,{type:'resetSettings'}).game.playLayoutV2Enabled,false);
});
