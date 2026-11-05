// Android-only native translation of the EXISTING website design tokens.
// Source of truth (unchanged): Puppy-Clicker/src/native/web/kawaii.css :root.
// This maps CSS colors to React Native; never inject browser CSS or DOM into Android.
import {paletteFor} from '../engine/settings.js';

export const WEBSITE_DARK_TOKENS=Object.freeze({
 bg:'#0A0C12',surface:'#12151F',card:'#1A1E2B',modal:'#161A25',
 cyan:'#00E5FF',cyanSoft:'#00C4E0',purple:'#7B5CFF',pink:'#FF5C9D',
 gold:'#FFD166',green:'#34D399',text:'#FFFFFF',muted:'#A0A8C0',
 tertiary:'#6B7280',onAccent:'#07141B'
});
const colorRgb=color=>[1,3,5].map(i=>parseInt(color.slice(i,i+2),16));
export function websitePaletteFor(preferences={},systemDark=true){
 const base=paletteFor(preferences,systemDark);
 if(!base.dark)return base;
 const cyan=base.accent?.toUpperCase()==='#00B8F0'?WEBSITE_DARK_TOKENS.cyan:base.accent;
 const [r,g,b]=colorRgb(cyan);
 return {...base,bg:WEBSITE_DARK_TOKENS.bg,surface:WEBSITE_DARK_TOKENS.surface,
  card:WEBSITE_DARK_TOKENS.card,accent:cyan,accentInk:cyan,
  accentSoft:`rgba(${r},${g},${b},0.12)`,
  line:`rgba(${r},${g},${b},${preferences?.highContrast?0.65:0.25})`,
  onAccent:WEBSITE_DARK_TOKENS.onAccent,text:WEBSITE_DARK_TOKENS.text,
  muted:WEBSITE_DARK_TOKENS.muted,gold:WEBSITE_DARK_TOKENS.gold,
  green:WEBSITE_DARK_TOKENS.green,danger:WEBSITE_DARK_TOKENS.pink};
}
