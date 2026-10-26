import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Linking,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

// This is the project's public publishable key, not an admin/service-role secret.
const publishableKey=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||
 'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const api=()=>NativeModules.PuppyBetaAccount;
const isCallback=url=>typeof url==='string'&&
 /^discord-1547982688898777118:\/authorize\/callback(?:\?|$)/.test(url);

export default function BetaAccountEntry(){
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
  }catch(error){
   // A device can have a valid PupEye session but no Discord link yet.
   // Do not manufacture gameplay credentials or progression from this.
   const message=String(error?.message||'');
   if(message.includes('PUP_ACCOUNT_DISCORD_REQUIRED')){
    setState({status:'NEEDS_DISCORD'});return;
   }
   if(message.includes('PUP_ACCOUNT_ON_OTHER_DEVICE')||message.includes('DISCORD_ALREADY_LINKED')){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('Your Pup Account is already linked to the Play app. The server requires an authorized transfer. Original progress is untouched.');
    return;
   }
   setState({status:'NEEDS_DISCORD'});
   setProblem('The PupEye server could not verify this beta installation. You may retry Discord sign-in.');
  }
 },[]);
 const finish=useCallback(async url=>{
  if(!isCallback(url)||recent.current===url)return;
  recent.current=url;
  setBusy(true);
  setProblem('');
  try{
   const result=await api().completeDiscord(url,publishableKey);
   setState(result);
  }catch(error){
   const message=String(error?.message||'');
   if(message.includes('PUP_ACCOUNT_ON_OTHER_DEVICE')||message.includes('DISCORD_ALREADY_LINKED')){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('Your Discord account is already linked to the original Play installation. PupEye must authorize device transfer before beta access.');
   }else{
    setProblem('Discord/PupEye authorization did not complete. Confirm you selected Puppy Clicker [Beta] for the callback and try again.');
   }
  }finally{setBusy(false);}
 },[]);
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
 return <View style={[styles.root,{paddingTop:insets.top+18,paddingBottom:insets.bottom+20}]}>
  <StatusBar barStyle="light-content" backgroundColor="#0F1A23"/>
  <ScrollView contentContainerStyle={styles.content}>
   <Text style={styles.logo}>🐶 Puppy Clicker [Beta]</Text>
   <Text style={styles.title}>{verified?'Pup Account connected':'Sign in to Puppy Clicker'}</Text>
   <Text style={styles.body}>This beta has its own secured Android identity. It never reads, uninstalls or overwrites your original Kotlin app.</Text>
   <View style={styles.card}>
    <Text style={styles.label}>PUPEYE ACCOUNT STATUS</Text>
    <Text style={styles.value}>{state.status.replaceAll('_',' ')}</Text>
    {verified?<><Text style={styles.body}>Discord: {state.username}</Text>
      <Text style={styles.body}>Cloud save: {state.cloudSaveExists?'Found':'Not found'}</Text>
      <Text style={styles.body}>Revision: {state.cloudRevision}</Text></>:
      <Text style={styles.body}>Connect Discord to register a real PupEye session. No guest game is launched and no preview save is substituted.</Text>}
   </View>
   {problem?<View style={styles.error}><Text style={styles.body}>{problem}</Text></View>:null}
   {busy?<ActivityIndicator color="#85D4FF" accessibilityLabel="Checking Discord and PupEye"/>:null}
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
   <View style={styles.card}>
    <Text style={styles.cardTitle}>About the beta</Text>
    <Text style={styles.body}>The original Puppy Clicker remains installed. Discord and Supabase enforce one active PupEye installation for existing accounts; access to an existing account may need an authorized device transfer.</Text>
    <Text style={styles.body}>Gameplay stays locked until the authenticated cloud state and signed economy are supported end to end. This is not a guest gameplay mode.</Text>
    <Text style={styles.body}>If Android asks which app should receive Discord authorization, choose Puppy Clicker [Beta].</Text>
   </View>
  </ScrollView>
 </View>;
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
