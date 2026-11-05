import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const mobile=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const load=path=>readFileSync(resolve(mobile,path),'utf8');
function blobSha(body){
 const data=Buffer.from(body,'utf8');
 return createHash('sha1').update(Buffer.from('blob '+data.length+'\0')).update(data).digest('hex');
}
test('website-owned account details UI has no independent Android copy drift',()=>{
 const source=JSON.parse(load('src/production/websiteUiSources.json'));
 assert.equal(source.upstream,'markhitchk/Puppy-Clicker');
 assert.match(source.commit,/^[a-f0-9]{40}$/);
 assert.deepEqual(source.components.map(({source,target})=>[source,target]),[
  ['src/native/src/components/AccountDetailsScreen.jsx','src/components/AccountDetailsScreen.jsx']
 ]);
 for(const item of source.components){
  const body=load(item.target);
  assert.equal(blobSha(body),item.blobSha,item.target);
  assert.match(body,/from 'react-native'/);
  assert.doesNotMatch(body,/<(?:div|button|section)\b|\b(?:window|document|localStorage)\s*\./);
 }
 const check=spawnSync(process.execPath,['scripts/sync-website-native-ui.mjs','--verify'],{
  cwd:mobile,encoding:'utf8'
 });
 assert.equal(check.status,0,check.stderr||check.stdout);
});
