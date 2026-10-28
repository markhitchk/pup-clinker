import React,{useCallback,useState} from 'react';
import {NativeModules} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {registerRootComponent} from 'expo';
import PuppyQueueRoom from './src/production/PuppyQueueRoom.jsx';

// This minimal bootstrap deliberately does NOT import App.js at module scope.
// Pup Account, cloud saves, games and notifications do not mount until admitted.
const beta=NativeModules.PuppyBetaAccount?.isBeta===true;
const queueEnabled=beta&&process.env.EXPO_PUBLIC_PUP_QUEUE_ENABLED==='true';
const queueEndpoint=process.env.EXPO_PUBLIC_PUP_QUEUE_URL||'';
const publishableKey=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'';

// Preserve the default fatal crash handler and store only a public error code.
try{
 const errors=globalThis.ErrorUtils;
 if(errors?.getGlobalHandler&&errors?.setGlobalHandler){
  const previous=errors.getGlobalHandler();
  errors.setGlobalHandler((error,isFatal)=>{
   if(isFatal){
    try{Promise.resolve(NativeModules.PuppyBetaAccount?.recordStartupCrash?.('PUPPY-CRASH-902')).catch(()=>{});}catch{}
   }
   previous?.(error,isFatal);
  });
 }
}catch{/* Diagnostic recording must never prevent startup. */}

function PuppyEntry(){
 const [admitted,setAdmitted]=useState(!queueEnabled);
 const onAdmitted=useCallback(()=>setAdmitted(true),[]);
 // In disabled-by-default builds, nothing changes for the production game.
 // In staged builds, the full game does not even evaluate until admission.
 if(!admitted)return <SafeAreaProvider>
  <PuppyQueueRoom native={NativeModules.PuppyBetaAccount} endpoint={queueEndpoint}
   publishableKey={publishableKey} onAdmitted={onAdmitted}/>
 </SafeAreaProvider>;
 const App=require('./App.js').default;
 return <App/>;
}
registerRootComponent(PuppyEntry);
