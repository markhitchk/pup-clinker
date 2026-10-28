import {useCallback,useEffect,useRef,useState} from 'react';
import {NativeModules} from 'react-native';
import {DEFAULT_PREFERENCES,sanitizePreferences} from '../engine/settings.js';
import {validBirthday} from '../engine/onboarding.js';

const key=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const defaults={...DEFAULT_PREFERENCES,uiScale:'compact',setupStep:0,setupComplete:false,
 birthdayMonth:0,birthdayDay:0,privacyConsentVersion:0};
const integers=['setupStep','birthdayMonth','birthdayDay','privacyConsentVersion'];
function normalize(raw){
 const setup={...defaults,...sanitizePreferences({...defaults,...raw})};
 for(const name of integers)if(Number.isInteger(raw?.[name]))setup[name]=raw[name];
 setup.setupStep=Math.max(0,Math.min(5,setup.setupStep));
 setup.setupComplete=raw?.setupComplete===true;
 return setup;
}

// Only UI/profile choices are stored here. This hook never creates gameplay
// credentials, edits balances, or uploads the read-only cloud projection.
export function useAccountOnboarding({enabled,account,activateAccount}){
 const [setup,setSetup]=useState({...defaults});
 const [ready,setReady]=useState(!enabled),[busy,setBusy]=useState(false),[problem,setProblem]=useState('');
 const current=useRef(setup),committed=useRef(setup),queue=useRef(Promise.resolve()),working=useRef(false);
 const native=()=>NativeModules.PuppyBetaAccount;
 const reload=useCallback(async()=>{
  if(!enabled)return true;
  setProblem('');
  try{
   const snapshot=normalize(await native().readSetup());
   current.current=snapshot;committed.current=snapshot;setSetup(snapshot);setReady(true);
   return true;
  }catch{
   setProblem('Unable to load startup settings. Tap Try Again.');
   return false;
  }
 },[enabled]);
 useEffect(()=>{if(enabled)void reload();},[enabled,reload]);
 const persist=useCallback(patch=>{
  const next={...current.current,...patch};current.current=next;setSetup(next);
  const write=queue.current.catch(()=>{}).then(async()=>{
   await native().saveSetup(JSON.stringify(next));committed.current=next;
  });
  queue.current=write;
  return write.catch(error=>{
   if(current.current===next){current.current=committed.current;setSetup(committed.current);}
   throw error;
  });
 },[]);
 const update=useCallback(async patch=>{
  const safe={};
  for(const name of Object.keys(DEFAULT_PREFERENCES))if(Object.hasOwn(patch,name))
   safe[name]=sanitizePreferences({...current.current,...patch})[name];
  for(const name of ['birthdayMonth','birthdayDay'])if(Number.isInteger(patch[name]))safe[name]=patch[name];
  try{await persist(safe);setProblem('');return true;}
  catch{setProblem('Could not save your setup choices. Please retry.');return false;}
 },[persist]);
 const next=useCallback(async()=>{
  if(working.current||!ready)return;
  working.current=true;setBusy(true);setProblem('');
  try{
   const s=current.current;if(s.setupComplete||s.setupStep>=5)return;
   if(s.setupStep===1){
    if(account?.status!=='VERIFIED'){setProblem('Sign in to your Pup Account before continuing.');return;}
    if(account.legalAccepted!==true){setProblem('Accept the Terms of Use and Privacy Policy in your Pup Account first.');return;}
    if(!await activateAccount(account)){setProblem('Unable to load your Pup Account cloud save. Check your connection and try again.');return;}
   }
   if(s.setupStep===2&&!validBirthday(s.birthdayMonth,s.birthdayDay)){setProblem('Choose both a valid month and day, or skip birthday.');return;}
   await persist({setupStep:s.setupStep+1,...(s.setupStep===3?{privacyConsentVersion:5}:{})});
  }catch{setProblem('Could not save setup. Please retry.');}
  finally{working.current=false;setBusy(false);}
 },[ready,account,activateAccount,persist]);
 const back=useCallback(async()=>{
  if(working.current)return;
  try{await persist({setupStep:Math.max(0,current.current.setupStep-1)});setProblem('');}
  catch{setProblem('Could not save setup. Please retry.');}
 },[persist]);
 const review=useCallback(async()=>{
  if(working.current)return;
  try{await persist({setupStep:1});setProblem('');}catch{setProblem('Could not save setup. Please retry.');}
 },[persist]);
 const finish=useCallback(async()=>{
  const s=current.current;
  if(working.current||!ready||s.setupComplete||s.setupStep!==5)return;
  if(account?.status!=='VERIFIED'||account.legalAccepted!==true||s.privacyConsentVersion<5||!validBirthday(s.birthdayMonth,s.birthdayDay)){
   setProblem('Review your account and setup before continuing.');return;
  }
  working.current=true;setBusy(true);setProblem('');
  try{
   await queue.current;
   if(!await activateAccount(account)){setProblem('Unable to verify your Pup Account cloud save. Please retry.');return;}
   await native().finishSetup(key);
   const done={...current.current,setupComplete:true};current.current=done;setSetup(done);
  }catch{setProblem('Pup Account verification or setup could not complete. Please retry.');}
  finally{working.current=false;setBusy(false);}
 },[ready,account,activateAccount]);
 return {setup,ready,busy,problem,reload,update,next,back,review,finish};
}
