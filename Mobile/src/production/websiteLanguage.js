// Android adapter for the website's shared English/Spanish language catalogue.
// Locale choice is a local presentation preference, never a signed gameplay action.
import {useEffect,useState} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {LANGUAGE_KEY,SUPPORTED_LANGUAGES,normalizeLanguage,t} from './websiteI18n.js';

function systemLanguage(){
 try{return normalizeLanguage(Intl.DateTimeFormat().resolvedOptions().locale);}
 catch{return 'en';}
}
let current=systemLanguage();
let userChanged=false;
let loading=null;
const listeners=new Set();
function notify(){for(const listener of listeners)listener(current);}

async function readSavedLanguage(){
 try{
  const saved=await AsyncStorage.getItem(LANGUAGE_KEY);
  if(!userChanged&&SUPPORTED_LANGUAGES.includes(saved)&&saved!==current){
   current=saved;notify();
  }
 }catch{/* Storage is optional; system language remains the fallback. */}
}
function hydrate(){
 if(!loading)loading=readSavedLanguage();
 return loading;
}
export function selectWebsiteLanguage(value){
 const next=normalizeLanguage(value);
 userChanged=true;
 if(next!==current){current=next;notify();}
 Promise.resolve(AsyncStorage.setItem(LANGUAGE_KEY,next)).catch(()=>{});
}
export function useWebsiteLanguage(){
 const [language,setLanguage]=useState(current);
 useEffect(()=>{
  listeners.add(setLanguage);
  setLanguage(current);
  void hydrate();
  return ()=>{listeners.delete(setLanguage);};
 },[]);
 return {language,setLanguage:selectWebsiteLanguage,t:(key,params)=>t(language,key,params)};
}
