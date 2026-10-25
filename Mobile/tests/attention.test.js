import test from 'node:test';
import assert from 'node:assert/strict';
import {ATTENTION_INTERVAL_MS,attentionMessage} from '../src/engine/attention.js';
import {reminderPlan} from '../src/engine/inbox.js';
import {freshGame} from '../src/engine/game.js';

test('Kotlin AFK parity: first reminder in two hours and repeats every two hours while away',()=>{
 const now=new Date(2026,9,24,8,0).getTime();
 const game={...freshGame(),dailyRewardNotifications:false,gameEventNotifications:false,puppyAttentionNotifications:true};
 const plan=reminderPlan(game,now);
 assert.equal(plan.length,1);
 assert.equal(plan[0].id,'puppy-attention');
 assert.equal(plan[0].at,now+ATTENTION_INTERVAL_MS);
 assert.equal(plan[0].repeatSeconds,7200);
 assert.equal(plan[0].category,'pup');
 assert.equal(reminderPlan({...game,puppyAttentionNotifications:false},now).length,0);
});

test('Kotlin attention text reflects the active puppy and care urgency',()=>{
 const now=ATTENTION_INTERVAL_MS*17;
 const healthy=attentionMessage({puppyName:'Mochi',happiness:95,fullness:90,cleanliness:100},now);
 const needsCare=attentionMessage({puppyName:'Mochi',happiness:65,fullness:52,cleanliness:75},now);
 const urgent=attentionMessage({puppyName:'Mochi',happiness:10,fullness:100,cleanliness:100},now);
 assert.match(healthy.body,/Mochi.*AFK/);
 assert.match(needsCare.body,/Mochi.*care/);
 assert.match(urgent.body,/Mochi.*urgent/);
 assert.equal(healthy.title,urgent.title);
});

test('invalid saved care values never create an unsafe or unbounded reminder',()=>{
 const message=attentionMessage({puppyName:'',happiness:NaN,fullness:undefined,cleanliness:Infinity},ATTENTION_INTERVAL_MS);
 assert.match(message.body,/Buddy/);
});
