import React from 'react';
import {Pressable,Text,View} from 'react-native';
import {colors as C} from '../theme.js';
import {format} from '../engine/game.js';

// Pure React Native variant of the illustrated/browser-style Play screen.
// The live tap callback, protected currency wallet and rewards are passed
// from the same gameplay component that renders the compact Play screen.
export default function V2PlayScreen({game,layout,wallet,portrait,onTap,cooldown,bonus,next,ticketVisible}){
 const f=layout.scale;
 const px=n=>Math.round(n*f);
 const edge=C.accentSoft;
 const border=C.accent;
 const exhausted=game.energy<=10;
 const tapName=cooldown?'Fair-play cooldown · '+cooldown+'s':exhausted?game.puppyName+' needs care':'Tap '+game.puppyName;
 return <View testID="android-v2-play-screen" style={{width:'100%',height:Math.max(340,layout.viewportHeight),position:'relative',gap:layout.gap}}>
  <View style={{minHeight:layout.headingHeight,justifyContent:'center',paddingHorizontal:px(3)}}>
   <Text accessibilityRole="header" style={{fontSize:px(27),fontWeight:'900',color:C.text}}>Play</Text>
  </View>
  {wallet}
  <View testID="android-v2-puppy-stage" style={{
   flex:1,minHeight:layout.minStageHeight,
   backgroundColor:C.dark?'#111822':C.surface,borderWidth:1,borderColor:border,
   borderRadius:px(25),paddingHorizontal:px(10),paddingVertical:px(8),gap:layout.gap,
   alignItems:'center',
  }}>
   <View style={{flex:1,minHeight:0,alignItems:'center',justifyContent:'flex-end',width:'100%'}}>
    {portrait}
   </View>
   <View style={{
    alignItems:'center',justifyContent:'center',width:'88%',minHeight:layout.nameHeight,
    backgroundColor:C.accentSoft,borderColor:border,borderWidth:2,
    borderRadius:px(24),paddingHorizontal:px(10),paddingVertical:px(4),
   }}>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.68} style={{fontSize:px(21),fontWeight:'900',color:C.text,textAlign:'center'}}>{game.puppyName}</Text>
    <Text numberOfLines={1} style={{fontSize:px(11),fontWeight:'800',color:C.accentInk,textAlign:'center'}}>Current Puppy</Text>
   </View>
   <View style={{
    flexDirection:'row',alignItems:'center',width:'100%',minHeight:layout.energyHeight,
    borderColor:edge,borderWidth:1,borderRadius:px(17),paddingHorizontal:px(6),paddingVertical:px(4),
    backgroundColor:C.card,
   }}>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.66}
     style={{flex:1,minWidth:0,textAlign:'center',fontSize:px(12),fontWeight:'800',color:exhausted?C.danger:C.text}}>⚡ Energy {game.energy}%</Text>
    <View style={{width:1,alignSelf:'stretch',backgroundColor:edge,marginHorizontal:px(5)}}/>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.66}
     style={{flex:1,minWidth:0,textAlign:'center',fontSize:px(12),fontWeight:'800',color:C.text}}>💖 Care +{bonus} 🍪/tap</Text>
   </View>
   <Pressable testID="android-v2-tap-button" accessibilityRole="button" accessibilityLabel={tapName}
    accessibilityState={{disabled:cooldown>0}} disabled={cooldown>0} onPress={onTap}
    style={({pressed})=>({
     width:'100%',minHeight:layout.tapHeight,
     backgroundColor:C.accent,borderWidth:1,borderColor:C.accent,
     borderRadius:px(30),paddingHorizontal:px(10),paddingVertical:px(5),
     alignItems:'center',justifyContent:'center',
     opacity:cooldown>0?0.45:pressed?0.85:1,
    })}>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}
     style={{fontSize:px(19),fontWeight:'900',color:C.onAccent,textAlign:'center'}}>🐾 {tapName}</Text>
    {!cooldown&&!exhausted?<Text numberOfLines={1} style={{fontSize:px(13),fontWeight:'800',color:C.onAccent,textAlign:'center'}}>+{format(game.clickPower+bonus)} treats</Text>:null}
   </Pressable>
  </View>
  <View testID="android-v2-next-reward" style={{
   flexDirection:'row',alignItems:'center',minHeight:layout.rewardHeight,backgroundColor:C.accentSoft,
   borderColor:edge,borderWidth:1,borderRadius:px(16),paddingHorizontal:px(10),paddingVertical:px(7),gap:px(7),
  }}>
   <Text style={{fontSize:px(22)}}>🎁</Text>
   <View style={{flex:1,minWidth:0,gap:px(5)}}>
    <View style={{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:px(6)}}>
     <Text numberOfLines={1} style={{flex:1,fontWeight:'900',fontSize:px(11),color:C.text}}>{next.title}</Text>
     <Text style={{fontSize:px(11),fontWeight:'800',color:C.text}}>{next.allClaimed?'✓':next.progress+'/'+next.target}</Text>
    </View>
    <View accessibilityRole="progressbar" accessibilityLabel="Next reward progress"
     accessibilityValue={{min:0,max:next.target,now:next.progress}}
     style={{height:px(5),backgroundColor:C.line,borderRadius:4,overflow:'hidden'}}>
     <View style={{width:(100*next.progress/next.target)+'%',height:'100%',backgroundColor:C.accent}}/>
    </View>
   </View>
   <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.65}
    style={{fontSize:px(11),fontWeight:'800',color:C.text,maxWidth:px(85)}}>
    {next.allClaimed?'All claimed':format(next.rewardTreats)+' 🍪'}
   </Text>
  </View>
  {ticketVisible?<View pointerEvents="none" style={{
   position:'absolute',top:px(100),left:px(12),right:px(12),zIndex:10,
   backgroundColor:C.surface,borderWidth:1,borderColor:C.accent,borderRadius:18,padding:12,
  }}><Text style={{fontSize:px(14),fontWeight:'900',textAlign:'center',color:C.text}}>🎟️ {game.lastTicketDrop} UPGRADE TICKET</Text></View>:null}
 </View>;
}
