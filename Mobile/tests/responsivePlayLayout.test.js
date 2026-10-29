import test from 'node:test';
import assert from 'node:assert/strict';
import {DESKTOP_PLAY_BREAKPOINT,isDesktopPlayWindow,playWindowLayout} from '../src/engine/responsivePlayLayout.js';
test('Android window width drives mobile/DeX/Chromebook presentation independently of device type',()=>{
 assert.equal(DESKTOP_PLAY_BREAKPOINT,960);
 for(const width of [320,393,600,959])assert.equal(isDesktopPlayWindow(width),false);
 for(const width of [960,1080,1366,1920])assert.equal(isDesktopPlayWindow(width),true);
 assert.equal(isDesktopPlayWindow(Number.NaN),false);
 assert.deepEqual(playWindowLayout(393,852),{desktop:false,short:false,sidebarWidth:0,panels:0});
 assert.deepEqual(playWindowLayout(1100,650),{desktop:true,short:true,sidebarWidth:184,panels:3});
 assert.deepEqual(playWindowLayout(1440,900),{desktop:true,short:false,sidebarWidth:226,panels:3});
});
