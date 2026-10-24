// Six-step sequence from PuppyOnboardingModel.kt; guest preview cannot claim native OAuth completion.
export const ONBOARDING_STEPS=['WELCOME','PLAYER_SETUP','PERSONALIZE','PRIVACY','NOTIFICATIONS','READY'];
export const normalizeLocalGuestName=raw=>{
 const name=String(raw||'').replace(/\s+/g,' ').trim().slice(0,24);
 return name||null;
};
export function validBirthday(month,day){
 if(month===0&&day===0)return true;
 if(!Number.isInteger(month)||!Number.isInteger(day)||month<1||month>12||day<1)return false;
 const max=[31,29,31,30,31,30,31,31,30,31,30,31][month-1];
 return day<=max;
}
