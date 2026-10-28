import {
 assertPublishableRequest,authenticateSession,verifyEnvelope,
 createAdminClient,HttpError,json,errorResponse
} from "../_shared/pupeye.ts";

/** Queue configuration is never exposed to the public waiting-room client. */
Deno.serve(async(req:Request)=>{
 try{
  assertPublishableRequest(req);
  const signed=verifyEnvelope(await req.json());
  if(!["queue-admin-status","queue-admin-config"].includes(signed.action))
   throw new HttpError(400,"QUEUE_ADMIN_ACTION_INVALID","Invalid queue operation.");
  const admin=createAdminClient();
  const session=await authenticateSession(req,admin,signed);
  // Role is stored server-side on verified PupEye player, NOT editable JWT user metadata.
  if(session.player.guild_role!=="DEVELOPER"||session.player.status!=="active")
   throw new HttpError(403,"QUEUE_ADMIN_FORBIDDEN","A verified developer role is required.");
  if(signed.action==="queue-admin-status"){
   const {data,error}=await admin.rpc("pup_queue_admin_status");
   // pup_queue must NEVER be exposed to the Data API; use dedicated public
   // service-role admin RPC instead of granting private schema access.
   if(error)throw new HttpError(503,"QUEUE_ADMIN_UNAVAILABLE","Queue monitoring is unavailable.");
   return json(200,{config:{
    mode:data.mode,capacity:data.capacity,slotsUsed:data.slots_used,
    maxAdmitsPerMinute:data.max_admits_per_minute,
    automaticActive:data.automatic_active,healthCheckedAt:data.health_checked_at,
    revision:data.revision
   }});
  }
  const payload=signed.payload;
  const mode=payload.mode;
  const capacity=payload.capacity??null;
  const rate=payload.maxAdmitsPerMinute??null;
  if(typeof mode!=="string"||!["disabled","manual","automatic","maintenance"].includes(mode)||
   (capacity!==null&&(!Number.isSafeInteger(capacity)||capacity<1||capacity>200000))||
   (rate!==null&&(!Number.isSafeInteger(rate)||rate<1||rate>100000)))
   throw new HttpError(400,"QUEUE_ADMIN_INVALID","Invalid queue mode or measured capacity.");
  const {data,error}=await admin.rpc("pup_queue_admin_update",{
   p_actor:session.player.id,p_mode:mode,p_capacity:capacity,p_rate:rate
  });
  if(error)throw new HttpError(409,"QUEUE_ADMIN_CONFLICT","Queue config was rejected; verify active leases and measured capacity.");
  return json(200,data);
 }catch(error){return errorResponse(error);}
});
