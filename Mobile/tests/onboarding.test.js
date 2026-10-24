import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame,sanitizeGame} from '../src/engine/game.js';
import {ONBOARDING_STEPS,validBirthday,normalizeLocalGuestName} from '../src/engine/onboarding.js';
import {transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
test('onboarding stage order reproduces Kotlin V6 welcome -> ready',()=>{
 assert.deepEqual(ONBOARDING_STEPS,['WELCOME','PLAYER_SETUP','PERSONALIZE','PRIVACY','NOTIFICATIONS','READY']);
});
test('fresh user must complete the guest onboarding before play',()=>{
 const g=freshGame();assert.equal(g.onboardingComplete,false);assert.equal(g.onboardingStep,0);
 const s={game:g,ready:true,notice:''};
 const next=transition(s,{type:'onboardNext'});
 assert.equal(next.game.onboardingStep,1);
 assert.equal(next.game.onboardingComplete,false);
});
test('guest nickname is sanitized and not connected to an official Player ID',()=>{
 assert.equal(normalizeLocalGuestName('  The\n Puppeteer  '),'The Puppeteer');
 assert.equal(normalizeLocalGuestName(' '),null);
 const g=freshGame();
 const next=transition({game:g,ready:true,notice:''},{type:'onboardPlayer',name:'Puppy Fan'});
 assert.equal(next.game.guestName,'Puppy Fan');
 assert.equal(next.game.authUserId,undefined);
});
test('birthday remains optional and only valid month/day is accepted',()=>{
 assert.equal(validBirthday(2,29),true);
 assert.equal(validBirthday(2,30),false);
 assert.equal(validBirthday(0,0),true);
 assert.equal(validBirthday(9,31),false);
 const s=transition({game:freshGame(),ready:true,notice:''},{type:'onboardBirthday',month:2,day:29});
 assert.equal(s.game.birthdayMonth,2);assert.equal(s.game.birthdayDay,29);
});
test('no replay can finish onboarding before explicit final ready step',()=>{
 let s={game:freshGame(),ready:true,notice:''};
 const premature=transition(s,{type:'onboardFinish'});
 assert.equal(premature.game.onboardingComplete,false);
 for(let i=0;i<5;i++)s=transition(s,{type:'onboardNext'});
 s=transition(s,{type:'onboardFinish'});
 assert.equal(s.game.onboardingComplete,true);
 assert.equal(parseGuestSave(serializeGuestSave(s.game)).onboardingComplete,true);
});

test('local guest names normalize whitespace when restoring saved state',()=>{
 const g=sanitizeGame({...freshGame(),guestName:'  Puppy\n  Tester  '});
 assert.equal(g.guestName,'Puppy Tester');
});
