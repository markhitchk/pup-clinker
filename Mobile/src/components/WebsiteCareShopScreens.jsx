// GENERATED, DO NOT EDIT. Source: markhitchk/Puppy-Clicker/src/native/App.jsx
// The website owns these React Native-compatible Care and Shop screen bodies.
// Native PupEye/gameplay callbacks and presentation dependencies are injected by the shell.
import React,{useState} from 'react';
import {Alert,Pressable,View} from 'react-native';

export function createWebsiteCareShopScreens({ROSTER,careTapBonus,mood,wellness,canPerformCare,Txt,styles,C,PageHeader,Portrait,upgradesForShopTab,Wallet,Button,RARITIES,accessoryPrices,accessoryOffer,v6Format,TICKET_RARITY_PRESENTATION,ticketShopOffer,MAX_UPGRADES_PER_ITEM,upgradeOffer,Card}){
function CareScreen({g,act,viewportHeight}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const tiny=viewportHeight<590,compact=viewportHeight<700;
 const portraitSize=tiny?66:compact?78:92;
 const bonus=careTapBonus(g);
 const note=g.energy<=10?'😴 Too tired to earn Treats on Play. Rest above 10% energy.':bonus===2?'✨ Thriving care bonus: +2 Treats on every normal puppy tap.':bonus===1?'✨ Healthy care bonus: +1 Treat on every normal puppy tap.':'Keep wellness at 70%+ to unlock a Treat bonus on Play.';
 const meter=(label,icon,value)=><View key={label} style={styles.compactCareMeter}>
  <Txt style={styles.compactCareLabel}>{icon} {label}</Txt>
  <View accessibilityRole="progressbar" accessibilityLabel={label} accessibilityValue={{min:0,max:100,now:value}} style={styles.compactCareTrack}><View style={[styles.compactCareFill,{width:Math.max(0,Math.min(100,value))+'%'}]}/></View>
  <Txt style={styles.compactCareValue}>{value}%</Txt>
 </View>;
 const action=(kind,icon,title,detail,label)=><Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{disabled:!canPerformCare(g,kind)}} disabled={!canPerformCare(g,kind)} onPress={()=>act({type:'care',kind,now:Date.now()})} style={[styles.restoredCareAction,!canPerformCare(g,kind)&&{opacity:.45}]}>
  <Txt style={{fontSize:22}}>{icon}</Txt><Txt style={styles.restoredCareActionTitle}>{title}</Txt><Txt muted style={styles.restoredCareActionDetail}>{detail}</Txt>
 </Pressable>;
 return <View style={[styles.restoredCare,{gap:tiny?5:8}]}>
  <PageHeader title="Pup Care" subtitle={compact?'Care is separate for each puppy.':'Care for your active puppy. Every puppy keeps its own needs and bond.'}/>
  <View style={[styles.restoredCareIdentity,{paddingVertical:tiny?7:9}]}>
   <Portrait puppy={pup} size={portraitSize} accessory={g.accessory}/>
   <View style={{flex:1,gap:4}}><Txt style={{fontSize:16,fontWeight:'900'}}>{g.puppyName}</Txt><Txt muted style={{fontSize:12}}>{mood(g)} · Wellness {wellness(g)}% · Bond {g.bond}%</Txt></View>
  </View>
  <View style={[styles.restoredCarePanel,{paddingVertical:tiny?6:8}]}>
   {[['Happiness','💖',g.happiness],['Fullness','🍖',g.fullness],['Energy','⚡',g.energy],['Cleanliness','🫧',g.cleanliness],['Bond','🤝',g.bond]].map(([label,icon,value])=>meter(label,icon,value))}
   <View style={styles.restoredCareDivider}/><Txt muted style={{fontSize:12,fontWeight:'700',color:g.energy<=10?C.danger:C.muted}}>{note}</Txt>
  </View>
  <Txt style={{fontSize:14,fontWeight:'900'}}>Care actions</Txt>
  <View style={styles.restoredCareRow}>
   {action('feed','🍖','Feed','-20 🍪','Feed, costs 20 Treats')}
   {action('play','🎾','Play','-1 🦴 · -12 ⚡','Play, costs 1 Bone and 12 energy')}
   {action('wash','🫧','Clean','-10 🍪','Clean, costs 10 Treats')}
  </View>
  <View style={styles.restoredCareRow}>
   {action('rest','🌙','Rest','Free · +30 ⚡','Rest, free, restores 30 energy')}
   {action('cuddle','🤗','Cuddle','Free · +Happy / Bond','Cuddle, free, improves happiness and bond')}
  </View>
 </View>;
}

