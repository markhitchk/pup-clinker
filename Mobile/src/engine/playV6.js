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
 * Wallet-only formatter for very large, server-owned account balances.
 * A nine-decimal T value can overflow the four-column wallet (e.g. 9000T).
 * Use the next conventional unit, with at most two visible decimals.
 * Actual progress is confirmed independently by a signed save receipt;
 * rounding display values must never round or mutate the stored economy.
 */
const WALLET_UNITS=[
 [1_000_000_000_000_000,'Qa'],
 [1_000_000_000_000,'T'],
 [1_000_000_000,'B'],
 [1_000_000,'M'],
 [1_000,'K'],
];
export function v6WalletFormat(value,compact=true){
 const n=Number.isSafeInteger(value)&&value>=0?value:0;
 if(!compact)return n.toLocaleString('en-US');
 const unit=WALLET_UNITS.find(([minimum])=>n>=minimum);
 if(!unit)return String(n);
 const [scale,suffix]=unit;
 const amount=n/scale;
 const decimals=amount<10?2:amount<100?1:0;
 return amount.toFixed(decimals).replace(/(\.\d*?[1-9])0+$|\.0+$/,'$1')+suffix;
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
