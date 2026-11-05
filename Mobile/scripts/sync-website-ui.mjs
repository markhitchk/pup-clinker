// Sync the website's React Native-compatible UI source into the Android build.
// The website is authoritative; Android never modifies the mirrored files.
// Run: node scripts/sync-website-ui.mjs          (fetch reviewed upstream)
//      node scripts/sync-website-ui.mjs --verify (offline integrity check)
import {createHash} from 'node:crypto';
import {mkdir,readFile,writeFile,unlink} from 'node:fs/promises';
import {dirname,resolve,relative} from 'node:path';
import {fileURLToPath} from 'node:url';

const mobileRoot=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const manifestPath=resolve(mobileRoot,'src/shared-ui/SOURCE.json');
const upstream='markhitchk/Puppy-Clicker';
const uiFolder='src/shared-ui';
const formSource='src/shared/usePupAuthForm.js';
const check=process.argv.includes('--verify');
const ref=process.env.PUPPY_WEBSITE_UI_REF||'main';
if(!/^[A-Za-z0-9._/-]{1,128}$/.test(ref)||ref.includes('..')||ref.startsWith('/'))
 throw Error('Invalid website source ref');
const targetOf=source=>{
 if(source===formSource)return 'src/shared/usePupAuthForm.js';
 if(!source.startsWith(uiFolder+'/'))throw Error('Not an approved shared UI path: '+source);
 const remainder=source.slice((uiFolder+'/').length);
 if(!remainder||remainder.split('/').some(p=>!p||p==='.'||p==='..'))
  throw Error('Invalid shared UI filename');
 if(!/\.(?:js|jsx|json)$/.test(remainder))throw Error('Unsupported shared UI extension');
 return 'src/shared-ui/'+remainder;
};
const gitSha=text=>{
 const body=Buffer.from(text,'utf8');
 return createHash('sha1').update(Buffer.from('blob '+body.length+'\0')).update(body).digest('hex');
};
async function verify(){
 const meta=JSON.parse(await readFile(manifestPath,'utf8'));
 if(meta.schema!==1||meta.upstream!==upstream||!Array.isArray(meta.files)||!meta.files.length)
  throw Error('Unrecognized shared UI source manifest');
 const set=new Set();
 for(const file of meta.files){
  if(file.target!==targetOf(file.source)||set.has(file.target)||!/^[a-f0-9]{40}$/.test(file.blobSha))
   throw Error('Invalid shared UI manifest file entry');
  set.add(file.target);
  const actual=gitSha(await readFile(resolve(mobileRoot,file.target),'utf8'));
  if(actual!==file.blobSha)throw Error('Android shared UI changed without website source: '+file.target);
 }
 if(!set.has('src/shared-ui/PupGameChrome.jsx')||!set.has('src/shared/usePupAuthForm.js'))
  throw Error('Required shared components are missing');
 console.log('Verified '+set.size+' canonical website React UI files');
 return meta;
}
if(check){
 await verify();
}else{
 const headers={'Accept':'application/vnd.github+json','User-Agent':'puppy-clicker-android-ui-sync',
  ...(process.env.GH_TOKEN?{Authorization:'Bearer '+process.env.GH_TOKEN}:{})};
 const api=async path=>{
  const res=await fetch('https://api.github.com/repos/'+upstream+path,{headers});
  if(!res.ok)throw Error('GitHub lookup failed: HTTP '+res.status+' '+path);
  return res.json();
 };
 const commitInfo=await api('/commits/'+encodeURIComponent(ref));
 const sha=commitInfo.sha;
 if(!/^[a-f0-9]{40}$/.test(sha))throw Error('Invalid upstream commit SHA');
 const entries=[];
 const visit=async folder=>{
  const contents=await api('/contents/'+folder+'?ref='+sha);
  if(!Array.isArray(contents))throw Error('Expected website shared UI directory');
  for(const item of contents){
   if(item.type==='dir')await visit(item.path);
   else if(item.type==='file'&&/\.(?:js|jsx|json)$/.test(item.path)){
    targetOf(item.path);
    entries.push(item.path);
   }
  }
 };
 await visit(uiFolder);
 entries.push(formSource);
 entries.sort();
 const files=[];
 for(const source of entries){
  const target=targetOf(source);
  const response=await fetch('https://raw.githubusercontent.com/'+upstream+'/'+sha+'/'+source);
  if(!response.ok)throw Error('Website source unavailable: '+source+' ('+response.status+')');
  const content=await response.text();
  if(content.length<80||content.length>100000)throw Error('Unexpected website UI module size: '+source);
  // Shared modules must not assume a DOM or privileged server runtime.
  if(/\b(?:document\.|window\.|localStorage\.|sessionStorage\.|process\.env\.SUPABASE_SERVICE_ROLE_KEY)\b/.test(content))
   throw Error('Browser-only or privileged code is not eligible for Android: '+source);
  const old=await readFile(resolve(mobileRoot,target),'utf8').catch(()=>null);
  if(old!==content){
   await mkdir(dirname(resolve(mobileRoot,target)),{recursive:true});
   await writeFile(resolve(mobileRoot,target),content);
  }
  files.push({source,target,blobSha:gitSha(content)});
 }
 const previous=await readFile(manifestPath,'utf8').then(JSON.parse).catch(()=>null);
 const prevFiles=previous?.files||[];
 for(const old of prevFiles){
  if(!files.some(f=>f.target===old.target)){
   const target=targetOf(old.source);
   if(target===old.target&&target!=='src/shared/usePupAuthForm.js')
    await unlink(resolve(mobileRoot,target)).catch(()=>{});
  }
 }
 const changed=files.length!==prevFiles.length||
  files.some(f=>!prevFiles.some(p=>p.target===f.target&&p.blobSha===f.blobSha));
 if(changed||!previous){
  await mkdir(dirname(manifestPath),{recursive:true});
  await writeFile(manifestPath,JSON.stringify({schema:1,upstream,commit:sha,files},null,2)+'\n');
 }
 await verify();
 console.log((changed?'Updated':'Already current')+' from website commit '+sha);
}
