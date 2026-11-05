import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import {WEBSITE_DARK_TOKENS,websitePaletteFor} from '../src/production/websiteTheme.js';
import {WEBSITE_UI_PROVENANCE} from '../src/production/websiteDesignTokens.js';
const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const read=path=>readFileSync(resolve(mobile,path),'utf8');
test('Android website palette follows existing canonical CSS',()=>{
 assert.equal(WEBSITE_UI_PROVENANCE.upstream,'markhitchk/Puppy-Clicker');
 assert.equal(WEBSITE_UI_PROVENANCE.source,'src/native/web/kawaii.css');
 assert.equal(WEBSITE_DARK_TOKENS.bg,'#0A0C12');
 assert.equal(WEBSITE_DARK_TOKENS.surface,'#12151F');
 assert.equal(WEBSITE_DARK_TOKENS.card,'#1A1E2B');
 assert.equal(WEBSITE_DARK_TOKENS.cyan,'#00E5FF');
 const dark=websitePaletteFor({},true);
 assert.equal(dark.bg,WEBSITE_DARK_TOKENS.bg);
 assert.equal(dark.accent,WEBSITE_DARK_TOKENS.cyan);
 assert.match(dark.line,/rgba\(0,229,255,0\.25\)/);
 assert.equal(websitePaletteFor({themeMode:'light'},true).dark,false);
 const verify=spawnSync(process.execPath,['scripts/sync-website-ui.mjs','--verify'],{cwd:mobile,encoding:'utf8'});
 assert.equal(verify.status,0,verify.stderr||verify.stdout);
});
test('Android Play follows mobile website structure in native React',()=>{
 const app=read('App.js'),screen=read('src/components/WebsitePlayScreen.jsx');
 assert.match(app,/WebsitePlayScreen\.jsx/);
 assert.match(app,/<WebsitePlayScreen game=\{g\}/);
 for(const marker of ['android-website-play-stage','Current Puppy','Tap your puppy and earn treats.','Care bonus','Next reward progress','android-tap-button','android-next-reward'])
  assert.ok(screen.includes(marker),marker);
 assert.doesNotMatch(screen,/<(?:div|button|span|section)\b|\b(?:window|document|localStorage|sessionStorage)\s*\./);
 assert.doesNotThrow(()=>transformSync(screen,{babelrc:false,configFile:false,plugins:[jsx]}));
});
