import ProductionEntry from './src/production/ProductionEntry.jsx';
import BetaAccountEntry from './src/production/BetaAccountEntry.jsx';
import WebDiscordHandoff from './src/production/WebDiscordHandoff.jsx';
import AccountOnboarding from './src/production/AccountOnboarding.jsx';
import PuppyStartupScreen from './src/production/PuppyStartupScreen.jsx';
import PuppyErrorBoundary from './src/production/PuppyErrorBoundary.jsx';
import {classifyStartupFailure,publicStartupIssue} from './src/production/startupErrors.js';
import {startupGate} from './src/production/startupGate.js';
import {useAccountOnboarding} from './src/production/useAccountOnboarding.js';
import {revampedPlayLayout,androidPlayLayout,v2PlayLayout,nextPlayReward} from './src/engine/playRevamp.js';
import {MAIN_TABS} from './src/production/websiteNavigation.js';
import {SafeAreaProvider,useSafeAreaInsets} from 'react-native-safe-area-context';
import {applyAppearance,interaction} from './src/theme.js';
import {DEFAULT_PREFERENCES,effectiveMotion} from './src/engine/settings.js';
import SettingsScreen from './src/components/SettingsScreen.jsx';
import V2PlayScreen from './src/components/V2PlayScreen.jsx';
import WebsitePlayScreen from './src/components/WebsitePlayScreen.jsx';
import {createWebsiteCareShopScreens} from './src/components/WebsiteCareShopScreens.jsx';
import {createWebsiteRewardsScreens} from './src/components/WebsiteRewardsScreens.jsx';
import {createWebsiteRosterPrestigeScreens} from './src/components/WebsiteRosterPrestigeScreens.jsx';
import {suggestPuppyName} from './src/production/websiteNameSuggestions.js';
import {useWebsiteLanguage} from './src/production/websiteLanguage.js';
import {webGoalState} from './src/production/websiteGoalState.js';
import WebsiteInventoryScreen from './src/components/WebsiteInventoryScreen.jsx';
import WebsiteDesktopDashboard from './src/components/WebsiteDesktopDashboard.jsx';
import {WebsiteDesktopNavigation,WebsiteDesktopTopBar,WebsiteDesktopHelp} from './src/components/WebsiteDesktopShell.jsx';
import {desktopLayoutForWindow} from './src/production/desktopLayout.js';
import AccountDetailsScreen from './src/components/AccountDetailsScreen.jsx';
import NotificationInbox from './src/components/NotificationInbox.jsx';
import PuppyDetailsDialog from './src/components/PuppyDetailsDialog.jsx';
import PuppyExchangeScreen from './src/components/PuppyExchangeScreen.jsx';
import PuppyPointer from './src/components/PuppyPointer.jsx';
import {useNotifications} from './src/state/useNotifications.js';
import {useLocalDiagnostics} from './src/state/useLocalDiagnostics.js';
import {themedStyles} from './src/theme.js';
import {PupResourceStrip,PupNeedPill,PupQuickAction} from './src/shared-ui/PupGameChrome.jsx';
import React,{useCallback,useEffect,useRef,useState} from 'react';
import {Alert,Animated,Easing,Image,ImageBackground,Modal,NativeModules,Pressable,ScrollView,StatusBar,Text,TextInput,Vibration,View,BackHandler,useColorScheme,useWindowDimensions} from 'react-native';
import {colors as C} from './src/theme.js';
import {useGame} from './src/state/useGame.js';
import {useRoster} from './src/state/useRoster.js';
import {ROSTER,UPGRADES,RARITIES,PRESTIGE_SKILLS,format,level,wellness,mood,careTapBonus,cost,boneCost,ticketCost,ticketShopCost,accessoryPrices,prestigePointsAvailable,skillCost,handValue} from './src/engine/game.js';
import {SCRATCHERS} from './src/engine/casinoV7.js';
import {ACHIEVEMENTS,achievementStatuses,guestLevel} from './src/engine/progression.js';
import {MAX_PLAYER_LEVEL,MAX_PLAYER_XP,MAX_UPGRADES_PER_ITEM,MAX_REWARD_STREAK_DAYS,levelXp,nextXpTarget} from './src/engine/balancedProgression.js';
import {gachaPool,GACHA_TREAT_COST} from './src/engine/gacha.js';
import {cardLabel,handTotal,canHit,canStand,canDouble,canSplit} from './src/engine/blackjack.js';
import {blackjackTable,blackjackFan,blackjackSharedCardWidth} from './src/engine/blackjackLayout.js';
import {BLACKJACK_V6,blackjackViewportWidth,blackjackActions,canDealBlackjack,blackjackHandSummaries,blackjackDealerCaption,blackjackDisplayRound} from './src/engine/blackjackV6.js';
import {ONBOARDING_STEPS,validBirthday} from './src/engine/onboarding.js';
import {goalsForDay,progressForGoal,localEpochDay,monthKey} from './src/engine/monthlyRewards.js';
import ScratchersV6 from './src/components/ScratchersV6.jsx';
import SlotsV6 from './src/components/SlotsV6.jsx';
import RouletteV6 from './src/components/RouletteV6.jsx';
import PlinkoV6 from './src/components/PlinkoV6.jsx';
import LuckyWheelV6 from './src/components/LuckyWheelV6.jsx';
import {V6_PLAY_LAYOUT,v6PlaySizing,v6Format,v6WalletFormat,v6CooldownSeconds,v6PlayTapLabel} from './src/engine/playV6.js';
import {V6_CARE_LAYOUT,canPerformCare,dailyGiftPreview,parkPreview} from './src/engine/careV6.js';
import {TICKET_RARITY_PRESENTATION,PRESTIGE_SKILL_PRESENTATION,upgradesForShopTab,upgradeOffer,ticketShopOffer,accessoryOffer,prestigeOffer,canBuySkill} from './src/engine/shopV6.js';
import {LEGACY_V6_PUPPIES,legacyRosterSections,unlockedLegacyCount} from './src/engine/rosterV6.js';
import {GACHA_ART} from './src/assets/gacha.js';
import {GACHA_MACHINE,gachaMachineFrame} from './src/engine/gachaV6.js';

