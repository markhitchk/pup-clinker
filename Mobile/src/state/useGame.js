import {useCallback,useEffect,useReducer,useRef} from 'react';
import {AppState} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {initialState,transition} from './reducer.js';
import {GUEST_SAVE_KEY,parseGuestSave,serializeGuestSave} from './guestSave.js';

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
 useEffect(()=>{
   const sub=AppState.addEventListener('change',status=>{
    if(status==='active')dispatch({type:'resume',now:Date.now()});
    else write();
   });
   return ()=>sub.remove();
 },[write]);
 return {state,act:dispatch};
}
