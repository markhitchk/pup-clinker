import {useCallback,useEffect,useReducer,useRef,useState} from 'react';
import {AppState,NativeModules,ToastAndroid} from 'react-native';
import {freshGame} from '../engine/game.js';
import {projectKotlinCloudSave} from '../production/kotlinCloudPreview.js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {initialState,transition} from './reducer.js';
import {GUEST_SAVE_KEY,parseGuestSave,serializeGuestSave} from './guestSave.js';
import {MONTHLY_REWARDS_URL,MONTHLY_REWARDS_CACHE_KEY,MONTHLY_REWARDS_REFRESH_MS,monthKey,localEpochDay,fallbackSchedule,parseMonthlySchedule} from '../engine/monthlyRewards.js';

const PUBLISHABLE_KEY=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const BETA_LIVE_ACTIONS=new Set(['tap','care']);
const BETA_LOCAL_ONLY_ACTIONS=new Set(['setPreference','resetSettings','dismiss','markNotificationsRead','clearReadNotifications','openNotification']);
function emptyAccountView(displayName='Pup Account'){const game=freshGame();game.onboardingComplete=true;game.onboardingStep=5;game.guestName=displayName;return game;}
export function useGame({mode='guest'}={}){
 const beta=mode==='beta';
 const [account,setAccount]=useState(beta?{status:'CHECKING'}:null);
 const [cloudState,setCloudState]=useState(beta?'NOT_LOADED':null);
 const liveVerified=useRef(false);
 const cachedPreview=useRef(false);
 const currentRequest=useRef(0);
 const pendingLiveWrites=useRef(0);
 const signedWriteQueue=useRef(Promise.resolve());
 const refreshAccount=useCallback(async (incoming)=>{
  if(!beta)return false;
  const native=NativeModules.PuppyBetaAccount;
  if(!native?.bootstrap){setAccount({status:'NATIVE_MODULE_MISSING'});setCloudState('NOT_LOADED');return false;}
  const request=++currentRequest.current;
  if(incoming?.status==='VERIFIED')setAccount(incoming);
  try{
   // A single signed request replaces sequential account-status + cloud-read.
   const result=await native.bootstrap(PUBLISHABLE_KEY);
   if(request!==currentRequest.current)return false;
   const active=result?.account;
   if(active?.status!=='VERIFIED'){
    liveVerified.current=false;
    cachedPreview.current=false;
    setAccount(active||{status:'NEEDS_ACCOUNT'});
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
    dispatch({type:'dismiss'});
    setCloudState('SIGN_IN_REQUIRED');
    return false;
   }
   const cloud=result.cloud;
   const g=cloud?.exists
    ?projectKotlinCloudSave(cloud.saveJson,active.displayName||active.username)
    :emptyAccountView(active.displayName||active.username);
   liveVerified.current=true;
   cachedPreview.current=false;
   setAccount(active);
   setCloudState(cloud?.exists?(active.readOnly?'SERVER_SNAPSHOT_READ_ONLY':'LIVE_SYNCED'):'NO_CLOUD_SAVE');
   dispatch({type:'hydrate',game:g,now:Date.now(),readOnly:true});
   dispatch({type:'dismiss'});
   return true;
  }catch(error){
   if(request!==currentRequest.current)return false;
   const code=String(error?.code||'')+' '+String(error?.message||'');
   const revoked=/BETA_LINK_REQUIRED|INSTALLATION_REVOKED|PUP_ACCOUNT_BLOCKED|BETA_LINK_REVOKED|BETA_OWNER_DEVICE_UNAVAILABLE/.test(code);
   if(revoked){
    cachedPreview.current=false;
    liveVerified.current=false;
    void native.clearCachedBootstrap?.().catch(()=>{});
   }
   if(cachedPreview.current&&!revoked){
    setCloudState('CACHED_OFFLINE');
    return false; // Keep last known encrypted preview visible; never grant writes.
   }
   setAccount({status:revoked?'NEEDS_ACCOUNT':'CONNECTION_ERROR'});
   setCloudState(revoked?'SIGN_IN_REQUIRED':'CONNECTION_ERROR');
   if(!liveVerified.current)dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
   return false;
  }
 },[beta]);
 const [state,dispatch]=useReducer(transition,undefined,initialState);
 const latest=useRef(state);
 const queue=useRef(Promise.resolve());
 const lastBlockedToast=useRef(0);
 latest.current=state;
 const write=useCallback(()=>{
   if(beta)return; // Never persist a beta session as guest-only progression.
   const current=latest.current;
   if(!current.ready)return;
   const snapshot=serializeGuestSave(current.game);
   queue.current=queue.current.catch(()=>{}).then(()=>AsyncStorage.setItem(GUEST_SAVE_KEY,snapshot));
 },[beta]);
 useEffect(()=>{
   if(beta){
    // Render the complete game navigation immediately, even on slow networks.
    // This placeholder is display-only and cannot accept economy actions.
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
    dispatch({type:'dismiss'});
    const native=NativeModules.PuppyBetaAccount;
    // Last signed snapshot is a visual-only head start during live verification.
    // Delayed cache reads cannot replace a newer server-verified response.
    if(native?.cachedBootstrap)void native.cachedBootstrap().then(result=>{
     if(liveVerified.current||result?.account?.status!=='CACHED')return;
     try{
      const account=result.account;
      const cloud=result.cloud;
      const g=cloud?.exists
       ?projectKotlinCloudSave(cloud.saveJson,account.displayName||account.username)
       :emptyAccountView(account.displayName||account.username);
      cachedPreview.current=true;
      setAccount(account);
      setCloudState('CACHED_VERIFYING');
      dispatch({type:'hydrate',game:g,now:Date.now(),readOnly:true});
     }catch{/* Invalid cached projection never replaces a fresh server result. */}
    }).catch(()=>{});
    void refreshAccount();return;
   }
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
 // Signed server response is the only source of new gameplay balances.
 // The queue prevents simultaneous taps from replaying one cloud revision.
 useEffect(()=>{
  if(!beta)return;
  const timer=setInterval(()=>{
   if(liveVerified.current&&pendingLiveWrites.current===0)void refreshAccount();
  },15000);
  const listener=AppState.addEventListener('change',status=>{
   if(status==='active'&&liveVerified.current&&pendingLiveWrites.current===0)
    void refreshAccount();
  });
  return ()=>{clearInterval(timer);listener.remove();};
 },[beta,refreshAccount]);
 const act=useCallback(action=>{
  if(beta&&BETA_LIVE_ACTIONS.has(action.type)){
   const native=NativeModules.PuppyBetaAccount;
   if(!liveVerified.current||!native?.gameplayAction||cloudState==='NO_CLOUD_SAVE'){
    if(ToastAndroid?.show)ToastAndroid.show('Live Pup Account progress is not available. Connect your cloud save.',ToastAndroid.SHORT);
    return;
   }
   pendingLiveWrites.current++;
   signedWriteQueue.current=signedWriteQueue.current.catch(()=>{}).then(async()=>{
    const result=await native.gameplayAction(action.type,action.type==='care'?action.kind:'',PUBLISHABLE_KEY);
    const game=projectKotlinCloudSave(result.saveJson,account?.displayName||account?.username);
    ++currentRequest.current; // Ignore an older overlapping bootstrap reply.
    dispatch({type:'hydrate',game,now:Date.now()});
    setCloudState('LIVE_SYNCED');
   }).catch(error=>{
    const code=String(error?.code||'');
    if(/CLOUD_SAVE_CONFLICT/.test(code))void refreshAccount();
    const message=/CLOUD_SAVE_CONFLICT/.test(code)
     ?'Cloud progress changed; refreshing before retry.'
     :/GAMEPLAY_REJECTED/.test(code)?'Action rejected by PupEye or puppy care limits.'
     :'Cloud action was not saved. Check your connection.';
    if(ToastAndroid?.show)ToastAndroid.show(message,ToastAndroid.SHORT);
   }).finally(()=>{pendingLiveWrites.current=Math.max(0,pendingLiveWrites.current-1);});
   return;
  }
  if(beta&&!BETA_LOCAL_ONLY_ACTIONS.has(action.type)){
   // A read-only cloud snapshot cannot accept economy mutations. Use a short,
   // rate-limited native toast instead of repeatedly obscuring the game screen.
   const now=Date.now();
   if(now-lastBlockedToast.current>4000){
    lastBlockedToast.current=now;
    if(ToastAndroid?.show)ToastAndroid.show('Cloud progress is read-only in this beta.',ToastAndroid.SHORT);
   }
   return;
  }
  dispatch(action);
 },[beta,account?.displayName,account?.username,cloudState,refreshAccount]);
 const updateAccountName=useCallback(async displayName=>{
  if(!beta||account?.status!=='VERIFIED')throw new Error('Sign in before editing your Pup Account.');
  const updated=await NativeModules.PuppyBetaAccount.updateDisplayName(displayName,PUBLISHABLE_KEY);
  if(updated?.status!=='VERIFIED')throw new Error('Account update was not verified.');
  await refreshAccount(updated);
  return updated;
 },[beta,account?.status,refreshAccount]);
 return {state,act,account,cloudState,refreshAccount,updateAccountName};
}
