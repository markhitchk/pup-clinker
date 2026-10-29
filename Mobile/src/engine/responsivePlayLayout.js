// React Native uses window dimensions (density-independent points), not CSS media queries.
// 960dp matches the website's desktop breakpoint and responds to DeX/ChromeOS resize.
export const DESKTOP_PLAY_BREAKPOINT=960;
export function isDesktopPlayWindow(width){
 return Number.isFinite(width)&&width>=DESKTOP_PLAY_BREAKPOINT;
}
export function playWindowLayout(width,height){
 const desktop=isDesktopPlayWindow(width);
 const short=Number.isFinite(height)&&height<700;
 return {desktop,short,sidebarWidth:desktop?(width<1250?184:226):0,panels:desktop?3:0};
}
