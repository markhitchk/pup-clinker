import React from 'react';
import {Pressable,Share,Text,View} from 'react-native';
import {colors as C} from '../theme.js';

const line=(label,value)=><View style={{gap:5,paddingVertical:12,borderBottomWidth:1,borderBottomColor:C.line}}>
 <Text style={{color:C.muted,fontSize:12,fontWeight:'700'}}>{label}</Text>
 <Text selectable style={{color:C.text,fontSize:16,fontWeight:'700'}}>{value||'Not available'}</Text>
</View>;

/**
 * Settings > Pup Account > Account Details.
 * Only public account metadata; installation IDs, internal UUIDs, signing
 * keys, session tokens and raw backend response data are never displayed.
 */
export default function AccountDetailsScreen({account,cloudState,onBack,onManage,onRefresh}){
 const verified=account?.status==='VERIFIED';
 const role=account?.guildRole==='DEVELOPER'?'Developer':
  account?.guildRole==='ADMIN'?'Administrator':
  account?.guildRole==='PUP_MEMBER'?'Pup Member':'Player';
 const code=verified?account?.friendCode:null;
 const linked=account?.linkedFromPlay===true;
 const button=(label,fn)=><Pressable accessibilityRole="button" onPress={fn}
  style={{borderRadius:12,padding:13,backgroundColor:C.surface,borderWidth:1,borderColor:C.line}}>
  <Text style={{color:C.text,textAlign:'center',fontWeight:'800'}}>{label}</Text>
 </Pressable>;
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
  {code?button('Share Friend Code',()=>void Share.share({message:code})):null}
  {button('Manage sign-in and contact details',onManage)}
  {button('Refresh account details',()=>void onRefresh?.())}
  {button('← Back to Settings',onBack)}
  <Text style={{color:C.muted,fontSize:12}}>Cloud backup status and diagnostics are available in Settings → Data Management. Device and backend credentials are never displayed.</Text>
 </View>;
}
