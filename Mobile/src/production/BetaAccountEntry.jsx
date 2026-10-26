import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Linking,Modal,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View} from 'react-native';
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
 const [mode,setMode]=useState('login');
 const [username,setUsername]=useState('');
 const [email,setEmail]=useState('');
 const [password,setPassword]=useState('');
 const [notice,setNotice]=useState('');
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
   if(message.includes('PUP_ACCOUNT_LOGIN_REQUIRED')||message.includes('PUP_ACCOUNT_DISCORD_REQUIRED')){
    setState({status:'NEEDS_LOGIN'});return;
   }
   if(message.includes('PUP_ACCOUNT_ON_OTHER_DEVICE')||message.includes('DISCORD_ALREADY_LINKED')){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('Your Discord account is already linked to another Puppy Clicker installation. The beta counts as a separate installation, even on the same phone. An authorized device transfer is required before beta access.');
    return;
   }
   setState({status:'NEEDS_LOGIN'});
   setProblem('The PupEye server could not verify this installation. Try signing in again.');
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
 const localSignIn=useCallback(async()=>{
  if(busy)return;
  if(!email.trim()||password.length<8||(mode==='register'&&!/^[A-Za-z0-9_.-]{3,20}$/.test(username))){
   setProblem('Enter a valid email and password (8+ characters). Registration also needs a 3–20 character username.');return;
  }
  setBusy(true);setProblem('');setNotice('');
  try{
   const native=api();
   const result=mode==='register'
    ?await native.registerLocal(username.trim(),email.trim(),password,publishableKey)
    :await native.loginLocal(email.trim(),password,publishableKey);
   setState(result);
   if(result.status==='VERIFIED')onVerified?.(result);
   else if(result.status==='EMAIL_CONFIRMATION_REQUIRED')setNotice('Check your email for a confirmation link, then sign in here. No account save was modified.');
  }catch(error){
   const code=String(error?.code||'');
   if(/PUP_ACCOUNT_ON_OTHER_DEVICE|DISCORD_ALREADY_LINKED/.test(code)){
    setState({status:'DEVICE_TRANSFER_REQUIRED'});
    setProblem('This Pup Account is already linked to a different installation. Use authorized device transfer; another account will not be created.');
   }else if(code.includes('PUP_USERNAME_TAKEN'))setProblem('That username is already registered. Choose another.');
   else if(code.includes('PUP_ACCOUNT_IDENTITY_CONFLICT'))setProblem('This installation already belongs to a different Pup Account. Use account recovery.');
   else if(code.includes('PUP_EMAIL_CONFIRMATION_REQUIRED'))setProblem('Verify your email, then sign in.');
   else if(code.includes('LOCAL_INVALID_CREDENTIALS'))setProblem('Email or password was not accepted. Try again.');
   else setProblem('Could not complete Pup Account sign-in. Check your details and connection and retry.');
  }finally{setPassword('');setBusy(false);}
 },[busy,mode,username,email,password,onVerified]);
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
   <Text style={styles.title}>{verified?'Pup Account connected':onboarding?'Set up your Pup Account':'Sign in to Puppy Clicker'}</Text>
   <Text style={styles.body}>{onboarding?'Create a Pup Account with an email, username and password, or sign in with Discord. Discord is optional. PupEye protects each account and its device.':'Use your Pup Account credentials or Discord to sign in. The beta never uninstalls or overwrites the Kotlin Play app.'}</Text>
   <View style={styles.card}>
    <Text style={styles.label}>PUPEYE ACCOUNT STATUS</Text>
    <Text style={styles.value}>{state.status.replaceAll('_',' ')}</Text>
    {verified?<><Text style={styles.body}>Pup Account: {state.username}</Text>
      <Text style={styles.body}>Cloud save: {state.cloudSaveExists?'Found':'Not found'}</Text>
      <Text style={styles.body}>Revision: {state.cloudRevision}</Text></>:
      <Text style={styles.body}>Register or sign in to your Pup Account, or choose Discord. No guest progression or preview save is substituted.</Text>}
   </View>
   {problem?<View style={styles.error}><Text style={styles.body}>{problem}</Text></View>:null}
   {notice?<Text style={styles.body}>{notice}</Text>:null}
   {busy?<ActivityIndicator color="#85D4FF" accessibilityLabel="Checking Pup Account and PupEye"/>:null}
   {verified&&onboarding?<Pressable accessibilityRole="button" style={styles.button} onPress={onClose}><Text style={styles.buttonText}>Use this Pup Account</Text></Pressable>:null}
   {!verified?<>
    <View style={styles.modeRow}>
     <Pressable accessibilityRole="button" accessibilityLabel="Sign in with Pup Account" onPress={()=>{setMode('login');setProblem('');setNotice('');}} style={[styles.mode,mode==='login'&&styles.selected]}><Text style={styles.secondaryText}>Sign in</Text></Pressable>
     <Pressable accessibilityRole="button" accessibilityLabel="Register a Pup Account" onPress={()=>{setMode('register');setProblem('');setNotice('');}} style={[styles.mode,mode==='register'&&styles.selected]}><Text style={styles.secondaryText}>Create account</Text></Pressable>
    </View>
    <View style={styles.card}>
     <Text style={styles.cardTitle}>{mode==='register'?'Create a Pup Account':'Pup Account sign-in'}</Text>
     {mode==='register'?<>
      <Text style={styles.label}>USERNAME</Text>
      <TextInput accessibilityLabel="Pup Account username" value={username} onChangeText={setUsername} autoCapitalize="none" autoCorrect={false} maxLength={20} style={styles.field} placeholder="3–20 letters, numbers, . _ -" placeholderTextColor="#9CB3C4"/>
     </>:null}
     <Text style={styles.label}>EMAIL</Text>
     <TextInput accessibilityLabel="Pup Account email" value={email} onChangeText={setEmail} autoComplete="email" keyboardType="email-address" autoCapitalize="none" autoCorrect={false} maxLength={254} style={styles.field} placeholder="you@example.com" placeholderTextColor="#9CB3C4"/>
     <Text style={styles.label}>PASSWORD</Text>
     <TextInput accessibilityLabel="Pup Account password" value={password} onChangeText={setPassword} autoComplete={mode==='register'?'new-password':'current-password'} secureTextEntry maxLength={128} style={styles.field} placeholder="At least 8 characters" placeholderTextColor="#9CB3C4"/>
     <Pressable accessibilityRole="button" disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void localSignIn()}><Text style={styles.buttonText}>{mode==='register'?'Register Pup Account':'Sign in with Pup Account'}</Text></Pressable>
     <Text style={styles.body}>Passwords are verified by Supabase Auth. A new account may require email confirmation before sign-in.</Text>
    </View>
    <Pressable accessibilityRole="button" accessibilityLabel="Sign in with Discord instead" disabled={busy} style={[styles.secondary,busy&&styles.disabled]} onPress={()=>void signin()}>
     <Text style={styles.secondaryText}>Continue with Discord (optional)</Text>
    </Pressable>
   </>:<>
    <Pressable accessibilityRole="button" disabled={busy} style={styles.button} onPress={()=>void read()}>
     <Text style={styles.buttonText}>Verify cloud save</Text>
    </Pressable>
    {state.discordUserId?<Text style={styles.body}>Discord: linked</Text>:<Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={()=>void signin()}><Text style={styles.secondaryText}>Link Discord (optional)</Text></Pressable>}
    {cloud?<Text style={styles.body}>Cloud response: {cloud.exists?'Save available at revision '+cloud.revision:'No authoritative save yet'}</Text>:null}
   </>}
   <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={()=>void check()}>
    <Text style={styles.secondaryText}>Retry account status</Text>
   </Pressable>
   {!onboarding?<View style={styles.card}>
    <Text style={styles.cardTitle}>About the beta</Text>
    <Text style={styles.body}>The original Puppy Clicker remains installed. PupEye enforces one active installation for existing Pup Accounts, whether signed in with Discord or an email/password; existing accounts may require an authorized device transfer.</Text>
    <Text style={styles.body}>Gameplay stays locked until the authenticated cloud state and signed economy are supported end to end. This is not a guest gameplay mode.</Text>
    <Text style={styles.body}>If you choose Discord and Android asks which app should receive authorization, choose Puppy Clicker [Beta].</Text>
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
 modeRow:{flexDirection:'row',gap:10},mode:{flex:1,padding:12,alignItems:'center',borderWidth:1,borderColor:'#5D8CA9',borderRadius:12},selected:{backgroundColor:'#254961',borderColor:'#87D6FF'},field:{borderWidth:1,borderColor:'#5D8CA9',borderRadius:12,color:'#FFF',fontSize:16,paddingHorizontal:14,paddingVertical:12,minHeight:48},
 disabled:{opacity:0.5}
});
