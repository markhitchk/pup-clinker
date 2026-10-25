// Part 3: Kotlin V6Shop/PuppyPupCoinShop/V6PrestigeCenter presentation
// and guest-only transaction guards. Source: PuppyClickerV6Activity.kt,
// PuppyMainScreensRevamp.kt, PuppyEconomyV7.kt, PuppyClickerV6ViewModel.kt.
import {MAX_TICKETS_PER_RARITY,UPGRADES,RARITIES,accessoryPrices,cost,boneCost,ticketCost,ticketShopCost,prestigePointsAvailable,skillCost} from './game.js';

export const TICKET_RARITY_PRESENTATION=Object.freeze({
 COMMON:{emoji:'🎟️',name:'Common'},
 UNCOMMON:{emoji:'🟢',name:'Uncommon'},
 RARE:{emoji:'🔵',name:'Rare'},
 EPIC:{emoji:'🟣',name:'Epic'},
 LEGENDARY:{emoji:'🟡',name:'Legendary'}
});
export const PRESTIGE_SKILL_PRESENTATION=Object.freeze({
 TAP_TRAINING:{emoji:'👆',title:'Tap Training',description:'Each CLICK upgrade bought in the Shop gains +1 extra tap power per skill level.'},
 AUTO_TRAINING:{emoji:'⚡',title:'Active Training',description:'Each ACTIVE upgrade bought in the Shop gains +1 extra Treat in the 10-tap active bonus per skill level.'},
 SMART_SHOPPER:{emoji:'🛍️',title:'Smart Shopper',description:'Shop treat prices are 5% cheaper per skill level.'},
 TICKET_SENSE:{emoji:'🎟️',title:'Ticket Sense',description:'At levels 2 and 4, matching-ticket upgrade requirements drop by 1 (minimum 1).'}
});

export const upgradesForShopTab=tab=>{
 if(tab==='upgrades')return UPGRADES.filter(u=>!u.rarity);
 if(tab==='tickets')return UPGRADES.filter(u=>Boolean(u.rarity));
 return [];
};
export function upgradeOffer(g,upgradeId){
 const u=UPGRADES.find(x=>x.id===upgradeId);
 if(!u)return null;
 const treats=cost(g,u),bones=boneCost(u),tickets=ticketCost(g,u),owned=g.upgrades[u.id]||0;
 const inStock=!u.rarity||(g.ticketInventory[u.rarity]||0)>=tickets;
 const skill=g.prestigeSkills?.[u.effect==='CLICK'?'TAP_TRAINING':'AUTO_TRAINING']||0;
 return {upgrade:u,owned,treats,bones,tickets,skill,actualAmount:u.amount+skill,
  canBuy:inStock&&g.treats>=treats&&g.bones>=bones&&owned<1000};
}
export function ticketShopOffer(g,rarity){
 if(!RARITIES.includes(rarity))return null;
 const owned=g.ticketInventory[rarity]||0;
 const price=ticketShopCost(rarity,g.ticketShopPurchases[rarity]||0);
 return {rarity,owned,price,canBuy:owned<MAX_TICKETS_PER_RARITY&&g.pupCoins>=price};
}
export function accessoryOffer(g,name){
 if(name==='None')return {name,owned:true,equipped:g.accessory==='None',price:null,canBuy:g.accessory!=='None'};
 const price=accessoryPrices[name];
 if(price===undefined)return null;
 const owned=g.ownedAccessories.includes(name);
 return {name,price,owned,equipped:g.accessory===name,canBuy:(owned||g.pupCoins>=price)&&g.accessory!==name};
}
export function prestigeOffer(g,activeCasinoRound=false){
 const points=prestigePointsAvailable(g);
 return {points,remaining:Math.max(0,50000-g.lifetimeTreats),canPrestige:points>0&&!activeCasinoRound,
  progress:Math.min(100,Math.max(0,g.lifetimeTreats/50000*100))};
}
export const canBuySkill=(g,skill)=>{
 const level=g.prestigeSkills[skill]||0;
 return Object.hasOwn(PRESTIGE_SKILL_PRESENTATION,skill)&&level<5&&g.skillPoints>=skillCost(g,skill);
};
