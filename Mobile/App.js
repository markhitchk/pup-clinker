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
function PlayScreen({g,act,onOpen}){
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
  <View style={styles.menuShortcuts}>
   <QuickAction emoji="🐕" title="Puppies" subtitle="Your roster" onPress={()=>onOpen('roster')}/>
   <QuickAction emoji="🎁" title="Rewards" subtitle="Daily goals" onPress={()=>onOpen('rewards')}/>
   <QuickAction emoji="🎲" title="Gacha" subtitle="Puppy pulls" onPress={()=>onOpen('gacha')}/>
   <QuickAction emoji="🎰" title="Casino" subtitle="Mini games" onPress={()=>onOpen('casino')}/>
  </View>
 </View>;
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
 const [query,setQuery]=useState('');
 const [filter,setFilter]=useState('all');
 const matches=ROSTER.filter(p=>{
  const owned=g.unlockedPuppies.includes(p.id);
  return (filter==='all'||(filter==='unlocked'&&owned)||(filter==='favorites'&&g.favoritePuppies.includes(p.id)))&&
   (p.name.toLowerCase().includes(query.trim().toLowerCase())||p.id.includes(query.trim().toLowerCase()));
 });
 return <Card title="🐕 PUPPY ROSTER" subtitle={(g.unlockedPuppies.length)+' unlocked'}>
   <TextInput accessibilityLabel="Search puppies" placeholder="Search puppies…" placeholderTextColor={C.muted} style={styles.input} value={query} onChangeText={setQuery}/>
   <View style={styles.actions}>{[['all','All'],['unlocked','Unlocked'],['favorites','★ Favorites']].map(([id,label])=>
    <Button key={id} compact secondary={filter!==id} onPress={()=>setFilter(id)}>{label}</Button>)}</View>
   <View style={styles.roster}>{matches.map(p=>{
     const owned=g.unlockedPuppies.includes(p.id),favorite=g.favoritePuppies.includes(p.id);
     return <View key={p.id} style={[styles.rosterPup,g.puppyStyle===p.id&&styles.rosterSelected,!owned&&{opacity:0.58}]}>
       <Portrait puppy={p} size={86}/>
       <Txt numberOfLines={1} style={styles.small}>{p.name}</Txt>
       <Txt muted style={styles.tiny}>{g.puppyStyle===p.id?'SELECTED':owned?'UNLOCKED':'LOCKED'}</Txt>
       {owned?<View style={{flexDirection:'row',alignItems:'center',gap:4}}>
        <Button compact onPress={()=>act({type:'switch',id:p.id,now:Date.now()})} disabled={g.puppyStyle===p.id}>Use</Button>
        <Button compact secondary onPress={()=>act({type:'favorite',id:p.id})}>{favorite?'★':'☆'}</Button>
       </View>:null}
     </View>;
   })}</View>
 </Card>;
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
function RewardsScreen({g,act,clock}){
 const statuses=achievementStatuses(g);
 return <>
  <Card title="🎁 REWARDS" subtitle={'Player Lv. '+guestLevel(g)+' · '+format(g.xp||0)+' XP'}>
   <Pair left="Daily streak" right={String(g.dailyStreak)}/>
   <Pair left="Tickets found" right={String(g.totalTicketsFound)}/>
   <Pair left="Lifetime Treats" right={format(g.lifetimeTreats)}/>
   <Pair left="Best combo" right={String(g.bestCombo)}/>
   <Pair left="Care actions" right={String(g.careActions)}/>
   <Button disabled={g.lastDailyClaimDay===Math.floor(clock/86400000)} onPress={()=>act({type:'daily',epochDay:Math.floor(clock/86400000)})}>🎁 Claim Daily Reward</Button>
  </Card>
  <Card title="🏆 ACHIEVEMENTS" subtitle={statuses.filter(s=>s.xpRewarded).length+' / '+ACHIEVEMENTS.length+' awarded'}>
   <Txt muted style={styles.small}>Nine Kotlin-derived achievement milestones. Each award grants 50 guest XP exactly once. Guest XP is not credited to your production Android account.</Txt>
   {statuses.map(a=><View key={a.id} style={styles.shopItem}>
    <View style={styles.shopLabel}>
     <Txt style={styles.itemName}>{a.emoji} {a.title}</Txt>
     <Txt muted style={styles.small}>{a.description}</Txt>
    </View>
    <Txt style={{color:a.completed?C.green:C.muted,fontWeight:'800',fontSize:12}}>{a.xpRewarded?'✓ +50 XP':Math.min(a.progress,a.target)+' / '+a.target}</Txt>
   </View>)}
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
  if(secondary==='gacha')return <GachaScreen g={g} act={act} state={state}/>;
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
   <Image source={require('./assets/logos/puppy_clicker.png')} resizeMode="contain" style={{width:42,height:42}}/><View style={styles.topTitles}><Txt style={styles.brand}>PUPPY CLICKER</Txt><Txt muted style={styles.tiny}>REACT NATIVE · ANDROID MIGRATION</Txt></View>
   <Txt style={styles.guestTag}>GUEST PREVIEW</Txt>
  </View>
  {state.notice?<Pressable accessibilityRole="button" onPress={()=>act({type:'dismiss'})} style={styles.notice}><Txt style={styles.small}>{state.notice}  ✕</Txt></Pressable>:null}
  <ScrollView key={secondary||tab} style={styles.scroll} contentContainerStyle={[styles.content,{maxWidth:width>650?670:undefined}]} keyboardShouldPersistTaps="handled">
   {secondary?<View style={styles.back}><Button compact secondary onPress={()=>setSecondary(null)}>← Back to {TABS.find(x=>x[0]===tab)?.[2]}</Button><Txt style={styles.heading}>{secondary.toUpperCase()}</Txt></View>:null}
   {!state.ready?<Card title="Loading local guest data…"><Txt muted>Reading device cache.</Txt></Card>:!g.onboardingComplete?<OnboardingScreen g={g} act={act}/>:<>{!secondary?<Wallet game={g}/>:null}{main()}</>}
  </ScrollView>
  {state.ready&&g.onboardingComplete?<View style={styles.bottomBar}>
   {TABS.map(([id,icon,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:tab===id&&!secondary}} onPress={()=>{setTab(id);setSecondary(null);}} style={[styles.tab,(tab===id&&!secondary)&&styles.tabSelected]}>
    <Txt style={styles.tabEmoji}>{icon}</Txt><Txt style={[styles.tabLabel,(tab===id&&!secondary)&&{color:C.accent}]}>{label}</Txt>
   </Pressable>)}
  </View>:null}
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
