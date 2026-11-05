// GENERATED. Canonical source: Puppy-Clicker/src/native/App.jsx.
// Website-owned Prestige and Roster JSX; all signed native actions are callbacks.
// Do not hand-edit: regenerate using scripts/sync-website-native-ui.mjs.
import React,{useEffect,useState} from 'react';
import {Alert,Pressable,ScrollView,TextInput,View,useWindowDimensions} from 'react-native';

export function createWebsiteRosterPrestigeScreens({ROSTER,LEGACY_V6_PUPPIES,legacyRosterSections,unlockedLegacyCount,PageHeader,Txt,Portrait,styles,C,Button,PuppyDetailsDialog,Card,suggestPuppyName,PRESTIGE_SKILLS,PRESTIGE_SKILL_PRESENTATION,prestigeOffer,canBuySkill,skillCost,v6Format}){
function PrestigeScreen({g,act,activeCasinoRound=false}){
 const offer=prestigeOffer(g,activeCasinoRound),compact=g.compactNumbers!==false;
 return <View style={styles.gamePage}>
  <PageHeader withLogo title="Prestige" subtitle="Permanent progression and skill points."/>
  <View style={styles.prestigeCenter}>
   <View style={styles.prestigeTitle}>
    <Txt style={{fontSize:32}}>⭐</Txt>
    <View style={{flex:1,gap:3}}>
     <Txt style={styles.prestigeCenterTitle}>Prestige Center</Txt>
     <Txt muted style={styles.small}>Prestige {g.prestigeCount} · {g.skillPoints} skill points available</Txt>
    </View>
   </View>
   {offer.points?<Txt style={styles.itemName}>This run can prestige now for +{offer.points} skill point{offer.points===1?'':'s'}.</Txt>:<>
    <Txt muted style={styles.small}>Earn {v6Format(offer.remaining,compact)} more lifetime treats this run to unlock Prestige.</Txt>
    <View style={styles.prestigeProgressTrack}><View style={[styles.fill,{backgroundColor:C.accent,width:offer.progress+'%'}]}/></View>
   </>}
   {activeCasinoRound?<Txt muted style={styles.small}>Finish the current Casino round before prestiging.</Txt>:null}
   <Button disabled={!offer.canPrestige} onPress={()=>Alert.alert('⭐ Prestige this run?',
    'You will earn '+offer.points+' skill point'+(offer.points===1?'':'s')+'. Current treats, upgrades, tickets, daily progress and run stats reset. Puppy Code unlocks, puppy collection, settings, bond, prestige level and existing skills stay.',
    [{text:'Cancel',style:'cancel'},{text:'Prestige',style:'destructive',onPress:()=>act({type:'prestige',now:Date.now()})}])}>
    {offer.canPrestige?'Prestige for +'+offer.points+' SP':'Prestige locked'}
   </Button>
  </View>
  <Txt style={styles.sectionTitle}>Permanent skill tree</Txt>
  <Txt muted style={styles.small}>Skills improve Shop upgrades; they do not give free tap power by themselves.</Txt>
  {PRESTIGE_SKILLS.map(skill=>{
   const details=PRESTIGE_SKILL_PRESENTATION[skill];
   const lvl=g.prestigeSkills[skill]||0,price=skillCost(g,skill),maxed=lvl>=5;
   return <View style={styles.prestigeSkillCard} key={skill}>
    <View style={styles.shopUpgradeHeading}>
     <Txt style={{fontSize:28}}>{details.emoji}</Txt>
     <View style={styles.shopLabel}>
      <Txt style={styles.itemName}>{details.title}</Txt>
      <Txt muted style={styles.small}>Level {lvl} / 5</Txt>
     </View>
     <Button compact disabled={!canBuySkill(g,skill)} onPress={()=>act({type:'skill',skill})}>{maxed?'MAX':price+' SP'}</Button>
    </View>
    <Txt muted style={styles.small}>{details.description}</Txt>
   </View>;
  })}
 </View>;
}

function RosterScreen({g,act,registry,onUse,onOpenExchange}){
 const {width:rosterViewport}=useWindowDimensions();
 const [search,setSearch]=useState('');
 const [status,setStatus]=useState('all');
 const [favoritesOnly,setFavoritesOnly]=useState(false);
 const [category,setCategory]=useState('all');
 const available=registry.items;
 const [sort,setSort]=useState('unlocked');
 const [inspected,setInspected]=useState(g.puppyStyle);
 const [detailsId,setDetailsId]=useState(null);
 const [view,setView]=useState('browser');
 const [renameOpen,setRenameOpen]=useState(false);
 const [editing,setEditing]=useState(g.puppyName);
 const [accessoriesOpen,setAccessoriesOpen]=useState(false),[sortOpen,setSortOpen]=useState(false),[retries,setRetries]=useState({});
 const nativeId=p=>p.assetId||(p.groupId==='v1'?'v1_'+p.id:p.id);
 const puppyName=p=>LEGACY_V6_PUPPIES.find(legacy=>legacy.id===p.id)?.name||p.name;
 const displayName=p=>p.id===g.puppyStyle?g.puppyName:puppyName(p);
 const retry=id=>setRetries(current=>({...current,[id]:(current[id]||0)+1}));
 const sorts=[['unlocked','Unlocked First'],['name','Name A–Z'],['name-desc','Name Z–A'],['newest','Newest Added'],['number','Oldest Added'],['category','Category']];
 const sections=legacyRosterSections(registry.items);
 useEffect(()=>setEditing(g.puppyName),[g.puppyName]);

 const selected=available.find(p=>p.id===g.puppyStyle)||available[0];
 const details=available.find(p=>p.id===detailsId);
 const detailsOwned=details?g.unlockedPuppies.includes(details.id):false;
 const detailsCurrent=details?.id===g.puppyStyle;
 const selectedOwned=g.unlockedPuppies.includes(selected.id);
 const selectedFavorite=g.favoritePuppies.includes(selected.id);
 const matches=available.filter(p=>{
  const owned=g.unlockedPuppies.includes(p.id);
  return (status==='all'||(status==='unlocked'&&owned)||(status==='locked'&&!owned))&&
   (!favoritesOnly||g.favoritePuppies.includes(p.id))&&
   (category==='all'||p.groupId===category)&&
   (puppyName(p).toLowerCase().includes(search.toLowerCase().trim())||nativeId(p).toLowerCase().includes(search.toLowerCase().trim())||p.id.includes(search.toLowerCase().trim()));
 }).sort((a,b)=>sort==='name'?puppyName(a).localeCompare(puppyName(b)):sort==='name-desc'?puppyName(b).localeCompare(puppyName(a)):sort==='newest'?b.number-a.number:sort==='number'?a.number-b.number:sort==='category'?a.groupOrder-b.groupOrder||puppyName(a).localeCompare(puppyName(b)):Number(g.unlockedPuppies.includes(b.id))-Number(g.unlockedPuppies.includes(a.id)));
 const reset=()=>{setSearch('');setStatus('all');setCategory('all');setFavoritesOnly(false);setSort('unlocked');};
 if(view==='collection')return <View style={styles.gamePage}>
  <PageHeader withLogo title="Puppy Collection" subtitle="V1 classics and V2 puppies in one collection."/>
  <Txt style={styles.itemName}>Unlocked {unlockedLegacyCount(g.unlockedPuppies)} / {LEGACY_V6_PUPPIES.length}</Txt>
  {sections.map(section=><View key={section.id} style={styles.rosterCollectionGroup}>
   <Txt style={styles.sectionTitle}>{section.title}</Txt>
   {section.items.map(p=>{
    const owned=g.unlockedPuppies.includes(p.id),selected=g.puppyStyle===p.id;
    return <Pressable key={p.id} disabled={!owned}
     accessibilityRole="button" accessibilityState={{disabled:!owned,selected}}
     accessibilityLabel={p.name+(owned?', unlocked':', locked')}
     onPress={()=>onUse?onUse(p.id):act({type:'switch',id:p.id,now:Date.now()})}
     style={[styles.rosterCollectionRow,selected&&styles.rosterSelected]}>
     <Portrait puppy={p} size={86} unlocked={owned} lazy
      accessory={selected?g.accessory:'None'}/>
     <View style={{flex:1,gap:5}}>
      <Txt style={styles.itemName}>{p.name}</Txt>
      <Txt muted style={styles.small}>{p.description}</Txt>
      {p.redeemOnly?<Txt muted style={styles.tiny}>{section.id.toUpperCase()} Puppy Code {owned?'unlocked':'unlock'}</Txt>:null}
     </View>
     <Txt style={styles.itemName}>{selected?'✓':owned?'Choose':'🔒'}</Txt>
    </Pressable>;
   })}
  </View>)}
  <Button secondary onPress={()=>setRenameOpen(!renameOpen)}>Rename {g.puppyName}</Button>
  {renameOpen?<View style={styles.rosterRename}>
   <TextInput value={editing} onChangeText={setEditing} maxLength={18}
    accessibilityLabel="Rename puppy" placeholder="Puppy name" placeholderTextColor={C.muted} style={styles.input}/>
   <Pressable accessibilityRole="button" accessibilityLabel="Generate random puppy name" onPress={()=>{try{setEditing(suggestPuppyName(editing));}catch{setEditing('Buddy');}}}
    style={[styles.rosterOutline,{alignSelf:'flex-start'}]}><Txt style={styles.rosterActionText}>🎲 Generate puppy name</Txt></Pressable>
   <View style={styles.actions}>
    <Button onPress={()=>{act({type:'rename',name:editing});setRenameOpen(false);}}>Save</Button>
    <Button secondary onPress={()=>setRenameOpen(false)}>Cancel</Button>
   </View>
  </View>:null}
  <Txt style={styles.sectionTitle}>Accessories</Txt>
  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap:7}}>
   {['None','Bandana','Bow','Crown'].map(name=><Pressable key={name}
    accessibilityRole="button"
    accessibilityState={{disabled:!g.ownedAccessories.includes(name),selected:g.accessory===name}}
    disabled={!g.ownedAccessories.includes(name)}
    onPress={()=>act({type:'equip',name})}
    style={[styles.filterChip,{flex:0,minWidth:90},g.accessory===name&&styles.filterChipSelected]}>
    <Txt style={[styles.filterText,g.accessory===name&&styles.filterTextSelected]}>
     {name}{g.ownedAccessories.includes(name)?'':' 🔒'}
    </Txt>
   </Pressable>)}
  </ScrollView>
  <Button compact secondary onPress={()=>setView('browser')}>Browse online V3+ and advanced filters</Button>
  <Txt muted style={styles.small}>Puppy collection; protected Puppy Code and Discord entitlements require verified account services.</Txt>
 </View>;
 return <View style={styles.restoredRoster}>
  <PuppyDetailsDialog visible={Boolean(details)} puppy={details} onClose={()=>setDetailsId(null)}
   portrait={details?<Portrait key={details.id+':details:'+(retries[details.id]||0)} puppy={details} size={112} unlocked={detailsOwned} accessory={detailsCurrent?g.accessory:'None'}/>:null}
   originalName={details?puppyName(details):''} displayName={details?displayName(details):''}
   assetId={details?nativeId(details):''} unlocked={detailsOwned} current={detailsCurrent}
   favorite={Boolean(details&&g.favoritePuppies.includes(details.id))}
   onToggleFavorite={()=>{if(detailsOwned)act({type:'favorite',id:details.id});}}
   onRetryArtwork={()=>{if(details)retry(details.id);}}
   onUse={id=>onUse?onUse(id):act({type:'switch',id,now:Date.now()})}
   onRename={name=>act({type:'rename',name})}/>
  <View style={styles.restoredRosterHeader}><View style={{flex:1,minWidth:0}}><Txt style={styles.pageTitle}>Puppy Roster</Txt><Txt muted numberOfLines={1} style={styles.pageSubtitle}>Browse every streamed puppy in the live roster.</Txt></View><Pressable accessibilityRole="button" accessibilityLabel="Open Puppy Exchange" onPress={onOpenExchange} style={styles.rosterOutline}><Txt style={styles.rosterActionText}>🎁 Exchange</Txt></Pressable></View>
  <View style={styles.restoredRosterFilters}>{[['all','All'],['unlocked','Unlocked'],['locked','Locked']].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:status===id}} onPress={()=>setStatus(id)} style={[styles.rosterStatus,status===id&&styles.rosterStatusSelected]}><Txt style={[styles.rosterControlText,status===id&&styles.rosterSelectedText]}>{label}</Txt></Pressable>)}</View>
  <TextInput accessibilityLabel="Search puppies or ID" placeholder="🔍  Search puppies or ID" placeholderTextColor={C.muted} style={styles.rosterSearch} maxLength={64} value={search} onChangeText={setSearch}/>
  <View style={styles.restoredRosterFilters}>
   <Pressable accessibilityRole="button" accessibilityLabel="Sort puppies" accessibilityState={{expanded:sortOpen}} onPress={()=>setSortOpen(!sortOpen)} style={[styles.rosterOutline,{flex:1,alignItems:'flex-start'}]}><Txt style={styles.rosterControlText}>Sort: {sorts.find(([id])=>id===sort)?.[1]}</Txt></Pressable>
   <Pressable accessibilityRole="button" accessibilityLabel="Show favorites only" accessibilityState={{selected:favoritesOnly}} onPress={()=>setFavoritesOnly(!favoritesOnly)} style={[styles.rosterOutline,favoritesOnly&&styles.rosterStatusSelected]}><Txt style={[styles.rosterControlText,favoritesOnly&&styles.rosterSelectedText]}>♡ Favorites</Txt></Pressable>
  </View>
  {sortOpen?<View style={styles.rosterOptions}>{sorts.map(([id,label])=><Pressable accessibilityRole="radio" accessibilityState={{checked:sort===id}} key={id} onPress={()=>{setSort(id);setSortOpen(false);}} style={styles.rosterOption}><Txt>{label}{sort===id?' ✓':''}</Txt></Pressable>)}</View>:null}
  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap:5}}>{[['all','All Categories'],...registry.categories.map(group=>[group.id,group.title])].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:category===id}} onPress={()=>setCategory(id)} style={[styles.rosterCategory,category===id&&styles.rosterStatusSelected]}><Txt numberOfLines={1} style={[styles.rosterControlText,category===id&&styles.rosterSelectedText]}>{label}</Txt></Pressable>)}</ScrollView>
  <Txt muted style={styles.small}>{matches.length} shown · {available.filter(p=>g.unlockedPuppies.includes(p.id)).length} unlocked</Txt>
  <View testID="current-puppy-hero" style={styles.restoredSelectedPanel}>
   <View style={styles.restoredSelectedRow}>
    <Portrait key={selected.id+':'+(retries[selected.id]||0)} puppy={selected} size={112} unlocked={selectedOwned} accessory={selected.id===g.puppyStyle?g.accessory:'None'}/>
    <View style={{flex:1,gap:2,minWidth:0}}><Txt numberOfLines={1} style={styles.rosterSelectedName}>{displayName(selected)}</Txt><Txt numberOfLines={1} style={styles.rosterMeta}>{nativeId(selected)} · {selected.groupTitle}</Txt>
     {selected.id===g.puppyStyle?<Txt style={styles.rosterCurrent}>Current Puppy</Txt>:selectedOwned?<Pressable accessibilityRole="button" accessibilityLabel={'Use '+puppyName(selected)} onPress={()=>onUse?onUse(selected.id):act({type:'switch',id:selected.id,now:Date.now()})}><Txt style={styles.rosterCurrent}>Use Puppy</Txt></Pressable>:<Txt style={styles.rosterMeta}>Locked</Txt>}
    </View>
    <Pressable accessibilityRole="button" accessibilityLabel={(selectedFavorite?'Remove ':'Add ')+displayName(selected)+(selectedFavorite?' from favorites':' to favorites')} disabled={!selectedOwned} onPress={()=>act({type:'favorite',id:selected.id})} style={styles.rosterHeart}><Txt style={[styles.rosterHeartText,selectedFavorite&&{color:'#FF5C9D'}]}>{selectedFavorite?'♥':'♡'}</Txt></Pressable>
   </View>
   {selected.id===g.puppyStyle?<View style={styles.restoredRosterFilters}>
    <Pressable accessibilityRole="button" accessibilityLabel="Rename current puppy" onPress={()=>setRenameOpen(true)} style={[styles.rosterOutline,{minHeight:30,paddingVertical:3}]}><Txt style={styles.rosterActionText}>Rename</Txt></Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel="Choose accessory" accessibilityState={{expanded:accessoriesOpen}} onPress={()=>setAccessoriesOpen(!accessoriesOpen)} style={[styles.rosterOutline,{minHeight:30,paddingVertical:3,flex:1}]}><Txt style={styles.rosterActionText}>Accessory: {g.accessory}</Txt></Pressable>
   </View>:null}
   {accessoriesOpen&&selected.id===g.puppyStyle?<View style={styles.rosterOptions}>{['None','Bandana','Bow','Crown'].map(name=><Pressable accessibilityRole="button" accessibilityLabel={'Equip '+name} accessibilityState={{disabled:!g.ownedAccessories.includes(name),selected:g.accessory===name}} disabled={!g.ownedAccessories.includes(name)} key={name} onPress={()=>{act({type:'equip',name});setAccessoriesOpen(false);}} style={styles.rosterOption}><Txt>{name}{g.ownedAccessories.includes(name)?g.accessory===name?' ✓':'':' 🔒'}</Txt></Pressable>)}</View>:null}
   {renameOpen&&selected.id===g.puppyStyle?<View style={styles.rosterRename}><TextInput value={editing} onChangeText={setEditing} maxLength={18} accessibilityLabel="Rename puppy" placeholder="Puppy name" placeholderTextColor={C.muted} style={styles.input}/><View style={styles.actions}><Pressable accessibilityRole="button" accessibilityLabel="Save puppy name" disabled={!editing.trim()} onPress={()=>{act({type:'rename',name:editing});setRenameOpen(false);}} style={styles.rosterOutline}><Txt style={styles.rosterActionText}>Save</Txt></Pressable><Pressable accessibilityRole="button" onPress={()=>{setEditing(g.puppyName);setRenameOpen(false);}} style={styles.rosterOutline}><Txt style={styles.rosterActionText}>Cancel</Txt></Pressable></View></View>:null}
  </View>
  {matches.length?<View testID="puppy-roster-grid" style={styles.restoredRosterGrid}>{matches.map(p=>{
   const owned=g.unlockedPuppies.includes(p.id),fav=g.favoritePuppies.includes(p.id);
   return <View key={p.id} style={[styles.restoredRosterTile,inspected===p.id&&{borderColor:C.accent}]}>
    <Pressable accessibilityRole="button" accessibilityLabel={'Inspect '+puppyName(p)} onPress={()=>{setInspected(p.id);setDetailsId(p.id);}} style={{width:'100%',alignItems:'center',gap:6}}>
    <Portrait key={p.id+':'+(retries[p.id]||0)} puppy={p} size={rosterViewport<400?104:116} unlocked={owned} lazy/>
    <Txt numberOfLines={1} style={styles.rosterTileName}>{displayName(p)}</Txt><Txt numberOfLines={1} style={styles.rosterMeta}>{nativeId(p)}</Txt><View style={styles.rosterGroupBadge}><Txt numberOfLines={1} style={styles.rosterMeta}>{p.groupTitle}</Txt></View><Txt style={styles.rosterMeta}>{p.id===g.puppyStyle?'Current':owned?'Unlocked':'🔒 Locked'}</Txt>
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel={(fav?'Remove ':'Add ')+displayName(p)+(fav?' from favorites':' to favorites')} disabled={!owned} onPress={e=>{e.stopPropagation?.();act({type:'favorite',id:p.id});}} style={[styles.rosterHeart,{position:'absolute',right:0,top:0}]}><Txt style={[styles.rosterHeartText,fav&&{color:'#FF5C9D'}]}>{fav?'♥':'♡'}</Txt></Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel={'Retry artwork for '+puppyName(p)} onPress={e=>{e.stopPropagation?.();retry(p.id);}} style={styles.rosterRetry}><Txt style={styles.rosterMeta}>↻</Txt></Pressable>
   </View>;
  })}</View>:<Card title="No puppies match these filters."><Button secondary onPress={reset}>Reset filters</Button></Card>}
  <Pressable accessibilityRole="button" onPress={()=>setView('collection')} style={styles.rosterOutline}><Txt style={styles.rosterActionText}>Puppy Collection</Txt></Pressable>
 </View>;
}

 return {PrestigeScreen,RosterScreen};
}
