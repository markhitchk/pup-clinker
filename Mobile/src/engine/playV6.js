// Part 1: Kotlin launcher V6Play/V6Header/V6Wallet/V6NeedPill contract.
// Source: App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt
// Guest-only preview; PupEye/server enforcement remains out of scope.
export const V6_PLAY_LAYOUT=Object.freeze({
 headerLogo:50,
 stageHeight:365,
 stageRadius:30,
 circleSize:258,
 portraitSize:238,
 needRadius:14,
 needMeterHeight:3,
 stageBottomLabelInset:14,
 idleDown:5,
 idleUp:-3,
 idleHalfCycleMs:1700,
 tapPressedScale:0.89,
 ticketVisibleMs:2750
});
export function v6PlaySizing(windowWidth){
 const width=Number.isFinite(windowWidth)?Math.max(0,windowWidth):360;
 const circle=Math.min(V6_PLAY_LAYOUT.circleSize,Math.max(0,width-32));
 return {circle,portrait:Math.max(0,circle-20),stageHeight:V6_PLAY_LAYOUT.stageHeight};
}
// Kotlin stores currency as signed 64-bit Long. Keep 64-bit account balances
// lossless for rendering if the server supplies canonical decimal strings.
// Do NOT pass JavaScript BigInt values into JSON guest saves; gameplay still
// requires a separate audited 64-bit migration before production.
export function v6Format(value,compact=true){
 const isDecimal=typeof value==='string'&&/^(?:0|[1-9][0-9]{0,18})$/.test(value);
 if(!isDecimal&&typeof value!=='bigint'&&!Number.isFinite(value))return '0';
 let amount;
 try{amount=isDecimal?BigInt(value):typeof value==='bigint'?value:BigInt(Math.trunc(value));}
 catch{return '0';}
 if(amount<0n)amount=0n;
 if(!compact)return amount.toLocaleString('en-US');
 // Half-up rounding, using integer arithmetic to avoid Number precision loss.
 function short(unit,places,suffix){
  const multiplier=10n**BigInt(places);
  const scaled=(amount*multiplier+unit/2n)/unit;
  const whole=scaled/multiplier;
  const fraction=(scaled%multiplier).toString().padStart(places,'0');
  return whole.toString()+'.'+fraction+suffix;
 }
 if(amount>=1_000_000_000_000n)return short(1_000_000_000_000n,2,'T');
 if(amount>=1_000_000_000n)return short(1_000_000_000n,2,'B');
 if(amount>=1_000_000n)return short(1_000_000n,2,'M');
 if(amount>=1_000n)return short(1_000n,1,'K');
 return amount.toString();
}
export function v6CooldownSeconds(game,now=Date.now()){
 const until=Number(game?.cooldownUntilMs)||0;
 return Number.isFinite(until)?Math.max(0,Math.ceil((until-now)/1000)):0;
}
export function v6PlayTapLabel(game,now=Date.now()){
 const remaining=v6CooldownSeconds(game,now);
 if(remaining)return '🐶👁️ Fair-play cooldown · '+remaining+'s';
 return 'Tap '+game.puppyName+' · +'+v6Format(game.clickPower,game.compactNumbers!==false)+' 🍪';
}
