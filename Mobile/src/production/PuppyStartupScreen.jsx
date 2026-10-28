import React from 'react';
import {ActivityIndicator,Image,Pressable,Share,StatusBar,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors as C,themedStyles} from '../theme.js';
import {publicStartupIssue} from './startupErrors.js';

/** Startup failures show only a public catalog entry; never a raw exception. */
export default function PuppyStartupScreen({phase='loading',retry,busy=false,issue}){
 const insets=useSafeAreaInsets();
 const failed=phase==='connection-error'||phase==='setup-error'||phase==='crash';
 const safe=publicStartupIssue(issue?.code||(phase==='setup-error'?'PUPPY-START-101':
  phase==='crash'?'PUPPY-CRASH-902':'PUPPY-NET-702'));
 const title=failed?safe.title:'Puppy Clicker';
 const detail=failed?safe.message:'Signing in and loading your puppies, settings, rewards and saved progress…';
 const shareCode=()=>{void Share.share({message:'Puppy Clicker startup error: '+safe.code+' — '+safe.title}).catch(()=>{});};
 return <View style={[s.root,{paddingTop:insets.top+24,paddingBottom:insets.bottom+24}]}>
  <StatusBar barStyle={C.dark?'light-content':'dark-content'} backgroundColor={C.bg}/>
  <View style={s.content}>
   <Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain"
    accessibilityLabel="Puppy Clicker" style={s.logo}/>
   <Text accessibilityRole="header" style={s.title}>{title}</Text>
   <Text style={s.description}>{detail}</Text>
   {failed?<View style={s.issue}>
    <Text accessibilityLabel={'Error code '+safe.code} selectable style={s.code}>{safe.code}</Text>
    <Text style={s.recovery}>{safe.recovery}</Text>
   </View>:null}
   {!failed?<ActivityIndicator accessibilityLabel="Loading Pup Account" size="large" color={C.accent}/>:null}
   {failed?<View style={s.actions}>
    <Pressable accessibilityRole="button" accessibilityLabel="Retry startup" disabled={busy||!retry}
     onPress={retry} style={[s.button,(busy||!retry)&&{opacity:.55}]}>
     {busy?<ActivityIndicator color={C.onAccent}/>:<Text style={s.buttonText}>Try Again</Text>}
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel="Share error code" onPress={shareCode}
     style={s.secondary}><Text style={s.secondaryText}>Share Error Code</Text></Pressable>
   </View>:null}
  </View>
  <Text style={s.footer}>Protected by PupEye · No account data was reset</Text>
 </View>;
}
const s=themedStyles(()=>({
 root:{flex:1,backgroundColor:C.bg,alignItems:'center',justifyContent:'space-between',paddingHorizontal:25},
 content:{flex:1,alignItems:'center',justifyContent:'center',gap:18,maxWidth:380,width:'100%'},
 logo:{width:116,height:116},
 title:{fontSize:25,fontWeight:'900',color:C.text,textAlign:'center'},
 description:{color:C.muted,fontSize:15,lineHeight:23,textAlign:'center'},
 issue:{backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:16,padding:17,gap:12,width:'100%'},
 code:{color:C.accentInk,fontSize:19,fontWeight:'900',textAlign:'center',letterSpacing:.5},
 recovery:{color:C.text,fontSize:14,lineHeight:21,textAlign:'center'},
 actions:{width:'100%',gap:10},
 button:{minHeight:51,borderRadius:18,backgroundColor:C.accent,alignItems:'center',justifyContent:'center',paddingHorizontal:28,width:'100%'},
 buttonText:{color:C.onAccent,fontWeight:'900',fontSize:17},
 secondary:{minHeight:48,borderRadius:18,borderColor:C.line,borderWidth:1,alignItems:'center',justifyContent:'center'},
 secondaryText:{color:C.text,fontSize:15,fontWeight:'800'},
 footer:{color:C.muted,fontSize:12,fontWeight:'700',textAlign:'center'}
}));
