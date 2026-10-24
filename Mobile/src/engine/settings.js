// Kotlin PuppyUiPreferences / PuppyPerformancePolicy / PuppySettingsUi presets.
export const ACCENT_PRESETS=[['Puppy Cyan','#00B8F0'],['Electric Blue','#2979FF'],['Purple','#8B5CF6'],['Pink','#EC4899'],['Red','#EF4444'],['Orange','#F97316'],['Lime','#84CC16'],['Green','#22C55E']];
export const DEFAULT_PREFERENCES=Object.freeze({
 themeMode:'system',accentHex:'#00B8F0',uiScale:'default',performancePreset:'automatic',
 animatedUi:true,buttonAnimations:true,reducedMotion:false,highContrast:false,
 hapticsEnabled:true,animationsEnabled:true,compactNumbers:true,puppyPointerEnabled:true,
 dailyRewardNotifications:true,gameEventNotifications:true,updateNotifications:true,puppyAttentionNotifications:false,
 anonymousDiagnosticsEnabled:false,crashReportsEnabled:false,supportIdentityEnabled:false
});
const enums={themeMode:['light','dark','system'],uiScale:['compact','default','large'],performancePreset:['automatic','quality','battery_saver']};
export function preferenceValue(name,value){
 if(!Object.hasOwn(DEFAULT_PREFERENCES,name))return undefined;
 if(name==='accentHex')return typeof value==='string'&&/^#[0-9a-f]{6}$/i.test(value)?value.toUpperCase():undefined;
 if(Object.hasOwn(enums,name))return enums[name].includes(value)?value:undefined;
 return typeof value==='boolean'?value:undefined;
}
export function sanitizePreferences(input){
 return Object.fromEntries(Object.entries(DEFAULT_PREFERENCES).map(([name,fallback])=>[name,preferenceValue(name,input?.[name])??fallback]));
}
export function effectiveMotion(g){
 const decorative=g.animatedUi!==false&&!g.reducedMotion&&g.performancePreset!=='battery_saver';
 return {decorative,tap:g.animationsEnabled!==false&&!g.reducedMotion,buttons:g.buttonAnimations!==false&&!g.reducedMotion,casino:decorative&&g.animationsEnabled!==false};
}
export function paletteFor(g,systemDark=true){
 const dark=g.themeMode==='dark'||(g.themeMode!=='light'&&systemDark),high=g.highContrast===true;
 const accent=preferenceValue('accentHex',g.accentHex)||DEFAULT_PREFERENCES.accentHex;
 const rgb=[1,3,5].map(i=>parseInt(accent.slice(i,i+2),16));
 const luminance=rgb.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0);
 // Text/link color must remain readable even for a very dark custom accent.
 const accentInk=dark&&luminance<.18?'#9DCBE0':!dark&&luminance>.18?'#07506B':accent;
 return {dark,bg:dark?'#0C0F12':'#FFFBFF',surface:dark?'#151A1F':'#FFFFFF',card:dark?(high?'#252D34':'#1C232A'):(high?'#E5E9EC':'#F1F3F5'),line:dark?(high?'#747E87':'#313941'):(high?'#8B9399':'#D7DCE0'),accent,accentInk,onAccent:luminance>.179?'#071014':'#FFFFFF',accentSoft:`rgba(${rgb.join(',')},${dark?(high?.36:.22):(high?.26:.16)})`,text:dark?'#F2F5F7':'#171A1D',muted:dark?(high?'#E7EDF1':'#B8C1C8'):(high?'#252A2E':'#555C62'),gold:dark?'#FFD56B':'#795700',green:dark?'#78E5B2':'#206540',danger:dark?'#FFB4AB':'#BA1A1A'};
}
export const uiScaleFactor=g=>g.uiScale==='compact'?.94:g.uiScale==='large'?1.08:1;
const dimension=/^(fontSize|lineHeight|padding.*|margin.*|gap|rowGap|columnGap|borderRadius|minHeight|minWidth|maxHeight|maxWidth|width|height)$/;
export function scaleStyles(styles,factor){
 return Object.fromEntries(Object.entries(styles).map(([name,style])=>[name,Object.fromEntries(Object.entries(style).map(([key,value])=>[key,typeof value==='number'&&dimension.test(key)?value*factor:value]))]));
}
