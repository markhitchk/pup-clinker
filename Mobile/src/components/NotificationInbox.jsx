import React,{useState} from 'react';
import {Image,Modal,Pressable,ScrollView,Text,View,useWindowDimensions} from 'react-native';
import {colors as C,themedStyles} from '../theme.js';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import PuppyPointer from './PuppyPointer.jsx';
const tabs=[['all','All'],['reward','🎁 Reward'],['pup','🐾 Pup'],['system','⚙️ System']];
const dayStart=value=>{const d=new Date(value);d.setHours(0,0,0,0);return d.getTime();};
export default function NotificationInbox({visible,g,act,onClose,onNavigate,serverBacked=false}){
 const [filter,setFilter]=useState('all');
 const insets=useSafeAreaInsets(),{height}=useWindowDimensions();
 const short=height-insets.top-insets.bottom<700;
 const items=g.notificationInbox.filter(n=>filter==='all'||n.category===filter).sort((a,b)=>b.createdAt-a.createdAt);
 const today=dayStart(Date.now()),yesterday=new Date(today);yesterday.setDate(yesterday.getDate()-1);
 const groups=[['Today',items.filter(n=>n.createdAt>=today)],['Yesterday',items.filter(n=>n.createdAt>=yesterday.getTime()&&n.createdAt<today)],['Earlier',items.filter(n=>n.createdAt<yesterday.getTime())]];
 const unread=g.notificationInbox.filter(n=>!n.read).length;
 const hasRead=g.notificationInbox.some(n=>n.read);
 const openItem=n=>{
  act({type:'openNotification',id:n.id});
  if(serverBacked&&n.id.startsWith('afk:'))act({type:'afkOpen',id:n.id});
  if(n.action==='daily'||n.action==='parkClaim'){
   onClose();onNavigate?.('rewards');
  }
 };
 const dateLabel=ms=>new Date(ms).toLocaleDateString('en-US',{month:'numeric',day:'numeric',year:'2-digit'});
 return <Modal visible={visible} transparent statusBarTranslucent navigationBarTranslucent animationType={g.reducedMotion||!g.animatedUi?'none':'fade'} onRequestClose={onClose}>
  <PuppyPointer enabled={g.puppyPointerEnabled} style={[s.scrim,{paddingTop:insets.top+18,paddingBottom:insets.bottom+18}]}>
   <View accessibilityViewIsModal style={[s.panel,{height:short?'96%':'90%'}]}>
    <View style={s.header}><Image source={require('../../assets/logos/puppy_clicker.png')} resizeMode="contain" style={s.logo}/><Text style={s.heading}>Notifications</Text><Pressable accessibilityRole="button" accessibilityLabel="Close notifications" onPress={onClose} style={s.close}><Text style={s.closeText}>×</Text></Pressable></View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.filters} contentContainerStyle={s.filterContent}>{tabs.map(([id,title])=><Pressable key={id} accessibilityRole="tab" accessibilityState={{selected:filter===id}} onPress={()=>setFilter(id)} style={[s.tab,filter===id&&s.selected]}><Text style={s.tabText}>{title}</Text>{id==='all'&&unread?<View style={s.unreadBadge}><Text style={s.unreadCount}>{Math.min(99,unread)}</Text></View>:null}</Pressable>)}</ScrollView>
    <ScrollView style={{flex:1}} contentContainerStyle={s.content}>{items.length===0?<View style={s.empty}><Text style={s.emptyTitle}>All caught up!</Text><Text style={s.emptyText}>Your rewards, puppy reminders and system notices appear here.</Text></View>:groups.map(([title,list])=>list.length?<View key={title} style={{gap:8}}><Text style={s.day}>{title}</Text>{list.map(n=><View key={n.id} style={[s.item,n.action&&s.claimable]}>
     <Pressable accessibilityRole="button" accessibilityLabel={n.title+(n.read?'':', unread')} onPress={()=>openItem(n)} style={s.itemHead}>
      <View style={s.emojiBox}><Text style={{fontSize:23}}>{n.category==='reward'?'🎁':n.category==='pup'?'🐶':'⚙️'}</Text></View>
      <View style={{flex:1,gap:4}}><View style={s.titleRow}><Text numberOfLines={2} style={s.itemTitle}>{n.title}</Text><View style={s.timeRow}><Text style={s.time}>{dateLabel(n.createdAt)}</Text>{!n.read?<View style={s.dot}/>:null}</View></View><Text style={s.itemBody}>{n.body}</Text></View>
     </Pressable>{n.action==='afkClaim'?<View style={s.claimRow}><Pressable accessibilityRole="button" accessibilityLabel={'Claim '+n.title} style={s.claim} onPress={()=>act({type:'afkClaim',id:n.id})}><Text style={s.claimText}>🦴 Claim</Text></Pressable></View>:null}
    </View>)}</View>:null)}</ScrollView>
    <View style={s.footer}><Pressable accessibilityRole="button" disabled={!hasRead} accessibilityState={{disabled:!hasRead}} onPress={()=>{act({type:'clearReadNotifications'});if(serverBacked)act({type:'afkClearRead'});}} style={[s.footerButton,!hasRead&&{opacity:.55}]}><Text style={s.footerText}>🗑 Clear Read</Text></Pressable><Pressable accessibilityRole="button" disabled={!unread} accessibilityState={{disabled:!unread}} onPress={()=>{act({type:'markNotificationsRead'});if(serverBacked)act({type:'afkMarkAllRead'});}} style={[s.footerButton,s.markAll,!unread&&{opacity:.55}]}><Text style={[s.footerText,{color:'#FFFFFF'}]}>✓ Mark All Read</Text></Pressable></View>
   </View>
  </PuppyPointer>
 </Modal>;
}
const s=themedStyles(()=>({
 scrim:{flex:1,backgroundColor:'rgba(0,0,0,.65)',justifyContent:'center',paddingHorizontal:8},panel:{maxHeight:850,width:'100%',maxWidth:570,alignSelf:'center',backgroundColor:C.dark?'#132F3B':C.surface,borderRadius:30,overflow:'hidden'},header:{backgroundColor:'#625DD7',paddingHorizontal:14,paddingVertical:9,flexDirection:'row',alignItems:'center',gap:9},logo:{width:46,height:46,backgroundColor:'#EFEEFF',borderRadius:14},heading:{fontSize:23,fontWeight:'900',color:'#FFFFFF',flex:1},close:{width:44,height:44,borderRadius:22,borderWidth:1,borderColor:'#A7A4EC',backgroundColor:'#7772D9',alignItems:'center',justifyContent:'center'},closeText:{fontSize:37,color:'#FFFFFF',lineHeight:41},filters:{maxHeight:61,backgroundColor:'#162D37'},filterContent:{padding:9,gap:6,alignItems:'center'},tab:{flexDirection:'row',alignItems:'center',gap:5,paddingHorizontal:12,paddingVertical:10,borderRadius:24,borderWidth:1,borderColor:'#34464D'},selected:{backgroundColor:'#716DE3',borderColor:'#918EF0'},unreadBadge:{minWidth:20,paddingHorizontal:5,paddingVertical:1,borderRadius:12,backgroundColor:'#E84B59'},unreadCount:{fontSize:12,color:'#FFFFFF',fontWeight:'900'},tabText:{fontSize:16,fontWeight:'800',color:'#FFFFFF'},content:{padding:10,gap:16,paddingBottom:18},day:{color:C.accentInk,fontSize:19,fontWeight:'900',paddingVertical:7},item:{backgroundColor:'#FFFFFE',padding:10,borderRadius:18,borderWidth:1,borderColor:'#DEE5E9',gap:10},claimable:{borderWidth:2,borderColor:'#FFD56B'},itemHead:{flexDirection:'row',gap:10},titleRow:{flexDirection:'row',gap:6,alignItems:'flex-start'},claimRow:{paddingLeft:56,alignItems:'flex-end'},emojiBox:{width:46,height:46,backgroundColor:'#FFF5CE',borderRadius:16,alignItems:'center',justifyContent:'center'},itemTitle:{flex:1,color:'#202039',fontSize:14,fontWeight:'900'},itemBody:{color:'#616775',fontSize:12,lineHeight:18},timeRow:{flexDirection:'row',alignItems:'center',gap:7},time:{fontSize:11,color:'#616775'},dot:{width:9,height:9,borderRadius:5,backgroundColor:'#23A3E5'},claim:{alignSelf:'flex-end',paddingHorizontal:16,paddingVertical:8,borderWidth:1,borderColor:'#268A42',backgroundColor:'#2EA851',borderRadius:24},claimText:{fontSize:14,fontWeight:'800',color:'#FFFFFF'},footer:{backgroundColor:'#162D37',padding:10,flexDirection:'row',gap:7},footerButton:{flex:1,justifyContent:'center',alignItems:'center',minHeight:44,borderRadius:25,borderWidth:1,borderColor:'#AAA6DF',padding:6},markAll:{backgroundColor:'#716DE3',borderColor:'#716DE3'},footerText:{fontSize:14,fontWeight:'800',color:'#C3C0F5',textAlign:'center'},empty:{padding:20,gap:9},emptyTitle:{fontSize:21,fontWeight:'800',color:C.text,textAlign:'center'},emptyText:{fontSize:15,lineHeight:22,color:C.muted,textAlign:'center'}
}));
