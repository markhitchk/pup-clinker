// Source parity: PuppyGachaEngine (PuppyGacha.kt) and PuppyCatalogV6.kt.
// Only the first four baseline puppies are non-redeemOnly in Kotlin;
// owner/developer/role/event-only skins must NEVER be randomly granted.
import {ROSTER} from './game.js';

const FREE_IDS=new Set(['classic','golden','poodle','spotty']);
export const GACHA_TREAT_COST=2500;
export const GACHA_COMMON_TICKET_COST=1;
export const eligibleGachaPuppies=(roster=ROSTER)=>[...new Map(roster.filter(p=>FREE_IDS.has(p.id)).map(p=>[p.id,p])).values()].sort((a,b)=>a.id.localeCompare(b.id,'en'));
export function gachaPool(roster,unlocked=[]){
 const pool=eligibleGachaPuppies(roster);
 const owned=new Set(unlocked);
 const missing=pool.filter(p=>!owned.has(p.id));
 return missing.length?missing:pool.filter(p=>owned.has(p.id));
}
export function applyGacha(g,payment,roll,roster=ROSTER){
 if(!['TREATS','COMMON_TICKET'].includes(payment))return {success:false,failure:'INVALID_PAYMENT'};
 const pool=gachaPool(roster,g.unlockedPuppies);
 if(!pool.length)return {success:false,failure:'NO_ELIGIBLE_PUPPIES'};
 if(payment==='TREATS'&&g.treats<GACHA_TREAT_COST)return {success:false,failure:'NOT_ENOUGH_TREATS'};
 if(payment==='COMMON_TICKET'&&(g.ticketInventory?.COMMON||0)<GACHA_COMMON_TICKET_COST)return {success:false,failure:'NOT_ENOUGH_TICKETS'};
 if(!Number.isSafeInteger(roll))return {success:false,failure:'INVALID_ROLL'};
 const selected=pool[((roll%pool.length)+pool.length)%pool.length];
 const isNewUnlock=!g.unlockedPuppies.includes(selected.id);
 if(payment==='TREATS')g.treats-=GACHA_TREAT_COST;
 else g.ticketInventory.COMMON-=GACHA_COMMON_TICKET_COST;
 if(isNewUnlock)g.unlockedPuppies.push(selected.id);
 g.gachaPulls=(g.gachaPulls||0)+1;
 return {success:true,puppyId:selected.id,puppyName:selected.name,payment,isNewUnlock};
}
