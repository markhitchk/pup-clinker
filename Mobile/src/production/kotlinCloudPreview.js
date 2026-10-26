import {freshGame,sanitizeGame,UPGRADES,RARITIES,PRESTIGE_SKILLS} from '../engine/game.js';

// Read-only compatibility projection. It must never be uploaded as a Kotlin
// Pup Account save: unknown Kotlin fields, PupEye signatures, and entitlement
// records must remain authoritative on the server.
export function projectKotlinCloudSave(raw,username=''){
 if(typeof raw!=='string'||raw.length>1100000)throw new Error('INVALID_CLOUD_SAVE');
 let document;
 try{document=JSON.parse(raw);}catch{throw new Error('INVALID_CLOUD_SAVE_JSON');}
 if(document?.format!=='puppy-clicker-cloud-save'||document?.version!==1)
  throw new Error('UNSUPPORTED_CLOUD_SAVE_FORMAT');
 const store=document.stores?.puppy_clicker_save;
 const values=store?.values;
 if(!values||typeof values!=='object'||Array.isArray(values))
  throw new Error('MISSING_KOTLIN_PROGRESS');
 const g=freshGame();
 const number=(key,fallback=0)=>Number.isSafeInteger(values[key])&&values[key]>=0?values[key]:fallback;
 const string=(key,fallback='')=>typeof values[key]==='string'?values[key]:fallback;
 const set=(key,fallback=[])=>Array.isArray(values[key])?values[key].filter(x=>typeof x==='string'):fallback;
 g.puppyStyle=string('puppy_style','classic');
 g.puppyName=string('puppy_name','Buddy');
 g.puppyNames={[g.puppyStyle]:g.puppyName};
 for(const [key,val] of Object.entries(values))
  if(key.startsWith('puppy_name_for_')&&typeof val==='string')
   g.puppyNames[key.slice('puppy_name_for_'.length)]=val;
 g.unlockedPuppies=set('unlocked_puppies',g.unlockedPuppies);
 g.accessory=string('accessory','None');
 g.ownedAccessories=set('owned_accessories_v7',[]);
 g.treats=number('treats');
 g.lifetimeTreats=number('lifetime_treats');
 g.bones=number('bones_v7');
 g.pupCoins=number('pup_coins_v7');
 g.casinoChips=number('casino_chips_v7');
 g.totalTaps=number('total_taps');
 g.legitimateTaps=number('legitimate_taps_v7',g.totalTaps);
 g.bestCombo=number('best_combo');
 g.careActions=number('care_actions_v5');
 g.totalShopPurchases=number('total_shop_purchases_v5');
 g.totalTicketsFound=number('total_tickets_found');
 g.happiness=number('happiness',100);
 g.fullness=number('fullness',100);
 g.energy=number('energy',100);
 g.cleanliness=number('cleanliness_v5',100);
 g.bond=number('bond_v5',10);
 g.upgrades=Object.fromEntries(UPGRADES.map(u=>[u.id,number('upgrade_'+u.id)]));
 g.ticketInventory=Object.fromEntries(RARITIES.map(r=>[r,number('ticket_'+r.toLowerCase())]));
 g.ticketShopPurchases=Object.fromEntries(RARITIES.map(r=>[r,number('ticket_shop_'+r.toLowerCase())]));
 g.prestigeCount=number('prestige_count_v6');
 g.skillPoints=number('prestige_skill_points_v6');
 g.totalPrestigePointsEarned=number('prestige_total_points_v6');
 g.prestigeSkills=Object.fromEntries(PRESTIGE_SKILLS.map(s=>[s,number('prestige_skill_'+s.toLowerCase())]));
 g.parkActive=values.park_active===true;
 g.parkReadyAtMs=number('park_ready_at');
 g.dailyStreak=number('daily_streak_v5');
 g.lastDailyClaimDay=number('daily_claim_day',0)||null;
 g.redeemedCodeIds=set('redeemed_code_ids');
 g.onboardingComplete=true;
 g.onboardingStep=5;
 // Internal legacy presentation field only; it is never a guest identity.
 g.guestName=typeof username==='string'&&username?username:'Pup Account';
 // Suppress mutating offline-care restoration during read-only projection.
 g.lastActiveAt=Date.now();
 return sanitizeGame(g);
}
