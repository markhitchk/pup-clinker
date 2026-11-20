import {puppyWeights} from '../supabase/functions/_shared/puppy-rarity.ts';
import {CASINO_PUPPY_POOL} from '../supabase/functions/_shared/beta-casino-rewards.ts';
export const rarityLabels={common:'⚪ Common',uncommon:'🟢 Uncommon',rare:'🔵 Rare',epic:'🟣 Epic',legendary:'🟡 Legendary',unobtainable:'🔒 Unobtainable'};
export const rarityOrder=Object.keys(rarityLabels);
export const unlockText=row=>{
 if(!row)return 'Ownership retained';
 if(row.rarity==='unobtainable')return 'Unavailable through gameplay';
 const sources={event:'Seasonal event',code:'Puppy Code',community:'Community reward'};
 if(sources[row.acquisition])return sources[row.acquisition];
 if(['classic','golden','spotty','poodle'].includes(row.puppy_id))return 'Starter puppy';
 if(['test_v1_crash_dummy','test_v2_crash_dummy'].includes(row.puppy_id))return 'Free test puppy';
 return CASINO_PUPPY_POOL.includes(row.puppy_id)?'Casino puppy reward':'No standard reward currently available';
};
export const transferText=row=>!row?'':row.bound?'Account-bound':[
 row.giftable?'Giftable':'Gifting restricted',row.tradeable?'Tradeable':'Trading restricted'
].join(' · ');
export function collectionStats(rows,owned){
 const count=items=>items.filter(p=>owned.includes(p.puppy_id)).length;
 const regular=rows.filter(p=>p.rarity!=='unobtainable');
 const special=rows.filter(p=>p.rarity==='unobtainable');
 return (regular.length?Math.round(100*count(regular)/regular.length)+'% complete':'No collectible puppies')+
  ' · '+count(regular)+' / '+regular.length+' collectible · '+count(special)+' / '+special.length+' Unobtainable';
}
export function poolOdds(pool){
 const present=Object.entries(puppyWeights).filter(([tier])=>pool.some(p=>p.rarity===tier));
 const total=present.reduce((sum,[,w])=>sum+w,0);
 return present.map(([tier,w])=>rarityLabels[tier]+' '+Number((100*w/total).toFixed(1))+'%').join(' · ');
}
