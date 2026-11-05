// GENERATED, DO NOT EDIT. Source: markhitchk/Puppy-Clicker/src/native/App.jsx
// Website-owned Rewards components. The browser-only compact HTML goal renderer
// is excluded; native View/Pressable markup and signed game actions remain identical.
import React,{useState} from 'react';
import {Pressable,View} from 'react-native';

export function createWebsiteRewardsScreens({Txt,styles,C,PageHeader,Button,Card,format,goalsForDay,monthKey,level,MAX_PLAYER_LEVEL,MAX_PLAYER_XP,nextXpTarget,levelXp,MAX_REWARD_STREAK_DAYS,achievementStatuses,parkPreview,ACHIEVEMENTS,webGoalState,webServerRewardsDate,webServerEpochDay,useLanguage}){
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

function RewardsGroup({title,description,initiallyOpen=false,children}){
 const [open,setOpen]=useState(initiallyOpen);
 return <View style={{borderWidth:1,borderColor:C.line,borderRadius:20,backgroundColor:C.surface,padding:12,gap:10}}>
  <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{expanded:open}}
   onPress={()=>setOpen(value=>!value)}
   style={{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingVertical:8,gap:8}}>
   <View style={{flex:1}}><Txt style={styles.sectionTitle}>{title}</Txt>{description?<Txt muted style={styles.small}>{description}</Txt>:null}</View>
   <Txt style={{fontSize:23}}>{open?'−':'+'}</Txt>
  </Pressable>
  {open?<View style={{gap:12}}>{children}</View>:null}
 </View>;
}

