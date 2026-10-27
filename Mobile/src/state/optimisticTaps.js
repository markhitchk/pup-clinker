import {tap} from '../engine/game.js';

// Display-only prediction for signed taps awaiting Supabase acknowledgement.
// The authoritative save is never mutated or written back by this projection.
// The server alone awards XP, RNG drops, purchases and permanent balances.
export function projectPendingTapDisplay(authoritative, pendingTaps){
 if(!Array.isArray(pendingTaps)||pendingTaps.length===0)return authoritative;
 const preview={...authoritative};
 for(const item of pendingTaps){
  const at=item?.at;
  if(!Number.isSafeInteger(at)||at<0)continue;
  // Deterministic "no ticket" roll: the server owns random prizes.
  // The normal tap engine mirrors Kotlin's care, cooldown and reward caps.
  tap(preview,at,()=>0.99);
 }
 return preview;
}
