// Single-source React Native UI components for Puppy Clicker website + Android.
// Canonical source: markhitchk/Puppy-Clicker/src/shared-ui/PupGameChrome.jsx
// Android receives this exact source through sync-website-ui.mjs. No browser APIs.
import React from 'react';
import {Alert,Pressable,Text,View} from 'react-native';

export const PUP_GLASS_TOKENS=Object.freeze({
 background:'#0A0C12',
 panel:'#12151F',
 raised:'#1A1E2B',
 border:'#00C4E0',
 accent:'#00E5FF',
 purple:'#7B5CFF',
 pink:'#FF5C9D',
 gold:'#FFD166',
 cardRadius:22,
 buttonRadius:999
});

export const PUP_WALLET_ITEMS=Object.freeze([
 ['🍪','Treats','treats'],
 ['🦴','Bones','bones'],
 ['🪙','Pup Coins','pupCoins'],
 ['🐾','Casino Chips','casinoChips']
]);

// Styling can be supplied by platform. The component hierarchy, accessibility,
// exact balance inspection, formatting and compact behavior are shared.
export function PupResourceStrip({
 game,styles={},colors={},compact=false,v2=false,formatValue,onInspect
}){
 const format=typeof formatValue==='function'?formatValue:(v=>Number(v||0).toLocaleString('en-US'));
 return <View testID="resource-strip" style={[
  styles.wallet,
  compact&&{paddingVertical:6,paddingHorizontal:5},
  v2&&{borderColor:colors.accent||PUP_GLASS_TOKENS.accent,borderWidth:2,borderRadius:PUP_GLASS_TOKENS.cardRadius}
 ]}>
  {PUP_WALLET_ITEMS.map(([emoji,label,key])=>{
   const raw=Number(game?.[key]??0);
   const value=Number.isFinite(raw)?raw:0;
   const exact=value.toLocaleString('en-US');
   const press=()=>typeof onInspect==='function'?
    onInspect({key,label,value,formatted:exact}):
    Alert.alert(label+' · Exact balance',exact+' '+label);
   return <Pressable key={key} accessibilityRole="button"
    accessibilityLabel={label+' exact balance: '+exact} onPress={press}
    style={styles.walletCell}>
    <Text style={[styles.walletEmoji,compact&&{fontSize:16}]}>{emoji}</Text>
    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.52}
     style={[styles.walletNumber,compact&&{fontSize:14}]}>
     {format(value,game?.compactNumbers!==false)}
    </Text>
    <Text numberOfLines={1} style={[styles.walletLabel,compact&&{fontSize:10}]}>
     {label}
    </Text>
   </Pressable>;
  })}
 </View>;
}

export function PupNeedPill({icon,value,styles={}}){
 const safe=Math.min(100,Math.max(0,Number(value)||0));
 return <View style={[styles.needPill,safe<=10&&styles.needPillLow]}>
  <Text style={styles.needPillText}>{icon} {safe}%</Text>
  <View accessibilityRole="progressbar" accessibilityLabel={icon+' puppy care'}
   accessibilityValue={{min:0,max:100,now:safe}} style={styles.needMeterTrack}>
   <View style={[styles.needMeterFill,{width:safe+'%'}]}/>
  </View>
 </View>;
}

export function PupQuickAction({emoji,title,subtitle,onPress,disabled=false,styles={}}){
 return <Pressable accessibilityRole="button" accessibilityLabel={title}
  accessibilityState={{disabled}} disabled={disabled} onPress={onPress}
  style={({pressed})=>[styles.quickAction,disabled&&styles.buttonDisabled,pressed&&{opacity:0.75}]}>
  <Text style={styles.quickActionIcon}>{emoji}</Text>
  <Text numberOfLines={1} style={styles.quickActionTitle}>{title}</Text>
  <Text numberOfLines={1} style={styles.quickActionDescription}>{subtitle}</Text>
 </Pressable>;
}
