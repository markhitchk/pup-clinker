// GENERATED from Puppy-Clicker/src/nameSuggestions.js (puppies catalog only).
// Cosmetic suggestions may use Math.random: NEVER use this generator for passwords,
// authentication tokens, friend codes, reward determination, or signed gameplay.
const puppies=['Mochi','Biscuit','Waffles','Pebbles','Noodle','Pippin','Pickles','Maple','Clover','Luna','Teddy','Poppy','Cocoa','Dumpling','Muffin','Sparky','Pumpkin','Boba','Toffee','Miso','Nugget','Bean','Sprout','Marshmallow','Pancake','Daisy','Cinnamon','Tater','Snickers','Cookie','Oreo','Mango','Pixel','Comet','Bubbles','Buttons'];
export function suggestPuppyName(previous=''){
 const current=String(previous||'').trim().toLowerCase();
 for(let i=0;i<5;i++){
  const name=puppies[Math.floor(Math.random()*puppies.length)];
  if(name.toLowerCase()!==current)return name;
 }
 return puppies.find(name=>name.toLowerCase()!==current)||'Buddy';
}
