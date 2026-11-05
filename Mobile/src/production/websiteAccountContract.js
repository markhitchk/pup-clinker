// GENERATED from website Pup Account login, registration and Join Source sources.
// Cosmetic/validation contract only: Android retains signed native PupEye credential transport.
// Do not hand-edit. Run scripts/sync-website-native-ui.mjs.
const PASSWORD_SYMBOLS=new Set(["!","@","#","$","%","^","&","*","(",")","_","+","-","=","[","]","{","}",";","'","\\",":","\"","|","<",">","?",",",".","/","`","~"]);
export const websitePasswordRules=value=>[
 {label:"8–128 characters",passed:value.length>=8&&value.length<=128},
 {label:"Uppercase letter",passed:/[A-Z]/.test(value)},
 {label:"Lowercase letter",passed:/[a-z]/.test(value)},
 {label:"Number",passed:/[0-9]/.test(value)},
 {label:"Special character",passed:[...value].some(character=>PASSWORD_SYMBOLS.has(character))}
];
export const WEBSITE_JOIN_SOURCE_OPTIONS=Object.freeze([
 ["friend","A friend, family member or another player"],
 ["discord","A Discord server or message"],
 ["youtube","YouTube"],["tiktok","TikTok"],["instagram","Instagram"],
 ["reddit","Reddit"],["google","Google search"],["play_store","Google Play Store"],
 ["other","Somewhere else"]
]);
export const WEBSITE_ACCOUNT_COPY=Object.freeze({
  "recoverTitle": "Recover your Pup Account",
  "registerTitle": "Your puppy journey starts here.",
  "signinTitle": "Welcome back to your puppy world.",
  "intro": "One Pup Account for your puppies, rewards and linked progress across the website and Android.",
  "signinDescription": "Sign in using the same Pup Account you use on Android. Your linked progress is preserved.",
  "benefits": [
    "🐾 Your puppy collection",
    "🔒 Verified account protection",
    "☁️ Linked cloud progress"
  ],
  "eyebrow": "PUPPY CLICKER · PUP ACCOUNT"
});
