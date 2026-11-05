// Android React Native implementation of the existing website mobile Play design.
// Visual reference (read-only): Puppy-Clicker/src/native/App.jsx PlayScreen,
// Puppy-Clicker/src/native/web/kawaii.css. Gameplay remains caller-owned PupEye actions.
import React from 'react';
import {Pressable,Text,View} from 'react-native';
import {colors as C} from '../theme.js';
import {format} from '../engine/game.js';

const clamp=(value,min,max)=>Math.min(max,Math.max(min,Number(value)||0));
const pct=(value,max)=>max>0?100*clamp(value,0,max)/max:0;

export default function WebsitePlayScreen({
 game,viewportHeight,wallet,portrait,onTap,cooldown,bonus,next,ticketVisible,desktop=false
}){
 const height=desktop?Math.max(690,(Number(viewportHeight)||700)-12):Math.max(350,Number(viewportHeight)||560);
 const short=!desktop&&height<540;
 const medium=!desktop&&height<660;
 const tapTitle=cooldown
  ?'Fair-play cooldown · '+cooldown+'s'
  :game.energy<=10
   ?game.puppyName+' is tired · Care needed'
   :'Tap '+game.puppyName;
 const canTap=cooldown<=0;
 return <View testID="android-play-screen"
  style={[styles.page,{height,gap:desktop?16:short?6:10}]}>
  <View style={[styles.heading,{minHeight:short?39:47}]}>
   <Text accessibilityRole="header" style={[styles.pageTitle,{fontSize:short?24:29}]}>Play</Text>
   <Text style={[styles.secondaryText,{fontSize:short?11:13}]}>Tap your puppy and earn treats.</Text>
  </View>
  {wallet}
  <View testID="android-website-play-stage"
   style={[styles.stage,{padding:desktop?17:short?8:12,gap:desktop?12:short?5:9}]}>
   <View style={[styles.puppyPlatform,{minHeight:desktop?350:short?128:medium?156:190}]}>
    <View pointerEvents="none" style={styles.orbitOuter}>
     <View style={styles.orbitInner}/>
    </View>
    <View style={styles.puppyPortrait}>{portrait}</View>
   </View>
   <View style={styles.puppyName}>
    <Text accessibilityRole="header" numberOfLines={1}
     adjustsFontSizeToFit minimumFontScale={0.65}
     style={[styles.puppyNameText,{fontSize:short?17:21}]}>
     {game.puppyName}
    </Text>
    <Text style={styles.puppyNameBadge}>Current Puppy</Text>
   </View>
   <View style={[styles.playEnergy,{padding:short?7:10}]}>
    <View style={styles.energyCell}>
     <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}
      style={styles.energyText}>⚡ Energy <Text style={styles.energyValue}>{game.energy}%</Text></Text>
    </View>
    <View style={styles.energyDivider}/>
    <View style={styles.energyCell}>
     <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}
      style={styles.energyText}>💖 Care bonus <Text style={styles.energyValue}>+{bonus} 🍪/tap</Text></Text>
    </View>
   </View>
   <Pressable testID="android-tap-button" accessibilityRole="button"
    accessibilityLabel={tapTitle} accessibilityState={{disabled:!canTap}}
    disabled={!canTap} onPress={onTap}
    style={({pressed})=>[styles.tapButton,{minHeight:short?55:69},!canTap&&styles.disabled,pressed&&styles.pressed]}>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}
     style={[styles.tapTitle,{fontSize:short?17:21}]}>🐾 {tapTitle}</Text>
    {!cooldown&&game.energy>10?<Text style={styles.tapReward}>
     +{format(game.clickPower+bonus)} treats
    </Text>:null}
   </Pressable>
  </View>
  <View testID="android-next-reward" style={[styles.rewardCard,{minHeight:short?62:80,padding:short?9:14}]}>
   <Text style={[styles.rewardEmoji,{fontSize:short?22:27}]}>🎁</Text>
   <View style={styles.rewardBody}>
    <View style={styles.rewardRow}>
     <Text numberOfLines={1} style={styles.rewardTitle}>{next.title}</Text>
     <Text style={styles.rewardProgress}>{next.allClaimed?'✓':next.progress+'/'+next.target}</Text>
    </View>
    <View accessibilityRole="progressbar" accessibilityLabel="Next reward progress"
     accessibilityValue={{min:0,max:next.target,now:clamp(next.progress,0,next.target)}}
     style={styles.progressTrack}>
     <View style={[styles.progressFill,{width:pct(next.progress,next.target)+'%'}]}/>
    </View>
    {!short?<Text numberOfLines={1} style={styles.rewardHint}>
     {next.allClaimed?'All goals claimed. See you tomorrow!'
      :next.progress>=next.target?'Reward ready — open Rewards to claim.'
      :'Keep playing to reach your next reward.'}
    </Text>:null}
   </View>
   <Text numberOfLines={2} style={styles.rewardValue}>
    {next.allClaimed?'All claimed':format(next.rewardTreats)+' 🍪'}
   </Text>
  </View>
  {ticketVisible?<View pointerEvents="none" style={styles.ticketOverlay}>
   <Text style={styles.ticketText}>🎟️ {game.lastTicketDrop} UPGRADE TICKET</Text>
  </View>:null}
 </View>;
}
const styles={
 page:{width:'100%',paddingHorizontal:2},
 heading:{justifyContent:'center',paddingHorizontal:3,gap:1},
 pageTitle:{fontWeight:'800',letterSpacing:-.5,color:C.text},
 secondaryText:{color:C.muted},
 stage:{flex:1,minHeight:0,borderWidth:1,borderColor:'rgba(0,229,255,0.25)',
  backgroundColor:'#101824',borderRadius:24,alignItems:'stretch',overflow:'hidden'},
 puppyPlatform:{flex:1,minHeight:0,alignItems:'center',justifyContent:'flex-end',
  position:'relative',overflow:'hidden'},
 orbitOuter:{position:'absolute',height:48,width:'73%',maxWidth:280,bottom:4,
  borderWidth:3,borderColor:C.accent,borderRadius:100,
  backgroundColor:'rgba(0,196,224,0.13)',shadowColor:C.accent,
  shadowOpacity:0.65,shadowRadius:12,elevation:6,transform:[{scaleY:0.65}]},
 orbitInner:{height:'100%',borderRadius:100,backgroundColor:'rgba(0,196,224,0.18)'},
 puppyPortrait:{flex:1,alignItems:'center',justifyContent:'center',zIndex:1},
 puppyName:{alignItems:'center',justifyContent:'center',gap:5,marginTop:2},
 puppyNameText:{fontWeight:'800',textAlign:'center',color:C.text},
 puppyNameBadge:{borderWidth:1,borderColor:C.accent,borderRadius:999,
  paddingVertical:3,paddingHorizontal:13,color:C.accent,fontSize:11,fontWeight:'700',
  backgroundColor:'rgba(0,196,224,0.08)',overflow:'hidden'},
 playEnergy:{flexDirection:'row',alignItems:'center',justifyContent:'center',
  backgroundColor:'#12151F',borderWidth:1,borderColor:'rgba(0,229,255,0.25)',
  borderRadius:20,gap:6},
 energyCell:{flex:1,minWidth:0,alignItems:'center'},
 energyDivider:{width:1,alignSelf:'stretch',backgroundColor:'rgba(0,229,255,0.25)'},
 energyText:{color:C.muted,fontSize:11,textAlign:'center',fontWeight:'600'},
 energyValue:{color:C.text,fontWeight:'800'},
 tapButton:{width:'100%',backgroundColor:C.accent,borderRadius:999,
  borderWidth:1,borderColor:'#8AF6FF',alignItems:'center',justifyContent:'center',
  paddingVertical:7,paddingHorizontal:12,gap:3,shadowColor:C.accent,shadowOpacity:0.4,
  shadowRadius:16,elevation:4},
 tapTitle:{fontWeight:'800',color:C.onAccent,textAlign:'center'},
 tapReward:{fontSize:12,color:C.onAccent,fontWeight:'700',textAlign:'center'},
 disabled:{opacity:0.5},pressed:{opacity:0.83,transform:[{scale:0.985}]},
 rewardCard:{flexDirection:'row',alignItems:'center',gap:9,backgroundColor:'#12151F',
  borderWidth:1,borderColor:'rgba(0,229,255,0.25)',borderRadius:22},
 rewardEmoji:{alignSelf:'center'},
 rewardBody:{flex:1,minWidth:0,gap:6},
 rewardRow:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:4},
 rewardTitle:{flex:1,fontSize:12,fontWeight:'800',color:C.text},
 rewardProgress:{fontSize:11,fontWeight:'800',color:C.text},
 progressTrack:{height:7,borderRadius:99,backgroundColor:'#2B3140',overflow:'hidden'},
 progressFill:{height:'100%',borderRadius:99,backgroundColor:C.accent},
 rewardHint:{fontSize:10,color:C.muted},
 rewardValue:{maxWidth:78,fontSize:11,color:C.text,fontWeight:'800',textAlign:'right'},
 ticketOverlay:{position:'absolute',top:120,left:10,right:10,padding:11,
  borderRadius:18,borderWidth:1,borderColor:C.accent,backgroundColor:'#12151F',
  elevation:8},
 ticketText:{fontSize:14,fontWeight:'800',color:C.text,textAlign:'center'}
};
