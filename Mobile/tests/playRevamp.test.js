import test from 'node:test';
import assert from 'node:assert/strict';
import {MAIN_TABS,revampedPlayLayout,nextPlayReward} from '../src/engine/playRevamp.js';
import {freshGame} from '../src/engine/game.js';
import {fallbackSchedule,goalsForDay,localEpochDay} from '../src/engine/monthlyRewards.js';
test('shipped primary order preserves Roster/Rewards and keeps Settings/Prestige internal',()=>{
 assert.deepEqual(MAIN_TABS.map(t=>t[2]),['Play','Care','Roster','Shop','Rewards']);
});
test('Play portrait follows Kotlin tiny/compact/regular height breakpoints',()=>{
 assert.equal(revampedPlayLayout(589).portrait,122);assert.equal(revampedPlayLayout(590).portrait,150);assert.equal(revampedPlayLayout(699).portrait,150);assert.equal(revampedPlayLayout(700).portrait,185);
 assert.equal(revampedPlayLayout(589).tapHeight,44);assert.equal(revampedPlayLayout(700).tapHeight,50);
});
test('Next Reward follows daily progress, advances after claim, and handles all claimed',()=>{
 const now=new Date(2026,9,24,12).getTime(),g=freshGame(),schedule=fallbackSchedule(new Date(now)),goals=goalsForDay(schedule,new Date(now));
 g.dailyDay=localEpochDay(new Date(now));g.dailyTapStart=0;
 const before=nextPlayReward(g,schedule,now);assert.match(before.title,new RegExp(goals[0].title));assert.equal(before.allClaimed,false);
 g.totalTaps=goals[0].target;
 assert.equal(nextPlayReward(g,schedule,now).progress,before.target);
 g.claimedDailyTasks=[goals[0].id];assert.notEqual(nextPlayReward(g,schedule,now).title,before.title);
 g.claimedDailyTasks=goals.map(goal=>goal.id);assert.equal(nextPlayReward(g,schedule,now).allClaimed,true);
 g.dailyDay--;assert.equal(nextPlayReward(g,schedule,now).allClaimed,false);
});
