import React,{useCallback,useEffect,useState} from 'react';
import {ActivityIndicator,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {classifyOriginalInstall,classifyVerifiedAccount} from './authority.js';
import BetaAccountEntry from './BetaAccountEntry.jsx';

const key=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||'';
const bridge=()=>NativeModules.PuppyProductionAuthority;
export default function ProductionEntry(){
 return NativeModules.PuppyBetaAccount?.isBeta===true?<BetaAccountEntry/>:<OriginalEntry/>;
}
function OriginalEntry(){
 const insets=useSafeAreaInsets();
 const [state,setState]=useState({status:'CHECKING',canPlay:false,
  message:'Inspecting the original Kotlin installation without changing player data.'});
 const [details,setDetails]=useState(null);
 const [busy,setBusy]=useState(false);
 const verify=useCallback(async()=>{
  setBusy(true);
  setDetails(null);
  try{
   const native=bridge();
   if(!native||typeof native.inspect!=='function'){
    setState(classifyOriginalInstall(null));return;
   }
   const probe=await native.inspect();
   const migration=classifyOriginalInstall(probe);
   setState(migration);
   if(migration.status!=='LEGACY_IDENTITY_PRESENT')return;
   if(!key){
    setState({status:'SERVER_KEY_MISSING',canPlay:false,
     message:'The production Supabase publishable key has not been configured. Device identity and local saves remain untouched.'});
    return;
   }
   if(typeof native.accountStatus!=='function'){
    setState({status:'NATIVE_ACCOUNT_CLIENT_MISSING',canPlay:false,
     message:'The Android PupEye account verification module is not available.'});
    return;
   }
   const account=await native.accountStatus(key);
   setDetails({username:account.username,hasSave:account.cloudSaveExists===true});
   setState(classifyVerifiedAccount(account));
  }catch(err){
   const code=String(err?.code||'').replace(/^BETA_/,'');
   if(['GLOBAL_BANNED','PUP_ACCOUNT_BLOCKED'].includes(code)){
    setState({status:'BANNED',canPlay:false,message:'Puppy Clicker access is restricted for this account or device. Contact Support for a review.'});
    return;
   }
   if(code==='REVIEW_REQUIRED'){
    setState({status:'REVIEW_REQUIRED',canPlay:false,message:'PupEye requires a Support review before access can continue.'});
    return;
   }
   // Do not display tokens, identities, signed envelopes, or raw exception data.
   setState({status:'ACCOUNT_VERIFICATION_BLOCKED',canPlay:false,
    message:'PupEye account verification could not complete. Your Kotlin data has not been overwritten. Reopen the original app or use the official account recovery process.'});
  }finally{
   setBusy(false);
  }
 },[]);
 useEffect(()=>{void verify();},[verify]);
 return <View style={[styles.root,{paddingTop:insets.top+16,paddingBottom:insets.bottom+20}]}>
  <StatusBar barStyle="light-content" backgroundColor="#101921"/>
  <ScrollView contentContainerStyle={styles.content}>
   <Text style={styles.logo}>🐶 Puppy Clicker</Text>
   <Text style={styles.heading}>Pup Account migration</Text>
   <Text style={styles.body}>Your existing Pup Account, cloud progress and PupEye device identity must be verified before the React Native game can replace Kotlin.</Text>
   <View style={styles.status}>
    <Text style={styles.statusTitle}>{busy?'Checking account':state.canPlay?'Account ready':'Account verification'}</Text>
    <Text style={styles.body}>{state.message}</Text>
    {busy?<ActivityIndicator size="small" color="#84D1FF" accessibilityLabel="Verifying Pup Account"/>:null}
   </View>
   {details?<View style={styles.status}>
    <Text style={styles.label}>Server-verified Pup Account</Text>
    <Text style={styles.username}>{details.username}</Text>
    
    <Text style={styles.body}>Original cloud save: {details.hasSave?'Found':'Not found'}</Text>
   </View>:null}
   <View style={styles.status}>
    <Text style={styles.label}>Existing progression is protected</Text>
    <Text style={styles.body}>No guest account is created. This screen never uploads a local preview save or replaces the original Kotlin save. Signed game transactions and recovery must be completed before gameplay is available.</Text>
   </View>
   <Pressable accessibilityRole="button" accessibilityLabel="Retry Pup Account verification"
    disabled={busy} onPress={()=>void verify()} style={[styles.button,busy&&styles.disabled]}>
    <Text style={styles.buttonText}>{busy?'Checking account…':'Retry account verification'}</Text>
   </Pressable>
  </ScrollView>
 </View>;
}
const styles=StyleSheet.create({
 root:{flex:1,backgroundColor:'#101921'},
 content:{padding:22,gap:18,maxWidth:560,width:'100%',alignSelf:'center'},
 logo:{color:'#F7F9FC',fontSize:20,fontWeight:'800'},
 heading:{color:'#F7F9FC',fontSize:28,fontWeight:'900'},
 body:{color:'#D2E0E9',fontSize:14,lineHeight:22},
 status:{backgroundColor:'#1B2C38',borderColor:'#3B5260',borderWidth:1,padding:17,borderRadius:16,gap:10},
 statusTitle:{color:'#84D1FF',fontSize:14,fontWeight:'900',letterSpacing:0.5},
 label:{color:'#F7F9FC',fontSize:15,fontWeight:'800'},
 username:{color:'#84D1FF',fontSize:18,fontWeight:'800'},
 button:{backgroundColor:'#87D6FF',padding:16,borderRadius:14,alignItems:'center'},
 buttonText:{color:'#071420',fontWeight:'900'},
 disabled:{opacity:0.5}
});
