// UI-only projection of server-verified PupEye access decisions.
// Never trust a locally cached snapshot as authorization to play.
const blocked=new Set(['GLOBAL_BANNED','PUP_ACCOUNT_BLOCKED','DEVICE_BANNED','INSTALLATION_BANNED']);
const review=new Set(['REVIEW_REQUIRED','PUPEYE_REVIEW_REQUIRED','INTEGRITY_REVIEW_REQUIRED']);
export const normalizedAccessCode=value=>String(value||'').replace(/^BETA_/,'').replace(/[^A-Z0-9_]/g,'').slice(0,64);

export function startupAccess({account,cloudState,syncStatus,ready}){
 const code=normalizedAccessCode(syncStatus?.errorCode);
 if(account?.status==='BANNED'||blocked.has(code))
  return {phase:'banned',title:'Access restricted',message:'This account or device has been restricted by Puppy Clicker. Gameplay is unavailable. Contact Support if you believe this is an error.'};
 if(account?.status==='REVIEW_REQUIRED'||review.has(code))
  return {phase:'review',title:'Account review required',message:'PupEye has temporarily restricted access while the account is reviewed. Contact Support for assistance.'};
 if(account?.status==='CHECKING'||(!account?.status&&cloudState==='NOT_LOADED'))
  return {phase:'loading',title:'Starting Puppy Clicker',message:'Checking your account and restoring your puppy progress…'};
 if(account?.status==='NEEDS_ACCOUNT'||cloudState==='SIGN_IN_REQUIRED')
  return {phase:'signin',title:'Welcome to Puppy Clicker',message:'Sign in with your Pup Account or Discord to continue.'};
 if(account?.status==='NATIVE_MODULE_MISSING')
  return {phase:'error',title:'Update required',message:'This installed build cannot connect to Pup Account. Install an updated official build.'};
 if(account?.status==='CONNECTION_ERROR'||account?.status==='CACHED'||cloudState==='CACHED_OFFLINE'||syncStatus?.state==='ERROR'||syncStatus?.state==='SAVED_DISPLAY_ERROR')
  return {phase:'offline',title:'Connection unavailable',message:'Puppy Clicker could not verify your account. Check your connection and try again. Your saved progress is not being replaced.'};
 if(account?.status!=='VERIFIED'||!ready)
  return {phase:'loading',title:'Preparing your game',message:'Restoring your puppy data securely…'};
 if(account.gameplayWritable!==true||cloudState==='SERVER_SNAPSHOT_READ_ONLY'||syncStatus?.state==='BLOCKED')
  return {phase:'error',title:'Gameplay unavailable',message:'Your account is connected, but game progress cannot be saved right now. Retry or contact Support.'};
 if(cloudState!=='LIVE_SYNCED'&&cloudState!=='NO_CLOUD_SAVE')
  return {phase:'loading',title:'Restoring your game',message:'Checking your saved progress…'};
 return {phase:'ready',title:'Ready',message:''};
}
