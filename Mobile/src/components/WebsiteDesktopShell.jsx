// Website desktop navigation translated to native React for Chromebook and DeX.
// Visual reference: Puppy-Clicker/src/native/web/DesktopNavigation.jsx.
// Keep screen actions in the signed native game, never in a browser WebView.
import React from 'react';
export {default as WebsiteDesktopInventory} from './WebsiteInventoryScreen.jsx';
import {Image,Pressable,ScrollView,Text,View} from 'react-native';
import {colors as C} from '../theme.js';
import {level} from '../engine/game.js';
import {MAX_PLAYER_LEVEL,MAX_PLAYER_XP,levelXp,nextXpTarget} from '../engine/balancedProgression.js';

const buttonStyle=(active)=>[
 styles.navItem,
 active&&{backgroundColor:'rgba(0,63,84,0.68)',borderColor:'#00C4E0'}
];
function NavButton({icon,label,active,onPress,tab=false}){
 return <Pressable accessibilityRole={tab?'tab':'button'}
  accessibilityLabel={label} accessibilityState={{selected:active}}
  onPress={onPress} style={({pressed})=>[...buttonStyle(active),pressed&&{opacity:.72}]}>
  <Text style={styles.navIcon}>{icon}</Text>
  <Text numberOfLines={1} style={[styles.navText,
   {color:active?C.accent:C.muted,fontWeight:active?'800':'700'}]}>{label}</Text>
 </Pressable>;
}
export function WebsiteDesktopNavigation({tabs,route,onOpen,width=204}){
 return <View testID="android-desktop-navigation"
  style={[styles.sidebar,{width,backgroundColor:'#0D1921',borderRightColor:'#23323B'}]}>
  <Pressable accessibilityRole="button" accessibilityLabel="Puppy Clicker home"
   onPress={()=>onOpen('play')} style={styles.brand}>
   <Image source={require('../../assets/logos/puppy_clicker.png')}
    accessibilityLabel="Puppy Clicker logo" resizeMode="contain" style={styles.logo}/>
   <Text style={[styles.brandText,{color:C.text}]}>Puppy{ '\n' }Clicker</Text>
  </Pressable>
  <ScrollView style={{flex:1}} contentContainerStyle={{paddingBottom:12,gap:5}}
   keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
   <NavButton icon="🏠" label="Home" onPress={()=>onOpen('play')}/>
   {tabs.map(([id,icon,label])=><NavButton key={id} icon={icon} label={label}
    active={route===id} tab onPress={()=>onOpen(id)}/>)}
   <View style={[styles.divider,{backgroundColor:C.line}]}/>
   <Text style={[styles.groupLabel,{color:C.muted}]}>MORE GAME MODES</Text>
   <NavButton icon="🎰" label="Casino" active={route==='casino'} onPress={()=>onOpen('casino')}/>
   <NavButton icon="🎁" label="Exchange" active={route==='exchange'} onPress={()=>onOpen('exchange')}/>
   <NavButton icon="🎒" label="Inventory" active={route==='inventory'} onPress={()=>onOpen('inventory')}/>
   <View style={[styles.divider,{backgroundColor:C.line}]}/>
   <Text style={[styles.groupLabel,{color:C.muted}]}>SUPPORT AND MORE</Text>
   <NavButton icon="⚙️" label="Settings" active={route==='settings'} onPress={()=>onOpen('settings')}/>
   <NavButton icon="❔" label="Help" active={route==='help'} onPress={()=>onOpen('help')}/>
   <NavButton icon="👤" label="Pup Account" active={route==='account-details'} onPress={()=>onOpen('account-details')}/>
  </ScrollView>
  <Text style={[styles.sidebarFoot,{color:C.muted}]}>🐾 Puppy Clicker · Android Desktop</Text>
 </View>;
}