function ShopScreen({g,act,onOpen}){
 const [shopTab,setShopTab]=useState('upgrades');
 const upgrades=upgradesForShopTab(shopTab);
 const compact=g.compactNumbers!==false;
 return <View style={styles.gamePage}>
  <PageHeader withLogo title="Puppy Shop" subtitle="Active upgrades, rare tickets, Pup Coins and Puppy Codes."/>
  <Wallet game={g}/>
  <View style={styles.shopTopActions}>
   <Button secondary onPress={()=>Alert.alert('🎫 Redeem Puppy Code','Kotlin has local Puppy Codes, but code redemption requires the signed live entitlement service before it can be enabled. No protected rewards will be simulated.')}>🎫 Redeem Code</Button>
   <Button secondary onPress={()=>onOpen('roster')}>🐶 Pups</Button>
  </View>
  <View style={styles.filterRow}>
   {[['upgrades','🍪 Upgrades'],['tickets','🎟️ Tickets'],['coins','🪙 Pup Coins']].map(([id,label])=>
    <Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:shopTab===id}}
     style={[styles.filterChip,shopTab===id&&styles.filterChipSelected]} onPress={()=>setShopTab(id)}>
     <Txt style={[styles.filterText,shopTab===id&&styles.filterTextSelected]} numberOfLines={1}>{label}</Txt>
    </Pressable>)}
  </View>
  {shopTab==='coins'?<>
   <View style={styles.pupCoinBalance}>
    <Txt style={styles.itemName}>🪙 Pup Coin Shop</Txt>
    <Txt style={styles.pageTitle}>{v6Format(g.pupCoins,compact)} Pup Coins</Txt>
    <Txt muted style={styles.small}>Pup Coins are the normal Shop currency. They are not Casino Chips.</Txt>
   </View>
   <Txt style={styles.sectionTitle}>Accessories</Txt>
   {['None',...Object.keys(accessoryPrices)].map(name=>{
    const offer=accessoryOffer(g,name);
    return <View key={name} style={styles.shopAccessoryRow}>
     <View style={styles.shopLabel}>
      <Txt style={styles.itemName}>{name}</Txt>
      <Txt muted style={styles.small}>{name==='None'?'Always available':offer.owned?'Owned permanently':offer.price+' Pup Coins'}</Txt>
     </View>
     <Button compact disabled={!offer.canBuy}
      onPress={()=>act({type:offer.owned?'equip':'accessory',name})}>{offer.equipped?'Equipped':offer.owned?'Equip':'Buy'}</Button>
    </View>;
   })}
   <Txt style={styles.sectionTitle}>Upgrade Tickets</Txt>
   <Txt muted style={styles.small}>Each purchase raises that rarity’s next price by 25% of its base price, capped at 6×.</Txt>
   {RARITIES.map(rarity=>{
    const offer=ticketShopOffer(g,rarity),info=TICKET_RARITY_PRESENTATION[rarity];
    return <View key={rarity} style={styles.shopAccessoryRow}>
     <View style={styles.shopLabel}>
      <Txt style={styles.itemName}>{info.emoji} {info.name}</Txt>
      <Txt muted style={styles.small}>Owned {offer.owned} · Next {v6Format(offer.price,compact)} 🪙</Txt>
     </View>
     <Button compact disabled={!offer.canBuy} onPress={()=>act({type:'ticket',rarity})}>Buy 1</Button>
    </View>;
   })}
  </>:<>
   {shopTab==='upgrades'&&(g.prestigeSkills.SMART_SHOPPER||0)>0?
    <Txt style={styles.shopDiscountLabel}>⭐ Smart Shopper discount active</Txt>:null}
   {shopTab==='tickets'?<Card title="🎟️ Ticket inventory">
     <Txt muted style={styles.small}>Rare drop: about 1 Upgrade Ticket per 500 legitimate taps.</Txt>
     {RARITIES.map(rarity=><View key={rarity} style={styles.pair}>
      <Txt style={styles.itemName}>{TICKET_RARITY_PRESENTATION[rarity].emoji} {TICKET_RARITY_PRESENTATION[rarity].name}</Txt>
      <Txt style={styles.value}>×{g.ticketInventory[rarity]||0}</Txt>
     </View>)}
    </Card>:null}
   {upgrades.map(u=>{
    const o=upgradeOffer(g,u.id),rarity=u.rarity?TICKET_RARITY_PRESENTATION[u.rarity]:null;
    const text=o.skill>0?u.rarity?u.description+' · skill bonus +'+o.skill:u.description+' · Prestige value +'+o.actualAmount:u.description;
    return <View key={u.id} style={[styles.shopUpgradeCard,rarity&&styles.shopTicketUpgradeCard]}>
     <View style={styles.shopUpgradeHeading}>
      <Txt style={{fontSize:31}}>{u.emoji}</Txt>
      <View style={styles.shopLabel}>
       <Txt style={styles.itemName}>{u.name}</Txt>
       <Txt muted style={styles.small}>{text}</Txt>
       <Txt muted style={styles.small}>Owned {o.owned} · Effective {Math.min(MAX_UPGRADES_PER_ITEM,o.owned)}/{MAX_UPGRADES_PER_ITEM}</Txt>
      </View>
      {rarity?<Txt style={styles.shopRarity}>{rarity.emoji} {rarity.name}</Txt>:
       <Button compact disabled={!o.canBuy}
        onPress={()=>act({type:'purchase',upgradeId:u.id})} >{o.owned>=MAX_UPGRADES_PER_ITEM?'MAX':v6Format(o.treats,compact)+' 🍪 + '+o.bones+' 🦴'}</Button>}
     </View>
     {rarity?<View style={styles.shopUpgradeFooter}>
      <View style={styles.shopLabel}>
       <Txt style={styles.itemName}>Need {o.tickets} · you have {g.ticketInventory[u.rarity]||0}</Txt>
       <Txt muted style={styles.small}>{o.owned>=MAX_UPGRADES_PER_ITEM?'Maximum effective upgrade reached':'+ '+v6Format(o.treats,compact)+' 🍪 + '+o.bones+' 🦴'}</Txt>
      </View>
      <Button compact disabled={!o.canBuy} onPress={()=>act({type:'purchase',upgradeId:u.id})}>{o.owned>=MAX_UPGRADES_PER_ITEM?'MAX':'Upgrade'}</Button>
     </View>:null}
    </View>;
   })}
  </>}
 </View>;
}

 return {CareScreen,ShopScreen};
}
