import React,{useEffect,useRef,useState} from 'react';
import {Alert,Animated,Easing,Image,ImageBackground,Modal,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,Vibration,View,useWindowDimensions} from 'react-native';
import {colors as C} from './src/theme.js';
import {useGame} from './src/state/useGame.js';
import {useRoster} from './src/state/useRoster.js';
import {ROSTER,UPGRADES,RARITIES,PRESTIGE_SKILLS,format,level,wellness,mood,cost,boneCost,ticketCost,ticketShopCost,accessoryPrices,prestigePointsAvailable,skillCost,handValue} from './src/engine/game.js';
import {SCRATCHERS} from './src/engine/casinoV7.js';
import {ACHIEVEMENTS,achievementStatuses,guestLevel} from './src/engine/progression.js';
import {gachaPool,GACHA_TREAT_COST} from './src/engine/gacha.js';
import {cardLabel,handTotal,canHit,canStand,canDouble,canSplit} from './src/engine/blackjack.js';
import {blackjackTable,blackjackFan,blackjackSharedCardWidth} from './src/engine/blackjackLayout.js';
import {ONBOARDING_STEPS,validBirthday} from './src/engine/onboarding.js';
import {goalsForDay,progressForGoal,localEpochDay,monthKey} from './src/engine/monthlyRewards.js';
import ScratchStage from './src/components/ScratchStage.jsx';
import {V6_PLAY_LAYOUT,v6PlaySizing,v6Format,v6CooldownSeconds,v6PlayTapLabel} from './src/engine/playV6.js';
import {V6_CARE_LAYOUT,canPerformCare,dailyGiftPreview,parkPreview} from './src/engine/careV6.js';
import {TICKET_RARITY_PRESENTATION,PRESTIGE_SKILL_PRESENTATION,upgradesForShopTab,upgradeOffer,ticketShopOffer,accessoryOffer,prestigeOffer,canBuySkill} from './src/engine/shopV6.js';
import {LEGACY_V6_PUPPIES,legacyRosterSections,unlockedLegacyCount} from './src/engine/rosterV6.js';
import {GACHA_ART} from './src/assets/gacha.js';
import {GACHA_MACHINE,gachaMachineFrame} from './src/engine/gachaV6.js';

