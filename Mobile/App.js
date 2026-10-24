import React,{useEffect,useMemo,useState} from 'react';
import {Alert,Image,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {colors as C} from './src/theme.js';
import {useGame} from './src/state/useGame.js';
import {ROSTER,UPGRADES,RARITIES,PRESTIGE_SKILLS,format,level,wellness,mood,cost,boneCost,ticketCost,ticketShopCost,accessoryPrices,prestigePointsAvailable,skillCost,handValue} from './src/engine/game.js';
import {SCRATCHERS} from './src/engine/casinoV7.js';

const TABS=[['play','🐾','Play'],['care','💖','Care'],['shop','🛍️','Shop'],['prestige','⭐','Prestige'],['settings','⚙️','Settings']];
const skillNames={TAP_TRAINING:'Tap Training',AUTO_TRAINING:'Bonus Training',SMART_SHOPPER:'Smart Shopper',TICKET_SENSE:'Ticket Sense'};
const die=()=>1+Math.floor(Math.random()*13);
const roll=()=>Math.floor(Math.random()*100);
const number=()=>Math.floor(Math.random()*37);
const numberLabel=n=>n===1?'A':n===11?'J':n===12?'Q':n===13?'K':String(n);
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
  {broken?<Txt style={{fontSize:size*0.36}}>🐶</Txt>:<Image accessibilityLabel={puppy.name} source={{uri:puppy.image}} resizeMode="contain" onError={()=>setBroken(true)} style={{width:'100%',height:'100%'}}/>}
 </View>;
}
function Wallet({game}){
 return <View style={styles.wallet}>
  {[['🍪','Treats',game.treats],['🦴','Bones',game.bones],['🪙','Pup Coins',game.pupCoins],['🐾','Chips',game.casinoChips]].map(([icon,label,value])=><View style={styles.walletCell} key={label}><Txt style={styles.walletLabel}>{icon} {label}</Txt><Txt style={styles.walletNumber}>{format(value)}</Txt></View>)}
 </View>;
}
function PlayScreen({g,act,onOpen}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const gain=g.clickPower+(wellness(g)>=85&&g.bond>=50?2:wellness(g)>=70?1:0);
 return <>
  <Card title={'🐶 '+g.puppyName} subtitle={'Lv. '+level(g)+' · '+mood(g)}>
   <View style={styles.statsRow}><Txt muted>⚡ Per tap: {format(gain)}</Txt><Txt muted>🔥 Combo: {g.combo}</Txt></View>
   <Pressable accessibilityRole="button" accessibilityLabel="Tap your puppy for Treats" onPress={()=>act({type:'tap',now:Date.now(),randoms:[Math.random(),Math.random()]})} style={({pressed})=>[styles.bigTap,pressed&&{transform:[{scale:0.97}]}]}>
    <Portrait puppy={pup} size={220}/>
    <Txt style={styles.tapCaption}>TAP TO EARN TREATS  🐾</Txt>
   </Pressable>
   <Txt muted style={styles.center}>{g.totalTaps} taps · +{g.activeBonus} every 10 active taps</Txt>
  </Card>
  <Card title="YOUR PUPPY">
   <View style={styles.meters}><Meter label="Happiness" icon="💖" value={g.happiness}/><Meter label="Fullness" icon="🍖" value={g.fullness}/><Meter label="Energy" icon="⚡" value={g.energy}/><Meter label="Clean" icon="🫧" value={g.cleanliness}/></View>
   <View style={styles.actions}><Button onPress={()=>onOpen('roster')} secondary compact>🐕 Roster</Button><Button onPress={()=>onOpen('casino')} secondary compact>🎰 Casino</Button><Button onPress={()=>onOpen('rewards')} compact>🎁 Rewards</Button></View>
  </Card>
 </>;
}
function CareScreen({g,act,clock}){
 const parkSeconds=Math.max(0,Math.ceil((g.parkReadyAtMs-clock)/1000));
 return <>
  <Card title="💖 PUPPY CARE" subtitle={'Wellness '+wellness(g)+'% · Bond '+g.bond+'/100'}>
   <View style={styles.meters}><Meter label="Happiness" icon="💖" value={g.happiness}/><Meter label="Fullness" icon="🍖" value={g.fullness}/><Meter label="Energy" icon="⚡" value={g.energy}/><Meter label="Cleanliness" icon="🫧" value={g.cleanliness}/></View>
   <View style={styles.actionGrid}>
    {[
      ['🍖 Feed','feed','20 Treats'],['🎾 Play','play','1 Bone'],['🧼 Wash','wash','10 Treats'],
      ['💤 Rest','rest','Free'],['🤗 Cuddle','cuddle','Free']
    ].map(([label,kind,price])=><View key={kind} style={styles.half}><Button onPress={()=>act({type:'care',kind})} secondary>{label}</Button><Txt muted style={styles.tinyCenter}>{price}</Txt></View>)}
   </View>
  </Card>
  <Card title="🌳 DOG PARK" subtitle="A one-minute activity">
   <Txt muted>Spend 20 energy to take your puppy for a walk and earn a reward.</Txt>
   <Button disabled={g.parkActive&&parkSeconds>0} onPress={()=>act({type:g.parkActive?'parkClaim':'parkStart',now:clock})}>{g.parkActive?parkSeconds>0?'Walk finishes in '+parkSeconds+'s':'Claim Park Reward':'Start Park Walk'}</Button>
  </Card>
  <Card title="🎁 DAILY REWARD">
   <Txt muted>Collect Treats, Bones and Pup Coins once per calendar day.</Txt>
   <Button onPress={()=>act({type:'daily',epochDay:Math.floor(clock/86400000)})} disabled={g.lastDailyClaimDay===Math.floor(clock/86400000)}>Claim Daily · Streak {g.dailyStreak} days</Button>
  </Card>
 </>;
}
function ShopScreen({g,act}){
 return <>
  <Card title="🛍️ TREAT UPGRADES" subtitle="Tap boosts and 10-tap active bonuses">
   {UPGRADES.map(u=><View key={u.id} style={styles.shopItem}>
     <View style={styles.shopLabel}><Txt style={styles.itemName}>{u.emoji} {u.name}  Lv.{g.upgrades[u.id]||0}</Txt><Txt muted style={styles.small}>{u.description}</Txt>
      {u.rarity?<Txt style={styles.tiny}>{u.rarity} · {ticketCost(g,u)} matching tickets</Txt>:null}</View>
     <Button compact onPress={()=>act({type:'purchase',upgrade:u})} disabled={g.treats<cost(g,u)||g.bones<boneCost(u)||(u.rarity&&(g.ticketInventory[u.rarity]||0)<ticketCost(g,u))}>{format(cost(g,u))} 🍪 + {boneCost(u)} 🦴</Button>
   </View>)}
  </Card>
  <Card title="🎟️ UPGRADE TICKETS" subtitle={'Pup Coins: '+format(g.pupCoins)}>
   {RARITIES.map(r=><View key={r} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{r}</Txt><Txt muted style={styles.small}>Owned: {g.ticketInventory[r]||0}</Txt></View><Button secondary compact disabled={g.pupCoins<ticketShopCost(r,g.ticketShopPurchases[r]||0)} onPress={()=>act({type:'ticket',rarity:r})}>{format(ticketShopCost(r,g.ticketShopPurchases[r]||0))} 🪙</Button></View>)}
  </Card>
  <Card title="🎀 PUPPY ACCESSORIES">
   {['None',...Object.keys(accessoryPrices)].map(name=><View key={name} style={styles.shopItem}><Txt>{name} {g.accessory===name?'✓':''}</Txt><Button secondary compact onPress={()=>act({type:g.ownedAccessories.includes(name)?'equip':'accessory',name})}>{g.ownedAccessories.includes(name)?'Equip':format(accessoryPrices[name])+' 🪙'}</Button></View>)}
  </Card>
 </>;
}
function PrestigeScreen({g,act}){
 const points=prestigePointsAvailable(g);
 return <>
  <Card title="⭐ PRESTIGE CENTER" subtitle={'Prestiged '+g.prestigeCount+' times'}>
   <Pair left="Lifetime Treats" right={format(g.lifetimeTreats)}/>
   <Pair left="Available on prestige" right={points+' Skill Points'}/>
   <Pair left="Unspent Skill Points" right={String(g.skillPoints)}/>
   <Txt muted style={styles.small}>Requires 50,000 lifetime Treats. Resets Treats, Bones and temporary upgrades. Permanent skills and unlocked puppies remain.</Txt>
   <Button disabled={!points} onPress={()=>Alert.alert('Confirm Prestige','Reset your current run for '+points+' Skill Points?', [{text:'Cancel',style:'cancel'},{text:'Prestige',onPress:()=>act({type:'prestige'})}])}>⭐ Prestige Now</Button>
  </Card>
  <Card title="🌟 PERMANENT SKILLS">
   {PRESTIGE_SKILLS.map(skill=><View key={skill} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{skillNames[skill]}</Txt><Txt muted style={styles.small}>Level {g.prestigeSkills[skill]}/5</Txt></View><Button compact secondary disabled={g.skillPoints<skillCost(g,skill)||g.prestigeSkills[skill]>=5} onPress={()=>act({type:'skill',skill})}>{skillCost(g,skill)} ⭐</Button></View>)}
  </Card>
 </>;
}
function RosterScreen({g,act}){
 return <Card title="🐕 PUPPY ROSTER" subtitle={(g.unlockedPuppies.length)+' unlocked'}>
  <View style={styles.roster}>
   {ROSTER.map(p=>{
    const unlocked=g.unlockedPuppies.includes(p.id);
    return <Pressable key={p.id} accessibilityRole="button" disabled={!unlocked} onPress={()=>act({type:'switch',id:p.id})} style={[styles.rosterPup,g.puppyStyle===p.id&&styles.rosterSelected,!unlocked&&{opacity:0.38}]}>
      <Portrait puppy={p} size={86}/><Txt numberOfLines={1} style={styles.small}>{p.name}</Txt><Txt muted style={styles.tiny}>{g.puppyStyle===p.id?'SELECTED':unlocked?'TAP TO USE':'LOCKED'}</Txt>
    </Pressable>;
   })}
  </View>
 </Card>;
}
function RewardsScreen({g,act,clock}){
 return <>
  <Card title="🎁 REWARDS">
   <Pair left="Daily streak" right={String(g.dailyStreak)}/>
   <Pair left="Tickets found" right={String(g.totalTicketsFound)}/>
   <Pair left="Lifetime Treats" right={format(g.lifetimeTreats)}/>
   <Pair left="Best combo" right={String(g.bestCombo)}/>
   <Pair left="Care actions" right={String(g.careActions)}/>
   <Button disabled={g.lastDailyClaimDay===Math.floor(clock/86400000)} onPress={()=>act({type:'daily',epochDay:Math.floor(clock/86400000)})}>🎁 Claim Daily Reward</Button>
  </Card>
  <Card title="🏆 ACHIEVEMENTS — MIGRATION">
   <Txt muted>Full Android achievements, XP and reward receipts still require their source-native progression and server verification before release.</Txt>
  </Card>
 </>;
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
  <Card title="🎟️ SCRATCHERS"><Txt muted>{SCRATCHERS.length} source-derived scratcher reward tiers. Choose a wager matching its card.</Txt><Button onPress={()=>casino('scratch',{roll:roll()})}>Scratch Ticket · {bet} Chips</Button></Card>
  <Card title="🔵 PLINKO"><Button onPress={()=>casino('plinko',{path:Array.from({length:8},()=>Math.random()<0.5)})}>Drop Ball · {bet} Chips</Button></Card>
  <Card title="🎠 LUCKY PUP WHEEL"><Button onPress={()=>casino('wheel',{roll:roll()})}>Spin Wheel · {bet} Chips</Button></Card>
  <Card title="🃏 BLACKJACK">
   {state.blackjack?<><Pair left="Dealer showing" right={numberLabel(state.blackjack.dealer[0])+' + ?'}/><Pair left="Your hand" right={state.blackjack.player.map(numberLabel).join(' · ')+' = '+handValue(state.blackjack.player)}/><View style={styles.actions}><Button onPress={()=>act({type:'blackjackHit',card:die()})}>Hit</Button><Button secondary onPress={()=>act({type:'blackjackStand',dealerCards:Array.from({length:14},die)})}>Stand</Button></View></>:<>
    <Txt muted>Basic Hit/Stand guest gameplay. Splitting, insurance and native table animations remain to be ported.</Txt>
    <Button onPress={()=>act({type:'blackjackStart',wager:bet,cards:[die(),die(),die(),die()]})}>Deal Blackjack · {bet} Chips</Button>
   </>}
  </Card>
 </>;
}
function SettingsScreen({g,act}){
 const [editing,setEditing]=useState(g.puppyName);
 useEffect(()=>setEditing(g.puppyName),[g.puppyName]);
 return <>
  <Card title="⚙️ SETTINGS">
   <Txt style={styles.itemName}>Puppy name</Txt>
   <TextInput style={styles.input} placeholder="Puppy name" placeholderTextColor={C.muted} value={editing} onChangeText={setEditing} maxLength={18}/>
   <Button compact onPress={()=>act({type:'rename',name:editing})}>Save Name</Button>
  </Card>
  <Card title="🔒 ACCOUNT & CLOUD">
   <Txt style={styles.itemName}>LOCAL GUEST PREVIEW</Txt>
   <Txt muted>This new React Native project is not yet connected to the original Kotlin app's authorized Supabase account or PupEye enforcement. Guest progress stays on this device and is NOT an official Android or web account save.</Txt>
   <Txt style={styles.small}>Discord account linking, account recovery, server-authoritative saves, migration of the original installed app and notifications are not available in this preview.</Txt>
  </Card>
  <Card title="📱 ABOUT">
   <Pair left="App engine" right="React Native"/>
   <Pair left="React edition" right="1.8.0 migration"/>
   <Pair left="Android ID" right="com.harleytg.puppyclicker"/>
   <Pair left="Tap count" right={format(g.totalTaps)}/>
   <Button secondary danger onPress={()=>Alert.alert('Reset Local Guest','Delete this React Native guest profile? This does not affect original Android or cloud saves.',[{text:'Cancel',style:'cancel'},{text:'Reset Guest',style:'destructive',onPress:()=>act({type:'clearGuest'})}])}>Reset Local Guest Progress</Button>
  </Card>
 </>;
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
  if(secondary==='rewards')return <RewardsScreen g={g} act={act} clock={clock}/>;
  if(secondary==='casino')return <CasinoScreen g={g} act={act} bet={bet} setBet={setBet} state={state}/>;
  if(tab==='play')return <PlayScreen g={g} act={act} onOpen={setSecondary}/>;
  if(tab==='care')return <CareScreen g={g} act={act} clock={clock}/>;
  if(tab==='shop')return <ShopScreen g={g} act={act}/>;
  if(tab==='prestige')return <PrestigeScreen g={g} act={act}/>;
  return <SettingsScreen g={g} act={act}/>;
 };
 return <View style={styles.root}>
  <StatusBar barStyle="light-content" backgroundColor={C.bg}/>
  <View style={styles.topBar}>
   <View style={styles.topTitles}><Txt style={styles.brand}>🐶 PUPPY CLICKER</Txt><Txt muted style={styles.tiny}>REACT NATIVE · ANDROID MIGRATION</Txt></View>
   <Txt style={styles.guestTag}>GUEST PREVIEW</Txt>
  </View>
  {state.notice?<Pressable accessibilityRole="button" onPress={()=>act({type:'dismiss'})} style={styles.notice}><Txt style={styles.small}>{state.notice}  ✕</Txt></Pressable>:null}
  <ScrollView key={secondary||tab} style={styles.scroll} contentContainerStyle={[styles.content,{maxWidth:width>650?670:undefined}]} keyboardShouldPersistTaps="handled">
   {secondary?<View style={styles.back}><Button compact secondary onPress={()=>setSecondary(null)}>← Back to {TABS.find(x=>x[0]===tab)?.[2]}</Button><Txt style={styles.heading}>{secondary.toUpperCase()}</Txt></View>:null}
   {!state.ready?<Card title="Loading local guest data…"><Txt muted>Reading device cache.</Txt></Card>:<>{!secondary?<Wallet game={g}/>:null}{main()}</>}
  </ScrollView>
  <View style={styles.bottomBar}>
   {TABS.map(([id,icon,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:tab===id&&!secondary}} onPress={()=>{setTab(id);setSecondary(null);}} style={[styles.tab,(tab===id&&!secondary)&&styles.tabSelected]}>
    <Txt style={styles.tabEmoji}>{icon}</Txt><Txt style={[styles.tabLabel,(tab===id&&!secondary)&&{color:C.accent}]}>{label}</Txt>
   </Pressable>)}
  </View>
 </View>;
}
const styles=StyleSheet.create({
 root:{flex:1,backgroundColor:C.bg,paddingTop:StatusBar.currentHeight||0},
 text:{fontSize:14,color:C.text},muted:{color:C.muted},small:{fontSize:12},tiny:{fontSize:10,color:C.muted},tinyCenter:{fontSize:10,color:C.muted,textAlign:'center'},
 center:{textAlign:'center',fontSize:12},heading:{fontSize:17,fontWeight:'800',letterSpacing:0.25},brand:{fontSize:17,fontWeight:'900',letterSpacing:0.8},value:{fontWeight:'800'},itemName:{fontWeight:'700',fontSize:14},
 topBar:{paddingHorizontal:16,paddingVertical:12,borderBottomWidth:1,borderColor:C.line,flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:4,backgroundColor:C.surface},
 topTitles:{flex:1,gap:2},guestTag:{fontSize:10,fontWeight:'800',color:C.gold,backgroundColor:'#352B13',paddingVertical:6,paddingHorizontal:8,borderRadius:8,overflow:'hidden'},
 scroll:{flex:1},content:{padding:14,paddingBottom:30,width:'100%',alignSelf:'center',gap:12},
 card:{backgroundColor:C.surface,borderWidth:1,borderColor:C.line,borderRadius:20,padding:15,gap:10},
 cardTitle:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8,flexWrap:'wrap'},
 wallet:{flexDirection:'row',flexWrap:'wrap',gap:8,justifyContent:'space-between'},
 walletCell:{width:'48.6%',backgroundColor:C.card,borderColor:C.line,borderWidth:1,borderRadius:14,padding:12,gap:4},
 walletLabel:{fontSize:12,color:C.muted},walletNumber:{fontSize:21,fontWeight:'800',color:C.text},
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
