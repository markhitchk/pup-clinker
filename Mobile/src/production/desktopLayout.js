// Match Puppy-Clicker/src/native/web/desktop-reference.css: @media(min-width:960px)
// Sidebar: clamp(180px,16.15vw,248px)
// Play dashboard columns: minmax(0,1.366fr) minmax(0,1fr), with an 18px gap.
// React Native reports dp rather than CSS px: for the corresponding Android
// window viewport, use measured dp and recalculate whenever the window resizes.
export const WEBSITE_DESKTOP_BREAKPOINT=960;
export const WEBSITE_SIDEBAR_MIN=180;
export const WEBSITE_SIDEBAR_MAX=248;
export const WEBSITE_SIDEBAR_VIEWPORT_SHARE=0.1615;
export const WEBSITE_DESKTOP_PLAY_SHARE=1.366;
export const WEBSITE_DESKTOP_RAIL_SHARE=1;
export const WEBSITE_DESKTOP_PAGE_GUTTER=20;
export const WEBSITE_DESKTOP_GRID_GAP=18;

export function desktopLayoutForWindow(width,height){
 const w=Number.isFinite(width)?Math.max(0,width):0;
 const h=Number.isFinite(height)?Math.max(0,height):0;
 const desktop=w>=WEBSITE_DESKTOP_BREAKPOINT;
 const sidebarWidth=desktop?Math.round(Math.max(WEBSITE_SIDEBAR_MIN,
  Math.min(WEBSITE_SIDEBAR_MAX,w*WEBSITE_SIDEBAR_VIEWPORT_SHARE))):0;
 const mainWidth=desktop?Math.max(0,w-sidebarWidth):w;
 const dashboardWidth=desktop?Math.max(0,mainWidth-2*WEBSITE_DESKTOP_PAGE_GUTTER-
  WEBSITE_DESKTOP_GRID_GAP):0;
 const rightRailWidth=desktop?Math.round(dashboardWidth*WEBSITE_DESKTOP_RAIL_SHARE/
  (WEBSITE_DESKTOP_PLAY_SHARE+WEBSITE_DESKTOP_RAIL_SHARE)):0;
 return {
  desktop,windowWidth:w,windowHeight:h,
  sidebarWidth,mainWidth,rightRailWidth,
  playWidth:desktop?dashboardWidth-rightRailWidth:mainWidth,
  compactDesktop:desktop&&(w<1200||h<720),
 };
}
