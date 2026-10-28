// Pure URI classifier shared by the app-root relay and native Pup Account UI.
const CALLBACK=/^discord-1547982688898777118:\/authorize\/callback(?:\?|$)/;
const STATE=/^pcw1_[A-Za-z0-9_-]{32,64}$/;
export function isWebDiscordCallback(url){
 if(typeof url!=='string'||!CALLBACK.test(url))return false;
 const query=url.includes('?')?url.slice(url.indexOf('?')+1):'';
 try{return STATE.test(new URLSearchParams(query).get('state')||'');}
 catch{return false;}
}
