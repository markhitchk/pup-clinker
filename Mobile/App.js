import React,{useEffect,useRef,useState} from 'react';
import {Alert,Animated,Easing,Image,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {colors as C} from './src/theme.js';
import {useGame} from './src/state/useGame.js';
import {ROSTER,UPGRADES,RARITIES,PRESTIGE_SKILLS,format,level,wellness,mood,cost,boneCost,ticketCost,ticketShopCost,accessoryPrices,prestigePointsAvailable,skillCost,handValue} from './src/engine/game.js';
import {SCRATCHERS} from './src/engine/casinoV7.js';
import {ACHIEVEMENTS,achievementStatuses,guestLevel} from './src/engine/progression.js';
import {gachaPool,GACHA_TREAT_COST} from './src/engine/gacha.js';
import {cardLabel,handTotal,canHit,canStand,canDouble,canSplit} from './src/engine/blackjack.js';
import {ONBOARDING_STEPS,validBirthday} from './src/engine/onboarding.js';
import {goalsForDay,progressForGoal,localEpochDay,monthKey} from './src/engine/monthlyRewards.js';

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
 return <View style={styles.meter}><View style={styles.pair}><Txt style={styles.small}>{icon} {label}</Txt><Txt style={styles.small}>{safe}%</Txt></View><View style={styles.track}><View style={[styles.fill,{width:safe+'%',backgroundColor:safe<=20?C.danger:C.accent}]}/></View></View>;
}
function Portrait({puppy,size=228}){
 const [broken,setBroken]=useState(false);
 useEffect(()=>setBroken(false),[puppy.id]);
 return <View style={[styles.portrait,{width:size,height:size,borderRadius:size/2}]}>
  {broken?<Txt style={{fontSize:size*0.36}}>🐶</Txt>:<Image accessibilityLabel={puppy.name} source={PUPPY_IMAGES[puppy.id]||{uri:puppy.image}} resizeMode="contain" onError={()=>setBroken(true)} style={{width:'100%',height:'100%'}}/>}
 </View>;
}
// Visual structure follows V6Play/V6Wallet/V6NeedPill in the Kotlin launcher.
function PageHeader({title,subtitle,action}){
 return <View style={styles.pageHeader}><View style={{flex:1}}>
   <Txt style={styles.pageTitle}>{title}</Txt><Txt muted style={styles.pageSubtitle}>{subtitle}</Txt>
  </View>{action||null}</View>;
}
function Wallet({game}){
 return <View style={styles.wallet}>
  {[['🍪','Treats',game.treats],['🦴','Bones',game.bones],['🪙','Pup Coins',game.pupCoins],['🐾','Casino Chips',game.casinoChips]].map(([icon,label,value])=>
   <View style={styles.walletCell} key={label}><Txt style={styles.walletEmoji}>{icon}</Txt><Txt numberOfLines={1} style={styles.walletNumber}>{format(value)}</Txt><Txt numberOfLines={1} style={styles.walletLabel}>{label}</Txt></View>)}
 </View>;
}
function NeedPill({icon,value}){
 const safe=Math.min(100,Math.max(0,Number(value)||0));
 return <View style={[styles.needPill,safe<=10&&styles.needPillLow]}><Txt style={styles.needPillText}>{icon} {safe}%</Txt></View>;
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
function PlayScreen({g,act,onOpen,schedule,clock}){
 const date=new Date(clock),today=localEpochDay(date);
 const nextGoal=goalsForDay(schedule,date).find(goal=>g.dailyDay!==today||!g.claimedDailyTasks.includes(goal.id));
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const {width,height}=useWindowDimensions();
 const tapScale=useRef(new Animated.Value(1)).current;
 const gain=g.clickPower+(wellness(g)>=85&&g.bond>=50?2:wellness(g)>=70?1:0);
 const portraitSize=Math.min(height<700?198:238,Math.max(150,width-110));
 const doTap=()=>{
  if(g.energy<=10){Alert.alert('😴 '+g.puppyName+' is tired','Rest in Pup Care before tapping again.');return;}
  act({type:'tap',now:Date.now(),randoms:[Math.random(),Math.random()]});
  tapScale.stopAnimation();
  Animated.sequence([
   Animated.timing(tapScale,{toValue:0.89,duration:75,useNativeDriver:true}),
   Animated.spring(tapScale,{toValue:1,speed:20,bounciness:12,useNativeDriver:true})
  ]).start();
 };
 return <View style={styles.gamePage}>
  <PageHeader title="Puppy Clicker" subtitle={g.puppyName+' · '+mood(g)}/>
  <Wallet game={g}/>
  <View style={styles.needsRow}><NeedPill icon="💖" value={g.happiness}/><NeedPill icon="🍖" value={g.fullness}/><NeedPill icon="⚡" value={g.energy}/><NeedPill icon="🫧" value={g.cleanliness}/></View>
  <Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName+' for Treats'} onPress={doTap} style={[styles.tapStage,{minHeight:height<700?302:365}]}>
   <Animated.View style={[styles.puppyCircle,{width:portraitSize+18,height:portraitSize+18,borderRadius:(portraitSize+18)/2,transform:[{scale:tapScale}]}]}>
    <Portrait puppy={pup} size={portraitSize}/>
   </Animated.View>
   <View style={styles.tapLabel}><Txt style={styles.tapLabelText}>Tap {g.puppyName} · +{format(gain)} 🍪</Txt></View>
  </Pressable>
  <View style={styles.nextRewardCard}>
   <Txt style={{fontSize:22}}>🎁</Txt>
   <View style={{flex:1,gap:5}}>
    <View style={styles.pair}><Txt style={styles.itemName} numberOfLines={1}>{nextGoal?'Next Reward · '+nextGoal.title:'Today complete'}</Txt><Txt style={styles.small}>{nextGoal?Math.min(progressForGoal(g,nextGoal,date),nextGoal.target)+'/'+nextGoal.target:'✓'}</Txt></View>
    <View style={styles.track}><View style={[styles.fill,{backgroundColor:C.accent,width:nextGoal?Math.min(100,100*progressForGoal(g,nextGoal,date)/nextGoal.target)+'%':'100%'}]}/></View>
   </View>
   <Txt style={styles.small}>{nextGoal?format(nextGoal.rewardTreats)+' 🍪':'All claimed'}</Txt>
  </View>
  <View style={styles.menuShortcuts}>
   <QuickAction emoji="🐕" title="Puppies" subtitle="Your roster" onPress={()=>onOpen('roster')}/>
   <QuickAction emoji="🎁" title="Rewards" subtitle="Daily goals" onPress={()=>onOpen('rewards')}/>
   <QuickAction emoji="🎲" title="Gacha" subtitle="Puppy pulls" onPress={()=>onOpen('gacha')}/>
   <QuickAction emoji="🎰" title="Casino" subtitle="Mini games" onPress={()=>onOpen('casino')}/>
  </View>
 </View>;
}
function CareScreen({g,act,clock,schedule}){
 const [page,setPage]=useState('care');
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const parkSeconds=Math.max(0,Math.ceil((g.parkReadyAtMs-clock)/1000));
 const today=localEpochDay(new Date(clock));
 return <View style={styles.gamePage}>
  <PageHeader title="Pup Care" subtitle="Care, bond, daily rewards and adventures."/>
  <View style={styles.filterRow}>
   {[['care','💖 Care'],['daily','📅 Daily']].map(([id,label])=><Pressable accessibilityRole="tab" accessibilityState={{selected:page===id}} key={id} onPress={()=>setPage(id)} style={[styles.filterChip,page===id&&styles.filterChipSelected]}><Txt style={[styles.filterText,page===id&&styles.filterTextSelected]}>{label}</Txt></Pressable>)}
  </View>
  {page==='care'?<>
   <View style={styles.activePuppy}>
    <Portrait puppy={pup} size={78}/>
    <View style={{flex:1,gap:4}}><Txt style={styles.itemName}>{g.puppyName}</Txt><Txt muted style={styles.small}>{mood(g)} · Wellness {wellness(g)}% · Bond {g.bond}%</Txt></View>
   </View>
   <Card title="Puppy Needs">
    <View style={styles.meters}><Meter label="Happiness" icon="💖" value={g.happiness}/><Meter label="Fullness" icon="🍖" value={g.fullness}/><Meter label="Energy" icon="⚡" value={g.energy}/><Meter label="Cleanliness" icon="🫧" value={g.cleanliness}/><Meter label="Bond" icon="🤝" value={g.bond}/></View>
    <Txt muted style={styles.small}>{g.energy<=10?'😴 Too tired to earn Treats on Play. Rest to restore energy.':wellness(g)>=85&&g.bond>=50?'✨ Thriving care bonus: +2 Treats on every tap.':wellness(g)>=70?'✨ Healthy care bonus: +1 Treat on every tap.':'Keep wellness at 70%+ to unlock a Treat bonus on Play.'}</Txt>
   </Card>
   <Txt style={styles.sectionTitle}>Care actions</Txt>
   <View style={styles.quickActionRow}>
    <QuickAction emoji="🍖" title="Feed" subtitle="-20 🍪" disabled={g.treats<20||g.fullness>=100} onPress={()=>act({type:'care',kind:'feed'})}/>
    <QuickAction emoji="🎾" title="Play" subtitle="-1 🦴 · -12 ⚡" disabled={g.bones<1||g.energy<12||(g.happiness===100&&g.bond===100)} onPress={()=>act({type:'care',kind:'play'})}/>
    <QuickAction emoji="🫧" title="Clean" subtitle="-10 🍪" disabled={g.treats<10||g.cleanliness>=100} onPress={()=>act({type:'care',kind:'wash'})}/>
   </View>
   <View style={styles.quickActionRow}>
    <QuickAction emoji="🌙" title="Rest" subtitle="Free · +30 ⚡" disabled={g.energy>=100} onPress={()=>act({type:'care',kind:'rest'})}/>
    <QuickAction emoji="🤗" title="Cuddle" subtitle="Free · Bond" disabled={g.happiness===100&&g.bond===100} onPress={()=>act({type:'care',kind:'cuddle'})}/>
   </View>
  </>:<>
   <Card title="🎁 DAILY PUPPY GIFT" subtitle={'Claim streak · '+g.dailyStreak+' days'}>
    <Txt muted>Collect Treats, Bones and Pup Coins once per calendar day.</Txt>
    <Button disabled={g.lastDailyClaimDay===today} onPress={()=>act({type:'daily',epochDay:today})}>{g.lastDailyClaimDay===today?'✓ Already claimed today':'🎁 Claim Daily Gift'}</Button>
   </Card>
   <Txt style={styles.sectionTitle}>Today’s goals</Txt>
   {goalsForDay(schedule,new Date(clock)).map(goal=><DailyGoalCard key={goal.id} g={g} act={act} goal={goal} clock={clock}/>)}
   <Card title="🌳 DOG PARK" subtitle="A one-minute puppy adventure">
    <Txt muted>Spend 20 energy to take your puppy for a walk and earn a reward.</Txt>
    <Button disabled={g.parkActive&&parkSeconds>0} onPress={()=>act({type:g.parkActive?'parkClaim':'parkStart',now:clock})}>{g.parkActive?parkSeconds>0?'Walk finishes in '+parkSeconds+'s':'Claim Park Reward':'Start Park Walk'}</Button>
   </Card>
  </>}
 </View>;
}
// V6Shop's 3 material filter sections; the guest preview does not redeem trusted codes.
function ShopScreen({g,act,onOpen}){
 const [shopTab,setShopTab]=useState('upgrades');
 const upgrades=shopTab==='upgrades'?UPGRADES.filter(u=>!u.rarity):UPGRADES.filter(u=>u.rarity);
 return <View style={styles.gamePage}>
  <PageHeader title="Puppy Shop" subtitle="Active upgrades, rare tickets, Pup Coins and Puppy Codes."/>
  <Wallet game={g}/>
  <View style={styles.actions}>
   <Button secondary onPress={()=>Alert.alert('Puppy Codes','Code redemption requires a linked, verified Puppy Account and is not enabled in this guest preview.')}>🎫 Redeem Code</Button>
   <Button secondary onPress={()=>onOpen('roster')}>🐶 Pups</Button>
  </View>
  <View style={styles.filterRow}>
   {[['upgrades','🍪 Upgrades'],['tickets','🎟️ Tickets'],['coins','🪙 Pup Coins']].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:shopTab===id}} style={[styles.filterChip,shopTab===id&&styles.filterChipSelected]} onPress={()=>setShopTab(id)}><Txt style={[styles.filterText,shopTab===id&&styles.filterTextSelected]} numberOfLines={1}>{label}</Txt></Pressable>)}
  </View>
  {shopTab==='coins'?<>
   <Card title="🪙 PUP COIN SHOP"><Txt muted>Use earned Pup Coins for tickets and accessories.</Txt>
    {RARITIES.map(r=><View key={r} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{r} Ticket</Txt><Txt muted style={styles.small}>Owned: {g.ticketInventory[r]||0}</Txt></View><Button secondary compact disabled={g.pupCoins<ticketShopCost(r,g.ticketShopPurchases[r]||0)} onPress={()=>act({type:'ticket',rarity:r})}>{format(ticketShopCost(r,g.ticketShopPurchases[r]||0))} 🪙</Button></View>)}
   </Card>
   <Card title="🎀 PUPPY ACCESSORIES">
    {['None',...Object.keys(accessoryPrices)].map(name=><View key={name} style={styles.shopItem}><Txt style={styles.itemName}>{name} {g.accessory===name?'✓':''}</Txt><Button secondary compact onPress={()=>act({type:g.ownedAccessories.includes(name)?'equip':'accessory',name})}>{g.ownedAccessories.includes(name)?'Equip':format(accessoryPrices[name])+' 🪙'}</Button></View>)}
   </Card>
  </>:<>
   {shopTab==='tickets'?<Card title="🎟️ TICKET INVENTORY">
     {RARITIES.map(r=><Pair key={r} left={r} right={g.ticketInventory[r]||0}/>)}
    </Card>:null}
   {upgrades.map(u=><View key={u.id} style={styles.upgradeCard}>
    <View style={styles.shopLabel}><Txt style={styles.itemName}>{u.emoji} {u.name}</Txt><Txt muted style={styles.small}>{u.description}</Txt><Txt muted style={styles.small}>Level {g.upgrades[u.id]||0}{u.rarity?' · '+u.rarity+' · '+ticketCost(g,u)+' tickets':''}</Txt></View>
    <Button compact disabled={g.treats<cost(g,u)||g.bones<boneCost(u)||(u.rarity&&(g.ticketInventory[u.rarity]||0)<ticketCost(g,u))} onPress={()=>act({type:'purchase',upgrade:u})}>{format(cost(g,u))} 🍪 + {boneCost(u)} 🦴</Button>
   </View>)}
  </>}
 </View>;
}
function PrestigeScreen({g,act}){
 const points=prestigePointsAvailable(g);
 const remaining=Math.max(0,50000-g.lifetimeTreats);
 const skills={TAP_TRAINING:['🐾','Tap Training','Boosts purchased tap upgrades.'],AUTO_TRAINING:['⚡','Bonus Training','Boosts purchased active 10-tap upgrades.'],SMART_SHOPPER:['🛍️','Smart Shopper','Reduces Shop upgrade prices.'],TICKET_SENSE:['🎟️','Ticket Sense','Reduces matching ticket costs.']};
 return <View style={styles.gamePage}>
  <PageHeader title="Prestige" subtitle="Permanent progression and skill points."/>
  <View style={styles.prestigeCenter}>
   <View style={styles.prestigeTitle}><Txt style={{fontSize:32}}>⭐</Txt><View style={{flex:1}}><Txt style={styles.heading}>Prestige Center</Txt><Txt muted>Prestige {g.prestigeCount} · {g.skillPoints} skill points available</Txt></View></View>
   {points? <Txt style={styles.itemName}>This run can prestige now for +{points} skill point{points===1?'':'s'}.</Txt>:<>
    <Txt muted>Earn {format(remaining)} more lifetime Treats to unlock Prestige.</Txt>
    <View style={styles.track}><View style={[styles.fill,{width:Math.min(100,g.lifetimeTreats/50000*100)+'%',backgroundColor:C.accent}]}/></View>
   </>}
   <Button disabled={!points} onPress={()=>Alert.alert('⭐ Prestige this run?','Current Treats, upgrades, tickets and run stats reset; unlocked puppies and permanent skills stay.',[{text:'Cancel',style:'cancel'},{text:'Prestige',onPress:()=>act({type:'prestige'})}])}>{points?'Prestige for +'+points+' SP':'Prestige locked'}</Button>
  </View>
  <Txt style={styles.sectionTitle}>Permanent skill tree</Txt>
  <Txt muted>Skills improve Shop upgrades; they do not give free tap power by themselves.</Txt>
  {PRESTIGE_SKILLS.map(skill=>{
   const [emoji,name,description]=skills[skill];
   const lvl=g.prestigeSkills[skill]||0,price=skillCost(g,skill),maxed=lvl>=5;
   return <View style={styles.upgradeCard} key={skill}>
    <Txt style={{fontSize:25}}>{emoji}</Txt>
    <View style={styles.shopLabel}><Txt style={styles.itemName}>{name}</Txt><Txt muted style={styles.small}>Level {lvl} / 5</Txt><Txt muted style={styles.small}>{description}</Txt></View>
    <Button compact disabled={maxed||g.skillPoints<price} onPress={()=>act({type:'skill',skill})}>{maxed?'MAX':price+' SP'}</Button>
   </View>;
  })}
 </View>;
}
function RosterScreen({g,act}){
 const [search,setSearch]=useState('');
 const [status,setStatus]=useState('all');
 const [favoritesOnly,setFavoritesOnly]=useState(false);
 const [category,setCategory]=useState('all');
 const [sort,setSort]=useState('unlocked');
 const [inspected,setInspected]=useState(g.puppyStyle);
 const selected=ROSTER.find(p=>p.id===inspected)||ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const selectedOwned=g.unlockedPuppies.includes(selected.id);
 const selectedFavorite=g.favoritePuppies.includes(selected.id);
 const matches=ROSTER.filter(p=>{
  const owned=g.unlockedPuppies.includes(p.id);
  return (status==='all'||(status==='unlocked'&&owned)||(status==='locked'&&!owned))&&
   (!favoritesOnly||g.favoritePuppies.includes(p.id))&&
   (category==='all'||(category==='v2'?p.id.startsWith('v2_'):!p.id.startsWith('v2_')))&&
   (p.name.toLowerCase().includes(search.toLowerCase().trim())||p.id.includes(search.toLowerCase().trim()));
 }).sort((a,b)=>sort==='name'?a.name.localeCompare(b.name):sort==='number'?a.number-b.number:Number(g.unlockedPuppies.includes(b.id))-Number(g.unlockedPuppies.includes(a.id)));
 const reset=()=>{setSearch('');setStatus('all');setCategory('all');setFavoritesOnly(false);setSort('unlocked');};
 return <View style={styles.gamePage}>
  <PageHeader title="Puppy Roster" subtitle="Browse your available and locked puppy artwork."/>
  <View style={styles.filterRow}>{[['all','All'],['unlocked','Unlocked'],['locked','Locked']].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:status===id}} onPress={()=>setStatus(id)} style={[styles.filterChip,status===id&&styles.filterChipSelected]}><Txt style={[styles.filterText,status===id&&styles.filterTextSelected]}>{label}</Txt></Pressable>)}</View>
  <TextInput accessibilityLabel="Search puppies or ID" placeholder="🔍 Search puppies or ID" placeholderTextColor={C.muted} style={styles.input} maxLength={64} value={search} onChangeText={setSearch}/>
  <View style={styles.actions}>
   <Button secondary compact onPress={()=>setSort(sort==='unlocked'?'name':sort==='name'?'number':'unlocked')}>Sort: {sort==='unlocked'?'Unlocked first':sort==='name'?'Name':'Number'}</Button>
   <Button compact secondary={!favoritesOnly} onPress={()=>setFavoritesOnly(!favoritesOnly)}>{favoritesOnly?'★':'☆'} Favorites</Button>
  </View>
  <View style={styles.filterRow}>{[['all','All Categories'],['v1','V1 Puppies'],['v2','V2 & Discord']].map(([id,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:category===id}} onPress={()=>setCategory(id)} style={[styles.filterChip,category===id&&styles.filterChipSelected]}><Txt numberOfLines={1} style={[styles.filterText,category===id&&styles.filterTextSelected]}>{label}</Txt></Pressable>)}</View>
  <Txt muted style={styles.small}>{matches.length} shown · {g.unlockedPuppies.length} unlocked</Txt>
  <View style={styles.selectedPuppyPanel}>
   <Portrait puppy={selected} size={108}/>
   <View style={{flex:1,gap:7}}>
    <Txt style={styles.heading}>{selected.name}</Txt>
    <Txt muted style={styles.small}>{selected.id} · {selectedOwned?'Unlocked':'Locked'}</Txt>
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
function GachaScreen({g,act,state}){
 const rotation=useRef(new Animated.Value(0)).current;
 const [revealed,setRevealed]=useState(false);
 const result=state.gachaResult;
 useEffect(()=>{
  rotation.stopAnimation();rotation.setValue(0);setRevealed(false);
  if(!result?.success)return;
  const animation=Animated.timing(rotation,{toValue:1,duration:780,easing:Easing.out(Easing.cubic),useNativeDriver:true});
  animation.start(({finished})=>{if(finished)setRevealed(true);});
  return ()=>animation.stop();
 },[result,rotation]);
 const pool=gachaPool(ROSTER,g.unlockedPuppies);
 return <>
  <Card title="🎲 PUPPY GACHA" subtitle="Source-matched capsule machine">
   <Pair left="🍪 Treats" right={format(g.treats)}/>
   <Pair left="🎟️ Common Tickets" right={String(g.ticketInventory.COMMON||0)}/>
   <Pair left="Total pulls" right={String(g.gachaPulls||0)}/>
   <Txt muted>Random Gacha only includes free/unrestricted puppies. Developer, Discord, special-code and event puppies cannot be granted by this machine.</Txt>
   <View style={styles.bigTap}>
    {result?.success?<Animated.View style={{transform:[{rotate:rotation.interpolate({inputRange:[0,1],outputRange:['0deg','360deg']})}]}}><Txt style={{fontSize:78}}>🎁</Txt></Animated.View>:<Txt style={{fontSize:88}}>🎰</Txt>}
    <Txt style={styles.tapCaption}>{result?revealed?'CAPSULE OPENED':'OPENING CAPSULE…':'PULL A PUPPY CAPSULE'}</Txt>
   </View>
   {result?.success?revealed?<View style={styles.card}>
      <Txt style={{fontSize:22,fontWeight:'800',textAlign:'center'}}>{result.isNewUnlock?'🐶 NEW PUPPY!':'🐶 DUPLICATE PUPPY'}</Txt>
      <Txt style={{textAlign:'center',fontWeight:'700'}}>{result.puppyName}</Txt>
      <View style={styles.actions}><Button compact disabled={!result.isNewUnlock} onPress={()=>{act({type:'switch',id:result.puppyId,now:Date.now()});act({type:'dismissGacha'});}}>Use Puppy</Button><Button compact secondary onPress={()=>act({type:'dismissGacha'})}>Pull Again</Button></View>
     </View>:null:<>
      {pool.length&&pool.every(p=>g.unlockedPuppies.includes(p.id))?<Txt style={styles.small} muted>All available free puppies are already owned. Another pull awards a duplicate but still costs currency, just as in the Android rules.</Txt>:null}
      <View style={styles.actions}>
       <Button disabled={g.treats<GACHA_TREAT_COST||!pool.length} onPress={()=>act({type:'gacha',payment:'TREATS',roll:Math.floor(Math.random()*0x7fffffff)})}>🍪 {format(GACHA_TREAT_COST)} Treats</Button>
       <Button secondary disabled={(g.ticketInventory.COMMON||0)<1||!pool.length} onPress={()=>act({type:'gacha',payment:'COMMON_TICKET',roll:Math.floor(Math.random()*0x7fffffff)})}>🎟️ 1 Common Ticket</Button>
      </View>
     </>}
  </Card>
 </>;
}
function RewardsScreen({g,act,clock,schedule,onOpen,onPrestige}){
 const date=new Date(clock),today=localEpochDay(date),goals=goalsForDay(schedule,date);
 const claimed=(goal)=>g.dailyDay===today&&g.claimedDailyTasks.includes(goal.id);
 const active=goals.filter(goal=>!claimed(goal)),completed=goals.filter(claimed);
 const giftClaimed=g.lastDailyClaimDay===today;
 const nextStreak=g.lastDailyClaimDay===today-1?Math.min(365,g.dailyStreak+1):1;
 const giftReward=150+nextStreak*25+guestLevel(g)*10;
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
function CasinoScreen({g,act,state,bet,setBet}){
 const wagers=[20,50,100,500,1000].map(w=>w);
 const casino=(gameType,extra={})=>act({type:'casino',gameType,wager:bet,...extra});
 return <>
  <Card title="🎰 PUPPY CASINO" subtitle={'🐾 '+format(g.casinoChips)+' Chips'}>
   <Txt muted>Casino Chips are earned through one-way Treat conversion. No real-money betting.</Txt>
   <View style={styles.actions}><Button secondary compact onPress={()=>act({type:'exchange',amount:100})}>100 🍪 → 10 🐾</Button><Button secondary compact onPress={()=>act({type:'exchange',amount:1000})}>1,000 🍪 → 100 🐾</Button></View>
   <Txt style={styles.itemName}>Chip wager</Txt>
   <View style={styles.actions}>{wagers.map(w=><Pressable key={w} accessibilityRole="button" onPress={()=>setBet(w)} style={[styles.wager,bet===w&&styles.wagerSelected]}><Txt>{w}</Txt></Pressable>)}</View>
  </Card>
  <Card title="🎰 SLOTS"><Txt muted>Three-reel weighted reels and triple-symbol prizes.</Txt><Button onPress={()=>casino('slots',{rolls:[roll(),roll(),roll()]})}>Spin Slots · {bet} Chips</Button></Card>
  <Card title="🎡 ROULETTE"><View style={styles.actions}><Button compact onPress={()=>casino('roulette',{bet:'RED',roll:number()})}>🔴 Red</Button><Button secondary compact onPress={()=>casino('roulette',{bet:'BLACK',roll:number()})}>⚫ Black</Button><Button secondary compact onPress={()=>casino('roulette',{bet:'EVEN',roll:number()})}>Even</Button></View></Card>
  <Card title="🎟️ SCRATCHERS" subtitle={SCRATCHERS.length+' card tiers'}>
   <Txt muted>Select the source-defined ticket. Each card has its own Chip wager and published payout table.</Txt>
   {SCRATCHERS.map(card=><View key={card.id} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{card.emoji} {card.name}</Txt><Txt muted style={styles.small}>Fixed wager: {format(card.wager)} Chips</Txt></View><Button compact disabled={g.casinoChips<card.wager||Boolean(state.blackjack)} onPress={()=>act({type:'casino',gameType:'scratch',wager:card.wager,roll:roll()})}>Scratch</Button></View>)}
  </Card>
  <Card title="🔵 PLINKO"><Button onPress={()=>casino('plinko',{path:Array.from({length:8},()=>Math.random()<0.5)})}>Drop Ball · {bet} Chips</Button></Card>
  <Card title="🎠 LUCKY PUP WHEEL"><Button onPress={()=>casino('wheel',{roll:roll()})}>Spin Wheel · {bet} Chips</Button></Card>
  <Card title="🃏 BLACKJACK" subtitle="52-card deck · up to 3 player hands">
   {state.blackjack?<>
    <Pair left="Dealer" right={cardLabel(state.blackjack.dealerCards[0])+' · '+(state.blackjack.complete?state.blackjack.dealerCards.slice(1).map(cardLabel).join(' · '):'🂠')}/>
    <Pair left="Current hand" right={(state.blackjack.activeHandIndex+1)+' / '+state.blackjack.hands.length}/>
    {state.blackjack.hands.map((hand,i)=><View key={i} style={[styles.shopItem,i===state.blackjack.activeHandIndex&&{backgroundColor:C.accentSoft,padding:8,borderRadius:10}]}>
      <View style={styles.shopLabel}><Txt style={styles.itemName}>Hand {i+1} {i===state.blackjack.activeHandIndex?'▶️':''}</Txt><Txt>{hand.cards.map(cardLabel).join(' · ')} — {handTotal(hand.cards).total}</Txt></View>
      <Txt muted>{format(hand.wager)} Chips {hand.doubled?'· Doubled':''} {hand.fromSplit?'· Split':''}</Txt>
     </View>)}
    <View style={styles.actions}>
     <Button disabled={!canHit(state.blackjack)} onPress={()=>act({type:'blackjackHit'})}>Hit</Button>
     <Button secondary disabled={!canStand(state.blackjack)} onPress={()=>act({type:'blackjackStand'})}>Stand</Button>
     <Button secondary disabled={!canDouble(state.blackjack)||g.casinoChips<state.blackjack.hands[state.blackjack.activeHandIndex].wager} onPress={()=>act({type:'blackjackDouble'})}>Double</Button>
     <Button secondary disabled={!canSplit(state.blackjack)||g.casinoChips<state.blackjack.hands[state.blackjack.activeHandIndex].wager} onPress={()=>act({type:'blackjackSplit'})}>Split</Button>
    </View>
   </>:<>
    <Txt muted>A single shuffled 52-card deck, natural Blackjack 3:2 payout, dealer stands on 17, up to three split hands. Chips are deducted before the round and the active game survives guest-save reloads.</Txt>
    <Button disabled={g.casinoChips<bet} onPress={()=>act({type:'blackjackStart',wager:bet,deck:shuffledBlackjackDeck()})}>Deal Blackjack · {bet} Chips</Button>
   </>}
  </Card>
 </>;
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
function SettingsScreen({g,act,onOpen}){
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
  {section('gameplay','🎮','Gameplay','Interaction, feedback and gameplay preferences',<><Pair left="Puppy" right={g.puppyName}/><Pair left="Tap power" right={format(g.clickPower)}/><Pair left="Total taps" right={format(g.totalTaps)}/></>)}
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
  {section('assets','☁','Online Assets','Streamed asset, roster and cache status',<Txt muted>Bundled V1/V2 puppy art is available offline. Dynamic V3+ manifests are not enabled.</Txt>)}
  {section('release-hub','↻','Release Hub',"What's New, installed build and updates",<Txt muted>React Native preview 1.8.0. Install updates through the separate preview GitHub Actions build.</Txt>)}
  {section('about','ⓘ','About Puppy Clicker','Version, development and legal',<>
   <Pair left="Developer" right="Harley's Studios"/><Pair left="React engine" right="React Native 0.86"/><Pair left="App ID" right="com.harleytg.puppyclicker.preview"/><Txt muted>Preview build, not a replacement for the Google Play Kotlin release.</Txt>
  </>)}
 </View>;
}

export default function App(){
 const {state,act}=useGame();
 const {width}=useWindowDimensions();
 const [tab,setTab]=useState('play');
 const [secondary,setSecondary]=useState(null);
 const [bet,setBet]=useState(20);
 const [clock,setClock]=useState(Date.now());
 useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000);return ()=>clearInterval(timer);},[]);
 const g=state.game;
 const main=()=>{
  if(secondary==='roster')return <RosterScreen g={g} act={act}/>;
  if(secondary==='rewards')return <RewardsScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule} onOpen={setSecondary} onPrestige={()=>{setTab('prestige');setSecondary(null);}}/>;
  if(secondary==='gacha')return <GachaScreen g={g} act={act} state={state}/>;
  if(secondary==='casino')return <CasinoScreen g={g} act={act} bet={bet} setBet={setBet} state={state}/>;
  if(tab==='play')return <PlayScreen g={g} act={act} onOpen={setSecondary} schedule={state.monthlySchedule} clock={clock}/>;
  if(tab==='care')return <CareScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule}/>;
  if(tab==='shop')return <ShopScreen g={g} act={act} onOpen={setSecondary}/>;
  if(tab==='prestige')return <PrestigeScreen g={g} act={act}/>;
  return <SettingsScreen g={g} act={act} onOpen={setSecondary}/>;
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

 // Kotlin V6 source: PuppyClickerV6Activity.kt and PuppyMainScreensRevamp.kt.
 gamePage:{gap:10},
 pageHeader:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingBottom:2},
 pageTitle:{fontSize:27,fontWeight:'900',letterSpacing:-0.5,color:C.text},
 pageSubtitle:{fontSize:14,color:C.muted,marginTop:2},
 needsRow:{flexDirection:'row',gap:6},
 needPill:{flex:1,minWidth:0,backgroundColor:C.surface,borderRadius:100,alignItems:'center',paddingVertical:9,paddingHorizontal:2},
 needPillLow:{backgroundColor:'#431F26'},
 needPillText:{fontSize:12,fontWeight:'800',color:C.text},
 tapStage:{backgroundColor:'#102027',borderRadius:30,alignItems:'center',justifyContent:'center',paddingHorizontal:12,paddingTop:12,paddingBottom:58,overflow:'hidden'},
 puppyCircle:{backgroundColor:C.surface,alignItems:'center',justifyContent:'center',overflow:'hidden'},
 tapLabel:{position:'absolute',bottom:14,alignSelf:'center',backgroundColor:C.surface,borderRadius:18,paddingHorizontal:14,paddingVertical:9,maxWidth:'95%'},
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
 wallet:{flexDirection:'row',backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:20,paddingHorizontal:5,paddingVertical:12,alignItems:'center'},
 walletCell:{flex:1,minWidth:0,alignItems:'center',justifyContent:'center',gap:4},
 walletEmoji:{fontSize:23},
 walletLabel:{fontSize:10,color:C.muted,textAlign:'center'},
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
