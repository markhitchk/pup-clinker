import React,{useEffect,useState} from 'react';
import DangerHoldDialog from './DangerHoldDialog.jsx';
import {Alert,BackHandler,Image,Linking,Pressable,ScrollView,Share,Switch,Text,TextInput,Vibration,View} from 'react-native';
import {colors as C,themedStyles} from '../theme.js';
import {ACCENT_PRESETS} from '../engine/settings.js';
import {guestLevel} from '../engine/progression.js';
import {PUPPY_IMAGES} from '../assets/puppies.js';
import {validBirthday} from '../engine/onboarding.js';

const T=({children,muted=false,style,...props})=><Text {...props} style={[s.text,muted&&s.muted,style]}>{children}</Text>;
function Action({children,onPress,disabled=false,danger=false}){return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={({pressed})=>[s.action,disabled&&{opacity:.45},pressed&&{opacity:.7},danger&&{borderColor:C.danger}]}><T style={{fontWeight:'700',color:danger?C.danger:C.accentInk,textAlign:'center'}}>{children}</T></Pressable>;}
function Toggle({title,detail,value,onChange,disabled=false}){return <View style={s.toggle}><View style={{flex:1,gap:3}}><T style={s.bold}>{title}</T>{detail?<T muted style={s.small}>{detail}</T>:null}</View><Switch accessibilityLabel={title} disabled={disabled} value={value===true} onValueChange={onChange} trackColor={{false:C.line,true:C.accent}} thumbColor={C.surface}/></View>;}
function Choices({label,value,items,onChange}){return <View style={{gap:6}}><T style={s.bold}>{label}</T><View style={s.choices}>{items.map(([id,title])=><Pressable key={id} accessibilityRole="radio" accessibilityState={{checked:value===id}} onPress={()=>onChange(id)} style={[s.choice,value===id&&s.selected]}><T style={{color:value===id?C.accentInk:C.text}}>{title}</T></Pressable>)}</View></View>;}
export default function SettingsScreen({g,act,onOpen,registry,notifications,diagnostics,betaMode=false,onConnectAccount,onUpdateAccountName,onUpdateBirthday,cloudState,syncStatus,onRefreshAccount}){
 const [expanded,setExpanded]=useState(null),[danger,setDanger]=useState(false),[dangerAction,setDangerAction]=useState(null);
 const [name,setName]=useState(g.guestName),[puppy,setPuppy]=useState(g.puppyName);
 const [profileBusy,setProfileBusy]=useState(false),[profileMessage,setProfileMessage]=useState('');
 const [month,setMonth]=useState(String(g.birthdayMonth||'')),[day,setDay]=useState(String(g.birthdayDay||''));
 const [hex,setHex]=useState(g.accentHex),[custom,setCustom]=useState(false),[report,setReport]=useState(''),[category,setCategory]=useState('Bug');
 useEffect(()=>setHex(g.accentHex),[g.accentHex]);
 useEffect(()=>{if(!profileBusy)setName(g.guestName);},[g.guestName,profileBusy]);
 useEffect(()=>{const sub=BackHandler.addEventListener('hardwareBackPress',()=>{if(!expanded)return false;setExpanded(null);return true;});return ()=>sub.remove();},[expanded]);
 const pref=(name,value)=>act({type:'setPreference',name,value});
 const toggle=(name,title,detail)=><Toggle title={title} detail={detail} value={g[name]} onChange={v=>pref(name,v)}/>;
 const section=(id,icon,title,subtitle,children)=><View key={id} style={s.section}>
  <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{expanded:expanded===id}} onPress={()=>{setExpanded(expanded===id?null:id);setDanger(false);}} style={s.sectionHead}>
   <View style={s.iconBox}><T style={s.icon}>{icon}</T></View><View style={{flex:1,gap:3}}><T style={s.sectionTitle}>{title}</T><T muted style={s.subtitle}>{subtitle}</T></View><T style={s.chevron}>{expanded===id?'⌃':'›'}</T>
  </Pressable>{expanded===id?<View style={s.body}>{children}</View>:null}
 </View>;
 const group=title=><T style={s.group}>{title}</T>;
 const pending=<T muted>{betaMode?'Live gameplay is signed and saved to Pup Account. This specific feature is not yet available until its server-side implementation is verified.':'Available after the original linked account and protected services are migrated. This preview has local guest progress.'}</T>;
 return <View style={s.page}>
  <View><T style={s.title}>Settings</T><T muted style={s.description}>Everything in one place. Tap a card to expand its settings.</T></View>
  <View style={s.identity}>
   <Image source={PUPPY_IMAGES[g.puppyStyle]||PUPPY_IMAGES.classic} resizeMode="contain" style={s.avatar}/>
   <View style={{flex:1,gap:8}}><T numberOfLines={1} style={s.identityName}>{g.guestName}</T><View style={s.choices}><T style={s.badge}>Lv {guestLevel(g)}</T><T style={s.badge}>{g.xp} XP</T></View></View>
  </View>
  {group('ACCOUNT')}
  {section('profile','👤','Profile',g.guestName+' · Level '+guestLevel(g),<>
   <T muted>{betaMode?'Pup Account · Signed gameplay saves directly to Supabase and syncs between verified installations.':'Guest · Level '+guestLevel(g)+' · Saved on this device'}</T><T style={s.bold}>Display name</T><TextInput accessibilityLabel="Display name" value={name} onChangeText={setName} maxLength={24} style={s.input}/><Action disabled={profileBusy||!name.trim()||name.trim().length<2||name.trim().length>24} onPress={()=>{
    if(!betaMode){act({type:'onboardPlayer',name});return;}
    setProfileBusy(true);setProfileMessage('');
    Promise.resolve(onUpdateAccountName?.(name.trim()))
     .then(()=>setProfileMessage('✓ Display name saved to your Pup Account.'))
     .catch(error=>setProfileMessage(error?.message||'Unable to save. Reconnect and retry.'))
     .finally(()=>setProfileBusy(false));
   }}>{profileBusy?'Saving…':'Save Display Name'}</Action>
    {profileMessage?<T accessibilityRole="alert" muted>{profileMessage}</T>:null}
   <T style={s.bold}>Puppy name</T><TextInput accessibilityLabel="Puppy name" value={puppy} onChangeText={setPuppy} maxLength={18} style={s.input}/><Action disabled={!puppy.trim()} onPress={()=>act({type:'rename',name:puppy})}>Save Puppy Name</Action>
   <T style={s.bold}>Birthday</T><T muted>No birth year is stored. Leave both fields empty to skip.</T><View style={s.choices}><TextInput accessibilityLabel="Birthday month" placeholder="Month (1–12)" placeholderTextColor={C.muted} keyboardType="number-pad" maxLength={2} value={month} onChangeText={setMonth} style={[s.input,{flex:1}]}/><TextInput accessibilityLabel="Birthday day" placeholder="Day (1–31)" placeholderTextColor={C.muted} keyboardType="number-pad" maxLength={2} value={day} onChangeText={setDay} style={[s.input,{flex:1}]}/></View><Action disabled={!validBirthday(Number(month),Number(day))} onPress={()=>{
     if(betaMode){void onUpdateBirthday?.({birthdayMonth:Number(month),birthdayDay:Number(day)}).then(ok=>setProfileMessage(ok?'✓ Birthday saved securely on this device.':'Unable to save birthday.')).catch(()=>setProfileMessage('Unable to save birthday.'));}
     else act({type:'onboardBirthday',month:Number(month),day:Number(day)});
    }}>Save Birthday</Action><Action onPress={()=>onOpen('achievements')}>Achievements & XP</Action>
  </>)}
  {section('discord','◉','Discord','Connect or manage your Discord account',betaMode?<><Action onPress={onConnectAccount}>Connect Pup Account with Discord</Action><T muted>The original Play app and beta have separate Android identities. An authorized companion link provides access without replacing the Play installation.</T></>:pending)}
  {group('APP & EXPERIENCE')}
  {section('appearance','🎨','Appearance','Theme, accent color, scale, contrast and motion',<>
   <Choices label="THEME" value={g.themeMode} items={[["light","Light"],["dark","Dark"],["system","Follow System"]]} onChange={v=>pref('themeMode',v)}/>
   <T style={s.bold}>ACCENT COLOR</T><T muted>Current: {ACCENT_PRESETS.find(p=>p[1]===g.accentHex)?.[0]||g.accentHex}</T>
   <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap:10}}>{ACCENT_PRESETS.map(([title,color])=><Pressable key={color} accessibilityRole="radio" accessibilityLabel={title} accessibilityState={{checked:g.accentHex===color}} onPress={()=>pref('accentHex',color)} style={{alignItems:'center',gap:4}}><View style={[s.swatch,{backgroundColor:color,borderColor:g.accentHex===color?C.text:C.line}]}>{g.accentHex===color?<T style={{color:'#071014',fontWeight:'900'}}>✓</T>:null}</View><T style={s.small}>{title.split(' ')[0]}</T></Pressable>)}</ScrollView>
   <Action onPress={()=>setCustom(!custom)}>Custom Color</Action>{custom?<><TextInput accessibilityLabel="Custom accent HEX" value={hex} onChangeText={setHex} autoCapitalize="characters" maxLength={7} style={s.input}/><Action disabled={!/^#[0-9a-f]{6}$/i.test(hex)} onPress={()=>{pref('accentHex',hex);setCustom(false);}}>Apply Custom Color</Action></>:null}
   <View style={s.preview}><T style={s.bold}>THEME EXAMPLE</T><T>Puppy Clicker</T><T muted>Current theme</T><View style={[s.action,{backgroundColor:C.accent}]}><T style={{color:C.onAccent}}>Accent example</T></View><T muted>{g.themeMode} · {g.accentHex}</T></View>
   <Choices label="Performance preset" value={g.performancePreset} items={[["automatic","Automatic"],["quality","Quality"],["battery_saver","Battery Saver"]]} onChange={v=>pref('performancePreset',v)}/>
   {toggle('animatedUi','Animated UI','Optional interface transitions and decorative movement.')}
   {toggle('buttonAnimations','Button animations','Small press and interaction motion.')}
   {toggle('reducedMotion','Reduced motion','Minimizes non-essential movement.')}
   {toggle('highContrast','High contrast','Strengthens separation between text, cards and controls.')}
   <Choices label="UI scale" value={g.uiScale} items={[["compact","Compact"],["default","Default"],["large","Large"]]} onChange={v=>pref('uiScale',v)}/>
  </>)}
  {section('notifications','🔔','Notifications','In-game alerts, updates and notification permissions',<>
   <T style={s.bold}>Notification Permission</T><T muted>{notifications.permission==='granted'?'Android notifications are allowed.':notifications.permission==='unsupported'?'Android notification delivery is available in the Android app.':'Android notification permission is required for alerts outside the app.'}</T>
   {notifications.permission!=='granted'?<Action disabled={notifications.permission==='unsupported'} onPress={notifications.permission==='denied'?notifications.openSystemSettings:notifications.request}>{notifications.permission==='denied'?'Open Android Settings':'Allow Notifications'}</Action>:null}
   {toggle('dailyRewardNotifications','Daily Rewards','Daily gifts and claimable reward reminders.')}
   {toggle('gameEventNotifications','Game Events','Dog Park Adventure completion alerts.')}
   {toggle('updateNotifications','App Updates','Keep update notices in the notification inbox when available.')}
   {toggle('puppyAttentionNotifications','Puppy Attention',"Every two hours while away, using your puppy's care status when scheduled.")}
   <T muted>{betaMode?'PupEye notification delivery and original Kotlin history are not yet ported to this beta.':'Local reminders are enabled for this preview. Remote update notices and the original Kotlin notification history still need account integration.'}</T>
   {notifications.error?<T style={{color:C.danger}}>{notifications.error}</T>:null}
   <Action onPress={()=>{notifications.check();onOpen('notifications');}}>Check Notifications Now</Action><Action onPress={notifications.sendTest}>Send Test Notification</Action>
  </>)}
  {section('privacy','🛡','Privacy & Data','Diagnostics, crash reporting, network use and PupEye privacy',<>
   {toggle('anonymousDiagnosticsEnabled','Optional anonymous diagnostics','Record recent screen visits locally for troubleshooting. No telemetry is uploaded.')}
   {toggle('crashReportsEnabled','Optional crash reports','Save a local error summary when Android can finish writing it. No remote crash destination is configured.')}
   {toggle('supportIdentityEnabled','Include account identity in support reports','Include your local display name only when you choose to share a support report.')}
   <T muted>{betaMode?'Beta gameplay uses your authenticated Pup Account, not guest progression. Taps, care, shop, Gacha, daily rewards, XP and casino settlements use signed cloud writes; unsupported actions remain blocked.':'Guest data stays on this device. Network access loads roster assets and monthly goals. PupEye and authoritative cloud saves still require migration.'}</T><T muted>{diagnostics?.summary||'No local diagnostics recorded.'}</T><Action onPress={diagnostics?.clear}>Clear Local Diagnostics</Action>
  </>)}
  {group('GAME')}
  {section('gameplay','🎮','Gameplay','Interaction, feedback and gameplay preferences',<>
   {toggle('hapticsEnabled','Haptic feedback','Android vibration feedback for supported interactions.')}
   {g.hapticsEnabled?<Action onPress={()=>Vibration.vibrate(30)}>Test Haptic</Action>:null}
   {toggle('animationsEnabled','Tap animations','Puppy idle and tap motion.')}
   {toggle('compactNumbers','Compact numbers','Use K/M/B abbreviations for large values.')}
   {toggle('reducedMotion','Reduced visual effects','Uses the same reduced-motion preference as Appearance.')}
   {toggle('puppyPointerEnabled','Puppy mouse pointer','Original light/dark pointer for a connected mouse or trackpad.')}
   <T muted>{betaMode?'Cloud-save writes: Signed gameplay and casino settlements · revision-checked':'Auto-save: Active · local guest save'}</T>
  </>)}
  {section('data','💾','Data Management','Save status, backups, resets and local data',<>
   <T style={s.bold}>CURRENT SAVE</T><T muted>{betaMode?'Pup Account: '+String(cloudState||'Not loaded').replaceAll('_',' ')+'. Progression, rewards, purchases and casino actions require protected cloud writes.':'Local guest save. Your original Kotlin app and cloud progress are separate.'}</T>
   {betaMode?<View style={{gap:7}}>
    <T style={s.bold}>SUPABASE CLOUD-SAVE VERIFICATION</T>
    <T accessibilityRole="alert" style={[s.small,{color:['ERROR','BLOCKED','SAVED_DISPLAY_ERROR'].includes(syncStatus?.state)?C.danger:C.text}]}>
     {'Status: '+String(syncStatus?.state||'NOT_VERIFIED').replaceAll('_',' ')}
    </T>
    {syncStatus?.savedAt?<T muted style={s.small}>{'Last confirmed update: '+(Number.isFinite(Date.parse(syncStatus.savedAt))?new Date(syncStatus.savedAt).toLocaleString():String(syncStatus.savedAt))}</T>:null}
    {syncStatus?.errorCode?<T style={[s.small,{color:C.danger}]}>{'Save error: '+syncStatus.errorCode}</T>:null}
    {syncStatus?.errorDetail?<T muted style={s.small}>{syncStatus.errorDetail}</T>:null}
    <T muted style={s.small}>Test Cloud Save sends a signed, non-economic write to Supabase and verifies the committed revision. It never spends Treats or changes XP.</T>
    <Action disabled={syncStatus?.state==='SAVING'} onPress={()=>act({type:'syncProbe'})}>{syncStatus?.state==='SAVING'?'Saving to Supabase…':'Test Cloud Save'}</Action>
    <Action onPress={()=>{void onRefreshAccount?.();}}>Reload Cloud Save</Action>
   </View>:null}
   <Action onPress={()=>setDanger(!danger)}>Danger Zone {danger?'⌃':'⌄'}</Action>{danger?<>
    <Action danger onPress={()=>setDangerAction('settings')}>Reset Settings</Action>
    {!betaMode?<><Action danger onPress={()=>setDangerAction('progress')}>Reset Game Progress</Action><Action danger onPress={()=>setDangerAction('delete')}>Delete Local Save Data</Action></>:<T muted>Destructive account actions are disabled in the beta.</T>}
   </>:null}
  </>)}
  {group('SUPPORT')}
  {section('support','🐾','Support & Reports','Report bugs, account problems, users, Casino issues and more',<>
   <Choices label="Report type" value={category} items={['Bug','Account','Casino','Other'].map(x=>[x,x])} onChange={setCategory}/><TextInput accessibilityLabel="Support report" multiline placeholder="What happened? Include the steps to reproduce." placeholderTextColor={C.muted} value={report} onChangeText={setReport} maxLength={2000} style={[s.input,{minHeight:110,textAlignVertical:'top'}]}/>
   <Action disabled={!report.trim()} onPress={()=>Share.share({message:`Puppy Clicker React Beta 1.8.0\n${category} report\n${g.supportIdentityEnabled?'Player: '+g.guestName+'\n':''}${report.trim()}`}).catch(e=>Alert.alert('Unable to share',e.message))}>Share Support Report</Action><T muted>Choose where to send your report in Android’s share menu.</T>
  </>)}
  {group('ADVANCED')}
  {section('advanced','▾','Advanced','Roster, PupEye, Online Assets, Release Hub and About',<T muted>Open the items below when you need them.</T>)}
  {section('roster','🐶','Puppy Roster','Manage streamed puppy roster content and refresh assets',<><Action onPress={()=>onOpen('roster')}>Open Puppy Roster</Action><Action disabled={registry.refreshing} onPress={registry.refresh}>Refresh Puppy Assets</Action></>)}
  {section('pupeye','👁','PupEye Protection','Fair-play and save integrity',pending)}
  {section('assets','☁','Online Assets','Streamed asset, roster and cache status',<>
   <T>Roster source: {registry.source}</T><T>Visible puppies: {registry.items.length}</T><T muted>{registry.status}</T><Action disabled={registry.refreshing} onPress={registry.refresh}>{registry.refreshing?'Refreshing…':'Refresh Assets'}</Action><Action disabled={registry.refreshing} onPress={registry.clearCache}>Clear Asset Cache</Action>
  </>)}
  {section('release-hub','↻','Release Hub',"What's New, installed build and app updates",<><T style={s.bold}>{betaMode?'React Native [Beta] 1.8.0':'React Native preview 1.8.0'}</T><T muted>Settings controls, local notification inbox and original hardware pointer artwork.</T><Action onPress={()=>Linking.openURL('https://github.com/markhitchk/pup-clinker/actions/workflows/react-native-preview-apk.yml')}>View Preview Builds</Action></>)}
  {section('about','ⓘ','About Puppy Clicker','Version, links, development and legal information',<><Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain" style={s.aboutLogo}/><T style={s.bold}>Puppy Clicker · 1.8.0 {betaMode?'Beta':'Preview'}</T><T>Built by Harley’s Studios</T><T muted>React Native Android · {betaMode?'com.harleytg.puppyclicker.beta':'com.harleytg.puppyclicker.preview'}</T><T muted>Production account migration and Google Play release validation are still required.</T></>)}
  {dangerAction?<DangerHoldDialog action={dangerAction} g={g} onDismiss={()=>setDangerAction(null)} onConfirmed={()=>{const type={settings:'resetSettings',progress:'resetGuestProgress',delete:'clearGuest'}[dangerAction];setDangerAction(null);act({type});}}/>:null}
 </View>;
}
const s=themedStyles(()=>({
 page:{gap:0},text:{color:C.text,fontSize:14},muted:{color:C.muted},small:{fontSize:12},bold:{fontWeight:'700'},
 title:{fontSize:28,fontWeight:'900',color:C.text},description:{fontSize:14,lineHeight:21},
 identity:{marginTop:14,marginBottom:16,backgroundColor:'#5B4FE0',borderRadius:24,padding:16,flexDirection:'row',alignItems:'center',gap:14,minHeight:108},avatar:{width:76,height:76},identityName:{fontSize:22,fontWeight:'900',color:'#FFFFFF'},badge:{fontSize:14,fontWeight:'800',color:'#FFFFFF',backgroundColor:'#7568E4',borderRadius:22,paddingHorizontal:10,paddingVertical:4,overflow:'hidden'},
 group:{fontSize:12,fontWeight:'900',color:C.accentInk,marginTop:7,marginBottom:7},section:{marginBottom:9,backgroundColor:C.surface,borderColor:C.line,borderWidth:1,borderRadius:20,overflow:'hidden'},sectionHead:{flexDirection:'row',alignItems:'center',paddingHorizontal:14,paddingVertical:12,gap:11,minHeight:68},sectionTitle:{fontSize:16,fontWeight:'800'},subtitle:{fontSize:12,lineHeight:17},iconBox:{width:42,height:42,borderRadius:12,backgroundColor:C.dark?'#494457':'#E7E1EF',alignItems:'center',justifyContent:'center'},icon:{fontSize:21},chevron:{fontSize:26,fontWeight:'800',color:C.accentInk},body:{padding:14,paddingTop:5,gap:12},
 action:{padding:12,borderRadius:20,borderWidth:1,borderColor:C.line,minHeight:44,justifyContent:'center'},toggle:{flexDirection:'row',alignItems:'center',gap:9,paddingVertical:5},choices:{flexDirection:'row',flexWrap:'wrap',gap:7},choice:{borderRadius:16,padding:9,borderWidth:1,borderColor:C.line},selected:{borderColor:C.accentInk,backgroundColor:C.accentSoft},input:{color:C.text,fontSize:15,borderRadius:12,padding:11,borderColor:C.line,borderWidth:1,backgroundColor:C.card},swatch:{width:42,height:42,borderRadius:21,borderWidth:2,alignItems:'center',justifyContent:'center'},preview:{backgroundColor:C.card,padding:14,borderRadius:18,gap:9},aboutLogo:{width:96,height:96,alignSelf:'center'}
}));
