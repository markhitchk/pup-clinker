import React,{useRef,useState} from 'react';
import {Image,PanResponder,Text,View} from 'react-native';
import {SCRATCH_COLUMNS,SCRATCH_ROWS,SCRATCH_REVEAL_THRESHOLD,markScratchTrail,scratchCoverage} from '../engine/scratch.js';

// React Native preview port of PuppyScratchersUi: same 1426x1077 frame placement
// and 68% reveal rule. The temporary view-tile coating is not Kotlin's native
// 48x20 Canvas mask or physical scratch-coin rotation.
export default function ScratchStage({round,borderImage,onReveal}){
 const [scratched,setScratched]=useState(()=>new Set());
 const cells=useRef(new Set()),bounds=useRef({width:0,height:0});
 const last=useRef(null),triggered=useRef(false);
 const callback=useRef(onReveal);
 callback.current=onReveal;
 const handler=useRef(null);
 handler.current=event=>{
  if(triggered.current)return;
  const e=event.nativeEvent;
  if(!Number.isFinite(e.locationX)||!Number.isFinite(e.locationY))return;
  const point={x:e.locationX,y:e.locationY,at:Date.now()};
  const before=last.current||point;
  const next=markScratchTrail(cells.current,before,point,bounds.current,point.at-before.at);
  last.current=point;
  if(next.size===cells.current.size)return;
  cells.current=next;
  setScratched(next);
  if(scratchCoverage(next)>=SCRATCH_REVEAL_THRESHOLD){
   triggered.current=true;
   callback.current();
  }
 };
 const responder=useRef(PanResponder.create({
  onStartShouldSetPanResponder:()=>true,
  onMoveShouldSetPanResponder:()=>true,
  onPanResponderGrant:e=>handler.current(e),
  onPanResponderMove:e=>handler.current(e),
  onPanResponderRelease:()=>{last.current=null;},
  onPanResponderTerminate:()=>{last.current=null;}
 })).current;
 const progress=Math.floor(scratchCoverage(scratched)*100);
 return <View style={{gap:8}}>
  <Text style={{color:'#E6D4A0',fontSize:13,fontWeight:'700'}}>
   🪙 Scratch Token · Drag across the silver coating · {progress}%
  </Text>
  <View style={{width:'100%',aspectRatio:1426/1077,position:'relative'}}>
   <Image pointerEvents="none" source={borderImage} resizeMode="stretch"
    style={{position:'absolute',left:0,top:0,width:'100%',height:'100%'}}/>
   <View
    testID="puppy-scratch-coating"
    accessibilityRole="adjustable"
    accessibilityLabel={'Puppy scratch card, '+progress+' percent cleared'}
    accessibilityHint="Drag to reveal at 68 percent. Activate with accessibility actions to reveal."
    accessibilityActions={[{name:'activate',label:'Reveal scratch card'}]}
    onAccessibilityAction={()=>{if(!triggered.current){triggered.current=true;callback.current();}}}
    onLayout={e=>{const {width,height}=e.nativeEvent.layout;bounds.current={width,height};}}
    {...responder.panHandlers}
    style={{position:'absolute',left:'14.03%',top:'44.75%',width:'71.04%',height:'41.13%',
     backgroundColor:'#FFF1C9',borderRadius:10,overflow:'hidden',zIndex:1}}
   >
    <View pointerEvents="none" style={{flex:1,flexDirection:'row',justifyContent:'space-evenly',alignItems:'center'}}>
     {round.symbols.map((symbol,index)=><Text key={index} style={{fontSize:28}}>{symbol}</Text>)}
    </View>
    <View pointerEvents="none" style={{position:'absolute',left:0,right:0,top:0,bottom:0}}>
     {Array.from({length:SCRATCH_ROWS},(_,row)=><View key={row} style={{flex:1,flexDirection:'row'}}>
      {Array.from({length:SCRATCH_COLUMNS},(_,col)=><View key={col}
       style={{flex:1,backgroundColor:scratched.has(row*SCRATCH_COLUMNS+col)?'transparent':'#91A5AD'}}/>)}
     </View>)}
    </View>
   </View>
  </View>
  <Text style={{color:'#ACC0C8',fontSize:12,textAlign:'center'}}>
   Clear 68% to settle your saved Casino Chip prize.
  </Text>
 </View>;
}
