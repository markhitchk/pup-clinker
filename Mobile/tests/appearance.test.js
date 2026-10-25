import test from 'node:test';
import assert from 'node:assert/strict';
import {effectiveMotion,paletteFor,scaleStyles} from '../src/engine/settings.js';
import {applyAppearance,colors,themedStyles} from '../src/theme.js';
test('reduced motion overrides all saved animation choices without overwriting them',()=>{
 const g={animatedUi:true,animationsEnabled:true,buttonAnimations:true,reducedMotion:true};
 assert.deepEqual(effectiveMotion(g),{decorative:false,tap:false,buttons:false,casino:false});assert.equal(g.animationsEnabled,true);
 assert.deepEqual(effectiveMotion({...g,reducedMotion:false,performancePreset:'battery_saver'}),{decorative:false,tap:true,buttons:true,casino:false});
});
test('Follow System resolves the correct palette and custom accent keeps readable ink',()=>{
 assert.equal(paletteFor({themeMode:'system'},true).dark,true);assert.equal(paletteFor({themeMode:'system'},false).dark,false);
 assert.equal(paletteFor({themeMode:'light'},true).bg,'#FFFBFF');
 assert.equal(paletteFor({themeMode:'dark',accentHex:'#000000'}).onAccent,'#FFFFFF');assert.notEqual(paletteFor({themeMode:'dark',accentHex:'#000000'}).accentInk,'#000000');
 assert.equal(paletteFor({themeMode:'light',accentHex:'#FFFFFF'}).onAccent,'#071014');assert.notEqual(paletteFor({themeMode:'light',accentHex:'#FFFFFF'}).accentInk,'#FFFFFF');
});
test('existing scene styles update after a theme change and scale leaves flex and opacity alone',()=>{
 const styles=themedStyles(()=>({card:{backgroundColor:colors.surface,fontSize:20,flex:1,opacity:.6,padding:10}}));
 applyAppearance({themeMode:'dark',uiScale:'default'},true);assert.equal(styles.card.backgroundColor,'#151A1F');
 applyAppearance({themeMode:'light',uiScale:'large'},true);assert.equal(styles.card.backgroundColor,'#FFFFFF');assert.equal(styles.card.fontSize,21.6);assert.equal(styles.card.opacity,.6);assert.equal(styles.card.flex,1);
 assert.equal(scaleStyles({card:{height:100,borderWidth:1}},.94).card.height,94);
});
