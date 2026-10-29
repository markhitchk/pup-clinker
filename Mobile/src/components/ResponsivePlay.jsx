import React from 'react';
import {Animated,ImageBackground,Pressable,StyleSheet,Text,View} from 'react-native';
import {ROSTER,careTapBonus,format,mood,wellness} from '../engine/game.js';
import {goalsForDay} from '../engine/monthlyRewards.js';

// Matches the website's dark kawaii-cyber /play layout using native Android views.
// The underlying tap, reward and cloud-save actions continue to live in Mobile/App.js.
const W={bg:'#0A0C12',panel:'#12151F',stage:'#111822',edge:'#075467',cyan:'#00E5FF',tap:'#15DCEF',ink:'#031924',white:'#F7FAFF',muted:'#A8B4C9'};
const txt=(size=14,bold=false,color=W.white)=>({fontSize:size,fontWeight:bold?'900':'500',color});
const S=StyleSheet.create({
 root:{width:'100%',gap:9},
 heading:{flexDirection:'row',alignItems:'center',gap:8,justifyContent:'space-between'},
 switch:{borderWidth:1,borderColor:W.edge,backgroundColor:W.panel,paddingHorizontal:13,paddingVertical:10,borderRadius:30,minHeight:39,justifyContent:'center'},
 frame:{flex:1,minHeight:305,borderWidth:1,borderColor:W.edge,borderRadius:24,backgroundColor:W.stage,paddingHorizontal:10,paddingVertical:9,gap:7,overflow:'hidden'},
 scene:{flex:1,minHeight:170,alignItems:'center',justifyContent:'flex-end',paddingTop:4,gap:3},
 orbit:{position:'absolute',bottom:34,width:'73%',height:46,backgroundColor:'#00C4E022',borderColor:W.cyan,borderWidth:3,borderRadius:80,elevation:4},
 pupName:{...txt(22,true),textAlign:'center',maxWidth:'100%'},
 current:{borderWidth:1,borderColor:W.cyan,borderRadius:99,backgroundColor:'#00C4E01B',paddingHorizontal:13,paddingVertical:3},
 energy:{width:'100%',flexDirection:'row',alignItems:'center',justifyContent:'center',borderWidth:1,borderColor:W.edge,borderRadius:17,backgroundColor:'#101720',minHeight:34,paddingVertical:7,paddingHorizontal:7,gap:6},
 energyDivider:{width:1,height:21,backgroundColor:W.edge},
 tap:{width:'100%',backgroundColor:W.tap,borderWidth:1,borderColor:'#8AF6FF',borderRadius:99,minHeight:64,paddingHorizontal:12,paddingVertical:7,alignItems:'center',justifyContent:'center',flexDirection:'row',gap:11,elevation:4},
 reward:{minHeight:59,flexDirection:'row',alignItems:'center',gap:9,paddingHorizontal:11,paddingVertical:9,borderWidth:1,borderColor:W.edge,borderRadius:18,backgroundColor:'#10202A'},
 track:{backgroundColor:'#2B3140',height:7,borderRadius:12,overflow:'hidden'},
 walletShell:{borderWidth:1,borderColor:W.edge,borderRadius:21,backgroundColor:W.panel,overflow:'hidden'},
 paw:{position:'absolute',fontSize:30,opacity:0.13},
 desktopGrid:{flexDirection:'row',gap:14,alignItems:'stretch',width:'100%'},
 nightYard:{flex:1.35,minWidth:0,overflow:'hidden',borderRadius:18,borderWidth:1,borderColor:'#263944',padding:16},
 veil:{position:'absolute',top:0,bottom:0,left:0,right:0,backgroundColor:'rgba(5,10,18,0.34)'},
 desktopScene:{flex:1,minHeight:385,backgroundColor:'transparent',borderWidth:0,paddingHorizontal:22},
 identity:{position:'absolute',top:96,left:18,zIndex:2,flexDirection:'row',alignItems:'center',gap:8,maxWidth:'54%'},
 desktopPanels:{flex:1,minWidth:0,gap:11},
 desktopCard:{borderWidth:1,borderColor:W.edge,backgroundColor:W.panel,borderRadius:18,padding:12,gap:7},
 desktopTitle:{...txt(17,true)},
 needRow:{flexDirection:'row',alignItems:'center',gap:7,minHeight:19}
});
function Label({children,size=14,strong=false,color=W.white,style,numberOfLines}){
 return <Text numberOfLines={numberOfLines} style={[txt(size,strong,color),style]}>{children}</Text>;
}
function NextReward({next}){
 const target=Math.max(1,Number(next.target)||1);
 const progress=Math.max(0,Math.min(target,Number(next.progress)||0));
 return <View testID="web-parity-next-reward" style={S.reward}>
  <Label size={27}>🎁</Label>
  <View style={{flex:1,minWidth:0,gap:7}}>
   <View style={{flexDirection:'row',justifyContent:'space-between',gap:7}}>
    <Label style={{flex:1}} size={12} strong numberOfLines={1}>{next.title}</Label>
    <Label size={12} strong>{next.allClaimed?'✓':progress+'/'+target}</Label>
   </View>
   <View accessibilityRole="progressbar" accessibilityLabel="Next reward progress" accessibilityValue={{min:0,max:target,now:progress}} style={S.track}>
    <View style={{height:7,width:(100*progress/target)+'%',backgroundColor:W.cyan,borderRadius:9}}/>
   </View>
  </View>
  <Label size={12} strong style={{maxWidth:75,textAlign:'right'}}>{next.allClaimed?'All claimed':format(next.rewardTreats)+' 🍪'}</Label>
 </View>;
}
function PlayContents({g,pup,next,bonus,cooldown,onTap,onOpen,Portrait,Wallet,tapScale,rotation,lift,ticketVisible,viewportHeight,desktop=false}){
 const portraitSize=desktop?Math.max(210,Math.min(330,viewportHeight*0.46)):Math.max(170,Math.min(245,viewportHeight*0.39));
 const stage=<View testID="web-parity-play-stage" style={[S.frame,desktop&&S.desktopScene]}>
   <View style={S.scene}>
    {!desktop?<View pointerEvents="none" style={{position:'absolute',top:0,bottom:0,left:0,right:0}}>
      <Text style={[S.paw,{left:'12%',top:'13%'}]}>🐾</Text>
      <Text style={[S.paw,{right:'12%',top:'29%'}]}>🐾</Text>
      <Text style={[S.paw,{left:'8%',top:'65%'}]}>🐾</Text>
      <Text style={[S.paw,{right:'8%',top:'67%'}]}>🐾</Text>
     </View>:null}
    <View pointerEvents="none" style={[S.orbit,desktop&&{width:300,bottom:12}]}/>
    <Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName+' to earn Treats'} accessibilityState={{disabled:cooldown>0}} disabled={cooldown>0} onPress={onTap} style={{zIndex:2}}>
     <Animated.View style={{transform:[{scale:tapScale},{rotate:rotation.interpolate({inputRange:[-4,4],outputRange:['-4deg','4deg']})},{translateY:lift}]}}>
      <Portrait puppy={pup} accessory={g.accessory} size={portraitSize}/>
     </Animated.View>
    </Pressable>
    {!desktop?<><Label size={22} strong style={S.pupName} numberOfLines={1}>{g.puppyName}</Label>
     <View style={S.current}><Label size={11} strong color={W.cyan}>Current Puppy</Label></View></>:null}
   </View>
   <View style={[S.energy,desktop&&{borderWidth:0,backgroundColor:'transparent',minHeight:28}]}>
    <Label size={desktop?13:11} color={W.muted} style={{flex:1,textAlign:'center'}}>⚡ <Label strong size={desktop?13:11}>Energy {g.energy}%</Label></Label>
    {!desktop?<View style={S.energyDivider}/>:null}
    <Label size={desktop?13:11} color={W.muted} style={{flex:1,textAlign:'center'}}>💖 Care bonus <Label strong size={desktop?13:11}>+{bonus} 🍪 / tap</Label></Label>
   </View>
   <Pressable accessibilityRole="button" accessibilityLabel={'Tap '+g.puppyName} accessibilityState={{disabled:cooldown>0}} disabled={cooldown>0} testID="web-parity-tap" onPress={onTap} style={[S.tap,desktop&&{minHeight:74},cooldown>0&&{opacity:0.45}]}>
    <Label size={27}>🐾</Label>
    <View style={{alignItems:'center',flexShrink:1}}>
     <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.5} style={txt(desktop?23:20,true,W.ink)}>
      {cooldown?'Fair-play cooldown · '+cooldown+'s':g.energy<=10?g.puppyName+' is tired':'Tap '+g.puppyName}
     </Text>
     {!cooldown&&g.energy>10?<Label size={desktop?17:13} strong color={W.ink}>+{format(g.clickPower+bonus)} treats</Label>:null}
    </View>
   </Pressable>
  </View>;
 return <View testID="web-parity-play" style={[S.root,desktop?{flex:1,minHeight:Math.max(530,viewportHeight-22)}:{minHeight:Math.max(380,viewportHeight)}]}>
  <View style={S.heading}>
   <View style={{flex:1,minWidth:0}}>
    <Label size={desktop?35:29} strong>Play</Label>
    {desktop?<Label size={15} color={W.muted}>Tap your puppy and earn treats.</Label>:null}
   </View>
   <Pressable accessibilityRole="button" accessibilityLabel="Switch puppy" onPress={()=>onOpen('roster')} style={S.switch}>
    <Label size={13} strong color={W.cyan}>⇄ Switch Puppy</Label>
   </Pressable>
  </View>
  {!desktop?<View style={S.walletShell}><Wallet game={g}/></View>:null}
  {stage}
  <NextReward next={next}/>
  {ticketVisible?<View pointerEvents="none" style={{position:'absolute',top:65,left:10,right:10,borderWidth:1,borderColor:W.cyan,borderRadius:16,backgroundColor:W.panel,padding:12,zIndex:20}}>
    <Label strong style={{textAlign:'center'}}>🎟️ {g.lastTicketDrop} UPGRADE TICKET</Label>
   </View>:null}
 </View>;
}
function DesktopDashboard({g,act,clock,schedule,viewportHeight,onOpen,Portrait,Wallet,DailyGoalCard,...props}){
 const pup=ROSTER.find(p=>p.id===g.puppyStyle)||ROSTER[0];
 const date=new Date(clock),goals=goalsForDay(schedule,date).slice(0,3);
 const needs=[['💖','Happiness',g.happiness],['🍗','Fullness',g.fullness],['⚡','Energy',g.energy],['🫧','Cleanliness',g.cleanliness],['🤝','Bond',g.bond]];
 return <View testID="desktop-play-dashboard" style={[S.desktopGrid,{minHeight:Math.max(550,viewportHeight)}]}>
  <ImageBackground source={require('../../assets/scenes/night-yard-v1.webp')} resizeMode="cover" imageStyle={{borderRadius:18}} style={S.nightYard}>
   <View pointerEvents="none" style={S.veil}/>
   <View style={S.identity}>
    <Portrait puppy={pup} size={56} accessory={g.accessory}/>
    <View style={{flex:1,minWidth:0}}>
     <Label size={20} strong numberOfLines={1}>{g.puppyName}</Label>
     <Label size={11} color={W.muted}>{pup.id.startsWith('v2_')?'V2':'V1'} Puppies · Current Puppy</Label>
    </View>
   </View>
   <PlayContents {...props} g={g} pup={pup} schedule={schedule} clock={clock} onOpen={onOpen} viewportHeight={viewportHeight} Wallet={Wallet} Portrait={Portrait} desktop/>
  </ImageBackground>
  <View testID="desktop-dashboard-panels" style={S.desktopPanels}>
   <View style={S.desktopCard}>
    <Label size={17} strong>Your Resources</Label>
    <View style={S.walletShell}><Wallet game={g}/></View>
   </View>
   <View style={[S.desktopCard,{flex:1}]}>
    <View style={{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:6}}>
     <Label size={17} strong>Today’s goals</Label>
     <Pressable accessibilityRole="button" accessibilityLabel="View all daily goals" onPress={()=>onOpen('rewards')}><Label size={12} strong color={W.cyan}>All goals →</Label></Pressable>
    </View>
    {goals.map(goal=><DailyGoalCard key={goal.id} g={g} goal={goal} act={act} clock={clock}/>)}
   </View>
   <View style={S.desktopCard}>
    <View style={{flexDirection:'row',justifyContent:'space-between',alignItems:'center'}}>
     <Label size={17} strong>Pup Care Status</Label>
     <Pressable accessibilityRole="button" accessibilityLabel="Go to Care" onPress={()=>onOpen('care')}><Label size={12} strong color={W.cyan}>Go to Care →</Label></Pressable>
    </View>
    <View style={{flexDirection:'row',gap:10,alignItems:'center'}}>
     <Portrait puppy={pup} size={56} accessory={g.accessory}/>
     <View style={{flex:1,minWidth:0}}>
      <Label strong numberOfLines={1}>{g.puppyName}</Label>
      <Label size={11} color={W.muted}>{mood(g)} · Wellness {wellness(g)}% · Bond {g.bond}%</Label>
     </View>
    </View>
    {needs.map(([icon,name,raw])=>{
      const value=Math.max(0,Math.min(100,Number(raw)||0));
      return <View style={S.needRow} key={name}>
       <Label size={11} color={W.muted} style={{width:100}}>{icon} {name}</Label>
       <View accessibilityRole="progressbar" accessibilityLabel={name+' need'} accessibilityValue={{min:0,max:100,now:value}} style={[S.track,{flex:1,height:6}]}>
        <View style={{height:6,width:value+'%',backgroundColor:W.cyan}}/>
       </View>
       <Label size={11} strong style={{width:34,textAlign:'right'}}>{Math.round(value)}%</Label>
      </View>;
    })}
    <Label size={11} color={W.muted}>✨ Care bonus +{careTapBonus(g)} Treats per tap</Label>
   </View>
  </View>
 </View>;
}
export default function ResponsivePlay(props){
 const pup=ROSTER.find(p=>p.id===props.g.puppyStyle)||ROSTER[0];
 if(props.desktop)return <DesktopDashboard {...props} pup={pup}/>;
 return <PlayContents {...props} pup={pup}/>;
}
