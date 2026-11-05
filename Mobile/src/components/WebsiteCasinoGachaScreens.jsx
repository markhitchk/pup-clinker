// GENERATED from Puppy-Clicker/src/native/App.jsx and src/native/web/WebsiteDesign.jsx.
// Gacha/Casino UI and disclosures belong to the website; signed game actions remain native.
// Android-only security adapter: legacy Casino recovery is disabled without a verified native capability.
// Do not hand-edit: run scripts/sync-website-native-ui.mjs.
import React,{useEffect,useRef,useState} from 'react';
import {Animated,Easing,Image,Modal,Pressable,View,useWindowDimensions} from 'react-native';

export function createWebsiteCasinoGachaScreens({Txt,Button,styles,C,ROSTER,GACHA_ART,GACHA_MACHINE,gachaMachineFrame,gachaPool,GACHA_TREAT_COST,v6Format,Portrait,PuppyPointer,CASINO_PAGES,format,SlotsV6,RouletteV6,PlinkoV6,ScratchersV6,LuckyWheelV6,BlackjackV6Panel}){
function CasinoIntroDialog({onBack,onContinue}){
 return <Modal visible transparent animationType="fade" onRequestClose={onBack}>
  <View style={styles.casinoIntroOverlay}>
   <View accessibilityRole="summary" style={styles.casinoIntroPanel}>
    <Txt style={styles.casinoIntroIcon}>🎰</Txt>
    <Txt style={styles.casinoIntroEyebrow}>PUPPY CLICKER</Txt>
    <Txt accessibilityRole="header" style={styles.casinoIntroTitle}>Puppy Casino</Txt>
    <Txt style={styles.casinoIntroNotice}>Simulated Gambling</Txt>
    <Txt style={styles.casinoIntroText}>Slots, roulette, blackjack, Plinko, scratchers and the Lucky Pup Wheel use game-only chips. No real money is used and no prizes have real-world cash value.</Txt>
    <View style={styles.casinoIntroActions}>
     <Button secondary onPress={onBack}>Go Back</Button>
     <Button onPress={onContinue}>Continue →</Button>
    </View>
   </View>
  </View>
 </Modal>;
}

function GachaScreen({g,act,state,registry,onBack}){
 const [stage,setStage]=useState(0);
 const [showInfo,setShowInfo]=useState(false);
 const [message,setMessage]=useState('');
 const turn=useRef(new Animated.Value(0)).current;
 const drop=useRef(new Animated.Value(GACHA_MACHINE.dropOffset)).current;
 const opening=useRef(new Animated.Value(0)).current;
 const revealTimer=useRef(null);
 const result=state.gachaResult;
 const {width}=useWindowDimensions();
 const frame=gachaMachineFrame(Math.max(0,Math.min(width-44,560)));
 const selected=registry.items.find(p=>p.id===result?.puppyId)
  ||ROSTER.find(p=>p.id===result?.puppyId);
 const pool=gachaPool(ROSTER,g.unlockedPuppies);
 const missing=pool.filter(p=>!g.unlockedPuppies.includes(p.id));
 const purchasesAllowed=!g.gameplayBusy&&stage===0&&!result?.success&&pool.length>0;
 useEffect(()=>{
  if(!result?.success){
   setStage(0);return;
  }
  setMessage('');
  if(g.animationsEnabled===false){turn.setValue(1);drop.setValue(0);opening.setValue(0);setStage(2);return;}
  setStage(1);
  turn.setValue(0);drop.setValue(GACHA_MACHINE.dropOffset);opening.setValue(0);
  const motion=Animated.sequence([
   Animated.timing(turn,{toValue:1,duration:GACHA_MACHINE.knobTurnMs,useNativeDriver:true}),
   Animated.spring(drop,{toValue:0,friction:5,tension:90,useNativeDriver:true})
  ]);
  motion.start(({finished})=>{if(finished)setStage(2);});
  return ()=>{motion.stop();if(revealTimer.current)clearTimeout(revealTimer.current);};
 },[result?.id,turn,drop,opening,g.animationsEnabled]);
 const openCapsule=()=>{
  if(stage!==2||!result?.success)return;
  if(g.animationsEnabled===false){opening.setValue(1);setStage(4);return;}
  setStage(3);
  opening.setValue(0);
  Animated.timing(opening,{toValue:1,duration:GACHA_MACHINE.capsuleOpenMs,
   easing:Easing.inOut(Easing.ease),useNativeDriver:true}).start(({finished})=>{
   if(finished)revealTimer.current=setTimeout(()=>setStage(4),GACHA_MACHINE.revealDelayMs);
  });
 };
 const reset=()=>{setStage(0);setMessage('');act({type:'dismissGacha'});};
 const pull=payment=>{
  if(!purchasesAllowed)return;
  const affordable=payment==='TREATS'?g.treats>=GACHA_TREAT_COST:(g.ticketInventory.COMMON||0)>=1;
  if(!affordable){setMessage(payment==='TREATS'?'You need 2500 Treats.':'You need 1 Common Ticket.');return;}
  setMessage('');
  act({type:'gacha',payment});
 };
 const turnDegrees=turn.interpolate({inputRange:[0,1],outputRange:['0deg','360deg']});
 const tiltOpacity=opening.interpolate({inputRange:[0,0.12,0.62,1],outputRange:[0,1,1,0]});
 const openOpacity=opening.interpolate({inputRange:[0,0.42,1],outputRange:[0,0,1]});
 return <View style={styles.gamePage}>
  <View style={styles.gachaHeader}>
   <Button compact secondary onPress={onBack}>← Back</Button>
   <View style={{flex:1}}>
    <Txt style={styles.heading}>Puppy Gacha</Txt>
    <Txt muted style={styles.small}>Turn · Drop · Open · Reveal!</Txt>
   </View>
   <Button compact secondary onPress={()=>setShowInfo(true)}>ⓘ Info</Button>
  </View>
  <View style={styles.gachaWallet}>
   <View style={styles.gachaWalletCell}>
    <Txt style={styles.gachaWalletLabel}>🍪 Treats</Txt>
    <Txt style={styles.gachaWalletValue}>{v6Format(g.treats,g.compactNumbers!==false)}</Txt>
   </View>
   <View style={styles.gachaWalletDivider}/>
   <View style={styles.gachaWalletCell}>
    <Txt style={styles.gachaWalletLabel}>🎟️ Common Tickets</Txt>
    <Txt style={styles.gachaWalletValue}>{g.ticketInventory.COMMON||0}</Txt>
   </View>
  </View>
  <View style={[styles.gachaMachine,{width:frame.width,height:frame.height}]}>
   <Image resizeMode="contain" accessibilityLabel="Original Puppy Gacha machine"
    source={GACHA_ART.machine} style={{position:'absolute',width:frame.width,height:frame.height}}/>
   <Animated.Image source={GACHA_ART.knob} resizeMode="contain"
    accessibilityLabel="Gacha turning knob"
    style={{position:'absolute',...frame.knob,transform:[{rotate:turnDegrees}]}}/>
   {(stage===1||stage===2)?<Animated.Image source={GACHA_ART.capsuleClosed}
    resizeMode="contain" accessibilityLabel="Closed puppy Gacha capsule"
    style={{position:'absolute',...frame.capsule,
     transform:[{translateY:drop}]}}/>:null}
   {stage===3||stage===4?<View style={{position:'absolute',...frame.open}}>
    <Animated.Image source={GACHA_ART.capsuleOpenTilt} resizeMode="contain"
     style={{width:'100%',height:'100%',opacity:tiltOpacity}}/>
    <Animated.Image source={GACHA_ART.capsuleOpen} resizeMode="contain"
     style={{position:'absolute',width:'100%',height:'100%',opacity:openOpacity}}/>
   </View>:null}
   {stage===2?<View style={styles.gachaOpenButton}>
    <Button onPress={openCapsule}>Open Capsule</Button>
   </View>:null}
  </View>
  <View style={styles.gachaPurchaseRow}>
   <Pressable accessibilityRole="button"
    disabled={!purchasesAllowed||g.treats<GACHA_TREAT_COST}
    onPress={()=>pull('TREATS')}
    style={[styles.gachaPurchaseButton,{backgroundColor:'#FFB51B',borderColor:'#D89500'},
     (!purchasesAllowed||g.treats<GACHA_TREAT_COST)&&styles.buttonDisabled]}>
    <Txt style={styles.gachaPurchaseTitle}>🍪 Pull with Treats</Txt>
    <Txt style={styles.gachaPurchaseSubtitle}>2,500 Treats</Txt>
   </Pressable>
   <Pressable accessibilityRole="button"
    disabled={!purchasesAllowed||(g.ticketInventory.COMMON||0)<1}
    onPress={()=>pull('COMMON_TICKET')}
    style={[styles.gachaPurchaseButton,{backgroundColor:'#7C3AED',borderColor:'#5B21B6'},
     (!purchasesAllowed||(g.ticketInventory.COMMON||0)<1)&&styles.buttonDisabled]}>
    <Txt style={styles.gachaPurchaseTitle}>🎟️ Use Common Ticket</Txt>
    <Txt style={styles.gachaPurchaseSubtitle}>1 Ticket</Txt>
   </Pressable>
  </View>
  {message?<Txt style={styles.gachaFailure}>{message}</Txt>:null}
  <Txt muted style={[styles.small,{textAlign:'center'}]}>
   {pool.length===0?'No puppies are currently eligible for Gacha.':
    missing.length===0?'Collection complete · no unowned Gacha puppies remain.':
    missing.length===1?'1 new puppy remains · new puppies are guaranteed first.':
    missing.length+' new puppies remain · new puppies are guaranteed first.'}
  </Txt>
  <Txt muted style={[styles.tiny,{textAlign:'center'}]}>
   Redeem-only and special locked puppies are excluded. Uses earned Treats or Common Upgrade Tickets only; no real money or cash-out.
  </Txt>
  <Txt muted style={[styles.tiny,{textAlign:'center'}]}>
   Rewards require authoritative PupEye verification before they can affect progression.
  </Txt>
  <Modal visible={stage===4&&Boolean(result?.success)} transparent
   animationType={g.reducedMotion||!g.animatedUi?'none':'fade'} onRequestClose={reset}>
   <PuppyPointer enabled={g.puppyPointerEnabled} style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.gachaRevealHeading}>✨ CAPSULE OPENED! ✨</Txt>
     <Txt style={[styles.itemName,{color:C.accentInk}]}>{result?.isNewUnlock?'NEW PUPPY!':'PUPPY REVEALED!'}</Txt>
     <View style={styles.gachaRevealPortrait}>
      {selected?<Portrait puppy={selected} size={188} unlocked/>:null}
     </View>
     <Txt style={styles.heading}>{selected?.emoji||'🐶'} {result?.puppyName||'Puppy'}</Txt>
     <Txt muted>{result?.isNewUnlock?'Added to your Puppy Roster.':'Already in your Puppy Roster.'}</Txt>
     <Button onPress={()=>{act({type:'switch',id:result.puppyId,now:Date.now()});reset();}}>Use Puppy</Button>
     <Button secondary onPress={reset}>Another Capsule</Button>
     <Button secondary compact onPress={reset}>Close</Button>
    </View>
   </PuppyPointer>
  </Modal>
  <Modal visible={showInfo} transparent animationType={g.reducedMotion||!g.animatedUi?'none':'fade'}
   onRequestClose={()=>setShowInfo(false)}>
   <PuppyPointer enabled={g.puppyPointerEnabled} style={styles.gachaModalScreen}>
    <View style={styles.gachaRevealCard}>
     <Txt style={styles.heading}>ⓘ Gacha Info</Txt>
     <Txt muted>Turn the machine with Treats or a Common Upgrade Ticket, then open your capsule. New unrestricted puppies are awarded before duplicates.</Txt>
     <Txt muted>Special event, developer, Discord-role and Puppy Code puppies are excluded. Unlocks must be verified by the authoritative Pup Account service.</Txt>
     <Button onPress={()=>setShowInfo(false)}>Got it</Button>
    </View>
   </PuppyPointer>
  </Modal>
 </View>;
}

