// These are owner-visible summaries only. Do not expose internal risk scores,
// review case evidence, detector thresholds, device IDs or other player records.
export function fairplayPresentation(value){
 if(!value||typeof value!=="object")return {
  status:"unavailable",title:"Status unavailable",detail:"The server has not confirmed your current fair-play status.",tone:"neutral"
 };
 switch(value.status){
  case "clear":return {status:"clear",title:"No active fair-play warning",detail:"PupEye has not reported a current tapping warning or temporary hold. Routine server-side checks still apply.",tone:"good"};
  case "warning":return {status:"warning",title:"Fair-play warning",detail:"PupEye noticed unusually intense activity. This is a warning, not an account ban.",tone:"warning"};
  case "restricted":return {status:"restricted",title:"Temporary tap hold",detail:"Tap rewards are paused for a limited time. Other gameplay and account access remain available unless a separate restriction applies.",tone:"warning"};
  default:return {status:"unavailable",title:"Status unavailable",detail:"The server returned an unrecognized fair-play state. Please check again.",tone:"neutral"};
 }
}
export function accountStandingPresentation(account){
 if(account?.status==="BANNED")return {title:"Account restricted",tone:"danger",detail:"A moderation restriction is active on this Pup Account."};
 if(account?.status==="VERIFIED")return {title:"Verified Pup Account",tone:"good",detail:"This identity was confirmed by the Puppy Clicker account server."};
 return {title:"Standing not verified",tone:"neutral",detail:"Your current account standing could not be confirmed."};
}
export function gameplayPresentation(account){
 if(account?.status==="BANNED")return {title:"Gameplay restricted",tone:"danger",detail:"The current account restriction prevents protected gameplay on this account."};
 if(account?.status!=="VERIFIED")return {title:"Unknown",tone:"neutral",detail:"Sign in to a verified Pup Account to view gameplay access."};
 if(account.gameplayWritable===true)return {title:"Web gameplay enabled",tone:"good",detail:"Your current account can submit server-authoritative browser gameplay."};
 if(account.gameplayWritable===false)return {title:"Web gameplay unavailable",tone:"neutral",detail:"Your account may need a valid cloud save or a verified Android ownership link."};
 return {title:"Not confirmed",tone:"neutral",detail:"The server has not supplied a gameplay access decision."};
}
export function androidConnectionPresentation(account){
 if(account?.webOnly===true)return {title:"Website Pup Account",detail:"This is a web-origin account. Browser verification is not Android device attestation."};
 if(account?.linkedFromPlay===true)return {title:"Android-linked",detail:"The server recognizes an eligible linked Android PupEye owner installation and cloud save."};
 if(account?.linkedFromPlay===false)return {title:"Android link unavailable",detail:"The linked Android ownership or cloud save could not be confirmed for website gameplay."};
 return {title:"Not required / unknown",detail:"PupEye Web verifies the browser account, not the physical Android device."};
}
