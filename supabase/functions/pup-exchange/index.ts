import {
  HttpError,assertPublishableRequest,authenticateSession,createAdminClient,
  errorResponse,json,verifyEnvelope
} from "../_shared/pupeye.ts";
import {assertGlobalEnforcementAllowed} from "../_shared/global-enforcement.ts";

// All requests are signed by the Android PupEye key and bound to its active
// session. No account UUID, owner, balance or entitlement supplied by the app
// is trusted. A linked beta resolves to the original account's player UUID.
const allowed=new Set(["profile","lookup","request","respond","remove",
  "offer","counter","confirm","decline","cancel"]);
// Fixed-width legacy player codes, plus the single reserved developer vanity code.
const codePattern=/^(?:PUP-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}|PUP-HARLEYTG-DEV)$/;
const idPattern=/^[a-z0-9_]{1,64}$/;
const consent=(a:any)=>Number(a.terms_version)>=1&&Number(a.privacy_version)>=1&&
  Boolean(a.terms_accepted_at)&&Boolean(a.privacy_accepted_at);
const requireOk=(e:any)=>{if(e)throw e;};
const s=(v:unknown,max=80)=>typeof v==="string"?v.trim().slice(0,max):"";
const uniqueIds=(v:unknown,min=1,max=5):string[]=>{
 if(!Array.isArray(v)||v.length<min||v.length>max||
    v.some(x=>typeof x!=="string"||!idPattern.test(x))||
    new Set(v).size!==v.length)
   throw new HttpError(400,"EXCHANGE_OFFER_INVALID","Select distinct eligible puppies.");
 return v;
};
async function identity(admin:any,session:any){
 let {data:account,error}=await admin.from("pup_accounts").select("*")
   .eq("player_uuid",session.player.id)
   .eq("active_installation_uuid",session.installation.id).maybeSingle();
 requireOk(error);
 if(!account){
  const linked=await admin.from("pup_beta_account_links")
    .select("account_uuid").eq("installation_uuid",session.installation.id)
    .gt("expires_at",new Date().toISOString()).maybeSingle();
  requireOk(linked.error);
  if(!linked.data)throw new HttpError(401,"EXCHANGE_LOGIN_REQUIRED","Sign in to your Pup Account.");
  const owner=await admin.from("pup_accounts").select("*")
    .eq("id",linked.data.account_uuid).maybeSingle();
  requireOk(owner.error);account=owner.data;
 }
 if(!account||account.status!=="active"||!consent(account))
   throw new HttpError(403,"EXCHANGE_ACCOUNT_UNAVAILABLE","Pup Account verification is required.");
 const player=await admin.from("pupeye_players").select("*")
   .eq("id",account.player_uuid).single();
 requireOk(player.error);
 if(player.data.status!=="active")throw new HttpError(403,"EXCHANGE_ACCOUNT_UNAVAILABLE","Pup Account is unavailable.");
 const installation=await admin.from("pupeye_installations").select("*")
   .eq("id",account.active_installation_uuid).maybeSingle();
 requireOk(installation.error);
 if(!installation.data||!installation.data.active||installation.data.revoked_at||
    installation.data.player_uuid!==account.player_uuid)
  throw new HttpError(403,"EXCHANGE_OWNER_UNAVAILABLE","The original PupEye installation is unavailable.");
 await assertGlobalEnforcementAllowed(admin,{
  player:player.data,installation:installation.data,
  discordUserId:account.discord_user_id,requestAction:"puppy-exchange"
 });
 return {account,player:player.data};
}
// Web sessions never impersonate Android installations or create signed envelopes.
// They map a verified Supabase user to the same authoritative Pup Account
// and pass through the existing identity/standing/ownership checks.
const WEB_ORIGINS=new Set([
 "https://puppy-clicker.vercel.app",
 "http://localhost:5173",
 "http://127.0.0.1:4173"
]);
async function identityFromWebSession(admin:any,req:Request){
 if(!WEB_ORIGINS.has(req.headers.get("origin")||""))
  throw new HttpError(403,"EXCHANGE_WEB_ORIGIN_INVALID","Unrecognized website origin.");
 const bearer=req.headers.get("authorization")||"";
 if(!/^Bearer\\s+\\S+$/.test(bearer))
  throw new HttpError(401,"EXCHANGE_WEB_SESSION_REQUIRED","Sign in to your Pup Account.");
 // getUser(token) checks the signature, expiry and current Auth user on the server.
 const {data:session,error:authError}=await admin.auth.getUser(bearer.replace(/^Bearer\\s+/,""));
 if(authError||!session?.user||session.user.is_anonymous)
  throw new HttpError(401,"EXCHANGE_WEB_SESSION_REQUIRED","Sign in to your Pup Account.");
 const link=await admin.from("pup_web_auth_links").select("account_uuid")
  .eq("auth_user_id",session.user.id).maybeSingle();
 requireOk(link.error);
 if(!link.data)
  throw new HttpError(403,"EXCHANGE_ACCOUNT_UNAVAILABLE","Link your existing Pup Account.");
 const owner=await admin.from("pup_accounts")
  .select("id,player_uuid,active_installation_uuid")
  .eq("id",link.data.account_uuid).maybeSingle();
 requireOk(owner.error);
 if(!owner.data?.player_uuid||!owner.data?.active_installation_uuid)
  throw new HttpError(403,"EXCHANGE_ACCOUNT_UNAVAILABLE","Pup Account ownership is unavailable.");
 const verified=await identity(admin,{
  player:{id:owner.data.player_uuid},
  installation:{id:owner.data.active_installation_uuid}
 });
 if(verified.account.id!==link.data.account_uuid)
  throw new HttpError(403,"EXCHANGE_ACCOUNT_UNAVAILABLE","Pup Account link mismatch.");
 return verified;
}

