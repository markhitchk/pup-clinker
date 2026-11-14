import React,{useState} from 'react';
import {Image,Pressable,Text,TextInput,View} from 'react-native';
import {colors as C,themedStyles} from '../theme.js';
import {PUPPY_IMAGES} from '../assets/puppies.js';
import {inventoryCategories,inventorySnapshot} from '../graphics/inventoryModel.js';

const count=value=>Number(value||0).toLocaleString('en-US');
const symbols={Bandana:'🧣',Bow:'🎀',Crown:'👑',
 COMMON:'⚪',UNCOMMON:'🟢',RARE:'🔵',EPIC:'🟣',LEGENDARY:'🟡'};
function Label({children,muted=false,style}){return <Text style={[styles.text,muted&&styles.muted,style]}>{children}</Text>;}
function Item({icon,title,detail,trailing}){return <View style={styles.item}>
 <Label style={styles.itemIcon}>{icon}</Label>
 <View style={{flex:1,minWidth:0,gap:3}}><Label style={styles.itemTitle}>{title}</Label>
  {detail?<Label muted style={styles.small}>{detail}</Label>:null}</View>
 {trailing?<Label style={styles.itemValue}>{trailing}</Label>:null}
 </View>;
}
export default function InventoryScreen({game,onOpen,onRefresh,cloudState}){
 const [category,setCategory]=useState('puppies');
 const [search,setSearch]=useState('');
 const inventory=inventorySnapshot(game);
 const filtered=inventory.puppies.filter(pup=>
  (pup.name+' '+pup.originalName+' '+pup.id).toLowerCase().includes(search.trim().toLowerCase()));
 return <View testID="android-inventory-screen" style={styles.page}>
  <View style={styles.head}>
   <View style={{flex:1,minWidth:0}}><Label style={styles.heading}>🎒 Inventory</Label>
    <Label muted style={styles.small}>Your collected puppies, items and balances.</Label></View>
   {onRefresh?<Pressable accessibilityRole="button" accessibilityLabel="Refresh inventory"
    onPress={onRefresh} style={styles.action}><Label style={styles.actionText}>↻ Refresh</Label></Pressable>:null}
  </View>
  <Label muted style={styles.small}>🔒 Read-only · Showing your current {cloudState==='LIVE_SYNCED'?'synced Pup Account': 'game'} progress. Inventory never changes cloud balances.</Label>
  <View style={styles.totals}>
   {inventoryCategories.slice(0,4).map(([id,emoji,label])=>
    <View key={id} style={styles.total}><Label muted style={styles.small}>{emoji} {label}</Label>
     <Label style={styles.totalValue}>{count(inventory.counts[id])}</Label></View>)}
  </View>
  <View style={styles.categories}>
   {inventoryCategories.map(([id,emoji,label])=><Pressable key={id}
    accessibilityRole="tab" accessibilityLabel={label} accessibilityState={{selected:category===id}}
    onPress={()=>setCategory(id)} style={[styles.tab,category===id&&styles.tabSelected]}>
    <Label style={[styles.tabLabel,category===id&&{color:C.accentInk}]}>{emoji} {label}</Label>
   </Pressable>)}
  </View>
  {category==='puppies'?<View style={styles.panel}>
   <Label style={styles.panelHeading}>Puppy collection</Label>
   <TextInput accessibilityLabel="Search owned puppies" placeholder="Search owned puppies…"
    placeholderTextColor={C.muted} value={search} onChangeText={setSearch} style={styles.search}/>
   {filtered.length?filtered.map(pup=><View key={pup.id} style={styles.item}>
    {PUPPY_IMAGES[pup.id]?<Image source={PUPPY_IMAGES[pup.id]} resizeMode="contain"
      style={styles.puppyImage} accessibilityLabel={pup.originalName}/>:
     <Label style={styles.itemIcon}>🐶</Label>}
    <View style={{flex:1,minWidth:0,gap:4}}><Label style={styles.itemTitle}>{pup.name}</Label>
     <Label muted style={styles.small}>{pup.originalName} · {pup.id}</Label>
     {pup.current||pup.favorite?<Label style={styles.badge}>{pup.current?'● Current puppy':''}{pup.current&&pup.favorite?'  ·  ':''}{pup.favorite?'♥ Favorite':''}</Label>:null}
    </View>
   </View>):<Label muted>No owned puppies match this search.</Label>}
   <Pressable accessibilityRole="button" onPress={()=>onOpen?.('roster')} style={styles.action}>
    <Label style={styles.actionText}>Open Puppy Roster</Label></Pressable>
  </View>:null}
  {category==='accessories'?<View style={styles.panel}>
   <Label style={styles.panelHeading}>Accessories</Label>
   {inventory.accessories.length?inventory.accessories.map(item=><Item key={item.name}
    icon={symbols[item.name]||'🎀'} title={item.name} detail="Owned permanently"
    trailing={item.equipped?'Equipped':undefined}/>):
    <Label muted>No purchased accessories yet.</Label>}
   <Pressable accessibilityRole="button" onPress={()=>onOpen?.('shop')} style={styles.action}>
    <Label style={styles.actionText}>Visit Shop</Label></Pressable>
  </View>:null}
  {category==='tickets'?<View style={styles.panel}><Label style={styles.panelHeading}>Upgrade tickets</Label>
   {inventory.tickets.map(item=><Item key={item.rarity} icon={symbols[item.rarity]}
    title={item.rarity.charAt(0)+item.rarity.slice(1).toLowerCase()+' ticket'}
    detail="Saved inventory" trailing={'×'+count(item.quantity)}/>)}
  </View>:null}
  {category==='upgrades'?<View style={styles.panel}><Label style={styles.panelHeading}>Purchased upgrades</Label>
   {inventory.upgrades.length?inventory.upgrades.map(item=><Item key={item.id}
    icon={item.emoji} title={item.name} detail={item.description} trailing={'Lv. '+count(item.level)}/>):
    <Label muted>You haven't purchased any upgrades yet.</Label>}
  </View>:null}
  {category==='wallet'?<View style={styles.panel}><Label style={styles.panelHeading}>Wallet</Label>
   {inventory.wallet.map(item=><Item key={item.key}
    icon={item.emoji} title={item.label} detail="Available balance" trailing={count(item.value)}/>)}
  </View>:null}
 </View>;
}
const styles=themedStyles(()=>({
 page:{width:'100%',gap:12,paddingBottom:12},
 text:{color:C.text,fontSize:14},
 muted:{color:C.muted},small:{fontSize:12,lineHeight:17},
 head:{flexDirection:'row',gap:10,alignItems:'center'},
 heading:{fontSize:23,fontWeight:'900'},
 action:{alignSelf:'flex-start',paddingHorizontal:12,paddingVertical:9,borderRadius:12,
  backgroundColor:C.accentSoft,borderWidth:1,borderColor:C.accent},
 actionText:{fontWeight:'800',color:C.accentInk,fontSize:12},
 totals:{flexDirection:'row',flexWrap:'wrap',gap:7},
 total:{flexBasis:'47%',flexGrow:1,backgroundColor:C.card,padding:11,
  borderRadius:14,borderWidth:1,borderColor:C.line,gap:4},
 totalValue:{fontSize:21,fontWeight:'900'},
 categories:{flexDirection:'row',flexWrap:'wrap',gap:6},
 tab:{paddingHorizontal:11,paddingVertical:9,borderRadius:16,backgroundColor:C.surface,
  borderWidth:1,borderColor:C.line},
 tabSelected:{borderColor:C.accent,backgroundColor:C.accentSoft},
 tabLabel:{fontSize:12,fontWeight:'800'},
 panel:{gap:9,backgroundColor:C.surface,borderRadius:18,borderWidth:1,
  borderColor:C.line,padding:11},
 panelHeading:{fontSize:17,fontWeight:'900'},
 item:{minHeight:54,flexDirection:'row',alignItems:'center',gap:10,
  paddingVertical:8,borderBottomWidth:1,borderBottomColor:C.line},
 itemIcon:{fontSize:25,width:37,textAlign:'center'},
 itemTitle:{fontWeight:'800',fontSize:14},
 itemValue:{fontWeight:'900',color:C.accentInk,fontSize:13},
 puppyImage:{width:54,height:54},
 badge:{fontSize:11,color:C.accentInk,fontWeight:'800'},
 search:{borderWidth:1,borderColor:C.line,borderRadius:12,paddingHorizontal:10,
  height:44,color:C.text,backgroundColor:C.bg}
}));
