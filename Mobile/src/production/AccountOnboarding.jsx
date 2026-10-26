import React,{useEffect,useState} from 'react';
import {ActivityIndicator,BackHandler,Image,KeyboardAvoidingView,Linking,Modal,Platform,Pressable,ScrollView,StatusBar,Switch,Text,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors as C,themedStyles} from '../theme.js';
import {validBirthday} from '../engine/onboarding.js';
import legal from '../../assets/legal/documents.json';

const titles=['Welcome','Set Up Your Account','Personalize','Permissions & Privacy','Notifications','Ready to Play'];
const months=['January','February','March','April','May','June','July','August','September','October','November','December'];
const maxDay=month=>[31,29,31,30,31,30,31,31,30,31,30,31][month-1]||31;
const T=({children,muted=false,style,...props})=><Text {...props} style={[styles.text,muted&&styles.muted,style]}>{children}</Text>;
function Button({children,onPress,disabled=false,secondary=false,label}){
 return <Pressable accessibilityRole="button" accessibilityLabel={label||children} disabled={disabled} onPress={onPress}
  style={[styles.button,secondary&&styles.secondary,disabled&&{opacity:0.45}]}><T style={[styles.buttonText,secondary&&{color:C.accentInk}]}>{children}</T></Pressable>;
}
function Card({title,children}){return <View style={styles.card}>{title?<T style={styles.cardTitle}>{title}</T>:null}{children}</View>;}
function Toggle({title,detail,value,onChange,disabled}){return <Card><View style={styles.row}><View style={{flex:1,gap:4}}><T style={styles.bold}>{title}</T><T muted style={styles.small}>{detail}</T></View><Switch accessibilityLabel={title} value={value} disabled={disabled} onValueChange={onChange}/></View></Card>;}
function Pair({label,value}){return <View style={styles.row}><T muted style={{flex:1}}>{label}</T><T selectable style={[styles.bold,{flexShrink:1,textAlign:'right'}]}>{value}</T></View>;}

