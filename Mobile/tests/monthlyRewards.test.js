import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {transition} from '../src/state/reducer.js';
import {
 fallbackGoals,fallbackSchedule,parseMonthlySchedule,goalsForDay,
 progressForGoal,rollDailyDay,claimDailyGoal,localEpochDay,monthKey
} from '../src/engine/monthlyRewards.js';

const date=new Date(2026,9,23,12,0,0); // local October 23 (Kotlin's LocalDate)
const next=new Date(2026,9,24,12,0,0);
const validSchedule=JSON.stringify({schemaVersion:1,month:'2026-10',days:{
  23:[
   {id:'tap_time',type:'taps',emoji:'🐾',title:'Tap Time',description:'Tap your puppy',target:3,rewardTreats:150},
   {id:'good_care',type:'care',target:2,rewardTreats:200},
   {id:'bond',type:'bond',target:75,rewardTreats:75},
   {id:'tap_time',type:'shop',target:1,rewardTreats:999},
   {id:'too_expensive',type:'taps',target:1,rewardTreats:100001},
   {id:'unsafe_metric',type:'server_role',target:1,rewardTreats:99}
  ]
}});

test('fallback goals use identical Kotlin day-dependent target and reward formulas',()=>{
 assert.deepEqual(fallbackGoals(1).map(g=>[g.id,g.target,g.rewardTreats]),[
  ['tap_time',50,200],['good_care',3,270],['shop_visit',1,400],['wellness',85,420]
 ]);
 assert.deepEqual(fallbackGoals(2).slice(0,2).map(g=>g.target),[75,4]);
 assert.equal(fallbackSchedule(date).source,'fallback');
 assert.equal(Object.keys(fallbackSchedule(date).days).length,31);
 assert.equal(monthKey(date),'2026-10');
 assert.equal(localEpochDay(next),localEpochDay(date)+1);
});
test('schedule parser permits only Kotlin-approved data-only goal metrics and payout caps',()=>{
 const s=parseMonthlySchedule(validSchedule,date);
 assert.equal(s.days[23].length,3);
 assert.equal(s.days[23][0].title,'Tap Time');
 assert.equal(s.days[23][1].emoji,'🎯');
 assert.equal(goalsForDay(s,date).length,3);
 assert.equal(goalsForDay(s,next)[0].target,100);
 assert.throws(()=>parseMonthlySchedule(validSchedule,new Date(2026,8,23)),/mismatched/);
 assert.throws(()=>parseMonthlySchedule(JSON.stringify({schemaVersion:2,month:'2026-10',days:{}}),date));
 assert.throws(()=>parseMonthlySchedule('x'.repeat(512*1024+1),date));
});
test('rollover resets daily counters on LOCAL date change while preserving game totals',()=>{
 const g=freshGame();g.totalTaps=40;g.careActions=7;g.totalShopPurchases=3;
 assert.equal(rollDailyDay(g,date),true);
 assert.equal(g.dailyTapStart,40);
 assert.equal(rollDailyDay(g,date),false);
 g.totalTaps+=4;g.careActions+=2;g.totalShopPurchases++;
 const s=parseMonthlySchedule(validSchedule,date);
 assert.equal(progressForGoal(g,s.days[23][0],date),4);
 assert.equal(progressForGoal(g,s.days[23][1],date),2);
 assert.equal(rollDailyDay(g,next),true);
 assert.equal(g.totalTaps,44);
 assert.equal(g.dailyTapStart,44);
 assert.equal(g.claimedDailyTasks.length,0);
});
test('valid completed goal awards treats, Bone, Pup Coin exactly once',()=>{
 const g=freshGame();
 rollDailyDay(g,date);g.totalTaps=3;
 const s=parseMonthlySchedule(validSchedule,date);
 const before=[g.treats,g.bones,g.pupCoins];
 assert.equal(claimDailyGoal(g,'tap_time',s,date),true);
 assert.deepEqual([g.treats,g.bones,g.pupCoins],[before[0]+150,before[1]+1,before[2]+1]);
 assert.equal(claimDailyGoal(g,'tap_time',s,date),false);
 assert.equal(claimDailyGoal(g,'not_a_real_goal',s,date),false);
 assert.equal(claimDailyGoal(g,'good_care',s,date),false);
 assert.equal(claimDailyGoal(g,'bond',s,date),false);
 assert.deepEqual(g.claimedDailyTasks,['tap_time']);
});
test('React reducer settles goals from verified state schedule, never from UI amounts',()=>{
 let state={game:freshGame(),ready:true,notice:'',monthlySchedule:parseMonthlySchedule(validSchedule,date),blackjack:null};
 state=transition(state,{type:'rollDailyDay',now:date.getTime()});
 for(let i=0;i<3;i++)state=transition(state,{type:'tap',now:date.getTime()+i*1000,randoms:[.99,.99]});
 const before=state.game.treats;
 state=transition(state,{type:'claimDailyGoal',goalId:'tap_time',rewardTreats:1000000,now:date.getTime()+5000});
 assert.equal(state.game.treats,before+150);
 assert.equal(state.game.claimedDailyTasks.length,1);
 state=transition(state,{type:'claimDailyGoal',goalId:'tap_time',rewardTreats:9999999,now:date.getTime()+8000});
 assert.equal(state.game.treats,before+150);
});
