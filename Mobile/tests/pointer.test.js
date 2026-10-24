import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const states=['default','hover','click','text_select','link','grab','grabbing','not_allowed','working','precision','alternate','secondary'];
test('all original mouse cursors retain exact PNG bytes, dimensions and transparency',()=>{
 for(const theme of ['light','dark'])for(const state of states){
  const name=`puppy_pointer_${theme}_${state}.png`;
  const asset=readFileSync(new URL('../assets/pointers/'+name,import.meta.url));
  assert.deepEqual(asset,readFileSync(new URL('../../App/app/src/main/res/drawable-nodpi/'+name,import.meta.url)));
  assert.equal(asset.readUInt32BE(16),64);assert.equal(asset.readUInt32BE(20),64);assert.equal(asset[25],6);
 }
});
test('native pointer plugin is idempotent and registers its view manager in Kotlin MainApplication',()=>{
 const pluginPath=new URL('../plugins/withPuppyPointer.cjs',import.meta.url);
 assert.ok(existsSync(pluginPath),'native pointer config plugin is missing');
 const {registerPackage}=require(pluginPath.pathname);
 const source='import com.facebook.react.PackageList\nval packages = PackageList(this).packages.apply {\n // custom packages\n}\n';
 const patched=registerPackage(source);
 assert.match(patched,/add\(com.harleytg.puppypointer.PuppyPointerPackage\(\)\)/);
 assert.equal(registerPackage(patched),patched);
 assert.throws(()=>registerPackage('unknown template'),/PackageList/);
});
