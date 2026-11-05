// Website's right-rail desktop dashboard, implemented with native React.
 // Source reference: Puppy-Clicker/src/native/App.jsx DesktopPlayScreen.
import React from 'react';
import {Pressable,Text,View} from 'react-native';
import {colors as C} from '../theme.js';
import {goalsForDay,progressForGoal,monthKey} from '../engine/monthlyRewards.js';
import {format,mood,wellness,careTapBonus} from '../engine/game.js';

const careRows=[
 ['💖','Happiness','happiness'],
 ['🍗','Fullness','fullness'],
 ['⚡','Energy','energy'],
 ['🫧','Cleanliness','cleanliness'],
 ['🤝','Bond','bond']
];
const currencies=[
 ['🍪','Treats','treats'],
 ['🦴','Bones','bones'],
 ['🪙','Pup Coins','pupCoins'],
 ['🐾','Casino Chips','casinoChips']
];
const clamp=(v,max)=>Math.max(0,Math.min(max,Number(v)||0));
function Panel({children,title,extra}){
 return <View style={[styles.panel,{backgroundColor:C.surface,borderColor:C.line}]}>
  <View style={styles.panelHeader}>
   <Text style={[styles.heading,{color:C.text}]}>{title}</Text>
   {extra}
  </View>
  {children}
 </View>;
}
function Action({title,onPress}){
 return <Pressable accessibilityRole="button" accessibilityLabel={title} onPress={onPress}
  style={({pressed})=>[styles.action,{borderColor:C.line},pressed&&{opacity:0.7}]}>
  <Text style={{color:C.accent,fontSize:12,fontWeight:'800'}}>{title}</Text>
 </Pressable>;
}
function ResourceGrid({game}){
 return <View testID="android-desktop-resources" style={styles.resources}>
  {currencies.map(([emoji,name,key])=><View key={key}
   style={[styles.resourceCell,{borderColor:C.line,backgroundColor:C.card}]}>
   <Text style={styles.resourceEmoji}>{emoji}</Text>
   <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.55}
    style={[styles.resourceAmount,{color:C.text}]}>{format(game[key]||0)}</Text>
   <Text style={[styles.resourceName,{color:C.muted}]}>{name}</Text>
  </View>)}
 </View>;
}
function GoalsPanel({game,schedule,clock,onOpen}){
 const today=new Date(clock);
 const tasks=goalsForDay(schedule,today).slice(0,3);
 return <Panel title="Today's goals"
  extra={<Action title="All goals →" onPress={()=>onOpen('rewards')}/>}>
  <Text style={{color:C.muted,fontSize:11,marginTop:-3,marginBottom:3}}>{monthKey(today)}</Text>
  {tasks.map(goal=>{
   const progress=clamp(progressForGoal(game,goal,today),goal.target);
   const complete=progress>=goal.target;
   const claimed=(game.claimedDailyTasks||[]).includes(goal.id);
   return <Pressable key={goal.id} accessibilityRole="button"
    accessibilityLabel={'View '+goal.title+' goal in Rewards'} onPress={()=>onOpen('rewards')}
    style={[styles.goalCard,{backgroundColor:C.card,borderColor:C.line}]}>
    <Text style={styles.goalEmoji}>{goal.emoji}</Text>
    <View style={{flex:1,minWidth:0,gap:5}}>
     <View style={styles.goalRow}>
      <Text numberOfLines={1} style={[styles.goalTitle,{color:C.text}]}>{goal.title}</Text>
      <Text style={[styles.goalCount,{color:C.text}]}>{progress}/{goal.target}</Text>
     </View>
     <View accessible accessibilityRole="progressbar"
      accessibilityLabel={goal.title+' progress'}
      accessibilityValue={{min:0,max:goal.target,now:progress}}
      style={styles.progress}><View style={[styles.fill,{
       backgroundColor:C.accent,width:(100*progress/Math.max(1,goal.target))+'%'}]}/></View>
     <Text style={{color:claimed?C.green:complete?C.gold:C.muted,fontSize:11}}>
      {claimed?'✓ Claimed':complete?'Reward ready to claim':'Keep playing to reach your goal'}
     </Text>
    </View>
   </Pressable>;
  })}
 </Panel>;
}
function CarePanel({game,onOpen}){
 const bonus=careTapBonus(game);
 return <Panel title="Pup Care Status"
  extra={<Action title="Go to Care →" onPress={()=>onOpen('care')}/>}>
  <Text style={[styles.careName,{color:C.text}]}>{game.puppyName}</Text>
  <Text style={{fontSize:12,color:C.muted,marginBottom:7}}>
   {mood(game)} · Wellness {wellness(game)}% · Bond {game.bond}%
  </Text>
  {careRows.map(([emoji,label,key])=>{
   const value=clamp(game[key],100);
   return <View key={key} style={styles.careRow}>
    <Text numberOfLines={1} style={[styles.careLabel,{color:C.muted}]}>{emoji} {label}</Text>
    <View accessibilityRole="progressbar" accessibilityLabel={label+' need'}
     accessibilityValue={{min:0,max:100,now:value}} style={[styles.progress,{flex:1}]}>
     <View style={[styles.fill,{backgroundColor:C.accent,width:value+'%'}]}/>
    </View>
    <Text style={{width:39,textAlign:'right',fontSize:11,color:C.text}}>{value}%</Text>
   </View>;
  })}
  <View style={[styles.careBonus,{borderColor:C.line,backgroundColor:C.accentSoft}]}>
   <Text style={{color:C.gold,fontSize:12,fontWeight:'800'}}>
    ✨ {bonus===2?'Thriving':bonus===1?'Healthy':'Pup'} care bonus
   </Text>
   <Text style={{color:C.muted,fontSize:11}}>+{bonus} Treats on every normal puppy tap.</Text>
  </View>
 </Panel>;
}
export default function WebsiteDesktopDashboard({
 game,schedule,clock,onOpen,play,railWidth=330
}){
 return <View testID="android-desktop-dashboard" style={styles.dashboard}>
  <View style={styles.playColumn}>{play}</View>
  <View style={[styles.rightRail,{width:railWidth}]}>
   <Panel title="Your Resources"><ResourceGrid game={game}/></Panel>
   <GoalsPanel game={game} schedule={schedule} clock={clock} onOpen={onOpen}/>
   <CarePanel game={game} onOpen={onOpen}/>
  </View>
 </View>;
}
const styles={
 dashboard:{width:'100%',flexDirection:'row',alignItems:'flex-start',gap:18},
 playColumn:{flex:1,minWidth:0},
 rightRail:{gap:16,minWidth:0},
 panel:{padding:15,borderRadius:22,borderWidth:1,gap:9},
 panelHeader:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8},
 heading:{fontSize:18,fontWeight:'800',flex:1},
 action:{borderWidth:1,borderRadius:24,paddingVertical:6,paddingHorizontal:10},
 resources:{flexDirection:'row',gap:7},
 resourceCell:{flex:1,minWidth:0,borderRadius:12,borderWidth:1,padding:6,
  alignItems:'center',justifyContent:'center',gap:4},
 resourceEmoji:{fontSize:20},
 resourceAmount:{fontSize:14,fontWeight:'800'},
 resourceName:{fontSize:9,textAlign:'center'},
 goalCard:{flexDirection:'row',alignItems:'center',padding:10,borderRadius:16,
  borderWidth:1,gap:9},
 goalEmoji:{fontSize:20},
 goalRow:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:4},
 goalTitle:{fontWeight:'800',fontSize:12,flex:1},
 goalCount:{fontSize:11,fontWeight:'800'},
 progress:{height:7,borderRadius:20,backgroundColor:'#2B3140',overflow:'hidden'},
 fill:{height:'100%',borderRadius:20},
 careName:{fontSize:17,fontWeight:'800',marginTop:2},
 careRow:{flexDirection:'row',alignItems:'center',gap:7,paddingVertical:5},
 careLabel:{width:93,fontSize:11},
 careBonus:{marginTop:6,padding:11,borderWidth:1,borderRadius:15,gap:5},
};
