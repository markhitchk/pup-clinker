// Read-only inventory view model based on the existing website's InventoryScreen.jsx.
// Data comes from the signed Pup Account projection already loaded by useGame.
import {ROSTER,UPGRADES,RARITIES} from '../engine/game.js';
import {WEBSITE_INVENTORY_CURRENCIES,WEBSITE_INVENTORY_ACCESSORYICONS,
 WEBSITE_INVENTORY_RARITYICONS,WEBSITE_INVENTORY_SECTIONS} from '../production/websiteInventoryContract.js';

// All category labels/icons are generated from the website InventoryScreen.jsx.
export const INVENTORY_CATEGORIES=Object.freeze(WEBSITE_INVENTORY_SECTIONS.map(
 ([id,icon,label])=>Object.freeze({id,icon,label})));
export const INVENTORY_CURRENCIES=Object.freeze(WEBSITE_INVENTORY_CURRENCIES.map(
 ([icon,label,key])=>Object.freeze({icon,label,key})));
export const INVENTORY_ACCESSORY_ICONS=WEBSITE_INVENTORY_ACCESSORYICONS;
export const INVENTORY_RARITY_ICONS=WEBSITE_INVENTORY_RARITYICONS;
export const inventoryCount=value=>{
 const number=Number(value);
 return Number.isFinite(number)?Math.max(0,Math.trunc(number)):0;
};
export const inventoryNumber=value=>inventoryCount(value).toLocaleString('en-US');
const array=value=>Array.isArray(value)?value:[];
const unique=value=>[...new Set(array(value).filter(x=>typeof x==='string'))];

export function buildInventoryModel(game,query=''){
 if(!game||typeof game!=='object')return null;
 const ownedIds=unique(game.unlockedPuppies);
 const knownById=new Map(ROSTER.map(p=>[p.id,p]));
 const search=String(query||'').trim().toLocaleLowerCase();
 const puppies=ownedIds.map(id=>{
  const source=knownById.get(id);
  const originalName=source?.name||id.replaceAll('_',' ');
  const name=game.puppyNames?.[id]||(id===game.puppyStyle?game.puppyName:null)||originalName;
  return {id,name,originalName,known:Boolean(source),
   current:id===game.puppyStyle,
   favorite:array(game.favoritePuppies).includes(id),
   group:id.startsWith('v2_')?'V2':id.startsWith('test_')?'Test':'V1'};
 }).filter(item=>!search||[item.id,item.name,item.originalName].some(x=>String(x).toLocaleLowerCase().includes(search)));
 const accessories=unique(game.ownedAccessories).filter(name=>name!=='None').map(name=>({
  id:name,name,icon:INVENTORY_ACCESSORY_ICONS[name]||'🎁',equipped:game.accessory===name
 }));
 const tickets=RARITIES.map(rarity=>({rarity,count:inventoryCount(game.ticketInventory?.[rarity]),
  icon:INVENTORY_RARITY_ICONS[rarity]||'🎟️'}));
 const upgrades=UPGRADES.filter(item=>inventoryCount(game.upgrades?.[item.id])>0)
  .map(item=>({...item,count:inventoryCount(game.upgrades?.[item.id])}));
 const wallet=INVENTORY_CURRENCIES.map(item=>({...item,amount:inventoryCount(game[item.key])}));
 return {
  puppies,ownedIds,accessories,tickets,upgrades,wallet,
  counts:{
   puppies:ownedIds.length,
   accessories:accessories.length,
   tickets:tickets.reduce((total,item)=>total+item.count,0),
   upgrades:upgrades.length,wallet:wallet.length
  }
 };
}
