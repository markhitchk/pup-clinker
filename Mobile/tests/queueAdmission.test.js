import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {nextQueueDelay,normalizeQueueResponse,queueError,QUEUE_ERRORS} from '../src/production/queueModel.js';
const file=(path)=>readFileSync(new URL(path,import.meta.url),'utf8');

test('queue errors are explicit stable support codes with safe text',()=>{
 assert.equal(Object.keys(QUEUE_ERRORS).length,10);
 for(const [code,error] of Object.entries(QUEUE_ERRORS)){
  assert.match(code,/^PC-Q0(?:0[1-9]|10)$/);
  assert.ok(error.title&&error.message&&error.recovery);
 }
 assert.equal(queueError('raw-secret').code,'PC-Q010');
});
test('queue status never fabricates ETA or accepts an untrusted position',()=>{
 const value=normalizeQueueResponse({state:'WAITING',playersAhead:247,
  estimatedWaitSeconds:20,retryAfterMs:100,correlationId:'not private!'});
 assert.equal(value.playersAhead,247);
 assert.equal(value.retryAfterMs,5000);
 assert.equal(value.estimatedWaitSeconds,undefined);
 assert.equal(value.correlationId,null);
 assert.equal(normalizeQueueResponse({state:'WAITING',playersAhead:-1}).playersAhead,null);
 assert.equal(normalizeQueueResponse({state:'ADMITTED'}).state,'ADMITTED');
 assert.equal(normalizeQueueResponse({state:'DROP_DB'}).state,'UNAVAILABLE');
});
test('queue polling uses bounded backoff and jitter',()=>{
 assert.ok(nextQueueDelay(12000,0,0.1)>=5000);
 assert.ok(nextQueueDelay(12000,0,1)>nextQueueDelay(12000,0,0));
 assert.ok(nextQueueDelay(12000,99,0.5)<=60000);
 assert.ok(nextQueueDelay(10,0,0)>0);
});
test('full game is not imported before server admission',()=>{
 const entry=file('../index.js'),queue=file('../src/production/PuppyQueueRoom.jsx');
 assert.doesNotMatch(entry,/import App from/);
 assert.match(entry,/if\(!admitted\)/);
 assert.match(entry,/require\('\.\/App\.js'\)/);
 assert.match(entry,/EXPO_PUBLIC_PUP_QUEUE_ENABLED/);
 assert.match(queue,/native\[method\]/);
 assert.match(queue,/queueAdmit/);
 assert.match(queue,/AppState\.addEventListener/);
 assert.match(queue,/lastHeartbeat/);
});
test('anonymous queue ticket uses device Keystore proof and is stored encrypted',()=>{
 const native=file('../plugins/puppy-production/PuppyBetaAccountPackage.kt');
 for(const marker of ['queueBootstrap','queueStatus','queueHeartbeat','queueAdmit',
  'queueFile','initializeQueueIdentity','PuppyClicker/Queue/v1',
  'X-Puppy-Queue-Ticket','SHA256withECDSA'])
  assert.ok(native.includes(marker),marker);
 assert.match(native,/write\(queueFile,JSONObject/);
 assert.match(native,/read\(queueFile\)/);
});
test('protected backend paths gate gameplay and save writes',()=>{
 for(const target of [
  '../../supabase/functions/pup-beta-link/index.ts',
  '../../supabase/functions/pup-account/index.ts',
  '../../supabase/functions/pupeye-sync/index.ts',
  '../../supabase/functions/pupeye-transaction/index.ts',
  '../../supabase/functions/pupeye-save-attestation/index.ts',
 ]){
  assert.match(file(target),/requirePlayerAdmission/);
 }
 const guard=file('../../supabase/functions/_shared/queue-admission.ts');
 assert.match(guard,/pup_queue_guard/);
 assert.match(guard,/session\.installation\.device_key_id/);
});
test('private schema has serialized admission and forbids direct user RPCs',()=>{
 const sql=file('../../supabase/migrations/20261028010000_pup_smart_admission_staging.sql');
 const monitorSql=file('../../supabase/migrations/20261028010100_pup_queue_trusted_monitor_staging.sql');
 assert.match(sql,/for update;/i);
 assert.match(sql,/pup_queue_guard/);
 assert.match(sql,/slots_used <= capacity/);
 assert.match(sql,/grant execute on function public\.pup_queue_dispatch.*to service_role/i);
 assert.match(sql,/revoke all on function public\.pup_queue_dispatch.*from public,anon,authenticated/i);
 assert.match(sql,/health_checked_at/);
 assert.match(monitorSql,/high_samples/);
 assert.doesNotMatch(sql,/grant execute on function .* to anon/i);
});
