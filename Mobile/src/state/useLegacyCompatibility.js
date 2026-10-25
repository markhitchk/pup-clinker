import {useEffect,useState} from 'react';
import {NativeModules,Platform} from 'react-native';

// This hook only checks in-place migration safety. It never reads, uploads,
// overwrites or deserializes old game progression into the guest reducer.
const INITIAL={loading:true,status:'checking',requiresMigration:false,legacyDataDetected:false};
export function useLegacyCompatibility(){
 const [state,setState]=useState(INITIAL);
 useEffect(()=>{
  let mounted=true;
  if(Platform.OS!=='android'){
   setState({loading:false,status:'unsupported',requiresMigration:false,legacyDataDetected:false});
   return;
  }
  const module=NativeModules.PuppyCompatibility;
  if(typeof module?.inspect!=='function'){
   // Expo Go does not include the custom Android native module.
   setState({loading:false,status:'native-module-unavailable',requiresMigration:false,legacyDataDetected:false});
   return;
  }
  void module.inspect().then(result=>{
   if(mounted)setState({...result,loading:false});
  }).catch(()=>{
   // A native read failure is a migration blocker, never a reason to reset data.
   if(mounted)setState({loading:false,status:'inspection-failed',requiresMigration:true,legacyDataDetected:true});
  });
  return ()=>{mounted=false;};
 },[]);
 return state;
}
