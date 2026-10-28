import { createHash, createPublicKey, createVerify } from "node:crypto";
import type { Buffer as NodeBuffer } from "node:buffer";
import { HttpError, createAdminClient } from "./pupeye.ts";

/** Queue proof is tied to the existing PupEye Android Keystore P-256 key. */
const ACTIONS=new Set(["bootstrap","status","heartbeat","admit","release"]);
const HEADER="X-Puppy-Queue-Ticket";
export function verifyQueueProof(raw: Record<string,unknown>) {
 const action=raw.action;
 const publicKey=raw.publicKey,signature=raw.signature,nonce=raw.nonce;
 const at=raw.timestampEpochMs,ticketId=raw.ticketId||"";
 if(typeof action!=="string"||!ACTIONS.has(action)||
  typeof publicKey!=="string"||!/^[-A-Za-z0-9+/=]{80,400}$/.test(publicKey)||
  typeof signature!=="string"||signature.length>220||
  typeof nonce!=="string"||!/^[a-z0-9-]{16,80}$/i.test(nonce)||
  typeof at!=="number"||!Number.isSafeInteger(at)||
  Math.abs(Date.now()-at)>90000||
  typeof ticketId!=="string"||(ticketId!==""&&!/^[0-9a-f-]{36}$/i.test(ticketId)))
  throw new HttpError(401,"PC-Q008","Queue ticket verification failed.");
 let keyBytes:Uint8Array,sigBytes:Uint8Array;
 try{keyBytes=Uint8Array.from(atob(publicKey),c=>c.charCodeAt(0));
     sigBytes=Uint8Array.from(atob(signature),c=>c.charCodeAt(0));}
 catch{throw new HttpError(401,"PC-Q008","Queue proof is invalid.");}
 const binding=createHash("sha256").update(keyBytes).digest("hex");
 const material=["PuppyClicker/Queue/v1",action,String(at),nonce,ticketId,publicKey].join("\n");
 try{
  const verifier=createVerify("SHA256");verifier.update(material);verifier.end();
  const key=createPublicKey({key:keyBytes as unknown as NodeBuffer,format:"der",type:"spki"});
  if(!verifier.verify(key,sigBytes))throw Error("Proof rejected");
 }catch{throw new HttpError(401,"PC-Q008","Queue proof is invalid.");}
 return {action,binding,ticketId:ticketId||null};
}
export function queueError(code:string):HttpError{
 if(code==="PC-Q007")return new HttpError(503,"PC-Q007","Puppy Clicker is undergoing maintenance.");
 if(code==="PC-Q005")return new HttpError(503,"PC-Q005","Queue service is protecting game availability.");
 return new HttpError(503,"PC-Q002","Queue status is temporarily unavailable.");
}
export function queueTicketFromRequest(req:Request):string|null {
 const ticket=req.headers.get(HEADER);
 return ticket&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(ticket)
  ?ticket:null;
}
/** Called after existing PupEye session+device verification; never replaces bans. */
export async function requirePlayerAdmission(
 req:Request, admin:any, session:{player:any;installation:any}, action:string
):Promise<void>{
 const ticket=queueTicketFromRequest(req);
 const {data,error}=await admin.rpc("pup_queue_guard",{
  p_ticket:ticket,p_binding_hash:session.installation.device_key_id,
  p_player:session.player.id,p_installation:session.installation.id,
  p_session:session.session.id,
  p_touch:true,
 });
 if(error){
  // Stable public error only; do not leak PostgreSQL errors or resource metrics.
  if(String(error.message).includes("QUEUE_MAINTENANCE"))
   throw new HttpError(503,"PC-Q007","Server maintenance is active.");
  if(String(error.message).includes("QUEUE_"))
   throw new HttpError(403,"PC-Q004","A valid player admission is required.");
  throw new HttpError(503,"PC-Q002","Admission service is unavailable.");
 }
 if(data!==true)throw new HttpError(403,"PC-Q004","Admission could not be verified.");
}
/**
 * Distributed rate limiting via explicitly configured upstream Redis.
 * Never use in-process counters as the production security boundary.
 * This requires a non-user-configurable source identifier at the trusted edge.
 */
