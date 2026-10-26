import {useCallback,useEffect,useReducer,useRef,useState} from 'react';
import {AppState,NativeModules} from 'react-native';
import {freshGame} from '../engine/game.js';
import {projectKotlinCloudSave} from '../production/kotlinCloudPreview.js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {initialState,transition} from './reducer.js';
import {GUEST_SAVE_KEY,parseGuestSave,serializeGuestSave} from './guestSave.js';
import {MONTHLY_REWARDS_URL,MONTHLY_REWARDS_CACHE_KEY,MONTHLY_REWARDS_REFRESH_MS,monthKey,localEpochDay,fallbackSchedule,parseMonthlySchedule} from '../engine/monthlyRewards.js';

const PUBLISHABLE_KEY=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const BETA_LOCAL_ONLY_ACTIONS=new Set(['setPreference','resetSettings','dismiss','markNotificationsRead','clearReadNotifications','openNotification']);
function emptyAccountView(){const game=freshGame();game.onboardingComplete=true;game.onboardingStep=5;game.guestName='Pup Account';return game;}
export function useGame({mode='guest'}={}){
 const beta=mode==='beta';
 const [account,setAccount]=useState(beta?{status:'CHECKING'}:null);
 const [cloudState,setCloudState]=useState(beta?'NOT_LOADED':null);
 const refreshAccount=useCallback(async (incoming)=>{
  if(!beta)return;
  const native=NativeModules.PuppyBetaAccount;
  if(!native){setAccount({status:'NATIVE_MODULE_MISSING'});setCloudState('NOT_LOADED');return;}
  try {
   const active=incoming?.status==='VERIFIED'?incoming:await native.status(PUBLISHABLE_KEY);
   setAccount(active);
   if(active?.status!=='VERIFIED'){
    setCloudState('SIGN_IN_REQUIRED');
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now()});
    dispatch({type:'dismiss'});
    return;
   }
   const cloud=await native.cloudRead(PUBLISHABLE_KEY);
   if(!cloud?.exists){
    setCloudState('NO_CLOUD_SAVE');
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now()});
    dispatch({type:'dismiss'});
    return;
   }
   const game=projectKotlinCloudSave(cloud.saveJson,active.username);
   setCloudState('SERVER_SNAPSHOT_READ_ONLY');
   dispatch({type:'hydrate',game,now:Date.now()});
   dispatch({type:'dismiss'});
  }catch(error){
   setAccount(old=>old?.status==='VERIFIED'?old:{status:'SIGN_IN_REQUIRED'});
   setCloudState('UNVERIFIED_OR_UNSUPPORTED');
   dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now()});
   dispatch({type:'dismiss'});
  }
 },[beta]);
 const [state,dispatch]=useReducer(transition,undefined,initialState);
 const latest=useRef(state);
 const queue=useRef(Promise.resolve());
 latest.current=state;
 const write=useCallback(()=>{
   if(beta)return; // Never persist a beta session as guest-only progression.
   const current=latest.current;
   if(!current.ready)return;
   const snapshot=serializeGuestSave(current.game);
   queue.current=queue.current.catch(()=>{}).then(()=>AsyncStorage.setItem(GUEST_SAVE_KEY,snapshot));
 },[beta]);
 useEffect(()=>{
   if(beta){void refreshAccount();return;}
   let alive=true;
   AsyncStorage.getItem(GUEST_SAVE_KEY).then(raw=>{
     if(alive)dispatch({type:'hydrate',game:parseGuestSave(raw),now:Date.now()});
   }).catch(()=>{if(alive)dispatch({type:'hydrate',now:Date.now()});});
   return ()=>{alive=false;};
 },[beta,refreshAccount]);
 // Kotlin saveState() persists accepted taps immediately. Do not leave a
 // 400 ms crash window after a tap, purchase or pending-casino mutation.
 // The existing promise queue serializes AsyncStorage writes in state order.
 useEffect(()=>{
   if(beta||!state.ready)return;
   write();
 },[beta,state.game,state.ready,write]);
 // Source: PuppyClickerV6ViewModel's one-second coroutine. A 1600 ms
 // expired combo disappears from Play, and care decays once every 120 ticks
 // only while the app is in the foreground. No background care loop runs.
 useEffect(()=>{
  if(beta)return;
  let ticks=0;
  const timer=setInterval(()=>{
   const now=Date.now();
   dispatch({type:'comboTimeout',now});
   ticks++;
   if(ticks%120===0&&AppState.currentState==='active')
    dispatch({type:'foregroundCareTick',now});
  },1000);
  return ()=>clearInterval(timer);
 },[beta]);
 // Kotlin MonthlyRewards: cache one validated month, refresh every 15 minutes,
 // never accept executable content or trust guest rewards as server-authoritative.
 useEffect(()=>{
  if(beta)return;
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
 },[beta]);
 useEffect(()=>{
   if(beta)return;
   const sub=AppState.addEventListener('change',status=>{
    if(status==='active')dispatch({type:'resume',now:Date.now()});
    else write();
   });
   return ()=>sub.remove();
 },[beta,write]);
 const act=useCallback(action=>{
  if(beta&&!BETA_LOCAL_ONLY_ACTIONS.has(action.type)){
   dispatch({type:'betaActionBlocked'});
   return;
  }
  dispatch(action);
 },[beta]);
 return {state,act,account,cloudState,refreshAccount};
}
