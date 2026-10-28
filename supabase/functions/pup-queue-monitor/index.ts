import {timingSafeEqual} from "node:crypto";
import {createAdminClient} from "../_shared/pupeye.ts";

// Staging only: feed trusted aggregate measurements from observability collector.
// The endpoint NEVER accepts untrusted app/customer-supplied health metrics.
function sameSecret(a:string,b:string){
 const x=new TextEncoder().encode(a),y=new TextEncoder().encode(b);
 return x.length>=32&&x.length===y.length&&timingSafeEqual(x,y);
}
Deno.serve(async(req:Request)=>{
 const header=req.headers.get("authorization")||"";
 const configured=Deno.env.get("PUP_QUEUE_MONITOR_TOKEN")||"";
 if(!sameSecret(header.replace(/^Bearer\s+/i,""),configured))
  return new Response(null,{status:401});
 if(req.method!=="POST")return new Response(null,{status:405});
 try{
  if(Number(req.headers.get("content-length")||0)>4096)
   return new Response(null,{status:413});
  const p=await req.json();
  const baselines=JSON.parse(Deno.env.get("PUP_QUEUE_MEASURED_BASELINES")||"null");
  const at=Date.parse(p.recordedAt);
  // Fail closed on missing evidence, not on players already safely admitted.
  if(!baselines||!Number.isFinite(at)||Math.abs(Date.now()-at)>30000)
   return new Response(JSON.stringify({code:"MONITOR_UNCONFIGURED"}),{status:503});
  const keys=["dbUtilization","edgeP95Ms","saveP95Ms","authErrorRate",
   "arrivalRate","activeSessions"];
  const values=keys.map(k=>p[k]),limits=keys.map(k=>baselines[k]);
  if(values.some(v=>typeof v!=="number"||!Number.isFinite(v)||v<0)||
     limits.some(v=>typeof v!=="number"||!Number.isFinite(v)||v<=0))
   return new Response(null,{status:400});
  const pressure=Math.min(1,Math.max(...values.map((v,i)=>v/limits[i])));
  const admin=createAdminClient();
  const {data,error}=await admin.rpc("pup_queue_health_report",{
   p_score:pressure,p_trustworthy:true
  });
  if(error)throw error;
  return new Response(JSON.stringify({ok:true,automaticActive:data.automaticActive}),{
   headers:{"Content-Type":"application/json","Cache-Control":"no-store"}
  });
 }catch{
  return new Response(JSON.stringify({code:"MONITOR_FAILED"}),{status:503,
   headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
 }
});