async function peerByCode(admin:any,code:string){
 if(!codePattern.test(code))throw new HttpError(400,"EXCHANGE_FRIEND_CODE_INVALID","Enter a valid Friend Code.");
 const p=await admin.from("pupeye_players").select("id,friend_code,status")
   .eq("friend_code",code).maybeSingle();requireOk(p.error);
 if(!p.data||p.data.status!=="active")
   throw new HttpError(404,"EXCHANGE_FRIEND_NOT_FOUND","Friend Code not found.");
 const a=await admin.from("pup_accounts").select("id,username,display_name,status,terms_version,privacy_version,terms_accepted_at,privacy_accepted_at")
   .eq("player_uuid",p.data.id).maybeSingle();requireOk(a.error);
 if(!a.data||a.data.status!=="active"||!consent(a.data))
   throw new HttpError(404,"EXCHANGE_FRIEND_NOT_FOUND","Friend Code not found.");
 return {id:a.data.id,code:p.data.friend_code,
  name:a.data.display_name||a.data.username};
}
async function pair(admin:any,me:string,them:string){
 const q=await admin.from("pup_exchange_connections").select("*")
  .or("and(requester.eq."+me+",recipient.eq."+them+"),and(requester.eq."+them+",recipient.eq."+me+")")
  .maybeSingle();
 requireOk(q.error);
 return q.data;
}
async function friend(admin:any,me:string,them:string,allowDirectGift=false){
 const connection=await pair(admin,me,them);
 // Even developers cannot override an explicit block from either party.
 if(connection?.state==="blocked")
  throw new HttpError(403,"EXCHANGE_BLOCKED","This connection is blocked.");
 if(!allowDirectGift&&connection?.state!=="accepted")
  throw new HttpError(403,"EXCHANGE_FRIENDSHIP_REQUIRED","Add this player as a friend first.");
}
async function developerGiftPrivileges(admin:any,accountId:string):Promise<boolean>{
 // PupEye guild roles are assigned by verified server-side Discord role sync,
 // never user metadata or an unverified client role claim.
 const q=await admin.from("pup_accounts").select("player_uuid,status")
  .eq("id",accountId).maybeSingle();
 requireOk(q.error);
 if(!q.data||q.data.status!=="active")return false;
 const p=await admin.from("pupeye_players")
  .select("status,guild_role,guild_verified_at").eq("id",q.data.player_uuid).maybeSingle();
 requireOk(p.error);
 return p.data?.status==="active"&&p.data?.guild_role==="DEVELOPER"&&
  Boolean(p.data?.guild_verified_at);
}
async function accessibleOffer(admin:any,id:string,me:string){
 if(!/^[0-9a-f-]{36}$/i.test(id))throw new HttpError(400,"EXCHANGE_OFFER_INVALID","Invalid offer.");
 const q=await admin.from("pup_exchange_offers").select("*").eq("id",id)
   .or("sender.eq."+me+",recipient.eq."+me).maybeSingle();
 requireOk(q.error);
 if(!q.data)throw new HttpError(404,"EXCHANGE_OFFER_NOT_FOUND","Offer unavailable.");
 return q.data;
}

