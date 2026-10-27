import test from 'node:test';
import assert from 'node:assert/strict';
import {startupAccess} from '../src/production/startupAccess.js';
test('account gate accepts signed gameplay only',()=>assert.equal(startupAccess({account:{status:'VERIFIED',gameplayWritable:true},cloudState:'LIVE_SYNCED',syncStatus:{state:'READY'},ready:true}).phase,'ready'));

test('bans and review cannot open gameplay',()=>{
 const base={account:{status:'VERIFIED',gameplayWritable:true},cloudState:'LIVE_SYNCED',ready:true};
 assert.equal(startupAccess({...base,syncStatus:{state:'ERROR',errorCode:'GLOBAL_BANNED'}}).phase,'banned');
 assert.equal(startupAccess({...base,syncStatus:{state:'ERROR',errorCode:'REVIEW_REQUIRED'}}).phase,'review');
 assert.equal(startupAccess({...base,account:{status:'CONNECTION_ERROR'}}).phase,'offline');
});
