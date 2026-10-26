// Port of DangerZoneConfirmation.kt. Only the final uninterrupted hold invokes
// the existing guest reducer action; opening, canceling and tapping never do.
import React,{useEffect,useRef,useState} from 'react';
import {Animated,AppState,Easing,Modal,Pressable,ScrollView,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors as C,themedStyles} from '../theme.js';
import PuppyPointer from './PuppyPointer.jsx';

const actions={
 settings:{title:'Reset settings?',description:'Restore theme, accent, motion, UI scale and gameplay preferences to their defaults. Your profile, birthday and saved game progress are not reset.',button:'Hold to Reset Settings'},
 progress:{title:'Reset game progress?',description:'This clears local guest gameplay progression: currencies, upgrades, XP, achievements, streaks, bonds, Casino state, local reward unlocks and prestige progress. Your local profile, birthday and app settings remain.',button:'Hold to Reset Progress'},
 delete:{title:'Delete local save data?',description:'This deletes local guest game progress, player identity and birthday/setup state. Puppy Clicker will restart into first-run setup. Your original Kotlin app and cloud save are separate.',button:'Hold to Delete Local Data'}
};
const duration=10000;

function DangerTape({reducedMotion}){
 const [size,setSize]=useState({width:0,height:0});
 const shift=useRef(new Animated.Value(0)).current;
 useEffect(()=>{
  shift.setValue(0);
  if(reducedMotion)return;
  const animation=Animated.loop(Animated.timing(shift,{toValue:38,duration:900,easing:Easing.linear,useNativeDriver:true}));
  animation.start();return ()=>animation.stop();
 },[reducedMotion,shift]);
 const {width:w,height:h}=size,frame=18;
 // All four clips look into the same global stripe field, so corners join.
 const clips=[{left:0,top:0,width:w,height:frame},{left:0,top:h-frame,width:w,height:frame},{left:0,top:frame,width:frame,height:Math.max(0,h-2*frame)},{left:w-frame,top:frame,width:frame,height:Math.max(0,h-2*frame)}];
 return <View pointerEvents="none" style={s.frame} onLayout={e=>setSize(e.nativeEvent.layout)}>{w>0&&h>0?clips.map((clip,index)=><View key={index} style={[s.clip,clip]}><Animated.View style={{position:'absolute',left:-clip.left,top:-clip.top,width:w,height:h,transform:[{translateX:shift}]}}>{Array.from({length:Math.ceil((w+h)/38)+4},(_,i)=><View key={i} style={{position:'absolute',left:i*38-h-76,top:-h,width:20,height:h*3,backgroundColor:'#E01818',transform:[{rotate:'45deg'}]}}/>)}</Animated.View></View>):null}</View>;
}

