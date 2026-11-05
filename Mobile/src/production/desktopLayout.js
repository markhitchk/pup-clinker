// Match the existing Puppy Clicker website's 960px desktop switch in Android dp.
// useWindowDimensions recalculates when a Chromebook/DeX freeform window resizes.
export const WEBSITE_DESKTOP_BREAKPOINT=960;

export function desktopLayoutForWindow(width,height){
 const w=Number.isFinite(width)?Math.max(0,width):0;
 const h=Number.isFinite(height)?Math.max(0,height):0;
 const desktop=w>=WEBSITE_DESKTOP_BREAKPOINT;
 const sidebarWidth=w>=1500?238:204;
 const mainWidth=Math.max(0,w-sidebarWidth);
 const rightRailWidth=Math.max(290,Math.min(400,Math.floor((mainWidth-58)*0.4)));
 return {
  desktop,
  windowWidth:w,windowHeight:h,
  sidebarWidth:desktop?sidebarWidth:0,
  mainWidth:desktop?mainWidth:w,
  rightRailWidth:desktop?rightRailWidth:0,
  compactDesktop:desktop&&(w<1200||h<720),
 };
}
