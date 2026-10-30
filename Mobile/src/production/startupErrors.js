// Player-facing startup failures are a finite, intentionally public catalogue.
// Never interpolate raw exception messages, URLs, tokens, identifiers or save data.
export const STARTUP_ERRORS=Object.freeze({
 'PUPPY-START-101':{title:'Startup settings unavailable',message:'Puppy Clicker could not read your saved app settings.',recovery:'Try Again. Your Pup Account and cloud progress have not been reset.'},
 'PUPPY-START-102':{title:'App component unavailable',message:'A required Android component did not start correctly.',recovery:'Reopen or update Puppy Clicker. If it continues, share this error code with Support.'},
 'PUPPY-NET-701':{title:'Connection unavailable',message:'Puppy Clicker cannot reach the account service right now.',recovery:'Check your internet connection and try again.'},
 'PUPPY-NET-702':{title:'Account service unavailable',message:'Puppy Clicker could not finish verifying your account and saved progress.',recovery:'Try Again. If the problem continues, send this code to Support.'},
 'PUPPY-AUTH-801':{title:'Sign-in required',message:'Your saved sign-in could not be verified.',recovery:'Sign in again. Your cloud save has not been deleted.'},
 'PUPEYE-ID-401':{title:'Account recovery required',message:'PupEye could not authorize this installation to access the account.',recovery:'Use your approved account-recovery process or contact Support. Do not reset your account.'},
 'PUPEYE-SERVER-701':{title:'Account verification blocked',message:'PupEye did not approve the account startup request.',recovery:'Contact Support with this code. Do not attempt to replace protected save data.'},
 'PUPPY-DB-301':{title:'Android update required',message:'This Puppy Clicker version cannot use the current Pup Account database layout.',recovery:'Install a newer official Android beta before reconnecting. Your cloud save has not been reset.'},
 'PUPPY-SAVE-205':{title:'Cloud progress could not be displayed',message:'Your saved progress could not be loaded safely on this version.',recovery:'Update Puppy Clicker or contact Support. The server save was not overwritten.'},
 'PUPPY-CRASH-901':{title:'Previous Android crash detected',message:'Puppy Clicker closed unexpectedly on the previous launch.',recovery:'Try Again. If it crashes repeatedly, share this code with Support.'},
 'PUPPY-CRASH-902':{title:'App interface error',message:'An unexpected error interrupted Puppy Clicker.',recovery:'Try restarting the screen. Your cloud save has not been reset.'},
 'PUPPY-CRASH-903':{title:'Android memory error',message:'Android could not allocate enough memory for Puppy Clicker.',recovery:'Close unused apps, then reopen Puppy Clicker.'},
 'PUPPY-CRASH-904':{title:'Android startup error',message:'A required app component failed while Android was starting the game.',recovery:'Reopen the app or install a newer version. Share this code if it persists.'},
 'PUPPY-START-999':{title:'Unexpected startup problem',message:'Puppy Clicker could not complete startup safely.',recovery:'Try Again or contact Support with this code.'}
});
const has=(x)=>Object.prototype.hasOwnProperty.call(STARTUP_ERRORS,x);
export function publicStartupIssue(code){
 const safe=typeof code==='string'&&has(code)?code:'PUPPY-START-999';
 return {code:safe,...STARTUP_ERRORS[safe]};
}
export function classifyStartupFailure({source='account',error,account,cloudState}={}){
 const code=typeof error?.code==='string'?error.code.toUpperCase():'';
 // Known machine codes only. Raw backend error text is never displayed.
 if(account?.status==='NATIVE_MODULE_MISSING')return publicStartupIssue('PUPPY-START-102');
 if(source==='setup')return publicStartupIssue('PUPPY-START-101');
 if(code.includes('DATABASE_LAYOUT_UNSUPPORTED')||account?.status==='DATABASE_LAYOUT_UNSUPPORTED')
  return publicStartupIssue('PUPPY-DB-301');
 if(source==='render'||source==='javascript')return publicStartupIssue('PUPPY-CRASH-902');
 if(source==='native')return publicStartupIssue('PUPPY-CRASH-901');
 if(code.includes('OWNER_DEVICE_UNAVAILABLE')||code.includes('ON_OTHER_DEVICE')||
  code.includes('DISCORD_ALREADY_LINKED')||account?.status==='DEVICE_TRANSFER_REQUIRED')
  return publicStartupIssue('PUPEYE-ID-401');
 if(code.includes('ACCOUNT_BLOCKED')||code.includes('PROGRESSION_LOCKED')||
  code.includes('INTEGRITY')||code.includes('BAN'))return publicStartupIssue('PUPEYE-SERVER-701');
 if(code.includes('INSTALLATION_REVOKED')||code.includes('LINK_REVOKED')||
  code.includes('SESSION_EXPIRED')||code.includes('SESSION_INVALID'))
  return publicStartupIssue('PUPPY-AUTH-801');
 if(/INVALID_CLOUD_SAVE|INVALID_KOTLIN_SAVE|UNSUPPORTED_KOTLIN_SAVE|MISSING_KOTLIN_PROGRESS|UNSUPPORTED_CLOUD_SAVE|SAVE_JSON/.test(code))
  return publicStartupIssue('PUPPY-SAVE-205');
 if(cloudState==='CACHED_OFFLINE'||code.includes('TIMEOUT')||code.includes('NETWORK_UNAVAILABLE'))
  return publicStartupIssue('PUPPY-NET-701');
 if(account?.status==='CONNECTION_ERROR'||cloudState==='CONNECTION_ERROR'||code.includes('TRANSPORT_FAILURE'))
  return publicStartupIssue('PUPPY-NET-702');
 return publicStartupIssue('PUPPY-START-999');
}
