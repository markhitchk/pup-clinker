import React from 'react';
import {Platform,View,requireNativeComponent} from 'react-native';
import {colors} from '../theme.js';
const NativePointer=Platform.OS==='android'?requireNativeComponent('PuppyPointerView'):View;
export default function PuppyPointer({enabled=true,children,style}){
 return <NativePointer collapsable={false} style={style} {...(Platform.OS==='android'?{darkTheme:colors.dark,puppyEnabled:enabled}:{})}>{children}</NativePointer>;
}
