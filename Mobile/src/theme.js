import {effectiveMotion,paletteFor,scaleStyles,uiScaleFactor} from './engine/settings.js';
// One app surface. Existing scenes share this palette and rebuild their style
// dictionaries only when appearance changes, never on animation frames.
const webPalette=(preferences,systemDark=true)=>{
 const winter=preferences.seasonalTheme==='winter'&&preferences.highContrast!==true;
 const accent=winter?'#80DCEB':(!preferences.accentHex||preferences.accentHex.toUpperCase()==='#00B8F0')?'#00E5FF':preferences.accentHex;
 // Default to the supplied dark design while retaining explicit accessibility preferences.
 const base=paletteFor({...preferences,accentHex:accent,themeMode:preferences.themeMode||'dark'},systemDark);
 if(!base.dark)return winter?{...base,bg:'#EAF6FC',surface:'#F5FCFF',card:'#FFFFFF',line:'#A4C4D7',text:'#1A3447',muted:'#365A70'}:base;
 if(winter)return {...base,bg:'#101C2C',surface:'#172D40',card:'#1D3B50',line:'#527B91',text:'#F3FAFF',muted:'#C2DDE9',gold:'#FFE1A0',green:'#86E1C5'};
 const high=preferences.highContrast===true;
 return {...base,bg:'#0A0C12',surface:'#12151F',card:high?'#252D3D':'#1A1E2B',line:high?'#A0BDD0':'rgba(0,229,255,0.25)',text:'#FFFFFF',muted:high?'#E7EDF1':'#A0A8C0',gold:'#FFD166',green:'#34D399'};
};
export const colors=webPalette({});
export const interaction={buttons:true};
let revision=0,scale=1,signature='';
export function applyAppearance(preferences,systemDark){
 interaction.buttons=effectiveMotion(preferences).buttons;
 const palette=webPalette(preferences,systemDark),nextScale=uiScaleFactor(preferences);
 const next=JSON.stringify([palette,nextScale]);
 if(next!==signature){Object.assign(colors,palette);scale=nextScale;signature=next;revision++;}
 return palette;
}
export function themedStyles(factory){
 let version=-1,cache;
 return new Proxy({}, {get(_target,key){if(version!==revision){cache=scaleStyles(factory(),scale);version=revision;}return cache[key];}});
}
