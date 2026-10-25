// Keep the AFK cadence and care thresholds aligned with
// App/app/src/main/java/com/harleytg/puppyclicker/PuppyAttentionNotifications.kt.
// Local Android reminders never grant currency or bypass account enforcement.
export const ATTENTION_INTERVAL_MS=2*60*60*1000;
const titles=[
 '🐾 Human? Attention required.',
 '🐶 Your puppy is looking for you',
 '🎾 AFK detected: puppy demands playtime',
 '🍪 A very serious puppy request'
];
const careScore=value=>Number.isFinite(value)?Math.max(0,Math.min(100,value)):100;
export function attentionMessage(game,now){
 const puppyName=typeof game?.puppyName==='string'&&game.puppyName.trim()
  ?game.puppyName.trim().slice(0,18):'Buddy';
 const need=Math.min(careScore(game?.happiness),careScore(game?.fullness),careScore(game?.cleanliness));
 const body=need<=34
  ?`${puppyName} says this is now an urgent snack/cuddle situation.`
  :need<=64
   ?`${puppyName} would like some care, snacks, or play time.`
   :`${puppyName} is waiting for attention and has noticed you are AFK.`;
 const index=Math.floor(Math.max(0,now)/ATTENTION_INTERVAL_MS)%titles.length;
 return {title:titles[index],body};
}
