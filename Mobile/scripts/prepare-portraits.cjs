// Deterministic port of Kotlin LegacyPuppyTransparency.apply. Source PNGs stay
// untouched; Metro bundles the same displayed pixels Kotlin produces at decode.
const fs=require('node:fs');
const path=require('node:path');
const {PNG}=require('pngjs');
function hasAlpha(bytes){
 if([4,6].includes(bytes[25]))return true;
 for(let at=8;at+12<=bytes.length;){const n=bytes.readUInt32BE(at);if(bytes.toString('ascii',at+4,at+8)==='tRNS')return true;at+=12+n;}
 return false;
}
function restoreAlpha(bytes,folder){
 if(!['v1','v2'].includes(folder)||hasAlpha(bytes))return bytes;
 const image=PNG.sync.read(bytes),{width:w,height:h,data}=image;
 const queue=new Uint32Array(w*h);let head=0,tail=0;
 function enqueue(index){const p=index*4;if(!data[p+3]||data[p]>20||data[p+1]>20||data[p+2]>20)return;data[p+3]=0;queue[tail++]=index;}
 for(let x=0;x<w;x++){enqueue(x);if(h>1)enqueue((h-1)*w+x);}
 for(let y=1;y<h-1;y++){enqueue(y*w);if(w>1)enqueue(y*w+w-1);}
 while(head<tail){const i=queue[head++],x=i%w,y=Math.floor(i/w);if(x>0)enqueue(i-1);if(x+1<w)enqueue(i+1);if(y>0)enqueue(i-w);if(y+1<h)enqueue(i+w);}
 return tail?PNG.sync.write(image,{colorType:6}):bytes;
}
function prepare(){
 const root=path.resolve(__dirname,'../assets');let count=0;
 for(const folder of ['v1','v2']){
  const dest=path.join(root,'rendered',folder);fs.mkdirSync(dest,{recursive:true});
  for(const name of fs.readdirSync(path.join(root,folder)).filter(n=>n.endsWith('.png'))){
   const source=fs.readFileSync(path.join(root,folder,name)),output=path.join(dest,name);
   const result=restoreAlpha(source,folder);
   if(!fs.existsSync(output)||!fs.readFileSync(output).equals(result))fs.writeFileSync(output,result);
   count++;
  }
 }
 console.log(`Prepared ${count} Kotlin-compatible puppy portraits.`);
}
module.exports={restoreAlpha,prepare};
if(require.main===module)prepare();