function RewardsScreen({g,act,clock,schedule,onOpen,onPrestige,readOnly=false,pendingTaps=0}){
 const {t:tr}=useLanguage();
 const date=webServerRewardsDate(clock),today=webServerEpochDay(clock),goals=goalsForDay(schedule,date);
 const claimed=(goal)=>g.dailyDay===today&&g.claimedDailyTasks.includes(goal.id);
 const active=goals.filter(goal=>!claimed(goal)),completed=goals.filter(claimed);
 const giftClaimed=g.lastDailyClaimDay===today;
 const nextStreak=g.lastDailyClaimDay===today-1?Math.min(MAX_REWARD_STREAK_DAYS,g.dailyStreak+1):1;
 const giftReward=150+Math.min(MAX_REWARD_STREAK_DAYS,nextStreak)*25+level(g)*10;
 const statuses=achievementStatuses(g);
 const park=parkPreview(g,clock);
 return <View style={styles.gamePage}>
  <PageHeader title={tr("rewards")} subtitle={tr("rewardsSubtitle")}/>
  <RewardsGroup title={"⭐ "+tr("overview")} description={tr("playerOverview")} initiallyOpen>
  <View style={styles.dailyGiftCard}>
   <Txt style={{fontSize:30}}>⭐</Txt>
   <View style={{flex:1,gap:5}}>
    <Txt style={styles.itemName}>{tr("levelLabel")+' '+level(g)+' / '+MAX_PLAYER_LEVEL}</Txt>
    <Txt muted style={styles.small}>
     {level(g)>=MAX_PLAYER_LEVEL?tr("maxLevel")+' · XP '+MAX_PLAYER_XP.toLocaleString('en-US'):
      g.xp.toLocaleString('en-US')+' / '+nextXpTarget(g.xp).toLocaleString('en-US')+' XP'}
    </Txt>
    <View accessibilityRole="progressbar" accessibilityLabel="Player XP"
     accessibilityValue={{min:0,max:100,
      now:level(g)>=MAX_PLAYER_LEVEL?100:Math.min(100,Math.floor(100*(g.xp-levelXp(level(g)))/Math.max(1,nextXpTarget(g.xp)-levelXp(level(g))))) }}
     style={[styles.track,{height:7}]}>
     <View style={[styles.fill,{height:7,width:
      (level(g)>=MAX_PLAYER_LEVEL?100:Math.min(100,100*(g.xp-levelXp(level(g)))/Math.max(1,nextXpTarget(g.xp)-levelXp(level(g)))))+'%'}]}/>
    </View>
   </View>
  </View>
  <View style={styles.streakCard}>
   <Txt style={{fontSize:42}}>🔥</Txt>
   <View style={{flex:1,gap:8}}><Txt style={styles.heading}>{tr("streakDays",{count:g.dailyStreak})}</Txt>
    <Txt muted style={styles.small}>{tr("streakAnnual",{days:MAX_REWARD_STREAK_DAYS})}</Txt>
    <View style={{flexDirection:'row',gap:6}}>{Array.from({length:7},(_,index)=><View key={index} style={[styles.streakDot,index<g.dailyStreak&&styles.streakDotFilled]}><Txt style={styles.small}>{index<g.dailyStreak?'✓':''}</Txt></View>)}</View>
   </View>
  </View>
  </RewardsGroup>
  <RewardsGroup title={"🎁 "+tr("dailyRewards")} description={tr("dailyDetails")} initiallyOpen>
  {!giftClaimed?<View style={styles.dailyGiftCard}>
   <Txt style={{fontSize:32}}>🎁</Txt><View style={{flex:1,gap:5}}><Txt style={styles.itemName}>{readOnly?'Daily Puppy Gift · Not yet live':'Daily Puppy Gift'}</Txt><Txt muted style={styles.small}>{format(giftReward)} Treats + 2 Bones + 3 Pup Coins</Txt></View>
   <Button compact disabled={readOnly} onPress={()=>act({type:'daily',epochDay:today,now:clock})}>{readOnly?'Not yet live':'Claim'}</Button>
  </View>:null}
  <View style={styles.pair}><Txt style={styles.sectionTitle}>{tr("todaysGoals")}</Txt><Txt muted style={styles.small}>{monthKey(date)} · {readOnly?'Rewards not yet live':schedule?.source==='github'?'Live':schedule?.source==='cache'?'Cached':'Fallback'}</Txt></View>
  {active.length?active.map(goal=><DailyGoalCard g={g} goal={goal} act={act} clock={clock} onOpen={onOpen} pendingTaps={pendingTaps} readOnly={readOnly} key={goal.id}/>):<Card title="🎉 All of today’s goals claimed"/>}
  </RewardsGroup>
  <RewardsGroup title={"✓ "+tr("claimedToday")} description={tr("collectedDaily")} >
  {giftClaimed||completed.length?<><Txt style={styles.sectionTitle}>Claimed</Txt>
   {giftClaimed?<View style={styles.claimedCard}><Txt>🎁</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{tr("dailyGift")}</Txt><Txt muted style={styles.small}>Treats + 2 Bones + 3 Pup Coins</Txt></View><Txt style={styles.claimedText}>✓ {tr("claimed")}</Txt></View>:null}
   {completed.map(goal=><View style={styles.claimedCard} key={goal.id}><Txt style={{fontSize:22}}>{goal.emoji}</Txt><View style={{flex:1}}><Txt style={styles.itemName}>{goal.title}</Txt><Txt muted style={styles.small}>{goal.rewardTreats} Treats + 1 Bone + 1 Pup Coin</Txt></View><Txt style={styles.claimedText}>✓ {tr("claimed")}</Txt></View>)}
  </>:null}
  </RewardsGroup>
  <RewardsGroup title={"🌳 "+tr("adventures")} description={tr("adventuresDetail")} >
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>🌳</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Dog Park Adventure</Txt><Txt muted style={styles.small}>{park.detail}</Txt></View><Button compact disabled={readOnly||park.disabled} onPress={()=>act({type:park.ready?'parkClaim':'parkStart',now:Date.now()})}>{readOnly?'View only':park.label}</Button></View>
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>🎲</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Puppy Gacha</Txt><Txt muted style={styles.small}>Earn and unlock eligible puppies</Txt></View><Button compact secondary onPress={()=>onOpen('gacha')}>Open</Button></View>
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>🎰</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Puppy Casino</Txt><Txt muted style={styles.small}>Slots · Roulette · Blackjack · Plinko · Scratchers · Lucky Wheel</Txt></View><Button compact secondary onPress={()=>onOpen('casino')}>Open</Button></View>
  </RewardsGroup>
  <RewardsGroup title={"✨ "+tr("progression")} description={tr("progressDetail")} >
  <View style={styles.dailyGiftCard}><Txt style={{fontSize:25}}>⭐</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Prestige</Txt><Txt muted style={styles.small}>{g.prestigeCount} prestiges · {g.skillPoints} skill points</Txt></View><Button compact secondary onPress={onPrestige}>Open</Button></View>
  </RewardsGroup>
  <RewardsGroup title={"🏆 "+tr("achievements")} description={tr("completedAwards",{done:statuses.filter(s=>s.xpRewarded).length,total:ACHIEVEMENTS.length})} >
  <Card title="🏆 ACHIEVEMENTS" subtitle={statuses.filter(s=>s.xpRewarded).length+' / '+ACHIEVEMENTS.length+' awarded'}>
   <Txt muted style={styles.small}>Earn +50 XP for completed achievements and +25 XP for claimed daily goals.</Txt>
   {statuses.filter(item=>!item.xpRewarded).map(item=><View key={item.id} style={styles.shopItem}><View style={styles.shopLabel}><Txt style={styles.itemName}>{item.emoji} {item.title}</Txt><Txt muted style={styles.small}>{item.description}</Txt></View><Txt style={styles.small}>{item.xpRewarded?'✓ +50 XP':Math.min(item.progress,item.target)+' / '+item.target}</Txt></View>)}
   {statuses.some(item=>item.xpRewarded)?<RewardsGroup title={tr("completedAchievements")} description={tr("completedNote")} >
    {statuses.filter(item=>item.xpRewarded).map(item=><View key={item.id} style={styles.shopItem}>
     <View style={styles.shopLabel}><Txt style={styles.itemName}>{item.emoji} {item.title}</Txt><Txt muted style={styles.small}>{item.description}</Txt></View>
     <Txt style={styles.small}>✓ +50 XP</Txt>
    </View>)}
   </RewardsGroup>:null}
  </Card>
  </RewardsGroup>
 </View>;
}

 return {DailyGoalCard,RewardsGroup,RewardsScreen};
}
