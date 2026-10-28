import React from 'react';
import {ActivityIndicator,Image,Pressable,StatusBar,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors as C,themedStyles} from '../theme.js';

/** Initial account restore happens behind this screen, never over gameplay. */
export default function PuppyStartupScreen({phase='loading',retry,busy=false}){
 const insets=useSafeAreaInsets();
 const error=phase==='connection-error'||phase==='setup-error';
 const title=error?'Unable to finish starting Puppy Clicker':'Puppy Clicker';
 const detail=phase==='setup-error'
  ?'Your saved app settings could not be loaded. Your account and progress were not reset.'
  :phase==='connection-error'
   ?'We could not securely verify your Pup Account and cloud progress. Check your connection and try again.'
   :'Signing in and loading your puppies, settings, rewards and saved progress…';
 return <View style={[s.root,{paddingTop:insets.top+24,paddingBottom:insets.bottom+24}]}>
  <StatusBar barStyle={C.dark?'light-content':'dark-content'} backgroundColor={C.bg}/>
  <View style={s.content}>
   <Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain"
    accessibilityLabel="Puppy Clicker" style={s.logo}/>
   <Text accessibilityRole="header" style={s.title}>{title}</Text>
   <Text style={s.description}>{detail}</Text>
   {!error?<ActivityIndicator accessibilityLabel="Loading Pup Account" size="large" color={C.accent}/>:null}
   {error?<Pressable accessibilityRole="button" accessibilityLabel="Retry startup" disabled={busy}
    onPress={retry} style={[s.button,busy&&{opacity:.55}]}>
    {busy?<ActivityIndicator color={C.onAccent}/>:<Text style={s.buttonText}>Try Again</Text>}
   </Pressable>:null}
  </View>
  <Text style={s.footer}>Protected by PupEye</Text>
 </View>;
}
const s=themedStyles(()=>({
 root:{flex:1,backgroundColor:C.bg,alignItems:'center',justifyContent:'space-between',paddingHorizontal:25},
 content:{flex:1,alignItems:'center',justifyContent:'center',gap:20,maxWidth:380,width:'100%'},
 logo:{width:130,height:130},
 title:{fontSize:26,fontWeight:'900',color:C.text,textAlign:'center'},
 description:{color:C.muted,fontSize:15,lineHeight:23,textAlign:'center'},
 button:{minHeight:51,borderRadius:18,backgroundColor:C.accent,alignItems:'center',justifyContent:'center',paddingHorizontal:28,width:'100%'},
 buttonText:{color:C.onAccent,fontWeight:'900',fontSize:17},
 footer:{color:C.muted,fontSize:12,fontWeight:'700'}
}));
