import {useEffect,useRef,useState} from 'react';
import {AppState} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
 ROSTER_INDEX_URL,ROSTER_ASSET_URL,ROSTER_CACHE_KEY,ROSTER_REFRESH_MS,ROSTER_RETRY_MS,
 ROSTER_MAX_REMOTE,ROSTER_MANIFEST_MAX_BYTES,utf8WithinLimit,parseRosterIndex,parseRosterManifest,
 parseCachedRoster,packRosterCache,mergeRoster,categoriesForRoster
} from '../engine/dynamicRoster.js';

export function useRoster(){
 const controls=useRef({});
 const [refreshing,setRefreshing]=useState(false),[status,setStatus]=useState('Bundled artwork available offline.');
 const [snapshot,setSnapshot]=useState(()=>({
  items:mergeRoster(),source:'bundled',updatedAt:null,categories:categoriesForRoster(mergeRoster())
 }));
 useEffect(()=>{
  let alive=true,loading=false,lastAttempt=0,lastSuccess=0;
  const update=(remote,source)=>{if(!alive)return;
   const items=mergeRoster(remote);
   setSnapshot({items,source,updatedAt:Date.now(),categories:categoriesForRoster(items)});
  };
  const getText=async (url,maxBytes)=>{
   const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),12000);
   try{
    const response=await fetch(url,{signal:controller.signal,headers:{Accept:'application/json'}});
    if(!response.ok)throw new Error('Roster HTTP '+response.status);
    const text=await response.text();
    if(!utf8WithinLimit(text,maxBytes))throw new Error('Roster file too large');
    return text;
   }finally{clearTimeout(timeout);}
  };
  const refresh=async(force=false)=>{
   const now=Date.now();
   if(!alive||loading||(!force&&(now-lastSuccess<ROSTER_REFRESH_MS||now-lastAttempt<ROSTER_RETRY_MS)))return;
   loading=true;lastAttempt=now;setRefreshing(true);setStatus('Refreshing puppy assets…');
   try{
    const folders=parseRosterIndex(await getText(ROSTER_INDEX_URL,512*1024));
    let assets=[];
    for(const folder of folders){
     try{
      const entries=parseRosterManifest(folder,await getText(ROSTER_ASSET_URL+'/'+folder+'/manifest.json',ROSTER_MANIFEST_MAX_BYTES));
      if(assets.length+entries.length>ROSTER_MAX_REMOTE)throw new Error('Roster capacity exceeded');
      assets=assets.concat(entries);
     }catch{/* One invalid/unavailable folder cannot hide the rest. */}
    }
    // Reject cross-folder ID collisions rather than letting a remote manifest replace ownership.
    const unique=new Set(assets.map(a=>a.id));
    if(unique.size!==assets.length)throw new Error('Duplicate cross-folder puppy id');
    const json=packRosterCache(assets);
    await AsyncStorage.setItem(ROSTER_CACHE_KEY,json);
    lastSuccess=Date.now();
    update(assets,'live');setStatus('Assets refreshed successfully.');
   }catch{if(alive)setStatus('Could not refresh online assets. Using cached or bundled artwork.');}
   finally{loading=false;if(alive)setRefreshing(false);}
  };
  const init=async()=>{
   try{
    const cached=await AsyncStorage.getItem(ROSTER_CACHE_KEY);
    if(cached)update(parseCachedRoster(cached),'cached');
   }catch{/* Corrupt cache falls back to bundled registry. */}
   void refresh();
  };
  controls.current={refresh:()=>refresh(true),clearCache:async()=>{if(loading)return;try{await AsyncStorage.removeItem(ROSTER_CACHE_KEY);update([],'bundled');lastAttempt=0;lastSuccess=0;setStatus('Roster metadata cache cleared. Bundled artwork remains available.');}catch{if(alive)setStatus('Unable to clear roster cache.');}}};
  void init();
  const timer=setInterval(()=>void refresh(),ROSTER_REFRESH_MS);
  const listener=AppState.addEventListener('change',status=>{if(status==='active')void refresh();});
  return ()=>{alive=false;clearInterval(timer);listener.remove();};
 },[]);
 return {...snapshot,refreshing,status,refresh:()=>controls.current.refresh?.(),clearCache:()=>controls.current.clearCache?.()};
}