function ProgressBar({value}){
 return <View accessibilityRole="progressbar" accessibilityLabel="Player level progress"
  accessibilityValue={{min:0,max:100,now:Math.round(value)}}
  style={styles.progress}>
  <View style={[styles.progressFill,{width:value+'%',backgroundColor:C.accent}]}/>
 </View>;
}
export function WebsiteDesktopTopBar({game,onNotifications,onSettings,onInventory,unread=0}){
 const current=level(game);
 const xp=Number.isFinite(Number(game.xp))?Math.max(0,Number(game.xp)):0;
 const previous=levelXp(current);
 const following=nextXpTarget(xp);
 const progress=current>=MAX_PLAYER_LEVEL?100:
  Math.min(100,Math.max(0,100*(xp-previous)/Math.max(1,following-previous)));
 const streak=Math.max(0,Number(game.dailyStreak)||0);
 return <View testID="android-desktop-top-bar"
  style={[styles.topBar,{backgroundColor:'#0A1017',borderBottomColor:'#23323B'}]}>
  <View style={[styles.progressCard,{backgroundColor:C.surface,borderColor:C.line}]}>
   <Text style={styles.topEmoji}>⭐</Text>
   <View style={styles.topContent}>
    <Text numberOfLines={1} style={[styles.topTitle,{color:C.text}]}>
     Player Level {current} / {MAX_PLAYER_LEVEL}
    </Text>
    <Text numberOfLines={1} style={[styles.topSmall,{color:C.muted}]}>
     {current>=MAX_PLAYER_LEVEL?'MAX LEVEL · XP '+MAX_PLAYER_XP.toLocaleString():
      xp.toLocaleString()+' / '+following.toLocaleString()+' XP'}
    </Text>
    <ProgressBar value={progress}/>
   </View>
  </View>
  <View style={[styles.streakCard,{backgroundColor:C.surface,borderColor:C.line}]}>
   <Text style={styles.topEmoji}>🔥</Text>
   <View style={styles.topContent}>
    <Text style={[styles.topTitle,{color:C.text}]}>{streak} day streak</Text>
    <Text numberOfLines={1} style={[styles.topSmall,{color:C.muted}]}>
     Yearly gift bonus · up to 365 days
    </Text>
    <View style={styles.dots}>{Array.from({length:7},(_,index)=>
     <Text key={index} style={[styles.dot,{
      backgroundColor:index<streak?C.accent:C.card,color:index<streak?C.onAccent:C.muted
     }]}>{index<streak?'✓':' '}</Text>)}</View>
   </View>
  <View style={styles.topActions}>
   <Pressable accessibilityRole="button" accessibilityLabel="Open inventory"
    onPress={onInventory} style={[styles.iconButton,{borderColor:C.line}]}>
    <Text style={styles.actionEmoji}>🎒</Text>
   </Pressable>
   <Pressable accessibilityRole="button" accessibilityLabel={'Notifications, '+unread+' unread'}
    onPress={onNotifications} style={[styles.iconButton,{borderColor:C.line}]}>
    <Text style={styles.actionEmoji}>🔔</Text>
    {unread>0?<View style={[styles.unreadDot,{backgroundColor:C.pink||'#FF5C9D'}]}/>:null}
   </Pressable>
   <Pressable accessibilityRole="button" accessibilityLabel="Open settings"
    onPress={onSettings} style={[styles.iconButton,{borderColor:C.line}]}>
    <Text style={styles.actionEmoji}>⚙️</Text>
   </Pressable>
  </View>
  </View>
 </View>;
}

