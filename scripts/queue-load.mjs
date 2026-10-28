// Isolated staging ONLY. Simulates new anonymous Android P-256 queue arrivals.
// Example: PUP_QUEUE_LOAD_APPROVED=YES PUP_QUEUE_STAGING_URL=https://<staging>.supabase.co/functions/v1/pup-queue
//          PUP_QUEUE_STAGING_KEY=sb_publishable_... PUP_QUEUE_LOAD_SIZE=100 node scripts/queue-load.mjs
import {createSign,generateKeyPairSync,randomUUID} from 'node:crypto';
import {performance} from 'node:perf_hooks';

const endpoint=process.env.PUP_QUEUE_STAGING_URL||'';
const key=process.env.PUP_QUEUE_STAGING_KEY||'';
const sample=Number(process.env.PUP_QUEUE_LOAD_SIZE||100);
const maxParallel=Number(process.env.PUP_QUEUE_LOAD_CONCURRENCY||40);
if(process.env.PUP_QUEUE_LOAD_APPROVED!=='YES')throw Error('Explicit staging load approval required');
if(!/^https:\/\/[a-z0-9-]+\.supabase\.co\/functions\/v1\/pup-queue$/.test(endpoint)||
  endpoint.includes('damcmyqusdxucwozkoeh')||
  !key.startsWith('sb_publishable_'))throw Error('Refusing to load test non-staging/production services');
if(![100,500,1000,5000,10000].includes(sample)||maxParallel<1||maxParallel>100)
 throw Error('Invalid staged arrival scenario/concurrency');
const observations=[],errors=[],tickets=[];
let cursor=0;
const begin=performance.now();
const pct=(samples,p)=>samples.length?samples[Math.min(samples.length-1,Math.floor(samples.length*p))]:null;
async function arrival(n){
 const {privateKey,publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
 const pub=publicKey.export({type:'spki',format:'der'}).toString('base64');
 const at=Date.now(),nonce=randomUUID(),action='bootstrap',ticketId='';
 const material=['PuppyClicker/Queue/v1',action,String(at),nonce,ticketId,pub].join('\n');
 const signer=createSign('SHA256');signer.update(material);signer.end();
 const payload={action,publicKey:pub,timestampEpochMs:at,nonce,ticketId,
  signature:signer.sign(privateKey).toString('base64')};
 const started=performance.now();
 try{
  const response=await fetch(endpoint,{method:'POST',
   headers:{'Content-Type':'application/json',apikey:key},
   body:JSON.stringify(payload),signal:AbortSignal.timeout(20000)});
  const result=await response.json();
  observations.push({latencyMs:performance.now()-started,state:result.state,httpStatus:response.status,
   ahead:result.playersAhead,index:n});
  if(result.ticketId)tickets.push(result.ticketId);
  if(!response.ok)errors.push({code:result.code||'HTTP_'+response.status});
 }catch(e){errors.push({code:'NETWORK_ERROR'});}
}
async function worker(){while(cursor<sample){const i=cursor++;await arrival(i);}}
await Promise.all(Array.from({length:Math.min(maxParallel,sample)},worker));
const latencies=observations.map(x=>x.latencyMs).sort((a,b)=>a-b);
const statuses=Object.fromEntries([...new Set(observations.map(x=>x.state))].map(k=>
 [k,observations.filter(x=>x.state===k).length]));
const report={scenarioArrivals:sample,concurrentWorkers:maxParallel,
 completed:observations.length,totalFailures:errors.length,uniqueTicketIds:new Set(tickets).size,
 states:statuses,p50Ms:pct(latencies,.5),p95Ms:pct(latencies,.95),p99Ms:pct(latencies,.99),
 elapsedSeconds:Math.round((performance.now()-begin)/1000),
 failureCodes:Object.fromEntries([...new Set(errors.map(e=>e.code))].map(k=>
  [k,errors.filter(e=>e.code===k).length]))};
console.log(JSON.stringify(report,null,2));
if(errors.length>0)process.exitCode=1;