import {PUPPY_IMAGES} from './src/assets/puppies.js';
const TABS=[['play','🐾','Play'],['care','💖','Care'],['shop','🛍️','Shop'],['prestige','⭐','Prestige'],['settings','⚙️','Settings']];
const skillNames={TAP_TRAINING:'Tap Training',AUTO_TRAINING:'Bonus Training',SMART_SHOPPER:'Smart Shopper',TICKET_SENSE:'Ticket Sense'};
const die=()=>1+Math.floor(Math.random()*13);
const roll=()=>Math.floor(Math.random()*100);
const number=()=>Math.floor(Math.random()*37);
const shuffledBlackjackDeck=()=>{
 const deck=Array.from({length:52},(_,i)=>i);
 for(let i=deck.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[deck[i],deck[j]]=[deck[j],deck[i]];}
 return deck;
};
function Txt({children,muted=false,style={},numberOfLines}){return <Text numberOfLines={numberOfLines} style={[styles.text,muted&&styles.muted,style]}>{children}</Text>;}
function Card({title,children,subtitle}){return <View style={styles.card}><View style={styles.cardTitle}><Txt style={styles.heading}>{title}</Txt>{subtitle?<Txt muted style={styles.tiny}>{subtitle}</Txt>:null}</View>{children}</View>;}
function Button({children,onPress,secondary=false,disabled=false,compact=false,danger=false}){
 return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={({pressed})=>[styles.button,secondary&&styles.buttonSecondary,disabled&&styles.buttonDisabled,compact&&styles.buttonCompact,danger&&styles.buttonDanger,pressed&&!disabled&&{opacity:0.7}]}>
  <Txt style={[styles.buttonText,secondary&&{color:C.text},disabled&&{color:C.muted}]}>{children}</Txt>
 </Pressable>;
}
function Pair({left,right}){return <View style={styles.pair}><Txt muted style={styles.small}>{left}</Txt><Txt style={styles.value}>{right}</Txt></View>;}
function Meter({label,value,icon}){
 const safe=Math.min(100,Math.max(0,Number(value)||0));
 return <View style={styles.careMeter}>
  <View style={styles.pair}><Txt style={styles.itemName}>{icon} {label}</Txt><Txt style={styles.itemName}>{safe}%</Txt></View>
  <View accessibilityRole="progressbar" accessibilityLabel={label+' need'}
   accessibilityValue={{min:0,max:100,now:safe}} style={styles.careMeterTrack}>
   <View style={[styles.careMeterFill,{width:safe+'%'}]}/>
  </View>
 </View>;
}
function Portrait({puppy,size=228,unlocked=true,accessory='None'}){
 const [broken,setBroken]=useState(false);
 useEffect(()=>setBroken(false),[puppy.id,puppy.image]);
 const v2=puppy.id.startsWith('v2_');
 const ratio=v2?0.96:0.90;
 const asset=PUPPY_IMAGES[puppy.id]||(puppy.image?{uri:puppy.image}:null);
 return <View style={[styles.portrait,{width:size,height:size,borderRadius:size/2,overflow:'hidden'}]}>
  {broken||!asset?<Txt style={{fontSize:size*0.36}}>🐶</Txt>:
   <Image accessibilityLabel={puppy.name} source={asset} resizeMode="contain"
    onError={()=>setBroken(true)} style={{width:size*ratio,height:size*ratio}}/>}
  {puppy.emoji?<View pointerEvents="none" style={[styles.portraitBadge,{top:size*0.05,right:size*0.05}]}>
   <Txt style={{fontSize:Math.max(9,size*0.13)}}>{puppy.emoji}</Txt>
  </View>:null}
  {!unlocked?<View pointerEvents="none" style={styles.lockedPortraitOverlay}>
   <Txt style={{fontSize:size*0.22}}>🔒</Txt>
  </View>:accessory!=='None'?<View pointerEvents="none" style={[styles.portraitAccessory,accessory==='Bandana'?{bottom:0}:{top:0}]}>
   <Txt style={{fontSize:size*(accessory==='Bandana'?0.18:0.16)}}>{accessory==='Bandana'?'🧣':accessory==='Bow'?'🎀':'👑'}</Txt>
  </View>:null}
 </View>;
}
// Visual structure follows V6Play/V6Wallet/V6NeedPill in the Kotlin launcher.
function PageHeader({title,subtitle,action,withLogo=false}){
 return <View style={styles.pageHeader}>
  {withLogo?<Image source={require('./assets/logos/puppy_clicker.png')}
    accessibilityLabel="Puppy Clicker logo" resizeMode="contain"
    style={{width:V6_PLAY_LAYOUT.headerLogo,height:V6_PLAY_LAYOUT.headerLogo}}/>:null}
  <View style={{flex:1}}>
   <Txt style={styles.pageTitle}>{title}</Txt><Txt muted style={styles.pageSubtitle}>{subtitle}</Txt>
  </View>{action||null}</View>;
}
function Wallet({game}){
 return <View style={styles.wallet}>
  {[['🍪','Treats',game.treats],['🦴','Bones',game.bones],['🪙','Pup Coins',game.pupCoins],['🐾','Casino Chips',game.casinoChips]].map(([icon,label,value])=>
   <View style={styles.walletCell} key={label}><Txt style={styles.walletEmoji}>{icon}</Txt><Txt numberOfLines={1} style={styles.walletNumber}>{v6Format(value,game.compactNumbers!==false)}</Txt><Txt numberOfLines={1} style={styles.walletLabel}>{label}</Txt></View>)}
 </View>;
}
function NeedPill({icon,value}){
 const safe=Math.min(100,Math.max(0,Number(value)||0));
 return <View style={[styles.needPill,safe<=10&&styles.needPillLow]}>
  <Txt style={styles.needPillText}>{icon} {safe}%</Txt>
  <View accessibilityRole="progressbar" accessibilityLabel={icon+' puppy care'}
   accessibilityValue={{min:0,max:100,now:safe}} style={styles.needMeterTrack}>
   <View style={[styles.needMeterFill,{width:safe+'%'}]}/>
  </View>
 </View>;
}
function QuickAction({emoji,title,subtitle,onPress,disabled=false}){
 return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{disabled}} disabled={disabled} onPress={onPress} style={({pressed})=>[styles.quickAction,disabled&&styles.buttonDisabled,pressed&&{opacity:0.75}]}>
  <Txt style={styles.quickActionIcon}>{emoji}</Txt><Txt numberOfLines={1} style={styles.quickActionTitle}>{title}</Txt><Txt numberOfLines={1} style={styles.quickActionDescription}>{subtitle}</Txt>
 </Pressable>;
}
function DailyGoalCard({g,goal,act,clock}){
 const date=new Date(clock),today=localEpochDay(date),claimed=g.dailyDay===today&&g.claimedDailyTasks.includes(goal.id);
 const progress=Math.max(0,progressForGoal(g,goal,date)),complete=progress>=goal.target;
 return <View style={styles.dailyGoalCard}>
  <Txt style={{fontSize:28}}>{goal.emoji}</Txt>
  <View style={{flex:1,gap:6}}>
   <View style={styles.pair}><Txt style={styles.itemName}>{goal.title}</Txt><Txt style={styles.value}>{Math.min(progress,goal.target)}/{goal.target}</Txt></View>
   <Txt muted style={styles.small}>{goal.description}</Txt>
   <View style={styles.track}><View style={[styles.fill,{backgroundColor:C.accent,width:Math.min(100,100*progress/goal.target)+'%'}]}/></View>
   <View style={styles.pair}><Txt muted style={styles.small}>Reward: {format(goal.rewardTreats)} 🍪 + 1 🦴 + 1 🪙</Txt>
    {claimed?<Txt style={styles.claimedText}>✓ Claimed</Txt>:<Button compact disabled={!complete} onPress={()=>act({type:'claimDailyGoal',goalId:goal.id,now:clock})}>Claim</Button>}
   </View>
  </View>
 </View>;
}
function PlayScreen({g,act,clock}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const {width}=useWindowDimensions();
 const size=v6PlaySizing(width);
 const cooldown=v6CooldownSeconds(g,clock);
 const tapScale=useRef(new Animated.Value(1)).current;
 const idleY=useRef(new Animated.Value(0)).current;
 const ticketOpacity=useRef(new Animated.Value(0)).current;
 const [ticketVisible,setTicketVisible]=useState(false);

 // Kotlin V6Play has an idle portrait bob: -3dp to +5dp, 1700ms each leg.
 useEffect(()=>{
  if(g.animationsEnabled===false){idleY.setValue(0);return;}
  const loop=Animated.loop(Animated.sequence([
   Animated.timing(idleY,{toValue:V6_PLAY_LAYOUT.idleDown,duration:V6_PLAY_LAYOUT.idleHalfCycleMs,easing:Easing.inOut(Easing.ease),useNativeDriver:true}),
   Animated.timing(idleY,{toValue:V6_PLAY_LAYOUT.idleUp,duration:V6_PLAY_LAYOUT.idleHalfCycleMs,easing:Easing.inOut(Easing.ease),useNativeDriver:true})
  ]));
  loop.start();
  return ()=>{loop.stop();idleY.setValue(0);};
 },[g.animationsEnabled,idleY]);

 useEffect(()=>{
  if(!g.ticketDropSerial||!g.lastTicketDrop)return;
  setTicketVisible(true);
  ticketOpacity.stopAnimation();
  Animated.timing(ticketOpacity,{toValue:1,duration:180,useNativeDriver:true}).start();
  if(g.hapticsEnabled!==false)Vibration.vibrate(40);
  const hide=setTimeout(()=>{
   Animated.timing(ticketOpacity,{toValue:0,duration:220,useNativeDriver:true})
    .start(({finished})=>{if(finished)setTicketVisible(false);});
  },V6_PLAY_LAYOUT.ticketVisibleMs);
  return ()=>clearTimeout(hide);
 },[g.ticketDropSerial,g.lastTicketDrop,ticketOpacity]);

 const doTap=()=>{
  const now=Date.now();
  if(v6CooldownSeconds(g,now)>0||g.energy<=10)return;
  act({type:'tap',now,randoms:[Math.random(),Math.random()]});
  if(g.hapticsEnabled!==false)Vibration.vibrate(12);
  if(g.animationsEnabled===false)return;
  tapScale.stopAnimation();
  tapScale.setValue(V6_PLAY_LAYOUT.tapPressedScale);
  Animated.spring(tapScale,{toValue:1,friction:4,tension:95,useNativeDriver:true}).start();
 };
 return <View style={styles.gamePage}>
  <PageHeader withLogo title="Puppy Clicker" subtitle={g.puppyName+' · '+mood(g)}/>
  <Wallet game={g}/>
  <View style={styles.needsRow}>
   <NeedPill icon="💖" value={g.happiness}/>
   <NeedPill icon="🍖" value={g.fullness}/>
   <NeedPill icon="⚡" value={g.energy}/>
   <NeedPill icon="🫧" value={g.cleanliness}/>
  </View>
  <View style={[styles.tapStage,{height:size.stageHeight}]}>
   <Pressable accessibilityRole="button"
    accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'}
    accessibilityState={{disabled:cooldown>0}}
    disabled={cooldown>0} onPress={doTap}
    style={{alignItems:'center',justifyContent:'center'}}>
    <Animated.View style={[styles.puppyCircle,{
     width:size.circle,height:size.circle,borderRadius:size.circle/2,
     transform:[{translateY:idleY},{scale:tapScale}]
    }]}>
     <Portrait puppy={pup} size={size.portrait}/>
    </Animated.View>
   </Pressable>
   <View pointerEvents="none" style={styles.tapLabel}>
    <Txt style={[styles.tapLabelText,cooldown>0&&{color:C.danger}]}>{v6PlayTapLabel(g,clock)}</Txt>
   </View>
  </View>
  {ticketVisible?<Animated.View pointerEvents="none" style={[styles.ticketOverlay,{opacity:ticketOpacity}]}>
   <Txt style={styles.ticketOverlayTitle}>🎟️ {g.lastTicketDrop} UPGRADE TICKET</Txt>
   <Txt style={styles.ticketOverlayDescription}>Rare reward found! Check your Shop inventory.</Txt>
  </Animated.View>:null}
 </View>;
}
// Part 2: direct port of the launched Kotlin V6CareAndDaily / V6CarePanel /
// V6DailyPanel. Guest-only data; notification scheduling is not enabled yet.
function CareScreen({g,act,clock,schedule,onOpen}){
 const [page,setPage]=useState('care');
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const gift=dailyGiftPreview(g,clock);
 const park=parkPreview(g,clock);
 const careAction=(kind,text)=>(
  <Button disabled={!canPerformCare(g,kind)}
   onPress={()=>act({type:'care',kind,now:Date.now()})}>{text}</Button>
 );
 return <View style={styles.gamePage}>
  <PageHeader withLogo title="Pup Care" subtitle="Care, bond, daily rewards and adventures."/>
  <View style={styles.filterRow}>
   {[['care','💖 Care'],['daily','📅 Daily']].map(([id,label])=>
    <Pressable accessibilityRole="tab" accessibilityState={{selected:page===id}}
     key={id} onPress={()=>setPage(id)}
     style={[styles.filterChip,page===id&&styles.filterChipSelected]}>
     <Txt style={[styles.filterText,page===id&&styles.filterTextSelected]}>{label}</Txt>
    </Pressable>)}
  </View>
  {page==='care'?<>
   <View style={styles.careIdentity}>
    <Portrait puppy={pup} size={V6_CARE_LAYOUT.portraitSize}/>
    <View style={{flex:1,gap:5}}>
     <Txt style={styles.pageTitle} numberOfLines={2}>{g.puppyName}</Txt>
     <Txt muted style={styles.small}>{mood(g)} · wellness {wellness(g)}%</Txt>
     <Txt style={styles.careBond}>🤝 Bond {g.bond}%</Txt>
    </View>
   </View>
   <View style={styles.careMeters}>
    <Meter label="Happiness" icon="💖" value={g.happiness}/>
    <Meter label="Fullness" icon="🍖" value={g.fullness}/>
    <Meter label="Energy" icon="⚡" value={g.energy}/>
    <Meter label="Cleanliness" icon="🫧" value={g.cleanliness}/>
    <Meter label="Bond" icon="🤝" value={g.bond}/>
   </View>
   <Txt style={styles.sectionTitle}>Care actions</Txt>
   <View style={styles.careActionRow}>
    {careAction('feed','🍖 Feed')}
    {careAction('play','🎾 Play')}
   </View>
   <View style={styles.careActionRow}>
    {careAction('rest','💤 Rest')}
    {careAction('wash','🫧 Groom')}
   </View>
   <Button secondary disabled={!canPerformCare(g,'cuddle')}
    onPress={()=>act({type:'care',kind:'cuddle',now:Date.now()})}>🤗 Cuddle · build bond</Button>
   <View style={styles.careRewardNote}>
    <Txt style={styles.itemName}>Active care rewards</Txt>
    <Txt muted style={styles.small}>Care actions improve your puppy’s needs and bond.</Txt>
    <Txt muted style={styles.small}>AFK Treats are claimable from the notification inbox in the original Kotlin app; that inbox has not been ported to this preview.</Txt>
   </View>
  </>:<>
   <View style={styles.careStreakBanner}>
    <Txt style={{fontSize:36}}>🔥</Txt>
    <View style={{flex:1,gap:4}}>
     <Txt style={styles.careStreakTitle}>{g.dailyStreak} day streak</Txt>
     <Txt muted style={styles.small}>Daily rewards grant Treats, Bones and Pup Coins — never automatic Upgrade Tickets.</Txt>
    </View>
   </View>
   <View style={styles.careDailyGift}>
    <Txt style={{fontSize:34}}>🎁</Txt>
    <View style={{flex:1,gap:4}}>
     <Txt style={styles.itemName}>Daily Puppy Gift</Txt>
     <Txt muted style={styles.small}>{v6Format(gift.rewardTreats,g.compactNumbers!==false)} Treats + 2 Bones + 3 Pup Coins</Txt>
    </View>
    <Button compact disabled={gift.claimed}
     onPress={()=>act({type:'daily',now:Date.now()})}>{gift.claimed?'Claimed':'Claim'}</Button>
   </View>
   <Txt style={styles.sectionTitle}>Today’s goals</Txt>
   {goalsForDay(schedule,new Date(clock)).map(goal=>
    <DailyGoalCard key={goal.id} g={g} act={act} goal={goal} clock={clock}/>)}
   <View style={styles.careParkCard}>
    <Txt style={{fontSize:32}}>🌳</Txt>
    <View style={{flex:1,gap:4}}>
     <Txt style={styles.itemName}>Dog Park Adventure</Txt>
     <Txt muted style={styles.small}>{park.detail}</Txt>
    </View>
    <Button compact disabled={park.disabled}
     onPress={()=>act({type:park.ready?'parkClaim':'parkStart',now:Date.now()})}>{park.label}</Button>
   </View>
   <View style={{marginTop:8}}>
    <Button compact secondary onPress={()=>onOpen('rewards')}>Preview · Rewards Hub</Button>
   </View>
  </>}
 </View>;
}
// Part 3: actual launched Kotlin V6Shop / V6Prestige, not a generic store.
function ShopScreen({g,act,onOpen}){
 const [shopTab,setShopTab]=useState('upgrades');
 const upgrades=upgradesForShopTab(shopTab);
 const compact=g.compactNumbers!==false;
 return <View style={styles.gamePage}>
  <PageHeader withLogo title="Puppy Shop" subtitle="Active upgrades, rare tickets, Pup Coins and Puppy Codes."/>
  <Wallet game={g}/>
  <View style={styles.shopTopActions}>
   <Button secondary onPress={()=>Alert.alert('🎫 Redeem Puppy Code','Kotlin has local Puppy Codes, but preview code redemption is disabled until original save, account and entitlement behavior can be verified. No protected rewards will be simulated.')}>🎫 Redeem Code</Button>
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
       <Txt muted style={styles.small}>Owned {o.owned}</Txt>
      </View>
      {rarity?<Txt style={styles.shopRarity}>{rarity.emoji} {rarity.name}</Txt>:
       <Button compact disabled={!o.canBuy}
        onPress={()=>act({type:'purchase',upgradeId:u.id})}>{v6Format(o.treats,compact)} 🍪 + {o.bones} 🦴</Button>}
     </View>
     {rarity?<View style={styles.shopUpgradeFooter}>
      <View style={styles.shopLabel}>
       <Txt style={styles.itemName}>Need {o.tickets} · you have {g.ticketInventory[u.rarity]||0}</Txt>
       <Txt muted style={styles.small}>+ {v6Format(o.treats,compact)} 🍪 + {o.bones} 🦴</Txt>
      </View>
      <Button compact disabled={!o.canBuy} onPress={()=>act({type:'purchase',upgradeId:u.id})}>Upgrade</Button>
     </View>:null}
    </View>;
   })}
  </>}
 </View>;
}
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
function RosterScreen({g,act,registry}){
 const [search,setSearch]=useState('');
 const [status,setStatus]=useState('all');
 const [favoritesOnly,setFavoritesOnly]=useState(false);
 const [category,setCategory]=useState('all');
 const available=registry.items;
 const [sort,setSort]=useState('unlocked');
 const [inspected,setInspected]=useState(g.puppyStyle);
 const [view,setView]=useState('collection');
 const [renameOpen,setRenameOpen]=useState(false);
 const [editing,setEditing]=useState(g.puppyName);
 const sections=legacyRosterSections(registry.items);
 useEffect(()=>setEditing(g.puppyName),[g.puppyName]);

 const selected=available.find(p=>p.id===inspected)||available.find(p=>p.id===g.puppyStyle)||available[0];
 const selectedOwned=g.unlockedPuppies.includes(selected.id);
 const selectedFavorite=g.favoritePuppies.includes(selected.id);
 const matches=available.filter(p=>{
  const owned=g.unlockedPuppies.includes(p.id);
  return (status==='all'||(status==='unlocked'&&owned)||(status==='locked'&&!owned))&&
   (!favoritesOnly||g.favoritePuppies.includes(p.id))&&
   (category==='all'||p.groupId===category)&&
   (p.name.toLowerCase().includes(search.toLowerCase().trim())||p.id.includes(search.toLowerCase().trim()));
 }).sort((a,b)=>sort==='name'?a.name.localeCompare(b.name):sort==='number'?a.number-b.number:Number(g.unlockedPuppies.includes(b.id))-Number(g.unlockedPuppies.includes(a.id)));
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
     onPress={()=>act({type:'switch',id:p.id,now:Date.now()})}
     style={[styles.rosterCollectionRow,selected&&styles.rosterSelected]}>
     <Portrait puppy={p} size={86} unlocked={owned}
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
  <Txt muted style={styles.small}>Guest preview collection; original Puppy Code and Discord entitlements are not connected.</Txt>
 </View>;
 return <View style={styles.gamePage}>
  <Button compact secondary onPress={()=>setView('collection')}>← Kotlin Puppy Collection</Button>
  <PageHeader title="Puppy Roster" subtitle="Browse the original Kotlin puppy roster and streamed artwork."/>
  <View style={styles.filterRow}>{[['all','All'],['unlocked','Unlocked'],['locked','Locked']].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:status===id}} onPress={()=>setStatus(id)} style={[styles.filterChip,status===id&&styles.filterChipSelected]}><Txt style={[styles.filterText,status===id&&styles.filterTextSelected]}>{label}</Txt></Pressable>)}</View>
  <TextInput accessibilityLabel="Search puppies or ID" placeholder="🔍 Search puppies or ID" placeholderTextColor={C.muted} style={styles.input} maxLength={64} value={search} onChangeText={setSearch}/>
  <View style={styles.actions}>
   <Button secondary compact onPress={()=>setSort(sort==='unlocked'?'name':sort==='name'?'number':'unlocked')}>Sort: {sort==='unlocked'?'Unlocked first':sort==='name'?'Name':'Number'}</Button>
   <Button compact secondary={!favoritesOnly} onPress={()=>setFavoritesOnly(!favoritesOnly)}>{favoritesOnly?'★':'☆'} Favorites</Button>
  </View>
  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap:6,paddingVertical:4}}>{[['all','All Categories'],...registry.categories.map(group=>[group.id,group.title])].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:category===id}} onPress={()=>setCategory(id)} style={[styles.filterChip,{flex:0,minWidth:108,paddingHorizontal:12},category===id&&styles.filterChipSelected]}><Txt numberOfLines={1} style={[styles.filterText,category===id&&styles.filterTextSelected]}>{label}</Txt></Pressable>)}</ScrollView>
  <Txt muted style={styles.small}>{matches.length} shown · {g.unlockedPuppies.length} unlocked · {registry.source==='live'?'Live roster':registry.source==='cached'?'Cached roster':'Bundled roster'}</Txt>
  <View style={styles.selectedPuppyPanel}>
   <Portrait puppy={selected} size={108}/>
   <View style={{flex:1,gap:7}}>
    <Txt style={styles.heading}>{selected.name}</Txt>
    <Txt muted style={styles.small}>{selected.id} · {selectedOwned?'Unlocked':'Locked'} · {selected.groupTitle}</Txt>
    <View style={styles.actions}>
     <Button compact disabled={!selectedOwned||selected.id===g.puppyStyle} onPress={()=>act({type:'switch',id:selected.id,now:Date.now()})}>Use</Button>
     <Button compact secondary onPress={()=>act({type:'favorite',id:selected.id})}>{selectedFavorite?'★':'☆'}</Button>
    </View>
   </View>
  </View>
  {matches.length?<View style={styles.roster}>{matches.map(p=>{
   const owned=g.unlockedPuppies.includes(p.id),fav=g.favoritePuppies.includes(p.id);
   return <Pressable accessibilityRole="button" accessibilityLabel={'Inspect '+p.name} key={p.id} onPress={()=>setInspected(p.id)} style={[styles.rosterPup,inspected===p.id&&styles.rosterSelected]}>
    <Portrait puppy={p} size={86}/>
    <Txt numberOfLines={1} style={styles.small}>{p.name}</Txt>
    <Txt muted style={styles.tiny}>{g.puppyStyle===p.id?'CURRENT':owned?'UNLOCKED':'LOCKED'} {fav?'★':''}</Txt>
   </Pressable>;
  })}</View>:<Card title="No puppies match these filters."><Button secondary onPress={reset}>Reset filters</Button></Card>}
 </View>;
}
// Part 4: source-matched mechanical Gacha stages 0 idle / 1 turn+drop /
// 2 ready to open / 3 opening / 4 reveal. Local guest-only settlement.
function GachaScreen({g,act,state,registry,onBack}){
 const [stage,setStage]=useState(0);
 const [showInfo,setShowInfo]=useState(false);
 const [message,setMessage]=useState('');
 const turn=useRef(new Animated.Value(0)).current;
 const drop=useRef(new Animated.Value(GACHA_MACHINE.dropOffset)).current;
 const opening=useRef(new Animated.Value(0)).current;
 const revealTimer=useRef(null);
 const result=state.gachaResult;
 const {width}=useWindowDimensions();
 const frame=gachaMachineFrame(Math.max(0,Math.min(width-44,560)));
 const selected=registry.items.find(p=>p.id===result?.puppyId)
  ||ROSTER.find(p=>p.id===result?.puppyId);
 const pool=gachaPool(ROSTER,g.unlockedPuppies);
 const missing=pool.filter(p=>!g.unlockedPuppies.includes(p.id));
 const purchasesAllowed=stage===0&&!result?.success&&pool.length>0;
 useEffect(()=>{
  if(!result?.success){
   setStage(0);return;
  }
  setMessage('');
  setStage(1);
  turn.setValue(0);drop.setValue(GACHA_MACHINE.dropOffset);opening.setValue(0);
  const motion=Animated.sequence([
   Animated.timing(turn,{toValue:1,duration:GACHA_MACHINE.knobTurnMs,useNativeDriver:true}),
   Animated.spring(drop,{toValue:0,friction:5,tension:90,useNativeDriver:true})
  ]);
  motion.start(({finished})=>{if(finished)setStage(2);});
  return ()=>{motion.stop();if(revealTimer.current)clearTimeout(revealTimer.current);};
 },[result,turn,drop,opening]);
 const openCapsule=()=>{
  if(stage!==2||!result?.success)return;
  setStage(3);
  opening.setValue(0);
  Animated.timing(opening,{toValue:1,duration:GACHA_MACHINE.capsuleOpenMs,
   easing:Easing.inOut(Easing.ease),useNativeDriver:true}).start(({finished})=>{
   if(finished)revealTimer.current=setTimeout(()=>setStage(4),GACHA_MACHINE.revealDelayMs);
  });
 };
 const reset=()=>{setStage(0);setMessage('');act({type:'dismissGacha'});};
 const pull=payment=>{
  if(!purchasesAllowed)return;
  const affordable=payment==='TREATS'?g.treats>=GACHA_TREAT_COST:(g.ticketInventory.COMMON||0)>=1;
  if(!affordable){setMessage(payment==='TREATS'?'You need 2500 Treats.':'You need 1 Common Ticket.');return;}
  setMessage('');
  act({type:'gacha',payment,roll:Math.floor(Math.random()*0x7fffffff)});
 };
 const turnDegrees=turn.interpolate({inputRange:[0,1],outputRange:['0deg','360deg']});
 const tiltOpacity=opening.interpolate({inputRange:[0,0.12,0.62,1],outputRange:[0,1,1,0]});
 const openOpacity=opening.interpolate({inputRange:[0,0.42,1],outputRange:[0,0,1]});
 return <View style={styles.gamePage}>
  <View style={styles.gachaHeader}>
   <Button compact secondary onPress={onBack}>← Back</Button>
   <View style={{flex:1}}>
    <Txt style={styles.heading}>Puppy Gacha</Txt>
    <Txt muted style={styles.small}>Turn · Drop · Open · Reveal!</Txt>
   </View>
   <Button compact secondary onPress={()=>setShowInfo(true)}>ⓘ Info</Button>
  </View>
  <View style={styles.gachaWallet}>
   <View style={styles.gachaWalletCell}>
    <Txt style={styles.gachaWalletLabel}>🍪 Treats</Txt>
    <Txt style={styles.gachaWalletValue}>{v6Format(g.treats,g.compactNumbers!==false)}</Txt>
   </View>
   <View style={styles.gachaWalletDivider}/>
   <View style={styles.gachaWalletCell}>
    <Txt style={styles.gachaWalletLabel}>🎟️ Common Tickets</Txt>
    <Txt style={styles.gachaWalletValue}>{g.ticketInventory.COMMON||0}</Txt>
   </View>
  </View>
  <View style={[styles.gachaMachine,{width:frame.width,height:frame.height}]}>
   <Image resizeMode="contain" accessibilityLabel="Original Puppy Gacha machine"
    source={GACHA_ART.machine} style={{position:'absolute',width:frame.width,height:frame.height}}/>
   <Animated.Image source={GACHA_ART.knob} resizeMode="contain"
    accessibilityLabel="Gacha turning knob"
    style={{position:'absolute',...frame.knob,transform:[{rotate:turnDegrees}]}}/>
   {(stage===1||stage===2)?<Animated.Image source={GACHA_ART.capsuleClosed}
    resizeMode="contain" accessibilityLabel="Closed puppy Gacha capsule"
    style={{position:'absolute',...frame.capsule,
     transform:[{translateY:drop}]}}/>:null}
   {stage===3||stage===4?<View style={{position:'absolute',...frame.open}}>
    <Animated.Image source={GACHA_ART.capsuleOpenTilt} resizeMode="contain"
     style={{width:'100%',height:'100%',opacity:tiltOpacity}}/>
    <Animated.Image source={GACHA_ART.capsuleOpen} resizeMode="contain"
     style={{position:'absolute',width:'100%',height:'100%',opacity:openOpacity}}/>
   </View>:null}
   {stage===2?<View style={styles.gachaOpenButton}>
    <Button onPress={openCapsule}>Open Capsule</Button>
   </View>:null}
  </View>
  <View style={styles.gachaPurchaseRow}>
   <Pressable accessibilityRole="button"
    disabled={!purchasesAllowed||g.treats<GACHA_TREAT_COST}
    onPress={()=>pull('TREATS')}
    style={[styles.gachaPurchaseButton,{backgroundColor:'#FFB51B',borderColor:'#D89500'},
     (!purchasesAllowed||g.treats<GACHA_TREAT_COST)&&styles.buttonDisabled]}>
    <Txt style={styles.gachaPurchaseTitle}>🍪 Pull with Treats</Txt>
    <Txt style={styles.gachaPurchaseSubtitle}>2,500 Treats</Txt>
   </Pressable>
   <Pressable accessibilityRole="button"
    disabled={!purchasesAllowed||(g.ticketInventory.COMMON||0)<1}
    onPress={()=>pull('COMMON_TICKET')}
    style={[styles.gachaPurchaseButton,{backgroundColor:'#7C3AED',borderColor:'#5B21B6'},
     (!purchasesAllowed||(g.ticketInventory.COMMON||0)<1)&&styles.buttonDisabled]}>
    <Txt style={styles.gachaPurchaseTitle}>🎟️ Use Common Ticket</Txt>
    <Txt style={styles.gachaPurchaseSubtitle}>1 Ticket</Txt>
   </Pressable>
  </View>
  {message?<Txt style={styles.gachaFailure}>{message}</Txt>:null}
  <Txt muted style={[styles.small,{textAlign:'center'}]}>
   {pool.length===0?'No puppies are currently eligible for Gacha.':
    missing.length===0?'Collection complete · no unowned Gacha puppies remain.':
    missing.length===1?'1 new puppy remains · new puppies are guaranteed first.':
    missing.length+' new puppies remain · new puppies are guaranteed first.'}
  </Txt>
  <Txt muted style={[styles.tiny,{textAlign:'center'}]}>
   Redeem-only and special locked puppies are excluded. Uses earned Treats or Common Upgrade Tickets only; no real money or cash-out.
  </Txt>
  <Txt muted style={[styles.tiny,{textAlign:'center'}]}>
   React guest preview: offline rewards are not official PupEye-verified entitlements.
  </Txt>
  <Modal visible={stage===4&&Boolean(result?.success)} transparent
   animationType="fade" onRequestClose={reset}>
   <View style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.gachaRevealHeading}>✨ CAPSULE OPENED! ✨</Txt>
     <Txt style={[styles.itemName,{color:C.accent}]}>{result?.isNewUnlock?'NEW PUPPY!':'PUPPY REVEALED!'}</Txt>
     <View style={styles.gachaRevealPortrait}>
      {selected?<Portrait puppy={selected} size={188} unlocked/>:null}
     </View>
     <Txt style={styles.heading}>{selected?.emoji||'🐶'} {result?.puppyName||'Puppy'}</Txt>
     <Txt muted>{result?.isNewUnlock?'Added to your Puppy Roster.':'Already in your Puppy Roster.'}</Txt>
     <Button onPress={()=>{act({type:'switch',id:result.puppyId,now:Date.now()});reset();}}>Use Puppy</Button>
     <Button secondary onPress={reset}>Another Capsule</Button>
     <Button secondary compact onPress={reset}>Close</Button>
    </View>
   </View>
  </Modal>
  <Modal visible={showInfo} transparent animationType="fade"
   onRequestClose={()=>setShowInfo(false)}>
   <View style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.heading}>ⓘ Gacha Info</Txt>
     <Txt muted>Turn the machine with Treats or a Common Upgrade Ticket, then open your capsule. New unrestricted puppies are awarded before duplicates.</Txt>
     <Txt muted>Special event, developer, Discord-role and Puppy Code puppies are excluded. This is a local guest preview, not a verified cloud account.</Txt>
     <Button onPress={()=>setShowInfo(false)}>Got it</Button>
    </View>
   </View>
  </Modal>
 </View>;
}
function RewardsScreen({g,act,clock,schedule,onOpen,onPrestige}){
 const date=new Date(clock),today=localEpochDay(date),goals=goalsForDay(schedule,date);
 const claimed=(goal)=>g.dailyDay===today&&g.claimedDailyTasks.includes(goal.id);
 const active=goals.filter(goal=>!claimed(goal)),completed=goals.filter(claimed);
 const giftClaimed=g.lastDailyClaimDay===today;
 const nextStreak=g.lastDailyClaimDay===today-1?Math.min(365,g.dailyStreak+1):1;
 const giftReward=150+nextStreak*25+level(g)*10;
 const statuses=achievementStatuses(g);
 return <View style={styles.gamePage}>
  <PageHeader title="Rewards" subtitle="Daily goals, adventures and permanent progression."/>
  <View style={styles.streakCard}>
   <Txt style={{fontSize:42}}>🔥</Txt>
   <View style={{flex:1,gap:8}}><Txt style={styles.heading}>{g.dailyStreak} day streak</Txt>
    <Txt muted style={styles.small}>Keep going! Consistent care unlocks bigger rewards.</Txt>
    <View style={{flexDirection:'row',gap:6}}>{Array.from({length:7},(_,index)=><View key={index} style={[styles.streakDot,index<g.dailyStreak&&styles.streakDotFilled]}><Txt style={styles.small}>{index<g.dailyStreak?'✓':''}</Txt></View>)}</View>
   </View>
  </View>
  {!giftClaimed?<View style={styles.dailyGiftCard}>
   <Txt style={{fontSize:32}}>🎁</Txt><View style={{flex:1,gap:5}}><Txt style={styles.itemName}>Daily Puppy Gift</Txt><Txt muted style={styles.small}>{format(giftReward)} Treats + 2 Bones + 3 Pup Coins</Txt></View>
   <Button compact onPress={()=>act({type:'daily',epochDay:today,now:clock})}>Claim</Button>
  </View>:null}
  <View style={styles.pair}><Txt style={styles.sectionTitle}>Today’s goals</Txt><Txt muted style={styles.small}>{monthKey(date)} · {schedule?.source==='github'?'Live':schedule?.source==='cache'?'Cached':'Fallback'}</Txt></View>
  {active.length?active.map(goal=><DailyGoalCard g={g} goal={goal} act={act} clock={clock} key={goal.id}/>):<Card title="🎉 All of today’s goals claimed"/>}
  {giftClaimed||completed.length?<><Txt style={styles.sectionTitle}>Claimed</Txt>
   {giftClaimed?<View style={styles.claimedCard}><Txt>🎁</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Daily Puppy Gift</Txt><Txt muted style={styles.small}>Treats + 2 Bones + 3 Pup Coins</Txt></View><Txt style={styles.claimedText}>✓ Claimed</Txt></View>:null}
   {completed.map(goal=><View style={styles.claimedCard} key={goal.id}><Txt style={{fontSize:22}}>{goal.emoji}</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{goal.title}</Txt><Txt muted style={styles.small}>{goal.rewardTreats} Treats + 1 Bone + 1 Pup Coin</Txt></View><Txt style={styles.claimedText}>✓ Claimed</Txt></View>)}
  </>:null}
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>🎲</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Puppy Gacha</Txt><Txt muted style={styles.small}>Earn and unlock eligible puppies</Txt></View><Button compact secondary onPress={()=>onOpen('gacha')}>Open</Button></View>
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>🎰</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Puppy Casino</Txt><Txt muted style={styles.small}>Slots · Roulette · Blackjack · Plinko · Scratchers · Lucky Wheel</Txt></View><Button compact secondary onPress={()=>onOpen('casino')}>Open</Button></View>
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>⭐</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Prestige</Txt><Txt muted style={styles.small}>{g.prestigeCount} prestiges · {g.skillPoints} skill points</Txt></View><Button compact secondary onPress={onPrestige}>Open</Button></View>
  <Card title="🏆 ACHIEVEMENTS" subtitle={statuses.filter(s=>s.xpRewarded).length+' / '+ACHIEVEMENTS.length+' awarded'}>
   <Txt muted style={styles.small}>Local preview achievements and XP are not production Android account rewards.</Txt>
   {statuses.map(item=><View key={item.id} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{item.emoji} {item.title}</Txt><Txt muted style={styles.small}>{item.description}</Txt></View><Txt style={styles.small}>{item.xpRewarded?'✓ +50 XP':Math.min(item.progress,item.target)+' / '+item.target}</Txt></View>)}
  </Card>
 </View>;
}
// The scene assets below are the SAME PNG blobs used by Kotlin's Compose casino.
const CASINO_ART={
 slots:require('./assets/casino/puppy_slots_machine.png'),
 slotsMascot:require('./assets/casino/puppy_slots_mascot.png'),
 slotsLever:require('./assets/casino/puppy_slots_lever_handle.png'),
 roulette:require('./assets/casino/puppy_roulette_wheel_base.png'),
 rouletteRotor:require('./assets/casino/puppy_roulette_rotor.png'),
 rouletteTable:require('./assets/casino/puppy_roulette_table.png'),
 scratchers:require('./assets/casino/puppy_scratch_play_border.png'),
 plinko:require('./assets/casino/puppy_plinko_frame.png'),
 plinkoPegs:require('./assets/casino/puppy_plinko_pegs.png'),
 plinkoBall:require('./assets/casino/puppy_plinko_ball.png'),
 wheel:require('./assets/casino/puppy_lucky_wheel_disc.png'),
 wheelFrame:require('./assets/casino/puppy_lucky_wheel_frame.png'),
 wheelPointer:require('./assets/casino/puppy_lucky_wheel_pointer.png'),
 blackjack:require('./assets/casino/puppy_blackjack_table.png'),
 cardFront:require('./assets/casino/blank_glossy_cream_card_panel.png'),
 cardBack:require('./assets/casino/glossy_blue_paw_print_card_back.png')
};
const CASINO_PAGES=[
 ['slots','🎰','Puppy Slots','Three-reel puppy slot machine',CASINO_ART.slots],
 ['roulette','🎡','Puppy Roulette','European roulette table and wheel',CASINO_ART.roulette],
 ['blackjack','🃏','Puppy Blackjack','52-card deck · Hit / Stand / Double / Split',CASINO_ART.blackjack],
 ['plinko','🔵','Puppy Plinko','Drop a ball through the peg board',CASINO_ART.plinko],
 ['scratchers','🎟️','Puppy Scratchers','Choose a card and reveal the prize',CASINO_ART.scratchers],
 ['wheel','🎠','Lucky Pup Wheel','Spin for simulated prizes',CASINO_ART.wheel]
];
function CasinoArtwork({image,ratio=1,height}){
 return <View style={styles.casinoArtworkFrame}><Image resizeMode="contain" source={image} style={{width:'100%',height:height||undefined,aspectRatio:height?undefined:ratio}}/></View>;
}
function BlackjackArtworkCard({id,hidden=false,width=47,height=66}){
 const source=hidden?CASINO_ART.cardBack:CASINO_ART.cardFront;
 return <ImageBackground source={source} resizeMode="stretch" style={[styles.blackjackArtworkCard,{width,height}]} imageStyle={{borderRadius:5}}>
  {!hidden?<Txt style={[styles.blackjackCardLabel,{fontSize:Math.max(9,width*.29)}]}>{cardLabel(id)}</Txt>:null}
 </ImageBackground>;
}
function BlackjackCardFan({cards=[],hideAfterFirst=false,zone,cardWidth}){
 const fan=blackjackFan(zone.width,cardWidth,cards.length);
 const h=Math.min(fan.cardWidth*1.4,zone.height);
 const left=(zone.width-fan.width)/2;
 return <View style={{position:'absolute',left:zone.left,top:zone.top,width:zone.width,height:zone.height}} pointerEvents="none">
  {cards.map((id,index)=><View key={index} style={{position:'absolute',left:left+index*fan.step,top:(zone.height-h)/2}}>
   <BlackjackArtworkCard id={id} hidden={hideAfterFirst&&index>0} width={fan.cardWidth} height={h}/>
  </View>)}
 </View>;
}
function BlackjackFeltTable({round}){
 const window=useWindowDimensions();
 const [measuredWidth,setMeasuredWidth]=useState(0);
 const width=measuredWidth||Math.min(window.width-32,430);
 const metrics=blackjackTable(width);
 const dealer=round?.dealerCards||[];
 const player=round?.hands?.[round.activeHandIndex]?.cards||[];
 const shared=blackjackSharedCardWidth(metrics,dealer.length,player.length);
 return <View style={{gap:8}}>
  <View style={{width:'100%'}} onLayout={event=>{
   const next=Math.round(event.nativeEvent.layout.width);
   if(next>0&&Math.abs(next-measuredWidth)>1)setMeasuredWidth(next);
  }}>
   <View style={{height:metrics.height,borderRadius:22,overflow:'hidden',backgroundColor:'#0B3026'}}>
    <Image source={CASINO_ART.blackjack} resizeMode="stretch" style={{
     position:'absolute',left:0,top:-metrics.artworkTop,width:metrics.width,height:metrics.artworkHeight
    }}/>
    <BlackjackCardFan cards={dealer} hideAfterFirst={Boolean(round&&!round.complete)} zone={metrics.dealer} cardWidth={shared}/>
    <BlackjackCardFan cards={player} zone={metrics.player} cardWidth={shared}/>
   </View>
  </View>
  <Txt muted style={styles.small}>{!round?'Choose a wager to deal.':round.complete?
   'Dealer · '+handTotal(dealer).total+' points':
   'Dealer · '+(dealer.length?cardLabel(dealer[0]):'Waiting')+' showing · hole card hidden'}</Txt>
 </View>;
}
function CasinoScreen({g,act,state,bet,setBet,onBack}){
 const [page,setPage]=useState(g.pendingScratch?'scratchers':'hub');
 const [accepted,setAccepted]=useState(false);
 const {width}=useWindowDimensions();
 useEffect(()=>{
  if(!accepted)Alert.alert('Puppy Casino — Simulated Gambling',
   'Slots, roulette, blackjack, Plinko, scratchers and the Lucky Pup Wheel use game-only chips. No real money is used and no prizes have real-world cash value.',
   [{text:'Go Back',style:'cancel',onPress:onBack},{text:'Continue',onPress:()=>setAccepted(true)}],{cancelable:false});
 },[accepted,onBack]);
 const casino=(gameType,extra={})=>act({type:'casino',gameType,wager:bet,...extra});
 const choices=[20,50,100,500,1000];
 const inBlackjack=Boolean(state.blackjack);
 const visiblePage=CASINO_PAGES.find(p=>p[0]===page);
 return <View style={styles.gamePage}>
  <View style={styles.pair}>
   <Button compact secondary onPress={()=>page==='hub'?onBack():setPage('hub')}>{page==='hub'?'‹ Rewards':'‹ Casino'}</Button>
   <Txt style={styles.pageTitle}>{visiblePage?.[2]||'Puppy Casino'}</Txt>
  </View>
  <Txt muted style={styles.small}>🐾 Casino Chip Wallet · {format(g.casinoChips)} Chips · Simulated, no real-money prizes</Txt>
  {page==='hub'?<>
   {g.pendingScratch?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎟️</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Scratchers Card</Txt><Txt muted style={styles.small}>Your saved result is ready. Scratch 68% to claim it; no other wagers can start.</Txt></View>
    <Button compact onPress={()=>setPage('scratchers')}>Resume</Button>
   </View>:null}
   <View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:28}}>🐾</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Treats → Casino Chips</Txt><Txt muted style={styles.small}>10 🍪 = 1 🐾 · One-way conversion</Txt></View>
   </View>
   <View style={styles.actions}><Button compact secondary disabled={g.treats<100} onPress={()=>act({type:'exchange',amount:100})}>100 🍪 → 10 🐾</Button><Button compact secondary disabled={g.treats<1000} onPress={()=>act({type:'exchange',amount:1000})}>1,000 🍪 → 100 🐾</Button></View>
   {CASINO_PAGES.map(([id,emoji,title,description,art])=><Pressable accessibilityRole="button" key={id} onPress={()=>setPage(id)} style={styles.casinoGameTile}>
    <Image source={art} resizeMode="contain" style={styles.casinoTileArt}/><View style={{flex:1,gap:5}}><Txt style={styles.itemName}>{emoji} {title}</Txt><Txt muted style={styles.small}>{description}</Txt></View><Txt style={styles.casinoTileArrow}>›</Txt>
   </Pressable>)}
  </>:<>
   <View style={styles.filterRow}>{choices.map(w=><Pressable key={w} accessibilityRole="button" accessibilityState={{selected:bet===w}} style={[styles.filterChip,bet===w&&styles.filterChipSelected]} onPress={()=>setBet(w)}><Txt numberOfLines={1} style={[styles.filterText,bet===w&&styles.filterTextSelected]}>{w}</Txt></Pressable>)}</View>
   {page==='slots'?<>
    <CasinoArtwork image={CASINO_ART.slots} ratio={1.17} height={Math.min(330,width*.72)}/>
    <View style={styles.dailyGiftCard}><Image source={CASINO_ART.slotsMascot} resizeMode="contain" style={{width:72,height:70}}/><View style={{flex:1}}><Txt style={styles.itemName}>Puppy Slots</Txt><Txt muted style={styles.small}>Triple-symbol and weighted reel payouts.</Txt></View><Image source={CASINO_ART.slotsLever} resizeMode="contain" style={{width:42,height:80}}/></View>
    <Button disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('slots',{rolls:[roll(),roll(),roll()]})}>🎰 Pull Lever · {bet} Chips</Button>
   </>:null}
   {page==='roulette'?<>
    <View style={styles.rouletteScene}><Image source={CASINO_ART.roulette} resizeMode="contain" style={styles.rouletteWheel}/><Image source={CASINO_ART.rouletteRotor} resizeMode="contain" style={styles.rouletteRotor}/></View>
    <CasinoArtwork image={CASINO_ART.rouletteTable} ratio={2}/>
    <Txt style={styles.sectionTitle}>Choose your bet</Txt>
    <View style={styles.quickActionRow}>
     <Button compact disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('roulette',{bet:'RED',roll:number()})}>🔴 Red</Button>
     <Button compact secondary disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('roulette',{bet:'BLACK',roll:number()})}>⚫ Black</Button>
     <Button compact secondary disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('roulette',{bet:'EVEN',roll:number()})}>Even</Button>
    </View>
   </>:null}
   {page==='plinko'?<>
    <View style={styles.plinkoScene}>
     <Image source={CASINO_ART.plinko} resizeMode="contain" style={styles.plinkoLayer}/>
     <Image source={CASINO_ART.plinkoPegs} resizeMode="contain" style={styles.plinkoLayer}/>
     <Image source={CASINO_ART.plinkoBall} resizeMode="contain" style={styles.plinkoBall}/>
    </View>
    <Button disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('plinko',{path:Array.from({length:8},()=>Math.random()<0.5)})}>🔵 Drop Ball · {bet} Chips</Button>
   </>:null}
   {page==='scratchers'?<>
    <Txt style={styles.sectionTitle}>Puppy Scratchers</Txt>
    {g.pendingScratch?<ScratchStage key={g.pendingScratch.createdAt} round={g.pendingScratch}
      borderImage={CASINO_ART.scratchers} onReveal={()=>act({type:'scratchReveal',now:Date.now()})}/>
      :<CasinoArtwork image={CASINO_ART.scratchers} ratio={1426/1077}/>}
    {g.pendingScratch?<Txt muted style={styles.small}>Card: {g.pendingScratch.card} · Wager: {g.pendingScratch.wager} Chips. Complete the scratch gesture before placing another bet.</Txt>:null}
    {SCRATCHERS.map(card=><View key={card.id} style={styles.casinoGameTile}><Txt style={{fontSize:26}}>{card.emoji}</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{card.name}</Txt><Txt muted style={styles.small}>{format(card.wager)} Chips</Txt></View><Button compact disabled={g.casinoChips<card.wager||inBlackjack||Boolean(g.pendingScratch)} onPress={()=>act({type:'casino',gameType:'scratch',wager:card.wager,roll:roll(),now:Date.now()})}>{g.pendingScratch?'Pending':'Buy card'}</Button></View>)}
   </>:null}
   {page==='wheel'?<>
    <View style={styles.wheelScene}>
     <Image source={CASINO_ART.wheelFrame} resizeMode="contain" style={styles.wheelLayer}/>
     <Image source={CASINO_ART.wheel} resizeMode="contain" style={styles.wheelLayer}/>
     <Image source={CASINO_ART.wheelPointer} resizeMode="contain" style={styles.wheelPointer}/>
    </View>
    <Button disabled={g.casinoChips<bet||inBlackjack} onPress={()=>casino('wheel',{roll:roll()})}>🎠 Spin Lucky Pup Wheel · {bet} Chips</Button>
   </>:null}
   {page==='blackjack'?<>
    <BlackjackFeltTable round={state.blackjack}/>
    {state.blackjack?<>
     <Txt muted>Current hand {state.blackjack.activeHandIndex+1} / {state.blackjack.hands.length}</Txt>
     {state.blackjack.hands.map((hand,index)=><View key={index} style={styles.dailyGiftCard}><View style={{flex:1}}><Txt style={styles.itemName}>Hand {index+1} · {handTotal(hand.cards).total} {index===state.blackjack.activeHandIndex?'▶':''}</Txt><Txt muted style={styles.small}>{format(hand.wager)} Chips {hand.doubled?'· Doubled':''} {hand.fromSplit?'· Split':''}</Txt></View></View>)}
     <View style={styles.quickActionRow}>
      <Button disabled={!canHit(state.blackjack)} onPress={()=>act({type:'blackjackHit'})}>🐾 HIT</Button>
      <Button secondary disabled={!canStand(state.blackjack)} onPress={()=>act({type:'blackjackStand'})}>STAND</Button>
     </View>
     <View style={styles.quickActionRow}>
      <Button secondary disabled={!canDouble(state.blackjack)||g.casinoChips<state.blackjack.hands[state.blackjack.activeHandIndex].wager} onPress={()=>act({type:'blackjackDouble'})}>2× DOUBLE</Button>
      <Button secondary disabled={!canSplit(state.blackjack)||g.casinoChips<state.blackjack.hands[state.blackjack.activeHandIndex].wager} onPress={()=>act({type:'blackjackSplit'})}>⇄ SPLIT</Button>
     </View>
    </>:<>
     <Txt muted style={styles.small}>One 52-card deck · Blackjack 3:2 · Dealer stands on 17 · Up to three hands. Unfinished local guest rounds are restored after restart.</Txt>
     <Button disabled={g.casinoChips<bet} onPress={()=>act({type:'blackjackStart',wager:bet,deck:shuffledBlackjackDeck()})}>🃏 Deal Blackjack · {bet} Chips</Button>
    </>}
   </>:null}
  </>}
 </View>;
}
function OnboardingScreen({g,act}){
 const step=g.onboardingStep;
 const [name,setName]=useState(g.guestName==='Guest Puppy Fan'?'':g.guestName);
 const [month,setMonth]=useState(g.birthdayMonth?String(g.birthdayMonth):'');
 const [day,setDay]=useState(g.birthdayDay?String(g.birthdayDay):'');
 const advance=()=>act({type:'onboardNext'});
 const dateMonth=month.trim()?Number(month):0,dateDay=day.trim()?Number(day):0;
 return <Card title={'🐾 SETUP · '+ONBOARDING_STEPS[step].replace('_',' ')} subtitle={'Step '+(step+1)+' of 6'}>
  <View style={styles.track}><View style={[styles.fill,{width:((step+1)/6*100)+'%'}]}/></View>
  {step===0?<><Txt style={styles.heading}>Welcome to Puppy Clicker!</Txt><Txt muted>Meet your puppies, earn Treats by tapping, take care of their needs, collect rewards and grow your roster.</Txt><Txt muted>This is the new local React Native preview. Your original Play Store account and cloud progress are not linked to this preview.</Txt></>:null}
  {step===1?<><Txt style={styles.heading}>Set up a guest profile</Txt><Txt muted>Your original Android release uses verified Discord accounts. Discord login and existing-save migration are not enabled yet in this test build.</Txt><Txt style={styles.itemName}>Guest display name</Txt><TextInput accessibilityLabel="Guest name" style={styles.input} placeholder="Guest Puppy Fan" placeholderTextColor={C.muted} value={name} maxLength={24} onChangeText={setName}/><Txt muted style={styles.small}>This name is local and is not an official account or Player ID.</Txt></>:null}
  {step===2?<><Txt style={styles.heading}>Personalize</Txt><Txt muted>Your birthday is optional. Only a month and day are kept in this device's local guest save.</Txt>
   <View style={styles.actions}><View style={{flex:1}}><Txt>Month (1–12)</Txt><TextInput accessibilityLabel="Birthday month" style={styles.input} keyboardType="number-pad" maxLength={2} placeholder="Month" placeholderTextColor={C.muted} value={month} onChangeText={setMonth}/></View>
   <View style={{flex:1}}><Txt>Day (1–31)</Txt><TextInput accessibilityLabel="Birthday day" style={styles.input} keyboardType="number-pad" maxLength={2} placeholder="Day" placeholderTextColor={C.muted} value={day} onChangeText={setDay}/></View></View>
   {!validBirthday(dateMonth,dateDay)?<Txt style={{color:C.danger}}>Choose both a valid month and day or leave both blank.</Txt>:null}
   <Button secondary compact onPress={()=>{setMonth('');setDay('');act({type:'onboardBirthday',month:0,day:0});}}>Skip Birthday</Button>
  </>:null}
  {step===3?<><Txt style={styles.heading}>Permissions & privacy</Txt><Txt muted>All gameplay in this preview is stored in a local guest save. There is no official Supabase synchronization, Discord account, device binding or PupEye verification.</Txt><Txt muted>Completing this screen does not create an account or opt you into data collection. Production security and privacy flows are separate migration requirements.</Txt></>:null}
  {step===4?<><Txt style={styles.heading}>Notifications</Txt><Txt muted>The preview can remember your notification preference, but it does not request notification permission or schedule Android alerts until that feature is implemented and tested.</Txt>
   <View style={styles.actions}><Button compact secondary={!g.notificationsOptIn} onPress={()=>act({type:'onboardNotifications',enabled:true})}>Enable preference</Button><Button compact secondary={g.notificationsOptIn} onPress={()=>act({type:'onboardNotifications',enabled:false})}>Keep disabled</Button></View>
   <Txt muted>Current choice: {g.notificationsOptIn?'Preference enabled; no alerts yet':'Disabled'}</Txt>
  </>:null}
  {step===5?<><Txt style={styles.heading}>Ready to play!</Txt><Pair left="Guest profile" right={g.guestName}/><Pair left="Puppy" right={g.puppyName}/><Pair left="Birthday" right={g.birthdayMonth?g.birthdayMonth+'/'+g.birthdayDay:'Skipped'}/><Pair left="Cloud verification" right="Not connected"/><Txt muted>Start a separate React Native guest save. Your existing Kotlin Puppy Clicker installation and progress remain unchanged.</Txt></>:null}
  <View style={styles.actions}>
   {step>0?<Button compact secondary onPress={()=>act({type:'onboardBack'})}>← Back</Button>:null}
   {step===5?<Button onPress={()=>act({type:'onboardFinish'})}>🐾 Start Playing</Button>:
    <Button disabled={step===2&&!validBirthday(dateMonth,dateDay)} onPress={()=>{
     if(step===1)act({type:'onboardPlayer',name:name.trim()||'Guest Puppy Fan'});
     if(step===2)act({type:'onboardBirthday',month:dateMonth,day:dateDay});
     advance();
    }}>Continue →</Button>}
  </View>
 </Card>;
}
// Matches the native PuppySettingsScreen accordion groups without inventing
// an authenticated account, protection status or notification permissions.
function SettingsSection({id,icon,title,subtitle,expanded,onToggle,children}){
 const open=expanded===id;
 return <View style={styles.settingsSection}>
  <Pressable accessibilityRole="button" accessibilityState={{expanded:open}} onPress={()=>onToggle(open?null:id)} style={styles.settingsSectionHeader}>
   <Txt style={styles.settingsSectionIcon}>{icon}</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{title}</Txt><Txt muted style={styles.small}>{subtitle}</Txt></View><Txt style={styles.settingsChevron}>{open?'⌃':'⌄'}</Txt>
  </Pressable>
  {open?<View style={styles.settingsSectionBody}>{children}</View>:null}
 </View>;
}
function SettingsScreen({g,act,onOpen,registry}){
 const [editing,setEditing]=useState(g.puppyName);
 const [expanded,setExpanded]=useState(null);
 useEffect(()=>setEditing(g.puppyName),[g.puppyName]);
 const toggle=setExpanded;
 const section=(id,icon,title,subtitle,content)=><SettingsSection key={id} id={id} icon={icon} title={title} subtitle={subtitle} expanded={expanded} onToggle={toggle}>{content}</SettingsSection>;
 const pending=<Txt muted>Not enabled in this guest preview. The original Kotlin app's linked-account and protected services have not been migrated.</Txt>;
 return <View style={styles.gamePage}>
  <PageHeader title="Settings" subtitle="Everything in one place. Tap a card to expand its settings."/>
  <View style={styles.activePuppy}><Txt style={{fontSize:32}}>🐶</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{g.guestName}</Txt><Txt muted style={styles.small}>Guest · Level {guestLevel(g)} · {g.puppyName}</Txt></View><Txt style={styles.previewPill}>PREVIEW</Txt></View>
  <Txt style={styles.settingsGroupTitle}>ACCOUNT</Txt>
  {section('profile','👤','Profile',g.guestName+' · Level '+guestLevel(g),<>
   <Txt style={styles.itemName}>Puppy name</Txt><TextInput style={styles.input} placeholder="Puppy name" placeholderTextColor={C.muted} value={editing} onChangeText={setEditing} maxLength={18}/><Button compact onPress={()=>act({type:'rename',name:editing})}>Save Name</Button>
  </>)}
  {section('discord','◉','Discord','Connect or manage your Discord account',pending)}
  <Txt style={styles.settingsGroupTitle}>APP & EXPERIENCE</Txt>
  {section('appearance','🎨','Appearance','Theme, accent color, scale, contrast and motion',<Txt muted>Using the original Kotlin dark palette and Puppy Cyan accent. User-selectable theme, scaling and motion options are still being ported.</Txt>)}
  {section('notifications','🔔','Notifications','In-game alerts, updates and notification permissions',<Txt muted>Notifications are not active. Onboarding only saves a local preference; no Android permission or push delivery is configured.</Txt>)}
  {section('privacy','🛡','Privacy & Data','Diagnostics, crash reporting, network use and PupEye privacy',<Txt muted>Guest game data is device-local. No production Discord identity, Supabase save or PupEye attestation is active.</Txt>)}
  <Txt style={styles.settingsGroupTitle}>GAME</Txt>
  {section('gameplay','🎮','Gameplay','Interaction, feedback and gameplay preferences',<>
   <Pair left="Puppy" right={g.puppyName}/>
   <Pair left="Tap power" right={format(g.clickPower)}/>
   <Pair left="Total taps" right={format(g.totalTaps)}/>
   <View style={styles.pair}><Txt>Tap haptics</Txt><Button secondary compact onPress={()=>act({type:'setPreference',name:'hapticsEnabled',value:g.hapticsEnabled===false})}>{g.hapticsEnabled===false?'OFF':'ON'}</Button></View>
   <View style={styles.pair}><Txt>Tap animation</Txt><Button secondary compact onPress={()=>act({type:'setPreference',name:'animationsEnabled',value:g.animationsEnabled===false})}>{g.animationsEnabled===false?'OFF':'ON'}</Button></View>
   <View style={styles.pair}><Txt>Compact wallet numbers</Txt><Button secondary compact onPress={()=>act({type:'setPreference',name:'compactNumbers',value:g.compactNumbers===false})}>{g.compactNumbers===false?'OFF':'ON'}</Button></View>
  </>)}
  {section('data','💾','Data Management','Save status, backups, resets and local data',<>
   <Txt muted>LOCAL GUEST SAVE ONLY. This does not access your original Kotlin progress, Supabase saves or Google Play account.</Txt>
   <Button danger secondary onPress={()=>Alert.alert('Reset Local Guest','Delete only this React Native guest progress? This cannot affect the original Kotlin app.',[{text:'Cancel',style:'cancel'},{text:'Reset guest',style:'destructive',onPress:()=>act({type:'clearGuest'})}])}>Reset Local Guest Progress</Button>
  </>)}
  <Txt style={styles.settingsGroupTitle}>SUPPORT</Txt>
  {section('support','🐾','Support & Reports','Report bugs, accounts and Casino issues',pending)}
  <Txt style={styles.settingsGroupTitle}>ADVANCED</Txt>
  {section('advanced','▾','Advanced','Roster, PupEye, Online Assets, Release Hub and About',<Txt muted>Open the items below for more information.</Txt>)}
  {section('roster','🐶','Puppy Roster','Manage your puppy collection',<Button secondary onPress={()=>onOpen('roster')}>🐶 Open Puppy Roster</Button>)}
  {section('pupeye','👁','PupEye Protection','Fair-play and save integrity',pending)}
  {section('assets','☁','Online Assets','Streamed asset, roster and cache status',<><Pair left="Roster source" right={registry.source}/><Pair left="Visible puppies" right={registry.items.length}/><Txt muted>Validated live manifests or cached data are displayed read-only. New puppy ownership must be verified by the production account server.</Txt></>)}
  {section('release-hub','↻','Release Hub',"What's New, installed build and updates",<Txt muted>React Native preview 1.8.0. Install updates through the separate preview GitHub Actions build.</Txt>)}
  {section('about','ⓘ','About Puppy Clicker','Version, development and legal',<>
   <Pair left="Developer" right="Harley's Studios"/><Pair left="React engine" right="React Native 0.86"/><Pair left="App ID" right="com.harleytg.puppyclicker.preview"/><Txt muted>Preview build, not a replacement for the Google Play Kotlin release.</Txt>
  </>)}
 </View>;
}

