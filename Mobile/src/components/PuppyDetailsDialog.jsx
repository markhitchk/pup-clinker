import React,{useEffect,useState} from 'react';
import {Modal,Pressable,ScrollView,Text,TextInput,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors as C,themedStyles} from '../theme.js';

/**
 * Kotlin PuppyRosterDialogSystem parity. A preview owns the Use/Rename
 * confirmation; it never mutates progress without the caller's action.
 */
export default function PuppyDetailsDialog({puppy,visible,onClose,portrait,assetId,originalName,
 displayName,unlocked,current,favorite,onToggleFavorite,onRetryArtwork,onUse,onRename}){
 const insets=useSafeAreaInsets();
 const [mode,setMode]=useState('preview');
 const [name,setName]=useState('');
 useEffect(()=>{if(visible){setMode('preview');setName(displayName||'');}},[visible,puppy?.id,displayName]);
 if(!puppy)return null;
 const finish=()=>{setMode('preview');onClose?.();};
 const renameValue=name.trim().replace(/\n/g,' ').slice(0,18);
 const title=mode==='rename'?'Rename Puppy':mode==='use'?'Use Puppy':unlocked?'Puppy Details':'How to Unlock';
 const performUse=()=>{if(!unlocked||current)return;onUse?.(puppy.id);finish();};
 const performRename=()=>{if(!current||!renameValue)return;onRename?.(renameValue);finish();};
 return <Modal visible={Boolean(visible)} transparent statusBarTranslucent navigationBarTranslucent
  animationType="fade" onRequestClose={finish}>
  <View style={[s.scrim,{paddingTop:insets.top+12,paddingBottom:insets.bottom+12}]}>
   <View accessibilityViewIsModal style={s.panel}>
    <View style={s.header}><Text style={s.heading}>{title}</Text>
     <Pressable accessibilityRole="button" accessibilityLabel="Close puppy details" onPress={finish}
      style={s.close}><Text style={s.cross}>×</Text></Pressable></View>
    <ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
     <View style={s.identity}>
      <View style={s.portrait}>{portrait}</View>
      <View style={{flex:1,gap:5,minWidth:0}}>
       <Text style={s.name}>{displayName||originalName}</Text>
       <Text style={s.muted}>{assetId}</Text>
       <View style={s.pills}>
        <View style={s.pill}><Text style={s.pillText}>{puppy.groupTitle||'Puppies'}</Text></View>
        <View style={s.pill}><Text style={s.pillText}>{current?'Current':unlocked?'Unlocked':'🔒 Locked'}</Text></View>
       </View>
      </View>
     </View>
     {mode==='preview'&&unlocked?<>
      <View style={s.section}><Text style={s.sectionHeading}>About</Text>
       <Text style={s.sectionBody}>{puppy.description||'No additional puppy details are available yet.'}</Text>
      </View>
      {displayName!==originalName?<View style={s.section}><Text style={s.sectionHeading}>Original roster name</Text><Text style={s.sectionBody}>{originalName}</Text></View>:null}
      <View style={s.row}>
       <Pressable accessibilityRole="button" accessibilityLabel={favorite?'Remove puppy from favorites':'Add puppy to favorites'}
        onPress={onToggleFavorite} style={s.secondary}><Text style={s.secondaryText}>{favorite?'♥ Favorite':'♡ Favorite'}</Text></Pressable>
       <Pressable accessibilityRole="button" accessibilityLabel="Retry puppy artwork" onPress={onRetryArtwork}
        style={s.secondary}><Text style={s.secondaryText}>↻ Retry Art</Text></Pressable>
      </View>
      {!current?<Pressable accessibilityRole="button" accessibilityLabel="Use Puppy" style={s.primary}
       onPress={()=>setMode('use')}><Text style={s.primaryText}>Use Puppy</Text></Pressable>:null}
      {current?<Pressable accessibilityRole="button" accessibilityLabel="Rename Puppy" style={s.secondary}
       onPress={()=>{setName(displayName||'');setMode('rename');}}><Text style={s.secondaryText}>Rename Puppy</Text></Pressable>:null}
     </>:null}
     {mode==='preview'&&!unlocked?<View style={s.section}>
      <Text style={s.sectionHeading}>Unlock requirement</Text>
      <Text style={s.sectionBody}>{puppy.unlockSource||'Unlock instructions are not available yet.'}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Retry puppy artwork" style={s.secondary} onPress={onRetryArtwork}><Text style={s.secondaryText}>↻ Retry Art</Text></Pressable>
     </View>:null}
     {mode==='use'?<View style={s.section}><Text style={s.sectionHeading}>Make active puppy</Text>
      <Text style={s.sectionBody}>Use {originalName} as your active puppy?</Text></View>:null}
     {mode==='rename'?<View style={s.section}><Text style={s.sectionHeading}>Original roster name</Text>
      <Text style={s.sectionBody}>{originalName}</Text>
      <TextInput accessibilityLabel="New puppy name" value={name} maxLength={18} onChangeText={setName}
       placeholder="Puppy name" placeholderTextColor={C.muted} style={s.input}/>
      <Text style={s.muted}>{name.length}/18</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Restore original puppy name" onPress={()=>setName(originalName.slice(0,18))}>
       <Text style={s.secondaryText}>Use original name</Text></Pressable></View>:null}
     {mode==='preview'?<Pressable accessibilityRole="button" accessibilityLabel="Done" style={s.primary} onPress={finish}>
      <Text style={s.primaryText}>{unlocked?'Done':'Close'}</Text></Pressable>:
      <View style={s.row}>
       <Pressable accessibilityRole="button" accessibilityLabel="Cancel" style={s.secondary} onPress={()=>setMode('preview')}><Text style={s.secondaryText}>Cancel</Text></Pressable>
       <Pressable accessibilityRole="button" accessibilityLabel={mode==='rename'?'Save puppy name':'Confirm use puppy'}
        disabled={mode==='rename'&&!renameValue} style={[s.primary,{flex:1},mode==='rename'&&!renameValue&&{opacity:.4}]}
        onPress={mode==='rename'?performRename:performUse}><Text style={s.primaryText}>{mode==='rename'?'Save':'Use Puppy'}</Text></Pressable>
      </View>}
    </ScrollView>
   </View>
  </View>
 </Modal>;
}
const s=themedStyles(()=>({
 scrim:{flex:1,justifyContent:'center',paddingHorizontal:16,backgroundColor:'rgba(0,0,0,0.70)'},
 panel:{width:'100%',maxWidth:460,maxHeight:'95%',alignSelf:'center',borderRadius:28,backgroundColor:C.dark?'#132F3B':C.surface,overflow:'hidden'},
 header:{paddingHorizontal:20,paddingTop:18,paddingBottom:8,flexDirection:'row',alignItems:'center'},
 heading:{flex:1,color:C.text,fontWeight:'900',fontSize:23},
 close:{width:40,height:40,alignItems:'center',justifyContent:'center'},
 cross:{fontSize:34,color:C.text},
 content:{paddingHorizontal:18,paddingBottom:24,gap:14},
 identity:{backgroundColor:C.dark?'#172B35':C.accentSoft,borderRadius:20,padding:12,flexDirection:'row',alignItems:'center',gap:12},
 portrait:{width:116,alignItems:'center',justifyContent:'center'},
 name:{fontSize:23,fontWeight:'900',color:C.text},
 muted:{fontSize:13,color:C.muted},
 pills:{flexDirection:'row',flexWrap:'wrap',gap:5},
 pill:{borderRadius:22,backgroundColor:C.dark?'#4A4556':'#E6E0EE',paddingHorizontal:10,paddingVertical:5},
 pillText:{color:C.text,fontSize:12,fontWeight:'800'},
 section:{backgroundColor:C.dark?'#172B35':C.accentSoft,borderRadius:18,padding:15,gap:8},
 sectionHeading:{color:C.muted,fontSize:16,fontWeight:'900'},
 sectionBody:{color:C.text,fontSize:17,lineHeight:25},
 row:{flexDirection:'row',gap:8,alignItems:'stretch'},
 secondary:{flex:1,minHeight:46,borderWidth:1,borderColor:C.muted,borderRadius:24,alignItems:'center',justifyContent:'center',padding:8},
 secondaryText:{color:C.accentInk,fontWeight:'800',fontSize:15},
 primary:{minHeight:48,backgroundColor:C.accent,borderRadius:24,alignItems:'center',justifyContent:'center',padding:9},
 primaryText:{color:C.dark?'#071C25':'#FFFFFF',fontSize:16,fontWeight:'900'},
 input:{color:C.text,borderColor:C.line,borderWidth:1,borderRadius:13,minHeight:48,paddingHorizontal:14}
}));
