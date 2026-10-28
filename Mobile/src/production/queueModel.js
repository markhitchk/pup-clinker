export const QUEUE_ERRORS=Object.freeze({
 "PC-Q001":{title:"Can't connect to the queue",message:"Your device couldn't reach the Puppy Clicker waiting room.",recovery:"Check your connection and try again."},
 "PC-Q002":{title:"Queue status unavailable",message:"We couldn't refresh your saved place right now.",recovery:"Your spot may still be reserved. Retry shortly."},
 "PC-Q003":{title:"Queue spot expired",message:"Your previous waiting-room ticket is no longer valid.",recovery:"Reconnect to get a new place in line."},
 "PC-Q004":{title:"Admission verification failed",message:"Your admission couldn't be verified securely.",recovery:"Reconnect to the queue. Your Pup Account and saves are unchanged."},
 "PC-Q005":{title:"Server is very busy",message:"Many puppies are joining. The waiting-room service is protecting the game.",recovery:"We'll check again after a short break."},
 "PC-Q006":{title:"Connection lost",message:"The connection dropped while you were waiting.",recovery:"Reconnect to restore your place if it's still eligible."},
 "PC-Q007":{title:"Maintenance in progress",message:"Puppy Clicker is temporarily unavailable for maintenance.",recovery:"Check back later. No queue position is being shown."},
 "PC-Q008":{title:"Queue ticket invalid",message:"This waiting-room ticket can no longer be used.",recovery:"Reconnect to request a new ticket."},
 "PC-Q009":{title:"Admission expired",message:"The time to enter Puppy Clicker has expired.",recovery:"Reconnect to get a new admission ticket."},
 "PC-Q010":{title:"Waiting room unavailable",message:"Puppy Clicker couldn't finish its entry check.",recovery:"Try again later or share this code with Support."}
});
export const clampQueueDelay=(ms)=>Math.min(60000,Math.max(5000,Number.isFinite(ms)?ms:12000));
export const nextQueueDelay=(ms,errors=0,random=Math.random())=>{
 const base=clampQueueDelay(ms);
 const backoff=Math.min(60000,base*Math.pow(1.7,Math.min(errors,4)));
 return Math.round(Math.max(5000,Math.min(60000,backoff*(0.85+0.3*random))));
};
export function queueError(code){
 const safe=Object.prototype.hasOwnProperty.call(QUEUE_ERRORS,code)?code:"PC-Q010";
 return {code:safe,...QUEUE_ERRORS[safe]};
}
export function normalizeQueueResponse(value){
 const allowed=new Set(["ALLOW","WAITING","READY","ADMITTED","MAINTENANCE","UNAVAILABLE","EXPIRED","REVOKED"]);
 const state=allowed.has(value?.state)?value.state:"UNAVAILABLE";
 const ahead=Number.isSafeInteger(value?.playersAhead)&&value.playersAhead>=0?value.playersAhead:null;
 return {state,playersAhead:ahead,code:value?.code,correlationId:typeof value?.correlationId==="string"&&
  /^[a-z0-9-]{12,64}$/i.test(value.correlationId)?value.correlationId:null,
  retryAfterMs:clampQueueDelay(value?.retryAfterMs)};
}
