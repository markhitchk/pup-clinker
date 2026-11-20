import React from 'react';
import {PuppyArtwork, Box, Badge, Title, Dot, Article, Select} from '../../src/components/CollectionCardHost.jsx';
import {rarityLabels} from './puppyRarity.js';
import {collectionCapabilities} from './puppyCollectionModes.js';


// One web collection card for the game roster, inventory and public PupDex.
// Selection only opens details; all mutations remain in the verified game modal.
export default function PuppyCollectionCard({area='pupdex',puppy,name,image,group,rarity,unlock,statuses=[],favorite=false,
 href,onSelect,label,cardClass='',contentClass='',buttonClass='',data={},artVersion=0}){
 const tier=rarity||'common';
 const caps=collectionCapabilities(area);
 const rarityName=rarityLabels[rarity]?.replace(/^\S+\s+/u,'')||'Legacy owned';
 const content=<>
  <Box className="meet-puppy-art">
   <PuppyArtwork key={puppy.id+':'+artVersion} id={puppy.id} name={name+' puppy artwork'} uri={image} size={215} lazy/>
   <Badge className="meet-puppy-group">{group}</Badge>
   {caps.showFavorites&&favorite&&<Badge className="collection-card-favorite" aria-label="Favorite">♥</Badge>}
  </Box>
  <Box className="meet-puppy-body">
   <Title>{name}</Title>
   <Box className="meet-puppy-badges" aria-label="Rarity and availability">
    <Badge className="meet-puppy-badge meet-puppy-badge-rarity"><Dot className="pup-rarity-dot" aria-hidden="true"/>{rarityName}</Badge>
    {unlock&&<Badge className="meet-puppy-badge collection-card-unlock">{unlock}</Badge>}
    {statuses.map(({text,className=''})=><Badge key={text} className={'meet-puppy-badge '+className}>{text}</Badge>)}
   </Box>
  </Box>
 </>;
 const cls='meet-puppy-link collection-card-content '+contentClass;
 return <Article className={'meet-puppy-card collection-card '+cardClass} data-rarity={tier} data-collection-mode={area} {...data}>
  {href?<Select className={cls} href={href} onClick={onSelect} aria-label={label} aria-haspopup="dialog">{content}</Select>:
   <Box className={cls}>{content}<Select type="button" className={'collection-card-select '+buttonClass}
    aria-label={label} aria-haspopup="dialog" onClick={onSelect}/></Box>}
 </Article>;
}
