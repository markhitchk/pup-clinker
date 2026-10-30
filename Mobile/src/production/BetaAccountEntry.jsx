import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Linking,Modal,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import legal from '../../assets/legal/documents.json';
import {isWebDiscordCallback} from './webDiscordCallback.js';
import {supportsDatabaseLayout} from './dbSchemaLayout.js';

// This is the project's public publishable key, not an admin/service-role secret.
const publishableKey=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||
 'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const api=()=>NativeModules.PuppyBetaAccount;
const isCallback=url=>typeof url==='string'&&
 /^discord-1547982688898777118:\/authorize\/callback(?:\?|$)/.test(url);

export default function BetaAccountEntry({visible=true,onVerified,onClose,onboarding=false,initialMode='signin',requiredAtStartup=false}){
 const insets=useSafeAreaInsets();
 const [state,setState]=useState({status:'CHECKING'});
 const [busy,setBusy]=useState(false);
 const [problem,setProblem]=useState('');
 const [cloud,setCloud]=useState(null);
 const [mode,setMode]=useState(initialMode);
 const [username,setUsername]=useState('');
 const [email,setEmail]=useState('');
 const [login,setLogin]=useState('');
 const [password,setPassword]=useState('');
 const [notice,setNotice]=useState('');
 const [linkNative,setLinkNative]=useState(false);
 const [tos,setTos]=useState(false),[privacy,setPrivacy]=useState(false),[policy,setPolicy]=useState(null);
 const [contactEmail,setContactEmail]=useState('');
 useEffect(()=>{if(visible){setMode(initialMode);setLinkNative(false);setNotice('');setProblem('');setTos(false);setPrivacy(false);}},[visible,initialMode]);
 const close=useCallback(()=>{setPassword('');setNotice('');setProblem('');onClose?.();},[onClose]);
 const recent=useRef('');
 const check=useCallback(async()=>{
  const native=api();
  if(!native||typeof native.status!=='function'){
   setState({status:'NATIVE_MODULE_MISSING'});
   return;
  }
  try{
   const result=await native.status(publishableKey);
   if(result?.status==='VERIFIED'&&!supportsDatabaseLayout(result.databaseLayoutVersion)){
    setState({status:'DATABASE_LAYOUT_UNSUPPORTED'});
    setProblem('Update Puppy Clicker Android beta before reconnecting your Pup Account.');
    return;
   }
   setState(result);
   if(result.status==='VERIFIED'&&result.legalAccepted===true)onVerified?.(result);
  }catch(error){
   // A device can have a valid PupEye session but no Discord link yet.
   // Do not manufacture gameplay credentials or progression from this.
   const message=[error?.code,error?.message].filter(Boolean).join(' ');
   if(['PUP_ACCOUNT_DISCORD_REQUIRED','PUP_ACCOUNT_LOGIN_REQUIRED','BETA_LINK_REQUIRED'].some(code=>message.includes(code))){
    setState({status:'NEEDS_ACCOUNT'});return;
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
  // Browser-initiated Discord code belongs to its own PKCE verifier.
  // Only the app-root WebDiscordHandoff may forward it; never pass it to PupEye.
  if(isWebDiscordCallback(url)||!isCallback(url)||recent.current===url)return;
  recent.current=url;
  setBusy(true);
  setProblem('');
  try{
   const result=await api().completeDiscord(url,publishableKey);
   if(result?.status==='VERIFIED'&&!supportsDatabaseLayout(result.databaseLayoutVersion)){
    setState({status:'DATABASE_LAYOUT_UNSUPPORTED'});
    setProblem('Update Puppy Clicker Android beta before reconnecting your Pup Account.');
    return;
   }
   setState(result);
   if(result.status==='VERIFIED'&&result.legalAccepted===true)onVerified?.(result);
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
  // The closed account sheet must not issue a second full account lookup on launch.
  if(visible)void check();
  void Linking.getInitialURL().then(url=>{if(alive&&isCallback(url))void finish(url);});
  const sub=Linking.addEventListener('url',e=>{if(isCallback(e.url))void finish(e.url);});
  return ()=>{alive=false;sub.remove();};
 },[check,finish,visible]);
 const signin=useCallback(async()=>{
  setBusy(true);setProblem('');recent.current='';
  try{await api().beginDiscord();}
  catch{setProblem('Unable to open Discord. Check your connection and retry.');}
  finally{setBusy(false);}
 },[]);
 const submit=useCallback(async()=>{
  if(busy)return;
  if(mode==='signup'&&(!/^[A-Za-z0-9_-]{3,20}$/.test(username)||password.length<8||(email.trim()&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())))){
   setProblem('Enter a valid username, password of at least 8 characters, and optional support contact email.');return;
  }
  if(mode==='signup'&&(!tos||!privacy)){setProblem('Accept both the Terms of Use and Privacy Policy.');return;}
  if(linkNative&&mode==='signup'&&state.status==='VERIFIED'&&username.trim().toLowerCase()!==String(state.username||'').toLowerCase()){
   setProblem('Keep your existing Pup username when adding password sign-in.');return;
  }
  if(mode==='signin'&&(!login.trim()||!password)){
   setProblem('Enter your Pup username and password.');return;
  }
  setBusy(true);setProblem('');setNotice('');
  try{
   const result=mode==='signup'
    ?await api().createPupAccount(username.trim(),email.trim(),password,tos,privacy,publishableKey)
    :await api().signInPupAccount(login.trim(),password,publishableKey);
   setPassword('');
   if(result?.status==='VERIFIED'&&!supportsDatabaseLayout(result.databaseLayoutVersion)){
    setState({status:'DATABASE_LAYOUT_UNSUPPORTED'});
    setProblem('Update Puppy Clicker Android beta before reconnecting your Pup Account.');
    return;
   }
   setState(result);setLinkNative(false);
   setContactEmail(result.contactEmail||email.trim());
   if(result.status==='VERIFIED'&&result.legalAccepted===true)onVerified?.(result);
  }catch(error){
   const code=String(error?.code||'').replace(/^BETA_/,'');
   const messages={
    PUP_USERNAME_TAKEN:'That Pup username is already taken. Choose another.',
    PUP_EMAIL_UNVERIFIED:'Please verify your email, then sign in.',
    PUP_SIGNIN_INVALID:'Incorrect Pup username or password.',
    PUP_SIGNUP_UNAVAILABLE:'Could not register Pup Account. You can sign in with Discord instead.',
    PUP_PASSWORD_SETUP_UNAVAILABLE:'Password registration is unavailable; Discord sign-in remains available.',
    PUP_PASSWORD_LOGIN_DISABLED:'Enable password authentication in Supabase Auth. No SMTP is needed.',
    PUP_LEGAL_ACCEPTANCE_REQUIRED:'Accept both required legal policies.',
    PUP_CONTACT_INVALID:'Enter a valid optional support contact email.',
    PUP_CREDENTIAL_ALREADY_LINKED:'This Pup Account already has a password login.',
    PUP_ACCOUNT_RECOVERY_REQUIRED:'Pup Account recovery is required. Contact Support.',
    PUP_ACCOUNT_SWITCH_BLOCKED:'This beta installation is already linked to another account.',
    PUP_ACCOUNT_DEVICE_UNAVAILABLE:'The original PupEye installation is unavailable. Contact Support.'
   };
   setProblem(messages[code]||'Pup Account authentication failed ('+code.slice(0,45)+'). Please retry.');
  }finally{setBusy(false);}
 },[busy,mode,username,email,login,password,tos,privacy,onVerified,linkNative]);
 const saveSupportContact=useCallback(async()=>{
  if(busy)return;
  setBusy(true);setProblem('');setNotice('');
  try{const result=await api().updateContactEmail(contactEmail,publishableKey);
   setState(result);setContactEmail(result.contactEmail||'');
   setNotice('Support contact saved. No verification mail was sent.');
  }catch{setProblem('Could not update your optional support contact email.');}
  finally{setBusy(false);}
 },[busy,contactEmail]);
 const consent=useCallback(async()=>{
  if(!tos||!privacy||busy){setProblem('Accept both policies first.');return;}
  setBusy(true);setProblem('');
  try{const r=await api().acceptPolicies(tos,privacy,publishableKey);setState(r);
   if(r.status==='VERIFIED'&&r.legalAccepted===true)onVerified?.(r);
  }catch{setProblem('Could not save legal acceptance. Please retry.');}
  finally{setBusy(false);}
 },[tos,privacy,busy,onVerified]);
 const read=useCallback(async()=>{
  setBusy(true);setProblem('');
  try{
   const result=await api().cloudRead(publishableKey);
   setCloud({exists:result.exists===true,revision:result.revision||0});
  }catch{setProblem('Server-side cloud-save verification failed. No local or Play data was changed.');}
  finally{setBusy(false);}
 },[]);
 const verified=state.status==='VERIFIED';
 const consentNeeded=verified&&state.legalAccepted!==true;
 const agreements=<View style={styles.card}>
  <Text style={styles.cardTitle}>Required agreements</Text>
  <Pressable accessibilityRole="checkbox" accessibilityLabel="Accept Terms of Use" accessibilityState={{checked:tos}} onPress={()=>setTos(v=>!v)}><Text style={styles.body}>{tos?'☑':'☐'} I accept the Terms of Use (version 1)</Text></Pressable>
  <Pressable accessibilityRole="button" accessibilityLabel="Read Terms of Use" onPress={()=>setPolicy('terms')}><Text style={styles.secondaryText}>Read Terms of Use</Text></Pressable>
  <Pressable accessibilityRole="checkbox" accessibilityLabel="Accept Privacy Policy" accessibilityState={{checked:privacy}} onPress={()=>setPrivacy(v=>!v)}><Text style={styles.body}>{privacy?'☑':'☐'} I have read and accept the Privacy Policy (version 1)</Text></Pressable>
  <Pressable accessibilityRole="button" accessibilityLabel="Read Privacy Policy" onPress={()=>setPolicy('privacy')}><Text style={styles.secondaryText}>Read Privacy Policy</Text></Pressable>
  <Text style={styles.body}>Optional diagnostics and notifications are separate choices.</Text>
 </View>;
 return <Modal visible={visible} animationType="slide" statusBarTranslucent navigationBarTranslucent onRequestClose={requiredAtStartup?()=>{}:close}>
 <View style={[styles.root,{paddingTop:insets.top+18,paddingBottom:insets.bottom+20}]}>
  <StatusBar barStyle="light-content" backgroundColor="#0F1A23"/>
  <ScrollView contentContainerStyle={styles.content}>
   {!requiredAtStartup?<Pressable accessibilityRole="button" onPress={close} style={styles.secondary}><Text style={styles.secondaryText}>{onboarding?'← Back to account setup':'← Back to Puppy Clicker'}</Text></Pressable>:null}
   <Text style={styles.logo}>🐶 Puppy Clicker [Beta]</Text>
   <Text style={styles.title}>{verified?'Pup Account connected':onboarding?'Set up Pup Account':'Sign in to Puppy Clicker'}</Text>
   <Text style={styles.body}>{onboarding?'Use a Pup username and password, or connect Discord instead. A support email is optional.':'Pup Account is Puppy Clicker’s own login system. You can use credentials or Discord, without replacing your Play app.'}</Text>
   <View style={styles.card}>
    <Text style={styles.label}>PUP ACCOUNT</Text>
    <Text style={styles.value}>{verified?'Connected':state.status==='DEVICE_TRANSFER_REQUIRED'?'DEVICE TRANSFER REQUIRED':'Sign-in required'}</Text>
    {verified?<><Text style={styles.body}>Pup Account: @{state.username}</Text>
      <Text style={styles.body}>Discord: {state.guildRole?'Connected':'Optional'}</Text>
      {!onboarding&&!requiredAtStartup?<Text style={styles.body}>Cloud save: {state.cloudSaveExists?'Found':'Not found'}</Text>:null}
      {state.linkedFromPlay?<Text style={styles.body}>Connected to your original Pup Account. Signed progression, shop, rewards, and casino actions synchronize with Play. Your Play installation remains active.</Text>:null}</>:
      <Text style={styles.body}>Sign in with your Pup Account or Discord. PupEye requires a verified account; no guest save is substituted.</Text>}
   </View>
   {problem?<View style={styles.error}><Text accessibilityRole="alert" style={styles.body}>{problem}</Text></View>:null}
   {notice?<View style={styles.card}><Text accessibilityRole="alert" style={styles.body}>{notice}</Text></View>:null}
   {busy?<ActivityIndicator color="#85D4FF" accessibilityLabel="Checking Discord and PupEye"/>:null}
   {verified&&!consentNeeded&&onboarding?<Pressable accessibilityRole="button" style={styles.button} onPress={close}><Text style={styles.buttonText}>Use this Pup Account</Text></Pressable>:null}
   {verified&&!consentNeeded&&!linkNative?<View style={styles.card}><Text style={styles.cardTitle}>Support contact (optional)</Text><Text style={styles.body}>Staff may reply manually using a developer or support member's personal email address. This address is not verified or used for account access.</Text><TextInput accessibilityLabel="Support contact email" autoCapitalize="none" keyboardType="email-address" value={contactEmail} onChangeText={setContactEmail} placeholder="Optional contact email" placeholderTextColor="#8AA1AF" style={styles.input}/><Pressable accessibilityRole="button" accessibilityLabel="Save support contact" style={styles.secondary} disabled={busy} onPress={()=>void saveSupportContact()}><Text style={styles.secondaryText}>Save support contact</Text></Pressable></View>:null}
   {verified&&!consentNeeded&&!state.guildRole?<Pressable accessibilityRole="button" accessibilityLabel="Link Discord (optional)" disabled={busy} style={styles.secondary} onPress={()=>void signin()}>
    <Text style={styles.secondaryText}>Link Discord (optional)</Text>
   </Pressable>:null}
   {verified&&!consentNeeded&&!linkNative?<Pressable accessibilityRole="button" accessibilityLabel="Add Pup Account password login" disabled={busy} style={styles.secondary}
    onPress={()=>{setUsername(state.username||'');setLinkNative(true);setMode('signup');setNotice('');setProblem('');}}>
    <Text style={styles.secondaryText}>Add username/password sign-in to this Pup Account</Text>
   </Pressable>:null}
   {consentNeeded?<>{agreements}<Pressable accessibilityRole="button" accessibilityLabel="Accept both policies" disabled={!tos||!privacy||busy} style={styles.button} onPress={()=>void consent()}><Text style={styles.buttonText}>Accept and continue</Text></Pressable></>:null}
   {(!verified||linkNative)?<>
    {!linkNative?<View style={styles.modes}>
     {['signin','signup','discord'].map(item=><Pressable key={item} accessibilityRole="button"
      accessibilityLabel={item==='signin'?'Sign in':item==='signup'?'Create account':'Discord login'}
      onPress={()=>{setMode(item);setProblem('');setNotice('');setPassword('');}}
      style={[styles.mode,mode===item&&styles.modeSelected]}>
      <Text style={[styles.modeText,mode===item&&styles.modeSelectedText]}>{item==='signin'?'Sign in':item==='signup'?'Create account':'Discord'}</Text>
     </Pressable>)}
    </View>:<Pressable accessibilityRole="button" style={styles.secondary} onPress={()=>{setLinkNative(false);setPassword('');setProblem('');}}>
     <Text style={styles.secondaryText}>← Back to your Pup Account</Text>
    </Pressable>}
    {mode!=='discord'?<View style={styles.card}>
     <Text style={styles.cardTitle}>{linkNative?'Add username/password sign-in':mode==='signup'?'Create a Pup Account':'Sign in to your Pup Account'}</Text>
     {linkNative?<Text style={styles.body}>Link sign-in to your existing Discord Pup Account @{state.username}. Your puppies, Treats, and XP stay on the same account.</Text>:null}
     {mode==='signup'?<>
      <Text style={styles.label}>USERNAME</Text>
      <TextInput accessibilityLabel="Pup username" autoCapitalize="none" autoCorrect={false}
       placeholder="3–20 letters, numbers, _ or -" placeholderTextColor="#8AA1AF"
       maxLength={20} editable={!linkNative} value={username} onChangeText={setUsername} style={styles.input}/>
      <Text style={styles.label}>SUPPORT CONTACT EMAIL (OPTIONAL)</Text>
      <TextInput accessibilityLabel="Pup email" autoCapitalize="none" autoCorrect={false}
       keyboardType="email-address" autoComplete="email" placeholder="Not used for sign-in or verification"
       placeholderTextColor="#8AA1AF" value={email} onChangeText={setEmail} style={styles.input}/>
     </>:<>
      <Text style={styles.label}>PUP USERNAME</Text>
      <TextInput accessibilityLabel="Pup username" autoCapitalize="none" autoCorrect={false}
       placeholder="Pup username" placeholderTextColor="#8AA1AF"
       value={login} onChangeText={setLogin} style={styles.input}/>
     </>}
     <Text style={styles.label}>PASSWORD</Text>
     <TextInput accessibilityLabel="Pup password" secureTextEntry autoCapitalize="none"
      autoCorrect={false} autoComplete={mode==='signup'?'new-password':'current-password'}
      placeholder={mode==='signup'?'At least 8 characters':'Password'} placeholderTextColor="#8AA1AF"
      value={password} onChangeText={setPassword} style={styles.input}/>
     {mode==='signup'?agreements:null}
     <Pressable accessibilityRole="button" accessibilityLabel={linkNative?'Add password to Pup Account':mode==='signup'?'Create Pup Account':'Sign in with Pup Account'}
      disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void submit()}>
      <Text style={styles.buttonText}>{linkNative?'Add password to Pup Account':mode==='signup'?'Create Pup Account':'Sign in with Pup Account'}</Text>
     </Pressable>
     {mode==='signup'?<Text style={styles.body}>No verification email or SMTP is required. Contact email is optional and used only for support; password is handled by Supabase Auth.</Text>:null}
     {mode==='signup'&&!linkNative?<Pressable accessibilityRole="button" accessibilityLabel="I already use Discord" onPress={()=>{setMode('discord');setProblem('');setPassword('');}}>
      <Text style={styles.secondaryText}>Already use Discord? Sign in with Discord first, then add a password to your existing account.</Text>
     </Pressable>:null}
    </View>:<Pressable accessibilityRole="button" accessibilityLabel="Connect Discord to Pup Account"
     disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void signin()}>
     <Text style={styles.buttonText}>Connect with Discord</Text>
    </Pressable>}
   </>:(!onboarding&&!requiredAtStartup)?<>
    <Pressable accessibilityRole="button" disabled={busy} style={styles.button} onPress={()=>void read()}>
     <Text style={styles.buttonText}>Verify cloud save</Text>
    </Pressable>
    {cloud?<Text style={styles.body}>Cloud response: {cloud.exists?'Save available at revision '+cloud.revision:'No authoritative save yet'}</Text>:null}
   </>:null}
   <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={()=>void check()}>
    <Text style={styles.secondaryText}>Retry account status</Text>
   </Pressable>
   {!onboarding?<View style={styles.card}>
    <Text style={styles.cardTitle}>Live beta status</Text>
    <Text style={styles.body}>Your Pup Account uses signed gameplay transactions and the same cloud save as Play. Keep your Play installation installed.</Text>
    <Text style={styles.body}>Live: Treats, XP, shop purchases, daily rewards, Gacha, and server-settled casino games. Unsupported redeem codes and restricted entitlement grants remain disabled.</Text>
    <Text style={styles.body}>If Android asks which app should receive Discord authorization, choose Puppy Clicker [Beta].</Text>
   </View>:null}
  </ScrollView>
  <Modal visible={Boolean(policy)} animationType="slide" onRequestClose={()=>setPolicy(null)}><View style={[styles.root,{paddingTop:insets.top+20,paddingBottom:insets.bottom+20,paddingHorizontal:20}]}><Text style={styles.title}>{policy==='terms'?'Terms of Use':'Privacy Policy'}</Text><ScrollView style={{flex:1}}><Text style={styles.body} selectable>{policy?legal[policy]:''}</Text></ScrollView><Pressable accessibilityRole="button" accessibilityLabel="Close policy" style={styles.button} onPress={()=>setPolicy(null)}><Text style={styles.buttonText}>Close</Text></Pressable></View></Modal>
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
 disabled:{opacity:0.5},
 modes:{flexDirection:'row',gap:8},mode:{flex:1,paddingVertical:12,paddingHorizontal:5,alignItems:'center',borderWidth:1,borderColor:'#5D8CA9',borderRadius:12},
 modeSelected:{backgroundColor:'#254C63',borderColor:'#87D6FF'},modeText:{fontSize:14,fontWeight:'800',color:'#B0E5FF'},
 modeSelectedText:{color:'#FFFFFF'},
 input:{color:'#FFFFFF',backgroundColor:'#152631',borderWidth:1,borderColor:'#5D8CA9',borderRadius:12,paddingHorizontal:14,minHeight:52,fontSize:16}
});
