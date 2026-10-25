import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {transition,initialState} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';

test('Kotlin puppy_name_for_<styleId> persists independently across switches and save/load',()=>{
 const now=Date.now();
 let state={...initialState(),game:freshGame(),ready:true};
 state=transition(state,{type:'rename',name:'Scout',now});
 state=transition(state,{type:'switch',id:'golden',now:now+1000});
 assert.equal(state.game.puppyName,'Sunny');
 state=transition(state,{type:'rename',name:'Goldie',now:now+2000});
 state=transition(state,{type:'switch',id:'classic',now:now+3000});
 assert.equal(state.game.puppyName,'Scout');
 assert.deepEqual(state.game.puppyNames,{classic:'Scout',golden:'Goldie'});
 const saved=parseGuestSave(serializeGuestSave(state.game));
 const restored={...initialState(),game:saved,ready:true};
 const switched=transition(restored,{type:'switch',id:'golden',now:now+4000});
 assert.equal(switched.game.puppyName,'Goldie');
});

test('legacy schema-1 guest save retains the renamed active puppy',()=>{
 const game=freshGame();
 game.puppyName='Legacy Name';
 delete game.puppyNames; // Guests saved before the per-puppy name map existed.
 const restored=parseGuestSave(JSON.stringify({kind:'guest-only',schema:1,game}));
 assert.equal(restored.puppyName,'Legacy Name');
 assert.equal(restored.puppyNames.classic,'Legacy Name');
});
