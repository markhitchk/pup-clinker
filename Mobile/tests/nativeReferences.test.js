import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync,readFileSync,readdirSync,statSync} from 'node:fs';
import {dirname,extname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

// Metro is not needed to verify static local module and artwork references.
// Validate *all* first-party JS/JSX before Expo's release bundle task.
const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
function walk(root){
 return readdirSync(root,{withFileTypes:true}).flatMap(entry=>{
  const path=join(root,entry.name);
  return entry.isDirectory()?walk(path):entry.isFile()&&/\.(?:js|jsx)$/.test(entry.name)?[path]:[];
 });
}
function resolved(path){
 const variants=[path,path+'.js',path+'.jsx',path+'.json',join(path,'index.js'),join(path,'index.jsx')];
 return variants.find(existsSync)||null;
}
function references(source){
 // Require a quoted path: catches static Metro require() with png assets.
 // Also checks from './module' and import './side-effect'.
 const results=[];
 for(const match of source.matchAll(/\brequire\s*\(\s*(['"])(\.[^'"\n]+)\1\s*\)/g)){
  results.push({type:'require',path:match[2]});
 }
 for(const match of source.matchAll(/\bfrom\s*(['"])(\.[^'"\n]+)\1/g)){
  results.push({type:'from',path:match[2]});
 }
 for(const match of source.matchAll(/\bimport\s*(['"])(\.[^'"\n]+)\1/g)){
  results.push({type:'sideEffectImport',path:match[2]});
 }
 return results;
}
test('all local Metro imports and static PNG requires resolve in source tree',()=>{
 const directories=['src','App.js','index.js','app.config.js'];
 const sources=[...walk(join(mobile,'src')),...directories.filter(x=>x.endsWith('.js')).map(x=>join(mobile,x))];
 const broken=[],inspected=new Set();
 for(const filename of sources){
  if(inspected.has(filename))continue;
  inspected.add(filename);
  const content=readFileSync(filename,'utf8');
  for(const ref of references(content)){
   const p=resolve(dirname(filename),ref.path);
   const actual=resolved(p);
   if(!actual)broken.push(filename.replace(mobile+'/','')+' -> '+ref.path);
   else if(ref.type==='require'&&/\.(?:png|jpe?g|webp)$/.test(actual)){
    const stat=statSync(actual);
    if(stat.size===0)broken.push('Empty bundled image: '+actual.replace(mobile+'/',''));
   }
  }
 }
 assert.deepEqual(broken,[],broken.join('\n'));
 assert.ok(inspected.size>=20,'Metro static-source coverage unexpectedly low');
});
test('six Casino components import only local art that Metro can bundle offline',()=>{
 const casino=['SlotsV6.jsx','RouletteV6.jsx','PlinkoV6.jsx','ScratchersV6.jsx',
  'ScratchStage.jsx','LuckyWheelV6.jsx'];
 const broken=[];
 let artCount=0;
 for(const component of casino){
  const filename=join(mobile,'src','components',component);
  const source=readFileSync(filename,'utf8');
  for(const m of references(source).filter(r=>r.type==='require')){
   artCount++;
   const resolvedPath=resolve(dirname(filename),m.path);
   if(!existsSync(resolvedPath))broken.push(component+' -> '+m.path);
  }
 }
 assert.deepEqual(broken,[]);
 assert.ok(artCount>=20,'Expected original Casino art in all six native screens');
});
