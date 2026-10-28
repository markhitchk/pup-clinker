import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,AppState,Image,Pressable,StatusBar,Text,View,useColorScheme} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {nextQueueDelay,normalizeQueueResponse,queueError} from './queueModel.js';

/** Runs before App.js, PupEye startup, cloud saves, notifications or gameplay. */
export default function PuppyQueueRoom({native,endpoint,publishableKey,onAdmitted}){
 const insets=useSafeAreaInsets(),dark=useColorScheme()!=='light';
 const palette=dark?{bg:'#0d1821',panel:'#18313e',text:'#ffffff',muted:'#b7c8d0',accent:'#85d5fc'}
  :{bg:'#f3f8fb',panel:'#ffffff',text:'#152a37',muted:'#4d6170',accent:'#008bcd'};
 const [status,setStatus]=useState({state:'CHECKING',playersAhead:null,code:null,retryAfterMs:12000});
 const [busy,setBusy]=useState(false);
 const mounted=useRef(true),generation=useRef(0),attempts=useRef(0),timer=useRef(null);
 const active=useRef(true),inFlight=useRef(false),lastHeartbeat=useRef(0);
 const stop=()=>{generation.current++;if(timer.current)clearTimeout(timer.current);timer.current=null;};
 const step=useCallback(async(kind='bootstrap')=>{
  if(inFlight.current||!mounted.current||!active.current)return;
  inFlight.current=true;setBusy(true);const issued=++generation.current;
  try{
   if(!native||!endpoint||!publishableKey)throw Error('QUEUE_UNCONFIGURED');
   const method=kind==='admit'?'queueAdmit':kind==='heartbeat'?'queueHeartbeat':
    kind==='status'?'queueStatus':'queueBootstrap';
   if(typeof native[method]!=='function')throw Error('QUEUE_BRIDGE_UNAVAILABLE');
   const result=normalizeQueueResponse(await native[method](endpoint,publishableKey));
   if(!mounted.current||issued!==generation.current)return;
   attempts.current=0;setStatus(result);
   if(kind==='heartbeat'||kind==='bootstrap')lastHeartbeat.current=Date.now();
   if(result.state==='ADMITTED'||result.state==='ALLOW'){
    onAdmitted();return;
   }
   if(result.state==='READY'){
    timer.current=setTimeout(()=>{inFlight.current=false;void step('admit');},500);
    return;
   }
   if(result.state==='EXPIRED'||result.state==='REVOKED'){
    // Ticket was cleared by the native Keystore bridge; next bootstrap can
    // create one, but never silently jump over a waiting state.
    timer.current=setTimeout(()=>{inFlight.current=false;void step('bootstrap');},nextQueueDelay(result.retryAfterMs,0));
    return;
   }
   if(result.state==='MAINTENANCE')return; // manual retry only
   timer.current=setTimeout(()=>{
    inFlight.current=false;
    void step(Date.now()-lastHeartbeat.current>60000?'heartbeat':'status');
   },nextQueueDelay(result.retryAfterMs,0));
  }catch{
   if(!mounted.current||issued!==generation.current)return;
   attempts.current++;
   setStatus({state:'UNAVAILABLE',code:kind==='bootstrap'?'PC-Q001':'PC-Q006',
    retryAfterMs:30000,playersAhead:null});
   timer.current=setTimeout(()=>{inFlight.current=false;void step(kind==='bootstrap'?'bootstrap':'status');},
    nextQueueDelay(30000,attempts.current));
  }finally{if(issued===generation.current)setBusy(false);}
 },[native,endpoint,publishableKey,onAdmitted]);
 useEffect(()=>{
  mounted.current=true;active.current=AppState.currentState!=='background';
  void step('bootstrap');
  const sub=AppState.addEventListener('change',state=>{
   if(state!=='active'){active.current=false;stop();inFlight.current=false;}
   else{active.current=true;stop();inFlight.current=false;void step('bootstrap');}
  });
  return ()=>{mounted.current=false;stop();sub.remove();};
 },[step]);
 const error=status.state==='UNAVAILABLE'||status.state==='MAINTENANCE';
 const issue=error?queueError(status.state==='MAINTENANCE'?'PC-Q007':status.code):null;
 const retry=()=>{stop();inFlight.current=false;attempts.current=0;void step('bootstrap');};
 return <View style={{flex:1,backgroundColor:palette.bg,paddingTop:insets.top+14,
   paddingBottom:insets.bottom+20,paddingHorizontal:21,justifyContent:'space-between'}}>
  <StatusBar barStyle={dark?'light-content':'dark-content'} backgroundColor={palette.bg}/>
  <View style={{flexDirection:'row',alignItems:'center',gap:10,paddingVertical:10}}>
   <Image source={require('../../assets/logos/puppy_clicker.png')} style={{width:58,height:58}} resizeMode="contain"/>
   <Text accessibilityRole="header" style={{color:palette.text,fontSize:24,fontWeight:'900'}}>Puppy Clicker</Text>
  </View>
  <View style={{backgroundColor:palette.panel,borderRadius:27,padding:26,alignItems:'center',gap:18,
    borderWidth:1,borderColor:dark?'#34515d':'#dce7ed'}}>
   <Text style={{fontSize:49}} accessible accessibilityLabel="Puppy paw">🐾</Text>
   <Text style={{fontSize:25,fontWeight:'900',textAlign:'center',color:palette.text}}>
    {status.state==='CHECKING'?'Checking entry…':error?issue.title:'Server Is Busy!'}</Text>
   <Text style={{fontSize:16,lineHeight:24,textAlign:'center',color:palette.muted}}>
    {error?issue.message:'Lots of puppies are joining right now. Your spot in line is saved!'}</Text>
   {status.state==='WAITING'?<>
    <Text style={{color:palette.text,fontWeight:'800',fontSize:19}}>
     {status.playersAhead===null?'Checking your place…':`Players ahead of you: ${status.playersAhead}`}</Text>
    <Text style={{color:palette.muted}}>Estimated wait: Calculating wait time</Text>
    <Text style={{color:palette.accent,fontWeight:'700'}}>Your place in line is reserved.</Text>
   </>:null}
   {error?<View style={{alignItems:'center',gap:9}}>
    <Text selectable accessibilityLabel={`Error code ${issue.code}`}
     style={{color:palette.accent,fontWeight:'900',fontSize:18}}>{issue.code}</Text>
    <Text style={{color:palette.muted,textAlign:'center'}}>{issue.recovery}</Text>
    {status.correlationId?<Text selectable style={{color:palette.muted,fontSize:12}}>
      Reference: {status.correlationId}</Text>:null}
    <Pressable accessibilityRole="button" accessibilityLabel="Retry waiting room" onPress={retry}
     disabled={busy} style={{paddingHorizontal:27,paddingVertical:14,borderRadius:24,
      backgroundColor:palette.accent,opacity:busy?.6:1}}>
     <Text style={{fontWeight:'900',color:'#0d1720'}}>Try Again</Text>
    </Pressable>
   </View>:<ActivityIndicator color={palette.accent} size="large"
     accessibilityLabel="Waiting for server admission"/>}
   <Text accessibilityLiveRegion="polite" style={{color:palette.muted}}>
    Connection: {error?'Reconnecting':status.state==='CHECKING'?'Checking':'Connected'}</Text>
  </View>
  <Text style={{color:palette.muted,textAlign:'center',fontSize:14}}>
    You'll enter automatically when it's your turn.</Text>
 </View>;
}
