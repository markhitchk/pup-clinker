import {ROSTER,UPGRADES,RARITIES} from '../engine/game.js';

// Read-only presentation of server-verified gameplay. This module never
// invents an entitlement, mutates a save, or writes to Supabase.
const count=value=>Number.isSafeInteger(value)&&value>=0?value:0;
const uniqueStrings=value=>Array.isArray(value)
 ?[...new Set(value.filter(item=>typeof item==='string'&&item.length>0&&item.length<=100))]:[];

export const INVENTORY_CATEGORIES=Object.freeze([
 {id:'puppies',icon:'🐶',label:'Puppies'},
 {id:'accessories',icon:'🎀',label:'Accessories'},
 {id:'tickets',icon:'🎟️',label:'Tickets'},
 {id:'upgrades',icon:'⬆️',label:'Upgrades'},
 {id:'wallet',icon:'🍪',label:'Wallet'},
]);
export const INVENTORY_WALLET=Object.freeze([
 {key:'treats',icon:'🍪',label:'Treats'},
 {key:'bones',icon:'🦴',label:'Bones'},
 {key:'pupCoins',icon:'🪙',label:'Pup Coins'},
 {key:'casinoChips',icon:'🐾',label:'Casino Chips'},
]);

export function buildInventoryView(game){
 const g=game&&typeof game==='object'?game:{};
 const ids=uniqueStrings(g.unlockedPuppies);
 const rosterById=new Map(ROSTER.map(pup=>[pup.id,pup]));
 const puppies=ids.map(id=>{
  const pup=rosterById.get(id);
  const name=typeof g.puppyNames?.[id]==='string'&&g.puppyNames[id].trim()
   ?g.puppyNames[id].trim().slice(0,40)
   :id===g.puppyStyle&&typeof g.puppyName==='string'&&g.puppyName.trim()
    ?g.puppyName.trim().slice(0,40):pup?.name||id.replaceAll('_',' ');
  return {id,name,baseName:pup?.name||'Owned puppy',known:!!pup,
   current:id===g.puppyStyle,favorite:Array.isArray(g.favoritePuppies)&&g.favoritePuppies.includes(id)};
 });
 const accessories=uniqueStrings(g.ownedAccessories).filter(name=>name!=='None')
  .map(name=>({name,equipped:g.accessory===name}));
 const tickets=RARITIES.map(rarity=>({rarity,quantity:count(g.ticketInventory?.[rarity])}));
 const upgrades=UPGRADES.filter(item=>count(g.upgrades?.[item.id])>0)
  .map(item=>({id:item.id,name:item.name,description:item.description,icon:item.emoji,
   level:count(g.upgrades[item.id])}));
 const wallet=INVENTORY_WALLET.map(item=>({...item,amount:count(g[item.key])}));
 return {puppies,accessories,tickets,upgrades,wallet,counts:{
  puppies:puppies.length,accessories:accessories.length,
  tickets:tickets.reduce((total,item)=>total+item.quantity,0),
  upgrades:upgrades.length,wallet:wallet.length,
 }};
}
export function filterInventoryPuppies(puppies,query){
 const text=String(query||'').trim().toLocaleLowerCase();
 return !text?puppies:puppies.filter(puppy=>
  [puppy.name,puppy.baseName,puppy.id].some(value=>value.toLocaleLowerCase().includes(text)));
}