export default function App(){
 const {state,act}=useGame();
 const registry=useRoster();
 const {width}=useWindowDimensions();
 const [tab,setTab]=useState('play');
 const [secondary,setSecondary]=useState(null);
 const [bet,setBet]=useState(20);
 const [clock,setClock]=useState(Date.now());
 useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000);return ()=>clearInterval(timer);},[]);
 const g=state.game;
 const main=()=>{
  if(secondary==='roster')return <RosterScreen g={g} act={act} registry={registry}/>;
  if(secondary==='rewards')return <RewardsScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule} onOpen={setSecondary} onPrestige={()=>{setTab('prestige');setSecondary(null);}}/>;
  if(secondary==='gacha')return <GachaScreen g={g} act={act} state={state} registry={registry} onBack={()=>setSecondary('rewards')}/>;
  if(secondary==='casino')return <CasinoScreen g={g} act={act} bet={bet} setBet={setBet} state={state} onBack={()=>setSecondary('rewards')}/>;
  if(tab==='play')return <PlayScreen g={g} act={act} clock={clock}/>;
  if(tab==='care')return <CareScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule} onOpen={setSecondary}/>;
  if(tab==='shop')return <ShopScreen g={g} act={act} onOpen={setSecondary}/>;
  if(tab==='prestige')return <PrestigeScreen g={g} act={act} activeCasinoRound={Boolean(state.blackjack||g.pendingScratch)}/>;
  return <SettingsScreen g={g} act={act} onOpen={setSecondary} registry={registry}/>;
 };
 return <View style={styles.root}>
  <StatusBar barStyle="light-content" backgroundColor={C.bg}/>

  {state.notice?<Pressable accessibilityRole="button" onPress={()=>act({type:'dismiss'})} style={styles.notice}><Txt style={styles.small}>{state.notice}  ✕</Txt></Pressable>:null}
  <ScrollView key={secondary||tab} style={styles.scroll} contentContainerStyle={[styles.content,{maxWidth:width>650?670:undefined}]} keyboardShouldPersistTaps="handled">
   {secondary?<View style={styles.back}><Button compact secondary onPress={()=>setSecondary(null)}>← Back to {TABS.find(x=>x[0]===tab)?.[2]}</Button><Txt style={styles.heading}>{secondary.toUpperCase()}</Txt></View>:null}
   {!state.ready?<Card title="Loading local guest data…"><Txt muted>Reading device cache.</Txt></Card>:!g.onboardingComplete?<OnboardingScreen g={g} act={act}/>:<>{secondary==='casino'?<Wallet game={g}/>:null}{main()}</>}
  </ScrollView>
  {state.ready&&g.onboardingComplete?<View style={styles.bottomBar}>
   {TABS.map(([id,icon,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:tab===id&&!secondary}} onPress={()=>{setTab(id);setSecondary(null);}} style={[styles.tab,(tab===id&&!secondary)&&styles.tabSelected]}>
    <Txt style={styles.tabEmoji}>{icon}</Txt><Txt style={[styles.tabLabel,(tab===id&&!secondary)&&{color:C.accent}]}>{label}</Txt>
   </Pressable>)}
  </View>:null}
 </View>;
}
const styles=StyleSheet.create({
 ticketOverlay:{position:'absolute',top:138,left:12,right:12,zIndex:30,backgroundColor:'#12323B',borderColor:C.accent,borderWidth:1,borderRadius:18,paddingHorizontal:12,paddingVertical:12,alignItems:'center',elevation:8},
 ticketOverlayTitle:{fontSize:15,fontWeight:'900',color:C.text,textAlign:'center'},
 ticketOverlayDescription:{fontSize:11,color:C.muted,textAlign:'center',marginTop:3},

 casinoGameTile:{flexDirection:'row',alignItems:'center',gap:12,padding:12,backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line},
 casinoTileArt:{width:80,height:80},
 casinoTileArrow:{fontSize:28,color:C.accent},
 casinoArtworkFrame:{backgroundColor:C.surface,borderRadius:20,overflow:'hidden',alignItems:'center',justifyContent:'center'},
 rouletteScene:{height:265,alignItems:'center',justifyContent:'center'},
 rouletteWheel:{height:260,width:260},
 rouletteRotor:{height:188,width:188,position:'absolute'},
 plinkoScene:{height:330,alignItems:'center',justifyContent:'center'},
 plinkoLayer:{height:330,width:'100%',position:'absolute'},
 plinkoBall:{height:27,width:27,position:'absolute',top:37},
 wheelScene:{height:320,alignItems:'center',justifyContent:'center'},
 wheelLayer:{height:310,width:310,position:'absolute'},
 wheelPointer:{height:80,width:55,position:'absolute',top:0},
 blackjackFelt:{width:'100%',aspectRatio:1122/1188,borderRadius:22,overflow:'hidden',justifyContent:'space-between',paddingVertical:'16%'},
 blackjackDealerArea:{alignItems:'center',gap:5,minHeight:95},
 blackjackPlayerArea:{alignItems:'center',gap:5,minHeight:105},
 blackjackFeltTitle:{color:'#FCFAE9',fontWeight:'900',fontSize:12,textShadowColor:'#1E392C',textShadowRadius:4},
 blackjackFan:{flexDirection:'row',alignItems:'center',justifyContent:'center',gap:3,minHeight:68},
 blackjackArtworkCard:{height:66,width:47,justifyContent:'center',alignItems:'center'},
 blackjackCardLabel:{fontSize:15,fontWeight:'900',color:'#1C2424'},

 dailyGoalCard:{flexDirection:'row',gap:12,padding:14,borderRadius:18,backgroundColor:C.surface,borderWidth:1,borderColor:C.line,alignItems:'flex-start'},
 nextRewardCard:{flexDirection:'row',alignItems:'center',gap:10,padding:12,backgroundColor:'#1B2930',borderColor:C.line,borderWidth:1,borderRadius:14},
 dailyGiftCard:{flexDirection:'row',gap:10,alignItems:'center',padding:14,borderRadius:19,backgroundColor:'#1B2930',borderColor:C.line,borderWidth:1},
 streakCard:{flexDirection:'row',alignItems:'center',gap:12,padding:16,backgroundColor:'#1B2930',borderRadius:22,borderWidth:1,borderColor:C.line},
 streakDot:{height:24,width:24,justifyContent:'center',alignItems:'center',borderRadius:20,backgroundColor:C.card,borderWidth:1,borderColor:C.line},
 streakDotFilled:{backgroundColor:C.accent,borderColor:C.accent},
 claimedCard:{flexDirection:'row',alignItems:'center',gap:10,padding:12,borderRadius:16,backgroundColor:C.surface,borderColor:C.line,borderWidth:1},
 claimedText:{fontSize:11,fontWeight:'900',color:C.accent},

 selectedPuppyPanel:{flexDirection:'row',alignItems:'center',gap:14,backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:18,padding:12},

 prestigeCenter:{backgroundColor:'#1B2930',borderRadius:24,padding:16,gap:11,borderWidth:1,borderColor:C.line},
 prestigeTitle:{flexDirection:'row',alignItems:'center',gap:9},
 settingsGroupTitle:{fontSize:12,fontWeight:'900',letterSpacing:1,color:C.muted,marginTop:7,marginBottom:1},
 settingsSection:{backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:17,overflow:'hidden'},
 settingsSectionHeader:{flexDirection:'row',alignItems:'center',gap:11,paddingHorizontal:14,paddingVertical:14},
 settingsSectionBody:{gap:10,paddingHorizontal:14,paddingBottom:14},
 settingsSectionIcon:{fontSize:24,width:30,textAlign:'center'},
 settingsChevron:{fontSize:22,fontWeight:'700',color:C.muted},
 previewPill:{fontSize:10,fontWeight:'800',color:C.gold,backgroundColor:'#352B13',paddingHorizontal:8,paddingVertical:6,borderRadius:10,overflow:'hidden'},

 // Kotlin PuppyGacha.kt machine 1024×1536 + original 5 separate PNG layers.
 gachaHeader:{flexDirection:'row',alignItems:'center',gap:10},
 gachaWallet:{flexDirection:'row',alignItems:'center',borderRadius:28,backgroundColor:'#8ADFF7',paddingHorizontal:14,paddingVertical:10,borderWidth:2,borderColor:'#FFFFFF'},
 gachaWalletCell:{flex:1,paddingHorizontal:8,gap:2},
 gachaWalletDivider:{height:42,width:2,backgroundColor:'#FFFFFF'},
 gachaWalletLabel:{fontSize:12,fontWeight:'900',color:'#102331'},
 gachaWalletValue:{fontSize:21,fontWeight:'900',color:'#081923'},
 gachaMachine:{alignSelf:'center',position:'relative',overflow:'hidden'},
 gachaOpenButton:{position:'absolute',bottom:GACHA_MACHINE.openButtonBottom,alignSelf:'center'},
 gachaPurchaseRow:{flexDirection:'row',gap:10},
 gachaPurchaseButton:{flex:1,height:64,borderRadius:28,borderWidth:2,justifyContent:'center',alignItems:'center',paddingHorizontal:5},
 gachaPurchaseTitle:{fontSize:11,fontWeight:'900',color:'#FFFFFF',textAlign:'center'},
 gachaPurchaseSubtitle:{fontSize:12,fontWeight:'800',color:'#FFFFFF',textAlign:'center'},
 gachaFailure:{color:'#FFE9E9',fontWeight:'800',textAlign:'center',padding:10,backgroundColor:'#6C2828',borderRadius:14},
 gachaModalScreen:{flex:1,backgroundColor:'rgba(0,0,0,0.70)',justifyContent:'center',paddingHorizontal:20},
 gachaRevealCard:{backgroundColor:C.surface,borderRadius:28,padding:18,gap:12,alignItems:'center',borderWidth:2,borderColor:C.accent},
 gachaRevealHeading:{fontSize:20,fontWeight:'900',color:C.text,textAlign:'center'},
 gachaRevealPortrait:{width:'100%',paddingVertical:10,alignItems:'center',borderRadius:24,backgroundColor:C.accentSoft},
 // Kotlin V6Pups portrait, 86dp horizontal entries and locked scrim.
 portraitBadge:{position:'absolute',backgroundColor:'rgba(19,28,32,0.86)',borderRadius:999,padding:3},
 portraitAccessory:{position:'absolute',alignSelf:'center'},
 lockedPortraitOverlay:{position:'absolute',top:0,left:0,right:0,bottom:0,backgroundColor:'rgba(0,0,0,0.42)',alignItems:'center',justifyContent:'center'},
 rosterCollectionGroup:{gap:8},
 rosterCollectionRow:{backgroundColor:C.surface,borderRadius:16,padding:12,flexDirection:'row',alignItems:'center',gap:11,borderWidth:1,borderColor:C.line},
 rosterRename:{gap:9},
 // Kotlin V6Shop and PuppyPupCoinShop native card and offer hierarchy.
 shopTopActions:{flexDirection:'row',gap:8},
 shopDiscountLabel:{color:C.accent,fontWeight:'800',fontSize:13},
 shopUpgradeCard:{backgroundColor:C.surface,borderRadius:16,padding:13,gap:9,borderWidth:1,borderColor:C.line},
 shopTicketUpgradeCard:{backgroundColor:C.card},
 shopUpgradeHeading:{flexDirection:'row',alignItems:'center',gap:10},
 shopUpgradeFooter:{flexDirection:'row',alignItems:'center',gap:8,justifyContent:'space-between'},
 shopRarity:{fontSize:12,fontWeight:'800',color:C.gold,maxWidth:90,textAlign:'right'},
 shopAccessoryRow:{flexDirection:'row',alignItems:'center',padding:12,borderRadius:16,backgroundColor:C.surface,borderWidth:1,borderColor:C.line,gap:8},
 pupCoinBalance:{borderRadius:18,backgroundColor:C.accentSoft,padding:14,gap:5,borderWidth:1,borderColor:C.line},
 prestigeCenterTitle:{fontSize:21,fontWeight:'900',color:C.text},
 prestigeProgressTrack:{height:7,backgroundColor:C.line,borderRadius:8,overflow:'hidden'},
 prestigeSkillCard:{backgroundColor:C.surface,borderRadius:16,padding:12,gap:7,borderWidth:1,borderColor:C.line},
 // Kotlin V6CareAndDaily: two-column action rows, 125dp portrait, 6dp meter tracks.
 careIdentity:{flexDirection:'row',alignItems:'center',gap:14},
 careBond:{fontSize:14,color:C.accent,fontWeight:'800'},
 careMeters:{gap:V6_CARE_LAYOUT.careMeterSpacing},
 careMeter:{backgroundColor:C.surface,padding:V6_CARE_LAYOUT.careMeterPadding,borderRadius:14,gap:5},
 careMeterTrack:{width:'100%',height:V6_CARE_LAYOUT.careMeterHeight,backgroundColor:C.line,borderRadius:10,overflow:'hidden'},
 careMeterFill:{height:'100%',borderRadius:10,backgroundColor:C.accent},
 careActionRow:{flexDirection:'row',gap:V6_CARE_LAYOUT.careActionSpacing},
 careRewardNote:{borderRadius:18,backgroundColor:C.accentSoft,padding:12,gap:5},
 careStreakBanner:{flexDirection:'row',alignItems:'center',gap:10,padding:16,borderRadius:V6_CARE_LAYOUT.dailyStreakRadius,backgroundColor:C.accentSoft},
 careStreakTitle:{fontSize:22,fontWeight:'900',color:C.text},
 careDailyGift:{flexDirection:'row',alignItems:'center',gap:10,padding:14,backgroundColor:C.surface,borderRadius:17},
 careParkCard:{flexDirection:'row',alignItems:'center',gap:10,padding:14,backgroundColor:C.surface,borderRadius:17},
 // Kotlin V6 source: PuppyClickerV6Activity.kt and PuppyMainScreensRevamp.kt.
 gamePage:{gap:10},
 pageHeader:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10,paddingBottom:2},
 pageTitle:{fontSize:27,fontWeight:'900',letterSpacing:-0.5,color:C.text},
 pageSubtitle:{fontSize:14,color:C.muted,marginTop:2},
 needsRow:{flexDirection:'row',gap:6},
 needPill:{flex:1,minWidth:0,backgroundColor:C.surface,borderRadius:V6_PLAY_LAYOUT.needRadius,paddingVertical:5,paddingHorizontal:6,gap:2},
 needMeterTrack:{width:'100%',height:V6_PLAY_LAYOUT.needMeterHeight,backgroundColor:C.card,borderRadius:4,overflow:'hidden'},
 needMeterFill:{height:'100%',backgroundColor:C.accent,borderRadius:4},
 needPillLow:{backgroundColor:'#431F26'},
 needPillText:{fontSize:12,fontWeight:'800',color:C.text},
 tapStage:{backgroundColor:C.accentSoft,borderRadius:V6_PLAY_LAYOUT.stageRadius,alignItems:'center',justifyContent:'center',overflow:'hidden',position:'relative'},
 puppyCircle:{backgroundColor:C.surface,alignItems:'center',justifyContent:'center',overflow:'hidden'},
 tapLabel:{position:'absolute',bottom:V6_PLAY_LAYOUT.stageBottomLabelInset,alignSelf:'center',backgroundColor:C.surface,borderRadius:18,paddingHorizontal:14,paddingVertical:8,maxWidth:'95%'},
 tapLabelText:{fontSize:14,fontWeight:'800',textAlign:'center',color:C.text},
 menuShortcuts:{flexDirection:'row',gap:6,paddingTop:8},
 quickActionRow:{flexDirection:'row',gap:6},
 quickAction:{flex:1,minWidth:0,minHeight:74,backgroundColor:C.surface,borderWidth:1,borderColor:C.line,borderRadius:18,paddingHorizontal:4,paddingVertical:6,justifyContent:'center',alignItems:'center',gap:3},
 quickActionIcon:{fontSize:21},
 quickActionTitle:{fontSize:12,color:C.text,fontWeight:'800',textAlign:'center'},
 quickActionDescription:{fontSize:10,color:C.muted,textAlign:'center'},
 filterRow:{flexDirection:'row',gap:6,marginVertical:3},
 filterChip:{flex:1,minWidth:0,borderRadius:12,borderWidth:1,borderColor:C.line,backgroundColor:C.surface,paddingHorizontal:4,paddingVertical:11,alignItems:'center',justifyContent:'center'},
 filterChipSelected:{backgroundColor:C.accentSoft,borderColor:C.accent},
 filterText:{color:C.muted,fontWeight:'700',fontSize:12,textAlign:'center'},
 filterTextSelected:{color:C.accent},
 activePuppy:{flexDirection:'row',alignItems:'center',gap:12,padding:12,backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line},
 sectionTitle:{fontSize:17,fontWeight:'800',color:C.text,marginTop:6},
 upgradeCard:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line,paddingHorizontal:12,paddingVertical:16,gap:8},

 root:{flex:1,backgroundColor:C.bg,paddingTop:StatusBar.currentHeight||0},
 text:{fontSize:14,color:C.text},muted:{color:C.muted},small:{fontSize:12},tiny:{fontSize:10,color:C.muted},tinyCenter:{fontSize:10,color:C.muted,textAlign:'center'},
 center:{textAlign:'center',fontSize:12},heading:{fontSize:17,fontWeight:'800',letterSpacing:0.25},brand:{fontSize:17,fontWeight:'900',letterSpacing:0.8},value:{fontWeight:'800'},itemName:{fontWeight:'700',fontSize:14},
 topBar:{paddingHorizontal:16,paddingVertical:12,borderBottomWidth:1,borderColor:C.line,flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:4,backgroundColor:C.surface},
 topTitles:{flex:1,gap:2},guestTag:{fontSize:10,fontWeight:'800',color:C.gold,backgroundColor:'#352B13',paddingVertical:6,paddingHorizontal:8,borderRadius:8,overflow:'hidden'},
 scroll:{flex:1},content:{padding:14,paddingBottom:30,width:'100%',alignSelf:'center',gap:12},
 card:{backgroundColor:C.surface,borderWidth:1,borderColor:C.line,borderRadius:20,padding:15,gap:10},
 cardTitle:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8,flexWrap:'wrap'},
 wallet:{flexDirection:'row',backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:20,paddingHorizontal:8,paddingVertical:11,alignItems:'center'},
 walletCell:{flex:1,minWidth:0,alignItems:'center',justifyContent:'center',gap:4},
 walletEmoji:{fontSize:18},
 walletLabel:{fontSize:11,color:C.muted,textAlign:'center'},
 walletNumber:{fontSize:16,fontWeight:'900',color:C.text,textAlign:'center'},
 bigTap:{alignItems:'center',justifyContent:'center',backgroundColor:C.accentSoft,borderRadius:26,minHeight:286,padding:12,gap:8,borderWidth:1,borderColor:'#23516A'},
 portrait:{backgroundColor:'#273843',overflow:'hidden',alignItems:'center',justifyContent:'center'},
 tapCaption:{fontSize:13,color:C.accent,fontWeight:'800',letterSpacing:0.5},
 statsRow:{flexDirection:'row',justifyContent:'space-between'},
 meter:{gap:4},meters:{gap:10},
 track:{height:8,backgroundColor:'#29313B',borderRadius:8,overflow:'hidden'},fill:{height:'100%',borderRadius:8},
 pair:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:9},actions:{flexDirection:'row',gap:7,flexWrap:'wrap',alignItems:'center'},
 actionGrid:{flexDirection:'row',flexWrap:'wrap',justifyContent:'space-between',gap:12},half:{width:'47.5%',gap:4},
 button:{backgroundColor:C.accent,borderRadius:11,minHeight:40,paddingHorizontal:12,paddingVertical:10,alignItems:'center',justifyContent:'center',flexGrow:1},
 buttonSecondary:{backgroundColor:C.card,borderWidth:1,borderColor:C.line},buttonDisabled:{opacity:0.4},buttonCompact:{minHeight:36,paddingHorizontal:9,paddingVertical:7,flexGrow:0},
 buttonDanger:{borderColor:C.danger},buttonText:{fontWeight:'800',fontSize:12,textAlign:'center',color:C.bg},
 shopItem:{paddingTop:10,borderTopWidth:1,borderColor:C.line,flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8},
 shopLabel:{flex:1,gap:3},roster:{flexDirection:'row',flexWrap:'wrap',gap:8,justifyContent:'space-between'},
 rosterPup:{width:'31%',alignItems:'center',gap:4,backgroundColor:C.card,padding:7,borderRadius:13,borderWidth:1,borderColor:C.line},
 rosterSelected:{borderColor:C.accent},wager:{backgroundColor:C.card,padding:10,borderRadius:10,borderWidth:1,borderColor:C.line},wagerSelected:{borderColor:C.accent,backgroundColor:C.accentSoft},
 notice:{padding:10,paddingHorizontal:16,backgroundColor:'#234354'},
 bottomBar:{flexDirection:'row',paddingTop:8,paddingBottom:8,backgroundColor:C.surface,borderTopWidth:1,borderTopColor:C.line},
 tab:{flex:1,alignItems:'center',justifyContent:'center',paddingVertical:6,borderRadius:10,gap:3},
 tabSelected:{backgroundColor:C.accentSoft},tabEmoji:{fontSize:18},tabLabel:{fontSize:10,fontWeight:'800',color:C.muted},
 input:{borderWidth:1,borderColor:C.line,backgroundColor:C.card,borderRadius:10,padding:10,fontSize:15,color:C.text},
 back:{flexDirection:'row',alignItems:'center',gap:8,flexWrap:'wrap'}
});
