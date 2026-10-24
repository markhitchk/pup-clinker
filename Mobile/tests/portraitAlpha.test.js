import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {existsSync,readFileSync} from 'node:fs';
import {PNG} from 'pngjs';
const require=createRequire(import.meta.url);
test('Kotlin edge-connected matte removal preserves enclosed black puppy details',()=>{
 const path=new URL('../scripts/prepare-portraits.cjs',import.meta.url);assert.ok(existsSync(path),'Kotlin legacy transparency cleanup is missing');
 const {restoreAlpha}=require(path.pathname),image=new PNG({width:5,height:5});
 for(let i=0;i<25;i++)image.data.set([200,150,90,255],i*4);
 for(let x=0;x<5;x++){image.data.set([1,1,1,255],x*4);image.data.set([0,0,0,255],(20+x)*4);}
 for(let y=0;y<5;y++){image.data.set([0,0,0,255],y*20);image.data.set([0,0,0,255],y*20+16);}
 image.data.set([0,0,0,255],12*4);
 const opaque=PNG.sync.write(image,{colorType:2}),out=PNG.sync.read(restoreAlpha(opaque,'v1'));
 assert.equal(out.data[3],0);assert.equal(out.data[12*4+3],255);assert.deepEqual([...out.data.slice(6*4,6*4+4)],[200,150,90,255]);
});
test('true-alpha exports and nonlegacy folders remain byte-identical',()=>{
 const path=new URL('../scripts/prepare-portraits.cjs',import.meta.url);assert.ok(existsSync(path));const {restoreAlpha}=require(path.pathname);
 const image=new PNG({width:2,height:2});image.data.fill(0);const alpha=PNG.sync.write(image,{colorType:6});
 assert.deepEqual(restoreAlpha(alpha,'v1'),alpha);
 const opaque=PNG.sync.write(image,{colorType:2});assert.deepEqual(restoreAlpha(opaque,'test'),opaque);
});
