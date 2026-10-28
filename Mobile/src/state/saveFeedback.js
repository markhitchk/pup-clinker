// UI-only interpretation of signed gameplay responses. Never changes
// balances, inventory or cloud revisions, and never retries economy writes.
const noChangeReasons=new Map([
 ['UPGRADE_MAXED','That upgrade has reached its maximum level. No progress was lost.'],
 ['TICKET_CAP','That ticket inventory is full. No progress was lost.'],
 ['SKILL_MAXED','That skill has reached its maximum level. No progress was lost.'],
 ['PRESTIGE_NOT_AVAILABLE','Prestige is not available yet. Your cloud progress is unchanged.'],
 ['GACHA_NO_PUPPIES','There are no eligible puppies for this draw. Your cloud progress is unchanged.'],
]);
const affordabilityReasons=new Set([
 'UPGRADE_NOT_AFFORDABLE','UPGRADE_NEEDS_TICKETS','TICKET_NOT_AFFORDABLE',
 'EXCHANGE_NOT_AFFORDABLE','ACCESSORY_NOT_AFFORDABLE','NOT_ENOUGH_CASINO_CHIPS',
 'GACHA_INSUFFICIENT_TREATS','GACHA_INSUFFICIENT_TICKET'
]);
export function classifyBetaSaveFailure(error){
 const raw=(String(error?.code||'')+' '+String(error?.message||'')).toUpperCase().slice(0,450);
 for(const [reason,notice] of noChangeReasons)
  if(raw.includes(reason))return {state:'NO_CHANGE',errorCode:null,notice};
 if([...affordabilityReasons].some(reason=>raw.includes(reason)))
  return {state:'NO_CHANGE',errorCode:null,
   notice:'This action needs more currency or tickets. Your cloud progress is unchanged.'};
 if(/BETA_GAMEPLAY_REJECTED|GAMEPLAY_NOT_PORTED|PUP_LEGAL_ACCEPTANCE_REQUIRED/.test(raw))
  return {state:'REJECTED',errorCode:'ACTION_NOT_ACCEPTED',
   notice:'That action was not accepted. Existing cloud progress remains intact.'};
 return {state:'VERIFYING_SAVE',errorCode:'SAVE_RECEIPT_UNCONFIRMED',
  notice:'The result of your last action needs verification. Check Cloud Backup before trying again.'};
}
