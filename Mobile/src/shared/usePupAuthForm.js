// Canonical cross-platform Pup Account form model.
// Keep identical in website src/shared and Android Mobile/src/shared.
// UI and secure session transports remain platform-specific.
import {useState} from 'react';

export const JOIN_SOURCE_OPTIONS=Object.freeze([
 ['friend','A friend, family member or another player'],
 ['discord','A Discord server or message'],
 ['youtube','YouTube'],['tiktok','TikTok'],['instagram','Instagram'],
 ['reddit','Reddit'],['google','Google search'],['play_store','Google Play Store'],
 ['other','Somewhere else']
]);
export const USERNAME_PATTERN=/^[A-Za-z0-9_-]{3,20}$/;
export const SUPPORT_EMAIL_PATTERN=/^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_SYMBOLS=new Set(["!","@","#","$","%","^","&","*","(",")","_","+","-","=","[","]","{","}",";","'","\\",":","\"","|","<",">","?",",",".","/","`","~"]);

export function passwordRules(value=''){
 const password=String(value);
 return [
  {label:'8–128 characters',passed:password.length>=8&&password.length<=128},
  {label:'Uppercase letter',passed:/[A-Z]/.test(password)},
  {label:'Lowercase letter',passed:/[a-z]/.test(password)},
  {label:'Number',passed:/[0-9]/.test(password)},
  {label:'Special character',passed:[...password].some(c=>PASSWORD_SYMBOLS.has(c))}
 ];
}
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
