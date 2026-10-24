import React,{useEffect,useRef,useState} from 'react';
import {Animated,Easing,Image,Pressable,StyleSheet,Text,View,useWindowDimensions} from 'react-native';
import {SLOTS_V6,SLOT_SYMBOLS,SLOTS_RULES,slotsFrame,slotsResultLabel,validSlotsWager,slotDrawIndex,slotsResultFromRound} from '../engine/slotsV6.js';

const art={
 machine:require('../../assets/casino/puppy_slots_machine.png'),
 mascot:require('../../assets/casino/puppy_slots_mascot.png'),
 leverBase:require('../../assets/casino/puppy_slots_lever_base.png'),
 lever:require('../../assets/casino/puppy_slots_lever_handle.png'),
 symbols:{
  '🍪':require('../../assets/casino/slot_treat.png'),
  '🎾':require('../../assets/casino/slot_ball.png'),
  '🐾':require('../../assets/casino/slot_paw.png'),
  '🎟️':require('../../assets/casino/slot_ticket.png'),
  '🐶':require('../../assets/casino/slot_puppy.png'),
  '⭐':require('../../assets/casino/slot_star.png')
 }
};
const idle=['🍪','🐶','🐾'];
const revealAtIndex=(result,i)=>result?.symbols?.[i]||idle[i];

