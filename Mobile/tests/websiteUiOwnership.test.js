import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {transformSync} from '@babel/core';
import jsx from '@babel/plugin-transform-react-jsx';
import {MAIN_TABS} from '../src/production/websiteNavigation.js';

const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const load=path=>readFileSync(resolve(mobile,path),'utf8');
function blobSha(body){
 const data=Buffer.from(body,'utf8');
 return createHash('sha1').update(Buffer.from('blob '+data.length+'\0')).update(data).digest('hex');
}
test('website-owned account details UI has no independent Android copy drift',()=>{
 const source=JSON.parse(load('src/production/websiteUiSources.json'));
 assert.equal(source.upstream,'markhitchk/Puppy-Clicker');
 assert.match(source.commit,/^[a-f0-9]{40}$/);
 assert.deepEqual(source.components.map(({source,target})=>[source,target]),[
  ['src/native/src/components/AccountDetailsScreen.jsx','src/components/AccountDetailsScreen.jsx'],
  ['src/native/src/components/NotificationInbox.jsx','src/components/NotificationInbox.jsx'],
  ['src/native/src/production/AccountOnboarding.jsx','src/production/AccountOnboarding.jsx'],
  ['src/native/src/components/PuppyDetailsDialog.jsx','src/components/PuppyDetailsDialog.jsx']
 ]);
 assert.equal(source.navigation.source,'src/native/src/engine/playRevamp.js');
 assert.equal(source.navigation.target,'src/production/websiteNavigation.js');
 assert.match(source.navigation.sourceSha,/^[a-f0-9]{40}$/);
 assert.equal(blobSha(load(source.navigation.target)),source.navigation.blobSha);
 assert.deepEqual(MAIN_TABS.map(([id,emoji,label])=>[id,emoji,label]),[
  ['play','🐾','Play'],['care','💖','Care'],['roster','🐶','Roster'],
  ['shop','🛍️','Shop'],['rewards','🎁','Rewards']
 ]);
 const app=load('App.js');
 assert.ok(app.includes("from './src/production/websiteNavigation.js'"));
 assert.ok(app.includes("onPress={()=>openScreen(id)}"));
 for(const item of source.components){
  const body=load(item.target);
  assert.match(item.sourceSha,/^[a-f0-9]{40}$/);
  assert.equal(blobSha(body),item.blobSha,item.target);
  assert.match(body,/from 'react-native'/);
  assert.doesNotMatch(body,/<(?:div|button|section)\b|\b(?:window|document|localStorage)\s*\./);
 }
 for(const key of ['careShop','rewards','languageCatalog','goalState','pupeyeStatus','rosterPrestige','puppyNames','inventoryContract']){
  const item=source[key];
  assert.equal(item.target.startsWith('src/'),true,key);
  assert.match(item.sourceSha,/^[a-f0-9]{40}$/,key+' canonical SHA');
  assert.equal(blobSha(load(item.target)),item.blobSha,key+' generated SHA');
 }
 const auth=source.authContract;
 assert.equal(auth.target,'src/production/websiteAccountContract.js');
 assert.deepEqual(auth.sources.map(item=>item.source),[
  'src/WebRegister.jsx','src/native/web/AccountGate.jsx','src/JoinSourceFields.jsx'
 ]);
 for(const item of auth.sources)assert.match(item.sourceSha,/^[a-f0-9]{40}$/);
 assert.equal(blobSha(load(auth.target)),auth.blobSha);
 const settings=source.settingsContract;
 assert.equal(settings.source,'src/native/web/WebSettings.jsx');
 assert.equal(settings.target,'src/production/websiteSettingsContract.js');
 assert.match(settings.sourceSha,/^[a-f0-9]{40}$/);
 assert.equal(blobSha(load(settings.target)),settings.blobSha);
 const casino=source.casinoGacha;
 assert.equal(casino.source,'src/native/App.jsx');
 assert.equal(casino.dialogSource,'src/native/web/WebsiteDesign.jsx');
 assert.equal(casino.target,'src/components/WebsiteCasinoGachaScreens.jsx');
 for(const field of ['sourceSha','dialogSha','blobSha'])assert.match(casino[field],/^[a-f0-9]{40}$/);
 assert.equal(blobSha(load(casino.target)),casino.blobSha);
  const screens=['src/components/WebsiteCareShopScreens.jsx','src/components/WebsiteRewardsScreens.jsx','src/components/WebsiteRosterPrestigeScreens.jsx','src/components/WebsiteCasinoGachaScreens.jsx'];
 for(const screen of screens){
  const body=load(screen);
  assert.doesNotMatch(body,/<(?:div|article|section|button)\\b/,screen);
  assert.doesNotThrow(()=>transformSync(body,{babelrc:false,configFile:false,plugins:[jsx]}),screen);
 }
 assert.match(load('src/components/WebsiteCareShopScreens.jsx'),/function CareScreen/);
 assert.match(load('src/components/WebsiteCareShopScreens.jsx'),/function ShopScreen/);
 assert.match(load('src/components/WebsiteRewardsScreens.jsx'),/function RewardsGroup/);
 assert.match(load('src/components/WebsiteRewardsScreens.jsx'),/function RewardsScreen/);
 assert.match(load('src/components/WebsiteRewardsScreens.jsx'),/function DailyGoalCard/);
 assert.match(load('src/components/WebsiteRosterPrestigeScreens.jsx'),/function RosterScreen/);
 assert.match(load('src/components/WebsiteRosterPrestigeScreens.jsx'),/function PrestigeScreen/);
 assert.match(load('src/components/WebsiteCasinoGachaScreens.jsx'),/function CasinoScreen/);
 assert.match(load('src/components/WebsiteCasinoGachaScreens.jsx'),/function GachaScreen/);
 assert.match(load('src/components/PuppyDetailsDialog.jsx'),/Generate random puppy name/);
 for(const name of ['CareScreen','ShopScreen','RewardsScreen','DailyGoalCard','RosterScreen','PrestigeScreen','CasinoScreen','GachaScreen'])assert.ok(!app.includes('function '+name+'('),name+' must not have a handwritten Android duplicate');
 assert.ok(app.includes('createWebsiteCareShopScreens'));
 assert.ok(app.includes('createWebsiteRewardsScreens'));
 assert.ok(app.includes('createWebsiteRosterPrestigeScreens'));
 assert.ok(app.includes('createWebsiteCasinoGachaScreens'));
  const check=spawnSync(process.execPath,['scripts/sync-website-native-ui.mjs','--verify'],{
  cwd:mobile,encoding:'utf8'
 });
 assert.equal(check.status,0,check.stderr||check.stdout);
});
