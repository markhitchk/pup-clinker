import { assertPublishableRequest,createAdminClient,json,errorResponse,HttpError } from "../_shared/pupeye.ts";
import { verifyQueueProof,queueRateLimit,queueDispatch,publicQueueResponse } from "../_shared/queue-admission.ts";

/** Decode at most 2 KiB, even when clients omit Content-Length or stream chunks. */
async function boundedJson(req:Request):Promise<Record<string,unknown>>{
 const reader=req.body?.getReader();
 if(!reader)throw new HttpError(400,"PC-Q008","Queue request is empty.");
 const fragments:Uint8Array[]=[];
 let total=0;
 try{
  while(true){
   const {done,value}=await reader.read();
   if(done)break;
   total+=value.byteLength;
   if(total>2048){
    await reader.cancel().catch(()=>{});
    throw new HttpError(413,"PC-Q008","Queue request is too large.");
   }
   fragments.push(value);
  }
 }finally{reader.releaseLock();}
 const combined=new Uint8Array(total);
 let pos=0;
 for(const part of fragments){combined.set(part,pos);pos+=part.byteLength;}
 try{
  const value=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(combined));
  if(!value||typeof value!=="object"||Array.isArray(value))
   throw Error("Invalid queue body");
  return value as Record<string,unknown>;
 }catch{throw new HttpError(400,"PC-Q008","Queue request is invalid.");}
}

/** Minimal anonymous route. No account creation, saves, user identifiers or secret keys. */
Deno.serve(async req=>{
 const correlationId=crypto.randomUUID();
 try{
  if(req.method!=="POST")throw new HttpError(405,"PC-Q010","Use POST for queue requests.");
  if(Number(req.headers.get("content-length")??0)>2048)
   throw new HttpError(413,"PC-Q008","Queue request is too large.");
  assertPublishableRequest(req);
  const body=await boundedJson(req);
  const proof=verifyQueueProof(body);
  await queueRateLimit(req,proof.binding,proof.nonce);
  const admin=createAdminClient();
  const result=publicQueueResponse(await queueDispatch(admin,proof.action,proof.binding,proof.ticketId));
  return new Response(JSON.stringify({...result,correlationId}),{
   status:result.state==="UNAVAILABLE"?503:200,
   headers:{"Content-Type":"application/json","Cache-Control":"no-store",
    "Retry-After":String(Math.ceil(result.retryAfterMs/1000))}
  });
 }catch(error){
  const code=error instanceof HttpError && /^PC-Q0(?:0[1-9]|10)$/.test(error.code)?error.code:"PC-Q010";
  const retry=code==="PC-Q005"?30:12;
  return new Response(JSON.stringify({
   state:"UNAVAILABLE",code,correlationId,retryAfterMs:retry*1000
  }),{status:code==="PC-Q005"?503:code==="PC-Q008"?401:503,
   headers:{"Content-Type":"application/json","Cache-Control":"no-store",
    "Retry-After":String(retry)}});
 }
});
