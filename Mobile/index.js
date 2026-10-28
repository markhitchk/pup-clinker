import {registerRootComponent} from 'expo';
import {NativeModules} from 'react-native';
import App from './App.js';

// Preserve React Native's default fatal handler, and best-effort persist only
// a public code so the NEXT launch can explain a JS runtime crash. No telemetry.
try {
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
}catch{/* Never make error recording an additional startup failure. */}

registerRootComponent(App);
