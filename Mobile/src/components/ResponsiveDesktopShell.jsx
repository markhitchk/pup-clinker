import React from 'react';
import {Image,Pressable,StyleSheet,Text,View} from 'react-native';
import {level} from '../engine/game.js';
import {levelXp,nextXpTarget,MAX_PLAYER_LEVEL} from '../engine/balancedProgression.js';

const W={bg:'#0A1017',panel:'#12151F',cyan:'#00E5FF',edge:'#075467',text:'#F7FAFF',muted:'#A8B4C9'};
const S=StyleSheet.create({
 sidebar:{backgroundColor:'#0B151D',borderRightWidth:1,borderRightColor:'#23323B',paddingHorizontal:12,paddingTop:14,paddingBottom:12,gap:8},
 brand:{flexDirection:'row',alignItems:'center',gap:10,paddingHorizontal:6,paddingBottom:14},
 nav:{minHeight:54,flexDirection:'row',alignItems:'center',gap:14,borderRadius:14,paddingHorizontal:12,borderWidth:1,borderColor:'transparent'},
 selected:{backgroundColor:'#003F5488',borderColor:W.cyan},
 navText:{fontSize:16,color:'#B8C4D8',fontWeight:'800'},
 header:{backgroundColor:W.bg,flexDirection:'row',alignItems:'center',gap:12,minHeight:88,paddingHorizontal:16,paddingVertical:10},
 stat:{backgroundColor:W.panel,borderWidth:1,borderColor:W.edge,borderRadius:16,flex:1,minWidth:0,paddingHorizontal:12,paddingVertical:10,gap:6},
 progress:{height:7,borderRadius:7,backgroundColor:'#2B3140',overflow:'hidden'},
 iconButton:{width:45,height:45,borderRadius:25,borderWidth:1,borderColor:'#B9C6DC',justifyContent:'center',alignItems:'center'},
});
const copy=(label,size=15,color=W.text,weight='800',style={})=><Text style={[{color,fontSize:size,fontWeight:weight},style]}>{label}</Text>;
export function DesktopSidebar({tabs,selected,onSelect,width=226,onOpenSettings,onOpenNotifications}){
 return <View testID="desktop-native-sidebar" style={[S.sidebar,{width}]}>
  <View style={S.brand}>
   <Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain" style={{width:56,height:56}}/>
   <View style={{flex:1}}>{copy('Puppy',21)}{copy('Clicker',21)}</View>
  </View>
  {tabs.map(([id,icon,label])=><Pressable key={id} accessibilityRole="tab" accessibilityLabel={label} accessibilityState={{selected:selected===id}}
   onPress={()=>onSelect(id)} style={[S.nav,selected===id&&S.selected]}>
   {copy(icon,26)}{copy(label,16,selected===id?W.cyan:'#B8C4D8')}
  </Pressable>)}
  <View style={{flex:1,minHeight:16}}/>
  <View style={{height:1,backgroundColor:'#28353E'}}/>
  <Pressable accessibilityRole="button" accessibilityLabel="Desktop notifications" onPress={onOpenNotifications} style={S.nav}>{copy('🔔',23)}{copy('Notifications',14)}</Pressable>
  <Pressable accessibilityRole="button" accessibilityLabel="Desktop settings" onPress={onOpenSettings} style={S.nav}>{copy('⚙️',23)}{copy('Settings',14)}</Pressable>
 </View>;
}
export function DesktopStatusHeader({g,onOpenNotifications,onOpenSettings,unread=0}){
 const current=level(g),xp=Number(g.xp)||0,target=nextXpTarget(xp);
 const progress=current>=MAX_PLAYER_LEVEL?100:Math.max(0,Math.min(100,100*(xp-levelXp(current))/Math.max(1,target-levelXp(current))));
 const streak=Math.max(0,Number(g.dailyStreak)||0);
 return <View testID="desktop-game-status-header" style={S.header}>
  <View style={S.stat}>
   {copy('⭐ Player Level '+current+' / '+MAX_PLAYER_LEVEL,15)}
   {copy(current>=MAX_PLAYER_LEVEL?'MAX LEVEL':xp.toLocaleString()+' / '+target.toLocaleString()+' XP',11,W.muted,'600')}
   <View accessibilityRole="progressbar" accessibilityLabel="Player level progress" accessibilityValue={{min:0,max:100,now:Math.round(progress)}} style={S.progress}>
    <View style={{backgroundColor:W.cyan,height:7,width:progress+'%'}}/>
   </View>
  </View>
  <View style={S.stat}>
   {copy('🔥 '+streak+' day streak',15)}
   {copy('Yearly gift bonus · up to 365 days',11,W.muted,'600')}
   <View style={{flexDirection:'row',gap:5}}>{Array.from({length:7},(_,i)=><Text key={i} style={{color:i<streak?W.cyan:W.muted,fontSize:13}}>{i<streak?'●':'○'}</Text>)}</View>
  </View>
  <Pressable accessibilityRole="button" accessibilityLabel="Desktop notifications" onPress={onOpenNotifications} style={S.iconButton}>
   {copy(unread>0?'🔔 •':'🔔',24)}
  </Pressable>
  <Pressable accessibilityRole="button" accessibilityLabel="Open settings" onPress={onOpenSettings} style={S.iconButton}>
   {copy('⚙️',24)}
  </Pressable>
 </View>;
}
