import React,{useMemo,useState} from 'react';
import {Image,Pressable,ScrollView,Text,TextInput,View} from 'react-native';
import {colors as C} from '../theme.js';
import {PUPPY_IMAGES} from '../assets/puppies.js';
import {buildInventoryView,filterInventoryPuppies,INVENTORY_CATEGORIES} from './inventoryModel.js';

const format=value=>Number(value||0).toLocaleString('en-US');
const icons={Bandana:'🧣',Bow:'🎀',Crown:'👑'};
const rarityIcons={COMMON:'⚪',UNCOMMON:'🟢',RARE:'🔵',EPIC:'🟣',LEGENDARY:'🟡'};
const labelStyle={color:C.text,fontSize:14,fontWeight:'700'};
const secondaryStyle={color:C.muted,fontSize:12};

function Item({icon,title,description,right,image}){
 return <View style={{padding:12,borderRadius:15,borderWidth:1,borderColor:C.line,backgroundColor:C.surface,
   flexDirection:'row',alignItems:'center',gap:12}}>
  {image?<Image source={image} accessibilityLabel={title} resizeMode="contain" style={{width:57,height:57}}/>:
   <Text style={{fontSize:28,width:37,textAlign:'center'}}>{icon||'🐾'}</Text>}
  <View style={{flex:1,gap:3}}>
   <Text numberOfLines={2} style={labelStyle}>{title}</Text>
   {description?<Text style={secondaryStyle}>{description}</Text>:null}
  </View>
  {right?<Text style={{color:C.accentInk,fontWeight:'800',fontSize:12,textAlign:'right',maxWidth:100}}>{right}</Text>:null}
 </View>;
}
function Empty({children}){return <Text style={{color:C.muted,textAlign:'center',padding:22}}>{children}</Text>;}
export default function InventoryScreen({game,onRefresh,onBack,cloudState,updatedAt}){
 const [selected,setSelected]=useState('puppies');
 const [search,setSearch]=useState('');
 const [refreshing,setRefreshing]=useState(false);
 const inventory=useMemo(()=>buildInventoryView(game),[game]);
 const puppies=filterInventoryPuppies(inventory.puppies,search);
 const refresh=async()=>{
  if(refreshing||!onRefresh)return;
  setRefreshing(true);
  try{await onRefresh();}finally{setRefreshing(false);}
 };
 const updated=updatedAt&&!Number.isNaN(Date.parse(updatedAt))?new Date(updatedAt).toLocaleString():null;
 return <View accessibilityLabel="Puppy Clicker Inventory" style={{gap:15,paddingBottom:24}}>
  <View style={{gap:5}}>
   <Text accessibilityRole="header" style={{color:C.text,fontWeight:'900',fontSize:25}}>🎒 Inventory</Text>
   <Text style={secondaryStyle}>Your owned puppies, items, upgrades and balances from your current game save.</Text>
  </View>
  <View style={{flexDirection:'row',gap:9,flexWrap:'wrap'}}>
   {onBack?<Pressable accessibilityRole="button" onPress={onBack} style={{borderRadius:12,borderWidth:1,borderColor:C.line,
    backgroundColor:C.surface,padding:11}}><Text style={labelStyle}>← Back</Text></Pressable>:null}
   {onRefresh?<Pressable accessibilityRole="button" disabled={refreshing} onPress={()=>void refresh()}
    style={{borderRadius:12,borderWidth:1,borderColor:C.accent,padding:11,opacity:refreshing?.5:1}}>
    <Text style={{color:C.accentInk,fontWeight:'800'}}>{refreshing?'Refreshing…':'↻ Refresh cloud save'}</Text>
   </Pressable>:null}
  </View>
  <View style={{flexDirection:'row',gap:8,flexWrap:'wrap'}}>
   {INVENTORY_CATEGORIES.slice(0,4).map(category=><View key={category.id}
    style={{width:'48%',backgroundColor:C.surface,borderWidth:1,borderColor:C.line,padding:12,borderRadius:14,gap:4}}>
    <Text style={secondaryStyle}>{category.icon} {category.label}</Text>
    <Text style={{color:C.text,fontSize:20,fontWeight:'900'}}>{format(inventory.counts[category.id])}</Text>
   </View>)}
  </View>
  <ScrollView horizontal showsHorizontalScrollIndicator={false}
   contentContainerStyle={{flexDirection:'row',gap:8,paddingVertical:3}}>
   {INVENTORY_CATEGORIES.map(category=><Pressable key={category.id} accessibilityRole="tab"
    accessibilityLabel={category.label+' inventory'} accessibilityState={{selected:selected===category.id}}
    onPress={()=>setSelected(category.id)} style={{borderWidth:1,borderColor:selected===category.id?C.accent:C.line,
    backgroundColor:selected===category.id?C.card:C.surface,borderRadius:18,paddingHorizontal:14,paddingVertical:9}}>
    <Text style={{color:selected===category.id?C.accentInk:C.text,fontWeight:'800'}}>{category.icon} {category.label} · {format(inventory.counts[category.id])}</Text>
   </Pressable>)}
  </ScrollView>
  {selected==='puppies'?<View style={{gap:10}}>
   <Text style={labelStyle}>🐶 Puppy collection</Text>
   <TextInput accessibilityLabel="Search owned puppies" value={search} onChangeText={setSearch}
    placeholder="Search your puppies…" placeholderTextColor={C.muted} style={{backgroundColor:C.surface,
    borderColor:C.line,borderWidth:1,borderRadius:12,paddingHorizontal:12,paddingVertical:10,color:C.text}}/>
   {puppies.length?puppies.map(pup=><Item key={pup.id} title={pup.name}
    description={pup.known?pup.baseName+' · '+pup.id:'Owned puppy · '+pup.id}
    right={[pup.current?'Current':null,pup.favorite?'♥ Favorite':null].filter(Boolean).join(' · ')}
    icon="🐾" image={PUPPY_IMAGES[pup.id]||null}/>):<Empty>No owned puppies match your search.</Empty>}
  </View>:null}
  {selected==='accessories'?<View style={{gap:10}}>
   <Text style={labelStyle}>🎀 Accessories</Text>
   {inventory.accessories.length?inventory.accessories.map(item=><Item key={item.name}
    icon={icons[item.name]||'🎁'} title={item.name} description="Owned permanently"
    right={item.equipped?'Equipped':null}/>):<Empty>No purchased accessories yet.</Empty>}
  </View>:null}
  {selected==='tickets'?<View style={{gap:10}}>
   <Text style={labelStyle}>🎟️ Upgrade tickets</Text>
   {inventory.tickets.map(item=><Item key={item.rarity} icon={rarityIcons[item.rarity]||'🎟️'}
    title={item.rarity.charAt(0)+item.rarity.slice(1).toLowerCase()+' ticket'}
    description="Spend in the Shop" right={'×'+format(item.quantity)}/>)}
  </View>:null}
  {selected==='upgrades'?<View style={{gap:10}}>
   <Text style={labelStyle}>⬆️ Purchased upgrades</Text>
   {inventory.upgrades.length?inventory.upgrades.map(item=><Item key={item.id} icon={item.icon}
    title={item.name} description={item.description} right={'Lv. '+format(item.level)}/>)
    :<Empty>No upgrades purchased yet.</Empty>}
  </View>:null}
  {selected==='wallet'?<View style={{gap:10}}>
   <Text style={labelStyle}>🍪 Wallet</Text>
   {inventory.wallet.map(item=><Item key={item.key} icon={item.icon} title={item.label}
    description="Available balance" right={format(item.amount)}/>)}
  </View>:null}
  <View style={{gap:4,paddingTop:5}}>
   <Text style={secondaryStyle}>🔒 Read-only inventory. Purchases and rewards must use signed PupEye gameplay actions.</Text>
   {cloudState?<Text style={secondaryStyle}>Cloud status: {cloudState.replaceAll('_',' ')}</Text>:null}
   {updated?<Text style={secondaryStyle}>Last server save: {updated}</Text>:null}
  </View>
 </View>;
}
