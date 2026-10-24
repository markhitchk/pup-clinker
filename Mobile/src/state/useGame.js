import {useCallback,useEffect,useReducer,useRef} from 'react';
import {AppState} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {initialState,transition} from './reducer.js';
import {GUEST_SAVE_KEY,parseGuestSave,serializeGuestSave} from './guestSave.js';
import {MONTHLY_REWARDS_URL,MONTHLY_REWARDS_CACHE_KEY,MONTHLY_REWARDS_REFRESH_MS,monthKey,localEpochDay,fallbackSchedule,parseMonthlySchedule} from '../engine/monthlyRewards.js';

export function useGame(){
 const [state,dispatch]=useReducer(transition,undefined,initialState);
 const latest=useRef(state);
 const queue=useRef(Promise.resolve());
 latest.current=state;
 const write=useCallback(()=>{
   const current=latest.current;
   if(!current.ready)return;
   const snapshot=serializeGuestSave(current.game);
   queue.current=queue.current.catch(()=>{}).then(()=>AsyncStorage.setItem(GUEST_SAVE_KEY,snapshot));
 },[]);
 useEffect(()=>{
   let alive=true;
   AsyncStorage.getItem(GUEST_SAVE_KEY).then(raw=>{
     if(alive)dispatch({type:'hydrate',game:parseGuestSave(raw),now:Date.now()});
   }).catch(()=>{if(alive)dispatch({type:'hydrate',now:Date.now()});});
   return ()=>{alive=false;};
 },[]);
 useEffect(()=>{
   if(!state.ready)return;
   const timeout=setTimeout(()=>{write();},400);
   return ()=>clearTimeout(timeout);
 },[state.game,state.ready,write]);
 // Kotlin MonthlyRewards: cache one validated month, refresh every 15 minutes,
 // never accept executable content or trust guest rewards as server-authoritative.
 useEffect(()=>{
  let alive=true;
  let activeMonth='';
  let busy=false;
  async function refreshMonthly(){
   if(!alive||busy)return;
   busy=true;
   try{
    const today=new Date(), month=monthKey(today),cacheKey=MONTHLY_REWARDS_CACHE_KEY+':'+month;
    if(month!==activeMonth){
     activeMonth=month;
     dispatch({type:'monthlySchedule',schedule:fallbackSchedule(today)});
     try{
      const cached=await AsyncStorage.getItem(cacheKey);
      if(alive&&cached&&month===monthKey(new Date())){
       const schedule=parseMonthlySchedule(cached,today,'cache');
       dispatch({type:'monthlySchedule',schedule});
      }
     }catch{/* Fall back to Kotlin's deterministic daily schedule. */}
    }
    const abort=new AbortController();
    const timeout=setTimeout(()=>abort.abort(),12000);
    try{
     const response=await fetch(MONTHLY_REWARDS_URL+'/'+month+'.json',{signal:abort.signal,headers:{Accept:'application/json'}});
     if(!response.ok)throw new Error('Monthly schedule HTTP '+response.status);
     const raw=await response.text();
     const schedule=parseMonthlySchedule(raw,today,'github');
     if(!alive||month!==monthKey(new Date()))return;
     dispatch({type:'monthlySchedule',schedule});
     await AsyncStorage.setItem(cacheKey,raw);
    }catch{/* Offline remains on last validated cached schedule or fallback. */}
    finally{clearTimeout(timeout);}
   }finally{busy=false;}
  }
  void refreshMonthly();
  const refreshTimer=setInterval(()=>void refreshMonthly(),MONTHLY_REWARDS_REFRESH_MS);
  const dayTimer=setInterval(()=>dispatch({type:'rollDailyDay',now:Date.now()}),60000);
  const sub=AppState.addEventListener('change',status=>{
   if(status==='active'){dispatch({type:'rollDailyDay',now:Date.now()});void refreshMonthly();}
  });
  return ()=>{alive=false;clearInterval(refreshTimer);clearInterval(dayTimer);sub.remove();};
 },[]);
 useEffect(()=>{
   const sub=AppState.addEventListener('change',status=>{
    if(status==='active')dispatch({type:'resume',now:Date.now()});
    else write();
   });
   return ()=>sub.remove();
 },[write]);
 return {state,act:dispatch};
}
