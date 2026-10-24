import {effectiveMotion,paletteFor,scaleStyles,uiScaleFactor} from './engine/settings.js';
// One app surface. Existing scenes share this palette and rebuild their style
// dictionaries only when appearance changes, never on animation frames.
export const colors=paletteFor({},true);
export const interaction={buttons:true};
let revision=0,scale=1,signature='';
export function applyAppearance(preferences,systemDark){
 interaction.buttons=effectiveMotion(preferences).buttons;
 const palette=paletteFor(preferences,systemDark),nextScale=uiScaleFactor(preferences);
 const next=JSON.stringify([palette,nextScale]);
 if(next!==signature){Object.assign(colors,palette);scale=nextScale;signature=next;revision++;}
 return palette;
}
export function themedStyles(factory){
 let version=-1,cache;
 return new Proxy({}, {get(_target,key){if(version!==revision){cache=scaleStyles(factory(),scale);version=revision;}return cache[key];}});
}
