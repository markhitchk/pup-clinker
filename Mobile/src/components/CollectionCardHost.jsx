// Native host elements for the JSX in the production website's collection card.
import React,{createContext,useContext} from 'react';
import {Image,Pressable,StyleSheet,Text,View,useWindowDimensions} from 'react-native';
import palette from '../../website/rarity-colors.json';
import {PUPPY_IMAGES} from '../assets/puppies.js';

const Tier=createContext(palette.common);
export function Article({children,...data}){
 const color=palette[data['data-rarity']]||palette.common;
 return <Tier.Provider value={color}><View style={[styles.article,{
  borderColor:data['data-current']==='true'?'#59bda0':color.ink,
  experimental_backgroundImage:'linear-gradient(160deg, rgba('+color.rgb+',0.12), #191c2b 44%, #10131f)'
 }]}>{children}</View></Tier.Provider>;
}
export function Box({className='',children}){
 const color=useContext(Tier),{width}=useWindowDimensions(),desktop=width>=960;
 if(className==='meet-puppy-art')return <View style={[styles.art,{
  height:desktop?Math.min(205,Math.max(148,width*.15)):Math.min(132,Math.max(96,width*.27)),
  borderColor:'rgba('+color.rgb+',0.24)',paddingTop:desktop?24:22
 }]}>{children}</View>;
 if(className==='meet-puppy-body')return <View style={{padding:desktop?12:9,gap:desktop?8:6}}>{children}</View>;
 if(className==='meet-puppy-badges')return <View style={styles.badges}>{children}</View>;
 return <View style={{flex:1}}>{children}</View>;
}
export function Title({children}){
 const {width}=useWindowDimensions();
 return <Text style={{fontSize:width>=960?17:15,fontWeight:'800',color:'#f4fbff'}}>{children}</Text>;
}
export function Badge({className='',children}){
 const tier=useContext(Tier);
 if(className.includes('collection-card-unlock'))return null;
 if(className==='collection-card-favorite')return <Text style={styles.favorite}>{children}</Text>;
 const group=className==='meet-puppy-group',rarity=className.includes('meet-puppy-badge-rarity');
 return <View style={[styles.pill,group&&styles.group,rarity&&{borderColor:tier.ink,backgroundColor:'rgba('+tier.rgb+',0.17)'}]}>
  {React.Children.map(children,child=>typeof child==='string'?<Text style={[styles.pillText,rarity&&{color:tier.ink}]}>{child}</Text>:child)}
 </View>;
}
export function Dot(){
 const tier=useContext(Tier);
 return <View style={{width:7,height:7,borderRadius:4,backgroundColor:'rgb('+tier.rgb+')'}}/>;
}
export function Select({onClick,children,...props}){
 return <Pressable accessibilityRole="button" accessibilityLabel={props['aria-label']} onPress={onClick}
  style={children?{flex:1}:StyleSheet.absoluteFill}>{children}</Pressable>;
}
export function PuppyArtwork({id,name,uri}){
 const image=PUPPY_IMAGES[id]||(uri?{uri}:null);
 return image?<Image accessibilityLabel={name} resizeMode="contain" source={image} style={{width:'100%',height:'100%'}}/>:null;
}
const styles=StyleSheet.create({
 article:{flex:1,overflow:'hidden',backgroundColor:'#191c2b',borderRadius:16,borderWidth:1},
 art:{alignItems:'center',justifyContent:'center',backgroundColor:'#111726',paddingHorizontal:5,paddingBottom:4,borderBottomWidth:1},
 badges:{flexDirection:'row',flexWrap:'wrap',alignItems:'center',gap:4},
 pill:{paddingHorizontal:6,paddingVertical:3,borderRadius:99,borderWidth:1,borderColor:'#3e566c',backgroundColor:'#263140',flexDirection:'row',alignItems:'center',gap:4},
 group:{position:'absolute',top:6,right:6,backgroundColor:'#112331'},
 favorite:{position:'absolute',top:6,left:7,fontSize:17,color:'#ff9cc4'},
 pillText:{fontSize:10,fontWeight:'800',color:'#e8f5fe'}
});
