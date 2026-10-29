import {themedStyles} from '../theme.js';
import React,{useEffect,useMemo,useRef,useState} from 'react';
import {Animated,Easing,Image,Pressable,Text,View,useWindowDimensions} from 'react-native';
import {PLINKO_V6,PLINKO_BINS,validPlinkoWager,plinkoLabel,plinkoFrame,
 plinkoAnimationSamples,plinkoResult} from '../engine/plinkoV6.js';

// Original Kotlin drawable assets; no redrawn peg grid or stock illustrations.
const art={
 header:require('../../assets/casino/puppy_plinko_header.png'),
 pegs:require('../../assets/casino/puppy_plinko_pegs.png'),
 ball:require('../../assets/casino/puppy_plinko_ball.png'),
 glass:require('../../assets/casino/puppy_plinko_glass.png'),
 frame:require('../../assets/casino/puppy_plinko_frame.png'),
 bins:require('../../assets/casino/puppy_plinko_bins.png'),
 drop:require('../../assets/casino/puppy_plinko_drop_button.png')
};

function PlinkoCabinet({round,result,motion,status}){
 const {width:screenWidth}=useWindowDimensions();
 const [measured,setMeasured]=useState(0);
 const width=Math.max(1,measured||Math.min(screenWidth-40,460));
 const metrics=plinkoFrame(width);
 const display=plinkoResult(round)||result;
 const progress=useRef(new Animated.Value(0)).current;
 const samples=useMemo(()=>plinkoAnimationSamples(display),[
  JSON.stringify(display?.pathRight||[]),JSON.stringify(display?.bounceJitterPermille||[])
 ]);
 useEffect(()=>{
  if(!round){progress.setValue(display?1:0);return;}
  const ms=motion?PLINKO_V6.ballMs:0;
  const elapsed=Math.max(0,Date.now()-round.createdAt);
  const fraction=ms?Math.min(1,elapsed/ms):1;
  progress.setValue(fraction);
  if(fraction>=1)return;
  const animation=Animated.timing(progress,{toValue:1,duration:ms-elapsed,easing:Easing.inOut(Easing.ease),useNativeDriver:true});
  animation.start();return ()=>animation.stop();
 },[round?.createdAt,motion,progress]);
 const {boardWidth:w,boardHeight:h,stageHeight,boardTop,headerWidth,headerHeight,bin}=metrics;
 const ballSize=w*PLINKO_V6.ballFraction;
 const inputRange=samples.inputRange;
 const translateX=progress.interpolate({inputRange,outputRange:samples.xs.map(x=>x*w-ballSize/2),extrapolate:'clamp'});
 const translateY=progress.interpolate({inputRange,outputRange:samples.ys.map(y=>y*h-ballSize/2),extrapolate:'clamp'});
 const [binHit,setBinHit]=useState(-1);
 useEffect(()=>{
  if(!round){setBinHit(display?.binIndex??-1);return;}
  setBinHit(-1);
  const timeLeft=Math.max(0,Math.ceil((PLINKO_V6.ballMs*.92)-(Date.now()-round.createdAt)));
  const token=setTimeout(()=>setBinHit(round.binIndex),timeLeft);
  return ()=>clearTimeout(token);
 },[round?.createdAt,round?.binIndex,display?.binIndex]);
 return <View style={s.stageContainer} onLayout={e=>{
  const next=Math.round(e.nativeEvent.layout.width);
  if(next>0&&Math.abs(next-measured)>1)setMeasured(next);
 }}>
  <View style={{width:w,height:stageHeight,alignSelf:'center'}}>
   <View style={{position:'absolute',top:boardTop,width:w,height:h}}>
    <Image source={art.pegs} resizeMode="stretch" style={[s.absolute,{
     left:w*.08,top:h*PLINKO_V6.pegsTop,
     width:w*PLINKO_V6.pegsWidth,height:h*PLINKO_V6.pegsHeight
    }]}/>
    <Animated.Image source={art.ball} resizeMode="contain" accessibilityLabel="Plinko ball"
     style={[s.absolute,{width:ballSize,height:ballSize,transform:[{translateX},{translateY}]}]}/>
    <Image source={art.glass} resizeMode="stretch" style={[s.absolute,{width:w,height:h,opacity:.58}]}/>
    <Image source={art.frame} resizeMode="stretch" style={[s.absolute,{width:w,height:h}]}/>
    <View style={[s.absolute,bin]}>
     <Image source={art.bins} resizeMode="stretch" style={[s.absolute,{width:bin.width,height:bin.height}]}/>
     <View style={[s.binLabels,{left:bin.width*PLINKO_V6.binPadding,right:bin.width*PLINKO_V6.binPadding,
      top:bin.height*.24,bottom:bin.height*.16}]}>
      {PLINKO_BINS.map((m,i)=><Text key={i}
       numberOfLines={1} adjustsFontSizeToFit minimumFontScale={.65}
       style={[s.binLabel,binHit===i&&s.binSelected]}>{plinkoLabel(m)}</Text>)}
     </View>
    </View>
   </View>
   <Image source={art.header} resizeMode="contain" accessibilityLabel="Puppy Plinko"
    style={[s.absolute,{top:0,left:(w-headerWidth)/2,width:headerWidth,height:headerHeight}]}/>
   <View style={s.statusCard}><Text style={s.status} numberOfLines={2}>{status}</Text></View>
  </View>
 </View>;
}

