// Generated downstream UI: website main is the ONLY editable source for allowlisted UI files.
// Do not copy server, browser-auth, or gameplay authority code from the website.
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const MOBILE=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const UPSTREAM='markhitchk/Puppy-Clicker';
const MANIFEST=resolve(MOBILE,'src/production/websiteUiSources.json');
const NAVIGATION_SOURCE='src/native/src/engine/playRevamp.js';
const NAVIGATION_TARGET='src/production/websiteNavigation.js';
const CARE_SHOP_SOURCE='src/native/App.jsx';
const CARE_SHOP_TARGET='src/components/WebsiteCareShopScreens.jsx';
const ROSTER_PRESTIGE_SOURCE='src/native/App.jsx';
const ROSTER_PRESTIGE_TARGET='src/components/WebsiteRosterPrestigeScreens.jsx';
const PUPPY_NAMES_SOURCE='src/nameSuggestions.js';
const PUPPY_NAMES_TARGET='src/production/websiteNameSuggestions.js';
const INVENTORY_CONTRACT_SOURCE='src/InventoryScreen.jsx';
const INVENTORY_CONTRACT_TARGET='src/production/websiteInventoryContract.js';
const REWARDS_SOURCE='src/native/App.jsx';
const REWARDS_TARGET='src/components/WebsiteRewardsScreens.jsx';
const SOURCE_MODULES=Object.freeze([
 {key:'languageCatalog',source:'src/communityI18n.js',target:'src/production/websiteI18n.js'},
 {key:'goalState',source:'src/native/web/webGoalState.js',target:'src/production/websiteGoalState.js'}
]);
const COMPONENTS=Object.freeze([
 {source:'src/native/src/components/AccountDetailsScreen.jsx',
  target:'src/components/AccountDetailsScreen.jsx'},
 {source:'src/native/src/components/NotificationInbox.jsx',
  target:'src/components/NotificationInbox.jsx'},
 {source:'src/native/src/production/AccountOnboarding.jsx',
  target:'src/production/AccountOnboarding.jsx'},
 {source:'src/native/src/components/PuppyDetailsDialog.jsx',
  target:'src/components/PuppyDetailsDialog.jsx'}
]);
// Only stable, reviewed RN Image-source substitutions are permitted.
// Unexpected website artwork references fail sync rather than silently diverging.
const IMAGE_ADAPTERS=Object.freeze({
 'src/native/src/components/NotificationInbox.jsx':[
  ['/assets/logos/puppy_clicker.png','../../assets/logos/puppy_clicker.png',1]
 ],
 'src/native/src/production/AccountOnboarding.jsx':[
  ['/assets/logos/puppy_clicker.png','../../assets/logos/puppy_clicker.png',2],
  ['/assets/logos/PupEye.png','../../assets/logos/PupEye.png',2],
  ['/assets/logos/harleys_studios.png','../../assets/logos/harleys_studios.png',1],
  ['https://raw.githubusercontent.com/markhitchk/pup-clinker/f35ad86ea874671bc05a3ab3dedde5bf97bbc06b/Mobile/assets/logos/discord.png',
   '../../assets/logos/discord.png',2]
 ]
});
function adaptWebsiteImages(source,body){
 for(const [uri,local,expected] of IMAGE_ADAPTERS[source]||[]){
  const original='source={({uri:"'+uri+'"})}';
  const replacement="source={require('"+local+"')}";
  const count=body.split(original).length-1;
  if(count!==expected)
   throw Error('Website UI artwork adapter needs review: '+source+' '+uri);
  body=body.split(original).join(replacement);
 }
 if(source==='src/native/src/components/PuppyDetailsDialog.jsx'){
  const browserImport="from '../../../nameSuggestions.js'";
  if(body.split(browserImport).length!==2)
   throw Error('Website profile name generator import changed; review adapter');
  body=body.replace(browserImport,"from '../production/websiteNameSuggestions.js'");
 }
 return body;
}