export default function DangerHoldDialog({action,g,onDismiss,onConfirmed}){
 const definition=actions[action];
 const insets=useSafeAreaInsets();
 const active=useRef(null),timer=useRef(null),bounds=useRef({width:0,height:0}),completed=useRef(false),confirm=useRef(onConfirmed);
 confirm.current=onConfirmed;
 const [elapsed,setElapsed]=useState(0),[holding,setHolding]=useState(false);
 const stop=()=>{if(timer.current!==null)clearInterval(timer.current);timer.current=null;active.current=null;};
 const cancel=()=>{stop();setHolding(false);setElapsed(0);};
 const dismiss=()=>{cancel();onDismiss();};
 useEffect(()=>{
  completed.current=false;cancel();
  const subscription=AppState.addEventListener('change',state=>{if(state!=='active')cancel();});
  return ()=>{stop();subscription.remove();};
 },[action]);
 const inside=e=>{const n=e.nativeEvent;return (n.touches?.length??1)===1&&n.locationX>=0&&n.locationY>=0&&n.locationX<=bounds.current.width&&n.locationY<=bounds.current.height;};
 const start=e=>{
  if(!definition||completed.current||AppState.currentState!=='active'||!inside(e))return;
  cancel();active.current=performance.now();setHolding(true);
  timer.current=setInterval(()=>{
   if(active.current===null)return;
   if(AppState.currentState!=='active'){cancel();return;}
   const value=Math.max(0,performance.now()-active.current);setElapsed(Math.min(duration,value));
   if(value>=duration){stop();completed.current=true;setHolding(false);confirm.current();}
  },50);
 };
 if(!definition)return null;
 return <Modal transparent visible statusBarTranslucent navigationBarTranslucent animationType="none" onRequestClose={dismiss}>
  <PuppyPointer enabled={g.puppyPointerEnabled} style={s.overlay}>
   <View accessibilityViewIsModal style={{flex:1,paddingTop:insets.top,paddingBottom:insets.bottom}}>
    <View style={{flex:1}}>
    {action!=='settings'?<DangerTape reducedMotion={g.reducedMotion||!g.animatedUi}/>:null}
    <View style={s.center}><ScrollView bounces={false} contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled">
     <View style={s.card}>
      <Text style={[s.warning,action==='settings'&&s.warningSettings]}>{action==='settings'?'⚙️ PREFERENCES':'⚠️ DANGER ZONE'}</Text><Text accessibilityRole="header" style={s.title}>{definition.title}</Text>
      <Text style={s.body}>{definition.description}</Text><Text style={s.detail}>Hold the button continuously for 10 seconds. Release, move off the button or leave the app to cancel.</Text>
      <View accessible accessibilityRole="button" accessibilityLabel={definition.button} accessibilityHint="Hold continuously for 10 seconds to confirm. Release to cancel."
       onLayout={e=>{bounds.current=e.nativeEvent.layout;}} onStartShouldSetResponder={()=>true}
       onResponderGrant={start} onResponderMove={e=>{if(!inside(e))cancel();}} onResponderStart={e=>{if((e.nativeEvent.touches?.length??1)!==1)cancel();}}
       onResponderRelease={cancel} onResponderTerminate={cancel} onResponderTerminationRequest={()=>true} style={s.hold}>
       <View pointerEvents="none" style={[s.progress,{width:(100*elapsed/duration)+'%'}]}/>
       <View pointerEvents="none" style={s.holdLabels}><Text style={s.holdTitle}>{holding?'KEEP HOLDING · '+((duration-elapsed)/1000).toFixed(1)+'s':definition.button}</Text>
        <Text style={s.holdDetail}>{holding?Math.floor(100*elapsed/duration)+'% complete':'Hold continuously for 10 seconds'}</Text></View>
      </View>
      <Pressable accessibilityRole="button" onPress={dismiss} style={s.cancel}><Text style={s.cancelText}>Cancel</Text></Pressable>
     </View>
    </ScrollView></View>
    </View>
   </View>
  </PuppyPointer>
 </Modal>;
}
const s=themedStyles(()=>({
 overlay:{flex:1,backgroundColor:'rgba(0,0,0,.64)'},frame:{position:'absolute',left:0,top:0,right:0,bottom:0},clip:{position:'absolute',overflow:'hidden',backgroundColor:'#000000'},
 center:{flex:1,padding:20},scroll:{flexGrow:1,justifyContent:'center',alignItems:'center',paddingVertical:8},card:{width:'94%',maxWidth:540,backgroundColor:C.dark?'#132F3B':C.surface,borderRadius:24,padding:20,gap:10},
 warning:{fontSize:14,fontWeight:'900',color:C.dark?'#FFB4AB':'#BA1A1A'},warningSettings:{color:C.accentInk},title:{fontSize:24,fontWeight:'900',color:C.text},body:{fontSize:14,lineHeight:21,color:C.text},detail:{fontSize:12,lineHeight:18,color:C.muted,marginBottom:6},
 hold:{minHeight:68,borderRadius:18,backgroundColor:C.dark?'#FFB4AB':'#BA1A1A',overflow:'hidden',justifyContent:'center',alignItems:'center',padding:10},holdLabels:{alignItems:'center',gap:4},progress:{position:'absolute',left:0,top:0,bottom:0,backgroundColor:'rgba(0,0,0,.18)'},holdTitle:{fontSize:16,fontWeight:'900',textAlign:'center',color:C.dark?'#690005':'#FFFFFF'},holdDetail:{fontSize:12,textAlign:'center',color:C.dark?'#690005':'#FFFFFF'},
 cancel:{minHeight:42,borderWidth:1,borderColor:C.line,borderRadius:24,justifyContent:'center',alignItems:'center',padding:10},cancelText:{fontSize:14,fontWeight:'700',color:C.accentInk}
}));
