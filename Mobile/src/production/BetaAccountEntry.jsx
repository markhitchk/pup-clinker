import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Image,Linking,Modal,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View,useWindowDimensions} from 'react-native';
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
 const {width:windowWidth}=useWindowDimensions();
 const desktop=windowWidth>=960;
 const [state,setState]=useState({status:'CHECKING'});
 const [busy,setBusy]=useState(false);
 const [problem,setProblem]=useState('');
 const [cloud,setCloud]=useState(null);
 const [mode,setMode]=useState(initialMode);
 const [username,setUsername]=useState('');
 const [email,setEmail]=useState('');
 const [login,setLogin]=useState('');
 const [password,setPassword]=useState('');
 const [confirmPassword,setConfirmPassword]=useState('');
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
  if(mode==='signup'&&!linkNative&&password!==confirmPassword){setProblem('Passwords do not match.');return;}
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
 const resetMode=next=>{setMode(next);setProblem('');setNotice('');setPassword('');setConfirmPassword('');};
 const openRecovery=()=>Linking.openURL('https://puppy-clicker.vercel.app/recovery').catch(()=>setProblem('Open puppy-clicker.vercel.app/recovery in your browser to recover your Pup Account.'));
 const provider=(title,detail,icon)=> <View style={styles.providerHeader}>
   <Image source={icon==='discord'?require('../../assets/logos/discord.png'):require('../../assets/logos/puppy_clicker.png')}
     resizeMode="contain" style={styles.providerIcon}/>
   <View style={{flex:1,minWidth:0}}><Text style={styles.providerTitle}>{title}</Text><Text style={styles.providerDetail}>{detail}</Text></View>
  </View>;
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
  <View style={[styles.root,{paddingTop:insets.top,paddingBottom:insets.bottom}]}>
   <StatusBar barStyle="light-content" backgroundColor="#0A0C12"/>
   <View style={[styles.layout,desktop&&styles.desktopLayout]}>
    {desktop?<View style={styles.intro}>
     <Text style={styles.backText} onPress={requiredAtStartup?undefined:close}>{requiredAtStartup?'Puppy Clicker [Beta]':'← Back to Puppy Clicker'}</Text>
     <View style={styles.introBody}>
      <Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain" style={{width:116,height:116}}/>
      <Text style={styles.eyebrow}>PUPPY CLICKER · PUP ACCOUNT</Text>
      <Text style={styles.introHeading}>{verified&&!linkNative?'Your Pup Account is connected.':mode==='signup'?'Your puppy journey starts here.':'Welcome back to your puppy world.'}</Text>
      <Text style={styles.introDescription}>One Pup Account for your puppies, rewards and linked progress across the website and Android.</Text>
      <Text style={styles.introBenefit}>🐾 Your puppy collection</Text>
      <Text style={styles.introBenefit}>🔒 Verified account protection</Text>
      <Text style={styles.introBenefit}>☁️ Linked cloud progress</Text>
     </View>
     <Text style={styles.introFooter}>Harley's Studios · Puppy Clicker</Text>
    </View>:null}
    <ScrollView style={styles.scroll} contentContainerStyle={[styles.content,desktop&&styles.desktopContent]} keyboardShouldPersistTaps="handled">
     {!desktop&&!requiredAtStartup?<Pressable accessibilityRole="button" onPress={close} style={styles.back}><Text style={styles.backText}>{onboarding?'← Back to account setup':'← Back to Puppy Clicker'}</Text></Pressable>:null}
     <View style={[styles.formPanel,desktop&&styles.desktopPanel]}>
      <Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain" style={styles.logoImage} accessibilityLabel="Puppy Clicker logo"/>
      <Text style={styles.step}>PUPPY CLICKER · SET UP YOUR ACCOUNT</Text>
      <Text accessibilityRole="header" style={styles.pageTitle}>🐶 Puppy Clicker · Pup Account</Text>
      {problem?<View style={styles.error}><Text accessibilityRole="alert" style={styles.body}>{problem}</Text></View>:null}
      {notice?<View style={styles.notice}><Text accessibilityRole="alert" style={styles.body}>{notice}</Text></View>:null}
      {busy?<ActivityIndicator color="#00B8F0" accessibilityLabel="Checking Discord and PupEye"/>:null}
      {verified&&!linkNative?<View style={styles.provider}>
       {provider('Pup Account','Verified identity and linked progress')}
       <Text style={styles.cardTitle}>Pup Account connected</Text>
       <Text style={styles.body}>Pup Account: @{state.username}</Text>
       <Text style={styles.body}>Discord: {state.guildRole?'Connected':'Optional'}</Text>
       {state.linkedFromPlay?<Text style={styles.body}>Connected to your original Pup Account. Signed progression, shop, rewards, and casino actions synchronize with Play. Your Play installation remains active.</Text>:null}
       {!onboarding&&!requiredAtStartup?<Text style={styles.body}>Cloud save: {state.cloudSaveExists?'Found':'Not found'}</Text>:null}
      </View>:null}
      {state.status==='DEVICE_TRANSFER_REQUIRED'?<Text style={styles.warning}>DEVICE TRANSFER REQUIRED · Contact support before connecting another installation.</Text>:null}
      {verified&&!consentNeeded&&onboarding&&!linkNative?<Pressable accessibilityRole="button" style={styles.button} onPress={close}><Text style={styles.buttonText}>Use this Pup Account</Text></Pressable>:null}
      {verified&&!consentNeeded&&!linkNative?<View style={styles.provider}>
       <Text style={styles.cardTitle}>Support contact (optional)</Text>
       <Text style={styles.body}>Used for manual support only, not sign-in or automatic password recovery.</Text>
       <TextInput accessibilityLabel="Support contact email" autoCapitalize="none" keyboardType="email-address" autoComplete="email" value={contactEmail} onChangeText={setContactEmail} placeholder="Optional support contact email" placeholderTextColor="#8AA1AF" style={styles.input}/>
       <Pressable accessibilityRole="button" accessibilityLabel="Save support contact" style={styles.secondary} disabled={busy} onPress={()=>void saveSupportContact()}><Text style={styles.secondaryText}>Save support contact</Text></Pressable>
       {!state.guildRole?<Pressable accessibilityRole="button" accessibilityLabel="Link Discord (optional)" style={styles.secondary} disabled={busy} onPress={()=>void signin()}><Text style={styles.secondaryText}>Link Discord (optional)</Text></Pressable>:null}
       <Pressable accessibilityRole="button" accessibilityLabel="Add Pup Account password login" style={styles.secondary} disabled={busy}
        onPress={()=>{setUsername(state.username||'');setLinkNative(true);resetMode('signup');}}>
        <Text style={styles.secondaryText}>Add username/password sign-in to this Pup Account</Text>
       </Pressable>
      </View>:null}
      {consentNeeded?<>{agreements}<Pressable accessibilityRole="button" accessibilityLabel="Accept both policies" disabled={!tos||!privacy||busy} style={styles.button} onPress={()=>void consent()}><Text style={styles.buttonText}>Accept and continue</Text></Pressable></>:null}
      {(!verified||linkNative)?<>
       {!linkNative?<View style={styles.modes} accessibilityRole="tablist">
        {[['signin','Sign In'],['signup','Create Account']].map(([id,label])=><Pressable key={id}
         accessibilityRole="tab" accessibilityState={{selected:mode===id}} accessibilityLabel={label}
         disabled={busy} onPress={()=>resetMode(id)}
         style={[styles.mode,mode===id&&styles.modeSelected]}>
         <Text style={[styles.modeText,mode===id&&styles.modeSelectedText]}>{label}</Text>
        </Pressable>)}
       </View>:<Pressable accessibilityRole="button" onPress={()=>{setLinkNative(false);resetMode('signin');}} style={styles.secondary}>
        <Text style={styles.secondaryText}>← Back to your Pup Account</Text>
       </Pressable>}
       {mode==='signin'?<>
        <Text style={styles.description}>Sign in with your existing Pup Account. Your puppies and progress stay linked across devices.</Text>
        <View style={styles.provider}>
         {provider('Pup Account','Username and password sign-in')}
         <Text style={styles.body}>Sign in using the same Pup Account you use on the website. Your linked progress is preserved.</Text>
         <Text style={styles.label}>Pup username</Text>
         <TextInput accessibilityLabel="Pup username" autoCapitalize="none" autoCorrect={false} autoComplete="username"
          placeholder="Pup Account username" placeholderTextColor="#8AA1AF" value={login} onChangeText={setLogin} style={styles.input}/>
         <Text style={styles.label}>Password</Text>
         <TextInput accessibilityLabel="Pup password" secureTextEntry autoCapitalize="none" autoCorrect={false} autoComplete="current-password"
          placeholder="Password" placeholderTextColor="#8AA1AF" value={password} onChangeText={setPassword} style={styles.input}/>
         <Pressable accessibilityRole="button" accessibilityLabel="Sign in with Pup Account" disabled={busy}
          style={[styles.button,busy&&styles.disabled]} onPress={()=>void submit()}>
          <Text style={styles.buttonText}>{busy?'Verifying…':'Sign in to Pup Account'}</Text>
         </Pressable>
        </View>
        <View style={styles.provider}>
         {provider('Discord','Alternative login or optional linked account','discord')}
         <Text style={styles.body}>Sign in with the Discord identity linked to your Pup Account. New Discord users can register.</Text>
         <Pressable accessibilityRole="button" accessibilityLabel="Connect with Discord" disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void signin()}>
          <View style={styles.discordButton}><Image source={require('../../assets/logos/discord.png')} resizeMode="contain" style={styles.discordSmall}/>
           <Text style={styles.buttonText}>Sign in with Discord</Text></View>
         </Pressable>
        </View>
        <Pressable accessibilityRole="link" accessibilityLabel="Recover Pup Account" onPress={openRecovery} style={styles.recovery}>
         <Text style={styles.secondaryText}>Forgot your username or password? Recover account</Text>
        </Pressable>
        <Text style={styles.footnote}>Pup Account verification protects your identity and cloud progress on web and Android.</Text>
       </>:<>
        <Text style={styles.description}>{linkNative?'Add a password to your existing Pup Account without replacing your puppies, Treats or XP.':'Create Pup Account · Choose a username and password, or continue with Discord. Both legal policies require acceptance.'}</Text>
        {!linkNative?<View style={styles.provider}>
         {provider('Discord','Alternative account registration or sign-in','discord')}
         <Text style={styles.body}>Register using your Discord identity and optionally import your verified Discord email as a support contact.</Text>
         <Pressable accessibilityRole="button" accessibilityLabel="Register with Discord" disabled={busy} style={[styles.secondary,busy&&styles.disabled]} onPress={()=>void signin()}>
          <View style={styles.discordButton}><Image source={require('../../assets/logos/discord.png')} resizeMode="contain" style={styles.discordSmall}/>
           <Text style={styles.secondaryText}>Register with Discord</Text></View>
         </Pressable>
        </View>:null}
        <View style={styles.provider}>
         {provider('Pup Account','Username and password registration')}
         <Text style={styles.body}>{linkNative?'Link sign-in to your existing Discord Pup Account @'+state.username+'. Your puppies, Treats, and XP stay on the same account.':'Create a new Pup Account to play on Android and the website. Already have an account? Choose Sign In.'}</Text>
         <Text style={styles.label}>Pup username</Text>
         <TextInput accessibilityLabel="Pup username" autoComplete="username" autoCapitalize="none" autoCorrect={false}
          placeholder="3–20 letters, numbers, _ or -" placeholderTextColor="#8AA1AF" maxLength={20}
          editable={!linkNative} value={username} onChangeText={setUsername} style={styles.input}/>
         <Text style={styles.label}>Password</Text>
         <TextInput accessibilityLabel="Pup password" secureTextEntry autoCapitalize="none" autoCorrect={false} autoComplete="new-password"
          placeholder="8–128 characters" maxLength={128} placeholderTextColor="#8AA1AF"
          value={password} onChangeText={setPassword} style={styles.input}/>
         <Text style={styles.footnote}>Use a strong password suggested by Google Password Manager, Samsung Pass, Bitwarden, or another password manager.</Text>
         <Text style={styles.label}>Confirm password</Text>
         <TextInput accessibilityLabel="Confirm password" secureTextEntry autoCapitalize="none" autoCorrect={false} autoComplete="new-password"
          maxLength={128} placeholder="Re-enter password" placeholderTextColor="#8AA1AF" value={confirmPassword}
          onChangeText={setConfirmPassword} style={styles.input}/>
         <Text style={styles.label}>Support contact email (optional)</Text>
         <TextInput accessibilityLabel="Pup email" autoCapitalize="none" autoCorrect={false} keyboardType="email-address"
          autoComplete="email" placeholder="Not used for sign-in or verification"
          placeholderTextColor="#8AA1AF" maxLength={254} value={email} onChangeText={setEmail} style={styles.input}/>
         <Text style={styles.footnote}>Contact email is only for manual support, not automatic password recovery.</Text>
         {agreements}
         <Pressable accessibilityRole="button" accessibilityLabel={linkNative?'Add password to Pup Account':'Create Pup Account'}
          disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void submit()}>
          <Text style={styles.buttonText}>{busy?'Creating account…':linkNative?'Add password to Pup Account':'Create Pup Account'}</Text>
         </Pressable>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="I already use Discord" onPress={()=>resetMode('signin')} style={styles.recovery}>
         <Text style={styles.secondaryText}>Already have an account? Sign In</Text>
        </Pressable>
       </>}
      </>:null}
      {verified&&!consentNeeded&&!linkNative&&!onboarding&&!requiredAtStartup?<Pressable accessibilityRole="button" style={styles.secondary} disabled={busy} onPress={()=>void read()}><Text style={styles.secondaryText}>Verify cloud save</Text></Pressable>:null}
      {cloud?<Text style={styles.body}>Cloud save: {cloud.exists?'Available at revision '+cloud.revision:'No linked save found'}</Text>:null}
      <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={()=>void check()}><Text style={styles.secondaryText}>Retry account status</Text></Pressable>
      {!onboarding&&verified?<Text style={styles.footnote}>Your original Play installation and protected cloud save remain unchanged.</Text>:null}
     </View>
    </ScrollView>
   </View>
   <Modal visible={Boolean(policy)} animationType="slide" onRequestClose={()=>setPolicy(null)}>
    <View style={[styles.root,{paddingTop:insets.top+20,paddingBottom:insets.bottom+20,paddingHorizontal:20}]}>
     <Text style={styles.pageTitle}>{policy==='terms'?'Terms of Use':'Privacy Policy'}</Text>
     <ScrollView style={{flex:1}}><Text style={styles.body} selectable>{policy?legal[policy]:''}</Text></ScrollView>
     <Pressable accessibilityRole="button" accessibilityLabel="Close policy" style={styles.button} onPress={()=>setPolicy(null)}><Text style={styles.buttonText}>Close</Text></Pressable>
    </View>
   </Modal>
  </View>
 </Modal>;
}
const styles=StyleSheet.create({
 root:{flex:1,backgroundColor:'#0A0C12'},
 layout:{flex:1,backgroundColor:'#0A0C12'},
 desktopLayout:{flexDirection:'row'},
 intro:{flex:0.86,minWidth:280,backgroundColor:'#131B2C',padding:36,
  borderRightWidth:1,borderRightColor:'rgba(0,229,255,0.14)',justifyContent:'space-between'},
 introBody:{gap:14},
 introHeading:{fontSize:42,lineHeight:47,fontWeight:'900',color:'#F3FBFF',letterSpacing:-1},
 introDescription:{color:'#B2C9D7',fontSize:16,lineHeight:27,marginBottom:14},
 introBenefit:{color:'#C4E7EF',fontSize:14,fontWeight:'700'},
 introFooter:{color:'#8FA2B7',fontSize:12},
 scroll:{flex:1,minWidth:0},
 content:{paddingVertical:14,paddingHorizontal:10,alignItems:'center',flexGrow:1},
 desktopContent:{padding:34,alignItems:'center'},
 formPanel:{width:'100%',maxWidth:530,padding:16,gap:15,backgroundColor:'#12151F',
  borderRadius:18,borderWidth:1,borderColor:'rgba(0,229,255,0.22)'},
 desktopPanel:{maxWidth:800,padding:36,backgroundColor:'transparent',borderWidth:0,gap:18},
 logoImage:{width:110,height:85,alignSelf:'center'},
 step:{color:'#00E5FF',fontSize:11,fontWeight:'900',textAlign:'center',letterSpacing:1},
 pageTitle:{fontSize:26,fontWeight:'900',color:'#FFFFFF',textAlign:'center'},
 body:{color:'#BCD1DF',fontSize:14,lineHeight:22},
 description:{color:'#BCD1DF',fontSize:14,lineHeight:23,textAlign:'center'},
 label:{fontSize:13,fontWeight:'800',color:'#E9FAFF'},
 cardTitle:{fontSize:20,fontWeight:'900',color:'#FFFFFF'},
 provider:{padding:14,backgroundColor:'#161A25',borderRadius:16,
  borderWidth:1,borderColor:'rgba(0,229,255,0.23)',gap:12},
 providerHeader:{flexDirection:'row',alignItems:'center',gap:12},
 providerIcon:{width:42,height:42},
 providerTitle:{color:'#F6FCFF',fontSize:16,fontWeight:'900'},
 providerDetail:{color:'#ABBFCC',fontSize:12},
 modes:{flexDirection:'row',gap:5,backgroundColor:'#0D1E29',borderRadius:14,padding:4},
 mode:{flex:1,borderRadius:11,paddingVertical:13,paddingHorizontal:8,alignItems:'center'},
 modeSelected:{backgroundColor:'#00B8F0'},
 modeText:{color:'#BCD1DF',fontSize:14,fontWeight:'900'},
 modeSelectedText:{color:'#001622'},
 input:{backgroundColor:'#12151F',borderWidth:1,borderColor:'#476579',borderRadius:11,
  color:'#F3FBFF',fontSize:16,minHeight:50,paddingHorizontal:12},
 button:{minHeight:50,backgroundColor:'#00B8F0',borderRadius:13,alignItems:'center',
  justifyContent:'center',padding:12},
 buttonText:{color:'#001622',fontWeight:'900',fontSize:15},
 disabled:{opacity:0.55},
 secondary:{minHeight:46,justifyContent:'center',alignItems:'center',padding:12,
  borderRadius:14,borderWidth:1,borderColor:'#476579'},
 secondaryText:{color:'#8CE9FF',fontSize:14,fontWeight:'800',textAlign:'center'},
 discordButton:{flexDirection:'row',alignItems:'center',justifyContent:'center',gap:10},
 discordSmall:{width:24,height:24},
 recovery:{minHeight:42,alignItems:'center',justifyContent:'center',padding:8},
 footnote:{color:'#ADC1CE',fontSize:12,lineHeight:19},
 back:{alignSelf:'flex-start',padding:10,marginBottom:6},
 backText:{color:'#9BEEFF',fontWeight:'800',fontSize:14},
 error:{backgroundColor:'#38282D',borderColor:'#CB7777',borderWidth:1,padding:14,borderRadius:13},
 notice:{backgroundColor:'#173B41',borderColor:'#00B8F0',borderWidth:1,padding:14,borderRadius:13},
 warning:{color:'#FFB4AD',fontSize:13,fontWeight:'900',lineHeight:19}
});
