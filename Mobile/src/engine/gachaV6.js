// Kotlin PuppyGacha.kt: PuppyMechanicalGachaMachine image placement and stages.
export const GACHA_MACHINE=Object.freeze({
 ratio:1024/1536,
 knobRatio:0.255,
 knobCenterY:1002/1536,
 capsuleRatio:0.205,
 capsuleCenterY:1246/1536,
 openRatio:0.305,
 openCenterY:1236/1536,
 knobTurnMs:620,
 capsuleOpenMs:640,
 revealDelayMs:300,
 dropOffset:-58,
 openButtonBottom:22
});
export function gachaMachineFrame(width){
 const w=Math.max(0,Number.isFinite(width)?width:0),h=w/GACHA_MACHINE.ratio;
 const size=(fraction,center)=>({width:w*fraction,height:w*fraction,left:(w-w*fraction)/2,top:h*center-w*fraction/2});
 return {width:w,height:h,knob:size(GACHA_MACHINE.knobRatio,GACHA_MACHINE.knobCenterY),
 capsule:size(GACHA_MACHINE.capsuleRatio,GACHA_MACHINE.capsuleCenterY),
 open:size(GACHA_MACHINE.openRatio,GACHA_MACHINE.openCenterY)};
}
