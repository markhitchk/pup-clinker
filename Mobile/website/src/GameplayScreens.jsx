import React,{useRef,useState,useEffect} from "react";
import {webServerRewardsDate,webServerEpochDay,monthKey,goalsForDay} from "../../src/engine/monthlyRewards.js";
import {format,ROSTER,careTapBonus,mood,wellness,accessoryPrices,RARITIES,PRESTIGE_SKILLS,skillCost} from "../../src/engine/game.js";
import {View,Platform,Animated,Vibration,Alert,Pressable} from "react-native";
import {colors as C} from "../../src/theme.js";
import {revampedPlayLayout} from "../../src/engine/playRevamp.js";
import {v6CooldownSeconds,v6Format} from "../../src/engine/playV6.js";
import {canPerformCare} from "../../src/engine/careV6.js";
import {upgradesForShopTab,accessoryOffer,ticketShopOffer,TICKET_RARITY_PRESENTATION,upgradeOffer,prestigeOffer,PRESTIGE_SKILL_PRESENTATION,canBuySkill} from "../../src/engine/shopV6.js";
import {MAX_UPGRADES_PER_ITEM} from "../../src/engine/balancedProgression.js";
import {GameHost,RenamePuppyDialog} from '../../src/components/GameHost.jsx';
import {PuppyPlatform} from './PuppyPlatform.jsx';
import {webGoalState} from './webGoalState.js';
import {nextPlayReward} from './playRevamp.js';
export function createGameplayScreens({Wallet,Portrait,Txt,Button,Card,PageHeader,styles}){
function DailyGoalCard({g,goal,act,clock,onOpen,readOnly=false,compact=false,pendingTaps=0}){
 const date=webServerRewardsDate(clock),today=webServerEpochDay(clock);
 const claimed=g.dailyDay===today&&g.claimedDailyTasks.includes(goal.id);
 const eligibility=webGoalState(g,goal,date);
 const {progress,complete,status,destination,continueLabel,maxedShop}=eligibility;
 const hint=readOnly?'Cloud progress must finish loading before rewards can be claimed.':status;
 const syncing=goal.metric==='taps'&&pendingTaps>0?
  ' · '+pendingTaps+' tap'+(pendingTaps===1?'':'s')+' awaiting cloud confirmation':'';
 const description=maxedShop?'All upgrades are maxed — your Shop Visit goal is complete.':goal.description;
 const label=readOnly?'Cloud unavailable':complete?'Claim Reward':continueLabel;
 const unavailable=readOnly||(!complete&&typeof onOpen!=='function');
 const activate=()=>{if(readOnly)return;
  if(complete)act({type:'claimDailyGoal',goalId:goal.id,now:clock});
  else onOpen?.(destination);
 };
 if(compact)return <GameHost.article className="compact-goal reference-goal"><GameHost.span className="goal-illustration" aria-hidden="true">{goal.emoji}</GameHost.span><GameHost.div className="reference-goal-body"><GameHost.div className="compact-goal-heading"><GameHost.strong>{goal.title}</GameHost.strong><GameHost.span>{Math.min(progress,goal.target)}/{goal.target}</GameHost.span></GameHost.div><GameHost.p className="goal-description">{description}</GameHost.p><GameHost.div className="cyber-progress" role="progressbar" aria-label={goal.title} aria-valuemin={0} aria-valuemax={goal.target} aria-valuenow={Math.min(progress,goal.target)}><GameHost.span style={{width:Math.min(100,100*progress/goal.target)+'%'}}/></GameHost.div><GameHost.p className="goal-progress-hint">{claimed?'Already claimed.':hint+syncing}</GameHost.p><GameHost.div className="goal-footer"><GameHost.p className="goal-reward">Reward: {format(goal.rewardTreats)} 🍪 + 1 🦴 + 1 🪙</GameHost.p>{claimed?<GameHost.span className="claimed">✓ Claimed</GameHost.span>:<GameHost.button className={'cyber-button '+(complete?'primary':'secondary')} disabled={unavailable} onClick={activate}>{label}</GameHost.button>}</GameHost.div></GameHost.div></GameHost.article>;

 return <View style={styles.dailyGoalCard}>
  <Txt style={{fontSize:28}}>{goal.emoji}</Txt>
  <View style={{flex:1,gap:6}}>
   <View style={styles.pair}><Txt style={styles.itemName}>{goal.title}</Txt><Txt style={styles.value}>{Math.min(progress,goal.target)}/{goal.target}</Txt></View>
   <Txt muted style={styles.small}>{description}</Txt>
   <View style={styles.track}><View style={[styles.fill,{backgroundColor:C.accent,width:Math.min(100,100*progress/goal.target)+'%'}]}/></View>
   {!claimed?<Txt muted style={styles.small}>{hint+syncing}</Txt>:null}
   <View style={styles.pair}><Txt muted style={styles.small}>Reward: {format(goal.rewardTreats)} 🍪 + 1 🦴 + 1 🪙</Txt>
    {claimed?<Txt style={styles.claimedText}>✓ Claimed</Txt>:<Button compact secondary={!complete} disabled={unavailable} onPress={activate}>{label}</Button>}
   </View>
  </View>
 </View>;
}
function PlayScreen({g,act,clock,schedule,viewportHeight,desktop=false,onOpen,pendingTaps=0}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const layout={...revampedPlayLayout(viewportHeight),portrait:desktop?260:220};
 const next=nextPlayReward(g,schedule,clock,{serverDay:true}),cooldown=v6CooldownSeconds(g,clock),bonus=careTapBonus(g);
 const animate=g.animatedUi!==false&&g.tapAnimationsEnabled!==false;
 const tapScale=useRef(new Animated.Value(1)).current;
 const rotation=useRef(new Animated.Value(0)).current;
 const lift=useRef(new Animated.Value(0)).current;
 const [ticketVisible,setTicketVisible]=useState(false);
 const [renameOpen,setRenameOpen]=useState(false);
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
  act({type:'tap',now:Date.now()});
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
 const label=cooldown?'Fair-play cooldown · '+cooldown+'s':g.energy<=10?g.puppyName+' is tired · Care needed':'Tap '+g.puppyName;
 return <GameHost.section className={'cyber-play '+(desktop?'desktop-play':'mobile-play')} data-reduced-motion={!g.idleAnimationsEnabled}>
  <GameHost.div className="play-heading"><GameHost.div><GameHost.h1>Play</GameHost.h1><GameHost.p>Tap your puppy and earn treats.</GameHost.p></GameHost.div><GameHost.button className="cyber-button secondary small switch-puppy-action" onClick={()=>onOpen('roster')}>⇄ Switch Puppy</GameHost.button></GameHost.div>
  {!desktop?<Wallet game={g}/>:<GameHost.div className="puppy-identity"><GameHost.div className="identity-portrait"><Portrait puppy={pup} size={64} accessory={g.accessory}/></GameHost.div><GameHost.div><GameHost.strong>{g.puppyName}</GameHost.strong><GameHost.p>{pup.id.startsWith('v2_')?pup.id:'v1_'+pup.id} · {pup.id.startsWith('v2_')?'V2':'V1'} Puppies</GameHost.p><GameHost.span className="current-puppy-badge">Current Puppy</GameHost.span><GameHost.button className="cyber-button secondary small" aria-label="Rename current puppy" onClick={()=>setRenameOpen(true)}>✎ Rename</GameHost.button></GameHost.div></GameHost.div>}
  {renameOpen?<RenamePuppyDialog name={g.puppyName} onClose={()=>setRenameOpen(false)} onSave={name=>{act({type:'rename',name});setRenameOpen(false);}}/>:null}
  <GameHost.div className="cyber-play-stage">
   <PuppyPlatform name={g.puppyName}>
    <Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'} accessibilityState={{disabled:cooldown>0}} disabled={cooldown>0} onPress={doTap}>
     <Animated.View style={{transform:[{scale:tapScale},{rotate:rotation.interpolate({inputRange:[-4,4],outputRange:['-4deg','4deg']})},{translateY:lift}]}}>
      <Portrait puppy={pup} size={layout.portrait} accessory={g.accessory}/>
     </Animated.View>
    </Pressable>
   </PuppyPlatform>
   <GameHost.div className="play-energy"><GameHost.span>⚡ <GameHost.strong>Energy {g.energy}%</GameHost.strong></GameHost.span><GameHost.span>💖 Care bonus <GameHost.strong>+{bonus} 🍪 / tap</GameHost.strong></GameHost.span><GameHost.span className="tap-tip">ⓘ Tap to earn treats!</GameHost.span></GameHost.div>
   <GameHost.button disabled={cooldown>0} onClick={doTap} className="cyber-tap"><GameHost.span aria-hidden="true">🐾</GameHost.span><GameHost.span><GameHost.strong>{label}</GameHost.strong>{!cooldown&&g.energy>10?<GameHost.small>+{format(g.clickPower+bonus)}{desktop?'':' treats'}</GameHost.small>:null}</GameHost.span></GameHost.button>
  </GameHost.div>
  <GameHost.div className="cyber-panel next-reward"><GameHost.span className="reward-icon" aria-hidden="true">🎁</GameHost.span><GameHost.div className="flex-1 min-w-0"><GameHost.div className="reward-heading"><GameHost.strong>{next.title}</GameHost.strong><GameHost.strong>{next.allClaimed?'✓':next.progress+'/'+next.target}</GameHost.strong></GameHost.div><GameHost.div className="cyber-progress" role="progressbar" aria-label="Next reward progress" aria-valuemin={0} aria-valuemax={next.target} aria-valuenow={next.progress}><GameHost.span style={{width:(100*next.progress/next.target)+'%'}}/></GameHost.div><GameHost.p>{next.allClaimed?'All goals claimed. See you tomorrow!':pendingTaps>0?pendingTaps+' tap'+(pendingTaps===1?' is':'s are')+' syncing. Progress is pending cloud confirmation.':next.progress>=next.target?'Reward ready — open Rewards to claim.':'Keep playing to reach your next reward.'}</GameHost.p></GameHost.div><GameHost.strong className="reward-value">{next.allClaimed?'All claimed':format(next.rewardTreats)+' 🍪'}</GameHost.strong></GameHost.div>
  {ticketVisible?<View pointerEvents="none" style={[styles.ticketOverlay,{top:2}]}><Txt style={styles.ticketOverlayTitle}>🎟️ {g.lastTicketDrop} UPGRADE TICKET</Txt></View>:null}
 </GameHost.section>;
}
function DesktopPlayScreen({g,act,clock,schedule,viewportHeight,onOpen,pendingTaps=0}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 return <GameHost.div className="desktop-dashboard desktop-reference">
  <PlayScreen g={g} act={act} clock={clock} schedule={schedule} viewportHeight={viewportHeight} onOpen={onOpen} pendingTaps={pendingTaps} desktop/>
  <GameHost.aside className="dashboard-panels">
   <GameHost.section className="cyber-panel"><GameHost.h2>Your Resources</GameHost.h2><Wallet game={g}/></GameHost.section>
   <GameHost.section className="cyber-panel daily-goals-panel compact-goals"><GameHost.div className="panel-heading"><GameHost.h2>Today’s goals</GameHost.h2><GameHost.span>{monthKey(webServerRewardsDate(clock))}</GameHost.span><GameHost.button className="cyber-button secondary all-daily-goals" aria-label="🎁 Rewards & daily goals" onClick={()=>onOpen('rewards')}>All goals →</GameHost.button></GameHost.div>{goalsForDay(schedule,new Date(clock)).slice(0,3).map(goal=><DailyGoalCard key={goal.id} g={g} goal={goal} act={act} clock={clock} onOpen={onOpen} compact/>)}</GameHost.section>
   <GameHost.section className="cyber-panel care-summary compact-care reference-care"><GameHost.div className="panel-heading"><GameHost.h2>Pup Care Status</GameHost.h2><GameHost.button className="cyber-button secondary small" onClick={()=>onOpen('care')}>Go to Care →</GameHost.button></GameHost.div><GameHost.div className="care-summary-content"><GameHost.div className="care-avatar"><Portrait puppy={pup} size={76} accessory={g.accessory}/></GameHost.div><GameHost.div className="care-meter-detail"><GameHost.strong className="care-puppy-name">{g.puppyName}</GameHost.strong><GameHost.p className="care-summary-caption">{mood(g)} · Wellness {wellness(g)}% · Bond {g.bond}%</GameHost.p>{[['💖','Happiness',g.happiness],['🍗','Fullness',g.fullness],['⚡','Energy',g.energy],['🫧','Cleanliness',g.cleanliness],['🤝','Bond',g.bond]].map(([icon,label,value])=><GameHost.div className="web-care-row" key={label}><GameHost.span>{icon} {label}</GameHost.span><GameHost.div className="cyber-progress" role="progressbar" aria-label={label+' need'} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value)}><GameHost.span style={{width:Math.max(0,Math.min(100,value))+'%'}}/></GameHost.div><GameHost.strong>{Math.round(value)}%</GameHost.strong></GameHost.div>)}</GameHost.div><GameHost.aside className="care-bonus-box"><GameHost.span>✨ {careTapBonus(g)===2?'Thriving':careTapBonus(g)===1?'Healthy':'Pup'} care bonus</GameHost.span><GameHost.p>+{careTapBonus(g)} Treats on every normal puppy tap.</GameHost.p></GameHost.aside></GameHost.div></GameHost.section>
  </GameHost.aside>
 </GameHost.div>;
}
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
return {DailyGoalCard,PlayScreen,DesktopPlayScreen,CareScreen,ShopScreen,PrestigeScreen};
}
