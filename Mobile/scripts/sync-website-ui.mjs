// One-way website -> Android style sync. No website mutations.
// Source: CURRENT website CSS (src/native/web/kawaii.css on main).
// Converts supported CSS custom-property colors to Android-native JS tokens.
// Browser DOM/CSS layouts cannot be executed directly by React Native.
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';

const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const target=resolve(mobile,'src/production/websiteDesignTokens.js');
const theme=resolve(mobile,'src/production/websiteTheme.js');
const website='markhitchk/Puppy-Clicker';
const source='src/native/web/kawaii.css';
const mapping=Object.freeze({
 bg:'bg-primary',surface:'bg-secondary',card:'bg-elevated',modal:'bg-modal',
 cyan:'accent-cyan',cyanSoft:'accent-cyan-soft',purple:'accent-purple',
 pink:'accent-pink',gold:'accent-gold',green:'accent-green',
 text:'text-primary',muted:'text-secondary',tertiary:'text-tertiary',
 onAccent:'on-accent'
});
const readLocal=async p=>readFile(p,'utf8');
const isHash=x=>/^[a-f0-9]{40}$/.test(x);
const cssValue=value=>{
 if(!/^#[a-f0-9]{3}(?:[a-f0-9]{3})?$/i.test(value))throw Error('Website CSS color is not a supported hex token: '+value);
 const h=value.slice(1);
 return '#'+(h.length===3?h.split('').map(c=>c+c).join(''):h).toUpperCase();
};
function getTokens(css){
 // The website currently defines a :root selector with all canonical UI colors.
 const roots=[...css.matchAll(/:root\s*\{([^}]+)\}/g)];
 if(!roots.length)throw Error('Canonical website root tokens not found');
 const variables=new Map();
 for(const [,body] of roots)for(const [,name,raw] of body.matchAll(/--([a-z0-9-]+)\s*:\s*([^;\}]+)/ig))
  variables.set(name,raw.trim());
 return Object.fromEntries(Object.entries(mapping).map(([key,cssKey])=>{
  const raw=variables.get(cssKey);
  if(!raw)throw Error('Missing website CSS token: '+cssKey);
  return [key,cssValue(raw)];
 }));
}
const build=(meta,tokens)=>'// Generated from the existing, unchanged Puppy Clicker website CSS.\n'+
 '// Edit website styling on the website. Update Android with sync-website-ui.mjs.\n'+
 'export const WEBSITE_UI_PROVENANCE=Object.freeze('+JSON.stringify(meta,null,2)+');\n'+
 'export const WEBSITE_DARK_TOKENS=Object.freeze('+JSON.stringify(tokens,null,2)+');\n';
const verify=async()=>{
 const content=await readLocal(target);
 const themeContent=await readLocal(theme);
 if(!themeContent.includes("from './websiteDesignTokens.js'")||
    !content.includes('export const WEBSITE_UI_PROVENANCE=')||
    !content.includes('export const WEBSITE_DARK_TOKENS='))throw Error('Android website CSS token integration is incomplete');
 for(const key of Object.keys(mapping))
  if(!content.includes('"'+key+'":'))throw Error('Missing generated website token: '+key);
 console.log('Native website theme tokens verified');
};
if(process.argv.includes('--verify'))await verify();
else{
 const ref=process.env.PUPPY_WEBSITE_UI_REF||'main';
 if(!/^[A-Za-z0-9._/-]{1,128}$/.test(ref)||ref.startsWith('/')||ref.includes('..'))
  throw Error('Invalid website source branch');
 const headers={Accept:'application/vnd.github+json','User-Agent':'Puppy-Clicker-Android-UI-sync',
  ...(process.env.GH_TOKEN?{Authorization:'Bearer '+process.env.GH_TOKEN}:{})};
 const commitRes=await fetch('https://api.github.com/repos/'+website+'/commits/'+encodeURIComponent(ref),{headers});
 if(!commitRes.ok)throw Error('Cannot resolve website revision: HTTP '+commitRes.status);
 const commit=(await commitRes.json()).sha;
 if(!isHash(commit))throw Error('Invalid website commit SHA');
 const sourceRes=await fetch('https://raw.githubusercontent.com/'+website+'/'+commit+'/'+source);
 if(!sourceRes.ok)throw Error('Cannot read the website stylesheet: HTTP '+sourceRes.status);
 const css=await sourceRes.text();
 if(css.length<300||css.length>150000)throw Error('Unexpected website stylesheet size');
 const tokens=getTokens(css);
 const body=Buffer.from(css,'utf8');
 const blobSha=createHash('sha1').update(Buffer.from('blob '+body.length+'\0')).update(body).digest('hex');
 const meta={upstream:website,source,commit,blobSha};
 const wanted=build(meta,tokens),current=await readLocal(target).catch(()=>null);
 if(current!==wanted)await writeFile(target,wanted);
 await verify();
 console.log(current===wanted?'Android colors already match website':'Android color tokens updated from website '+commit);
}
