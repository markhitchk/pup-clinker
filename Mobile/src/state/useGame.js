import {useCallback,useEffect,useMemo,useReducer,useRef,useState} from 'react';
import {AppState,NativeModules,ToastAndroid} from 'react-native';
import {freshGame} from '../engine/game.js';
import {projectKotlinCloudSave} from '../production/kotlinCloudPreview.js';
import {supportsDatabaseLayout} from '../production/dbSchemaLayout.js';
import {requiresPupAccountSignIn} from '../production/startupErrors.js';
import {projectPendingTapDisplay} from './optimisticTaps.js';
import {classifyBetaSaveFailure} from './saveFeedback.js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {initialState,transition} from './reducer.js';
import {GUEST_SAVE_KEY,parseGuestSave,serializeGuestSave} from './guestSave.js';
import {MONTHLY_REWARDS_URL,MONTHLY_REWARDS_CACHE_KEY,MONTHLY_REWARDS_REFRESH_MS,monthKey,localEpochDay,fallbackSchedule,parseMonthlySchedule} from '../engine/monthlyRewards.js';

const PUBLISHABLE_KEY=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const BETA_LIVE_ACTIONS=new Set([
 'syncProbe','tap','care','purchase','ticket','accessory','equip','exchange','skill','daily',
 'claimDailyGoal','parkStart','parkClaim','prestige',
 'slotsStart','slotsReveal','rouletteStart','rouletteReveal','plinkoStart','plinkoReveal',
 'scratchStart','scratchReveal','wheelStart','wheelReveal',
 'blackjackStart','blackjackHit','blackjackStand','blackjackDouble','blackjackSplit',
 'gacha','switch','rename','favorite','afkStart','afkSettle','afkClaim','afkOpen','afkMarkAllRead','afkClearRead'
]);
const BETA_LOCAL_ONLY_ACTIONS=new Set(['setPreference','resetSettings','dismiss','markNotificationsRead','clearReadNotifications','openNotification','dismissGacha','checkNotifications','testNotification']);
function emptyAccountView(displayName='Pup Account'){const game=freshGame();game.onboardingComplete=true;game.onboardingStep=5;game.guestName=displayName;return game;}
export function useGame({mode='guest'}={}){
 const beta=mode==='beta';
 const [account,setAccount]=useState(beta?{status:'CHECKING'}:null);
 const [cloudState,setCloudState]=useState(beta?'NOT_LOADED':null);
 const [startupError,setStartupError]=useState(null);
 const [syncStatus,setSyncStatus]=useState({state:'NOT_VERIFIED',revision:null,savedAt:null,errorCode:null,notice:null});
 const liveVerified=useRef(false);
 const cachedPreview=useRef(false);
 const signInRequired=useRef(false);
 const currentRequest=useRef(0);
 const pendingLiveWrites=useRef(0);
 const needsReceiptRecheck=useRef(false);
 const signedWriteQueue=useRef(Promise.resolve());
 const nextTapIntentId=useRef(0);
 const pendingAfkClaims=useRef(new Set());
 const [pendingTapIntents,setPendingTapIntents]=useState([]);
 const refreshAccount=useCallback(async (incoming)=>{
  if(!beta)return false;
  const native=NativeModules.PuppyBetaAccount;
  if(!native?.bootstrap){setStartupError({code:'PUPPY_NATIVE_MODULE_MISSING'});setAccount({status:'NATIVE_MODULE_MISSING'});setCloudState('CONNECTION_ERROR');return false;}
  const request=++currentRequest.current;
  if(incoming?.status==='VERIFIED'){
  signInRequired.current=false;
  setAccount(incoming);
  // After sign-in, remain on startup until the signed cloud snapshot arrives.
  // Background refreshes must not interrupt an already verified game.
  if(!liveVerified.current)setCloudState('NOT_LOADED');
 }
  try{
   // A single signed request replaces sequential account-status + cloud-read.
   const result=await native.bootstrap(PUBLISHABLE_KEY);
   if(request!==currentRequest.current)return false;
   const active=result?.account;
   if(active?.status==='VERIFIED'&&!supportsDatabaseLayout(active.databaseLayoutVersion)){
    const incompatible=new Error('DATABASE_LAYOUT_UNSUPPORTED');
    incompatible.code='DATABASE_LAYOUT_UNSUPPORTED';
    throw incompatible; // Never hydrate or authorize an incompatible save.
   }
   if(active?.status!=='VERIFIED'){
    setStartupError(null);
    setPendingTapIntents([]);
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
   signInRequired.current=false;
   setStartupError(null);
   setAccount(active);
   setCloudState(cloud?.exists?(active.gameplayWritable===true?'LIVE_SYNCED':'SERVER_SNAPSHOT_READ_ONLY'):'NO_CLOUD_SAVE');
   setSyncStatus(previous=>{
    if(previous.state==='SAVED'&&cloud?.revision===previous.revision)return previous;
    return {state:active.gameplayWritable===true?'READY':'BLOCKED',
     revision:Number.isFinite(cloud?.revision)?cloud.revision:null,
     savedAt:cloud?.updatedAt||null,errorCode:active.gameplayWritable===true?null:'ACCOUNT_READ_ONLY',notice:null};
   });
   dispatch({type:'hydrate',game:g,now:Date.now(),readOnly:true});
   dispatch({type:'dismiss'});
   return true;
  }catch(error){
   if(request!==currentRequest.current)return false;
   const code=String(error?.code||'')+' '+String(error?.message||'');
   // A PupEye device session with no signed-in Pup Account is NOT offline.
   // Unlock the actual sign-in screen without clearing device identity or saves.
   if(requiresPupAccountSignIn(error)){
    liveVerified.current=false;
    cachedPreview.current=false;
    signInRequired.current=true;
    setPendingTapIntents([]);
    setStartupError(null);
    setAccount({status:'NEEDS_ACCOUNT'});
    setCloudState('SIGN_IN_REQUIRED');
    setSyncStatus(current=>({...current,state:'NOT_VERIFIED',errorCode:'SIGN_IN_REQUIRED'}));
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
    dispatch({type:'dismiss'});
    return false;
   }
   if(error?.code==='DATABASE_LAYOUT_UNSUPPORTED'){
    liveVerified.current=false;cachedPreview.current=false;
    setPendingTapIntents([]);
    setStartupError({code:'DATABASE_LAYOUT_UNSUPPORTED'});
    setAccount({status:'DATABASE_LAYOUT_UNSUPPORTED'});
    setCloudState('CONNECTION_ERROR');
    setSyncStatus(current=>({...current,state:'BLOCKED',errorCode:'DATABASE_LAYOUT_UNSUPPORTED'}));
    dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
    return false;
   }
   const revoked=/BETA_LINK_REQUIRED|INSTALLATION_REVOKED|PUP_ACCOUNT_BLOCKED|BETA_LINK_REVOKED|BETA_OWNER_DEVICE_UNAVAILABLE/.test(code);
   // Projected cloud parse errors are recognized locally; no exception text
   // is handed to the splash screen or persisted as diagnostics.
   const projectionError=/^(INVALID_CLOUD_SAVE|MISSING_KOTLIN_PROGRESS|UNSUPPORTED_CLOUD_SAVE|UNSUPPORTED_KOTLIN_SAVE)/.test(String(error?.message||''));
   const safeFailure={code:projectionError?'INVALID_CLOUD_SAVE':String(error?.code||'BETA_TRANSPORT_FAILURE').slice(0,72)};
   if(revoked){
    cachedPreview.current=false;
    liveVerified.current=false;
    void native.clearCachedBootstrap?.().catch(()=>{});
   }
   if(liveVerified.current&&!revoked){
    // A transient refresh outage after a successful startup must never throw
    // the player back to sign-in or replace their last verified cloud progress.
    // The server remains the only authority for subsequent economy writes.
    setCloudState('LAST_VERIFIED');
    setSyncStatus(previous=>({...previous,state:'RECONNECTING',errorCode:'CLOUD_TEMPORARILY_UNAVAILABLE'}));
    return false;
   }
   if(cachedPreview.current&&!revoked){
    setStartupError(safeFailure);
    setCloudState('CACHED_OFFLINE');
    return false; // Cached snapshots alone never authorize gameplay.
   }
   setStartupError(safeFailure);
   setAccount({status:revoked?'NEEDS_ACCOUNT':'CONNECTION_ERROR'});
   setCloudState(revoked?'SIGN_IN_REQUIRED':'CONNECTION_ERROR');
   setSyncStatus(current=>({...current,state:'ERROR',
    errorCode:revoked?'SESSION_REVOKED':'BOOTSTRAP_FAILED'}));
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
     if(liveVerified.current||signInRequired.current||result?.account?.status!=='CACHED'||
      !supportsDatabaseLayout(result.account.databaseLayoutVersion))return;
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
  // Monthly reward definitions are display data; beta uses the same validated feed as Play.
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
   const claimId=action.type==='afkClaim'?String(action.id||''):null;
   if(claimId!==null&&pendingAfkClaims.current.has(claimId))return;
   const native=NativeModules.PuppyBetaAccount;
   if(!liveVerified.current||!native?.gameplayAction||account?.gameplayWritable!==true){
    const reason=!liveVerified.current?'ACCOUNT_NOT_VERIFIED':
     !native?.gameplayAction?'NATIVE_SAVE_BRIDGE_MISSING':'ACCOUNT_NOT_WRITABLE';
    setSyncStatus(current=>({...current,state:'BLOCKED',errorCode:reason}));
    if(ToastAndroid?.show)ToastAndroid.show('Action unavailable. Sign in or reconnect to your Pup Account.',ToastAndroid.LONG);
    return;
   }
   // Instantly preview accepted input while the signed request is queued.
   // Only the native/server response can commit balances, XP and rewards.
   const tapIntentId=action.type==='tap'?++nextTapIntentId.current:null;
   if(tapIntentId!==null)setPendingTapIntents(current=>[...current,{id:tapIntentId,at:Number.isSafeInteger(action.now)?action.now:Date.now()}]);
   if(claimId!==null)pendingAfkClaims.current.add(claimId);
   pendingLiveWrites.current++;
   setSyncStatus(previous=>({...previous,state:'SAVING',errorCode:null,notice:null}));
   signedWriteQueue.current=signedWriteQueue.current.catch(()=>{}).then(async()=>{
    const intent=Object.fromEntries(
     ['type','kind','id','payment','upgradeId','rarity','name','skill','goalId','amount','wager','bet'].filter(k=>action[k]!==undefined).map(k=>[k,action[k]])
    );
    const result=await native.gameplayAction(JSON.stringify(intent),PUBLISHABLE_KEY);
    if(result.saved!==true||!Number.isSafeInteger(result.revision)||result.revision<=0)
     throw Object.assign(new Error('No confirmed cloud save receipt'),{code:'SAVE_RECEIPT_MISSING'});
    // Supabase has already committed at this point. Never misreport a
    // projection/UI error as a failed write or encourage a double-spend retry.
    ++currentRequest.current;
    setCloudState('LIVE_SYNCED');
    setSyncStatus({state:'SAVED',revision:result.revision,
     savedAt:result.savedAt||new Date().toISOString(),errorCode:null,notice:null});
    try {
     const game=projectKotlinCloudSave(result.saveJson,account?.displayName||account?.username);
     dispatch({type:'hydrate',game,now:Date.now(),readOnly:true});
    }catch{
     setSyncStatus({state:'SAVED_DISPLAY_ERROR',revision:result.revision,
      savedAt:result.savedAt||new Date().toISOString(),
      errorCode:'SAVED_BUT_DISPLAY_REFRESH_FAILED',notice:'Your action was saved, but the screen needs to reload the latest cloud progress.'});
     void refreshAccount();
    }
   }).catch(error=>{
    // A timeout or dropped response does NOT prove the server rejected the
    // mutation. Never replay an ambiguous economy action or erase the last
    // verified cloud snapshot. Re-read the signed server state after all
    // in-flight writes settle, and ask the player before submitting again.
    const failure=classifyBetaSaveFailure(error);
    if(failure.state==='VERIFYING_SAVE')needsReceiptRecheck.current=true;
    setSyncStatus(previous=>({...previous,...failure}));
   }).finally(()=>{
    if(claimId!==null)pendingAfkClaims.current.delete(claimId);
    if(tapIntentId!==null)setPendingTapIntents(current=>current.filter(item=>item.id!==tapIntentId));
    pendingLiveWrites.current=Math.max(0,pendingLiveWrites.current-1);
    if(pendingLiveWrites.current===0&&needsReceiptRecheck.current){
     needsReceiptRecheck.current=false;
     void refreshAccount();
    }
   });
   return;
  }
  if(beta&&!BETA_LOCAL_ONLY_ACTIONS.has(action.type)){
   // Unported reward and economy features cannot mint unsupported saves. Use a short,
   // rate-limited native toast instead of repeatedly obscuring the game screen.
   const now=Date.now();
   if(now-lastBlockedToast.current>4000){
    lastBlockedToast.current=now;
    if(ToastAndroid?.show)ToastAndroid.show('This feature is temporarily unavailable.',ToastAndroid.SHORT);
   }
   return;
  }
  dispatch(action);
 },[beta,account?.displayName,account?.username,cloudState,refreshAccount]);
 const afkResumeChecked=useRef(false);
 useEffect(()=>{
  if(!beta||account?.status!=='VERIFIED'||account.gameplayWritable!==true||!liveVerified.current)return;
  // The server alone timestamps AFK starts and settles earned Treats.
  // No client clock or balance is accepted for these intents.
  if(!afkResumeChecked.current){afkResumeChecked.current=true;act({type:'afkSettle'});}
  const sub=AppState.addEventListener('change',status=>{
   if(status==='background')act({type:'afkStart'});
   if(status==='active')act({type:'afkSettle'});
  });
  return ()=>sub.remove();
 },[beta,account?.status,account?.gameplayWritable,act]);
 const updateAccountName=useCallback(async displayName=>{
  if(!beta||account?.status!=='VERIFIED')throw new Error('Sign in before editing your Pup Account.');
  const updated=await NativeModules.PuppyBetaAccount.updateDisplayName(displayName,PUBLISHABLE_KEY);
  if(updated?.status!=='VERIFIED')throw new Error('Account update was not verified.');
  await refreshAccount(updated);
  return updated;
 },[beta,account?.status,refreshAccount]);
 const gameForDisplay=useMemo(()=>beta?projectPendingTapDisplay(state.game,pendingTapIntents):state.game,[beta,state.game,pendingTapIntents]);
 return {state,gameForDisplay,act,account,cloudState,startupError,syncStatus,refreshAccount,updateAccountName};
}
