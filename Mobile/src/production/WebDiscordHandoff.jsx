import React,{useEffect} from 'react';
import {Alert,Linking} from 'react-native';
import {isWebDiscordCallback} from './webDiscordCallback.js';

// Web uses exactly the same Discord redirect as Android. This component is
// mounted at the app root and forwards ONLY the Discord authorization code,
// never a PupEye session, OAuth verifier, account secret, or device key.
const RELAY='https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/pup-web-discord-relay';
const KEY='sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const CODE=/^[A-Za-z0-9_-]{8,2048}$/;
let latestUrl='';
async function forward(url){
 if(!isWebDiscordCallback(url)||url===latestUrl)return;
 latestUrl=url;
 const params=new URLSearchParams(url.slice(url.indexOf('?')+1));
 const state=params.get('state'),code=params.get('code');
 if(!CODE.test(code||'')){
  Alert.alert('Puppy Clicker web login','Discord did not return an authorization code. Retry from the website.');
  return;
 }
 try{
  const response=await fetch(RELAY,{
   method:'POST',headers:{'Content-Type':'application/json',apikey:KEY},
   body:JSON.stringify({action:'relay-submit',state,code})
  });
  if(!response.ok)throw Error('Relay unavailable');
  Alert.alert('Puppy Clicker web login','Discord authorization received. Return to the original website browser to finish signing in.',[
   {text:'Stay in app',style:'cancel'},
   {text:'Open Puppy Clicker Web',onPress:()=>void Linking.openURL('https://puppy-clicker.vercel.app/')}
  ]);
 }catch{
  // Do not silently let the native account handler consume a web PKCE code.
  Alert.alert('Web login not completed','Puppy Clicker could not deliver the Discord authorization to your browser. Please retry from the website.');
 }
}
export default function WebDiscordHandoff(){
 useEffect(()=>{
  let active=true;
  void Linking.getInitialURL().then(url=>{if(active&&url)void forward(url);}).catch(()=>{});
  const sub=Linking.addEventListener('url',event=>{if(event?.url)void forward(event.url);});
  return()=>{active=false;sub.remove();};
 },[]);
 return null;
}
