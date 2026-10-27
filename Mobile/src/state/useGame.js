import {useCallback,useEffect,useReducer,useRef,useState} from 'react';
import {AppState,NativeModules,ToastAndroid} from 'react-native';
import {freshGame} from '../engine/game.js';
import {projectKotlinCloudSave} from '../production/kotlinCloudPreview.js';
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
 'gacha','switch','rename'
]);
const BETA_LOCAL_ONLY_ACTIONS=new Set(['setPreference','resetSettings','dismiss','checkNotifications','testNotification','markNotificationsRead','clearReadNotifications','openNotification','dismissGacha']);
function emptyAccountView(displayName='Pup Account'){const game=freshGame();game.onboardingComplete=true;game.onboardingStep=5;game.guestName=displayName;return game;}
export function useGame({mode='guest'}={}){
 const beta=mode==='beta';
 const [account,setAccount]=useState(beta?{status:'CHECKING'}:null);
 const [cloudState,setCloudState]=useState(beta?'NOT_LOADED':null);
 const [syncStatus,setSyncStatus]=useState({state:'NOT_VERIFIED',revision:null,savedAt:null,errorCode:null});
 const liveVerified=useRef(false);
 const cachedPreview=useRef(false);
 const currentRequest=useRef(0);
 const pendingLiveWrites=useRef(0);
 const bootstrapInFlight=useRef(false);
 const lastServerTreats=useRef(null);
 const signedWriteQueue=useRef(Promise.resolve());
 const refreshAccount=useCallback(async (incoming)=>{
  if(!beta)return false;
  const native=NativeModules.PuppyBetaAccount;
  if(!native?.bootstrap){setAccount({status:'NATIVE_MODULE_MISSING'});setCloudState('NOT_LOADED');return false;}
  if(bootstrapInFlight.current)return false;
  bootstrapInFlight.current=true;
  const previouslyVerified=liveVerified.current;
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
   lastServerTreats.current=g.treats;
   setAccount(active);
   setCloudState(cloud?.exists?(active.gameplayWritable===true?'LIVE_SYNCED':'SERVER_SNAPSHOT_READ_ONLY'):'NO_CLOUD_SAVE');
   setSyncStatus(previous=>{
    if(previous.state==='SAVED'&&cloud?.revision===previous.revision)return previous;
    return {state:active.gameplayWritable===true?'READY':'BLOCKED',
     revision:Number.isFinite(cloud?.revision)?cloud.revision:null,
     savedAt:cloud?.updatedAt||null,errorCode:active.gameplayWritable===true?null:'ACCOUNT_READ_ONLY'};
   });
   dispatch({type:'hydrate',game:g,now:Date.now(),readOnly:true});
   dispatch({type:'dismiss'});
   return true;
  }catch(error){
   if(request!==currentRequest.current)return false;
   const code=String(error?.code||'')+' '+String(error?.message||'');
   const errorCode=String(error?.code||'').replace(/^BETA_/,'').replace(/[^A-Z0-9_]/g,'').slice(0,64)||'BOOTSTRAP_DISPLAY_FAILED';
   const errorDetail=String(error?.message||'Cloud projection or bootstrap failed').slice(0,120);
   const banned=['GLOBAL_BANNED','PUP_ACCOUNT_BLOCKED','DEVICE_BANNED','INSTALLATION_BANNED'].includes(errorCode);
   const review=errorCode==='REVIEW_REQUIRED';
   const revoked=banned||review||/BETA_LINK_REQUIRED|INSTALLATION_REVOKED|BETA_LINK_REVOKED|BETA_OWNER_DEVICE_UNAVAILABLE/.test(code);
   liveVerified.current=false; // Failed verification cannot authorize gameplay writes.
   setSyncStatus(current=>({...current,state:'ERROR',
    errorCode:banned?'GLOBAL_BANNED':review?'REVIEW_REQUIRED':revoked?'SESSION_REVOKED':errorCode,errorDetail}));
   if(revoked){
    cachedPreview.current=false;
    lastServerTreats.current=null;
    void native.clearCachedBootstrap?.().catch(()=>{});
   }
   if((cachedPreview.current||previouslyVerified)&&!revoked){
    cachedPreview.current=true;
    setAccount(previous=>previous?.status==='VERIFIED'?{...previous,status:'CACHED',gameplayWritable:false}:previous);
    setCloudState('CACHED_OFFLINE');
    return false; // Preserve encrypted snapshot for display, never writes.
   }
   setAccount({status:banned?'BANNED':review?'REVIEW_REQUIRED':revoked?'NEEDS_ACCOUNT':'CONNECTION_ERROR'});
   setCloudState(banned||review?'ACCESS_RESTRICTED':revoked?'SIGN_IN_REQUIRED':'CONNECTION_ERROR');
   dispatch({type:'hydrate',game:emptyAccountView(),now:Date.now(),readOnly:true});
   return false;
  }finally{
   bootstrapInFlight.current=false;
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
   if(AppState.currentState==='active'&&pendingLiveWrites.current===0&&cloudState!=='SIGN_IN_REQUIRED'&&cloudState!=='ACCESS_RESTRICTED')void refreshAccount();
  },15000);
  const listener=AppState.addEventListener('change',status=>{
   if(status==='active'&&pendingLiveWrites.current===0&&cloudState!=='SIGN_IN_REQUIRED'&&cloudState!=='ACCESS_RESTRICTED')
    void refreshAccount();
  });
  return ()=>{clearInterval(timer);listener.remove();};
 },[beta,cloudState,refreshAccount]);
 const act=useCallback(action=>{
  if(beta&&BETA_LIVE_ACTIONS.has(action.type)){
   const native=NativeModules.PuppyBetaAccount;
   if(!liveVerified.current||!native?.gameplayAction||account?.gameplayWritable!==true||account?.status!=='VERIFIED'){
    const reason=!liveVerified.current?'ACCOUNT_NOT_VERIFIED':
     !native?.gameplayAction?'NATIVE_SAVE_BRIDGE_MISSING':'ACCOUNT_NOT_WRITABLE';
    setSyncStatus(current=>({...current,state:'BLOCKED',errorCode:reason}));
    if(ToastAndroid?.show)ToastAndroid.show('Reconnect your Pup Account to save progress.',ToastAndroid.LONG);
    return;
   }
   pendingLiveWrites.current++;
   setSyncStatus(previous=>({...previous,state:'SAVING',errorCode:null}));
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
    const savedAt=result.savedAt||new Date().toISOString();
    setSyncStatus({state:'SAVED',revision:result.revision,
     savedAt,errorCode:null,lastAction:action.type,lastTapGain:null});
    try {
     const game=projectKotlinCloudSave(result.saveJson,account?.displayName||account?.username);
     const previousTreats=lastServerTreats.current;
     lastServerTreats.current=game.treats;
     const lastTapGain=action.type==='tap'&&Number.isSafeInteger(previousTreats)
      ?Math.max(0,game.treats-previousTreats):null;
     setSyncStatus({state:'SAVED',revision:result.revision,
      savedAt,errorCode:null,lastAction:action.type,lastTapGain});
     dispatch({type:'hydrate',game,now:Date.now(),readOnly:true});
    }catch{
     setSyncStatus({state:'SAVED_DISPLAY_ERROR',revision:result.revision,
      savedAt:result.savedAt||new Date().toISOString(),
      errorCode:'SAVED_BUT_DISPLAY_REFRESH_FAILED'});
     if(ToastAndroid?.show)ToastAndroid.show('Progress saved, but the screen could not refresh.',ToastAndroid.LONG);
    }
   }).catch(error=>{
    const code=String(error?.code||'SAVE_NETWORK_FAILURE').replace(/^BETA_/,'').slice(0,64);
    setSyncStatus(previous=>({...previous,state:'ERROR',errorCode:code,
     errorDetail:String(error?.message||'').slice(0,120)}));
    if(/CLOUD_SAVE_CONFLICT/.test(code))void refreshAccount();
    if(ToastAndroid?.show)ToastAndroid.show('Unable to save progress. Check your connection.',ToastAndroid.LONG);
   }).finally(()=>{pendingLiveWrites.current=Math.max(0,pendingLiveWrites.current-1);});
   return;
  }
  if(beta&&!BETA_LOCAL_ONLY_ACTIONS.has(action.type)){
   // Unported reward and economy features cannot mint unsupported saves. Use a short,
   // rate-limited native toast instead of repeatedly obscuring the game screen.
   const now=Date.now();
   if(now-lastBlockedToast.current>4000){
    lastBlockedToast.current=now;
    if(ToastAndroid?.show)ToastAndroid.show('That feature is not live yet. Play and Care sync to Pup Account.',ToastAndroid.SHORT);
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
 return {state,act,account,cloudState,syncStatus,refreshAccount,updateAccountName};
}
