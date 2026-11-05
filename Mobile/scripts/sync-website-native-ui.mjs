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
const COMPONENTS=Object.freeze([
 {source:'src/native/src/components/AccountDetailsScreen.jsx',
  target:'src/components/AccountDetailsScreen.jsx'},
 {source:'src/native/src/components/NotificationInbox.jsx',
  target:'src/components/NotificationInbox.jsx'},
 {source:'src/native/src/production/AccountOnboarding.jsx',
  target:'src/production/AccountOnboarding.jsx'}
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
 const matches=[...source.matchAll(/export const MAIN_TABS=Object\\.freeze\\((\\[[^\\n]*\\])\\);/g)];
 if(matches.length!==1)throw Error('Review canonical website tab definition before syncing');
 const tabIds=[...matches[0][1].matchAll(/\\['([^']+)','[^']+','[^']+'\\]/g)].map(item=>item[1]);
 if(tabIds.join(',')!=='play,care,roster,shop,rewards')
  throw Error('Website primary tab membership changed; review required');
 return '// GENERATED from website '+NAVIGATION_SOURCE+'. Do not hand-edit.\\n'+
  'export const MAIN_TABS=Object.freeze('+matches[0][1]+');\\n';
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
 console.log('Canonical website native UI and navigation hashes verified');
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
 const desired=JSON.stringify({upstream:UPSTREAM,commit,components,navigation},null,2)+'\n';
 if((await readFile(MANIFEST,'utf8').catch(()=>null))!==desired)
  await writeFile(MANIFEST,desired);
 await verify();
 console.log('Native website UI components follow '+UPSTREAM+'@'+commit);
}
