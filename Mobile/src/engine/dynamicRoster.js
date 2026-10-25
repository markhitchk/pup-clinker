// Data-only, readonly port of DynamicPuppyRoster.kt. This module NEVER grants puppy
// ownership; protected unlocks remain controlled by the Kotlin-backed server.
import {ROSTER} from './game.js';
export const ROSTER_INDEX_URL='https://api.github.com/repos/markhitchk/pup-clinker/contents/assets?ref=main';
export const ROSTER_ASSET_URL='https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets';
export const ROSTER_CACHE_KEY='puppy-clicker:roster:read-only:v1';
export const ROSTER_REFRESH_MS=60*60*1000;
export const ROSTER_RETRY_MS=5*60*1000;
export const ROSTER_INDEX_MAX_BYTES=512*1024;
export const ROSTER_MANIFEST_MAX_BYTES=256*1024;
export const ROSTER_MAX_REMOTE=500;
const folders=new Set(['v2','test','discord_rewards']);
const validFolder=s=>folders.has(s)||/^v[3-9][0-9]*$/.test(s);
const folderFetchOrder=folder=>folder==='test'?2147483647:folder==='discord_rewards'?2147483646:Number(folder.slice(1));
const validId=s=>typeof s==='string'&&/^[a-z0-9_]{1,64}$/.test(s);
const validFile=s=>typeof s==='string'&&/^[A-Za-z0-9._-]{1,128}\.png$/.test(s);
const groupTitle=(folder,root)=>{
 if(folder==='test')return 'Test Puppies';
 if(folder==='discord_rewards')return 'Discord Rewards';
 if(typeof root.roster==='string'&&root.roster.trim())return root.roster.trim().toUpperCase()+' Puppies';
 return folder.toUpperCase()+' Puppies';
};
const groupOrder=folder=>folder==='test'?2147483647:folder==='discord_rewards'?3:Number(folder.slice(1));
const trim=(v,max)=>typeof v==='string'?v.trim().slice(0,max):'';
export function utf8WithinLimit(text,max){
 if(typeof text!=='string')return false;
 let size=0;
 for(const c of text){
  const code=c.codePointAt(0);
  size+=code<=0x7f?1:code<=0x7ff?2:code<=0xffff?3:4;
  if(size>max)return false;
 }
 return true;
}
const safeSize=utf8WithinLimit;
const prefix=folder=>folder==='test'?'test_':folder==='discord_rewards'?'v2_':folder+'_';
export function parseRosterIndex(raw){
 if(!safeSize(raw,ROSTER_INDEX_MAX_BYTES))throw new Error('Roster index size exceeds limit');
 const data=JSON.parse(raw);
 if(!Array.isArray(data))throw new Error('Roster directory must be a list');
 const out=new Set();
 for(const entry of data){
  if(entry&&entry.type==='dir'&&validFolder(entry.name))out.add(entry.name);
 }
 return [...out].sort((a,b)=>folderFetchOrder(a)-folderFetchOrder(b)||a.localeCompare(b));
}
export function parseRosterManifest(folder,raw){
 if(!validFolder(folder)||!safeSize(raw,ROSTER_MANIFEST_MAX_BYTES))throw new Error('Invalid roster folder or size');
 const root=JSON.parse(raw);
 if(!Array.isArray(root.puppies)||root.puppies.length>ROSTER_MAX_REMOTE)throw new Error('Invalid roster manifest');
 const seen=new Set(),result=[],required=prefix(folder);
 const title=groupTitle(folder,root);
 for(const item of root.puppies){
  if(!item||typeof item!=='object'||Array.isArray(item))throw new Error('Invalid puppy item');
  const id=typeof item.asset_id==='string'?item.asset_id.trim():'';
  const fileName=typeof item.file==='string'?item.file.trim():'';
  if(!validId(id)||!id.startsWith(required)||seen.has(id))throw new Error('Invalid or duplicate asset id');
  if(!validFile(fileName))throw new Error('Invalid PNG filename');
  seen.add(id);
  const free=typeof item.free==='boolean'?item.free:String(root.ownership||'').toLowerCase()==='free';
  const fallback=id.slice(required.length).split('_').filter(Boolean).map(w=>w[0].toUpperCase()+w.slice(1)).join(' ');
  const name=trim(item.name,81)||fallback;
  if(!name||name.length>80||typeof item.description==='string'&&item.description.trim().length>240)throw new Error('Invalid puppy description');
  const emoji=trim(item.emoji,8)||(folder==='test'?'🧪':folder==='discord_rewards'?'💬':'🐾');
  const description=trim(item.description,240)||
   (folder==='test'?'Free test roster puppy.':folder==='discord_rewards'?'Discord-authenticated Pup Member reward.':title.replace(/ Puppies$/,'')+' roster puppy.');
  result.push({id,name,emoji,description,free,redeemOnly:free?false:item.redeem_only!==false,
   groupId:folder,groupTitle:title,groupOrder:groupOrder(folder),
   assetId:id,fileName,folder,number:Number.isSafeInteger(item.number)?item.number:null,
   image:ROSTER_ASSET_URL+'/'+folder+'/'+fileName,remote:true});
 }
 return result;
}
export function validateRemoteRoster(remote){
 if(!Array.isArray(remote)||remote.length>ROSTER_MAX_REMOTE)throw new Error('Invalid remote roster length');
 const seen=new Set();
 for(const item of remote){
  if(!item||!validFolder(item.folder)||!validId(item.id)||item.id!==item.assetId||
    !item.id.startsWith(prefix(item.folder))||!validFile(item.fileName)||
    typeof item.name!=='string'||!item.name||item.name.length>80||
    typeof item.free!=='boolean'||typeof item.redeemOnly!=='boolean'||
    seen.has(item.id))throw new Error('Invalid cached roster item');
  if(item.image!==ROSTER_ASSET_URL+'/'+item.folder+'/'+item.fileName)throw new Error('Unsafe image URL');
  seen.add(item.id);
 }
 return remote;
}
export function parseCachedRoster(raw){
 if(!safeSize(raw,ROSTER_INDEX_MAX_BYTES))throw new Error('Oversized roster cache');
 const v=JSON.parse(raw);
 if(v?.schema!==1)throw new Error('Unsupported roster cache schema');
 return validateRemoteRoster(v.assets);
}
export function packRosterCache(remote){return JSON.stringify({schema:1,assets:validateRemoteRoster(remote)});}
export function mergeRoster(remote=[]){
 // Remote may improve metadata, but it must not change local guest account ownership.
 const base=new Map(ROSTER.map((item,index)=>{
  const knownFree=['classic','golden','poodle','spotty'].includes(item.id)||item.id.startsWith('test_');
  const groupId=item.groupId||(item.id.startsWith('v2_')?(item.id==='v2_discord_pup'?'discord_rewards':'v2'):'v1');
  return [item.id,{
   ...item,free:knownFree,redeemOnly:!knownFree,
   groupId,groupTitle:item.groupTitle||(groupId==='discord_rewards'?'Discord Rewards':groupId==='v2'?'V2 Puppies':'V1 Puppies'),
   groupOrder:item.groupOrder||groupOrder(groupId),number:item.number||index+1
  }];
 }));
 for(const item of validateRemoteRoster(remote)){
  const existing=base.get(item.id);
  base.set(item.id,{...existing,...item,number:item.number||existing?.number||base.size+1});
 }
 return [...base.values()];
}
export function categoriesForRoster(items){
 const groups=new Map();
 for(const p of items)if(!groups.has(p.groupId))groups.set(p.groupId,{id:p.groupId,title:p.groupTitle,order:p.groupOrder});
 return [...groups.values()].sort((a,b)=>a.order-b.order||a.id.localeCompare(b.id));
}
