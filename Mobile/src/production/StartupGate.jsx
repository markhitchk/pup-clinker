import React from 'react';
import {View,Text,ActivityIndicator,Pressable} from 'react-native';
export default function StartupGate({access,onRetry,onSignIn}){
 return <View style={{flex:1,backgroundColor:'#08161C',justifyContent:'center',padding:30}}><Text style={{color:'#fff',fontSize:28,textAlign:'center'}}>Puppy Clicker</Text><Text style={{color:'#fff',textAlign:'center',fontSize:22}}>{access.title}</Text><Text style={{color:'#fff',textAlign:'center'}}>{access.message}</Text>{access.phase==='loading'?<ActivityIndicator/>:<Pressable accessibilityRole="button" onPress={access.phase==='signin'?onSignIn:onRetry}><Text style={{color:'#00BBFF',textAlign:'center'}}>{access.phase==='signin'?'Sign in':'Retry'}</Text></Pressable>}</View>;
}
