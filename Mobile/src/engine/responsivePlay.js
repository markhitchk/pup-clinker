/**
 * One responsive viewport decision for Android tablets, desktop-width web, and phones.
 * Mirrors the upstream Puppy-Clicker /play breakpoint (960 CSS pixels).
 */
export const WEBSITE_PLAY_DESKTOP_MIN_WIDTH=960;
export function playViewportLayout(width,height=700){
 const w=Number.isFinite(Number(width))?Number(width):390;
 const h=Number.isFinite(Number(height))?Number(height):700;
 const desktop=w>=WEBSITE_PLAY_DESKTOP_MIN_WIDTH;
 // Width is the actual available app viewport (after any native side navigation).
 const dashboardWidth=desktop?Math.max(300,Math.min(490,Math.round(w*.39))):0;
 const available=w-dashboardWidth-(desktop?85:32);
 const portrait=desktop
  ?Math.max(155,Math.min(320,available,h<700?220:290))
  :Math.max(115,Math.min(220,w-112,h<560?130:h<700?164:210));
 return {desktop,dashboardWidth,portrait,stageHeight:desktop?400:h<610?300:360};
}
export function playPercent(progress,target=100){
 const t=Number(target),p=Number(progress);
 if(!Number.isFinite(t)||t<=0||!Number.isFinite(p))return 0;
 return Math.min(100,Math.max(0,100*p/t));
}
