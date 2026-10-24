import {freshGame,sanitizeGame} from '../engine/game.js';
import {validBlackjackState} from '../engine/blackjack.js';

export const GUEST_SAVE_KEY='puppy-clicker:react-native:guest:v1';
export const serializeGuestSave=game=>{
  const clean=sanitizeGame(game);
  if(clean.casinoRound&&!validBlackjackState(clean.casinoRound))clean.casinoRound=null;
  return JSON.stringify({kind:'guest-only',schema:1,game:clean,savedAt:Date.now()});
};
export function parseGuestSave(raw){
  if(typeof raw!=='string'||raw.length>1000000)return freshGame();
  try{
    const envelope=JSON.parse(raw);
    if(envelope?.kind!=='guest-only'||envelope?.schema!==1||!envelope?.game||typeof envelope.game!=='object')return freshGame();
    const clean=sanitizeGame(envelope.game);
    if(clean.casinoRound&&!validBlackjackState(clean.casinoRound))clean.casinoRound=null;
    return clean;
  }catch{return freshGame();}
}
