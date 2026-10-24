import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {PNG} from 'pngjs';
import {stripEdgeMatte,shouldRemoveMatte,adaptiveForeground} from '../scripts/prepare-legacy-puppies.mjs';

const root=resolve(fileURLToPath(new URL('..',import.meta.url)));
test('opaque original V1 classic PNG has Kotlin-matte removal and no source mutation',()=>{
 const src=readFileSync(join(root,'assets/v1/v1_classic.png'));
 assert.equal(shouldRemoveMatte(src),true,'Kotlin only converts non-alpha RGB legacy files');
 const generated=join(root,'assets/generated/puppies/v1/v1_classic.png');
 assert.ok(existsSync(generated),'Postinstall must generate transparent version before Metro');
 const image=PNG.sync.read(readFileSync(generated));
 assert.equal(image.width,1254);assert.equal(image.height,1254);
 const edges=[[0,0],[1253,0],[0,1253],[1253,1253]];
 for(const [x,y] of edges)
   assert.equal(image.data[(y*image.width+x)*4+3],0,'matte corner must be transparent');
 assert.equal(src[25],2,'original committed PNG must stay opaque RGB');
});

test('dark interior must remain opaque; only outer-connected near-black becomes alpha zero',()=>{
 const p=new PNG({width:5,height:5});
 for(let i=0;i<25;i++){
   const x=i%5,y=Math.floor(i/5),inside=x>=1&&x<=3&&y>=1&&y<=3;
   p.data[i*4]=p.data[i*4+1]=p.data[i*4+2]=inside?200:0;
   p.data[i*4+3]=255;
 }
 // The black center is a puppy eye fully enclosed by lighter fur.
 const center=12*4;
 p.data[center]=p.data[center+1]=p.data[center+2]=0;
 const altered=stripEdgeMatte(p);
 assert.equal(altered,16,'only border-connected pixels become transparent');
 assert.equal(p.data[3],0);
 assert.equal(p.data[center+3],255);
 assert.equal(p.data[6*4+3],255);
});

test('alpha-equipped original V2 portrait bypasses Kotlin edge matte stripping',()=>{
 const p=readFileSync(join(root,'assets/v2/v2_dev_pup.png'));
 assert.equal(p[25],6,'original V2 image includes alpha');
 assert.equal(shouldRemoveMatte(p),false);
});

test('generated Expo foreground retains source logo proportions inside adaptive icon safe zone',()=>{
 const original=PNG.sync.read(readFileSync(join(root,'assets/logos/puppy_clicker.png')));
 const fg=adaptiveForeground(original);
 assert.equal(fg.width,1024);
 assert.equal(fg.height,1024);
 assert.equal(fg.data[3],0,'adaptive icon should have transparent corners');
 assert.ok(existsSync(join(root,'assets/generated/branding/launcher-adaptive.png')));
});

test('React renders generated static portraits without replacing original bundled roster map',()=>{
 const app=readFileSync(join(root,'App.js'),'utf8');
 const converted=readFileSync(join(root,'src/assets/puppies-transparent.js'),'utf8');
 assert.match(app,/MATTE_FREE_PUPPY_IMAGES\[puppy\.id\]/);
 assert.match(converted,/"classic": require\('\.\.\/\.\.\/assets\/generated\/puppies\/v1\/v1_classic\.png'\)/);
 assert.match(app,/const ratio=0\.96/);
});
