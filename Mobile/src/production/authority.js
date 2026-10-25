// Production-only handoff policy. The former local guest save is NEVER treated
// as a verified Pup Account, even if it has the correct game economy fields.
export const KOTLIN_PACKAGE='com.harleytg.puppyclicker';
export const PRODUCTION_FUNCTION_URL=
 'https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/pup-account';

export function classifyOriginalInstall(probe){
 if(!probe||typeof probe!=='object'||Array.isArray(probe))
  return {status:'NATIVE_UNAVAILABLE',canPlay:false,message:'The native PupEye integration is missing.'};
 if(probe.packageName!==KOTLIN_PACKAGE)
  return {status:'SIGNED_UPGRADE_REQUIRED',canPlay:false,
   message:'This beta installs beside the Play app and cannot read its private data. Do not uninstall Kotlin; existing accounts require an authorized device transfer or a correctly signed Play upgrade.'};
 if(!probe.hasOriginalSave||!probe.hasSealedSave)
  return {status:'ORIGINAL_SAVE_MISSING',canPlay:false,
   message:'Original verified Kotlin progression was not found. Nothing has been created or overwritten.'};
 if(!probe.hasOriginalEncryptionKey||!probe.hasAuthorityState||!probe.hasOriginalSigningKey)
  return {status:'PUPEYE_IDENTITY_INCOMPLETE',canPlay:false,
   message:'The original PupEye identity is incomplete; automatic recovery is blocked to protect existing progress.'};
 if(!probe.hasOriginalSession)
  return {status:'DISCORD_REAUTH_REQUIRED',canPlay:false,
   message:'No original PupEye backend session is available. Secure Discord/device reauthorization is required.'};
 return {status:'LEGACY_IDENTITY_PRESENT',canPlay:false,
  message:'Original Kotlin save and device keys were detected. Checking the live Pup Account without modifying either save.'};
}
export function classifyVerifiedAccount(value){
 if(value?.verified!==true||typeof value?.accountId!=='string'||!value.accountId||
 typeof value?.username!=='string'||!value.username)
  return {status:'ACCOUNT_NOT_VERIFIED',canPlay:false,
   message:'Pup Account was not verified by the authoritative server.'};
 if(value.cloudSaveExists!==true||!Number.isSafeInteger(value.cloudRevision)||value.cloudRevision<1)
  return {status:'CLOUD_RESTORE_REQUIRED',canPlay:false,
   message:'The account was verified, but an authoritative cloud save must be recovered before gameplay.'};
 return {status:'SERVER_ACCOUNT_CONFIRMED',canPlay:false,
  message:'Pup Account and cloud revision verified. Progression migration and signed game transactions must be completed before gameplay unlocks.'};
}
// This policy intentionally does not authorize local gameplay. A signed,
// revision-aware native gameplay service must replace the guest engine first.
export const mayEnterGameplay=()=>false;
