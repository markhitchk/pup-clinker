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
 g.ticketInventory=Object.fromEntries(RARITIES.map(r=>[r,number('upgrade_ticket_'+r.toLowerCase())]));
 g.ticketShopPurchases=Object.fromEntries(RARITIES.map(r=>[r,number('ticket_shop_purchase_'+r.toLowerCase()+'_v7')]));
 g.prestigeCount=number('prestige_count_v6');
 g.skillPoints=number('prestige_skill_points_v6');
 g.totalPrestigePointsEarned=number('prestige_total_points_v6');
 g.prestigeSkills=Object.fromEntries(PRESTIGE_SKILLS.map(s=>[s,number('prestige_skill_'+s.toLowerCase()+'_v6')]));
 g.xp=number('player_xp_v1');
 g.parkActive=values.park_active===true;
 const active=g.puppyStyle;
 const careFields=[['happiness','happiness'],['fullness','fullness'],['energy','energy'],['cleanliness','cleanliness_v5'],['bond','bond_v5']];
 for(const id of g.unlockedPuppies){
  const prefix='care_profile_v1_'+id+'_';
  if(!Number.isSafeInteger(values[prefix+'updated_at']))continue;
  g.careProfiles[id]=Object.fromEntries(careFields.map(([field,fallback])=>[field,number(prefix+field,number(fallback,field==='bond'?10:100))]));
  g.careUpdatedAtByPuppy[id]=number(prefix+'updated_at');
 }
 if(g.careProfiles[active])Object.assign(g,g.careProfiles[active]);
 g.parkReadyAtMs=number('park_ready_at');
 g.dailyStreak=number('daily_streak_v5');
 g.lastDailyClaimDay=number('daily_claim_day',0)||null;
 g.achievementRewardedIds=set('achievement_rewarded_v1');
 g.dailyDay=number('daily_day_v5');
 g.dailyTapStart=number('daily_tap_start_v5');
 g.dailyCareStart=number('daily_care_start_v5');
 g.dailyShopStart=number('daily_shop_start_v5');
 g.claimedDailyTasks=set('daily_tasks_v5');
 g.cooldownUntilMs=number('fair_play_cooldown_until');
 g.parkActive=values.park_active===true;
 const cloudRound=string('beta_casino_round_v1');
 const previousResult=string('beta_last_casino_result_v1');
 const parseRecord=value=>{try{const r=JSON.parse(value);return r&&typeof r==='object'&&!Array.isArray(r)?r:null;}catch{return null;}};
 const pending=parseRecord(cloudRound),last=parseRecord(previousResult);
 // React's Blackjack layout requires a 52-card structural deck. Construct
 // dummy, presentation-only remaining cards, never the server's real shoe.
 const publicBlackjackState=round=>{
  if(!round||!Array.isArray(round.hands)||!Array.isArray(round.dealerCards))return null;
  const seen=new Set([...round.dealerCards,...round.hands.flatMap(h=>h.cards||[])]);
  const extra=round.complete?[]:[Array.from({length:52},(_,i)=>i).find(i=>!seen.has(i))];
  if(extra.some(v=>v===undefined))return null;
  const dealer=[...round.dealerCards,...extra];
  const used=new Set([...dealer,...round.hands.flatMap(h=>h.cards||[])]);
  const remainingDeck=Array.from({length:52},(_,i)=>i).filter(i=>!used.has(i));
  return {...round,dealerCards:dealer,remainingDeck};
 };
 const lastBlackjack=parseRecord(string('beta_last_blackjack_result_v1'));
 if(pending){
  if(pending.type==='BLACKJACK')g.casinoRound=publicBlackjackState(pending);
  if(pending.game==='SLOTS')g.pendingSlots=pending;
  if(pending.game==='ROULETTE')g.pendingRoulette=pending;
  if(pending.game==='PLINKO')g.pendingPlinko=pending;
  if(pending.type==='SCRATCHERS')g.pendingScratch=pending;
  if(pending.type==='LUCKY_WHEEL')g.pendingWheel=pending;
 }
  g.redeemedCodeIds=set('redeemed_code_ids');
 g.onboardingComplete=true;
 g.onboardingStep=5;
 // Internal legacy presentation field only; it is never a guest identity.
 g.guestName=typeof username==='string'&&username?username:'Pup Account';
 // Suppress mutating offline-care restoration during read-only projection.
 g.lastActiveAt=Date.now();
 const projected=sanitizeGame(g);
 projected.betaLastCasinoResult=last;
 projected.betaBlackjackDisplay=lastBlackjack?.state?{...lastBlackjack,state:publicBlackjackState(lastBlackjack.state)}:null;
 return projected;
}
