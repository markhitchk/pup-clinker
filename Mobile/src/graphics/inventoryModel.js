import {ROSTER,UPGRADES,RARITIES} from '../engine/game.js';

// Read-only projection of the exact game state displayed by the signed PupEye
// account flow. Never awards items, changes account authority, or writes saves.
const safeInt=value=>Number.isFinite(Number(value))?Math.max(0,Math.trunc(Number(value))):0;
const distinct=values=>[...new Set(Array.isArray(values)?values.filter(value=>typeof value==='string'&&value.trim()):[])];
const WALLET=Object.freeze([
 ['🍪','Treats','treats'],['🦴','Bones','bones'],
 ['🪙','Pup Coins','pupCoins'],['🐾','Casino Chips','casinoChips']
]);

export function inventorySnapshot(game={}){
 const g=game||{};
 const ids=distinct(g.unlockedPuppies);
 const catalog=new Map(ROSTER.map(pup=>[pup.id,pup]));
 const puppies=ids.map(id=>{
  const pup=catalog.get(id);
  return {id,name:g.puppyNames?.[id]||(id===g.puppyStyle?g.puppyName:null)||pup?.name||id.replaceAll('_',' '),
   originalName:pup?.name||'Owned puppy',known:Boolean(pup),
   current:id===g.puppyStyle,favorite:Array.isArray(g.favoritePuppies)&&g.favoritePuppies.includes(id)};
 });
 const accessories=distinct(g.ownedAccessories).filter(name=>name!=='None')
  .map(name=>({name,equipped:name===g.accessory}));
 const tickets=RARITIES.map(rarity=>({rarity,quantity:safeInt(g.ticketInventory?.[rarity])}));
 const upgrades=UPGRADES.filter(upgrade=>safeInt(g.upgrades?.[upgrade.id])>0)
  .map(upgrade=>({id:upgrade.id,name:upgrade.name,emoji:upgrade.emoji,
   description:upgrade.description,level:safeInt(g.upgrades?.[upgrade.id])}));
 const wallet=WALLET.map(([emoji,label,key])=>({key,emoji,label,value:safeInt(g[key])}));
 return {puppies,accessories,tickets,upgrades,wallet,counts:{
  puppies:puppies.length,accessories:accessories.length,
  tickets:tickets.reduce((sum,ticket)=>sum+ticket.quantity,0),
  upgrades:upgrades.length,wallet:wallet.length
 }};
}
export const inventoryCategories=Object.freeze([
 ['puppies','🐶','Puppies'],['accessories','🎀','Accessories'],
 ['tickets','🎟️','Tickets'],['upgrades','⬆️','Upgrades'],['wallet','🍪','Wallet']
]);
