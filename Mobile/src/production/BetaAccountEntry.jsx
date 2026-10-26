import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Linking,Modal,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

// This is the project's public publishable key, not an admin/service-role secret.
const publishableKey=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||
 'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const api=()=>NativeModules.PuppyBetaAccount;
const isCallback=url=>typeof url==='string'&&
 /^discord-1547982688898777118:\/authorize\/callback(?:\?|$)/.test(url);

export default function BetaAccountEntry({visible=true,onVerified,onClose,onboarding=false}){
 const insets=useSafeAreaInsets();
 const [state,setState]=useState({status:'CHECKING'});
 const [busy,setBusy]=useState(false);
 const [problem,setProblem]=useState('');
 const [cloud,setCloud]=useState(null);
 const recent=useRef('');
 const check=useCallback(async()=>{
  const native=api();
  if(!native||typeof native.status!=='function'){
   setState({status:'NATIVE_MODULE_MISSING'});
   return;
  }
  try{
   const result=await native.status(publishableKey);
   setState(result);
   if(result.status==='VERIFIED')onVerified?.(result);
  }catch(error){
   // A device can have a valid PupEye session but no Discord link yet.
   // Do not manufacture gameplay credentials or progression from this.
   const message=[error?.code,error?.message].filter(Boolean).join(' ');
   if(message.includes('PUP_ACCOUNT_DISCORD_REQUIRED')){
    setState({status:'NEEDS_DISCORD'});return;
   }
   if(message.includes('PUP_ACCOUNT_ON_OTHER_DEVICE')||message.includes('DISCORD_ALREADY_LINKED')){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('Your Discord account is already linked to another Puppy Clicker installation. The beta counts as a separate installation, even on the same phone. An authorized device transfer is required before beta access.');
    return;
   }
   setState({status:'NEEDS_DISCORD'});
   setProblem('The PupEye server could not verify this beta installation. You may retry Discord sign-in.');
  }
 },[onVerified]);
 const finish=useCallback(async url=>{
  if(!isCallback(url)||recent.current===url)return;
  recent.current=url;
  setBusy(true);
  setProblem('');
  try{
   const result=await api().completeDiscord(url,publishableKey);
   setState(result);
   if(result.status==='VERIFIED')onVerified?.(result);
  }catch(error){
   const message=[error?.code,error?.message].filter(Boolean).join(' ');
   if(message.includes('PUP_ACCOUNT_ON_OTHER_DEVICE')||message.includes('DISCORD_ALREADY_LINKED')){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('Discord authorization succeeded, but PupEye blocked this beta because your account is already linked to another Puppy Clicker installation. The beta counts as a separate installation, even on the same phone. An authorized device transfer is required before beta access.');
   }else{
    setProblem('Discord/PupEye sign-in could not complete. Please retry.');
   }
  }finally{setBusy(false);}
 },[onVerified]);
 useEffect(()=>{
  let alive=true;
  void check();
  void Linking.getInitialURL().then(url=>{if(alive&&isCallback(url))void finish(url);});
  const sub=Linking.addEventListener('url',e=>{if(isCallback(e.url))void finish(e.url);});
  return ()=>{alive=false;sub.remove();};
 },[check,finish]);
 const signin=useCallback(async()=>{
  setBusy(true);setProblem('');recent.current='';
  try{await api().beginDiscord();}
  catch{setProblem('Unable to open Discord. Check your connection and retry.');}
  finally{setBusy(false);}
 },[]);
 const read=useCallback(async()=>{
  setBusy(true);setProblem('');
  try{
   const result=await api().cloudRead(publishableKey);
   setCloud({exists:result.exists===true,revision:result.revision||0});
  }catch{setProblem('Server-side cloud-save verification failed. No local or Play data was changed.');}
  finally{setBusy(false);}
 },[]);
 const verified=state.status==='VERIFIED';
 return <Modal visible={visible} animationType="slide" statusBarTranslucent navigationBarTranslucent onRequestClose={onClose}>
 <View style={[styles.root,{paddingTop:insets.top+18,paddingBottom:insets.bottom+20}]}>
  <StatusBar barStyle="light-content" backgroundColor="#0F1A23"/>
  <ScrollView contentContainerStyle={styles.content}>
   <Pressable accessibilityRole="button" onPress={onClose} style={styles.secondary}><Text style={styles.secondaryText}>{onboarding?'← Back to account setup':'← Back to Puppy Clicker'}</Text></Pressable>
   <Text style={styles.logo}>🐶 Puppy Clicker [Beta]</Text>
   <Text style={styles.title}>{verified?'Pup Account connected':onboarding?'Connect Discord':'Sign in to Puppy Clicker'}</Text>
   <Text style={styles.body}>{onboarding?'Passwordless Pup Account sign-in. Discord verifies your identity; Supabase stores your account and cloud progression. PupEye controls device access.':'This beta has its own secured Android identity. It cannot read the Play app sandbox or overwrite its local save. Your existing Discord account can authorize a read-only beta companion.'}</Text>
   <View style={styles.card}>
    <Text style={styles.label}>PUPEYE ACCOUNT STATUS</Text>
    <Text style={styles.value}>{state.status.replaceAll('_',' ')}</Text>
    {verified?<><Text style={styles.body}>Discord: {state.username}</Text>
      <Text style={styles.body}>Cloud save: {state.cloudSaveExists?'Found':'Not found'}</Text>
      <Text style={styles.body}>Revision: {state.cloudRevision}</Text>
      {state.linkedFromPlay?<Text style={styles.body}>Connected to your original Pup Account in read-only beta mode. The Play installation stays active; no device transfer or guest progress was created.</Text>:null}</>:
      <Text style={styles.body}>Connect Discord to register a real PupEye session. No guest game is launched and no preview save is substituted.</Text>}
   </View>
   {problem?<View style={styles.error}><Text style={styles.body}>{problem}</Text></View>:null}
   {busy?<ActivityIndicator color="#85D4FF" accessibilityLabel="Checking Discord and PupEye"/>:null}
   {verified&&onboarding?<Pressable accessibilityRole="button" style={styles.button} onPress={onClose}><Text style={styles.buttonText}>Use this Pup Account</Text></Pressable>:null}
   {!verified?<Pressable accessibilityRole="button" accessibilityLabel="Connect Discord to Pup Account"
    disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void signin()}>
    <Text style={styles.buttonText}>Connect with Discord</Text>
   </Pressable>:<>
    <Pressable accessibilityRole="button" disabled={busy} style={styles.button} onPress={()=>void read()}>
     <Text style={styles.buttonText}>Verify cloud save</Text>
    </Pressable>
    {cloud?<Text style={styles.body}>Cloud response: {cloud.exists?'Save available at revision '+cloud.revision:'No authoritative save yet'}</Text>:null}
   </>}
   <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={()=>void check()}>
    <Text style={styles.secondaryText}>Retry account status</Text>
   </Pressable>
   {!onboarding?<View style={styles.card}>
    <Text style={styles.cardTitle}>About the beta</Text>
    <Text style={styles.body}>The original Puppy Clicker remains installed and retains cloud-save write authority. Discord can authorize this beta as a read-only companion without changing the primary PupEye device.</Text>
    <Text style={styles.body}>Gameplay stays locked until the authenticated cloud state and signed economy are supported end to end. This is not a guest gameplay mode.</Text>
    <Text style={styles.body}>If Android asks which app should receive Discord authorization, choose Puppy Clicker [Beta].</Text>
   </View>:null}
  </ScrollView>
 </View>
 </Modal>;
}
const styles=StyleSheet.create({
 root:{flex:1,backgroundColor:'#0F1A23'},
 content:{padding:22,gap:18,maxWidth:600,width:'100%',alignSelf:'center'},
 logo:{fontWeight:'900',fontSize:21,color:'#FFF'},
 title:{color:'#FFF',fontSize:29,fontWeight:'900'},
 body:{color:'#D0DEE8',fontSize:15,lineHeight:24},
 card:{borderRadius:18,borderWidth:1,borderColor:'#415767',padding:18,gap:12,backgroundColor:'#1A2C38'},
 label:{color:'#8DD6FF',fontSize:13,fontWeight:'900',letterSpacing:1},
 value:{color:'#FFF',fontWeight:'900',fontSize:20},
 cardTitle:{color:'#FFF',fontSize:19,fontWeight:'800'},
 error:{borderWidth:1,borderColor:'#CB7777',padding:16,borderRadius:15,backgroundColor:'#38282D'},
 button:{backgroundColor:'#87D6FF',padding:18,borderRadius:14,alignItems:'center'},
 buttonText:{color:'#0B1C29',fontSize:17,fontWeight:'900'},
 secondary:{borderRadius:14,borderWidth:1,borderColor:'#5D8CA9',padding:14,alignItems:'center'},
 secondaryText:{color:'#B0E5FF',fontWeight:'800'},
 disabled:{opacity:0.5}
});