import {PUPPY_IMAGES} from './src/assets/puppies.js';
const TABS=MAIN_TABS;
const skillNames={TAP_TRAINING:'Tap Training',AUTO_TRAINING:'Bonus Training',SMART_SHOPPER:'Smart Shopper',TICKET_SENSE:'Ticket Sense'};
const die=()=>1+Math.floor(Math.random()*13);
const roll=()=>Math.floor(Math.random()*100);
const number=()=>Math.floor(Math.random()*37);
const shuffledBlackjackDeck=()=>{
 const deck=Array.from({length:52},(_,i)=>i);
 for(let i=deck.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[deck[i],deck[j]]=[deck[j],deck[i]];}
 return deck;
};
function Txt({children,muted=false,style={},numberOfLines,...textProps}){return <Text {...textProps} numberOfLines={numberOfLines} style={[styles.text,muted&&styles.muted,style]}>{children}</Text>;}
function Card({title,children,subtitle}){return <View style={styles.card}><View style={styles.cardTitle}><Txt style={styles.heading}>{title}</Txt>{subtitle?<Txt muted style={styles.tiny}>{subtitle}</Txt>:null}</View>{children}</View>;}
const AnimatedPressable=Animated.createAnimatedComponent(Pressable);
function Button({children,onPress,secondary=false,disabled=false,compact=false,danger=false}){
 const scale=useRef(new Animated.Value(1)).current;
 useEffect(()=>{if(!interaction.buttons){scale.stopAnimation();scale.setValue(1);}},[interaction.buttons,scale]);
 const press=pressed=>{if(!interaction.buttons)return;Animated.spring(scale,{toValue:pressed?0.97:1,speed:50,bounciness:2,useNativeDriver:true}).start();};
 return <AnimatedPressable onPressIn={()=>press(true)} onPressOut={()=>press(false)} accessibilityRole="button" disabled={disabled} onPress={onPress} style={[styles.button,{transform:[{scale}]},secondary&&styles.buttonSecondary,disabled&&styles.buttonDisabled,compact&&styles.buttonCompact,danger&&styles.buttonDanger]}>
  <Txt style={[styles.buttonText,secondary&&{color:C.text},disabled&&{color:C.muted}]}>{children}</Txt>
 </AnimatedPressable>;
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
 const ratio=0.96;
 const asset=PUPPY_IMAGES[puppy.id]||(puppy.image?{uri:puppy.image}:null);
 return <View style={[styles.portrait,{width:size,height:size}]}>
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
function Wallet({game,compact=false,v2=false}){
 return <PupResourceStrip game={game} styles={styles} colors={C} compact={compact} v2={v2} formatValue={v6WalletFormat}/>;
}
function NeedPill({icon,value}){
 return <PupNeedPill icon={icon} value={value} styles={styles}/>;
}
function QuickAction({emoji,title,subtitle,onPress,disabled=false}){
 return <PupQuickAction emoji={emoji} title={title} subtitle={subtitle} onPress={onPress} disabled={disabled} styles={styles}/>;
}
function PlayScreen({g,act,clock,schedule,viewportHeight,desktop=false}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const layout=androidPlayLayout(viewportHeight);
 const next=nextPlayReward(g,schedule,clock),cooldown=v6CooldownSeconds(g,clock),bonus=careTapBonus(g);
 const animate=g.animatedUi!==false&&g.tapAnimationsEnabled!==false;
 const tapScale=useRef(new Animated.Value(1)).current;
 const rotation=useRef(new Animated.Value(0)).current;
 const lift=useRef(new Animated.Value(0)).current;
 const [ticketVisible,setTicketVisible]=useState(false);
 const [renameOpen,setRenameOpen]=useState(false);
 const [renameDraft,setRenameDraft]=useState('');
 useEffect(()=>{
  if(!animate){for(const [value,target] of [[tapScale,1],[rotation,0],[lift,0]]){value.stopAnimation();value.setValue(target);}}
 },[animate,tapScale,rotation,lift]);
 useEffect(()=>{
  if(!g.ticketDropSerial||!g.lastTicketDrop)return;
  setTicketVisible(true);if(g.hapticsEnabled)Vibration.vibrate(40);
  const timer=setTimeout(()=>setTicketVisible(false),2750);return ()=>clearTimeout(timer);
 },[g.ticketDropSerial,g.lastTicketDrop]);
 const doTap=()=>{
  if(v6CooldownSeconds(g,Date.now()))return;
  if(g.energy<=10){Alert.alert('😴 '+g.puppyName+' is tired','Rest in Pup Care to keep playing.');return;}
  act({type:'tap',now:Date.now(),randoms:[Math.random(),Math.random()]});
  if(g.hapticsEnabled)Vibration.vibrate(12);
  if(!animate)return;
  tapScale.stopAnimation();rotation.stopAnimation();lift.stopAnimation();
  tapScale.setValue(.92);rotation.setValue(-4);lift.setValue(7);
  Animated.parallel([
   Animated.sequence([Animated.spring(tapScale,{toValue:1.08,friction:4,tension:100,useNativeDriver:true}),Animated.spring(tapScale,{toValue:1,friction:6,tension:90,useNativeDriver:true})]),
   Animated.sequence([Animated.spring(rotation,{toValue:4,friction:5,tension:90,useNativeDriver:true}),Animated.spring(rotation,{toValue:0,friction:6,tension:80,useNativeDriver:true})]),
   Animated.sequence([Animated.spring(lift,{toValue:-5,friction:5,tension:95,useNativeDriver:true}),Animated.spring(lift,{toValue:0,friction:6,tension:85,useNativeDriver:true})])
  ]).start();
 };
 const accent=g.accentHex||'#00B8F0',rgb=[1,3,5].map(i=>parseInt(accent.slice(i,i+2),16)).join(',');
 const label=cooldown?'🐶👁️ Fair-play cooldown · '+cooldown+'s':g.energy<=10?'😴 '+g.puppyName+' is tired · Care needed':'🐾 Tap '+g.puppyName+'  +'+(g.clickPower+bonus);
 if(g.playLayoutV2Enabled===true&&!desktop){
  const v2={...v2PlayLayout(viewportHeight,g.uiScale),viewportHeight};
  return <V2PlayScreen game={g} layout={v2} wallet={<Wallet game={g} compact={g.uiScale==='compact'||v2.short} v2/>}
   portrait={<Pressable testID="android-v2-puppy-portrait" accessibilityRole="button"
    accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'} accessibilityState={{disabled:cooldown>0}}
    disabled={cooldown>0} onPress={doTap}>
    <Animated.View style={{transform:[{scale:tapScale},{rotate:rotation.interpolate({inputRange:[-4,4],outputRange:['-4deg','4deg']})},{translateY:lift}]}}>
     <Portrait puppy={pup} size={v2.portrait} accessory={g.accessory}/>
    </Animated.View>
   </Pressable>}
   onTap={doTap} cooldown={cooldown} bonus={bonus} next={next} ticketVisible={ticketVisible}/>;
 }
 return <>
 <WebsitePlayScreen game={g} viewportHeight={viewportHeight} desktop={desktop}
  identityPortrait={desktop?<Portrait puppy={pup} size={64} accessory={g.accessory}/>:null}
  onRename={desktop?()=>{setRenameDraft(g.puppyName);setRenameOpen(true);}:undefined}
  wallet={desktop?null:<Wallet game={g} compact={viewportHeight<585}/>}
  portrait={<Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'}
   accessibilityState={{disabled:cooldown>0}} disabled={cooldown>0} onPress={doTap}>
   <Animated.View style={{transform:[{scale:tapScale},
    {rotate:rotation.interpolate({inputRange:[-4,4],outputRange:['-4deg','4deg']})},
    {translateY:lift}]}}>
    <Portrait puppy={pup} size={desktop?260:Math.max(85,Math.min(214,Math.floor((viewportHeight-330)*0.85)))}
     accessory={g.accessory}/>
   </Animated.View>
  </Pressable>}
  onTap={doTap} cooldown={cooldown} bonus={bonus} next={next}
  ticketVisible={ticketVisible}/>
 {renameOpen?<Modal visible transparent animationType="fade"
  onRequestClose={()=>setRenameOpen(false)}>
  <View style={{flex:1,justifyContent:'center',alignItems:'center',
   backgroundColor:'rgba(0,0,0,0.75)',padding:20}}>
   <View style={{width:'100%',maxWidth:400,backgroundColor:C.surface,borderRadius:24,
    borderWidth:1,borderColor:C.line,padding:20,gap:14}}>
    <Txt style={{fontSize:22,fontWeight:'800',color:C.text}}>Rename Puppy</Txt>
    <Txt muted style={{fontSize:12}}>Update your active puppy's name in your Pup Account.</Txt>
    <TextInput accessibilityLabel="New puppy name" value={renameDraft} onChangeText={setRenameDraft}
     placeholder="Puppy name" placeholderTextColor={C.muted} maxLength={18}
     style={{color:C.text,backgroundColor:C.card,borderColor:C.line,borderWidth:1,
      borderRadius:14,padding:12,minHeight:44}}/>
    <View style={{flexDirection:'row',gap:10}}>
     <Pressable accessibilityRole="button" accessibilityLabel="Cancel rename"
      onPress={()=>setRenameOpen(false)}
      style={{flex:1,borderWidth:1,borderColor:C.line,borderRadius:999,padding:12,alignItems:'center'}}>
      <Txt style={{color:C.accent,fontWeight:'800'}}>Cancel</Txt>
     </Pressable>
     <Pressable accessibilityRole="button" accessibilityLabel="Save puppy name"
      accessibilityState={{disabled:!renameDraft.trim()}}
      disabled={!renameDraft.trim()}
      onPress={()=>{const name=renameDraft.trim().replace(/\\n/g,' ').slice(0,18);
       if(name)act({type:'rename',name,now:Date.now()});setRenameOpen(false);}}
      style={{flex:1,borderRadius:999,padding:12,alignItems:'center',
       backgroundColor:C.accent,opacity:renameDraft.trim()?1:0.5}}>
      <Txt style={{color:C.onAccent,fontWeight:'800'}}>Save</Txt>
     </Pressable>
    </View>
   </View>
  </View>
 </Modal>:null}
 </>;
}
// Website-owned Roster and Prestige screens are generated; no Android copy.
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
  if(g.animationsEnabled===false){turn.setValue(1);drop.setValue(0);opening.setValue(0);setStage(2);return;}
  setStage(1);
  turn.setValue(0);drop.setValue(GACHA_MACHINE.dropOffset);opening.setValue(0);
  const motion=Animated.sequence([
   Animated.timing(turn,{toValue:1,duration:GACHA_MACHINE.knobTurnMs,useNativeDriver:true}),
   Animated.spring(drop,{toValue:0,friction:5,tension:90,useNativeDriver:true})
  ]);
  motion.start(({finished})=>{if(finished)setStage(2);});
  return ()=>{motion.stop();if(revealTimer.current)clearTimeout(revealTimer.current);};
 },[result,turn,drop,opening,g.animationsEnabled]);
 const openCapsule=()=>{
  if(stage!==2||!result?.success)return;
  if(g.animationsEnabled===false){opening.setValue(1);setStage(4);return;}
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
   Rewards require authoritative PupEye verification before they can affect progression.
  </Txt>
  <Modal visible={stage===4&&Boolean(result?.success)} transparent
   animationType={g.reducedMotion||!g.animatedUi?'none':'fade'} onRequestClose={reset}>
   <PuppyPointer enabled={g.puppyPointerEnabled} style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.gachaRevealHeading}>✨ CAPSULE OPENED! ✨</Txt>
     <Txt style={[styles.itemName,{color:C.accentInk}]}>{result?.isNewUnlock?'NEW PUPPY!':'PUPPY REVEALED!'}</Txt>
     <View style={styles.gachaRevealPortrait}>
      {selected?<Portrait puppy={selected} size={188} unlocked/>:null}
     </View>
     <Txt style={styles.heading}>{selected?.emoji||'🐶'} {result?.puppyName||'Puppy'}</Txt>
     <Txt muted>{result?.isNewUnlock?'Added to your Puppy Roster.':'Already in your Puppy Roster.'}</Txt>
     <Button onPress={()=>{act({type:'switch',id:result.puppyId,now:Date.now()});reset();}}>Use Puppy</Button>
     <Button secondary onPress={reset}>Another Capsule</Button>
     <Button secondary compact onPress={reset}>Close</Button>
    </View>
   </PuppyPointer>
  </Modal>
  <Modal visible={showInfo} transparent animationType={g.reducedMotion||!g.animatedUi?'none':'fade'}
   onRequestClose={()=>setShowInfo(false)}>
   <PuppyPointer enabled={g.puppyPointerEnabled} style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.heading}>ⓘ Gacha Info</Txt>
     <Txt muted>Turn the machine with Treats or a Common Upgrade Ticket, then open your capsule. New unrestricted puppies are awarded before duplicates.</Txt>
     <Txt muted>Special event, developer, Discord-role and Puppy Code puppies are excluded. Unlocks must be verified by the authoritative Pup Account service.</Txt>
     <Button onPress={()=>setShowInfo(false)}>Got it</Button>
    </View>
   </PuppyPointer>
  </Modal>
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
 const label=hidden?'Hidden dealer card':cardLabel(id);
 const rank=label.slice(0,-1),suit=label.slice(-1);
 const color=suit==='♥'||suit==='♦'?'#BD2331':'#171717';
 return <ImageBackground accessible accessibilityRole="image" accessibilityLabel={label}
  source={source} resizeMode="stretch" style={[styles.blackjackArtworkCard,{width,height}]} imageStyle={{borderRadius:5}}>
  {!hidden?<><View pointerEvents="none" style={styles.blackjackCardCorner}>
    <Text style={[styles.blackjackCardRank,{fontSize:Math.max(9,width*.29),color}]}>{rank}</Text>
    <Text style={[styles.blackjackCardRank,{fontSize:Math.max(9,width*.29),color}]}>{suit}</Text>
   </View>
   <Text pointerEvents="none" style={[styles.blackjackCardPip,{fontSize:Math.max(12,width*.43),color}]}>{suit}</Text>
  </>:null}
 </ImageBackground>;
}
function BlackjackCardFan({cards=[],hideAfterFirst=false,zone,cardWidth}){
 const fan=blackjackFan(zone.width,cardWidth,cards.length);
 const h=Math.min(fan.cardWidth*1.4,zone.height);
 const left=(zone.width-fan.width)/2;
 return <View style={{position:'absolute',left:zone.left,top:zone.top,width:zone.width,height:zone.height}} pointerEvents="box-none">
  {cards.map((id,index)=><View key={index} style={{position:'absolute',left:left+index*fan.step,top:(zone.height-h)/2}}>
   <BlackjackArtworkCard id={id} hidden={hideAfterFirst&&index>0} width={fan.cardWidth} height={h}/>
  </View>)}
 </View>;
}
function BlackjackFeltTable({round,selectedHand=0,onSelectHand=()=>{}}){
 const {width:windowWidth,height:windowHeight,fontScale}=useWindowDimensions();
 const [measuredWidth,setMeasuredWidth]=useState(0);
 const containerWidth=measuredWidth||Math.max(1,windowWidth-48);
 const width=blackjackViewportWidth(containerWidth,windowHeight,fontScale);
 const metrics=blackjackTable(width);
 const dealer=round?.dealerCards||[];
 const player=round?.hands?.[selectedHand]?.cards||[];
 const shared=blackjackSharedCardWidth(metrics,dealer.length,player.length);
 return <View style={styles.blackjackTableGroup}>
  {round&&round.hands.length>1?<View style={styles.blackjackHandSelector}>
   {round.hands.map((hand,index)=><Pressable key={index}
    accessibilityRole="tab" accessibilityState={{selected:selectedHand===index}}
    accessibilityLabel={'Hand '+(index+1)+(index===round.activeHandIndex&&!round.complete?', current turn':', inspect hand')}
    onPress={()=>onSelectHand(index)}
    style={[styles.blackjackHandChip,selectedHand===index&&styles.blackjackHandChipSelected]}>
    <Txt numberOfLines={1} style={styles.blackjackHandChipLabel}>Hand {index+1}</Txt>
   </Pressable>)}
  </View>:null}
  <View style={{width:'100%',alignItems:'center'}} onLayout={event=>{
   const next=Math.round(event.nativeEvent.layout.width);
   if(next>0&&Math.abs(next-measuredWidth)>1)setMeasuredWidth(next);
  }}>
   <View testID="blackjack_table" accessibilityLabel="Puppy Blackjack green felt table"
    style={{width:metrics.width,height:metrics.height,overflow:'hidden',backgroundColor:'#0B3026'}}>
    <Image source={CASINO_ART.blackjack} resizeMode="stretch" style={{
     position:'absolute',left:0,top:-metrics.artworkTop,width:metrics.width,height:metrics.artworkHeight
    }}/>
    <BlackjackCardFan cards={dealer} hideAfterFirst={Boolean(round&&!round.complete)} zone={metrics.dealer} cardWidth={shared}/>
    <BlackjackCardFan cards={player} zone={metrics.player} cardWidth={shared}/>
    {round?<View pointerEvents="none" style={styles.blackjackTableChip}>
     <View style={styles.blackjackTableChipCenter}/>
    </View>:null}
   </View>
  </View>
  <Txt muted style={[styles.small,{textAlign:'center'}]}>{blackjackDealerCaption(round)}</Txt>
 </View>;
}
function BlackjackV6Panel({g,act,state,onBack}){
 const [wager,setWager]=useState(100);
 const [selectedHand,setSelectedHand]=useState(0);
 const [showRules,setShowRules]=useState(false);
 const active=state.blackjack;
 const displayed=blackjackDisplayRound(active,state.blackjackDisplay);
 const outcome=active?null:state.blackjackDisplay?.outcome||null;
 const controls=active?blackjackActions(active,g.casinoChips):null;
 const {width:windowWidth,fontScale}=useWindowDimensions();
 const oneRow=windowWidth>=BLACKJACK_V6.actionRowThreshold&&fontScale<=1.3;
 useEffect(()=>{
  // Native remember(roundKey,activeHandIndex) follows the next playable hand.
  if(active)setSelectedHand(Math.max(0,active.activeHandIndex));
  else if(displayed)setSelectedHand(0);
 },[active?.activeHandIndex,active?.hands.length,displayed?.complete]);
 const lines=blackjackHandSummaries(displayed,outcome);
 const move=(type)=>{if(active)act({type});};
 const buttons=[
  ['🐾 HIT','hit','blackjackHit','#1463AD','#FFFFFF'],
  ['STAND','stand','blackjackStand','#237246','#FFFFFF'],
  ['2× DOUBLE','double','blackjackDouble','#FFCF4A','#322408'],
  ['⇄ SPLIT','split','blackjackSplit','#7845AD','#FFFFFF']
 ];
 return <View style={styles.blackjackScreen}>
  <View style={styles.blackjackHeader}>
   <Button compact secondary onPress={onBack}>‹ Casino</Button>
   <Txt style={[styles.heading,{flex:1}]}>Puppy Blackjack</Txt>
  </View>
  <View style={styles.blackjackWallet}>
   <Txt style={styles.itemName}>🐾 Casino Chip Wallet · {format(g.casinoChips)} Chips</Txt>
  </View>
  <BlackjackFeltTable round={displayed} selectedHand={selectedHand} onSelectHand={setSelectedHand}/>
  {displayed?<View accessibilityLiveRegion="polite" style={[
   styles.blackjackSummary,outcome&&outcome.hands.some(h=>h.result==='WIN'||h.result==='BLACKJACK')&&styles.blackjackWinSummary
  ]}>
   {lines.map(line=><Txt key={line.index} style={[styles.small,line.active&&{fontWeight:'900',color:C.text}]}>
    Hand {line.index+1} · {line.total}{line.soft?' soft':''} · {line.wager} Chips · {line.status}
   </Txt>)}
   {outcome?<Txt style={styles.itemName}>Round complete · {outcome.totalPayout} Chips returned</Txt>:null}
  </View>:null}
  {active&&!active.complete?<View testID="blackjack_actions" style={styles.blackjackActions}>
   {Array.from({length:oneRow?1:2},(_,row)=><View key={row} style={styles.blackjackActionRow}>
    {buttons.slice(row*(oneRow?4:2),(row+1)*(oneRow?4:2)).map(([label,key,type,backgroundColor,color])=>
     <Pressable key={key} accessibilityRole="button"
      accessibilityState={{disabled:!controls[key]}}
      disabled={!controls[key]} onPress={()=>move(type)}
      style={({pressed})=>[styles.blackjackAction,{backgroundColor},!controls[key]&&styles.buttonDisabled,pressed&&{transform:[{scale:.97}]}]}>
      <Text style={[styles.blackjackActionText,{color}]}>{label}</Text>
     </Pressable>)}
   </View>)}
  </View>:null}
  {!active?<>
   <Txt style={styles.sectionTitle}>Wager</Txt>
   <Txt muted style={styles.small}>Natural Blackjack returns 2.5× total. Normal wins return 2×. Pushes return 1×.</Txt>
   <View style={styles.blackjackWagerRow}>
    {BLACKJACK_V6.wagerPresets.map(preset=><Pressable key={preset}
     accessibilityRole="button" accessibilityState={{selected:wager===preset}}
     onPress={()=>setWager(preset)} style={[styles.filterChip,wager===preset&&styles.filterChipSelected]}>
     <Txt style={[styles.filterText,wager===preset&&styles.filterTextSelected]}>{preset}</Txt>
    </Pressable>)}
   </View>
   <Button disabled={!canDealBlackjack(g.casinoChips,wager,active,g.pendingScratch||g.pendingSlots||g.pendingRoulette||g.pendingPlinko||g.pendingWheel)}
    onPress={()=>{setSelectedHand(0);act({type:'blackjackStart',wager,deck:shuffledBlackjackDeck(),now:Date.now()});}}>
    {g.pendingScratch||g.pendingSlots||g.pendingRoulette||g.pendingPlinko||g.pendingWheel?'Finish the current Casino round first':g.casinoChips<wager?'Not Enough Chips':'Deal for '+wager+' Chips'}
   </Button>
  </>:null}
  <Button secondary compact onPress={()=>setShowRules(!showRules)}>{showRules?'Hide Blackjack Rules':'ⓘ Blackjack Rules'}</Button>
  {showRules?<View style={styles.blackjackRules}>
   <Txt style={styles.itemName}>Published Blackjack rules</Txt>
   {BLACKJACK_V6.rules.map(([label,value])=><View key={label} style={styles.pair}>
    <Txt muted style={styles.small}>{label}</Txt>
    <Txt style={styles.small}>{value}</Txt>
   </View>)}
   <Txt muted style={styles.small}>Dealer Blackjack is resolved immediately after the initial deal. Split aces cannot be resplit in this version.</Txt>
   <Txt muted style={styles.small}>Every deck position, hand, wager, split, double, and dealer draw is saved to the active casino round.</Txt>
   <Txt muted style={styles.small}>Wagers and payouts are committed by the signed PupEye server. Cards are dealt without exposing the hidden shoe.</Txt>
  </View>:null}
 </View>;
}
function CasinoScreen({g,act,state,bet,setBet,onBack}){
 const [page,setPage]=useState(g.pendingScratch?'scratchers':state.blackjack?'blackjack':g.pendingSlots?'slots':g.pendingRoulette?'roulette':g.pendingPlinko?'plinko':g.pendingWheel?'wheel':'hub');
 const [accepted,setAccepted]=useState(false);
 const {width}=useWindowDimensions();
 useEffect(()=>{
  if(!accepted)Alert.alert('Puppy Casino — Simulated Gambling',
   'Slots, roulette, blackjack, Plinko, scratchers and the Lucky Pup Wheel use game-only chips. No real money is used and no prizes have real-world cash value.',
   [{text:'Go Back',style:'cancel',onPress:onBack},{text:'Continue',onPress:()=>setAccepted(true)}],{cancelable:false});
 },[accepted,onBack]);
 const casino=(gameType,extra={})=>act({type:'casino',gameType,wager:bet,...extra});
 const choices=[20,50,100,500,1000];
 const inBlackjack=Boolean(state.blackjack||g.pendingSlots||g.pendingRoulette||g.pendingPlinko||g.pendingScratch||g.pendingWheel);
 const visiblePage=CASINO_PAGES.find(p=>p[0]===page);
 return <View style={styles.gamePage}>
  {page!=='blackjack'&&page!=='slots'&&page!=='roulette'&&page!=='plinko'&&page!=='scratchers'&&page!=='wheel'?<><View style={styles.pair}>
   <Button compact secondary onPress={()=>page==='hub'?onBack():setPage('hub')}>{page==='hub'?'‹ Rewards':'‹ Casino'}</Button>
   <Txt style={styles.pageTitle}>{visiblePage?.[2]||'Puppy Casino'}</Txt>
  </View>
  <Txt muted style={styles.small}>🐾 Casino Chip Wallet · {format(g.casinoChips)} Chips · Simulated, no real-money prizes</Txt></>:null}
  {page==='hub'?<>
   {g.pendingWheel?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎡</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Lucky Pup Wheel Spin</Txt>
     <Txt muted style={styles.small}>Your result is committed. Resume the wheel to reveal and settle once.</Txt></View>
    <Button compact onPress={()=>setPage('wheel')}>Resume</Button>
   </View>:null}
   {g.pendingPlinko?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🔵</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Plinko Drop</Txt>
     <Txt muted style={styles.small}>Your path, nine-bin landing and payout are saved. Resume the ball drop to settle once.</Txt></View>
    <Button compact onPress={()=>setPage('plinko')}>Resume</Button>
   </View>:null}
   {g.pendingRoulette?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎡</Txt><View style={{flex:1}}>
     <Txt style={styles.itemName}>Unfinished Roulette Spin</Txt>
     <Txt muted style={styles.small}>Your accepted bet and winning pocket are saved. Resume the wheel to reveal and settle.</Txt>
    </View><Button compact onPress={()=>setPage('roulette')}>Resume</Button>
   </View>:null}
   {g.pendingSlots?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎰</Txt>
    <View style={{flex:1}}>
     <Txt style={styles.itemName}>Unfinished Slots Spin</Txt>
     <Txt muted style={styles.small}>Your original wager and outcome are saved. Resume to reveal and settle once.</Txt>
    </View>
    <Button compact onPress={()=>setPage('slots')}>Resume</Button>
   </View>:null}
   {state.blackjack?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🃏</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Blackjack Hand</Txt>
     <Txt muted style={styles.small}>Your original cards and wager are saved. Resume before opening another casino game.</Txt></View>
    <Button compact onPress={()=>setPage('blackjack')}>Resume</Button>
   </View>:null}
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
   {page!=='blackjack'&&page!=='slots'&&page!=='roulette'&&page!=='plinko'&&page!=='scratchers'&&page!=='wheel'?<View style={styles.filterRow}>{choices.map(w=><Pressable key={w} accessibilityRole="button" accessibilityState={{selected:bet===w}} style={[styles.filterChip,bet===w&&styles.filterChipSelected]} onPress={()=>setBet(w)}><Txt numberOfLines={1} style={[styles.filterText,bet===w&&styles.filterTextSelected]}>{w}</Txt></Pressable>)}</View>:null}
   {page==='slots'?<SlotsV6 game={g} pending={g.pendingSlots}
    lastResult={state.slotsDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='roulette'?<RouletteV6 game={g} pending={g.pendingRoulette}
    lastResult={state.rouletteDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='plinko'?<PlinkoV6 game={g} pending={g.pendingPlinko}
    lastResult={state.plinkoDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='scratchers'?<ScratchersV6 game={g} pending={g.pendingScratch}
    lastResult={state.scratchDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='wheel'?<LuckyWheelV6 game={g} pending={g.pendingWheel}
    lastResult={state.wheelDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='blackjack'?<BlackjackV6Panel g={g} act={act} state={state} onBack={()=>setPage('hub')}/>:null}
  </>}
 </View>;
}
function OnboardingScreen({g,act,notifications}){
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
  {step===4?<><Txt style={styles.heading}>Notifications</Txt><Txt muted>Choose local reward and puppy reminders. Android asks for permission when you enable notifications.</Txt>
   <View style={styles.actions}><Button compact secondary={!g.notificationsOptIn} onPress={async()=>{const enabled=await notifications.request();act({type:'onboardNotifications',enabled});}}>Allow Notifications</Button><Button compact secondary={g.notificationsOptIn} onPress={()=>act({type:'onboardNotifications',enabled:false})}>Keep disabled</Button></View>
   <Txt muted>Current choice: {g.notificationsOptIn?'Local reminders enabled':'Disabled'}</Txt>
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
// Beta uses the real Puppy Clicker screen hierarchy, not a migration-only page.
// The production package continues to require an original-key-signed Play upgrade.
export default function App(){return <SafeAreaProvider><WebDiscordHandoff/><PuppyErrorBoundary>{NativeModules.PuppyBetaAccount?.isBeta===true?<PuppyApp betaMode/>:<ProductionEntry/>}</PuppyErrorBoundary></SafeAreaProvider>;}
export function PuppyApp({betaMode=false}){
 const insets=useSafeAreaInsets();
 const systemDark=useColorScheme()==='dark';
 const {state,gameForDisplay,queuedTaps,act:gameAct,account,cloudState,startupError,syncStatus,refreshAccount,recoverRetiredIdentity,updateAccountName}=useGame({mode:betaMode?'beta':'guest'});
 const onboarding=useAccountOnboarding({enabled:betaMode,account,activateAccount:refreshAccount});
 const act=useCallback(action=>{
  gameAct(action);
  if(betaMode&&action.type==='setPreference')void onboarding.update({[action.name]:action.value});
  if(betaMode&&action.type==='resetSettings')void onboarding.update(DEFAULT_PREFERENCES);
 },[gameAct,betaMode,onboarding.update]);
 const [crashChecked,setCrashChecked]=useState(!betaMode);
 const [previousCrash,setPreviousCrash]=useState(null);
 const [startupRetryBusy,setStartupRetryBusy]=useState(false);
 useEffect(()=>{
  if(!betaMode)return;
  let active=true;
  const native=NativeModules.PuppyBetaAccount;
  Promise.resolve(native?.readStartupCrash?.()).then(result=>{
   if(active&&result?.exists===true)setPreviousCrash(publicStartupIssue(result.code));
  }).catch(()=>{/* Crash markers are optional; never block authenticated startup. */})
   .finally(()=>{if(active)setCrashChecked(true);});
  return ()=>{active=false;};
 },[betaMode]);
 const retryStartup=()=>{
  if(startupRetryBusy)return;
  setStartupRetryBusy(true);
  void Promise.allSettled([refreshAccount(),onboarding.reload()])
   .finally(()=>setStartupRetryBusy(false));
 };
 const recoverRetiredIdentityAtStartup=()=>{
  if(startupRetryBusy)return;
  setStartupRetryBusy(true);
  void recoverRetiredIdentity()
   .catch(()=>Alert.alert('Beta identity recovery unsuccessful',
    'The server could not approve a new local identity. Your Pup Account and cloud saves were not reset. Retry or contact Puppy Clicker Support.'))
   .finally(()=>setStartupRetryBusy(false));
 };
 const retryPreviousCrash=()=>{
  if(startupRetryBusy)return;
  setStartupRetryBusy(true);
  const native=NativeModules.PuppyBetaAccount;
  Promise.resolve(native?.clearStartupCrash?.()).then(()=>{
   setPreviousCrash(null);
   return Promise.allSettled([refreshAccount(),onboarding.reload()]);
  }).catch(()=>{/* Keep marker visible until it has been acknowledged. */})
   .finally(()=>setStartupRetryBusy(false));
 };
 const [accountOpen,setAccountOpen]=useState(false);
 const [accountEntryMode,setAccountEntryMode]=useState('signin');
 const openAccount=mode=>{setAccountEntryMode(mode||'signin');setAccountOpen(true);};
 const registry=useRoster();
 const {width,height:windowHeight}=useWindowDimensions();
 const desktopLayout=desktopLayoutForWindow(width,windowHeight);
 const desktop=desktopLayout.desktop;
 const [tab,setTab]=useState('play');
 const [secondary,setSecondary]=useState(null);
 const [profileReturnTo,setProfileReturnTo]=useState('settings');
 const [bet,setBet]=useState(20);
 const [clock,setClock]=useState(Date.now());
 const [contentHeight,setContentHeight]=useState(600);
 useEffect(()=>{const sub=BackHandler.addEventListener('hardwareBackPress',()=>{if(!secondary)return false;setSecondary(secondary==='account-details'?profileReturnTo:null);return true;});return ()=>sub.remove();},[secondary,profileReturnTo]);
 useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000);return ()=>clearInterval(timer);},[]);
 const saved=betaMode?{...gameForDisplay,...Object.fromEntries(Object.keys(DEFAULT_PREFERENCES).map(name=>[name,onboarding.setup[name]??state.game[name]])),birthdayMonth:onboarding.setup.birthdayMonth,birthdayDay:onboarding.setup.birthdayDay}:state.game;
 applyAppearance(saved,systemDark);
 const motion=effectiveMotion(saved);
 const g={...saved,animationsEnabled:motion.casino,tapAnimationsEnabled:motion.tap,idleAnimationsEnabled:motion.decorative&&saved.animationsEnabled,buttonMotionEnabled:motion.buttons};
 const [inboxOpen,setInboxOpen]=useState(false);
 const [inventoryRefreshing,setInventoryRefreshing]=useState(false);
 const refreshInventory=async()=>{
  if(inventoryRefreshing)return;
  setInventoryRefreshing(true);
  try{await refreshAccount();}finally{setInventoryRefreshing(false);}
 };
 const notifications=useNotifications({game:saved,ready:betaMode?(onboarding.ready&&onboarding.setup.setupComplete&&account?.status==='VERIFIED'&&account?.legalAccepted===true):(state.ready&&saved.onboardingComplete),act,onOpen:()=>setInboxOpen(true)});
 const diagnostics=useLocalDiagnostics(saved,secondary||tab,state.ready);
 const openScreen=id=>{if(id==='notifications'){notifications.check();setInboxOpen(true);}else if(TABS.some(tab=>tab[0]===id)){setTab(id);setSecondary(null);}else if(id==='account-details'){setProfileReturnTo(secondary==='inventory'?'inventory':'settings');setSecondary(id);}else setSecondary(id);};
 const usePuppy=id=>{if(!saved.unlockedPuppies.includes(id))return;act({type:'switch',id,now:Date.now()});setTab('play');setSecondary(null);};
 const main=()=>{
  if(secondary==='inventory')return <WebsiteInventoryScreen game={g} onOpen={openScreen}
   onBack={()=>setSecondary(null)} onRefresh={refreshInventory}
   refreshing={inventoryRefreshing} updatedAt={syncStatus?.savedAt}/>;
  if(secondary==='help')return <WebsiteDesktopHelp onOpen={openScreen}/>;
  if(secondary==='achievements')return <RewardsScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule} onOpen={openScreen} onPrestige={()=>openScreen('prestige')} pendingTaps={queuedTaps||0} readOnly={betaMode&&!(cloudState==='LIVE_SYNCED'||(cloudState==='NO_CLOUD_SAVE'&&account?.gameplayWritable===true))}/>;
  if(secondary==='settings')return <SettingsScreen g={saved} act={act} onOpen={openScreen} registry={registry} notifications={notifications} diagnostics={diagnostics} betaMode={betaMode} account={account} onConnectAccount={()=>openAccount('signin')} onUpdateAccountName={updateAccountName} onUpdateBirthday={onboarding.update} cloudState={cloudState} syncStatus={syncStatus} onRefreshAccount={refreshAccount}/>;
  if(secondary==='account-details')return <AccountDetailsScreen account={account} cloudState={cloudState}
   onBack={()=>setSecondary(profileReturnTo)} onManage={()=>openAccount('signin')} onRefresh={refreshAccount}/>;
  if(secondary==='prestige')return <PrestigeScreen g={g} act={act} activeCasinoRound={Boolean(state.blackjack||g.pendingScratch||g.pendingSlots||g.pendingRoulette||g.pendingPlinko||g.pendingWheel)}/>;
  if(secondary==='exchange')return <PuppyExchangeScreen game={g} registry={registry} account={account} onBack={()=>setSecondary(null)} onRefresh={refreshAccount}/>;
  if(secondary==='roster'||tab==='roster'&&!secondary){
   const roster=<RosterScreen g={g} act={act} registry={registry} onUse={usePuppy} onOpenExchange={()=>setSecondary('exchange')}/>;
   return desktop?<View testID="android-desktop-roster-shop" style={{flexDirection:'row',alignItems:'flex-start',gap:18}}>
    <View style={{flex:1,minWidth:0}}>{roster}</View>
    <View style={{flex:1,minWidth:0,borderLeftWidth:1,borderLeftColor:C.line,paddingLeft:18}}>
     <ShopScreen g={g} act={act} onOpen={openScreen}/>
    </View>
   </View>:roster;
  }
  if(secondary==='rewards'||tab==='rewards'&&!secondary)return <RewardsScreen g={g} act={act} clock={clock} schedule={state.monthlySchedule} onOpen={openScreen} onPrestige={()=>openScreen('prestige')} pendingTaps={queuedTaps||0} readOnly={betaMode&&!(cloudState==='LIVE_SYNCED'||(cloudState==='NO_CLOUD_SAVE'&&account?.gameplayWritable===true))}/>;
  if(secondary==='gacha')return <GachaScreen g={g} act={act} state={state} registry={registry} onBack={()=>openScreen('rewards')}/>;
  if(secondary==='casino')return <CasinoScreen g={g} act={act} bet={bet} setBet={setBet} state={state} onBack={()=>openScreen('rewards')}/>;
  if(tab==='play')return desktop?
   <WebsiteDesktopDashboard game={g} schedule={state.monthlySchedule} clock={clock}
    onOpen={openScreen} railWidth={desktopLayout.rightRailWidth}
    play={<PlayScreen key="desktop" g={g} act={act} clock={clock} desktop
     schedule={state.monthlySchedule} viewportHeight={contentHeight-6}/>}/>:
   <PlayScreen key="phone" g={g} act={act} clock={clock} schedule={state.monthlySchedule}
    viewportHeight={contentHeight-6}/>
  if(tab==='care')return <CareScreen g={g} act={act} viewportHeight={contentHeight-6}/>;
  if(tab==='shop')return <ShopScreen g={g} act={act} onOpen={openScreen}/>;
  return null;
 };
 // Restore an existing signed session and its cloud save on the startup screen.
 // No account banner, cached preview or unverified account can enter gameplay.
 const startPhase=betaMode?startupGate({account,cloudState,setupReady:onboarding.ready,
  setupProblem:onboarding.problem,gameReady:state.ready}):'ready';
 if(betaMode&&!crashChecked)return <PuppyStartupScreen phase="loading"/>;
 if(betaMode&&previousCrash)return <PuppyStartupScreen phase="crash" issue={previousCrash}
  busy={startupRetryBusy} retry={retryPreviousCrash}/>;
 if(betaMode&&(startPhase==='loading'||startPhase==='connection-error'||startPhase==='setup-error'))
  return <PuppyStartupScreen phase={startPhase} issue={classifyStartupFailure({
   source:startPhase==='setup-error'?'setup':'account',error:startupError,account,cloudState
  })} busy={startupRetryBusy||onboarding.busy} retry={retryStartup}
   onRecoverIdentity={recoverRetiredIdentityAtStartup}/>;
 if(betaMode&&(startPhase==='signin'||startPhase==='legal'))return <>
  <PuppyStartupScreen phase="loading"/>
  <BetaAccountEntry visible requiredAtStartup initialMode="signin" onboarding
   onVerified={refreshAccount} onClose={()=>{}}/>
 </>;
 if(betaMode&&!onboarding.setup.setupComplete)return <>
  <AccountOnboarding flow={onboarding} account={account} notifications={notifications} onConnect={openAccount}/>
  <BetaAccountEntry visible={accountOpen} initialMode={accountEntryMode} onboarding onVerified={refreshAccount} onClose={()=>setAccountOpen(false)}/>
 </>;
 return <PuppyPointer enabled={saved.puppyPointerEnabled} style={[styles.root,{paddingTop:insets.top,paddingBottom:insets.bottom}]}>
  <StatusBar barStyle={C.dark?'light-content':'dark-content'} backgroundColor={C.bg}/>
  <View testID={desktop?'android-desktop-window':'android-phone-window'}
   style={{flex:1,flexDirection:desktop?'row':'column',minWidth:0,minHeight:0}}>
   {desktop?<WebsiteDesktopNavigation tabs={TABS} route={secondary||tab}
    onOpen={openScreen} width={desktopLayout.sidebarWidth}/>:null}
   <View style={{flex:1,minWidth:0,minHeight:0}}>
  {state.ready&&saved.onboardingComplete?(desktop?
   <WebsiteDesktopTopBar game={g}
    unread={(saved.notificationInbox||[]).filter(n=>!n.read).length}
    onNotifications={()=>{notifications.check();setInboxOpen(true);}}
    onSettings={()=>openScreen('settings')} onInventory={()=>openScreen('inventory')}/>:
   <View style={[styles.topBar,g.playLayoutV2Enabled&&{backgroundColor:C.bg,borderBottomWidth:1,borderBottomColor:C.accentSoft}]}>
   <Image source={require('./assets/logos/puppy_clicker.png')} resizeMode="contain" style={{width:32,height:32}}/>
   <Txt style={[styles.brand,{flex:1,letterSpacing:0}]}>Puppy Clicker</Txt>
   <Pressable accessibilityRole="button" accessibilityLabel="Open inventory"
    testID="android-phone-inventory-button"
    onPress={()=>openScreen('inventory')} style={styles.headerButton}>
    <Txt style={{fontSize:23}}>🎒</Txt>
   </Pressable>
   <Pressable accessibilityRole="button" accessibilityLabel={'Notifications, '+saved.notificationInbox.filter(n=>!n.read).length+' unread'} onPress={()=>{notifications.check();setInboxOpen(true);}} style={styles.headerButton}><Txt style={{fontSize:23}}>🔔</Txt>{saved.notificationInbox.some(n=>!n.read)?<View style={styles.unreadDot}/>:null}</Pressable>
   <Pressable accessibilityRole="button" accessibilityLabel="Open settings" onPress={()=>setSecondary('settings')} style={styles.headerButton}><Txt style={{fontSize:23}}>⚙️</Txt></Pressable>
  </View>):null}

{state.notice?<Pressable accessibilityRole="button" onPress={()=>act({type:'dismiss'})} style={styles.notice}><Txt style={styles.small}>{state.notice}  ✕</Txt></Pressable>:null}
  <ScrollView key={secondary||tab} scrollEnabled={desktop||windowHeight<700||contentHeight<575||Boolean(secondary)||tab!=='play'} onLayout={event=>{const height=event.nativeEvent.layout.height;if(height>0)setContentHeight(height);}} style={styles.scroll} contentContainerStyle={[styles.content,(secondary==='settings'||['play','care','roster'].includes(tab)&&!secondary)&&{paddingTop:2,paddingBottom:0,paddingHorizontal:secondary==='settings'?16:tab==='roster'?12:14,gap:0},{maxWidth:desktop?undefined:width>650?670:undefined,
    paddingHorizontal:desktop?20:undefined,
    paddingVertical:desktop?14:undefined}]} keyboardShouldPersistTaps="handled">
   {secondary&&secondary!=='settings'&&secondary!=='exchange'&&secondary!=='account-details'&&secondary!=='inventory'?<View style={styles.back}><Button compact secondary onPress={()=>setSecondary(null)}>← Back to {TABS.find(x=>x[0]===tab)?.[2]}</Button><Txt style={styles.heading}>{secondary.toUpperCase()}</Txt></View>:null}
   {!state.ready?<Card title={betaMode?'Loading Pup Account…':'Loading local guest data…'}><Txt muted>{betaMode?'Checking signed cloud progress.':'Reading device cache.'}</Txt></Card>:!g.onboardingComplete?<OnboardingScreen g={saved} act={act} notifications={notifications}/>:<>{secondary==='casino'?<Wallet game={g}/>:null}{main()}</>}
  </ScrollView>
  {!desktop&&state.ready&&g.onboardingComplete&&!secondary?<View testID="game-bottom-bar" style={[styles.bottomBar,g.playLayoutV2Enabled&&{backgroundColor:C.card,borderTopColor:C.accentSoft}]}>
   {TABS.map(([id,icon,label])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:tab===id&&!secondary}} testID={'android-main-tab-'+id} accessibilityLabel={label} onPress={()=>openScreen(id)} style={[styles.tab,(tab===id&&!secondary)&&styles.tabSelected]}>
    <Txt style={styles.tabEmoji}>{icon}</Txt><Txt style={[styles.tabLabel,(tab===id&&!secondary)&&{color:C.accentInk}]}>{label}</Txt>
   </Pressable>)}
  </View>:null}
   </View>
  </View>
  <NotificationInbox visible={inboxOpen} g={saved} act={act} onClose={()=>setInboxOpen(false)} onNavigate={openScreen} serverBacked={betaMode}/>
  {betaMode?<BetaAccountEntry visible={accountOpen} initialMode={accountEntryMode} onVerified={refreshAccount} onClose={()=>setAccountOpen(false)}/>:null}
 </PuppyPointer>;
}
const styles=themedStyles(()=>({
 restoredRoster:{gap:5,width:'100%'},restoredRosterHeader:{flexDirection:'row',alignItems:'center',gap:6,marginBottom:5},restoredRosterFilters:{flexDirection:'row',gap:6},
 rosterOutline:{minHeight:34,borderRadius:18,borderWidth:1,borderColor:C.muted,paddingHorizontal:10,paddingVertical:5,alignItems:'center',justifyContent:'center'},rosterControlText:{fontSize:12,fontWeight:'700',color:C.muted},rosterActionText:{fontSize:12,fontWeight:'700',color:C.accentInk},rosterSelectedText:{color:C.text},rosterStatus:{flex:1,minHeight:34,borderRadius:7,borderWidth:1,borderColor:C.muted,paddingHorizontal:14,justifyContent:'center'},rosterStatusSelected:{backgroundColor:C.dark?'#494457':'#E7E1EF',borderColor:'transparent'},rosterCategory:{minHeight:34,paddingHorizontal:15,borderRadius:7,borderWidth:1,borderColor:C.muted,justifyContent:'center'},rosterSearch:{minHeight:56,borderRadius:22,borderWidth:1,borderColor:C.muted,color:C.text,paddingHorizontal:14,fontSize:16},
 restoredSelectedPanel:{backgroundColor:C.accentSoft,borderRadius:14,padding:6,gap:3,marginVertical:3},restoredSelectedRow:{flexDirection:'row',gap:7,alignItems:'center'},rosterSelectedName:{fontSize:14,fontWeight:'900'},rosterMeta:{fontSize:11,color:C.text,fontWeight:'600'},rosterCurrent:{fontSize:11,color:C.accentInk,fontWeight:'900'},rosterHeart:{width:32,height:32,alignItems:'center',justifyContent:'center'},rosterHeartText:{fontSize:25,color:C.muted},rosterOptions:{backgroundColor:C.surface,borderRadius:12,borderColor:C.line,borderWidth:1},rosterOption:{minHeight:36,padding:8},
 restoredRosterGrid:{flexDirection:'row',flexWrap:'wrap',gap:7},restoredRosterTile:{width:'31.8%',minWidth:0,alignItems:'center',paddingHorizontal:5,paddingVertical:8,gap:3,borderRadius:18,borderWidth:2,borderColor:'transparent',backgroundColor:C.surface},rosterTileName:{fontSize:16,fontWeight:'900',textAlign:'center',maxWidth:'100%'},rosterGroupBadge:{maxWidth:'100%',borderRadius:14,backgroundColor:C.dark?'#494457':'#E7E1EF',paddingHorizontal:5,paddingVertical:2},rosterRetry:{position:'absolute',right:0,bottom:0,width:28,height:28,justifyContent:'center',alignItems:'center'},

 restoredCare:{width:'100%'},restoredCareIdentity:{flexDirection:'row',alignItems:'center',gap:10,paddingHorizontal:12,borderRadius:18,borderWidth:1,borderColor:C.line,backgroundColor:C.accentSoft},
 restoredCarePanel:{paddingHorizontal:11,borderRadius:18,borderWidth:1,borderColor:C.line,backgroundColor:C.surface},compactCareMeter:{flexDirection:'row',alignItems:'center',paddingVertical:3},compactCareLabel:{width:92,fontSize:12,fontWeight:'700'},compactCareTrack:{flex:1,height:5,borderRadius:9,backgroundColor:C.dark?'#494457':'#E7E1EF',overflow:'hidden'},compactCareFill:{height:'100%',backgroundColor:C.accent,borderRadius:9},compactCareValue:{width:38,marginLeft:8,fontSize:12,fontWeight:'900'},restoredCareDivider:{height:1,backgroundColor:C.line,marginVertical:5},
 restoredCareRow:{flexDirection:'row',gap:6},restoredCareAction:{flex:1,minWidth:0,minHeight:74,backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line,paddingVertical:6,paddingHorizontal:4,alignItems:'center',justifyContent:'center',gap:2},restoredCareActionTitle:{fontSize:14,fontWeight:'900',textAlign:'center'},restoredCareActionDetail:{fontSize:11,fontWeight:'700',textAlign:'center'},

 restoredPlay:{width:'100%',position:'relative'},
 restoredStage:{flex:1,minHeight:0,borderRadius:22,borderWidth:1,paddingHorizontal:10,paddingVertical:6,alignItems:'center',justifyContent:'center',gap:3},
 restoredEnergy:{fontSize:12,color:C.muted,fontWeight:'700',textAlign:'center',marginVertical:2,width:'100%'},
 restoredTap:{width:'100%',borderRadius:18,backgroundColor:C.accent,alignItems:'center',justifyContent:'center',paddingHorizontal:8},
 restoredNextReward:{flexDirection:'row',alignItems:'center',gap:8,paddingHorizontal:11,paddingVertical:8,borderRadius:14,backgroundColor:C.accentSoft,borderColor:C.line,borderWidth:1},

 headerButton:{width:40,height:40,borderRadius:21,borderColor:C.text,borderWidth:1,alignItems:'center',justifyContent:'center',marginLeft:4},unreadDot:{position:'absolute',right:1,top:0,width:9,height:9,borderRadius:5,backgroundColor:'#FFB4AB'},
 ticketOverlay:{position:'absolute',top:138,left:12,right:12,zIndex:30,backgroundColor:C.surface,borderColor:C.accent,borderWidth:1,borderRadius:18,paddingHorizontal:12,paddingVertical:12,alignItems:'center',elevation:8},
 ticketOverlayTitle:{fontSize:15,fontWeight:'900',color:C.text,textAlign:'center'},
 ticketOverlayDescription:{fontSize:11,color:C.muted,textAlign:'center',marginTop:3},

 casinoGameTile:{flexDirection:'row',alignItems:'center',gap:12,padding:12,backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line},
 casinoTileArt:{width:80,height:80},
 casinoTileArrow:{fontSize:28,color:C.accentInk},
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
 // Kotlin PuppyBlackjackUi.kt: compact casino header, table and two/one-row actions.
 blackjackScreen:{gap:8,width:'100%',paddingHorizontal:2},
 blackjackHeader:{flexDirection:'row',alignItems:'center',gap:10},
 blackjackWallet:{paddingHorizontal:12,paddingVertical:8,borderRadius:12,backgroundColor:C.surface},
 blackjackTableGroup:{gap:6,alignItems:'center',width:'100%'},
 blackjackHandSelector:{flexDirection:'row',gap:4,width:'100%'},
 blackjackHandChip:{flex:1,minWidth:0,paddingVertical:9,paddingHorizontal:5,alignItems:'center',borderRadius:17,backgroundColor:C.surface},
 blackjackHandChipSelected:{backgroundColor:C.accentSoft,borderWidth:1,borderColor:C.accent},
 blackjackHandChipLabel:{fontSize:12,fontWeight:'800',color:C.text},
 blackjackCardCorner:{position:'absolute',top:2,left:3,gap:0},
 blackjackCardRank:{fontWeight:'900',lineHeight:14},
 blackjackCardPip:{position:'absolute',alignSelf:'center',top:'29%',fontWeight:'900'},
 blackjackTableChip:{position:'absolute',width:14,height:14,alignSelf:'center',bottom:'17%',borderRadius:7,backgroundColor:'#FFCF4A',alignItems:'center',justifyContent:'center'},
 blackjackTableChipCenter:{width:8,height:8,borderRadius:4,backgroundColor:'#8F5425'},
 blackjackSummary:{gap:4,paddingHorizontal:10,paddingVertical:8,borderRadius:12,borderWidth:1,borderColor:'#FFCF4A',backgroundColor:C.surface},
 blackjackWinSummary:{backgroundColor:C.dark?'#25633E':'#DCF2E1'},
 blackjackActions:{gap:BLACKJACK_V6.actionRowGap},
 blackjackActionRow:{flexDirection:'row',gap:BLACKJACK_V6.actionGap},
 blackjackAction:{flex:1,minWidth:0,minHeight:BLACKJACK_V6.actionHeight,borderRadius:12,alignItems:'center',justifyContent:'center',paddingHorizontal:4,paddingVertical:8},
 blackjackActionText:{fontSize:12,fontWeight:'900',textAlign:'center'},
 blackjackWagerRow:{flexDirection:'row',flexWrap:'wrap',gap:6},
 blackjackRules:{backgroundColor:C.surface,borderRadius:18,padding:14,gap:6,borderWidth:1,borderColor:C.line},
  blackjackFelt:{width:'100%',aspectRatio:1122/1188,borderRadius:22,overflow:'hidden',justifyContent:'space-between',paddingVertical:'16%'},
 blackjackDealerArea:{alignItems:'center',gap:5,minHeight:95},
 blackjackPlayerArea:{alignItems:'center',gap:5,minHeight:105},
 blackjackFeltTitle:{color:'#FCFAE9',fontWeight:'900',fontSize:12,textShadowColor:'#1E392C',textShadowRadius:4},
 blackjackFan:{flexDirection:'row',alignItems:'center',justifyContent:'center',gap:3,minHeight:68},
 blackjackArtworkCard:{height:66,width:47,justifyContent:'center',alignItems:'center'},
 blackjackCardLabel:{fontSize:15,fontWeight:'900',color:'#1C2424'},

 dailyGoalCard:{flexDirection:'row',gap:12,padding:14,borderRadius:18,backgroundColor:C.surface,borderWidth:1,borderColor:C.line,alignItems:'flex-start'},
 nextRewardCard:{flexDirection:'row',alignItems:'center',gap:10,padding:12,backgroundColor:C.accentSoft,borderColor:C.line,borderWidth:1,borderRadius:14},
 dailyGiftCard:{flexDirection:'row',gap:10,alignItems:'center',padding:14,borderRadius:19,backgroundColor:C.accentSoft,borderColor:C.line,borderWidth:1},
 streakCard:{flexDirection:'row',alignItems:'center',gap:12,padding:16,backgroundColor:C.accentSoft,borderRadius:22,borderWidth:1,borderColor:C.line},
 streakDot:{height:24,width:24,justifyContent:'center',alignItems:'center',borderRadius:20,backgroundColor:C.card,borderWidth:1,borderColor:C.line},
 streakDotFilled:{backgroundColor:C.accent,borderColor:C.accent},
 claimedCard:{flexDirection:'row',alignItems:'center',gap:10,padding:12,borderRadius:16,backgroundColor:C.surface,borderColor:C.line,borderWidth:1},
 claimedText:{fontSize:11,fontWeight:'900',color:C.accentInk},

 selectedPuppyPanel:{flexDirection:'row',alignItems:'center',gap:14,backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:18,padding:12},

 prestigeCenter:{backgroundColor:C.accentSoft,borderRadius:24,padding:16,gap:11,borderWidth:1,borderColor:C.line},
 prestigeTitle:{flexDirection:'row',alignItems:'center',gap:9},
 settingsGroupTitle:{fontSize:12,fontWeight:'900',letterSpacing:1,color:C.muted,marginTop:7,marginBottom:1},
 settingsSection:{backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:17,overflow:'hidden'},
 settingsSectionHeader:{flexDirection:'row',alignItems:'center',gap:11,paddingHorizontal:14,paddingVertical:14},
 settingsSectionBody:{gap:10,paddingHorizontal:14,paddingBottom:14},
 settingsSectionIcon:{fontSize:24,width:30,textAlign:'center'},
 settingsChevron:{fontSize:22,fontWeight:'700',color:C.muted},
 previewPill:{fontSize:10,fontWeight:'800',color:C.gold,backgroundColor:C.dark?'#352B13':'#FFF0C7',paddingHorizontal:8,paddingVertical:6,borderRadius:10,overflow:'hidden'},

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
 shopDiscountLabel:{color:C.accentInk,fontWeight:'800',fontSize:13},
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
 careBond:{fontSize:14,color:C.accentInk,fontWeight:'800'},
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
 needPillLow:{backgroundColor:C.dark?'#431F26':'#FFE3E8'},
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
 filterTextSelected:{color:C.accentInk},
 activePuppy:{flexDirection:'row',alignItems:'center',gap:12,padding:12,backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line},
 sectionTitle:{fontSize:17,fontWeight:'800',color:C.text,marginTop:6},
 upgradeCard:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',backgroundColor:C.surface,borderRadius:18,borderWidth:1,borderColor:C.line,paddingHorizontal:12,paddingVertical:16,gap:8},

 root:{flex:1,backgroundColor:C.bg,paddingTop:StatusBar.currentHeight||0},
 text:{fontSize:14,color:C.text},muted:{color:C.muted},small:{fontSize:12},tiny:{fontSize:10,color:C.muted},tinyCenter:{fontSize:10,color:C.muted,textAlign:'center'},
 center:{textAlign:'center',fontSize:12},heading:{fontSize:17,fontWeight:'800',letterSpacing:0.25},brand:{fontSize:17,fontWeight:'900',letterSpacing:0.8},value:{fontWeight:'800'},itemName:{fontWeight:'700',fontSize:14},
 topBar:{height:48,paddingHorizontal:10,flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:4,backgroundColor:C.surface},
 topTitles:{flex:1,gap:2},guestTag:{fontSize:10,fontWeight:'800',color:C.gold,backgroundColor:C.dark?'#352B13':'#FFF0C7',paddingVertical:6,paddingHorizontal:8,borderRadius:8,overflow:'hidden'},
 scroll:{flex:1},content:{padding:14,paddingBottom:30,width:'100%',alignSelf:'center',gap:12},
 card:{backgroundColor:C.surface,borderWidth:1,borderColor:C.line,borderRadius:20,padding:15,gap:10},
 cardTitle:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8,flexWrap:'wrap'},
 wallet:{flexDirection:'row',backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:20,paddingHorizontal:8,paddingVertical:11,alignItems:'center'},
 walletCell:{flex:1,minWidth:0,alignItems:'center',justifyContent:'center',gap:4},
 walletEmoji:{fontSize:18},
 walletLabel:{fontSize:11,color:C.muted,textAlign:'center'},
 walletNumber:{fontSize:16,fontWeight:'900',color:C.text,textAlign:'center'},
 bigTap:{alignItems:'center',justifyContent:'center',backgroundColor:C.accentSoft,borderRadius:26,minHeight:286,padding:12,gap:8,borderWidth:1,borderColor:'#23516A'},
 portrait:{backgroundColor:'transparent',alignItems:'center',justifyContent:'center'},
 tapCaption:{fontSize:13,color:C.accentInk,fontWeight:'800',letterSpacing:0.5},
 statsRow:{flexDirection:'row',justifyContent:'space-between'},
 meter:{gap:4},meters:{gap:10},
 track:{height:8,backgroundColor:C.dark?'#2B3140':'#D5D9E2',borderRadius:8,overflow:'hidden'},fill:{height:'100%',borderRadius:8},
 pair:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:9},actions:{flexDirection:'row',gap:7,flexWrap:'wrap',alignItems:'center'},
 actionGrid:{flexDirection:'row',flexWrap:'wrap',justifyContent:'space-between',gap:12},half:{width:'47.5%',gap:4},
 button:{backgroundColor:C.accent,borderRadius:11,minHeight:40,paddingHorizontal:12,paddingVertical:10,alignItems:'center',justifyContent:'center',flexGrow:1},
 buttonSecondary:{backgroundColor:C.card,borderWidth:1,borderColor:C.line},buttonDisabled:{opacity:0.4},buttonCompact:{minHeight:36,paddingHorizontal:9,paddingVertical:7,flexGrow:0},
 buttonDanger:{borderColor:C.danger},buttonText:{fontWeight:'800',fontSize:12,textAlign:'center',color:C.onAccent},
 shopItem:{paddingTop:10,borderTopWidth:1,borderColor:C.line,flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8},
 shopLabel:{flex:1,gap:3},roster:{flexDirection:'row',flexWrap:'wrap',gap:8,justifyContent:'space-between'},
 rosterPup:{width:'31%',alignItems:'center',gap:4,backgroundColor:C.card,padding:7,borderRadius:13,borderWidth:1,borderColor:C.line},
 rosterSelected:{borderColor:C.accent},wager:{backgroundColor:C.card,padding:10,borderRadius:10,borderWidth:1,borderColor:C.line},wagerSelected:{borderColor:C.accent,backgroundColor:C.accentSoft},
 notice:{padding:10,paddingHorizontal:16,backgroundColor:C.accentSoft},
 bottomBar:{flexDirection:'row',paddingTop:8,paddingBottom:8,backgroundColor:C.surface,borderTopWidth:1,borderTopColor:C.line},
 tab:{flex:1,alignItems:'center',justifyContent:'center',paddingVertical:6,borderRadius:10,gap:3},
 tabSelected:{backgroundColor:C.accentSoft},tabEmoji:{fontSize:18},tabLabel:{fontSize:10,fontWeight:'800',color:C.muted},
 input:{borderWidth:1,borderColor:C.line,backgroundColor:C.card,borderRadius:10,padding:10,fontSize:15,color:C.text},
 back:{flexDirection:'row',alignItems:'center',gap:8,flexWrap:'wrap'},
 // Match the website's existing kawaii.css / native App.jsx presentation.
 // All overrides are Android-only; browser CSS and website code are unchanged.
 topBar:{minHeight:52,paddingHorizontal:12,paddingVertical:7,flexDirection:'row',
  justifyContent:'space-between',alignItems:'center',gap:8,
  backgroundColor:C.surface,borderBottomWidth:1,borderBottomColor:C.line},
 brand:{fontSize:17,fontWeight:'800',color:C.text},
 card:{backgroundColor:C.surface,borderWidth:1,borderColor:C.line,
  borderRadius:22,padding:16,gap:12},
 button:{backgroundColor:C.accent,borderRadius:999,minHeight:46,
  paddingHorizontal:18,paddingVertical:12,alignItems:'center',
  justifyContent:'center',flexGrow:1},
 buttonSecondary:{backgroundColor:C.surface,borderWidth:1,borderColor:C.line},
 buttonCompact:{minHeight:37,paddingHorizontal:13,paddingVertical:8,flexGrow:0},
 buttonText:{fontWeight:'700',fontSize:14,textAlign:'center',color:C.onAccent},
 bottomBar:{flexDirection:'row',paddingHorizontal:8,paddingVertical:6,
  backgroundColor:C.surface,borderTopWidth:1,borderTopColor:C.line,gap:4},
 tab:{flex:1,alignItems:'center',justifyContent:'center',paddingVertical:8,borderRadius:20,gap:4},
 tabEmoji:{fontSize:20},tabLabel:{fontSize:11,fontWeight:'700',color:C.muted},
 wallet:{flexDirection:'row',backgroundColor:C.surface,borderColor:C.line,
  borderWidth:1,borderRadius:22,paddingHorizontal:8,paddingVertical:12,
  alignItems:'center'},
 walletEmoji:{fontSize:21},walletNumber:{fontSize:16,fontWeight:'800',
  color:C.text,textAlign:'center'},
 walletLabel:{fontSize:10,color:C.muted,textAlign:'center'},
 pageTitle:{fontSize:27,fontWeight:'800',letterSpacing:-.5,color:C.text},
 pageSubtitle:{fontSize:13,color:C.muted,marginTop:4},
 shopUpgradeCard:{backgroundColor:C.surface,borderRadius:22,padding:16,
  gap:10,borderWidth:1,borderColor:C.line},
 rosterSelected:{borderColor:C.accent},
 restoredSelectedPanel:{backgroundColor:C.accentSoft,borderRadius:24,
  borderWidth:1,borderColor:C.accent,padding:13,gap:10,marginVertical:4},
 input:{borderWidth:1,borderColor:C.line,backgroundColor:C.card,
  borderRadius:18,padding:14,fontSize:15,color:C.text}
}));
// Website-owned screen functions are generated from src/native/App.jsx; do not duplicate their JSX here.
const {CareScreen,ShopScreen}=createWebsiteCareShopScreens({ROSTER,careTapBonus,mood,wellness,canPerformCare,Txt,styles,C,PageHeader,Portrait,upgradesForShopTab,Wallet,Button,RARITIES,accessoryPrices,accessoryOffer,v6Format,TICKET_RARITY_PRESENTATION,ticketShopOffer,MAX_UPGRADES_PER_ITEM,upgradeOffer,Card});
// Native cloud authorities use the Android calendar day; this adapter prevents
// UI-only website date logic from changing signed reward transactions.
const nativeRewardDate=timestamp=>new Date(timestamp);
const nativeRewardEpoch=timestamp=>localEpochDay(new Date(timestamp));
const {DailyGoalCard,RewardsScreen}=createWebsiteRewardsScreens({
 Txt,styles,C,PageHeader,Button,Card,format,goalsForDay,monthKey,level,
 MAX_PLAYER_LEVEL,MAX_PLAYER_XP,nextXpTarget,levelXp,MAX_REWARD_STREAK_DAYS,
 achievementStatuses,parkPreview,ACHIEVEMENTS,webGoalState,
 webServerRewardsDate:nativeRewardDate,webServerEpochDay:nativeRewardEpoch,
 useLanguage:useWebsiteLanguage
});
const {PrestigeScreen,RosterScreen}=createWebsiteRosterPrestigeScreens({
 ROSTER,LEGACY_V6_PUPPIES,legacyRosterSections,unlockedLegacyCount,
 PageHeader,Txt,Portrait,styles,C,Button,PuppyDetailsDialog,Card,suggestPuppyName,
 PRESTIGE_SKILLS,PRESTIGE_SKILL_PRESENTATION,prestigeOffer,canBuySkill,skillCost,v6Format
});
