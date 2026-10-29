/**
 * React Native counterpart to Puppy-Clicker/src/native/App.jsx PlayScreen and
 * DesktopPlayScreen. Keep touch/economy actions in the signed game adapter.
 * Website CSS/HTML cannot be mounted in an Android native view.
 */
import React,{useEffect,useState} from 'react';
import {ImageBackground,Modal,Pressable,ScrollView,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {format,careTapBonus,mood,wellness} from '../engine/game.js';
import {goalsForDay,progressForGoal,localEpochDay,monthKey} from '../engine/monthlyRewards.js';
import {playViewportLayout,playPercent} from '../engine/responsivePlay.js';

const yard=require('../../assets/scenes/night-yard-v1.webp');
const ink='#F7F9FC',muted='#B8C7D7',cyan='#00E5FF',surface='#12151F';
const panel={backgroundColor:surface,borderWidth:1,borderColor:'#274354',borderRadius:19,padding:14};
const title={fontSize:17,fontWeight:'900',color:ink};
const copy={fontSize:12,color:muted};
const row={flexDirection:'row',alignItems:'center',gap:8};
const val=n=>Number.isFinite(Number(n))?Math.round(Number(n)):0;

function Label({children,style}){return <Text style={[{color:ink,fontWeight:'800'},style]}>{children}</Text>}
function Action({children,onPress,disabled=false,secondary=false,style}){
 return <Pressable accessibilityRole="button" accessibilityState={{disabled}} disabled={disabled}
  onPress={onPress} style={[{minHeight:38,borderRadius:16,justifyContent:'center',alignItems:'center',paddingHorizontal:14,
   backgroundColor:secondary?'#0D2635':cyan,borderWidth:1,borderColor:secondary?'#386478':'#1AEAFF',opacity:disabled?.5:1},style]}>
  <Text style={{color:secondary?ink:'#061C25',fontWeight:'900',fontSize:13,textAlign:'center'}}>{children}</Text>
 </Pressable>;
}
function Track({label,progress,target=100,height=7}){
 return <View accessibilityRole="progressbar" accessibilityLabel={label}
  accessibilityValue={{min:0,max:target,now:Math.max(0,Math.min(target,Number(progress)||0))}}
  style={{backgroundColor:'#263444',height,borderRadius:12,overflow:'hidden',flex:1}}>
  <View style={{height:'100%',width:playPercent(progress,target)+'%',borderRadius:12,backgroundColor:cyan}}/>
 </View>;
}
function ResourcePanel({wallet}){return <View style={[panel,{gap:7}]} testID="desktop-play-resources">
 <Label style={title}>Your Resources</Label>{wallet}
 </View>}
function GoalsPanel({g,schedule,clock,readOnly,onOpen,act}){
 const date=new Date(clock),day=localEpochDay(date),goals=goalsForDay(schedule,date).slice(0,3);
 return <View style={[panel,{gap:10}]} testID="desktop-play-goals">
  <View style={[row,{justifyContent:'space-between',flexWrap:'wrap'}]}>
   <Label style={title}>Today’s goals</Label>
   <Text style={copy}>{monthKey(date)}</Text>
   <Pressable accessibilityRole="button" onPress={()=>onOpen('rewards')}><Text style={{color:cyan,fontWeight:'700'}}>All goals →</Text></Pressable>
  </View>
  {goals.map(goal=>{
   const current=Math.max(0,progressForGoal(g,goal,date));
   const claimed=g.dailyDay===day&&g.claimedDailyTasks?.includes(goal.id);
   return <View key={goal.id} style={{backgroundColor:'#182330',borderRadius:15,borderColor:'#2E4553',borderWidth:1,padding:10,gap:7}}>
    <View style={row}>
     <Text style={{fontSize:26}}>{goal.emoji}</Text>
     <View style={{flex:1,minWidth:0,gap:4}}>
      <View style={[row,{justifyContent:'space-between'}]}><Label style={{fontSize:13,flex:1}} numberOfLines={1}>{goal.title}</Label>
       <Text style={copy}>{Math.min(current,goal.target)}/{goal.target}</Text></View>
      <Text numberOfLines={1} style={copy}>{goal.description}</Text>
     </View>
    </View>
    <Track label={goal.title} progress={current} target={goal.target}/>
    <View style={[row,{justifyContent:'space-between'}]}>
     <Text style={[copy,{flex:1}]} numberOfLines={2}>Reward: {format(goal.rewardTreats)} 🍪 + 1 🦴 + 1 🪙</Text>
     {claimed?<Text style={{color:cyan,fontWeight:'800'}}>✓ Claimed</Text>:
      <Action disabled={readOnly||current<goal.target} onPress={()=>act({type:'claimDailyGoal',goalId:goal.id,now:clock})}
       style={{minHeight:30,paddingHorizontal:12,borderRadius:11}}>Claim</Action>}
    </View>
   </View>;
  })}
 </View>;
}
function CarePanel({g,puppyPortrait,onOpen}){
 const needs=[['💖','Happiness',g.happiness],['🍗','Fullness',g.fullness],['⚡','Energy',g.energy],['🫧','Cleanliness',g.cleanliness],['🤝','Bond',g.bond]];
 const bonus=careTapBonus(g);
 return <View style={[panel,{gap:10}]} testID="desktop-play-care">
  <View style={[row,{justifyContent:'space-between'}]}><Label style={title}>Pup Care Status</Label>
   <Pressable accessibilityRole="button" onPress={()=>onOpen('care')}><Text style={{color:cyan,fontWeight:'800'}}>Go to Care →</Text></Pressable></View>
  <View style={row}>
   <View style={{width:65,height:65,backgroundColor:'#0A293B',borderRadius:13,borderWidth:1,borderColor:cyan,alignItems:'center',justifyContent:'center'}}>
    {puppyPortrait(58)}
   </View>
   <View style={{flex:1,minWidth:0}}>
    <Label style={{fontSize:15}}>{g.puppyName}</Label>
    <Text style={copy}>{mood(g)} · Wellness {wellness(g)}% · Bond {val(g.bond)}%</Text>
   </View>
  </View>
  {needs.map(([icon,name,value])=><View key={name} style={[row,{gap:5}]}>
   <Text style={[copy,{width:105}]}>{icon} {name}</Text>
   <Track label={name+' need'} progress={value}/>
   <Text style={[copy,{minWidth:34,textAlign:'right'}]}>{val(value)}%</Text>
  </View>)}
  <View style={{backgroundColor:'#0B2633',borderWidth:1,borderColor:'#1D5066',borderRadius:12,padding:9}}>
   <Text style={copy}>✨ {bonus===2?'Thriving':bonus===1?'Healthy':'Pup'} care bonus: +{bonus} Treats per normal tap</Text>
  </View>
 </View>;
}
export default function WebsitePlayScreen({g,act,clock,schedule,viewportHeight,onOpen,wallet,renderPortrait,onTap,
 cooldown=0,ticketVisible=false,readOnly=false}){
 const {width}=useWindowDimensions();
 const layout=playViewportLayout(width,viewportHeight);
 const desktop=layout.desktop;
 const bonus=careTapBonus(g);
 const [renameOpen,setRenameOpen]=useState(false);
 const [name,setName]=useState(g.puppyName||'');
 useEffect(()=>{if(!renameOpen)setName(g.puppyName||'');},[g.puppyName,renameOpen]);
 const next=(()=>{ // passed down by the adapter as an additional field; never calculate rewards from the UI
  return null;
 })();
 const reward=g.nextPlayReward;
 const disabled=readOnly||cooldown>0||val(g.energy)<=10;
 const tapLabel=readOnly?'Pup Account is read-only':cooldown?'Fair-play cooldown · '+cooldown+'s':
  val(g.energy)<=10?g.puppyName+' is tired · Care needed':'Tap '+g.puppyName;
 const heading=<View style={[row,{justifyContent:'space-between',alignItems:'flex-start',flexWrap:'wrap'}]}>
  <View style={{flex:1,minWidth:160}}><Label style={{fontSize:desktop?35:26}}>Play</Label>
   <Text style={[copy,{fontSize:desktop?16:13}]}>Tap your puppy and earn treats.</Text></View>
  <Action secondary onPress={()=>onOpen('roster')}>⇄ Switch Puppy</Action>
 </View>;
 const identity=desktop?<View style={[row,{alignItems:'center',gap:12}]}>
   <View style={{width:60,height:60,borderRadius:14,borderColor:cyan,borderWidth:1,backgroundColor:'#092335',alignItems:'center',justifyContent:'center'}}>{renderPortrait(55)}</View>
   <View style={{flex:1,minWidth:0,gap:4}}><Label style={{fontSize:20}}>{g.puppyName}</Label>
    <Text style={copy}>Current Puppy</Text>
    <Pressable accessibilityRole="button" disabled={readOnly} onPress={()=>setRenameOpen(true)}>
     <Text style={{color:readOnly?muted:cyan,fontWeight:'800'}}>✎ Rename</Text>
    </Pressable>
   </View>
  </View>:null;
 const stage=<View style={{flex:1,minHeight:desktop?380:layout.stageHeight,padding:desktop?16:12,gap:desktop?12:9,
  backgroundColor:desktop?'transparent':'#0A1823',borderRadius:20,borderColor:'#1A5368',borderWidth:desktop?0:1}}>
  <View style={{flex:1,alignItems:'center',justifyContent:'center',minHeight:layout.portrait+22}}>
   <Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'}
    accessibilityState={{disabled}} disabled={disabled} onPress={onTap}
    style={{justifyContent:'center',alignItems:'center'}}>
    {renderPortrait(layout.portrait)}
   </Pressable>
   <View pointerEvents="none" style={{height:18,width:Math.min(layout.portrait*1.45,375),maxWidth:'90%',
    marginTop:-7,borderRadius:60,backgroundColor:'#07536C',borderWidth:3,borderColor:cyan,
    shadowColor:cyan,shadowOpacity:.6,shadowRadius:15,elevation:8}}/>
  </View>
  <View style={[row,{justifyContent:'center',flexWrap:'wrap'}]}>
   <Text style={[copy,{fontSize:desktop?13:12}]}>⚡ Energy {val(g.energy)}%</Text>
   <Text style={[copy,{fontSize:desktop?13:12}]}>💖 Care bonus +{bonus} 🍪/tap</Text>
  </View>
  <Action onPress={onTap} disabled={disabled} style={{minHeight:desktop?68:55,borderRadius:23}}>
   🐾 {tapLabel}{!disabled?' · +'+format(val(g.clickPower)+bonus)+' 🍪':''}
  </Action>
  {readOnly?<Text style={[copy,{textAlign:'center'}]}>Protected cloud progress. Reconnect an authorized, write-enabled Pup Account to play.</Text>:null}
 </View>;
 const rewardPanel=<View style={{backgroundColor:'#092F42',borderColor:'#1F667D',borderWidth:1,borderRadius:20,
  padding:desktop?14:12,flexDirection:'row',alignItems:'center',gap:10}}>
  <Text style={{fontSize:desktop?32:25}}>🎁</Text>
  <View style={{flex:1,minWidth:0,gap:7}}>
   <View style={[row,{justifyContent:'space-between'}]}><Label style={{fontSize:13,flex:1}}>{reward?.title||'Next reward'}</Label>
    <Text style={copy}>{reward?.allClaimed?'✓':(reward?.progress??0)+'/'+(reward?.target??1)}</Text></View>
   <Track label="Next reward progress" progress={reward?.progress??0} target={Math.max(1,reward?.target??1)}/>
   <Text style={copy}>{reward?.allClaimed?'All goals claimed. See you tomorrow!':'Keep playing to reach your next reward.'}</Text>
  </View>
  <Label style={{fontSize:12}}>{reward?.allClaimed?'All claimed':format(reward?.rewardTreats??0)+' 🍪'}</Label>
 </View>;
 const main=<View testID={desktop?'desktop-website-play':'mobile-website-play'}
  style={{flex:desktop?1:undefined,minWidth:0,minHeight:desktop?0:undefined,gap:12,backgroundColor:desktop?'#0A0C12':undefined,
   borderWidth:desktop?1:0,borderColor:'#263944',borderRadius:18,overflow:'hidden'}}>
  {desktop?<ImageBackground source={yard} resizeMode="cover" imageStyle={{opacity:.48}}
   style={{flex:1,minHeight:550,padding:16,gap:12,backgroundColor:'#08121C'}}>
   {heading}{identity}{stage}{rewardPanel}
  </ImageBackground>:<View style={{paddingHorizontal:8,gap:12}}>{heading}{wallet}{stage}{rewardPanel}</View>}
  {ticketVisible?<View pointerEvents="none" style={{position:'absolute',top:110,left:15,right:15,padding:12,
   borderRadius:15,backgroundColor:'#102E3D',borderColor:cyan,borderWidth:1,elevation:10,alignItems:'center'}}>
   <Label>🎟️ {g.lastTicketDrop} UPGRADE TICKET</Label></View>:null}
 </View>;
 const dashboard=desktop?<View style={{width:layout.dashboardWidth,minWidth:0,gap:12}}>
  <ResourcePanel wallet={wallet}/>
  <GoalsPanel g={g} schedule={schedule} clock={clock} readOnly={readOnly} onOpen={onOpen} act={act}/>
  <CarePanel g={g} onOpen={onOpen} puppyPortrait={renderPortrait}/>
 </View>:null;
 return <>
  <ScrollView testID="website-play-scroll" style={{flex:1}} contentContainerStyle={{
   flexGrow:1,padding:desktop?14:4,gap:14,flexDirection:desktop?'row':'column',alignItems:'stretch'}}
   keyboardShouldPersistTaps="handled">
   {main}{dashboard}
  </ScrollView>
  <Modal visible={renameOpen} transparent animationType="fade" onRequestClose={()=>setRenameOpen(false)}>
   <View style={{flex:1,backgroundColor:'#000B',justifyContent:'center',padding:24}}>
    <View style={[panel,{gap:14}]}>
     <Label style={title}>Rename puppy</Label><Text style={copy}>Choose a nickname for your current puppy.</Text>
     <TextInput autoFocus value={name} onChangeText={setName} maxLength={18} placeholder="Puppy name"
      placeholderTextColor="#9BA9B8" style={{color:ink,borderColor:'#31566B',borderWidth:1,borderRadius:12,padding:12}}/>
     <View style={[row,{justifyContent:'flex-end'}]}>
      <Action secondary onPress={()=>setRenameOpen(false)}>Cancel</Action>
      <Action disabled={readOnly||!name.trim()} onPress={()=>{act({type:'rename',name:name.trim()});setRenameOpen(false);}}>Save name</Action>
     </View>
    </View>
   </View>
  </Modal>
 </>;
}
