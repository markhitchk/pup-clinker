import {useCallback,useEffect,useState} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
const EVENTS='puppy-local-diagnostics',CRASH='puppy-local-error';
export function useLocalDiagnostics(g,screen,ready){
 const [summary,setSummary]=useState('No local diagnostics recorded.');
 const clear=useCallback(async()=>{await AsyncStorage.multiRemove([EVENTS,CRASH]);setSummary('Local diagnostics cleared.');},[]);
 useEffect(()=>{
  if(!ready)return;
  let alive=true;
  async function update(){
   if(!g.anonymousDiagnosticsEnabled)await AsyncStorage.removeItem(EVENTS);
   else{
    const raw=await AsyncStorage.getItem(EVENTS);let entries=[];
    try{const data=JSON.parse(raw);if(Array.isArray(data))entries=data.slice(-49);}catch{}
    entries.push({screen,at:Date.now()});await AsyncStorage.setItem(EVENTS,JSON.stringify(entries));
   }
   if(!g.crashReportsEnabled)await AsyncStorage.removeItem(CRASH);
   const data=await AsyncStorage.multiGet([EVENTS,CRASH]);
   if(alive)setSummary((g.anonymousDiagnosticsEnabled?'Screen history recorded locally. ':'Diagnostics are off. ')+(data[1][1]?'One local error summary saved.':'No local error summary saved.'));
  }
  void update().catch(()=>{if(alive)setSummary('Local diagnostics storage is unavailable.');});
  return ()=>{alive=false;};
 },[ready,screen,g.anonymousDiagnosticsEnabled,g.crashReportsEnabled]);
 useEffect(()=>{
  const errors=globalThis.ErrorUtils;
  if(!ready||!g.crashReportsEnabled||!errors?.getGlobalHandler||!errors?.setGlobalHandler)return;
  const previous=errors.getGlobalHandler();
  const handler=(error,fatal)=>{
   // No account, save balances, tokens or full stack in the optional summary.
   void AsyncStorage.setItem(CRASH,JSON.stringify({at:Date.now(),name:String(error?.name||'Error').slice(0,80),fatal:!!fatal})).catch(()=>{});
   previous(error,fatal);
  };
  errors.setGlobalHandler(handler);
  return ()=>{if(errors.getGlobalHandler()===handler)errors.setGlobalHandler(previous);};
 },[ready,g.crashReportsEnabled]);
 return {summary,clear};
}
