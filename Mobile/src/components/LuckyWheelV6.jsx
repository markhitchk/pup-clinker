import {themedStyles} from '../theme.js';
import React,{useEffect,useRef,useState} from 'react';
import {Animated,Easing,Image,Pressable,Text,View,useWindowDimensions} from 'react-native';
import {
 WHEEL_V6,WHEEL_SEGMENTS,WHEEL_GUEST_UNLOCKS_ENABLED,wheelTargetDegrees,
 wheelFrame,wheelLabelPosition,wheelResult,wheelResultLabel,validWheelWager
} from '../engine/wheelV6.js';

// Byte-identical originals from Kotlin PuppyLuckyWheelUi.kt; no emoji-only wheel.
const ART={
 disc:require('../../assets/casino/puppy_lucky_wheel_disc.png'),
 frame:require('../../assets/casino/puppy_lucky_wheel_frame.png'),
 pointer:require('../../assets/casino/puppy_lucky_wheel_pointer.png')
};
const easing=Easing.inOut(Easing.ease);
function WheelBoard({pending,lastResult,animationsEnabled}){
 const {width:screenWidth}=useWindowDimensions();
 const [contentWidth,setContentWidth]=useState(Math.max(1,screenWidth-46));
 const frame=wheelFrame(contentWidth);
 const rotation=useRef(new Animated.Value(0)).current;
 const pointer=useRef(new Animated.Value(0)).current;
 const display=wheelResult(pending)||lastResult;
 const segment=display?.segmentIndex??null;
 const target=segment===null?0:wheelTargetDegrees(segment);
 const rotate=rotation.interpolate({inputRange:[0,2520],outputRange:['0deg','2520deg'],extrapolate:'extend'});
 const unrotate=rotation.interpolate({inputRange:[0,2520],outputRange:['0deg','-2520deg'],extrapolate:'extend'});
 const click=pointer.interpolate({inputRange:[-13,0,4],outputRange:['-13deg','0deg','4deg'],extrapolate:'clamp'});
 useEffect(()=>{
  const saved=wheelResult(pending);
  if(!saved){
   rotation.setValue(display?target:0);
   pointer.setValue(0);
   return;
  }
  if(!animationsEnabled){
   rotation.setValue(target);
   pointer.setValue(0);
   return;
  }
  const elapsed=Math.max(0,Date.now()-pending.createdAt);
  const fraction=Math.min(1,elapsed/WHEEL_V6.rotationMs);
  rotation.setValue(target*fraction);
  pointer.setValue(0);
  const spin=Animated.timing(rotation,{toValue:target,
   duration:Math.max(0,WHEEL_V6.rotationMs-elapsed),easing,useNativeDriver:true});
  // Kotlin has 18 click cycles while its disc eases into the committed segment.
  const ticks=Animated.sequence(Array.from({length:WHEEL_V6.pointerClicks},(_,i)=>
   Animated.sequence([
    Animated.timing(pointer,{toValue:-(13-Math.min(6,i*.35)),duration:30,useNativeDriver:true}),
    Animated.timing(pointer,{toValue:4,duration:38,useNativeDriver:true}),
    Animated.timing(pointer,{toValue:0,duration:32,useNativeDriver:true}),
    Animated.delay(WHEEL_V6.pointerDelayBase+i*WHEEL_V6.pointerDelayStep)
   ])
  ));
  spin.start();
  if(elapsed<WHEEL_V6.rotationMs)ticks.start();
  return ()=>{spin.stop();ticks.stop();};
 },[pending?.createdAt,segment,animationsEnabled,rotation,pointer]);
 const size=frame.disc.width;
 return <View style={styles.boardMeasured} onLayout={e=>{
  const w=Math.round(e.nativeEvent.layout.width);
  if(w>0&&Math.abs(w-contentWidth)>1)setContentWidth(w);
 }}>
  <View testID="lucky_wheel_board" accessibilityLabel="Lucky Pup Wheel seven-segment prize board"
   style={{width:frame.width,height:frame.height,alignSelf:'center'}}>
   <Animated.View style={[styles.absolute,frame.disc,{transform:[{rotate}]}]}>
    <Image source={ART.disc} resizeMode="stretch" accessibilityLabel="Lucky Pup Wheel prize disc"
     style={{width:size,height:size}}/>
    {WHEEL_SEGMENTS.map(s=>{
     const pos=wheelLabelPosition(s.index,size);
     return <Animated.View key={s.id} style={[styles.segmentLabelWrap,{
      left:pos.left,top:pos.top,width:pos.width,transform:[{rotate:unrotate}]
     }]}>
      <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={.65}
       style={[styles.segmentLabel,{fontSize:pos.fontSize}]}>{s.label}</Text>
     </Animated.View>;
    })}
   </Animated.View>
   <Image source={ART.frame} resizeMode="stretch" pointerEvents="none"
    style={[styles.absolute,{width:frame.width,height:frame.height}]}/>
   <Animated.Image source={ART.pointer} resizeMode="stretch" accessibilityLabel="Lucky Pup Wheel pointer"
    style={[styles.absolute,frame.pointer,{transform:[{rotate:click}]}]}/>
  </View>
 </View>;
}
export default function LuckyWheelV6({game,pending,lastResult,onAction,onBack}){
 const [wager,setWager]=useState(20);
 const [showRules,setShowRules]=useState(false);
 const [message,setMessage]=useState('');
 const action=useRef(onAction);action.current=onAction;
 const animationsEnabled=game.animationsEnabled!==false;
 const display=wheelResult(pending)||lastResult;
 const blocked=Boolean(pending||game.casinoRound||game.pendingSlots||game.pendingRoulette||
  game.pendingPlinko||game.pendingScratch);
 const canSpin=!blocked&&validWheelWager(wager)&&game.casinoChips>=wager;
 useEffect(()=>{
  if(!pending)return;
  const delayMs=animationsEnabled?WHEEL_V6.spinMs:WHEEL_V6.reducedMotionMs;
  const elapsed=Math.max(0,Date.now()-pending.createdAt);
  const task=setTimeout(()=>action.current({type:'wheelReveal',now:Date.now()}),
   Math.max(10,delayMs-elapsed));
  return ()=>clearTimeout(task);
 },[pending?.createdAt,animationsEnabled]);
 const start=()=>{
  if(!canSpin){setMessage('Finish the current Casino round or choose an affordable wager.');return;}
  setMessage('');
  // The local roll is fixed before animation. No special/role/Code puppy IDs
  // can be minted through this unverified guest preview.
  action.current({type:'wheelStart',wager,roll:Math.floor(Math.random()*100),now:Date.now()});
 };
 const status=pending?'Wheel spinning…':display?wheelResultLabel(display):'Choose a wager and spin.';
 return <View style={styles.page}>
  <Pressable accessibilityRole="button" style={styles.back} onPress={onBack}>
   <Text style={styles.whiteBold}>‹ Puppy Casino</Text>
  </Pressable>
  <Text style={styles.heading}>Lucky Pup Wheel</Text>
  <Text style={styles.muted}>Spin for Casino Chip multipliers or a real eligible puppy unlock from the existing roster.</Text>
  <View style={styles.wallet}>
   <Text style={{fontSize:28}}>🐾</Text>
   <View style={{flex:1}}>
    <Text style={styles.whiteBold}>Casino Chip Wallet</Text>
    <Text style={styles.walletTotal}>{game.casinoChips.toLocaleString('en-US')} Casino Chips</Text>
   </View>
   <Text style={styles.rtp}>{WHEEL_V6.chipRtp} RTP</Text>
  </View>
  <View style={styles.cartoonStage}>
   <Text style={styles.stageTitle}>LUCKY PUP WHEEL</Text>
   <Text style={styles.muted}>Prize committed first · wheel animation is visual only</Text>
   <WheelBoard pending={pending} lastResult={lastResult} animationsEnabled={animationsEnabled}/>
   <View accessibilityLiveRegion="polite" style={styles.resultCard}>
    <Text style={styles.resultText}>{status}</Text>
   </View>
  </View>
  <Text style={styles.section}>Wager</Text>
  <View style={styles.presets}>{WHEEL_V6.wagers.map(n=><Pressable accessibilityRole="button"
   accessibilityState={{selected:wager===n,disabled:blocked}} disabled={blocked} key={n}
   onPress={()=>setWager(n)} style={[styles.preset,wager===n&&styles.selectedPreset]}>
   <Text style={styles.whiteBold}>{n.toLocaleString('en-US')}</Text>
  </Pressable>)}</View>
  <Pressable accessibilityRole="button" accessibilityState={{disabled:!canSpin}}
   disabled={!canSpin} onPress={start} style={[styles.spinButton,!canSpin&&styles.disabled]}>
   <Text style={styles.whiteBold}>{blocked?'Round In Progress':game.casinoChips<wager?
    'Not Enough Chips':'Spin Wheel · '+wager+' Chips'}</Text>
  </Pressable>
  {message?<Text accessibilityLiveRegion="polite" style={styles.error}>{message}</Text>:null}
  <Pressable accessibilityRole="button" onPress={()=>setShowRules(!showRules)}
   style={styles.rulesButton}>
   <Text style={styles.whiteBold}>{showRules?'Hide Published Wheel':'ⓘ Published Wheel & Rules'}</Text>
  </Pressable>
  {showRules?<View style={styles.rules}>
   <Text style={styles.section}>Published wheel</Text>
   <Text style={styles.muted}>Casino Chip expected return: {WHEEL_V6.chipRtp}. Puppy unlock value is separate.</Text>
   {WHEEL_SEGMENTS.map(segment=><View key={segment.id} style={styles.ruleRow}>
    <Text style={styles.muted}>{segment.emoji} {segment.description}</Text>
    <Text style={styles.whiteBold}>{segment.weight}%</Text>
   </View>)}
   <Text style={styles.muted}>• Pup Unlock uses the current casino-eligible roster pool only.</Text>
   <Text style={styles.muted}>• Already-owned pups are excluded before the spin.</Text>
   <Text style={styles.muted}>• The existing one-casino-puppy-per-day cap still applies in Kotlin.</Text>
   <Text style={styles.muted}>• If the pup pool/cap cannot award a pup, that slice refunds the wager instead.</Text>
   <Text style={styles.muted}>• The selected segment and puppy ID are committed before animation.</Text>
  </View>:null}
  {!WHEEL_GUEST_UNLOCKS_ENABLED?<Text style={styles.safety}>
   React guest preview: Pup Unlock is not available without verified ownership. Its 2% slice refunds the wager, as Kotlin does when no award can be made. No special puppy IDs are granted locally.
  </Text>:null}
  <Text style={styles.muted}>Simulated local Casino Chips only · Not the authoritative Kotlin/PupEye casino.</Text>
 </View>;
}
const styles=themedStyles(()=>({
 page:{gap:12,width:'100%',backgroundColor:'#0C0F12'},back:{minHeight:44,alignSelf:'flex-start',justifyContent:'center'},
 heading:{fontSize:27,fontWeight:'900',color:'#FFFFFF'},section:{fontSize:17,fontWeight:'900',color:'#FFFFFF'},
 muted:{color:'#B7C8CA',fontSize:12,textAlign:'center'},whiteBold:{color:'#FFFFFF',fontWeight:'900'},
 wallet:{borderRadius:18,backgroundColor:'#17272B',borderColor:'#576D73',borderWidth:1,
  padding:14,flexDirection:'row',alignItems:'center',gap:10},
 walletTotal:{color:'#FFFFFF',fontWeight:'900',fontSize:20},rtp:{color:'#75DCC6',fontSize:12,fontWeight:'900'},
 cartoonStage:{backgroundColor:'#29312A',borderRadius:28,borderWidth:2,borderColor:'#D5AB67',
  padding:12,alignItems:'center',gap:6,overflow:'hidden'},
 stageTitle:{fontWeight:'900',fontSize:20,color:'#F7E0AC'},boardMeasured:{width:'100%',alignItems:'center'},
 absolute:{position:'absolute'},segmentLabelWrap:{position:'absolute',alignItems:'center',
  justifyContent:'center',backgroundColor:'#00000070',borderRadius:4,paddingHorizontal:2,paddingVertical:1},
 segmentLabel:{fontWeight:'900',color:'#FFFFFF',textAlign:'center'},
 resultCard:{maxWidth:'100%',paddingHorizontal:16,paddingVertical:9,borderRadius:15,
  backgroundColor:'#FFF1D4',borderColor:'#A97B47',borderWidth:2},
 resultText:{fontWeight:'900',textAlign:'center',color:'#503420',fontSize:12},
 presets:{flexDirection:'row',gap:6},
 preset:{flex:1,alignItems:'center',justifyContent:'center',minWidth:0,padding:10,
  borderRadius:15,backgroundColor:'#304145'},
 selectedPreset:{backgroundColor:'#245D54',borderColor:'#7DE1C6',borderWidth:1},
 spinButton:{backgroundColor:'#287B70',borderRadius:14,minHeight:48,
  justifyContent:'center',alignItems:'center',padding:10},disabled:{opacity:.4},
 error:{color:'#FA9BA0',fontSize:12},rulesButton:{minHeight:42,padding:10,
  borderRadius:13,borderColor:'#53686B',borderWidth:1,alignItems:'center'},
 rules:{backgroundColor:'#192D30',borderRadius:18,padding:14,gap:8},
 ruleRow:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:8},
 safety:{color:'#F6DCA5',fontSize:12,textAlign:'center',padding:10,borderWidth:1,borderColor:'#8B7142',borderRadius:12}
}));
