import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const source=join(root,'../App/app/src/main/res/drawable');
const copied=join(root,'assets/casino');
const names=[
 'blank_glossy_cream_card_panel.png','glossy_blue_paw_print_card_back.png',
 'puppy_blackjack_table.png','puppy_lucky_wheel_disc.png',
 'puppy_lucky_wheel_frame.png','puppy_lucky_wheel_pointer.png',
 'puppy_plinko_ball.png','puppy_plinko_bins.png','puppy_plinko_drop_button.png',
 'puppy_plinko_frame.png','puppy_plinko_glass.png','puppy_plinko_header.png','puppy_plinko_pegs.png',
 'puppy_roulette_ball.png','puppy_roulette_rotor.png','puppy_roulette_table.png','puppy_roulette_wheel_base.png',
 'puppy_scratch_play_border.png','puppy_slots_lever_base.png','puppy_slots_lever_handle.png',
 'puppy_slots_machine.png','puppy_slots_mascot.png',
 'pup_coin.png','slot_ball.png','slot_paw.png','slot_puppy.png',
 'slot_star.png','slot_ticket.png','slot_treat.png'
];
test('all 29 mobile casino PNGs are byte-identical to original Kotlin drawables',()=>{
 for(const name of names){
  const original=readFileSync(join(source,name));
  const react=readFileSync(join(copied,name));
  assert.ok(react.equals(original),name+' must not be replaced with a mockup or compressed derivative');
 }
 assert.equal(readdirSync(copied).filter(name=>name.endsWith('.png')).length,names.length);
});
test('React casino uses original art in six standalone game screens without WebViews',()=>{
 const app=readFileSync(join(root,'App.js'),'utf8');
 for(const name of ['slots','roulette','blackjack','plinko','scratchers','wheel']){
  assert.ok(app.includes("['"+name+"'"),name+' navigation is missing');
 }
 for(const name of ['puppy_blackjack_table.png','puppy_slots_machine.png','puppy_roulette_wheel_base.png',
   'puppy_plinko_frame.png','puppy_scratch_play_border.png','puppy_lucky_wheel_disc.png']){
  assert.ok(app.includes(name),name+' not referenced by UI');
 }
 assert.ok(!app.includes('<WebView'));
});