function blobSha(value){
 const data=Buffer.from(value,'utf8');
 return createHash('sha1').update(Buffer.from('blob '+data.length+'\0')).update(data).digest('hex');
}
function assertNativeComponent(value,path){
 if(typeof value!=='string'||!value.includes("from 'react-native'")||value.length<200||value.length>100000)
  throw Error('Unsupported website native component: '+path);
 if(/<(?:div|article|section|header|nav|span|button|input)\b|\b(?:window|document|localStorage|sessionStorage)\s*\./.test(value))
  throw Error('Website browser-only code cannot be copied to Android: '+path);
}
function extractWebsiteTabs(source){
 const matches=[...source.matchAll(/export const MAIN_TABS=Object\.freeze\((\[[^\n]*\])\);/g)];
 if(matches.length!==1)throw Error('Review canonical website tab definition before syncing');
 const tabIds=[...matches[0][1].matchAll(/\['([^']+)','[^']+','[^']+'\]/g)].map(item=>item[1]);
 if(tabIds.join(',')!=='play,care,roster,shop,rewards')
  throw Error('Website primary tab membership changed; review required');
 return '// GENERATED from website '+NAVIGATION_SOURCE+'. Do not hand-edit.\n'+
  'export const MAIN_TABS=Object.freeze('+matches[0][1]+');\n';
}
function extractWebsiteScreen(source,name){
 const at=source.indexOf('function '+name+'(');
 if(at<0)throw Error('Missing canonical screen '+name);
 const end=source.indexOf('\nfunction ',at+1);
 if(end<0)throw Error('Invalid canonical screen boundary: '+name);
 return source.slice(at,end).replace(/\n\/\/ Part 3:[^\n]*\s*$/,'').trimEnd();
}
function generateCareShop(source){
 const care=extractWebsiteScreen(source,'CareScreen');
 const shop=extractWebsiteScreen(source,'ShopScreen');
 if(!care.includes('Care actions')||!shop.includes('Pup Coin Shop')||
    /<(?:div|section|article|button)\b|\b(?:window|document|localStorage)\./.test(care+'\n'+shop))
  throw Error('Website Care/Shop changed native structure; review extraction');
 const deps='ROSTER,careTapBonus,mood,wellness,canPerformCare,Txt,styles,C,PageHeader,Portrait,upgradesForShopTab,Wallet,Button,RARITIES,accessoryPrices,accessoryOffer,v6Format,TICKET_RARITY_PRESENTATION,ticketShopOffer,MAX_UPGRADES_PER_ITEM,upgradeOffer,Card';
 return '// GENERATED, DO NOT EDIT. Source: markhitchk/Puppy-Clicker/src/native/App.jsx\n'+
 '// The website owns these React Native-compatible Care and Shop screen bodies.\n'+
 '// Native PupEye/gameplay callbacks and presentation dependencies are injected by the shell.\n'+
 "import React,{useState} from 'react';\n"+
 "import {Alert,Pressable,View} from 'react-native';\n\n"+
 'export function createWebsiteCareShopScreens({'+deps+'}){\n'+
 care+'\n\n'+shop+'\n\n return {CareScreen,ShopScreen};\n}\n';
}
function extractWebsitePart(source,name,tail){
 const start=source.indexOf('function '+name+'(');
 const end=source.indexOf(tail,start+1);
 if(start<0||end<0)throw Error('Review website Rewards function '+name);
 return source.slice(start,end).trimEnd();
}
function generateRewards(source){
 let daily=extractWebsitePart(source,'DailyGoalCard','\nfunction PlayScreen(');
 const htmlStart=daily.indexOf(' if(compact)return ');
 const nativeStart=daily.indexOf('\n\n return <View',htmlStart);
 if(htmlStart<0||nativeStart<0)
  throw Error('Website native goal/card boundary changed; review before synchronizing');
 daily=daily.slice(0,htmlStart)+'\n'+daily.slice(nativeStart+1);
 const group=extractWebsitePart(source,'RewardsGroup','\nfunction RewardsScreen(');
 const screen=extractWebsitePart(source,'RewardsScreen','\n// The scene assets below');
 if(/<(?:div|section|article|button)\b|\b(?:window|document|localStorage)\s*\./.test(daily+group+screen))
  throw Error('Website Rewards no longer contains purely native JSX');
 const deps='Txt,styles,C,PageHeader,Button,Card,format,goalsForDay,monthKey,level,MAX_PLAYER_LEVEL,MAX_PLAYER_XP,nextXpTarget,levelXp,MAX_REWARD_STREAK_DAYS,achievementStatuses,parkPreview,ACHIEVEMENTS,webGoalState,webServerRewardsDate,webServerEpochDay,useLanguage';
 return '// GENERATED, DO NOT EDIT. Source: markhitchk/Puppy-Clicker/src/native/App.jsx\n'+
 '// Website-owned Rewards components. The browser-only compact HTML goal renderer\n'+
 '// is excluded; native View/Pressable markup and signed game actions remain identical.\n'+
 "import React,{useState} from 'react';\n"+
 "import {Pressable,View} from 'react-native';\n\n"+
 'export function createWebsiteRewardsScreens({'+deps+'}){\n'+
 daily+'\n\n'+group+'\n\n'+screen+'\n\n return {DailyGoalCard,RewardsGroup,RewardsScreen};\n}\n';
}
function adaptPureWebsiteModule(item,body){
 if(item.key==='goalState'){
  if(!body.includes("from '../src/engine/"))
   throw Error('Website goal eligibility imports changed; review before syncing');
  return body.replaceAll('../src/engine/','../engine/');
 }
 return body;
}
function generateRosterPrestige(source){
 const prestige=extractWebsitePart(source,'PrestigeScreen','\nfunction RosterScreen(');
 const roster=extractWebsitePart(source,'RosterScreen','\nfunction GachaScreen(')
  .replace(/\n\/\/ Part 4:[\s\S]*$/,'').trimEnd();
 if(!roster.includes('puppy-roster-grid')||!roster.includes('current-puppy-hero')||
   !prestige.includes('Permanent skill tree')||
   /<(?:div|section|article|button)\b|\b(?:window|document|localStorage)\s*\./.test(roster+prestige))
  throw Error('Website Roster/Prestige structure changed; review before mirroring');
 const deps='ROSTER,LEGACY_V6_PUPPIES,legacyRosterSections,unlockedLegacyCount,PageHeader,Txt,Portrait,styles,C,Button,PuppyDetailsDialog,Card,suggestPuppyName,PRESTIGE_SKILLS,PRESTIGE_SKILL_PRESENTATION,prestigeOffer,canBuySkill,skillCost,v6Format';
 return '// GENERATED. Canonical source: Puppy-Clicker/src/native/App.jsx.\n'+
 '// Website-owned Prestige and Roster JSX; all signed native actions are callbacks.\n'+
 '// Do not hand-edit: regenerate using scripts/sync-website-native-ui.mjs.\n'+
 "import React,{useEffect,useState} from 'react';\n"+
 "import {Alert,Pressable,ScrollView,TextInput,View,useWindowDimensions} from 'react-native';\n\n"+
 'export function createWebsiteRosterPrestigeScreens({'+deps+'}){\n'+
 prestige+'\n\n'+roster+'\n\n return {PrestigeScreen,RosterScreen};\n}\n';
}
function extractInventoryAssignment(source,name){
 const match=source.match(new RegExp('const '+name+'=([\\s\\S]*?);\\n'));
 if(!match)throw Error('Website Inventory contract changed: '+name);
 return match[1].trim();
}
function generateInventoryContract(source){
 const names=['currencies','accessoryIcons','rarityIcons','sections'];
 return '// GENERATED from the canonical website Puppy-Clicker/src/InventoryScreen.jsx.\n'+
 '// Presentation-only inventory categories and labels; NO account or save permissions.\n'+
 names.map(name=>'export const WEBSITE_INVENTORY_'+name.toUpperCase()+
  '=Object.freeze('+extractInventoryAssignment(source,name)+');').join('\n')+'\n';
}
function generatePuppyNames(source){
 const names=extractInventoryAssignment(source,'puppies');
 return '// GENERATED from Puppy-Clicker/src/nameSuggestions.js (puppies catalog only).\n'+
 '// Cosmetic suggestions may use Math.random: NEVER use this generator for passwords,\n'+
 '// authentication tokens, friend codes, reward determination, or signed gameplay.\n'+
 'const puppies='+names+';\n'+
 "export function suggestPuppyName(previous=''){\n"+
 " const current=String(previous||'').trim().toLowerCase();\n"+
 " for(let i=0;i<5;i++){\n"+
 "  const name=puppies[Math.floor(Math.random()*puppies.length)];\n"+
 "  if(name.toLowerCase()!==current)return name;\n"+
 " }\n"+
 " return puppies.find(name=>name.toLowerCase()!==current)||'Buddy';\n"+
 '}\n';
}
async function verify(){
 const manifest=JSON.parse(await readFile(MANIFEST,'utf8'));
 if(manifest.upstream!==UPSTREAM||!/^[a-f0-9]{40}$/.test(manifest.commit)
    ||!Array.isArray(manifest.components)||manifest.components.length!==COMPONENTS.length)
  throw Error('Invalid canonical website UI source manifest');
 for(const component of COMPONENTS){
  const saved=manifest.components.find(item=>item.source===component.source&&item.target===component.target);
  if(!saved||!/^[a-f0-9]{40}$/.test(saved.blobSha)||!/^[a-f0-9]{40}$/.test(saved.sourceSha))
   throw Error('Missing pinned website component '+component.source);
  const local=await readFile(resolve(MOBILE,component.target),'utf8');
  assertNativeComponent(local,component.target);
  if(blobSha(local)!==saved.blobSha)
   throw Error('Website UI component drift: '+component.target+'; re-run website UI sync');
 }
 const nav=manifest.navigation;
 if(!nav||nav.source!==NAVIGATION_SOURCE||nav.target!==NAVIGATION_TARGET||
    !/^[a-f0-9]{40}$/.test(nav.sourceSha)||!/^[a-f0-9]{40}$/.test(nav.blobSha))
  throw Error('Missing website-owned five-tab navigation provenance');
 const generated=await readFile(resolve(MOBILE,NAVIGATION_TARGET),'utf8');
 if(blobSha(generated)!==nav.blobSha)
  throw Error('Website navigation drift: '+NAVIGATION_TARGET);
 if(!generated.includes("export const MAIN_TABS=Object.freeze("))
  throw Error('Invalid generated website navigation');
 const careShop=manifest.careShop;
 if(!careShop||careShop.source!==CARE_SHOP_SOURCE||careShop.target!==CARE_SHOP_TARGET||
    !/^[a-f0-9]{40}$/.test(careShop.sourceSha)||!/^[a-f0-9]{40}$/.test(careShop.blobSha))
  throw Error('Missing canonical website Care/Shop provenance');
 const generatedCareShop=await readFile(resolve(MOBILE,CARE_SHOP_TARGET),'utf8');
 if(blobSha(generatedCareShop)!==careShop.blobSha)
  throw Error('Website Care/Shop UI drift; regenerate before merging');
 const rewards=manifest.rewards;
 if(!rewards||rewards.source!==REWARDS_SOURCE||rewards.target!==REWARDS_TARGET||
    !/^[a-f0-9]{40}$/.test(rewards.sourceSha)||!/^[a-f0-9]{40}$/.test(rewards.blobSha))
  throw Error('Missing website-owned Rewards source metadata');
 if(blobSha(await readFile(resolve(MOBILE,REWARDS_TARGET),'utf8'))!==rewards.blobSha)
  throw Error('Website Rewards UI drift; regenerate before merging');
 for(const item of SOURCE_MODULES){
  const saved=manifest[item.key];
  if(!saved||saved.source!==item.source||saved.target!==item.target||
     !/^[a-f0-9]{40}$/.test(saved.sourceSha)||!/^[a-f0-9]{40}$/.test(saved.blobSha))
   throw Error('Missing pinned website module '+item.key);
  const current=await readFile(resolve(MOBILE,item.target),'utf8');
  if(blobSha(current)!==saved.blobSha)
   throw Error('Website source drift: '+item.target);
 }
 const more=[
  {key:'rosterPrestige',source:ROSTER_PRESTIGE_SOURCE,target:ROSTER_PRESTIGE_TARGET},
  {key:'puppyNames',source:PUPPY_NAMES_SOURCE,target:PUPPY_NAMES_TARGET},
  {key:'inventoryContract',source:INVENTORY_CONTRACT_SOURCE,target:INVENTORY_CONTRACT_TARGET}
 ];
 for(const item of more){
  const saved=manifest[item.key];
  if(!saved||saved.source!==item.source||saved.target!==item.target||
     !/^[a-f0-9]{40}$/.test(saved.sourceSha)||!/^[a-f0-9]{40}$/.test(saved.blobSha))
   throw Error('Missing pinned website Part 4 source '+item.key);
  if(blobSha(await readFile(resolve(MOBILE,item.target),'utf8'))!==saved.blobSha)
   throw Error('Website Part 4 source drift: '+item.target);
 }
 console.log('Canonical website UI sources, Part 4 screens and contracts verified');
}
async function fetchText(url,headers){
 const response=await fetch(url,{headers});
 if(!response.ok)throw Error('Website UI fetch HTTP '+response.status+' for '+url);
 return response.text();
}
if(process.argv.includes('--verify'))await verify();
else{
 const ref=process.env.PUPPY_WEBSITE_UI_REF||'main';
 if(!/^[A-Za-z0-9._/-]{1,128}$/.test(ref)||ref.startsWith('/')||ref.includes('..'))
  throw Error('Invalid website source branch');
 const headers={Accept:'application/vnd.github+json','User-Agent':'Puppy-Clicker-Android-UI-sync',
  ...(process.env.GH_TOKEN?{Authorization:'Bearer '+process.env.GH_TOKEN}:{})};
 const commitResult=JSON.parse(await fetchText(
  'https://api.github.com/repos/'+UPSTREAM+'/commits/'+encodeURIComponent(ref),headers));
 const commit=commitResult.sha;
 if(!/^[a-f0-9]{40}$/.test(commit))throw Error('Invalid website commit hash');
 const components=[];
 for(const item of COMPONENTS){
  const url='https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+item.source;
  const raw=await fetchText(url,{'User-Agent':'Puppy-Clicker-Android-UI-sync'});
  assertNativeComponent(raw,item.source);
  const body=adaptWebsiteImages(item.source,raw);
  assertNativeComponent(body,item.target);
  const targetPath=resolve(MOBILE,item.target);
  const previous=await readFile(targetPath,'utf8').catch(()=>null);
  if(previous!==body)await writeFile(targetPath,body);
  components.push({...item,sourceSha:blobSha(raw),blobSha:blobSha(body)});
 }
 const navRaw=await fetchText(
  'https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+NAVIGATION_SOURCE,
  {'User-Agent':'Puppy-Clicker-Android-UI-sync'});
 const navGenerated=extractWebsiteTabs(navRaw);
 if((await readFile(resolve(MOBILE,NAVIGATION_TARGET),'utf8').catch(()=>null))!==navGenerated)
  await writeFile(resolve(MOBILE,NAVIGATION_TARGET),navGenerated);
 const navigation={source:NAVIGATION_SOURCE,target:NAVIGATION_TARGET,
  sourceSha:blobSha(navRaw),blobSha:blobSha(navGenerated)};
 const sourceApp=await fetchText(
  'https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+CARE_SHOP_SOURCE,
  {'User-Agent':'Puppy-Clicker-Android-UI-sync'});
 const careShopGenerated=generateCareShop(sourceApp);
 const generatedTarget=resolve(MOBILE,CARE_SHOP_TARGET);
 if((await readFile(generatedTarget,'utf8').catch(()=>null))!==careShopGenerated)
  await writeFile(generatedTarget,careShopGenerated);
 const careShop={source:CARE_SHOP_SOURCE,target:CARE_SHOP_TARGET,
  sourceSha:blobSha(sourceApp),blobSha:blobSha(careShopGenerated)};
 const rewardsGenerated=generateRewards(sourceApp);
 if((await readFile(resolve(MOBILE,REWARDS_TARGET),'utf8').catch(()=>null))!==rewardsGenerated)
  await writeFile(resolve(MOBILE,REWARDS_TARGET),rewardsGenerated);
 const rewards={source:REWARDS_SOURCE,target:REWARDS_TARGET,
  sourceSha:blobSha(sourceApp),blobSha:blobSha(rewardsGenerated)};
 const syncedModules={};
 for(const item of SOURCE_MODULES){
  const raw=await fetchText(
   'https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+item.source,
   {'User-Agent':'Puppy-Clicker-Android-UI-sync'});
  const body=adaptPureWebsiteModule(item,raw);
  if((await readFile(resolve(MOBILE,item.target),'utf8').catch(()=>null))!==body)
   await writeFile(resolve(MOBILE,item.target),body);
  syncedModules[item.key]={source:item.source,target:item.target,
   sourceSha:blobSha(raw),blobSha:blobSha(body)};
 }
 const rosterGenerated=generateRosterPrestige(sourceApp);
 if((await readFile(resolve(MOBILE,ROSTER_PRESTIGE_TARGET),'utf8').catch(()=>null))!==rosterGenerated)
  await writeFile(resolve(MOBILE,ROSTER_PRESTIGE_TARGET),rosterGenerated);
 const rosterPrestige={source:ROSTER_PRESTIGE_SOURCE,target:ROSTER_PRESTIGE_TARGET,
  sourceSha:blobSha(sourceApp),blobSha:blobSha(rosterGenerated)};
 const srcNames=await fetchText(
  'https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+PUPPY_NAMES_SOURCE,
  {'User-Agent':'Puppy-Clicker-Android-UI-sync'});
 const nameGenerated=generatePuppyNames(srcNames);
 if((await readFile(resolve(MOBILE,PUPPY_NAMES_TARGET),'utf8').catch(()=>null))!==nameGenerated)
  await writeFile(resolve(MOBILE,PUPPY_NAMES_TARGET),nameGenerated);
 const puppyNames={source:PUPPY_NAMES_SOURCE,target:PUPPY_NAMES_TARGET,
  sourceSha:blobSha(srcNames),blobSha:blobSha(nameGenerated)};
 const srcInventory=await fetchText(
  'https://raw.githubusercontent.com/'+UPSTREAM+'/'+commit+'/'+INVENTORY_CONTRACT_SOURCE,
  {'User-Agent':'Puppy-Clicker-Android-UI-sync'});
 const inventoryGenerated=generateInventoryContract(srcInventory);
 if((await readFile(resolve(MOBILE,INVENTORY_CONTRACT_TARGET),'utf8').catch(()=>null))!==inventoryGenerated)
  await writeFile(resolve(MOBILE,INVENTORY_CONTRACT_TARGET),inventoryGenerated);
 const inventoryContract={source:INVENTORY_CONTRACT_SOURCE,target:INVENTORY_CONTRACT_TARGET,
  sourceSha:blobSha(srcInventory),blobSha:blobSha(inventoryGenerated)};
 const desired=JSON.stringify({upstream:UPSTREAM,commit,components,navigation,careShop,rewards,...syncedModules,rosterPrestige,puppyNames,inventoryContract},null,2)+'\n';
 if((await readFile(MANIFEST,'utf8').catch(()=>null))!==desired)
  await writeFile(MANIFEST,desired);
 await verify();
 console.log('Native website UI components follow '+UPSTREAM+'@'+commit);
}
