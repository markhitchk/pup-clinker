import {useEffect,useState} from 'react';
import {AppState} from 'react-native';

export type RarityRow = {
 puppy_id:string; rarity:string; acquisition:string; published:boolean;
 bound:boolean; giftable:boolean; tradeable:boolean;
};
type Catalog = {rows:RarityRow[]; loading:boolean; error:boolean};
const endpoint='https://damcmyqusdxucwozkoeh.supabase.co/rest/v1/rpc/pup_rarity_public';
const publishableKey=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
export function usePublicPuppyCatalog():Catalog{
 const [catalog,setCatalog]=useState<Catalog>({rows:[],loading:true,error:false});
 useEffect(()=>{
  let active=true;
  let controller:AbortController|undefined;
  async function refresh(){
   controller?.abort();
   const request=new AbortController();
   controller=request;
   const timeout=setTimeout(()=>request.abort(),15000);
   try{
    const response=await fetch(endpoint,{method:'POST',signal:request.signal,
     headers:{apikey:publishableKey,'Content-Type':'application/json'},body:'{}'});
    if(!response.ok)throw Error('Rarity catalog unavailable');
    const rows:unknown=await response.json();
    if(!Array.isArray(rows)||!rows.every(row=>typeof row?.puppy_id==='string'&&typeof row?.rarity==='string'))
     throw Error('Invalid rarity catalog');
    if(active&&request===controller)setCatalog({rows,loading:false,error:false});
   }catch{
    if(active&&request===controller)setCatalog(previous=>({...previous,loading:false,error:true}));
   }finally{clearTimeout(timeout);}
  }
  void refresh();
  const listener=AppState.addEventListener('change',state=>{if(state==='active')void refresh();});
  return ()=>{active=false;controller?.abort();listener.remove();};
 },[]);
 return catalog;
}
