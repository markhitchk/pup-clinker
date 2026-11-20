// Public PupDex URLs accept stable game IDs and source asset filenames.
// Canonical share URLs use ?pup=v1_midnight; older ?=v1_midnight URLs work too.
export const PUPPY_QUERY_KEYS=['pup','puppy','id','asset',''];

export function puppyAssetId(puppy){
 const match=String(puppy?.image||'').split('/').pop()?.match(/^([^/?#]+)\.(?:png|webp|svg)$/i);
 return match?.[1]||String(puppy?.id||'');
}

export function readPuppyQuery(search){
 const params=new URLSearchParams(search);
 for(const key of PUPPY_QUERY_KEYS){
  const value=params.get(key)?.trim();
  if(value)return value;
 }
 // Treat ?v1_midnight as a shorthand, but not unrelated query keys.
 for(const [key,value] of params)if(!value&&/^(?:v1_|v2_|test_)[a-z0-9_]+$/i.test(key))return key;
 return null;
}

export function matchPuppy(puppies,identifier){
 if(typeof identifier!=='string'||identifier.length>120)return null;
 const wanted=identifier.trim().toLowerCase().replace(/\.(?:png|webp|svg)$/i,'');
 if(!wanted)return null;
 return puppies.find(p=>p.id.toLowerCase()===wanted||puppyAssetId(p).toLowerCase()===wanted)||null;
}

export function puppyDetailsHref(puppy){
 return '/pupdex?pup='+encodeURIComponent(puppyAssetId(puppy));
}

export function withoutPuppyQuery(search){
 const params=new URLSearchParams(search);
 for(const key of PUPPY_QUERY_KEYS)params.delete(key);
 for(const [key,value] of [...params])if(!value&&/^(?:v1_|v2_|test_)[a-z0-9_]+$/i.test(key))params.delete(key);
 const rest=params.toString();
 return rest?'?'+rest:'';
}
