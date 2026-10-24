import PuppyPointer from './PuppyPointer.jsx';
import {themedStyles} from '../theme.js';
import React,{useEffect,useRef,useState} from 'react';
import {Animated,Easing,Image,Modal,Pressable,ScrollView,Text,View,useWindowDimensions} from 'react-native';
import {ROULETTE_V6,ROULETTE_BETS,ROULETTE_OUTSIDE,rouletteBoardZones,rouletteWheelTarget,
 rouletteColorHex,rouletteColor,rouletteValidWager,rouletteBetLabel,rouletteResult} from '../engine/rouletteV6.js';

const IMAGES={
 bowl:require('../../assets/casino/puppy_roulette_wheel_base.png'),
 rotor:require('../../assets/casino/puppy_roulette_rotor.png'),
 ball:require('../../assets/casino/puppy_roulette_ball.png'),
 table:require('../../assets/casino/puppy_roulette_table.png')
};
const OUTSIDE=new Set(ROULETTE_OUTSIDE);
const hex={RED:'#B92B35',BLACK:'#16181C',GREEN:'#187A45'};
function RouletteBoardTarget({rect,label,selected,onPress}){
 return <Pressable accessibilityRole="button" accessibilityLabel={label}
  accessibilityState={{selected}} onPress={onPress}
  style={[styles.target,rect,selected&&styles.selectedTarget]}/>;
}
function RouletteBoard({type,number,onChoose,onPicker}){
 const {width:windowWidth}=useWindowDimensions();
 const [width,setWidth]=useState(Math.max(180,windowWidth-54));
 const frame=rouletteBoardZones(width);
 return <View>
  <Text style={styles.boardTitle}>BET BOARD</Text>
  <View style={styles.boardMeasured} onLayout={e=>{
   const next=Math.round(e.nativeEvent.layout.width);
   if(next>0&&Math.abs(width-next)>1)setWidth(next);
  }}>
   <View style={{width:frame.width,height:frame.height}}>
    <Image source={IMAGES.table} resizeMode="stretch" accessibilityLabel="Puppy Roulette betting table"
     style={{width:frame.width,height:frame.height,position:'absolute'}}/>
    <RouletteBoardTarget rect={frame.zero} label="Bet on 0"
     selected={type==='STRAIGHT'&&number===0} onPress={()=>onChoose('STRAIGHT',0)}/>
    {frame.numbers.map(cell=><RouletteBoardTarget key={cell.number} rect={cell}
     label={'Bet on '+cell.number} selected={type==='STRAIGHT'&&number===cell.number}
     onPress={()=>onChoose('STRAIGHT',cell.number)}/>)}
    {frame.outside.map(cell=><RouletteBoardTarget key={cell.type} rect={cell}
     label={'Bet on '+(ROULETTE_BETS.find(b=>b.id===cell.type)?.label||cell.type)}
     selected={type===cell.type} onPress={()=>onChoose(cell.type)}/>)}
   </View>
  </View>
  <Pressable onPress={onPicker} accessibilityRole="button" style={styles.outlineButton}>
   <Text style={styles.whiteBold}>{type==='STRAIGHT'?'Exact number: '+number+' · picker':'Choose exact landing number'}</Text>
  </Pressable>
 </View>;
}
function RouletteWheel({display,pending,animationsEnabled}){
 const wheel=useRef(new Animated.Value(0)).current;
 const ball=useRef(new Animated.Value(0)).current;
 const radius=useRef(new Animated.Value(0)).current;
 const shown=rouletteResult(pending)||display;
 const active=Boolean(pending);
 useEffect(()=>{
  const target=shown&&rouletteWheelTarget(shown.winningNumber);
  if(!target){wheel.setValue(0);ball.setValue(0);radius.setValue(0);return;}
  if(!active||!animationsEnabled){
   wheel.setValue(active?0:target.wheel%360);
   ball.setValue(target.pocketAngle+90+(active?0:target.wheel));
   radius.setValue(1);return;
  }
  wheel.setValue(0);ball.setValue(0);radius.setValue(0);
  const elapsed=Math.max(0,Date.now()-pending.createdAt);
  // The underlying result is fixed before the animation begins.
  const wheelMotion=Animated.timing(wheel,{toValue:target.wheel,duration:2400,easing:Easing.inOut(Easing.ease),useNativeDriver:true});
  const ballMotion=Animated.sequence([
   Animated.timing(ball,{toValue:ROULETTE_V6.ballCruise+90,duration:1750,easing:Easing.linear,useNativeDriver:true}),
   Animated.timing(ball,{toValue:target.ball+90,duration:650,easing:Easing.inOut(Easing.ease),useNativeDriver:true})
  ]);
  const dropMotion=Animated.sequence([
   Animated.delay(Math.max(0,1650-elapsed)),
   Animated.timing(radius,{toValue:1,duration:750,easing:Easing.inOut(Easing.ease),useNativeDriver:true})
  ]);
  const whole=Animated.parallel([wheelMotion,ballMotion,dropMotion]);whole.start();
  return ()=>whole.stop();
 },[shown?.winningNumber,pending?.createdAt,animationsEnabled,wheel,ball,radius]);
 const wheelRotation=wheel.interpolate({inputRange:[0,ROULETTE_V6.wheelTarget],outputRange:['0deg',ROULETTE_V6.wheelTarget+'deg']});
 const target=shown?rouletteWheelTarget(shown.winningNumber):null;
 const ballEnd=target?target.ball+90:1;
 const ballRotation=ball.interpolate({inputRange:[Math.min(ROULETTE_V6.ballCruise+90,ballEnd)-10,0,Math.max(1,ballEnd)+10],outputRange:[Math.min(ROULETTE_V6.ballCruise+90,ballEnd)-10+'deg','0deg',Math.max(1,ballEnd)+10+'deg'],extrapolate:'extend'});
 const drop=radius.interpolate({inputRange:[0,1],outputRange:[0,ROULETTE_V6.ballOrbit*.13]});
 return <View style={styles.wheelScene}>
  <Image source={IMAGES.bowl} resizeMode="contain" style={styles.wheelBowl}/>
  <Animated.Image source={IMAGES.rotor} accessibilityLabel="European roulette rotor"
   resizeMode="contain" style={[styles.wheelRotor,{transform:[{rotate:wheelRotation}]}]}/>
  <Animated.View pointerEvents="none" style={[styles.wheelOrbit,{transform:[{rotate:ballRotation}]}]}>
   <Animated.Image source={IMAGES.ball} accessibilityLabel="Roulette ball"
    resizeMode="contain" style={[styles.ball,{transform:[{translateY:drop}]}]}/>
  </Animated.View>
  <View pointerEvents="none" style={styles.wheelLabel}>
   <Text style={styles.whiteBold}>{!active&&shown?shown.winningNumber:'SPIN'}</Text>
  </View>
 </View>;
}
export default function RouletteV6({game,pending,lastResult,onAction,onBack}){
 const [wager,setWager]=useState(100);
 const [selectedType,setSelectedType]=useState('RED');
 const [selectedNumber,setSelectedNumber]=useState(0);
 const [picker,setPicker]=useState(false);
 const [help,setHelp]=useState(false);
 const [message,setMessage]=useState('');
 const onActionRef=useRef(onAction);onActionRef.current=onAction;
 const motion=game.animationsEnabled!==false;
 const display=rouletteResult(pending)||lastResult;
 const canSpin=!pending&&!game.pendingSlots&&!game.pendingScratch&&!game.pendingPlinko&&!game.pendingWheel&&!game.casinoRound&&
  rouletteValidWager(wager)&&game.casinoChips>=wager;
 useEffect(()=>{
  if(!pending)return;
  const duration=motion?ROULETTE_V6.spinMs:ROULETTE_V6.reducedMotionMs;
  const elapsed=Math.max(0,Date.now()-pending.createdAt);
  const timeout=setTimeout(()=>onActionRef.current({type:'rouletteReveal',now:Date.now()}),
   Math.max(10,duration-elapsed));
  return ()=>clearTimeout(timeout);
 },[pending?.createdAt,pending?.winningNumber,motion]);
 const choose=(type,number)=>{
  setSelectedType(type);
  if(type==='STRAIGHT')setSelectedNumber(number);
 };
 const bet={type:selectedType,...(selectedType==='STRAIGHT'?{number:selectedNumber}:{})};
 const pull=()=>{
  if(!canSpin){setMessage('Finish the current round or choose an affordable wager.');return;}
  setMessage('');
  onActionRef.current({type:'rouletteStart',wager,bet,winningNumber:Math.floor(Math.random()*37),now:Date.now()});
 };
 const rules=[['Straight number 0–36','36× total'],['Red / Black','2× total'],['Odd / Even','2× total'],['1–18 / 19–36','2× total']];
 return <View style={styles.page}>
  <Pressable accessibilityRole="button" onPress={onBack} style={styles.back}>
   <Text style={styles.whiteBold}>‹ Puppy Casino</Text>
  </Pressable>
  <Text style={styles.title}>Puppy Roulette</Text>
  <Text style={styles.muted}>European single-zero roulette. Your bet is saved before the wheel resolves.</Text>
  <View style={styles.wallet}>
   <Text style={{fontSize:30}}>🐾</Text>
   <View style={{flex:1}}>
    <Text style={styles.whiteBold}>Casino Chip Wallet</Text>
    <Text style={styles.walletAmount}>{game.casinoChips.toLocaleString('en-US')} Casino Chips</Text>
   </View>
   <Text style={styles.rtp}>{ROULETTE_V6.rtp} RTP</Text>
  </View>
  <View style={styles.scene}>
   <RouletteWheel display={lastResult} pending={pending} animationsEnabled={motion}/>
   <RouletteBoard type={selectedType} number={selectedNumber} onChoose={choose} onPicker={()=>setPicker(true)}/>
  </View>
  {!pending&&display?<View accessibilityLiveRegion="polite" style={[styles.result,{backgroundColor:hex[display.color]}]}>
   <Text style={styles.resultNumber}>{display.winningNumber}</Text>
   <Text style={styles.whiteBold}>{display.color}</Text>
   <Text style={styles.whiteBold}>{display.won?
    'WIN · '+display.multiplier+'× · '+display.payout+' Chips returned':'No win · 0 Chips returned'}</Text>
  </View>:null}
  <Text style={styles.muted}>Bet board: red, black, green zero, or choose an exact landing number.</Text>
  <Text style={styles.section}>Wager</Text>
  <Text style={styles.muted}>Payout multipliers are total Casino Chips returned, including the wager.</Text>
  <View style={styles.presets}>
   {ROULETTE_V6.presets.map(v=><Pressable key={v} accessibilityRole="button" accessibilityState={{selected:wager===v}}
    onPress={()=>setWager(v)} style={[styles.preset,wager===v&&styles.selectedPreset]}>
    <Text style={styles.whiteBold}>{v.toLocaleString('en-US')}</Text>
   </Pressable>)}
  </View>
  <Pressable accessibilityRole="button" accessibilityState={{disabled:!canSpin}} disabled={!canSpin}
   onPress={pull} style={[styles.spinButton,!canSpin&&styles.disabled]}>
   <Text style={styles.whiteBold}>{pending?'Round In Progress':game.casinoChips<wager?'Not Enough Chips':
    'Spin '+rouletteBetLabel(bet)+' · '+wager+' Chips'}</Text>
  </Pressable>
  {message?<Text style={styles.error} accessibilityLiveRegion="polite">{message}</Text>:null}
  <Pressable accessibilityRole="button" onPress={()=>setHelp(!help)} style={styles.outlineButton}>
   <Text style={styles.whiteBold}>{help?'Hide Roulette Rules':'ⓘ Roulette Rules'}</Text>
  </Pressable>
  {help?<View style={styles.rules}>
   <Text style={styles.section}>Published Roulette rules</Text>
   {rules.map(([label,value])=><View key={label} style={styles.ruleRow}>
    <Text style={styles.muted}>{label}</Text><Text style={styles.whiteBold}>{value}</Text>
   </View>)}
   <Text style={styles.muted}>Zero is green. It wins only a straight bet on 0 and loses all even-money bets.</Text>
   <Text style={styles.muted}>The selected bet is stored with the accepted wager before the winning number is generated.</Text>
   <Text style={styles.muted}>European single-zero theoretical RTP: {ROULETTE_V6.rtp}.</Text>
  </View>:null}
  <Text style={styles.muted}>Guest preview only; no real money, cash prizes or authoritative cloud betting.</Text>
  <Modal visible={picker} transparent animationType={game.reducedMotion||!game.animatedUi?'none':'fade'} onRequestClose={()=>setPicker(false)}>
   <PuppyPointer enabled={game.puppyPointerEnabled} style={styles.modalBackdrop}>
    <View style={styles.modalBody}>
     <Text style={styles.section}>Choose landing number</Text>
     <Text style={styles.muted}>Pick 0–36. Exact-number bets return 36× total.</Text>
     <ScrollView style={{maxHeight:360}}>
      <View style={styles.numberGrid}>
       {Array.from({length:37},(_,i)=><Pressable key={i} accessibilityRole="button"
        accessibilityState={{selected:selectedType==='STRAIGHT'&&selectedNumber===i}}
        accessibilityLabel={'Bet on '+i}
        onPress={()=>{choose('STRAIGHT',i);setPicker(false);}}
        style={[styles.numberOption,{backgroundColor:rouletteColorHex(i)},
         selectedType==='STRAIGHT'&&selectedNumber===i&&styles.selectedTarget]}>
        <Text style={styles.whiteBold}>{i}</Text>
       </Pressable>)}
      </View>
     </ScrollView>
     <Pressable accessibilityRole="button" onPress={()=>setPicker(false)} style={styles.outlineButton}>
      <Text style={styles.whiteBold}>Close</Text>
     </Pressable>
    </View>
   </PuppyPointer>
  </Modal>
 </View>;
}
const styles=themedStyles(()=>({
 page:{gap:12,width:'100%',backgroundColor:'#0C0F12'},
 back:{minHeight:44,alignSelf:'flex-start',justifyContent:'center'},
 whiteBold:{color:'#FFFFFF',fontWeight:'900',textAlign:'center'},
 white:{color:'#FFFFFF'},muted:{color:'#AFBBC2',fontSize:12},
 title:{fontSize:27,fontWeight:'900',color:'#FFFFFF'},
 section:{fontSize:18,fontWeight:'900',color:'#FFFFFF'},
 wallet:{backgroundColor:'#192A2C',borderWidth:1,borderColor:'#55635C',borderRadius:18,padding:14,flexDirection:'row',gap:10,alignItems:'center'},
 walletAmount:{fontSize:20,fontWeight:'900',color:'#FFFFFF'},rtp:{color:'#76DAC5',fontSize:12,fontWeight:'900'},
 scene:{padding:10,backgroundColor:'#0D2118',borderRadius:22,borderColor:'#FFFFFF25',borderWidth:1,overflow:'hidden'},
 wheelScene:{height:ROULETTE_V6.wheelHeight,justifyContent:'center',alignItems:'center'},
 wheelBowl:{position:'absolute',width:ROULETTE_V6.wheelBase,height:ROULETTE_V6.wheelBase},
 wheelRotor:{position:'absolute',width:ROULETTE_V6.rotor,height:ROULETTE_V6.rotor},
 wheelOrbit:{position:'absolute',width:ROULETTE_V6.wheelBase,height:ROULETTE_V6.wheelBase},
 ball:{position:'absolute',width:ROULETTE_V6.ball,height:ROULETTE_V6.ball,
  left:(ROULETTE_V6.wheelBase-ROULETTE_V6.ball)/2,top:(ROULETTE_V6.wheelBase-ROULETTE_V6.ball)/2-ROULETTE_V6.ballOrbit*0.93},
 wheelLabel:{paddingHorizontal:9,paddingVertical:4,borderRadius:12,backgroundColor:'#182C2FE8',borderWidth:1,borderColor:'#76DAC5'},
 boardTitle:{fontSize:11,fontWeight:'900',color:'#FFFFFFBF',textAlign:'center',marginBottom:5},
 boardMeasured:{width:'100%',alignItems:'center'},
 target:{position:'absolute',borderRadius:4},
 selectedTarget:{borderWidth:2,borderColor:'#70EEE5'},
 outlineButton:{borderWidth:1,borderColor:'#FFFFFF70',borderRadius:12,padding:12,alignItems:'center',marginTop:7},
 result:{borderRadius:20,padding:14,alignItems:'center',gap:3},
 resultNumber:{color:'#FFFFFF',fontSize:44,fontWeight:'900'},
 presets:{flexDirection:'row',gap:6},preset:{flex:1,alignItems:'center',paddingVertical:10,backgroundColor:'#293A3B',borderRadius:15},
 selectedPreset:{borderWidth:1,borderColor:'#70EEE5',backgroundColor:'#285650'},
 spinButton:{minHeight:48,borderRadius:13,backgroundColor:'#287B72',alignItems:'center',justifyContent:'center',padding:9},
 disabled:{opacity:0.4},error:{color:'#FF8787',padding:8},
 rules:{backgroundColor:'#192A2C',borderRadius:18,padding:14,gap:8},
 ruleRow:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:8},
 modalBackdrop:{flex:1,justifyContent:'center',padding:20,backgroundColor:'#000000B8'},
 modalBody:{padding:18,borderRadius:20,gap:10,backgroundColor:'#192A2C'},
 numberGrid:{flexDirection:'row',flexWrap:'wrap',gap:6,justifyContent:'space-between'},
 numberOption:{width:'30%',minHeight:48,alignItems:'center',justifyContent:'center',borderRadius:10}
}));
