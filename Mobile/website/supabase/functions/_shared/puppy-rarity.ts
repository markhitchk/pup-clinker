export const puppyWeights = {common:60, uncommon:25, rare:10, epic:4, legendary:1} as const;
export type RarityRow = {puppy_id:string, rarity:string, acquisition:string, published:boolean};
export function eligibleIds(ids: readonly string[], catalog: readonly RarityRow[]) {
  const allowed = new Set(catalog.filter(row => row.published && row.acquisition === 'standard' &&
    Object.hasOwn(puppyWeights,row.rarity)).map(row => row.puppy_id));
  return ids.filter(id => allowed.has(id));
}

export function weightedChoice(ids: readonly string[], catalog: readonly RarityRow[], draw:(limit:number)=>number) {
  const allowed = new Set(eligibleIds(ids,catalog));
  const tiers = Object.entries(puppyWeights).map(([rarity,weight]) => ({
    weight, ids:catalog.filter(row=>row.rarity===rarity && allowed.has(row.puppy_id)).map(row=>row.puppy_id)
  })).filter(tier=>tier.ids.length);
  if (!tiers.length) return null;
  let roll = draw(tiers.reduce((total,tier)=>total+tier.weight,0));
  for (const tier of tiers) {
    if (roll < tier.weight) return tier.ids[draw(tier.ids.length)];
    roll -= tier.weight;
  }
  throw Error('INVALID_PUPPY_ROLL');
}

export function secureInt(limit:number):number {
 if (!Number.isSafeInteger(limit) || limit<=0 || limit>0x100000000) throw Error("RNG_RANGE");
 const domain=0x100000000,threshold=domain-(domain%limit);
 const bytes=new Uint32Array(1);
 do{crypto.getRandomValues(bytes);}while(bytes[0]>=threshold);
 return bytes[0]%limit;
}
