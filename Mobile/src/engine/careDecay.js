// Source parity: PuppyCareSystem.kt. Each puppy owns its own offline care clock.
import {switchPuppy} from './game.js';
const FIELDS=['happiness','fullness','energy','cleanliness','bond'];
const clamp=n=>Math.min(100,Math.max(0,Number.isFinite(n)?Math.trunc(n):0));
export function decayCareProfile(profile,elapsedMinutes){
 const m=Math.min(240,Math.max(0,Number.isFinite(elapsedMinutes)?Math.trunc(elapsedMinutes):0));
 const p=Object.fromEntries(FIELDS.map(k=>[k,clamp(profile?.[k]??(k==='bond'?10:100))]));
 return {...p,happiness:Math.max(0,p.happiness-Math.floor(m/3)),
  fullness:Math.max(0,p.fullness-Math.floor(m/2)),
  energy:Math.max(0,p.energy-Math.floor(m/4)),
  cleanliness:Math.max(0,p.cleanliness-Math.floor(m/4))};
}
const lastFor=(g,id,now)=> {
 const fromMap=g.careUpdatedAtByPuppy?.[id];
 const fromSave=g.lastActiveAt;
 const n=Number.isSafeInteger(fromMap)&&fromMap>0?fromMap:Number.isSafeInteger(fromSave)&&fromSave>0?fromSave:now;
 return Math.min(now,n);
};
export function applyOfflineCare(g,now=Date.now()){
 if(!Number.isFinite(now)||now<=0)return;
 const id=g.puppyStyle,then=lastFor(g,id,now);
 const elapsed=Math.max(0,Math.floor((now-then)/60000));
 const original=Object.fromEntries(FIELDS.map(k=>[k,g[k]]));
 const next=decayCareProfile(original,elapsed);
 for(const field of FIELDS)g[field]=next[field];
 if(!g.careProfiles||typeof g.careProfiles!=='object')g.careProfiles={};
 if(!g.careUpdatedAtByPuppy||typeof g.careUpdatedAtByPuppy!=='object')g.careUpdatedAtByPuppy={};
 g.careProfiles[id]=next;g.careUpdatedAtByPuppy[id]=now;g.lastActiveAt=now;
}
export function switchCareProfile(g,id,now=Date.now()){
 if(id===g.puppyStyle||!g.unlockedPuppies?.includes(id))return false;
 // Update the outgoing puppy independently before switching.
 applyOfflineCare(g,now);
 const then=lastFor(g,id,now);
 const snapshot=g.careProfiles?.[id]||{happiness:100,fullness:100,energy:100,cleanliness:100,bond:10};
 g.careProfiles[id]=decayCareProfile(snapshot,Math.floor(Math.max(0,now-then)/60000));
 const ok=switchPuppy(g,id);
 if(ok){g.careUpdatedAtByPuppy[id]=now;g.lastActiveAt=now;}
 return ok;
}
