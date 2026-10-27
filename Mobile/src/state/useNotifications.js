import {useCallback,useEffect,useRef,useState} from 'react';
import {AppState,Linking,Platform,ToastAndroid} from 'react-native';
import * as Notifications from 'expo-notifications';
import {reminderPlan} from '../engine/inbox.js';

export function useNotifications({game,ready,act,onOpen}){
 const [permission,setPermission]=useState('unknown');
 const [error,setError]=useState('');
 const [awayAt,setAwayAt]=useState(AppState.currentState==='active'?0:Date.now());
 const latest=useRef({game,act,onOpen});latest.current={game,act,onOpen};
 const queue=useRef(Promise.resolve());
 const refreshPermission=useCallback(async()=>{
  if(Platform.OS!=='android'){setPermission('unsupported');return false;}
  const status=await Notifications.getPermissionsAsync();
  setPermission(status.granted?'granted':status.canAskAgain?'undetermined':'denied');return status.granted;
 },[]);
 const channels=useCallback(async()=>{
  if(Platform.OS!=='android')return;
  for(const [id,name] of [['reward','Daily Rewards'],['pup','Game Events & Puppy Attention'],['system','App Updates & System']])
   await Notifications.setNotificationChannelAsync(id,{name,importance:Notifications.AndroidImportance.DEFAULT});
 },[]);
 const request=useCallback(async()=>{
  try{
   await channels();const status=await Notifications.requestPermissionsAsync();
   setPermission(status.granted?'granted':status.canAskAgain?'undetermined':'denied');
   return status.granted;
  }catch(e){setError('Could not request Android notification permission.');return false;}
 },[channels]);
 useEffect(()=>{
  if(!ready)return;
  const check=()=>{act({type:'checkNotifications',now:Date.now()});void refreshPermission().catch(e=>setError('Could not check Android notification settings.'));};
  check();
  const sub=AppState.addEventListener('change',status=>{if(status==='active'){setAwayAt(0);check();}else if(status==='background'){setAwayAt(Date.now());}});
  const timer=setInterval(()=>act({type:'checkNotifications',now:Date.now()}),60000);
  return ()=>{sub.remove();clearInterval(timer);};
 },[ready,act,refreshPermission]);
 useEffect(()=>{
  if(Platform.OS!=='android')return;
  Notifications.setNotificationHandler({handleNotification:async()=>({shouldShowBanner:true,shouldShowList:true,shouldPlaySound:false,shouldSetBadge:false})});
  const received=Notifications.addNotificationReceivedListener(notification=>{
   latest.current.act({type:'checkNotifications',now:Date.now()});
   const content=notification?.request?.content;
   if(!content)return;
   const data=content.data||{};
   latest.current.act({type:'addNotification',notification:{
    id:String(data.noticeId||notification.request.identifier||('android:'+Date.now())),
    category:['reward','pup','system'].includes(data.category)?data.category:'system',
    title:String(content.title||'Puppy Clicker'),body:String(content.body||''),createdAt:Date.now()
   }});
  });
  const open=()=>{latest.current.act({type:'checkNotifications',now:Date.now()});latest.current.onOpen();};
  const response=Notifications.addNotificationResponseReceivedListener(()=>{open();void Notifications.clearLastNotificationResponseAsync();});
  let alive=true;
  void Notifications.getLastNotificationResponseAsync().then(result=>{if(alive&&result){open();void Notifications.clearLastNotificationResponseAsync();}}).catch(e=>setError(e.message));
  return ()=>{alive=false;received.remove();response.remove();Notifications.setNotificationHandler(null);};
 },[ready]);
 useEffect(()=>{
  if(Platform.OS!=='android')return;
  const plan=ready&&permission==='granted'?reminderPlan({...game,puppyAttentionNotifications:game.puppyAttentionNotifications&&awayAt>0},awayAt||Date.now()):[];
  queue.current=queue.current.catch(()=>{}).then(async()=>{
   await channels();
   for(const id of ['puppy-daily','puppy-park','puppy-attention'])await Notifications.cancelScheduledNotificationAsync(id);
   for(const item of plan){
    const trigger=item.repeatSeconds
     ?{type:Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,seconds:item.repeatSeconds,repeats:true,channelId:item.category}
     :{type:Notifications.SchedulableTriggerInputTypes.DATE,date:new Date(item.at),channelId:item.category};
    await Notifications.scheduleNotificationAsync({identifier:item.id,content:{title:item.title,body:item.body,data:{category:item.category}},trigger});
   }
  }).catch(e=>setError('Some reminders could not be scheduled. Check Android notification settings.'));
 },[ready,permission,awayAt,game.dailyRewardNotifications,game.gameEventNotifications,game.puppyAttentionNotifications,game.parkActive,game.parkReadyAtMs,game.lastDailyClaimDay,channels]);
 const publish=useCallback(async({id,category='system',title='Puppy Clicker',body='',toast=false,tray=true})=>{
  const note={id:id||'event:'+Date.now(),category:['system','reward','pup'].includes(category)?category:'system',
   title:String(title).slice(0,80),body:String(body).slice(0,220),createdAt:Date.now()};
  latest.current.act({type:'addNotification',notification:note});
  if(toast&&Platform.OS==='android'&&AppState.currentState==='active')
   ToastAndroid.show(note.body,ToastAndroid.SHORT);
  if(tray&&Platform.OS==='android'&&permission==='granted'){
   try{
    await channels();
    await Notifications.scheduleNotificationAsync({content:{title:note.title,body:note.body,data:{category:note.category,noticeId:note.id}},trigger:null});
   }catch{setError('Android notification could not be delivered. Check your notification settings.');}
  }
 },[channels,permission,ready]);
 const sendTest=useCallback(async()=>{
  act({type:'testNotification',now:Date.now()});
  try{
   if(!await refreshPermission()){setError('Test added to your inbox. Allow Android notifications to show it outside the app.');return;}
   await channels();await Notifications.scheduleNotificationAsync({content:{title:'Puppy Clicker',body:'Test notification — notifications are working.'},trigger:{channelId:'system'}});setError('');
  }catch(e){setError('Test saved to the panel; Android notification delivery failed.');}
 },[act,channels,refreshPermission]);
 return {permission,error,request,sendTest,publish,openSystemSettings:()=>Linking.openSettings(),check:()=>act({type:'checkNotifications',now:Date.now()})};
}
