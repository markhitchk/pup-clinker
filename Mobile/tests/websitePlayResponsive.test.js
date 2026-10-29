import test from 'node:test';
import assert from 'node:assert/strict';
import {WEBSITE_PLAY_DESKTOP_MIN_WIDTH,playViewportLayout,playPercent} from '../src/engine/responsivePlay.js';
import {readFileSync} from 'node:fs';
test('website Play keeps one >=960px mobile/desktop breakpoint',()=>{
 assert.equal(WEBSITE_PLAY_DESKTOP_MIN_WIDTH,960);
 assert.equal(playViewportLayout(959,900).desktop,false);
 assert.equal(playViewportLayout(960,900).desktop,true);
 assert.equal(playViewportLayout(1440,900).desktop,true);
});
test('website Play fits narrow phones and compact desktops',()=>{
 for(const [w,h] of [[320,540],[390,680],[412,780],[960,650],[1280,740],[1920,1080]]){
  const l=playViewportLayout(w,h);
  assert.ok(l.portrait>=115&&l.portrait<=320);
  assert.ok(l.portrait<w);
  assert.ok(l.desktop?l.dashboardWidth>=300:l.dashboardWidth===0);
 }
});
test('server-derived progress meters clamp invalid and overshooting goals',()=>{
 assert.equal(playPercent(25,100),25);
 assert.equal(playPercent(300,100),100);
 assert.equal(playPercent(-1,100),0);
 assert.equal(playPercent(20,0),0);
});
test('native website view uses the original scene and respects protected writes',()=>{
 const screen=readFileSync(new URL('../src/components/WebsitePlayScreen.jsx',import.meta.url),'utf8');
 const app=readFileSync(new URL('../App.js',import.meta.url),'utf8');
 assert.match(screen,/night-yard-v1\.webp/);
 assert.match(screen,/desktop-website-play/);
 assert.match(screen,/mobile-website-play/);
 assert.match(screen,/readOnly\|\|cooldown>0/);
 assert.match(screen,/readOnly\|\|current<goal\.target/);
 assert.match(app,/WebsitePlayScreen/);
});