function CasinoScreen({g,act,state,bet,setBet,onBack}){
 const [page,setPage]=useState(g.pendingScratch?'scratchers':state.blackjack?'blackjack':g.pendingSlots?'slots':g.pendingRoulette?'roulette':g.pendingPlinko?'plinko':g.pendingWheel?'wheel':'hub');
 const [accepted,setAccepted]=useState(false);
 const {width}=useWindowDimensions();
 const casino=(gameType,extra={})=>act({type:'casino',gameType,wager:bet,...extra});
 const choices=[20,50,100,500,1000];
 const inBlackjack=Boolean(g.nativeCasinoPending||state.blackjack||g.pendingSlots||g.pendingRoulette||g.pendingPlinko||g.pendingScratch||g.pendingWheel);
 const visiblePage=CASINO_PAGES.find(p=>p[0]===page);
 if(!accepted)return <CasinoIntroDialog onBack={onBack} onContinue={()=>setAccepted(true)}/>;
 return <View style={styles.gamePage}>
  {g.nativeCasinoPending?<View accessibilityRole="alert" style={styles.notice}>
   <Txt>{g.nativeCasinoRefund?'An unfinished round from the old app is blocking the Casino. Recover it to return the reserved Chips and start playing.':'Finish your saved Casino round in the registered Android app, let it sync, then refresh here. Your wager is preserved.'}</Txt>
   {g.nativeCasinoRefund&&g.canRecoverLegacyCasino===true?<Button disabled={g.gameplayBusy} onPress={()=>act({type:'casinoRecoverLegacy'})}>{g.gameplayBusy?'Recovering old round…':'Recover old round · Return '+g.nativeCasinoRefund+' Chips'}</Button>:null}
  </View>:null}
  {page!=='blackjack'&&page!=='slots'&&page!=='roulette'&&page!=='plinko'&&page!=='scratchers'&&page!=='wheel'?<><View style={styles.pair}>
   <Button compact secondary onPress={()=>page==='hub'?onBack():setPage('hub')}>{page==='hub'?'‹ Rewards':'‹ Casino'}</Button>
   <Txt style={styles.pageTitle}>{visiblePage?.[2]||'Puppy Casino'}</Txt>
  </View>
  <Txt muted style={styles.small}>🐾 Casino Chip Wallet · {format(g.casinoChips)} Chips · Simulated, no real-money prizes</Txt></>:null}
  {page==='hub'?<>
   {g.pendingWheel?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎡</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Lucky Pup Wheel Spin</Txt>
     <Txt muted style={styles.small}>Your result is committed. Resume the wheel to reveal and settle once.</Txt></View>
    <Button compact onPress={()=>setPage('wheel')}>Resume</Button>
   </View>:null}
   {g.pendingPlinko?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🔵</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Plinko Drop</Txt>
     <Txt muted style={styles.small}>Your path, nine-bin landing and payout are saved. Resume the ball drop to settle once.</Txt></View>
    <Button compact onPress={()=>setPage('plinko')}>Resume</Button>
   </View>:null}
   {g.pendingRoulette?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎡</Txt><View style={{flex:1}}>
     <Txt style={styles.itemName}>Unfinished Roulette Spin</Txt>
     <Txt muted style={styles.small}>Your accepted bet and winning pocket are saved. Resume the wheel to reveal and settle.</Txt>
    </View><Button compact onPress={()=>setPage('roulette')}>Resume</Button>
   </View>:null}
   {g.pendingSlots?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎰</Txt>
    <View style={{flex:1}}>
     <Txt style={styles.itemName}>Unfinished Slots Spin</Txt>
     <Txt muted style={styles.small}>Your original wager and outcome are saved. Resume to reveal and settle once.</Txt>
    </View>
    <Button compact onPress={()=>setPage('slots')}>Resume</Button>
   </View>:null}
   {state.blackjack?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🃏</Txt>
    <View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Blackjack Hand</Txt>
     <Txt muted style={styles.small}>Your original cards and wager are saved. Resume before opening another casino game.</Txt></View>
    <Button compact onPress={()=>setPage('blackjack')}>Resume</Button>
   </View>:null}
   {g.pendingScratch?<View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:26}}>🎟️</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Unfinished Scratchers Card</Txt><Txt muted style={styles.small}>Your saved result is ready. Scratch 68% to claim it; no other wagers can start.</Txt></View>
    <Button compact onPress={()=>setPage('scratchers')}>Resume</Button>
   </View>:null}
   <View style={styles.dailyGiftCard}>
    <Txt style={{fontSize:28}}>🐾</Txt><View style={{flex:1}}><Txt style={styles.itemName}>Treats → Casino Chips</Txt><Txt muted style={styles.small}>10 🍪 = 1 🐾 · One-way conversion</Txt></View>
   </View>
   <View style={styles.actions}><Button compact secondary disabled={g.treats<100} onPress={()=>act({type:'exchange',amount:100})}>100 🍪 → 10 🐾</Button><Button compact secondary disabled={g.treats<1000} onPress={()=>act({type:'exchange',amount:1000})}>1,000 🍪 → 100 🐾</Button></View>
   {CASINO_PAGES.map(([id,emoji,title,description,art])=><Pressable accessibilityRole="button" key={id} onPress={()=>setPage(id)} style={styles.casinoGameTile}>
    <Image source={art} resizeMode="contain" style={styles.casinoTileArt}/><View style={{flex:1,gap:5}}><Txt style={styles.itemName}>{emoji} {title}</Txt><Txt muted style={styles.small}>{description}</Txt></View><Txt style={styles.casinoTileArrow}>›</Txt>
   </Pressable>)}
  </>:<>
   {page!=='blackjack'&&page!=='slots'&&page!=='roulette'&&page!=='plinko'&&page!=='scratchers'&&page!=='wheel'?<View style={styles.filterRow}>{choices.map(w=><Pressable key={w} accessibilityRole="button" accessibilityState={{selected:bet===w}} style={[styles.filterChip,bet===w&&styles.filterChipSelected]} onPress={()=>setBet(w)}><Txt numberOfLines={1} style={[styles.filterText,bet===w&&styles.filterTextSelected]}>{w}</Txt></Pressable>)}</View>:null}
   {page==='slots'?<SlotsV6 game={g} pending={g.pendingSlots}
    lastResult={state.slotsDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='roulette'?<RouletteV6 game={g} pending={g.pendingRoulette}
    lastResult={state.rouletteDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='plinko'?<PlinkoV6 game={g} pending={g.pendingPlinko}
    lastResult={state.plinkoDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='scratchers'?<ScratchersV6 game={g} pending={g.pendingScratch}
    lastResult={state.scratchDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='wheel'?<LuckyWheelV6 game={g} pending={g.pendingWheel}
    lastResult={state.wheelDisplay} onAction={act} onBack={()=>setPage('hub')}/>:null}
   {page==='blackjack'?<BlackjackV6Panel g={g} act={act} state={state} onBack={()=>setPage('hub')}/>:null}
  </>}
 </View>;
}

 return {GachaScreen,CasinoScreen};
}
