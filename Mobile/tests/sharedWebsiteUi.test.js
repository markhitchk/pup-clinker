import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';

const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const read=path=>readFileSync(resolve(mobile,path),'utf8');
test('Android embeds exact website React UI files and validates pinned blob hashes',()=>{
 const result=spawnSync(process.execPath,['scripts/sync-website-ui.mjs','--verify'],{cwd:mobile,encoding:'utf8'});
 assert.equal(result.status,0,result.stderr||result.stdout);
 const manifest=JSON.parse(read('src/shared-ui/SOURCE.json'));
 assert.equal(manifest.upstream,'markhitchk/Puppy-Clicker');
 assert.ok(manifest.files.some(f=>f.target==='src/shared-ui/PupGameChrome.jsx'));
 assert.ok(manifest.files.some(f=>f.target==='src/shared/usePupAuthForm.js'));
});
test('real Android game uses same React Native resource and care UI components as website',()=>{
 const game=read('App.js');
 const chrome=read('src/shared-ui/PupGameChrome.jsx');
 assert.match(game,/from '\.\/src\/shared-ui\/PupGameChrome\.jsx'/);
 for(const name of ['PupResourceStrip','PupNeedPill','PupQuickAction']){
  assert.ok(game.includes('<'+name),name);
  assert.ok(chrome.includes('export function '+name+'('),name);
 }
 assert.doesNotMatch(chrome,/\b(?:window|document|localStorage|sessionStorage)\s*\./);
 assert.ok(chrome.includes('accessibilityRole="progressbar"'));
 assert.ok(chrome.includes('exact balance'));
 assert.doesNotThrow(()=>transformSync(chrome,{babelrc:false,configFile:false,plugins:[jsx]}));
});
