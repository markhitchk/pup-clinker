import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

import {compileGameplay} from './website-screens.mjs';
import {parseSync} from '@babel/core';

const root=fileURLToPath(new URL('../',import.meta.url));
const config=JSON.parse(readFileSync(new URL('./website-files.json',import.meta.url)));
const lockPath=path.join(root,'website-source.json');
const digest=data=>createHash('sha256').update(data).digest('hex');
const args=process.argv.slice(2);
const lock=existsSync(lockPath)?JSON.parse(readFileSync(lockPath)):null;
if(args[0]==='--verify'){
  if(!lock || !/^[0-9a-f]{40}$/.test(lock.commit))throw Error('Missing website revision');
  if(Object.keys(config).length!==Object.keys(lock.files).length)throw Error('Website source inventory changed');
  for(const [dest,source] of Object.entries(config)){
    const item=lock.files[dest];
    if(item?.source!==source || digest(readFileSync(path.join(root,dest)))!==item.sha256)
      throw Error('Website source drift at '+dest);
  }
  console.log('Verified '+Object.keys(config).length+' shared files from '+lock.commit);
  process.exit(0);
}
if(args.length!==2 || !/^[0-9a-f]{40}$/.test(args[1]))
  throw Error('Usage: node scripts/sync-website.mjs WEBSITE_CHECKOUT FULL_COMMIT_SHA');
const [checkout,commit]=args;
const git=(...args)=>execFileSync('git',['-C',checkout,...args],{maxBuffer:64*1024*1024});
if(git('rev-parse',commit+'^{commit}').toString().trim()!==commit)throw Error('Commit verification failed');
function forMetro(source,contents){
  if(source==='src/PuppyCollectionCard.jsx'){
    // Preserve the production card JSX tree, labels and conditional controls.
    // Android supplies host views for the HTML tags used in this one component.
    const hosts={div:'Box',span:'Badge',h3:'Title',i:'Dot',article:'Article',a:'Select',button:'Select'};
    return contents.replace("import PuppyArtwork from './native/web/PuppyArtwork.jsx';",
      "import {PuppyArtwork, Box, Badge, Title, Dot, Article, Select} from '../../src/components/CollectionCardHost.jsx';")
      .replace("import './styles/collection-card.css';",'')
      .replace(/<(\/?)([a-z][\w-]*)(?=[\s/>])/g,(_,close,tag)=>{
        if(!Object.hasOwn(hosts,tag))throw Error('Unmapped collection element: '+tag);
        return '<'+close+hosts[tag];
      });
  }
  if(!source.startsWith('src/native/src/components/'))return contents;
  const transformed=contents.replace(/\(\{uri:"(?:https:\/\/raw\.githubusercontent\.com\/markhitchk\/pup-clinker\/[a-f0-9]{40}\/Mobile)?\/assets\/([a-z_]+\/[a-z0-9_]+\.png)"\}\)/g,(_,name)=>{
    if(!existsSync(path.join(root,'assets',name)))throw Error('Missing Android artwork: '+name);
    return "require('../../assets/"+name+"')";
  });
  if(/uri:\s*["'](?:\/assets\/|https:\/\/raw\.githubusercontent)/.test(transformed))throw Error('Unmapped image URI in '+source);
  return transformed;
}
const updates=[];
const files={};
for(const [dest,source] of Object.entries(config)){
  const data=git('show',commit+':'+source);
  let output=data;
  if(source.endsWith('.jsx'))output=Buffer.from(forMetro(source,data.toString('utf8')));
  if(dest==='website/src/GameplayScreens.jsx')output=Buffer.from(compileGameplay(data.toString()));
  if(dest==='website/src/webGoalState.js')output=Buffer.from(data.toString().replaceAll("from '../src/engine/","from '../../src/engine/"));
  if(dest==='website/src/playRevamp.js')output=Buffer.from(data.toString().replace("from './monthlyRewards.js'","from '../../src/engine/monthlyRewards.js'"));
  if(dest==='website/src/PuppyPlatform.jsx'){
    const source=data.toString();
    const ast=parseSync(source,{babelrc:false,configFile:false,parserOpts:{plugins:['jsx']}});
    const node=ast.program.body.find(n=>n.declaration?.id?.name==='PuppyPlatform');
    if(!node)throw Error('Website PuppyPlatform component missing');
    const body=source.slice(node.start,node.end).replace(/<(\/?)(div|span|h2)(?=[\s/>])/g,'<$1GameHost.$2');
    output=Buffer.from("import React from 'react';\nimport {GameHost} from '../../src/components/GameHost.jsx';\n"+body+'\n');
  }
  if(dest==='website/rarity-colors.json'){
    const colors={};
    const rules=data.toString().matchAll(/\[data-rarity="([a-z]+)"\]\s*\{\s*--rarity-rgb:\s*([\d ]+);\s*--rarity-ink:\s*(#[0-9a-f]+);/gi);
    for(const [,tier,rgb,ink] of rules)colors[tier]={rgb:rgb.trim().split(/\s+/).join(','),ink};
    if(Object.keys(colors).length!==6)throw Error('Review changed rarity color rules');
    output=Buffer.from(JSON.stringify(colors,null,2)+'\n');
  }
  updates.push([path.join(root,dest),output]);
  files[dest]={source,sha256:digest(output)};
}
for(const [name,data] of updates){
  mkdirSync(path.dirname(name),{recursive:true});
  writeFileSync(name,data);
}
writeFileSync(lockPath,JSON.stringify({repository:'markhitchk/Puppy-Clicker',commit,files},null,2)+'\n');
console.log('Synchronized '+updates.length+' files from '+commit);