export default function AccountOnboarding({flow,account,notifications,onConnect}){
 const {setup:g,ready,busy,problem,update,next,back,review,finish}=flow;
 const insets=useSafeAreaInsets(),step=g.setupStep;
 const [picker,setPicker]=useState(null),[birthdaySkipped,setBirthdaySkipped]=useState(false),[document,setDocument]=useState(null),[linkError,setLinkError]=useState('');
 const [notificationBusy,setNotificationBusy]=useState(false);
 const disabled=busy||notificationBusy;
 const connected=account?.status==='VERIFIED';
 useEffect(()=>{
  const listener=BackHandler.addEventListener('hardwareBackPress',()=>{
   if(picker){setPicker(null);return true;}
   if(document){setDocument(null);return true;}
   if(disabled)return true;
   if(step>0&&step<5){void back();return true;}
   return false;
  });
  return ()=>listener.remove();
 },[step,disabled,back,picker,document]);
 const set=(name,value)=>void update({[name]:value});
 const open=url=>{void Linking.openURL(url).catch(()=>setLinkError('Could not open this link. Please try again.'));};
 const wantsNotifications=g.dailyRewardNotifications||g.gameEventNotifications||g.updateNotifications;
 const needsPermission=wantsNotifications&&notifications.permission!=='granted';
 const notifyContinue=async()=>{
  if(disabled)return;
  setNotificationBusy(true);
  try{if(needsPermission)await notifications.request();await next();}
  finally{setNotificationBusy(false);}
 };
 const continueLabel=step===0?'Get Started':step===4&&needsPermission?'Enable Notifications':step===5?'Start Playing':'Continue';
 const continueAction=step===4?notifyContinue:step===5?finish:next;
 const legalLinks=<View style={styles.legal}><Pressable accessibilityRole="button" onPress={()=>setDocument('terms')}><T style={styles.link}>Terms of Use</T></Pressable><Pressable accessibilityRole="button" onPress={()=>setDocument('privacy')}><T style={styles.link}>Privacy Policy</T></Pressable></View>;
 if(!ready)return <View style={[styles.root,{paddingTop:insets.top+20}]}><ActivityIndicator color={C.accent}/><T>{problem||'Loading your setup…'}</T></View>;
 return <KeyboardAvoidingView behavior={Platform.OS==='ios'?'padding':undefined} style={[styles.root,{paddingTop:insets.top,paddingBottom:insets.bottom}]}>
  <StatusBar barStyle={C.dark?'light-content':'dark-content'} backgroundColor={C.bg}/>
  <View style={styles.shell}>
   <View style={styles.header}><T muted style={styles.small}>Step {step+1} of 6</T><T accessibilityRole="header" style={styles.title}>{titles[step]}</T>
    <View accessibilityLabel={'Setup step '+(step+1)+' of 6'} style={styles.progress}>{titles.map((title,i)=><View key={title} style={[styles.segment,{backgroundColor:i<=step?C.accent:C.line}]}/>)}</View>
   </View>
   <ScrollView style={{flex:1}} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.body}>
    {step===0?<View style={styles.welcome}>
     <Image source={require('../../assets/logos/puppy_clicker.png')} accessibilityLabel="Puppy Clicker logo" resizeMode="contain" style={{width:96,height:96}}/>
     <T style={[styles.title,{textAlign:'center'}]}>Welcome to Puppy Clicker</T><T muted style={[styles.small,{textAlign:'center'}]}>Tap, care for puppies, build your collection, and make the game yours.</T>
     <View style={styles.features}>
      <View style={styles.feature}><Image source={require('../../assets/logos/PupEye.png')} accessibilityLabel="PupEye fair-play protection" resizeMode="contain" style={styles.featureIcon}/><T style={styles.bold}>PupEye</T><T muted style={styles.featureBody}>Fair-play + save integrity.</T><T style={styles.link}>Protected</T></View>
      <Pressable accessibilityRole="button" accessibilityLabel="Open Discord community" onPress={()=>open('https://discord.gg/HcZweHchbv')} style={styles.feature}><Image source={require('../../assets/logos/discord.png')} accessibilityLabel="Discord" resizeMode="contain" style={[styles.featureIcon,{tintColor:C.text}]}/><T style={styles.bold}>Discord</T><T muted style={styles.featureBody}>Community, updates, and account connection.</T><T style={styles.link}>Open ›</T></Pressable>
      <Pressable accessibilityRole="button" accessibilityLabel="Open Roadmap" onPress={()=>open('https://trello.com/b/qwwa6bq7')} style={styles.feature}><T style={styles.featureGlyph}>PC</T><T style={styles.bold}>Roadmap</T><T muted style={styles.featureBody}>Planned features and work in progress.</T><T style={styles.link}>Trello ›</T></Pressable>
     </View>
     <T muted style={styles.small}>Powered by</T><Image source={require('../../assets/logos/harleys_studios.png')} accessibilityLabel="Harley's Studios" resizeMode="contain" style={{width:'52%',height:54}}/>
     {legalLinks}<T muted style={[styles.small,{textAlign:'center'}]}>Puppy Clicker is in active development. Features and balance may change.</T>
    </View>:null}
    {step===1?<>
     <T muted style={styles.small}>Create a Pup Account with a username, email and password, or sign in with Discord. Both use Supabase; PupEye binds the active installation. Discord is optional.</T>
     <Card><View style={styles.row}><Image source={require('../../assets/logos/discord.png')} accessibilityLabel="Discord" resizeMode="contain" style={[styles.featureIcon,{tintColor:C.text}]}/><View style={{flex:1}}><T style={styles.cardTitle}>Pup Account · T0</T><T muted style={styles.small}>{connected?'@'+account.username:'Email/password or Discord'}</T></View><T style={styles.badge}>{connected?'CONNECTED':'SIGN IN'}</T></View>
      <T muted style={styles.small}>{connected?"Your Pup Account is backed by Supabase cloud progression and protected by this device's PupEye key.":'Create an in-game Pup Account with email/password, or continue with Discord. Linking Discord later does not replace your account or username.'}</T>
      {connected?<><Button disabled={disabled} label="Continue with Pup Account" onPress={()=>void next()}>{busy?'Loading Pup Account…':'Continue with @'+account.username}</Button><Button secondary disabled={disabled} onPress={onConnect}>Manage Pup Account / Discord</Button></>:
       <Button disabled={disabled} onPress={onConnect}>Sign in or register Pup Account</Button>}
     </Card>
     <Card><View style={styles.row}><Image source={require('../../assets/logos/PupEye.png')} resizeMode="contain" style={styles.featureIcon}/><View style={{flex:1}}><T style={styles.bold}>Protected by PupEye</T><T muted style={styles.small}>Save integrity + fair-play protection.</T></View></View></Card>
    </>:null}
    {step===2?<>
     <T>Set up your appearance. Everything here can be changed later in Settings.</T>
     <Card title="Birthday"><T muted style={styles.small}>Optional · only the month and day are stored locally.</T>
      {!birthdaySkipped?<><View style={styles.row}><View style={{flex:1}}><Button secondary disabled={disabled} label="Birthday month" onPress={()=>setPicker('month')}>{months[g.birthdayMonth-1]||'Month'}</Button></View><View style={{flex:1}}><Button secondary disabled={disabled||!g.birthdayMonth} label="Birthday day" onPress={()=>setPicker('day')}>{g.birthdayDay?String(g.birthdayDay):'Day'}</Button></View></View>
       {!validBirthday(g.birthdayMonth,g.birthdayDay)?<T style={styles.errorText}>Choose both a month and day, or leave birthday blank.</T>:null}
       <Button secondary disabled={disabled} onPress={()=>{setBirthdaySkipped(true);void update({birthdayMonth:0,birthdayDay:0});}}>Skip birthday</Button></>:
       <><T>Birthday not set</T><Button secondary disabled={disabled} onPress={()=>setBirthdaySkipped(false)}>Add birthday instead</Button></>}
     </Card>
     <Card title="Appearance"><T muted style={styles.small}>Changes preview immediately.</T><T style={styles.bold}>Theme</T><View style={styles.choices}>{['system','light','dark'].map(mode=><Choice key={mode} label={mode[0].toUpperCase()+mode.slice(1)} selected={g.themeMode===mode} disabled={disabled} onPress={()=>set('themeMode',mode)}/>)}</View>
      <T style={styles.bold}>Accent</T><View style={styles.accents}>{['#00B8F0','#2979FF','#8B5CF6','#EC4899'].map(hex=><Pressable key={hex} accessibilityRole="button" accessibilityLabel={'Accent '+hex} accessibilityState={{selected:g.accentHex===hex}} disabled={disabled} onPress={()=>set('accentHex',hex)} style={[styles.accent,{backgroundColor:hex,borderColor:g.accentHex===hex?C.text:'transparent'}]}><T style={{color:'#FFF',fontWeight:'900'}}>{g.accentHex===hex?'✓':''}</T></Pressable>)}</View>
      <T style={styles.bold}>UI Scale</T><View style={styles.choices}>{['compact','default','large'].map(scale=><Choice key={scale} label={scale[0].toUpperCase()+scale.slice(1)} selected={g.uiScale===scale} disabled={disabled} onPress={()=>set('uiScale',scale)}/>)}</View>
      <View style={styles.row}><View style={{flex:1}}><T style={styles.bold}>Reduced Motion</T><T muted style={styles.small}>Minimize onboarding and app animations.</T></View><Switch accessibilityLabel="Reduced Motion" value={g.reducedMotion} disabled={disabled} onValueChange={value=>set('reducedMotion',value)}/></View>
     </Card>
    </>:null}
    {step===3?<>
     <T>Choose optional data sharing. Declining these options does not block Puppy Clicker.</T><T muted style={styles.small}>Anonymous diagnostics and crash reporting are OFF by default and can be changed later in Settings → Privacy & Data.</T>
     <Toggle title="Optional anonymous diagnostics" detail="Allow anonymous technical diagnostics for local troubleshooting. The support Discord webhook is not used for telemetry." value={g.anonymousDiagnosticsEnabled} disabled={disabled} onChange={v=>set('anonymousDiagnosticsEnabled',v)}/>
     <Toggle title="Optional crash reports" detail="Allow crash-report collection for troubleshooting. Remote delivery requires a separate crash destination." value={g.crashReportsEnabled} disabled={disabled} onChange={v=>set('crashReportsEnabled',v)}/>
     <Toggle title="Include account identity in support reports" detail="Optional. Support reports may include your username, public Player ID, Friend Code, and linked Discord identity. OAuth tokens, birthday, save data, and Treat balances are never included." value={g.supportIdentityEnabled} disabled={disabled} onChange={v=>set('supportIdentityEnabled',v)}/>
     <Card title="🔔 Notifications"><T muted style={styles.small}>Notification choices are configured on the next step. Android notification permission is requested only there, when needed.</T></Card>
     <Card title="🌐 Internet & network functionality"><T muted style={styles.small}>Network access supports streamed assets, feature flags, community links, and online features. INTERNET is an app capability, not a runtime consent prompt.</T></Card>
     <Card title="🛡️ PupEye fair-play diagnostics"><T muted style={styles.small}>PupEye device access and integrity checks are separate from optional reporting. Declining telemetry does not disable protection.</T></Card>{legalLinks}
    </>:null}
    {step===4?<>
     <T>Choose what Puppy Clicker may alert you about.</T><T muted style={styles.small}>You can change these choices later in Settings.</T>
     <Toggle title="Daily rewards" detail="Reminders when daily rewards are available." value={g.dailyRewardNotifications} disabled={disabled} onChange={v=>set('dailyRewardNotifications',v)}/>
     <Toggle title="Game events" detail="Seasonal and important in-game events." value={g.gameEventNotifications} disabled={disabled} onChange={v=>set('gameEventNotifications',v)}/>
     <Toggle title="App updates" detail="Important Puppy Clicker update notices." value={g.updateNotifications} disabled={disabled} onChange={v=>set('updateNotifications',v)}/>
     {needsPermission?<><Card title="Notification permission"><T muted style={styles.small}>Android will ask for permission after you continue. You can skip this and enable it later.</T></Card><Button secondary disabled={disabled} onPress={()=>void next()}>Not Now</Button></>:
      wantsNotifications?<T style={styles.bold}>✓ Notifications are ready.</T>:null}
    </>:null}
    {step===5?<>
     <T style={styles.cardTitle}>Your setup is ready.</T><T muted style={styles.small}>You can change profile, appearance, and notification options later in Settings.</T>
     <Card title="Player Profile"><Pair label="Username" value={account?.username||'Not connected'}/><Pair label="Player ID" value={account?.playerId||'Not available'}/><Pair label="Friend Code" value={account?.friendCode||'Not available'}/></Card>
     <Card title="Connected"><Pair label="Discord" value={account?.discordUserId?'Linked':'Not linked (optional)'}/><Pair label="PupEye" value={connected?'Device verified':'Verification required'}/>{g.birthdayMonth?<Pair label="Birthday" value={months[g.birthdayMonth-1]+' '+g.birthdayDay}/>:null}</Card>
     <Card title="Preferences"><Pair label="Theme" value={g.themeMode==='system'?'Follow System':g.themeMode==='light'?'Light':'Dark'}/><Pair label="UI scale" value={g.uiScale[0].toUpperCase()+g.uiScale.slice(1)}/></Card>
     <Button secondary disabled={disabled} onPress={()=>void review()}>Review Setup</Button>
    </>:null}
    {problem||linkError?<T accessibilityRole="alert" style={styles.errorText}>{problem||linkError}</T>:null}
    {disabled?<ActivityIndicator color={C.accent} accessibilityLabel="Checking Pup Account setup"/>:null}
   </ScrollView>
   <View style={styles.footer}>{step>0&&step<5?<View style={{flex:1}}><Button secondary disabled={disabled} onPress={()=>void back()}>Back</Button></View>:null}
    {step!==1?<View style={{flex:1}}><Button label={continueLabel} disabled={disabled||(step===2&&!validBirthday(g.birthdayMonth,g.birthdayDay))||(step===5&&!connected)} onPress={()=>void continueAction()}>{continueLabel}</Button></View>:null}
   </View>
  </View>
  <Modal visible={Boolean(picker)} transparent statusBarTranslucent navigationBarTranslucent onRequestClose={()=>setPicker(null)}>
   <View style={styles.overlay}><View style={styles.dialog}><T style={styles.cardTitle}>{picker==='month'?'Month':'Day'}</T><ScrollView>{Array.from({length:picker==='month'?12:maxDay(g.birthdayMonth)},(_,i)=>i+1).map(value=><Button key={value} secondary onPress={()=>{void update(picker==='month'?{birthdayMonth:value,birthdayDay:g.birthdayDay>maxDay(value)?0:g.birthdayDay}:{birthdayDay:value});setPicker(null);}}>{picker==='month'?months[value-1]:String(value)}</Button>)}</ScrollView><Button secondary onPress={()=>setPicker(null)}>Cancel</Button></View></View>
  </Modal>
  <Modal visible={Boolean(document)} statusBarTranslucent navigationBarTranslucent onRequestClose={()=>setDocument(null)}>
   <View style={[styles.root,{paddingTop:insets.top+12,paddingBottom:insets.bottom+12,paddingHorizontal:20}]}><T style={styles.title}>{document==='terms'?'Terms of Use':'Privacy Policy'}</T><ScrollView><T selectable>{document?legal[document]:''}</T></ScrollView><Button onPress={()=>setDocument(null)}>Close</Button></View>
  </Modal>
 </KeyboardAvoidingView>;
}
function Choice({label,selected,onPress,disabled}){return <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{selected}} disabled={disabled} onPress={onPress} style={[styles.choice,selected&&{backgroundColor:C.accentSoft,borderColor:C.accent}]}><T style={styles.small}>{label}</T></Pressable>;}
const styles=themedStyles(()=>({
 root:{flex:1,backgroundColor:C.bg},shell:{flex:1,width:'100%',maxWidth:560,alignSelf:'center'},header:{paddingHorizontal:20,paddingVertical:6,gap:4},
 title:{fontSize:24,fontWeight:'900',color:C.text},text:{fontSize:14,lineHeight:21,color:C.text},muted:{color:C.muted},small:{fontSize:12,lineHeight:18},bold:{fontWeight:'700'},
 progress:{flexDirection:'row',gap:6,marginVertical:6},segment:{height:4,flex:1,borderRadius:4},body:{paddingHorizontal:20,paddingVertical:4,paddingBottom:18,gap:10},
 card:{backgroundColor:C.card,borderRadius:16,borderWidth:1,borderColor:C.line,padding:14,gap:10},cardTitle:{fontSize:16,fontWeight:'900'},row:{flexDirection:'row',gap:10,alignItems:'center'},
 button:{minHeight:46,borderRadius:24,paddingHorizontal:16,paddingVertical:11,alignItems:'center',justifyContent:'center',backgroundColor:C.accent},buttonText:{fontSize:14,fontWeight:'700',color:C.onAccent,textAlign:'center'},
 secondary:{backgroundColor:'transparent',borderWidth:1,borderColor:C.line},footer:{paddingHorizontal:20,paddingVertical:6,borderTopWidth:1,borderColor:C.line,flexDirection:'row',gap:10},
 welcome:{alignItems:'center',gap:6},features:{flexDirection:'row',gap:8,marginVertical:8},feature:{flex:1,minHeight:154,paddingHorizontal:8,paddingVertical:9,borderRadius:16,backgroundColor:C.card,alignItems:'center',gap:4},featureIcon:{width:40,height:40},featureGlyph:{fontSize:25,fontWeight:'900',color:C.accentInk,minHeight:40},featureBody:{fontSize:11,lineHeight:16,textAlign:'center',flex:1},link:{color:C.accentInk,fontSize:12,fontWeight:'700'},
 legal:{flexDirection:'row',flexWrap:'wrap',justifyContent:'center',gap:20,paddingVertical:10},badge:{fontSize:10,fontWeight:'900',padding:6,borderRadius:6,backgroundColor:C.accentSoft,color:C.accentInk},
 choices:{flexDirection:'row',gap:7,flexWrap:'wrap'},choice:{paddingHorizontal:12,paddingVertical:7,borderRadius:10,borderWidth:1,borderColor:C.line},accents:{flexDirection:'row',justifyContent:'space-evenly'},accent:{width:42,height:42,borderRadius:21,borderWidth:3,alignItems:'center',justifyContent:'center'},
 errorText:{color:C.danger,fontSize:13,lineHeight:20},overlay:{flex:1,backgroundColor:'rgba(0,0,0,0.65)',justifyContent:'center',padding:24},dialog:{maxHeight:'80%',backgroundColor:C.bg,borderRadius:20,padding:18,gap:12}
}));
