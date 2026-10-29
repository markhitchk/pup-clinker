import test from 'node:test';
import assert from 'node:assert/strict';
import {DEVELOPER_SUPPORT_MESSAGE,visibleInboxItems,checkInbox} from '../src/engine/inbox.js';

const emptyInbox=()=>({
 notificationInbox:[],
 notificationSeenIds:[],
 dailyRewardNotifications:false,
 gameEventNotifications:false,
 lastDailyClaimDay:0
});

test('a lifetime Developer Message always appears in All and System',()=>{
 for(const tab of ['all','system']){
  const items=visibleInboxItems([],tab);
  assert.equal(items.length,1);
  assert.equal(items[0].id,DEVELOPER_SUPPORT_MESSAGE.id);
  assert.equal(items[0].title,'Developer Message');
  assert.equal(items[0].pinned,true);
  assert.equal(items[0].read,true);
  assert.equal(items[0].category,'system');
 }
 assert.equal(visibleInboxItems([],'reward').length,0);
 assert.equal(visibleInboxItems([],'pup').length,0);
 assert.equal(DEVELOPER_SUPPORT_MESSAGE.url,'https://ko-fi.com/harleytg_');
});

test('the pinned message survives clearing inbox and is not stored as unread gameplay data',()=>{
 const game=emptyInbox();
 const now=Date.UTC(2026,9,29,12,0);
 checkInbox(game,now);
 assert.equal(game.notificationInbox.length,0);
 const initial=visibleInboxItems(game.notificationInbox);
 assert.equal(initial.filter(n=>n.pinned).length,1);
 // Clearing the user's saved inbox cannot delete a source-defined permanent notice.
 game.notificationInbox=[];
 const reopened=visibleInboxItems(game.notificationInbox);
 assert.equal(reopened.length,1);
 assert.equal(reopened[0].id,DEVELOPER_SUPPORT_MESSAGE.id);
});

test('a legacy one-time copy is migrated away, not duplicated',()=>{
 const game=emptyInbox();
 game.notificationInbox=[{
  id:DEVELOPER_SUPPORT_MESSAGE.id,
  category:'system',
  title:DEVELOPER_SUPPORT_MESSAGE.title,
  body:DEVELOPER_SUPPORT_MESSAGE.body,
  createdAt:Date.now(),
  read:false,
  action:null
 }];
 assert.equal(visibleInboxItems(game.notificationInbox,'all').length,1);
 checkInbox(game,Date.now());
 assert.equal(game.notificationInbox.length,0);
 assert.equal(visibleInboxItems(game.notificationInbox,'system').length,1);
});

test('other inbox items keep their sorting, filters and read state',()=>{
 const regular={id:'regular:1',category:'reward',title:'Reward',body:'Ready',createdAt:1234,read:false,action:null};
 const items=visibleInboxItems([regular],'all');
 assert.equal(items.length,2);
 assert.equal(items[0].pinned,true);
 assert.deepEqual(visibleInboxItems([regular],'reward'),[regular]);
 assert.equal(regular.read,false);
});
