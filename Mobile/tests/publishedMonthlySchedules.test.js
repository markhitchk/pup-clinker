import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fallbackGoals, goalsForDay, parseMonthlySchedule} from '../src/engine/monthlyRewards.js';

for(const [month,year,monthIndex,count] of [['2026-10',2026,9,31],['2026-11',2026,10,30]]){
 test('published '+month+' goals cover every day and preserve server fallback rewards',()=>{
  const raw=readFileSync(new URL('../../assets/rewards/'+month+'.json',import.meta.url),'utf8');
  const date=new Date(year,monthIndex,29,12,0,0);
  const schedule=parseMonthlySchedule(raw,date,'github');
  assert.equal(schedule.month,month);
  assert.equal(Object.keys(schedule.days).length,count);
  for(let day=1;day<=count;day++){
   const forDate=new Date(year,monthIndex,day,12,0,0);
   const expected=fallbackGoals(day).map(g=>[g.id,g.metric,g.target,g.rewardTreats]);
   const actual=goalsForDay(schedule,forDate).map(g=>[g.id,g.metric,g.target,g.rewardTreats]);
   assert.deepEqual(actual,expected,'unexpected goals on '+month+'-'+day);
  }
 });
}
