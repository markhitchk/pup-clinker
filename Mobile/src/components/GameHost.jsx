// Native controls and layout for production Play JSX. Game actions, animation
// timing, progress, wallet, care, prices and goals are owned by website source.
import React,{useState} from 'react';
import {Modal,Pressable,ScrollView,StyleSheet,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {colors as C} from '../theme.js';

const row={flexDirection:'row',alignItems:'center',gap:8};
const centered={alignItems:'center',justifyContent:'center'};
const card=()=>({borderWidth:1,borderColor:C.line,backgroundColor:C.surface,borderRadius:20,padding:14});
function boxStyle(cls,width){
 const rules={
  'cyber-play':{flex:1,gap:10,minWidth:0},'play-heading':{...row,justifyContent:'space-between',flexWrap:'wrap'},
  'cyber-play-stage':{...card(),gap:8,padding:8,borderRadius:24},
  'puppy-platform':{...centered,paddingVertical:8,gap:6,overflow:'hidden'},
  'platform-portrait':{zIndex:1},'platform-name':{...centered,zIndex:1},
  'platform-orbit':{position:'absolute',width:'76%',height:44,bottom:50,borderRadius:100,borderColor:C.accent,borderWidth:3,backgroundColor:'rgba(0,196,224,.12)'},
  'play-energy':{...row,...card(),padding:8,justifyContent:'space-evenly',flexWrap:'wrap'},
  'next-reward':{...row,...card()},'cyber-panel':card(),'reward-heading':{...row,justifyContent:'space-between'},
  'flex-1':{flex:1,minWidth:0},'desktop-dashboard':{flexDirection:'row',alignItems:'flex-start',gap:16},
  'dashboard-panels':{width:Math.min(330,width*.32),gap:12},'panel-heading':{...row,flexWrap:'wrap',justifyContent:'space-between'},
  'puppy-identity':{...card(),...row},'identity-portrait':centered,'care-summary-content':{gap:8},
  'care-meter-detail':{gap:5},'web-care-row':row,'care-bonus-box':{backgroundColor:C.accentSoft,borderRadius:10,padding:8},
  'compact-goal':{flexDirection:'row',gap:8,paddingVertical:8},'reference-goal-body':{flex:1,gap:4},
  'compact-goal-heading':{...row,justifyContent:'space-between'},'goal-footer':{gap:4}
 };
 return cls.split(/\s+/).map(k=>rules[k]).filter(Boolean);
}
function nativeControl(tag){
 function Host({children,className='',style,onClick,disabled,...props}){
  const {width}=useWindowDimensions();
  if(props.role==='progressbar'){
   const current=Number(props['aria-valuenow'])||0,max=Number(props['aria-valuemax'])||1;
   return <View accessibilityRole="progressbar" accessibilityLabel={props['aria-label']} accessibilityValue={{min:0,max,now:current}}
    style={{height:7,flexGrow:1,minWidth:40,backgroundColor:C.line,borderRadius:9,overflow:'hidden'}}><View style={{height:'100%',width:Math.min(100,Math.max(0,100*current/max))+'%',backgroundColor:C.accent}}/></View>;
  }
  if(className.includes('platform-paws')){
   const positions=[{left:'15%',top:'15%'},{right:'16%',top:'24%'},{left:'10%',top:'56%'},{right:'10%',top:'61%'}];
   return <View pointerEvents="none" importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>{React.Children.map(children,(child,i)=><View style={[{position:'absolute',opacity:.18},positions[i]]}>{child}</View>)}</View>;
  }
  if(tag==='button'){
   const tap=className.includes('cyber-tap');
   return <Pressable accessibilityRole="button" accessibilityLabel={props['aria-label']} disabled={disabled}
    accessibilityState={{disabled:Boolean(disabled)}} onPress={onClick} style={({pressed})=>[centered,{
     flexDirection:'row',flexWrap:'wrap',gap:6,minHeight:tap?52:36,borderRadius:tap?40:14,paddingHorizontal:12,paddingVertical:6,
     backgroundColor:tap||className.includes('primary')?C.accent:C.card,borderWidth:1,borderColor:C.line,
     opacity:disabled ? 0.5 : pressed ? 0.8 : 1
    }]}>{React.Children.map(children,child=>typeof child==='string'?<Text style={{color:C.accentInk,fontWeight:'700'}}>{child}</Text>:child)}</Pressable>;
  }
  if(['span','strong','p','small','h1','h2'].includes(tag)){
   if(className==='tap-tip'&&width<960)return null;
   const heading=tag==='h1'||tag==='h2',label=props['aria-label'];
   return <Text accessibilityRole={heading?'header':undefined} accessibilityLabel={label}
    style={[{color:tag==='p'||tag==='small'?C.muted:C.text,fontWeight:heading||tag==='strong'?'700':'500',
     fontSize:tag==='h1'?30:tag==='h2'?20:tag==='small'?12:14},className==='current-puppy-badge'&&{color:C.accentInk,backgroundColor:C.accentSoft},style]}>{children}</Text>;
  }
  return <View style={[...boxStyle(className,width),style]}>{children}</View>;
 }
 Host.displayName='NativeGame'+tag;
 return Host;
}
export const GameHost=Object.fromEntries(['div','section','aside','article','span','strong','p','small','h1','h2','button'].map(tag=>[tag,nativeControl(tag)]));

export function RenamePuppyDialog({name,onSave,onClose}){
 const [value,setValue]=useState(name);
 return <Modal visible transparent onRequestClose={onClose} animationType="fade">
  <View style={{flex:1,justifyContent:'center',padding:20,backgroundColor:'#000a'}}>
   <ScrollView style={{flexGrow:0}} contentContainerStyle={[card(),{gap:12}]}>
    <Text accessibilityRole="header" style={{fontSize:22,color:C.text}}>Rename puppy</Text>
    <TextInput accessibilityLabel="Puppy name" autoFocus selectTextOnFocus maxLength={18} value={value} onChangeText={setValue} style={{padding:12,color:C.text,backgroundColor:C.card}}/>
    <GameHost.button onClick={onClose}>Cancel</GameHost.button>
    <GameHost.button disabled={!value.trim()} onClick={()=>onSave(value.trim())}>Save name</GameHost.button>
   </ScrollView>
  </View>
 </Modal>;
}