export default function SlotsV6({game,pending,lastResult,onAction,onBack}){
 const {width:windowWidth}=useWindowDimensions();
 const [sceneWidth,setSceneWidth]=useState(Math.max(220,Math.min(windowWidth-40,520)));
 const [wager,setWager]=useState(100);
 const [help,setHelp]=useState(false);
 const [revealStep,setRevealStep]=useState(0);
 const [tick,setTick]=useState(0);
 const [message,setMessage]=useState('');
 const phaseToken=useRef('');
 const action=useRef(onAction);action.current=onAction;
 const leverAnim=useRef(new Animated.Value(0)).current;
 const bob=useRef(new Animated.Value(-2)).current;
 const spin=Boolean(pending);
 const displayed=slotsResultFromRound(pending)||lastResult;
 const stopped=pending?revealStep:3;
 const canSpin=!pending&&validSlotsWager(wager)&&game.casinoChips>=wager&&!game.casinoRound&&!game.pendingScratch&&!game.pendingRoulette&&!game.pendingPlinko&&!game.pendingWheel;
 const frame=slotsFrame(sceneWidth);
 const motion=game.animationsEnabled!==false;
 useEffect(()=>{
  if(!motion){bob.setValue(0);return;}
  const loop=Animated.loop(Animated.sequence([
   Animated.timing(bob,{toValue:3,duration:900,easing:Easing.inOut(Easing.ease),useNativeDriver:true}),
   Animated.timing(bob,{toValue:-2,duration:900,easing:Easing.inOut(Easing.ease),useNativeDriver:true})
  ]));
  loop.start();
  return ()=>{loop.stop();bob.setValue(0);};
 },[bob,motion]);
 useEffect(()=>{
  if(!pending){setRevealStep(3);setTick(0);phaseToken.current='';return;}
  const token=String(pending.createdAt)+':'+pending.rolls.join(',');
  if(token!==phaseToken.current){
   phaseToken.current=token;
   setRevealStep(0);setTick(0);
   if(motion){
    leverAnim.setValue(0);
    Animated.sequence([
     Animated.timing(leverAnim,{toValue:24,duration:140,useNativeDriver:true}),
     Animated.spring(leverAnim,{toValue:0,friction:5,tension:95,useNativeDriver:true})
    ]).start();
   }
  }
  const duration=motion?SLOTS_V6.revealMs:SLOTS_V6.reducedMotionRevealMs;
  const initialTime=Math.max(0,Date.now()-pending.createdAt);
  const left=Math.max(50,duration-initialTime);
  const timer=setTimeout(()=>action.current({type:'slotsReveal',now:Date.now()}),left);
  if(!motion)return ()=>clearTimeout(timer);
  const interval=setInterval(()=>{
   const elapsed=Date.now()-pending.createdAt;
   setTick(Math.max(0,Math.floor(elapsed/SLOTS_V6.tickMs)));
   setRevealStep(elapsed>=SLOTS_V6.reelTicks[2]*SLOTS_V6.tickMs?3:
    elapsed>=SLOTS_V6.reelTicks[1]*SLOTS_V6.tickMs?2:
    elapsed>=SLOTS_V6.reelTicks[0]*SLOTS_V6.tickMs?1:0);
  },SLOTS_V6.tickMs);
  return ()=>{clearTimeout(timer);clearInterval(interval);};
 },[pending?.createdAt,motion,leverAnim]);
 const start=()=>{
  if(!canSpin){setMessage('Finish your current round or select an affordable wager.');return;}
  setMessage('');
  action.current({type:'slotsStart',wager,rolls:[0,0,0].map(()=>Math.floor(Math.random()*100)),now:Date.now()});
 };
 const symbols=[0,1,2].map(i=>pending&&motion&&stopped<=i?
  SLOT_SYMBOLS[slotDrawIndex(i,tick)].emoji:revealAtIndex(displayed,i));
 return <View style={s.page}>
  <Pressable accessibilityRole="button" onPress={onBack} style={s.back}>
   <Text style={s.white}>‹ Puppy Casino</Text>
  </Pressable>
  <Text style={s.title}>Puppy Slots</Text>
  <Text style={s.muted}>Three weighted reels. The outcome is saved before it is revealed.</Text>
  <View style={s.wallet}>
   <Text style={s.walletPaw}>🐾</Text><View style={{flex:1}}>
    <Text style={s.whiteBold}>Casino Chip Wallet</Text>
    <Text style={s.walletTotal}>{game.casinoChips.toLocaleString('en-US')} Casino Chips</Text>
   </View>
   <Text style={s.rtp}>{SLOTS_V6.publishedRtp} RTP</Text>
  </View>
  <View style={s.machineCard}>
   <View style={{width:'100%'}} onLayout={e=>{
    const next=Math.round(e.nativeEvent.layout.width);
    if(next>0&&Math.abs(next-sceneWidth)>1)setSceneWidth(next);
   }}>
    <View style={{height:frame.height,width:frame.width}}>
     <Animated.Image accessibilityLabel="Puppy Slots mascot" source={art.mascot} resizeMode="contain"
      style={[s.absolute,frame.mascot,{transform:[{translateY:bob}]}]}/>
     <Image accessibilityLabel="Puppy Slots machine" source={art.machine} resizeMode="contain"
      style={[s.absolute,frame.machine]}/>
     {symbols.map((symbol,i)=><View key={i} testID={'slot_reel_'+i}
      accessibilityLabel={'Reel '+(i+1)+': '+(SLOT_SYMBOLS.find(x=>x.emoji===symbol)?.name||'Unknown')}
      style={[s.absolute,frame.reels[i],{alignItems:'center',justifyContent:'center'}]}>
      <Image source={art.symbols[symbol]} resizeMode="contain"
       style={{width:'76%',height:'76%'}}/>
      {spin?<View pointerEvents="none" style={[s.reelGlow,{opacity:motion?0.65:0.4}]}/>:null}
     </View>)}
     <Image source={art.leverBase} resizeMode="contain" style={[s.absolute,frame.leverBase]}/>
     <Pressable accessibilityRole="button" accessibilityLabel="Pull Puppy Slots lever"
      accessibilityState={{disabled:!canSpin}} disabled={!canSpin} onPress={start}
      style={[s.absolute,frame.lever]}>
      <Animated.Image source={art.lever} resizeMode="stretch"
       style={{height:'100%',width:'100%',transform:[{rotate:leverAnim.interpolate({inputRange:[0,24],outputRange:['0deg','24deg']})}]}}/>
     </Pressable>
    </View>
   </View>
   {spin?<><Text style={s.machineStatus}>Reel 1 → Reel 2 → Reel 3</Text>
    <Text style={s.machineDetail}>Result reveals after the final reel locks.</Text>
   </>:displayed?<><Text style={s.machineStatus}>{slotsResultLabel(displayed)}</Text>
    <Text style={s.machineDetail}>{displayed.payout} Chips returned</Text>
   </>:<Text style={s.machineDetail}>Choose a wager and pull the machine.</Text>}
  </View>
  <Text style={s.section}>Wager</Text>
  <Text style={s.muted}>Multipliers are the total Casino Chip return, including the original wager.</Text>
  <View style={s.wagerRow}>{SLOTS_V6.presets.map(n=><Pressable key={n}
   accessibilityRole="button" accessibilityState={{selected:wager===n}}
   onPress={()=>setWager(n)} style={[s.wagerChoice,wager===n&&s.selectedChoice]}>
    <Text style={s.whiteBold}>{n.toLocaleString('en-US')}</Text>
   </Pressable>)}</View>
  <Pressable accessibilityRole="button" accessibilityState={{disabled:!canSpin}}
   disabled={!canSpin} onPress={start} style={[s.pullButton,!canSpin&&s.disabled]}>
   <Text style={s.pullText}>{spin?'Round In Progress':game.casinoChips<wager?'Not Enough Chips':'Pull Lever · '+wager+' Chips'}</Text>
  </Pressable>
  {message?<Text accessibilityLiveRegion="polite" style={s.error}>{message}</Text>:null}
  <Pressable accessibilityRole="button" onPress={()=>setHelp(!help)} style={s.helpButton}>
   <Text style={s.whiteBold}>{help?'Hide Rules':'ⓘ Slots Rules'}</Text>
  </Pressable>
  {help?<View style={s.rules}>
   <Text style={s.section}>How Puppy Slots works</Text>
   {SLOTS_RULES.map(line=><Text key={line} style={s.muted}>• {line}</Text>)}
  </View>:null}
  <View style={s.rules}>
   <Text style={s.section}>Published payout table</Text>
   <View style={s.ruleRow}><Text style={s.white}>Any matching pair</Text><Text style={s.whiteBold}>0.5×</Text></View>
   {SLOT_SYMBOLS.map(symbol=><View key={symbol.id} style={s.ruleRow}>
    <Text style={s.white}>3× {symbol.emoji} {symbol.name}</Text><Text style={s.whiteBold}>{symbol.multiplier}×</Text>
   </View>)}
  </View>
  <View style={s.rules}>
   <Text style={s.section}>Published reel weights</Text>
   <Text style={s.muted}>Each reel uses the same 100-point distribution.</Text>
   {SLOT_SYMBOLS.map(symbol=><View key={symbol.id} style={s.ruleRow}>
    <Text style={s.white}>{symbol.emoji} {symbol.name}</Text><Text style={s.whiteBold}>{symbol.weight}%</Text>
   </View>)}
  </View>
  <Text style={s.muted}>Guest preview only — no production casino balance, authoritative server settlement or cash prizes.</Text>
 </View>;
}
const s=StyleSheet.create({
 page:{gap:12,width:'100%'},
 back:{alignSelf:'flex-start',minHeight:44,justifyContent:'center'},
 title:{fontSize:26,fontWeight:'900',color:'#F7F9FC'},
 section:{fontSize:17,fontWeight:'900',color:'#F7F9FC'},
 white:{color:'#F7F9FC'},
 whiteBold:{color:'#F7F9FC',fontWeight:'900'},
 muted:{fontSize:12,color:'#B5C4CA'},
 wallet:{borderRadius:18,borderColor:'#4C5960',borderWidth:1,backgroundColor:'#1B2930',
  flexDirection:'row',alignItems:'center',gap:10,padding:14},
 walletPaw:{fontSize:30},walletTotal:{fontSize:22,fontWeight:'900',color:'#F7F9FC'},
 rtp:{fontWeight:'800',fontSize:12,color:'#72DEC7'},
 machineCard:{borderRadius:26,backgroundColor:'#17191F',padding:10,borderColor:'#58C4B9',
  borderWidth:2,alignItems:'center',gap:4,overflow:'hidden'},
 absolute:{position:'absolute'},
 reelGlow:{position:'absolute',left:0,top:0,right:0,bottom:0,borderRadius:12,borderWidth:2,borderColor:'#D2E7D7'},
 machineStatus:{color:'#FFFFFF',fontSize:15,fontWeight:'900',textAlign:'center'},
 machineDetail:{fontSize:12,color:'#DDDDDD',textAlign:'center'},
 wagerRow:{flexDirection:'row',gap:6},
 wagerChoice:{flex:1,borderRadius:17,backgroundColor:'#263940',paddingHorizontal:4,paddingVertical:10,alignItems:'center'},
 selectedChoice:{borderColor:'#7DD8D2',borderWidth:1,backgroundColor:'#275450'},
 pullButton:{borderRadius:14,backgroundColor:'#287C72',minHeight:48,justifyContent:'center',alignItems:'center'},
 disabled:{opacity:0.38},pullText:{fontWeight:'900',color:'#FFFFFF'},
 helpButton:{borderRadius:14,borderWidth:1,borderColor:'#53686F',padding:12,alignItems:'center'},
 error:{padding:10,color:'#FF8B91'},
 rules:{borderRadius:18,backgroundColor:'#1B2930',padding:14,gap:7},
 ruleRow:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10}
});
