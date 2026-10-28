import {assertEquals,assertRejects} from "jsr:@std/assert@1";
import {createSign,generateKeyPairSync,createHash} from "node:crypto";
import {verifyQueueProof,publicQueueResponse,requirePlayerAdmission} from "../_shared/queue-admission.ts";
function proof(action="bootstrap",at=Date.now()){
 const {privateKey,publicKey}=generateKeyPairSync("ec",{namedCurve:"prime256v1"});
 const pub=publicKey.export({type:"spki",format:"der"}).toString("base64");
 const nonce=crypto.randomUUID(),ticketId="";
 const material=["PuppyClicker/Queue/v1",action,String(at),nonce,ticketId,pub].join("\n");
 const sig=createSign("SHA256");sig.update(material);sig.end();
 return {action,publicKey:pub,nonce,ticketId,timestampEpochMs:at,
  signature:sig.sign(privateKey).toString("base64")};
}
Deno.test("Queue proof binds Android P-256 key to signed operation",()=>{
 const signed=proof("bootstrap");
 const verified=verifyQueueProof(signed);
 assertEquals(verified.action,"bootstrap");
 assertEquals(verified.binding,createHash("sha256").update(Buffer.from(signed.publicKey,"base64")).digest("hex"));
});
Deno.test("Queue request cannot mutate action, time or signature",async()=>{
 const good=proof("admit");
 await assertRejects(async()=>verifyQueueProof({...good,action:"bootstrap"}));
 await assertRejects(async()=>verifyQueueProof({...good,timestampEpochMs:Date.now()-600000}));
 await assertRejects(async()=>verifyQueueProof({...good,signature:"bad"}));
});
Deno.test("Only bounded public fields leave the queue Edge Function",()=>{
 const safe=publicQueueResponse({state:"WAITING",ticketId:"e098d14a-d133-4bcf-a9be-9e60c08ea4c9",
  playersAhead:17,retryAfterMs:1,estimatedWaitSeconds:100,secretToken:"private"});
 assertEquals(safe.playersAhead,17);
 assertEquals(safe.retryAfterMs,5000);
 assertEquals("secretToken" in safe,false);
 assertEquals(safe.estimatedWaitSeconds,null);
});
Deno.test("Server rejects gameplay without verified live admission",async()=>{
 const session={player:{id:"p"},installation:{id:"i",device_key_id:"a".repeat(64)}};
 const admin={rpc:async()=>({data:null,error:{message:"QUEUE_ADMISSION_REQUIRED"}})};
 const req=new Request("https://localhost/save");
 await assertRejects(()=>requirePlayerAdmission(req,admin,session,"save-checkpoint"));
});
