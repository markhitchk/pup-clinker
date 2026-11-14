import React,{useState} from 'react';
import {Platform,StyleSheet,View} from 'react-native';
import {Canvas,Circle,RadialGradient,Skia,vec} from 'react-native-skia';
import {shouldUseVulkanEffects} from '../graphics/vulkanPolicy.js';

// This is a non-interactive ambient layer beneath puppy art and tap controls.
// React Native owns gameplay, authentication, accessibility and all hit targets.
// Graphite's Android renderer uses Vulkan 1.1; no Canvas is mounted if unavailable.
const STARS=Object.freeze([
 {x:.16,y:.14,r:1.6},{x:.75,y:.15,r:1.4},{x:.88,y:.32,r:1.2},
 {x:.12,y:.52,r:1.1},{x:.78,y:.53,r:1.8},{x:.27,y:.78,r:1.3},
 {x:.64,y:.86,r:1.5},{x:.9,y:.73,r:1.0},
]);

export default function VulkanYardEffects({preferences}){
 const [frame,setFrame]=useState({width:0,height:0});
 if(Platform.OS!=='android'||!shouldUseVulkanEffects(preferences,Skia.gpu?.isAvailable===true))return null;
 const {width,height}=frame;
 const center=vec(width*.5,height*.51);
 const radius=Math.max(width*.53,height*.45);
 return <View pointerEvents="none" accessible={false} style={StyleSheet.absoluteFillObject}
  onLayout={event=>{
   const next=event.nativeEvent.layout;
   if(next.width!==frame.width||next.height!==frame.height)
    setFrame({width:next.width,height:next.height});
  }}>
  {width>0&&height>0?<Canvas style={StyleSheet.absoluteFillObject}
   android={{backingView:'TextureView',opaque:false}}>
   <Circle c={center} r={radius}>
    <RadialGradient c={center} r={radius}
     colors={['rgba(0,184,240,0.13)','rgba(0,184,240,0)']}/>
   </Circle>
   {STARS.map((star,index)=><Circle key={index} cx={star.x*width} cy={star.y*height}
    r={star.r} color="rgba(144,228,255,0.38)"/>)}
  </Canvas>:null}
 </View>;
}