async function canGiftAnyCatalogPuppy(admin:any,accountId:string):Promise<boolean>{
 const q=await admin.from("pup_exchange_privileged_gifters")
  .select("enabled").eq("account_uuid",accountId).maybeSingle();
 requireOk(q.error);
 return q.data?.enabled===true;
}
// Dynamic puppies must be published in the game's official catalog. Never
// allow the app to mint arbitrary puppy IDs, even with the owner gift role.
// New V3+ manifest IDs are registered as bound/non-transferable by default,
// which allows gifts only by explicitly privileged accounts.
async function registerPublishedPuppies(admin:any,missing:string[]){
 if(!missing.length)return;
 const base="https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets";
 const getJson=async(url:string,maxBytes:number)=>{
  let response:Response;
  try{
   response=await fetch(url,{headers:{"Accept":"application/json"},signal:AbortSignal.timeout(9000)});
  }catch{throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","The official puppy catalog is temporarily unavailable.");}
  if(!response.ok)throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","The official puppy catalog is temporarily unavailable.");
  const content=await response.text();
  if(content.length>maxBytes)throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","Invalid official puppy catalog.");
  try{return JSON.parse(content);}catch{throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","Invalid official puppy catalog.");}
 };
 const index=await getJson("https://api.github.com/repos/markhitchk/pup-clinker/contents/assets?ref=main",512*1024);
 if(!Array.isArray(index))throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","Invalid official puppy catalog.");
 const folders=index.filter((x:any)=>x?.type==="dir"&&
   (["v2","test","discord_rewards"].includes(x.name)||/^v[3-9][0-9]*$/.test(x.name)))
  .map((x:any)=>x.name).slice(0,30);
 const recognized=new Set<string>();
 for(const folder of folders){
  const manifest=await getJson(base+"/"+folder+"/manifest.json",256*1024);
  if(!Array.isArray(manifest?.puppies)||manifest.puppies.length>500)
   throw new HttpError(503,"EXCHANGE_CATALOG_UNAVAILABLE","Invalid official puppy catalog.");
  const prefix=folder==="test"?"test_":folder==="discord_rewards"?"v2_":folder+"_";
  for(const item of manifest.puppies){
   if(typeof item?.asset_id!=="string"||typeof item?.file!=="string")continue;
   const id=item.asset_id.trim();
   if(idPattern.test(id)&&id.startsWith(prefix)&&
      /^[A-Za-z0-9._-]{1,128}$/.test(item.file.trim())&&item.file.endsWith(".png"))recognized.add(id);
  }
  if(missing.every(id=>recognized.has(id)))break;
 }
 if(missing.some(id=>!recognized.has(id)))
  throw new HttpError(409,"EXCHANGE_UNKNOWN_PUPPY","This puppy is not in the published official roster.");
 const inserted=await admin.from("pup_exchange_transfer_policy")
  .upsert(missing.map(puppy_id=>({
   puppy_id,giftable:false,tradeable:false,bound:true,source_copy:false
  })),{onConflict:"puppy_id",ignoreDuplicates:true});
 requireOk(inserted.error);
}

async function eligible(admin:any,account:any,ids:string[],type:"gift"|"trade"){
 const privileged=type==="gift"&&
  (await canGiftAnyCatalogPuppy(admin,account)||await developerGiftPrivileges(admin,account));
 if(privileged){
  const rows=await admin.from("pup_exchange_transfer_policy").select("puppy_id").in("puppy_id",ids);
  requireOk(rows.error);
  const missing=ids.filter(id=>!rows.data.some((x:any)=>x.puppy_id===id));
  await registerPublishedPuppies(admin,missing);
  return;
 }
 const cloud=await admin.from("pup_account_saves")
   .select("save_data").eq("account_uuid",account).maybeSingle();
 requireOk(cloud.error);
 const owned=cloud.data?.save_data?.stores?.puppy_clicker_save?.values?.unlocked_puppies;
 if(!Array.isArray(owned)||ids.some(id=>!owned.includes(id)))
  throw new HttpError(409,"EXCHANGE_INVENTORY_CHANGED","Refresh your puppy roster.");
 const p=await admin.from("pup_exchange_transfer_policy")
   .select("puppy_id,giftable,tradeable,bound,source_copy").in("puppy_id",ids);
 requireOk(p.error);
 if(ids.some(id=>!p.data.some((x:any)=>x.puppy_id===id&&!x.bound&&!x.source_copy&&
   (type==="gift"?x.giftable:x.tradeable))))
  throw new HttpError(409,"EXCHANGE_NOT_TRANSFERABLE","These puppies cannot be transferred.");
}
async function snapshot(admin:any,who:any,player:any){
 const policies=await admin.from("pup_exchange_transfer_policy")
   .select("puppy_id,giftable,tradeable").eq("bound",false).eq("source_copy",false)
   .limit(500);requireOk(policies.error);
 const c=await admin.from("pup_exchange_connections").select("*")
  .or("requester.eq."+who.id+",recipient.eq."+who.id)
  .order("updated_at",{ascending:false}).limit(100);requireOk(c.error);
 const o=await admin.from("pup_exchange_offers").select("*")
  .or("sender.eq."+who.id+",recipient.eq."+who.id)
  .order("created_at",{ascending:false}).limit(60);requireOk(o.error);
 const peerIds=[...new Set([
  ...(c.data||[]).flatMap((x:any)=>[x.requester,x.recipient]),
  ...(o.data||[]).flatMap((x:any)=>[x.sender,x.recipient])
 ])].filter(id=>id!==who.id);
 const accounts=peerIds.length?await admin.from("pup_accounts")
  .select("id,username,display_name,player_uuid").in("id",peerIds):{data:[],error:null};
 requireOk(accounts.error);
 const players=accounts.data.length?await admin.from("pupeye_players")
   .select("id,friend_code").in("id",accounts.data.map((a:any)=>a.player_uuid)):{data:[],error:null};
 requireOk(players.error);
 const canGiftDirect=await developerGiftPrivileges(admin,who.id);
 const canGiftAll=canGiftDirect||await canGiftAnyCatalogPuppy(admin,who.id);
 const names=new Map(accounts.data.map((a:any)=>[a.id,{
  name:a.display_name||a.username,
  code:players.data.find((p:any)=>p.id===a.player_uuid)?.friend_code||""
 }]));
 return {
  policies:(policies.data||[]).filter((p:any)=>p.giftable||p.tradeable)
    .map((p:any)=>({id:p.puppy_id,giftable:p.giftable,tradeable:p.tradeable})),
  profile:{username:who.display_name||who.username,friendCode:player.friend_code,canGiftAll,canGiftDirect},
  connections:(c.data||[]).map((x:any)=>({
   id:x.id,peer:names.get(x.requester===who.id?x.recipient:x.requester),
   incoming:x.recipient===who.id,state:x.state,blockedByMe:x.blocked_by===who.id
  })),
  offers:(o.data||[]).map((x:any)=>({
   id:x.id,kind:x.kind,offered:x.offered,requested:x.requested,
   state:x.state,incoming:x.recipient===who.id,
   peer:names.get(x.sender===who.id?x.recipient:x.sender),
   expiresAt:x.expires_at,createdAt:x.created_at
  }))
 };
}
async function exchangeRequest(req:Request):Promise<Response>{
 try{
  if(req.method!=="POST")throw new HttpError(405,"EXCHANGE_METHOD_INVALID","POST required.");
  assertPublishableRequest(req);
  const body=await req.json();
  const web=body&&typeof body==="object"&&!Array.isArray(body)&&
   typeof body.operation==="string";
  const admin=createAdminClient();
  let params:any,verified:any;
  if(web){
   verified=await identityFromWebSession(admin,req);
   params=body;
  }else{
   // Preserve the Android PupEye signature, nonce, device and session path.
   const envelope=verifyEnvelope(body,"puppy-exchange");
   const session=await authenticateSession(req,admin,envelope);
   verified=await identity(admin,session);
   params=envelope.payload;
  }
  const action=s(params.operation,32);
  if(!allowed.has(action))throw new HttpError(400,"EXCHANGE_ACTION_INVALID","Invalid Exchange action.");
  // Both transports share the exact same server-side exchange policy and RPC.
  const {account,player}=verified;
  const me=account.id;
  if(action==="profile")return json(200,await snapshot(admin,account,player));
  if(action==="lookup"){
   const target=await peerByCode(admin,s(params.friendCode,30).toUpperCase());
   if(target.id===me)throw new HttpError(409,"EXCHANGE_SELF","You cannot add yourself.");
   return json(200,{player:target});
  }
  if(action==="request"){
   const target=await peerByCode(admin,s(params.friendCode,30).toUpperCase());
   if(target.id===me)throw new HttpError(409,"EXCHANGE_SELF","You cannot add yourself.");
   const existing=await pair(admin,me,target.id);
   if(existing?.state==="blocked")throw new HttpError(403,"EXCHANGE_BLOCKED","Friend request unavailable.");
   if(existing?.state==="accepted")return json(200,{ok:true,alreadyFriends:true});
   if(existing?.state==="pending")throw new HttpError(409,"EXCHANGE_REQUEST_PENDING","Friend request already pending.");
   if(existing){
    const up=await admin.from("pup_exchange_connections").update({
     requester:me,recipient:target.id,state:"pending",blocked_by:null,updated_at:new Date().toISOString()
    }).eq("id",existing.id).eq("state","declined");requireOk(up.error);
   }else{
    const q=await admin.from("pup_exchange_connections").insert({
     requester:me,recipient:target.id,state:"pending"
    });requireOk(q.error);
   }
   return json(200,{ok:true});
  }
  if(action==="respond"||action==="remove"){
   const id=s(params.connectionId,50);
   if(!/^[0-9a-f-]{36}$/i.test(id))throw new HttpError(400,"EXCHANGE_CONNECTION_INVALID","Invalid connection.");
   const q=await admin.from("pup_exchange_connections").select("*").eq("id",id)
    .or("requester.eq."+me+",recipient.eq."+me).maybeSingle();requireOk(q.error);
   const c=q.data;
   if(!c)throw new HttpError(404,"EXCHANGE_CONNECTION_NOT_FOUND","Connection unavailable.");
   if(action==="remove"){
    if(c.state==="blocked"&&c.blocked_by!==me)
      throw new HttpError(403,"EXCHANGE_BLOCKED","This player blocked the connection.");
    const removed=await admin.from("pup_exchange_connections").delete().eq("id",id);
    requireOk(removed.error);return json(200,{ok:true});
   }
   const answer=s(params.answer,12);
   if(!["accept","decline","block"].includes(answer))
    throw new HttpError(400,"EXCHANGE_RESPONSE_INVALID","Invalid response.");
   if(answer!=="block"&&(c.recipient!==me||c.state!=="pending"))
    throw new HttpError(403,"EXCHANGE_RESPONSE_INVALID","Request is not awaiting your response.");
   if(answer==="block"&&c.state==="blocked"&&c.blocked_by!==me)
    throw new HttpError(403,"EXCHANGE_BLOCKED","Connection unavailable.");
   const up=await admin.from("pup_exchange_connections").update({
    state:answer==="accept"?"accepted":answer==="decline"?"declined":"blocked",
    blocked_by:answer==="block"?me:null,updated_at:new Date().toISOString()
   }).eq("id",id);requireOk(up.error);
   return json(200,{ok:true});
  }
  if(action==="offer"){
   const target=await peerByCode(admin,s(params.friendCode,30).toUpperCase());
   const kind=params.kind==="gift"?"gift":params.kind==="trade"?"trade":null;
   if(!kind)throw new HttpError(400,"EXCHANGE_OFFER_INVALID","Invalid offer type.");
   const canGiftDirect=kind==="gift"&&await developerGiftPrivileges(admin,me);
   await friend(admin,me,target.id,canGiftDirect);
   const ids=uniqueIds(params.offered,1,kind==="gift"?1:5);
   await eligible(admin,me,ids,kind);
   const q=await admin.from("pup_exchange_offers").insert({
    sender:me,recipient:target.id,kind,offered:ids,requested:[]
   });requireOk(q.error);return json(200,{ok:true});
  }
  if(["counter","confirm","decline","cancel"].includes(action)){
   const offer=await accessibleOffer(admin,s(params.offerId,40),me);
   if(offer.state==="completed"||offer.state==="declined"||offer.state==="cancelled")
    throw new HttpError(409,"EXCHANGE_OFFER_CLOSED","Offer is closed.");
   if(action==="cancel"){
    if(offer.sender!==me)throw new HttpError(403,"EXCHANGE_NOT_SENDER","Only sender can cancel.");
    const q=await admin.from("pup_exchange_offers").update({state:"cancelled",updated_at:new Date().toISOString()})
     .eq("id",offer.id).in("state",["pending","countered"]);requireOk(q.error);
    return json(200,{ok:true});
   }
   if(action==="decline"){
    if(offer.recipient!==me)throw new HttpError(403,"EXCHANGE_NOT_RECIPIENT","Only recipient can decline.");
    const q=await admin.from("pup_exchange_offers").update({state:"declined",updated_at:new Date().toISOString()})
     .eq("id",offer.id).in("state",["pending","countered"]);requireOk(q.error);
    return json(200,{ok:true});
   }
   if(action==="counter"){
    if(offer.kind!=="trade"||offer.recipient!==me||offer.state!=="pending")
     throw new HttpError(409,"EXCHANGE_NOT_PENDING","Cannot counter this trade.");
    const requested=uniqueIds(params.requested,1,5);
    await eligible(admin,me,requested,"trade");
    const q=await admin.from("pup_exchange_offers").update({
     requested,state:"countered",updated_at:new Date().toISOString()
    }).eq("id",offer.id).eq("state","pending");requireOk(q.error);
    return json(200,{ok:true});
   }
   if(action==="confirm"){
    if((offer.kind==="gift"&&offer.recipient!==me)||
       (offer.kind==="trade"&&offer.sender!==me))
      throw new HttpError(403,"EXCHANGE_NOT_AUTHORIZED","Confirmation required from other player.");
    const q=await admin.rpc("pup_exchange_complete",{p_offer:offer.id,p_actor:me});
    if(q.error){
      const code=String(q.error.message).match(/EXCHANGE_[A-Z_]+/)?.[0]||"EXCHANGE_TRANSFER_REJECTED";
      throw new HttpError(409,code,"The exchange could not be completed. Refresh and try again.");
    }
    return json(200,q.data);
   }
  }
  throw new HttpError(400,"EXCHANGE_ACTION_INVALID","Invalid exchange action.");
  }catch(error){return errorResponse(error);}
}
// Native signed requests do not have browser CORS; allow only the official web
// origin (and local development) to send authenticated browser requests.
Deno.serve(async(req:Request)=>{
 const origin=req.headers.get("origin")||"";
 const web=WEB_ORIGINS.has(origin);
 if(req.method==="OPTIONS"){
  return new Response(null,{status:web?204:403,
   headers:web?{
    "access-control-allow-origin":origin,
    "access-control-allow-methods":"POST,OPTIONS",
    "access-control-allow-headers":"authorization,apikey,content-type,x-client-info",
    "access-control-max-age":"600",
    "vary":"Origin"
   }:{"cache-control":"no-store"}});
 }
 const response=await exchangeRequest(req);
 if(web){response.headers.set("access-control-allow-origin",origin);
  response.headers.set("vary","Origin");}
 return response;
});