export default function PlinkoV6({game,pending,lastResult,onAction,onBack}){
 const [wager,setWager]=useState(20);
 const [showRules,setShowRules]=useState(false);
 const [error,setError]=useState('');
 const actionRef=useRef(onAction);actionRef.current=onAction;
 const motion=game.animationsEnabled!==false;
 const display=plinkoResult(pending)||lastResult;
 const blocked=!!(pending||game.pendingSlots||game.pendingRoulette||game.pendingScratch||game.pendingWheel||game.casinoRound);
 const enabled=!game.casinoReadOnly&&!game.gameplayBusy&&!blocked&&validPlinkoWager(wager)&&game.casinoChips>=wager;
 useEffect(()=>{
  if(!pending)return;
  const duration=motion?PLINKO_V6.dropMs:PLINKO_V6.reducedMotionMs;
  const elapsed=Math.max(0,Date.now()-pending.createdAt);
  const timer=setTimeout(()=>actionRef.current({type:'plinkoReveal',now:Date.now()}),Math.max(10,duration-elapsed));
  return ()=>clearTimeout(timer);
 },[pending?.createdAt,motion]);
 const start=()=>{
  if(!enabled){setError('Finish the current round or choose an affordable wager.');return;}
  setError('');
  const pathRight=Array.from({length:PLINKO_V6.rows},()=>Math.random()<.5);
  const bounceJitterPermille=Array.from({length:PLINKO_V6.rows},
   ()=>Math.floor(Math.random()*(PLINKO_V6.maxJitter*2+1))-PLINKO_V6.maxJitter);
  actionRef.current({type:'plinkoStart',wager,pathRight,bounceJitterPermille,now:Date.now()});
 };
 const status=pending?'Ball in motion…':
  display?'Landed on '+plinkoLabel(display.multiplierHundredths)+' • '+display.payout+' Chips returned':
  'Choose a wager and drop the ball.';
 return <View style={s.page}>
  <Pressable accessibilityRole="button" style={s.back} onPress={onBack}><Text style={s.whiteBold}>‹ Puppy Casino</Text></Pressable>
  <Text style={s.title}>Pup Plinko</Text>
  <Text style={s.muted}>Drop the Plinko ball through eight rows of pegs into one of nine blue-felt prize pockets. The path is committed before the animation.</Text>
  <View style={s.wallet}>
   <Text style={{fontSize:28}}>🐾</Text>
   <View style={{flex:1}}><Text style={s.whiteBold}>Casino Chip Wallet</Text>
    <Text style={s.walletAmount}>{game.casinoChips.toLocaleString('en-US')} Casino Chips</Text></View>
   <Text style={s.rtp}>{PLINKO_V6.rtp} RTP</Text>
  </View>
  <PlinkoCabinet round={pending} result={lastResult} motion={motion} status={status}/>
  <Text style={s.section}>Wager</Text>
  <View style={s.presets}>{PLINKO_V6.betPresets.map(n=><Pressable key={n} accessibilityRole="button"
   accessibilityState={{selected:wager===n}} onPress={()=>setWager(n)}
   style={[s.preset,n===wager&&s.selectedPreset]}><Text style={s.whiteBold}>{n.toLocaleString('en-US')}</Text></Pressable>)}</View>
  <Pressable accessibilityRole="button" accessibilityLabel={'Drop Ball · '+wager+' Chips'}
   accessibilityState={{disabled:!enabled}} disabled={!enabled} onPress={start}
   style={[s.dropButton,!enabled&&s.disabled]}>
   <Image source={art.drop} resizeMode="contain" style={{width:'100%',height:88}}/>
   {!enabled?<Text style={s.muted}>{blocked?'Round In Progress':game.casinoChips<wager?'Not Enough Chips':'Plinko Locked'}</Text>:null}
  </Pressable>
  {error?<Text accessibilityLiveRegion="polite" style={s.error}>{error}</Text>:null}
  <Pressable accessibilityRole="button" onPress={()=>setShowRules(!showRules)} style={s.ruleToggle}>
   <Text style={s.whiteBold}>{showRules?'Hide Plinko Rules':'ⓘ Plinko Rules'}</Text>
  </Pressable>
  {showRules?<View style={s.rules}>
   <Text style={s.section}>How Pup Plinko works</Text>
   <Text style={s.muted}>• Eight saved left/right bounces determine one of nine bins.</Text>
   <Text style={s.muted}>• Published RTP: {PLINKO_V6.rtp}.</Text>
   <Text style={s.muted}>• The exact path, bin, multiplier and payout are committed before the ball moves.</Text>
   <Text style={s.muted}>• All wagers and payouts use the Casino Chip wallet.</Text>
  </View>:null}
  <Text style={s.muted}>Local guest preview only; no real-money play or authoritative cloud Casino transactions.</Text>
 </View>;
}
const s=themedStyles(()=>({
 page:{width:'100%',gap:12,backgroundColor:'#0C0F12'},back:{minHeight:44,alignSelf:'flex-start',justifyContent:'center'},
 title:{fontSize:27,fontWeight:'900',color:'#FFFFFF'},whiteBold:{color:'#FFFFFF',fontWeight:'900'},
 muted:{color:'#B8C6CF',fontSize:12},section:{color:'#FFFFFF',fontWeight:'900',fontSize:18},
 wallet:{borderRadius:18,padding:14,backgroundColor:'#192A2D',borderWidth:1,borderColor:'#506067',flexDirection:'row',alignItems:'center',gap:10},
 walletAmount:{color:'#FFFFFF',fontSize:20,fontWeight:'900'},rtp:{color:'#75D8C8',fontSize:12,fontWeight:'900'},
 absolute:{position:'absolute'},stageContainer:{width:'100%',alignItems:'center'},
 statusCard:{position:'absolute',bottom:4,alignSelf:'center',maxWidth:'95%',backgroundColor:'#192A2DEB',paddingHorizontal:14,paddingVertical:7,borderRadius:14,borderWidth:1,borderColor:'#75D8C870'},
 status:{color:'#FFFFFF',fontWeight:'900',textAlign:'center',fontSize:12},
 binLabels:{position:'absolute',flexDirection:'row',alignItems:'center',justifyContent:'space-evenly'},
 binLabel:{flex:1,color:'#FFFFFF',fontSize:9,fontWeight:'900',textAlign:'center'},
 binSelected:{fontSize:10,transform:[{scale:1.12}]},
 presets:{flexDirection:'row',gap:6},
 preset:{flex:1,alignItems:'center',justifyContent:'center',backgroundColor:'#28393C',paddingVertical:11,borderRadius:15},
 selectedPreset:{backgroundColor:'#245450',borderWidth:1,borderColor:'#6DD5C5'},
 dropButton:{alignItems:'center',justifyContent:'center',minHeight:88},
 disabled:{opacity:.42},error:{color:'#FF8B90'},
 ruleToggle:{alignItems:'center',padding:12,borderWidth:1,borderColor:'#607078',borderRadius:12},
 rules:{borderRadius:18,padding:14,gap:8,backgroundColor:'#192A2D'}
}));