export function WebsiteDesktopHelp({onOpen}){
 return <View testID="android-desktop-help" style={{gap:16}}>
  <Text accessibilityRole="header" style={[styles.pageTitle,{color:C.text}]}>Puppy Clicker Help</Text>
  <Text style={{color:C.muted}}>Your puppy adventure, on Android and desktop.</Text>
  <View style={[styles.inventoryPanel,{backgroundColor:C.surface,borderColor:C.line,gap:12}]}>
   <Text style={[styles.topTitle,{color:C.text,fontSize:19}]}>Tap, care, collect</Text>
   <Text style={{color:C.muted,lineHeight:22}}>Tap your puppy to earn Treats. Keep energy above 10% with Pup Care, and improve wellness and bond for a care bonus. Browse your puppies in Roster, buy upgrades in Shop, and claim goals in Rewards.</Text>
   <NavButton icon="💖" label="Open Pup Care" onPress={()=>onOpen('care')}/>
   <NavButton icon="🎁" label="Goals and Rewards" onPress={()=>onOpen('rewards')}/>
  </View>
  <View style={[styles.inventoryPanel,{backgroundColor:C.surface,borderColor:C.line,gap:12}]}>
   <Text style={[styles.topTitle,{color:C.text,fontSize:19}]}>Your Pup Account</Text>
   <Text style={{color:C.muted,lineHeight:22}}>The app saves verified gameplay actions through your signed Pup Account. Account and device settings remain available in Settings.</Text>
   <NavButton icon="⚙️" label="Open Settings" onPress={()=>onOpen('settings')}/>
  </View>
 </View>;
}
const styles={
 sidebar:{borderRightWidth:1,paddingHorizontal:12,paddingTop:22},
 brand:{flexDirection:'row',alignItems:'center',gap:9,paddingHorizontal:8,
  paddingVertical:12,marginBottom:10},
 logo:{width:56,height:56},brandText:{fontSize:18,fontWeight:'900',lineHeight:21},
 navItem:{flexDirection:'row',alignItems:'center',gap:10,paddingHorizontal:10,
  minHeight:58,borderRadius:14,borderWidth:1,borderColor:'transparent'},
 navIcon:{fontSize:28,width:34,textAlign:'center'},
 navText:{fontSize:16,flex:1},
 divider:{height:1,marginVertical:10},
 groupLabel:{fontSize:10,fontWeight:'800',letterSpacing:1.1,paddingLeft:11,marginBottom:3},
 sidebarFoot:{fontSize:10,textAlign:'center',padding:10},
 topBar:{minHeight:112,flexDirection:'row',alignItems:'stretch',gap:12,
  paddingHorizontal:16,paddingVertical:14,borderBottomWidth:0},
 progressCard:{flex:0.35,minWidth:0,flexDirection:'row',alignItems:'center',
  borderRadius:18,borderWidth:1,padding:10,gap:10},
 streakCard:{flex:0.65,minWidth:0,flexDirection:'row',alignItems:'center',
  borderRadius:18,borderWidth:1,padding:10,gap:10},
 topEmoji:{fontSize:35},topContent:{flex:1,minWidth:0,gap:3},
 topTitle:{fontSize:15,fontWeight:'800'},topSmall:{fontSize:11},
 progress:{height:7,borderRadius:99,backgroundColor:'#2B3140',overflow:'hidden',marginTop:4},
 progressFill:{height:'100%',borderRadius:99},
 dots:{flexDirection:'row',gap:5,marginTop:3},dot:{width:20,height:20,borderRadius:99,
  overflow:'hidden',textAlign:'center',fontSize:10,fontWeight:'900'},
 topActions:{flexDirection:'row',alignItems:'center',gap:5},
 iconButton:{width:48,height:48,alignItems:'center',justifyContent:'center',borderWidth:1,
  borderRadius:26,position:'relative'},actionEmoji:{fontSize:27},
 unreadDot:{position:'absolute',top:1,right:1,width:8,height:8,borderRadius:4},
 pageTitle:{fontSize:30,fontWeight:'800'},
 inventoryTabs:{flexDirection:'row',flexWrap:'wrap',gap:5},
 search:{minHeight:46,borderWidth:1,borderRadius:17,paddingHorizontal:12},
 inventoryPanel:{borderRadius:22,borderWidth:1,padding:17},
 inventoryRow:{paddingVertical:10,borderBottomWidth:1,gap:5},
 inventoryName:{fontWeight:'800',fontSize:16},
 secondaryButton:{alignItems:'center',padding:12,borderWidth:1,borderRadius:24,alignSelf:'flex-start'}
};
