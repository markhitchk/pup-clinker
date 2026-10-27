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
export function v6Format(value,compact=true){
 const n=Number.isFinite(value)?Math.trunc(value):0;
 if(!compact)return n.toLocaleString('en-US');
 if(n>=1_000_000_000_000)return (n/1_000_000_000_000).toFixed(2)+'T';
 if(n>=1_000_000_000)return (n/1_000_000_000).toFixed(2)+'B';
 if(n>=1_000_000)return (n/1_000_000).toFixed(2)+'M';
 if(n>=1_000)return (n/1_000).toFixed(1)+'K';
 return String(n);
}
/**
 * The original Kotlin wallet's two decimals hide legitimate live changes
 * when a Pup Account holds trillions. Keep v6Format unchanged for Kotlin
 * screen parity elsewhere, but show nine decimals in the compact wallet.
 * A capped tap of 10,000 Treats now visibly changes the
 * balance, without mutating or locally inventing server-owned currency.
 */
export function v6WalletFormat(value,compact=true){
 const n=Number.isSafeInteger(value)&&value>=0?value:0;
 if(!compact)return n.toLocaleString('en-US');
 if(n>=1_000_000_000_000)return (n/1_000_000_000_000).toFixed(9)+'T';
 if(n>=1_000_000_000)return (n/1_000_000_000).toFixed(4)+'B';
 if(n>=1_000_000)return (n/1_000_000).toFixed(3)+'M';
 return v6Format(n,true);
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
