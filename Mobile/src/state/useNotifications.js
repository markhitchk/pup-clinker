import {useCallback,useEffect,useRef,useState} from 'react';
import {AppState,Linking,Platform} from 'react-native';
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
  }catch(e){setError('Could not request notification permission. '+e.message);return false;}
 },[channels]);
 useEffect(()=>{
  if(!ready)return;
  const check=()=>{act({type:'checkNotifications',now:Date.now()});void refreshPermission().catch(e=>setError(e.message));};
  check();
  const sub=AppState.addEventListener('change',status=>{if(status==='active'){setAwayAt(0);check();}else if(status==='background'){setAwayAt(Date.now());}});
  const timer=setInterval(()=>act({type:'checkNotifications',now:Date.now()}),60000);
  return ()=>{sub.remove();clearInterval(timer);};
 },[ready,act,refreshPermission]);
 useEffect(()=>{
  if(Platform.OS!=='android'||!ready)return;
  Notifications.setNotificationHandler({handleNotification:async()=>({shouldShowBanner:true,shouldShowList:true,shouldPlaySound:false,shouldSetBadge:false})});
  const received=Notifications.addNotificationReceivedListener(()=>latest.current.act({type:'checkNotifications',now:Date.now()}));
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
   for(const item of plan)await Notifications.scheduleNotificationAsync({identifier:item.id,content:{title:item.title,body:item.body,data:{category:item.category}},trigger:{type:Notifications.SchedulableTriggerInputTypes.DATE,date:new Date(item.at),channelId:item.category}});
  }).catch(e=>setError('Notification scheduling failed. '+e.message));
 },[ready,permission,awayAt,game.dailyRewardNotifications,game.gameEventNotifications,game.puppyAttentionNotifications,game.parkActive,game.parkReadyAtMs,game.lastDailyClaimDay,channels]);
 const sendTest=useCallback(async()=>{
  act({type:'testNotification',now:Date.now()});
  try{
   if(!await refreshPermission()){setError('Test added to your inbox. Allow Android notifications to show it outside the app.');return;}
   await channels();await Notifications.scheduleNotificationAsync({content:{title:'Puppy Clicker',body:'Test notification — notifications are working.'},trigger:{channelId:'system'}});setError('');
  }catch(e){setError('Test saved to inbox; Android delivery failed. '+e.message);}
 },[act,channels,refreshPermission]);
 return {permission,error,request,sendTest,openSystemSettings:()=>Linking.openSettings(),check:()=>act({type:'checkNotifications',now:Date.now()})};
}
