/**
 * A beta screen is never interactive until a freshly signed account and
 * cloud snapshot have finished loading. Cached previews are not authorization.
 */
export function startupGate({account,cloudState,setupReady,setupProblem,gameReady}){
 if(!setupReady&&setupProblem)return 'setup-error';
 if(account?.status==='CHECKING'||cloudState==='NOT_LOADED'||cloudState==='CACHED_VERIFYING'||!setupReady)return 'loading';
 if(cloudState==='CONNECTION_ERROR'||cloudState==='CACHED_OFFLINE'||
  account?.status==='CONNECTION_ERROR'||account?.status==='NATIVE_MODULE_MISSING'||
  account?.status==='DEVICE_TRANSFER_REQUIRED')return 'connection-error';
 if(cloudState==='SIGN_IN_REQUIRED'||account?.status==='NEEDS_ACCOUNT'||
  account?.status==='NEEDS_DISCORD'||account?.status==='NEEDS_CREDENTIALS')return 'signin';
 if(account?.status!=='VERIFIED')return 'signin';
 if(account.legalAccepted!==true)return 'legal';
 if(!gameReady)return 'loading';
 return 'ready';
}
