import React,{useEffect,useRef,useState} from 'react';
import {Image,PanResponder,Text,View} from 'react-native';
import {
 SCRATCH_COLUMNS,SCRATCH_ROWS,SCRATCH_REVEAL_THRESHOLD,SCRATCH_FRAME,
 SCRATCH_COIN_ASPECT,SCRATCH_COIN_DIAMETER,markScratchTrail,scratchCoverage,
 scratchCoinAt,addAccessibleScratchProgress
} from '../engine/scratch.js';

// Kotlin's masked Canvas uses BlendMode.Clear. This dependency-free Android
// preview uses matching 48×20 transparent coverage cells; true offscreen
// Canvas blending/visual proof remains an explicit future parity gate.
const TOKEN=require('../../assets/casino/pup_coin.png');
const backgrounds={
 casino_scratch:'#FFECEC',ultra_pup:'#E9F8FF',golden_paw:'#FFF7D6'
};
export default function ScratchStage({round,borderImage,onReveal,lastResult,cardId='pup_scratch'}){
 const [scratched,setScratched]=useState(()=>new Set());
 const [coatingBounds,setCoatingBounds]=useState({width:0,height:0});
 const [coin,setCoin]=useState(null);
 const cells=useRef(new Set()),bounds=useRef({width:0,height:0});
 const last=useRef(null),coinRef=useRef(null),triggered=useRef(false),paintAt=useRef(0);
 const callback=useRef(onReveal),handler=useRef(null),complete=useRef(false);
 callback.current=onReveal;
 const pending=Boolean(round);
 const shown=round||lastResult;
 const revealed=!round&&Boolean(lastResult);
 useEffect(()=>{
  cells.current=new Set();last.current=null;coinRef.current=null;
  triggered.current=false;complete.current=false;
  paintAt.current=0;setScratched(new Set());setCoin(null);
 },[round?.createdAt]);
 const update=(newCells)=>{
  if(newCells.size===cells.current.size)return;
  cells.current=newCells;
  const coverage=scratchCoverage(newCells);
  if(coverage>=SCRATCH_REVEAL_THRESHOLD&&!triggered.current){
   triggered.current=true;complete.current=true;setScratched(newCells);
   callback.current?.();return;
  }
  const now=Date.now();
  if(now-paintAt.current>=32){paintAt.current=now;setScratched(newCells);}
 };
 handler.current=(e,kind)=>{
  if(!round||triggered.current)return;
  const ne=e.nativeEvent;
  if(!Number.isFinite(ne.locationX)||!Number.isFinite(ne.locationY))return;
  const now=Number.isFinite(ne.timestamp)?ne.timestamp:Date.now();
  const point={x:ne.locationX,y:ne.locationY,at:now};
  const prev=kind==='start'?point:last.current||point;
  const dt=Math.max(1,Math.min(50,point.at-prev.at||16));
  const delta={x:point.x-prev.x,y:point.y-prev.y};
  const b=bounds.current;
  if(b.width<=0||b.height<=0)return;
  const nextCoin=scratchCoinAt(kind==='start'?null:coinRef.current,point,delta,dt,b);
  coinRef.current=nextCoin;
  setCoin(nextCoin);
  last.current=point;
  update(markScratchTrail(cells.current,prev,point,b,dt,SCRATCH_COIN_DIAMETER/2));
 };
 const gesture=useRef(PanResponder.create({
  onStartShouldSetPanResponder:()=>true,
  onMoveShouldSetPanResponder:()=>true,
  onPanResponderGrant:e=>handler.current(e,'start'),
  onPanResponderMove:e=>handler.current(e,'move'),
  onPanResponderRelease:()=>{last.current=null;coinRef.current&&
   (coinRef.current={...coinRef.current,rotation:coinRef.current.rotation*.28});
   if(coinRef.current)setCoin(coinRef.current);
   setScratched(new Set(cells.current));},
  onPanResponderTerminate:()=>{last.current=null;setScratched(new Set(cells.current));}
 })).current;
 const accessibilityStep=()=>{
  if(!round||triggered.current)return;
  const next=addAccessibleScratchProgress(cells.current,scratchCoverage(cells.current)+.1);
  update(next);
 };
 const progress=Math.min(100,Math.floor(scratchCoverage(scratched)*100));
 const bg=backgrounds[cardId]||'#FFF8E7';
 const symbols=shown?.symbols||['?','?','?'];
 const diameter=Math.min(SCRATCH_COIN_DIAMETER,Math.max(24,Math.min(coatingBounds.width,coatingBounds.height)*.8||56));
 const coinWidth=diameter*SCRATCH_COIN_ASPECT;
 const resting=coin||{x:coatingBounds.width*.5,y:coatingBounds.height*.82,rotation:0};
 return <View style={{gap:7}}>
  <View style={{width:'100%',aspectRatio:SCRATCH_FRAME.width/SCRATCH_FRAME.height,position:'relative'}} testID="scratch_card_stage">
   <View style={{
     position:'absolute',
     left:(SCRATCH_FRAME.cardX/SCRATCH_FRAME.width*100)+'%',
     top:(SCRATCH_FRAME.cardY/SCRATCH_FRAME.height*100)+'%',
     width:(SCRATCH_FRAME.cardWidth/SCRATCH_FRAME.width*100)+'%',
     height:(SCRATCH_FRAME.cardHeight/SCRATCH_FRAME.height*100)+'%',
     backgroundColor:bg,borderRadius:12,overflow:'hidden',borderWidth:1,borderColor:'#FFFFFFAA'
    }}>
    <View style={{flex:1,flexDirection:'row',justifyContent:'space-evenly',alignItems:'center',gap:3,padding:5}}>
     {symbols.map((symbol,i)=><View key={i} style={{flex:1,alignItems:'center',justifyContent:'center',
       backgroundColor:'#FFFFFFB8',borderWidth:1,borderColor:'#D7B5578C',borderRadius:9,paddingVertical:4}}>
      <Text style={{fontSize:Math.min(25,Math.max(14,bounds.current.width/14)),textAlign:'center'}}>{symbol}</Text>
      <Text style={{fontSize:9,fontWeight:'900',color:'#2F3A36'}}>PRIZE</Text>
     </View>)}
    </View>
    {!revealed?<View
      testID="puppy-scratch-coating" {...(pending?gesture.panHandlers:{})}
      accessibilityRole="adjustable"
      accessibilityLabel={'Puppy scratch card, '+progress+' percent cleared'}
      accessibilityHint="Drag the Scratch Token to remove 68 percent of coating. TalkBack users can use the increment action to clear more coating."
      accessibilityActions={[{name:'increment',label:'Scratch more coating'}]}
      onAccessibilityAction={event=>{if(event.nativeEvent.actionName==='increment')accessibilityStep();}}
      onLayout={e=>{const b={width:e.nativeEvent.layout.width,height:e.nativeEvent.layout.height};
        bounds.current=b;setCoatingBounds(old=>old.width===b.width&&old.height===b.height?old:b);}}
      style={{position:'absolute',left:0,right:0,top:0,bottom:0,borderRadius:12,overflow:'hidden'}}
     >
      <View pointerEvents="none" style={{position:'absolute',left:0,right:0,top:0,bottom:0,opacity:.95}}>
       {Array.from({length:SCRATCH_ROWS},(_,row)=><View key={row} style={{flex:1,flexDirection:'row'}}>
        {Array.from({length:SCRATCH_COLUMNS},(_,col)=>{
         const id=row*SCRATCH_COLUMNS+col;
         const alpha=(row*4+col*3)%7;
         return <View key={col} style={{flex:1,backgroundColor:scratched.has(id)?'transparent':
          ['#F1F4F6','#E6EBED','#D8DDE1','#CDD3D8','#C4CAD0','#BEC5CA','#B5BCC3'][alpha]}}/>;
        })}
       </View>)}
      </View>
      {!pending||progress<55?<View pointerEvents="none" style={{position:'absolute',top:'32%',alignSelf:'center',
       opacity:pending?Math.max(0.06,1-progress/80):1}}>
       <Text style={{fontSize:18,fontWeight:'900',fontStyle:'italic',color:'#58616AA6',textAlign:'center'}}>Scratch</Text>
       <Text style={{fontSize:18,fontWeight:'900',fontStyle:'italic',color:'#58616AA6',textAlign:'center'}}>Here!</Text>
      </View>:null}
     </View>:null}
    {pending&&coatingBounds.width>0?<Image pointerEvents="none"
      source={TOKEN} resizeMode="contain" accessibilityLabel="Scratch Token"
      style={{position:'absolute',width:coinWidth,height:diameter,
       left:Math.max(0,resting.x-coinWidth/2),top:Math.max(0,resting.y-diameter/2),
       transform:[{rotate:(resting.rotation||0)+'deg'}]}}/>:null}
   </View>
   <Image pointerEvents="none" source={borderImage} resizeMode="stretch"
    style={{position:'absolute',top:0,left:0,width:'100%',height:'100%'}}/>
  </View>
  {shown?<View style={{alignItems:'center',marginHorizontal:12,paddingVertical:6,paddingHorizontal:10,
    backgroundColor:'#075843',borderRadius:12,borderWidth:1,borderColor:'#FFFFFF29'}}>
   <Text style={{fontSize:12,color:'#FFFFFF',fontWeight:'900',textAlign:'center'}}>
    {pending?'Scratch with the Scratch Token • '+progress+'%':
     revealed?'Card revealed • '+shown.payout+' Chips returned':'Scratch to reveal.'}
   </Text>
  </View>:null}
 </View>;
}
