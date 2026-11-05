// Native Play/Account Inventory based on the existing website InventoryScreen.jsx.
// The screen only reads the verified game projection; all purchases/equips happen
// through the signed native Shop and Roster flows, never local inventory writes.
import React,{useMemo,useState} from 'react';
import {Image,Pressable,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {colors as C} from '../theme.js';
import {PUPPY_IMAGES} from '../assets/puppies.js';
import {
 INVENTORY_CATEGORIES,INVENTORY_CURRENCIES,
 inventoryNumber,buildInventoryModel
} from './inventoryModel.js';

function Action({text,onPress,primary=false,disabled=false}){
 return <Pressable accessibilityRole="button" accessibilityLabel={text}
  accessibilityState={{disabled}} disabled={disabled} onPress={onPress}
  style={({pressed})=>[styles.action,{borderColor:primary?C.accent:C.line,
   backgroundColor:primary?C.accent:C.surface},pressed&&{opacity:.75},
   disabled&&{opacity:.5}]}>
  <Text style={{color:primary?C.onAccent:C.accent,fontWeight:'800',fontSize:13}}>{text}</Text>
 </Pressable>;
}
function Stat({icon,label,value}){
 return <View style={[styles.stat,{backgroundColor:C.surface,borderColor:C.line}]}>
  <Text style={[styles.statLabel,{color:C.muted}]}>{icon} {label}</Text>
  <Text style={[styles.statValue,{color:C.text}]}>{inventoryNumber(value)}</Text>
 </View>;
}
function Item({icon,title,subtitle,amount,tag}){
 return <View style={[styles.item,{backgroundColor:C.card,borderColor:C.line}]}>
  <Text style={styles.itemIcon}>{icon}</Text>
  <View style={{flex:1,minWidth:0,gap:3}}>
   <Text style={[styles.itemName,{color:C.text}]} numberOfLines={2}>{title}</Text>
   <Text style={[styles.itemSub,{color:C.muted}]} numberOfLines={2}>{subtitle}</Text>
  </View>
  {tag?<Text style={[styles.itemTag,{color:C.accent,borderColor:C.line}]}>{tag}</Text>:null}
  {amount!=null?<Text style={[styles.itemAmount,{color:C.text}]}>{amount}</Text>:null}
 </View>;
}
function PuppyTile({puppy,width,onOpen}){
 const image=PUPPY_IMAGES[puppy.id];
 return <Pressable accessibilityRole="button"
  accessibilityLabel={'View '+puppy.name+' in Roster'}
  onPress={()=>onOpen('roster')}
  style={({pressed})=>[styles.puppyCard,{width,backgroundColor:C.card,
   borderColor:puppy.current?C.accent:C.line},pressed&&{opacity:.75}]}>
  <View style={styles.puppyArt}>
   {image?<Image source={image} accessibilityLabel={puppy.name+' artwork'}
    resizeMode="contain" style={styles.puppyImage}/>:
    <Text style={{fontSize:39}}>🐾</Text>}
  </View>
  <Text style={[styles.puppyName,{color:C.text}]} numberOfLines={1}>{puppy.name}</Text>
  <Text style={[styles.puppySub,{color:C.muted}]} numberOfLines={1}>
   {puppy.originalName} · {puppy.group}
  </Text>
  <View style={styles.puppyFlags}>
   {puppy.current?<Text style={[styles.flag,{color:C.accent,borderColor:C.accent}]}>
    Current
   </Text>:null}
   {puppy.favorite?<Text style={[styles.flag,{color:C.pink||'#FF5C9D',
    borderColor:C.pink||'#FF5C9D'}]}>♥ Favorite</Text>:null}
  </View>
 </Pressable>;
}
export default function WebsiteInventoryScreen({
 game,onOpen=()=>{},onBack,onRefresh,updatedAt,refreshing=false
}){
 const {width:windowWidth}=useWindowDimensions();
 const [category,setCategory]=useState('puppies');
 const [search,setSearch]=useState('');
 const [panelWidth,setPanelWidth]=useState(0);
 const model=useMemo(()=>buildInventoryModel(game,search),[game,search]);
 const width=panelWidth||Math.min(windowWidth-28,900);
 const columns=width>=1000?5:width>=720?4:width>=480?3:2;
 const gap=10;
 const cardWidth=Math.max(112,(width-24-(columns-1)*gap)/columns);
 if(!model)return <View style={[styles.frame,{backgroundColor:C.surface,borderColor:C.line}]}>
  <Text accessibilityRole="alert" style={{color:C.muted}}>Inventory requires a verified Pup Account game save.</Text>
 </View>;
 const date=updatedAt&&Number.isFinite(new Date(updatedAt).getTime())
  ?new Date(updatedAt).toLocaleString():null;
 return <View testID="android-inventory-screen" style={styles.page}>
  <View style={styles.heading}>
   <View style={{flex:1,gap:6,minWidth:0}}>
    <Text style={[styles.eyebrow,{color:C.accent}]}>🎒 PUP ACCOUNT COLLECTION</Text>
    <Text accessibilityRole="header" style={[styles.title,{color:C.text}]}>Inventory</Text>
    <Text style={[styles.subtitle,{color:C.muted}]}>
     Your actual owned puppies, items and balances from protected game progress.
    </Text>
   </View>
   <View style={styles.actions}>
    {onBack?<Action text="← Back to game" onPress={onBack}/>:null}
    <Action text="👤 Pup Account" onPress={()=>onOpen('account-details')}/>
    {onRefresh?<Action text={refreshing?'Refreshing…':'↻ Refresh'} onPress={onRefresh}
     disabled={refreshing} primary/>:null}
   </View>
  </View>
  <View style={styles.stats}>
   <Stat icon="🐶" label="Puppies" value={model.counts.puppies}/>
   <Stat icon="🎀" label="Accessories" value={model.counts.accessories}/>
   <Stat icon="🎟️" label="Tickets" value={model.counts.tickets}/>
   <Stat icon="⬆️" label="Upgrade types" value={model.counts.upgrades}/>
  </View>
  <View accessibilityRole="tablist" style={styles.tabs}>
   {INVENTORY_CATEGORIES.map(({id,icon,label})=>
    <Pressable key={id} accessibilityRole="tab" accessibilityLabel={label}
     accessibilityState={{selected:category===id}}
     onPress={()=>setCategory(id)} style={({pressed})=>[
      styles.tab,{borderColor:category===id?C.accent:C.line,
       backgroundColor:category===id?C.accentSoft:C.surface},pressed&&{opacity:.75}]}>
     <Text style={{color:category===id?C.accent:C.text,fontWeight:'800',fontSize:12}}>
      {icon} {label} · {inventoryNumber(model.counts[id])}
     </Text>
    </Pressable>)}
  </View>
  <View testID="android-inventory-content"
   onLayout={event=>{const w=event.nativeEvent.layout.width;if(w>0&&w!==panelWidth)setPanelWidth(w);}}
   style={[styles.frame,{backgroundColor:C.surface,borderColor:C.line}]}>
   {category==='puppies'?<>
    <Text style={[styles.sectionTitle,{color:C.text}]}>🐶 Puppy collection</Text>
    <Text style={{color:C.muted,fontSize:12}}>
     Currently playing: {game.puppyName||'Your puppy'}. Switch puppies in the Roster.
    </Text>
    <TextInput accessibilityLabel="Search puppies" placeholder="Search your puppies…"
     placeholderTextColor={C.muted} value={search} onChangeText={setSearch}
     style={[styles.search,{color:C.text,backgroundColor:C.card,borderColor:C.line}]}/>
    {model.puppies.length?
     <View testID="android-inventory-puppy-grid" style={[styles.puppyGrid,{gap}]}>
      {model.puppies.map(p=><PuppyTile key={p.id} puppy={p} width={cardWidth} onOpen={onOpen}/>)}
     </View>:<Text style={[styles.empty,{color:C.muted}]}>
      No owned puppies match your search.
     </Text>}
   </>:null}
   {category==='accessories'?<>
    <Text style={[styles.sectionTitle,{color:C.text}]}>🎀 Accessories</Text>
    <Text style={{color:C.muted,fontSize:12}}>Purchased accessories are permanent. Equip them in the Shop.</Text>
    {model.accessories.length?model.accessories.map(a=><Item key={a.id} icon={a.icon}
     title={a.name} subtitle="Owned permanently" tag={a.equipped?'Equipped':null}/>):
     <Text style={[styles.empty,{color:C.muted}]}>No purchased accessories yet. Visit the Shop to collect some.</Text>}
   </>:null}
   {category==='tickets'?<>
    <Text style={[styles.sectionTitle,{color:C.text}]}>🎟️ Upgrade tickets</Text>
    <Text style={{color:C.muted,fontSize:12}}>Spend these on eligible Shop upgrades. Quantities reflect your verified game save.</Text>
    {model.tickets.map(t=><Item key={t.rarity} icon={t.icon}
     title={t.rarity.charAt(0)+t.rarity.slice(1).toLowerCase()+' ticket'}
     subtitle={t.rarity.toLowerCase()+' rarity'} amount={'×'+inventoryNumber(t.count)}/>)}
   </>:null}
   {category==='upgrades'?<>
    <Text style={[styles.sectionTitle,{color:C.text}]}>⬆️ Purchased upgrades</Text>
    <Text style={{color:C.muted,fontSize:12}}>Owned upgrade levels, not available Shop offers.</Text>
    {model.upgrades.length?model.upgrades.map(u=><Item key={u.id} icon={u.emoji}
     title={u.name} subtitle={u.description} amount={'Lv. '+inventoryNumber(u.count)}/>):
     <Text style={[styles.empty,{color:C.muted}]}>You haven't purchased any upgrades yet.</Text>}
   </>:null}
   {category==='wallet'?<>
    <Text style={[styles.sectionTitle,{color:C.text}]}>🍪 Wallet</Text>
    <Text style={{color:C.muted,fontSize:12}}>Your current game currency balances.</Text>
    {model.wallet.map(w=><Item key={w.key} icon={w.icon} title={w.label}
     subtitle="Available balance" amount={inventoryNumber(w.amount)}/>)}
   </>:null}
  </View>
  <View style={styles.footer}>
   <Text style={{fontSize:11,color:C.muted}}>🔒 Read-only inventory · Your protected Pup Account save remains the source of truth.</Text>
   {date?<Text style={{fontSize:11,color:C.muted}}>Last synced: {date}</Text>:null}
  </View>
 </View>;
}
const styles={
 page:{width:'100%',gap:14,paddingBottom:22},
 heading:{flexDirection:'row',flexWrap:'wrap',alignItems:'flex-start',gap:12},
 eyebrow:{fontSize:11,fontWeight:'900',letterSpacing:1.1},
 title:{fontSize:30,fontWeight:'800'},
 subtitle:{fontSize:13,lineHeight:19,maxWidth:570},
 actions:{flexDirection:'row',flexWrap:'wrap',gap:8},
 action:{borderWidth:1,paddingHorizontal:13,paddingVertical:10,borderRadius:999,minHeight:39},
 stats:{flexDirection:'row',flexWrap:'wrap',gap:8},
 stat:{flexGrow:1,flexBasis:135,padding:12,borderWidth:1,borderRadius:18,gap:7},
 statLabel:{fontSize:12,fontWeight:'700'},statValue:{fontSize:23,fontWeight:'900'},
 tabs:{flexDirection:'row',flexWrap:'wrap',gap:7},
 tab:{borderRadius:999,paddingHorizontal:12,paddingVertical:10,borderWidth:1,minHeight:42},
 frame:{width:'100%',borderWidth:1,borderRadius:24,padding:12,gap:12,minWidth:0},
 sectionTitle:{fontSize:19,fontWeight:'800'},
 search:{minHeight:46,borderRadius:16,paddingHorizontal:14,borderWidth:1,fontSize:14},
 puppyGrid:{flexDirection:'row',flexWrap:'wrap',alignItems:'stretch',width:'100%'},
 puppyCard:{borderRadius:18,borderWidth:1,padding:9,gap:5,alignItems:'center',minWidth:0},
 puppyArt:{width:'100%',height:110,alignItems:'center',justifyContent:'center'},
 puppyImage:{width:'100%',height:110},
 puppyName:{fontSize:13,fontWeight:'800',textAlign:'center'},
 puppySub:{fontSize:10,textAlign:'center'},
 puppyFlags:{flexDirection:'row',gap:3,flexWrap:'wrap',justifyContent:'center'},
 flag:{fontSize:10,fontWeight:'700',borderRadius:999,borderWidth:1,overflow:'hidden',paddingHorizontal:5,paddingVertical:2},
 item:{flexDirection:'row',alignItems:'center',borderRadius:16,borderWidth:1,padding:12,gap:10,minHeight:64},
 itemIcon:{fontSize:25},
 itemName:{fontSize:15,fontWeight:'800'},
 itemSub:{fontSize:11},
 itemTag:{borderWidth:1,borderRadius:999,overflow:'hidden',paddingHorizontal:7,paddingVertical:4,fontSize:11,fontWeight:'800'},
 itemAmount:{fontSize:14,fontWeight:'900',textAlign:'right'},
 empty:{fontSize:13,paddingVertical:15},
 footer:{gap:5,paddingHorizontal:4}
};
