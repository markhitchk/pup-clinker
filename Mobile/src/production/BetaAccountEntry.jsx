import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Image,Linking,Modal,NativeModules,Pressable,ScrollView,StatusBar,StyleSheet,Text,TextInput,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import legal from '../../assets/legal/documents.json';
import {isWebDiscordCallback} from './webDiscordCallback.js';
import {supportsDatabaseLayout} from './dbSchemaLayout.js';
import {usePupAuthForm,registrationError,loginError,passwordRules,JOIN_SOURCE_OPTIONS} from '../shared/usePupAuthForm.js';
import {WEBSITE_ACCOUNT_COPY} from './websiteAccountContract.js';

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
 const {mode,setMode,chooseMode,username,setUsername,login,setLogin,password,setPassword,confirm,setConfirm,
  contactEmail:email,setContactEmail:setEmail,joinSource,setJoinSource,joinDetails,setJoinDetails,
  terms:tos,setTerms:setTos,privacy,setPrivacy,clearSecrets}=usePupAuthForm(initialMode);
 const [notice,setNotice]=useState('');
 const [linkNative,setLinkNative]=useState(false);
 const [policy,setPolicy]=useState(null);
 const [contactEmail,setContactEmail]=useState('');
 useEffect(()=>{if(visible){setMode(initialMode);setLinkNative(false);setNotice('');setProblem('');setTos(false);setPrivacy(false);}},[visible,initialMode]);
 const close=useCallback(()=>{clearSecrets();setNotice('');setProblem('');onClose?.();},[onClose]);
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
  if(mode==='signup'){
   const invalid=registrationError({username,password,confirm,contactEmail:email,terms:tos,privacy});
   if(invalid){setProblem(invalid);return;}
  }
  if(linkNative&&mode==='signup'&&state.status==='VERIFIED'&&username.trim().toLowerCase()!==String(state.username||'').toLowerCase()){
   setProblem('Keep your existing Pup username when adding password sign-in.');return;
  }
  const missing=mode!=='signup'?loginError(login,password):'';
  if(missing){setProblem(missing);return;}
  setBusy(true);setProblem('');setNotice('');
  try{
   const result=mode==='signup'
    ?await api().createPupAccount(username.trim(),email.trim(),password,tos,privacy,
      joinSource,joinSource?joinDetails.trim():'',publishableKey)
    :await api().signInPupAccount(login.trim(),password,publishableKey);
   clearSecrets();
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
    PUP_PASSWORD_COMPLEXITY_REQUIRED:'Use 8–128 characters with uppercase and lowercase letters, a number, and a special character.',
    PUP_JOIN_SOURCE_INVALID:'Choose a valid optional discovery source or leave it blank.',
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
 },[busy,mode,username,email,login,password,confirm,tos,privacy,joinSource,joinDetails,onVerified,linkNative]);
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
   <View style={{alignItems:'center',gap:9}}>
    <Image source={require('../../assets/logos/puppy_clicker.png')} accessibilityLabel="Puppy Clicker logo" resizeMode="contain" style={{width:88,height:88}}/>
    <Text style={styles.logo}>{WEBSITE_ACCOUNT_COPY.eyebrow}</Text>
   </View>
   <Text style={styles.title}>{verified?'Pup Account connected':mode==='signup'?WEBSITE_ACCOUNT_COPY.registerTitle:WEBSITE_ACCOUNT_COPY.signinTitle}</Text>
   <Text style={[styles.body,{textAlign:'center'}]}>{WEBSITE_ACCOUNT_COPY.intro}</Text>
   <View style={styles.benefits} accessibilityLabel="Pup Account benefits">
    {WEBSITE_ACCOUNT_COPY.benefits.map(benefit=><View key={benefit} style={styles.benefit}>
     <Text style={styles.benefitText}>{benefit}</Text>
    </View>)}
   </View>
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
     {['signin','signup'].map(item=><Pressable key={item} accessibilityRole="tab" accessibilityState={{selected:mode===item}}
      accessibilityLabel={item==='signin'?'Sign In':'Create Account'}
      onPress={()=>{chooseMode(item);setProblem('');setNotice('');}}
      style={[styles.mode,(mode===item||(mode==='discord'&&item==='signin'))&&styles.modeSelected]}>
      <Text style={[styles.modeText,(mode===item||(mode==='discord'&&item==='signin'))&&styles.modeSelectedText]}>{item==='signin'?'Sign In':'Create Account'}</Text>
     </Pressable>)}
    </View>:<Pressable accessibilityRole="button" style={styles.secondary} onPress={()=>{setLinkNative(false);setPassword('');setProblem('');}}>
     <Text style={styles.secondaryText}>← Back to your Pup Account</Text>
    </Pressable>}
    {mode!=='discord'?<View style={styles.card}>
     <Text style={styles.cardTitle}>{linkNative?'Add username/password sign-in':mode==='signup'?'Create a Pup Account':'Sign in to your Pup Account'}</Text>
     {linkNative?<Text style={styles.body}>Link sign-in to your existing Discord Pup Account @{state.username}. Your puppies, Treats, and XP stay on the same account.</Text>:null}
     {mode==='signup'?<>
      <Text style={styles.label}>USERNAME</Text>
      <TextInput accessibilityLabel="Pup username" autoCapitalize="none" autoCorrect={false} autoComplete="username"
       placeholder="3–20 letters, numbers, _ or -" placeholderTextColor="#8AA1AF"
       maxLength={20} editable={!linkNative} value={username} onChangeText={setUsername} style={styles.input}/>
      {!linkNative?<Pressable accessibilityRole="button" accessibilityLabel="Generate username"
       disabled={busy} onPress={()=>void api().authSuggestion('username')
        .then(value=>{if(typeof value==='string'){setUsername(value);setProblem('');}})
        .catch(()=>setProblem('Secure username suggestion is unavailable.'))}>
       <Text style={styles.secondaryText}>🎲 Generate username</Text>
      </Pressable>:null}
      <Text style={styles.label}>SUPPORT CONTACT EMAIL (OPTIONAL)</Text>
      <TextInput accessibilityLabel="Pup email" autoCapitalize="none" autoCorrect={false}
       keyboardType="email-address" autoComplete="email" placeholder="Not used for sign-in or verification"
       placeholderTextColor="#8AA1AF" value={email} onChangeText={setEmail} style={styles.input}/>
     </>:<>
      <Text style={styles.label}>PUP USERNAME</Text>
      <TextInput accessibilityLabel="Pup username" autoCapitalize="none" autoCorrect={false} autoComplete="username"
       placeholder="Pup username" placeholderTextColor="#8AA1AF"
       value={login} onChangeText={setLogin} style={styles.input}/>
     </>}
     <Text style={styles.label}>{mode==='signup'?'NEW PASSWORD':'PASSWORD'}</Text>
     <TextInput accessibilityLabel="Pup password" secureTextEntry autoCapitalize="none"
      autoCorrect={false} autoComplete={mode==='signup'?'new-password':'current-password'}
      placeholder={mode==='signup'?'At least 8 characters':'Password'} placeholderTextColor="#8AA1AF"
      value={password} onChangeText={setPassword} style={styles.input}/>
     {mode==='signup'?<Pressable accessibilityRole="button" accessibilityLabel="Generate strong password"
      disabled={busy} onPress={()=>void api().authSuggestion('password')
       .then(value=>{if(typeof value==='string'){setPassword(value);setConfirm(value);
        setNotice('Strong password generated. Save it in your password manager before registering.');}})
       .catch(()=>setProblem('Secure password generation is unavailable.'))}>
      <Text style={styles.secondaryText}>🔐 Generate strong password</Text>
     </Pressable>:null}
     {mode==='signup'?<>
      <Text style={styles.body}>Choose a strong password or use Android's password manager suggestion.</Text>
      <View style={{gap:4}}>{passwordRules(password).map(rule=><Text key={rule.label}
       style={{color:rule.passed?'#83E7B5':'#BBD0DF',fontSize:12}}>
       {rule.passed?'✓':'○'} {rule.label}</Text>)}</View>
      <Text style={styles.label}>CONFIRM PASSWORD</Text>
      <TextInput accessibilityLabel="Confirm password" secureTextEntry
       autoComplete="new-password" autoCapitalize="none" autoCorrect={false}
       placeholder="Re-enter password" placeholderTextColor="#8AA1AF"
       maxLength={128} value={confirm} onChangeText={setConfirm} style={styles.input}/>
     </>:null}
     {mode==='signup'&&!linkNative?<View style={{gap:8}}>
      <Text style={styles.label}>HOW DID YOU HEAR ABOUT PUPPY CLICKER? (OPTIONAL)</Text>
      <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>
       {JOIN_SOURCE_OPTIONS.map(([value,label])=><Pressable key={value} accessibilityRole="button"
        accessibilityLabel={label} accessibilityState={{selected:joinSource===value}}
        onPress={()=>{setJoinSource(joinSource===value?'':value);setJoinDetails('');}}
        style={[styles.mode,{flexGrow:0,paddingHorizontal:10,paddingVertical:9},joinSource===value&&styles.modeSelected]}>
        <Text style={[styles.modeText,{fontSize:12},joinSource===value&&styles.modeSelectedText]}>{label}</Text>
       </Pressable>)}
      </View>
      {joinSource?<><Text style={styles.label}>{joinSource==='friend'?'WHO INVITED YOU? (OPTIONAL)':'WHERE DID YOU SEE US? (OPTIONAL)'}</Text>
       <TextInput accessibilityLabel="Discovery source details" maxLength={120}
        placeholder={joinSource==='friend'?'Pup username or name':'Server, channel, creator or website'}
        placeholderTextColor="#8AA1AF" value={joinDetails} onChangeText={setJoinDetails} style={styles.input}/></>:null}
      <Text style={styles.body}>Optional feedback only. No invite code or reward is required.</Text>
     </View>:null}
     {mode==='signup'?agreements:null}
     <Pressable accessibilityRole="button" accessibilityLabel={linkNative?'Add password to Pup Account':mode==='signup'?'Create Pup Account':'Sign in with Pup Account'}
      disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void submit()}>
      <Text style={styles.buttonText}>{busy?'Verifying…':linkNative?'Add password to Pup Account':mode==='signup'?'Create Pup Account':'Sign in to Pup Account'}</Text>
     </Pressable>
     {mode==='signup'?<Text style={styles.body}>No verification email or SMTP is required. Contact email is optional and used only for support; password is handled by Supabase Auth.</Text>:null}
     {mode==='signup'&&!linkNative?<Pressable accessibilityRole="button" accessibilityLabel="I already use Discord" onPress={()=>{setMode('discord');setProblem('');setPassword('');}}>
      <Text style={styles.secondaryText}>Already use Discord? Sign in with Discord first, then add a password to your existing account.</Text>
     </Pressable>:null}
    </View>:<Pressable accessibilityRole="button" accessibilityLabel="Connect Discord to Pup Account"
     disabled={busy} style={[styles.button,busy&&styles.disabled]} onPress={()=>void signin()}>
     <Text style={styles.buttonText}>Connect with Discord</Text>
    </Pressable>}
    {mode!=='discord'&&!linkNative?<View style={styles.card}>
     <View style={{flexDirection:'row',alignItems:'center',gap:10}}>
      <Image source={require('../../assets/logos/discord.png')} resizeMode="contain" style={{width:30,height:30}}/>
      <View style={{flex:1}}><Text style={styles.cardTitle}>Discord</Text>
       <Text style={styles.body}>Alternative login or optional linked account</Text></View>
     </View>
     <Text style={styles.body}>Sign in with the Discord identity linked to your Pup Account.</Text>
     <Pressable accessibilityRole="button" accessibilityLabel="Sign in with Discord"
      disabled={busy} style={styles.button} onPress={()=>void signin()}>
      <Text style={styles.buttonText}>Sign in with Discord</Text>
     </Pressable>
    </View>:null}
    <Pressable accessibilityRole="button" accessibilityLabel="Recover Pup Account"
     style={styles.secondary} onPress={()=>void Linking.openURL('https://puppy-clicker.vercel.app/recovery')}>
     <Text style={styles.secondaryText}>Forgot your username or password? Recover account</Text>
    </Pressable>
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
 root:{flex:1,backgroundColor:'#0A0C12'},
 content:{padding:22,gap:18,maxWidth:600,width:'100%',alignSelf:'center'},
 benefits:{width:'100%',flexDirection:'row',flexWrap:'wrap',alignItems:'center',justifyContent:'center',gap:7,marginVertical:2},
 benefit:{backgroundColor:'rgba(0,229,255,0.08)',borderWidth:1,borderColor:'rgba(0,229,255,0.25)',borderRadius:999,paddingHorizontal:11,paddingVertical:7},
 benefitText:{color:'#D9F5FF',fontSize:11,fontWeight:'700'},
 logo:{fontWeight:'900',fontSize:12,color:'#00E5FF',letterSpacing:1.2},
 title:{color:'#FFF',fontSize:29,fontWeight:'900'},
 body:{color:'#D0DEE8',fontSize:15,lineHeight:24},
 card:{borderRadius:22,borderWidth:1,borderColor:'#2D5364',padding:18,gap:12,backgroundColor:'#12151F'},
 label:{color:'#8DD6FF',fontSize:13,fontWeight:'900',letterSpacing:1},
 value:{color:'#FFF',fontWeight:'900',fontSize:20},
 cardTitle:{color:'#FFF',fontSize:19,fontWeight:'800'},
 error:{borderWidth:1,borderColor:'#CB7777',padding:16,borderRadius:15,backgroundColor:'#38282D'},
 button:{backgroundColor:'#00E5FF',padding:18,borderRadius:24,alignItems:'center'},
 buttonText:{color:'#0B1C29',fontSize:17,fontWeight:'900'},
 secondary:{borderRadius:14,borderWidth:1,borderColor:'#5D8CA9',padding:14,alignItems:'center'},
 secondaryText:{color:'#B0E5FF',fontWeight:'800'},
 disabled:{opacity:0.5},
 modes:{flexDirection:'row',gap:8},mode:{flex:1,paddingVertical:12,paddingHorizontal:5,alignItems:'center',borderWidth:1,borderColor:'#5D8CA9',borderRadius:12},
 modeSelected:{backgroundColor:'#183C4D',borderColor:'#00E5FF'},modeText:{fontSize:14,fontWeight:'800',color:'#B0E5FF'},
 modeSelectedText:{color:'#FFFFFF'},
 input:{color:'#FFFFFF',backgroundColor:'#152631',borderWidth:1,borderColor:'#5D8CA9',borderRadius:12,paddingHorizontal:14,minHeight:52,fontSize:16}
});
