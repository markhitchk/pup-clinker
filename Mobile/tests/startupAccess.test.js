import test from 'node:test';
import assert from 'node:assert/strict';
import {startupAccess} from '../src/production/startupAccess.js';
test('account gate accepts signed gameplay only',()=>assert.equal(startupAccess({account:{status:'VERIFIED',gameplayWritable:true},cloudState:'LIVE_SYNCED',syncStatus:{state:'READY'},ready:true}).phase,'ready'));
