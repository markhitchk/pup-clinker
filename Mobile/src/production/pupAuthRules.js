// Keeps Android Pup Account sign-up requirements aligned with WebRegister.jsx.
// These rules do not replace signed native PupEye server validations.
const allowedPasswordSymbols=new Set(["!","@","#","$","%","^","&","*","(",")","_","+","-","=","[","]","{","}",";","'","\\",":",'"',"|","<",">","?",",",".","/","~",String.fromCharCode(96)]);
export function websitePasswordRules(value=''){
 const pwd=String(value);
 return [
  {label:'8–128 characters',passed:pwd.length>=8&&pwd.length<=128},
  {label:'Uppercase letter',passed:/[A-Z]/.test(pwd)},
  {label:'Lowercase letter',passed:/[a-z]/.test(pwd)},
  {label:'Number',passed:/[0-9]/.test(pwd)},
  {label:'Special character',passed:[...pwd].some(x=>allowedPasswordSymbols.has(x))}
 ];
}
export const DISCOVERY_SOURCES=Object.freeze([
 ['friend','A friend, family member or another player'],['discord','A Discord server or message'],
 ['youtube','YouTube'],['tiktok','TikTok'],['instagram','Instagram'],
 ['reddit','Reddit'],['google','Google search'],['play_store','Google Play Store'],
 ['other','Somewhere else']
]);
export function validDiscoverySource(source,details){
 return (source===''||DISCOVERY_SOURCES.some(([id])=>id===source))
  &&typeof details==='string'&&details.length<=120
  &&![...details].some(char=>char.charCodeAt(0)<32||char.charCodeAt(0)===127)
  &&(source!==''||details.trim()==='');
}
