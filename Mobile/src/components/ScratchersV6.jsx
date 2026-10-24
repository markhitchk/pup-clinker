import {themedStyles} from '../theme.js';
import React,{useState} from 'react';
import {Image,Pressable,Text,View} from 'react-native';
import ScratchStage from './ScratchStage.jsx';
import {SCRATCHERS} from '../engine/casinoV7.js';
import {SCRATCH_PUBLISHED_RTP,SCRATCH_CARD_META,SCRATCH_PRIZES} from '../engine/scratch.js';

const BORDER=require('../../assets/casino/puppy_scratch_play_border.png');
const TOKEN=require('../../assets/casino/pup_coin.png');
const cards=SCRATCHERS.map(x=>({...x,...SCRATCH_CARD_META[x.id]}));
export default function ScratchersV6({game,pending,lastResult,onAction,onBack}){
 const [wager,setWager]=useState(20);
 const [help,setHelp]=useState(false);
 const [message,setMessage]=useState('');
 const selected=cards.find(x=>x.wager===wager)||cards[0];
 const active=cards.find(x=>x.wager===pending?.wager)||selected;
 const display=lastResult||pending;
 const blocking=!!(pending||game.pendingSlots||game.pendingRoulette||game.pendingPlinko||game.pendingWheel||game.casinoRound);
 const canBuy=!blocking&&game.casinoChips>=wager;
 const start=()=>{
  if(!canBuy){setMessage('Finish the current Casino round or choose a card you can afford.');return;}
  setMessage('');
  onAction({type:'scratchStart',wager,roll:Math.floor(Math.random()*100),now:Date.now()});
 };
 return <View style={s.page}>
  <Pressable accessibilityRole="button" onPress={onBack} style={s.back}>
   <Text style={s.whiteBold}>‹ Puppy Casino</Text>
  </Pressable>
  <Text style={s.title}>Pup Scratchers</Text>
  <Text style={s.muted}>Pick a scratch card, then drag the Scratch Token across the coating. The coin has weighted movement and scratches a wider trail when you move it faster.</Text>
  <View style={s.wallet}>
   <Text style={s.walletIcon}>🐾</Text>
   <View style={{flex:1}}>
    <Text style={s.whiteBold}>Casino Chip Wallet</Text>
    <Text style={s.walletAmount}>{game.casinoChips.toLocaleString('en-US')} Casino Chips</Text>
   </View>
   <Text style={s.rtp}>{SCRATCH_PUBLISHED_RTP} RTP</Text>
  </View>
  <View style={s.tokenInfo}>
   <Image source={TOKEN} resizeMode="contain" accessibilityLabel="Scratch Token"
    style={{width:54*960/973,height:54}}/>
   <View style={{flex:1,gap:3}}>
    <Text style={s.whiteBold}>Scratch Token</Text>
    <Text style={s.muted}>Weighted drag • speed-sensitive scratch radius • physical coin rotation</Text>
   </View>
  </View>
  <ScratchStage key={pending?.createdAt||'idle'} round={pending} lastResult={display}
   cardId={active.id} borderImage={BORDER}
   onReveal={()=>onAction({type:'scratchReveal',now:Date.now()})}/>
  {lastResult&&!pending?<View style={s.revealed}>
   <Text style={s.whiteBold}>{SCRATCH_PRIZES.find(p=>p.id===lastResult.prize)?.label||'Prize revealed'}</Text>
   <Text style={s.whiteBold}>{lastResult.payout} Chips returned</Text>
  </View>:null}
  {pending?<Text style={s.muted}>Your {active.name} result is already saved. Clear 68% of the coating to reveal it and settle exactly once.</Text>:null}
  <Text style={s.section}>Scratch cards</Text>
  <Text style={s.muted}>Every card keeps the published 91% Casino Chip RTP, but each has a different prize distribution.</Text>
  <View style={s.cardGrid}>
   {cards.map(card=><Pressable key={card.id} accessibilityRole="button"
    accessibilityLabel={card.name+' '+card.wager+' Casino Chips'} accessibilityState={{selected:wager===card.wager,disabled:blocking}}
    disabled={blocking} onPress={()=>setWager(card.wager)}
    style={[s.cardOption,wager===card.wager&&s.cardSelected]}>
    <Text style={s.whiteBold}>{card.emoji} {card.shortName}</Text>
    <Text style={s.muted}>{card.wager.toLocaleString('en-US')} Chips</Text>
   </Pressable>)}
  </View>
  <Pressable accessibilityRole="button" accessibilityState={{disabled:!canBuy}}
   disabled={!canBuy} onPress={start} style={[s.buyButton,!canBuy&&s.disabled]}>
   <Text style={s.whiteBold}>{blocking?'Round In Progress':game.casinoChips<wager?'Not Enough Chips':
    'Buy '+selected.name+' · '+wager+' Chips'}</Text>
  </Pressable>
  {message?<Text accessibilityLiveRegion="polite" style={s.error}>{message}</Text>:null}
  <Pressable accessibilityRole="button" onPress={()=>setHelp(!help)} style={s.helpButton}>
   <Text style={s.whiteBold}>{help?'Hide Card Odds':'ⓘ Card Odds & Rules'}</Text>
  </Pressable>
  {help?<View style={s.odds}>
   <Text style={s.section}>{active.emoji} {active.name} odds</Text>
   <Text style={s.muted}>Expected Casino Chip return: {SCRATCH_PUBLISHED_RTP}</Text>
   {SCRATCH_PRIZES.map((p,i)=><View style={s.oddsRow} key={p.id}>
    <Text style={s.muted}>{p.emoji} {p.label}</Text>
    <Text style={s.whiteBold}>{active.odds[i]}%</Text>
   </View>)}
   <Text style={s.muted}>• The outcome is committed before scratching.</Text>
   <Text style={s.muted}>• 68% of the coating must be removed before automatic reveal.</Text>
   <Text style={s.muted}>• Faster scratch-token movement creates a slightly wider scratch trail.</Text>
   <Text style={s.muted}>• All costs and payouts use Casino Chips only.</Text>
  </View>:null}
  <Text style={s.muted}>Local guest preview only — no production PupEye, Supabase or real-money Casino settlement.</Text>
 </View>;
}
const s=themedStyles(()=>({
 page:{gap:12,width:'100%',backgroundColor:'#0C0F12'},back:{minHeight:44,alignSelf:'flex-start',justifyContent:'center'},
 whiteBold:{color:'#FFFFFF',fontWeight:'900'},title:{fontSize:27,color:'#FFFFFF',fontWeight:'900'},
 muted:{fontSize:12,color:'#BBC9D0'},section:{fontSize:18,fontWeight:'900',color:'#FFFFFF'},
 wallet:{flexDirection:'row',alignItems:'center',gap:10,borderRadius:18,padding:14,backgroundColor:'#17292C',
  borderWidth:1,borderColor:'#566970'},
 walletIcon:{fontSize:30},walletAmount:{color:'#FFFFFF',fontWeight:'900',fontSize:21},
 rtp:{color:'#74DCCB',fontSize:12,fontWeight:'900'},
 tokenInfo:{paddingHorizontal:12,paddingVertical:10,backgroundColor:'#3753476E',borderRadius:18,
  flexDirection:'row',alignItems:'center',gap:10,borderWidth:1,borderColor:'#4E6669'},
 revealed:{padding:11,borderRadius:12,backgroundColor:'#075843',alignItems:'center',gap:3},
 cardGrid:{flexDirection:'row',flexWrap:'wrap',gap:8},
 cardOption:{width:'48%',minHeight:65,alignItems:'center',justifyContent:'center',gap:3,
  backgroundColor:'#283B3F',borderRadius:16,borderWidth:1,borderColor:'#506065'},
 cardSelected:{backgroundColor:'#205D51',borderColor:'#74DCCB',borderWidth:2},
 buyButton:{minHeight:48,alignItems:'center',justifyContent:'center',padding:10,
  backgroundColor:'#26796F',borderRadius:14},disabled:{opacity:.4},
 helpButton:{alignItems:'center',borderColor:'#536D71',borderWidth:1,borderRadius:14,padding:12},
 odds:{padding:14,borderRadius:18,backgroundColor:'#192A2F',gap:7},
 oddsRow:{flexDirection:'row',alignItems:'center',justifyContent:'space-between'},
 error:{color:'#F28D8D',fontSize:12}
}));
