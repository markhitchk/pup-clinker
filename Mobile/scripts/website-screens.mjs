// Read named components from the actual website router's Play module. Fail the
// sync when a component disappears, or starts using a new platform dependency.
import {parseSync,traverse} from '@babel/core';

const screens=['DailyGoalCard','PlayScreen','DesktopPlayScreen','CareScreen','ShopScreen','PrestigeScreen'];
const supplied=new Set(['Wallet','Portrait','Txt','Button','Card','PageHeader','styles','PuppyPlatform','RenamePuppyDialog','webGoalState','nextPlayReward']);
const hosts=new Set(['div','section','aside','article','span','strong','p','small','h1','h2','button']);
export function compileGameplay(source){
 const ast=parseSync(source,{sourceType:'module',babelrc:false,configFile:false,parserOpts:{plugins:['jsx']}});
 const imported=new Map(), found=new Map();
 for(const node of ast.program.body){
  if(node.type==='ImportDeclaration'){
   for(const spec of node.specifiers)imported.set(spec.local.name,node.source.value);
  }
  if(node.type==='FunctionDeclaration'&&screens.includes(node.id.name))found.set(node.id.name,node);
 }
 const needed=new Set(['React']);
 traverse(ast,{FunctionDeclaration(p){
  if(!found.has(p.node.id?.name))return;
  p.traverse({ReferencedIdentifier(id){
   const name=id.node.name;
   if(imported.has(name)&&!supplied.has(name))needed.add(name);
   else if(id.scope.getBinding(name)?.scope.path.isProgram()&&!supplied.has(name)&&!screens.includes(name)&&!imported.has(name))
    throw Error('Review new website gameplay dependency '+name);
  }});
 }});
 const imports=new Map();
 for(const name of needed){
  let from=imported.get(name);
  if(from?.startsWith('./src/'))from='../../src/'+from.slice(6);
  else if(from!=='react'&&from!=='react-native')throw Error('Review new website gameplay import '+from);
  if(!imports.has(from))imports.set(from,[]);
  imports.get(from).push(name);
 }
 const lines=[];
 for(const [from,names] of imports){
  const named=names.filter(n=>n!=='React').map(n=>n==='C'?'colors as C':n);
  lines.push('import '+[names.includes('React')?'React':null,named.length?'{'+named.join(',')+'}':null].filter(Boolean).join(',')+' from '+JSON.stringify(from)+';');
 }
 lines.push("import {GameHost,RenamePuppyDialog} from '../../src/components/GameHost.jsx';",
  "import {PuppyPlatform} from './PuppyPlatform.jsx';",
  "import {webGoalState} from './webGoalState.js';","import {nextPlayReward} from './playRevamp.js';",
  'export function createGameplayScreens({Wallet,Portrait,Txt,Button,Card,PageHeader,styles}){');
 for(const name of screens){
  const node=found.get(name);
  if(!node)throw Error('Missing website component '+name);
  const body=source.slice(node.start,node.end).replace(/<(\/?)([a-z][\w-]*)(?=[\s/>])/g,(_,close,tag)=>{
   if(!hosts.has(tag))throw Error('Review new gameplay element '+tag);
   return '<'+close+'GameHost.'+tag;
  });
  // Both signed platforms settle daily goals against the server's UTC day.
  lines.push(body.replace("{serverDay:Platform.OS==='web'}","{serverDay:true}"));
 }
 lines.push('return {'+screens.join(',')+'};','}');
 return lines.join('\n')+'\n';
}
