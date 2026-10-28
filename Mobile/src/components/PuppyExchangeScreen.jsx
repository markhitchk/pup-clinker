import React,{useCallback,useEffect,useMemo,useState} from 'react';
import {ActivityIndicator,Alert,NativeModules,Pressable,ScrollView,Share,StyleSheet,Text,TextInput,View} from 'react-native';

// Kotlin Exchange parity: Friends / Connect / Gifts / Trade / History.
// Transfers are never applied by React state; a signed PupEye request must
// settle both cloud saves atomically before the roster can be refreshed.
const KEY=process.env.EXPO_PUBLIC_PUPPY_SUPABASE_KEY||
 'sb_publishable_id7zKwH7BPmmohc7T3RZmA_DTjMaFVU';
const TABS=[['friends','Friends'],['connect','Connect'],['gifts','Gifts'],
 ['trade','Trade'],['history','History']];
const empty={profile:null,connections:[],offers:[],policies:[]};
const safeMessage=code=>{
 if(/QUEUE|PC-Q/.test(code))return 'Server admission is unavailable. Reopen Puppy Clicker and retry.';
 if(/LOGIN|SESSION|ACCOUNT|INSTALLATION|LEGAL/.test(code))
  return 'Verify or reconnect your Pup Account before using Puppy Exchange.';
 if(/FRIEND_CODE_INVALID/.test(code))return 'Enter a valid PUP Friend Code.';
 if(/FRIEND_NOT_FOUND/.test(code))return 'No eligible player was found for that Friend Code.';
 if(/REQUEST_PENDING/.test(code))return 'That friend request is already waiting for a response.';
 if(/BLOCKED/.test(code))return 'This connection is blocked.';
 if(/NOT_TRANSFERABLE/.test(code))return 'That puppy is protected and cannot be exchanged.';
 if(/UNKNOWN_PUPPY/.test(code))return 'This puppy has not been published in the official roster yet.';
 if(/CATALOG_UNAVAILABLE/.test(code))return 'The official puppy catalog is unavailable. Try again later.';
 if(/INVENTORY|CLOUD_SAVE/.test(code))return 'Cloud ownership changed. Refresh the roster before trading.';
 if(/EXPIRED|CLOSED|NOT_PENDING/.test(code))return 'This offer is no longer active. Refresh to see the latest status.';
 if(/FRIENDSHIP/.test(code))return 'Both players must first accept a friend request.';
 if(/PUPPY_NATIVE|NATIVE/.test(code))return 'This beta build needs the updated Puppy Exchange native module.';
 return 'Exchange could not be verified. Refresh before repeating an offer.';
};
const names=values=>values?.length?values.join(', '):'None';
export default function PuppyExchangeScreen({game,registry,onBack,onRefresh,account}){
 const [tab,setTab]=useState('friends'),[data,setData]=useState(empty);
 const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false);
 const [error,setError]=useState(''),[notice,setNotice]=useState('');
 const [code,setCode]=useState(''),[found,setFound]=useState(null);
 const [friendCode,setFriendCode]=useState('');
 const [offerKind,setOfferKind]=useState('gift'),[selected,setSelected]=useState([]);
 const [counterIds,setCounterIds]=useState([]);
 const [recipientOfferId,setRecipientOfferId]=useState(null);
 const verified=account?.status==='VERIFIED'&&account?.legalAccepted===true;
 const bridge=NativeModules.PuppyBetaAccount;
 const request=useCallback(async(operation,fields={})=>{
  if(!verified||!bridge?.exchangeAction)
   throw Object.assign(new Error('Exchange bridge unavailable'),{code:'EXCHANGE_LOGIN_REQUIRED'});
  const result=await bridge.exchangeAction(JSON.stringify({operation,...fields}),KEY);
  const parsed=JSON.parse(result?.json||'null');
  if(!parsed||typeof parsed!=='object')throw new Error('Invalid exchange response');
  return parsed;
 },[verified,bridge]);
 const refresh=useCallback(async(silent=false)=>{
  if(!silent)setLoading(true);
  try{
   const latest=await request('profile');
   setData({
    profile:latest.profile||null,
    connections:Array.isArray(latest.connections)?latest.connections:[],
    offers:Array.isArray(latest.offers)?latest.offers:[],
    policies:Array.isArray(latest.policies)?latest.policies:[]
   });
   setError('');
   return true;
  }catch(e){
   if(!silent)setError(safeMessage(String(e?.code||'EXCHANGE_UNAVAILABLE')));
   return false;
  }finally{if(!silent)setLoading(false);}
 },[request]);
 useEffect(()=>{
  void refresh();
  // Poll the authoritative service for peer requests; does not create local
  // transactions or bypass the existing app notification infrastructure.
  const id=setInterval(()=>void refresh(true),20000);
  return ()=>clearInterval(id);
 },[refresh]);
 const run=async(operation,fields={},message='Exchange updated.')=>{
  if(busy)return;
  setBusy(true);setError('');setNotice('');
  try{
   await request(operation,fields);
   setNotice(message);
   await refresh(true);
   if(operation==='confirm')await onRefresh?.();
   return true;
  }catch(e){
   setError(safeMessage(String(e?.code||'EXCHANGE_UNAVAILABLE')));
   return false;
  }finally{setBusy(false);}
 };
 const friends=data.connections.filter(c=>c.state==='accepted');
 const incoming=data.connections.filter(c=>c.state==='pending'&&c.incoming);
 const outgoing=data.connections.filter(c=>c.state==='pending'&&!c.incoming);
 const blocked=data.connections.filter(c=>c.state==='blocked');
 const policy=new Map(data.policies.map(p=>[p.id,p]));
 const byId=new Map((registry?.items||[]).map(p=>[p.id,p]));
 const active=useMemo(()=>new Set(game?.unlockedPuppies||[]),[game?.unlockedPuppies]);
 const canGiftAll=data.profile?.canGiftAll===true;
 const canGiftDirect=data.profile?.canGiftDirect===true;
 const eligible=kind=>(registry?.items||[]).filter(p=>
  kind==='gift'&&canGiftAll ? true : active.has(p.id)&&
   (kind==='gift'?policy.get(p.id)?.giftable:policy.get(p.id)?.tradeable));
 const puppyLabel=id=>byId.get(id)?.name||id;
 const details=ids=>ids?.length?ids.map(puppyLabel).join(' · '):'No puppies selected';
 const sendFriend=async()=>{
  const valid=code.trim().toUpperCase();
  if(!/^(?:PUP-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}|PUP-HARLEYTG-DEV)$/.test(valid)){
   setError('Use a Friend Code like PUP-XXXX-XXXX-XXXX.');return;
  }
  if(await run('request',{friendCode:valid},'Friend request sent.'))
   {setCode('');setFound(null);setTab('friends');}
 };
 const sendOffer=()=>{
  if(!friendCode||selected.length<1){
   setError('Choose an accepted friend and one or more eligible puppies.');return;
  }
  Alert.alert(offerKind==='gift'?'Send puppy gift?':'Create trade offer?',
   offerKind==='gift'
    ?'The recipient must accept your gift. The sender keeps their original copy.'
    :'The recipient selects their side; you must confirm before anything transfers.',
   [{text:'Cancel',style:'cancel'},{text:'Continue',onPress:async()=>{
    if(await run('offer',{friendCode,kind:offerKind,offered:selected},
      offerKind==='gift'?'Gift offer sent.':'Trade proposal sent.'))setSelected([]);
   }}]);
 };
 const answer=(c,choice)=>{
  Alert.alert(choice==='block'?'Block player?':'Respond to friend request?',
   choice==='block'?'Blocking prevents exchanges between your accounts.':
    choice==='accept'?'Accept this player as a friend?':'Decline this request?',
   [{text:'Cancel',style:'cancel'},{text:'Confirm',onPress:()=>void run('respond',
    {connectionId:c.id,answer:choice},'Friend connection updated.')}]);
 };
 const finishOffer=(offer,op)=>{
  const message=op==='confirm'
   ?'Confirm this exchange? The server will atomically update both Pup Accounts. Never retry after an uncertain response without refreshing.'
   :op==='cancel'?'Cancel your pending offer?':'Decline this incoming offer?';
  Alert.alert('Puppy Exchange',message,[{text:'Back',style:'cancel'},
   {text:'Confirm',onPress:()=>void run(op,{offerId:offer.id},
     op==='confirm'?'Exchange confirmed. Cloud rosters will synchronize.':'Offer updated.')}]);
 };
 const panel=(children)=><View style={styles.card}>{children}</View>;
 const button=(label,callback,disabled=false,secondary=false)=>
  <Pressable accessibilityRole="button" disabled={busy||disabled} onPress={callback}
   style={[styles.button,secondary&&styles.buttonSecondary,(busy||disabled)&&styles.disabled]}>
   <Text style={[styles.buttonText,secondary&&styles.secondaryText]}>{label}</Text>
  </Pressable>;
 const selector=(kind,chosen,setChosen,max)=>{
  const pups=eligible(kind);
  return <View style={styles.section}>
   <Text style={styles.label}>{kind==='gift'&&canGiftAll?'All official puppies':'Your eligible puppies'} · {chosen.length}/{max}</Text>
   {kind==='gift'&&canGiftAll?<Text style={styles.muted}>Developer gifting: choose any published puppy, including protected and future roster styles. The recipient must still accept.</Text>:null}
   {pups.length===0?<Text style={styles.muted}>No server-approved {kind==='gift'?'giftable':'tradeable'} puppies are available in your verified roster. Protected and source-copy puppies cannot be exchanged.</Text>:null}
   <View style={styles.wrap}>{pups.map(p=>{
    const checked=chosen.includes(p.id);
    return <Pressable key={p.id} accessibilityRole="checkbox"
     accessibilityState={{checked}} onPress={()=>setChosen(old=>checked?old.filter(id=>id!==p.id):
      old.length>=max?old:[...old,p.id])} style={[styles.chip,checked&&styles.chipOn]}>
     <Text style={styles.chipText}>{checked?'✓ ':''}{p.name}</Text>
    </Pressable>;
   })}</View>
  </View>;
 };
 const offerCard=(o)=><View key={o.id} style={styles.offer}>
   <Text style={styles.bold}>{o.incoming?'From':'To'} {o.peer?.name||'Pup Account'} · {o.kind==='gift'?'🎁 Gift':'⇄ Trade'}</Text>
   <Text style={styles.muted}>Status: {o.state.replaceAll('_',' ')}</Text>
   <Text style={styles.body}>Sender offers: {details(o.offered)}</Text>
   {o.kind==='trade'?<Text style={styles.body}>Recipient offers: {details(o.requested)}</Text>:null}
   {o.state==='pending'&&o.incoming&&o.kind==='gift'?<View style={styles.actions}>
    {button('Accept gift',()=>finishOffer(o,'confirm'))}
    {button('Decline',()=>finishOffer(o,'decline'),false,true)}
   </View>:null}
   {o.state==='pending'&&o.incoming&&o.kind==='trade'?<View style={styles.section}>
    <Text style={styles.muted}>Select the puppy or puppies you will offer in return.</Text>
    {recipientOfferId===o.id?selector('trade',counterIds,setCounterIds,5):button('Choose your trade offer',()=>{
     setRecipientOfferId(o.id);setCounterIds([]);
    },false,true)}
    {recipientOfferId===o.id?button('Submit counteroffer',async()=>{
     if(await run('counter',{offerId:o.id,requested:counterIds},
       'Counteroffer sent. The sender must approve it.')){setCounterIds([]);setRecipientOfferId(null);}
    },counterIds.length<1):null}
    {button('Decline',()=>finishOffer(o,'decline'),false,true)}
   </View>:null}
   {o.state==='countered'&&!o.incoming?button('Confirm both trade offers',
     ()=>finishOffer(o,'confirm')):null}
   {o.state==='pending'&&!o.incoming||o.state==='countered'&&!o.incoming?
    button('Cancel offer',()=>finishOffer(o,'cancel'),false,true):null}
  </View>;
 return <View style={styles.root}>
  <View style={styles.header}><Text style={styles.title}>🎁 Puppy Exchange</Text>
   {button('← Roster',onBack,false,true)}
  </View>
  {panel(<>
   <Text style={styles.bold}>{data.profile?.username||'Pup Account'}</Text>
   <Text style={styles.muted}>YOUR FRIEND CODE</Text>
   <Text selectable style={styles.friendCode}>{data.profile?.friendCode||'Checking…'}</Text>
   <View style={styles.actions}>
    {button('Share code',()=>void Share.share({message:data.profile?.friendCode||''}),!data.profile?.friendCode,true)}
    {button('Refresh',()=>void refresh(),loading,true)}
   </View>
   <Text style={styles.muted}>Verified account · server-protected ownership · no local or guest trades</Text>
  </>)}
  {error?panel(<Text accessibilityRole="alert" style={styles.error}>{error}</Text>):null}
  {notice?panel(<Text accessibilityRole="alert" style={styles.success}>{notice}</Text>):null}
  {loading?<ActivityIndicator color="#73CFE7"/>:null}
  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabs}>
   {TABS.map(([id,label])=><Pressable key={id} accessibilityRole="tab"
    accessibilityState={{selected:tab===id}} style={[styles.tab,tab===id&&styles.tabActive]}
    onPress={()=>setTab(id)}><Text style={styles.tabText}>{label}</Text></Pressable>)}
  </ScrollView>
  {tab==='friends'?<View style={styles.section}>
   {panel(<><Text style={styles.heading}>Friend requests · {incoming.length}</Text>
    {incoming.length===0?<Text style={styles.muted}>No new requests.</Text>:incoming.map(c=>
     <View key={c.id} style={styles.offer}><Text style={styles.bold}>{c.peer?.name||'Player'}</Text>
      <Text style={styles.muted}>{c.peer?.code}</Text>
      <View style={styles.actions}>
       {button('Accept',()=>answer(c,'accept'))}
       {button('Decline',()=>answer(c,'decline'),false,true)}
       {button('Block',()=>answer(c,'block'),false,true)}
      </View></View>)}
   </>)}
   {panel(<><Text style={styles.heading}>Friends · {friends.length}</Text>
    {friends.length===0?<Text style={styles.muted}>No accepted friends yet. Use Connect to send a request.</Text>:friends.map(c=>
     <View key={c.id} style={styles.offer}>
      <Text style={styles.bold}>{c.peer?.name||'Player'}</Text>
      <Text style={styles.muted}>{c.peer?.code}</Text>
      <View style={styles.actions}>
       {button('Exchange',()=>{setFriendCode(c.peer?.code||'');setTab('gifts');})}
       {button('Block',()=>answer(c,'block'),false,true)}
      </View></View>)}
    {outgoing.map(c=><Text key={c.id} style={styles.muted}>Awaiting reply: {c.peer?.name}</Text>)}
   </>)}
   {blocked.length?panel(<><Text style={styles.heading}>Blocked</Text>
    {blocked.map(c=><View key={c.id} style={styles.offer}>
     <Text style={styles.body}>{c.peer?.name||'Player'}</Text>
     {c.blockedByMe?button('Unblock',()=>void run('remove',{connectionId:c.id},
      'Player unblocked.'),false,true):<Text style={styles.muted}>Connection blocked</Text>}
    </View>)}</>):null}
  </View>:null}
  {tab==='connect'?panel(<>
   <Text style={styles.heading}>Connect with a friend</Text>
   <Text style={styles.muted}>Enter their Friend Code. The recipient accepts from Friends. Developer gifting is available directly in Gifts without adding a friend.</Text>
   <TextInput accessibilityLabel="Friend Code" value={code}
    onChangeText={x=>{setCode(x.toUpperCase());setFound(null);}} autoCapitalize="characters"
    placeholder="PUP-XXXX-XXXX-XXXX" placeholderTextColor="#8B9DAC"
    maxLength={19} style={styles.input}/>
   <View style={styles.actions}>
    {button('Find player',async()=>{
     setBusy(true);setError('');
     try{setFound((await request('lookup',{friendCode:code.trim().toUpperCase()})).player);}
     catch(e){setError(safeMessage(String(e?.code||'')));}
     finally{setBusy(false);}
    },!code)}
    {button('Send request',()=>void sendFriend(),!found,true)}
   </View>
   {found?<Text style={styles.body}>Found: {found.name} · {found.code}</Text>:null}
  </>):null}
  {tab==='gifts'||tab==='trade'?<View style={styles.section}>
   {panel(<>
    <Text style={styles.heading}>{tab==='gifts'?'🎁 Send a puppy gift':'⇄ Trade puppies'}</Text>
    <Text style={styles.muted}>{tab==='gifts'
     ?(canGiftDirect
       ?'Developer gift: send to any eligible account using their Friend Code, without connecting. The recipient must accept.'
       :'The sender retains their original puppy; the recipient must accept.')
     :'You offer up to five owned puppies. Your friend counters with up to five, then you confirm.'}</Text>
    <Text style={styles.label}>{tab==='gifts'&&canGiftDirect?'Gift recipient':'Accepted friend'}</Text>
    <View style={styles.wrap}>{friends.map(c=><Pressable key={c.id}
      accessibilityRole="radio" accessibilityState={{checked:friendCode===c.peer?.code}}
      onPress={()=>setFriendCode(c.peer?.code||'')}
      style={[styles.chip,friendCode===c.peer?.code&&styles.chipOn]}>
      <Text style={styles.chipText}>{c.peer?.name}</Text></Pressable>)}</View>
    {tab==='gifts'&&canGiftDirect?<TextInput
      accessibilityLabel="Gift recipient Friend Code"
      value={friendCode}
      onChangeText={x=>setFriendCode(x.toUpperCase().trim())}
      autoCapitalize="characters"
      placeholder="Recipient Friend Code: PUP-XXXX-XXXX-XXXX"
      placeholderTextColor="#8B9DAC"
      maxLength={19} style={styles.input}/>:null}
    {friends.length===0&&!canGiftDirect?<Text style={styles.muted}>Add a friend in Connect first.</Text>:null}
    {selector(tab==='gifts'?'gift':'trade',selected,setSelected,tab==='gifts'?1:5)}
    {button(tab==='gifts'?'Offer gift':'Propose trade',()=>{
     setOfferKind(tab==='gifts'?'gift':'trade');
     // sendOffer reads offerKind on the next render. Pass the intended mode
     // directly below rather than relying on asynchronous React state.
     const kind=tab==='gifts'?'gift':'trade';
     const recipientCode=friendCode.trim().toUpperCase();
     if(!/^(?:PUP-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}|PUP-HARLEYTG-DEV)$/.test(recipientCode)||selected.length===0){
      setError('Enter a valid recipient Friend Code and select a puppy.');return;
     }
     Alert.alert(kind==='gift'?'Send gift?':'Propose trade?',
      kind==='gift'?'Your friend must accept the puppy copy.':
       'Your friend must counter, and you must approve the final trade.',
      [{text:'Cancel',style:'cancel'},{text:'Send',onPress:async()=>{
       if(await run('offer',{friendCode:recipientCode,kind,offered:selected},'Offer sent.'))setSelected([]);
      }}]);
    },(friends.length===0&&!canGiftDirect)||selected.length===0||!friendCode)}
   </>)}
   {panel(<><Text style={styles.heading}>{tab==='gifts'?'Gift offers':'Trade offers'}</Text>
    {data.offers.filter(o=>o.kind===(tab==='gifts'?'gift':'trade')&&
     !['completed','declined','cancelled','expired'].includes(o.state)).map(offerCard)}
    {data.offers.filter(o=>o.kind===(tab==='gifts'?'gift':'trade')&&
     !['completed','declined','cancelled','expired'].includes(o.state)).length===0?
     <Text style={styles.muted}>No pending offers.</Text>:null}
   </>)}
  </View>:null}
  {tab==='history'?panel(<><Text style={styles.heading}>Exchange history</Text>
   {data.offers.filter(o=>['completed','declined','cancelled','expired'].includes(o.state))
    .map(offerCard)}
   {!data.offers.some(o=>['completed','declined','cancelled','expired'].includes(o.state))?
    <Text style={styles.muted}>No completed or closed offers yet.</Text>:null}
  </>):null}
  {busy?<ActivityIndicator color="#73CFE7"/>:null}
  <Text style={styles.footer}>Only verified Pup Accounts may connect. Server-approved transfer policy (with an account-bound gift-only owner exception), account standing, and atomic cloud receipts govern every gift and trade.</Text>
 </View>;
}
const styles=StyleSheet.create({
 root:{gap:12,paddingBottom:24},
 header:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8,flexWrap:'wrap'},
 title:{fontSize:25,fontWeight:'900',color:'#EAF7FF'},
 heading:{fontSize:17,fontWeight:'850',color:'#EAF7FF'},
 bold:{fontSize:15,fontWeight:'800',color:'#EAF7FF'},
 label:{fontSize:12,fontWeight:'800',color:'#B2C9D6'},
 body:{fontSize:14,color:'#EAF7FF',lineHeight:21},
 muted:{fontSize:13,color:'#A8BFCB',lineHeight:20},
 friendCode:{fontSize:22,fontWeight:'900',color:'#7DD9ED',letterSpacing:1},
 card:{padding:14,backgroundColor:'#172832',borderColor:'#395262',borderWidth:1,borderRadius:17,gap:10},
 section:{gap:10},offer:{paddingVertical:10,gap:7,borderTopColor:'#395262',borderTopWidth:1},
 input:{padding:12,borderRadius:12,borderWidth:1,borderColor:'#648295',color:'#FFFFFF',backgroundColor:'#0E1B24',fontSize:16},
 tabs:{gap:8,paddingVertical:2},tab:{paddingHorizontal:16,paddingVertical:12,borderRadius:14,backgroundColor:'#243642'},
 tabActive:{backgroundColor:'#0C6574'},tabText:{fontSize:13,color:'#FFFFFF',fontWeight:'800'},
 actions:{flexDirection:'row',flexWrap:'wrap',gap:7},
 button:{minHeight:38,minWidth:80,justifyContent:'center',alignItems:'center',paddingHorizontal:13,
  backgroundColor:'#2C91A6',borderRadius:12},
 buttonSecondary:{backgroundColor:'#233D49',borderColor:'#678394',borderWidth:1},
 buttonText:{color:'#FFFFFF',fontSize:13,fontWeight:'800'},
 secondaryText:{color:'#E9F8FF'},disabled:{opacity:0.38},
 wrap:{flexDirection:'row',flexWrap:'wrap',gap:8},
 chip:{padding:10,borderRadius:10,borderColor:'#59798A',borderWidth:1,backgroundColor:'#21323D'},
 chipOn:{borderColor:'#65CEE0',backgroundColor:'#14616F'},
 chipText:{fontSize:13,color:'#E5F8FF',fontWeight:'700'},
 error:{color:'#FF9DA1',fontWeight:'700'},success:{color:'#98E1B3',fontWeight:'700'},
 footer:{fontSize:11,color:'#8FA5B2',lineHeight:17}
});
