import test from 'node:test';
import assert from 'node:assert/strict';
import {freshGame} from '../src/engine/game.js';
import {initialState,transition} from '../src/state/reducer.js';
import {parseGuestSave,serializeGuestSave} from '../src/state/guestSave.js';
const now=new Date(2026,9,24,12).getTime();
const start=()=>({...initialState(),game:freshGame(),ready:true});
test('checking an available daily gift creates one persistent unread inbox item',()=>{
 let s=transition(start(),{type:'checkNotifications',now});
 assert.equal(s.game.notificationInbox?.length,1);
 s=transition(s,{type:'checkNotifications',now:now+1});
 assert.equal(s.game.notificationInbox.length,1);
 const g=parseGuestSave(serializeGuestSave(s.game));
 assert.equal(g.notificationInbox.length,1);assert.equal(g.notificationInbox[0].read,false);
});
test('mark read and clear read preserve unread entries and all balances',()=>{
 let s=transition(start(),{type:'testNotification',now});
 assert.equal(s.game.notificationInbox?.length,1);
 s=transition(s,{type:'markNotificationsRead'});
 s=transition(s,{type:'clearReadNotifications'});
 assert.deepEqual(s.game.notificationInbox,[]);assert.equal(s.game.treats,0);
});
test('daily notification claim cannot credit a reward twice',()=>{
 let s=transition(start(),{type:'checkNotifications',now});
 const id=s.game.notificationInbox?.[0]?.id;assert.ok(id);
 s=transition(s,{type:'openNotification',id,now});
 assert.equal(s.game.treats,0,'opening a notification must not itself mint rewards');
 s=transition(s,{type:'daily',now});
 const treats=s.game.treats;assert.ok(treats>0);
 s=transition(s,{type:'daily',now});assert.equal(s.game.treats,treats);
 assert.equal(s.game.notificationInbox[0].action,null);
});
test('disabled categories prevent new reminders and checking never invents AFK balances',()=>{
 const s=start();s.game.dailyRewardNotifications=false;s.game.gameEventNotifications=false;
 const next=transition(s,{type:'checkNotifications',now});
 assert.deepEqual(next.game.notificationInbox,[]);assert.equal(next.game.treats,0);
});

test('clearing a read daily reminder does not resurrect it on the next check',()=>{
 let s=transition(start(),{type:'checkNotifications',now});
 s=transition(s,{type:'markNotificationsRead'});s=transition(s,{type:'clearReadNotifications'});
 s=transition(s,{type:'checkNotifications',now:now+100});assert.equal(s.game.notificationInbox.length,0);
});

test('saved notification actions reject invalid items and cap retained history',()=>{
 const game={...freshGame(),notificationInbox:[{id:'bad',title:'Bad',body:'Bad',category:'reward',createdAt:-1},...Array.from({length:150},(_,i)=>({id:'n'+i,title:'Hello',body:'Body',category:'system',createdAt:100+i,action:'grantAdmin'}))]};
 const restored=parseGuestSave(serializeGuestSave(game));assert.equal(restored.notificationInbox.length,100);assert.ok(restored.notificationInbox.every(n=>n.action===null));
});
