// UI capabilities only: existing verified gameplay actions remain the authority.
export const collectionModes=Object.freeze({
 roster:Object.freeze({ownedOnly:false,gameActions:true,showFavorites:true,copyLink:true,viewAsset:true}),
 inventory:Object.freeze({ownedOnly:true,gameActions:false,showFavorites:true,copyLink:true,viewAsset:true}),
 pupdex:Object.freeze({ownedOnly:false,gameActions:false,showFavorites:false,copyLink:true,viewAsset:true})
});
export function collectionCapabilities(mode,{owned=false,current=false,freeTest=false}={}){
 const config=collectionModes[mode]||collectionModes.pupdex;
 return {...config,canUse:config.gameActions&&owned&&!current,
  canRename:config.gameActions&&owned&&current,
  canFavorite:config.gameActions&&owned,
  canClaim:config.gameActions&&!owned&&freeTest};
}
