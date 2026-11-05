import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import {WEBSITE_DESKTOP_BREAKPOINT,desktopLayoutForWindow} from '../src/production/desktopLayout.js';

const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
const require=createRequire(import.meta.url);
test('Android Chromebook and Samsung DeX match website desktop breakpoint',()=>{
 assert.equal(WEBSITE_DESKTOP_BREAKPOINT,960);
 for(const width of [360,600,800,959]){
  const layout=desktopLayoutForWindow(width,700);
  assert.equal(layout.desktop,false,'phone/tablet width '+width);
  assert.equal(layout.sidebarWidth,0);
 }
 for(const width of [960,1024,1280,1600,1920]){
  const layout=desktopLayoutForWindow(width,850);
  assert.equal(layout.desktop,true,'desktop width '+width);
  assert.ok(layout.sidebarWidth>=200);
  assert.ok(layout.rightRailWidth>=290);
  assert.ok(layout.mainWidth>layout.rightRailWidth);
 }
 assert.equal(desktopLayoutForWindow(1279,800).compactDesktop,false);
 assert.equal(desktopLayoutForWindow(1100,680).compactDesktop,true);
 assert.equal(desktopLayoutForWindow(960,700).compactDesktop,true);
 assert.equal(desktopLayoutForWindow(NaN,Infinity).desktop,false);
});
test('Android manifest allows landscape/desktop freeform, without altering production package',()=>{
 const app=JSON.parse(read('app.json')).expo;
 assert.equal(app.orientation,'default');
 assert.equal(app.android.package,'com.harleytg.puppyclicker');
 assert.ok(app.plugins.includes('./plugins/withPuppyDesktopMode.cjs'));
 const plugin=require('../plugins/withPuppyDesktopMode.cjs');
 const manifest={manifest:{application:[{$:{},activity:[{$:{'android:name':'.MainActivity'}}]}]}};
 plugin.enableResizableWindowing(manifest);
 assert.equal(manifest.manifest.application[0].$['android:resizeableActivity'],'true');
 assert.equal(manifest.manifest.application[0].activity[0].$['android:resizeableActivity'],'true');
 assert.equal(manifest.manifest.application[0].activity[0].$['android:screenOrientation'],undefined);
 const appConfig=read('app.config.js');
 assert.match(appConfig,/com\.harleytg\.puppyclicker\.beta/);
});
test('Desktop copies website visual structure as native components',()=>{
 const shell=read('src/components/WebsiteDesktopShell.jsx');
 const dashboard=read('src/components/WebsiteDesktopDashboard.jsx');
 const app=read('App.js');
 const play=read('src/components/WebsitePlayScreen.jsx');
 assert.match(shell,/android-desktop-navigation/);
 assert.match(shell,/Player Level/);
 assert.match(shell,/day streak/);
 for(const label of ['Play','Care','Roster','Shop','Rewards','Casino','Exchange','Inventory','Settings','Help']){
  assert.ok(shell.includes(label),'Missing desktop navigation: '+label);
 }
 for(const label of ['Your Resources',"Today's goals",'Pup Care Status','Go to Care']){
  assert.ok(dashboard.includes(label),'Missing website desktop dashboard panel: '+label);
 }
 assert.match(app,/desktopLayoutForWindow\(width,windowHeight\)/);
 assert.match(app,/WebsiteDesktopDashboard game=\{g\}/);
 assert.match(app,/WebsiteDesktopNavigation tabs=\{TABS\}/);
 assert.match(app,/WebsiteDesktopTopBar game=\{g\}/);
 assert.match(app,/!desktop&&state\.ready&&g\.onboardingComplete/);
 assert.match(app,/scrollEnabled=\{desktop\|\|windowHeight<560\|\|Boolean\(secondary\)\|\|tab!=='play'\}/);
 assert.match(play,/desktop\?Math\.max\(690/);
 assert.doesNotMatch(shell+dashboard,/<(?:div|section|aside|article)\b|\bwindow\./);
 for(const component of [shell,dashboard,play])
  assert.doesNotThrow(()=>transformSync(component,{babelrc:false,configFile:false,plugins:[jsx]}));
});
