// Android React form model aligned with the existing Pup Account website flow.
// The website remains unchanged; native auth uses a signed PupEye transport.
import {useState} from 'react';

import {websitePasswordRules,WEBSITE_JOIN_SOURCE_OPTIONS} from '../production/websiteAccountContract.js';
export const JOIN_SOURCE_OPTIONS=WEBSITE_JOIN_SOURCE_OPTIONS;
export const passwordRules=websitePasswordRules;
export const USERNAME_PATTERN=/^[A-Za-z0-9_-]{3,20}$/;
export const SUPPORT_EMAIL_PATTERN=/^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export function registrationError({username='',password='',confirm='',contactEmail='',terms=false,privacy=false}={}){
 if(!USERNAME_PATTERN.test(String(username).trim()))
  return 'Username must be 3–20 letters, numbers, underscores or hyphens.';
 if(passwordRules(password).some(rule=>!rule.passed))
  return 'Password must be 8–128 characters and include uppercase and lowercase letters, a number, and a special character.';
 if(password!==confirm)return 'Passwords do not match.';
 const email=String(contactEmail).trim();
 if(email&&(!SUPPORT_EMAIL_PATTERN.test(email)||email.length>254))
  return 'Enter a valid optional support email.';
 if(!terms||!privacy)return 'Accept both the Terms of Use and Privacy Policy.';
 return '';
}
export function loginError(login,password){
 if(!String(login||'').trim()||!password)return 'Enter your Pup username and password.';
 return '';
}
export function usePupAuthForm(initialMode='signin'){
 const [mode,setMode]=useState(initialMode);
 const [username,setUsername]=useState('');
 const [login,setLogin]=useState('');
 const [password,setPassword]=useState('');
 const [confirm,setConfirm]=useState('');
 const [contactEmail,setContactEmail]=useState('');
 const [joinSource,setJoinSource]=useState('');
 const [joinDetails,setJoinDetails]=useState('');
 const [terms,setTerms]=useState(false);
 const [privacy,setPrivacy]=useState(false);
 const clearSecrets=()=>{setPassword('');setConfirm('');};
 const chooseMode=next=>{setMode(next);clearSecrets();};
 return {mode,setMode,chooseMode,username,setUsername,login,setLogin,password,setPassword,
  confirm,setConfirm,contactEmail,setContactEmail,joinSource,setJoinSource,joinDetails,setJoinDetails,
  terms,setTerms,privacy,setPrivacy,clearSecrets};
}