export async function queueRateLimit(req:Request,binding:string):Promise<void>{
 const endpoint=Deno.env.get("PUP_QUEUE_REDIS_REST_URL");
 const key=Deno.env.get("PUP_QUEUE_REDIS_REST_TOKEN");
 const ipCeiling=Number(Deno.env.get("PUP_QUEUE_IP_MAX_PER_MIN"));
 const bindingCeiling=Number(Deno.env.get("PUP_QUEUE_DEVICE_MAX_PER_MIN"));
 if(!endpoint||!key||!Number.isSafeInteger(ipCeiling)||ipCeiling<1||
    !Number.isSafeInteger(bindingCeiling)||bindingCeiling<1)
  throw new HttpError(503,"PC-Q005","Admission rate limiting is not configured.");
 // Edge/runtime-provided connecting IP is an abuse signal, not account identity.
 const ip=req.headers.get("cf-connecting-ip")??req.headers.get("x-real-ip")??
  req.headers.get("x-forwarded-for")?.split(",")[0]??"unknown";
 const privacyDigest=createHash("sha256").update(ip).digest("hex").slice(0,24);
 const slot=Math.floor(Date.now()/60000);
 const buckets=[`pupq:ip:${privacyDigest}:${slot}`,`pupq:device:${binding}:${slot}`];
 const response=await fetch(endpoint.replace(/\/$/,"")+"/pipeline",{
  method:"POST",headers:{Authorization:"Bearer "+key,"Content-Type":"application/json"},
  body:JSON.stringify(buckets.flatMap(b=>[["INCR",b],["EXPIRE",b,90]])),
  signal:AbortSignal.timeout(2000)
 }).catch(()=>null);
 if(!response?.ok)throw new HttpError(503,"PC-Q005","Queue protection is temporarily unavailable.");
 const results=await response.json().catch(()=>null);
 if(!Array.isArray(results)||results.length!==4)
  throw new HttpError(503,"PC-Q005","Queue protection is temporarily unavailable.");
 // Thresholds are anti-abuse request ceilings, not player capacity.
 if(Number(results[0]?.result)>ipCeiling||Number(results[2]?.result)>bindingCeiling)
  throw new HttpError(429,"PC-Q005","Please slow down and retry.");
}
export async function queueDispatch(admin:any,action:string,binding:string,ticketId:string|null){
 // Ordinary status polling must remain read-only. The capacity-serialized
 // dispatch RPC deliberately rejects "status" and is only for transitions.
 const {data,error}=action==="status"
  ?await admin.rpc("pup_queue_status",{
    p_binding_hash:binding,p_ticket:ticketId
   })
  :await admin.rpc("pup_queue_dispatch",{
    p_action:action,p_binding_hash:binding,p_ticket:ticketId
   });
 if(error)throw new HttpError(503,"PC-Q002","Queue status is temporarily unavailable.");
 return data as Record<string,unknown>;
}
export function publicQueueResponse(raw:Record<string,unknown>){
 const state=typeof raw.state==="string"?raw.state:"UNAVAILABLE";
 const legalStates=new Set(["ALLOW","WAITING","READY","ADMITTED","EXPIRED","REVOKED","MAINTENANCE","UNAVAILABLE","RELEASED"]);
 const safeState=legalStates.has(state)?state:"UNAVAILABLE";
 const retryMs=Math.min(60000,Math.max(5000,Number(raw.retryAfterMs)||12000));
 return {
  state:safeState,
  ticketId:typeof raw.ticketId==="string"?raw.ticketId:null,
  playersAhead:typeof raw.playersAhead==="number"&&Number.isSafeInteger(raw.playersAhead)&&raw.playersAhead>=0?raw.playersAhead:null,
  estimatedWaitSeconds:null, // no invented ETA before load-calibrated throughput
  retryAfterMs:retryMs,
  code:typeof raw.code==="string"&&/^PC-Q(?:00[1-9]|010)$/.test(raw.code)?raw.code:null,
  protocol:1,
 };
}
