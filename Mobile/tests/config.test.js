import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join,dirname} from 'node:path';
const root=join(dirname(fileURLToPath(import.meta.url)),'..');
const app=()=>JSON.parse(readFileSync(join(root,'app.json'),'utf8')).expo;
test('React Native replaces the UI with the existing Android app identity',()=>{
  const config=app();
  assert.equal(config.android.package,'com.harleytg.puppyclicker');
  assert.equal(config.android.versionCode,35);
  assert.equal(config.android.permissions.includes('INTERNET'),true);
});
test('no WebView or browser host is used for Android gameplay',()=>{
 const pkg=JSON.parse(readFileSync(join(root,'package.json'),'utf8'));
 const source=readFileSync(join(root,'App.js'),'utf8');
 assert.equal(Boolean(pkg.dependencies['react-native-webview']),false);
 assert.equal(source.includes('WebView'),false);
 assert.match(source,/Play/);assert.match(source,/Care/);assert.match(source,/Prestige/);
});
