import {freshGame,sanitizeGame} from '../engine/game.js';

export const GUEST_SAVE_KEY='puppy-clicker:react-native:guest:v1';
export const serializeGuestSave=game=>JSON.stringify({
  kind:'guest-only',schema:1,game:sanitizeGame(game),savedAt:Date.now()
});
export function parseGuestSave(raw){
  if(typeof raw!=='string'||raw.length>1000000)return freshGame();
  try{
    const envelope=JSON.parse(raw);
    if(envelope?.kind!=='guest-only'||envelope?.schema!==1||!envelope?.game||typeof envelope.game!=='object')return freshGame();
    return sanitizeGame(envelope.game);
  }catch{return freshGame();}
}
