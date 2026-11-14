import React,{useEffect,useState} from 'react';
import {Image,Linking,Pressable,Share,Text,TextInput,View} from 'react-native';
import {colors as C} from '../theme.js';
import {normalizeAvatarUrl} from '../production/avatarUrl.js';

const line=(label,value)=><View style={{gap:5,paddingVertical:12,borderBottomWidth:1,borderBottomColor:C.line}}>
 <Text style={{color:C.muted,fontSize:12,fontWeight:'700'}}>{label}</Text>
 <Text selectable style={{color:C.text,fontSize:16,fontWeight:'700'}}>{value||'Not available'}</Text>
</View>;

/** Settings > Pup Account > Account Details. Only verified public identity metadata. */
export default function AccountDetailsScreen({account,cloudState,onBack,onManage,onRefresh,onSaveAvatarUrl}){
 const verified=account?.status==='VERIFIED';
 const role=account?.guildRole==='DEVELOPER'?'Developer':
  account?.guildRole==='ADMIN'?'Administrator':
  account?.guildRole==='PUP_MEMBER'?'Pup Member':'Player';
 const code=verified?account?.friendCode:null;
 const linked=account?.linkedFromPlay===true;
 const existing=normalizeAvatarUrl(account?.avatarUrl);
 const [url,setUrl]=useState(account?.avatarUrl||'');
 const [busy,setBusy]=useState(false);
 const [message,setMessage]=useState('');
 const [previewFailed,setPreviewFailed]=useState(false);
 const [linkFailed,setLinkFailed]=useState(false);
 const normalized=normalizeAvatarUrl(url);
 const hostingPage=/^https:\/\/(?:www\.|m\.)?(?:imgur\.com|imgbb\.com|ibb\.co|postimg\.cc)\//i.test(url.trim());
 useEffect(()=>{setUrl(account?.avatarUrl||'');setPreviewFailed(false);},[account?.avatarUrl]);
 useEffect(()=>setPreviewFailed(false),[normalized]);
 const button=(label,fn,disabled=false)=><Pressable accessibilityRole="button"
  accessibilityLabel={label} disabled={disabled} onPress={fn}
  style={{borderRadius:12,padding:13,backgroundColor:C.surface,borderWidth:1,borderColor:C.line,opacity:disabled?.5:1}}>
  <Text style={{color:C.text,textAlign:'center',fontWeight:'800'}}>{label}</Text>
 </Pressable>;
 const save=async value=>{
  if(!verified||busy||!onSaveAvatarUrl)return;
  if(value!==null&&!normalized){setMessage('Use a public HTTPS image URL, not a hosting page link.');return;}
  setBusy(true);setMessage('');
  try{
   await onSaveAvatarUrl(value);
   setUrl(value||'');
   setMessage(value?'Profile picture saved to your Pup Account.':'Profile picture removed from your Pup Account.');
  }catch{
   setMessage('Could not update the profile picture. Check your Pup Account connection and retry.');
  }finally{setBusy(false);}
 };
 const openHost=host=>{setLinkFailed(false);void Linking.openURL(host).catch(()=>setLinkFailed(true));};
 return <View style={{gap:16,paddingBottom:24}}>
  <Text style={{color:C.text,fontSize:25,fontWeight:'900'}}>👤 Account Details</Text>
  <Text style={{color:C.muted,fontSize:13}}>Your verified Pup Account information.</Text>
  <View style={{padding:16,borderRadius:16,borderWidth:1,borderColor:C.line,backgroundColor:C.surface}}>
   {line('Account username',verified?'@'+account.username:null)}
   {line('Display name',verified?(account.displayName||account.username):null)}
   {line('Friend Code',code)}
   {line('Account standing',verified?'Verified':account?.status==='CHECKING'?'Checking…':'Sign-in required')}
   {line('Role',verified?role:null)}
   {line('Account connection',verified?(linked?'Linked to original Play account':'Pup Account'):null)}
   {line('Gameplay access',verified?(account.gameplayWritable===true?'Enabled':'Unavailable'):null)}
  </View>
  {verified?<View style={{padding:16,borderRadius:16,borderWidth:1,borderColor:C.line,backgroundColor:C.surface,gap:12}}>
   <Text style={{color:C.text,fontSize:19,fontWeight:'900'}}>🖼️ Profile picture</Text>
   <View style={{flexDirection:'row',alignItems:'center',gap:12}}>
    {normalized&&!previewFailed?<Image source={{uri:normalized}} resizeMode="cover"
      accessibilityLabel="Profile picture preview" onError={()=>setPreviewFailed(true)}
      style={{width:74,height:74,borderRadius:37,backgroundColor:C.line}}/>:
     <View style={{width:74,height:74,borderRadius:37,backgroundColor:C.line,alignItems:'center',justifyContent:'center'}}>
      <Text style={{color:C.text,fontSize:30,fontWeight:'900'}}>{Array.from(account.displayName||account.username||'🐾')[0].toUpperCase()}</Text>
     </View>}
    <View style={{flex:1,gap:3}}>
     <Text style={{color:C.text,fontWeight:'800'}}>Custom Pup Account avatar</Text>
     <Text style={{color:C.muted,fontSize:12}}>This picture also appears on the website after saving.</Text>
    </View>
   </View>
   <Text style={{color:C.muted,fontSize:13}}>Upload a picture to a hosting site, copy its direct image link, and paste that URL below.</Text>
   {button('Upload on Imgur ↗',()=>openHost('https://imgur.com/'))}
   {button('Upload on ImgBB ↗',()=>openHost('https://imgbb.com/'))}
   {button('Upload on Postimages ↗',()=>openHost('https://postimages.org/'))}
   {linkFailed?<Text style={{color:C.danger}}>Could not open the image host. Try your browser.</Text>:null}
   <Text style={{color:C.text,fontWeight:'800'}}>Direct image URL</Text>
   <TextInput accessibilityLabel="Profile picture direct image URL" value={url}
    onChangeText={value=>{setUrl(value);setMessage('');}}
    placeholder="https://i.imgur.com/IMAGE.jpg" placeholderTextColor={C.muted}
    keyboardType="url" autoCapitalize="none" autoCorrect={false} maxLength={2048}
    editable={!busy}
    style={{borderWidth:1,borderColor:url.trim()&&!normalized?C.danger:C.line,
      borderRadius:12,color:C.text,paddingHorizontal:13,paddingVertical:12,backgroundColor:C.bg}}/>
   {url.trim()&&!normalized?<Text style={{color:C.danger,fontSize:12}}>
    {hostingPage?'Copy the direct image address, not the Imgur/ImgBB/Postimages page link.':
     'Enter a valid public HTTPS image link.'}
   </Text>:null}
   {normalized&&previewFailed?<Text style={{color:C.danger,fontSize:12}}>This image could not load. Confirm the host allows public image links.</Text>:null}
   {button(busy?'Saving picture…':'Save avatar URL',()=>void save(normalized),busy||!normalized||normalized===existing)}
   {existing?button('Remove picture',()=>void save(null),busy):null}
   <Text style={{color:C.muted,fontSize:12}}>For Imgur, use i.imgur.com/IMAGE.jpg rather than an imgur.com post. Your picture is public. External hosts may log viewers' IP addresses; removing the link here does not delete the uploaded image.</Text>
   {message?<Text accessibilityLiveRegion="polite" style={{color:C.text,fontSize:13}}>{message}</Text>:null}
  </View>:null}
  {code?button('Share Friend Code',()=>void Share.share({message:code})):null}
  {button('Manage sign-in and contact details',onManage)}
  {button('Refresh account details',()=>void onRefresh?.())}
  {button('← Back to Settings',onBack)}
  <Text style={{color:C.muted,fontSize:12}}>Cloud backup status and diagnostics are available in Settings → Data Management. Device and backend credentials are never displayed.</Text>
 </View>;
}
